# Implementation Notes — Phase 1: Alarm Reliability

**Date:** 2026-07-08 · **Scope:** P-001–P-003, T-101–T-114 per [tasks.md](tasks.md)

## What was done

Built the Phase 0 scaffold (Kotlin 2.0.21 / Compose / Material 3, min SDK 26, target 35) plus the entire Phase 1 reliability layer, from scratch — the repo had no code before this. 31 JVM unit tests, all passing; debug APK assembles cleanly. Key pieces:

- **Data layer:** Room entities/DAOs for all five MVP tables (`Deck`, `Card`, `CardProgress`, `Alarm`, `StreakState` — the latter two are schema-only until Phases 3–5), `SettingsRepository` over DataStore (`vibration_enabled`, `overlay_blocking_opted_in`), debug-only seeder (1 JA deck, 3 cards, 1 disabled alarm).
- **Scheduling:** `NextFireTimeCalculator` (pure next-occurrence math incl. DST behavior, densest test coverage), `AlarmScheduler` (`setAlarmClock`, per-alarm request codes, one-shot + re-arm), `AlarmReceiver`, `BootReceiver` (boot + time/timezone change).
- **Ring path:** `AlarmRingingService` (foreground, `systemExempted` type) owning `AlarmAudioEngine` (TTS loop or fallback tone + linear 20s volume ramp + vibration) and publishing transient `RingingState` via a `StateFlow` holder.
- **UI:** `ChallengeActivity` shell (lock-screen flags, back-press no-op, dev-dismiss button tagged `PHASE1-DEV-ONLY`), debug `MainActivity` (test-alarm trigger, permission prompts, JA voice check), `OverlayRedisplayPolicy` for the opt-in blocking tier.
- **Assets:** generated `res/raw/fallback_alarm.wav` (two-tone beep, scripted with Python's stdlib `wave`), vector notification/launcher icons.

## Design patterns and concepts

- **Single-owner resource pattern (the load-bearing decision):** all sound/vibration is owned by the foreground service; the activity is a stateless projection of `RingingState`. This is essentially MVI at the process level — UI can die, be re-launched, or never appear, and the enforcement layer is unaffected (FR-007/FR-009). The `StateFlow` holder is the narrow contract Phase 3 will extend (rung/attempt state) without touching service internals.
- **Pure-core, impure-shell:** everything with tricky logic (`NextFireTimeCalculator`, `VolumeRamp`, `OverlayRedisplayPolicy`, `ttsTextFor`) is a pure function/class with no Android dependencies, exhaustively unit-tested on the JVM; the Android components are thin adapters around them. This is why 31 meaningful tests run in ~2s with no emulator.
- **Graceful-degradation ladder as data, not branches:** the three blocking tiers collapse into one testable predicate (`OverlayRedisplayPolicy.shouldRedisplay`) — denied permissions are a *normal* input combination, not an error path.
- **Fail-loud invariant (NFR-003):** every failure on the ring path (TTS init timeout, missing card, missing deck) converges on the same recovery — fallback tone + visible shell. `runCatching` + `fold` keeps the two outcomes explicit at the single decision point in the service.
- **Config-as-code:** every tunable (`VOLUME_RAMP_DURATION = 20.seconds` per RD-005, timeouts, debounce) lives in `AlarmConfig` — one place to tune, no magic numbers at call sites.

## Bug caught by tests

`OverlayRedisplayPolicy` initialized its last-relaunch timestamp to `Long.MIN_VALUE`; `nowMs - Long.MIN_VALUE` overflows negative, so the debounce wrongly suppressed *every* redisplay. Caught by the unit matrix on first run; fixed with a nullable sentinel. (Kept here as a reminder: subtraction-based debounce needs a "never" representation that can't overflow.)

## Deviations

1. **`language` CHECK constraint (MVP plan Phase 0):** Room cannot emit SQL `CHECK` constraints on generated tables. Conservative option taken: a `Language` enum + TypeConverter, so invalid values are unrepresentable from app code — but the knowledge-transfer memo's warning about raw-SQL writes bypassing app-level checks technically still stands. Revisit if we ever add a migration or raw write path to `deck`.
2. **Instrumented tests written but not executed:** the build machine has no emulator or device (`adb devices` empty). `DaoTest` and the manual matrix ([test-matrix.md](test-matrix.md)) are authored and ready; they must be run once Android Studio (emulator) is installed or the Note 10 is plugged in. Unit tests + APK assembly are the verification actually performed.
3. **Toolchain installed via Homebrew** (`openjdk@21`, `android-commandlinetools`, Gradle wrapper 8.11.1) because the machine had no Android toolchain. Android Studio is still needed for the emulator half of the RD-003 test matrix (note: Xcode cannot emulate Android). `local.properties` points at the Homebrew SDK; Android Studio will overwrite this with its own SDK path on first open — that's fine.
4. **T-101's instrumented channel test and T-105/T-108/T-109's instrumented service/activity tests were deferred** along with deviation 2 — the pure-logic halves of those tasks are unit-tested instead. The instrumented gaps are all captured by test-matrix.md rows.
5. **T-110/T-112 UI surfaces live on the debug MainActivity** rather than dedicated screens — the real homes (Settings, onboarding) are Phase 5/7 per requirements; building throwaway polished screens now would be waste.

## What Phase 3 needs to know

- Replace `ChallengeScreen`'s dev-dismiss (grep `PHASE1-DEV-ONLY`) with the answer flow; resolve via `AlarmRingingService.resolveIntent()`.
- Extend `RingingState` with rung/attempt fields; the holder pattern stays.
- `CardDao.firstCardOfDeck` is the placeholder card-selection point the Phase 4 sampler replaces.
