#!/bin/bash

# FreshFood Server Startup Script
# This script starts the Node.js Express server for model distribution

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   FreshFood Model Server Startup${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: Node.js is not installed${NC}"
    echo "Please install Node.js 18.x or higher"
    echo "Visit: https://nodejs.org/"
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo -e "${RED}Error: Node.js version 18 or higher required${NC}"
    echo "Current version: $(node -v)"
    exit 1
fi

echo -e "${GREEN}✓ Node.js version: $(node -v)${NC}"

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Warning: .env file not found${NC}"
    echo "Creating .env from .env.example..."
    
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo -e "${GREEN}✓ Created .env file${NC}"
        echo -e "${YELLOW}⚠ Please edit .env and set your configuration (especially CLIENT_API_KEY)${NC}"
        echo ""
    else
        echo -e "${RED}Error: .env.example not found${NC}"
        exit 1
    fi
fi

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}Installing dependencies...${NC}"
    npm install
    echo -e "${GREEN}✓ Dependencies installed${NC}"
    echo ""
fi

# Check if models directory exists
if [ ! -d "models" ]; then
    echo -e "${YELLOW}Creating models directory...${NC}"
    mkdir -p models
    echo -e "${GREEN}✓ Models directory created${NC}"
    echo ""
fi

# Check if manifest.json exists
if [ ! -f "models/manifest.json" ]; then
    echo -e "${YELLOW}Warning: models/manifest.json not found${NC}"
    echo "You'll need to generate it using:"
    echo "  node scripts/generate-manifest.js --server-url http://localhost:3000"
    echo ""
fi

# Parse command line arguments
MODE="production"
USE_PM2=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --dev)
            MODE="development"
            shift
            ;;
        --pm2)
            USE_PM2=true
            shift
            ;;
        --help)
            echo "Usage: ./start-server.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --dev       Start in development mode (with nodemon)"
            echo "  --pm2       Start using PM2 process manager"
            echo "  --help      Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./start-server.sh              # Start in production mode"
            echo "  ./start-server.sh --dev        # Start in development mode"
            echo "  ./start-server.sh --pm2        # Start with PM2"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${BLUE}Starting server...${NC}"
echo ""

# Start server based on mode
if [ "$USE_PM2" = true ]; then
    # Check if PM2 is installed
    if ! command -v pm2 &> /dev/null; then
        echo -e "${RED}Error: PM2 is not installed${NC}"
        echo "Install PM2 globally: npm install -g pm2"
        exit 1
    fi
    
    echo -e "${GREEN}Starting with PM2...${NC}"
    pm2 start src/index.js --name freshfood-server
    echo ""
    echo -e "${GREEN}✓ Server started with PM2${NC}"
    echo ""
    echo "Useful PM2 commands:"
    echo "  pm2 logs freshfood-server    # View logs"
    echo "  pm2 stop freshfood-server    # Stop server"
    echo "  pm2 restart freshfood-server # Restart server"
    echo "  pm2 delete freshfood-server  # Remove from PM2"
    
elif [ "$MODE" = "development" ]; then
    echo -e "${GREEN}Starting in development mode (with auto-reload)...${NC}"
    echo ""
    npm run dev
    
else
    echo -e "${GREEN}Starting in production mode...${NC}"
    echo ""
    npm start
fi
