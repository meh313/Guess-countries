"""Applies {code, field, value} edits to existing entries in the per-continent catalog files.

    python3 tools/apply-edits.py edits.json

value is a string, a digit string (population), a number (areaSqKm) or a JSON array string (lists).
New countries are added with gen-entries.py, not here.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import CONTINENTS, catalog_file  # noqa: E402

STRING_FIELDS = {'name', 'officialName', 'capital', 'quizCapital', 'continent', 'subregion', 'flagDescription',
                 'currency', 'funFact', 'driveSide'}
LIST_FIELDS = {'flagColors', 'languages', 'landmarks'}


def kstr(value):
    value = value.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$')
    return '"' + value + '"'


def render(field, value):
    if field in STRING_FIELDS:
        return kstr(value)
    if field in LIST_FIELDS:
        items = json.loads(value) if isinstance(value, str) else value
        assert isinstance(items, list) and all(isinstance(i, str) for i in items), (field, value)
        return 'listOf(' + ', '.join(kstr(i) for i in items) + ')'
    if field == 'population':
        return f"{int(re.sub(r'[^0-9]', '', str(value)))}L"
    if field == 'areaSqKm':
        n = float(str(value).replace(',', ''))
        return f'{n:.1f}' if n == int(n) else repr(n)
    if field == 'isSovereign':
        return 'true' if value in (True, 'true') else 'false'
    raise ValueError(field)


def apply(edits):
    report = []
    files = {}
    for cont in CONTINENTS:
        path = catalog_file(cont)
        if path.exists():
            files[path] = path.read_text()
    for e in edits:
        code, field, value = e['code'], e['field'], e['value']
        for path, src in files.items():
            m = re.search(r'^        Country\(\n            code = "' + code + r'"\n(?:            .*\n)+?        \)', src, re.M)
            if not m:
                continue
            block = m.group(0)
            new = render(field, value)
            line = re.compile(r'^(            )' + re.escape(field) + r' = .*?(,?)$', re.M)
            lm = line.search(block)
            if lm:
                block2 = block[:lm.start()] + f'{lm.group(1)}{field} = {new}{lm.group(2)}' + block[lm.end():]
                report.append((code, field, 'replaced'))
            else:
                head, tail = block.rsplit('\n        )', 1)
                if not head.rstrip().endswith(','):
                    head = head.rstrip() + ','
                block2 = head + f'\n            {field} = {new}\n        )' + tail
                report.append((code, field, 'added'))
            files[path] = src[:m.start()] + block2 + src[m.end():]
            break
        else:
            raise KeyError(f'{code} is not in the catalog')
    for path, src in files.items():
        path.write_text(src)
    return report


if __name__ == '__main__':
    for row in apply(json.load(open(sys.argv[1]))):
        print(*row)
