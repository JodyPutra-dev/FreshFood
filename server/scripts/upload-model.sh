#!/bin/bash

# FreshFood Model Upload Script
# Uploads a TFLite model to the server

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
VERBOSE=false
DRY_RUN=false

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

# Function to display usage
usage() {
    echo "Usage: $0 [OPTIONS] <model_file> [server_url] [api_key]"
    echo ""
    echo "Upload a TFLite model to the FreshFood Model Server"
    echo ""
    echo "Arguments:"
    echo "  model_file    Path to .tflite model file (required)"
    echo "  server_url    Server URL (optional, default: from env or prompt)"
    echo "  api_key       API key (optional, default: from env or prompt)"
    echo ""
    echo "Options:"
    echo "  -v, --verbose    Enable verbose output"
    echo "  -d, --dry-run    Validate without uploading"
    echo "  -h, --help       Display this help message"
    echo ""
    echo "Environment Variables:"
    echo "  SERVER_URL       Default server URL"
    echo "  ADMIN_API_KEY    Default API key"
    echo ""
    echo "Examples:"
    echo "  $0 fruitid.tflite"
    echo "  $0 fruitid.tflite https://your-domain.com your-api-key"
    echo "  $0 -v fruitid.tflite"
    echo "  $0 -d fruitid.tflite  # Dry run"
    exit 1
}

# Parse options
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            break
            ;;
    esac
done

# Check if model file is provided
if [ $# -lt 1 ]; then
    print_error "Model file path is required"
    usage
fi

MODEL_FILE="$1"
SERVER_URL="${2:-${SERVER_URL}}"
API_KEY="${3:-${ADMIN_API_KEY}}"

# Validate model file exists
if [ ! -f "$MODEL_FILE" ]; then
    print_error "Model file not found: $MODEL_FILE"
    exit 1
fi

# Validate file extension
if [[ ! "$MODEL_FILE" =~ \.tflite$ ]]; then
    print_error "Invalid file extension. Only .tflite files are allowed"
    exit 1
fi

# Get file size
FILE_SIZE=$(stat -f%z "$MODEL_FILE" 2>/dev/null || stat -c%s "$MODEL_FILE" 2>/dev/null)
FILE_SIZE_MB=$((FILE_SIZE / 1024 / 1024))

print_info "Model file: $MODEL_FILE"
print_info "File size: ${FILE_SIZE_MB}MB"

# Validate file size (default max: 100MB)
MAX_SIZE_MB=${MAX_FILE_SIZE_MB:-100}
if [ $FILE_SIZE_MB -gt $MAX_SIZE_MB ]; then
    print_error "File size (${FILE_SIZE_MB}MB) exceeds maximum allowed size (${MAX_SIZE_MB}MB)"
    exit 1
fi

# Get server URL if not provided
if [ -z "$SERVER_URL" ]; then
    echo -n "Enter server URL: "
    read SERVER_URL
fi

# Validate server URL
if [ -z "$SERVER_URL" ]; then
    print_error "Server URL is required"
    exit 1
fi

# Get API key if not provided
if [ -z "$API_KEY" ]; then
    echo -n "Enter API key: "
    read -s API_KEY
    echo ""
fi

# Validate API key
if [ -z "$API_KEY" ]; then
    print_error "API key is required"
    exit 1
fi

if [ ${#API_KEY} -lt 16 ]; then
    print_warning "API key seems short (${#API_KEY} characters). Are you sure it's correct?"
fi

# Extract model name
MODEL_NAME=$(basename "$MODEL_FILE" .tflite)
print_info "Model name: $MODEL_NAME"

# Dry run mode
if [ "$DRY_RUN" = true ]; then
    print_info "DRY RUN MODE - No upload will be performed"
    print_info "Would upload to: ${SERVER_URL}/admin/upload"
    print_success "Validation passed"
    exit 0
fi

# Confirm upload
echo ""
print_warning "Ready to upload:"
echo "  File: $MODEL_FILE (${FILE_SIZE_MB}MB)"
echo "  Server: $SERVER_URL"
echo "  Model: $MODEL_NAME"
echo ""
read -p "Continue? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "Upload cancelled"
    exit 0
fi

# Perform upload
print_info "Uploading model to server..."

UPLOAD_URL="${SERVER_URL}/admin/upload"

if [ "$VERBOSE" = true ]; then
    print_info "Upload URL: $UPLOAD_URL"
    CURL_OPTS="--progress-bar -v"
else
    CURL_OPTS="--progress-bar"
fi

# Execute curl upload
RESPONSE=$(curl $CURL_OPTS -w "\n%{http_code}" \
    -X POST \
    -H "X-API-Key: $API_KEY" \
    -F "file=@$MODEL_FILE" \
    "$UPLOAD_URL" 2>&1)

# Extract HTTP status code (last line)
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$VERBOSE" = true ]; then
    print_info "HTTP Status: $HTTP_CODE"
    print_info "Response: $RESPONSE_BODY"
fi

# Check HTTP status code
if [ "$HTTP_CODE" -eq 200 ]; then
    print_success "Model uploaded successfully!"
    
    # Parse and display response details
    if command -v jq &> /dev/null; then
        echo ""
        echo "$RESPONSE_BODY" | jq '.'
    else
        echo "$RESPONSE_BODY"
    fi
    
    exit 0
elif [ "$HTTP_CODE" -eq 401 ]; then
    print_error "Authentication failed. Invalid API key."
    exit 1
elif [ "$HTTP_CODE" -eq 413 ]; then
    print_error "File too large. Server rejected upload."
    exit 1
elif [ "$HTTP_CODE" -eq 400 ]; then
    print_error "Bad request. Check file format and parameters."
    echo "$RESPONSE_BODY"
    exit 1
else
    print_error "Upload failed with HTTP status $HTTP_CODE"
    echo "$RESPONSE_BODY"
    exit 1
fi
