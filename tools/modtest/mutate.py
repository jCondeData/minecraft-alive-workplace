#!/usr/bin/env python3
"""Mutation spot-check: plant small bugs in changed code and see whether the tests notice.

  mutate.py [repo] --since REV [--max 6] [--cmd "./gradlew --max-workers=1 :1.21.1:runGameTest"]
  mutate.py [repo] --file path/Foo.java [--lines 40-80] [--max 6]
  mutate.py [repo] --restore            # put back any file a killed run left mutated

Each mutant changes ONE thing on a changed line (a comparison flipped, && swapped for ||, a
negation dropped, true/false swapped in a return, a boundary constant moved by one, a
side-effect call deleted), rebuilds, runs the tests, and puts the line back. A mutant the tests
kill is fine. A SURVIVOR is a line where a bug would ship unnoticed: write a test that fails on
the mutant (the report prints the exact change) and passes on the real code.
Mutants that don't compile are skipped. The first run is on the real code: tests that already
fail there (a tester's bug tests) don't count as kills. Every run is ~1.5-3 min, so keep --max
small (4-8) in the per-change check and spread them over the riskiest files. The command usually
needs the project's Java setup: pass --cmd "export JAVA_HOME=...; ./gradlew ..." or set
MODTEST_CMD. The original file is backed up in build/modtest/mutant-backup/ before each mutant;
--restore puts it back after a crash.
"""
import argparse
import json
import os
import random
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets  # noqa: E402

OPS = [
    ("flip <=", re.compile(r"(?<=[\w)\]]) <= (?=[\w(!-])"), " < "),
    ("flip >=", re.compile(r"(?<=[\w)\]]) >= (?=[\w(!-])"), " > "),
    ("flip <", re.compile(r"(?<=[\w)\]]) < (?=[\w(!-])"), " <= "),
    ("flip >", re.compile(r"(?<=[\w)\]]) > (?=[\w(!-])"), " >= "),
    ("== to !=", re.compile(r" == "), " != "),
    ("!= to ==", re.compile(r" != "), " == "),
    ("&& to ||", re.compile(r" && "), " || "),
    ("|| to &&", re.compile(r" \|\| "), " && "),
    ("drop !", re.compile(r"(?<=[\s(])!(?=[\w(])"), ""),
    ("return true->false", re.compile(r"\breturn true;"), "return false;"),
    ("return false->true", re.compile(r"\breturn false;"), "return true;"),
    ("constant +1", re.compile(r"(?:[<>]=?|[=!]=) (\d+)(?![\w.])"), None),
    ("+ to -", re.compile(r"(?<=[\w)]) \+ (?=\d)"), " - "),
    ("- to +", re.compile(r"(?<=[\w)]) - (?=\d)"), " + "),
]
CALL_STMT = re.compile(r"^(\s*)((?:this\.|[a-z]\w*\.)*[a-z]\w*\([^;]*\);\s*)$")
SKIP_LINE = re.compile(r"^\s*(//|\*|/\*|import |package |@|LOGGER|log\.|.*\b(LOG|LOGGER)\.)")


def git(root, *args):
    return subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True).stdout


def changed_lines(root, since, main_dirs):
    """{file: set(line numbers)} of lines added/changed since REV (plus working tree)."""
    out = {}
    diff = git(root, "diff", "-U0", since, "--", *[str(d.relative_to(root)) for d in main_dirs])
    cur = None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            p = line[6:] if line.startswith("+++ b/") else None
            cur = root / p if p and p.endswith(".java") else None
        elif line.startswith("@@") and cur:
            m = re.search(r"\+(\d+)(?:,(\d+))?", line)
            start, n = int(m.group(1)), int(m.group(2) or 1)
            out.setdefault(cur, set()).update(range(start, start + n))
    return out


def candidates(path, lines):
    src = path.read_text(encoding="utf-8").split("\n")
    cands = []
    for ln in sorted(lines):
        if ln < 1 or ln > len(src):
            continue
        text = src[ln - 1]
        if SKIP_LINE.match(text) or not text.strip():
            continue
        code = re.sub(r"\"(?:\\.|[^\"\\])*\"", lambda m: "\x00" * len(m.group(0)), text)  # don't mutate strings
        code = code.split("//")[0]
        for name, rx, rep in OPS:
            for m in rx.finditer(code):
                if rep is None:
                    new = text[:m.start(1)] + str(int(m.group(1)) + 1) + text[m.end(1):]
                else:
                    new = text[:m.start()] + rep + text[m.end():]
                cands.append(dict(file=path, line=ln, op=name, old=text, new=new))
        m = CALL_STMT.match(code)
        if m and not re.match(r"\s*(return|throw|super|this\()", text) and "=" not in code.split("(")[0]:
            cands.append(dict(file=path, line=ln, op="delete call", old=text, new=m.group(1) + "/* mutant */ ;"))
    return cands


def pick(cands, n, seed):
    """Spread over files and operators: round-robin by file, then by operator."""
    rnd = random.Random(seed)
    rnd.shuffle(cands)
    by_file = {}
    for c in cands:
        by_file.setdefault(c["file"], []).append(c)
    chosen, used_ops = [], set()
    while len(chosen) < n and any(by_file.values()):
        for f in list(by_file):
            lst = by_file[f]
            if not lst:
                continue
            lst.sort(key=lambda c: c["op"] in used_ops)
            c = lst.pop(0)
            chosen.append(c)
            used_ops.add(c["op"])
            if len(chosen) >= n:
                break
    return chosen


def backup_dir(root):
    d = root / "build" / "modtest" / "mutant-backup"
    d.mkdir(parents=True, exist_ok=True)
    return d


def restore_all(root):
    d = backup_dir(root)
    n = 0
    for meta in d.glob("*.json"):
        info = json.loads(meta.read_text())
        shutil.copyfile(meta.with_suffix(".orig"), info["path"])
        meta.with_suffix(".orig").unlink()
        meta.unlink()
        n += 1
    return n


def run(cmd, root, timeout):
    t = time.time()
    try:
        r = subprocess.run(cmd, shell=True, cwd=root, capture_output=True, text=True, timeout=timeout)
        out = r.stdout + r.stderr
        code = r.returncode
    except subprocess.TimeoutExpired as e:
        return "TIMEOUT", time.time() - t, str(e.stdout or "")[-2000:]
    if code == 0:
        return "SURVIVED", time.time() - t, out[-1500:]
    if re.search(r"Compilation failed|error: |compileJava FAILED|Execution failed for task '.*compile", out):
        return "NO-COMPILE", time.time() - t, out[-1500:]
    return "KILLED", time.time() - t, out[-3000:]


def failed_tests(root, since_time):
    """Names of the tests that failed in the server logs written by this run."""
    from results import parse_log   # same folder
    names = []
    for log in root.glob("versions/*/build/run/*/logs/latest.log"):
        if log.stat().st_mtime >= since_time:
            names += list(parse_log(log)["failed"])
    for log in root.glob("build/run/*/logs/latest.log"):          # single-version projects
        if log.stat().st_mtime >= since_time:
            names += list(parse_log(log)["failed"])
    return list(dict.fromkeys(names))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", nargs="?", default=".")
    ap.add_argument("--since")
    ap.add_argument("--file")
    ap.add_argument("--lines", help="a-b (with --file)")
    ap.add_argument("--max", type=int, default=6)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--cmd", help="test command (default: runGameTest on the first node)")
    ap.add_argument("--timeout", type=int, default=900)
    ap.add_argument("--list", action="store_true", help="only list the mutants that would run")
    ap.add_argument("--no-baseline", action="store_true", help="skip the first run on the real code (only if it is known to pass)")
    ap.add_argument("--restore", action="store_true")
    ap.add_argument("--include-client", action="store_true", help="also mutate src/client* (only useful with a client test command)")
    a = ap.parse_args()
    root = repo_root(a.repo)
    left = restore_all(root)
    if left:
        print(f"restored {left} file(s) left mutated by an interrupted run")
    if a.restore:
        return
    main_dirs, _ = source_sets(root)
    if not a.include_client:   # the server test run never executes client code: check that with screenshots
        main_dirs = [d for d in main_dirs if not re.search(r"client", d.parent.name, re.I)]
    if a.file:
        f = (root / a.file).resolve()
        n = len(f.read_text().split("\n"))
        lo, hi = map(int, a.lines.split("-")) if a.lines else (1, n)
        targets = {f: set(range(lo, hi + 1))}
    elif a.since:
        targets = changed_lines(root, a.since, main_dirs)
    else:
        sys.exit("give --since REV or --file PATH")
    cands = [c for f, ls in targets.items() for c in candidates(f, ls)]
    if not cands:
        print("no mutable lines in the changed code")
        return
    chosen = pick(cands, a.max, a.seed)
    a.cmd = a.cmd or os.environ.get("MODTEST_CMD")
    if not a.cmd:
        nodes = sorted(p.name for p in (root / "versions").glob("*") if p.is_dir()) if (root / "versions").exists() else []
        a.cmd = f"./gradlew --max-workers=1 {':' + nodes[0] + ':' if nodes else ''}runGameTest"
    print(f"# Mutation spot-check: {len(chosen)} of {len(cands)} possible mutants; command: {a.cmd}\n")
    base_fail, base_status = set(), "SURVIVED"
    if not a.list and not a.no_baseline:
        # tests that already fail on the real code (e.g. a tester's bug tests) must not count as kills
        t0 = time.time()
        base_status, secs, tail = run(a.cmd, root, a.timeout)
        if base_status == "NO-COMPILE":
            sys.exit("the real code doesn't build with this command:\n" + tail[-1500:])
        base_fail = set(failed_tests(root, t0))
        print(f"0. baseline on the real code: {'all pass' if base_status == 'SURVIVED' else 'failing: ' + ', '.join(sorted(base_fail)) or base_status} ({secs:.0f}s)")
    if a.list:
        for c in chosen:
            print(f"- {c['file'].relative_to(root)}:{c['line']} {c['op']}\n    - `{c['old'].strip()}`\n    + `{c['new'].strip()}`")
        return
    results = []
    bdir = backup_dir(root)
    for i, c in enumerate(chosen, 1):
        f = c["file"]
        orig = f.read_text(encoding="utf-8")
        key = bdir / f"m{i}"
        shutil.copyfile(f, key.with_suffix(".orig"))
        key.with_suffix(".json").write_text(json.dumps(dict(path=str(f))))
        t0 = time.time()
        try:
            lines = orig.split("\n")
            lines[c["line"] - 1] = c["new"]
            f.write_text("\n".join(lines), encoding="utf-8")
            status, secs, tail = run(a.cmd, root, a.timeout)
        finally:
            f.write_text(orig, encoding="utf-8")
            key.with_suffix(".orig").unlink(missing_ok=True)
            key.with_suffix(".json").unlink(missing_ok=True)
        killer = ""
        logdir = root / "build" / "modtest" / "mutants"
        logdir.mkdir(parents=True, exist_ok=True)
        (logdir / f"m{i}.txt").write_text(f"{c['file']}:{c['line']} {c['op']}\n- {c['old']}\n+ {c['new']}\n\n{tail}")
        if status in ("KILLED", "TIMEOUT"):
            new_fail = [n for n in failed_tests(root, t0) if n not in base_fail]
            if base_status != "SURVIVED" and not new_fail and status == "KILLED":
                status = "SURVIVED"          # only the tests that already failed on the real code failed
            killer = ", ".join(new_fail)[:240] or ("(timed out)" if status == "TIMEOUT" else "")
        results.append((c, status, secs, killer))
        print(f"{i}. {status:10s} {f.relative_to(root)}:{c['line']} ({c['op']}, {secs:.0f}s){'  killed by ' + killer if killer else ''}", flush=True)
    surv = [r for r in results if r[1] == "SURVIVED"]
    real = [r for r in results if r[1] in ("KILLED", "SURVIVED", "TIMEOUT")]
    print(f"\nKilled {sum(1 for r in real if r[1] != 'SURVIVED')} of {len(real)} "
          f"({sum(1 for r in results if r[1] == 'NO-COMPILE')} didn't compile).")
    if surv:
        print("\n## Survivors: bugs no test would catch (write a test that fails on each)\n")
        for c, status, secs, _ in surv:
            print(f"- `{c['file'].relative_to(root)}:{c['line']}` ({c['op']})\n    - real:   `{c['old'].strip()}`\n    - mutant: `{c['new'].strip()}`")
    sys.exit(1 if surv else 0)


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
