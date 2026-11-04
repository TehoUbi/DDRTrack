# Changelog

## Unreleased

Photos are read with a new OCR engine that reads Japanese titles.

- Photos and screenshots are read with PaddleOCR's PP-OCRv6 models on
  ONNX Runtime instead of ML Kit. One model reads kana, kanji and Latin,
  so titles like 向日葵サンセット and ブタサンダー come out as written.
  ML Kit still watches the live preview to decide when to take the photo.
- The photo's reading now decides; live preview frames only fill in what
  it couldn't read. Before, eight Latin-only preview frames could outvote
  the photo, which is why Japanese titles never showed up.
- The title band is read again at full resolution, and so is the
  difficulty banner when "SINGLE" / "DOUBLE" or the level is missing.
- The song list has every DDR song with its chart levels (about 1,500, up
  from 93). A title is matched knowing which chart was read, and partly
  read Japanese titles still match. Typing a romanized name finds the
  Japanese title.
- The song list refreshes itself weekly from 3icecream.com, so songs
  released after this build are recognised. Settings shows the count and
  has a "Check now" button.
- A missing FAST or SLOW count is worked out from the other
  (FAST + SLOW = Perfect + Great + Good).
- Fixes: a cabinet sticker saying "in the past" was read as the FAST
  label; SLOW could take the Good count from the next column on a tilted
  photo; "TRIP MACHINE" titles were thrown out as rival-list text.
- The APK is built for arm64 phones and x86_64 emulators.

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
