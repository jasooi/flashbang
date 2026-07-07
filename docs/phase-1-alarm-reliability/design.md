# Design — Phase 1: Alarm Reliability

**Status:** Draft, pending approval · **Requirements:** [requirements.md](requirements.md) · **Architecture:** [.claude/architecture.md](../../.claude/architecture.md)

## Design overview

A four-component pipeline, with ownership boundaries chosen so that killing any UI never silences a ringing alarm:

```
AlarmScheduler ──(setAlarmClock)──▶ AlarmReceiver ──(startForegroundService)──▶ AlarmRingingService ──(full-screen intent)──▶ ChallengeActivity
     ▲                                                                              │ owns: audio, TTS, vibration,
     └── BootReceiver (BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED) ───────────────┘        ramp, ringing state
```

The single most important invariant (FR-007/FR-009/NFR-003): **`AlarmRingingService` is the only component that starts or stops sound.** `ChallengeActivity` is a dumb view over the service's state; it can be killed, backgrounded, or never even shown (notification-permission denied, OEM quirk) and the alarm still rings until the service is explicitly resolved.

## Package structure

Single-module Compose app. Applies the Phase 0 scaffold; package names use `com.flashbang` as the working name (rename is a one-shot refactor later, PRD §11 "branding TBD").

```
app/src/main/java/com/flashbang/
  alarm/            AlarmScheduler.kt, AlarmReceiver.kt, BootReceiver.kt, NextFireTimeCalculator.kt
  alarm/ring/       AlarmRingingService.kt, RingingState.kt, AlarmNotifications.kt
  audio/            AlarmAudioEngine.kt, VolumeRamp.kt, FallbackTonePlayer.kt
  tts/              TtsEngine.kt, TtsVoiceChecker.kt
  data/             (Phase 0: Room entities/DAOs, DataStore SettingsRepository)
  ui/challenge/     ChallengeActivity.kt, ChallengeScreen.kt
  ui/permissions/   OverlayPermissionScreen.kt, NotificationPermissionPrompt.kt
  config/           AlarmConfig.kt
```

## Component design

### `config/AlarmConfig.kt` (FR-008, RD-005)

All tunables in one object — no magic numbers at call sites:

```kotlin
object AlarmConfig {
    val VOLUME_RAMP_DURATION: Duration = 20.seconds   // RD-005
    const val INITIAL_VOLUME_FRACTION = 0.05f
    val TTS_INIT_TIMEOUT: Duration = 3.seconds        // then fallback tone (FR-017)
    val TTS_REPEAT_GAP: Duration = 2.seconds          // pause between TTS loop reads
    const val RINGING_CHANNEL_ID = "alarm_ringing"
}
```

### `alarm/AlarmScheduler.kt` (FR-001, FR-002)

- `schedule(alarm: Alarm)`: computes the next fire instant via `NextFireTimeCalculator` (pure function: `Alarm.time` + `days_of_week` + "now" → next `ZonedDateTime`; fully unit-testable, this is where DST/midnight-wraparound bugs live so it gets the densest tests), then calls `AlarmManager.setAlarmClock(AlarmClockInfo(triggerMillis, showIntent), operation)`.
  - `operation` = `PendingIntent.getBroadcast` → `AlarmReceiver`, with `alarmId` extra, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, and `requestCode = alarmId` so per-alarm intents don't collide.
  - `showIntent` = the app's main activity (what the status-bar alarm icon opens).
- `cancel(alarm)`, `rescheduleAll()` (used by `BootReceiver` and after any alarm edit).
- One-shot semantics: `setAlarmClock` fires once; after each fire (and after snooze, Phase 3), the service asks the scheduler to arm the next occurrence. Repeating is derived from `days_of_week`, never from `setRepeating` (inexact by design on modern Android).

### `alarm/AlarmReceiver.kt` + `alarm/BootReceiver.kt` (FR-003, FR-003b, FR-004)

- `AlarmReceiver.onReceive`: extract `alarmId`, `ContextCompat.startForegroundService(...)` with the id. Nothing else — receivers get ~10s and the service owns everything after this.
- `BootReceiver`: registered for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`; calls `AlarmScheduler.rescheduleAll()` inside `goAsync()` + a coroutine (Room read off the main thread). Direct-boot (pre-first-unlock) explicitly not handled — documented MVP limitation in requirements.

### `alarm/ring/AlarmRingingService.kt` — the core (FR-004–FR-009, FR-017, NFR-003, NFR-006)

Foreground service, `android:foregroundServiceType="systemExempted"` (the Android 14+ type designated for `setAlarmClock` users; manifest also declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`).

Lifecycle on `onStartCommand(ACTION_RING, alarmId)`:
1. `startForeground()` **immediately** with the ringing notification (built by `AlarmNotifications`: high-priority, ongoing, category `CATEGORY_ALARM`, full-screen intent → `ChallengeActivity`, content tap → same). Doing this first is mandatory — Android kills services that delay `startForeground`.
2. Load `Alarm` → deck → current card (suspend, Room). Failure here still rings: fallback tone + a shell showing an error card. **No code path returns before sound starts** (NFR-003).
3. Start `AlarmAudioEngine`.
4. Publish `RingingState` (see below).

Resolution: `onStartCommand(ACTION_RESOLVE)` (sent by the dev-dismiss in Phase 1; by answer-correct/ladder-dismiss in Phase 3) → stop engine, cancel vibration, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`, and re-arm the next occurrence via `AlarmScheduler`. `stopWithTask=false`; `onTaskRemoved` does **not** stop the service (FR-009).

`RingingState` (in-memory only, per requirements): `data class RingingState(val alarmId: Long, val cardFront: String, val cardReading: String?)` exposed as a `StateFlow` on a companion/singleton holder so `ChallengeActivity` can render without binding ceremony. Phase 3 extends this with rung/attempt state — the field layout is the contract Phase 3 builds on.

### `audio/` (FR-006, FR-008, FR-008b, FR-017)

- `AlarmAudioEngine`: owns whichever source is active (TTS loop or fallback tone) plus vibration. All output uses `AudioAttributes(USAGE_ALARM)`. Started with `vibrationEnabled` read from DataStore at ring time.
- `VolumeRamp`: coroutine that interpolates player volume `INITIAL_VOLUME_FRACTION → 1.0` linearly over `VOLUME_RAMP_DURATION`. Applies to *our* players (`MediaPlayer.setVolume` / TTS `KEY_PARAM_VOLUME` per utterance) — never touches `AudioManager` stream volume (FR-008: don't overwrite the user's system setting).
- `FallbackTonePlayer`: looping `MediaPlayer` over a bundled `res/raw` tone. Also used if the deck/card load fails.
- Vibration: `Vibrator.vibrate(VibrationEffect.createWaveform(..., repeat))` with `USAGE_ALARM` attributes; cancelled only on resolve.

### `tts/` (FR-014–FR-017)

- `TtsEngine.startLoop(text)`: init platform `TextToSpeech`; on `onInit` success, speak `card.reading ?: card.front` (FR-016), re-queueing on `UtteranceProgressListener.onDone` after `TTS_REPEAT_GAP`. If init doesn't succeed within `TTS_INIT_TIMEOUT`, or errors at any point, the engine swaps to `FallbackTonePlayer` **without stopping the ramp or vibration** — the swap is invisible to everything else (FR-017).
- No network path: we call the platform engine with whatever on-device voice exists; there is no fetch/download call reachable from the ring path (FR-014). Voice *download* lives only in `TtsVoiceChecker` (below), invoked from deck-setup UI (FR-015).
- `TtsVoiceChecker.check(language)`: `isLanguageAvailable()` → `AVAILABLE`/`MISSING_DATA`/`NOT_SUPPORTED`; on missing, fires `ACTION_INSTALL_TTS_DATA` intent. Surfaced as a minimal dialog at deck/language setup (final onboarding placement is Phase 7).

### `ui/challenge/ChallengeActivity.kt` (FR-005, FR-013, FR-019, FR-020, RD-001)

- `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `excludeFromRecents`, single-instance launch mode.
- Back press consumed by an `OnBackPressedCallback` no-op while ringing (lock-out behavior without screen pinning, FR-013).
- Renders from the service's `RingingState` flow: card front, centered, clean/minimal Material 3 (RD-001). One button: **"Dismiss (dev)"** → sends `ACTION_RESOLVE` to the service. Marked with a `// PHASE1-DEV-ONLY: replaced by Phase 3 challenge flow` comment so Phase 3's removal is greppable (FR-020).
- If launched with no active `RingingState` (stale notification tap), it just finishes.

### Blocking tiers (FR-010–FR-012)

- **Tier 1 (default):** full-screen intent + ongoing notification (tap re-opens `ChallengeActivity`) + never-stopping audio. On API 33+, `POST_NOTIFICATIONS` is requested at alarm-creation (FR-010b) with a warning UI state when denied.
- **Tier 2 (opt-in overlay):** if `overlay_blocking_opted_in` && `Settings.canDrawOverlays()`: `ChallengeActivity` reports `onStop`-while-unresolved to the service, which re-launches it via `startActivity` (holding `SYSTEM_ALERT_WINDOW` exempts the app from background-activity-launch restrictions). Re-launch is debounced (1/sec) to avoid a pathological loop.
- **Tier 3 (denied/never asked):** nothing extra happens; audio + notification are the pressure (FR-012 — a normal state, not an error).
- `OverlayPermissionScreen`: placeholder copy lives in `strings.xml` under `overlay_permission_rationale` (single findable string per RD-002), with a `TODO(product): copy pending owner review` comment.

## Data flow: alarm fires at 06:45 on a locked phone

1. OS delivers the exact broadcast → `AlarmReceiver` → `startForegroundService`.
2. Service posts ringing notification + starts foreground (t < 100ms), begins card load and TTS init in parallel, starts vibration + ramp immediately with silence-free ordering: fallback tone starts at once **only if** TTS init misses its 3s timeout; otherwise first TTS utterance is the first sound. (Perceived-latency target NFR-005 is met by the vibration + screen-on happening instantly.)
3. Full-screen intent launches `ChallengeActivity` over the lock screen.
4. User taps "Dismiss (dev)" → `ACTION_RESOLVE` → audio/vibration stop, service stops, next occurrence armed.
5. Airplane mode changes nothing in steps 1–4 (FR-018): no network calls exist on this path.

## Error handling

| Failure | Behavior |
|---|---|
| TTS init timeout/error | Fallback tone, same ramp, shell unaffected (FR-017) |
| Card/deck query fails | Fallback tone + error text in shell — still rings, still resolvable |
| Notification permission denied | Full-screen intent + audio still work; creation UI warned (FR-010b) |
| User swipes app from recents | Service continues (`onTaskRemoved` no-op), notification re-entry |
| Activity crashes | Service unaffected; notification re-opens shell |
| Process killed mid-ring by OEM | Out of our control; mitigations = `systemExempted` type + battery-settings help page (NFR-002) |

## Security & permissions

Manifest: `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `POST_NOTIFICATIONS` (runtime, API 33+), `SYSTEM_ALERT_WINDOW` (runtime special, opt-in only). No `INTERNET` permission is needed by this phase at all (Firebase adds it in Phase 6 — good forcing function for FR-018 honesty in the meantime). Receivers are `exported="false"` except where the system broadcast requires the intent-filter (boot/time — still not app-exported). All `PendingIntent`s are `FLAG_IMMUTABLE`.

## Testing strategy

- **Unit (JVM):** `NextFireTimeCalculator` (days-of-week wrap, DST spring-forward/fall-back, "now == alarm time" edge, disabled days), `VolumeRamp` interpolation (0s, 10s, 20s, past-end), TTS-fallback decision logic (timeout vs error vs success), notification-content assembly.
- **Instrumented (emulator):** receiver → service start wiring; service publishes `RingingState`; `ACTION_RESOLVE` tears everything down; activity renders state and finishes when stale.
- **Manual matrix (RD-003):** Android Studio emulator API 26 (min) and API 34 (FSI auto-grant, FGS types); physical Samsung Note 10 / Android 12 for lock-screen, audio-stream, DND, silent-mode, reboot-reschedule, airplane-mode (FR-018), and battery-management behavior. A written checklist for this matrix is a deliverable of the test task (T-113) so it's rerunnable each phase.

## Technical risks & mitigations

1. **OEM process kills** (NFR-002): `systemExempted` + `setAlarmClock` is the correct API posture; residual risk documented, help-page task deferred to a later phase, Xiaomi/Oppo untested gap carried in MVP_PROJECT_PLAN.
2. **Full-screen intent on API 34+** depends on Play classifying us as an alarm app; mitigated by `USE_FULL_SCREEN_INTENT` declaration + notification fallback path already being the Tier-1 design.
3. **TTS voice quality/availability varies by OEM engine**: mitigated by deck-setup-time voice check (FR-015) and the fallback tone; accepted that some devices will read Japanese imperfectly with a non-Google engine.
4. **Overlay re-launch loops** on some launchers: debounce + opt-in-only limits blast radius.

## Alternatives considered

- **`setExactAndAllowWhileIdle` instead of `setAlarmClock`:** rejected — weaker Doze guarantees, no status-bar icon, and Play's exact-alarm policy carve-out is cleanest for true alarm-clock usage (PRD §5.1 already decided this).
- **MediaPlayer-only (no TTS) for Phase 1, TTS in Phase 3:** rejected — TTS reliability (init timing, fallback) is exactly the kind of load-bearing risk this phase exists to retire early.
- **Bound-service + AIDL-style connection for activity↔service:** rejected in favor of a `StateFlow` holder + start-intents — less ceremony, no binder lifecycle bugs, sufficient for one-process app.
- **Persisting ringing state to Room for crash recovery:** rejected for MVP — if the process dies mid-ring the alarm is already lost (OS-level), and stale "ringing" rows create worse bugs than they fix.
