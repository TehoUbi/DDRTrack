# Changelog

## 1.1.0 (2025-09-27)

Scanning works on real DDR WORLD result screens.

- The stats panel is re-read from the full-resolution photo; its
  digits were getting lost when the whole cabinet photo was scaled down.
- Judgment counts OCR can't read are worked out from the score, EX score
  and step total ("MAX COMBO 140 / 417"), and a single misread count is
  corrected. Values that were worked out are marked on the review screen.
- The WORLD layout: FAST/SLOW under their labels, BEST SCORE, combo
  "a / b", stylised labels, tilted photos, stacked digits.
- The song title is taken from under the jacket instead of the player
  name or cabinet decals, and Japanese titles are read with ML Kit's
  Japanese model.
- Auto-capture locks on the score, EX score and step total, so it no
  longer waits for every small digit. Live analysis runs at 1080p.

## 1.0.0 (2025-08-31)

First release.

- Scan DDR A20 PLUS / A3 / WORLD result screens with the camera: live
  multi-frame reading, auto-capture, and repair of OCR misreads against
  the scoring rules. Screenshots can be imported from the gallery.
- Two-player result screens: reads your side.
- Editable review form; manual entry; edit and delete.
- History with search and filters, grouped by session.
- Progress: plays per week, weekly accuracy, comfort level, best clear
  per level, FAST/SLOW bias, per-chart score history.
- Leaderboard server (Ktor + PostgreSQL) with chart rankings and rival
  head-to-heads; background upload with offline queueing.
- Optional Firebase backup and restore of plays and sessions.
- CSV export, JSON backup and restore.
