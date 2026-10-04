"""Tiles rendered flags into one image for review by eye, our drawing beside the independent one.

    python3 tools/contact-sheet.py --out sheet.png [--in out] [--ref ref] \
        [--seed content/seed/europe.json | --continent Europe | XK TW ...]

Each tile is labelled "XK Kosovo" (names from the seed file or the catalog). When ref/<code>.png exists
(render-flags.mjs --source cfi) it is drawn to the right of ours, so a wrong or outdated flag stands out.
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import entries  # noqa: E402


def flatten(image):
    """RGB with fully transparent pixels turned white (Nepal's surround), as tools/to-webp.py does."""
    rgba = image.convert('RGBA')
    opaque = rgba.getchannel('A').point(lambda a: 255 if a > 0 else 0)
    return Image.composite(rgba.convert('RGB'), Image.new('RGB', rgba.size, (255, 255, 255)), opaque)

ap = argparse.ArgumentParser()
ap.add_argument('codes', nargs='*')
ap.add_argument('--out', required=True)
ap.add_argument('--in', dest='src', default='out')
ap.add_argument('--ref', default='ref')
ap.add_argument('--seed')
ap.add_argument('--continent')
a = ap.parse_args()

names = {e['code']: e['name'] for e in entries()}
if a.seed:
    seed = json.loads(Path(a.seed).read_text())['entries']
    names.update({e['code']: e['name'] for e in seed})
    codes = [e['code'] for e in seed]
elif a.continent:
    codes = [e['code'] for e in entries(a.continent)]
else:
    codes = [c.upper() for c in a.codes]
codes = [c.upper() for c in a.codes] or codes

W, H, PAD, COLS = 200, 150, 10, 3
with_ref = Path(a.ref).exists()
tile_w = W * 2 + PAD if with_ref else W
rows = (len(codes) + COLS - 1) // COLS
sheet = Image.new('RGB', (COLS * (tile_w + PAD) + PAD, rows * (H + 30) + PAD), (245, 245, 245))
draw = ImageDraw.Draw(sheet)
font = ImageFont.load_default(size=14)
for i, code in enumerate(codes):
    x = PAD + (i % COLS) * (tile_w + PAD)
    y = PAD + (i // COLS) * (H + 30)
    ours = Path(a.src) / f'{code.lower()}.png'
    if ours.exists():
        sheet.paste(flatten(Image.open(ours)).resize((W, H), Image.LANCZOS), (x, y))
    else:
        draw.rectangle((x, y, x + W, y + H), outline=(200, 0, 0), width=3)
    ref = Path(a.ref) / f'{code.lower()}.png'
    if with_ref and ref.exists():
        img = flatten(Image.open(ref))
        img = img.resize((W, int(W * img.height / img.width)), Image.LANCZOS)
        sheet.paste(img, (x + W + PAD, y + (H - img.height) // 2))
    draw.text((x, y + H + 6), f"{code} {names.get(code, '?')}", fill=(0, 0, 0), font=font)
sheet.save(a.out)
print(f'{len(codes)} flags -> {a.out} ({sheet.width}x{sheet.height}, ref {"shown" if with_ref else "absent"})')
