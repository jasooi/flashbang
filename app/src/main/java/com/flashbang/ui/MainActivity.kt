package com.flashbang.ui

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flashbang.R
import com.flashbang.alarm.AlarmScheduler
import com.flashbang.data.FlashbangDatabase
import com.flashbang.data.SettingsRepository
import com.flashbang.tts.TtsVoiceChecker
import com.flashbang.ui.permissions.Permissions
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * PHASE1-DEV-ONLY home screen: manual triggers for everything the reliability
 * layer needs verified (test alarm, permissions, overlay opt-in, voice check).
 * Phase 5 replaces this with the real alarm-list/settings UI.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, getString(R.string.notification_denied_warning), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    DebugHome()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DebugHome() {
        val scope = rememberCoroutineScope()
        val settings = remember { SettingsRepository(this) }
        var status by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                "Phase 1 dev screen — replaced by the real alarm UI in Phase 5",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = {
                scope.launch {
                    val alarm = FlashbangDatabase.get(this@MainActivity).alarmDao().enabled().firstOrNull()
                        ?: FlashbangDatabase.get(this@MainActivity).alarmDao().byId(1L)
                    if (alarm == null) {
                        status = "No seeded alarm found"
                        return@launch
                    }
                    val now = ZonedDateTime.now()
                    val inOneMinute = alarm.copy(
                        enabled = true,
                        timeMinutes = (now.hour * 60 + now.minute + 1) % 1440,
                        daysOfWeekMask = 0,
                    )
                    FlashbangDatabase.get(this@MainActivity).alarmDao().update(inOneMinute)
                    AlarmScheduler(this@MainActivity).schedule(inOneMinute)
                    status = "Alarm scheduled for 1 minute from now — lock the phone"
                }
            }) { Text("Schedule test alarm (+1 min)") }

            if (Permissions.notificationPermissionRequired()) {
                Button(onClick = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Request notification permission") }
            }

            Button(onClick = {
                scope.launch {
                    settings.setOverlayBlockingOptedIn(true)
                    if (!Permissions.overlayPermissionGranted(this@MainActivity)) {
                        startActivity(Permissions.overlaySettingsIntent(this@MainActivity))
                    } else {
                        status = "Overlay blocking already granted + opted in"
                    }
                }
            }) { Text(stringResource(R.string.overlay_opt_in_button)) }
            Text(
                stringResource(R.string.overlay_permission_rationale),
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = {
                TtsVoiceChecker(this@MainActivity).check(Locale.JAPANESE) { result ->
                    status = "Japanese TTS voice: $result"
                    if (result == TtsVoiceChecker.Status.MISSING_DATA) {
                        TtsVoiceChecker(this@MainActivity).launchVoiceDownload()
                    }
                }
            }) { Text("Check Japanese TTS voice") }

            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
