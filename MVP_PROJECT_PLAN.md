# MVP Project Plan — Flashcard Alarm

**Release:** MVP · **Status:** Not started (docs-only phase) · **Source of truth:** [flashcard-alarm-prd.md](flashcard-alarm-prd.md) §9 (Scope) for what's in/out, [.claude/architecture.md](.claude/architecture.md) for tech stack and data model, [flashcard-alarm-knowledge-transfer.md](flashcard-alarm-knowledge-transfer.md) for scheduler/data-model/seeding design lessons from a prior related project.

## How to use this document

This file tracks MVP scope at the feature level via checkboxes. Before a feature is implemented, its `requirements.md`, `design.md`, and `tasks.md` must be written (by the feature-planner agent) and approved by the human. Check a feature's box only once it is built, tested, and human-reviewed — not when docs are written. Sub-bullets under each feature are the individual capabilities that must all land for the feature to count as done.

Build order follows the PRD's own prioritization: alarm reliability is called out as the load-bearing layer the entire product depends on (§5), so it comes first, before any of the study-content features.

---

## Phase 0 — Foundations

- [ ] **Project scaffolding**
  - [ ] Kotlin + Jetpack Compose (Material 3) project set up, min SDK ~26
  - [ ] Package/module structure agreed (see architecture.md, to be extended once code exists)
- [ ] **Data layer**
  - [ ] Room schema: `Deck`, `Card`, `CardProgress`, `Alarm`, `StreakState` (architecture.md)
  - [ ] DataStore schema for settings (N, hint delay, defaults)
  - [ ] `card.deck_id` non-null from day one, `language` backed by a real enum/CHECK constraint (per knowledge-transfer.md §2 — do not repeat the nullable-FK and app-only-validation mistakes from the prior project)

## Phase 1 — Alarm reliability (load-bearing layer, PRD §5)

- [ ] **Exact alarm scheduling** (§5.1) — `AlarmManager.setAlarmClock()`, `USE_EXACT_ALARM` declared, `BOOT_COMPLETED` receiver re-registers all active alarms after restart
- [ ] **Alarm fire path** (§5.1) — exact alarm → foreground service (owns audio + state) → full-screen intent → challenge activity (`setShowWhenLocked`/`setTurnScreenOn`)
- [ ] **Audio** (§5.2) — alarm-stream routing (`USAGE_ALARM`), volume ramp curve, audio owned by the foreground service (survives the user escaping the challenge UI)
- [ ] **Blocking tiers** (§5.3) — default (full-screen intent + persistent audio + ongoing notification), opt-in overlay permission ("Prevent phone use"), degraded no-overlay fallback
- [ ] **TTS reliability** (§5.4) — on-device voice check/download at deck setup time (never at alarm time), reading-field pronunciation for kanji fronts, bundled fallback tone if TTS init fails
- [ ] **Offline posture verification** (§5.5) — confirm the full alarm→challenge→dismiss path works with networking fully disabled

## Phase 2 — Decks and cards (PRD §6.1)

- [ ] **Manual card CRUD** — front (required), back (required), hint (optional), reading (optional)
- [ ] **One user deck + pre-loaded sample deck**, deck-per-alarm assignment, progress keyed to deck (not alarm) so shared-deck alarms stay consistent
- [ ] **Sample deck seeding** — idempotent (skip if already seeded), single transaction for deck+cards, silent no-op if content is missing rather than blocking onboarding (per knowledge-transfer.md §3)
- [ ] **Sample deck content authored** — JLPT N5-adjacent Japanese starter deck (PRD §10)

## Phase 3 — Alarm challenge flow (PRD §4, §6.2–§6.4)

- [ ] **Happy-path alarm flow** (§4.1) — fires → escalating audio/UI → typed-answer input → correct answer stops alarm, updates streak
- [ ] **Hint ladder** (§6.2) — user-provided hint shown if present; otherwise deterministic mask auto-hint generated from back/reading; TTS never reads the answer aloud
- [ ] **Escalation ladder** (§4.2, §6.3) — typed → MCQ (1 correct + 3 distractors from deck) → dismissable, N wrong attempts per rung (default 3, configurable), always ultimately dismissable
- [ ] **Typed-answer matching** (§6.4) — normalisation pipeline (trim → case-fold → NFKC → katakana→hiragana fold), matches against normalised back or reading
- [ ] **Snooze** (§4.1) — re-arms for snooze interval, advances to next card on next ring, logs outcome as `snoozed`

## Phase 4 — Scheduler ("ease sampler") (PRD §6.5)

- [ ] **Ease signal capture** — per-card outcome tracking: pre_hint / post_hint / wrong_attempts / snoozed / escaped (response time explicitly excluded as a signal)
- [ ] **Weighted sampler** — harder cards shown more often; no deck-exhaustion concept
- [ ] **Recency guard** — a card shown/snoozed today is ineligible while other eligible cards exist; degenerate one-card-deck repeat case handled
- [ ] **Escape handling** — `escaped` cards weighted maximally with an explicit floor/reset (not just a nudge), to avoid the ease-factor death spiral described in knowledge-transfer.md §1
- [ ] Ease-update logic lives in exactly one place in the codebase (no duplicate implementations — see knowledge-transfer.md §1 "bug to not repeat")

## Phase 5 — Streaks and settings (PRD §6.6–§6.7)

- [ ] **Streaks** — consecutive days with ≥1 answered challenge (either rung; `escaped` doesn't count), displayed in-app
- [ ] **Settings screen** — alarm time(s), snooze duration, N (ladder threshold), hint reveal delay, deck-per-alarm assignment, overlay permission opt-in, TTS voice check/download entry point

## Phase 6 — Analytics instrumentation (PRD §8)

- [ ] **Event schema wired** — `alarm_fired`, `answered_pre_hint`, `answered_post_hint`, `fell_to_mcq`, `answered_mcq`, `escaped`, `snoozed` (timestamp, anonymous install ID, deck language)
- [ ] **Offline-queued, one-way flush** confirmed — nothing at alarm time depends on network; functional data never leaves the device

## Phase 7 — Launch readiness (PRD §10)

- [ ] Privacy policy URL published
- [ ] Play Data Safety declaration covering Firebase Analytics events and anonymous identifiers
- [ ] Play policy review prep for alarm-clock core function (`USE_EXACT_ALARM`, Android 14+ full-screen-intent auto-grant)
- [ ] Onboarding flow: first-run → sample deck → set first alarm → TTS voice check/download → optional overlay permission pitch

---

## Definition of done for MVP

All phases above checked off, matching the full in-scope list in PRD §9: manual cards, one user deck + sample deck, card add/edit/delete, forward-direction cards only, full hint ladder, full escalation ladder, ease sampler with recency guard, snooze-advances-card, streaks, the complete alarm-reliability layer, Firebase event instrumentation, and settings.

Explicitly **not** required for MVP (PRD §9 deferred list): Anki import + LLM hint proxy, multiple user decks, badges/social sharing, reverse card direction, fuzzy answer matching, iOS, monetisation.

## Open risks carried into build (PRD §11)

- Hint-reveal delay default (15s vs 30s) undecided — pick during build, candidate as the one launch A/B lever.
- MCQ distractor pool may be thin in small decks (<8 cards) — acceptable for MVP.
- OEM background-kill aggression (Xiaomi/Oppo) could kill the foreground service despite correct APIs — test on at least one aggressive OEM device; consider an in-app battery-settings help page.
- App name/branding still TBD.
