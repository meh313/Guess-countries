#!/usr/bin/env python3
"""Writes the launcher icon, its themed outline and the pin the store graphics use, from the icon's master.

    python3 tools/gen-app-icon.py

The master, assets/app-icon-master.webp, is the whole icon as designed: an origami map pin on a navy square.
Everything is cut from it, so a new master means running this again and nothing else:

- app/src/main/res/mipmap-<density>/ic_launcher_foreground.webp and ic_launcher_background.webp: the two layers
  of the adaptive icon (Android 8 and later), 108 dp square. The master's square becomes the 72 dp the launcher
  shows, so the pin keeps the size and place it has in the master and stays inside the 66 dp every mask keeps.
  The foreground is the pin alone; the background is the master with the pin painted out (its navy, the glow in
  the pin's hole and the shadow under its tip), drawn on past the master's edges for the 18 dp a launcher may
  move it by.
- mipmap-<density>/ic_launcher.webp and ic_launcher_round.webp: the icons of Android 7, which has no adaptive
  icons: the master in a 44 dp rounded square (corners at 21% of its side, as before) or circle, on 48 dp.
- app/src/main/res/drawable/ic_launcher_monochrome.xml: the themed icon (Android 13 and later), a vector the
  launcher recolours. It is the pin's outline, each colour of the origami a piece of its own with a gap between
  them, so the swirl of the folds still shows in one colour.
- assets/app-icon-pin.webp: the pin alone, 1024 px, on transparency, framed like the master, which
  StoreGraphicsTest draws on the feature graphic.

The pin is told from the navy by colour: a pixel brighter than the navy around it is the pin, and the navy is
estimated from the pixels that are certainly navy (dark and blue). Lossy WebP (quality 92, alpha kept exact), so
running it twice can differ in the last bits; there is no --check. Besides Pillow it needs numpy, scipy and
scikit-image (pip install numpy scipy scikit-image), which CI does not install.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
from skimage import measure

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO  # noqa: E402

MASTER = REPO / 'assets/app-icon-master.webp'
PIN = REPO / 'assets/app-icon-pin.webp'
RES = REPO / 'app/src/main/res'
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
QUALITY = 92
LEGACY_CORNER = 0.21  # of the 44 dp square's side, the corner of the icons this replaces
GAP_DP = 1.25  # between the pieces of the themed outline, in the 108 dp of the layer
# A piece of the themed outline is dropped when nothing in it is wider than PIECE_DP (a sliver of a fold), and so is
# any point of a piece narrower than NARROW_DP that runs more than SPIKE_DP beyond the rest (a fold's tail), since
# in one colour they read as noise, not as folds.
PIECE_DP, NARROW_DP, SPIKE_DP = 4.0, 1.8, 2.5

# The origami's four colours, for the themed outline's pieces.
YELLOW, WHITE, ORANGE, BLUE = 1, 2, 3, 4


def load():
    image = Image.open(MASTER).convert('RGB')
    assert image.width == image.height, image.size
    return np.asarray(image).astype(np.float32)


def smooth_fill(rgb, known, sigma):
    """The colour of each pixel guessed from the known pixels near it (normalised Gaussian convolution), widening
    the search where none are close, so a region with no known pixels gets the colour of its surroundings."""
    out = None
    for s in (sigma, sigma * 4, sigma * 16):
        weight = ndi.gaussian_filter(known.astype(np.float32), s)
        guess = ndi.gaussian_filter(rgb * known[..., None], sigma=(s, s, 0)) / np.maximum(weight, 1e-6)[..., None]
        if out is None:
            out, have = guess, weight > 0.05
        else:
            out[~have] = guess[~have]
            have |= weight > 0.05
    return out


def pin_mask(rgb):
    n = rgb.shape[0]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    navy = (r < 30) & (g < 64) & (b < 114)  # the navy, its glow and the shadow; of the pin, only its deepest creases
    lift = rgb - smooth_fill(rgb, navy, n / 85)
    pin = (lift[..., 2] > 14) | (lift[..., 1] > 14) | (lift[..., 0] > 30)
    pin = ndi.binary_opening(pin, structure=np.ones((5, 5)))
    labels, count = ndi.label(pin)
    sizes = ndi.sum(pin, labels, range(1, count + 1))
    pin = labels == 1 + int(np.argmax(sizes))
    # Dark creases between folds read as navy; any enclosed gap smaller than the pin's hole is one.
    gaps, count = ndi.label(ndi.binary_fill_holes(pin) & ~pin)
    sizes = ndi.sum(gaps > 0, gaps, range(1, count + 1))
    for i, size in enumerate(sizes, start=1):
        if size < 0.01 * n * n:
            pin |= gaps == i
    return pin


def colours(rgb, pin):
    """Each pin pixel's origami colour; shading and crease pixels take their nearest colour's."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    top, bottom = rgb.max(-1), rgb.min(-1)
    saturation = (top - bottom) / np.maximum(top, 1)
    blue = b > r + 25
    white = (saturation < 0.22) & (top > 120) & ~blue
    yellow = (g > 0.55 * r) & (g > 1.6 * b) & (r > 0.9 * g) & ~white
    orange = (g <= 0.55 * r) & (r > 1.5 * b) & ~white
    kind = np.zeros(pin.shape, np.uint8)
    for value, where in ((YELLOW, yellow), (WHITE, white), (ORANGE, orange), (BLUE, blue)):
        kind[pin & where] = value
    nearest = ndi.distance_transform_edt(kind == 0, return_distances=False, return_indices=True)
    kind = ndi.median_filter(kind[tuple(nearest)], size=9)
    kind[~pin] = 0
    return kind


def on_layer(image, mode):
    """The master's square centred on the 108 dp layer, half as wide again: its middle 72 dp are what launchers show."""
    pad = int(round(image.shape[0] / 4))
    return np.pad(image, ((pad, pad), (pad, pad), (0, 0)), mode=mode)


def save(array, path, mode):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(np.rint(array), 0, 255).astype(np.uint8), mode).save(path, 'WEBP', quality=QUALITY, method=6)


def resized_rgba(rgba, side):
    """Premultiplied resize, so the navy under the pin's edge neither darkens nor lightens it."""
    image = Image.fromarray(rgba.astype(np.uint8), 'RGBA').convert('RGBa').resize((side, side), Image.LANCZOS)
    return np.asarray(image.convert('RGBA')).astype(np.float32)


def layers(rgb, pin):
    background = rgb.copy()
    under = ndi.binary_dilation(pin, iterations=3)
    background[under] = smooth_fill(rgb, ~under, rgb.shape[0] / 85)[under]
    background = on_layer(background, 'edge')
    alpha = np.where(pin, 255, 0).astype(np.float32)
    foreground = on_layer(np.dstack([rgb, alpha]), 'constant')
    for density, scale in DENSITIES.items():
        side = int(round(108 * scale))
        folder = RES / f'mipmap-{density}'
        bg = Image.fromarray(np.clip(np.rint(background), 0, 255).astype(np.uint8)).resize((side, side), Image.LANCZOS)
        save(np.asarray(bg), folder / 'ic_launcher_background.webp', 'RGB')
        save(resized_rgba(np.clip(np.rint(foreground), 0, 255), side), folder / 'ic_launcher_foreground.webp', 'RGBA')


def legacy(rgb):
    master = Image.fromarray(np.clip(np.rint(rgb), 0, 255).astype(np.uint8))
    for density, scale in DENSITIES.items():
        side, inset = int(round(48 * scale)), int(round(2 * scale))
        shape = side - 2 * inset
        tile = master.resize((shape, shape), Image.LANCZOS)
        for name in ('ic_launcher', 'ic_launcher_round'):
            k = 8  # the mask drawn 8 times larger and scaled down, for smooth corners
            mask = Image.new('L', (side * k, side * k), 0)
            box = (inset * k, inset * k, (side - inset) * k - 1, (side - inset) * k - 1)
            if name == 'ic_launcher':
                ImageDraw.Draw(mask).rounded_rectangle(box, radius=LEGACY_CORNER * shape * k, fill=255)
            else:
                ImageDraw.Draw(mask).ellipse(box, fill=255)
            mask = mask.resize((side, side), Image.BOX)
            icon = Image.new('RGBA', (side, side), (0, 0, 0, 0))
            icon.paste(tile, (inset, inset))
            icon.putalpha(mask)
            save(np.asarray(icon), RES / f'mipmap-{density}' / f'{name}.webp', 'RGBA')


def trimmed(piece, narrow, spike):
    """The piece without its points: the parts narrower than `narrow` that reach more than `spike` from the rest."""
    body = ndi.distance_transform_edt(piece) > narrow / 2
    near = ndi.distance_transform_edt(~body) <= narrow / 2
    reach = ndi.distance_transform_edt(~near)
    points, count = ndi.label(piece & ~near)
    for i in range(1, count + 1):
        point = points == i
        if reach[point].max() > spike:
            piece &= ~point
    return piece


def outline(pin, kind):
    """The themed icon's pieces as polygons in the layer's 108 units: one per patch of one colour, each shrunk by
    half the gap, dropped or trimmed when thin, traced and simplified to the straight folds the origami is made of."""
    n = pin.shape[0]
    per_unit = n / 72  # master pixels per unit of the 108-unit layer
    shrink = GAP_DP * per_unit / 2
    pieces = np.zeros_like(pin)
    for value in (YELLOW, WHITE, ORANGE, BLUE):
        labels, count = ndi.label(kind == value)
        for i in range(1, count + 1):
            piece = ndi.distance_transform_edt(labels == i) > shrink
            if piece.any() and ndi.distance_transform_edt(piece).max() * 2 >= PIECE_DP * per_unit:
                pieces |= trimmed(piece, NARROW_DP * per_unit, SPIKE_DP * per_unit)
    polygons = []
    for contour in measure.find_contours(np.pad(pieces.astype(np.float32), 1), 0.5):
        if len(contour) < 20:
            continue
        points = measure.approximate_polygon(contour - 1, tolerance=n / 400)[:-1]
        polygons.append([(18 + (x + 0.5) / per_unit, 18 + (y + 0.5) / per_unit) for y, x in points])
    return polygons


def number(value):
    """A path coordinate to a tenth of a unit, under half a pixel at the 432 px of the largest launcher icon."""
    return f'{value:.1f}'.rstrip('0').rstrip('.')


def monochrome(polygons):
    # Implicit line-tos and one decimal keep the path under the 800 characters lint warns at.
    data = ''.join('M' + ' '.join(f'{number(x)},{number(y)}' for x, y in polygon) + 'Z' for polygon in polygons)
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Themed (monochrome) launcher icon: the origami pin's outline, each of its colours a piece of its own, traced
     from assets/app-icon-master.webp by tools/gen-app-icon.py. The launcher recolours it. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF000000"
        android:fillType="evenOdd"
        android:pathData="{data}" />
</vector>
'''
    (RES / 'drawable/ic_launcher_monochrome.xml').write_text(xml)


def store_pin(rgb, pin):
    rgba = np.dstack([rgb, np.where(pin, 255, 0).astype(np.float32)])
    save(resized_rgba(np.clip(np.rint(rgba), 0, 255), 1024), PIN, 'RGBA')


def main():
    rgb = load()
    pin = pin_mask(rgb)
    layers(rgb, pin)
    legacy(rgb)
    monochrome(outline(pin, colours(rgb, pin)))
    store_pin(rgb, pin)
    print(f'pin: {pin.mean():.1%} of the master; wrote {len(DENSITIES) * 4} launcher images, the themed outline and {PIN.name}')


if __name__ == '__main__':
    main()
