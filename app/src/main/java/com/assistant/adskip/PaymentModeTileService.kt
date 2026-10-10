package com.assistant.adskip

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class PaymentModeTileService : TileService() {

    companion object {
        const val PREFS_NAME = "SmartAssistantPrefs"
        const val KEY_PAYMENT_MODE = "is_payment_mode_active"

        fun isPaymentModeActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PAYMENT_MODE, false)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = prefs.getBoolean(KEY_PAYMENT_MODE, false)
        val newState = !currentState

        prefs.edit().putBoolean(KEY_PAYMENT_MODE, newState).apply()
        updateTileState()
        Log.d("PaymentTile", "Payment Mode toggled: $newState")
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = isPaymentModeActive(this)

        if (isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Pay Mode: ON"
            tile.subtitle = "Safe for GPay/Bank"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_app_logo)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Pay Mode: OFF"
            tile.subtitle = "Assistant Active"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_app_logo)
        }
        tile.updateTile()
    }
}
