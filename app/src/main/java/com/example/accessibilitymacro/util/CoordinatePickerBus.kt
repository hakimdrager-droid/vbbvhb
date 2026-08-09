package com.example.accessibilitymacro.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PickTarget { START, END }

data class PickedPoint(val target: PickTarget, val x: Float, val y: Float)

object CoordinatePickerBus {
    private val _pickedPoint = MutableStateFlow<PickedPoint?>(null)
    val pickedPoint: StateFlow<PickedPoint?> = _pickedPoint.asStateFlow()

    fun emit(point: PickedPoint) {
        _pickedPoint.value = point
    }

    fun consume() {
        _pickedPoint.value = null
    }
}
