# Changelog

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
