# Recordly Mobile (Android)

An Android screen recorder & creator video editor inspired by **Screen Studio** / **Recordly**, powered by **Groq API**, **AndroidX Media3 Transformer (1.11.1)**, **Jetpack Compose**, and **Post-Recording Visual Cursor Detection & Auto-Zoom** — with strictly **zero root or Shizuku required**.

---

## Architecture & Features

### 1. Mathematical Auto Zoom-In & Centering Pipeline
- **Zero Root / Zero Daemon Architecture:** Android's SurfaceFlinger natively renders connected USB or Bluetooth mouse pointers directly into the screen buffer. `MediaProjection` records this arrow directly into MP4 frames.
- **Visual Cursor Detection (`VisualAnalyzer`):**
  - Multi-scale procedural template matching specifically calibrated for the AOSP arrow cursor (dark outline, white core, top-left hotspot).
  - Fast two-probe pre-filter to scan frames at 6 fps in milliseconds.
  - Frame-differencing to find localized UI activity hotspots (touch / click reactions) with status bar exclusion.
  - Fuses visual samples with `RecordlyAccessibilityService` clicks and visually inferred clicks.
- **Screen Studio Dynamic Keyframing (`AutoZoomGenerator`):**
  - 350ms lead-in ease-in before clicks/dwells.
  - 1.5s–2.5s hold zoomed at 1.8x – 2.2x.
  - Dynamic panning across waypoints if sequential actions occur within 2.0s without zooming out.
  - 400ms lead-out ease-out back to 1.0x full screen when idle.
  - Viewport boundary clamping (`fx, fy ∈ [0.5/S, 1 - 0.5/S]`) so black margins never leak into the frame.
- **Unified NDC Centering Math (`CameraMath`):**
  - Transform applied to NDC vertices: $p' = S \cdot (p - g)$ with $g = (2fx - 1, 1 - 2fy)$.
  - Media3 OpenGL `MatrixTransformation` sets uniform scale $S$ and translates $(-S \cdot gx, -S \cdot gy)$, centering the focal point at screen center $(0, 0)$.
  - Compose Live Preview (`StudioEditorScreen`) uses `TextureView` and the identical `NdcTransform` via `graphicsLayer` centered on `TransformOrigin.Center`, guaranteeing 1:1 parity between live preview and exported video.

### 2. Phone Keyboard Auto-Trim
- **Real-Time Detection:** `RecordlyAccessibilityService` tracks `TYPE_INPUT_METHOD` window states and records millisecond timestamps whenever the soft keyboard appears.
- **Configurable Modes:**
  - `REMOVE`: Trims keyboard segments so typing never appears in the final video.
  - `SPEED_UP_5X`: Speeds up keyboard typing by 5.0x so typing passes rapidly.
  - `KEEP`: Retains normal speed.
- **Vision Fallback:** Groq Vision scans keyframes to catch on-screen keyboards even in imported videos.

### 3. Whole-App Groq API Suite
- **Groq Whisper (`whisper-large-v3-turbo`):** Automatic speech-to-text with word-level timestamps, generating crisp mobile captions serialized to `.srt`. Also supports importing custom external audio and `.srt` files.
- **Groq Vision (`qwen/qwen3.8-27b`):** Multi-frame visual analyzer detecting cursor locations, UI action context, loading/idle screens, and keyboard visibility.
- **Groq AI Director (`llama-3.3-70b-versatile`):** Generates an Edit Decision List (EDL) adjusting clip speeds (0.75x–5.0x) to synchronize screen recording motion to spoken narration, trimming dead silences, and optimizing zoom keyframes.
- **Interactive Prompt Refinement:** Natural-language chat box in the Studio Editor allowing users to say "make typing 3x faster" or "add zoom on the third button" and get instant timeline updates from Groq.

### 4. Hardware-Accelerated Video Export
- AndroidX Media3 Transformer with H.264 hardware encoding, clipping, speed adjustments, zoom matrix transformations, subtitle overlays, and cursor focus halos.
- Direct export to `Movies/Recordly` (MP4) and `Downloads/Recordly` (.SRT).

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
│   │   │   ├── ai/
│   │   │   │   ├── AiPipelineOrchestrator.kt
│   │   │   │   ├── AudioExtractor.kt
│   │   │   │   ├── GroqApiClient.kt
│   │   │   │   └── VisualAnalyzer.kt
│   │   │   ├── capture/
│   │   │   │   ├── ActiveSessionTracker.kt
│   │   │   │   ├── FloatingControlService.kt
│   │   │   │   ├── MouseDeviceMonitor.kt
│   │   │   │   ├── RecordlyAccessibilityService.kt
│   │   │   │   └── ScreenRecordService.kt
│   │   │   ├── data/
│   │   │   │   ├── ProjectRepository.kt
│   │   │   │   ├── SettingsRepository.kt
│   │   │   │   └── SrtCodec.kt
│   │   │   ├── editor/
│   │   │   │   ├── AutoZoomGenerator.kt
│   │   │   │   ├── VideoExportEngine.kt
│   │   │   │   └── ZoomPipeline.kt
│   │   │   ├── model/
│   │   │   │   └── Models.kt
│   │   │   └── ui/
│   │   │       ├── HomeScreen.kt
│   │   │       ├── RecordlyViewModel.kt
│   │   │       ├── SettingsScreen.kt
│   │   │       ├── StudioEditorScreen.kt
│   │   │       └── theme/Theme.kt
│   │   └── res/
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## How to Compile into APK

### Option 1: Android Studio (Recommended)
1. Extract `RecordlyMobile.zip`.
2. Open Android Studio → **File** → **Open...** → select the `RecordlyMobile` directory.
3. Allow Gradle to sync.
4. Go to **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)** (or click **Run** to install directly on a connected device).

### Option 2: Command Line
Requirements: **JDK 17+** and **Android SDK API 35** (`ANDROID_HOME` configured).

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

## Running the App
1. Open **Settings** in the app and enter your **Groq API Key** (`gsk_...`), then tap **Test Key**.
2. Connect a USB-C or Bluetooth mouse to your Android device (the mouse pointer will appear natively on screen).
3. Tap **Start Recording** on the Home Screen.
4. Record your workflow and tap **Stop & Edit** on the floating pill or notification.
5. In the **Studio Editor**, tap **Groq AI Magic Sync** to automatically synchronize narration, trims, keyboard removal, and dynamic auto zoom!
