"""Reads the country entries out of the per-continent Kotlin catalog files.

Used by the other tools so none of them hard-codes a country list. Each entry is a dict with code,
name, continent, subregion, capital, quizCapital and isSovereign (the fields the tools need).
"""
import json
import re
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MODEL_DIR = REPO / 'app/src/main/java/com/example/data/model'
CONTINENTS = ['Africa', 'Americas', 'Asia', 'Europe', 'Oceania', 'Antarctica']

ENTRY = re.compile(r'^        Country\(\n(?:            .*\n)+?        \)', re.M)


def fold(text):
    """Lowercase without accents, the same rule as TextFolding.kt, so sorting agrees with the app."""
    text = unicodedata.normalize('NFD', text)
    text = ''.join(ch for ch in text if unicodedata.category(ch) != 'Mn')
    special = {'ø': 'o', 'æ': 'ae', 'ß': 'ss', 'ł': 'l', 'đ': 'd', 'ð': 'd', 'þ': 'th'}
    return ''.join(special.get(ch, ch) for ch in text.replace('’', "'").lower())


def catalog_file(continent):
    return MODEL_DIR / f'{continent}Catalog.kt'


def _string(block, field):
    m = re.search(r'^            ' + field + r' = "((?:[^"\\]|\\.)*)"', block, re.M)
    return m.group(1).replace('\\"', '"').replace('\\$', '$').replace('\\\\', '\\') if m else None


def entries(continent=None):
    """All entries, or those of one continent, in file order."""
    result = []
    for cont in CONTINENTS if continent is None else [continent]:
        path = catalog_file(cont)
        if not path.exists():
            continue
        for block in ENTRY.findall(path.read_text()):
            capital = _string(block, 'capital')
            result.append({
                'code': _string(block, 'code'),
                'name': _string(block, 'name'),
                'continent': _string(block, 'continent'),
                'subregion': _string(block, 'subregion'),
                'capital': capital,
                'quizCapital': _string(block, 'quizCapital') or capital,
                'isSovereign': 'isSovereign = false' not in block,
                'file': str(path.relative_to(REPO)),
            })
    return result


def codes(continent=None):
    return [e['code'] for e in entries(continent)]


def seed_codes(seed_path):
    """Codes of a batch that is not in the catalog yet (content/seed/<continent>.json)."""
    return [e['code'] for e in json.loads(Path(seed_path).read_text())['entries']]


if __name__ == '__main__':
    import sys
    cont = sys.argv[1] if len(sys.argv) > 1 else None
    print(' '.join(codes(cont)))
