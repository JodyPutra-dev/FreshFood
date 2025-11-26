# Troubleshooting Guide

## Introduction

This guide provides solutions for common issues in the FreshFood OTA (Over-The-Air) model update system. It covers Android app issues, server issues, network issues, and configuration problems.

## Table of Contents

- [Android App Issues](#android-app-issues)
- [Server Issues](#server-issues)
- [Network Issues](#network-issues)
- [Configuration Issues](#configuration-issues)
- [Debugging Tips](#debugging-tips)
- [Getting Help](#getting-help)

## Android App Issues

### Issue: "Check for Updates" Button Does Nothing

**Symptoms**:
- Button tap has no visible effect
- No progress bar appears
- No status message displayed
- App doesn't crash

**Possible Causes**:
- Network unavailable
- Server unreachable
- API key missing or invalid
- Silent crash in update code

**Solutions**:

1. **Check logcat for errors**:
   ```bash
   adb logcat | grep -E "ModelUpdateManager|SettingsFragment"
   ```
   Look for error messages or exceptions.

2. **Verify network connectivity**:
   - Ensure WiFi or cellular data enabled
   - Check airplane mode is OFF
   - Test internet access in browser/other apps

3. **Test server endpoint manually**:
   ```bash
   curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json
   ```
   Should return JSON with models array.

4. **Verify API key in BuildConfig**:
   - Check `local.properties` has `modelUpdateApiKey` set
   - Ensure value matches server's `CLIENT_API_KEY`

5. **Rebuild project to regenerate BuildConfig**:
   ```bash
   ./gradlew clean build
   ```
   Then reinstall: `./gradlew installDebug`

6. **Check for crashes**:
   ```bash
   adb logcat | grep AndroidRuntime
   ```
   Look for "FATAL EXCEPTION" messages.

---

### Issue: "Error: Failed to fetch manifest"

**Symptoms**:
- Error message displayed in SettingsFragment after tapping "Check for Updates"
- Update process stops immediately

**Possible Causes**:
- Server URL incorrect
- Server not running
- Network timeout
- CORS/SSL issues
- DNS resolution failure

**Solutions**:

1. **Verify server URL in `local.properties`**:
   - For emulator: `modelUpdateBaseUrl=http://10.0.2.2:3000/models/`
   - For device (local): `modelUpdateBaseUrl=http://192.168.x.x:3000/models/`
   - For production: `modelUpdateBaseUrl=https://your-domain.com/models/`

2. **Check server is running**:
   ```bash
   curl https://your-domain.com/health
   ```
   Should return `{"status":"ok","uptime":123}`

3. **Test manifest endpoint directly**:
   ```bash
   curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json
   ```

4. **Check server logs**:
   ```bash
   # If using PM2
   pm2 logs freshfood-server

   # If running with npm
   # Check terminal where `npm run dev` is running
   ```

5. **Verify SSL certificate valid (for HTTPS)**:
   ```bash
   openssl s_client -connect your-domain.com:443
   ```
   Look for "Verify return code: 0 (ok)"

6. **Check timeout settings**:
   - `ModelUpdateService.kt` has 30s connect timeout, 60s read timeout
   - For slow connections, these may need adjustment

7. **Test from device network**:
   ```bash
   # Install terminal app on device or use ADB
   adb shell curl http://10.0.2.2:3000/health
   ```

---

### Issue: "Error: HTTP 401 Unauthorized"

**Symptoms**:
- 401 error when checking for updates or downloading models
- Authentication failure message

**Possible Causes**:
- API key mismatch between app and server
- API key not set on server or app
- nginx not forwarding X-API-Key header
- API key contains extra spaces/newlines

**Solutions**:

1. **Verify CLIENT_API_KEY on server matches MODEL_UPDATE_API_KEY in app**:
   
   **Server side**:
   ```bash
   cat server/.env | grep CLIENT_API_KEY
   ```
   
   **Android side**:
   ```bash
   # Check local.properties
   cat local.properties | grep modelUpdateApiKey
   
   # Or check gradle.properties
   cat gradle.properties | grep modelUpdateApiKey
   ```

2. **Ensure keys are identical**:
   - Case-sensitive (e.g., "AbC123" ≠ "abc123")
   - No extra spaces or quotes
   - Same character length

3. **Rebuild Android app after changing API key**:
   ```bash
   ./gradlew clean build
   ./gradlew installDebug
   ```

4. **Restart server after changing .env**:
   ```bash
   pm2 restart freshfood-server
   
   # Or if running with npm
   # Stop with Ctrl+C and restart: npm run dev
   ```

5. **Verify nginx forwards X-API-Key header**:
   
   Check `server/nginx/nginx.conf` contains:
   ```nginx
   proxy_set_header X-API-Key $http_x_api_key;
   ```
   
   Should be in both `location /` and `location /models/` blocks.

6. **Test API key manually**:
   ```bash
   curl -v -H "X-API-Key: your-key" https://your-domain.com/manifest.json
   ```
   
   Check request headers include `X-API-Key`, response is 200 (not 401).

7. **Check server logs for authentication messages**:
   ```bash
   pm2 logs freshfood-server | grep -E "authentication|401"
   ```
   
   Look for "Client authentication failed" or "Invalid API key" messages.

8. **Verify API key length**:
   - Should be at least 16 characters
   - Recommended: 64 characters (generated with `openssl rand -hex 32`)

---

### Issue: "SHA256 mismatch for [model]"

**Symptoms**:
- Download completes but verification fails
- Model not loaded in app
- Previous version retained

**Possible Causes**:
- Model file corrupted during upload to server
- Model file corrupted during download
- manifest.json hash incorrect
- Network corruption (rare)

**Solutions**:

1. **Verify model file integrity on server**:
   
   **Linux/Mac**:
   ```bash
   sha256sum server/models/fruitid.tflite
   ```
   
   **Windows**:
   ```bash
   certutil -hashfile server\models\fruitid.tflite SHA256
   ```

2. **Compare with manifest.json hash**:
   ```bash
   cat server/models/manifest.json | jq '.models[] | select(.name=="fruitid") | .sha256'
   ```
   
   Should match the hash from step 1.

3. **If hashes don't match, regenerate manifest**:
   ```bash
   cd server
   node scripts/verify-manifest.js --fix
   ```

4. **Re-upload model to server**:
   ```bash
   cd server
   ./scripts/upload-model.sh path/to/model.tflite https://your-domain.com $ADMIN_API_KEY
   ```

5. **Clear app cache and retry**:
   ```bash
   adb shell pm clear com.jody.freshfood
   ```
   Then reinstall and test update.

6. **Check for network corruption**:
   - Test download on different network (WiFi vs cellular)
   - Try from different device
   - Check router/firewall not modifying traffic

7. **Verify model file is valid TFLite**:
   ```bash
   file server/models/fruitid.tflite
   ```
   Should show "data" or binary file (not text/HTML).

---

### Issue: Model Version Doesn't Update After Successful Download

**Symptoms**:
- "Successfully updated X model(s)" message shown
- Version number unchanged in Settings
- SHA-256 unchanged

**Possible Causes**:
- Metadata not saved to SharedPreferences
- UI not refreshing
- Version number not incremented on server
- App cached old metadata

**Solutions**:

1. **Check SharedPreferences**:
   ```bash
   adb shell run-as com.jody.freshfood cat shared_prefs/freshfood_models.xml
   ```
   
   Look for `<int name="fruitid_version" value="2" />` entries.

2. **Verify metadata update code**:
   - Check `ModelUpdateManager.kt` calls `ModelManager.updateModelMetadata`
   - Check `ModelManager.kt` saves to SharedPreferences

3. **Check manifest.json version on server**:
   ```bash
   curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json
   ```
   
   Verify `version` field incremented (e.g., 2 instead of 1).

4. **Ensure server increments version on upload**:
   - Check `server/src/utils/manifest.js` increments version
   - Manually verify manifest after upload

5. **Force UI refresh**:
   - Navigate to different tab
   - Return to Settings tab
   - Version should refresh

6. **Restart app and check version**:
   - Force stop app: `adb shell am force-stop com.jody.freshfood`
   - Relaunch app
   - Check Settings tab

7. **Check for file write permissions**:
   ```bash
   adb shell run-as com.jody.freshfood ls -la shared_prefs/
   ```
   
   Ensure `freshfood_models.xml` is writable.

---

### Issue: App Crashes When Checking for Updates

**Symptoms**:
- App force closes when tapping "Check for Updates"
- "Unfortunately, FreshFood has stopped" dialog

**Possible Causes**:
- Null pointer exception
- Network error not handled
- Coroutine crash
- Missing permissions

**Solutions**:

1. **Check crash logs**:
   ```bash
   adb logcat | grep AndroidRuntime
   ```
   
   Look for stack trace starting with "FATAL EXCEPTION".

2. **Verify all nullable types handled**:
   - Check `ModelUpdateManager.kt` and `ModelDownloader.kt`
   - Ensure null checks for response bodies, URLs, etc.

3. **Check for missing permissions**:
   - Verify `AndroidManifest.xml` has `<uses-permission android:name="android.permission.INTERNET" />`
   - Check device settings allow internet access for app

4. **Test with try-catch**:
   - Temporarily wrap update code in try-catch
   - Log exception details

5. **Update dependencies**:
   ```bash
   # Check for outdated libraries
   ./gradlew dependencyUpdates
   ```
   
   Update versions in `libs.versions.toml`.

6. **Check coroutine scope**:
   - Ensure coroutines launched in correct scope (viewModelScope, lifecycleScope)
   - Check for cancelled coroutines

7. **Test on different Android version**:
   - Issue may be API-level specific
   - Test on API 24, 28, 33, 36

---

## Server Issues

### Issue: Server Won't Start

**Symptoms**:
- `npm start` or `pm2 start` fails
- Error: "Address already in use" or "EADDRINUSE"
- Server crashes immediately after start

**Possible Causes**:
- Port 3000 already in use
- Node.js version incompatible
- Missing dependencies
- Syntax errors in code
- .env file missing or invalid

**Solutions**:

1. **Check if port already in use**:
   
   **Linux/Mac**:
   ```bash
   lsof -i :3000
   ```
   
   **Windows**:
   ```bash
   netstat -ano | findstr :3000
   ```

2. **Kill process using port**:
   
   **Linux/Mac**:
   ```bash
   kill -9 <PID>
   ```
   
   **Windows**:
   ```bash
   taskkill /PID <PID> /F
   ```
   
   Or change PORT in `.env` to different value (e.g., 3001).

3. **Verify Node.js version**:
   ```bash
   node --version
   ```
   
   Should be 18.x or higher. Install compatible version if needed.

4. **Check .env file exists and is valid**:
   ```bash
   cat server/.env
   ```
   
   Should have required variables: `PORT`, `NODE_ENV`, `ADMIN_API_KEY`, `CLIENT_API_KEY`, etc.

5. **Install dependencies**:
   ```bash
   cd server
   npm install
   ```

6. **Check for syntax errors**:
   ```bash
   node src/index.js
   ```
   
   Run directly to see error messages.

7. **Check PM2 logs**:
   ```bash
   pm2 logs freshfood-server --err
   ```

---

### Issue: Model Upload Fails (POST /admin/upload)

**Symptoms**:
- 400 or 500 error when uploading model
- "Upload failed" message
- File not appearing in `server/models/` directory

**Possible Causes**:
- File is not .tflite format
- File exceeds size limit
- Wrong API key (CLIENT_API_KEY instead of ADMIN_API_KEY)
- Disk space full
- Directory not writable

**Solutions**:

1. **Verify file is .tflite**:
   ```bash
   file model.tflite
   ```
   
   Should show "data" or binary file format.

2. **Check file size under limit**:
   ```bash
   ls -lh model.tflite
   ```
   
   Default max: 100MB (configurable via `MAX_FILE_SIZE_MB` in `.env`).

3. **Verify using ADMIN_API_KEY (not CLIENT_API_KEY)**:
   ```bash
   curl -X POST \
     -H "X-API-Key: $ADMIN_API_KEY" \
     -F "file=@model.tflite" \
     https://your-domain.com/admin/upload
   ```

4. **Check disk space**:
   ```bash
   df -h
   ```
   
   Ensure sufficient space in server directory.

5. **Verify models directory writable**:
   ```bash
   ls -la server/models/
   ```
   
   Check permissions allow write access.

6. **Check server error logs**:
   ```bash
   pm2 logs freshfood-server --err
   ```
   
   Look for multer errors, file system errors, or validation failures.

7. **Test with curl verbose**:
   ```bash
   curl -v -X POST \
     -H "X-API-Key: $ADMIN_API_KEY" \
     -F "file=@model.tflite" \
     https://your-domain.com/admin/upload
   ```

---

### Issue: manifest.json Not Updating After Upload

**Symptoms**:
- Model uploaded successfully
- manifest.json unchanged
- New model not appearing in app updates

**Possible Causes**:
- Manifest file permissions issue
- Manifest update logic bug
- File locking (Windows)
- Path mismatch

**Solutions**:

1. **Check manifest file permissions**:
   ```bash
   ls -la server/models/manifest.json
   ```
   
   Should be writable by server process.

2. **Verify manifest update logic**:
   - Check `server/src/utils/manifest.js` `addOrUpdateModel` function
   - Add logging to debug

3. **Manually verify manifest**:
   ```bash
   cat server/models/manifest.json
   ```
   
   Check if new model entry exists with correct version.

4. **Regenerate manifest**:
   ```bash
   cd server
   node scripts/verify-manifest.js --fix
   ```
   
   This rebuilds manifest from existing .tflite files.

5. **Check for file locking issues (Windows)**:
   - Close any editors with manifest.json open
   - Restart server

6. **Verify MANIFEST_PATH in .env**:
   ```bash
   cat server/.env | grep MANIFEST_PATH
   ```
   
   Should point to `./models/manifest.json` or absolute path.

---

### Issue: nginx Returns 502 Bad Gateway

**Symptoms**:
- Requests to `https://your-domain.com` return 502 error
- "Bad Gateway" page displayed

**Possible Causes**:
- Express server not running
- nginx can't connect to backend
- Port mismatch in nginx.conf
- Firewall blocking port 3000

**Solutions**:

1. **Verify Express server running**:
   ```bash
   curl http://localhost:3000/health
   ```
   
   Should return `{"status":"ok"}`. If fails, start server.

2. **Check nginx error logs**:
   ```bash
   sudo tail -f /var/log/nginx/freshfood_error.log
   ```
   
   Look for "connect() failed" messages.

3. **Verify proxy_pass URL in nginx.conf**:
   ```nginx
   proxy_pass http://localhost:3000;
   ```
   
   Should match server PORT in `.env`.

4. **Test nginx config**:
   ```bash
   sudo nginx -t
   ```
   
   Should show "syntax is ok" and "test is successful".

5. **Restart nginx**:
   ```bash
   sudo systemctl restart nginx
   ```

6. **Check firewall rules**:
   ```bash
   sudo ufw status
   ```
   
   Ensure port 3000 accessible from nginx (localhost).

7. **Verify server listening on correct port**:
   ```bash
   netstat -tuln | grep 3000
   ```
   
   Should show `0.0.0.0:3000` or `127.0.0.1:3000`.

---

### Issue: SSL Certificate Errors

**Symptoms**:
- "Certificate expired" errors
- "Invalid certificate" warnings
- HTTPS connections fail

**Possible Causes**:
- Certificate expired
- Certificate not renewed
- Wrong certificate paths
- Domain mismatch

**Solutions**:

1. **Check certificate expiry**:
   ```bash
   openssl x509 -in /etc/letsencrypt/live/your-domain.com/fullchain.pem -noout -dates
   ```
   
   Look at "notAfter" date.

2. **Renew certificate**:
   ```bash
   sudo certbot renew
   ```

3. **Verify certificate paths in nginx.conf**:
   ```nginx
   ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
   ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
   ```
   
   Paths must match actual certificate location.

4. **Test SSL**:
   ```bash
   openssl s_client -connect your-domain.com:443
   ```
   
   Check for "Verify return code: 0 (ok)".

5. **Check certbot auto-renewal**:
   ```bash
   sudo certbot renew --dry-run
   ```
   
   Should succeed without errors.

6. **Reload nginx after renewal**:
   ```bash
   sudo systemctl reload nginx
   ```

---

## Network Issues

### Issue: Rate Limit Errors (429 Too Many Requests)

**Symptoms**:
- Requests fail with 429 status code
- "Too many requests" error message
- Occurs after multiple update checks

**Possible Causes**:
- Exceeded rate limit (default: 50 req/15min per API key, 100 req/15min per IP)
- App polling too frequently
- Multiple devices using same API key

**Solutions**:

1. **Wait for rate limit window to reset**:
   - Default: 15 minutes
   - Check `Retry-After` header in response

2. **Increase rate limits in server `.env`**:
   ```env
   RATE_LIMIT_MAX_REQUESTS=200
   RATE_LIMIT_WINDOW_MS=900000  # 15 minutes
   ```
   
   Restart server: `pm2 restart freshfood-server`

3. **Check if app polling too frequently**:
   - Updates should be hourly or on-demand (not continuous)
   - Check for infinite loops in update code

4. **Verify rate limit configuration**:
   - Check `server/src/middleware/auth.js` (IP limiter)
   - Check `server/src/routes/models.js` (API key limiter)

5. **Use different API key for testing**:
   - Generate separate testing API key
   - Use different IP address

6. **Monitor rate limit headers**:
   ```bash
   curl -v -H "X-API-Key: your-key" https://your-domain.com/manifest.json
   ```
   
   Look for `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers.

---

### Issue: Slow Model Downloads

**Symptoms**:
- Downloads take very long time
- Timeout errors
- Progress bar stuck

**Possible Causes**:
- Large model files (50MB+)
- Slow network connection
- Server bandwidth limits
- Network congestion

**Solutions**:

1. **Check model file size**:
   ```bash
   ls -lh server/models/*.tflite
   ```
   
   Models over 50MB may take minutes on slow connections.

2. **Increase timeout in `ModelDownloader.kt`**:
   ```kotlin
   .readTimeout(120, TimeUnit.SECONDS)  // Change to 300 for 5 minutes
   ```

3. **Test download speed**:
   ```bash
   curl -H "X-API-Key: your-key" \
     -o test.tflite \
     -w "Speed: %{speed_download} bytes/sec\n" \
     https://your-domain.com/models/fruitid.tflite
   ```

4. **Enable nginx gzip compression** (limited effect on binary files):
   ```nginx
   gzip on;
   gzip_types application/octet-stream;
   ```

5. **Use CDN for model distribution** (advanced):
   - CloudFlare, AWS CloudFront, or similar
   - Configure nginx to serve from CDN

6. **Optimize model size**:
   - Quantize TFLite models (float32 → int8)
   - Use post-training quantization
   - Can reduce size by 75%

7. **Test on different network**:
   - Try WiFi vs cellular
   - Different network provider

---

### Issue: CORS Errors in Browser Testing

**Symptoms**:
- "CORS policy" errors when testing API from browser
- Preflight requests (OPTIONS) blocked
- 403 Forbidden for cross-origin requests

**Possible Causes**:
- ALLOWED_ORIGINS not configured
- Origin not in whitelist
- CORS headers missing

**Solutions**:

1. **Add origin to ALLOWED_ORIGINS in `.env`**:
   ```env
   ALLOWED_ORIGINS=https://your-domain.com,http://localhost:3000
   ```
   
   Comma-separated list, no spaces.

2. **Verify CORS middleware in `server/src/index.js`**:
   ```javascript
   const allowedOrigins = process.env.ALLOWED_ORIGINS
     ? process.env.ALLOWED_ORIGINS.split(',').map(origin => origin.trim())
     : ['http://localhost:3000'];
   
   app.use(cors({
     origin: allowedOrigins,
     methods: ['GET', 'POST'],
     allowedHeaders: ['Content-Type', 'X-API-Key']
   }));
   ```

3. **Check nginx CORS headers** (if manually configured):
   ```nginx
   add_header 'Access-Control-Allow-Origin' '$http_origin';
   add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS';
   add_header 'Access-Control-Allow-Headers' 'X-API-Key, Content-Type';
   ```

4. **Note: Android app doesn't have CORS issues**:
   - Native HTTP client not subject to browser CORS
   - Only affects browser-based testing

5. **Restart server after .env changes**:
   ```bash
   pm2 restart freshfood-server
   ```

---

## Configuration Issues

### Issue: BuildConfig Fields Not Generated

**Symptoms**:
- "Unresolved reference: BuildConfig" in Android Studio
- Compile errors when accessing `BuildConfig.MODEL_UPDATE_API_KEY`

**Possible Causes**:
- buildConfig feature not enabled
- Gradle sync needed
- Cache corruption

**Solutions**:

1. **Enable buildConfig in `app/build.gradle.kts`**:
   ```kotlin
   android {
       buildFeatures {
           viewBinding = true
           buildConfig = true  // Add this line
       }
   }
   ```

2. **Rebuild project**:
   - `Build > Clean Project`
   - `Build > Rebuild Project`

3. **Sync Gradle files**:
   - `File > Sync Project with Gradle Files`

4. **Invalidate caches**:
   - `File > Invalidate Caches / Restart`
   - Select "Invalidate and Restart"

5. **Check buildConfigField syntax** (in `app/build.gradle.kts`):
   ```kotlin
   defaultConfig {
       buildConfigField("String", "MODEL_UPDATE_API_KEY", 
           "\"${project.findProperty("modelUpdateApiKey") ?: "default-key"}\"")
   }
   ```

6. **Verify BuildConfig.java generated**:
   - Check `app/build/generated/source/buildConfig/debug/com/jody/freshfood/BuildConfig.java`

---

### Issue: local.properties Not Working

**Symptoms**:
- Properties set in `local.properties` not applied
- App uses default values
- BuildConfig has wrong values

**Possible Causes**:
- File in wrong location
- Property names incorrect
- Quotes around values
- Gradle sync needed

**Solutions**:

1. **Verify file location**:
   - Should be in project root (same level as `build.gradle.kts`)
   - NOT in `app/` subdirectory

2. **Check property names match** (case-sensitive):
   ```properties
   modelUpdateBaseUrl=http://10.0.2.2:3000/models/
   modelUpdateApiKey=your-key-here
   contributeBaseUrl=http://10.0.2.2:3000/api/
   ```

3. **Ensure no quotes around values**:
   ```properties
   # Correct
   modelUpdateApiKey=abc123
   
   # Wrong
   modelUpdateApiKey="abc123"
   ```

4. **Sync Gradle after changes**:
   - `File > Sync Project with Gradle Files`

5. **Check `project.findProperty()` usage in `build.gradle.kts`**:
   ```kotlin
   buildConfigField("String", "MODEL_UPDATE_API_KEY",
       "\"${project.findProperty("modelUpdateApiKey") ?: "default"}\"")
   ```

6. **Rebuild project**:
   ```bash
   ./gradlew clean build
   ```

---

### Issue: Environment Variables Not Loaded on Server

**Symptoms**:
- Server uses default values instead of `.env` values
- API keys not working
- Server starts but behaves incorrectly

**Possible Causes**:
- .env file missing
- .env syntax errors
- dotenv not loaded
- Server not restarted

**Solutions**:

1. **Verify .env file exists**:
   ```bash
   ls -la server/.env
   ```
   
   If missing, copy from template: `cp server/.env.example server/.env`

2. **Check .env syntax**:
   ```env
   # Correct
   CLIENT_API_KEY=abc123
   PORT=3000
   
   # Wrong (spaces around =)
   CLIENT_API_KEY = abc123
   
   # Wrong (unnecessary quotes)
   CLIENT_API_KEY="abc123"
   ```

3. **Restart server after .env changes**:
   ```bash
   pm2 restart freshfood-server
   
   # Or if running with npm
   # Stop with Ctrl+C and restart
   npm run dev
   ```

4. **Test env loading**:
   Add to `server/src/index.js`:
   ```javascript
   console.log('CLIENT_API_KEY:', process.env.CLIENT_API_KEY);
   console.log('PORT:', process.env.PORT);
   ```

5. **Ensure dotenv loaded**:
   Check top of `server/src/index.js`:
   ```javascript
   require('dotenv').config();
   ```

6. **Check for .env in .gitignore**:
   ```bash
   cat .gitignore | grep .env
   ```
   
   Should be listed (don't commit .env to git).

---

## Debugging Tips

### Enable Verbose Logging (Android)

**Set HttpLoggingInterceptor to BODY level** in `ModelUpdateService.kt`:

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY  // Change to BODY for full logs
}
```

Shows full request/response including headers and body.

### Monitor Server Requests

**View PM2 logs**:
```bash
pm2 logs freshfood-server --lines 100
```

**View real-time logs**:
```bash
pm2 logs freshfood-server --lines 0
```

**Filter for errors**:
```bash
pm2 logs freshfood-server --err
```

### Test Endpoints with curl

**Include verbose flag**:
```bash
curl -v -H "X-API-Key: your-key" https://your-domain.com/manifest.json
```

Shows:
- Request headers
- Response headers
- Status code
- Response body

**Test with timing**:
```bash
curl -w "@curl-format.txt" -H "X-API-Key: your-key" https://your-domain.com/manifest.json
```

Create `curl-format.txt`:
```
     time_namelookup:  %{time_namelookup}s\n
        time_connect:  %{time_connect}s\n
     time_appconnect:  %{time_appconnect}s\n
       time_redirect:  %{time_redirect}s\n
  time_starttransfer:  %{time_starttransfer}s\n
                     ----------\n
          time_total:  %{time_total}s\n
```

### Use Android Studio Network Profiler

**Access profiler**:
1. `View > Tool Windows > Profiler`
2. Select running app
3. Click Network tab

**Features**:
- View all HTTP requests/responses
- Timing information
- Request/response headers
- Throttle network speed

### Check SHA-256 Hashes

**Always verify hashes match**:

**Server side**:
```bash
sha256sum server/models/fruitid.tflite
```

**Manifest**:
```bash
cat server/models/manifest.json | jq '.models[] | select(.name=="fruitid") | .sha256'
```

**After download** (if debugging):
```bash
adb shell run-as com.jody.freshfood sha256sum files/models/fruitid.tflite
```

### Test in Isolation

**Test server endpoints independently**:
1. Test health: `curl https://your-domain.com/health`
2. Test manifest: `curl -H "X-API-Key: key" https://your-domain.com/manifest.json`
3. Test download: `curl -H "X-API-Key: key" -o test.tflite https://your-domain.com/models/fruitid.tflite`

**Test app network layer independently**:
- Use MockWebServer in unit tests
- Test ApiKeyInterceptor separately
- Test ModelDownloader with local files

---

## Getting Help

### Check Existing Documentation

- **Server README**: [`server/README.md`](../server/README.md) - Extensive server setup and troubleshooting
- **Nginx README**: [`server/nginx/README.md`](../server/nginx/README.md) - SSL/TLS and proxy configuration
- **Android Testing Guide**: [`docs/ANDROID_TESTING.md`](ANDROID_TESTING.md) - Testing procedures
- **Deployment Guide**: [`docs/DEPLOYMENT.md`](DEPLOYMENT.md) - Production deployment

### Review Logs

**Always check both server and Android logs**:

**Server logs**:
```bash
pm2 logs freshfood-server
```

**Android logs**:
```bash
adb logcat | grep -E "ModelUpdateManager|ModelDownloader|SettingsFragment|ModelUpdateService"
```

### Search Issues

Check GitHub issues for similar problems:
- [FreshFood Issues](https://github.com/JodyPutra-dev/FreshFood/issues)
- Search for error messages, symptoms
- Check closed issues for solutions

### Create Detailed Bug Report

When reporting issues, include:

1. **Environment**:
   - Android version (API level)
   - Device/emulator
   - Server OS and Node.js version
   - nginx version (if applicable)

2. **Configuration**:
   - Server URL (without API keys)
   - Build type (debug/release)
   - Network type (WiFi/cellular/localhost)

3. **Steps to Reproduce**:
   - Exact sequence of actions
   - What you expected to happen
   - What actually happened

4. **Logs**:
   - Relevant Android logcat output
   - Server logs (PM2 or npm)
   - nginx error logs (if 502/SSL errors)

5. **Screenshots**:
   - Error messages
   - Settings screen state
   - Server response (curl output)

### Contact

- **GitHub Issues**: [Create new issue](https://github.com/JodyPutra-dev/FreshFood/issues/new)
- **Discussions**: Use GitHub Discussions for questions
- **Email**: [Contact details if applicable]

---

**Remember**: Most issues are configuration-related. Double-check API keys, URLs, and BuildConfig values first.
