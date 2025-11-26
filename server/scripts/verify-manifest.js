#!/usr/bin/env node

/**
 * FreshFood Manifest Verification Script
 * Validates manifest.json integrity and consistency with actual model files
 */

const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');

// ANSI color codes
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m'
};

// Parse command line arguments
const args = process.argv.slice(2);
const FIX_MODE = args.includes('--fix');
const VERBOSE = args.includes('--verbose') || args.includes('-v');

// Configuration
const MANIFEST_PATH = path.join(__dirname, '..', 'models', 'manifest.json');
const MODELS_DIR = path.join(__dirname, '..', 'models');

/**
 * Calculate SHA-256 hash of a file
 */
async function calculateSHA256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = require('fs').createReadStream(filePath);
    
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex').toLowerCase()));
    stream.on('error', reject);
  });
}

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
 * Main verification function
 */
async function verifyManifest() {
  printHeader('FreshFood Manifest Verification');
  
  let hasErrors = false;
  let hasWarnings = false;
  const fixes = [];
  
  try {
    // Step 1: Check if manifest exists
    print('\n[1] Checking manifest file...', 'blue');
    try {
      await fs.access(MANIFEST_PATH);
      print('✓ Manifest file exists', 'green');
    } catch (error) {
      print('✗ Manifest file not found', 'red');
      print(`  Expected: ${MANIFEST_PATH}`, 'yellow');
      return 1;
    }
    
    // Step 2: Parse manifest JSON
    print('\n[2] Parsing manifest JSON...', 'blue');
    let manifest;
    try {
      const manifestContent = await fs.readFile(MANIFEST_PATH, 'utf-8');
      manifest = JSON.parse(manifestContent);
      print('✓ Valid JSON structure', 'green');
    } catch (error) {
      print('✗ Failed to parse JSON', 'red');
      print(`  Error: ${error.message}`, 'yellow');
      return 1;
    }
    
    // Step 3: Validate manifest structure
    print('\n[3] Validating manifest structure...', 'blue');
    if (!manifest.models || !Array.isArray(manifest.models)) {
      print('✗ Invalid structure: missing or invalid "models" array', 'red');
      return 1;
    }
    print(`✓ Valid structure with ${manifest.models.length} model(s)`, 'green');
    
    // Step 4: Validate each model entry
    print('\n[4] Validating model entries...', 'blue');
    
    for (let i = 0; i < manifest.models.length; i++) {
      const model = manifest.models[i];
      const modelIndex = i + 1;
      
      if (VERBOSE) {
        print(`\n  Checking model ${modelIndex}: ${model.name || 'UNNAMED'}`, 'blue');
      }
      
      // Check required fields
      if (!model.name || typeof model.name !== 'string') {
        print(`  ✗ Model ${modelIndex}: Missing or invalid "name"`, 'red');
        hasErrors = true;
      }
      
      if (typeof model.version !== 'number' || model.version < 1) {
        print(`  ✗ Model ${modelIndex}: Invalid "version" (${model.version})`, 'red');
        hasErrors = true;
      }
      
      if (!model.downloadUrl || typeof model.downloadUrl !== 'string') {
        print(`  ✗ Model ${modelIndex}: Missing or invalid "downloadUrl"`, 'red');
        hasErrors = true;
      }
      
      if (!model.sha256 || typeof model.sha256 !== 'string') {
        print(`  ✗ Model ${modelIndex}: Missing or invalid "sha256"`, 'red');
        hasErrors = true;
      } else if (model.sha256.length !== 64) {
        print(`  ✗ Model ${modelIndex}: Invalid SHA-256 length (${model.sha256.length}, expected 64)`, 'red');
        hasErrors = true;
      }
      
      if (VERBOSE && !hasErrors) {
        print(`  ✓ Model ${modelIndex}: All required fields valid`, 'green');
      }
    }
    
    if (!hasErrors) {
      print('✓ All model entries have valid structure', 'green');
    }
    
    // Step 5: Check for duplicate model names
    print('\n[5] Checking for duplicate model names...', 'blue');
    const modelNames = manifest.models.map(m => m.name);
    const duplicates = modelNames.filter((name, index) => modelNames.indexOf(name) !== index);
    
    if (duplicates.length > 0) {
      print(`✗ Found duplicate model names: ${duplicates.join(', ')}`, 'red');
      hasErrors = true;
    } else {
      print('✓ No duplicate model names', 'green');
    }
    
    // Step 6: Check if model files exist and verify hashes
    print('\n[6] Verifying model files and SHA-256 hashes...', 'blue');
    
    for (const model of manifest.models) {
      const modelFile = `${model.name}.tflite`;
      const modelPath = path.join(MODELS_DIR, modelFile);
      
      if (VERBOSE) {
        print(`\n  Checking ${modelFile}...`, 'blue');
      }
      
      // Check if file exists
      try {
        await fs.access(modelPath);
        if (VERBOSE) {
          print(`    ✓ File exists`, 'green');
        }
      } catch (error) {
        print(`  ✗ ${modelFile}: File not found`, 'red');
        hasErrors = true;
        continue;
      }
      
      // Calculate and verify SHA-256
      try {
        const actualHash = await calculateSHA256(modelPath);
        
        if (actualHash !== model.sha256) {
          print(`  ✗ ${modelFile}: SHA-256 mismatch`, 'red');
          print(`    Expected: ${model.sha256}`, 'yellow');
          print(`    Actual:   ${actualHash}`, 'yellow');
          hasErrors = true;
          
          if (FIX_MODE) {
            fixes.push({
              name: model.name,
              oldHash: model.sha256,
              newHash: actualHash
            });
          }
        } else {
          if (VERBOSE) {
            print(`    ✓ SHA-256 verified`, 'green');
          }
        }
      } catch (error) {
        print(`  ✗ ${modelFile}: Failed to calculate hash`, 'red');
        print(`    Error: ${error.message}`, 'yellow');
        hasErrors = true;
      }
    }
    
    if (!hasErrors) {
      print('✓ All model files verified', 'green');
    }
    
    // Step 7: Check for orphaned files
    print('\n[7] Checking for orphaned .tflite files...', 'blue');
    
    const filesInDir = await fs.readdir(MODELS_DIR);
    const tfliteFiles = filesInDir.filter(f => f.endsWith('.tflite'));
    const manifestModelFiles = manifest.models.map(m => `${m.name}.tflite`);
    
    const orphanedFiles = tfliteFiles.filter(f => !manifestModelFiles.includes(f));
    
    if (orphanedFiles.length > 0) {
      print(`⚠ Found ${orphanedFiles.length} orphaned file(s):`, 'yellow');
      orphanedFiles.forEach(f => print(`    - ${f}`, 'yellow'));
      hasWarnings = true;
    } else {
      print('✓ No orphaned files', 'green');
    }
    
    // Step 8: Apply fixes if requested
    if (FIX_MODE && fixes.length > 0) {
      print('\n[8] Applying fixes...', 'blue');
      
      for (const fix of fixes) {
        const modelIndex = manifest.models.findIndex(m => m.name === fix.name);
        if (modelIndex !== -1) {
          manifest.models[modelIndex].sha256 = fix.newHash;
          print(`  ✓ Updated SHA-256 for ${fix.name}`, 'green');
        }
      }
      
      // Write updated manifest
      const manifestContent = JSON.stringify(manifest, null, 2);
      await fs.writeFile(MANIFEST_PATH, manifestContent, 'utf-8');
      print('\n✓ Manifest updated successfully', 'green');
    }
    
    // Summary
    printHeader('Verification Summary');
    
    if (hasErrors) {
      print('\n✗ Verification FAILED with errors', 'red');
      if (FIX_MODE && fixes.length > 0) {
        print(`  Fixed ${fixes.length} hash mismatch(es)`, 'green');
        print('  Run verification again to ensure all issues are resolved', 'yellow');
      } else if (!FIX_MODE && fixes.length > 0) {
        print(`  Run with --fix flag to automatically update ${fixes.length} hash(es)`, 'yellow');
      }
      return 1;
    } else if (hasWarnings) {
      print('\n⚠ Verification completed with warnings', 'yellow');
      print('  All critical checks passed', 'green');
      return 0;
    } else {
      print('\n✓ Verification PASSED', 'green');
      print('  All checks passed successfully', 'green');
      return 0;
    }
    
  } catch (error) {
    print('\n✗ Verification failed with unexpected error', 'red');
    print(`Error: ${error.message}`, 'yellow');
    if (VERBOSE) {
      console.error(error);
    }
    return 1;
  }
}

// Display usage
function showUsage() {
  console.log(`
FreshFood Manifest Verification Script

Usage: node verify-manifest.js [OPTIONS]

Options:
  --fix       Automatically update manifest with correct SHA-256 hashes
  --verbose   Show detailed information for each model
  -v          Alias for --verbose
  --help      Display this help message

Examples:
  node verify-manifest.js                # Verify manifest
  node verify-manifest.js --verbose      # Verify with detailed output
  node verify-manifest.js --fix          # Verify and fix hash mismatches
  node verify-manifest.js --fix --verbose # Fix with detailed output
`);
  process.exit(0);
}

// Check for help flag
if (args.includes('--help') || args.includes('-h')) {
  showUsage();
}

// Run verification
verifyManifest()
  .then(exitCode => {
    process.exit(exitCode);
  })
  .catch(error => {
    console.error('Unexpected error:', error);
    process.exit(1);
  });
