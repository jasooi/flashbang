# Phase 1 Manual Reliability Test Matrix (T-113)

Rerunnable checklist per NFR-001/002/005 and FR-018. Fill one column per run.
Devices: **E26** = emulator API 26, **E34** = emulator API 34, **N10** = Samsung Note 10 (Android 12).

Status legend: ✅ pass · ❌ fail (file a task) · ➖ not applicable on this device · ⬜ not yet run

| # | Scenario | E26 | E34 | N10 |
|---|---|---|---|---|
| 1 | Alarm fires on time with screen locked; challenge shows over lock screen | ⬜ | ⬜ | ⬜ |
| 2 | TTS reads the card reading (からい for 辛い), loops with gap | ⬜ | ⬜ | ⬜ |
| 3 | Volume ramps low → full over ~20 s | ⬜ | ⬜ | ⬜ |
| 4 | Vibration on by default; off after toggling `vibration_enabled` | ⬜ | ⬜ | ⬜ |
| 5 | Silent mode: alarm still audible (alarm stream) | ⬜ | ⬜ | ⬜ |
| 6 | DND on (alarms exception default): alarm still audible | ⬜ | ⬜ | ⬜ |
| 7 | Airplane mode, cold start to dismiss: full path works offline (FR-018) | ⬜ | ⬜ | ⬜ |
| 8 | Reboot → unlock → alarm still fires at original time (FR-003) | ⬜ | ⬜ | ⬜ |
| 9 | Manual clock change forward past alarm time → alarm reschedules sanely (FR-003b) | ⬜ | ⬜ | ⬜ |
| 10 | Timezone change → next fire recomputed (FR-003b) | ⬜ | ⬜ | ⬜ |
| 11 | Swipe app from recents mid-ring → audio continues; notification re-opens shell (FR-009) | ⬜ | ⬜ | ⬜ |
| 12 | Home press mid-ring, overlay opted-in + granted → challenge redisplays ≤1s (FR-011) | ⬜ | ⬜ | ⬜ |
| 13 | Home press mid-ring, overlay denied → no redisplay, audio + notification persist (FR-012) | ⬜ | ⬜ | ⬜ |
| 14 | Notification permission denied (API 33+): alarm still rings + full-screen (FR-010b) | ➖ | ⬜ | ➖ |
| 15 | Uninstall Google TTS / disable voice → fallback tone plays, shell still shows (FR-017) | ⬜ | ⬜ | ⬜ |
| 16 | Dismiss (dev) → sound + vibration stop, notification cleared, service gone (NFR-006) | ⬜ | ⬜ | ⬜ |
| 17 | Fire → first sound/frame latency subjectively <1s from locked (NFR-005) | ⬜ | ⬜ | ⬜ |
| 18 | Samsung battery: app in "sleeping apps" list → alarm still fires next morning (NFR-002) | ➖ | ➖ | ⬜ |
| 19 | Full-screen intent auto-grant behavior on API 34 (alarm-clock qualification) | ➖ | ⬜ | ➖ |

## Run log

| Date | Device | Build | Notes |
|---|---|---|---|
| — | — | — | Not yet executed: no device/emulator available on the build machine (see implementation-notes.md, Deviations) |
