require('dotenv').config();
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const path = require('path');

// Import routes
const modelsRouter = require('./routes/models');

// Initialize Express app
const app = express();
const PORT = process.env.PORT || 3000;
const NODE_ENV = process.env.NODE_ENV || 'development';

// Middleware stack
app.use(helmet()); // Security headers

// CORS configuration with allowed origins
const allowedOrigins = process.env.ALLOWED_ORIGINS 
  ? process.env.ALLOWED_ORIGINS.split(',').map(origin => origin.trim())
  : ['http://localhost:3000'];

if (NODE_ENV === 'production' && (!process.env.ALLOWED_ORIGINS || process.env.ALLOWED_ORIGINS === '*')) {
  console.warn('WARNING: ALLOWED_ORIGINS not configured for production. Using default localhost.');
}

app.use(cors({
  origin: allowedOrigins,
  methods: ['GET', 'POST'],
  allowedHeaders: ['Content-Type', 'X-API-Key']
}));
app.use(morgan('combined')); // Request logging
app.use(express.json()); // JSON body parsing

// Serve models directory as static files (read-only)
const modelsDir = path.join(__dirname, '..', process.env.MODELS_DIR || 'models');
app.use('/models', express.static(modelsDir, {
  setHeaders: (res, filePath) => {
    if (filePath.endsWith('.tflite')) {
      res.set('Content-Type', 'application/octet-stream');
      res.set('Cache-Control', 'public, max-age=3600'); // Cache for 1 hour
    }
  }
}));

// Mount routes
app.use('/', modelsRouter);

// 404 handler for undefined routes
app.use((req, res) => {
  res.status(404).json({
    error: 'Not Found',
    message: `Route ${req.method} ${req.originalUrl} not found`,
    timestamp: new Date().toISOString()
  });
});

// Global error handler
app.use((err, req, res, next) => {
  console.error('Error:', err);
  
  const statusCode = err.statusCode || 500;
  const message = err.message || 'Internal Server Error';
  
  res.status(statusCode).json({
    error: err.name || 'Error',
    message: message,
    timestamp: new Date().toISOString(),
    ...(NODE_ENV === 'development' && { stack: err.stack })
  });
});

// Start server
const server = app.listen(PORT, () => {
  console.log('='.repeat(50));
  console.log(`FreshFood Model Server`);
  console.log('='.repeat(50));
  console.log(`Environment: ${NODE_ENV}`);
  console.log(`Server listening on port: ${PORT}`);
  console.log(`Models directory: ${modelsDir}`);
  console.log(`Server URL: ${process.env.SERVER_URL || `http://localhost:${PORT}`}`);
  console.log('='.repeat(50));
});

// Graceful shutdown handler
const shutdown = (signal) => {
  console.log(`\n${signal} received. Shutting down gracefully...`);
  server.close(() => {
    console.log('Server closed. Exiting process.');
    process.exit(0);
  });
  
  // Force exit after 10 seconds if graceful shutdown fails
  setTimeout(() => {
    console.error('Forced shutdown after timeout.');
    process.exit(1);
  }, 10000);
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

module.exports = app;
