#!/usr/bin/env python3
"""Generates the world map: app/src/main/assets/world.json (the outlines) and data/model/Geo.kt (the bounding boxes).

    python3 tools/gen-worldmap.py [--cache DIR] [--tolerance 0.05] [--check]

Source: Natural Earth (public domain), the 1:50m admin-0 countries and the 1:10m populated places, downloaded from
the GitHub mirror into --cache (default ~/.cache/natural-earth, outside the repository: the files are data, never run).
Every entry of the catalog (tools/catalog_codes.py: 196 states plus Antarctica) gets one country record, and nothing
else does: features are matched by ISO_A2_EH, then ISO_A2, then HAND_A3 for the rest (Somaliland and Northern
Cyprus are merged into Somalia and Cyprus). Western Sahara, the dependencies and the other non-catalog features get
no record: their rings go into "background", land drawn in the land colour that is no country (so Greenland or
Puerto Rico is not shown as sea), simplified like a country's. Each outer ring is simplified with Douglas-Peucker at
--tolerance degrees, keeps at least four points, and is dropped when its simplified area is under MIN_RING_AREA
square degrees unless it is the country's largest; coordinates are rounded to two decimals. A hole (an enclave:
Lesotho in South Africa) is kept as the enclave's own drawn ring, so both outlines are one line and a renderer that
fills with the even-odd rule leaves the enclave out of its host; a hole whose enclave is not drawn is dropped. A
country whose largest ring is under POINT_AREA square degrees (Monaco, the Vatican, the atoll states) is instead a
square of POINT_SIDE degrees around its capital, marked "point": true, so every code has a polygon. The label is Natural Earth's LABEL_X/LABEL_Y; the capital is the
populated-places "Admin-0 capital" whose name matches the catalog's quizCapital (accents, case and punctuation
folded; "Admin-0 capital alt" or any other place of the country when only that matches), else the first "Admin-0
capital", else the label point. A point square is labelled at its centre; a Natural Earth label that falls outside
the frame (a few island states; Kiribati's sits on Kiritimati) is replaced by the centroid of the largest ring.

The bbox is [west, south, east, north]. A country whose rings span more than 180 degrees of longitude (Russia, Fiji,
Kiribati, the United States, New Zealand) has it computed with the western hemisphere shifted by +360, so east may
exceed 180 and west < east always holds; the rings themselves stay as Natural Earth splits them at the antimeridian.
The frame is the box a renderer shows the country in: the bbox of the largest ring and of the ring holding the
capital (Malaysia's is not the largest), grown by every ring whose own bbox comes within FRAME_GAP degrees of it in both axes, again and again until nothing more joins (so island
chains are followed: Indonesia and the Bahamas stay whole). At 3 degrees Corsica joins France and French Guiana does
not, Tasmania joins Australia, the Canaries, Madeira, the Azores, the Galapagos, Svalbard, Alaska and Hawaii stay
out; at 10 degrees the chain would pull in Alaska and Hawaii through the panhandle islands and the Azores through
Madeira. It uses the same shifted frame as the bbox when the country spans the antimeridian, and comes back to
-180..180 when the rings it kept all lie west of it. For a point square and Antarctica the frame is the bbox.

Format of world.json (compact, countries sorted by code; format 1 had no holes and no background):

    {"format":2,"source":"Natural Earth 50m (public domain)","countries":{"FR":{"polys":[[[lon,lat],...],...],
     "holes":[[[lon,lat],...],...],"bbox":[west,south,east,north],"frame":[west,south,east,north],"label":[lon,lat],
     "capital":[lon,lat] or null,"point":false,"neighbours":["AD",...]},...},"background":[[[lon,lat],...],...]}

Geo.kt carries the same bbox, frame, label, capital and point flag per code, for the screens that need them without
reading the asset. When the asset comes out over MAX_ASSET_BYTES the tolerance is raised to FALLBACK_TOLERANCE.

Land borders: each record also carries "neighbours", the codes of the catalog countries it shares a land border
with, sorted. Two countries border each other when their Natural Earth outlines (before simplification) share at
least MIN_SHARED_VERTICES points (holes included, so an enclave meets its host): the 50m countries are cut from one
set of boundary lines, so a shared border has the same points on both sides, while two countries that only touch
at a corner (Namibia and Zimbabwe at the Kazungula quadripoint) share one. HAND_NEIGHBOURS adds the borders the data
cannot show (the microstates drawn as point squares, the 150 m Botswana–Zambia border, Ceuta and Melilla, Saint
Martin); DENY_NEIGHBOURS removes any false positive. Neighbours.kt carries the
same lists, generated from the asset like Geo.kt.

--check needs no download (CI runs it): it fails when the asset does not parse, is not format 2, misses a catalog
code or lacks polygons or a holes list for one, has no background land, or when Geo.kt or Neighbours.kt is not what
this script writes from the asset. Whether the asset itself is current is checked by regenerating it and looking at
`git diff`.
"""
import argparse
import json
import math
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO, entries, fold  # noqa: E402

OUT_JSON = REPO / 'app/src/main/assets/world.json'
OUT_KT = REPO / 'app/src/main/java/com/example/data/model/Geo.kt'
OUT_NEIGHBOURS_KT = REPO / 'app/src/main/java/com/example/data/model/Neighbours.kt'
DEFAULT_CACHE = Path.home() / '.cache' / 'natural-earth'   # outside the repository: the files are data, never run
MIRROR = 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/'
COUNTRIES_FILE = 'ne_50m_admin_0_countries.geojson'
PLACES_FILE = 'ne_10m_populated_places_simple.geojson'
SOURCE = 'Natural Earth 50m (public domain)'
FORMAT = 2

TOLERANCE = 0.05            # Douglas-Peucker tolerance in degrees
FALLBACK_TOLERANCE = 0.08   # used when the asset comes out over MAX_ASSET_BYTES
MAX_ASSET_BYTES = 700 * 1024
MIN_RING_AREA = 0.01        # square degrees; smaller simplified rings are dropped unless they are the largest
POINT_AREA = 0.01           # a country whose largest ring is under this is drawn as a point square
POINT_SIDE = 0.08           # side of that square, degrees
MIN_RING_POINTS = 4
FRAME_GAP = 3               # degrees; a ring joins the frame when its bbox comes within this of the frame, both axes
DECIMALS = 2

# Features whose ISO codes do not name a catalog country: ADM0_A3 -> catalog code. In the current file ISO_A2_EH
# (or ISO_A2 for Taiwan, whose EH value is "CN-TW") already covers France, Norway, Kosovo, Palestine, South Sudan
# and Taiwan; the entries are kept for older copies. Somaliland and Northern Cyprus are merged into their state.
HAND_A3 = {
    'KOS': 'XK', 'FRA': 'FR', 'NOR': 'NO', 'PSX': 'PS', 'SDS': 'SS', 'TWN': 'TW',
    'SOL': 'SO', 'CYN': 'CY',
}
# Populated places use other ADM0_A3 values than the countries file for a few states.
CAPITAL_A3 = {'SS': ['SSD', 'SDS'], 'PS': ['PSX'], 'XK': ['KOS']}
ANTARCTICA = 'AQ'

MIN_SHARED_VERTICES = 2     # outline points two countries must share to count as bordering
# Borders the outlines cannot show (see the module docstring): the point-square microstates; Botswana–Zambia's 150 m
# at Kazungula; Spain–Morocco at Ceuta and Melilla and France–Netherlands on Saint Martin (not in the 50m outlines).
HAND_NEIGHBOURS = ['VA IT', 'MC FR', 'SM IT', 'BW ZM', 'ES MA', 'FR NL']
DENY_NEIGHBOURS = []        # pairs the outlines wrongly join, as 'XX YY'
ANTARCTICA_LABEL = (0.0, -80.0)


# ----------------------------------------------------------------------------------------------------- geometry

def ring_area(ring):
    """Shoelace area of a ring (closed or not), in square degrees."""
    n = len(ring)
    if n < 3:
        return 0.0
    total = 0.0
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        total += x1 * y2 - x2 * y1
    return abs(total) / 2


def _segment_distance(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(p[0] - ax, p[1] - ay)
    t = ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return math.hypot(p[0] - (ax + t * dx), p[1] - (ay + t * dy))


def douglas_peucker(points, tolerance):
    """The points of an open polyline that Douglas-Peucker keeps, both ends included (iterative)."""
    n = len(points)
    if n < 3:
        return list(points)
    keep = [False] * n
    keep[0] = keep[-1] = True
    stack = [(0, n - 1)]
    while stack:
        first, last = stack.pop()
        best, best_d = -1, tolerance
        a, b = points[first], points[last]
        for i in range(first + 1, last):
            d = _segment_distance(points[i], a, b)
            if d > best_d:
                best, best_d = i, d
        if best >= 0:
            keep[best] = True
            stack.append((first, best))
            stack.append((best, last))
    return [p for p, k in zip(points, keep) if k]


def extreme_points(ring):
    """West, north, east and south-most points of a ring, in that order, duplicates dropped."""
    west = min(ring, key=lambda p: p[0])
    north = max(ring, key=lambda p: p[1])
    east = max(ring, key=lambda p: p[0])
    south = min(ring, key=lambda p: p[1])
    out = []
    for p in (west, north, east, south):
        if p not in out:
            out.append(p)
    return out


def bbox_corners(ring):
    xs = [p[0] for p in ring]
    ys = [p[1] for p in ring]
    return [[min(xs), min(ys)], [min(xs), max(ys)], [max(xs), max(ys)], [max(xs), min(ys)]]


def simplify_ring(ring, tolerance):
    """A closed GeoJSON ring -> an open ring of at least MIN_RING_POINTS rounded points, consecutive duplicates removed."""
    pts = [tuple(p[:2]) for p in ring]
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts = pts[:-1]
    if len(pts) >= 3:
        # Split the ring at the point farthest from the first one, so both halves are proper polylines.
        far = max(range(len(pts)), key=lambda i: (pts[i][0] - pts[0][0]) ** 2 + (pts[i][1] - pts[0][1]) ** 2)
        first = douglas_peucker(pts[:far + 1], tolerance)
        second = douglas_peucker(pts[far:] + [pts[0]], tolerance)
        pts = first[:-1] + second[:-1]
    rounded = []
    for x, y in pts:
        p = [round(x, DECIMALS) + 0.0, round(y, DECIMALS) + 0.0]
        if not rounded or rounded[-1] != p:
            rounded.append(p)
    if len(rounded) > 1 and rounded[0] == rounded[-1]:
        rounded.pop()
    if len(rounded) < MIN_RING_POINTS:
        original = [[round(p[0], DECIMALS) + 0.0, round(p[1], DECIMALS) + 0.0] for p in ring]
        rounded = extreme_points(original)
        if len(rounded) < MIN_RING_POINTS:
            rounded = bbox_corners(original)
    return rounded


def point_square(lon, lat):
    h = POINT_SIDE / 2
    return [[round(lon - h, DECIMALS) + 0.0, round(lat - h, DECIMALS) + 0.0],
            [round(lon - h, DECIMALS) + 0.0, round(lat + h, DECIMALS) + 0.0],
            [round(lon + h, DECIMALS) + 0.0, round(lat + h, DECIMALS) + 0.0],
            [round(lon + h, DECIMALS) + 0.0, round(lat - h, DECIMALS) + 0.0]]


def bbox_of(polys, wrap=True):
    """[west, south, east, north]; in the +360 frame when the rings span more than 180 degrees of longitude."""
    lons = [p[0] for ring in polys for p in ring]
    lats = [p[1] for ring in polys for p in ring]
    if wrap and max(lons) - min(lons) > 180:
        lons = [lon + 360 if lon < 0 else lon for lon in lons]
    return [min(lons), min(lats), max(lons), max(lats)]


def frame_of(polys, bbox, capital):
    """The box of the largest ring and the capital's, plus every ring whose bbox comes within FRAME_GAP of it,
    grown until stable; widened to the capital point when that still lies outside (Tarawa's atoll is too thin)."""
    shifted = bbox[2] > 180

    def shift(lon):
        return lon + 360 if shifted and lon < 0 else lon

    def ring_box(ring):
        lons = [shift(p[0]) for p in ring]
        lats = [p[1] for p in ring]
        return [min(lons), min(lats), max(lons), max(lats)]

    def contains(box, lon, lat):
        return box[0] <= lon <= box[2] and box[1] <= lat <= box[3]

    boxes = [ring_box(r) for r in polys]
    areas = [ring_area(r) for r in polys]
    used = {areas.index(max(areas))}
    frame = list(boxes[next(iter(used))])
    if capital is not None:
        lon, lat = shift(capital[0]), capital[1]
        for i, b in enumerate(boxes):
            if contains(b, lon, lat):   # Kuala Lumpur is on the peninsula, the largest ring is on Borneo
                used.add(i)
                frame = [min(frame[0], b[0]), min(frame[1], b[1]), max(frame[2], b[2]), max(frame[3], b[3])]
        if not contains(frame, lon, lat):
            frame = [min(frame[0], lon), min(frame[1], lat), max(frame[2], lon), max(frame[3], lat)]
    grown = True
    while grown:
        grown = False
        for i, b in enumerate(boxes):
            if i in used:
                continue
            if (b[0] <= frame[2] + FRAME_GAP and b[2] >= frame[0] - FRAME_GAP
                    and b[1] <= frame[3] + FRAME_GAP and b[3] >= frame[1] - FRAME_GAP):
                used.add(i)
                frame = [min(frame[0], b[0]), min(frame[1], b[1]), max(frame[2], b[2]), max(frame[3], b[3])]
                grown = True
    if frame[0] >= 180:   # every ring kept lies west of the antimeridian: back to the plain frame
        frame = [frame[0] - 360, frame[1], frame[2] - 360, frame[3]]
    return frame


def inside(point, bbox):
    west, south, east, north = bbox
    lon = point[0] + 360 if east > 180 and point[0] < 0 else point[0]
    return west <= lon <= east and south <= point[1] <= north


def centroid(ring):
    """Area centroid of a ring, the mean of its points for a degenerate one."""
    a = 0.0
    cx = cy = 0.0
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        cross = x1 * y2 - x2 * y1
        a += cross
        cx += (x1 + x2) * cross
        cy += (y1 + y2) * cross
    if abs(a) < 1e-12:
        return [sum(p[0] for p in ring) / n, sum(p[1] for p in ring) / n]
    return [cx / (3 * a), cy / (3 * a)]


# ---------------------------------------------------------------------------------------------------- the data

def fetch(cache, name):
    path = cache / name
    if not path.exists():
        cache.mkdir(parents=True, exist_ok=True)
        print(f'downloading {MIRROR}{name}')
        with urllib.request.urlopen(MIRROR + name) as response:
            path.write_bytes(response.read())
    return json.loads(path.read_text(encoding='utf-8'))


def catalog_code(props, codes):
    for field in ('ISO_A2_EH', 'ISO_A2'):
        value = props.get(field)
        if value in codes:
            return value, field
    value = HAND_A3.get(props.get('ADM0_A3'))
    return (value, 'ADM0_A3') if value in codes else (None, None)


def place_key(name):
    """Capital names folded for matching: no accents, case, punctuation or spaces; Saint -> St."""
    text = fold(name).replace('ʻ', '').replace("'", '').replace('saint ', 'st ')
    return re.sub(r'[^a-z0-9]', '', text)


def pick_capital(entry, candidates):
    """(lon, lat, how) from the country's populated places: the Admin-0 capital (or alt) with the catalog's name,
    else any place with that name (Gitega is an Admin-1 capital), else the first Admin-0 capital; None when none."""
    wanted = {place_key(entry['quizCapital']), place_key(entry['capital'])}
    for cla in ('Admin-0 capital', 'Admin-0 capital alt'):
        for c in candidates:
            if c['featurecla'] == cla and place_key(c['name']) in wanted:
                return c['lon'], c['lat'], 'matched' if cla == 'Admin-0 capital' else f'matched alt {c["name"]}'
    for c in candidates:
        if place_key(c['name']) in wanted:
            return c['lon'], c['lat'], f'matched {c["featurecla"]} {c["name"]}'
    for c in candidates:
        if c['featurecla'] == 'Admin-0 capital':
            return c['lon'], c['lat'], f'first capital {c["name"]}'
    return None


def adjacency(raw, report):
    """code -> sorted codes it shares a land border with, from the raw outlines (see the module docstring)."""
    owners = {}   # vertex -> codes whose outline has it
    for code, entry in raw.items():
        if code == ANTARCTICA:
            continue
        seen = set()
        for ring in entry['rings'] + entry.get('holes', []):
            for point in ring:
                vertex = (round(point[0], 6), round(point[1], 6))
                if vertex in seen:
                    continue
                seen.add(vertex)
                owners.setdefault(vertex, set()).add(code)
    shared = {}   # (a, b) -> vertices in common
    for codes in owners.values():
        if len(codes) < 2:
            continue
        for a in codes:
            for b in codes:
                if a < b:
                    shared[(a, b)] = shared.get((a, b), 0) + 1
    pairs = {pair for pair, count in shared.items() if count >= MIN_SHARED_VERTICES}
    touching = sorted(f'{a}-{b}' for (a, b), count in shared.items() if count < MIN_SHARED_VERTICES)
    for line in HAND_NEIGHBOURS:
        a, b = line.split()
        pairs.add((min(a, b), max(a, b)))
    for line in DENY_NEIGHBOURS:
        a, b = line.split()
        pairs.discard((min(a, b), max(a, b)))
    neighbours = {code: set() for code in raw}
    for a, b in pairs:
        neighbours[a].add(b)
        neighbours[b].add(a)
    report.append(f'land borders: {len(pairs)} pairs ({len(HAND_NEIGHBOURS)} by hand, {len(DENY_NEIGHBOURS)} denied); '
                  f'touching at one point only, not counted: {", ".join(touching) or "none"}')
    return {code: sorted(codes) for code, codes in neighbours.items()}


def build(cache, tolerance, report):
    """The countries dict of the asset. `report` collects the summary lines."""
    catalog = {e['code']: e for e in entries()}
    codes = set(catalog)
    countries_fc = fetch(cache, COUNTRIES_FILE)
    places_fc = fetch(cache, PLACES_FILE)

    raw = {}        # code -> {'rings': [...], 'holes': [...], 'labels': [(area, (x, y))], 'a3': set()}
    other_land = {}  # ADM0_A3 -> outer rings of a feature that is no catalog country: the background land
    hand_used = []
    dropped = []
    for feature in countries_fc['features']:
        props = feature['properties']
        code, field = catalog_code(props, codes)
        geometry = feature['geometry']
        polygons = [geometry['coordinates']] if geometry['type'] == 'Polygon' else geometry['coordinates']
        rings = [polygon[0] for polygon in polygons if polygon]
        if code is None:
            dropped.append(f"{props.get('ADM0_A3')} {props.get('NAME')}")
            other_land.setdefault(props['ADM0_A3'], []).extend(rings)
            continue
        if field == 'ADM0_A3':
            hand_used.append(f"{props['ADM0_A3']} -> {code}")
        entry = raw.setdefault(code, {'rings': [], 'holes': [], 'labels': [], 'a3': set()})
        entry['rings'].extend(rings)
        # An enclave (Lesotho) shares its outline with its host's hole: the holes count for the borders, and the
        # ones whose enclave is drawn are cut out of the host (see holes_of).
        entry['holes'].extend(hole for polygon in polygons for hole in polygon[1:])
        entry['a3'].add(props['ADM0_A3'])
        if props.get('LABEL_X') is not None and props.get('LABEL_Y') is not None:
            entry['labels'].append((max((ring_area(r) for r in rings), default=0), (props['LABEL_X'], props['LABEL_Y'])))

    capitals = {}   # code -> [candidate]
    for feature in places_fc['features']:
        props = feature['properties']
        if not str(props.get('featurecla', '')).startswith('Admin-0 capital') and props.get('iso_a2') not in raw:
            continue
        for code, entry in raw.items():
            if props.get('iso_a2') == code or props.get('adm0_a3') in entry['a3'] or props.get('adm0_a3') in CAPITAL_A3.get(code, ()):
                capitals.setdefault(code, []).append({
                    'featurecla': props['featurecla'], 'name': props['name'],
                    'lon': float(props['longitude']), 'lat': float(props['latitude']),
                })
                break

    neighbours = adjacency(raw, report)
    countries = {}
    drawn = {}      # code -> {index of a raw ring: the ring drawn for it}
    point_codes, fallback_capitals, inexact, relabelled = [], [], [], []
    for code in sorted(codes):
        if code == ANTARCTICA:
            drawn[code] = simplify_rings(raw[code]['rings'], tolerance)
            # Its ring goes round the pole, so the plain box (the whole band) is the right one.
            countries[code] = record(list(drawn[code].values()), list(ANTARCTICA_LABEL), None, False, wrap=False)
            countries[code]['neighbours'] = []
            continue
        if code not in raw:
            raise SystemExit(f'{code} ({catalog[code]["name"]}) has no feature in {COUNTRIES_FILE}')
        data = raw[code]
        label = list(max(data['labels'])[1]) if data['labels'] else None
        picked = pick_capital(catalog[code], capitals.get(code, []))
        if picked and not picked[2] == 'matched':
            inexact.append(f'{code} {picked[2]}')
        largest = max(ring_area(r) for r in data['rings'])
        if largest < POINT_AREA:
            centre = picked[:2] if picked else label
            square = point_square(*centre)
            drawn[code] = {i: square for i in range(len(data['rings']))}   # every ring of it is the square
            polys = [square]
            point = True
            point_codes.append(code)
        else:
            drawn[code] = simplify_rings(data['rings'], tolerance)
            polys = list(drawn[code].values())
            point = False
        if point:
            label = list(centre)
        elif label is None or not inside(label, frame_of(polys, bbox_of(polys), picked[:2] if picked else None)):
            if label is not None:
                relabelled.append(code)
            label = centroid(max(polys, key=ring_area))
        if picked:
            capital = [picked[0], picked[1]]
        else:
            fallback_capitals.append(f'{code} {catalog[code]["quizCapital"]}')
            capital = list(label)
        countries[code] = record(polys, label, capital, point)
        countries[code]['neighbours'] = neighbours.get(code, [])

    holes_of(raw, drawn, countries, report)
    background = []
    for a3 in sorted(other_land):
        background.extend(simplify_country(other_land[a3], tolerance))

    report.append(f'hand map used: {", ".join(sorted(set(hand_used))) or "none"}')
    report.append(f'dropped {len(dropped)} non-catalog features, drawn as background land in {len(background)} rings: '
                  f'{", ".join(dropped)}')
    report.append(f'countries: {len(countries)} ({len(countries) - len(point_codes)} from 50m outlines, '
                  f'{len(point_codes)} point squares: {", ".join(point_codes)})')
    report.append(f'capitals matched inexactly: {"; ".join(inexact) or "none"}')
    report.append(f'label point outside the outline, centroid used: {", ".join(relabelled) or "none"}')
    report.append(f'capitals missing, label used: {", ".join(fallback_capitals) or "none"}')
    return countries, background


def holes_of(raw, drawn, countries, report):
    """Gives each record its "holes": for each Natural Earth hole, the drawn ring of the enclave that fills it (the
    outer ring of another country that has most of the hole's points), so the two outlines are one line. A hole whose
    enclave is not drawn (too small, like Malawi's islands in Mozambique's part of the lake) is left out."""
    owners = {}     # raw outline vertex -> {(code, ring index)}
    for code, entry in raw.items():
        for i, ring in enumerate(entry['rings']):
            for point in ring:
                owners.setdefault((round(point[0], 6), round(point[1], 6)), set()).add((code, i))
    kept, left_out = [], []
    for code in sorted(countries):
        holes = []
        for hole in raw.get(code, {}).get('holes', []):
            counts = {}
            for point in hole:
                for owner in owners.get((round(point[0], 6), round(point[1], 6)), ()):
                    if owner[0] != code:
                        counts[owner] = counts.get(owner, 0) + 1
            best = max(counts, key=counts.get, default=None)
            if best is not None and counts[best] * 2 >= len(hole) and best[1] in drawn.get(best[0], {}):
                holes.append(drawn[best[0]][best[1]])
                kept.append(f'{best[0]} in {code}')
            else:
                left_out.append(f'{best[0] if best else "?"} in {code}')
        countries[code]['holes'] = holes
    report.append(f'holes cut for their enclave: {", ".join(kept) or "none"}; '
                  f'left out (enclave not drawn): {", ".join(left_out) or "none"}')


def simplify_rings(rings, tolerance):
    """Raw ring index -> simplified ring, for the rings kept: the largest and every one of at least MIN_RING_AREA."""
    simplified = [simplify_ring(r, tolerance) for r in rings]
    areas = [ring_area(r) for r in simplified]
    largest = areas.index(max(areas))
    return {i: r for i, r in enumerate(simplified) if i == largest or areas[i] >= MIN_RING_AREA}


def simplify_country(rings, tolerance):
    return list(simplify_rings(rings, tolerance).values())


def record(polys, label, capital, point, wrap=True):
    def rnd(v):
        return round(v, DECIMALS) + 0.0
    bbox = bbox_of(polys, wrap)
    return {
        'polys': polys,
        'holes': [],
        'bbox': [rnd(v) for v in bbox],
        'frame': [rnd(v) for v in (bbox if point or not wrap else frame_of(polys, bbox, capital))],
        'label': [rnd(label[0]), rnd(label[1])],
        'capital': None if capital is None else [rnd(capital[0]), rnd(capital[1])],
        'point': point,
    }


# ---------------------------------------------------------------------------------------------------- outputs

def asset_text(countries, background):
    return json.dumps({'format': FORMAT, 'source': SOURCE, 'countries': countries, 'background': background},
                      separators=(',', ':'), ensure_ascii=False) + '\n'


def kotlin_text(countries):
    def num(v):
        return 'null' if v is None else repr(float(v))
    lines = [
        'package com.example.data.model',
        '',
        '// Generated by tools/gen-worldmap.py from Natural Earth (public domain); do not edit. The same numbers as',
        '// the asset world.json: bounding box, label point and capital per catalog code, for use without the outlines.',
        '',
        '/** Where each catalog country sits on the map: its bounding box, label point and capital. */',
        'object Geo {',
        '    /**',
        '     * Degrees. [east] exceeds 180 for a country spanning the antimeridian (its box is in a frame where western',
        '     * longitudes are shifted by +360); the frame is the box to show the country in (the main ring and its',
        '     * neighbours, without far-off territories); [point] marks a microstate drawn as a square around its capital.',
        '     */',
        '    data class Place(',
        '        val code: String,',
        '        val west: Double,',
        '        val south: Double,',
        '        val east: Double,',
        '        val north: Double,',
        '        val frameWest: Double,',
        '        val frameSouth: Double,',
        '        val frameEast: Double,',
        '        val frameNorth: Double,',
        '        val labelLon: Double,',
        '        val labelLat: Double,',
        '        val capitalLon: Double?,',
        '        val capitalLat: Double?,',
        '        val point: Boolean,',
        '    )',
        '',
        '    val byCode: Map<String, Place> = mapOf(',
    ]
    for code in sorted(countries):
        c = countries[code]
        w, s, e, n = c['bbox']
        fw, fs, fe, fn = c['frame']
        cap = c['capital'] or [None, None]
        lines.append(f'        "{code}" to Place("{code}", {num(w)}, {num(s)}, {num(e)}, {num(n)}, '
                     f'{num(fw)}, {num(fs)}, {num(fe)}, {num(fn)}, '
                     f'{num(c["label"][0])}, {num(c["label"][1])}, {num(cap[0])}, {num(cap[1])}, '
                     f'{"true" if c["point"] else "false"}),')
    lines += [
        '    )',
        '',
        '    fun of(code: String): Place? = byCode[code]',
        '}',
        '',
    ]
    return '\n'.join(lines)


def neighbours_text(countries):
    lines = [
        'package com.example.data.model',
        '',
        '// Generated by tools/gen-worldmap.py from Natural Earth (public domain); do not edit. The same lists as the',
        '// asset world.json: the catalog countries each one shares a land border with.',
        '',
        '/** Which catalog countries border which: a symmetric map, each list sorted by code; islands and Antarctica have none. */',
        'object Neighbours {',
        '    val byCode: Map<String, List<String>> = mapOf(',
    ]
    for code in sorted(countries):
        codes = ', '.join(f'"{c}"' for c in countries[code].get('neighbours', []))
        lines.append(f'        "{code}" to listOf({codes}),')
    lines += [
        '    )',
        '',
        '    /** The codes of the countries [code] shares a land border with, sorted; empty for an island, an unknown code or Antarctica. */',
        '    fun of(code: String): List<String> = byCode[code].orEmpty()',
        '}',
        '',
    ]
    return '\n'.join(lines)


def check():
    """Offline: the asset parses and covers the catalog, and Geo.kt is what the asset says."""
    problems = []
    try:
        data = json.loads(OUT_JSON.read_text(encoding='utf-8'))
    except (OSError, ValueError) as e:
        print(f'{OUT_JSON}: {e}', file=sys.stderr)
        return 1
    if data.get('format') != FORMAT:
        problems.append(f'format is {data.get("format")!r}, expected {FORMAT}')
    countries = data.get('countries', {})
    codes = [e['code'] for e in entries()]
    for code in codes:
        c = countries.get(code)
        if c is None:
            problems.append(f'{code} is missing')
        elif not c.get('polys') or any(len(ring) < MIN_RING_POINTS for ring in c['polys']):
            problems.append(f'{code} has no usable polygon')
        elif len(c.get('bbox', [])) != 4 or len(c.get('frame', [])) != 4:
            problems.append(f'{code} has no bbox or frame')
        elif not isinstance(c.get('neighbours'), list):
            problems.append(f'{code} has no neighbours list')
        elif not isinstance(c.get('holes'), list) or any(len(ring) < MIN_RING_POINTS for ring in c['holes']):
            problems.append(f'{code} has no usable holes list')
    background = data.get('background')
    if not isinstance(background, list) or not background or any(len(ring) < MIN_RING_POINTS for ring in background):
        problems.append('the background land is missing or has a ring of fewer than four points')
    for code in codes:
        for other in countries.get(code, {}).get('neighbours', []):
            if other not in countries:
                problems.append(f'{code} borders {other}, which is not in the catalog')
            elif code not in countries[other].get('neighbours', []):
                problems.append(f'{code} borders {other} but not the other way round')
    extra = sorted(set(countries) - set(codes))
    if extra:
        problems.append(f'not in the catalog: {", ".join(extra)}')
    size = OUT_JSON.stat().st_size
    if size > MAX_ASSET_BYTES:
        problems.append(f'asset is {size} bytes, over {MAX_ASSET_BYTES}')
    if not OUT_KT.exists() or OUT_KT.read_text(encoding='utf-8') != kotlin_text(countries):
        problems.append(f'{OUT_KT.relative_to(REPO)} does not match the asset; run tools/gen-worldmap.py')
    if not OUT_NEIGHBOURS_KT.exists() or OUT_NEIGHBOURS_KT.read_text(encoding='utf-8') != neighbours_text(countries):
        problems.append(f'{OUT_NEIGHBOURS_KT.relative_to(REPO)} does not match the asset; run tools/gen-worldmap.py')
    for p in problems:
        print(f'{OUT_JSON.relative_to(REPO)}: {p}', file=sys.stderr)
    if not problems:
        print(f'{OUT_JSON.relative_to(REPO)} is current ({len(countries)} countries, {size} bytes); Geo.kt and Neighbours.kt match')
    return 1 if problems else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--cache', type=Path, default=DEFAULT_CACHE, help='directory for the Natural Earth downloads')
    parser.add_argument('--tolerance', type=float, default=TOLERANCE, help='Douglas-Peucker tolerance in degrees')
    parser.add_argument('--check', action='store_true', help='verify the committed outputs without downloading')
    args = parser.parse_args()
    if args.check:
        return check()

    tolerance = args.tolerance
    report = []
    countries, background = build(args.cache, tolerance, report)
    text = asset_text(countries, background)
    if len(text.encode('utf-8')) > MAX_ASSET_BYTES and tolerance < FALLBACK_TOLERANCE:
        print(f'asset would be {len(text.encode("utf-8"))} bytes at {tolerance} degrees; '
              f'retrying at {FALLBACK_TOLERANCE}')
        tolerance = FALLBACK_TOLERANCE
        report = []
        countries, background = build(args.cache, tolerance, report)
        text = asset_text(countries, background)
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(text, encoding='utf-8')
    OUT_KT.write_text(kotlin_text(countries), encoding='utf-8')
    OUT_NEIGHBOURS_KT.write_text(neighbours_text(countries), encoding='utf-8')
    for line in report:
        print(line)
    points = sum(len(ring) for c in countries.values() for ring in c['polys'] + c['holes'])
    rings = sum(len(c['polys']) + len(c['holes']) for c in countries.values())
    print(f'tolerance {tolerance} degrees: {rings} rings, {points} points in the countries; '
          f'{len(background)} rings, {sum(len(ring) for ring in background)} points of background land')
    print(f'wrote {OUT_JSON.relative_to(REPO)}: {OUT_JSON.stat().st_size} bytes; {OUT_KT.relative_to(REPO)}; {OUT_NEIGHBOURS_KT.relative_to(REPO)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
