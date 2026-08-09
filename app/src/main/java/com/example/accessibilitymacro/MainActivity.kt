package com.example.accessibilitymacro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.accessibilitymacro.data.MacroPresetRepository
import com.example.accessibilitymacro.model.MacroPreset
import com.example.accessibilitymacro.service.MacroAccessibilityService
import com.example.accessibilitymacro.service.OverlayService
import com.example.accessibilitymacro.ui.PresetFormViewModel
import com.example.accessibilitymacro.util.PickTarget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: MacroPresetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MacroPresetRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val formViewModel: PresetFormViewModel = viewModel()
                    DashboardScreen(repository = repository, formViewModel = formViewModel)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(repository: MacroPresetRepository, formViewModel: PresetFormViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by repository.presetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var overlayGranted by remember { mutableStateOf(hasOverlayPermission(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = hasOverlayPermission(context)
    }

    fun launchPicker(target: PickTarget) {
        if (!overlayGranted) {
            formViewModel.setPickStatus("Grant overlay permission first")
            return
        }
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START_COORD_PICKER
            putExtra(OverlayService.EXTRA_PICK_TARGET, target.name)
        }
        context.startForegroundService(intent)
        formViewModel.setPickStatus("Switch to the target app/screen and tap the ${target.name.lowercase()} point…")
        (context as? Activity)?.moveTaskToBack(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Accessibility Macro", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        PermissionRow(
            label = "Overlay permission (SYSTEM_ALERT_WINDOW)",
            granted = overlayGranted,
            onRequest = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }
        )

        Spacer(Modifier.height(8.dp))

        PermissionRow(
            label = "Accessibility service enabled",
            granted = accessibilityEnabled,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            overlayGranted = hasOverlayPermission(context)
        }) {
            Text("Refresh permission status")
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Macro Presets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(
                enabled = overlayGranted,
                onClick = {
                    val intent = Intent(context, OverlayService::class.java)
                    context.startForegroundService(intent)
                }
            ) {
                Text("Start Overlay")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!overlayGranted) {
            Text("Grant overlay permission to enable the floating control panel.", style = MaterialTheme.typography.bodySmall)
        }
        if (!accessibilityEnabled) {
            Text("Enable the accessibility service to allow gestures to be dispatched.", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        PresetCreator(
            formViewModel = formViewModel,
            onPickStart = { launchPicker(PickTarget.START) },
            onPickEnd = { launchPicker(PickTarget.END) },
            onSave = {
                val preset = formViewModel.buildPresetAndReset()
                scope.launch { repository.savePreset(preset) }
            }
        )

        Spacer(Modifier.height(16.dp))
        Text("Saved presets", style = MaterialTheme.typography.titleSmall)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(presets) { preset ->
                PresetRow(
                    preset = preset,
                    onActivate = {
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_SET_ACTIVE_PRESET
                            putExtra(
                                OverlayService.EXTRA_PRESET,
                                kotlinx.serialization.json.Json.encodeToString(
                                    MacroPreset.serializer(), preset
                                )
                            )
                        }
                        context.startService(intent)
                    },
                    onDelete = {
                        scope.launch { repository.deletePreset(preset.id) }
                    },
                    onRunNow = {
                        MacroAccessibilityService.instance?.executePreset(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = if (granted) "\u2705" else "\u26A0\uFE0F", modifier = Modifier.padding(end = 8.dp))
        Text(label, modifier = Modifier.weight(1f))
        if (!granted) {
            TextButton(onClick = onRequest) { Text("Fix") }
        }
    }
}

@Composable
private fun PresetCreator(
    formViewModel: PresetFormViewModel,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onSave: () -> Unit
) {
    val name by formViewModel.name.collectAsStateWithLifecycle()
    val startX by formViewModel.startX.collectAsStateWithLifecycle()
    val startY by formViewModel.startY.collectAsStateWithLifecycle()
    val endX by formViewModel.endX.collectAsStateWithLifecycle()
    val endY by formViewModel.endY.collectAsStateWithLifecycle()
    val durationMs by formViewModel.durationMs.collectAsStateWithLifecycle()
    val jitterEnabled by formViewModel.jitterEnabled.collectAsStateWithLifecycle()
    val pickStatus by formViewModel.pickStatus.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("New Preset", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = name,
                onValueChange = formViewModel::onNameChange,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text("Start point", style = MaterialTheme.typography.labelMedium)
            Row {
                OutlinedTextField(
                    value = startX,
                    onValueChange = formViewModel::onStartXChange,
                    label = { Text("X") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = startY,
                    onValueChange = formViewModel::onStartYChange,
                    label = { Text("Y") },
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onPickStart, modifier = Modifier.align(Alignment.End)) {
                Text("Pick Start on screen")
            }

            Spacer(Modifier.height(4.dp))
            Text("End point", style = MaterialTheme.typography.labelMedium)
            Row {
                OutlinedTextField(
                    value = endX,
                    onValueChange = formViewModel::onEndXChange,
                    label = { Text("X") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = endY,
                    onValueChange = formViewModel::onEndYChange,
                    label = { Text("Y") },
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onPickEnd, modifier = Modifier.align(Alignment.End)) {
                Text("Pick End on screen")
            }

            if (pickStatus != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    pickStatus!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = durationMs,
                onValueChange = formViewModel::onDurationChange,
                label = { Text("Duration (ms)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = jitterEnabled, onCheckedChange = formViewModel::onJitterEnabledChange)
                Text("Coordinate stability jitter (+/- few px)")
            }

            Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) {
                Text("Save Preset")
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: MacroPreset,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(preset.name, modifier = Modifier.weight(1f))
            TextButton(onClick = onRunNow) { Text("Run") }
            TextButton(onClick = onActivate) { Text("Set Active") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun hasOverlayPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedComponentName = "${context.packageName}/${MacroAccessibilityService::class.java.name}"
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServicesSetting)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expectedComponentName, ignoreCase = true)) return true
    }
    return false
}
