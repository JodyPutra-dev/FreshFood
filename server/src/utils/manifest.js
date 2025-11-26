const fs = require('fs').promises;
const path = require('path');

/**
 * Read manifest from file
 * @param {string} manifestPath - Path to manifest.json
 * @returns {Promise<Object>} Manifest object with models array
 */
async function readManifest(manifestPath) {
  try {
    // Check if manifest file exists
    try {
      await fs.access(manifestPath);
    } catch (error) {
      // Return default structure if file doesn't exist
      console.log('Manifest file not found, returning default structure');
      return { models: [] };
    }
    
    // Read and parse manifest
    const manifestContent = await fs.readFile(manifestPath, 'utf-8');
    const manifest = JSON.parse(manifestContent);
    
    // Validate structure matches ModelManifestDto format
    if (!manifest.models || !Array.isArray(manifest.models)) {
      throw new Error('Invalid manifest structure: missing or invalid models array');
    }
    
    // Validate each model entry
    manifest.models.forEach((model, index) => {
      if (!model.name || typeof model.name !== 'string') {
        throw new Error(`Invalid model at index ${index}: missing or invalid name`);
      }
      if (typeof model.version !== 'number' || model.version < 1) {
        throw new Error(`Invalid model at index ${index}: invalid version`);
      }
      if (!model.downloadUrl || typeof model.downloadUrl !== 'string') {
        throw new Error(`Invalid model at index ${index}: missing or invalid downloadUrl`);
      }
      if (!model.sha256 || typeof model.sha256 !== 'string') {
        throw new Error(`Invalid model at index ${index}: missing or invalid sha256`);
      }
    });
    
    return manifest;
    
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error(`Failed to parse manifest JSON: ${error.message}`);
    }
    throw error;
  }
}

module.exports = {
  readManifest
};
