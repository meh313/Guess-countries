"""Generates FlagLookAlikes.kt: for every sovereign country, the flags people most often confuse with it.

    python3 tools/gen-lookalikes.py [--threshold 14.0] [--sheet /tmp/lookalikes] [--stats]

How: each shipped flag (app/src/main/res/drawable-nodpi/flag_xx.webp) is flattened onto white, shrunk to 16x12 and
converted to CIELAB. The distance of two flags is the mean per-pixel Lab distance, taking the smallest over the
other flag as it is, mirrored left-right, flipped upside down and turned a quarter (Ireland and Ivory Coast,
Indonesia and Poland, France and the Netherlands are the same flag in different positions). For each country the
list is: the curated classics first (CURATED, expanded in both directions), then the nearest others under THRESHOLD,
at most MAX_GENERATED generated and MAX_TOTAL in all. Deterministic: no randomness, ties broken by code.

--sheet DIR writes review strips (country, then its look-alikes) as PNGs so the result can be looked at by eye.
"""
import argparse
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO, entries  # noqa: E402

FLAGS = REPO / 'app/src/main/res/drawable-nodpi'
OUT = REPO / 'app/src/main/java/com/example/data/model/FlagLookAlikes.kt'
W, H = 16, 12
MAX_GENERATED = 5
MAX_TOTAL = 8

# Classic confusions, each group is mutually similar. Expanded in both directions.
CURATED = [
    'TD RO AD MD',          # blue-yellow-red vertical bands
    'ID MC PL',             # red over white
    'IE CI IT MX',          # green, white and orange or red bands
    'AU NZ',                # Union Jack and Southern Cross
    'NO IS DK SE FI',       # Nordic crosses
    'NL LU FR RU',          # red, white and blue bands
    'SI SK RU RS HR',       # Slavic red-white-blue
    'CO EC VE',             # Gran Colombia yellow-blue-red
    'GN ML SN CM',          # red, yellow and green
    'HN SV NI AR GT UY',    # Central American and Argentine blue-white-blue
    'BH QA',                # white with a serrated edge
    'EG IQ SY YE',          # red-white-black bands
    'TR TN',                # red with a white crescent
    'AT LV PE CA',          # red-white-red
    'BE DE',                # black, yellow and red
    'BG HU',                # white, green, red
    'JO PS SD KW AE',       # Pan-Arab colours
    'IN NE',                # saffron or orange, white, green
    'JP BD PW',             # a disc on a plain field
    'US LR MY',             # stripes with a star canton
    'CL CU CZ PH',          # a triangle or canton on bands of white, red and blue
    'CR TH',                # red, white and blue horizontal bands
]


def flatten(image):
    rgba = image.convert('RGBA')
    opaque = rgba.getchannel('A').point(lambda a: 255 if a > 0 else 0)
    return Image.composite(rgba.convert('RGB'), Image.new('RGB', rgba.size, (255, 255, 255)), opaque)


def srgb_to_lab(rgb):
    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    return (116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))


def lab_pixels(image):
    return [srgb_to_lab(p) for p in image.convert('RGB').getdata()]


def variants(small):
    """The flag as it is, mirrored, flipped and turned a quarter, each as a list of 16x12 Lab pixels."""
    turned = small.transpose(Image.Transpose.ROTATE_90).resize((W, H), Image.LANCZOS)
    return [
        lab_pixels(small),
        lab_pixels(small.transpose(Image.Transpose.FLIP_LEFT_RIGHT)),
        lab_pixels(small.transpose(Image.Transpose.FLIP_TOP_BOTTOM)),
        lab_pixels(turned),
    ]


def mean_distance(a, b):
    total = 0.0
    for (l1, a1, b1), (l2, a2, b2) in zip(a, b):
        total += math.sqrt((l1 - l2) ** 2 + (a1 - a2) ** 2 + (b1 - b2) ** 2)
    return total / len(a)


def load(codes):
    plain, var = {}, {}
    for code in codes:
        path = FLAGS / f'flag_{code.lower()}.webp'
        small = flatten(Image.open(path)).resize((W, H), Image.LANCZOS)
        plain[code] = lab_pixels(small)
        var[code] = variants(small)
    return plain, var


def distances(codes, plain, var):
    dist = {}
    for i, a in enumerate(codes):
        for b in codes[i + 1:]:
            d = min(mean_distance(plain[a], v) for v in var[b])
            dist[(a, b)] = dist[(b, a)] = d
    return dist


def curated_neighbours(codes):
    known = set(codes)
    near = {c: [] for c in codes}
    for group in CURATED:
        members = [c for c in group.split() if c in known]
        for c in members:
            for other in members:
                if other != c and other not in near[c]:
                    near[c].append(other)
    return near


def build(codes, dist, threshold):
    curated = curated_neighbours(codes)
    result = {}
    for code in codes:
        generated = sorted((o for o in codes if o != code and o not in curated[code] and dist[(code, o)] <= threshold),
                           key=lambda o: (dist[(code, o)], o))[:MAX_GENERATED]
        result[code] = (curated[code] + generated)[:MAX_TOTAL]
    return result


def kotlin(result):
    lines = ['package com.example.data.model', '',
             '/**',
             ' * For each sovereign country, the flags most often confused with it: curated classics first, then the',
             ' * nearest by image similarity. Generated by tools/gen-lookalikes.py from the shipped flag images; do not',
             ' * edit by hand, change CURATED in the script and regenerate.',
             ' */',
             'object FlagLookAlikes {',
             '    val similar: Map<String, List<String>> = mapOf(']
    items = [f'        "{code}" to listOf({", ".join(chr(34) + c + chr(34) for c in near)})' for code, near in sorted(result.items()) if near]
    lines.append(',\n'.join(items))
    lines += ['    )', '', '    /** Codes of the flags that look like [code], most alike first; empty when none does. */',
              '    fun of(code: String): List<String> = similar[code].orEmpty()', '}', '']
    return '\n'.join(lines)


def sheets(result, names, folder):
    folder.mkdir(parents=True, exist_ok=True)
    try:
        font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
    except OSError:
        font = ImageFont.load_default()
    tw, th = 84, 63
    codes = sorted(result)
    per_page = 28
    for page in range(0, len(codes), per_page):
        chunk = codes[page:page + per_page]
        img = Image.new('RGB', (9 * (tw + 8) + 100, len(chunk) * (th + 22) + 8), 'white')
        d = ImageDraw.Draw(img)
        for row, code in enumerate(chunk):
            y = 6 + row * (th + 22)
            for col, c in enumerate([code] + result[code]):
                x = 6 + col * (tw + 8)
                tile = flatten(Image.open(FLAGS / f'flag_{c.lower()}.webp')).resize((tw, th), Image.LANCZOS)
                img.paste(tile, (x, y))
                d.rectangle([x - 1, y - 1, x + tw, y + th], outline='black' if col == 0 else (170, 170, 170), width=2 if col == 0 else 1)
                d.text((x, y + th + 2), f'{c} {names[c]}'[:16], fill='black', font=font)
        img.save(folder / f'lookalikes-{page // per_page + 1:02d}.png')
    print(f'wrote {(len(codes) + per_page - 1) // per_page} review sheet(s) to {folder}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--threshold', type=float, default=14.0)
    ap.add_argument('--sheet')
    ap.add_argument('--stats', action='store_true')
    ap.add_argument('--out', default=str(OUT))
    args = ap.parse_args()

    people = [e for e in entries() if e['isSovereign']]
    codes = sorted(e['code'] for e in people)
    names = {e['code']: e['name'] for e in people}
    plain, var = load(codes)
    dist = distances(codes, plain, var)

    if args.stats:
        pairs = []
        for group in CURATED:
            members = [c for c in group.split() if c in names]
            for i, a in enumerate(members):
                for b in members[i + 1:]:
                    pairs.append((dist[(a, b)], a, b))
        pairs.sort()
        print('curated pair distances (smallest first):')
        for d, a, b in pairs:
            print(f'  {d:5.1f}  {a} {b}')
        everything = sorted(set((round(dist[(a, b)], 1), a, b) for a in codes for b in codes if a < b))
        print('closest 25 pairs overall:')
        for d, a, b in everything[:25]:
            print(f'  {d:5.1f}  {a} {b}  {names[a]} / {names[b]}')
        for t in (8, 10, 12, 14, 16, 18, 20):
            n = sum(1 for (a, b), d in dist.items() if a < b and d <= t)
            print(f'threshold {t}: {n} pairs')
        return

    result = build(codes, dist, args.threshold)
    Path(args.out).write_text(kotlin(result))
    with_any = sum(1 for v in result.values() if v)
    print(f'{args.out}: {with_any} of {len(codes)} countries have look-alikes; '
          f'average {sum(len(v) for v in result.values()) / len(codes):.1f}, max {max(len(v) for v in result.values())}')
    if args.sheet:
        sheets(result, names, Path(args.sheet))


if __name__ == '__main__':
    main()
