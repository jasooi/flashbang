# Requirements — Phase 1: Alarm Reliability

**Status:** Approved — open questions resolved by owner on 2026-07-08, senior review pass applied same day · **Maps to:** [MVP_PROJECT_PLAN.md](../../MVP_PROJECT_PLAN.md) Phase 1 · **PRD source:** [flashcard-alarm-prd.md](../../flashcard-alarm-prd.md) §5 (primary), §4.1, §10

## Feature overview

This is the load-bearing layer of the entire product: the mechanism that fires an alarm exactly on time, keeps it audible and unskippable until answered, and does all of this fully offline. The PRD is explicit that engineering effort in the first build cycle concentrates here, because every other feature (hint ladder, escalation ladder, scheduler, streaks) only matters if the alarm reliably rings and can be reasoned about as "always fires, always eventually dismissable."

Phase 1 delivers the scheduling, foreground-service, audio, screen-blocking, TTS-infrastructure, and offline-guarantee layers. It does **not** deliver the actual answer/hint/escalation UI logic (typed input, hint reveal, MCQ, dismiss-after-N-fails) — that is Phase 3. To make Phase 1 independently buildable and testable, it introduces a minimal **challenge shell**: a full-screen activity that the reliability layer launches and can prove is unskippable, containing a placeholder card display and a temporary developer-only dismiss affordance. Phase 3 replaces the shell's internals with the real challenge flow without changing anything in this phase's scheduling/service/audio/blocking contracts.

## User stories

(Full list in PRD §3; these are the subset this phase serves.)

- As a learner, I want the alarm to start quiet and only ramp up while showing me the card, so that I'm not jolted awake before I've even had a chance to try recalling the answer.
- As a learner, I want the alarm to always become dismissable eventually, so that I never feel trapped or unsafe first thing in the morning.
- As a learner, I want kanji cards to be read aloud with the correct pronunciation (not misread), so that the alarm prompt is actually intelligible in Japanese.
- As a learner, I want the app to work fully offline, so that a lack of signal or airplane mode never causes my alarm to fail or go silent.
- As a learner, I want to optionally let the app take over my screen if I try to wander off mid-challenge, so that I can choose stronger enforcement if I know I'm prone to disabling alarms half-asleep.

## Functional requirements

### Scheduling
- **FR-001**: The app must schedule every alarm using `AlarmManager.setAlarmClock()`, not `setExact`/`setExactAndAllowWhileIdle`, so alarms are exempt from Doze and surface the status-bar alarm icon.
- **FR-002**: The app must declare and use the `USE_EXACT_ALARM` permission.
- **FR-003**: A `BOOT_COMPLETED` broadcast receiver must re-register every enabled alarm after device restart, restoring the exact same fire times as before reboot.
- **FR-003b**: The app must also re-register all enabled alarms on `TIME_SET` and `TIMEZONE_CHANGED` broadcasts, since `AlarmManager` fire times are wall-clock-relative and become stale when the user (or carrier) changes the clock or crosses a timezone.
- **FR-004**: On alarm fire, the exact-alarm callback must start a foreground service before any UI is shown; the service owns audio and challenge state for the lifetime of the ringing alarm.
- **FR-005**: The foreground service must launch the challenge shell via a full-screen intent, with `setShowWhenLocked` and `setTurnScreenOn`, so it displays over the lock screen without user interaction.

### Audio
- **FR-006**: Alarm audio must be routed to the alarm stream (`AudioAttributes.USAGE_ALARM`) so silent mode and DND alarm exceptions apply.
- **FR-007**: Audio playback must be owned and controlled by the foreground service, not the challenge activity. If the user navigates away from or kills the challenge activity, audio must continue at full volume.
- **FR-008**: Volume must start low and escalate gradually to full volume over a ramp duration whose default is **20 seconds**. The duration must be a named configuration constant in code (no magic number inline at the call site) so it can be tuned without hunting through the audio engine. The ramp applies to the app's own player volume — the app must never modify the user's system alarm-stream volume setting.
- **FR-008b**: The alarm must vibrate while ringing, **on by default and user-toggleable** in Settings. Vibration follows the same lifecycle as audio: owned by the service, stops only on resolution (per FR-009), never as a side effect of activity lifecycle.
- **FR-009**: Audio must only stop when the service is explicitly told the alarm is resolved (answered or dismissed via the ladder's dismiss rung) — never as a side effect of activity lifecycle events (onPause, onStop, onDestroy, task removal).

### Screen blocking
- **FR-010**: By default (no extra permission), the app must show the full-screen intent over the lock screen, keep audio playing per FR-007, and post a high-priority ongoing notification that re-opens the challenge shell if tapped or if the user navigates elsewhere.
- **FR-010b**: On Android 13+ (API 33), the app must request `POST_NOTIFICATIONS` at alarm-creation time (not at fire time). If the user denies it, the alarm must still ring and show the full-screen challenge (audio and full-screen intent are the enforcement layer); the degraded state loses only the persistent re-entry notification, and the alarm-creation UI should warn the user of this.
- **FR-011**: The app must offer an opt-in overlay permission (`SYSTEM_ALERT_WINDOW`, "Prevent phone use"). When granted, if the user navigates to another foreground app while the alarm is active, the challenge shell must redisplay as an overlay over that app.
- **FR-012**: If the overlay permission is requested and denied (or never granted), the app must degrade gracefully: no overlay blocking, but FR-007 (audio never stops) and FR-010 (persistent notification) still apply. This must never be treated as an error state.
- **FR-013**: The app must never use screen pinning / lock task mode.

### TTS
- **FR-014**: The app must use the platform `TextToSpeech` API with on-device voices only when an alarm is ringing — no network-dependent TTS path may be reachable from the alarm-fire code path.
- **FR-015**: Voice availability for a deck's language must be checked at deck setup / language selection time (not at alarm fire time), prompting the user to download the on-device voice then if missing.
- **FR-016**: If the card being read has a non-empty `reading` field, TTS must speak the `reading`, not the `front`.
- **FR-017**: If TTS fails to initialize when an alarm fires, the service must fall back to a bundled default alarm tone; the challenge shell must still display normally. The alarm must never fail silent (no audio and no visible challenge) under any TTS failure mode.

### Offline posture
- **FR-018**: The entire alarm fire → ring → dismiss path (FR-001 through FR-017) must function correctly with networking fully disabled (airplane mode), indefinitely, with no timeout or retry logic that assumes eventual connectivity.

### Challenge shell (Phase 1 scope only — superseded by Phase 3)
- **FR-019**: The challenge shell must display the front (or reading, for TTS) of whatever card the alarm's assigned deck currently points to, read via the same data path Phase 3 will use (`Deck` → `Card`), so no rework is needed when Phase 3 lands.
- **FR-020**: The challenge shell must expose exactly one interaction in this phase: a clearly-labeled temporary "Dismiss (dev)" control that stops the service and audio, so the reliability layer can be verified end-to-end without the real answer/escalation logic existing yet. This control must be removed/replaced, not layered under, when Phase 3 ships.

## Non-functional requirements

- **NFR-001 (reliability)**: An alarm scheduled for a given time must fire within the OS-level accuracy guarantee of `setAlarmClock()` (effectively exact) on every tested device/OEM, including after reboot and after the app process has been killed by the OS.
- **NFR-002 (OEM resilience)**: The foreground service must remain alive for the duration of a ringing alarm on the physical test device (Samsung Galaxy Note 10 — Samsung's battery management is moderately aggressive). Where OEM power management prevents this despite correct API usage, the app must surface in-app guidance directing the user to the relevant OEM battery settings. **Known gap accepted for MVP:** the most aggressive OEMs (Xiaomi/Oppo, PRD §11) are not in the current test matrix; this risk carries forward in MVP_PROJECT_PLAN.md.
- **NFR-003 (safety invariant)**: There must be no code path, permission state, or error condition under which the alarm produces neither audio nor a visible challenge ("fails silent").
- **NFR-004 (Play policy)**: The exact-alarm and full-screen-intent usage must be consistent with Play policy for apps whose core function is an alarm clock (`USE_EXACT_ALARM` justification, Android 14+ `USE_FULL_SCREEN_INTENT` auto-grant for alarm-clock apps).
- **NFR-005 (latency)**: From alarm fire to first audible sound and first visible frame of the challenge shell must be near-instantaneous (target: under 1 second) even on a locked, doze-idle device.
- **NFR-006 (battery)**: The foreground service must not run beyond the lifetime of a ringing/snoozed alarm; it must fully stop (not just quiet down) once the alarm is resolved.

## UI/UX requirements

- Full-screen challenge shell renders over the lock screen with no home/back/recents navigation available while ringing (task exclusion / back-press interception), consistent with an alarm clock's expected lock-out behavior — but never via screen pinning (FR-013).
- The ongoing notification while an alarm rings must clearly communicate "alarm is active" and, when tapped, bring the challenge shell back to the foreground.
- The overlay-permission request must be presented with a plain-language explanation of what it does and that it is optional, at the point the user first sets up or edits an alarm (the exact placement within a full onboarding sequence is Phase 7's concern; Phase 1 only needs the request screen/dialog itself to exist and be triggerable for testing).
- TTS voice-download prompting (FR-015) needs a minimal UI surface (a dialog or inline prompt at deck/language setup) sufficient to drive the on-device voice download; polish and placement within onboarding is Phase 7.

## Data requirements

- Uses the existing `Alarm` entity from architecture.md (`id, deck_id, time, days_of_week, enabled, snooze_duration`) — no new persisted fields required for this phase.
- Uses the existing `Deck`/`Card` entities (already scoped in Phase 0) to resolve which card the challenge shell displays; Phase 1 does not require the Phase 2 card-management UI to exist, only the Room tables and at least one manually-seeded test card/deck for QA.
- The "alarm is currently ringing" state (which alarm, which card, current rung/volume — though rungs are Phase 3) is transient, held in the foreground service, not persisted to Room.
- New DataStore settings introduced by this phase: `vibration_enabled` (Boolean, default true, per FR-008b) and `overlay_blocking_opted_in` (Boolean, default false, per FR-011 — distinct from whether the OS permission is actually granted, which is queried live).

## API requirements

Not applicable — no backend or network API in this app. The relevant boundaries are internal component contracts, to be defined in `design.md`:
- Alarm scheduling component ↔ `BOOT_COMPLETED` receiver
- Foreground service ↔ challenge shell (audio/state ownership, per FR-007)
- Foreground service ↔ TTS component (voice check, fallback tone)
- Foreground service ↔ overlay-permission component

## Out of scope

- **Direct-boot alarms (documented MVP limitation):** if the device reboots and the user has not yet unlocked it once, `BOOT_COMPLETED` is not delivered until first unlock and credential-encrypted storage (where Room lives) is unavailable, so an alarm cannot fire in that window. Supporting this requires a `directBootAware` receiver on `LOCKED_BOOT_COMPLETED` plus mirroring the alarm schedule into device-protected storage — deliberately deferred; revisit post-MVP if field reports show overnight-reboot misses. (Rebooting and unlocking normally is fully covered by FR-003.)
- Typed-answer input, hint ladder, MCQ, dismiss-after-N-fails escalation logic (Phase 3).
- Ease sampler / which-card-to-show-next selection logic (Phase 4) — Phase 1 shows whatever card the deck currently points to, with no weighting.
- Card/deck CRUD UI (Phase 2).
- Streak calculation and display, full Settings screen (Phase 5).
- Analytics event firing (Phase 6) — though FR-019's shared data path should make wiring events in later phases straightforward.
- Full onboarding flow sequencing (Phase 7) — Phase 1 only needs the individual permission/voice-check prompts to exist and be functionally correct, not their final place in a first-run sequence.

## Resolved decisions (owner sign-off, 2026-07-08)

- **RD-001** (was OQ-001): Challenge-shell UI should be clean, minimal, and functional — no need to match the eventual Phase 3 visual design, but not visibly rough either.
- **RD-002** (was OQ-002): Engineering generates placeholder copy for the overlay-permission explanation; owner will tweak wording later. Track the copy string in one easily-findable place (string resources) so the tweak is a one-line change.
- **RD-003** (was OQ-003): Test matrix is the **Android Studio emulator** on macOS for virtual testing (note: Xcode is Apple-platform-only and cannot emulate Android) plus a physical **Samsung Galaxy Note 10**. The Note 10 tops out at Android 12, so Android 14+ behaviors (full-screen-intent auto-grant, foreground-service type enforcement) are verified on an API 34+ emulator image.
- **RD-004** (was OQ-004): Vibration is in scope: on by default, toggleable in Settings (now FR-008b).
- **RD-005** (was OQ-005): Volume ramp default is 20 seconds, gradual, defined as a named configuration constant in code — no inline magic number (now FR-008).
