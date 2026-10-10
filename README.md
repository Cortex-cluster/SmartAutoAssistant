# Smart Auto Assistant 📱⚡

An intelligent, hands-free Android background assistant designed for seamless YouTube watching, riding/commuting on Bluetooth, and zero-touch call and message handling.

---

## 🌟 Key Features

### 1. YouTube & Media Superpowers
* **Instant Ad Auto-Skip:** Automatically clicks the "Skip Ad" button in under 50 milliseconds the microsecond it appears.
* **In-Stream Ad Muter:** Silences media volume during unskippable video ads and instantly restores audio when the video resumes.
* **Shorts & Reels Auto-Scroller:** Automatically swipes up to the next video hands-free when eating, working out, or cooking.
* **Earphone Disconnect Auto-Pause:** Instantly pauses media if Bluetooth earphones disconnect or are pulled out, preventing sound blasting in public.

### 2. Hands-Free Bluetooth Riding Companion
* **Caller ID Announcement:** Tells you which app a call is coming from (WhatsApp, Instagram, Telegram, Phone) and speaks the caller's name or spells unsaved numbers into your earbuds.
* **Voice Responder:** Answer or decline incoming calls completely hands-free by speaking:
  * *"Pick up"* or *"Answer"* → Answers the call.
  * *"Cut it"* or *"Decline"* → Rejects the call and automatically resumes YouTube.
* **Auto-SMS on Decline:** When you say *"Cut it"*, it automatically sends an SMS: *"I am riding/busy right now, will call you back shortly."*
* **WhatsApp Read-Aloud & Voice Reply:** Announces incoming WhatsApp messages into your ear and lets you reply by voice (*"Reply I'm on my way"*).
* **Spam Call Auto-Blocker:** Automatically drops incoming spam and telemarketing calls without interrupting your music.

---

## 🔒 Privacy & Security
* **Zero Internet Permission:** The app does NOT request `android.permission.INTERNET`. It is physically impossible for the app to send data off your device.
* **100% Local & On-Device:** All speech synthesis, recognition, and automation runs locally on your Android OS.

---

## 🛠️ Tech Stack & Architecture
* **Language:** Kotlin
* **Target SDK:** Android 34 (Android 14) / Min SDK: Android 26 (Android 8.0)
* **Core APIs:**
  * `AccessibilityService` (YouTube Skip & Gesture Automation)
  * `NotificationListenerService` (Call & WhatsApp Message Interception)
  * `TextToSpeech` & `SpeechRecognizer` (Voice Engine)
  * `BroadcastReceiver` (Bluetooth Disconnect Watcher)

---

## 🚀 Building From Source

```bash
# Clone the repository
git clone https://github.com/Cortex-cluster/SmartAutoAssistant.git
cd SmartAutoAssistant

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```
