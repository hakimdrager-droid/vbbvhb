package com.example.accessibilitymacro.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibilitymacro.model.GestureStep
import com.example.accessibilitymacro.model.MacroPreset
import com.example.accessibilitymacro.util.CoordinatePickerBus
import com.example.accessibilitymacro.util.PickTarget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PresetFormViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val KEY_NAME = "form_name"
        const val KEY_START_X = "form_start_x"
        const val KEY_START_Y = "form_start_y"
        const val KEY_END_X = "form_end_x"
        const val KEY_END_Y = "form_end_y"
        const val KEY_DURATION_MS = "form_duration_ms"
        const val KEY_JITTER_ENABLED = "form_jitter_enabled"
        const val KEY_PICK_STATUS = "form_pick_status"
    }

    val name: StateFlow<String> = savedStateHandle.getStateFlow(KEY_NAME, "")
    val startX: StateFlow<String> = savedStateHandle.getStateFlow(KEY_START_X, "100")
    val startY: StateFlow<String> = savedStateHandle.getStateFlow(KEY_START_Y, "500")
    val endX: StateFlow<String> = savedStateHandle.getStateFlow(KEY_END_X, "100")
    val endY: StateFlow<String> = savedStateHandle.getStateFlow(KEY_END_Y, "500")
    val durationMs: StateFlow<String> = savedStateHandle.getStateFlow(KEY_DURATION_MS, "80")
    val jitterEnabled: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_JITTER_ENABLED, false)
    val pickStatus: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_PICK_STATUS, null)

    init {
        viewModelScope.launch {
            CoordinatePickerBus.pickedPoint.collect { point ->
                if (point == null) return@collect
                when (point.target) {
                    PickTarget.START -> {
                        savedStateHandle[KEY_START_X] = point.x.toInt().toString()
                        savedStateHandle[KEY_START_Y] = point.y.toInt().toString()
                    }
                    PickTarget.END -> {
                        savedStateHandle[KEY_END_X] = point.x.toInt().toString()
                        savedStateHandle[KEY_END_Y] = point.y.toInt().toString()
                    }
                }
                savedStateHandle[KEY_PICK_STATUS] =
                    "${point.target.name} set to (${point.x.toInt()}, ${point.y.toInt()})"
                CoordinatePickerBus.consume()
            }
        }
    }

    fun onNameChange(value: String) { savedStateHandle[KEY_NAME] = value }
    fun onStartXChange(value: String) { savedStateHandle[KEY_START_X] = value }
    fun onStartYChange(value: String) { savedStateHandle[KEY_START_Y] = value }
    fun onEndXChange(value: String) { savedStateHandle[KEY_END_X] = value }
    fun onEndYChange(value: String) { savedStateHandle[KEY_END_Y] = value }
    fun onDurationChange(value: String) { savedStateHandle[KEY_DURATION_MS] = value }
    fun onJitterEnabledChange(value: Boolean) { savedStateHandle[KEY_JITTER_ENABLED] = value }

    fun setPickStatus(text: String?) { savedStateHandle[KEY_PICK_STATUS] = text }

    fun buildPresetAndReset(): MacroPreset {
        val step = GestureStep(
            startX = startX.value.toFloatOrNull() ?: 0f,
            startY = startY.value.toFloatOrNull() ?: 0f,
            endX = endX.value.toFloatOrNull() ?: 0f,
            endY = endY.value.toFloatOrNull() ?: 0f,
            durationMs = durationMs.value.toLongOrNull() ?: 80L
        )
        val preset = MacroPreset(
            id = UUID.randomUUID().toString(),
            name = name.value.ifBlank { "Unnamed preset" },
            steps = listOf(step),
            jitterEnabled = jitterEnabled.value
        )
        savedStateHandle[KEY_NAME] = ""
        savedStateHandle[KEY_PICK_STATUS] = null
        return preset
    }
}
