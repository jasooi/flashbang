# Architecture — Flashcard Alarm

Companion to [flashcard-alarm-prd.md](../flashcard-alarm-prd.md). This file holds the tech stack and data model so the PRD can stay focused on product intent.

---

## Tech stack

- **Kotlin + Jetpack Compose (Material 3)** — chosen over Flutter deliberately: every load-bearing component (foreground service, full-screen intent, exact alarms, boot receiver, overlay, audio focus, TTS lifecycle) is native Android API surface, and the UI footprint is small. Android-only removes Flutter's cross-platform rationale.
- **Room** (relational data), **DataStore** (settings).
- Platform **TextToSpeech** with on-device voices.
- **Firebase Analytics** for event collection.
- **No backend** in MVP.
- Min SDK ~26, target latest stable.

---

## Data model (Room, local)

- **Deck** (id, name, language, created_at)
- **Card** (id, deck_id, front, back, reading?, hint?, created_at)
- **CardProgress** (card_id, deck_id, ease_score, last_shown_at, last_outcome, counts: pre_hint / post_hint / wrong_attempts / snoozes / escapes) — keyed to deck, progress belongs to the deck so a card's ease state is consistent across all alarms using that deck.
- **Alarm** (id, deck_id, time, days_of_week, enabled, snooze_duration)
- **StreakState** (current_streak, best_streak, last_answered_date)
- Settings in **DataStore** (N, hint delay, defaults).

All local; no user accounts, no server-side user data in MVP.

---

## API endpoints

None in MVP — the app has no backend. Network is used only for opportunistic analytics flush (Firebase Analytics SDK); nothing at alarm time depends on the network.
