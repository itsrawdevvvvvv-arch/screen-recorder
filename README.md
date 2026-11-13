# Recordly Mobile (Android)

A minimal, privacy-centric screen recording & visual contact sheet studio for Android.
Designed with the sleek layout aesthetic of the reference showcase cards, a warm color palette (**Warm Sand**, **Terracotta**, **Forest Sage**, **Dark Charcoal**), **Product Sans** typography, and a **zero-API-key Claude AI storyboard workflow**.

---

## Highlights

### 1. Minimal Zero-API-Key Architecture
- **No API keys or online quotas required:** Recording and editing work 100% offline on-device.
- **Claude AI Storyboard Workflow:**
  1. Record your screen workflow.
  2. Recordly automatically extracts crisp timestamped keyframes and generates a high-resolution **Multi-Page Landscape PDF Storyboard** and **.zip bundle** (`frames/`, `contact_sheet.pdf`, `metadata.json`, `prompt.txt`).
  3. Tap **Copy AI Prompt** and share the contact sheet with Claude AI (or any LLM).
  4. Claude analyzes the visual frames and returns an Edit Decision List (EDL) in clean JSON with cuts, speeds, and dynamic camera zoom coordinates.
  5. Tap **Paste Claude's Response** in Recordly — the app instantly executes the trims and renders dynamic pan-zooms in the live preview player and hardware-accelerated MP4 export!

### 2. Floating Recording Menu Toggle
- An instant toggle switch on the Home Screen top banner and Settings: **`MENU: ON / OFF`**.
- **When ON:** A draggable, semi-transparent capsule pill overlays on screen with timer, Pause/Resume, and Stop buttons.
- **When OFF:** Records cleanly and unobtrusively without any UI elements obscuring the capture. The user easily stops recording with one tap from the Android status bar notification.

### 3. Native PDF & ZIP Contact Sheet Engine
- Uses built-in `android.graphics.pdf.PdfDocument` (zero third-party bloat) to lay out a clean 2x3 grid of timestamp-badged frames per page with header metadata and Claude AI prompt instructions.
- Bundles all high-resolution frames, the PDF, and structured prompt text into a single `.zip` file for instant sharing.
- Save directly to device storage (`Downloads/Recordly`).

### 4. Mathematical Camera Zoom & Centering Pipeline (`CameraMath`)
- Derivation: $p' = S \cdot (p - g) \implies tx = -S \cdot gx, \quad ty = -S \cdot gy$
- Viewport clamped to $[0.5/S, 1 - 0.5/S]$ to prevent black border bleed.
- Unified coordinate math shared between Jetpack Compose live preview (`TextureView` + `graphicsLayer`) and AndroidX Media3 Transformer OpenGL export.

### 5. UI Design & Reference Color Palette
- **Warm Sand** (`#DCC9A9`): Primary highlights, badges, and key metric cards.
- **Terracotta** (`#B83A2D`): Action buttons, recording indicators, and alert states.
- **Forest Sage** (`#4E6851`): Success indicators, bento steps, and secondary tags.
- **Studio Charcoal** (`#0E1012`, `#17191D`, `#22262E`): Sleek dark background surfaces with subtle borders.
- **Product Sans**: Geometric typography across headings, metrics, and cards.
- **Hero Card**: Contour wave background with camera focal radar graphic and pulse ring.
- **Floating Pill Navigation Bar**: Bottom navigation with Home, Projects Grid, and Settings.

---

## Project Structure

```
RecordlyMobile/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/recordly/mobile/
│   │   │   ├── MainActivity.kt
│   │   │   ├── RecordlyApp.kt
│   │   │   ├── capture/
│   │   │   │   ├── ActiveSessionTracker.kt
│   │   │   │   ├── FloatingControlService.kt
│   │   │   │   └── ScreenRecordService.kt
│   │   │   ├── data/
│   │   │   │   ├── ProjectRepository.kt
│   │   │   │   └── SettingsRepository.kt
│   │   │   ├── editor/
│   │   │   │   ├── AutoZoomGenerator.kt
│   │   │   │   ├── ContactSheetEngine.kt
│   │   │   │   └── VideoExportEngine.kt
│   │   │   ├── model/
│   │   │   │   └── Models.kt
│   │   │   └── ui/
│   │   │       ├── HomeScreen.kt
│   │   │       ├── RecordlyViewModel.kt
│   │   │       ├── SettingsScreen.kt
│   │   │       ├── StudioEditorScreen.kt
│   │   │       └── theme/Theme.kt
│   │   └── res/
│   │       ├── drawable/
│   │       ├── mipmap-anydpi-v26/
│   │       ├── values/
│   │       │   ├── colors.xml
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── xml/
│   │           └── file_paths.xml
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## How to Compile into APK

### Option 1: Android Studio (Recommended)
1. Extract `RecordlyMobile.zip`.
2. Open Android Studio → **File** → **Open...** → choose the `RecordlyMobile` folder.
3. Allow Gradle to sync.
4. Click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)** (or click **Run** to launch directly on your Android phone or emulator).

### Option 2: Command Line (Terminal / PowerShell)
Requires **JDK 17+** and **Android SDK (API 35)**:

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
chmod +x gradlew
./gradlew assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Step-by-Step Workflow Guide

1. **Launch Recordly Mobile**:
   - Check the **MENU: ON / OFF** toggle on the home banner to decide if you want the floating overlay menu or quiet recording.
2. **Record Screen**:
   - Tap the large terracotta record button on the Hero Showcase Card.
   - Perform your app walkthrough, tutorial, or demo.
   - Stop recording via the floating pill or notification bar.
3. **Contact Sheet Storyboard**:
   - The Studio Editor opens automatically with your contact sheet.
   - Tap **"Copy AI Prompt"** (copies a detailed prompt with exact frame timestamps).
   - Tap **"Download PDF"** or **"Download ZIP"** to save to `Downloads/Recordly`.
4. **AI Processing with Claude**:
   - Paste the copied prompt and upload the PDF/ZIP into Claude AI.
   - Claude reviews the storyboard frames and outputs a clean JSON Edit Decision List (EDL).
5. **Apply & Export**:
   - Tap **"Paste Claude Response"** in Recordly and paste Claude's JSON.
   - Trims, speeds, and dynamic camera zooms are instantly applied to your timeline!
   - Tap **"Export MP4"** to render a 1080p hardware-accelerated video to your device gallery (`Movies/Recordly`).
