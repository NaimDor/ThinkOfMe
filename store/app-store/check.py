#!/usr/bin/env python3
"""Checks the App Store listing text against Apple's limits (run in CI)."""
import sys
from pathlib import Path

LIMITS = {"name": 30, "subtitle": 30, "promotional_text": 170, "description": 4000}
KEYWORD_BYTES = 100
root = Path(__file__).resolve().parent / "metadata"
problems = []
for locale in sorted(p.name for p in root.iterdir() if p.is_dir()):
    for field, limit in LIMITS.items():
        text = (root / locale / f"{field}.txt").read_text().strip()
        if not text or len(text) > limit:
            problems.append(f"{locale}/{field}: {len(text)} characters (limit {limit})")
    keywords = (root / locale / "keywords.txt").read_text().strip()
    if len(keywords.encode()) > KEYWORD_BYTES or ", " in keywords:
        problems.append(f"{locale}/keywords: {len(keywords.encode())} bytes (limit {KEYWORD_BYTES}, no spaces after commas)")
    print(f"{locale}: " + ", ".join(f"{f} {len((root / locale / f'{f}.txt').read_text().strip())}" for f in LIMITS) + f", keywords {len(keywords.encode())}B")
if problems:
    print("\n".join(problems))
    sys.exit(1)
