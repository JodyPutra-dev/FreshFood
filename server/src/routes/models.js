const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs').promises;
const { verifyClientApiKey } = require('../middleware/auth');

/**
 * GET /manifest.json
 * Returns the model manifest matching ModelManifestDto structure
 * Requires client API key authentication
 */
router.get('/manifest.json', verifyClientApiKey, async (req, res, next) => {
  try {
    const manifestPath = path.join(__dirname, '../..', process.env.MANIFEST_PATH || 'models/manifest.json');
    
    // Check if manifest exists
    try {
      await fs.access(manifestPath);
    } catch (error) {
      // Return empty manifest if file doesn't exist
      return res.status(200)
        .set('Cache-Control', 'public, max-age=300')
        .json({ models: [] });
    }
    
    // Read and parse manifest
    const manifestContent = await fs.readFile(manifestPath, 'utf-8');
    const manifest = JSON.parse(manifestContent);
    
    // Validate structure
    if (!manifest.models || !Array.isArray(manifest.models)) {
      throw new Error('Invalid manifest structure');
    }
    
    // Set cache headers and return manifest
    res.status(200)
      .set('Cache-Control', 'public, max-age=300') // Cache for 5 minutes
      .set('Content-Type', 'application/json')
      .json(manifest);
      
  } catch (error) {
    console.error('Error reading manifest:', error);
    next(error);
  }
});

/**
 * GET /models/:modelName.tflite
 * Downloads a specific model file
 * Requires client API key authentication
 */
router.get('/models/:modelName.tflite', verifyClientApiKey, async (req, res, next) => {
  try {
    const { modelName } = req.params;
    
    // Validate model name (alphanumeric, underscores, hyphens only)
    const validNameRegex = /^[a-zA-Z0-9_-]+$/;
    if (!validNameRegex.test(modelName)) {
      return res.status(400).json({
        error: 'Bad Request',
        message: 'Invalid model name. Only alphanumeric characters, underscores, and hyphens are allowed.',
        timestamp: new Date().toISOString()
      });
    }
    
    // Construct file path
    const modelsDir = path.join(__dirname, '../..', process.env.MODELS_DIR || 'models');
    const filePath = path.join(modelsDir, `${modelName}.tflite`);
    
    // Check if file exists
    try {
      await fs.access(filePath);
    } catch (error) {
      return res.status(404).json({
        error: 'Not Found',
        message: `Model '${modelName}.tflite' not found`,
        timestamp: new Date().toISOString()
      });
    }
    
    // Get file stats for ETag
    const stats = await fs.stat(filePath);
    const etag = `"${stats.size}-${stats.mtime.getTime()}"`;
    
    // Set headers and send file
    res.status(200)
      .set('Content-Type', 'application/octet-stream')
      .set('Content-Disposition', `attachment; filename="${modelName}.tflite"`)
      .set('Cache-Control', 'public, max-age=86400') // Cache for 1 day
      .set('ETag', etag)
      .sendFile(filePath);
      
  } catch (error) {
    console.error('Error downloading model:', error);
    next(error);
  }
});

module.exports = router;
