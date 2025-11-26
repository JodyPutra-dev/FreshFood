# Android Testing Guide

## Introduction

This guide covers testing the over-the-air (OTA) model update functionality in the FreshFood Android app. It includes testing procedures for the update flow, SHA-256 verification, network scenarios, and troubleshooting common issues.

### Prerequisites

Before testing, ensure you have:

- **Android Studio** - Hedgehog+ (2023.1.1+) recommended
- **Server Running** - Either local (`http://10.0.2.2:3000` for emulator) or production (`https://your-domain.com`)
- **API Key Configured** - Client API key matching server's `CLIENT_API_KEY`
- **Test Device/Emulator** - API 24+ (Android 7.0+)
- **Test Models** - Multiple versions of `.tflite` models available on server

## Configuration

### Server URL Configuration

Edit `local.properties` in project root (create if doesn't exist):

**For Android Emulator (localhost testing):**
```properties
modelUpdateBaseUrl=http://10.0.2.2:3000/models/
modelUpdateApiKey=your-client-api-key-here
contributeBaseUrl=http://10.0.2.2:3000/api/
```

**For Physical Device (same network):**
```properties
# Replace 192.168.1.100 with your computer's IP address
modelUpdateBaseUrl=http://192.168.1.100:3000/models/
modelUpdateApiKey=your-client-api-key-here
contributeBaseUrl=http://192.168.1.100:3000/api/
```

**For Physical Device (production):**
```properties
modelUpdateBaseUrl=https://your-domain.com/models/
modelUpdateApiKey=your-production-client-api-key
contributeBaseUrl=https://your-domain.com/api/
```

### API Key Configuration

The API key must match the server's `CLIENT_API_KEY` exactly (case-sensitive). This key is used by `ApiKeyInterceptor` to add the `X-API-Key` header to all requests.

**Verify server API key:**
```bash
# On server
cat server/.env | grep CLIENT_API_KEY
```

**Configure Android app:**
Add to `local.properties`:
```properties
modelUpdateApiKey=<same-key-from-server>
```

### Rebuild After Configuration Changes

BuildConfig fields are generated at compile time. After any configuration changes, rebuild the project:

1. **Clean Project**: `Build > Clean Project`
2. **Rebuild Project**: `Build > Rebuild Project`
3. **Sync Gradle**: `File > Sync Project with Gradle Files`

Alternatively, via command line:
```bash
./gradlew clean build
```

### Debug vs Release Build Differences

**Debug Build** (refer to `app/build.gradle.kts` lines 30-36):
- Automatically uses `http://10.0.2.2:3000/models/` (emulator localhost)
- Uses `debug-api-key-for-local-testing` as API key
- HTTP logging enabled (shows full request/response)
- No minification

**Release Build**:
- Uses values from `gradle.properties` or `local.properties`
- Requires production server URL and API key
- Minimal logging
- ProGuard/R8 minification enabled

## Testing OTA Updates via SettingsFragment

### Basic Update Flow

The SettingsFragment (refer to `SettingsFragment.kt` lines 124-134) displays current model versions and provides an update button.

**Step 1: Launch App**
1. Build and install app: `./gradlew installDebug`
2. Launch app on emulator/device
3. Navigate to **Settings** tab (bottom navigation)

**Step 2: Check Current Versions**
- Current model versions displayed under "Current Models" section
- Format: `Model Name: Version X (SHA-256: abc123...)`
- Example: `fruitid: Version 1 (SHA-256: a1b2c3d4...)`

**Step 3: Trigger Update Check**
1. Tap **"Check for Updates"** button
2. Observe status messages (refer to `SettingsFragment.kt` lines 86-122 for UpdateStatus handling):
   - `UpdateStatus.Checking` → "Checking for updates…" (progress bar visible)
   - `UpdateStatus.Downloading` → "Downloading [model_name]…" (progress bar visible)
   - `UpdateStatus.Success` → "Successfully updated X model(s)" or "All models are up to date" (progress bar hidden)
   - `UpdateStatus.Error` → Error message with reason (progress bar hidden)

**Step 4: Verify Version Update**
- After successful update, model version number should increment
- SHA-256 hash should change to new model's hash
- New version persisted in SharedPreferences (managed by `ModelManager`)

### Expected Status Messages

| Status | Message | Progress Bar | Description |
|--------|---------|--------------|-------------|
| `Checking` | "Checking for updates…" | Visible | Fetching manifest from server |
| `Downloading` | "Downloading [model_name]…" | Visible | Downloading and verifying model |
| `Success` (with updates) | "Successfully updated X model(s)" | Hidden | Update completed successfully |
| `Success` (no updates) | "All models are up to date" | Hidden | No newer versions available |
| `Error` | Specific error message | Hidden | Update failed (see error text) |

## Testing Complete Update Flow

### Step-by-Step Test Procedure

**Step 1: Note Current State**
1. Open Settings tab
2. Note current model versions (e.g., fruitid v1, apple_ripeness v1)
3. Screenshot for reference

**Step 2: Upload New Model Version to Server**

Using the upload script:
```bash
cd server
./scripts/upload-model.sh path/to/updated-model.tflite https://your-domain.com $ADMIN_API_KEY
```

Or using curl:
```bash
curl -X POST \
  -H "X-API-Key: $ADMIN_API_KEY" \
  -F "file=@path/to/model.tflite" \
  https://your-domain.com/admin/upload
```

**Step 3: Verify Manifest Updated**
```bash
curl -H "X-API-Key: $CLIENT_API_KEY" https://your-domain.com/manifest.json
```

Expected response:
```json
{
  "models": [
    {
      "name": "fruitid",
      "version": 2,
      "downloadUrl": "https://your-domain.com/models/fruitid.tflite",
      "sha256": "new-hash-here",
      "updatedAt": "2025-11-26T12:00:00.000Z"
    }
  ]
}
```

**Step 4: Trigger Update in App**
1. Return to Settings tab in app
2. Tap "Check for Updates"
3. Observe download progress

**Step 5: Verify Update Success**
1. Check status message: "Successfully updated 1 model(s)"
2. Verify new version displayed: "fruitid: Version 2"
3. Verify SHA-256 updated

**Step 6: Test Model Inference**
1. Navigate to Scan tab
2. Capture or select food image
3. Verify model loads and produces prediction
4. Confirms updated model is functional

## Testing SHA-256 Verification

The ModelDownloader (refer to `ModelDownloader.kt` lines 38-56) computes SHA-256 during download and verifies against the manifest hash.

### Test 1: Normal Case (Valid Model)

**Purpose**: Verify successful download and verification with correct hash

**Procedure**:
1. Upload valid model to server
2. Trigger update in app
3. Monitor logcat: `adb logcat | grep ModelDownloader`

**Expected Logs**:
```
D/ModelDownloader: Starting download: https://your-domain.com/models/fruitid.tflite
D/ModelDownloader: Download completed, verifying SHA-256...
D/ModelDownloader: SHA-256 verification successful
D/ModelDownloader: Model saved to: /data/user/0/com.jody.freshfood/files/models/fruitid.tflite
```

**Expected Outcome**: Update succeeds, new version displayed

### Test 2: Tampered Hash (Incorrect Manifest)

**Purpose**: Verify ModelDownloader detects hash mismatch

**Procedure**:
1. Manually edit `server/models/manifest.json`
2. Change SHA-256 hash to incorrect value (modify a few characters)
3. Save manifest
4. Trigger update in app
5. Monitor logcat

**Expected Logs**:
```
E/ModelDownloader: SHA-256 mismatch for fruitid.tflite
E/ModelDownloader: Expected: a1b2c3d4e5f6...
E/ModelDownloader: Got: x9y8z7w6v5u4...
E/ModelDownloader: Deleting corrupted file
```

**Expected Outcome**: 
- Update fails with "SHA256 mismatch" error
- Temp file deleted
- Previous model version retained

**Cleanup**:
```bash
# Regenerate correct manifest
cd server
node scripts/verify-manifest.js --fix
```

### Test 3: Interrupted Download

**Purpose**: Verify graceful handling of network interruption

**Procedure**:
1. Start model download
2. While downloading (progress bar visible), enable airplane mode
3. Wait for timeout (120 seconds read timeout)
4. Monitor logcat

**Expected Logs**:
```
E/ModelDownloader: Download failed: java.net.SocketTimeoutException: timeout
E/ModelDownloader: Cleaning up failed download
```

**Expected Outcome**:
- Error message displayed: "Error: Download failed"
- No corrupted file saved
- Previous model version retained

**Cleanup**: Disable airplane mode, retry update

### Logcat Monitoring Commands

```bash
# Monitor all OTA-related logs
adb logcat | grep -E "ModelDownloader|ModelUpdateManager|SettingsFragment|ModelUpdateService"

# Monitor only errors
adb logcat | grep -E "ModelDownloader|ModelUpdateManager" | grep -E "E/|W/"

# Monitor SHA-256 verification
adb logcat | grep "SHA-256"

# Save logs to file
adb logcat | grep -E "ModelDownloader|ModelUpdateManager" > ota_test_logs.txt
```

## Testing Network Scenarios

### Test 1: No Network Connection

**Purpose**: Verify graceful handling when network unavailable

**Procedure**:
1. Disable WiFi and cellular data (airplane mode)
2. Tap "Check for Updates"
3. Observe behavior

**Expected Outcome** (refer to `SettingsFragment.kt` lines 58-68):
- Toast message: "No internet connection"
- Status message: "Error: No internet connection"
- No crash, graceful error handling

**Cleanup**: Re-enable network

### Test 2: Slow Network

**Purpose**: Verify timeout handling with slow connection

**Procedure**:
1. Use Android Studio Network Profiler to throttle speed:
   - Open Network Profiler: `View > Tool Windows > Profiler`
   - Select Network tab
   - Set speed limit (e.g., 50 KB/s)
2. Trigger update for large model
3. Monitor progress

**Expected Outcome**:
- Download progresses slowly
- If exceeds read timeout (120s in `ModelDownloader.kt` line 21), fails with timeout error
- For large models, consider increasing timeout

**Cleanup**: Remove network throttling

### Test 3: Server Unreachable

**Purpose**: Verify handling when server is down

**Procedure**:
1. Stop server: `pm2 stop freshfood-server` (or `Ctrl+C` if running with `npm run dev`)
2. Tap "Check for Updates" in app
3. Monitor logcat

**Expected Logs**:
```
E/ModelUpdateService: Connection failed: java.net.ConnectException: Failed to connect to your-domain.com/123.45.67.89:443
```

**Expected Outcome**:
- Error message: "Error: Failed to fetch manifest"
- No crash

**Cleanup**: Restart server

### Test 4: 401 Unauthorized

**Purpose**: Verify handling of API key mismatch

**Procedure**:
1. Edit `local.properties`, change `modelUpdateApiKey` to wrong value
2. Rebuild project: `./gradlew clean build`
3. Reinstall app: `./gradlew installDebug`
4. Tap "Check for Updates"

**Expected Logs**:
```
E/ModelUpdateService: HTTP 401: Unauthorized
E/ModelUpdateService: Response: {"error":"Invalid or missing API key"}
```

**Expected Outcome**:
- Error message: "Error: HTTP 401" (authentication failed)
- Check logcat for details

**Cleanup**: Restore correct API key, rebuild

## Testing on Emulator

### Emulator Setup

**Requirements**:
- Android Studio emulator with API 24+ (minSdk requirement)
- Internet permission in AndroidManifest.xml (already configured)

**Create Emulator**:
1. Open AVD Manager: `Tools > Device Manager`
2. Create Virtual Device
3. Select device definition (e.g., Pixel 6)
4. Select system image: API 24, 28, 33, or 36 (test multiple versions)
5. Finish and launch emulator

### Server URL for Emulator

The emulator cannot access `localhost` directly. Use `10.0.2.2` which maps to the host machine's `localhost`.

**Configure `local.properties`**:
```properties
modelUpdateBaseUrl=http://10.0.2.2:3000/models/
```

**Test server accessibility from emulator**:
```bash
# In Android Studio Terminal with emulator running
adb shell curl http://10.0.2.2:3000/health
```

Expected response: `{"status":"ok","uptime":123}`

### Multi-Version Testing

Test on different Android API levels to ensure compatibility:

**API 24 (Android 7.0)** - Minimum supported version
**API 28 (Android 9.0)** - Common production version
**API 33 (Android 13)** - Recent version
**API 36 (Android 14)** - Latest version

Run tests on each API level to catch version-specific issues.

## Testing on Physical Device

### Device Setup

**Requirements**:
- Physical Android device (API 24+)
- USB debugging enabled: `Settings > Developer Options > USB Debugging`
- Device connected to computer via USB

**Enable Developer Options**:
1. Go to `Settings > About Phone`
2. Tap "Build Number" 7 times
3. Return to Settings, find "Developer Options"
4. Enable "USB Debugging"

### Local Testing (Same Network)

**Step 1: Find Host Machine IP**

```bash
# Windows
ipconfig
# Look for "IPv4 Address" (e.g., 192.168.1.100)

# Linux/Mac
ifconfig
# or
ip addr show
```

**Step 2: Configure App**

Edit `local.properties`:
```properties
# Replace 192.168.1.100 with your actual IP
modelUpdateBaseUrl=http://192.168.1.100:3000/models/
modelUpdateApiKey=your-client-api-key
```

**Step 3: Ensure Device and Computer on Same Network**
- Both connected to same WiFi network
- Computer firewall allows port 3000 (if applicable)

**Step 4: Test Connectivity**

```bash
# Install terminal emulator on device or use ADB
adb shell curl http://192.168.1.100:3000/health
```

### Production Testing

**Configure for production server**:
```properties
modelUpdateBaseUrl=https://your-domain.com/models/
modelUpdateApiKey=your-production-client-api-key
```

### Install and Monitor

**Install Debug APK**:
```bash
./gradlew installDebug
```

**Monitor Logs**:
```bash
# Real-time monitoring
adb logcat | grep -E "ModelUpdateManager|ModelDownloader|SettingsFragment"

# Filter by app package
adb logcat | grep "com.jody.freshfood"

# Save to file
adb logcat > device_test_logs.txt
```

## Verifying Model Loading

### Check Model File Exists

After successful update, verify model file saved correctly:

```bash
# List model files
adb shell run-as com.jody.freshfood ls files/models/

# Expected output:
# fruitid.tflite
# apple_ripeness.tflite
# avocado_ripeness.tflite
# bread_ripeness.tflite
```

### Check Model Metadata

Model metadata stored in SharedPreferences (refer to `ModelManager.kt` lines 70-84):

```bash
# View SharedPreferences
adb shell run-as com.jody.freshfood cat shared_prefs/freshfood_models.xml
```

**Expected content**:
```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="fruitid_version" value="2" />
    <string name="fruitid_sha256">a1b2c3d4e5f6...</string>
    <long name="fruitid_updated_at" value="1732622400000" />
</map>
```

### Verify Model Loads in App

**Step 1: Navigate to Scan Tab**

**Step 2: Capture/Select Image**
- Tap camera icon to capture photo
- Or tap gallery icon to select existing image

**Step 3: Observe Inference**
- Model should load without error
- Prediction displayed with confidence score
- Example: "Fresh (95% confidence)"

**Step 4: Check Logs**
```bash
adb logcat | grep "ModelManager"
```

Expected logs:
```
D/ModelManager: Loading model: fruitid
D/ModelManager: Model loaded successfully: fruitid.tflite
D/ModelManager: Model metadata: version=2, sha256=a1b2c3d4...
```

## Automated Testing

### Unit Tests for ModelDownloader

**Example test structure** (not yet implemented):

```kotlin
@Test
fun testSHA256Verification_ValidHash() {
    val expectedHash = "a1b2c3d4e5f6..."
    val modelBytes = loadTestModel()
    
    val computedHash = ModelDownloader.computeSHA256(modelBytes)
    
    assertEquals(expectedHash, computedHash)
}

@Test
fun testSHA256Verification_InvalidHash() {
    val expectedHash = "incorrect_hash"
    val modelBytes = loadTestModel()
    
    val computedHash = ModelDownloader.computeSHA256(modelBytes)
    
    assertNotEquals(expectedHash, computedHash)
}
```

### Instrumented Tests for SettingsFragment

**Example test structure** (not yet implemented):

```kotlin
@Test
fun testCheckForUpdates_SuccessFlow() {
    // Mock server response
    mockWebServer.enqueue(MockResponse()
        .setResponseCode(200)
        .setBody(manifestJsonWithUpdates))
    
    // Launch SettingsFragment
    launchFragmentInContainer<SettingsFragment>()
    
    // Tap "Check for Updates" button
    onView(withId(R.id.btnCheckUpdates)).perform(click())
    
    // Verify progress bar shown
    onView(withId(R.id.progressBar)).check(matches(isDisplayed()))
    
    // Wait for completion
    Thread.sleep(2000)
    
    // Verify success message
    onView(withText(containsString("Successfully updated")))
        .check(matches(isDisplayed()))
}
```

### Mock Server with MockWebServer

```kotlin
@Before
fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()
    
    // Configure app to use mock server URL
    val mockUrl = mockWebServer.url("/models/").toString()
    // Inject mockUrl into ModelUpdateService
}

@Test
fun testManifestFetch_401Unauthorized() {
    mockWebServer.enqueue(MockResponse()
        .setResponseCode(401)
        .setBody("""{"error":"Invalid API key"}"""))
    
    // Trigger update
    val result = modelUpdateService.getManifest()
    
    // Verify error handled
    assertTrue(result.isFailure)
    assertEquals(401, result.exceptionOrNull()?.statusCode)
}

@After
fun tearDown() {
    mockWebServer.shutdown()
}
```

### CI/CD Integration Suggestions

**GitHub Actions Example**:
```yaml
name: Android CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Run unit tests
        run: ./gradlew test
      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 29
          script: ./gradlew connectedAndroidTest
```

## Troubleshooting

### Issue: "Check for Updates" Button Does Nothing

**Symptoms**: Button tap has no effect, no progress bar, no status message

**Solutions**:
1. Check logcat: `adb logcat | grep -E "ModelUpdateManager|SettingsFragment"`
2. Verify network connectivity: WiFi/data enabled, not in airplane mode
3. Test server endpoint: `curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json`
4. Verify API key in BuildConfig: Check `local.properties` has `modelUpdateApiKey`
5. Rebuild: `./gradlew clean build`

### Issue: "Error: Failed to fetch manifest"

**Symptoms**: Error message after tapping "Check for Updates"

**Solutions**:
1. Verify server URL in `local.properties`: Should be `http://10.0.2.2:3000/models/` for emulator
2. Check server running: `curl https://your-domain.com/health`
3. Test manifest: `curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json`
4. Check server logs: `pm2 logs freshfood-server`
5. Verify timeout settings in `ModelUpdateService.kt` (30s connect, 60s read)

### Issue: "Error: HTTP 401"

**Symptoms**: 401 error when checking for updates

**Solutions**:
1. Verify API keys match:
   - Server: `cat server/.env | grep CLIENT_API_KEY`
   - Android: Check `local.properties` `modelUpdateApiKey`
2. Ensure keys identical (case-sensitive, no spaces)
3. Rebuild app: `./gradlew clean build`
4. Restart server: `pm2 restart freshfood-server`
5. Verify nginx forwards X-API-Key header: Check `nginx.conf` has `proxy_set_header X-API-Key $http_x_api_key;`
6. Test manually: `curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json`

### Issue: "SHA256 mismatch"

**Symptoms**: Download completes but verification fails

**Solutions**:
1. Verify server-side hash: `sha256sum server/models/fruitid.tflite`
2. Compare with manifest: `cat server/models/manifest.json | grep sha256`
3. Regenerate manifest: `node server/scripts/verify-manifest.js --fix`
4. Re-upload model: `./server/scripts/upload-model.sh path/to/model.tflite`
5. Clear app cache: `adb shell pm clear com.jody.freshfood`

### Issue: Update Succeeds But Version Doesn't Change

**Symptoms**: Success message shown but version number unchanged

**Solutions**:
1. Check SharedPreferences: `adb shell run-as com.jody.freshfood cat shared_prefs/freshfood_models.xml`
2. Check manifest version on server: `curl -H "X-API-Key: your-key" https://your-domain.com/manifest.json`
3. Verify version incremented on server (not same version)
4. Force UI refresh: Navigate away from Settings and back
5. Restart app

For comprehensive troubleshooting, see [docs/TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Best Practices

✅ **Test on emulator before physical device** - Faster iteration, easier debugging

✅ **Always verify SHA-256 hashes** - Match between server manifest and actual files

✅ **Use debug build for local testing** - Automatic localhost configuration, verbose logging

✅ **Monitor logcat during testing** - Catch issues immediately with detailed error messages

✅ **Test different network conditions** - WiFi, cellular, offline, slow connections

✅ **Verify model inference after update** - Ensure updated model loads and functions correctly

✅ **Test on multiple Android versions** - API 24, 28, 33, 36 for compatibility

✅ **Use MockWebServer for unit tests** - Isolate network layer, predictable responses

✅ **Clear app data between tests** - Prevents state contamination: `adb shell pm clear com.jody.freshfood`

✅ **Document test results** - Keep log of what was tested, outcomes, issues found

---

**Next Steps**: See [docs/TROUBLESHOOTING.md](TROUBLESHOOTING.md) for detailed issue resolution and [docs/DEPLOYMENT.md](DEPLOYMENT.md) for production deployment.
