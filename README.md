# Smart Auto Assistant 📱⚡

An intelligent, hands-free Android background assistant designed for seamless YouTube watching, riding/commuting on Bluetooth, and zero-touch call and message handling.

---

## 🌟 Key Features

### 1. 🛡️ Payment & System Perks
* **💳 Quick Settings Tile (Payment Mode):** Adds a dedicated button in your notification drawer to toggle the assistant **ON/OFF in one tap** before opening Google Pay / PhonePe so banking security never complains.
* **🔋 Smart Battery Voice Alerts:** Speaks *"Battery fully charged, please unplug charger"* at 100%, and warns at 15%.
* **🎨 Custom Cyber Tech Icon:** Sleek cybernetic shield and lightning bolt launcher icon.

### 2. 🏍️ Riding, Travel & Safety Superpowers
* **📢 Speed-Adaptive Volume:** Automatically boosts earphone volume when riding fast (>25 km/h) against wind noise, and lowers it when stopped at traffic lights.
* **🚨 Anti-Theft Earbud Alarm:** If your phone is locked and someone pulls out your earbuds, the phone immediately sounds a loud security alarm.
* **🆘 Emergency Voice & Shake SOS:** Speak *"Help SOS"* or violently shake the phone 5 times to send an emergency SMS with live Google Maps GPS coordinates.
* **🔄 Flip-to-Mute:** Silence any ringing call instantly by simply flipping your phone face-down on the table.

### 3. 🤖 AI Secretary & Voice Engine
* **Pixel-Style "Call Screening":** Say *"Screen call"* → Assistant speaks: *"Hi, the owner is currently busy. Please state your name and purpose of call."*
* **📋 "Read Summary" Voice Command:** Say *"Read summary"* to hear all unread notifications summarized into your ear.
* **🗣️ Voice-to-Self Note:** Say *"Note: Buy milk and finish report"* to save voice notes instantly.
* **Caller ID Announcement & Voice Pickup:** Speaks caller name on WhatsApp/Insta/Phone; say *"Pick up"* to answer or *"Cut it"* to decline.
* **Auto-SMS on Decline:** Sends *"I am riding/busy right now..."* when you decline a call.
* **WhatsApp Read-Aloud & Voice Reply:** Speaks incoming messages and lets you voice reply (*"Reply I'm on my way"*).

### 4. 📺 YouTube & Media Superpowers
* **Instant Ad Auto-Skip:** Automatically clicks the "Skip Ad" button in under 50ms.
* **In-Stream Ad Muter:** Silences media during unskippable ads; restores volume when the video plays.
* **Shorts & Reels Auto-Scroller:** Automatically swipes to the next video hands-free when eating, working out, or cooking.
* **Earphone Disconnect Auto-Pause:** Instantly pauses playback when earphones disconnect to prevent loud audio in public.

---

## 🔒 Privacy & Security
* **Zero Internet Permission:** The app does NOT request `android.permission.INTERNET`. It is physically impossible for the app to send data off your device.
* **100% Local & On-Device:** All sensors, speech recognition, and automations run entirely on your device.

---

## 🛠️ Tech Stack & Architecture
* **Language:** Kotlin
* **Target SDK:** Android 34 (Android 14) / Min SDK: Android 26 (Android 8.0)
* **Core APIs:**
  * `TileService` (Quick Settings Payment Toggle)
  * `SensorManager` (Accelerometer Shake SOS & Flip-to-Mute)
  * `LocationManager` (Speed-Adaptive Volume & GPS SOS)
  * `AccessibilityService` (YouTube Skip & Gesture Automation)
  * `NotificationListenerService` (Call & WhatsApp Message Interception)
  * `TextToSpeech` & `SpeechRecognizer` (Voice Engine)
  * `KeyguardManager` (Anti-Theft Screen Lock Watcher)

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
