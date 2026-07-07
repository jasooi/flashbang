# Design — Phase 2: Decks and Cards

**Status:** Draft, pending approval · **Requirements:** [requirements.md](requirements.md) · **Builds on:** [../phase-1-alarm-reliability/design.md](../phase-1-alarm-reliability/design.md)

## Design overview

Phase 2 adds a thin repository layer over the Phase 1 DAOs, a three-screen Compose UI (deck list → card list → card editor) with Navigation-Compose, and a production sample-deck seeder driven by a bundled JSON asset. No schema changes; no new permissions; the Phase 1 service/scheduling layer is untouched except for retargeting the dev test-alarm at a picked deck.

```
FlashbangApp ──► SampleDeckSeeder ──► assets/sample_deck_ja.json
                       │ (single transaction, idempotent, silent-fail)
                       ▼
DeckListScreen ──► CardListScreen ──► CardEditorScreen
     │                                      │
     └── DeckSetupCard (one-time)           └── CardDraft.validate() (pure)
              │
              └── TtsVoiceChecker (Phase 1, FR-208)
```

## Package structure (additions)

```
app/src/main/java/com/flashbang/
  data/repo/        DeckRepository.kt, CardRepository.kt
  data/seed/        SampleDeckSeeder.kt, SampleDeckAsset.kt
  ui/decks/         DeckListScreen.kt, DeckListViewModel.kt, DeckSetupCard.kt
  ui/cards/         CardListScreen.kt, CardListViewModel.kt,
                    CardEditorScreen.kt, CardEditorViewModel.kt, CardDraft.kt
  ui/dev/           DevToolsSection.kt   (Phase 1 debug controls, relocated)
  ui/Navigation.kt  (NavHost: decks / cards/{deckId} / editor/{deckId}?cardId=)
app/src/main/assets/sample_deck_ja.json
```

New dependencies: `androidx.navigation:navigation-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `org.jetbrains.kotlinx:kotlinx-serialization-json` (+ serialization plugin) for the asset parse. All offline; still no `INTERNET` permission.

## Component design

### DAO additions (`data/Daos.kt`)

- `CardDao`: `@Update update(card)`, `@Delete delete(card)`, `observeByDeck(deckId): Flow<List<Card>>`, `countByDeck(deckId): Int`
- `DeckDao`: `observeAllWithCounts(): Flow<List<DeckWithCount>>` (`@Query` join with card count), `byNameAndLanguage(name, language): Deck?` (seeder idempotency check)
- `AlarmDao`: `setDeck(alarmId, deckId)` (`@Query UPDATE`)

### Repositories (`data/repo/`)

Thin, constructor-injected with DAOs (manual wiring, consistent with Phase 1's no-DI stance). `CardRepository` owns validation-passed writes only; `DeckRepository` owns user-deck creation (`createUserDeck(name, language)`) and exposes `decksWithCounts`. Repositories exist so Phase 4's sampler and Phase 6's analytics have one seam to hook, not to abstract Room for its own sake.

### Sample deck seeding (`data/seed/`)

- `SampleDeckAsset`: `@Serializable` DTOs — `SampleDeck(name, language, cards: List<SampleCard(front, back, reading?, hint?)>)`.
- `SampleDeckSeeder.seedIfNeeded(context, db)`, called from `FlashbangApp.onCreate` in an application-scope coroutine (replaces `DebugSeeder`, which is deleted):
  1. **Idempotency:** `deckDao.byNameAndLanguage(SAMPLE_DECK_NAME, JA)` exists → return `AlreadySeeded`. Name-based check is safe because deck renaming is out of scope in MVP (FR-205); revisit if renaming ever ships.
  2. Parse `assets/sample_deck_ja.json`; blank/missing front or back rows are skipped (partial success), an entirely missing/malformed asset returns `Skipped` — **never throws past the seeder** (FR-209).
  3. Insert deck + all cards in one `db.withTransaction { }`.
  4. Log the outcome; UI needs no signal (deck list is reactive via `observeAllWithCounts`).
- Sample content: 25–30 JLPT-N5-adjacent entries authored during T-202 (每 card: kanji/word front, English back, kana reading, short hint where useful).

### Card validation (`ui/cards/CardDraft.kt`)

Pure and JVM-tested, mirroring the Phase 1 pure-core pattern:

```kotlin
data class CardDraft(val front: String, val back: String, val reading: String, val hint: String) {
    data class Errors(val frontBlank: Boolean, val backBlank: Boolean) { val ok get() = !frontBlank && !backBlank }
    fun validate(): Errors
    fun toCard(deckId: Long, existingId: Long = 0): Card   // trims all fields, blanks → null for reading/hint
}
```

### Screens & ViewModels

All ViewModels take repositories via a shared manual factory; state exposed as `StateFlow`, collected with `collectAsStateWithLifecycle` (Phase 1 idiom).

- **DeckListScreen** (home): decks with name/language/count (FR-207). When no user deck exists, shows `DeckSetupCard` (OQ-201 recommendation: eager) — language picker (JA/EN/ZH), optional name field defaulting to "My Deck", create button → `DeckRepository.createUserDeck` → fire `TtsVoiceChecker.check(locale)`; `MISSING_DATA` prompts the Phase 1 download dialog (FR-208). Overflow/dev entry navigates to `DevToolsSection`.
- **CardListScreen**: lazy list (NFR-204) of front/back/reading, FAB → editor(add), row tap → editor(edit). Empty state points at the FAB. Shows the FR-214 warning banner when this deck is assigned to an enabled alarm and has zero cards.
- **CardEditorScreen**: four fields, inline errors from `CardDraft.validate()` (FR-201/202), save disabled until valid; delete (edit mode only) behind a confirmation dialog (FR-203). Reading field placeholder: "Pronunciation, e.g. かな — used by the alarm voice".
- **DevToolsSection**: the Phase 1 debug controls verbatim, plus a deck picker for the test alarm (`AlarmDao.setDeck`, FR-212). Tagged `PHASE1-DEV-ONLY` where applicable; Phase 5 absorbs the alarm parts into real Settings.

### Navigation

Single `NavHost` in `MainActivity`: `decks` (start) → `cards/{deckId}` → `editor/{deckId}?cardId={cardId}`, plus `dev`. `ChallengeActivity` stays a separate activity outside the graph (lock-screen lifecycle is deliberately isolated).

## Data flow: first launch to first alarm-ready deck

1. `FlashbangApp.onCreate` → seeder coroutine (off-main, NFR-202) → sample deck appears reactively in the deck list within ~100ms of first frame.
2. User opens the app → deck list shows "JLPT N5 Starter (JA) · 28 cards" + the setup card.
3. User either uses the sample deck immediately (PRD's install→alarm-in-a-minute path via dev/Phase-5 alarm assignment) or sets up their own deck (language pick → voice check → add cards).

## Error handling

| Failure | Behavior |
|---|---|
| Sample asset missing/malformed | `Skipped` result, warning log, app fully functional (FR-209) |
| Individual bad rows in asset | Row skipped, rest seeded (partial success) |
| Seeder DB error | Transaction rolls back atomically; retried next app start (idempotency check makes this safe) |
| Save with blank front/back | Inline field errors, save blocked (FR-201) |
| Deleting last card of an alarm-assigned deck | Allowed; FR-214 banner appears; Phase 1 empty-deck ring path already safe |
| Voice check fails at deck setup | Non-blocking prompt, deck still created (mirrors Phase 1 FR-015 posture) |

## Testing strategy

- **Unit (JVM):** `CardDraft.validate()`/`toCard()` (trim, blank→null, error matrix); `SampleDeckAsset` JSON parsing incl. malformed input and bad-row skipping (parser isolated from Android).
- **Instrumented (device pending, same deviation posture as Phase 1):** new DAO methods; `SampleDeckSeeder` idempotency (seed twice → one deck), transactionality (failure injects → zero rows), silent-skip on missing asset; repository round-trips.
- **Manual additions to the Phase 1 matrix:** sample deck present on fresh install; JP text renders on Note 10; add/edit/delete card round-trip; edited card is what the next alarm reads (ties to Phase 1 row 2).

## Alternatives considered

- **`is_sample` column vs name-based idempotency check:** column is more robust long-term but needs a schema migration now for a distinction MVP never renames away; name+language check matches the knowledge-transfer §3 pattern and is sufficient until deck renaming exists.
- **CSV asset (knowledge-transfer §4 pattern) vs JSON:** that pattern targets *user-supplied* files where header tolerance matters; a developer-controlled asset gets a typed serializer and zero quoting edge cases. JSON chosen (OQ-203 recommendation).
- **DataStore "seeded" flag vs querying the DB:** a flag can desync from the DB after clear-data/restore; the DB itself is the source of truth. Query chosen.
- **Deferring the user-deck setup until first card-add (lazy):** fewer first-run surfaces, but it buries the language picker that the TTS voice check needs and fights the §10 onboarding flow. Eager setup card chosen (OQ-201), pending owner confirmation.
