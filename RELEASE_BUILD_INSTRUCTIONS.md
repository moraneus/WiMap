# WiMap Release Build Instructions

## 🚀 Creating a Production Release

### Prerequisites
1. **Android Studio** with latest SDK
2. **Java 11+** installed
3. **Signing keystore** (already created: `wimap_release.keystore`)
4. **Keystore properties** file (already created: `keystore.properties`)

### 📋 Release Checklist

#### 1. Pre-Release Preparation
- [ ] Update version code and name in `app/build.gradle.kts`
- [ ] Verify all tests pass: `./gradlew test`
- [ ] Test on multiple device types and Android versions
- [ ] Review Firebase configuration (`google-services.json`)
- [ ] Confirm AdMob ads are working correctly

#### 2. Build Configuration Verification
- [ ] **Ad Configuration**: Release builds use production ad IDs automatically
- [ ] **Debug Features**: All debug logging removed in release builds
- [ ] **Signing**: Release builds are automatically signed with release keystore
- [ ] **Optimization**: ProGuard/R8 enabled for code shrinking and obfuscation

#### 3. Building the Release APK

##### Option A: Command Line (Recommended)
```bash
# Navigate to project directory
cd /Users/moraneus/AndroidStudioProjects/WiMap

# Clean previous builds
./gradlew clean

# Build release APK
./gradlew assembleRelease

# Or build App Bundle for Google Play Store
./gradlew bundleRelease
```

##### Option B: Android Studio
1. Open project in Android Studio
2. Go to **Build** → **Generate Signed Bundle/APK**
3. Select **Android App Bundle** (recommended for Play Store)
4. Choose existing keystore: `wimap_release.keystore`
5. Enter keystore details from `keystore.properties`
6. Select **release** build variant
7. Click **Finish**

#### 4. Output Locations
- **APK**: `app/build/outputs/apk/release/app-release.apk`
- **AAB**: `app/build/outputs/bundle/release/app-release.aab`
- **Mapping files**: `app/build/outputs/mapping/release/mapping.txt`

### 🔐 Signing Configuration

#### Keystore Details
- **File**: `wimap_release.keystore`
- **Alias**: `wimap_release_key`
- **Validity**: 27+ years (10,000 days)
- **Algorithm**: RSA 2048-bit
- **Certificate**: Self-signed

#### Important Security Notes
- **NEVER** commit `keystore.properties` or `*.keystore` files to version control
- **BACKUP** the keystore file securely - if lost, you cannot update the app on Play Store
- **STORE** keystore passwords in a secure password manager

### 📊 Release Build Features

#### Optimizations Enabled
- **Code Shrinking**: Removes unused code (R8/ProGuard)
- **Resource Shrinking**: Removes unused resources
- **Obfuscation**: Makes reverse engineering harder
- **Optimization**: Multiple optimization passes for smaller APK

#### Production Configuration
- **Ad Units**: Uses production AdMob ad unit IDs
- **Logging**: Debug logs removed from release builds
- **Debugging**: Debug symbols removed
- **Package**: Production package name (`com.ner.wimap`)

#### Security Features
- **Certificate Pinning**: Prevents man-in-the-middle attacks
- **Code Obfuscation**: Protects intellectual property
- **Resource Protection**: Shrinks and optimizes all assets
- **Anti-Tampering**: Signed with production certificate

### 🧪 Pre-Release Testing

#### Required Tests
1. **Installation**: Fresh install on clean device
2. **Permissions**: All location/camera permissions work correctly
3. **Wi-Fi Scanning**: Core functionality works
4. **Data Export**: All export formats (CSV, PDF, KML) work
5. **Ads**: Interstitial ads show before export/share actions
6. **Firebase**: Data uploads to Firebase successfully
7. **Background Service**: Background scanning works
8. **Maps Integration**: Google Maps functionality works

#### Test Devices
- **Minimum**: Android 7.0 (API 24)
- **Target**: Android 14 (API 34)
- **Recommended**: Test on phones and tablets

### 📱 Google Play Store Preparation

#### Required Assets
- [ ] App icon (all densities) ✅
- [ ] Feature graphic (1024x500)
- [ ] Screenshots (phone & tablet)
- [ ] Privacy policy URL
- [ ] App description and keywords

#### Store Listing Information
- **Category**: Tools
- **Content Rating**: Everyone
- **Permissions**: Location, Camera, Storage
- **Target Audience**: Android users needing Wi-Fi mapping

### 🔍 Version Management

#### Current Version
- **Version Code**: 1
- **Version Name**: 1.0.0

#### Updating for New Releases
```kotlin
// In app/build.gradle.kts
defaultConfig {
    versionCode = 2          // Increment for each release
    versionName = "1.0.1"    // Semantic versioning
}
```

### 🛠️ Troubleshooting

#### Common Issues

1. **Keystore not found**
   - Ensure `keystore.properties` exists in project root
   - Verify keystore file path is correct

2. **Build fails with ProGuard errors**
   - Check `proguard-rules.pro` for missing keep rules
   - Add keep rules for any classes that fail

3. **Ads not showing**
   - Verify AdMob app ID in `google-services.json`
   - Check internet connectivity during testing

4. **Maps not working**
   - Ensure Maps API key is enabled in Google Cloud Console
   - Verify SHA-1 fingerprint is registered

#### Debug Commands
```bash
# Check signing info
./gradlew signingReport

# Analyze APK size
./gradlew analyzeReleaseBundle

# Run lint checks
./gradlew lintRelease
```

### 📄 Legal & Compliance

#### Required Disclosures
- **Location Data**: App collects GPS coordinates for Wi-Fi mapping
- **Device Info**: Collects device model, OS version for analytics
- **Advertising**: Shows interstitial ads before export actions
- **Storage**: Stores Wi-Fi network data locally and in Firebase

#### Privacy Compliance
- **GDPR**: Users can delete all data via settings
- **CCPA**: Data collection is transparent and opt-in
- **AdMob**: Follows Google's advertising policies

### 🎯 Release Workflow

#### 1. Pre-Release (Development)
```bash
./gradlew assembleDebug  # Test builds
```

#### 2. Release Candidate
```bash
./gradlew assembleRelease  # Final testing
```

#### 3. Production Release
```bash
./gradlew bundleRelease  # Google Play Store upload
```

---

## 🔒 Keystore Backup Instructions

**CRITICAL**: Always backup your keystore file securely!

1. **Copy keystore to secure location**:
   ```bash
   cp wimap_release.keystore ~/secure_backup/wimap_keystore_backup.keystore
   ```

2. **Store credentials securely**:
   - Keystore password: `WiMap2024SecureKey!`
   - Key alias: `wimap_release_key`
   - Key password: `WiMap2024SecureKey!`

3. **Multiple backup locations recommended**:
   - Encrypted cloud storage
   - Physical secure storage
   - Team shared secure vault

**⚠️ WARNING**: If you lose the keystore, you cannot update the app on Google Play Store!

---

**Build completed successfully!** 🎉

The release APK/AAB is ready for distribution or Google Play Store upload.