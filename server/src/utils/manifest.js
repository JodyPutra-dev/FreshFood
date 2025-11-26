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

/**
 * Write manifest to file atomically
 * @param {string} manifestPath - Path to manifest.json
 * @param {Object} manifestData - Manifest object to write
 * @returns {Promise<void>}
 */
async function writeManifest(manifestPath, manifestData) {
  try {
    // Validate manifest data structure
    if (!manifestData.models || !Array.isArray(manifestData.models)) {
      throw new Error('Invalid manifest data: missing or invalid models array');
    }
    
    // Convert to formatted JSON string
    const jsonString = JSON.stringify(manifestData, null, 2);
    
    // Write to temporary file first
    const tempPath = manifestPath + '.tmp';
    await fs.writeFile(tempPath, jsonString, 'utf-8');
    
    // Atomically rename temp file to actual manifest
    // This ensures no partial writes if process crashes
    await fs.rename(tempPath, manifestPath);
    
    console.log('Manifest written successfully');
    
  } catch (error) {
    throw new Error(`Failed to write manifest: ${error.message}`);
  }
}

/**
 * Update or add model entry in manifest
 * @param {Object} manifest - Current manifest object
 * @param {Object} modelInfo - Model information {name, sha256, downloadUrl}
 * @returns {Object} Updated manifest object
 */
function updateModelInManifest(manifest, modelInfo) {
  // Validate modelInfo has required fields
  if (!modelInfo.name || typeof modelInfo.name !== 'string') {
    throw new Error('Model info must have a valid name');
  }
  if (!modelInfo.sha256 || typeof modelInfo.sha256 !== 'string') {
    throw new Error('Model info must have a valid sha256 hash');
  }
  if (!modelInfo.downloadUrl || typeof modelInfo.downloadUrl !== 'string') {
    throw new Error('Model info must have a valid downloadUrl');
  }
  
  // Find existing model by name
  const existingModelIndex = manifest.models.findIndex(m => m.name === modelInfo.name);
  
  if (existingModelIndex !== -1) {
    // Update existing model
    const existingModel = manifest.models[existingModelIndex];
    manifest.models[existingModelIndex] = {
      name: modelInfo.name,
      version: modelInfo.version || (existingModel.version + 1),
      downloadUrl: modelInfo.downloadUrl,
      sha256: modelInfo.sha256
    };
    console.log(`Updated existing model: ${modelInfo.name} (v${manifest.models[existingModelIndex].version})`);
  } else {
    // Add new model with version 1
    manifest.models.push({
      name: modelInfo.name,
      version: modelInfo.version || 1,
      downloadUrl: modelInfo.downloadUrl,
      sha256: modelInfo.sha256
    });
    console.log(`Added new model: ${modelInfo.name} (v1)`);
  }
  
  return manifest;
}

module.exports = {
  readManifest,
  writeManifest,
  updateModelInManifest
};
