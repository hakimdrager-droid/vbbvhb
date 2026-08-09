package com.example.accessibilitymacro.model

import kotlinx.serialization.Serializable

@Serializable
data class GestureStep(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long = 80L,
    val delayBeforeMs: Long = 0L
)

@Serializable
data class MacroPreset(
    val id: String,
    val name: String,
    val steps: List<GestureStep>,
    val jitterEnabled: Boolean = false,
    val jitterPixels: Int = 3,
    val loopCount: Int = 1
)
