"""Lists the texts whose English in the code (translatableWithFallback) differs from the language file, which wins.

Run after changing any text: python tools/check_lang.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
lang = json.loads((ROOT / "src/main/resources/assets/culprit/lang/en_us.json").read_text(encoding="utf-8"))
call = re.compile(r'translatableWithFallback\(\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"', re.S)
found = 0
for f in (ROOT / "src/main/java").rglob("*.java"):
    for key, fallback in call.findall(f.read_text(encoding="utf-8")):
        english = bytes(fallback, "utf-8").decode("unicode_escape")
        if key in lang and lang[key] != english:
            found += 1
            print(f"{f.name}: {key}\n  file: {lang[key]!r}\n  code: {english!r}")
print(f"{found} differ")
