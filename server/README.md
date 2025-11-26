# FreshFood Model Server

Node.js/Express server for distributing TensorFlow Lite models to the FreshFood Android app via over-the-air (OTA) updates.

## Overview

This server provides a REST API for:
- **Static Model Distribution**: Serves pre-placed TFLite models to Android clients
- **Manifest Management**: Provides version information and SHA-256 hashes
- **Automatic Updates**: Android app can check for and download model updates

### Architecture

```
Internet → nginx (reverse proxy, SSL/TLS) → Express Server → Static Files (models/)
```

The server runs behind an nginx reverse proxy that handles:
- SSL/TLS termination
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
# Generate strong API key (32+ characters)
CLIENT_API_KEY=your-strong-client-key-here

# Set your production server URL
SERVER_URL=https://your-domain.com

# Configure CORS allowed origins
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com

# Adjust other settings as needed
PORT=3000
NODE_ENV=production
```

**Generate strong API key**:
```bash
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
| `CLIENT_API_KEY` | API key for client downloads (/manifest.json, /models/*) | - | **Yes** |
| `MODELS_DIR` | Models directory path | ./models | No |
| `MANIFEST_PATH` | Manifest file path | ./models/manifest.json | No |
| `ALLOWED_ORIGINS` | CORS allowed origins (comma-separated) | http://localhost:3000 | **Yes** (production) |
| `SERVER_URL` | Public server URL | http://localhost:3000 | **Yes** (production) |

### Security Considerations

#### Single-Tier API Key System

The server implements a single-tier authentication system for client access:

**CLIENT_API_KEY**: Used for `/manifest.json` and `/models/*` endpoints (model downloads)
- Used by Android app for fetching models
- Must match `MODEL_UPDATE_API_KEY` in Android app's BuildConfig
- Should be kept private
- Can be shared with all instances of the Android app

The key should be:
- Generated using `openssl rand -hex 32` (produces 64-character hex string)
- Stored in environment variables only
- Never committed to version control

#### CORS Configuration

Configure `ALLOWED_ORIGINS` to restrict which domains can access the API:
```env
ALLOWED_ORIGINS=https://your-domain.com,https://app.your-domain.com
```

For development, you can use `http://localhost:3000`, but **never use `*` in production**.

#### Additional Security Measures

1. **API Key Authentication**: All model endpoints require X-API-Key header
2. **HTTPS**: Always use HTTPS in production (configured via nginx)
3. **SHA-256 Verification**: Ensures model integrity (matches Android ModelDownloader.kt implementation)
4. **Nginx Header Passthrough**: X-API-Key header forwarded through reverse proxy
5. **Timing-Safe Comparison**: Prevents timing attacks on API key validation

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

## Model Management Process

### Overview

This server uses a **manual model management workflow**. Models are not uploaded via API endpoints. Instead, you place `.tflite` files directly in the `models/` directory and generate the manifest manually using a script.

### Step-by-Step Guide

#### 1. Prepare Model File

- Ensure file has `.tflite` extension
- Test model locally before deployment
- Note the model name (e.g., `fruitid.tflite`)

#### 2. Place Model in Directory

Copy or move your `.tflite` file to the server's `models/` directory:

```bash
# Local development
cp path/to/fruitid.tflite models/

# Production server (via SCP)
scp fruitid.tflite user@your-server:/path/to/server/models/
```

#### 3. Generate Manifest

Run the manifest generation script:

```bash
# Basic usage
node scripts/generate-manifest.js --server-url https://your-domain.com

# Increment version for existing models
node scripts/generate-manifest.js --server-url https://your-domain.com --increment-version

# Custom output path
node scripts/generate-manifest.js --server-url https://your-domain.com --output models/manifest.json
```

**Script Features**:
- Automatically scans `models/` directory for all `.tflite` files
- Calculates SHA-256 hash for each model
- Generates `manifest.json` with proper format matching `ModelManifestDto`
- Supports version increment (reads existing manifest and increments version if model exists)
- Provides colored output with detailed progress information

**Example Output**:
```
========================================
   TFLite Manifest Generator
========================================

✔ Step 1/6: Validating server URL...
✔ Step 2/6: Checking models directory...
✔ Step 3/6: Scanning for .tflite files...
  Found 1 model(s) to process

✔ Step 4/6: Reading existing manifest...
✔ Step 5/6: Generating manifest entries...
  Processing: fruitid.tflite
✔ Step 6/6: Writing manifest to models/manifest.json...

========================================
Manifest generated successfully!
========================================

Summary:
- Total models: 1
- Models: fruitid (v2, SHA256: a1b2c3d4...)

Next steps:
1. Verify manifest: node scripts/verify-manifest.js
2. Test download: curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json
3. Restart server if running: pm2 restart freshfood-server
```

#### 4. Verify Manifest

Use the verification script to ensure manifest is valid:

```bash
node scripts/verify-manifest.js
```

This script:
- Validates JSON structure
- Checks that all models in manifest actually exist in `models/` directory
- Verifies SHA-256 hashes match file contents
- Reports any discrepancies

#### 5. Restart Server (if running)

If server is already running, restart it to ensure manifest changes are picked up:

```bash
# Using PM2
pm2 restart freshfood-server

# Or if running directly
npm start
```

#### 6. Test Deployment

Verify the manifest is accessible:

```bash
curl -H "X-API-Key: your-client-api-key" https://your-domain.com/manifest.json
```

Download the model to test:

```bash
curl -H "X-API-Key: your-client-api-key" -O https://your-domain.com/models/fruitid.tflite
```

### Version Management

**New Models**: When adding a brand new model, the script sets version to `1` by default.

**Updating Existing Models**: When replacing an existing model with a new version:

1. Replace the `.tflite` file in `models/` directory with the new version
2. Run the script with `--increment-version` flag:
   ```bash
   node scripts/generate-manifest.js --server-url https://your-domain.com --increment-version
   ```
3. The script will:
   - Read the existing manifest
   - Detect that the model name already exists
   - Increment the version number (e.g., v2 → v3)
   - Calculate new SHA-256 hash
   - Update the manifest

### Android App Update Flow

Once you've updated the manifest:

1. **Android app periodically checks** `/manifest.json` endpoint
2. **Compares versions**: If server version > local version, update is triggered
3. **Downloads model**: Fetches the new `.tflite` file from `/models/:modelName.tflite`
4. **Verifies integrity**: Calculates SHA-256 of downloaded file and compares with manifest
5. **Loads model**: If hash matches, replaces old model with new one

### Adding New Models

To add a completely new model (not an update to existing):

```bash
# 1. Place the new model file
cp path/to/newmodel.tflite models/

# 2. Generate manifest (no --increment-version needed for new models)
node scripts/generate-manifest.js --server-url https://your-domain.com

# 3. Verify
node scripts/verify-manifest.js

# 4. Restart server
pm2 restart freshfood-server
```

The manifest will now include both old and new models:

```json
{
  "models": [
    {
      "name": "fruitid",
      "version": 2,
      "downloadUrl": "https://your-domain.com/models/fruitid.tflite",
      "sha256": "abc123..."
    },
    {
      "name": "newmodel",
      "version": 1,
      "downloadUrl": "https://your-domain.com/models/newmodel.tflite",
      "sha256": "def456..."
    }
  ]
}
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

   **Critical**: Set CLIENT_API_KEY in .env:
   ```bash
   # Generate API key
   openssl rand -hex 32  # For CLIENT_API_KEY
   ```

   Update .env:
   ```env
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

8. **Place Initial Models**
   ```bash
   # Copy your .tflite files to models/ directory
   cp path/to/fruitid.tflite models/
   
   # Generate manifest
   node scripts/generate-manifest.js --server-url https://your-domain.com
   
   # Verify manifest
   node scripts/verify-manifest.js
   ```

9. **Test Deployment**
   ```bash
   curl -H "X-API-Key: your-client-api-key" https://your-domain.com/manifest.json
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

# Windows PowerShell
Get-FileHash models/fruitid.tflite -Algorithm SHA256
```

Compare with manifest.json entry.

**Regenerate manifest**:
```bash
# Regenerate from scratch
node scripts/generate-manifest.js --server-url https://your-domain.com

# Verify correctness
node scripts/verify-manifest.js
```

**Check file corruption**:
If hash still doesn't match, the file may be corrupted. Replace the `.tflite` file and regenerate manifest.

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

To rotate CLIENT_API_KEY:

1. Generate new key: `openssl rand -hex 32`
2. Update server `.env` file with new CLIENT_API_KEY
3. Restart server: `pm2 restart freshfood-server`
4. Update CLIENT_API_KEY in Android app (`gradle.properties` or `local.properties`)
5. Rebuild and redeploy Android app

**Note**: All Android app users will need to update to the new app version to continue receiving model updates.

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

After removing old models, regenerate the manifest:
```bash
node scripts/generate-manifest.js --server-url https://your-domain.com
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

The system supports unlimited concurrent users (limited only by server resources and network bandwidth):
- No per-API-key or per-IP rate limits
- All users share the same CLIENT_API_KEY embedded in the APK
- Scale horizontally with load balancer for higher traffic
- Monitor server CPU, memory, and network usage to determine capacity

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

4. **Input Validation**
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
