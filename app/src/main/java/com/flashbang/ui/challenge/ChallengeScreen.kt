package com.flashbang.ui.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flashbang.R
import com.flashbang.alarm.ring.RingingState

/** Clean, minimal shell (RD-001). Phase 3 replaces the dismiss button with the challenge flow. */
@Composable
fun ChallengeScreen(state: RingingState, onDevDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.cardFront,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            // PHASE1-DEV-ONLY: replaced by the Phase 3 challenge flow (FR-020).
            Button(onClick = onDevDismiss) {
                Text(stringResource(R.string.challenge_dev_dismiss))
            }
        }
    }
}
