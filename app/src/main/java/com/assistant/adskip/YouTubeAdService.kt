package com.assistant.adskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class YouTubeAdService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private var isAdMuted = false
    private var previousVolume = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPolling = false

    // Auto-scroller for Shorts & Reels
    private var isAutoScrollEnabled = true
    private var lastScrollTime = 0L

    companion object {
        const val TAG = "YouTubeAdService"
        var instance: YouTubeAdService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "YouTubeAdService connected and ready.")
        startContinuousAdWatcher()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isPolling = false
        if (isAdMuted) {
            unmuteMedia()
        }
    }

    private fun startContinuousAdWatcher() {
        isPolling = true
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!isPolling) return
                try {
                    val root = rootInActiveWindow
                    if (root != null) {
                        scanAndHandleAds(root)
                        checkAndAutoScrollShorts(root)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Watcher error: ${e.message}")
                }
                mainHandler.postDelayed(this, 150)
            }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = event?.source ?: rootInActiveWindow ?: return
        scanAndHandleAds(root)
        checkAndAutoScrollShorts(root)
    }

    private fun scanAndHandleAds(rootNode: AccessibilityNodeInfo) {
        if (PaymentModeTileService.isPaymentModeActive(this)) {
            return
        }

        val autoSkipEnabled = FeatureManager.isEnabled(this, FeatureManager.KEY_YT_AUTO_SKIP)
        val adMuteEnabled = FeatureManager.isEnabled(this, FeatureManager.KEY_YT_AD_MUTE)

        val skipped = if (autoSkipEnabled) checkAndClickSkipAd(rootNode) else false
        if (skipped) {
            if (isAdMuted) {
                unmuteMedia()
            }
            return
        }

        if (adMuteEnabled) {
            val isVideoAdPlaying = isVideoAdInPlayer(rootNode)
            if (isVideoAdPlaying) {
                if (!isAdMuted) {
                    muteMedia()
                }
            } else if (isAdMuted) {
                unmuteMedia()
            }
        } else if (isAdMuted) {
            unmuteMedia()
        }
    }

    private fun checkAndClickSkipAd(rootNode: AccessibilityNodeInfo): Boolean {
        return findAndClickRecursive(rootNode)
    }

    private fun findAndClickRecursive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("skip_ad") || viewId.contains("ad_skip") || viewId.contains("modern_skip")) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Skipped ad by viewId: $viewId")
                return true
            }
            node.parent?.let { parent ->
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "Skipped ad via parent of: $viewId")
                    return true
                }
            }
        }

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val matchesText = text.equals("Skip", ignoreCase = true) ||
                          text.equals("Skip Ad", ignoreCase = true) ||
                          text.equals("Skip Ads", ignoreCase = true) ||
                          contentDesc.contains("Skip Ad", ignoreCase = true)

        if (matchesText) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Skipped ad by text: $text")
                return true
            }
            node.parent?.let { parent ->
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "Skipped ad via parent text: $text")
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickRecursive(child)) return true
        }

        return false
    }

    private fun isVideoAdInPlayer(rootNode: AccessibilityNodeInfo): Boolean {
        val adCountdownIds = listOf(
            "com.google.android.youtube:id/ad_progress_text",
            "com.google.android.youtube:id/ad_countdown_text",
            "com.google.android.youtube:id/ad_time_remaining"
        )
        for (id in adCountdownIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return true
        }

        val strictAdTexts = listOf("Ad 1 of", "Ad 2 of", "Video will play after ad")
        for (text in strictAdTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) return true
        }
        return false
    }

    /**
     * Hands-Free Shorts / Reels Auto-Scroller:
     * Detects when in YouTube Shorts or Instagram Reels player,
     * and auto-swipes to the next video hands-free.
     */
    private fun checkAndAutoScrollShorts(rootNode: AccessibilityNodeInfo) {
        if (!FeatureManager.isEnabled(this, FeatureManager.KEY_SHORTS_SCROLL)) return

        val packageName = rootNode.packageName?.toString()?.lowercase() ?: ""

        val isYouTube = packageName.contains("youtube")
        val isInstagram = packageName.contains("instagram")

        if (!isYouTube && !isInstagram) return

        var isShortsOrReelsActive = false

        if (isYouTube) {
            val shortsIds = listOf(
                "com.google.android.youtube:id/reel_player_page_tree",
                "com.google.android.youtube:id/shorts_container",
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_watch_fragment_root"
            )
            for (id in shortsIds) {
                if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                    isShortsOrReelsActive = true
                    break
                }
            }
            if (!isShortsOrReelsActive) {
                // Secondary check: YouTube shorts action bar buttons
                val remixButton = rootNode.findAccessibilityNodeInfosByText("Remix")
                val soundButton = rootNode.findAccessibilityNodeInfosByText("Use this sound")
                if (remixButton.isNotEmpty() || soundButton.isNotEmpty()) {
                    isShortsOrReelsActive = true
                }
            }
        } else if (isInstagram) {
            val reelIds = listOf(
                "com.instagram.android:id/reel_viewer_container",
                "com.instagram.android:id/clips_video_container",
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/viewer_media_view_root"
            )
            for (id in reelIds) {
                if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) {
                    isShortsOrReelsActive = true
                    break
                }
            }
            if (!isShortsOrReelsActive) {
                val audioTrack = rootNode.findAccessibilityNodeInfosByText("Audio")
                val reelsLabel = rootNode.findAccessibilityNodeInfosByText("Reels")
                if (audioTrack.isNotEmpty() || reelsLabel.isNotEmpty()) {
                    isShortsOrReelsActive = true
                }
            }
        }

        if (isShortsOrReelsActive) {
            val now = System.currentTimeMillis()
            // Auto-scroll interval: 15 seconds allows viewing video then auto-advances hands-free
            if (now - lastScrollTime > 15000L) {
                lastScrollTime = now
                swipeUpNextVideo()
            }
        }
    }

    private fun swipeUpNextVideo() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // Center X with safe vertical swipe distance (from 75% down to 25% up)
        val centerX = (width / 2).toFloat()
        val startY = height * 0.75f
        val endY = height * 0.25f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Auto-scroll swipe gesture completed successfully.")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Auto-scroll swipe gesture cancelled by OS.")
            }
        }, null)
    }

    /**
     * "Skip Channel" / "Don't Recommend":
     * Clicks the 3 dots menu and taps "Don't recommend channel".
     */
    fun skipChannel() {
        if (!FeatureManager.isEnabled(this, FeatureManager.KEY_SKIP_CHANNEL)) return
        val rootNode = rootInActiveWindow ?: return
        val menuNodes = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/menu_button")
        if (menuNodes.isNotEmpty() && menuNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            mainHandler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: return@postDelayed
                val dontRecommend = updatedRoot.findAccessibilityNodeInfosByText("Don't recommend channel")
                if (dontRecommend.isNotEmpty()) {
                    dontRecommend[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked 'Don't recommend channel' successfully.")
                }
            }, 500)
        }
    }

    private fun muteMedia() {
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > 0) previousVolume = currentVol
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            isAdMuted = true
        } catch (e: Exception) {
            Log.e(TAG, "Mute error: ${e.message}")
        }
    }

    private fun unmuteMedia() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            if (previousVolume > 0) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            isAdMuted = false
        } catch (e: Exception) {
            Log.e(TAG, "Unmute error: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "YouTubeAdService interrupted.")
    }
}
