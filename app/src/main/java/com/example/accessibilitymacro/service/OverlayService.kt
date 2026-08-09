package com.example.accessibilitymacro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.accessibilitymacro.MainActivity
import com.example.accessibilitymacro.R
import com.example.accessibilitymacro.model.MacroPreset
import com.example.accessibilitymacro.util.CoordinatePickerBus
import com.example.accessibilitymacro.util.PickTarget
import com.example.accessibilitymacro.util.PickedPoint

class OverlayService : Service() {

    companion object {
        const val ACTION_SET_ACTIVE_PRESET = "com.example.accessibilitymacro.SET_ACTIVE_PRESET"
        const val ACTION_START_COORD_PICKER = "com.example.accessibilitymacro.START_COORD_PICKER"
        const val ACTION_STOP_COORD_PICKER = "com.example.accessibilitymacro.STOP_COORD_PICKER"
        const val EXTRA_PRESET = "extra_preset"
        const val EXTRA_PICK_TARGET = "extra_pick_target"

        private const val NOTIF_CHANNEL_ID = "macro_overlay_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var captureView: View? = null

    private var activePreset: MacroPreset? = null
    private var coordPickerActive = false
    private var currentPickTarget: PickTarget = PickTarget.START

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_ACTIVE_PRESET -> {
                val json = intent.getStringExtra(EXTRA_PRESET)
                activePreset = json?.let {
                    runCatching {
                        kotlinx.serialization.json.Json.decodeFromString<MacroPreset>(it)
                    }.getOrNull()
                }
            }
            ACTION_START_COORD_PICKER -> {
                val targetName = intent.getStringExtra(EXTRA_PICK_TARGET)
                val target = runCatching { PickTarget.valueOf(targetName ?: "START") }
                    .getOrDefault(PickTarget.START)
                startCoordinatePicker(target)
            }
            ACTION_STOP_COORD_PICKER -> stopCoordinatePicker()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeAllOverlayViews()
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        view.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = kotlin.math.abs(event.rawX - initialTouchX) +
                            kotlin.math.abs(event.rawY - initialTouchY)
                        if (moved < 15f) togglePanel()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(view, params)
    }

    private fun togglePanel() {
        if (panelView != null) {
            removePanel()
            return
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        panelView = view

        refreshPanelStatus()

        view.findViewById<Button>(R.id.buttonTrigger).setOnClickListener {
            triggerActivePreset()
        }
        view.findViewById<Button>(R.id.buttonPickStart).setOnClickListener {
            startCoordinatePicker(PickTarget.START)
        }
        view.findViewById<Button>(R.id.buttonPickEnd).setOnClickListener {
            startCoordinatePicker(PickTarget.END)
        }
        view.findViewById<Button>(R.id.buttonClose).setOnClickListener {
            removePanel()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }

        windowManager.addView(view, params)
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
    }

    private fun triggerActivePreset() {
        val service = MacroAccessibilityService.instance
        val preset = activePreset
        if (service == null) {
            updatePanelStatus("Cannot trigger — accessibility service not connected")
            return
        }
        if (preset == null) {
            updatePanelStatus("No active preset selected")
            return
        }
        service.executePreset(preset) { success ->
            updatePanelStatus(if (success) "Macro completed" else "Macro stopped/cancelled")
        }
    }

    private fun refreshPanelStatus() {
        val text = if (MacroAccessibilityService.isServiceEnabled()) {
            "Accessibility service: connected"
        } else {
            "Accessibility service: NOT enabled — open the app to enable it"
        }
        updatePanelStatus(text)
    }

    private fun updatePanelStatus(text: String) {
        panelView?.findViewById<TextView>(R.id.textStatus)?.text = text
    }

    private fun startCoordinatePicker(target: PickTarget) {
        currentPickTarget = target
        if (coordPickerActive) return
        coordPickerActive = true

        updatePanelStatus("Tap anywhere on screen to set the ${target.name} point…")

        val view = View(this)
        captureView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val point = PickedPoint(currentPickTarget, event.rawX, event.rawY)
                CoordinatePickerBus.emit(point)
                updatePanelStatus(
                    "${currentPickTarget.name} point captured: " +
                        "(${event.rawX.toInt()}, ${event.rawY.toInt()})"
                )
                stopCoordinatePicker()
            }
            true
        }

        windowManager.addView(view, params)
    }

    private fun stopCoordinatePicker() {
        coordPickerActive = false
        captureView?.let { runCatching { windowManager.removeView(it) } }
        captureView = null
    }

    private fun removeAllOverlayViews() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        removePanel()
        stopCoordinatePicker()
        bubbleView = null
    }
}
