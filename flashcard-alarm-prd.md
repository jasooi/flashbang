# PRD — Flashcard Alarm (working title)

**Version:** 0.1 (draft) · **Owner:** Jas · **Date:** July 2026 · **Platform:** Android only

---

## 1. Purpose and positioning

A flashcard-as-alarm app for language learners. The user's daily alarm *is* a flashcard: the alarm sound is a text-to-speech reading of a card from a deck the user chose, and the alarm can only be dismissed by answering the card correctly. Because the alarm is a forcing function the user already has, the app delivers guaranteed daily retrieval practice with zero additional time investment.

**Positioning statement (app store copy direction):** This app is not for learning new flashcards. It reinforces cards you are already studying, before you forget them — every morning, automatically, in the time you would have spent hitting snooze.

**Honest mechanism (what we claim):** retrieval practice + unskippable daily consistency. We do **not** claim subconscious or sleep-state learning benefits.

**Category lineage:** Alarmy (task-to-dismiss alarms, proven willingness to tolerate friction at wake-up) × spaced-repetition flashcards (Anki et al.). Competitive research (July 2026) found no notable product occupying this fusion; the likely reason incumbents avoided it — flashcards break the "alarm must always be dismissable" contract — is solved by the reinforcement-only framing plus the escalation ladder (§6.3).

---

## 2. Target user

**Primary segment:** language learners maintaining a vocabulary deck (self-study or classroom), who struggle with review consistency rather than with study method. Initial market focus: learners of Japanese and English learners in Japan, expanding later.

**Explicitly secondary / deferred:** test-cramming students (short horizon, deck churn — poorly served by the SRS layer) and Anki power users (served when Anki import ships, post-MVP).

**Core user need:** a low-friction way to reinforce cards already being studied. Studying should feel like something that happens in the background of an existing routine, not an added burden.

---

## 3. User stories

- As a learner, I want my alarm to make me answer a vocabulary flashcard before it stops, so that I get guaranteed daily review without setting aside separate study time.
- As a learner, I want the alarm to start quiet and only ramp up while showing me the card, so that I'm not jolted awake before I've even had a chance to try recalling the answer.
- As a learner, I want a hint to appear if I'm stuck for too long, so that I'm not held hostage by a card I genuinely can't recall while still half-asleep.
- As a learner, I want the alarm to always become dismissable eventually (even without the right answer), so that I never feel trapped or unsafe first thing in the morning.
- As a learner, I want to snooze and get a different card next time, so that snoozing still counts as useful practice instead of pure avoidance.
- As a learner, I want cards I struggle with to come back around more often than ones I already know, so that my limited morning review time is spent where it matters most.
- As a learner, I want kanji cards to be read aloud with the correct pronunciation (not misread), so that the alarm prompt is actually intelligible in Japanese.
- As a learner, I want to add, edit, and delete my own flashcards, so that the deck matches exactly what I'm currently studying.
- As a learner, I want a sample deck available immediately after install, so that I can set up my first alarm within a minute without having to author cards first.
- As a learner, I want to see my streak of answered mornings, so that I have a visible sense of consistency and motivation to keep going.
- As a learner, I want the app to work fully offline, so that a lack of signal or airplane mode never causes my alarm to fail or go silent.
- As a learner, I want to optionally let the app take over my screen if I try to wander off mid-challenge, so that I can choose stronger enforcement if I know I'm prone to disabling alarms half-asleep.
- As a learner, I want to control settings like snooze length, hint delay, and how many wrong attempts before the question gets easier, so that the difficulty matches my own tolerance for morning friction.

---

## 4. Core user experience

### 4.1 The alarm flow (happy path)

1. Alarm fires at the scheduled time. Audio begins at **low volume**: TTS reads the card front (the target-language word/phrase), repeating on a loop.
2. Volume escalates gradually. The challenge screen is displayed over the lock screen showing the card front and a typed-answer input.
3. After a configurable delay (default 15–30 s band, single default value to be set during build), the **hint** is revealed on screen and the TTS loop continues front-only. (Escalation is annoyance/volume plus on-screen hint — the TTS never reads the answer aloud; see §6.2.)
4. The user types the card's meaning. Correct answer → alarm stops, streak updates, brief success state, done.
5. **Snooze** is available at all times. Snoozing re-arms the alarm for the snooze interval and advances to the **next card** on the next ring (snoozing = extra reps, not pure failure). The snoozed card is logged as `snoozed` for the scheduler.

### 4.2 Answer flow (escalation ladder)

There is no MCQ-vs-typed setting; the ladder replaces it.

- **Rung 1 — typed answer.** Exact match after normalisation (§6.4). After **N wrong attempts** →
- **Rung 2 — multiple choice.** 1 correct + 3 distractors drawn from the same deck's card backs. After **N wrong attempts** →
- **Rung 3 — dismissable.** A "dismiss" control appears. The card is marked `escaped` (failed) and rescheduled aggressively by the sampler.

**N = 3 by default, user-configurable in Settings.** The ladder guarantees the alarm is always ultimately dismissable — this is a hard product invariant (an alarm that cannot be silenced is an uninstall and a safety problem).

### 4.3 Out-of-alarm experience

Deck and card management (§6.1), settings, streak display. Deliberately minimal: the product lives at the alarm moment.

---

## 5. Alarm reliability (load-bearing layer)

This section is first-class because the entire product depends on the alarm firing exactly on time, audibly, offline, every time. Engineering effort in the first build cycle concentrates here.

### 5.1 Scheduling

- `AlarmManager.setAlarmClock()` for all alarms — exempt from Doze, surfaces the status-bar alarm icon. Declare `USE_EXACT_ALARM` (app's core function is an alarm clock, so this qualifies under Play policy).
- `BOOT_COMPLETED` broadcast receiver re-registers all active alarms after device restart.
- Alarm fire path: exact alarm → **foreground service** (owns audio + state) → **full-screen intent** launches the challenge activity with `setShowWhenLocked` / `setTurnScreenOn`. On Android 14+, `USE_FULL_SCREEN_INTENT` is auto-granted to alarm-clock apps; Play review will verify the core function.

### 5.2 Audio

- Audio routed to the alarm stream (`AudioAttributes.USAGE_ALARM`) so silent mode and DND alarm exceptions behave as users expect.
- **Audio is the true enforcement layer.** The sound is owned by the foreground service, not the activity: if the user escapes the challenge UI, the alarm keeps ringing at full volume until the challenge is answered or the ladder's dismiss rung is reached.
- Volume ramp: starts low, escalates on a fixed curve.

### 5.3 Blocking other apps (tiered, with graceful degradation)

1. **Default (no extra permission):** full-screen intent over lock screen + persistent audio + high-priority ongoing notification that re-opens the challenge. Covers the overwhelming majority of wake-ups.
2. **Opt-in "Prevent phone use" (overlay permission `SYSTEM_ALERT_WINDOW`):** if the user navigates away, the challenge redisplays over the foreground app. Requested during onboarding with a clear explanation; never required.
3. **Degraded behaviour when overlay is denied:** no blocking — the phone simply never goes quiet (per 5.2) and the notification persists. Behaviourally equivalent pressure, permission-light, honest.

Screen pinning / lock task mode: explicitly out — requires per-session confirmation and reads as hostile.

### 5.4 TTS reliability

- Platform `TextToSpeech`, **on-device voices only** at alarm time — no network dependency at wake.
- Voice availability is verified at **deck setup / language selection time**; the user is prompted to download the on-device voice for the deck's language then. Never at 6:45 am.
- If a card has a **reading** field (§7), TTS speaks the reading, not the front — this avoids kanji misreadings (e.g. 辛い, 行く) where a wrong reading would make the alarm prompt unintelligible.
- Fallback if TTS init fails at alarm time: a bundled default alarm tone plays; the challenge screen still shows the card. The alarm never fails silent.

### 5.5 Offline posture

The app is fully functional with zero network, indefinitely. All functional data is local (§7). Network is used only for opportunistic analytics flush (§8) — nothing at alarm time waits on the network.

---

## 6. Feature specification (MVP)

### 6.1 Decks and cards

- Manual card entry: **front** (target language, required), **back** (meaning, required), **hint** (optional), **reading** (optional; kana reading for kanji fronts — used for answer matching and TTS).
- One user deck in MVP, plus **one pre-loaded sample deck** so the first alarm can be set within a minute of install.
- Card operations: add, edit, delete. (Multi-deck organisation and moving cards between decks ship with multi-deck support, post-MVP §9.)
- Each alarm is assigned a deck. Multiple alarms may share a deck; **progress belongs to the deck**, so a card's ease state is consistent across all alarms using that deck.

### 6.2 Hint ladder

- If the user provided a hint, it is shown at the hint stage.
- If not, a **deterministic mask hint** is auto-generated at display time: first character + length mask (e.g. た＿＿ for a 3-kana answer; "a_____" style for Latin script). Zero infrastructure, works offline, generated from the back (or reading, if present).
- LLM-generated hints: **deferred**, bundled with Anki import (where they are actually needed — imported decks arrive hint-less in bulk). Requires a key-holding server-side proxy; out of MVP scope.
- The hint reveal is on-screen only. **TTS never reads the back/answer aloud** — the pre-hint vs post-hint distinction is a core scheduler and metrics signal (§6.5, §8) and reading the answer would void it.

### 6.3 Escalation ladder

As specified in §4.2. N default 3, configurable. `escaped` cards are weighted maximally by the sampler (shown at the earliest eligible opportunity, subject to the recency guard).

### 6.4 Typed-answer matching policy

Exact match after normalisation. Normalisation pipeline: trim whitespace → case-fold → Unicode NFKC (folds width variants, compatibility characters) → **katakana→hiragana fold** (fixed code-point offset). Input matches if it equals the normalised **back** or the normalised **reading**. No fuzzy/edit-distance matching in MVP — the MCQ rung is the safety net for near-misses. (Note: normalisation cannot convert kanji↔kana; that is what the reading field is for.)

### 6.5 Scheduler ("ease sampler")

Not interval-based SRS; a **weighted sampler**: harder cards are shown more often, easier cards less. No concept of deck exhaustion — there is always a next card.

- **Ease signal per card**, from alarm outcomes only: answered **pre-hint** (strongest positive) / answered **post-hint** (weak positive) / **wrong attempts count** (negative, per rung) / **snoozed past** (negative) / **escaped** (strongest negative). Response *time* is explicitly **not** a signal (confounded by grogginess, typing, IME).
- **Recency guard:** a card just shown is ineligible while other eligible cards exist — specifically, a card snoozed/answered today is not re-shown the same day if alternatives exist. If it is the only card (or all others are also in cooldown), it may repeat. Degenerate case: a one-card deck simply repeats.
- Design reference: adapt the ease-update logic from Jas's existing SRS implementation (port the design, not the code — target is small, ~150 lines of Kotlin).

### 6.6 Streaks

A streak = consecutive days with at least one alarm challenge **answered** (either rung; `escaped` does not count). Displayed in-app. Badges and social sharing: deferred (§9).

### 6.7 Settings

Alarm time(s), snooze duration, N (ladder threshold), hint reveal delay, deck-per-alarm assignment, overlay permission opt-in, TTS voice check/download entry point.

---

## 7. Data model and tech stack

See [Architecture.md](.claude/architecture.md) for the Room data model, tech stack, and API surface.

---

## 8. Metrics and analytics

### 8.1 North star

**Answer-quality progression:** the ratio of *pre-hint : post-hint : escaped* outcomes, compared between a user's first days and ~day 30. The product works if the distribution shifts toward pre-hint over a month of use. Supporting product-health metric: **week-4 alarm retention** (user still has an active alarm). Tracked but secondary: DAU, installs (DAU is near-tautological for an alarm app and would mask failure).

### 8.2 Event schema (offline-queued, one-way)

`alarm_fired`, `answered_pre_hint`, `answered_post_hint`, `fell_to_mcq`, `answered_mcq`, `escaped`, `snoozed` — each with timestamp, anonymous install ID, deck language, days-since-install derivable. Events queue locally and flush opportunistically when network exists (Firebase handles this); **nothing at alarm time depends on network**. Functional data never leaves the device; analytics is a separate one-way stream.

### 8.3 Monetisation

MVP: none. Roadmap: ad placements on the post-dismiss / ringing-adjacent surfaces (Alarmy model). Note the dependency: ad revenue scales with completed alarm sessions — another reason completion quality, not DAU, is the north star.

---

## 9. Scope

### MVP (in)

Manual cards; one user deck + one pre-loaded sample deck; card add/edit/delete; forward-direction cards only (front→back; reverse deferred — many-to-one translation ambiguity makes back→front pedagogically weak); full hint ladder with deterministic mask auto-hints; full escalation ladder (typed → MCQ → dismissable, N=3 default); ease sampler with recency guard; snooze-advances-card; streaks; the complete alarm-reliability layer (§5); Firebase event instrumentation; settings.

### Deferred (explicitly out of MVP)

Anki `.apkg` import (front/back text only at first; note types, cloze, media, templates are a later decision) + the LLM hint proxy that ships with it; multiple user decks and moving cards between decks; badges + social sharing (the "fastest answer" badge is **cut permanently** — speed rewards corrupt the ease signal and train position-memorisation); reverse card direction; fuzzy answer matching; iOS; monetisation.

---

## 10. Launch checklist (action items)

- [ ] **Privacy policy URL** (required by Play for analytics collection).
- [ ] **Play Data Safety declaration** covering Firebase Analytics events and anonymous identifiers.
- [ ] Play policy review prep: alarm-clock core function (for `USE_EXACT_ALARM` and Android 14+ full-screen-intent auto-grant).
- [ ] Onboarding flow: first-run → sample deck → set first alarm → TTS voice check/download → optional overlay permission pitch.
- [ ] Sample deck content authored (suggest: JLPT N5-adjacent Japanese starter deck, given initial market).

## 11. Open questions / risks

- Single default for hint-reveal delay (15 s vs 30 s) — decide during build; consider making it the one A/B lever at launch.
- MCQ distractor quality in small decks (<8 cards): distractor pool may be thin; acceptable for MVP, revisit with multi-deck.
- OEM aggression (Xiaomi/Oppo battery killers) can kill foreground services despite correct APIs — test matrix should include at least one aggressive OEM device; consider an in-app "battery settings" help page (dontkillmyapp-style guidance).
- Name/branding: TBD.
