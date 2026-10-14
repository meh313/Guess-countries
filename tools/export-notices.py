#!/usr/bin/env python3
"""Writes app/src/main/assets/NOTICES.txt, the third-party notices the About screen shows the player.

The source is licenses/THIRD_PARTY_NOTICES.md, the developers' file: it also says where the files came from and how
to reproduce them, which a player does not need. From each of its `## ` sections this script keeps one block: the
work's name and version, what the app uses it for, the licence's name, the copyright line and the licence text (or
a link to it, where the licence does not ask for the text itself). The libraries the app is built with are added
from LIBRARIES below.

Usage: tools/export-notices.py [--check]   (--check fails when the committed asset is out of date)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "licenses" / "THIRD_PARTY_NOTICES.md"
OUT = ROOT / "app" / "src" / "main" / "assets" / "NOTICES.txt"

# What each work in the markdown file is used for, in the player's words; the section heading is the fallback.
USED_FOR = {
    "flag-icons": "the flag images; every flag in the app is rendered from its SVG files.",
    "Natural Earth": "the world map; the country outlines, label points and capitals in world.json are simplified from its data.",
}

# Works that are not in the markdown file because nothing of theirs is copied into the repository: the libraries the
# APK is built with. Their licence asks for a copy of its text, which the link gives.
LIBRARIES = [
    {
        "name": "Android Jetpack and Kotlin libraries",
        "used_for": "the app is built with them: the screens, the database, the background work and the language runtime.",
        "licence": "Apache License 2.0",
        "copyright": "Copyright The Android Open Source Project; Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
        "url": "https://www.apache.org/licenses/LICENSE-2.0",
        "text": None,
    },
]

LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)\s]+)\)")
VERSION = re.compile(r"\b(\d+\.\d+(?:\.\d+)*)\b")
LICENCE = re.compile(
    r"under the ([A-Z][^.:\n]*?(?:Licen[cs]e|License)(?: [0-9][0-9.]*)?(?: \([A-Z]+\))?)[:.]"
    r"|\b(?:is|are) in the (public domain)\b"
)
COPYRIGHT = re.compile(r"^Copyright\b.*$", re.MULTILINE)
FENCE = re.compile(r"^```[^\n]*\n(.*?)^```", re.MULTILINE | re.DOTALL)


def parse(markdown: str) -> list[dict]:
    """One dict per `## ` section of the developers' file."""
    works = []
    sections = re.split(r"^## +", markdown, flags=re.MULTILINE)[1:]
    for section in sections:
        heading, _, body = section.partition("\n")
        heading = heading.strip()
        fence = FENCE.search(body)
        prose = body[: fence.start()] if fence else body
        prose_flat = " ".join(prose.split())
        link = LINK.search(prose_flat)
        if not link:
            raise SystemExit(f"{SOURCE}: section '{heading}' names no linked project")
        name, url = link.group(1), link.group(2)
        after_link = prose_flat[link.end():]
        version = VERSION.search(after_link)
        licence = LICENCE.search(prose_flat)
        if not licence:
            raise SystemExit(
                f"{SOURCE}: section '{heading}' does not say which licence applies "
                "('... under the X License' or '... is in the public domain')"
            )
        text = fence.group(1).strip("\n") if fence else None
        copyright_line = COPYRIGHT.search(text or "")
        works.append(
            {
                "name": name + (f" {version.group(1)}" if version else ""),
                "used_for": USED_FOR.get(name, heading[:1].lower() + heading[1:] + "."),
                "licence": licence.group(1) or "Public domain",
                "copyright": copyright_line.group(0) if copyright_line else None,
                "url": url,
                "text": text,
            }
        )
    return works


def render(works: list[dict]) -> str:
    lines = [
        "Third-party notices",
        "",
        "World Flags ships work by others. Each is listed here with what the app uses it for and the terms it comes under.",
    ]
    for work in works:
        lines += ["", work["name"], "-" * len(work["name"]), f"Used for: {work['used_for']}", f"Licence: {work['licence']}"]
        if work["copyright"]:
            lines.append(work["copyright"])
        lines.append(work["url"])
        if work["text"]:
            lines += ["", work["text"]]
        else:
            lines.append("The licence text is at that address.")
        lines.append("")
    return "\n".join(lines).rstrip("\n") + "\n"


def main() -> int:
    if not SOURCE.exists():
        print(f"{SOURCE} is missing", file=sys.stderr)
        return 1
    works = parse(SOURCE.read_text(encoding="utf-8")) + LIBRARIES
    text = render(works)
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print(f"{OUT} is out of date; run tools/export-notices.py", file=sys.stderr)
            return 1
        print(f"{OUT} is current ({len(works)} works)")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT}: {len(works)} works, {OUT.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
