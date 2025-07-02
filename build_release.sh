#!/bin/bash

# WiMap Release Build Script
# This script builds a production-ready release APK and AAB

set -e  # Exit on any error

echo "🚀 WiMap Release Build Script"
echo "================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if we're in the correct directory
if [ ! -f "app/build.gradle.kts" ]; then
    echo -e "${RED}Error: Please run this script from the project root directory${NC}"
    exit 1
fi

# Check if keystore exists
if [ ! -f "keystore.properties" ]; then
    echo -e "${RED}Error: keystore.properties not found${NC}"
    echo "Please ensure the signing keystore is configured"
    exit 1
fi

if [ ! -f "wimap_release.keystore" ]; then
    echo -e "${RED}Error: wimap_release.keystore not found${NC}"
    echo "Please ensure the release keystore file exists"
    exit 1
fi

echo -e "${BLUE}Step 1: Cleaning previous builds...${NC}"
./gradlew clean

echo -e "${BLUE}Step 2: Running tests...${NC}"
./gradlew test || {
    echo -e "${YELLOW}Warning: Some tests failed, but continuing with build...${NC}"
}

echo -e "${BLUE}Step 3: Building release APK...${NC}"
./gradlew assembleRelease

echo -e "${BLUE}Step 4: Building release App Bundle (AAB)...${NC}"
./gradlew bundleRelease

# Check if builds succeeded
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo -e "${GREEN}✅ Release APK built successfully!${NC}"
    echo "Location: app/build/outputs/apk/release/app-release.apk"
    
    # Get APK size
    APK_SIZE=$(du -h "app/build/outputs/apk/release/app-release.apk" | cut -f1)
    echo "APK Size: $APK_SIZE"
else
    echo -e "${RED}❌ Release APK build failed${NC}"
fi

if [ -f "app/build/outputs/bundle/release/app-release.aab" ]; then
    echo -e "${GREEN}✅ Release AAB built successfully!${NC}"
    echo "Location: app/build/outputs/bundle/release/app-release.aab"
    
    # Get AAB size
    AAB_SIZE=$(du -h "app/build/outputs/bundle/release/app-release.aab" | cut -f1)
    echo "AAB Size: $AAB_SIZE"
else
    echo -e "${RED}❌ Release AAB build failed${NC}"
fi

# Show mapping file location (for crash analysis)
if [ -f "app/build/outputs/mapping/release/mapping.txt" ]; then
    echo -e "${GREEN}✅ ProGuard mapping file generated${NC}"
    echo "Location: app/build/outputs/mapping/release/mapping.txt"
    echo -e "${YELLOW}Important: Save this file for crash report analysis!${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Release build completed!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Test the release APK on multiple devices"
echo "2. Upload the AAB file to Google Play Console"
echo "3. Save the ProGuard mapping file for crash analysis"
echo ""
echo -e "${BLUE}Build artifacts:${NC}"
echo "📱 APK (for direct installation): app/build/outputs/apk/release/"
echo "📦 AAB (for Google Play Store): app/build/outputs/bundle/release/"
echo "🗺️  ProGuard mapping: app/build/outputs/mapping/release/"