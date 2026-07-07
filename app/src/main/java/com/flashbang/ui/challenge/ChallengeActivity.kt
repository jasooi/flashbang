package com.flashbang.ui.challenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashbang.alarm.ring.AlarmRingingService
import com.flashbang.alarm.ring.RingingStateHolder

/**
 * The challenge shell (FR-005, FR-013, FR-019/FR-020): a disposable view over the
 * service's RingingState. Killing or leaving this activity never silences the
 * alarm — the service owns all sound. Phase 3 replaces the shell contents with
 * the real answer/hint/escalation flow.
 */
class ChallengeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Lock-out behavior without screen pinning (FR-013): back does nothing while ringing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            MaterialTheme {
                val state by RingingStateHolder.state.collectAsStateWithLifecycle()
                val current = state
                if (current == null) {
                    // Stale launch (e.g. old notification tap after resolve): nothing to show.
                    finish()
                } else {
                    ChallengeScreen(
                        state = current,
                        onDevDismiss = { startService(AlarmRingingService.resolveIntent(this)) },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Tier-2 overlay redisplay decision belongs to the service (FR-011).
        if (RingingStateHolder.state.value != null) {
            startService(AlarmRingingService.challengeHiddenIntent(this))
        }
    }
}
