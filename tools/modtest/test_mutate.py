#!/usr/bin/env python3
"""Tests for mutate.py's --compat-cmd, on a throwaway one-file mod with shell commands for test suites
(no Gradle). Run after changing the mutation tool:

  python3 tools/modtest/test_mutate.py
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = "src/main/java/mod/A.java"
CODE = "package mod;\n\nclass A {\n\tboolean big(int x) {\n\t\tif (x > 1) {\n\t\t\treturn true;\n\t\t}\n\t\treturn false;\n\t}\n}\n"
# a "suite" that passes on the real line and fails on any mutant of it
CATCHES = f"grep -q 'if (x > 1) {{' {SRC}"


def mutate(root, *args):
    r = subprocess.run([sys.executable, os.path.join(HERE, "mutate.py"), root, "--file", SRC, "--lines", "5-5",
                        "--max", "1", *args], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    fails = 0
    with tempfile.TemporaryDirectory() as root:
        os.makedirs(os.path.join(root, "src/main/java/mod"))
        with open(os.path.join(root, SRC), "w") as f:
            f.write(CODE)
        subprocess.run(["git", "init", "-q", root], check=True)
        cases = [
            ("no compat command: the main suite's survivor stays one", ["--cmd", "true"], 1, "SURVIVED"),
            ("the compat suite kills it", ["--cmd", "true", "--compat-cmd", CATCHES], 0, "killed by (compat) failed"),
            ("the compat suite misses it too", ["--cmd", "true", "--compat-cmd", "true"], 1, "SURVIVED"),
            ("compat failing on the real code too isn't a kill", ["--cmd", "true", "--compat-cmd", "false"], 1,
             "compat baseline failed without a failing test"),
            ("a main run that fails with no test named stops the check", ["--cmd", "false"], 1,
             "failed without a failing test (Gradle"),
            ("--compat-max 0 re-runs nothing", ["--cmd", "true", "--compat-cmd", CATCHES, "--compat-max", "0"], 1, "SURVIVED"),
            ("the main suite's own kill never runs the compat suite", ["--cmd", CATCHES, "--compat-cmd", "exit 7"], 0, "KILLED"),
        ]
        for name, args, want_code, want_text in cases:
            code, out = mutate(root, *args)
            with open(os.path.join(root, SRC)) as f:
                restored = f.read() == CODE
            ok = code == want_code and want_text in out and restored
            if name.startswith("the main suite's own kill"):
                ok = ok and "compat baseline" not in out
            print(("ok   " if ok else "FAIL ") + name)
            if not ok:
                fails += 1
                print(f"     exit {code} (want {want_code}), file restored: {restored}\n" + out[-1200:])
    print(f"\n{fails} failed" if fails else "\nall passed")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
