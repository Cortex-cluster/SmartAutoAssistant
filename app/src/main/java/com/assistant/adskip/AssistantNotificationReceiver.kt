package com.assistant.adskip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AssistantNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_MASTER = "com.assistant.adskip.ACTION_TOGGLE_MASTER"
        const val ACTION_UPDATE_NOTIFICATION = "com.assistant.adskip.ACTION_UPDATE_NOTIFICATION"
        private const val CHANNEL_ID = "assistant_control_channel"
        private const val NOTIFICATION_ID = 9001

        fun showOrUpdateControlNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Smart Assistant Quick Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification to quickly turn Smart Assistant ON or OFF"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val isMasterOn = FeatureManager.isMasterEnabled(context)
            val toggleIntent = Intent(context, AssistantNotificationReceiver::class.java).apply {
                action = ACTION_TOGGLE_MASTER
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                102,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val actionTitle = if (isMasterOn) "Turn OFF Assistant" else "Turn ON Assistant"
            val contentTitle = if (isMasterOn) "Smart Assistant is RUNNING" else "Smart Assistant is PAUSED"
            val contentText = if (isMasterOn) {
                "Ads skip & media auto-control active • Tap to pause"
            } else {
                "All assistant triggers paused • Tap to resume"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    R.drawable.ic_power,
                    actionTitle,
                    togglePendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_MASTER) {
            val currentlyOn = FeatureManager.isMasterEnabled(context)
            val newState = !currentlyOn
            FeatureManager.setMasterEnabled(context, newState)
            showOrUpdateControlNotification(context)

            // Notify UI or services if needed
            val updateIntent = Intent("com.assistant.adskip.MASTER_STATE_CHANGED")
            context.sendBroadcast(updateIntent)
        }
    }
}
