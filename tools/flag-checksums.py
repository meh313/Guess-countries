"""Writes app/src/test/resources/flag-checksums.txt: the SHA-256 of every bundled flag file.

FlagArtTest pins the files to these sums, so a swapped or re-rendered flag fails the suite until the
new image has been looked at and this file regenerated.
"""
import hashlib
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FLAGS = REPO / 'app/src/main/res/drawable-nodpi'
OUT = REPO / 'app/src/test/resources/flag-checksums.txt'

rows = []
for p in sorted(FLAGS.glob('flag_*.webp')):
    rows.append(f"{p.stem[len('flag_'):].upper()} {hashlib.sha256(p.read_bytes()).hexdigest()}")
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text('\n'.join(rows) + '\n')
print(len(rows), 'checksums written to', OUT.relative_to(REPO))
