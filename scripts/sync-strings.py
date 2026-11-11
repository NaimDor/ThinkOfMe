#!/usr/bin/env python3
"""Generate the iOS String Catalog from the Android string resources.

Android's core/ui strings are the single source of truth for UI copy in all
launch languages (en, he, ar, ru). This script converts them, including
plurals and positional format arguments, into ios/App/Resources/Localizable.xcstrings.

    python3 scripts/sync-strings.py          # write
    python3 scripts/sync-strings.py --check  # fail if out of date (CI)
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android/core/ui/src/main/res"
OUT = ROOT / "ios/App/Resources/Localizable.xcstrings"
LANGS = {"values": "en", "values-iw": "he", "values-ar": "ar", "values-ru": "ru"}

# iOS-only strings (Android shows these through system UI).
EXTRA = {
    "continue_apple": {"en": "Continue with Apple", "he": "המשך עם Apple", "ar": "المتابعة باستخدام Apple", "ru": "Продолжить с Apple"},
    "tracking_reason": {
        "en": "Lets us show ads that are more relevant to you. ThinkOfMe works the same either way.",
        "he": "מאפשר להציג מודעות רלוונטיות יותר. ThinkOfMe עובדת אותו דבר בכל מקרה.",
        "ar": "يتيح لنا عرض إعلانات أكثر ملاءمة لك. يعمل ThinkOfMe بالطريقة نفسها في كل الأحوال.",
        "ru": "Позволяет показывать более подходящую рекламу. ThinkOfMe работает одинаково в любом случае.",
    },
}


def convert(text: str) -> str:
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    text = re.sub(r"%(\d+\$)?d", lambda m: f"%{m.group(1) or ''}lld", text)
    text = re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", text)
    return text


def unit(value: str) -> dict:
    return {"stringUnit": {"state": "translated", "value": value}}


def load():
    strings: dict[str, dict] = {}
    for folder, lang in LANGS.items():
        root = ET.parse(RES / folder / "strings.xml").getroot()
        for el in root:
            name = el.get("name")
            if el.tag == "string":
                strings.setdefault(name, {})[lang] = unit(convert(el.text or ""))
            elif el.tag == "plurals":
                forms = {item.get("quantity"): convert(item.text or "") for item in el}
                if any("%2$" in v for v in forms.values()):
                    # Multi-argument plural: vary by the first argument via a substitution.
                    variations = {q: unit(v.replace("%1$lld", "%arg")) for q, v in forms.items()}
                    strings.setdefault(name, {})[lang] = {
                        "stringUnit": {"state": "translated", "value": "%#@count@"},
                        "substitutions": {
                            "count": {"argNum": 1, "formatSpecifier": "lld", "variations": {"plural": variations}}
                        },
                    }
                else:
                    strings.setdefault(name, {})[lang] = {
                        "variations": {"plural": {q: unit(v) for q, v in forms.items()}}
                    }
    for key, values in EXTRA.items():
        strings[key] = {lang: unit(value) for lang, value in values.items()}
    return strings


def build() -> str:
    catalog = {
        "sourceLanguage": "en",
        "strings": {
            key: {"extractionState": "manual", "localizations": dict(sorted(locs.items()))}
            for key, locs in sorted(load().items())
        },
        "version": "1.0",
    }
    return json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"


if __name__ == "__main__":
    content = build()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != content:
            print(f"{OUT} is out of date. Run: python3 scripts/sync-strings.py", file=sys.stderr)
            sys.exit(1)
        print("strings up to date")
    else:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(content, encoding="utf-8")
        print(f"wrote {OUT.relative_to(ROOT)}")
