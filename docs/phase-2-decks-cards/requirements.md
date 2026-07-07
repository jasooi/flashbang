# Requirements — Phase 2: Decks and Cards

**Status:** Draft, pending approval · **Maps to:** [MVP_PROJECT_PLAN.md](../../MVP_PROJECT_PLAN.md) Phase 2 · **PRD source:** [flashcard-alarm-prd.md](../../flashcard-alarm-prd.md) §6.1 (primary), §10 · **Design lessons:** [flashcard-alarm-knowledge-transfer.md](../../flashcard-alarm-knowledge-transfer.md) §3

## Feature overview

The study-content layer: manual card management, the pre-loaded sample deck, and deck-per-alarm assignment. The PRD's onboarding promise depends on this phase — "the first alarm can be set within a minute of install" requires the sample deck to exist before the user has authored anything. Phase 1 shipped the Room schema and a debug-only seeder; Phase 2 replaces that seeder with the real sample-deck mechanism, adds the card CRUD UI, and gives alarms a real deck-assignment path.

Out-of-alarm UI is deliberately minimal (PRD §4.3): the product lives at the alarm moment. This phase builds exactly what's needed to maintain a deck, no more.

## User stories

(From PRD §3; the subset this phase serves.)

- As a learner, I want to add, edit, and delete my own flashcards, so that the deck matches exactly what I'm currently studying.
- As a learner, I want a sample deck available immediately after install, so that I can set up my first alarm within a minute without having to author cards first.
- As a learner, I want kanji cards to carry a reading, so that the alarm prompt is pronounced correctly (supports Phase 1 FR-016).

## Functional requirements

### Cards
- **FR-201**: Users can create a card with **front** (required), **back** (required), **reading** (optional), and **hint** (optional). Front and back must be non-blank after trimming; validation errors are shown inline, and saving is blocked until valid.
- **FR-202**: Users can edit any field of an existing card, with the same validation.
- **FR-203**: Users can delete a card, with a confirmation step (destructive action).
- **FR-204**: The card list for a deck shows front, back, and (when present) reading, ordered by creation, and updates reactively as cards change.

### Decks
- **FR-205**: The app has exactly **two decks** in MVP: the pre-loaded sample deck and one user deck. There is no deck creation, deletion, or renaming UI beyond the user deck's initial setup (multi-deck is post-MVP, PRD §9).
- **FR-206**: The user deck is created via a one-time lightweight setup: the user picks the deck's **language** (JA / EN / ZH) — required because language drives TTS voice checking (Phase 1 FR-015) — and may optionally name it (default: "My Deck").
- **FR-207**: The deck list shows each deck's name, language, and card count.
- **FR-208**: Creating the user deck triggers the Phase 1 TTS voice check (`TtsVoiceChecker`) for the chosen language, prompting a voice download when missing — this is the "deck setup / language selection time" hook required by PRD §5.4.

### Sample deck
- **FR-209**: On first app start, the sample deck (JLPT N5-adjacent Japanese starter content, PRD §10) is seeded from a bundled asset file. Seeding is **idempotent** (skips if the sample deck already exists), runs deck+cards in a **single transaction**, and on any failure (missing/malformed asset) is a **silent no-op that never blocks app startup** — per knowledge-transfer §3, a missing sample deck degrades onboarding, it must never break it.
- **FR-210**: Sample deck content ships as a structured asset (front, back, reading, hint per row) so content can be revised without code changes. Target size 25–30 cards.
- **FR-211**: Sample-deck cards are fully editable and deletable, same as user-deck cards — once installed it is the user's copy, not a protected resource.

### Deck-per-alarm assignment
- **FR-212**: Every alarm references exactly one deck (`alarm.deck_id`, already NOT NULL). Phase 2 provides the data path and a minimal picker for assigning a deck to an alarm; the full alarm-editing UI remains Phase 5. Multiple alarms may share a deck.
- **FR-213**: Card progress remains keyed to `(card_id, deck_id)` (schema shipped in Phase 1) so that alarms sharing a deck see consistent ease state. Phase 2 creates **no** progress rows and adds **no** progress logic — that is Phase 4 (which will lazily create progress on first outcome).
- **FR-214**: If a deck assigned to an enabled alarm has zero cards, the assignment is allowed but the UI shows an inline warning ("this alarm will ring with a default tone until the deck has cards") — the Phase 1 empty-deck fallback path already keeps the alarm safe.

## Non-functional requirements

- **NFR-201**: All CRUD and seeding operations work fully offline (no change to the Phase 1 no-`INTERNET`-permission posture).
- **NFR-202**: Seeding must not delay first-frame render; it runs off the main thread and the deck list updates reactively when it completes.
- **NFR-203**: All Japanese text (sample content, reading fields) is stored and rendered correctly (UTF-8 end-to-end; no mojibake on any tested device).
- **NFR-204**: Card list remains responsive at several hundred cards (lazy list; no full-deck loads on the main thread).

## UI/UX requirements

- Screens: **deck list** (home), **card list** (per deck), **card editor** (add/edit). Clean, minimal Material 3, consistent with the Phase 1 shell (RD-001 aesthetic).
- The deck list becomes the app's real home screen; the Phase 1 debug controls (test alarm, permission triggers, voice check) move to a clearly-labeled dev section or overflow entry — still reachable for the Phase 1 test matrix, but no longer the landing surface.
- Card deletion confirms via dialog; validation errors show inline under the offending field, not as toasts.
- Reading and hint fields are visibly optional (label or placeholder), with the reading field explained briefly ("used for pronunciation — e.g. かな for kanji").

## Data requirements

- No schema changes: `Deck`, `Card`, `Alarm`, `CardProgress` from Phase 1 suffice.
- New DataStore key: none required (sample-seed idempotency is determined by querying for the sample deck, not a flag — survives backup/restore mismatches better).
- New bundled asset: `assets/sample_deck_ja.json` (or equivalent) with the N5 starter content.
- The Phase 1 `DebugSeeder` is **removed**; the dev test-alarm flow re-targets whatever deck the user picks.

## API requirements

Not applicable (no backend). Internal contracts defined in design.md: repository layer over the Phase 1 DAOs, seeder ↔ app startup, deck-picker ↔ `AlarmDao`.

## Out of scope

- Anki/CSV import of any kind (deferred, PRD §9 — the knowledge-transfer §4 import pattern is for that later work).
- Multiple user decks, moving cards between decks, deck deletion/renaming.
- Card browsing niceties: search, sort options, bulk operations.
- Progress display on cards (Phase 4+) and streaks (Phase 5).
- Reverse-direction cards, fuzzy matching (deferred permanently for MVP, PRD §9).
- Full onboarding sequencing (Phase 7) — Phase 2 only guarantees the sample deck exists and the setup + voice-check hooks work when reached.

## Open questions

- **OQ-201**: User-deck setup timing — create it eagerly on first app open (a "set up your deck" card on the home screen), or lazily when the user first taps "add card"? **Recommendation: eager home-screen card** — it doubles as the natural place for the language picker and voice check, and matches the §10 onboarding flow.
- **OQ-202**: Sample deck content sign-off — engineering will author the 25–30 N5-adjacent entries (word, meaning, kana reading, optional hint); does the owner want to review the word list before it ships, or after implementation with tweaks as a follow-up?
- **OQ-203**: Asset format for sample content — JSON (recommended: typed, trivially parsed, no quoting edge cases) vs CSV (matches the knowledge-transfer import pattern but that pattern targets *user-supplied* files, which this is not).
