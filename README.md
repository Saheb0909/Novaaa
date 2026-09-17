# Nova — Personal AI Android Assistant

Nova is a fast, modern personal AI voice and phone assistant built natively for Android using Kotlin and Jetpack Compose. Nova understands natural-language commands in English, Hindi, Bengali, Hinglish, and Banglish, executing actions through Android platform APIs and falling back gracefully to Gemini AI.

---

## Features

### 1. Natural Language & Voice Assistant
- Multilingual intent processing supporting **English, Hindi, Bengali, Hinglish, and Banglish** (e.g., *"Flashlight bandh karo"*, *"Sound komao"*, *"ভলিউম কমিয়ে দাও"*, *"YouTube kholo"*, *"WhatsApp message pathao"*).
- Contextual speech recognition (STT) and voice feedback (TTS).
- Distinct Assistant status states: **IDLE**, **LISTENING**, **THINKING**, **EXECUTING**, **SPEAKING**.
- Addresses user politely as **Saheb** when appropriate.

### 2. Application Control
- Natural app launching by name or package: YouTube, WhatsApp, Instagram, Chrome, Camera, Settings, Maps, Gmail, and any user-installed application.
- Live BottomSheet application manager that queries package manager launcher intents to browse, filter, and launch installed apps.

### 3. Hardware & Phone Controls
- **Flashlight / Torch**: Camera2 torch hardware control with state tracking.
- **Volume**: Media volume adjustment by percentage or step up/down.
- **Silent Mode**: Ringer mode switching to Silent or Normal.
- **Screen Brightness**: Direct window brightness adjustment with deep links to Display Settings.
- **System Panels**: Direct launching of Quick Settings, Wi-Fi Settings, Bluetooth Settings, Airplane Mode, and Display Settings.
- **Media Controls**: Next, previous, play, and pause media key events.

### 4. Safety & Confirmation System
- **Consequential actions** (Phone Calls, WhatsApp messages, SMS) always trigger an explicit **ActionConfirmation** dialog displaying the action type, recipient, and message before execution.
- If an operation cannot be performed or hardware is missing, Nova never pretends it succeeded; it explicitly informs the user.
- Strictly adheres to Android security boundaries (e.g. system shutdown/reboot opens Settings rather than attempting prohibited root operations).

---

## Requirements

- **Android Version**: Android 7.0 (API Level 24) minimum; target API 36.
- **Architecture**: Jetpack Compose (Material 3), Kotlin Coroutines, MVVM.
- **Gemini API**: Optional Gemini 3.5 Flash integration for fallback reasoning.

---

## Required Permissions

All permissions are declared in `AndroidManifest.xml` and follow the principle of least privilege:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.RECORD_AUDIO` | Required at runtime for voice input (Speech-to-Text). |
| `android.permission.CAMERA` | Required for Camera2 flash hardware torch control. |
| `android.permission.INTERNET` | For Gemini API network requests. |
| `android.permission.ACCESS_NETWORK_STATE` | Check connectivity for external AI calls. |
| `android.permission.MODIFY_AUDIO_SETTINGS` | Adjust volume levels and silent modes. |
| `android.permission.VIBRATE` | Haptic feedback for interactions. |

---

## Setup & Configuration

### Configuring Gemini API Key
The application reads the API key securely via `BuildConfig.GEMINI_API_KEY` through the Secrets Gradle Plugin:
1. Open the **Secrets panel in AI Studio** or update the `.env` file at the root:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```
2. In `.env.example`, ensure `GEMINI_API_KEY` is documented.
3. If no key is set, Nova will still execute all local phone control tools and app launcher commands, and gracefully inform you when conversational generative AI features require a key.

---

## Building the App

### Debug Build
```bash
gradle :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Release APK
```bash
gradle :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Release Android App Bundle (AAB) for Play Store
```bash
gradle :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

---

## Release Signing Configuration

Release signing is configured in `app/build.gradle.kts`:
- **Default Fallback**: For automated builds in container environments without custom credentials, it signs using the local debug keystore so the release artifact builds reliably.
- **Production Signing**: To sign with your private upload key, set the following environment variables or place your keystore at `/my-upload-key.jks`:
  ```bash
  export KEYSTORE_PATH="/path/to/my-upload-key.jks"
  export STORE_PASSWORD="your_keystore_password"
  export KEY_ALIAS="your_key_alias"
  export KEY_PASSWORD="your_key_password"
  gradle :app:bundleRelease
  ```

---

## Android Limitations & Fallbacks

- **Power Off / Reboot**: Modern non-rooted Android prevents 3rd-party apps from turning off the device directly (`android.permission.REBOOT` is signature-only). Nova explains this limitation and opens the Power/Settings menu for you.
- **Do-Not-Disturb Access**: On devices requiring Special Access for Do Not Disturb, Nova opens Sound Settings if ringer modification is restricted.
- **System-wide Brightness**: Without `WRITE_SETTINGS` system permission, third-party apps cannot modify system-wide display brightness globally; Nova adjusts in-app window attributes and offers direct deep linking into Android Display Settings.

---

## Play Store Publishing Checklist

1. **Google Play Console**: Create developer account and register app under `com.aistudio.novaassistant.vyza` (or your custom package ID).
2. **App Bundle Upload**: Upload `app-release.aab` from `app/build/outputs/bundle/release/app-release.aab`.
3. **Play App Signing**: Enroll in Google Play App Signing.
4. **Data Safety Declaration**: Declare microphone permission usage (voice input, not shared or sold).
5. **Privacy Policy**: Provide a public URL declaring voice recognition and offline tool usage.
6. **Store Listing**: Include app description, screenshots (phone & tablet), and high-resolution icon (512x512).
