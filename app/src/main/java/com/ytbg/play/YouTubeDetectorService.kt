package com.ytbg.play

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class YouTubeDetectorService : AccessibilityService() {

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val isYouTube = packageName == YOUTUBE_PACKAGE

            // Update floating button visibility
            FloatingButtonService.isYouTubeActive = isYouTube

            // If YouTube just opened and service isn't running, start it
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
        FloatingButtonService.isYouTubeActive = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
