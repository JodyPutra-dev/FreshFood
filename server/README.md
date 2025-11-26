# FreshFood Model Server

Node.js/Express server for distributing TensorFlow Lite models to the FreshFood Android app via over-the-air (OTA) updates.

## Overview

This server provides a REST API for:
- **Model Distribution**: Serves TFLite models to Android clients
- **Manifest Management**: Provides version information and SHA-256 hashes
- **Admin Uploads**: Secure endpoint for uploading new model versions
- **Automatic Updates**: Android app can check for and download model updates

### Architecture

```
Internet → nginx (reverse proxy, SSL/TLS) → Express Server → File System
```

The server runs behind an nginx reverse proxy that handles:
- SSL/TLS termination
- Rate limiting
- Security headers
- Static file optimization

## Documentation

For comprehensive guides on different aspects of the FreshFood system:

| Document | Description |
|----------|-------------|
| [Main README](../README.md) | Project overview, architecture diagram, and quick start guide |
| [Android Testing Guide](../docs/ANDROID_TESTING.md) | How to test OTA updates in the Android app |
| [Troubleshooting Guide](../docs/TROUBLESHOOTING.md) | Consolidated troubleshooting for Android and server issues |
| [Deployment Guide](../docs/DEPLOYMENT.md) | Full production deployment guide for server and Android app |
| [Nginx Configuration](nginx/README.md) | nginx setup, SSL/TLS, and security headers |

**Note**: This document focuses on server-specific setup and configuration. For Android app integration, testing procedures, and production deployment, see the guides above.

## Prerequisites

- **Node.js**: 18.x or higher
- **npm**: 9.x or higher
- **nginx**: For production deployment (optional for development)
- **SSL Certificate**: Let's Encrypt recommended (for production)
- **Operating System**: Linux, macOS, or Windows

## Installation

### 1. Navigate to Server Directory

```bash
cd server
```

### 2. Install Dependencies

```bash
npm install
```

This will install all required packages:
- express (web framework)
- multer (file upload handling)
- express-rate-limit (rate limiting)
- helmet (security headers)
- cors (cross-origin resource sharing)
- morgan (HTTP request logging)
- dotenv (environment variables)

### 3. Create Environment Configuration

```bash
cp .env.example .env
```

### 4. Edit Environment Variables

Open `.env` and configure:

```bash
nano .env  # or use your preferred editor
```

**Important**: Update the following variables:

```env
# Generate strong API keys (32+ characters each)
ADMIN_API_KEY=your-strong-admin-key-here
CLIENT_API_KEY=your-strong-client-key-here

# Set your production server URL
SERVER_URL=https://your-domain.com

# Configure CORS allowed origins
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com

# Adjust other settings as needed
PORT=3000
NODE_ENV=production
```

**Generate strong API keys** (generate separate keys for admin and client):
```bash
openssl rand -hex 32  # For ADMIN_API_KEY
openssl rand -hex 32  # For CLIENT_API_KEY (must match Android app configuration)
```

### 5. Create Models Directory (if not exists)

```bash
mkdir -p models
```

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `PORT` | Server port | 3000 | No |
| `NODE_ENV` | Environment mode (development/production) | development | No |
| `ADMIN_API_KEY` | Secret key for admin uploads (/admin/*) | - | **Yes** |
| `CLIENT_API_KEY` | API key for client downloads (/manifest.json, /models/*) | - | **Yes** |
| `MODELS_DIR` | Models directory path | ./models | No |
| `MANIFEST_PATH` | Manifest file path | ./models/manifest.json | No |
| `MAX_FILE_SIZE_MB` | Max upload size in MB | 100 | No |
| `RATE_LIMIT_WINDOW_MS` | Rate limit window (ms) | 900000 (15 min) | No |
| `RATE_LIMIT_MAX_REQUESTS` | Max requests per window | 100 | No |
| `ALLOWED_ORIGINS` | CORS allowed origins (comma-separated) | http://localhost:3000 | **Yes** (production) |
| `SERVER_URL` | Public server URL | http://localhost:3000 | **Yes** (production) |

### Security Considerations

#### Two-Tier API Key System

The server implements a two-tier authentication system for enhanced security:

1. **ADMIN_API_KEY**: Used for `/admin/upload` endpoint (model uploads)
   - Highly sensitive - grants ability to upload/modify models
   - Should be kept extremely secure and rotated regularly
   - Only known to administrators and CI/CD systems

2. **CLIENT_API_KEY**: Used for `/manifest.json` and `/models/*` endpoints (model downloads)
   - Used by Android app for fetching models
   - Must match `MODEL_UPDATE_API_KEY` in Android app's BuildConfig
   - Less sensitive than admin key but should still be kept private
   - Can be shared with all instances of the Android app

Both keys should be:
- Generated using `openssl rand -hex 32` (produces 64-character hex string)
- Stored in environment variables only
- Never committed to version control
- Different from each other

#### CORS Configuration

Configure `ALLOWED_ORIGINS` to restrict which domains can access the API:
```env
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com
```

For development, you can use `http://localhost:3000`, but **never use `*` in production**.

#### Additional Security Measures

1. **API Key Authentication**: All model endpoints require X-API-Key header
2. **HTTPS**: Always use HTTPS in production (configured via nginx)
3. **Rate Limiting**: 
   - Per-API-key: 50 requests per 15 minutes
   - Per-IP: 100 requests per 15 minutes
4. **File Size Limits**: Prevents disk exhaustion
5. **SHA-256 Verification**: Ensures model integrity (matches Android ModelDownloader.kt implementation)
6. **Nginx Header Passthrough**: X-API-Key header forwarded through reverse proxy
7. **Timing-Safe Comparison**: Prevents timing attacks on API key validation

## Running the Server

### Development Mode

Uses nodemon for automatic restart on code changes:

```bash
npm run dev
```

### Production Mode

```bash
npm start
```

### Using PM2 (Recommended for Production)

Install PM2 globally:
```bash
npm install -g pm2
```

Start server:
```bash
pm2 start src/index.js --name freshfood-server
```

Save PM2 configuration:
```bash
pm2 save
```

Enable startup on boot:
```bash
pm2 startup
```

Monitor logs:
```bash
pm2 logs freshfood-server
```

Stop server:
```bash
pm2 stop freshfood-server
```

## API Endpoints

### Public Endpoints (Require Client API Key)

All public endpoints require authentication via the `X-API-Key` header with the `CLIENT_API_KEY` value.

#### GET /manifest.json

Returns the model manifest matching `ModelManifestDto` structure from the Android app.

**Authentication**: Required (X-API-Key header with CLIENT_API_KEY)

**Response Format**:
```json
{
  "models": [
    {
      "name": "fruitid",
      "version": 2,
      "downloadUrl": "https://your-domain.com/models/fruitid.tflite",
      "sha256": "abc123def456..."
    }
  ]
}
```

**Cache**: 5 minutes (max-age=300)

**Example Request**:
```bash
curl -H "X-API-Key: your-client-api-key" https://your-domain.com/manifest.json
```

#### GET /models/:modelName.tflite

Downloads a specific TFLite model file.

**Authentication**: Required (X-API-Key header with CLIENT_API_KEY)

**Parameters**:
- `modelName`: Model name (alphanumeric, underscores, hyphens only)

**Headers**:
- `Content-Type`: application/octet-stream
- `Content-Disposition`: attachment; filename="{modelName}.tflite"
- `ETag`: For caching
- `Cache-Control`: public, max-age=86400 (1 day)

**Example Request**:
```bash
curl -H "X-API-Key: your-client-api-key" -O https://your-domain.com/models/fruitid.tflite
```

**Rate Limiting**: 
- Per API key: 50 requests per 15 minutes
- Per IP: 100 requests per 15 minutes (secondary defense)

### Admin Endpoints (Require Admin API Key)

#### POST /admin/upload

Upload a new model version. Requires authentication via X-API-Key header with ADMIN_API_KEY.

**Authentication**: X-API-Key header with ADMIN_API_KEY

**Request**:
- Method: POST
- Content-Type: multipart/form-data
- Body: Form field `file` with .tflite file

**Process**:
1. Validates authentication
2. Validates file (must be .tflite)
3. Saves file to models directory
4. Computes SHA-256 hash
5. Updates manifest.json:
   - If model exists: increments version
   - If new model: sets version to 1
6. Returns upload status

**Response**:
```json
{
  "success": true,
  "message": "Model uploaded successfully",
  "model": {
    "name": "fruitid",
    "version": 3,
    "sha256": "abc123def456...",
    "downloadUrl": "https://your-domain.com/models/fruitid.tflite",
    "fileSize": 5242880
  },
  "timestamp": "2025-11-26T10:30:00.000Z"
}
```

**Example Request**:
```bash
curl -X POST \
  -H "X-API-Key: your-api-key-here" \
  -F "file=@fruitid.tflite" \
  https://your-domain.com/admin/upload
```

**Error Responses**:
- `400 Bad Request`: Invalid file or missing file
- `401 Unauthorized`: Missing or invalid API key
- `413 Payload Too Large`: File exceeds size limit
- `500 Internal Server Error`: Server error

## Model Upload Process

### Step-by-Step Guide

1. **Prepare Model File**
   - Ensure file has `.tflite` extension
   - Verify file size is within limits (default: 100MB)
   - Test model locally before uploading

2. **Upload Model**
   ```bash
   curl -X POST \
     -H "X-API-Key: your-api-key" \
     -F "file=@path/to/model.tflite" \
     https://your-domain.com/admin/upload
   ```

3. **Server Actions**
   - Computes SHA-256 hash of uploaded file
   - Updates or creates model entry in manifest.json
   - Increments version number automatically

4. **Android App Update**
   - App periodically checks manifest.json
   - Detects new version
   - Downloads model if SHA-256 differs
   - Verifies integrity before loading

### Using the Upload Script

A convenience script is provided:

```bash
./scripts/upload-model.sh path/to/model.tflite https://your-domain.com your-api-key
```

## Deployment

### Development Deployment

1. Install dependencies: `npm install`
2. Configure `.env` file
3. Run server: `npm run dev`
4. Test endpoints: `curl http://localhost:3000/manifest.json`

### Production Deployment

1. **Set Up Server**
   - Ubuntu/Debian VPS or cloud instance
   - 1GB+ RAM, 10GB+ disk recommended
   - Open ports 80 and 443

2. **Install Node.js**
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt install -y nodejs
   ```

3. **Install nginx**
   ```bash
   sudo apt install nginx
   ```

4. **Configure SSL Certificate**
   - See `nginx/README.md` for detailed instructions
   - Use Let's Encrypt (free, automatic renewal)

5. **Deploy Server Code**
   ```bash
   git clone <your-repo>
   cd server
   npm install --production
   cp .env.example .env
   nano .env  # Configure production settings
   ```

   **Critical**: Set both API keys in .env:
   ```bash
   # Generate two separate keys
   openssl rand -hex 32  # For ADMIN_API_KEY
   openssl rand -hex 32  # For CLIENT_API_KEY
   ```

   Update .env:
   ```env
   ADMIN_API_KEY=<generated-admin-key>
   CLIENT_API_KEY=<generated-client-key>
   ALLOWED_ORIGINS=https://your-domain.com
   SERVER_URL=https://your-domain.com
   ```

   **Important**: Save the CLIENT_API_KEY - you'll need to configure it in the Android app's `gradle.properties` or `local.properties`:
   ```properties
   modelUpdateApiKey=<same-client-key-from-server>
   ```

6. **Configure nginx**
   - See `nginx/README.md` for configuration steps
   - Update domain name and certificate paths
   - Enable and reload nginx

7. **Start Server with PM2**
   ```bash
   npm install -g pm2
   pm2 start src/index.js --name freshfood-server
   pm2 save
   pm2 startup
   ```

8. **Initialize Models**
   ```bash
   ./scripts/init-models.sh
   ```

9. **Test Deployment**
   ```bash
   curl https://your-domain.com/manifest.json
   curl https://your-domain.com/health
   ```

### Docker Deployment (Optional)

Create `Dockerfile`:
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --production
COPY . .
EXPOSE 3000
CMD ["node", "src/index.js"]
```

Build and run:
```bash
docker build -t freshfood-server .
docker run -d -p 3000:3000 --env-file .env freshfood-server
```

## Monitoring and Logs

### Application Logs

**Development** (stdout):
```bash
npm run dev
```

**Production with PM2**:
```bash
pm2 logs freshfood-server
pm2 logs freshfood-server --lines 100
pm2 logs freshfood-server --err  # Errors only
```

### Nginx Logs

**Access logs**:
```bash
sudo tail -f /var/log/nginx/freshfood_access.log
```

**Error logs**:
```bash
sudo tail -f /var/log/nginx/freshfood_error.log
```

### Monitoring Disk Space

Models directory can grow over time:
```bash
du -sh models/
```

Clean up old versions if needed:
```bash
# List model files
ls -lh models/*.tflite
```

### Health Checks

Test server health:
```bash
curl https://your-domain.com/health
```

### Monitoring Tools (Optional)

- **Prometheus + Grafana**: Metrics and dashboards
- **Uptime Robot**: Uptime monitoring
- **Logstash/ELK**: Log aggregation and analysis

## Troubleshooting

### Server Won't Start

**Check port availability**:
```bash
# Linux/macOS
lsof -i :3000

# Windows
netstat -ano | findstr :3000
```

**Verify environment variables**:
```bash
cat .env
```

**Check Node.js version**:
```bash
node --version  # Should be 18.x or higher
```

### Upload Fails

**Check file size**:
```bash
ls -lh model.tflite
```

Ensure it's under the `MAX_FILE_SIZE_MB` limit.

**Verify API key**:
```bash
echo $ADMIN_API_KEY  # Should match .env file
```

**Check disk space**:
```bash
df -h
```

**Check permissions**:
```bash
ls -la models/
# Ensure Node.js process can write to models directory
```

### Android App Can't Fetch Manifest

**Test manifest endpoint with API key**:
```bash
curl -H "X-API-Key: your-client-api-key" https://your-domain.com/manifest.json
```

If this returns 401 Unauthorized, check:
1. **API key mismatch**: Verify CLIENT_API_KEY in server .env matches MODEL_UPDATE_API_KEY in Android app
2. **Nginx header passthrough**: Ensure nginx.conf includes `proxy_set_header X-API-Key $http_x_api_key;`
3. **API key format**: Key must be at least 16 characters

**Check nginx configuration**:
```bash
sudo nginx -t
sudo systemctl status nginx
```

**Verify SSL certificate**:
```bash
curl -v https://your-domain.com/manifest.json
# Should show valid SSL certificate
```

**Check CORS settings**:
Verify ALLOWED_ORIGINS in .env matches your app's domain:
```bash
# In .env
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com
```

**Test nginx header forwarding**:
```bash
# Test that X-API-Key header reaches the Express server
curl -H "X-API-Key: test-key" -v https://your-domain.com/manifest.json
# Check nginx logs to verify header is being forwarded
```

### 401 Unauthorized Errors

If Android app receives 401 errors when fetching models:

1. **Verify API keys match**:
   - Server: Check `CLIENT_API_KEY` in `.env`
   - Android: Check `MODEL_UPDATE_API_KEY` in `gradle.properties` or `local.properties`
   - Both must be identical

2. **Check BuildConfig generation**:
   ```bash
   # In Android Studio, rebuild project to regenerate BuildConfig
   ./gradlew clean build
   ```

3. **Verify header in OkHttp requests**:
   - Ensure `ApiKeyInterceptor` is added to OkHttpClient
   - Check that `BuildConfig.MODEL_UPDATE_API_KEY` is not empty

4. **Test manually**:
   ```bash
   # Use the same API key configured in Android app
   curl -H "X-API-Key: your-client-api-key" https://your-domain.com/manifest.json
   ```

5. **Check server logs**:
   ```bash
   pm2 logs freshfood-server
   # Look for "Client authentication failed" messages
   ```

### SHA-256 Mismatch

**Verify manifest hash**:
```bash
# Linux/macOS
sha256sum models/fruitid.tflite

# macOS alternative
shasum -a 256 models/fruitid.tflite
```

Compare with manifest.json entry.

**Regenerate manifest**:
```bash
node scripts/verify-manifest.js --fix
```

**Check file corruption**:
Re-upload the model file if hash doesn't match.

### Rate Limit Errors (429)

**Increase rate limits** in `.env`:
```env
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=200  # Increase from 100
```

Restart server after changes.

**Check client behavior**:
Ensure Android app doesn't poll too frequently (recommended: hourly or on app start).

## Maintenance

### Update Dependencies

Check for updates:
```bash
npm outdated
```

Update packages:
```bash
npm update
```

Security audit:
```bash
npm audit
npm audit fix
```

### Rotate API Key

1. Generate new key: `openssl rand -hex 32`
2. Update `.env` file
3. Restart server: `pm2 restart freshfood-server`
4. Update API key in upload scripts/tools

### Backup Manifest

```bash
# Manual backup
cp models/manifest.json models/manifest.json.backup

# Automated daily backup (cron)
0 2 * * * cp /path/to/server/models/manifest.json /path/to/backups/manifest-$(date +\%Y\%m\%d).json
```

### Clean Up Old Models

If you have multiple versions:
```bash
# List all models
ls -lt models/*.tflite

# Remove specific old version
rm models/fruitid_v1.tflite
```

**Note**: Only the latest version needs to be kept unless you support rollbacks.

### Log Rotation

PM2 handles log rotation automatically. Configure in PM2 ecosystem file:

```javascript
// ecosystem.config.js
module.exports = {
  apps: [{
    name: 'freshfood-server',
    script: './src/index.js',
    error_file: './logs/err.log',
    out_file: './logs/out.log',
    log_date_format: 'YYYY-MM-DD HH:mm:ss',
    max_size: '10M',
    max_files: 10
  }]
};
```

Start with ecosystem file:
```bash
pm2 start ecosystem.config.js
```

## Integration with Android App

### Update Check Interval

The Android app checks for updates based on `ModelUpdateService.kt`. Recommended intervals:
- **Development**: Every app start
- **Production**: Every 6-24 hours
- **Background**: Using WorkManager with network constraints

### Manifest URL Configuration

Update in Android app's network configuration or BuildConfig:

```kotlin
// Example configuration
object ServerConfig {
    const val MANIFEST_URL = "https://your-domain.com/manifest.json"
    const val BASE_URL = "https://your-domain.com"
}
```

### SHA-256 Verification

The server generates SHA-256 hashes that match the format expected by `ModelDownloader.kt`:
- Lowercase hexadecimal string
- 64 characters (256 bits / 4 bits per hex char)

Example: `a1b2c3d4e5f6...` (64 chars total)

## Performance Benchmarks

### Server Performance

- **Manifest requests**: <50ms response time
- **Model downloads**: Limited by network bandwidth
- **Upload processing**: ~1-2 seconds for 5MB model (includes SHA-256 computation)

### Recommended Specifications

**Minimum**:
- 1 vCPU
- 1GB RAM
- 20GB SSD
- 100Mbps network

**Recommended** (for production):
- 2 vCPUs
- 2GB RAM
- 50GB SSD
- 1Gbps network

### Concurrent Users

With default rate limiting (100 req/15min):
- ~400 users per hour per IP
- Scale up rate limits or use load balancer for higher traffic

## Security Considerations

1. **API Key Security**
   - Store in environment variables only
   - Never commit to version control
   - Rotate regularly (monthly or after any suspected compromise)
   - Use strong random keys (32+ characters)

2. **HTTPS Only**
   - Never serve models over HTTP in production
   - Enforced by nginx configuration
   - Validates certificates

3. **SHA-256 Verification**
   - Every model download is verified by Android app
   - Prevents man-in-the-middle attacks
   - Ensures model integrity

4. **Rate Limiting**
   - Prevents abuse and DoS attacks
   - Configurable per endpoint
   - Monitor for unusual patterns

5. **File Size Limits**
   - Prevents disk exhaustion
   - Default: 100MB per upload
   - Adjust based on model sizes

6. **Input Validation**
   - Model names sanitized (alphanumeric, underscores, hyphens)
   - File extensions validated
   - Prevents directory traversal attacks

## References

- [TensorFlow Lite Guide](https://www.tensorflow.org/lite/guide)
- [Express.js Documentation](https://expressjs.com/)
- [Nginx Documentation](https://nginx.org/en/docs/)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [PM2 Documentation](https://pm2.keymetrics.io/docs/)
- [Node.js Best Practices](https://github.com/goldbergyoni/nodebestpractices)

## License

See LICENSE file in the root directory.

## Support

For issues or questions:
1. Check this README and `nginx/README.md`
2. Review logs (application and nginx)
3. Verify configuration files
4. Test endpoints with curl
5. Check Android app integration

For deployment assistance, consult your DevOps team or cloud provider documentation.
