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
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            textToSpeech?.setAudioAttributes(audioAttributes)
        }
    }

    private fun isBluetoothConnected(): Boolean {
        return audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
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

    private fun handleIncomingCall(packageName: String, callerTitle: String, callerText: String) {
        if (!isBluetoothConnected()) return

        val appName = when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("telegram") -> "Telegram"
            else -> "Phone"
        }

        val callerIdentity = if (callerTitle.isNotBlank()) callerTitle else callerText
        val announcement = if (callerIdentity.matches(Regex("^[+0-9\\s-]+$"))) {
            "$appName call from number ${callerIdentity.replace("", " ").trim()}"
        } else {
            "$appName call from $callerIdentity"
        }

        textToSpeech?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "CALL_ANNOUNCE")
        mainHandler.postDelayed({
            startVoiceListeningForCall()
        }, 3000)
    }

    private fun startVoiceListeningForCall() {
        startSpeechRecognition { command ->
            processCallCommand(command)
        }
    }

    private fun startVoiceListeningForReply() {
        startSpeechRecognition { command ->
            when {
                command.startsWith("reply ") || command.startsWith("send ") -> {
                    val replyText = command.substringAfter(" ").trim()
                    sendWhatsAppReply(replyText)
                }
                command.contains("read summary") -> {
                    readNotificationSummary()
                }
                command.startsWith("note ") -> {
                    saveVoiceNote(command.removePrefix("note ").trim())
                }
                command.contains("help sos") -> {
                    sensorSafetyManager.triggerEmergencySos("Voice Help SOS")
                }
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
            textToSpeech?.speak("Reply sent", TextToSpeech.QUEUE_ADD, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Reply error: ${e.message}")
        }
    }

    private fun processCallCommand(command: String) {
        val sbn = activeCallNotification ?: return
        val actions = sbn.notification.actions ?: return

        when {
            command.contains("pick up") || command.contains("answer") || command.contains("accept") -> {
                val answerAction = actions.firstOrNull {
                    it.title.toString().contains("answer", true) || it.title.toString().contains("accept", true)
                }
                try { answerAction?.actionIntent?.send() } catch (_: Exception) {}
            }
            command.contains("cut") || command.contains("reject") || command.contains("decline") -> {
                val declineAction = actions.firstOrNull {
                    it.title.toString().contains("decline", true) || it.title.toString().contains("reject", true)
                }
                try { declineAction?.actionIntent?.send() } catch (_: Exception) {}

                activeCallerNumber?.let { number ->
                    sendAutoSms(number)
                }
                resumeYouTubePlayback()
            }
            // Pixel-Style "Call Screening" (AI Secretary)
            command.contains("screen") || command.contains("screen call") -> {
                textToSpeech?.speak(
                    "Hi, the owner is currently busy. Please state your name and purpose of call.",
                    TextToSpeech.QUEUE_FLUSH, null, "SCREEN_CALL"
                )
            }
            command.contains("help sos") -> {
                sensorSafetyManager.triggerEmergencySos("Voice Help SOS")
            }
        }
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

            val summary = "You have $waCount unread WhatsApp messages and $otherCount other app notifications."
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

        textToSpeech?.speak("Note saved: $noteText", TextToSpeech.QUEUE_ADD, null, "NOTE_SAVED")
        Log.d(TAG, "Voice note saved: $noteText")
    }

    private fun sendAutoSms(phoneNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "I am riding/busy right now, will call you back shortly."
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-SMS error: ${e.message}")
        }
    }

    private fun startSpeechRecognition(onResult: (String) -> Unit) {
        if (isListeningForVoice) return
        isListeningForVoice = true

        mainHandler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                    isListeningForVoice = false
                    onResult(text)
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
