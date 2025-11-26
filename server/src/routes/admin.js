const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs').promises;
const crypto = require('crypto');
const { verifyApiKey } = require('../middleware/auth');
const { calculateSHA256 } = require('../utils/hash');
const { readManifest, writeManifest, updateModelInManifest } = require('../utils/manifest');

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const modelsDir = path.join(__dirname, '../..', process.env.MODELS_DIR || 'models');
    cb(null, modelsDir);
  },
  filename: (req, file, cb) => {
    // Preserve original filename
    cb(null, file.originalname);
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: (parseInt(process.env.MAX_FILE_SIZE_MB) || 100) * 1024 * 1024 // Convert MB to bytes
  },
  fileFilter: (req, file, cb) => {
    // Accept only .tflite files
    if (path.extname(file.originalname).toLowerCase() === '.tflite') {
      cb(null, true);
    } else {
      cb(new Error('Only .tflite files are allowed'), false);
    }
  }
});

/**
 * POST /admin/upload
 * Upload a new model version
 * Requires X-API-Key header for authentication
 */
router.post('/upload', verifyApiKey, upload.single('file'), async (req, res, next) => {
  let uploadedFilePath = null;
  
  try {
    // Check if file was uploaded
    if (!req.file) {
      return res.status(400).json({
        error: 'Bad Request',
        message: 'No file uploaded. Please provide a .tflite file.',
        timestamp: new Date().toISOString()
      });
    }
    
    uploadedFilePath = req.file.path;
    const fileName = req.file.filename;
    
    // Validate file extension
    if (!fileName.endsWith('.tflite')) {
      throw new Error('Invalid file extension. Only .tflite files are allowed.');
    }
    
    // Extract model name (remove .tflite extension)
    const modelName = fileName.replace('.tflite', '');
    
    console.log(`Processing upload for model: ${modelName}`);
    
    // Compute SHA-256 hash
    console.log('Computing SHA-256 hash...');
    const sha256Hash = await calculateSHA256(uploadedFilePath);
    console.log(`SHA-256: ${sha256Hash}`);
    
    // Read existing manifest
    const manifestPath = path.join(__dirname, '../..', process.env.MANIFEST_PATH || 'models/manifest.json');
    let manifest = await readManifest(manifestPath);
    
    // Construct download URL
    const serverUrl = process.env.SERVER_URL || `http://localhost:${process.env.PORT || 3000}`;
    const downloadUrl = `${serverUrl}/models/${fileName}`;
    
    // Find existing model to determine version
    const existingModel = manifest.models.find(m => m.name === modelName);
    const newVersion = existingModel ? existingModel.version + 1 : 1;
    
    // Update manifest
    const modelInfo = {
      name: modelName,
      version: newVersion,
      downloadUrl: downloadUrl,
      sha256: sha256Hash
    };
    
    manifest = updateModelInManifest(manifest, modelInfo);
    
    // Write updated manifest
    await writeManifest(manifestPath, manifest);
    
    console.log(`Model uploaded successfully: ${modelName} v${newVersion}`);
    
    // Return success response
    res.status(200).json({
      success: true,
      message: 'Model uploaded successfully',
      model: {
        name: modelName,
        version: newVersion,
        sha256: sha256Hash,
        downloadUrl: downloadUrl,
        fileSize: req.file.size
      },
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('Upload error:', error);
    
    // Clean up uploaded file on error
    if (uploadedFilePath) {
      try {
        await fs.unlink(uploadedFilePath);
        console.log('Cleaned up uploaded file after error');
      } catch (unlinkError) {
        console.error('Failed to clean up uploaded file:', unlinkError);
      }
    }
    
    // Handle specific multer errors
    if (error instanceof multer.MulterError) {
      if (error.code === 'LIMIT_FILE_SIZE') {
        return res.status(413).json({
          error: 'Payload Too Large',
          message: `File size exceeds the maximum allowed size of ${process.env.MAX_FILE_SIZE_MB || 100}MB`,
          timestamp: new Date().toISOString()
        });
      }
    }
    
    next(error);
  }
});

// Error handler for multer errors
router.use((error, req, res, next) => {
  if (error instanceof multer.MulterError) {
    return res.status(400).json({
      error: 'Upload Error',
      message: error.message,
      timestamp: new Date().toISOString()
    });
  }
  next(error);
});

module.exports = router;
