package com.example.accessibilitymacro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.accessibilitymacro.model.GestureStep
import com.example.accessibilitymacro.model.MacroPreset
import kotlin.random.Random

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroAccessibilityService"

        @Volatile
        var instance: MacroAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "MacroAccessibilityService connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */ }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted by the system")
    }

    fun executeMacroGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long,
        delay: Long = 0L,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val strokeBuilder = GestureDescription.StrokeDescription(
            path,
            0L,
            duration.coerceAtLeast(1L)
        )

        val gestureBuilder = GestureDescription.Builder().addStroke(strokeBuilder)

        val dispatch = {
            val dispatched = dispatchGesture(
                gestureBuilder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        onComplete(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "Gesture cancelled")
                        onComplete(false)
                    }
                },
                null
            )
            if (!dispatched) {
                Log.w(TAG, "dispatchGesture() returned false — gesture rejected by system")
                onComplete(false)
            }
        }

        if (delay > 0L) {
            android.os.Handler(mainLooper).postDelayed(dispatch, delay)
        } else {
            dispatch()
        }
    }

    fun executeMacroChain(
        steps: List<GestureStep>,
        jitterEnabled: Boolean = false,
        jitterPixels: Int = 3,
        stepIndex: Int = 0,
        onChainComplete: (Boolean) -> Unit = {}
    ) {
        if (stepIndex >= steps.size) {
            onChainComplete(true)
            return
        }

        val step = steps[stepIndex]
        val (sx, sy, ex, ey) = if (jitterEnabled) {
            jitterCoordinates(step, jitterPixels)
        } else {
            listOf(step.startX, step.startY, step.endX, step.endY)
        }

        executeMacroGesture(
            startX = sx,
            startY = sy,
            endX = ex,
            endY = ey,
            duration = step.durationMs,
            delay = step.delayBeforeMs
        ) { success ->
            if (!success) {
                Log.w(TAG, "Chain stopped early at step $stepIndex (gesture failed/cancelled)")
                onChainComplete(false)
                return@executeMacroGesture
            }
            executeMacroChain(steps, jitterEnabled, jitterPixels, stepIndex + 1, onChainComplete)
        }
    }

    fun executePreset(preset: MacroPreset, onFinished: (Boolean) -> Unit = {}) {
        fun runLoop(remaining: Int) {
            if (remaining <= 0) {
                onFinished(true)
                return
            }
            executeMacroChain(
                steps = preset.steps,
                jitterEnabled = preset.jitterEnabled,
                jitterPixels = preset.jitterPixels
            ) { success ->
                if (!success) {
                    onFinished(false)
                } else {
                    runLoop(remaining - 1)
                }
            }
        }
        runLoop(preset.loopCount.coerceAtLeast(1))
    }

    private fun jitterCoordinates(step: GestureStep, maxPixels: Int): List<Float> {
        fun jitter(v: Float): Float {
            val offset = Random.nextInt(-maxPixels, maxPixels + 1)
            return (v + offset).coerceAtLeast(0f)
        }
        return listOf(jitter(step.startX), jitter(step.startY), jitter(step.endX), jitter(step.endY))
    }
}

private operator fun List<Float>.component1() = this[0]
private operator fun List<Float>.component2() = this[1]
private operator fun List<Float>.component3() = this[2]
private operator fun List<Float>.component4() = this[3]
