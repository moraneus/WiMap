# 🗺️ WiMap - WiFi Network Scanner & Mapper

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**WiMap** is a comprehensive WiFi network scanner and mapping application for Android that helps you discover, analyze, and manage wireless networks in your area. With advanced features like GPS mapping, network analytics, and export capabilities, WiMap is the perfect tool for network professionals, IT administrators, and curious users.

## ✨ Features

### 🔍 **Advanced WiFi Scanning**
- **Real-time network discovery** with automatic refresh
- **Detailed network information**: SSID, BSSID, signal strength, security type, channel, and vendor
- **Signal strength visualization** with color-coded indicators
- **Background scanning** with persistent notifications
- **Smart filtering** and sorting options

### 🗺️ **GPS Location Mapping**
- **Interactive map view** showing network locations
- **GPS coordinate tracking** for precise network positioning
- **Peak signal location** recording for optimal positioning
- **Vendor identification** from MAC address lookup
- **Custom markers** with network details

### 📋 **Network Management**
- **Pin networks** for quick access and monitoring
- **Add photos** and comments to saved networks
- **Offline status tracking** for pinned networks
- **Network history** with scan session management
- **Smart organization** with search and filter capabilities

### 📊 **Export & Analytics**
- **Multiple export formats**: PDF reports, CSV data, Google Maps KML
- **Comprehensive reporting** with network statistics
- **Scan session history** with detailed network logs
- **Share functionality** for collaboration
- **Professional documentation** generation

### 🔐 **Privacy & Security**
- **Local data storage** with optional cloud sync
- **Password validation** with secure storage
- **Privacy-focused design** with minimal data collection
- **Transparent permissions** with clear explanations
- **GDPR compliant** privacy practices

## 📱 Screenshots

| Main Scanner | Network Details | Map View | Export Options |
|--------------|-----------------|----------|----------------|
| ![Scanner](docs/screenshots/scanner.png) | ![Details](docs/screenshots/details.png) | ![Map](docs/screenshots/map.png) | ![Export](docs/screenshots/export.png) |

## 🚀 Getting Started

### Prerequisites
- **Android 7.0** (API level 24) or higher
- **Location services** enabled (required for WiFi scanning on Android 10+)
- **WiFi enabled** on your device

### Installation

#### From Google Play Store
```
🔗 Coming Soon - WiMap will be available on Google Play Store
```

#### From APK Release
1. Download the latest APK from [Releases](https://github.com/yourorg/wimap/releases)
2. Enable "Install from unknown sources" in Android settings
3. Install the APK file
4. Grant required permissions when prompted

### Required Permissions

| Permission | Purpose | Required |
|------------|---------|----------|
| **Location (Fine)** | WiFi scanning on Android 10+ | ✅ Yes |
| **WiFi State** | Read network information | ✅ Yes |
| **WiFi Control** | Network connection testing | ✅ Yes |
| **Camera** | Take network location photos | ❌ Optional |
| **Storage** | Save exported files | ❌ Optional |

## 🛠️ Development

### Tech Stack
- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt/Dagger
- **Database**: Room with Flow-based reactive updates
- **Maps**: Google Maps SDK
- **Networking**: Retrofit with coroutines
- **Analytics**: Firebase Analytics (optional)

### Building from Source

#### Prerequisites
- **Android Studio** Arctic Fox or newer
- **JDK 11** or higher
- **Android SDK** with build tools 34.0.0+

#### Setup
```bash
# Clone the repository
git clone https://github.com/yourorg/wimap.git
cd wimap

# Create local.properties file
echo "sdk.dir=/path/to/your/android/sdk" > local.properties

# Add your Google Maps API key to local.properties
echo "MAPS_API_KEY=your_google_maps_api_key" >> local.properties

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

#### Google Services Configuration
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
2. Add your Android app with package name `com.ner.wimap`
3. Download `google-services.json` and place it in the `app/` directory
4. Enable Analytics and configure as needed

### Project Structure
```
app/
├── src/main/java/com/ner/wimap/
│   ├── data/           # Data layer (repositories, database, API)
│   ├── domain/         # Business logic (use cases, repositories)
│   ├── presentation/   # ViewModels and UI state management
│   ├── ui/            # Compose UI screens and components
│   ├── di/            # Dependency injection modules
│   ├── utils/         # Utility classes and extensions
│   └── wifi/          # WiFi scanning and network logic
├── src/main/res/       # Android resources
└── src/test/          # Unit tests
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Workflow
1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use [ktlint](https://ktlint.github.io/) for code formatting
- Write meaningful commit messages
- Add unit tests for new features

## 📄 Documentation

- [Privacy Policy](PRIVACY_POLICY.md) - How we handle your data
- [Release Notes](RELEASE_NOTES.md) - Version history and changes
- [Build Instructions](RELEASE_BUILD_INSTRUCTIONS.md) - Detailed build setup
- [API Documentation](docs/api.md) - Internal API reference

## 🐛 Issue Reporting

Found a bug? Have a feature request? Please check our [issue tracker](https://github.com/yourorg/wimap/issues) and create a new issue if needed.

### Bug Reports
Please include:
- **Device model** and Android version
- **App version** (found in Settings → About)
- **Steps to reproduce** the issue
- **Expected behavior** vs actual behavior
- **Screenshots** if applicable

## 📊 Analytics & Privacy

WiMap respects your privacy:
- **Local-first**: Data is stored locally on your device
- **Optional analytics**: Firebase analytics can be disabled
- **No personal data collection**: We don't collect personal information
- **Transparent permissions**: Clear explanation of why each permission is needed

See our [Privacy Policy](PRIVACY_POLICY.md) for complete details.

## 🏆 Use Cases

### For IT Professionals
- **Site surveys** for WiFi deployment planning
- **Network auditing** and security assessment
- **Coverage mapping** and signal optimization
- **Documentation generation** for client reports

### For Researchers
- **WiFi landscape analysis** in different areas
- **Signal propagation studies** with GPS data
- **Network density research** with export capabilities
- **Academic projects** with comprehensive data collection

### For General Users
- **Find optimal locations** for WiFi access
- **Understand network coverage** in your area
- **Monitor home network** performance over time
- **Educational exploration** of wireless technologies

## 🔒 Security

- All network passwords are stored encrypted locally
- No passwords or sensitive data are transmitted to external servers
- Optional cloud sync uses anonymized data only
- Regular security updates and vulnerability assessments

## 📞 Support

- **In-app support**: Settings → Help & Support
- **Email**: support@wimap.app
- **Documentation**: Check this README and linked docs
- **Issues**: GitHub issue tracker for bug reports

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 WiMap Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🙏 Acknowledgments

- **Google** for Android development tools and Maps SDK
- **OUI Database** for network vendor identification
- **Material Design** for UI components and guidelines
- **Open source community** for various libraries and tools used

---

**Made with ❤️ by the WiMap Team**

*Empowering users to understand and navigate the wireless world around them.*