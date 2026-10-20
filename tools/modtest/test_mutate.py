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


def unit_checks():
    """B223: run()'s NO-COMPILE vs KILLED classification and the compat Gradle flags (no Gradle needed)."""
    sys.path.insert(0, HERE)
    import mutate
    noise = "[Server] WARN loot modifier supplementaries:x - error: unknown condition\\n"
    cases = [
        ("a Supplementaries 'error: ' log line with a failing test is KILLED",
         f"printf '{noise}FAILED: FurnitureChoiceTest\\n'; exit 1", "KILLED"),
        ("a real javac error is NO-COMPILE",
         "printf 'src/main/java/mod/A.java:5: error: cannot find symbol\\n'; exit 1", "NO-COMPILE"),
        ("Gradle's Compilation failed is NO-COMPILE", "printf 'Compilation failed; see the error\\n'; exit 1", "NO-COMPILE"),
        ("a javac error among the log noise is NO-COMPILE",
         f"printf '{noise}A.java:9: error: ; expected\\n'; exit 1", "NO-COMPILE"),
        ("exit 0 survives even with 'error: ' in the output", f"printf '{noise}'", "SURVIVED"),
    ]
    bad = 0
    for name, cmd, want in cases:
        got = mutate.run(cmd, ".", 30)[0]
        print(("ok   " if got == want else f"FAIL (got {got}) ") + name)
        bad += got != want
    f = mutate.compat_gradle_flags
    flag = "--no-daemon -Dorg.gradle.jvmargs=-Xmx1536m"
    flag_cases = [
        ("compat gradlew gets --no-daemon and a capped heap", f("export J=1; ./gradlew --max-workers=1 runCompatGameTest"),
         f"export J=1; ./gradlew {flag} --max-workers=1 runCompatGameTest"),
        ("an explicit --daemon choice stays", f("./gradlew --daemon x"), "./gradlew --daemon x"),
        ("explicit jvmargs stay", f("./gradlew -Dorg.gradle.jvmargs=-Xmx3g x"), "./gradlew -Dorg.gradle.jvmargs=-Xmx3g x"),
        ("a non-Gradle command stays", f("true"), "true"),
        ("no command stays None", f(None), None),
    ]
    for name, got, want in flag_cases:
        print(("ok   " if got == want else f"FAIL (got {got!r}) ") + name)
        bad += got != want
    return bad


def main():
    fails = unit_checks()
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
