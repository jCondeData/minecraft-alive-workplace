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

--compat-cmd CMD (the nightly passes runCompatGameTest): a mutant the main command lets survive is
run once more with CMD, so code only the optional mods' compat tests reach (compat/, storage networks,
Create) isn't reported as a survivor when a compat test kills it. CMD gets its own baseline on the
real code, run the first time a survivor needs it; a kill there is listed as "killed by (compat) ...".
At most --compat-max survivors are re-run (default 6), the rest stay survivors.
A --compat-cmd that calls gradlew gets "--no-daemon -Dorg.gradle.jvmargs=-Xmx1536m" added unless it
already says --daemon/--no-daemon or sets jvmargs: a Gradle daemon reused between compat mutants gets
OOM-killed on the 7 GB container. Only javac's own error lines (Foo.java:12: error: ...), "Compilation
failed" and a failed compile task count as NO-COMPILE; the compat server log's "error: " lines don't.

--covering (the nightly passes it, B1056): each run has only the GameTest classes that name the mutated class (its simple
name as a word; when none does, the ones naming a class of the main code that uses it), at most --cover-max of them,
by leaving only those entrypoints in the test mod's fabric.mod.json for the run (put back after it, and by --restore
after a crash). The whole suite outgrew the nightly's time: a baseline took 25 minutes and a mutant 35, so every shard
timed out and the report said "0 of 0 killed". The baseline then runs the classes of all the picked mutants together; a
mutant no test class reaches is listed as NO-TEST (not run, not a kill). --compat-cmd runs get the same, from the compat
suite's fabric.mod.json. --budget SECONDS: no run starts that would likely end past it (the slowest run so far is the
guess), so a CI job ends with a report instead of being cancelled; the mutants left are listed as not run.
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
GAMETEST_JSON = "src/gametest/resources/fabric.mod.json"
COMPAT_JSON = "src/compattest/resources/fabric.mod.json"
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


def registered_tests(root, json_rel):
    """{fully qualified name: source} of the GameTest classes the test mod at json_rel registers (sources found)."""
    p = root / json_rel
    if not p.exists():
        return {}
    out = {}
    for e in json.loads(p.read_text(encoding="utf-8")).get("entrypoints", {}).get("fabric-gametest", []):
        fq = e if isinstance(e, str) else e.get("value", "")
        f = p.parent.parent / "java" / (fq.split("::")[0].replace(".", "/") + ".java")
        if fq and f.exists():
            out[fq] = f.read_text(encoding="utf-8", errors="replace")
    return out


def covering(name, tests, main_src, cap):
    """The test classes (sorted) that name class `name`, the most mentions first, at most cap; when none does, those
    naming a class of the main code (main_src: {simple name: source}) that names it. Empty: no test reaches it by name."""
    def naming(rx):
        hits = [(len(rx.findall(t)), fq) for fq, t in tests.items()]
        return sorted(fq for n, fq in sorted(hits, key=lambda h: (-h[0], h[1]))[:cap] if n)
    word = re.compile(r"\b%s\b" % re.escape(name))
    found = naming(word)
    if found:
        return found
    users = sorted(n for n, t in main_src.items() if n != name and word.search(t))
    return naming(re.compile(r"\b(?:%s)\b" % "|".join(map(re.escape, users)))) if users else []


class OnlyTests:
    """Leaves only `classes` registered in the test mod's fabric.mod.json for one run and puts the file back after (with
    a backup that --restore puts back after a crash). classes None: the file stays as it is."""

    def __init__(self, root, json_rel, classes, tag):
        self.path, self.classes, self.orig = root / json_rel, classes, None
        self.key = backup_dir(root) / f"entry-{tag}"

    def __enter__(self):
        if self.classes is None or not self.path.exists():
            return self
        self.orig = self.path.read_text(encoding="utf-8")
        shutil.copyfile(self.path, self.key.with_suffix(".orig"))
        self.key.with_suffix(".json").write_text(json.dumps(dict(path=str(self.path))))
        mod = json.loads(self.orig)
        mod["entrypoints"]["fabric-gametest"] = sorted(self.classes)
        self.path.write_text(json.dumps(mod, indent="\t") + "\n", encoding="utf-8")
        return self

    def __exit__(self, *exc):
        if self.orig is not None:
            self.path.write_text(self.orig, encoding="utf-8")
            self.key.with_suffix(".orig").unlink(missing_ok=True)
            self.key.with_suffix(".json").unlink(missing_ok=True)
        return False


def compat_gradle_flags(cmd):
    """Compat runs: no reused daemon, capped heap (B223). Other commands and explicit choices stay as given."""
    if not cmd or "gradlew" not in cmd or re.search(r"--(no-)?daemon|org\.gradle\.jvmargs", cmd):
        return cmd
    return re.sub(r"(\./gradlew)", r"\1 --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m", cmd, count=1)


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
    # javac's own lines only ("Foo.java:12: error: "): the compat suite's server log is full of mods' "- error: " warnings
    if re.search(r"Compilation failed|\.java:\d+: error: |compileJava FAILED|Execution failed for task '.*compile", out):
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


def reached_summary(root, since_time):
    """Whether this run's server logs end their tests with a summary ("All N required tests passed" or "N required tests
    failed"). A mutant that crashes the server (an exception out of a tick) leaves no summary and no failed test by name:
    that's a kill, not a survivor, even when the real code has failing tests of its own."""
    from results import SUMMARY_OK, SUMMARY_FAIL, read_text   # same folder
    logs = [l for l in list(root.glob("versions/*/build/run/*/logs/latest.log")) + list(root.glob("build/run/*/logs/latest.log"))
            if l.stat().st_mtime >= since_time]
    return all(SUMMARY_OK.search(t) or SUMMARY_FAIL.search(t) for t in map(read_text, logs))   # no server log: can't tell


def compat_run(a, root, f, orig, c, key, compat, cover=None):
    """Run a main-suite survivor once with --compat-cmd: (status, seconds, killer). cover: the compat test classes to run
    (None: the whole compat suite)."""
    secs = 0.0
    if compat["base"] is None:
        t0 = time.time()
        with OnlyTests(root, COMPAT_JSON, compat.get("union"), "compat"):
            bstatus, bsecs, tail = run(a.compat_cmd, root, a.timeout)
        a.slowest = max(a.slowest, bsecs)
        secs += bsecs
        if bstatus == "TIMEOUT":
            compat["left"] = 0
            print(f"compat baseline timed out after {a.timeout}s; survivors stay survivors", flush=True)
            return "SURVIVED", secs, ""
        if bstatus == "NO-COMPILE":
            compat["left"] = 0
            print("compat baseline doesn't build; survivors stay survivors:\n" + tail[-800:], flush=True)
            return "SURVIVED", secs, ""
        compat["base"] = (bstatus, set(failed_tests(root, t0)))
        if bstatus != "SURVIVED" and not compat["base"][1]:
            compat["left"] = 0
            print("compat baseline failed without a failing test; survivors stay survivors:\n" + tail[-800:], flush=True)
            return "SURVIVED", secs, ""
        print(f"   compat baseline on the real code: {'all pass' if bstatus == 'SURVIVED' else 'failing: ' + (', '.join(sorted(compat['base'][1])) or bstatus)} ({bsecs:.0f}s)", flush=True)
    bstatus, bfail = compat["base"]
    shutil.copyfile(f, key.with_suffix(".orig"))
    key.with_suffix(".json").write_text(json.dumps(dict(path=str(f))))
    t0 = time.time()
    try:
        lines = orig.split("\n")
        lines[c["line"] - 1] = c["new"]
        f.write_text("\n".join(lines), encoding="utf-8")
        with OnlyTests(root, COMPAT_JSON, cover, "compat"):
            status, more, _ = run(a.compat_cmd, root, a.timeout)
        a.slowest = max(a.slowest, more)
    finally:
        f.write_text(orig, encoding="utf-8")
        key.with_suffix(".orig").unlink(missing_ok=True)
        key.with_suffix(".json").unlink(missing_ok=True)
    secs += more
    if status == "NO-COMPILE":
        return "SURVIVED", secs, ""
    if status in ("KILLED", "TIMEOUT"):
        new_fail = [n for n in failed_tests(root, t0) if n not in bfail]
        crashed = status == "KILLED" and not new_fail and not reached_summary(root, t0)
        if status == "KILLED" and not new_fail and bstatus != "SURVIVED" and not crashed:
            return "SURVIVED", secs, ""      # only the compat tests that already failed on the real code
        return status, secs, "(compat) " + (", ".join(new_fail)[:220] or ("(timed out)" if status == "TIMEOUT" else "(server crashed)" if crashed else "failed"))
    return "SURVIVED", secs, ""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", nargs="?", default=".")
    ap.add_argument("--since")
    ap.add_argument("--file")
    ap.add_argument("--lines", help="a-b (with --file)")
    ap.add_argument("--max", type=int, default=6)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--shard", help="K/N: run only every Nth of the picked mutants, starting at the Kth (1-based), so N "
                                    "parallel jobs with the same --max and --seed share one set of mutants")
    ap.add_argument("--cmd", help="test command (default: runGameTest on the first node)")
    ap.add_argument("--compat-cmd", help="second test command for the main command's survivors (e.g. runCompatGameTest)")
    ap.add_argument("--compat-max", type=int, default=6, help="re-run at most this many survivors with --compat-cmd")
    ap.add_argument("--timeout", type=int, default=900)
    ap.add_argument("--covering", action="store_true", help="run only the GameTest classes that name the mutated class (B1056)")
    ap.add_argument("--cover-max", type=int, default=40, help="with --covering: at most this many test classes a mutant")
    ap.add_argument("--budget", type=int, help="seconds: start no run that would likely end past this (a CI job's time)")
    ap.add_argument("--list", action="store_true", help="only list the mutants that would run")
    ap.add_argument("--no-baseline", action="store_true", help="skip the first run on the real code (only if it is known to pass)")
    ap.add_argument("--restore", action="store_true")
    ap.add_argument("--include-client", action="store_true", help="also mutate src/client* (only useful with a client test command)")
    a = ap.parse_args()
    started = time.time()
    a.slowest = 0.0
    a.compat_cmd = compat_gradle_flags(a.compat_cmd)
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
    if a.shard:
        k, n = map(int, a.shard.split("/"))
        if not 1 <= k <= n:
            sys.exit(f"--shard {a.shard}: want K/N with 1 <= K <= N")
        chosen = chosen[k - 1::n]
        if not chosen:
            print(f"shard {a.shard}: no mutants left for this shard")
            return
    a.cmd = a.cmd or os.environ.get("MODTEST_CMD")
    if not a.cmd:
        nodes = sorted(p.name for p in (root / "versions").glob("*") if p.is_dir()) if (root / "versions").exists() else []
        a.cmd = f"./gradlew --max-workers=1 {':' + nodes[0] + ':' if nodes else ''}runGameTest"
    print(f"# Mutation spot-check: {len(chosen)} of {len(cands)} possible mutants; command: {a.cmd}\n")
    cover, ccover, union, cunion = {}, {}, None, None
    if a.covering:
        main_src = {}
        for d in main_dirs:
            for jf in d.rglob("*.java"):
                main_src[jf.stem] = main_src.get(jf.stem, "") + jf.read_text(encoding="utf-8", errors="replace")
        tests, ctests = registered_tests(root, GAMETEST_JSON), registered_tests(root, COMPAT_JSON)
        for i, c in enumerate(chosen, 1):
            cover[i] = covering(c["file"].stem, tests, main_src, a.cover_max)
            ccover[i] = covering(c["file"].stem, ctests, main_src, a.cover_max) if a.compat_cmd else []
            names = ", ".join(x.rsplit(".", 1)[-1] for x in cover[i][:6]) + (", ..." if len(cover[i]) > 6 else "")
            print(f"   {i}. {c['file'].stem}: {len(cover[i])} test class(es)" + (f", {len(ccover[i])} compat" if a.compat_cmd else "")
                  + (f" ({names})" if names else ""))
        union = sorted({x for v in cover.values() for x in v})
        cunion = sorted({x for v in ccover.values() for x in v})
        print()
    base_fail, base_status = set(), "SURVIVED"
    if not a.list and not a.no_baseline:
        # tests that already fail on the real code (e.g. a tester's bug tests) must not count as kills
        t0 = time.time()
        if union == []:
            base_status, secs, tail = "SURVIVED", 0.0, ""     # no picked mutant has a main-suite test class
        else:
            with OnlyTests(root, GAMETEST_JSON, union, "gametest"):
                base_status, secs, tail = run(a.cmd, root, a.timeout)
        a.slowest = secs
        if base_status == "TIMEOUT":
            # B1056: a baseline cut off by --timeout is a timeout, not Gradle failing
            sys.exit(f"the run on the real code timed out after {a.timeout}s ({len(union) if union is not None else 'all'} "
                     "test classes): raise --timeout or run fewer tests (--covering)")
        if base_status == "NO-COMPILE":
            sys.exit("the real code doesn't build with this command:\n" + tail[-1500:])
        base_fail = set(failed_tests(root, t0))
        if base_status != "SURVIVED" and not base_fail:
            # Gradle itself failed (a plugin not resolved, out of memory): every mutant would read as a survivor
            sys.exit("the run on the real code failed without a failing test (Gradle, not the tests?):\n" + tail[-1500:])
        print(f"0. baseline on the real code: {'all pass' if base_status == 'SURVIVED' else 'failing: ' + ', '.join(sorted(base_fail)) or base_status} ({secs:.0f}s)")
    if a.list:
        for c in chosen:
            print(f"- {c['file'].relative_to(root)}:{c['line']} {c['op']}\n    - `{c['old'].strip()}`\n    + `{c['new'].strip()}`")
        return
    results = []
    bdir = backup_dir(root)
    compat = dict(left=a.compat_max, base=None, union=cunion)
    not_run = []

    def out_of_time():
        return a.budget is not None and time.time() - started + a.slowest > a.budget

    for i, c in enumerate(chosen, 1):
        f = c["file"]
        if a.covering and not cover[i] and not ccover[i]:
            results.append((c, "NO-TEST", 0.0, ""))
            print(f"{i}. NO-TEST    {f.relative_to(root)}:{c['line']} ({c['op']}, 0s)  no GameTest class names {f.stem} "
                  "or a class that uses it", flush=True)
            continue
        if out_of_time():
            not_run = chosen[i - 1:]
            break
        orig = f.read_text(encoding="utf-8")
        key = bdir / f"m{i}"
        shutil.copyfile(f, key.with_suffix(".orig"))
        key.with_suffix(".json").write_text(json.dumps(dict(path=str(f))))
        t0 = time.time()
        try:
            lines = orig.split("\n")
            lines[c["line"] - 1] = c["new"]
            f.write_text("\n".join(lines), encoding="utf-8")
            if a.covering and not cover[i]:
                status, secs, tail = "SURVIVED", 0.0, "(no main-suite test class names it: compat only)"
            else:
                with OnlyTests(root, GAMETEST_JSON, cover.get(i), "gametest"):
                    status, secs, tail = run(a.cmd, root, a.timeout)
                a.slowest = max(a.slowest, secs)
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
            crashed = status == "KILLED" and not new_fail and not reached_summary(root, t0)
            if base_status != "SURVIVED" and not new_fail and status == "KILLED" and not crashed:
                status = "SURVIVED"          # only the tests that already failed on the real code failed
            killer = ", ".join(new_fail)[:240] or ("(timed out)" if status == "TIMEOUT" else "(server crashed)" if crashed else "")
        if status == "SURVIVED" and a.compat_cmd and compat["left"] > 0 and (not a.covering or ccover[i]) and not out_of_time():
            compat["left"] -= 1
            status, more, killer = compat_run(a, root, f, orig, c, key, compat, ccover.get(i) if a.covering else None)
            secs += more
        elif status == "SURVIVED" and a.covering and not cover[i]:
            status = "NO-TEST"       # only compat tests name it, and they weren't run (--compat-max or out of time)
        results.append((c, status, secs, killer))
        print(f"{i}. {status:10s} {f.relative_to(root)}:{c['line']} ({c['op']}, {secs:.0f}s){'  killed by ' + killer if killer else ''}", flush=True)
    surv = [r for r in results if r[1] == "SURVIVED"]
    real = [r for r in results if r[1] in ("KILLED", "SURVIVED", "TIMEOUT")]
    print(f"\nKilled {sum(1 for r in real if r[1] != 'SURVIVED')} of {len(real)} "
          f"({sum(1 for r in results if r[1] == 'NO-COMPILE')} didn't compile"
          + (f", {sum(1 for r in results if r[1] == 'NO-TEST')} reached by no test class" if a.covering else "") + ").")
    if not_run:
        print(f"\nOut of time: {len(not_run)} mutant(s) not run (--budget {a.budget}s, slowest run {a.slowest:.0f}s):")
        for c in not_run:
            print(f"- {c['file'].relative_to(root)}:{c['line']} ({c['op']})")
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
