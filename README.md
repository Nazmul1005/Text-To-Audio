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

**Latest APK:** [GitHub Releases](https://github.com/USERNAME/REPOSITORY/releases) (replace with your repo)

Or install directly from the [GitHub Pages download page](https://USERNAME.github.io/REPOSITORY/) (replace with your repo).

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