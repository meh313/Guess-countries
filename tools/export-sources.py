#!/usr/bin/env python3
"""Writes app/src/main/assets/sources.json: for every country with research or audit records, the URLs its facts
were checked against, deduplicated and in first-seen order. Countries from the first catalog (no research files)
are left out, and the app shows no Sources row for them.

Usage: tools/export-sources.py [--check]   (--check fails when the committed file is out of date)
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESEARCH = ROOT / "content" / "research"
AUDIT = ROOT / "content" / "audit"
OUT = ROOT / "app" / "src" / "main" / "assets" / "sources.json"


def collect() -> dict[str, list[str]]:
    sources: dict[str, list[str]] = {}

    def add(code: str, urls) -> None:
        bucket = sources.setdefault(code, [])
        for url in urls or []:
            if isinstance(url, str) and url.startswith("http") and url not in bucket:
                bucket.append(url)

    for path in sorted(RESEARCH.glob("*.json")):
        for entry in json.loads(path.read_text())["entries"]:
            for value in entry.values():
                if isinstance(value, dict):
                    add(entry["code"], value.get("sources"))
    for path in sorted(AUDIT.glob("*.json")):
        for fix in json.loads(path.read_text()).get("fixes", []):
            add(fix["code"], fix.get("sources"))
    return {code: urls for code, urls in sorted(sources.items()) if urls}


def main() -> int:
    data = collect()
    text = json.dumps(data, indent=0, ensure_ascii=False, separators=(",", ":")) + "\n"
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != text:
            print(f"{OUT} is out of date; run tools/export-sources.py", file=sys.stderr)
            return 1
        print(f"{OUT} is current ({len(data)} countries)")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text)
    print(f"wrote {OUT}: {len(data)} countries, {sum(len(v) for v in data.values())} URLs, {OUT.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
