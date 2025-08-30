#!/usr/bin/env python3
"""Generate the files in samples/.

    pip install pillow
    python3 tools/make_samples.py

- samples/result-*.png: synthetic result screens laid out like DDR A3 /
  WORLD (labels left, values right, big score), for trying "From gallery"
  on the scan screen without a cabinet. They are drawings, not game
  captures.
- samples/sample-backup.json: about three months of plausible plays for
  Settings > Restore, so the history, progress and session views have
  something in them.

Everything is seeded, so re-running gives byte-identical output.
"""
import json
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "samples"
FONT_DIRS = [Path("/usr/share/fonts/truetype/dejavu"), Path("/Library/Fonts"), Path("C:/Windows/Fonts")]


def font(bold: bool, size: int) -> ImageFont.FreeTypeFont:
    names = ["DejaVuSans-Bold.ttf", "Arial Bold.ttf", "arialbd.ttf"] if bold else ["DejaVuSans.ttf", "Arial.ttf", "arial.ttf"]
    for d in FONT_DIRS:
        for n in names:
            if (d / n).exists():
                return ImageFont.truetype(str(d / n), size)
    raise SystemExit("No TrueType font found; install DejaVu fonts")


# --- DDR scoring, mirroring core's DdrScoring -------------------------------

def money_score(m, p, gr, gd, ok, miss, ng=0):
    objects = m + p + gr + gd + ok + miss + ng
    scaled = 1_000_000 * (m + ok + p) + 600_000 * gr + 200_000 * gd
    raw = scaled // objects - 10 * (p + gr + gd)
    return max(raw, 0) // 10 * 10


def ex_score(m, p, gr, gd, ok, miss):
    return 3 * m + 2 * p + gr + 3 * ok


GRADES = [("AAA", 990_000), ("AA+", 950_000), ("AA", 900_000), ("AA-", 890_000), ("A+", 850_000), ("A", 800_000),
          ("A-", 790_000), ("B+", 750_000), ("B", 700_000), ("B-", 690_000), ("C+", 650_000), ("C", 600_000),
          ("C-", 590_000), ("D+", 550_000), ("D", 0)]
GRADE_NAMES = {"AAA": "AAA", "AA+": "AA_PLUS", "AA": "AA", "AA-": "AA_MINUS", "A+": "A_PLUS", "A": "A", "A-": "A_MINUS",
               "B+": "B_PLUS", "B": "B", "B-": "B_MINUS", "C+": "C_PLUS", "C": "C", "C-": "C_MINUS", "D+": "D_PLUS", "D": "D"}


def grade(score):
    return next(label for label, floor in GRADES if score >= floor)


def lamp(m, p, gr, gd, ok, miss):
    if miss: return "CLEAR"
    if gd: return "FULL_COMBO"
    if gr: return "GREAT_FULL_COMBO"
    if p: return "PERFECT_FULL_COMBO"
    return "MARVELOUS_FULL_COMBO"


LAMP_TEXT = {"FULL_COMBO": "FULL COMBO", "GREAT_FULL_COMBO": "GREAT FULL COMBO",
             "PERFECT_FULL_COMBO": "PERFECT FULL COMBO", "MARVELOUS_FULL_COMBO": "MARVELOUS FULL COMBO"}

DIFF_COLORS = {"BEGINNER": "#6CE8FF", "BASIC": "#FFD84D", "DIFFICULT": "#FF5C7A", "EXPERT": "#6CF0A0", "CHALLENGE": "#C77DFF"}

# --- Result screens ---------------------------------------------------------


def draw_table(d, x, y, j, fast, slow):
    labels = ["MARVELOUS", "PERFECT", "GREAT", "GOOD", "O.K.", "MISS", "MAX COMBO", "EX SCORE"]
    m, p, gr, gd, ok, miss = j
    values = [m, p, gr, gd, ok, miss, m + p + gr + gd, ex_score(*j)]
    colors = ["#F2F6FF", "#FFD84D", "#6CF0A0", "#4FA3FF", "#FF9F43", "#FF5C5C", "#FFFFFF", "#FFFFFF"]
    lf, vf = font(True, 26), font(True, 28)
    for i, (label, value, color) in enumerate(zip(labels, values, colors)):
        row = y + i * 40
        d.text((x, row), label, fill=color, font=lf)
        d.text((x + 380, row), f"{value:04d}" if i < 6 else str(value), fill="#FFFFFF", font=vf, anchor="ra")
    d.text((x + 430, y), "FAST", fill="#6CB4FF", font=lf)
    d.text((x + 560, y), str(fast), fill="#FFFFFF", font=vf)
    d.text((x + 430, y + 40), "SLOW", fill="#FF8A8A", font=lf)
    d.text((x + 560, y + 40), str(slow), fill="#FFFFFF", font=vf)


def result_screen(path, title, artist, difficulty, level, j, fast, slow):
    img = Image.new("RGB", (1280, 720), "#0B0820")
    d = ImageDraw.Draw(img)
    for i in range(720):  # dim vertical gradient like the cabinet background
        d.line([(0, i), (1280, i)], fill=(11 + i // 30, 8, 32 + i // 20))
    d.rectangle([40, 30, 1240, 690], outline="#3B3270", width=3)
    d.rectangle([60, 50, 240, 230], fill="#2A2350")  # jacket placeholder
    d.text((150, 140), "♪", fill="#6C62B0", font=font(True, 90), anchor="mm")
    d.text((270, 60), title, fill="#FFFFFF", font=font(True, 48))
    d.text((272, 118), artist, fill="#B8B2E0", font=font(False, 26))
    d.rounded_rectangle([270, 165, 560, 210], radius=8, fill=DIFF_COLORS[difficulty])
    d.text((285, 170), f"{difficulty} {level}", fill="#0E0B1A", font=font(True, 30))
    d.text((580, 172), "SINGLE", fill="#B8B2E0", font=font(True, 26))
    s = money_score(*j)
    g = grade(s)
    d.text((1200, 70), g, fill="#FFD84D" if g == "AAA" else "#E6EAF2", font=font(True, 110), anchor="ra")
    d.text((1200, 200), f"{s:,}", fill="#FFFFFF", font=font(True, 64), anchor="ra")
    lp = lamp(*j)
    if lp in LAMP_TEXT:
        d.text((1200, 290), LAMP_TEXT[lp], fill="#6CF0A0", font=font(True, 30), anchor="ra")
    draw_table(d, 120, 330, j, fast, slow)
    img.save(path, optimize=True)
    return s


def two_player_screen(path):
    img = Image.new("RGB", (1280, 720), "#0B0820")
    d = ImageDraw.Draw(img)
    d.text((640, 40), "PARANOiA", fill="#FFFFFF", font=font(True, 44), anchor="ma")
    d.text((640, 100), "EXPERT 14", fill="#6CF0A0", font=font(True, 28), anchor="ma")
    sides = [(60, (300, 80, 10, 2, 30, 3)), (680, (250, 90, 35, 8, 28, 12))]
    lf, vf = font(True, 22), font(True, 24)
    for x, j in sides:
        d.text((x + 20, 160), "1P" if x < 640 else "2P", fill="#B8B2E0", font=font(True, 26))
        d.text((x + 520, 190), f"{money_score(*j):,}", fill="#FFFFFF", font=font(True, 50), anchor="ra")
        labels = ["MARVELOUS", "PERFECT", "GREAT", "GOOD", "O.K.", "MISS", "EX SCORE"]
        values = list(j) + [ex_score(*j)]
        for i, (label, value) in enumerate(zip(labels, values)):
            d.text((x + 20, 290 + i * 46), label, fill="#E6EAF2", font=lf)
            d.text((x + 520, 290 + i * 46), f"{value:04d}" if i < 6 else str(value), fill="#FFFFFF", font=vf, anchor="ra")
    d.line([(640, 150), (640, 680)], fill="#3B3270", width=2)
    img.save(path, optimize=True)


# --- Sample backup ----------------------------------------------------------

CHARTS = [
    ("Butterfly", "EXPERT", 12), ("B4U", "EXPERT", 12), ("AM-3P", "EXPERT", 12), ("CANDY☆", "DIFFICULT", 11),
    ("Xepher", "DIFFICULT", 13), ("DEAD END", "EXPERT", 13), ("SABER WING", "EXPERT", 13), ("PARANOiA", "EXPERT", 14),
    ("TRIP MACHINE CLIMAX", "EXPERT", 14), ("Afronova", "EXPERT", 14), ("MAX 300", "EXPERT", 15),
    ("Healing Vision ~Angelic mix~", "EXPERT", 15), ("PARANOiA Rebirth", "EXPERT", 15), ("Pluto Relinquish", "CHALLENGE", 16),
    ("ENDYMION", "EXPERT", 17), ("Valkyrie dimension", "CHALLENGE", 17),
]


def sample_backup(path):
    rnd = random.Random(118)
    start = datetime(2026, 2, 6, 19, 30, tzinfo=timezone.utc)
    plays = []
    for week in range(15):
        for visit in range(2 if week % 5 != 4 else 1):
            t = start + timedelta(days=week * 7 + visit * 3, minutes=rnd.randint(0, 90))
            for _ in range(rnd.randint(5, 11)):
                skill = 0.5 + week * 0.03
                # Harder charts get picked more as the player improves.
                pool = [c for c in CHARTS if c[2] <= 13 + week // 3]
                title, diff, level = rnd.choice(pool)
                notes = 260 + level * 24
                hard = max(0.05, 1.15 - skill + (level - 12) * 0.12)
                miss = int(rnd.random() * 14 * hard)
                good = int(rnd.random() * 7 * hard)
                great = int(rnd.random() * 45 * hard)
                perfect = int(notes * min(0.6, 0.1 + hard * 0.3) + rnd.randint(0, 12))
                ok = 18 + level
                marv = notes - miss - good - great - perfect
                j = (marv, perfect, great, good, ok, miss)
                failed = hard > 1.3 and rnd.random() < 0.25
                score = money_score(*j)
                if failed:
                    score = score * 6 // 10 // 10 * 10
                plays.append({
                    "id": f"sample-{len(plays):04d}",
                    "songTitle": title,
                    "style": "SINGLE",
                    "difficulty": diff,
                    "level": level,
                    "score": score,
                    "exScore": ex_score(*j),
                    "grade": "E" if failed else GRADE_NAMES[grade(score)],
                    "lamp": "FAILED" if failed else lamp(*j),
                    "marvelous": marv, "perfect": perfect, "great": great, "good": good, "ok": ok, "miss": miss,
                    "maxCombo": notes - miss * rnd.randint(8, 40) if miss else notes,
                    "fast": rnd.randint(0, 25), "slow": rnd.randint(0, 35),
                    "failed": failed,
                    "playedAt": t.strftime("%Y-%m-%dT%H:%M:%SZ"),
                    "source": rnd.choice(["CAMERA", "CAMERA", "CAMERA", "SCREENSHOT", "MANUAL"]),
                    "note": "sample data",
                })
                t += timedelta(minutes=rnd.randint(3, 7))
    for p in plays:
        p["maxCombo"] = max(0, p["maxCombo"])
    backup = {"format": "ddr-tracker-backup", "version": 1, "exportedAt": "2026-05-22T00:00:00Z", "plays": plays}
    path.write_text(json.dumps(backup, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return len(plays)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    s1 = result_screen(OUT / "result-butterfly-pfc.png", "Butterfly", "SMiLE.dk", "EXPERT", 12, (452, 12, 0, 0, 34, 0), 5, 7)
    s2 = result_screen(OUT / "result-paranoia-clear.png", "PARANOiA", "180", "EXPERT", 14, (318, 61, 14, 3, 30, 4), 11, 23)
    s3 = result_screen(OUT / "result-max300-gfc.png", "MAX 300", "Ω", "EXPERT", 15, (402, 88, 21, 0, 25, 0), 30, 9)
    two_player_screen(OUT / "result-two-player.png")
    n = sample_backup(OUT / "sample-backup.json")
    print(f"scores {s1:,} / {s2:,} / {s3:,}; backup with {n} plays")
