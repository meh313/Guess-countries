"""Exports the current catalog entries of one continent, joined with their provenance, as JSON.

    python3 tools/export-entries.py Europe [--out /path/europe-export.json] [--codes AL,AD]

Each record holds what the app ships today (parsed from <Continent>Catalog.kt, the authority), the seed's
owner rules and the research pass's sources, notes and remaining doubts for that country. The audit
workflow hands this file to its verifier agents, so they check what is actually in the catalog and start
from the doubts the first pass already flagged.
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import CONTINENTS, REPO, catalog_file  # noqa: E402

BLOCK = re.compile(r'^        Country\(\n(?:            .*\n)+?        \)', re.M)
STRING = r'"((?:[^"\\]|\\.)*)"'


def unescape(text):
    return text.replace('\\"', '"').replace('\\$', '$').replace('\\\\', '\\')


def string_field(block, field):
    m = re.search(r'^            ' + field + r' = ' + STRING, block, re.M)
    return unescape(m.group(1)) if m else None


def list_field(block, field):
    m = re.search(r'^            ' + field + r' = listOf\((.*)\)\s*,?\s*$', block, re.M)
    if not m:
        return None
    return [unescape(s) for s in re.findall(STRING, m.group(1))]


def number_field(block, field):
    m = re.search(r'^            ' + field + r' = ([0-9][0-9_.]*)L?', block, re.M)
    if not m:
        return None
    raw = m.group(1).replace('_', '')
    return int(raw) if field == 'population' else float(raw)


def parse_block(block):
    capital = string_field(block, 'capital')
    return {
        'code': string_field(block, 'code'),
        'name': string_field(block, 'name'),
        'officialName': string_field(block, 'officialName'),
        'capital': capital,
        'quizCapital': string_field(block, 'quizCapital') or capital,
        'continent': string_field(block, 'continent'),
        'subregion': string_field(block, 'subregion'),
        'population': number_field(block, 'population'),
        'areaSqKm': number_field(block, 'areaSqKm'),
        'flagColors': list_field(block, 'flagColors'),
        'flagDescription': string_field(block, 'flagDescription'),
        'languages': list_field(block, 'languages'),
        'currency': string_field(block, 'currency'),
        'landmarks': list_field(block, 'landmarks'),
        'funFact': string_field(block, 'funFact'),
        'driveSide': string_field(block, 'driveSide') or 'Right',
        'isSovereign': 'isSovereign = false' not in block,
    }


def sources_of(entry):
    """Per-field {sources, note} from a research entry, plus the flag reasons' bases and sources."""
    out = {}
    for field, value in entry.items():
        if isinstance(value, dict) and 'sources' in value:
            out[field] = {'sources': value.get('sources', []), 'note': value.get('note', '')}
    flag = entry.get('flag') or {}
    out['flagReasons'] = [
        {'feature': r.get('feature'), 'why': r.get('why'), 'basis': r.get('basis'), 'sources': r.get('sources', [])}
        for r in flag.get('reasons', [])
    ]
    return out


def export(continent, only=None):
    path = catalog_file(continent)
    records = []
    for block in BLOCK.findall(path.read_text()):
        rec = parse_block(block)
        if only and rec['code'] not in only:
            continue
        records.append(rec)
    seed_path = REPO / 'content/seed' / f'{continent.lower()}.json'
    research_path = REPO / 'content/research' / f'{continent.lower()}.json'
    seed = {e['code']: e for e in json.loads(seed_path.read_text())['entries']} if seed_path.exists() else {}
    research = {}
    doubts = []
    if research_path.exists():
        data = json.loads(research_path.read_text())
        research = {e['code']: e for e in data.get('entries', [])}
        doubts = data.get('doubts', [])
    out = []
    for rec in records:
        code = rec['code']
        s = seed.get(code, {})
        r = research.get(code)
        out.append({
            'code': code,
            'name': rec['name'],
            'current': rec,
            'inQuickPass': code in seed,
            'ownerRules': s.get('ownerRules'),
            'leftHandSeed': s.get('leftHand'),
            'priorDoubts': (r or {}).get('remainingDoubts'),
            'priorProvenance': sources_of(r) if r else None,
        })
    return {'continent': continent, 'count': len(out), 'continentDoubts': doubts, 'entries': out}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('continent', choices=[c for c in CONTINENTS if c != 'Antarctica'])
    ap.add_argument('--out')
    ap.add_argument('--codes', help='comma separated codes to keep')
    args = ap.parse_args()
    only = set(args.codes.split(',')) if args.codes else None
    data = export(args.continent, only)
    text = json.dumps(data, ensure_ascii=False, indent=1)
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(text)
        print(f'{args.continent}: {data["count"]} entries -> {args.out} ({len(text) // 1024} KB)')
    else:
        print(text)


if __name__ == '__main__':
    main()
