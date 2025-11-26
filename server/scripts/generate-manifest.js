#!/usr/bin/env node

/**
 * FreshFood Manifest Generator Script
 * Generates manifest.json from existing .tflite files in models directory
 */

const fs = require('fs').promises;
const path = require('path');
const { calculateSHA256 } = require('../src/utils/hash');

// ANSI color codes
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m'
};

// Configuration
const MODELS_DIR = path.join(__dirname, '..', 'models');
const DEFAULT_OUTPUT = path.join(MODELS_DIR, 'manifest.json');

/**
 * Print colored message
 */
function print(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`);
}

/**
 * Print section header
 */
function printHeader(message) {
  console.log('');
  print('='.repeat(60), 'blue');
  print(message, 'blue');
  print('='.repeat(60), 'blue');
}

/**
 * Parse command line arguments
 */
function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    serverUrl: null,
    incrementVersion: false,
    output: DEFAULT_OUTPUT,
    help: false
  };
  
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    
    if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg === '--server-url') {
      options.serverUrl = args[++i];
    } else if (arg === '--increment-version') {
      options.incrementVersion = true;
    } else if (arg === '--output') {
      options.output = args[++i];
    }
  }
  
  return options;
}

/**
 * Show usage instructions
 */
function showUsage() {
  console.log(`
FreshFood Manifest Generator

Generates manifest.json from existing .tflite model files.

Usage: node generate-manifest.js --server-url <url> [OPTIONS]

Required:
  --server-url <url>      Server URL for generating download URLs
                         Example: https://your-domain.com
                         Example: http://localhost:3000

Options:
  --increment-version     Increment version for existing models (default: start at 1)
  --output <path>         Output path for manifest.json (default: models/manifest.json)
  --help, -h             Display this help message

Examples:
  # Generate manifest for production
  node generate-manifest.js --server-url https://your-domain.com

  # Generate manifest for local testing
  node generate-manifest.js --server-url http://localhost:3000

  # Increment versions for existing models
  node generate-manifest.js --server-url https://your-domain.com --increment-version

  # Specify custom output path
  node generate-manifest.js --server-url https://your-domain.com --output ./custom-manifest.json

Workflow:
  1. Place .tflite files in models/ directory
  2. Run this script to generate manifest
  3. Verify with: node verify-manifest.js
  4. Restart server to serve updated manifest
  5. Android app will detect new models on next update check
`);
}

/**
 * Read existing manifest if it exists
 */
async function readExistingManifest(manifestPath) {
  try {
    const content = await fs.readFile(manifestPath, 'utf-8');
    const manifest = JSON.parse(content);
    
    if (!manifest.models || !Array.isArray(manifest.models)) {
      print('⚠ Existing manifest has invalid structure, starting fresh', 'yellow');
      return { models: [] };
    }
    
    return manifest;
  } catch (error) {
    // Manifest doesn't exist or is invalid, start fresh
    return { models: [] };
  }
}

/**
 * Main generation function
 */
async function generateManifest(options) {
  printHeader('FreshFood Manifest Generator');
  
  try {
    // Step 1: Validate server URL
    print('\n[1] Validating server URL...', 'blue');
    
    if (!options.serverUrl) {
      print('✗ Server URL is required', 'red');
      print('  Use --server-url <url> to specify the server URL', 'yellow');
      print('  Example: --server-url https://your-domain.com', 'yellow');
      return 1;
    }
    
    // Remove trailing slash from server URL
    const serverUrl = options.serverUrl.replace(/\/$/, '');
    print(`✓ Server URL: ${serverUrl}`, 'green');
    
    // Step 2: Check models directory
    print('\n[2] Checking models directory...', 'blue');
    
    try {
      await fs.access(MODELS_DIR);
      print(`✓ Models directory exists: ${MODELS_DIR}`, 'green');
    } catch (error) {
      print('✗ Models directory not found', 'red');
      print(`  Expected: ${MODELS_DIR}`, 'yellow');
      return 1;
    }
    
    // Step 3: Scan for .tflite files
    print('\n[3] Scanning for .tflite files...', 'blue');
    
    const files = await fs.readdir(MODELS_DIR);
    const tfliteFiles = files.filter(f => f.endsWith('.tflite'));
    
    if (tfliteFiles.length === 0) {
      print('✗ No .tflite files found in models directory', 'red');
      print('  Place your model files in the models/ directory first', 'yellow');
      return 1;
    }
    
    print(`✓ Found ${tfliteFiles.length} .tflite file(s):`, 'green');
    tfliteFiles.forEach(f => print(`    - ${f}`, 'cyan'));
    
    // Step 4: Read existing manifest (if increment mode)
    let existingManifest = { models: [] };
    
    if (options.incrementVersion) {
      print('\n[4] Reading existing manifest...', 'blue');
      existingManifest = await readExistingManifest(options.output);
      
      if (existingManifest.models.length > 0) {
        print(`✓ Found ${existingManifest.models.length} existing model(s)`, 'green');
      } else {
        print('⚠ No existing manifest found, starting with version 1', 'yellow');
      }
    }
    
    // Step 5: Generate manifest entries
    const stepNum = options.incrementVersion ? 5 : 4;
    print(`\n[${stepNum}] Generating manifest entries...`, 'blue');
    
    const manifest = { models: [] };
    
    for (const filename of tfliteFiles) {
      const modelName = filename.replace('.tflite', '');
      const modelPath = path.join(MODELS_DIR, filename);
      
      print(`\n  Processing ${filename}...`, 'cyan');
      
      // Calculate SHA-256 hash
      print('    Computing SHA-256...', 'cyan');
      const sha256 = await calculateSHA256(modelPath);
      print(`    ✓ SHA-256: ${sha256}`, 'green');
      
      // Determine version
      let version = 1;
      
      if (options.incrementVersion) {
        const existingModel = existingManifest.models.find(m => m.name === modelName);
        if (existingModel) {
          version = existingModel.version + 1;
          print(`    ✓ Version: ${version} (incremented from ${existingModel.version})`, 'green');
        } else {
          print(`    ✓ Version: ${version} (new model)`, 'green');
        }
      } else {
        print(`    ✓ Version: ${version}`, 'green');
      }
      
      // Generate download URL
      const downloadUrl = `${serverUrl}/models/${filename}`;
      
      // Add to manifest
      manifest.models.push({
        name: modelName,
        version: version,
        downloadUrl: downloadUrl,
        sha256: sha256
      });
      
      print(`    ✓ Added to manifest`, 'green');
    }
    
    // Step 6: Write manifest to file
    const writeStepNum = options.incrementVersion ? 6 : 5;
    print(`\n[${writeStepNum}] Writing manifest to file...`, 'blue');
    
    const manifestContent = JSON.stringify(manifest, null, 2);
    await fs.writeFile(options.output, manifestContent, 'utf-8');
    
    print(`✓ Manifest written to: ${options.output}`, 'green');
    
    // Summary
    printHeader('Generation Summary');
    
    print(`\n✓ Successfully generated manifest`, 'green');
    print(`  Models: ${manifest.models.length}`, 'cyan');
    print(`  Output: ${options.output}`, 'cyan');
    
    print('\nModel details:', 'blue');
    manifest.models.forEach(model => {
      print(`  • ${model.name} (v${model.version})`, 'cyan');
      print(`    URL: ${model.downloadUrl}`, 'cyan');
      print(`    SHA: ${model.sha256.substring(0, 16)}...`, 'cyan');
    });
    
    print('\nNext steps:', 'yellow');
    print('  1. Verify manifest: node scripts/verify-manifest.js', 'yellow');
    print('  2. Restart server to serve updated manifest', 'yellow');
    print('  3. Android app will detect new models on next update check', 'yellow');
    
    return 0;
    
  } catch (error) {
    print('\n✗ Generation failed with error', 'red');
    print(`Error: ${error.message}`, 'yellow');
    console.error(error);
    return 1;
  }
}

// Main execution
const options = parseArgs();

if (options.help) {
  showUsage();
  process.exit(0);
}

generateManifest(options)
  .then(exitCode => {
    process.exit(exitCode);
  })
  .catch(error => {
    console.error('Unexpected error:', error);
    process.exit(1);
  });
