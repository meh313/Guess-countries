"""Turns validated research JSON into Country(...) entries in the right continent file, in alphabetical order.

    python3 tools/gen-entries.py content/research/europe.json

Run tools/validate-research.py first. Existing codes are refused; the file is rebuilt with every entry
(old and new) sorted by folded name, so the order never drifts.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import ENTRY, catalog_file, codes as catalog_codes, fold  # noqa: E402

IMPORT = {'__file__': str(Path(__file__).resolve().parent / 'apply-edits.py')}
exec((Path(__file__).resolve().parent / 'apply-edits.py').read_text().split("if __name__ == '__main__':")[0], IMPORT)
render = IMPORT['render']


def value(entry, field):
    f = entry[field]
    return f['value'] if isinstance(f, dict) else f


def emoji(code):
    return ''.join(chr(0x1F1E6 + ord(ch) - ord('A')) for ch in code)


def block(e):
    lines = [
        ('code', render('name', e['code'])),
        ('name', render('name', e['name'])),
        ('officialName', render('officialName', value(e, 'officialName'))),
        ('capital', render('capital', value(e, 'capital'))),
        ('continent', render('continent', e['continent'])),
        ('subregion', render('subregion', e['subregion'])),
        ('population', render('population', value(e, 'population'))),
        ('areaSqKm', render('areaSqKm', value(e, 'areaSqKm'))),
        ('flagEmoji', render('name', emoji(e['code']))),
        ('flagColors', render('flagColors', e['flagColors'])),
        ('flagDescription', render('flagDescription', e['flag']['text'])),
        ('languages', render('languages', value(e, 'languages'))),
        ('currency', render('currency', value(e, 'currency'))),
        ('landmarks', render('landmarks', value(e, 'landmarks'))),
        ('funFact', render('funFact', value(e, 'funFact'))),
    ]
    if value(e, 'driveSide') == 'Left':
        lines.append(('driveSide', render('driveSide', 'Left')))
    quiz = value(e, 'quizCapital') if 'quizCapital' in e else None
    if quiz and quiz != value(e, 'capital'):
        lines.append(('quizCapital', render('quizCapital', quiz)))
    body = ',\n'.join(f'            {k} = {v}' for k, v in lines)
    return f'        Country(\n{body}\n        )'


data = json.loads(Path(sys.argv[1]).read_text())
batch = data['entries']
existing = set(catalog_codes())
dupes = [e['code'] for e in batch if e['code'] in existing]
if dupes:
    sys.exit(f'already in the catalog: {dupes}')
by_continent = {}
for e in batch:
    by_continent.setdefault(e['continent'], []).append(e)

for continent, items in by_continent.items():
    path = catalog_file(continent)
    src = path.read_text()
    head, rest = src.split('    val countries: List<Country> = listOf(\n', 1)
    blocks = ENTRY.findall(rest)
    blocks += [block(e) for e in items]
    name_of = lambda b: fold(re.search(r'name = "((?:[^"\\]|\\.)*)"', b).group(1))
    blocks.sort(key=name_of)
    path.write_text(head + '    val countries: List<Country> = listOf(\n' + ',\n'.join(blocks) + '\n    )\n}\n')
    print(f'{continent}: +{len(items)} entries, {len(blocks)} total -> {path.name}')
