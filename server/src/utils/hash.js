const fs = require('fs');
const crypto = require('crypto');

/**
 * Calculate SHA-256 hash of a file
 * @param {string} filePath - Path to the file
 * @returns {Promise<string>} SHA-256 hash as lowercase hexadecimal string
 */
function calculateSHA256(filePath) {
  return new Promise((resolve, reject) => {
    try {
      // Create hash object
      const hash = crypto.createHash('sha256');
      
      // Create read stream
      const stream = fs.createReadStream(filePath);
      
      // Handle stream events
      stream.on('data', (chunk) => {
        hash.update(chunk);
      });
      
      stream.on('end', () => {
        // Get hash digest as lowercase hexadecimal string
        // This matches the format expected by Android ModelDownloader.kt
        const hashString = hash.digest('hex').toLowerCase();
        resolve(hashString);
      });
      
      stream.on('error', (error) => {
        reject(new Error(`Failed to calculate hash: ${error.message}`));
      });
      
    } catch (error) {
      reject(new Error(`Failed to calculate hash: ${error.message}`));
    }
  });
}

module.exports = {
  calculateSHA256
};
