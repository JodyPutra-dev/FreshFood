const crypto = require('crypto');

/**
 * Verify client API key from request header
 * Used for model distribution endpoints (/manifest.json, /models/*)
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 * @param {Function} next - Express next middleware function
 */
function verifyClientApiKey(req, res, next) {
  try {
    // Extract API key from header
    const providedKey = req.headers['x-api-key'];
    const expectedKey = process.env.CLIENT_API_KEY;
    
    // Check if API key is configured
    if (!expectedKey || expectedKey === 'your-client-api-key-here-change-this-in-production') {
      console.error('CLIENT_API_KEY not properly configured');
      return res.status(500).json({
        error: 'Internal Server Error',
        message: 'Server authentication not configured',
        timestamp: new Date().toISOString()
      });
    }
    
    // Check if API key was provided
    if (!providedKey) {
      console.warn('Client authentication attempt without API key');
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'API key required. Please provide X-API-Key header.',
        timestamp: new Date().toISOString()
      });
    }
    
    // Validate API key format (basic check)
    if (typeof providedKey !== 'string' || providedKey.length < 16) {
      console.warn('Client authentication attempt with invalid API key format');
      return res.status(403).json({
        error: 'Forbidden',
        message: 'Invalid API key format',
        timestamp: new Date().toISOString()
      });
    }
    
    // Compare keys using constant-time comparison to prevent timing attacks
    const providedKeyBuffer = Buffer.from(providedKey, 'utf-8');
    const expectedKeyBuffer = Buffer.from(expectedKey, 'utf-8');
    
    // Ensure both buffers have the same length for comparison
    if (providedKeyBuffer.length !== expectedKeyBuffer.length) {
      console.warn('Client authentication failed: Key length mismatch');
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Invalid API key',
        timestamp: new Date().toISOString()
      });
    }
    
    // Perform constant-time comparison
    const isValid = crypto.timingSafeEqual(providedKeyBuffer, expectedKeyBuffer);
    
    if (!isValid) {
      console.warn('Client authentication failed: Invalid API key');
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Invalid API key',
        timestamp: new Date().toISOString()
      });
    }
    
    // Authentication successful
    console.log('Client authentication successful');
    next();
    
  } catch (error) {
    console.error('Client authentication error:', error);
    return res.status(500).json({
      error: 'Internal Server Error',
      message: 'Authentication failed',
      timestamp: new Date().toISOString()
    });
  }
}

module.exports = {
  verifyClientApiKey
};
