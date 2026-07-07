# Knowledge Transfer: Laoshi → Flashcard Alarm

From: the engineer who built Laoshi's scheduler, data model, and import pipeline
To: whoever's building Flashcard Alarm
Re: the four places Laoshi's design should actually shape your decisions

None of this is code you can import — different language, different runtime, different everything, and you won't have the Laoshi repo to check against, so treat every snippet below as illustrative rather than a file you can go pull up. This is the "here's what I learned building the same kind of thing once already" memo. Four areas. I'll give you the exact logic, why it's shaped that way, and — more usefully — where it will and won't map onto what you're building, including the mistakes I'd fix if I were starting over.

## What Laoshi is, quickly

Laoshi Coach is a web app for practicing Mandarin Chinese and Japanese vocabulary in context. Users organize vocabulary into **decks** (per-deck language tag, ZH or JP). Each vocabulary item is called a **Word** — not just a flashcard front/back, but a record that also carries its own spaced-repetition state (more below). During a practice session, the user writes full original sentences using a target word, and an LLM-based "coach" evaluates each sentence for grammar/usage/naturalness and gives conversational feedback — think an AI tutor chat, not a flip-a-card-and-grade-yourself flow. After working through a word (possibly across several sentence attempts), the user self-rates their own mastery of that word on a 0-5 scale, and *that* rating is what drives the spaced-repetition scheduler discussed in §1 below. Progress is tracked via a modified SM-2 algorithm, plus a daily practice streak.

Stack, for orientation only (none of it is relevant to what you're building except as background on why certain design decisions look the way they do): React + TypeScript + Vite frontend, Flask + SQLAlchemy + PostgreSQL backend, JWT-based auth, and an AI layer (OpenAI Agents SDK orchestrating DeepSeek for Chinese sentence feedback, Claude 3.5 Sonnet for Japanese sentence feedback, Gemini Flash for orchestration and summaries), with mem0 for cross-session user memory and Redis for in-session conversation state. Flashcard Alarm shares none of this — it's Kotlin/Compose, fully offline, no accounts, no LLM in the loop at all. The only thing carrying over is *design thinking* from having already built one spaced-repetition/vocabulary product, not any of this infrastructure.

One framing that matters throughout what follows: Laoshi's "quality" score that feeds its scheduler is a **human self-rating** — the user directly tells the app "I knew this a 4/5" after already seeing AI feedback on their sentence. It is not computed automatically from correctness. Your app has no self-rating step at all; your equivalent signal (pre_hint / post_hint / wrong_attempts / snoozed / escaped) is derived entirely from observed behavior. Keep that distinction in mind — it's the main reason §1 below is "read the reasoning, don't copy the mechanism."

---

## 1. Scheduler ease-logic

This is the one you'll be most tempted to copy wholesale, and the one where you should copy the *least* literally, because your scheduler and mine are solving different problems. Read this whole section before you write a line of Kotlin.

### What Laoshi actually does

Every word carries four SRS fields: `repetitions` (int), `interval_days` (int), `ease_factor` (float, starts at 2.5), `next_review_date` (date, null = never shown). After each practice turn, the user self-rates 0-5 and this runs:

```python
def update_srs(word, quality: int):
    # Fast-track perfect first attempts
    if word.repetitions == 0 and quality == 5:
        word.interval_days = 14
        word.repetitions = 2
        word.ease_factor = 2.5
    elif quality < 3:
        # Harsh reset on failure (fair since user controls quality)
        word.repetitions = 0
        word.interval_days = 1
    else:
        # Standard SM-2 progression with gentler early intervals
        if word.repetitions == 0:
            word.interval_days = 1
        elif word.repetitions == 1:
            word.interval_days = 3
        elif word.repetitions == 2:
            word.interval_days = 7
        else:
            new_interval = word.interval_days * word.ease_factor
            # Use ceil for intervals < 7 to prevent stuck at 1 day
            word.interval_days = math.ceil(new_interval) if new_interval < 7 else round(new_interval)
        word.repetitions += 1

    # Ease factor always updates, all branches — this is the one line that's pure textbook SM-2
    word.ease_factor += (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    word.ease_factor = max(1.3, word.ease_factor)

    word.next_review_date = date.today() + timedelta(days=word.interval_days)
```

Three design choices worth knowing the reasoning behind, since your ease sampler will face analogous choices even though the mechanism is different:

- **The 1.3 ease floor.** Textbook SM-2 lets ease factor drift arbitrarily low, which produces a death spiral — a card that's been failed a lot gets shown so frequently and with such short intervals that it becomes annoying rather than helpful. 1.3 is a hand-picked floor that keeps a "hard" card's interval from collapsing to nothing. Your `escaped` signal is the equivalent failure mode — if you let ease keep dropping unbounded on repeated `escaped` outcomes, that card will dominate every single morning. You want an analogous floor on how low ease can go, or a cap on how often one card is allowed to be selected consecutively regardless of how bad its ease score is.
- **Quality-5-on-first-try fast track.** This exists because early SM-2 (1/3/7-day steps for the first three reps) is annoyingly slow for a word the user clearly already knows — without the fast track, a genuinely known word still takes three sessions before entering exponential growth. You don't have this problem in the same shape (no "already knew this on day one" case really applies to an alarm-reinforcement product), but the underlying principle — don't force every card through the same ramp regardless of demonstrated strength — is worth keeping. A card answered pre-hint five days running shouldn't need the same number of reps to "de-prioritize" as one that's been oscillating.
- **Harsh reset on failure, no partial credit.** Quality < 3 wipes `repetitions` to 0 and interval to 1 day, full stop, regardless of how many successful reps came before. This is deliberate — SM-2 without a hard reset lets ease-factor math paper over a fresh failure. Your `escaped` outcome should probably do the equivalent: don't let a card's history of pre-hint answers soften how aggressively it comes back after an escape. The PRD already says "escaped cards are weighted maximally" — just make sure your implementation actually resets rather than merely nudges.

### Where it stops being a template

Laoshi's scheduler answers "when should this word next appear" (an interval, days out). Yours answers "which of N always-available cards gets shown right now" (a sampling weight, no interval, no exhaustion). These are different data structures, not different tunings of the same one. Concretely:

- You have no `next_review_date` — there's no "not due yet." Every card is always eligible modulo the recency guard. Don't try to retrofit interval-days into this; it doesn't apply.
- You have five outcome signals (pre_hint, post_hint, wrong_attempts, snoozed, escaped) mapping to one ease score, versus Laoshi's single 0-5 quality. Here's the thing to know going in: **Laoshi never actually solves the problem you're facing.** The `quality` that feeds `update_srs` isn't derived from anything — it's a raw int the user types in directly (their own self-rating), passed straight through from the API request into `update_srs` with zero averaging or computation on my end. There is an averaging step elsewhere in the same code path (grammar/usage/naturalness scores across multiple sentence-attempts for one word get averaged via a plain mean into a display-only `is_correct` flag, used for the session summary and report card) — but that's a fully separate path that never touches the scheduler. So there's no pattern here to lean on for collapsing several behavioral counters into one ease adjustment; that mapping is entirely your own design problem, not a solved one you're just adapting. Budget real design time for it rather than assuming a mature spaced-repetition codebase already has the answer sitting in it — it doesn't, mine included. The one thing I'd flag pre-emptively: don't reach for a naive mean across signals of different scales (a `wrong_attempts` count and a boolean `escaped` flag aren't commensurable) — you'll want deliberate per-signal weights, tuned against real usage data once you have it, not derived up front from first principles.
- Recency guard: I don't have one. Laoshi's word pool is a single-pass-per-session selection (see below), never a "what haven't I shown today" rolling constraint. Your recency guard (a card shown today is ineligible while alternatives exist) is genuinely new design, not something to adapt from anything I built — just flagging so you don't go looking for a pattern that isn't there.

### The 40/60 new/due split — reference only, weaker relevance

For completeness, since it's the other half of "the scheduler": each practice session picks words via

```python
target_new = round(words_count * 0.4)
target_review = words_count - target_new
# Pool 1: next_review_date IS NULL (new)
# Pool 2: next_review_date <= today, sorted soonest-overdue-first
# shortfall in either pool backfills from the other; final fallback pool = not-yet-due words
```

New words are `random.sample`'d (randomized); due words take the N most-overdue deterministically (not randomized). This doesn't transfer conceptually — you have no "new vs. due" distinction, every card in a one-deck alarm is just "in the rotation" from day one. I'm including it mainly so you don't wonder if you're missing something; you're not.

### A bug to not repeat

`update_srs` exists as two separate copies in my codebase — once as a module function in `practice_runner.py`, once as a `Word.update_srs` model method — that are supposed to stay identical and don't have a shared source of truth. They've drifted from each other before. Whatever language you write your ease-update logic in, put it in exactly one place and have everything call through it.

---

## 2. Data model shape

My schema (SQLAlchemy/Postgres, but the shape is what matters):

```python
class Deck(db.Model):
    id, name (String 200, required), description (String 500, nullable)
    user_id (FK, required)
    language (String(2), default 'ZH')   # app-level enum check only, no DB CHECK constraint
    created_ds, updated_ds

class Word(db.Model):
    id, word (String 150, required), reading (String 150, required), meaning (String 300, required)
    notes (String 200, nullable — I use this for "which CSV import batch" tagging)
    deck_id (FK, nullable — see gotcha below)
    # SRS fields live directly on Word:
    repetitions, interval_days, ease_factor, next_review_date
    # mastery fields, also directly on Word:
    last_quality, marked_as_known, is_mastered
```

Your PRD's `Deck`/`Card`/`CardProgress` split maps cleanly onto this — `Card` ≈ my `Word` minus the SRS/mastery columns, `CardProgress` ≈ those columns pulled into their own table. That split is the right call for you and *not* something you should backport into my design, because our constraints differ:

- I have exactly one owner per word (`user_id`), one deck per word, and progress is meaningless outside that single relationship, so embedding SRS state directly on `Word` was fine — there was never a second consumer of the same word's state.
- Your PRD explicitly wants multiple alarms to share one deck with progress "belonging to the deck," which is a many-consumers-one-progress-record shape. Embedding would be wrong for you. Keep `CardProgress` separate, keyed on `(card_id, deck_id)` like the PRD already says. I'm flagging this only to confirm your instinct is right, not to talk you out of it.

Two things I'd genuinely warn you away from copying:

- **`Word.deck_id` is nullable.** It shouldn't be — a word with no deck is a real bug state that's crept in over time (an artifact of words originally being created before decks existed as a concept, and the migration never tightening the constraint). Don't inherit this. Make your `card.deck_id` NOT NULL from day one; there's no legacy reason for you to leave the door open.
- **`language` has no DB-level CHECK constraint**, only an app-level tuple check (`SUPPORTED_LANGUAGES = ('ZH', 'JP')`). Fine for Postgres-via-SQLAlchemy where I control every write path, but you're on Room/SQLite — use an actual enum type or a `CHECK` constraint in your schema rather than relying on Kotlin-side validation alone, since a future you (or a bad migration) can write directly around app-level checks in ways that are harder to do with a real DB constraint.

One naming note worth keeping for its own sake: I call the transliteration field `reading` (not `pinyin`, not `furigana`), specifically so the same column name works across languages and the UI picks the label. Keep the same convention if you want later multi-language support (the PRD's reading field already exists for exactly this reason, so you're already aligned).

---

## 3. Sample deck seeding — pattern reusable, current content isn't

The seeding logic, in full, because it's short and the shape is worth copying:

```python
def seed_sample_deck_for_user(user_id, language='ZH'):
    if user_has_sample_deck(user_id, language):   # idempotency check by deck name
        return None
    try:
        words_data = load_sample_words_from_csv(language)
        if not words_data:
            return None   # silent no-op if CSV missing — never blocks signup
        deck = Deck(name=..., description=..., language=language, user_id=user_id)
        db.session.add(deck)
        db.session.flush()          # get deck.id before creating words
        word_objects = [Word(..., deck_id=deck.id, repetitions=0, interval_days=1, ease_factor=2.5) for wd in words_data]
        db.session.add_all(word_objects)
        db.session.commit()
        return deck
    except Exception:
        db.session.rollback()       # seeding failure never breaks account creation
        return None
```

Three things worth carrying over as pattern:
- **Idempotency check before seeding** (skip if a deck with that exact name already exists for the user) — cheap and prevents duplicate sample decks if seeding ever gets triggered twice.
- **Silent skip on missing content, never a hard failure.** Seeding a sample deck is a nice-to-have, not core function; treat it that way in your error handling. For you this matters even more, since your onboarding flow (§10 of your PRD) explicitly depends on "sample deck exists" to get the user to their first alarm within a minute — but the failure mode should be "onboarding continues, deck is just empty" not "onboarding breaks."
- **Explicit transaction boundary around deck+words together.** Whatever your Room equivalent is (a single `@Transaction` DAO method), don't create the deck and then loop-insert cards outside that boundary — you want all-or-nothing.

What doesn't carry over: the content. My CSV (`swe_vocab_list.csv`) is Chinese software-engineering vocabulary (文档/Wéndàng/documentation, that kind of thing) — wrong language and wrong audience for what your PRD wants (a JLPT N5-adjacent Japanese starter deck). You're authoring that content fresh regardless of anything in my repo; there's no shortcut here, just don't go looking for one.

---

## 4. CSV import pattern — for your deferred Anki importer

This is scoped narrowly: you don't need this for MVP (manual entry only, per your PRD), but when you get to the deferred Anki `.apkg` import, this is the shape I'd reuse. The current implementation (PapaParse, client-side, in the React app):

```typescript
// 1. Parse with header:true — get field names as-authored
const parseResult = await Papa.parse(file, { header: true, skipEmptyLines: true })

// 2. Normalize headers for matching, but keep original casing for lookup
const headers = parseResult.meta.fields?.map(f => f.trim().toLowerCase())
const requiredColumns = ['word', 'meaning']
const missingColumns = requiredColumns.filter(col => !headers.includes(col))
const hasReading = headers.includes('reading')
const hasPinyin = headers.includes('pinyin')   // accept either column name — backward compat

// 3. Build a lowercase→original field map so we can pull values by normalized key
const fieldMap = {}
parseResult.meta.fields?.forEach(f => { fieldMap[f.trim().toLowerCase()] = f })

// 4. Map every row, then filter — don't reject the whole file for a few bad rows
const allRows = parseResult.data.map(row => ({
  word: row[fieldMap['word']], reading: row[fieldMap[hasReading ? 'reading' : 'pinyin']], meaning: row[fieldMap['meaning']],
}))
const validRows = allRows.filter(w => w.word && w.reading && w.meaning)
const skippedCount = allRows.length - validRows.length

// 5. Import what's valid, report the skip count as a warning, not a failure
await api.post('/api/words', validRows)
if (skippedCount > 0) onUploadWarning(`${skippedCount} row(s) were excluded due to missing data.`)
```

The two decisions worth keeping when you get to Anki import: **header matching should be case/whitespace-tolerant and accept known synonyms** (you'll want something similar for however Anki field names map to `front`/`back`/`reading`/`hint`), and **partial success beats all-or-nothing** — a user importing a 500-card Anki deck where three rows are malformed should get 497 cards and a warning, not a hard failure. This is a UX pattern to reimplement in Kotlin (file picker → parse → validate → partial-import-with-warning), not code to port — there's no PapaParse on Android, but the shape of the validation pipeline is exactly transferable.

---

That's the whole transfer. Everything else in your PRD — the alarm/foreground-service/full-screen-intent stack, TTS, the hint ladder, the escalation ladder, answer normalization — has no Laoshi precedent at all; I never built any of it, so there's nothing to hand off there beyond "good luck, that part's genuinely new."
