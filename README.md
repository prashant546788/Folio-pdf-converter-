## What this is
**Folio PDF is an Android app for working with PDFs and related documents.** It goes beyond file conversion: users can batch-process PDFs, apply tools such as merge, split, compression, security, metadata, and watermarks, then view, print, save, or upload documents. It also includes document scanning, QR/barcode tools, and AI-assisted notes and translation.
<img width="460" height="770" alt="1000199953" src="https://github.com/user-attachments/assets/83b8dfc7-7943-46ce-a59e-d8834e59210a" />
<img width="460" height="770" alt="1000199955" src="https://github.com/user-attachments/assets/653ddd8b-0e61-4101-b141-a9a043f3ab44" />
<img width="460" height="770" alt="1000199952" src="https://github.com/user-attachments/assets/a41e00a9-fed3-427e-ac5b-7a29dc630b6c" />
### Stack
- **Language(s):** Kotlin; Gradle Kotlin DSL for build configuration
- **Framework / runtime:** Android, with Jetpack Compose and Material 3 for the UI
- **Notable libraries:** PDFBox-Android for PDF processing; Room for local storage; Firebase AI for AI features; ML Kit for OCR and barcode scanning; CameraX and ZXing for scanning

### How it's organized
```text
app/
  src/main/java/com/example/
    MainActivity.kt      App entry point and Compose navigation
    data/
      ai/                AI notes and PDF translation
      cloud/             Cloud provider authentication and storage
      local/             Room database and cloud token storage
      pdf/               PDF conversion, tools, scanning, security, and print logic
      repository/        PDF repository
      video/             YouTube downloader engine
    model/               PDF and note data models
    ui/
      screens/           App screens for studio, batch work, vault, viewer, and more
      components/        Reusable Compose UI components
      theme/             App theme
    res/                 Android resources
  build.gradle.kts       Android app configuration and dependencies
gradle/
  libs.versions.toml     Dependency and plugin version catalog
```

**How it fits together:** `MainActivity` sets up the Compose app and navigation, while `FolioViewModel` connects screen actions to app state and operations. PDF work is handled by engines in `data/pdf` and coordinated with `PdfRepository`; Room-backed local storage supports the document vault. Separate AI and cloud modules provide notes/translation and storage integrations.

### How to run it
The README contains screenshots but no setup instructions. The project is configured as an Android app, so open it in Android Studio, sync Gradle, and run the `app` configuration on an Android device or emulator (minimum SDK 24). With a compatible Gradle installation, the main build and unit-test tasks are:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

Dropbox and OneDrive credentials are listed as optional configuration in `.env.example`. Release signing uses `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`.
## Repository overview

**Folio PDF Converter** is a public Kotlin project for beginner-friendly PDF tools and conversion workflows.

### Key details
- **Repository:** [prashant546788/Folio-pdf-converter-](https://github.com/prashant546788/Folio-pdf-converter-)
- **Primary language:** Kotlin (100%)
- **Main branch:** `main`
- **Project size:** Approximately 3.2 MB
- **GitHub Pages:** [Live project page](https://prashant546788.github.io/Folio-pdf-converter-/)
- **Template:** Based on Google AI Studio’s repository template
- **Community activity:** 2 stars, no forks or open issues

### Intended purpose
The repository appears to provide tools for:

- Converting documents into PDFs
- Generating and processing PDF files
- Reading or viewing PDFs
- Offering PDF-related utilities through a simple Android application interface

### Project structure
- `app/` — Main application source code
- `gradle/` — Gradle wrapper and build configuration support
- `build.gradle.kts` and `settings.gradle.kts` — Kotlin/Gradle project configuration
- `.github/` — GitHub configuration and automation
- `.env.example` — Example environment variables
- `firebase-applet-config.json` — Firebase or applet configuration
- `metadata.json` — Project metadata
- `README.md` — Project documentation

Overall, it is a small, newly created Kotlin Gradle project presented as a PDF utility application, currently with limited repository activity and no explicit license.
### Try asking
- “Trace how `PdfConversionEngine` and `PdfRepository` turn selected files into saved PDFs.”
- “Which PDF features are implemented in `PdfToolboxEngine`, and which are handled by the other PDF engines?”
- “How do `YouTubeDownloaderEngine` and `YoutubeDownloaderScreen` fit into the app’s navigation and intended workflows?”


