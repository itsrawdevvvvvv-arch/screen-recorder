# Recordly Mobile (Android)

An Android screen recorder & AI studio editor powered by **Groq API**, **AndroidX Media3 Transformer**, and **Post-Recording AI Cursor Tracking** — with zero root or Shizuku required.

## Features Implemented
- **Screen Capture + Floating Studio Bubble:** `MediaProjection` + `VirtualDisplay` + `MediaRecorder` foreground service with draggable overlay controls (Record / Pause / Stop / Keyboard indicator).
- **Post-Recording Visible Mouse Tracking & Desktop Auto-Zoom:**
  - Real-time physical mouse detection (`InputDevice.SOURCE_MOUSE`).
  - **Zero-Setup Post-Recording Tracker:** Once recording finishes, Recordly scans the recorded frames using local localized-motion detection + **Groq Vision (`qwen/qwen3.8-27b`)** to locate Android's visible mouse cursor, infer clicks/dwells, and generate smooth spring-eased zoom-in/out keyframes and optional click-ripple highlight rings.
- **Phone Keyboard Auto-Trim:**
  - **Real-time (`RecordlyAccessibilityService`):** Logs exact `[startMs, endMs]` intervals whenever `TYPE_INPUT_METHOD` appears on screen and automatically trims (or speeds up 5x) those sections.
  - **Post-Recording Fallback:** Groq Vision scans sampled frames to catch on-screen keyboards in imported videos too.
- **Groq AI Suite (Whole-App AI Support):**
  - **Whisper `.srt` + Word Timestamps:** Uses `whisper-large-v3-turbo` (`verbose_json` + `word` & `segment` granularities) to auto-generate `.srt` subtitles, or lets you import your own `.srt` & voiceover audio.
  - **Vision Keyframe Scanner:** Uses `qwen/qwen3.8-27b` to locate the visible cursor, detect UI context, loading/idle screens, and keyboard visibility.
  - **AI Director (`llama-3.3-70b-versatile`):** Automatically trims silences/keyboard spans, adjusts segment playback speeds (`0.75x – 5.0x`) to synchronize screen actions with the `.srt` narration, aligns zoom keyframes, and supports natural-language prompt edits.
- **Hardware-Accelerated Export (`Media3 Transformer 1.11.1`):** Applies `ClippingConfiguration`, `SpeedProvider`, `MatrixTransformation` zoom/pan, and `OverlayEffect` (`BitmapOverlay` cursor focus ring/ripples + burned-in `.srt` captions) and exports MP4 to `Movies/Recordly` + `.srt` to `Downloads/Recordly`.

---

## How to Compile into APK

### Option 1: Android Studio (Recommended)
1. Unzip `RecordlyMobile.zip`.
2. Open Android Studio → **Open** → select the `RecordlyMobile` folder.
3. Let Gradle sync (AGP `8.10.0`, Gradle `8.11.1`, Kotlin `2.1.20`, Compile SDK `35`).
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)** (or **Run** directly onto a connected Android 10+ phone).

### Option 2: Command Line
Make sure you have **JDK 17+** and **Android SDK (API 35)** installed (`ANDROID_HOME` set):

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
chmod +x gradlew
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## App Setup on Phone
1. Open **Settings** inside the app and paste your **Groq API Key** (`gsk_...`), then tap **Test Key**.
2. *(Optional for instant Keyboard Auto-Trim)* Tap **Enable Keyboard Auto-Detector** on the Home screen to turn on Recordly's Accessibility service.
3. Connect a USB-C or Bluetooth mouse, tap **Start Recording**, and run **Groq AI Magic Sync** in the Studio Editor when finished — all cursor tracking, zoom-in/zoom-out, keyboard cuts, and `.srt` speed syncing happen automatically post-recording.
