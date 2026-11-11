#!/usr/bin/env python3
"""Every GameTest batch belongs to one test class (B1166).

Tests in one batch run side by side, and a class's Leftovers.clear(helper) or village() setup removes whatever stands
in its batch's area: when two classes shared a batch, one's setup took the other's villagers or hall away (B427,
B428). So a batch name may be used by one class only.

  batchcheck.py [repo]     check; exit 1 with one line per problem

Batch names are read from @GameTest(batch = ...) and from the Stonecutter swap line above it
(`//$ gametest_batch AREA 'x'`, `//$ gametest_ticks_batch AREA '100' 'x'`). A batch is a string literal, a String
constant of the class (`static final String BATCH = "..."`, also `OtherTests.BATCH`) or a concatenation of those.
What is checked, in each test source set separately (src/gametest and src/compattest run in different servers):

  - no batch name is used by two classes;
  - the swap line and the annotation under it name the same batch (the swap rewrites the annotation per node);
  - every batch can be read (an expression this script doesn't understand is a problem, never skipped).

Tests without a batch share vanilla's default batch on purpose; they are not checked.
"""
import bisect
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets, java_files, read  # noqa: E402

CONST = re.compile(r'\bstatic\s+final\s+String\s+(\w+)\s*=\s*((?:"[^"\n]*"|[^;"\n])+);')
ANNOTATION = re.compile(r"@GameTest\s*\(([^)]*)\)", re.S)
BATCH_ARG = re.compile(r'\bbatch\s*=\s*((?:"[^"]*"|[^,)"])+)')
SWAP = re.compile(r"^\s*//\$\s+(gametest\w*)\s+(.*)$")
SWAP_ARG = re.compile(r"'([^']*)'|(\S+)")
PART = re.compile(r'"([^"]*)"|(\w+(?:\.\w+)?)')


def evaluate(expr, own, everyone):
    """'BATCH + "Walk"' -> 'qaSentryLeash1108dWalk', or None when a part can't be resolved."""
    out = []
    for part in [p.strip() for p in expr.split("+")]:
        m = PART.fullmatch(part)
        if not m:
            return None
        if m.group(1) is not None:
            out.append(m.group(1))
            continue
        name = m.group(2)
        if "." in name:
            cls, field = name.rsplit(".", 1)
            value = everyone.get(cls, {}).get(field)
        else:
            value = own.get(name)
        if value is None:
            return None
        out.append(value)
    return "".join(out)


def constants(text):
    """The file's String constants whose value is literal (one pass for constants built from other constants)."""
    raw = {m.group(1): m.group(2) for m in CONST.finditer(text)}
    values = {}
    for _ in range(3):
        for name, expr in raw.items():
            if name not in values:
                v = evaluate(expr, values, {})
                if v is not None:
                    values[name] = v
    return values


def check_set(test_dir, root):
    files = [(f, read(f)) for f in java_files([test_dir])]
    everyone = {f.stem: constants(text) for f, text in files}
    users = defaultdict(set)   # batch -> {class}
    problems = []
    for f, text in files:
        own = everyone[f.stem]
        rel = f.relative_to(root)
        lines = text.split("\n")
        starts = [0]
        for line in lines:
            starts.append(starts[-1] + len(line) + 1)
        for m in ANNOTATION.finditer(text):
            lineno = bisect.bisect_right(starts, m.start()) - 1  # 0-based
            b = BATCH_ARG.search(m.group(1))
            batch = None
            if b:
                batch = evaluate(b.group(1), own, everyone)
                if batch is None:
                    problems.append(f"{rel}:{lineno + 1}: can't read the batch {b.group(1).strip()}")
                    continue
                users[batch].add(f.stem)
            swap = SWAP.match(lines[lineno - 1]) if lineno > 0 else None
            if swap and swap.group(1).endswith("_batch"):
                args = [a or b2 for a, b2 in SWAP_ARG.findall(swap.group(2))]
                said = evaluate(args[-1], own, everyone) if args else None
                if said is None:
                    problems.append(f"{rel}:{lineno}: can't read the swap line's batch {args[-1] if args else ''}")
                elif said != batch:
                    other = f'says "{batch}"' if batch is not None else "has none"
                    problems.append(f'{rel}:{lineno}: the swap line says batch "{said}", the annotation {other}')
            elif swap and batch is not None:
                problems.append(f"{rel}:{lineno}: the annotation has batch \"{batch}\" but the swap line "
                                f"{swap.group(1)} has none")
    for batch, classes in sorted(users.items()):
        if len(classes) > 1:
            problems.append(f"{test_dir.relative_to(root)}: batch \"{batch}\" is used by {len(classes)} classes: "
                            f"{', '.join(sorted(classes))} (give each class its own batch name)")
    return problems, len(users)


def main():
    root = repo_root(sys.argv[1] if len(sys.argv) > 1 else ".")
    _, tests = source_sets(root)
    problems, batches = [], 0
    for d in tests:
        p, n = check_set(d, root)
        problems += p
        batches += n
    for p in problems:
        print(p)
    if problems:
        print(f"batchcheck: {len(problems)} problem(s)")
        return 1
    print(f"batchcheck: OK ({batches} batches, each in one class)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
