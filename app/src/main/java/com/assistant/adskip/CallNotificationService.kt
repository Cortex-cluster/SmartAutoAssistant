package com.assistant.adskip

import android.app.Notification
import android.app.RemoteInput
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import android.os.Build
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class CallNotificationService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private lateinit var audioManager: AudioManager
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeCallNotification: StatusBarNotification? = null
    private var activeCallerNumber: String? = null
    private var activeWhatsAppReplyAction: Notification.Action? = null
    private var isListeningForVoice = false

    private lateinit var sensorSafetyManager: SensorSafetyManager
    private lateinit var notesPrefs: SharedPreferences

    companion object {
        const val TAG = "CallNotificationService"
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == action ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                Log.d(TAG, "Earphones disconnected!")
                pauseMedia()
                // Anti-Theft Alarm if phone was locked
                sensorSafetyManager.triggerAntiTheftAlarmIfLocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)
        sensorSafetyManager = SensorSafetyManager(this, textToSpeech)
        sensorSafetyManager.start()
        notesPrefs = getSharedPreferences("SmartAssistantNotes", Context.MODE_PRIVATE)

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(noisyReceiver, filter)
        Log.d(TAG, "CallNotificationService created with safety sensors.")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            textToSpeech?.setAudioAttributes(audioAttributes)
            textToSpeech?.setSpeechRate(0.95f)
            textToSpeech?.setPitch(1.0f)
        }
    }

    private fun isBluetoothConnected(): Boolean {
        return try {
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn
        } catch (_: Exception) {
            true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        // If Payment Mode is ON from Quick Settings, bypass interference
        if (PaymentModeTileService.isPaymentModeActive(this)) {
            return
        }

        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: ""
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // 1. Spam Call Auto-Blocker
        val isSpam = title.contains("spam", true) ||
                     title.contains("scam", true) ||
                     title.contains("telemarketer", true) ||
                     text.contains("suspected spam", true)

        if (isSpam) {
            Log.d(TAG, "Spam detected ($title). Auto-rejecting.")
            val declineAction = notification.actions?.firstOrNull {
                it.title.toString().contains("decline", true) ||
                it.title.toString().contains("reject", true) ||
                it.title.toString().contains("dismiss", true)
            }
            try { declineAction?.actionIntent?.send() } catch (_: Exception) {}
            return
        }

        // 2. Incoming Call Detection
        val isCall = notification.category == Notification.CATEGORY_CALL ||
                (notification.actions?.any {
                    it.title.toString().contains("answer", true) ||
                    it.title.toString().contains("accept", true)
                } == true)

        if (isCall) {
            activeCallNotification = sbn
            sensorSafetyManager.isPhoneRinging = true
            if (title.matches(Regex("^[+0-9\\s-]+$"))) {
                activeCallerNumber = title.replace(" ", "")
            }
            handleIncomingCall(packageName, title, text)
            return
        }

        // 3. WhatsApp Message Reading Aloud
        if (packageName.contains("whatsapp") && isBluetoothConnected() && title.isNotBlank() && text.isNotBlank()) {
            handleWhatsAppMessage(notification, title, text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.id == activeCallNotification?.id) {
            activeCallNotification = null
            sensorSafetyManager.isPhoneRinging = false
            stopVoiceListening()
            resumeYouTubePlayback()
        }
    }

    private fun handleWhatsAppMessage(notification: Notification, sender: String, message: String) {
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        } ?: return

        activeWhatsAppReplyAction = replyAction
        val announcement = "$sender says: $message"
        textToSpeech?.speak(announcement, TextToSpeech.QUEUE_ADD, null, "WA_MSG")

        mainHandler.postDelayed({
            startVoiceListeningForReply()
        }, 3500)
    }

    private fun formatCallerForSpeech(raw: String): String {
        val clean = raw.trim()
        val digitsOnly = clean.replace(Regex("[^0-9]"), "")

        // If it's a saved contact name (contains letters)
        if (clean.any { it.isLetter() }) {
            return clean
        }

        // If it's a 10+ digit phone number, read in friendly 5-digit chunks: "98765 43210"
        val tenDigits = if (digitsOnly.length > 10) digitsOnly.takeLast(10) else digitsOnly
        if (tenDigits.length == 10) {
            return "${tenDigits.substring(0, 5)}, ${tenDigits.substring(5)}"
        }

        return clean
    }

    private fun handleIncomingCall(packageName: String, callerTitle: String, callerText: String) {
        if (!isBluetoothConnected()) return

        val appName = when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("telegram") -> "Telegram"
            else -> "Phone"
        }

        val callerIdentity = if (callerTitle.isNotBlank()) callerTitle else callerText
        val naturalCaller = formatCallerForSpeech(callerIdentity)

        val announcement = if (callerIdentity.any { it.isLetter() }) {
            "$appName call from $naturalCaller"
        } else {
            "Incoming call from $naturalCaller"
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.65f) // Comfortable earphone volume
        }
        textToSpeech?.speak(announcement, TextToSpeech.QUEUE_FLUSH, params, "CALL_ANNOUNCE")
        mainHandler.postDelayed({
            startVoiceListeningForCall()
        }, 3000)
    }

    private fun startVoiceListeningForCall() {
        startSpeechRecognition { candidates ->
            processCallCommand(candidates)
        }
    }

    private fun startVoiceListeningForReply() {
        startSpeechRecognition { candidates ->
            processReplyCommand(candidates)
        }
    }

    private fun processReplyCommand(commands: List<String>) {
        // 1. WhatsApp Voice Reply (English + Hindi: reply, bhejo, jawab do, भेजो, जवाब दो)
        for (cmd in commands) {
            val prefixes = listOf("reply ", "send ", "bhejo ", "jawab do ", "bolo ", "भेजो ", "जवाब दो ", "बोलो ")
            val matched = prefixes.firstOrNull { cmd.startsWith(it) }
            if (matched != null) {
                val replyText = cmd.substringAfter(matched).trim()
                sendWhatsAppReply(replyText)
                return
            }
        }

        // 2. Read Summary (English + Hindi: summary, समरी, message padho, मैसेज पढ़ो)
        if (commands.any { it.contains("summary") || it.contains("समरी") || it.contains("message") || it.contains("मैसेज") }) {
            readNotificationSummary()
            return
        }

        // 3. Voice Note (English + Hindi: note, likho, नोट, लिखो)
        for (cmd in commands) {
            val prefixes = listOf("note ", "likho ", "नोट ", "लिखो ")
            val matched = prefixes.firstOrNull { cmd.startsWith(it) }
            if (matched != null) {
                saveVoiceNote(cmd.substringAfter(matched).trim())
                return
            }
        }
    }

    private fun sendWhatsAppReply(replyText: String) {
        val action = activeWhatsAppReplyAction ?: return
        val remoteInput = action.remoteInputs?.firstOrNull() ?: return

        try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(this, 0, intent)
            textToSpeech?.speak("Reply sent: $replyText", TextToSpeech.QUEUE_ADD, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Reply error: ${e.message}")
        }
    }

    private fun processCallCommand(commands: List<String>) {
        val sbn = activeCallNotification
        val actions = sbn?.notification?.actions

        // Pick up Call (English: pick up, answer | Hindi/Hinglish: uthao, utha, उठाओ, उठा लो, फोन उठाओ)
        val isAnswer = commands.any { cmd ->
            cmd.contains("pick up") || cmd.contains("answer") || cmd.contains("accept") ||
            cmd.contains("utha") || cmd.contains("uthhao") || cmd.contains("uthao") ||
            cmd.contains("utha lo") || cmd.contains("phone uthao") || cmd.contains("call uthao") ||
            cmd.contains("उठाओ") || cmd.contains("उठा") || cmd.contains("फोन उठाओ") ||
            cmd.contains("कॉल उठाओ") || cmd.contains("हां") || cmd.contains("उठा लो") ||
            cmd.contains("yes") || cmd.contains("haan")
        }

        // Cut / Decline Call (English: cut, reject | Hindi/Hinglish: kato, kaat do, काटो, काट, फोन काटो)
        val isDecline = commands.any { cmd ->
            cmd.contains("cut") || cmd.contains("reject") || cmd.contains("decline") ||
            cmd.contains("kato") || cmd.contains("kaat") || cmd.contains("kat do") ||
            cmd.contains("kaat do") || cmd.contains("phone kato") || cmd.contains("call kato") ||
            cmd.contains("काटो") || cmd.contains("काट") || cmd.contains("काट दो") ||
            cmd.contains("फोन काटो") || cmd.contains("कॉल काटो") || cmd.contains("नहीं") ||
            cmd.contains("drop") || cmd.contains("no")
        }

        // Pixel-Style Call Screening (English: screen | Hindi/Hinglish: poocho, pucho, पूछो, स्क्रीन)
        val isScreen = commands.any { cmd ->
            cmd.contains("screen") || cmd.contains("screen call") || cmd.contains("poocho") ||
            cmd.contains("pucho") || cmd.contains("स्क्रीन") || cmd.contains("पूछो") ||
            cmd.contains("स्क्रीन करो") || cmd.contains("पता करो")
        }

        when {
            isAnswer -> {
                answerCall(actions)
            }
            isDecline -> {
                declineCall(actions)
            }
            isScreen -> {
                textToSpeech?.speak(
                    "नमस्ते, फ़ोन के मालिक अभी व्यस्त हैं। कृपया अपना नाम और काम बताएँ।",
                    TextToSpeech.QUEUE_FLUSH, null, "SCREEN_CALL_HI"
                )
            }
        }
    }

    private fun answerCall(actions: Array<Notification.Action>?) {
        Log.d(TAG, "Executing call answer across all layers...")

        // 1. Android TelecomManager (Official OS Call Answering API)
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecomManager?.acceptRingingCall()
                Log.d(TAG, "Called telecomManager.acceptRingingCall()")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager answer error: ${e.message}")
        }

        // 2. HeadsetHook Key Event (Universal Bluetooth earphone answer hardware trigger)
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
            Log.d(TAG, "Dispatched KEYCODE_HEADSETHOOK for answering call.")
        } catch (e: Exception) {
            Log.w(TAG, "HeadsetHook answer error: ${e.message}")
        }

        // 3. Notification Action Intent (WhatsApp, Instagram, Telegram VoIP calls)
        val answerAction = actions?.firstOrNull {
            it.title.toString().contains("answer", true) ||
            it.title.toString().contains("accept", true) ||
            it.title.toString().contains("उठा", true)
        }
        try {
            answerAction?.actionIntent?.send()
            Log.d(TAG, "Triggered answer PendingIntent.")
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent answer error: ${e.message}")
        }
    }

    private fun declineCall(actions: Array<Notification.Action>?) {
        Log.d(TAG, "Executing call decline across all layers...")

        // 1. Android TelecomManager (Official OS Call Rejection API)
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager?.endCall()
                Log.d(TAG, "Called telecomManager.endCall()")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager endCall error: ${e.message}")
        }

        // 2. Notification Action Intent (WhatsApp, Instagram, Phone dialer)
        val declineAction = actions?.firstOrNull {
            it.title.toString().contains("decline", true) ||
            it.title.toString().contains("reject", true) ||
            it.title.toString().contains("dismiss", true) ||
            it.title.toString().contains("काट", true)
        }
        try {
            declineAction?.actionIntent?.send()
            Log.d(TAG, "Triggered decline PendingIntent.")
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent decline error: ${e.message}")
        }

        // 3. Auto-SMS on Decline
        activeCallerNumber?.let { number ->
            sendAutoSms(number)
        }
        resumeYouTubePlayback()
    }

    // "Read My Notifications" Voice Summary
    private fun readNotificationSummary() {
        try {
            val notifications = activeNotifications ?: return
            var waCount = 0
            var otherCount = 0

            for (n in notifications) {
                if (n.packageName.contains("whatsapp")) waCount++
                else if (!n.packageName.contains("android") && !n.isOngoing) otherCount++
            }

            val summary = "आपके पास $waCount नए व्हाट्सएप मैसेज और $otherCount अन्य नोटिफिकेशन्स हैं।"
            textToSpeech?.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "NOTIF_SUMMARY")
        } catch (e: Exception) {
            Log.e(TAG, "Summary error: ${e.message}")
        }
    }

    // Voice-to-Self Note
    private fun saveVoiceNote(noteText: String) {
        val existingNotes = notesPrefs.getString("notes_list", "") ?: ""
        val newNotes = "$existingNotes\n• $noteText (${System.currentTimeMillis()})"
        notesPrefs.edit().putString("notes_list", newNotes).apply()

        textToSpeech?.speak("नोट सेव हो गया: $noteText", TextToSpeech.QUEUE_ADD, null, "NOTE_SAVED")
        Log.d(TAG, "Voice note saved: $noteText")
    }

    private fun sendAutoSms(phoneNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "I am riding/busy right now, will call you back shortly. (व्यस्त हूँ, बाद में कॉल करता हूँ)"
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-SMS error: ${e.message}")
        }
    }

    private fun startSpeechRecognition(onResult: (List<String>) -> Unit) {
        if (isListeningForVoice) return
        isListeningForVoice = true

        mainHandler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val lowerList = list.map { it.lowercase() }
                    isListeningForVoice = false
                    onResult(lowerList)
                }
                override fun onError(error: Int) { isListeningForVoice = false }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    private fun stopVoiceListening() {
        isListeningForVoice = false
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }

    private fun pauseMedia() {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }

    private fun resumeYouTubePlayback() {
        mainHandler.postDelayed({
            try {
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            } catch (_: Exception) {}
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(noisyReceiver)
        sensorSafetyManager.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
