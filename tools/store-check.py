#!/usr/bin/env python3
"""Checks the Play Store listing in store/ against Google Play's upload rules.

    python3 tools/store-check.py [--check]

store/screenshots/: JPEG or 24-bit PNG (8 bits a channel, RGB, no alpha channel and no transparent colour), each side
320 to 3,840 px and the long side at most twice the short one, at most 8 MB a file. The tablet_* files are the tablet
screenshots, each side at least 1,080 px; the rest are the phone ones. Play takes up to eight of each kind and needs
two to publish, so there are two to eight phone screenshots and at most eight tablet ones.
store/feature-graphic.png: 1,024 x 500 px, JPEG or 24-bit PNG, at most 15 MB.
store/icon-512.png: 512 x 512 px, 32-bit PNG (RGB with an alpha channel), at most 1,024 KB.
store/listing.md: the title at most 30 characters, each short description 80, the full description 4,000 and the
"What's new" text 500.
The rules are Play Console Help's ("Add preview assets to showcase your app", support.google.com/googleplay/
android-developer/answer/9866151) and the Console's own limits for its upload and text fields. The store* tests of
ScreenshotsTest and StoreGraphicsTest write the pictures.
Without --check the report is printed and the exit code is 0; with it, any problem exits 1 (CI runs it).
"""
import argparse
import re
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO  # noqa: E402

STORE = REPO / 'store'
SCREENSHOTS = STORE / 'screenshots'
FEATURE_GRAPHIC = STORE / 'feature-graphic.png'
ICON = STORE / 'icon-512.png'
LISTING = STORE / 'listing.md'
MB = 1024 * 1024
PNG_SIGNATURE = b'\x89PNG\r\n\x1a\n'
# PNG colour types (IHDR): 2 is RGB, 6 is RGB with an alpha channel; at 8 bits a channel, 24 and 32 bits a pixel.
RGB, RGBA = 2, 6
FORMATS = {'png24': '24-bit PNG', 'png32': '32-bit PNG, with an alpha channel', 'jpeg': 'JPEG'}


def image_format(path):
    """(kind, width, height) from the file's header: kind is a key of FORMATS, or what else the file is."""
    data = path.read_bytes()
    if data.startswith(b'\xff\xd8'):
        return ('jpeg',) + jpeg_size(data)
    if not data.startswith(PNG_SIGNATURE):
        return 'file that is no PNG or JPEG', 0, 0
    width, height, depth, colour = struct.unpack('>IIBB', data[16:26])
    transparent = b'tRNS' in png_chunks(data)
    if depth == 8 and colour == RGB and not transparent:
        return 'png24', width, height
    if depth == 8 and colour == RGBA:
        return 'png32', width, height
    extra = ' with a transparent colour' if transparent else ''
    return f'PNG of colour type {colour} at {depth} bits a channel{extra}', width, height


def png_chunks(data):
    """The chunk types of a PNG, in order."""
    out, at = [], len(PNG_SIGNATURE)
    while at + 8 <= len(data):
        length, kind = struct.unpack('>I4s', data[at:at + 8])
        out.append(kind)
        at += 12 + length
    return out


def jpeg_size(data):
    """(width, height) from a JPEG's first start-of-frame marker."""
    at = 2
    while at + 4 <= len(data):
        marker, length = data[at + 1], struct.unpack('>H', data[at + 2:at + 4])[0]
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack('>HH', data[at + 5:at + 9])
            return width, height
        at += 2 + length
    return 0, 0


def describe(path):
    """One report line: the file, its size in pixels, its format and its size in bytes."""
    kind, width, height = image_format(path)
    return f'{path.relative_to(REPO)}: {width}x{height} {FORMATS.get(kind, kind)}, {path.stat().st_size:,} bytes'


def check_screenshot(path, tablet):
    """The problems with one screenshot, each the end of a sentence that starts with the file's name."""
    kind, width, height = image_format(path)
    problems = []
    if kind not in ('png24', 'jpeg'):
        problems.append(f'is a {FORMATS.get(kind, kind)}; Play takes a JPEG or a 24-bit PNG')
    short, long = sorted((width, height))
    lowest = 1080 if tablet else 320
    if short < lowest or long > 3840:
        problems.append(f'is {width}x{height}; each side must be {lowest:,} to 3,840 px')
    if long > 2 * short:
        problems.append(f'is {width}x{height}, {long / short:.2f}:1; the long side can be at most twice the short one')
    if path.stat().st_size > 8 * MB:
        problems.append(f'is {path.stat().st_size:,} bytes; the limit is 8 MB')
    return problems


def check_graphic(path, kinds, size, limit):
    """The problems with the feature graphic or the icon; [kinds] are the formats Play takes for it."""
    if not path.exists():
        return ['is missing; Play needs it to publish the listing']
    kind, width, height = image_format(path)
    problems = []
    if kind not in kinds:
        wanted = ' or a '.join(FORMATS[k] for k in kinds)
        problems.append(f'is a {FORMATS.get(kind, kind)}; Play takes a {wanted}')
    if (width, height) != size:
        problems.append(f'is {width}x{height}; Play takes {size[0]}x{size[1]}')
    if path.stat().st_size > limit:
        problems.append(f'is {path.stat().st_size:,} bytes; the limit is {limit // 1024:,} KB')
    return problems


def listing_texts():
    """(name, text, limit) for each text of store/listing.md that Play limits; text is None when it is not found."""
    listing = LISTING.read_text()

    def find(pattern):
        match = re.search(pattern, listing, re.M | re.S)
        return match.group(1).strip() if match else None

    texts = [('title', find(r'^\*\*Title \(30\):\*\* (.*?)$'), 30),
             ('short description', find(r'^\*\*Short description \(80\):\*\* (.*?)$'), 80)]
    others = find(r'^\*\*Short description in the other store languages \(80\):\*\*\n(.*?)\n\n') or ''
    texts += [(f'short description ({language})', text, 80) for language, text in re.findall(r'^- (\w+): (.*)$', others, re.M)]
    texts.append(('full description', find(r'^\*\*Full description\*\*\n(.*?)\n\*\*What\'s new'), 4000))
    texts.append(("What's new", find(r'^\*\*What\'s new \([\d.]+\)\*\*\n(.*?)\n\n'), 500))
    return texts


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--check', action='store_true', help='exit 1 on any problem')
    args = parser.parse_args()
    problems = []
    shots = sorted(p for p in SCREENSHOTS.iterdir() if p.suffix.lower() in ('.png', '.jpg', '.jpeg'))
    tablet = [p for p in shots if p.name.startswith('tablet_')]
    phone = [p for p in shots if p not in tablet]
    for path in shots:
        print(describe(path))
        problems += [f'{path.relative_to(REPO)} {p}' for p in check_screenshot(path, path in tablet)]
    if not 2 <= len(phone) <= 8:
        problems.append(f'{SCREENSHOTS.relative_to(REPO)} has {len(phone)} phone screenshots; Play takes 2 to 8')
    if len(tablet) > 8:
        problems.append(f'{SCREENSHOTS.relative_to(REPO)} has {len(tablet)} tablet screenshots; Play takes up to 8')
    for path, kinds, size, limit in [(FEATURE_GRAPHIC, ('png24', 'jpeg'), (1024, 500), 15 * MB),
                                     (ICON, ('png32',), (512, 512), 1024 * 1024)]:
        if path.exists():
            print(describe(path))
        problems += [f'{path.relative_to(REPO)} {p}' for p in check_graphic(path, kinds, size, limit)]
    for name, text, limit in listing_texts():
        if text is None:
            problems.append(f'{LISTING.relative_to(REPO)} has no {name}')
            continue
        print(f'{LISTING.relative_to(REPO)} {name}: {len(text):,} of {limit:,} characters')
        if len(text) > limit:
            problems.append(f'{LISTING.relative_to(REPO)} {name} is {len(text):,} characters; Play takes {limit:,}')
    for p in problems:
        print(p, file=sys.stderr)
    if not problems:
        print(f"{len(phone)} phone and {len(tablet)} tablet screenshots, the feature graphic, the icon and the listing "
              "texts meet Play's rules")
    return 1 if problems and args.check else 0


if __name__ == '__main__':
    raise SystemExit(main())
