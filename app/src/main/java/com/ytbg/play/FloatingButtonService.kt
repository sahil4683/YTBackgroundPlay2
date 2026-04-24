package com.ytbg.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var blackOverlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        const val CHANNEL_ID = "yt_bg_channel"
        const val NOTIFICATION_ID = 101
        const val PREF_METHOD = "lock_method"
        const val METHOD_A = "A"
        const val METHOD_B = "B"
        const val METHOD_C = "C"

        var isRunning = false
        var isYouTubeActive = false
            set(value) {
                field = value
                visibilityCallback?.invoke(value)
            }
        var visibilityCallback: ((Boolean) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        prefs = getSharedPreferences("ytbg_prefs", Context.MODE_PRIVATE)
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingButton()
    }

    private fun getCurrentMethod() = prefs.getString(PREF_METHOD, METHOD_A) ?: METHOD_A

    // METHOD A: Simulate Home press first, then lock after 800ms delay
    // YouTube thinks user went home normally — not locked
    private fun methodA_HomeThenLock() {
        YouTubeDetectorService.instance?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        )
        handler.postDelayed({
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
        }, 800)
    }

    // METHOD B: Black fullscreen overlay — YouTube never sees a lock event
    // Tap black screen again to dismiss and return
    private fun methodB_FakeScreenOff() {
        if (blackOverlayView != null) {
            methodB_RemoveOverlay()
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "YTBg::FakeOff"
        )
        wakeLock?.acquire(30 * 60 * 1000L)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        blackOverlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { methodB_RemoveOverlay() }
        }

        windowManager.addView(blackOverlayView, overlayParams)
        floatingView?.findViewById<TextView>(R.id.tvLabel)?.text = "Tap to wake"
    }

    private fun methodB_RemoveOverlay() {
        blackOverlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        blackOverlayView = null
        wakeLock?.release()
        wakeLock = null
        floatingView?.findViewById<TextView>(R.id.tvLabel)?.let { updateButtonLabel(it) }
    }

    // METHOD C: Request audio focus (holds audio session alive) then lock
    private fun methodC_AudioFocusThenLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        handler.postDelayed({
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
            handler.postDelayed({
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            }, 3000)
        }, 300)
    }

    private fun handleLockButtonTap() {
        when (getCurrentMethod()) {
            METHOD_A -> methodA_HomeThenLock()
            METHOD_B -> methodB_FakeScreenOff()
            METHOD_C -> methodC_AudioFocusThenLock()
        }
    }

    private fun showFloatingButton() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 300
        }

        val label = floatingView!!.findViewById<TextView>(R.id.tvLabel)
        floatingView!!.visibility = View.GONE

        visibilityCallback = { ytActive ->
            floatingView?.post {
                floatingView?.visibility = if (ytActive) View.VISIBLE else View.GONE
                if (!ytActive) methodB_RemoveOverlay()
            }
        }

        updateButtonLabel(label)

        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        var moved = false

        floatingView!!.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = initialX + dx; params.y = initialY + dy
                    windowManager.updateViewLayout(floatingView, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) { handleLockButtonTap(); updateButtonLabel(label) }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun updateButtonLabel(label: TextView) {
        label.text = when (getCurrentMethod()) {
            METHOD_A -> "🏠 Home+Lock"
            METHOD_B -> "⚫ Fake Off"
            METHOD_C -> "🎵 Audio+Lock"
            else -> "Lock & Play"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "YT Background Play", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val subtitle = when (getCurrentMethod()) {
            METHOD_A -> "Mode: Home + Lock"
            METHOD_B -> "Mode: Fake Screen Off"
            METHOD_C -> "Mode: Audio Focus + Lock"
            else -> "Active"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YT Background Play")
            .setContentText("$subtitle • Open YouTube to use")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        floatingView?.findViewById<TextView>(R.id.tvLabel)?.let { updateButtonLabel(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        visibilityCallback = null
        methodB_RemoveOverlay()
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        floatingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatingView = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
