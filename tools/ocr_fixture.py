#!/usr/bin/env python3
"""Turn a photo of a real result screen into parser test fixtures.

    uv venv --python 3.12 .ocr && uv pip install --python .ocr/bin/python rapidocr-onnxruntime pillow
    .ocr/bin/python tools/ocr_fixture.py photo.jpg core/src/test/resources/realscreens/name

ML Kit only runs on a phone, so the fixtures come from RapidOCR (the PP-OCR
models on onnxruntime), which fails in similar ways: small isolated digits
go missing, stylised labels come back as "MOTS" or "iss", and stacked
single digits get merged. That is what the parser has to survive.

Writes two files, mirroring the app's two-pass scan:
  name.json        the whole photo, long side scaled to 2560 px
  name-panel.json  the stats panel cropped from the full-resolution photo,
                   with the crop rectangle in page coordinates

Only recognised text and boxes are written; the photo is not copied.
"""
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageOps
from rapidocr_onnxruntime import RapidOCR

PAGE_SIDE = 2560
LABEL = re.compile(r"(marvelous|perfect|great|good|o\.?k\.?|miss|exscore|maxcombo|bestscore|score)\W*", re.I)


def ocr(engine, image):
    tmp = Path("/tmp/ocr_fixture_input.png")
    image.save(tmp)
    result, _ = engine(str(tmp))
    lines = []
    for quad, text, conf in result or []:
        xs = [p[0] for p in quad]
        ys = [p[1] for p in quad]
        (x0, y0), (x1, y1) = quad[0], quad[1]
        lines.append({
            "text": text,
            "box": [round(min(xs)), round(min(ys)), round(max(xs)), round(max(ys))],
            "angle": round(math.degrees(math.atan2(y1 - y0, x1 - x0)), 2),
            "confidence": round(float(conf), 3),
        })
    return lines


def main(photo, out_prefix):
    engine = RapidOCR()
    full = ImageOps.exif_transpose(Image.open(photo)).convert("RGB")
    scale = min(1.0, PAGE_SIDE / max(full.size))
    page = full.resize((round(full.width * scale), round(full.height * scale)), Image.LANCZOS)
    lines = ocr(engine, page)
    meta = {"source": Path(photo).name, "engine": "RapidOCR (PP-OCR, onnxruntime)"}
    Path(out_prefix + ".json").write_text(json.dumps(
        {**meta, "width": page.width, "height": page.height, "lines": lines}, ensure_ascii=False, indent=1))

    # Stats panel: the rows holding the table labels, padded, full width of their content.
    labels = [l for l in lines if LABEL.fullmatch(l["text"].replace(" ", ""))]
    if not labels:
        print("no stats labels found; skipping panel")
        return
    top = min(l["box"][1] for l in labels)
    bottom = max(l["box"][3] for l in labels)
    row = (bottom - top) / 8
    in_rows = [l for l in lines if l["box"][1] >= top - row and l["box"][3] <= bottom + row]
    left = max(0, min(l["box"][0] for l in labels) - row)
    right = min(page.width, max(l["box"][2] for l in in_rows) + row)
    region = [round(left), round(max(0, top - row)), round(right), round(min(page.height, bottom + row))]
    crop = full.crop(tuple(round(v / scale) for v in region))
    panel = ocr(engine, crop)
    Path(out_prefix + "-panel.json").write_text(json.dumps(
        {**meta, "region": region, "width": crop.width, "height": crop.height, "lines": panel}, ensure_ascii=False, indent=1))
    print(f"{out_prefix}: {len(lines)} lines, panel {region} -> {len(panel)} lines")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
