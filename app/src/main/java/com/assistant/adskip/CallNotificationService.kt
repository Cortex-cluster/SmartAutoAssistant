package com.assistant.adskip

import android.app.Notification
import android.app.RemoteInput
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    companion object {
        const val TAG = "CallNotificationService"
    }

    // BroadcastReceiver for Earphone Disconnect (Prevents loud speaker playback)
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == action ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                Log.d(TAG, "Earphones disconnected! Auto-pausing media.")
                pauseMedia()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(noisyReceiver, filter)
        Log.d(TAG, "CallNotificationService created.")
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
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: ""
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // 1. Check for SPAM Call Auto-Blocker
        val isSpam = title.contains("spam", ignoreCase = true) ||
                     title.contains("scam", ignoreCase = true) ||
                     title.contains("telemarketer", ignoreCase = true) ||
                     text.contains("suspected spam", ignoreCase = true)

        if (isSpam) {
            Log.d(TAG, "Spam detected ($title). Auto-rejecting immediately.")
            val declineAction = notification.actions?.firstOrNull {
                it.title.toString().contains("decline", true) ||
                it.title.toString().contains("reject", true) ||
                it.title.toString().contains("dismiss", true)
            }
            try {
                declineAction?.actionIntent?.send()
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting spam call: ${e.message}")
            }
            return
        }

        // 2. Incoming Call Detection (WhatsApp, Instagram, Phone)
        val isCall = notification.category == Notification.CATEGORY_CALL ||
                (notification.actions?.any {
                    it.title.toString().contains("answer", true) ||
                    it.title.toString().contains("accept", true)
                } == true)

        if (isCall) {
            activeCallNotification = sbn
            if (title.matches(Regex("^[+0-9\\s-]+$"))) {
                activeCallerNumber = title.replace(" ", "")
            }
            handleIncomingCall(packageName, title, text)
            return
        }

        // 3. WhatsApp Message Reading Aloud & Voice Reply (Bluetooth only)
        if (packageName.contains("whatsapp") && isBluetoothConnected() && title.isNotBlank() && text.isNotBlank()) {
            handleWhatsAppMessage(notification, title, text)
        }
    }

    private fun handleWhatsAppMessage(notification: Notification, sender: String, message: String) {
        // Find the Quick Reply action
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        } ?: return

        activeWhatsAppReplyAction = replyAction
        val announcement = "$sender says: $message"
        textToSpeech?.speak(announcement, TextToSpeech.QUEUE_ADD, null, "WA_MSG")

        // Listen for "Reply [message]" after speaking
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
            if (command.startsWith("reply ") || command.startsWith("send ")) {
                val replyText = command.substringAfter(" ").trim()
                sendWhatsAppReply(replyText)
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
            Log.d(TAG, "Voice WhatsApp reply sent successfully: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp reply: ${e.message}")
        }
    }

    private fun processCallCommand(command: String) {
        val sbn = activeCallNotification ?: return
        val actions = sbn.notification.actions ?: return

        if (command.contains("pick up") || command.contains("answer") || command.contains("accept")) {
            val answerAction = actions.firstOrNull {
                it.title.toString().contains("answer", true) || it.title.toString().contains("accept", true)
            }
            try {
                answerAction?.actionIntent?.send()
            } catch (e: Exception) {
                Log.e(TAG, "Answer error: ${e.message}")
            }
        } else if (command.contains("cut") || command.contains("reject") || command.contains("decline")) {
            val declineAction = actions.firstOrNull {
                it.title.toString().contains("decline", true) || it.title.toString().contains("reject", true)
            }
            try {
                declineAction?.actionIntent?.send()
            } catch (e: Exception) {
                Log.e(TAG, "Decline error: ${e.message}")
            }

            // Auto-SMS when you say "Cut it" while riding
            activeCallerNumber?.let { number ->
                sendAutoSms(number)
            }
            resumeYouTubePlayback()
        }
    }

    private fun sendAutoSms(phoneNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "I am riding/busy right now, will call you back shortly."
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "Auto-SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-SMS: ${e.message}")
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

    private fun pauseMedia() {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing media: ${e.message}")
        }
    }

    private fun resumeYouTubePlayback() {
        mainHandler.postDelayed({
            try {
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming media: ${e.message}")
            }
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(noisyReceiver)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
