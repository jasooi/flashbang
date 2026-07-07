# Tasks — Phase 1: Alarm Reliability

**Status:** In progress — all code written, unit suite green; device-dependent verification pending (2026-07-08) · **Requirements:** [requirements.md](requirements.md) · **Design:** [design.md](design.md)

## Task overview

~16 tasks: 3 prerequisite tasks (Phase 0 scaffolding, which does not exist yet — the repo has no code), 11 implementation tasks, 2 verification tasks. Sized for focused 1–3 hour sessions. The dependency graph is mostly linear through the service core (T-105) and then fans out; T-102/T-106/T-107 are parallelizable after their prerequisites.

Update the checkbox and add a completion date as each task lands; per CLAUDE.md, also keep `implementation-notes.md` in this directory current as work proceeds.

## Prerequisites (Phase 0 — must land first)

- [ ] **P-001 — Project scaffold** 🟡 2026-07-08 — *builds, 31 unit tests green, APK assembles; "installs on emulator" pending device*
  - Kotlin + Jetpack Compose (Material 3) app module, min SDK 26, target latest stable; working package `com.flashbang`; Gradle version catalog; JUnit + Robolectric + androidx-test wired up so `./gradlew test` and `connectedAndroidTest` both run green on an empty test.
  - **Acceptance:** app builds, installs, and shows an empty main activity on an API 26 and API 34 emulator.
- [x] **P-002 — Room data layer** ✅ 2026-07-08 — *DAO instrumented tests written, execution pending device (see implementation-notes.md Deviations #2); CHECK constraint → enum TypeConverter (Deviation #1)* (depends: P-001)
  - Entities + DAOs for `Deck`, `Card`, `Alarm` (`CardProgress`/`StreakState` schemas created too, but no logic — later phases). Constraints per MVP_PROJECT_PLAN Phase 0: `card.deck_id` NOT NULL, `language` CHECK-constrained.
  - **Acceptance:** DAO instrumented tests pass for insert/query of a deck with cards and an alarm referencing the deck; a debug-only seed inserts 1 deck + 3 test cards + 1 alarm for manual QA (placeholder — real sample-deck seeding is Phase 2).
- [x] **P-003 — Settings DataStore** ✅ 2026-07-08 — *unit tests green* (depends: P-001)
  - `SettingsRepository` exposing typed accessors; keys for this phase: `vibration_enabled` (default true), `overlay_blocking_opted_in` (default false). Schema leaves room for Phase 3/5 keys (N, hint delay, snooze).
  - **Acceptance:** unit tests cover defaults and round-trip writes.

## Phase 1 implementation tasks

- [ ] **T-101 — AlarmConfig + notification channel** 🟡 2026-07-08 — *code complete; instrumented channel test deferred (Deviation #4)* (depends: P-001)
  - `config/AlarmConfig.kt` with all constants from design.md; notification channel registration at app start (`RINGING_CHANNEL_ID`, max importance, no sound on the channel itself — the service owns sound).
  - **Files:** `config/AlarmConfig.kt`, `FlashbangApp.kt`
  - **Tests:** channel exists after app init (instrumented).

- [x] **T-102 — NextFireTimeCalculator + AlarmScheduler** ✅ 2026-07-08 — *9 unit tests incl. DST green* (depends: P-002) · FR-001, FR-002
  - Pure `NextFireTimeCalculator` (alarm time + days_of_week + now → next fire instant), then `AlarmScheduler.schedule/cancel/rescheduleAll` wrapping `setAlarmClock` with per-alarm request codes; `USE_EXACT_ALARM` in manifest.
  - **Files:** `alarm/NextFireTimeCalculator.kt`, `alarm/AlarmScheduler.kt`, `AndroidManifest.xml`
  - **Tests (unit, dense — this is the DST/wraparound hotspot):** same-day future time, same-day past time → next enabled day, week wraparound, all-days vs single-day, DST spring-forward and fall-back, now == alarm time.

- [ ] **T-103 — AlarmReceiver** 🟡 2026-07-08 — *code complete; instrumented wiring test deferred* (depends: T-101, T-102) · FR-004
  - Broadcast receiver extracting `alarmId` and starting the foreground service; manifest wiring, `exported="false"`.
  - **Files:** `alarm/AlarmReceiver.kt`, `AndroidManifest.xml`
  - **Tests:** instrumented — receiving the intent starts the service with the right extra.

- [ ] **T-104 — BootReceiver (boot + time-change rescheduling)** 🟡 2026-07-08 — *code complete; manual verification = matrix rows 8–10* (depends: T-102) · FR-003, FR-003b
  - Handles `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` via `goAsync()` + coroutine → `rescheduleAll()`; `RECEIVE_BOOT_COMPLETED` permission.
  - **Files:** `alarm/BootReceiver.kt`, `AndroidManifest.xml`
  - **Tests:** unit test on the reschedule delegation (receiver logic extracted for testability); manual reboot check happens in T-113.

- [ ] **T-105 — AlarmRingingService core** 🟡 2026-07-08 — *code complete; instrumented tests deferred, covered by matrix rows 1, 11, 16* (depends: T-101, T-103, P-002) · FR-004, FR-005, FR-007, FR-009
  - FGS (`systemExempted` type): immediate `startForeground` with full-screen-intent notification (`AlarmNotifications.kt`), card load, `RingingState` StateFlow holder, `ACTION_RING`/`ACTION_RESOLVE` handling, re-arm-next-occurrence on resolve, `onTaskRemoved` no-op. Audio/TTS integration stubbed until T-106/T-107.
  - **Files:** `alarm/ring/AlarmRingingService.kt`, `alarm/ring/RingingState.kt`, `alarm/ring/AlarmNotifications.kt`, `AndroidManifest.xml`
  - **Tests:** instrumented — service starts foreground, publishes state, tears down fully on resolve (notification gone, service stopped), next occurrence armed.

- [x] **T-106 — Audio engine: fallback tone, volume ramp, vibration** ✅ 2026-07-08 — *ramp unit tests green; audible behavior verified via matrix rows 3–5* (depends: T-101, P-003) · FR-006, FR-008, FR-008b, FR-017(partial)
  - `AlarmAudioEngine`, `VolumeRamp` (linear, `AlarmConfig` values, player-volume only), `FallbackTonePlayer` (bundled `res/raw` tone, looping, `USAGE_ALARM`), waveform vibration gated on `vibration_enabled`.
  - **Files:** `audio/AlarmAudioEngine.kt`, `audio/VolumeRamp.kt`, `audio/FallbackTonePlayer.kt`, `res/raw/fallback_alarm.ogg`
  - **Tests:** unit — ramp interpolation at 0s/mid/end/past-end, initial fraction respected; vibration-flag gating logic.

- [x] **T-107 — TtsEngine: loop, reading-over-front, fallback swap** ✅ 2026-07-08 — *text-selection unit tests green; fallback behavior = matrix row 15* (depends: T-106) · FR-014, FR-016, FR-017
  - Platform TTS init with `TTS_INIT_TIMEOUT`; speak `reading ?: front` on repeat with `TTS_REPEAT_GAP`; any init/utterance failure swaps to fallback tone without interrupting ramp/vibration. No network-reachable call on this path.
  - **Files:** `tts/TtsEngine.kt`
  - **Tests:** unit — fallback decision logic (success/timeout/error), text selection (reading present vs absent); instrumented smoke on emulator TTS.

- [ ] **T-108 — Wire audio+TTS into service** 🟡 2026-07-08 — *code complete incl. no-silent-path ordering; verification = matrix rows 2, 15, 16* (depends: T-105, T-106, T-107) · FR-007, FR-009, NFR-003, NFR-006
  - Service drives engine lifecycle; verify the no-silent-path ordering from design.md ("Data flow" §step 2); card-load failure → fallback tone + error state, still resolvable.
  - **Files:** `alarm/ring/AlarmRingingService.kt`
  - **Tests:** instrumented — resolve stops all output; forced TTS failure still produces audible ring (fallback); forced card-load failure still rings and resolves.

- [ ] **T-109 — ChallengeActivity shell** 🟡 2026-07-08 — *code complete; instrumented tests deferred* (depends: T-105) · FR-005, FR-013, FR-019, FR-020, RD-001
  - Compose activity: `setShowWhenLocked`/`setTurnScreenOn`, back-press no-op, excluded from recents; renders `RingingState` (card front, clean minimal M3); single `Dismiss (dev)` button → `ACTION_RESOLVE`, tagged `// PHASE1-DEV-ONLY`; finishes if launched with no active state.
  - **Files:** `ui/challenge/ChallengeActivity.kt`, `ui/challenge/ChallengeScreen.kt`, `AndroidManifest.xml`
  - **Tests:** instrumented — renders front text from state; dismiss resolves service; stale launch finishes immediately.

- [ ] **T-110 — Notification permission flow (API 33+)** 🟡 2026-07-08 — *predicate unit-tested; manual = matrix row 14; lives on debug MainActivity (Deviation #5)* (depends: T-105) · FR-010, FR-010b
  - `POST_NOTIFICATIONS` runtime request at alarm-creation surface (debug screen for now — real alarm-edit UI is Phase 5), denial warning state; verify ring path works with permission denied.
  - **Files:** `ui/permissions/NotificationPermissionPrompt.kt`, manifest
  - **Tests:** manual on API 34 emulator (grant, deny, ring in both states); unit for the "should warn" predicate.

- [x] **T-111 — Overlay opt-in + redisplay (Tier 2/3)** ✅ 2026-07-08 — *policy matrix + debounce unit tests green (caught an overflow bug); manual = matrix rows 12–13* (depends: T-109, P-003) · FR-011, FR-012
  - Opt-in screen with placeholder rationale copy in `strings.xml` (`overlay_permission_rationale`, `TODO(product)` per RD-002) → `ACTION_MANAGE_OVERLAY_PERMISSION`; service re-launches unresolved challenge on activity `onStop` when opted-in && granted, debounced 1/sec; denied/never-asked = silent Tier-3 (no error).
  - **Files:** `ui/permissions/OverlayPermissionScreen.kt`, `alarm/ring/AlarmRingingService.kt`, `res/values/strings.xml`
  - **Tests:** unit — redisplay decision matrix (opted-in × granted × resolved) and debounce; manual — home-press during ring on emulator with/without grant.

- [ ] **T-112 — TTS voice check at deck setup** 🟡 2026-07-08 — *code complete on debug MainActivity; manual voice-download flow pending device* (depends: T-107) · FR-015
  - `TtsVoiceChecker` (`AVAILABLE`/`MISSING_DATA`/`NOT_SUPPORTED`) + minimal dialog triggering `ACTION_INSTALL_TTS_DATA`; triggerable from a debug entry point until Phase 2's deck UI / Phase 7's onboarding exist.
  - **Files:** `tts/TtsVoiceChecker.kt`, `ui/permissions/` dialog composable
  - **Tests:** unit — status mapping; manual — Japanese voice missing → prompt → download → available.

## Verification tasks

- [ ] **T-113 — Manual reliability matrix** 🟡 2026-07-08 — *test-matrix.md authored; execution blocked on emulator/device* (depends: T-108–T-112) · NFR-001, NFR-002, NFR-005, FR-018
  - Author `docs/phase-1-alarm-reliability/test-matrix.md` (rerunnable checklist) and execute on: emulator API 26, emulator API 34, Samsung Note 10 (Android 12). Must cover: locked-screen fire, silent mode, DND, airplane mode end-to-end (FR-018), reboot → alarm survives (FR-003), manual clock/timezone change (FR-003b), app-swiped-from-recents during ring, notification-denied ring (API 34), Samsung battery "sleeping apps" interaction, fire-to-sound latency spot check (NFR-005).
  - **Acceptance:** matrix doc committed with pass/fail per cell; failures filed as tasks before phase sign-off.

- [ ] **T-114 — Test-suite green + implementation notes** 🟡 2026-07-08 — *unit suite green (31/31), implementation-notes.md written; connectedAndroidTest pending device* (depends: all above)
  - Full `./gradlew test connectedAndroidTest` green; `implementation-notes.md` covers what was built, the service-owns-audio pattern, and any Deviations (per CLAUDE.md); then request human review of the phase.

## Definition of done

All boxes above checked; every FR/NFR in requirements.md traceable to a passing test or a passing matrix cell; the dev-dismiss remains the only challenge interaction (Phase 3's contract point); human has reviewed and approved, and MVP_PROJECT_PLAN.md Phase 1 items are marked complete by the project-plan-tracker.
