package com.ytbg.play

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class YouTubeDetectorService : AccessibilityService() {

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        var instance: YouTubeDetectorService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val isYouTube = packageName == YOUTUBE_PACKAGE

            FloatingButtonService.isYouTubeActive = isYouTube

            if (isYouTube && !FloatingButtonService.isRunning) {
                val serviceIntent = Intent(this, FloatingButtonService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    override fun onInterrupt() {
        instance = null
        FloatingButtonService.isYouTubeActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
