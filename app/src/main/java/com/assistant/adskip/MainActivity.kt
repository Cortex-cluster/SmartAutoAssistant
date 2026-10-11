package com.assistant.adskip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101
    private lateinit var tvActiveCount: TextView
    private lateinit var rvFeatures: RecyclerView
    private lateinit var featureAdapter: FeatureAdapter
    private val featureList = FeatureManager.getAllFeatures()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvActiveCount = findViewById(R.id.tvActiveCount)
        rvFeatures = findViewById(R.id.rvFeatures)

        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnNotification = findViewById<Button>(R.id.btnNotification)
        val btnMicPermission = findViewById<Button>(R.id.btnMicPermission)

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Turn ON 'Smart Auto Assistant'", Toast.LENGTH_LONG).show()
        }

        btnNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Allow Notification Access for 'Smart Auto Assistant'", Toast.LENGTH_LONG).show()
        }

        btnMicPermission.setOnClickListener {
            checkAndRequestPermissions()
        }

        setupRecyclerView()
        updateActiveCount()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateActiveCount()
    }

    private fun setupRecyclerView() {
        featureAdapter = FeatureAdapter(
            features = featureList,
            onToggleChanged = { _, _ ->
                updateActiveCount()
            },
            onInfoClicked = { feature ->
                showFeatureDetailsDialog(feature)
            }
        )
        rvFeatures.layoutManager = LinearLayoutManager(this)
        rvFeatures.adapter = featureAdapter
    }

    private fun updateActiveCount() {
        val activeCount = featureList.count {
            FeatureManager.isEnabled(this, it.id, it.defaultEnabled)
        }
        tvActiveCount.text = "$activeCount of ${featureList.size} features active • 100% Offline"
    }

    private fun showFeatureDetailsDialog(feature: FeatureItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_feature_details, null)

        val tvCategory = view.findViewById<TextView>(R.id.tvDetailCategory)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDetailDescription)
        val tvTrigger = view.findViewById<TextView>(R.id.tvDetailTrigger)
        val layoutVoiceCommands = view.findViewById<View>(R.id.layoutVoiceCommands)
        val tvVoiceCommands = view.findViewById<TextView>(R.id.tvDetailVoiceCommands)
        val btnClose = view.findViewById<Button>(R.id.btnDetailClose)

        tvCategory.text = feature.category
        tvTitle.text = feature.title
        tvDescription.text = feature.description
        tvTrigger.text = feature.triggerInfo

        if (feature.voiceCommands.isNotEmpty()) {
            layoutVoiceCommands.visibility = View.VISIBLE
            tvVoiceCommands.text = feature.voiceCommands.joinToString("\n") { "• $it" }
        } else {
            layoutVoiceCommands.visibility = View.GONE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun checkAndRequestPermissions() {
        val permissionList = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissionList.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}
