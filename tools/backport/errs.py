"""Sorts a compile log's errors by file: each error's line, message and the symbol javac names.

    python tools\\backport\\errs.py <compile log> [words in a file's name ...]
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace").splitlines()
want = [w.lower() for w in sys.argv[2:]]
head = re.compile(r"^(?:\s*)(.*?[\\/]dev[\\/]culprit[\\/](.*?\.java)):(\d+): error: (.*)$")
by_file = defaultdict(list)
for i, line in enumerate(log):
    m = head.match(line)
    if not m:
        continue
    detail = []
    for nxt in log[i + 1:i + 6]:
        s = nxt.strip()
        if s.startswith(("symbol:", "location:", "required:", "found:", "reason:")):
            detail.append(s)
    by_file[m.group(2).replace("\\", "/")].append((int(m.group(3)), m.group(4), "; ".join(detail)))
total = 0
for f in sorted(by_file):
    if want and not any(w in f.lower() for w in want):
        continue
    errs = sorted(set(by_file[f]))
    total += len(errs)
    print(f"== {f} ({len(errs)})")
    for line, msg, detail in errs:
        print(f"  {line}: {msg}" + (f"  [{detail}]" if detail else ""))
print(f"{total} errors in {len(by_file)} files")
