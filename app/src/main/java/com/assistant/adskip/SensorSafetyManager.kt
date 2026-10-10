package com.assistant.adskip

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log

class SensorSafetyManager(
    private val context: Context,
    private val tts: TextToSpeech?
) : SensorEventListener, LocationListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Battery Alert state
    private var lastAnnouncedBattery = -1

    // Flip to mute state
    var isPhoneRinging = false

    companion object {
        const val TAG = "SensorSafetyManager"
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            if (level != -1 && scale > 0) {
                val percentage = (level * 100) / scale
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL

                if (percentage == 100 && isCharging && lastAnnouncedBattery != 100) {
                    lastAnnouncedBattery = 100
                    tts?.speak("Battery fully charged, please unplug charger.", TextToSpeech.QUEUE_ADD, null, "BATTERY_FULL")
                } else if (percentage == 15 && !isCharging && lastAnnouncedBattery != 15) {
                    lastAnnouncedBattery = 15
                    tts?.speak("Battery low: 15% remaining.", TextToSpeech.QUEUE_ADD, null, "BATTERY_LOW")
                } else if (percentage in 16..99) {
                    lastAnnouncedBattery = percentage
                }
            }
        }
    }

    fun start() {
        // Register Battery Receiver
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Register Accelerometer for Flip-to-Mute
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Start GPS Speed Tracking
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, this)
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted for speed tracking.")
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
            sensorManager.unregisterListener(this)
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sensors: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val z = event.values[2]

        // Flip-to-Mute (When ringing and flipped face-down on table: Z < -7 m/s²)
        if (isPhoneRinging && z < -7.0f) {
            Log.d(TAG, "Phone flipped face down! Muting ringer.")
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            isPhoneRinging = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Speed Tracking (Logged safely without forcing harsh earphone volumes)
    override fun onLocationChanged(location: Location) {
        val speedKmh = location.speed * 3.6f
        Log.d(TAG, "Current speed: $speedKmh km/h")
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // Anti-Theft Alarm on Earbud Disconnect when Screen Locked
    fun triggerAntiTheftAlarmIfLocked() {
        try {
            if (keyguardManager.isKeyguardLocked) {
                Log.w(TAG, "Anti-theft: Earbuds pulled while phone was locked! Sounding ALARM!")
                tts?.speak("Warning! Earphones disconnected while phone is locked!", TextToSpeech.QUEUE_FLUSH, null, null)

                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone?.play()

                Handler(Looper.getMainLooper()).postDelayed({
                    try { ringtone?.stop() } catch (_: Exception) {}
                }, 8000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in anti-theft alarm: ${e.message}")
        }
    }
}
