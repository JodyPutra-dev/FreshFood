#!/bin/bash

# FreshFood Model Initialization Script
# Copies bundled models from Android app assets to server models directory

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$SERVER_DIR")"

# Paths
ANDROID_ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets/models"
SERVER_MODELS_DIR="$SERVER_DIR/models"
MANIFEST_PATH="$SERVER_MODELS_DIR/manifest.json"

# Banner
echo ""
echo "=========================================================="
echo "  FreshFood Model Initialization Script"
echo "=========================================================="
echo ""

print_info "Project root: $PROJECT_ROOT"
print_info "Android assets: $ANDROID_ASSETS_DIR"
print_info "Server models: $SERVER_MODELS_DIR"
echo ""

# Check if Android assets directory exists
if [ ! -d "$ANDROID_ASSETS_DIR" ]; then
    print_error "Android assets directory not found: $ANDROID_ASSETS_DIR"
    print_error "Make sure you're running this script from the server directory"
    exit 1
fi

# Check if server models directory exists, create if not
if [ ! -d "$SERVER_MODELS_DIR" ]; then
    print_warning "Server models directory not found, creating..."
    mkdir -p "$SERVER_MODELS_DIR"
    print_success "Created directory: $SERVER_MODELS_DIR"
fi

# Get list of .tflite files in Android assets
print_info "Scanning Android assets directory..."
TFLITE_FILES=($(find "$ANDROID_ASSETS_DIR" -name "*.tflite" -type f))

if [ ${#TFLITE_FILES[@]} -eq 0 ]; then
    print_warning "No .tflite files found in Android assets directory"
    exit 0
fi

print_success "Found ${#TFLITE_FILES[@]} model file(s)"
echo ""

# Read existing manifest or create new one
if [ -f "$MANIFEST_PATH" ]; then
    print_info "Backing up existing manifest..."
    cp "$MANIFEST_PATH" "$MANIFEST_PATH.backup"
    print_success "Backup created: $MANIFEST_PATH.backup"
    
    # Read existing manifest
    EXISTING_MANIFEST=$(cat "$MANIFEST_PATH")
else
    print_info "Creating new manifest..."
    EXISTING_MANIFEST='{"models":[]}'
fi

# Prompt for server URL
echo ""
print_info "Enter the server URL (e.g., https://your-domain.com or http://localhost:3000)"
read -p "Server URL: " SERVER_URL

if [ -z "$SERVER_URL" ]; then
    print_error "Server URL is required"
    exit 1
fi

# Remove trailing slash from URL
SERVER_URL="${SERVER_URL%/}"

# Initialize manifest array
MANIFEST_MODELS="[]"

# Process each model file
echo ""
print_info "Processing model files..."
echo ""

for TFLITE_FILE in "${TFLITE_FILES[@]}"; do
    # Get filename and model name
    FILENAME=$(basename "$TFLITE_FILE")
    MODEL_NAME="${FILENAME%.tflite}"
    DEST_PATH="$SERVER_MODELS_DIR/$FILENAME"
    
    print_info "Processing: $MODEL_NAME"
    
    # Check if file already exists in server directory
    if [ -f "$DEST_PATH" ]; then
        print_warning "  File already exists: $FILENAME"
        read -p "  Overwrite? (y/N): " -n 1 -r
        echo ""
        
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "  Skipped: $MODEL_NAME"
            echo ""
            continue
        fi
    fi
    
    # Copy file
    print_info "  Copying file..."
    cp "$TFLITE_FILE" "$DEST_PATH"
    print_success "  Copied: $FILENAME"
    
    # Calculate SHA-256 hash
    print_info "  Calculating SHA-256 hash..."
    
    # Use appropriate command based on OS
    if command -v sha256sum &> /dev/null; then
        SHA256=$(sha256sum "$DEST_PATH" | awk '{print $1}')
    elif command -v shasum &> /dev/null; then
        SHA256=$(shasum -a 256 "$DEST_PATH" | awk '{print $1}')
    else
        print_error "  No SHA-256 command found (sha256sum or shasum)"
        exit 1
    fi
    
    print_success "  SHA-256: $SHA256"
    
    # Construct download URL
    DOWNLOAD_URL="$SERVER_URL/models/$FILENAME"
    
    # Add to manifest (version 1 for new models)
    print_info "  Adding to manifest (version 1)"
    
    # Use Node.js to update manifest
    node -e "
        const fs = require('fs');
        const manifestPath = '$MANIFEST_PATH';
        
        // Read existing manifest
        let manifest = { models: [] };
        if (fs.existsSync(manifestPath)) {
            manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
        }
        
        // Check if model already exists
        const existingIndex = manifest.models.findIndex(m => m.name === '$MODEL_NAME');
        
        if (existingIndex !== -1) {
            // Update existing model
            manifest.models[existingIndex] = {
                name: '$MODEL_NAME',
                version: manifest.models[existingIndex].version + 1,
                downloadUrl: '$DOWNLOAD_URL',
                sha256: '$SHA256'
            };
            console.log('  Updated existing model (incremented version)');
        } else {
            // Add new model
            manifest.models.push({
                name: '$MODEL_NAME',
                version: 1,
                downloadUrl: '$DOWNLOAD_URL',
                sha256: '$SHA256'
            });
            console.log('  Added new model');
        }
        
        // Write manifest
        fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    "
    
    print_success "  Completed: $MODEL_NAME"
    echo ""
done

# Display summary
echo ""
echo "=========================================================="
print_success "Initialization Complete"
echo "=========================================================="
echo ""

print_info "Server models directory: $SERVER_MODELS_DIR"
print_info "Manifest file: $MANIFEST_PATH"

echo ""
print_info "Models initialized:"
ls -lh "$SERVER_MODELS_DIR"/*.tflite 2>/dev/null || print_warning "No model files found"

echo ""
print_info "Manifest contents:"
cat "$MANIFEST_PATH" | (command -v jq &> /dev/null && jq '.' || cat)

echo ""
print_success "Next steps:"
echo "  1. Review the manifest.json file"
echo "  2. Update SERVER_URL in .env if needed"
echo "  3. Start the server: npm start"
echo "  4. Test the endpoint: curl $SERVER_URL/manifest.json"
echo ""
