package com.assistant.adskip

import android.content.Context
import android.content.SharedPreferences

data class FeatureItem(
    val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val description: String,
    val triggerInfo: String,
    val voiceCommands: List<String> = emptyList(),
    val requiredPermission: String,
    val defaultEnabled: Boolean = true
)

object FeatureManager {

    private const val PREFS_NAME = "SmartAssistantFeatures"

    const val KEY_MASTER_ASSISTANT_ENABLED = "feat_master_assistant_enabled"
    const val KEY_YT_AUTO_SKIP = "feat_yt_auto_skip"
    const val KEY_YT_AD_MUTE = "feat_yt_ad_mute"
    const val KEY_SHORTS_SCROLL = "feat_shorts_scroll"
    const val KEY_SKIP_CHANNEL = "feat_skip_channel"
    const val KEY_PAYMENT_MODE = "feat_payment_mode"
    const val KEY_WA_READ_REPLY = "feat_wa_read_reply"
    const val KEY_CALL_ANNOUNCER = "feat_call_announcer"
    const val KEY_CALL_VOICE_CONTROL = "feat_call_voice_control"
    const val KEY_AI_SECRETARY = "feat_ai_secretary"
    const val KEY_EARPHONE_AUTO_PAUSE = "feat_earphone_auto_pause"
    const val KEY_ANTI_THEFT_ALARM = "feat_anti_theft_alarm"
    const val KEY_FLIP_TO_MUTE = "feat_flip_to_mute"
    const val KEY_BATTERY_ALERTS = "feat_battery_alerts"
    const val KEY_VOICE_NOTES = "feat_voice_notes"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isMasterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MASTER_ASSISTANT_ENABLED, true)
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ASSISTANT_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context, featureId: String, defaultValue: Boolean = true): Boolean {
        if (!isMasterEnabled(context)) return false
        return getPrefs(context).getBoolean(featureId, defaultValue)
    }

    fun setEnabled(context: Context, featureId: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(featureId, enabled).apply()
    }

    fun getAllFeatures(): List<FeatureItem> {
        return listOf(
            FeatureItem(
                id = KEY_YT_AUTO_SKIP,
                title = "YouTube Ad Auto-Skip",
                summary = "Instantly clicks 'Skip Ad' buttons in under 50ms",
                category = "YouTube & Media",
                description = "Monitors YouTube video player in real time using the Accessibility service. When a 'Skip Ad', 'Skip Ads', or countdown skip button is displayed, it triggers an instant recursive click in <50ms without touching the screen.",
                triggerInfo = "Trigger: Active inside YouTube App when an ad presents a skip button.\nPermission: Accessibility Service.",
                voiceCommands = emptyList(),
                requiredPermission = "Accessibility Service",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_YT_AD_MUTE,
                title = "YouTube Ad In-Stream Mute",
                summary = "Mutes unskippable ads; restores volume right after",
                category = "YouTube & Media",
                description = "Detects unskippable pre-roll and mid-roll ads by watching YouTube player nodes (e.g. ad progress timers, countdowns). Automatically mutes media audio so you never suffer loud advertisements, then restores your previous volume the millisecond the video resumes.",
                triggerInfo = "Trigger: Unskippable ad playing in YouTube.\nPermission: Accessibility Service & Audio Settings.",
                voiceCommands = emptyList(),
                requiredPermission = "Accessibility Service",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_SHORTS_SCROLL,
                title = "Shorts & Reels Smart Auto-Scroller",
                summary = "Auto-scrolls on video completion & skips sponsored ads instantly",
                category = "YouTube & Media",
                description = "Monitors YouTube Shorts and Instagram Reels progress bar and loop indicators. Automatically advances to the next video the moment playback finishes (or upon video loop reset). Instantly swipes away sponsored / promoted reels and shorts without delay.",
                triggerInfo = "Trigger: Active in YouTube Shorts & Instagram Reels.\nPermission: Accessibility Service (Gesture Dispatch).",
                voiceCommands = emptyList(),
                requiredPermission = "Accessibility Service",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_SKIP_CHANNEL,
                title = "'Don't Recommend' Auto-Click",
                summary = "Automatically dismiss unwanted channels via 3-dot menu",
                category = "YouTube & Media",
                description = "Quickly cleans your recommendation feed by finding the 3-dot menu and executing 'Don't recommend channel' automatically.",
                triggerInfo = "Trigger: Assistant action inside YouTube feed.\nPermission: Accessibility Service.",
                voiceCommands = emptyList(),
                requiredPermission = "Accessibility Service",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_PAYMENT_MODE,
                title = "Payment Mode Quick Settings Tile",
                summary = "One-tap privacy freeze for GPay, PhonePe, and Paytm",
                category = "Privacy & Safety",
                description = "Instantly pauses all accessibility clicking, reading, and voice listeners whenever you tap the 'Pay Mode' Quick Settings tile. Ensures 100% security and zero interference during UPI PIN entry and bank transfers.",
                triggerInfo = "Trigger: Tap 'Pay Mode' tile in Android Quick Settings dropdown.\nPermission: Quick Settings Tile Service.",
                voiceCommands = emptyList(),
                requiredPermission = "Quick Settings Tile",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_WA_READ_REPLY,
                title = "WhatsApp Read-Aloud & Voice Reply",
                summary = "Reads incoming chats in earbuds; reply hands-free",
                category = "Calls & Communication",
                description = "When earphones or Bluetooth are connected, reads incoming WhatsApp messages in clear speech. Then automatically listens for your voice command to reply without picking up the phone.",
                triggerInfo = "Trigger: Incoming WhatsApp notification while headphones are connected.\nPermission: Notification Listener + Microphone.",
                voiceCommands = listOf("reply <your message>", "send <your message>", "bhejo <message>", "jawab do <message>", "बोलो / भेजो <message>"),
                requiredPermission = "Notification Access & Microphone",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_CALL_ANNOUNCER,
                title = "Incoming Caller ID Announcer",
                summary = "Speaks caller name or comfortable 5-digit number chunks",
                category = "Calls & Communication",
                description = "Announces incoming phone calls (and WhatsApp / Instagram VoIP calls) through your earphones. Formatted in comfortable, natural audio (split 10-digit numbers into friendly 5-digit chunks like '98765, 43210') with soothing volume so your ears never get hurt.",
                triggerInfo = "Trigger: Incoming phone or VoIP call while headset is connected.\nPermission: Notification Listener & Read Phone State.",
                voiceCommands = emptyList(),
                requiredPermission = "Notification Access & Phone State",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_CALL_VOICE_CONTROL,
                title = "Voice Call Answering & Cutting",
                summary = "Answer or reject calls with bilingual voice commands",
                category = "Calls & Communication",
                description = "Tri-layer call management using TelecomManager, hardware HeadsetHook, and notification actions. Works in both English and Hindi/Hinglish even in noisy environments.",
                triggerInfo = "Trigger: Ringer active with earphones on.\nPermission: TelecomManager & Microphone.",
                voiceCommands = listOf(
                    "To Answer: 'pick up', 'answer', 'uthao', 'utha lo', 'call uthao', 'उठाओ', 'उठा लो'",
                    "To Cut: 'cut it', 'cut', 'reject', 'decline', 'kato', 'kaat do', 'फोन काटो', 'काट दो'"
                ),
                requiredPermission = "Answer Calls & Microphone",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_AI_SECRETARY,
                title = "AI Secretary & Auto-SMS on Decline",
                summary = "Says 'Pawan is busy' and sends auto-SMS when call declined",
                category = "Calls & Communication",
                description = "Screens calls by saying 'नमस्ते, पवन अभी व्यस्त हैं। कृपया अपना नाम और काम बताएँ।' on screen command, and automatically sends a friendly SMS ('Pawan is busy right now, will call you back shortly') if a call is declined.",
                triggerInfo = "Trigger: When declining or screening a call via voice.\nPermission: Send SMS & Microphone.",
                voiceCommands = listOf("screen", "screen call", "poocho", "pucho", "पूछो", "स्क्रीन करो"),
                requiredPermission = "Send SMS & Microphone",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_EARPHONE_AUTO_PAUSE,
                title = "Earphone Disconnect Auto-Pause",
                summary = "Immediately pauses media when earbuds are disconnected",
                category = "Audio & Accessories",
                description = "Catches the system ACTION_AUDIO_BECOMING_NOISY and Bluetooth disconnection events immediately, pausing whatever video or music is playing so audio never blasts through the phone speaker in public.",
                triggerInfo = "Trigger: Earbuds unplugged or Bluetooth disconnects.\nPermission: Audio Settings.",
                voiceCommands = emptyList(),
                requiredPermission = "Audio Management",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_ANTI_THEFT_ALARM,
                title = "Anti-Theft Earbud Disconnect Alarm",
                summary = "Triggers siren alarm if earphones pulled while locked",
                category = "Privacy & Safety",
                description = "If you are sleeping on a train or walking in public with earbuds in and someone snatches your phone or pulls out your earphones while the screen is locked, an emergency siren alarm immediately plays at maximum volume to deter theft.",
                triggerInfo = "Trigger: Disconnecting earphones while device keyguard is locked.\nPermission: Keyguard & Audio/Alarm.",
                voiceCommands = emptyList(),
                requiredPermission = "Keyguard & Alarm Management",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_FLIP_TO_MUTE,
                title = "Flip-to-Mute Ringing Calls",
                summary = "Silence ringer by placing phone face down on a table",
                category = "Privacy & Safety",
                description = "Uses the device accelerometer sensor (Z-axis < -7 m/s²). If your phone is ringing during a meeting or class, simply flip the device face down onto a table to silence the ringer instantly.",
                triggerInfo = "Trigger: Phone ringing and flipped face down.\nPermission: Accelerometer Sensor.",
                voiceCommands = emptyList(),
                requiredPermission = "Accelerometer Sensor",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_BATTERY_ALERTS,
                title = "Smart Battery Voice Alerts",
                summary = "Announces 100% full charge and 15% low battery warnings",
                category = "System & Utility",
                description = "Keeps your battery healthy. Announces 'Battery fully charged, please unplug charger' when reaching 100%, and warns 'Battery low: 15% remaining' so you never run out of charge unexpectedly.",
                triggerInfo = "Trigger: Battery hits 100% while charging or drops to 15% on battery.\nPermission: Battery Broadcast.",
                voiceCommands = emptyList(),
                requiredPermission = "Battery Monitor",
                defaultEnabled = true
            ),
            FeatureItem(
                id = KEY_VOICE_NOTES,
                title = "Voice-to-Self Notes & Summary",
                summary = "Speak 'note <text>' to save thoughts; 'summary' to read back",
                category = "System & Utility",
                description = "Allows taking quick memos hands-free while walking or driving. Say 'note buy milk' or 'likho meeting at 5pm' to save thoughts, and 'summary' or 'message padho' to hear active unread notifications.",
                triggerInfo = "Trigger: Voice responder listening mode.\nPermission: Microphone & Notification Listener.",
                voiceCommands = listOf("note <memo text>", "likho <memo text>", "summary", "message padho", "नोट / लिखो <मैसेज>"),
                requiredPermission = "Microphone & Notification Access",
                defaultEnabled = true
            )
        )
    }
}
