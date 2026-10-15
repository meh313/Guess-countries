#!/usr/bin/env python3
"""Checks the translations in app/src/main/res/values-*/ against the English strings in values/.

    python3 tools/strings-audit.py [--check] [--locale fr]

For every locale folder: every string and plurals of the default files exists in the locale (same file name), with
the same format placeholders (%1$s, %2$d, %s ...) in the same multiset, no key the default does not have, a plurals
entry for every quantity the language needs (CLDR: ar has zero/one/two/few/many/other, fr and es one/many/other,
de one/other; lint asks for the same) and an
"other" entry everywhere, no untranslated copy of an English string longer than one word (a few words are the same
in every language: the exceptions are listed in SAME_IS_FINE), and XML that Android accepts (apostrophes escaped).
Without --check the report is printed and the exit code is 0; with it, any problem exits 1 (CI runs it).
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO  # noqa: E402

RES = REPO / 'app/src/main/res'
QUANTITIES = {
    'fr': {'one', 'many', 'other'}, 'es': {'one', 'many', 'other'}, 'de': {'one', 'other'},
    'ar': {'zero', 'one', 'two', 'few', 'many', 'other'},
}
PLACEHOLDER = re.compile(r'%(\d+\$)?[sd]')
# English strings that stay as they are in every language (brand, codes, symbols, proper nouns).
SAME_IS_FINE = {'World Flags', 'OK', 'Blitz', 'GitHub', 'Antarctica', 'UK, USA, DRC', '%1$s · %2$s', '%1$s, %2$s', '%1$s/%2$s',
                '·', '—', 'Africa', 'Europe', 'Asia', 'Oceania'}


def entries(folder):
    """{file name: {key: (kind, text or {quantity: text})}} for every strings*.xml of a values folder."""
    out = {}
    for path in sorted(folder.glob('strings*.xml')):
        root = ET.parse(path).getroot()
        items = {}
        for node in root:
            name = node.get('name')
            if node.tag == 'string':
                items[name] = ('string', ''.join(node.itertext()))
            elif node.tag == 'plurals':
                items[name] = ('plurals', {item.get('quantity'): ''.join(item.itertext()) for item in node})
        out[path.name] = items
    return out


def placeholders(text):
    return sorted(PLACEHOLDER.findall(text))


def format_only(text):
    """True for a string with no letters once its placeholders are gone ("%1$s · %2$s"): nothing to translate."""
    return not re.search(r'[^\W\d_]', PLACEHOLDER.sub('', text))


def unescaped_apostrophe(path):
    raw = path.read_text(encoding='utf-8')
    problems = []
    for lineno, line in enumerate(raw.splitlines(), 1):
        body = re.sub(r'<[^>]+>', '', line)
        if re.search(r"(?<!\\)'", body) and 'CDATA' not in line:
            problems.append(f'{path.relative_to(REPO)}:{lineno}: unescaped apostrophe')
    return problems


def audit(locale, default):
    folder = RES / f'values-{locale}'
    problems = []
    if not folder.exists():
        return [f'values-{locale} does not exist']
    translated = entries(folder)
    same = 0
    for file, items in default.items():
        got = translated.get(file)
        if got is None:
            problems.append(f'values-{locale}/{file} is missing')
            continue
        for key, (kind, value) in items.items():
            if key not in got:
                problems.append(f'values-{locale}/{file}: {key} is missing')
                continue
            tkind, tvalue = got[key]
            if tkind != kind:
                problems.append(f'values-{locale}/{file}: {key} is a {tkind}, expected a {kind}')
                continue
            if kind == 'string':
                if placeholders(tvalue) != placeholders(value):
                    problems.append(f'values-{locale}/{file}: {key} placeholders {placeholders(tvalue)} != {placeholders(value)}')
                if tvalue == value and value not in SAME_IS_FINE and len(value.split()) > 1 and not format_only(value):
                    problems.append(f'values-{locale}/{file}: {key} is not translated: {value!r}')
                elif tvalue == value:
                    same += 1
            else:
                needed = QUANTITIES.get(locale, {'one', 'other'})
                missing = needed - set(tvalue)
                if missing:
                    problems.append(f'values-{locale}/{file}: {key} lacks quantities {sorted(missing)}')
                if 'other' not in tvalue:
                    problems.append(f'values-{locale}/{file}: {key} has no "other"')
                expected = placeholders(value.get('other', ''))
                for quantity, text in tvalue.items():
                    if quantity not in {'zero', 'one', 'two', 'few', 'many', 'other'}:
                        problems.append(f'values-{locale}/{file}: {key} has an unknown quantity {quantity}')
                    # A quantity may spell the number out ("one review") and drop the placeholder; never add one.
                    extra = [p for p in placeholders(text) if p not in expected]
                    if extra:
                        problems.append(f'values-{locale}/{file}: {key}/{quantity} has placeholders {extra} the English has not')
        for key in got:
            if key not in items:
                problems.append(f'values-{locale}/{file}: {key} is not in the default strings')
    for path in folder.glob('strings*.xml'):
        problems.extend(unescaped_apostrophe(path))
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--check', action='store_true', help='exit 1 on any problem')
    parser.add_argument('--locale', action='append', help='only these locales (default: every values-* folder with strings)')
    args = parser.parse_args()
    default = entries(RES / 'values')
    locales = args.locale or sorted(p.name[7:] for p in RES.glob('values-*') if list(p.glob('strings*.xml')))
    total = sum(len(v) for v in default.values())
    failed = False
    for locale in locales:
        problems = audit(locale, default)
        if problems:
            failed = True
            for p in problems:
                print(p, file=sys.stderr)
            print(f'values-{locale}: {len(problems)} problem(s)')
        else:
            print(f'values-{locale}: complete ({total} strings and plurals)')
    if not locales:
        print('no translations yet')
    return 1 if (failed and args.check) else 0


if __name__ == '__main__':
    raise SystemExit(main())
