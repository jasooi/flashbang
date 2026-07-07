# Tasks — Phase 2: Decks and Cards

**Status:** Draft, pending approval · **Requirements:** [requirements.md](requirements.md) · **Design:** [design.md](design.md)

## Task overview

9 tasks, 1–3h each. Linear through the data layer (T-201 → T-202), then the UI tasks fan out; T-203 (validation) is independent and can go first. Prerequisite: Phase 1 code is in place (it is); Phase 1's *device verification* does not block Phase 2 development, but both phases' matrices should be executed together once a device is available.

Update checkboxes with completion dates as tasks land; keep `implementation-notes.md` in this directory current, logging deviations as they happen (per CLAUDE.md).

## Tasks

- [ ] **T-201 — DAO additions + repository layer** · FR-204, FR-207, FR-212, FR-213
  - `CardDao.update/delete/observeByDeck/countByDeck`, `DeckDao.observeAllWithCounts/byNameAndLanguage`, `AlarmDao.setDeck`; `DeckRepository` (incl. `createUserDeck`), `CardRepository`.
  - **Files:** `data/Daos.kt`, `data/repo/DeckRepository.kt`, `data/repo/CardRepository.kt`
  - **Tests:** instrumented DAO tests for each new method (written now, run when device available — same posture as Phase 1); repository logic that is pure (user-deck naming default) unit-tested.

- [ ] **T-202 — Sample deck seeder + authored N5 content** (depends: T-201) · FR-209, FR-210, FR-211, NFR-202
  - `SampleDeckAsset` DTOs + kotlinx-serialization setup; `SampleDeckSeeder.seedIfNeeded` (idempotent by name+language, single transaction, silent-skip, partial-success row handling); author `assets/sample_deck_ja.json` with 25–30 JLPT-N5-adjacent cards (front/back/reading, hints where useful); delete `DebugSeeder` and wire the seeder into `FlashbangApp`.
  - **Files:** `data/seed/*.kt`, `app/src/main/assets/sample_deck_ja.json`, `FlashbangApp.kt` (DebugSeeder.kt removed), `gradle/libs.versions.toml`
  - **Tests:** unit — asset parsing (valid, malformed JSON, blank-front row skipped); instrumented — seed twice yields one deck, missing asset is a no-op, transaction atomicity.
  - **Note:** word list goes to the owner for content review per OQ-202 (before or after merge, owner's choice).

- [ ] **T-203 — CardDraft validation model** · FR-201, FR-202
  - Pure `CardDraft` with `validate()` and `toCard()` (trim; blank reading/hint → null).
  - **Files:** `ui/cards/CardDraft.kt`
  - **Tests:** unit — error matrix (blank/whitespace front/back), trimming, null-conversion, edit-mode id passthrough.

- [ ] **T-204 — Navigation + deck list screen** (depends: T-201) · FR-205, FR-207
  - Navigation-Compose `NavHost` (`decks` / `cards/{deckId}` / `editor/{deckId}?cardId=` / `dev`); `DeckListScreen` + VM over `observeAllWithCounts`; MainActivity becomes the NavHost shell; Phase 1 debug controls move behind a dev entry (T-208 finishes this).
  - **Files:** `ui/Navigation.kt`, `ui/decks/DeckListScreen.kt`, `ui/decks/DeckListViewModel.kt`, `ui/MainActivity.kt`, `gradle/libs.versions.toml`
  - **Tests:** VM unit test with fake repository (deck list state emission).

- [ ] **T-205 — Deck setup card + voice-check hook** (depends: T-204) · FR-206, FR-208
  - `DeckSetupCard` on the deck list when no user deck exists: language picker (JA/EN/ZH), optional name (default "My Deck"), create → `createUserDeck` → `TtsVoiceChecker.check`, download prompt on `MISSING_DATA` (reuses Phase 1 dialog).
  - **Files:** `ui/decks/DeckSetupCard.kt`, `ui/decks/DeckListViewModel.kt`
  - **Tests:** VM unit test — setup-card visibility (sample-only vs both decks); voice-check flow manual (matrix).

- [ ] **T-206 — Card list screen** (depends: T-204) · FR-204, FR-214, NFR-204
  - Lazy list with front/back/reading rows, FAB → add, tap → edit, empty state; FR-214 warning banner (deck assigned to enabled alarm && zero cards).
  - **Files:** `ui/cards/CardListScreen.kt`, `ui/cards/CardListViewModel.kt`
  - **Tests:** VM unit tests — list emission, banner predicate (fake repos).

- [ ] **T-207 — Card editor screen** (depends: T-203, T-206) · FR-201, FR-202, FR-203
  - Add/edit modes from route args; inline validation errors; save-disabled-until-valid; delete with confirmation dialog in edit mode.
  - **Files:** `ui/cards/CardEditorScreen.kt`, `ui/cards/CardEditorViewModel.kt`
  - **Tests:** VM unit tests — save gating, error surfacing, delete flow (fake repository).

- [ ] **T-208 — Dev tools section + alarm deck picker** (depends: T-204) · FR-212, FR-214
  - Relocate Phase 1 debug controls (test alarm, permissions, voice check) to `ui/dev/DevToolsSection.kt`; add deck picker driving `AlarmDao.setDeck` for the test alarm; test-alarm flow uses the picked deck.
  - **Files:** `ui/dev/DevToolsSection.kt`, `ui/MainActivity.kt`
  - **Tests:** manual (dev-only surface); `setDeck` covered by T-201 DAO tests.

- [ ] **T-209 — Suite green, matrix additions, implementation notes**
  - `./gradlew test` green; instrumented suite compiled (execution pending device, tracked as the standing Phase 1 deviation); append Phase 2 rows to [../phase-1-alarm-reliability/test-matrix.md](../phase-1-alarm-reliability/test-matrix.md) (fresh-install sample deck, JP rendering on Note 10, CRUD round-trip, edited-card-rings check); write `implementation-notes.md`; request human review.

## Definition of done

All tasks checked; every FR in requirements.md traceable to a passing test or a matrix row; sample deck visible with correct content on a fresh install; a card added in the UI is the card the Phase 1 alarm reads (end-to-end tie); human review + approval, then project-plan-tracker marks Phase 2 in MVP_PROJECT_PLAN.md.
