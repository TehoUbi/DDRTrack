#!/usr/bin/env python3
"""Generate the sample data in samples/.

    python3 tools/make_samples.py            # sample-backup.json, demo-players.json
    python3 tools/make_samples.py --screens  # also synthetic result screens (needs Pillow)

- samples/sample-backup.json: a year of plausible plays (to the day before
  END below) for Settings > Restore from a file, so history, progress,
  sessions and chart pages have something in them. Real charts at their
  real levels, from the bundled song list, including Japanese titles.
- samples/demo-players.json: fake rivals for the leaderboard server's
  `--seed-demo`, with scores on many of the same charts, so the compare
  and leaderboard screens have head-to-heads to show.
- samples/result-*.png (--screens): synthetic result screens laid out like
  DDR A3 / WORLD. Drawings, not game captures; the real photos in samples/
  are better for testing the scanner.

Everything is seeded, so re-running gives byte-identical output.
"""
import hashlib
import json
import math
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "samples"
SONGS = ROOT / "core/src/main/resources/com/tehoubi/ddrtracker/core/songs/songs.tsv"
FONT_DIRS = [Path("/usr/share/fonts/truetype/dejavu"), Path("/Library/Fonts"), Path("C:/Windows/Fonts")]


def font(bold, size):
    from PIL import ImageFont
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
    from PIL import Image, ImageDraw
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
    from PIL import Image, ImageDraw
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


# --- Sample backup and demo players -----------------------------------------

END = datetime(2026, 11, 4, tzinfo=timezone.utc)
DIFFS = ["BEGINNER", "BASIC", "DIFFICULT", "EXPERT", "CHALLENGE"]
# The songs in the sample photos, so scanning them lands on charts with history.
PHOTO_CHARTS = [("Scarlet Moon", "EXPERT"), ("向日葵サンセット", "EXPERT"), ("ブタサンダー", "EXPERT"),
                ("TRIP MACHINE EVOLUTION", "CHALLENGE"), ("アイスクリームマジック", "DIFFICULT")]


def chart_pool(rnd):
    """Single charts from Difficult to Challenge at levels 8-17, about 200 of them."""
    charts = {}
    for line in SONGS.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        title, _, levels = line.split("\t")[:3]
        levels = [int(n) for n in levels.split(",")] if levels else []
        for d in (2, 3, 4):
            if len(levels) > d and 8 <= levels[d] <= 17:
                charts[(title, DIFFS[d])] = levels[d]
    keys = sorted(charts)
    rnd.shuffle(keys)
    picked = set(keys[:200]) | {c for c in PHOTO_CHARTS if c in charts}
    return [(t, d, charts[(t, d)]) for t, d in sorted(picked)]


def chart_shape(title, difficulty, level):
    """Steps and freeze arrows, fixed per chart so every player sees the same counts."""
    h = int(hashlib.sha1(f"{title}/{difficulty}".encode()).hexdigest(), 16)
    return 110 + level * 32 + h % 90, (h >> 8) % (6 + level * 2)


def play(rnd, title, difficulty, level, skill, at, bias):
    """One attempt. skill 0..1 maps to the level a player is comfortable at (8 to 18)."""
    steps, freezes = chart_shape(title, difficulty, level)
    gap = level - (8 + skill * 10)  # above zero: harder than they're used to
    marv_share = min(0.93, max(0.1, 0.62 - 0.09 * gap + rnd.gauss(0, 0.07)))
    bad_share = min(0.6, 0.09 * math.exp(0.45 * gap) + abs(rnd.gauss(0, 0.015)))
    bad = max(1, min(int(steps * bad_share), int(steps * (1 - marv_share))))
    clean = rnd.random() < 1 / (1 + math.exp(1.3 * (gap + 1.5)))  # no misses
    miss = 0 if clean else max(1, int(bad * rnd.uniform(0.1, 0.3)))
    good = 0 if clean and rnd.random() < 0.5 else int(bad * rnd.uniform(0.05, 0.15))
    great = max(0, bad - miss - good)
    marv = int(steps * marv_share)
    perfect = steps - marv - great - good - miss
    ng = 0 if clean else min(freezes, int(rnd.random() * (1 + max(0, gap)) * 2))
    ok = freezes - ng
    failed = gap > 1.0 and rnd.random() < 0.18 * gap
    score = money_score(marv, perfect, great, good, ok, miss, ng)
    if failed:
        score = score * rnd.randint(40, 75) // 100 // 10 * 10
    timed = perfect + great + good
    fast = min(timed, max(0, int(timed * (bias + rnd.gauss(0, 0.06)))))
    combo = steps if miss == 0 else max(1, int(steps * rnd.uniform(0.1, 0.8)))
    return {
        "songTitle": title, "style": "SINGLE", "difficulty": difficulty, "level": level,
        "score": score, "exScore": ex_score(marv, perfect, great, good, ok, miss),
        "marvelous": marv, "perfect": perfect, "great": great, "good": good, "ok": ok, "miss": miss,
        "maxCombo": combo, "fast": fast, "slow": timed - fast, "failed": failed, "playedAt": at,
    }


def sample_backup(path, pool):
    """You, over the last year: twice a week or so, improving from about 12s to 15s."""
    rnd = random.Random(2026)
    start = END - timedelta(days=365)
    favourites = rnd.sample(pool, 40)
    plays = []
    day = start
    while day < END - timedelta(days=1):
        day += timedelta(days=rnd.choice([1, 2, 2, 3, 3, 4, 6]))
        if rnd.random() < 0.08:  # the odd week off
            day += timedelta(days=rnd.randint(5, 12))
        if day >= END:
            break
        progress = (day - start).days / 365
        skill = 0.42 + 0.3 * progress + rnd.gauss(0, 0.02)
        t = day.replace(hour=rnd.choice([1, 2, 3, 19, 20, 21, 22, 23]), minute=rnd.randint(0, 59))
        for _ in range(rnd.randint(7, 16)):
            reach = 8 + skill * 10 + 1.5
            choices = [c for c in (favourites if rnd.random() < 0.45 else pool) if c[2] <= reach]
            title, diff, level = rnd.choice(choices)
            # Timing drifts early over the last few weeks (a new cabinet, say),
            # enough for the app to suggest an offset change.
            bias = 0.42 if (END - day).days > 21 else 0.72
            p = play(rnd, title, diff, level, skill, t, bias=bias)
            score = p["score"]
            plays.append({
                "id": f"sample-{len(plays):04d}", **{k: v for k, v in p.items() if k != "playedAt"},
                "grade": "E" if p["failed"] else GRADE_NAMES[grade(score)],
                "lamp": "FAILED" if p["failed"] else lamp(p["marvelous"], p["perfect"], p["great"], p["good"], p["ok"], p["miss"]),
                "playedAt": t.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "source": rnd.choice(["CAMERA", "CAMERA", "CAMERA", "CAMERA", "SCREENSHOT", "MANUAL"]),
            })
            t += timedelta(minutes=rnd.randint(3, 8))
    backup = {"format": "ddr-tracker-backup", "version": 1, "exportedAt": END.strftime("%Y-%m-%dT%H:%M:%SZ"), "plays": plays}
    path.write_text(json.dumps(backup, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return len(plays)


DEMO_PLAYERS = [("Kaito", 0.95), ("Mika", 0.88), ("Ren", 0.82), ("Sora", 0.76),
                ("Hana", 0.70), ("Yuki", 0.64), ("Taro", 0.56), ("Aoi", 0.48)]


def demo_players(path, pool):
    """Rivals above and below you, each with bests on many of the same charts."""
    rnd = random.Random(7)
    start = END - timedelta(days=365)
    players = []
    for name, skill in DEMO_PLAYERS:
        reach = 8 + skill * 10 + 1
        charts = [c for c in pool if c[2] <= reach and rnd.random() < 0.6]
        plays = []
        for title, diff, level in charts:
            for _ in range(rnd.choice([1, 1, 2, 2, 3, 4])):
                at = start + timedelta(minutes=rnd.randint(0, 365 * 24 * 60 - 60 * 24))
                p = play(rnd, title, diff, level, skill + rnd.gauss(0, 0.02), at, bias=rnd.uniform(0.3, 0.6))
                del p["fast"], p["slow"]
                p["playedAt"] = int(at.timestamp() * 1000)
                plays.append({"clientId": f"seed-{len(plays)}", **p})
        players.append({"name": name, "plays": plays})
    path.write_text(json.dumps({"players": players}, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return sum(len(p["plays"]) for p in players)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    pool = chart_pool(random.Random(118))
    n = sample_backup(OUT / "sample-backup.json", pool)
    d = demo_players(OUT / "demo-players.json", pool)
    print(f"{len(pool)} charts; backup with {n} plays; {len(DEMO_PLAYERS)} demo players with {d} plays")
    if "--screens" in sys.argv:
        result_screen(OUT / "result-butterfly-pfc.png", "Butterfly", "SMiLE.dk", "EXPERT", 12, (452, 12, 0, 0, 34, 0), 5, 7)
        result_screen(OUT / "result-paranoia-clear.png", "PARANOiA", "180", "EXPERT", 14, (318, 61, 14, 3, 30, 4), 11, 23)
        result_screen(OUT / "result-max300-gfc.png", "MAX 300", "Ω", "EXPERT", 15, (402, 88, 21, 0, 25, 0), 30, 9)
        two_player_screen(OUT / "result-two-player.png")
        print("drew samples/result-*.png")
