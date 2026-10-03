"""Checks a continent's research JSON against every catalog rule before any Kotlin is generated.

    python3 tools/validate-research.py content/research/europe.json [--seed content/seed/europe.json]

Exit status 1 and one line per problem when anything fails; mentions of Israel are reported as warnings
(the owner's rule is no entry, and neighbours' texts are written so they need not name it).

Research JSON shape (one object per country in "entries"):
  code, name, continent, subregion                           plain strings (echoed from the seed)
  officialName, capital, currency, funFact, driveSide        {"value": str, "sources": [...], "note": str}
  quizCapital (optional)                                      {"value": str, ...}
  population, areaSqKm                                        {"value": number, ...}
  languages, landmarks                                        {"value": [str], ...}
  flagColors                                                  [str]
  flag                                                        {"reasons": [{feature, why, basis, sources}], "text": str}
  remainingDoubts                                             str
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import entries as catalog_entries, fold  # noqa: E402

CONTINENTS = {'Africa', 'Americas', 'Asia', 'Europe', 'Oceania'}
SUBREGIONS = {
    'Northern Europe', 'Western Europe', 'Southern Europe', 'Eastern Europe',
    'Central Asia', 'Eastern Asia', 'South-Eastern Asia', 'Southern Asia', 'Western Asia',
    'Northern Africa', 'Western Africa', 'Middle Africa', 'Eastern Africa', 'Southern Africa',
    'Northern America', 'Central America', 'Caribbean', 'South America',
    'Australia and New Zealand', 'Melanesia', 'Micronesia', 'Polynesia',
}
FIELDS = ['officialName', 'capital', 'population', 'areaSqKm', 'languages', 'currency', 'landmarks', 'funFact', 'driveSide']
BASES = {'official', 'historical', 'traditional', 'unexplained'}
REASON_WORDS = re.compile(
    r'because|stand|represent|symbol|honor|remember|commemorat|inspired|reflect|recall|refer|celebrat|'
    r'reminds|linked|tradition|often said|official|adopted|chosen|comes? from|came from|taken from|'
    r'copied|in memory|meaning|mean |means|for the|shows|show |marks|reminder|unity|freedom|independence', re.I)
BRITISH = re.compile(r'colour|centre|tricolour|bicolour|symbolis|neighbour|\bmetre|honour|favour|organis|recognis|defence|\bgrey\b|kilometre|programme|travell|labour|harbour|armour', re.I)
JARGON = re.compile(r'\b(hoist|canton|fimbriation|ensign|saltire)\b', re.I)
VOLATILE = re.compile(r'\b(currently|nowadays|these days|this year|recently|fastest|most visited|newest|latest)\b', re.I)
REPEATED = re.compile(r'\b([A-Za-z]{4,})\s+\1\b', re.I)
CURRENCY = re.compile(r'^[^()]+ \(.+\)$')
ALLOWED_JARGON = {'CH': {'canton'}}
PS_RULES = {'capital': 'Al Quds (Jerusalem)', 'quizCapital': 'Al Quds'}

ap = argparse.ArgumentParser()
ap.add_argument('research')
ap.add_argument('--seed')
a = ap.parse_args()
data = json.loads(Path(a.research).read_text())
batch = data['entries']
seed = {e['code']: e for e in json.loads(Path(a.seed).read_text())['entries']} if a.seed else None
catalog = catalog_entries()
cat_codes = {e['code'] for e in catalog}
cat_names = {fold(e['name']) for e in catalog}
cat_quiz_capitals = {e['quizCapital'] for e in catalog if e['isSovereign']}

errors, warnings = [], []
err = lambda code, msg: errors.append(f'{code}: {msg}')


def val(e, field):
    f = e.get(field)
    return f.get('value') if isinstance(f, dict) else f


seen_names, seen_quiz = {}, {}
for e in batch:
    code = e.get('code', '??')
    if not re.fullmatch(r'[A-Z]{2}', code):
        err(code, 'code must be two uppercase letters')
    if code in cat_codes:
        err(code, 'already in the catalog')
    if seed is not None and code not in seed:
        err(code, 'not in the seed list')
    name = e.get('name', '')
    if fold(name) in cat_names or fold(name) in seen_names:
        err(code, f'name "{name}" already used')
    seen_names[fold(name)] = code
    if seed is not None and name != seed[code]['name']:
        err(code, f'name "{name}" differs from the seed "{seed[code]["name"]}"')
    if e.get('continent') not in CONTINENTS:
        err(code, f'continent {e.get("continent")!r}')
    if e.get('subregion') not in SUBREGIONS:
        err(code, f'subregion {e.get("subregion")!r}')
    for field in FIELDS + (['quizCapital'] if 'quizCapital' in e else []):
        f = e.get(field)
        if not isinstance(f, dict) or 'value' not in f:
            err(code, f'{field} missing or not an object with value')
            continue
        if not f.get('sources'):
            err(code, f'{field} has no sources')
    if errors and errors[-1].startswith(code) and 'missing' in errors[-1]:
        continue
    pop = val(e, 'population')
    if not isinstance(pop, (int, float)) or pop <= 0:
        err(code, 'population must be a positive number')
    else:
        digits = str(int(pop)).rstrip('0')
        if len(digits) == 3 and digits.endswith('5'):
            err(code, f'population {int(pop)} sits on a compact-rounding tie')
    area = val(e, 'areaSqKm')
    if not isinstance(area, (int, float)) or area <= 0:
        err(code, 'areaSqKm must be a positive number')
    capital, quiz = val(e, 'capital') or '', val(e, 'quizCapital') or val(e, 'capital') or ''
    if not capital.strip():
        err(code, 'capital is empty')
    if capital.startswith('None'):
        err(code, 'capital "None…" is reserved for Antarctica')
    if '/' in quiz or quiz not in capital:
        err(code, f'quizCapital "{quiz}" must be one city contained in capital "{capital}"')
    if quiz in cat_quiz_capitals or quiz in seen_quiz:
        err(code, f'quizCapital "{quiz}" is already another country\'s quiz answer')
    seen_quiz[quiz] = code
    langs = val(e, 'languages')
    if not isinstance(langs, list) or not langs or not all(isinstance(l, str) and l.strip() for l in langs):
        err(code, 'languages must be a non-empty list')
    cur = val(e, 'currency') or ''
    if not CURRENCY.match(cur):
        err(code, f'currency "{cur}" is not "Name (symbol)"')
    lm = val(e, 'landmarks')
    if not isinstance(lm, list) or len(lm) != 3:
        err(code, 'landmarks must be exactly three')
    else:
        for l in lm:
            if ', ' in l or ' & ' in l:
                err(code, f'landmark "{l}" should use "Name (Place)"')
    fun = val(e, 'funFact') or ''
    if not (20 <= len(fun) <= 160):
        err(code, f'funFact is {len(fun)} chars (20–160)')
    if VOLATILE.search(fun):
        err(code, f'funFact ages badly: {VOLATILE.search(fun).group(0)!r}')
    ds = val(e, 'driveSide')
    if ds not in ('Right', 'Left'):
        err(code, f'driveSide {ds!r}')
    elif seed is not None and 'leftHand' in seed[code] and (ds == 'Left') != bool(seed[code]['leftHand']):
        err(code, f'driveSide {ds} contradicts the pinned left-hand list')
    colors = e.get('flagColors')
    if not isinstance(colors, list) or not colors:
        err(code, 'flagColors must be a non-empty list')
    flag = e.get('flag') or {}
    text = flag.get('text') or ''
    if not (150 <= len(text) <= 450):
        err(code, f'flag text is {len(text)} chars (150–450)')
    if '"' in text or '\n' in text:
        err(code, 'flag text contains a double quote or line break')
    if not REASON_WORDS.search(text):
        err(code, 'flag text does not say why')
    reasons = flag.get('reasons') or []
    if not reasons:
        err(code, 'flag has no reasons')
    for r in reasons:
        if r.get('basis') not in BASES:
            err(code, f'flag reason basis {r.get("basis")!r}')
        if not r.get('sources'):
            err(code, f'flag reason "{r.get("feature")}" has no sources')
    for label, t in (('flag text', text), ('funFact', fun)):
        if BRITISH.search(t):
            err(code, f'{label} is not American spelling: {BRITISH.search(t).group(0)!r}')
        m = JARGON.search(t)
        if m and m.group(1).lower() not in ALLOWED_JARGON.get(code, set()):
            err(code, f'{label} uses flag jargon {m.group(1)!r}')
        if REPEATED.search(t):
            err(code, f'{label} repeats a word: {REPEATED.search(t).group(0)!r}')
    if code in ('XK', 'TW') and not re.search(r'recogni|disputed', fun, re.I):
        err(code, 'funFact must carry the agreed status sentence')
    if code == 'PS':
        for field, want in PS_RULES.items():
            if val(e, field) != want:
                err(code, f'{field} must be {want!r}')
        if not cur.startswith('No currency of its own'):
            err(code, 'currency must start with "No currency of its own"')
    texts = [val(e, 'officialName') or '', capital, cur, fun, text] + list(lm or [])
    if any('israel' in t.lower() for t in texts):
        warnings.append(f'{code}: a text mentions Israel (owner rule: review)')

if seed is not None:
    missing = set(seed) - {e.get('code') for e in batch}
    if missing:
        errors.append(f'batch lacks seed codes: {sorted(missing)}')

for w in warnings:
    print('WARNING', w)
for x in errors:
    print('ERROR', x)
print(f'{len(batch)} entries, {len(errors)} errors, {len(warnings)} warnings')
sys.exit(1 if errors else 0)
