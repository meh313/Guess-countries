"""Converts rendered flag PNGs into the app's WebP flag resources.

    python3 tools/to-webp.py [--in out] [--lossy Q] [--seed content/seed/europe.json] [XK TW ...]

Codes come from the arguments, else the seed file, else every catalog entry. Input is <in>/<code>.png
(600x450, from render-flags.mjs); output is app/src/main/res/drawable-nodpi/flag_<code>.webp, lossless
unless --lossy is given (a smaller-APK fallback; checksums and colour anchors assume lossless).
"""
import argparse
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO, codes as catalog_codes, seed_codes  # noqa: E402

FLAGS = REPO / 'app/src/main/res/drawable-nodpi'

ap = argparse.ArgumentParser()
ap.add_argument('codes', nargs='*')
ap.add_argument('--in', dest='src', default='out')
ap.add_argument('--lossy', type=int, default=None, metavar='Q')
ap.add_argument('--seed')
a = ap.parse_args()
codes = [c.upper() for c in a.codes] or (seed_codes(a.seed) if a.seed else catalog_codes())

total = 0
for code in codes:
    image = Image.open(Path(a.src) / f'{code.lower()}.png').convert('RGB')  # drops the alpha channel
    assert image.size == (600, 450), (code, image.size)
    target = FLAGS / f'flag_{code.lower()}.webp'
    if a.lossy is None:
        image.save(target, 'WEBP', lossless=True, method=6)
    else:
        image.save(target, 'WEBP', quality=a.lossy, method=6)
    size = target.stat().st_size
    total += size
    print(f'{code} {size:>7,} bytes')
print(f'{len(codes)} flags, {total:,} bytes')
