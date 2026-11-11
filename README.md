# TextToAudio 🎙️

An Android app that converts text from PDF documents, images, and manual input into natural-sounding audio using on-device Text-to-Speech.

## Features

- 📄 **PDF Text Extraction** — Parse text from PDF documents using Apache PDFBox
- 📷 **Image OCR** — Extract text from photos/scans using Google ML Kit Text Recognition
- ✍️ **Manual Input** — Type or paste text directly
- 🔊 **Offline TTS** — Android's built-in Text-to-Speech engine (no internet required)
- ▶️ **Playback Controls** — Play, Pause, Stop
- 🎨 **Clean UI** — Material Design with scrollable text preview

## Download

**Latest APK:** [Download from GitHub Releases](https://github.com/Nazmul1005/Text-To-Audio/releases/latest)

Or download directly from the [GitHub Pages download page](https://nazmul1005.github.io/Text-To-Audio/).

> **Requirements:** Android 7.0+ (API 24+)

## Screenshots

| Home | PDF Selection | Text Preview | Playing |
|------|---------------|--------------|---------|
| ![](screenshots/home.png) | ![](screenshots/pdf.png) | ![](screenshots/preview.png) | ![](screenshots/playing.png) |

## Building from Source

### Prerequisites
- Android Studio (Koala or newer)
- JDK 17
- Android SDK 34

### Steps
1. Clone the repository
2. Open in Android Studio
3. Let Gradle sync
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Or via command line:
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## APK Signing

### Local Signing (Development)
1. Generate a keystore:
```bash
./generate-keystore.sh
```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in your values:
```properties
keystoreFile=keystore/release.keystore
keystorePassword=your_password
keyAlias=release
keyPassword=your_password
```

3. Build signed release APK:
```bash
./gradlew assembleRelease
# Signed APK at: app/build/outputs/apk/release/app-release.apk
```

### CI/CD Signing (GitHub Actions)
For signed releases via GitHub Actions, set these secrets in your repository:

1. Go to **Settings → Secrets and variables → Actions**
2. Add these secrets:
   - `KEYSTORE_PASSWORD` — Your keystore password
   - `KEY_PASSWORD` — Your key password
   - `KEY_ALIAS` — Your key alias (e.g., `release`)
   - `KEYSTORE_FILE` — Base64-encoded keystore file

To encode the keystore as base64:
```bash
base64 keystore/release.keystore
```

The GitHub Actions workflow will automatically sign release APKs when these secrets are set.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | XML + ViewBinding |
| PDF Parsing | Apache PDFBox (Android port) |
| OCR | Google ML Kit Text Recognition |
| TTS | Android TextToSpeech API |
| Build | Gradle (Kotlin DSL) |

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  File Input │────▶│  Text Extraction │────▶│  TTS Synthesis  │
│  (PDF/Image)│     │  (PDFBox/ML Kit) │     │  (Android TTS)  │
└─────────────┘     └──────────────────┘     └─────────────────┘
```

## Permissions

- `READ_EXTERNAL_STORAGE` — Access PDF/image files
- `READ_MEDIA_IMAGES` — Access images on Android 13+

## License

MIT License — feel free to use and modify.

## Contributing

1. Fork the repo
2. Create a feature branch
3. Commit your changes
4. Open a Pull Request

---

**Built with ❤️ for Android**
