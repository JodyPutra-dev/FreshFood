# Deployment Guide

## Introduction

This guide provides step-by-step instructions for deploying the FreshFood OTA model update system to production, covering both server infrastructure and Android app distribution.

### Prerequisites

**Server Requirements**:
- VPS or cloud server (DigitalOcean, AWS, Google Cloud, etc.)
- Ubuntu 22.04 LTS (recommended) or similar Linux distribution
- Root or sudo access
- Domain name with DNS access

**Android Requirements**:
- Android Studio Hedgehog+ (2023.1.1+)
- Java keystore for app signing
- Access to Play Console (optional, for Play Store distribution)

### Deployment Stages

1. Server provisioning and setup
2. Dependency installation
3. Domain and SSL configuration
4. Server code deployment
5. nginx configuration
6. Android app configuration
7. App signing and building
8. Testing and release

---

## Server Deployment

### Step 1: Provision Server

**Recommended Specifications**:
- **CPU**: 2 vCPUs minimum
- **RAM**: 2GB minimum (4GB recommended)
- **Storage**: 50GB SSD
- **OS**: Ubuntu 22.04 LTS
- **Network**: 100 Mbps+ bandwidth

**Cloud Providers**:
- **DigitalOcean**: $12/month Droplet (2 vCPU, 2GB RAM)
- **AWS EC2**: t3.small instance ($15/month)
- **Google Cloud**: e2-small instance ($13/month)
- **Linode**: Shared CPU 2GB ($12/month)

**Initial Setup**:
1. Create server instance
2. Note public IP address (e.g., `123.45.67.89`)
3. Set up SSH key authentication
4. Connect via SSH: `ssh root@123.45.67.89`

**Security Best Practices**:

```bash
# Create non-root user
adduser freshfood
usermod -aG sudo freshfood

# Disable root SSH login
nano /etc/ssh/sshd_config
# Set: PermitRootLogin no
systemctl restart sshd

# Switch to new user
su - freshfood
```

---

### Step 2: Install Dependencies

**Update System**:
```bash
sudo apt update && sudo apt upgrade -y
```

**Install Node.js 18.x**:
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Verify installation
node --version  # Should show v18.x.x
npm --version   # Should show 9.x.x or higher
```

**Install nginx**:
```bash
sudo apt install nginx -y

# Verify installation
nginx -v  # Should show nginx/1.18.x or higher

# Start and enable nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

**Install PM2** (Process Manager):
```bash
sudo npm install -g pm2

# Verify installation
pm2 --version
```

**Install Certbot** (for SSL certificates):
```bash
sudo apt install certbot python3-certbot-nginx -y

# Verify installation
certbot --version
```

---

### Step 3: Configure Domain and DNS

**Point Domain to Server**:

1. Log in to your domain registrar (Namecheap, GoDaddy, etc.)
2. Go to DNS management
3. Add A record:
   - **Type**: A
   - **Name**: @ (or subdomain like `api`)
   - **Value**: Your server IP (e.g., `123.45.67.89`)
   - **TTL**: 300 (5 minutes)

**Wait for DNS Propagation** (5-30 minutes):
```bash
# Test DNS resolution
nslookup your-domain.com

# Should return your server IP
```

**Verify Domain Resolves**:
```bash
ping your-domain.com
# Should ping your server IP
```

---

### Step 4: Deploy Server Code

**Clone Repository**:
```bash
cd ~
git clone https://github.com/JodyPutra-dev/FreshFood.git
cd FreshFood/server
```

**Install Dependencies**:
```bash
npm install --production
```

**Create Environment File**:
```bash
cp .env.example .env
```

**Generate API Keys**:
```bash
# Generate two separate keys
ADMIN_KEY=$(openssl rand -hex 32)
CLIENT_KEY=$(openssl rand -hex 32)

# Display keys (save these securely)
echo "ADMIN_API_KEY=$ADMIN_KEY"
echo "CLIENT_API_KEY=$CLIENT_KEY"

# Add to .env file
echo "ADMIN_API_KEY=$ADMIN_KEY" >> .env
echo "CLIENT_API_KEY=$CLIENT_KEY" >> .env
```

**Configure `.env` File**:
```bash
nano .env
```

**Complete `.env` Configuration**:
```env
# Server Configuration
PORT=3000
NODE_ENV=production
SERVER_URL=https://your-domain.com

# API Keys (generated above)
ADMIN_API_KEY=<your-64-char-admin-key>
CLIENT_API_KEY=<your-64-char-client-key>

# CORS Configuration
ALLOWED_ORIGINS=https://your-domain.com

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=50

# File Upload
MAX_FILE_SIZE_MB=100
MANIFEST_PATH=./models/manifest.json
MODELS_PATH=./models
```

**Important**: Save `CLIENT_API_KEY` - you'll need it for Android app configuration.

---

### Step 5: Configure SSL Certificate

**Obtain Let's Encrypt Certificate**:
```bash
sudo certbot --nginx -d your-domain.com
```

**Follow Prompts**:
1. Enter email address (for renewal notifications)
2. Agree to Terms of Service (Y)
3. Share email with EFF (optional, Y/N)
4. Choose redirect option: **2** (Redirect HTTP to HTTPS)

**Verify Certificate**:
```bash
sudo certbot certificates

# Should show:
# Certificate Name: your-domain.com
#   Domains: your-domain.com
#   Expiry Date: 2025-02-24 (VALID: 89 days)
```

**Test Auto-Renewal**:
```bash
sudo certbot renew --dry-run

# Should show "Congratulations, all simulated renewals succeeded"
```

**Set Up Auto-Renewal Cron Job**:
```bash
# Certbot usually creates this automatically, but verify:
sudo systemctl status certbot.timer

# Should show "active (waiting)"
```

---

### Step 6: Configure nginx

**Copy nginx Configuration**:
```bash
sudo cp ~/FreshFood/server/nginx/nginx.conf /etc/nginx/sites-available/freshfood
```

**Edit Configuration**:
```bash
sudo nano /etc/nginx/sites-available/freshfood
```

**Update Domain Name and Paths**:
```nginx
server {
    server_name your-domain.com;  # Change this

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;  # Change this
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;  # Change this

    # Rest of configuration remains the same
}
```

**Enable Site**:
```bash
sudo ln -s /etc/nginx/sites-available/freshfood /etc/nginx/sites-enabled/
```

**Remove Default Site** (optional):
```bash
sudo rm /etc/nginx/sites-enabled/default
```

**Test Configuration**:
```bash
sudo nginx -t

# Should show:
# nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
# nginx: configuration file /etc/nginx/nginx.conf test is successful
```

**Reload nginx**:
```bash
sudo systemctl reload nginx
```

**Enable nginx on Boot**:
```bash
sudo systemctl enable nginx
```

---

### Step 7: Start Server with PM2

**Start Server**:
```bash
cd ~/FreshFood/server
pm2 start src/index.js --name freshfood-server
```

**Save PM2 Configuration**:
```bash
pm2 save
```

**Enable PM2 Startup Script**:
```bash
pm2 startup

# Follow the instructions output by the command
# Copy and run the command it provides, e.g.:
sudo env PATH=$PATH:/usr/bin pm2 startup systemd -u freshfood --hp /home/freshfood
```

**Verify Server Running**:
```bash
pm2 status

# Should show:
# ┌────┬────────────────────┬─────────┬─────────┬─────────┐
# │ id │ name               │ status  │ cpu     │ memory  │
# ├────┼────────────────────┼─────────┼─────────┼─────────┤
# │ 0  │ freshfood-server   │ online  │ 0%      │ 45.3mb  │
# └────┴────────────────────┴─────────┴─────────┴─────────┘
```

**Check Logs**:
```bash
pm2 logs freshfood-server

# Should show server startup messages
```

---

### Step 8: Initialize Models

**Option A: Copy from Android App Assets** (if available):
```bash
cd ~/FreshFood/server
./scripts/init-models.sh
```

Follow prompts to:
1. Enter path to Android app assets (e.g., `../app/src/main/assets/models/`)
2. Enter server URL (e.g., `https://your-domain.com`)

**Option B: Manually Upload Models**:
```bash
cd ~/FreshFood/server

# Upload each model
./scripts/upload-model.sh path/to/fruitid.tflite https://your-domain.com $ADMIN_API_KEY
./scripts/upload-model.sh path/to/apple_ripeness.tflite https://your-domain.com $ADMIN_API_KEY
# ... repeat for all models
```

**Verify Manifest**:
```bash
curl -H "X-API-Key: $CLIENT_API_KEY" https://your-domain.com/manifest.json

# Should return JSON with models array
```

---

### Step 9: Configure Firewall

**Enable UFW** (Uncomplicated Firewall):
```bash
sudo ufw enable
```

**Allow SSH** (port 22):
```bash
sudo ufw allow 22/tcp
```

**Allow HTTP** (port 80):
```bash
sudo ufw allow 80/tcp
```

**Allow HTTPS** (port 443):
```bash
sudo ufw allow 443/tcp
```

**Check Status**:
```bash
sudo ufw status

# Should show:
# Status: active
# 
# To                         Action      From
# --                         ------      ----
# 22/tcp                     ALLOW       Anywhere
# 80/tcp                     ALLOW       Anywhere
# 443/tcp                    ALLOW       Anywhere
```

---

### Step 10: Test Server Deployment

**Health Check**:
```bash
curl https://your-domain.com/health

# Expected: {"status":"ok","uptime":123}
```

**Fetch Manifest**:
```bash
curl -H "X-API-Key: $CLIENT_API_KEY" https://your-domain.com/manifest.json

# Expected: {"models":[...]}
```

**Download Model**:
```bash
curl -H "X-API-Key: $CLIENT_API_KEY" -o test.tflite https://your-domain.com/models/fruitid.tflite

# Should download model file
```

**Verify SHA-256**:
```bash
sha256sum test.tflite

# Compare with manifest sha256 field
```

**Test Upload** (admin endpoint):
```bash
curl -X POST \
  -H "X-API-Key: $ADMIN_API_KEY" \
  -F "file=@test.tflite" \
  https://your-domain.com/admin/upload

# Expected: {"success":true,"model":{"name":"test",...}}
```

---

## Android App Deployment

### Step 1: Configure Production URLs

**Edit `gradle.properties`**:
```properties
# Server URLs (production)
modelUpdateBaseUrl=https://your-domain.com/models/
contributeBaseUrl=https://your-domain.com/api/

# API Key (must match server's CLIENT_API_KEY)
modelUpdateApiKey=<CLIENT_API_KEY from server>
```

**Verify API Key Matches**:
```bash
# Server side
cat ~/FreshFood/server/.env | grep CLIENT_API_KEY

# Android side
cat gradle.properties | grep modelUpdateApiKey

# Should be identical
```

**Security Note**:
- Commit `gradle.properties` only if using private repository
- For public repos, add to `.gitignore` and share via secure channel
- Team members can override in `local.properties` (gitignored)

---

### Step 2: Generate Signing Key

**Create Keystore**:
```bash
keytool -genkey -v \
  -keystore freshfood-release.keystore \
  -alias freshfood \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

**Enter Information**:
- **Keystore password**: (create strong password, save securely)
- **Key password**: (can be same as keystore password)
- **First and Last Name**: Your Name or Company
- **Organizational Unit**: Your Department
- **Organization**: Your Company
- **City**: Your City
- **State**: Your State
- **Country Code**: US (or your country)

**Store Keystore Securely**:
```bash
# Move to safe location (DO NOT commit to git)
mv freshfood-release.keystore ~/secure-keys/

# Back up to secure cloud storage
# AWS S3, Google Drive (encrypted), password manager, etc.
```

**Create `keystore.properties`**:
```bash
# In project root (same level as settings.gradle.kts)
nano keystore.properties
```

**Add Keystore Information**:
```properties
storeFile=../secure-keys/freshfood-release.keystore
storePassword=<your-keystore-password>
keyAlias=freshfood
keyPassword=<your-key-password>
```

**Add to `.gitignore`**:
```bash
echo "keystore.properties" >> .gitignore
echo "*.keystore" >> .gitignore
```

---

### Step 3: Configure Signing in build.gradle.kts

**Edit `app/build.gradle.kts`**:

Add signing config before `buildTypes` block:

```kotlin
android {
    // ... existing configuration

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ... rest of configuration
}
```

---

### Step 4: Update App Version

**Edit `app/build.gradle.kts`**:

```kotlin
defaultConfig {
    applicationId = "com.jody.freshfood"
    minSdk = 24
    targetSdk = 34
    versionCode = 2  // Increment for each release (was 1)
    versionName = "1.1.0"  // Update version string (was 1.0.0)
    
    // ... rest of configuration
}
```

**Version Naming**:
- **versionCode**: Integer, must increment with each release (1, 2, 3, ...)
- **versionName**: String, semantic versioning (1.0.0, 1.1.0, 2.0.0)

---

### Step 5: Build Release APK

**Clean Project**:
```bash
./gradlew clean
```

**Build Release APK**:
```bash
./gradlew assembleRelease
```

**APK Location**:
```
app/build/outputs/apk/release/app-release.apk
```

**Verify Signing**:
```bash
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# Should show:
# jar verified.
```

**Check APK Size**:
```bash
ls -lh app/build/outputs/apk/release/app-release.apk

# Typical size: 20-50MB depending on models
```

---

### Step 6: Test Release Build

**Install on Test Device**:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

**Test OTA Updates**:
1. Launch app
2. Navigate to Settings tab
3. Tap "Check for Updates"
4. Verify uses production server (not localhost)
5. Observe download progress

**Verify Configuration**:
```bash
# Check logcat for server URL
adb logcat | grep ModelUpdateService

# Should show production URL, not 10.0.2.2
```

**Test Model Download and Verification**:
1. Trigger update if available
2. Monitor logcat: `adb logcat | grep ModelDownloader`
3. Verify SHA-256 verification succeeds
4. Confirm model version updates

**Test All App Features**:
- ✅ Scanning (camera and gallery)
- ✅ Model inference (predictions)
- ✅ History (view past scans)
- ✅ Contributions (upload training data)
- ✅ Settings (check for updates, view versions)

---

### Step 7: Prepare for Play Store (Optional)

**Create App Listing**:
1. Log in to [Google Play Console](https://play.google.com/console)
2. Create application
3. Complete store listing:
   - App name: FreshFood
   - Short description (80 chars): ML-powered food freshness detection
   - Full description (4000 chars): Detailed app description
   - Category: Food & Drink
   - Screenshots: 2-8 screenshots (1080x1920 or 1440x2560)
   - Feature graphic: 1024x500 PNG
   - App icon: 512x512 PNG

**Prepare Assets**:
- Screenshots from different screens (Home, Scan, Results, History, Settings)
- Promo video (optional): 30-120 seconds
- Privacy policy URL (required): Host on website or GitHub Pages

**Build App Bundle** (recommended for Play Store):
```bash
./gradlew bundleRelease
```

**Bundle Location**:
```
app/build/outputs/bundle/release/app-release.aab
```

**Upload to Play Console**:
1. Go to "Release" > "Production" (or "Internal Testing" for testing)
2. Create new release
3. Upload `app-release.aab`
4. Set release name: "1.1.0"
5. Add release notes
6. Set rollout percentage (e.g., 20% for gradual rollout)
7. Review and rollout

**Configure Play Store Listing**:
- Title (30 chars): FreshFood - Food Quality Analyzer
- Description: Include features, benefits, how to use
- Screenshots: Add screenshots to listing
- Categorization: Food & Drink
- Content rating: Complete questionnaire (likely "Everyone")
- Target audience: Select age groups
- Pricing: Free or Paid

---

### Step 8: Alternative Distribution

**Direct APK Distribution**:

1. **Host APK on Website**:
   ```bash
   # Upload APK to web hosting
   scp app-release.apk user@your-site.com:/var/www/html/downloads/
   ```

2. **Create Download Page**:
   ```html
   <a href="/downloads/app-release.apk">Download FreshFood v1.1.0</a>
   ```

3. **Share Link**: `https://your-site.com/downloads/app-release.apk`

**Internal Testing** (Firebase App Distribution):

1. Install Firebase CLI: `npm install -g firebase-tools`
2. Login: `firebase login`
3. Initialize: `firebase init appdistribution`
4. Upload APK:
   ```bash
   firebase appdistribution:distribute app-release.apk \
     --app <firebase-app-id> \
     --groups testers \
     --release-notes "Version 1.1.0 - OTA updates"
   ```

**Enterprise Distribution** (MDM):
- Use Mobile Device Management solution (Microsoft Intune, VMware Workspace ONE)
- Upload APK to MDM console
- Deploy to managed devices

---

## Post-Deployment

### Monitoring

**Server Monitoring**:

```bash
# Real-time stats
pm2 monit

# View logs
pm2 logs freshfood-server --lines 100

# View specific log types
pm2 logs freshfood-server --err  # Errors only
pm2 logs freshfood-server --out  # Standard output
```

**nginx Logs**:
```bash
# Access logs
sudo tail -f /var/log/nginx/freshfood_access.log

# Error logs
sudo tail -f /var/log/nginx/freshfood_error.log
```

**Uptime Monitoring**:
- **UptimeRobot**: Free tier, 5-minute checks, https://uptimerobot.com
- **Pingdom**: 1-minute checks, https://pingdom.com
- **StatusCake**: Free tier, 5-minute checks, https://www.statuscake.com

**Set Up Alerts**:
- **PM2 Plus**: Free for 1 server, https://pm2.io
- **AWS CloudWatch**: If using AWS, set up alarms
- **Custom Script**: Email on error via cron job

---

### Maintenance

**Update Dependencies**:

```bash
# Server
cd ~/FreshFood/server
npm update
npm audit fix

# Android
./gradlew dependencyUpdates
# Review output, update libs.versions.toml
```

**Security Patches**:
```bash
# Server OS
sudo apt update && sudo apt upgrade

# Reboot if kernel updated
sudo reboot
```

**Certificate Renewal**:
```bash
# Automatic with certbot, but verify
sudo certbot renew --dry-run

# Manual renewal (if needed)
sudo certbot renew
sudo systemctl reload nginx
```

**Rotate API Keys**:

1. Generate new keys:
   ```bash
   NEW_ADMIN=$(openssl rand -hex 32)
   NEW_CLIENT=$(openssl rand -hex 32)
   ```

2. Update server `.env`:
   ```bash
   nano ~/FreshFood/server/.env
   # Replace API keys
   ```

3. Restart server:
   ```bash
   pm2 restart freshfood-server
   ```

4. Update Android `gradle.properties`:
   ```properties
   modelUpdateApiKey=<NEW_CLIENT_KEY>
   ```

5. Build and release new app version

**Backup Manifest**:
```bash
# Daily backup via cron
crontab -e

# Add line:
0 2 * * * cp ~/FreshFood/server/models/manifest.json ~/backups/manifest-$(date +\%Y\%m\%d).json
```

---

### Scaling

**Horizontal Scaling**:

1. **Load Balancer** (nginx or HAProxy):
   ```nginx
   upstream freshfood_servers {
       server 10.0.0.1:3000;
       server 10.0.0.2:3000;
       server 10.0.0.3:3000;
   }

   server {
       location / {
           proxy_pass http://freshfood_servers;
       }
   }
   ```

2. **Shared File Storage** (NFS or S3):
   - Models stored on shared filesystem
   - All servers access same model files

**CDN Integration**:

1. **CloudFlare**:
   - Change DNS to CloudFlare nameservers
   - Enable "Proxy" (orange cloud)
   - Auto-caching for `/models/*`

2. **AWS CloudFront**:
   - Create distribution
   - Origin: Your domain
   - Cache behavior: Cache `/models/*` for 1 day

**Database** (future enhancement):
- Migrate from file-based manifest to PostgreSQL/MongoDB
- Store model metadata in database
- Keep files on filesystem or S3

**Caching** (Redis):
- Cache manifest responses (5-minute TTL)
- Reduce file I/O
- Install: `sudo apt install redis-server`

---

### Rollback Procedures

**Server Rollback**:

1. **Stop server**:
   ```bash
   pm2 stop freshfood-server
   ```

2. **Checkout previous version**:
   ```bash
   cd ~/FreshFood
   git log --oneline  # Find previous commit
   git checkout <previous-commit-hash>
   ```

3. **Install dependencies**:
   ```bash
   cd server
   npm install
   ```

4. **Start server**:
   ```bash
   pm2 start src/index.js --name freshfood-server
   ```

**Android Rollback**:

1. **Checkout previous version**:
   ```bash
   git checkout <previous-commit-hash>
   ```

2. **Rebuild APK**:
   ```bash
   ./gradlew clean assembleRelease
   ```

3. **Distribute** previous version APK

**Model Rollback**:

1. **Re-upload previous model version**:
   ```bash
   ./scripts/upload-model.sh path/to/previous-model.tflite https://your-domain.com $ADMIN_API_KEY
   ```

2. Manifest automatically increments version (e.g., v3 overwrites v2)

---

## Security Checklist

Before going live, verify all security measures:

- [ ] HTTPS enabled with valid SSL certificate (Let's Encrypt)
- [ ] API keys generated with `openssl rand -hex 32` (64 characters each)
- [ ] ADMIN_API_KEY different from CLIENT_API_KEY
- [ ] API keys stored in environment variables only (not committed to git)
- [ ] Firewall configured (UFW enabled, ports 22/80/443 allowed)
- [ ] SSH key authentication enabled, password auth disabled
- [ ] Non-root user created for server operations
- [ ] Rate limiting enabled (50 req/15min per API key, 100 req/15min per IP)
- [ ] CORS configured with specific origins (not `*` wildcard)
- [ ] nginx security headers enabled (HSTS, CSP, X-Frame-Options, X-Content-Type-Options)
- [ ] Android app uses release signing key (not debug key)
- [ ] ProGuard/R8 enabled for release builds
- [ ] SHA-256 verification active in ModelDownloader
- [ ] Server logs monitored for suspicious activity
- [ ] Backup strategy implemented (manifest, models, database if applicable)
- [ ] Uptime monitoring configured (UptimeRobot, Pingdom, etc.)
- [ ] Alert system configured (email, Slack, PagerDuty)

---

## Troubleshooting Deployment Issues

For detailed troubleshooting, see [docs/TROUBLESHOOTING.md](TROUBLESHOOTING.md).

**Common Issues**:

- **SSL Certificate Errors**: Verify domain DNS, run `sudo certbot renew`
- **nginx 502 Errors**: Check Express server running, verify `proxy_pass` URL
- **API Key Mismatches**: Ensure CLIENT_API_KEY identical on server and Android
- **Model Downloads Fail**: Check nginx forwards X-API-Key header
- **App Can't Connect**: Verify firewall allows port 443, test with curl

---

## References

- **Server Setup Guide**: [server/README.md](../server/README.md)
- **Nginx Configuration**: [server/nginx/README.md](../server/nginx/README.md)
- **Android Testing Guide**: [ANDROID_TESTING.md](ANDROID_TESTING.md)
- **Troubleshooting Guide**: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

---

**Congratulations!** Your FreshFood OTA system is now deployed to production. Monitor logs, test regularly, and keep dependencies updated.
