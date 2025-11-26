# Nginx Setup Guide

This guide covers installing and configuring nginx as a reverse proxy for the FreshFood Model Server.

## Installation

### Ubuntu/Debian
```bash
sudo apt update
sudo apt install nginx
```

### CentOS/RHEL
```bash
sudo yum install epel-release
sudo yum install nginx
```

### macOS
```bash
brew install nginx
```

## SSL Certificate Setup

We recommend using Let's Encrypt for free SSL certificates.

### Install Certbot

**Ubuntu/Debian:**
```bash
sudo apt install certbot python3-certbot-nginx
```

**CentOS/RHEL:**
```bash
sudo yum install certbot python3-certbot-nginx
```

**macOS:**
```bash
brew install certbot
```

### Generate SSL Certificate

```bash
sudo certbot --nginx -d your-domain.com
```

Follow the prompts to:
1. Enter your email address
2. Agree to terms of service
3. Choose whether to redirect HTTP to HTTPS (recommended: yes)

### Auto-Renewal Setup

Certbot automatically installs a systemd timer for certificate renewal. Test it with:

```bash
sudo certbot renew --dry-run
```

Certificates will auto-renew before expiration. You can also manually renew:

```bash
sudo certbot renew
```

### Update Certificate Paths

After generating certificates, update the paths in `nginx.conf`:

```nginx
ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
```

Replace `your-domain.com` with your actual domain name.

## Configuration Deployment

### 1. Copy Configuration File

Copy the nginx.conf to the sites-available directory:

```bash
sudo cp nginx/nginx.conf /etc/nginx/sites-available/freshfood
```

### 2. Update Domain and Paths

Edit the configuration file:

```bash
sudo nano /etc/nginx/sites-available/freshfood
```

Update the following:
- `server_name your-domain.com` (replace with your domain)
- SSL certificate paths
- Any other environment-specific settings

### 3. Create Symbolic Link

Enable the site by creating a symbolic link:

```bash
sudo ln -s /etc/nginx/sites-available/freshfood /etc/nginx/sites-enabled/
```

### 4. Remove Default Site (Optional)

```bash
sudo rm /etc/nginx/sites-enabled/default
```

### 5. Test Configuration

Always test the configuration before reloading:

```bash
sudo nginx -t
```

You should see:
```
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

### 6. Reload Nginx

```bash
sudo systemctl reload nginx
```

### 7. Enable Nginx on Boot

```bash
sudo systemctl enable nginx
```

### 8. Check Status

```bash
sudo systemctl status nginx
```

## Firewall Configuration

### UFW (Ubuntu/Debian)

```bash
# Allow HTTP
sudo ufw allow 80/tcp

# Allow HTTPS
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

### firewalld (CentOS/RHEL)

```bash
# Allow HTTP
sudo firewall-cmd --permanent --add-service=http

# Allow HTTPS
sudo firewall-cmd --permanent --add-service=https

# Reload firewall
sudo firewall-cmd --reload

# Check status
sudo firewall-cmd --list-all
```

## Troubleshooting

### Check Nginx Status

```bash
sudo systemctl status nginx
```

### View Error Logs

```bash
sudo tail -f /var/log/nginx/freshfood_error.log
```

### View Access Logs

```bash
sudo tail -f /var/log/nginx/freshfood_access.log
```

### Common Issues

#### Port 80/443 Already in Use

Check what's using the port:
```bash
sudo netstat -tulpn | grep :80
sudo netstat -tulpn | grep :443
```

Stop the conflicting service or change nginx ports.

#### Certificate Path Errors

Verify certificate files exist:
```bash
sudo ls -la /etc/letsencrypt/live/your-domain.com/
```

#### Permission Issues

Ensure nginx can read certificate files:
```bash
sudo chmod 644 /etc/letsencrypt/live/your-domain.com/fullchain.pem
sudo chmod 600 /etc/letsencrypt/live/your-domain.com/privkey.pem
```

#### Configuration Syntax Errors

Always test configuration before reloading:
```bash
sudo nginx -t
```

Fix any syntax errors reported.

#### Connection Refused

Ensure the Express server is running:
```bash
curl http://localhost:3000/manifest.json
```

If it fails, start the Node.js server first.

## Security Best Practices

### 1. Keep Nginx Updated

```bash
sudo apt update && sudo apt upgrade nginx  # Ubuntu/Debian
sudo yum update nginx                       # CentOS/RHEL
```

### 2. Regularly Renew SSL Certificates

Set up automatic renewal (already configured with certbot):
```bash
sudo certbot renew --dry-run
```

### 3. Monitor Access Logs

Regularly check for suspicious activity:
```bash
sudo tail -f /var/log/nginx/freshfood_access.log
```

Look for:
- Unusual request patterns
- Failed authentication attempts
- High request rates from single IPs

### 4. Configure Fail2Ban

Install fail2ban to protect against brute force attacks:

```bash
sudo apt install fail2ban  # Ubuntu/Debian
sudo yum install fail2ban  # CentOS/RHEL
```

Create nginx jail configuration:
```bash
sudo nano /etc/fail2ban/jail.d/nginx.conf
```

Add:
```ini
[nginx-limit-req]
enabled = true
filter = nginx-limit-req
logpath = /var/log/nginx/freshfood_error.log
maxretry = 5
bantime = 3600
```

Restart fail2ban:
```bash
sudo systemctl restart fail2ban
```

### 5. Use Strong SSL Ciphers

The provided configuration already uses strong ciphers. Test your SSL configuration:

```bash
# Using SSL Labs (recommended)
# Visit: https://www.ssllabs.com/ssltest/

# Or use openssl
openssl s_client -connect your-domain.com:443 -tls1_2
```

### 6. Disable Unused HTTP Methods

Add to server block in nginx.conf:
```nginx
if ($request_method !~ ^(GET|POST|HEAD)$ ) {
    return 405;
}
```

## Performance Tuning

### Worker Processes

Edit `/etc/nginx/nginx.conf`:
```nginx
worker_processes auto;  # Use CPU core count
worker_connections 1024;
```

### Gzip Compression

Add to http block:
```nginx
gzip on;
gzip_vary on;
gzip_min_length 1024;
gzip_types text/plain text/css application/json application/javascript;
```

Note: Binary .tflite files won't compress well, so gzip is less beneficial for model downloads.

### Connection Keep-Alive

```nginx
keepalive_timeout 65;
keepalive_requests 100;
```

## Monitoring

### Check Real-Time Connections

```bash
watch -n 1 "netstat -an | grep :443 | wc -l"
```

### Analyze Access Logs

```bash
# Top IPs
sudo awk '{print $1}' /var/log/nginx/freshfood_access.log | sort | uniq -c | sort -rn | head -10

# Top URLs
sudo awk '{print $7}' /var/log/nginx/freshfood_access.log | sort | uniq -c | sort -rn | head -10

# HTTP status codes
sudo awk '{print $9}' /var/log/nginx/freshfood_access.log | sort | uniq -c | sort -rn
```

### Log Rotation

Nginx typically includes logrotate configuration. Verify:
```bash
cat /etc/logrotate.d/nginx
```

## Additional Resources

- [Nginx Documentation](https://nginx.org/en/docs/)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [Nginx Security Tips](https://nginx.org/en/docs/http/ngx_http_ssl_module.html)
