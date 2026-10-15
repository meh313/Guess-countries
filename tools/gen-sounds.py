#!/usr/bin/env python3
"""Writes the three short feedback sounds of the quiz as WAV files into app/src/main/res/raw.

    python3 tools/gen-sounds.py [--check]

answer_correct: two rising sine notes (E5 then A5), 70 ms each.  answer_wrong: one low note (A3) with a hard
decay, 160 ms.  quiz_finished: a rising arpeggio (C5 E5 G5 C6), 90 ms a note.  Mono, 22.05 kHz, 16-bit, each
faded in and out over 5 ms so there is no click; about 6 to 16 KB a file. Deterministic (no randomness), so
--check can tell whether the committed files are what this script writes.
"""
import argparse
import io
import math
import struct
import sys
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_codes import REPO  # noqa: E402

OUT = REPO / 'app/src/main/res/raw'
RATE = 22050
FADE = int(RATE * 0.005)

SOUNDS = {
    # name: [(frequency Hz, seconds, gain 0..1, decay per second as a fraction kept)]
    'answer_correct': [(659.25, 0.07, 0.5, 1.0), (880.0, 0.09, 0.5, 1.0)],
    'answer_wrong': [(220.0, 0.16, 0.5, 0.04)],
    'quiz_finished': [(523.25, 0.09, 0.45, 1.0), (659.25, 0.09, 0.45, 1.0), (783.99, 0.09, 0.45, 1.0), (1046.5, 0.18, 0.45, 0.2)],
}


def note(frequency, seconds, gain, keep):
    count = int(RATE * seconds)
    out = []
    for i in range(count):
        t = i / RATE
        envelope = gain * (keep ** t)
        if i < FADE:
            envelope *= i / FADE
        if count - i <= FADE:
            envelope *= (count - i) / FADE
        # A sine with a touch of the octave above, so it reads as a chime, not a test tone.
        sample = math.sin(2 * math.pi * frequency * t) + 0.25 * math.sin(4 * math.pi * frequency * t)
        out.append(int(max(-1.0, min(1.0, envelope * sample / 1.25)) * 32767))
    return out


def render(parts):
    samples = []
    for part in parts:
        samples.extend(note(*part))
    buffer = io.BytesIO()
    with wave.open(buffer, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(struct.pack('<%dh' % len(samples), *samples))
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--check', action='store_true', help='verify the committed files without writing')
    args = parser.parse_args()
    stale = []
    for name, parts in SOUNDS.items():
        path = OUT / f'{name}.wav'
        data = render(parts)
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                stale.append(path.relative_to(REPO))
        else:
            OUT.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            print(f'wrote {path.relative_to(REPO)}: {len(data)} bytes')
    if args.check:
        for p in stale:
            print(f'{p} is not what tools/gen-sounds.py writes', file=sys.stderr)
        if not stale:
            print(f'{len(SOUNDS)} sounds in {OUT.relative_to(REPO)} are current')
        return 1 if stale else 0
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
