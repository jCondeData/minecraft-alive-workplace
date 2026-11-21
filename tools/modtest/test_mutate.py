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
    # a mutant that crashes the server leaves a log with no summary: a kill, even when the real code has a failing test
    from pathlib import Path
    with tempfile.TemporaryDirectory() as d:
        log = Path(d, "build/run/compatGameTest/logs/latest.log")
        summary_cases = [
            ("no server log: can't tell, counts as finished", None, True),
            ("a run that passed reached its summary", "[INFO] All 55 required tests passed :)\n", True),
            ("a run with failing tests reached its summary", "[ERROR] 1 required test failed :(\n", True),
            ("a server that crashed mid-run didn't", "Caused by: java.lang.NullPointerException\nType: Game test server\n", False),
        ]
        for name, text, want in summary_cases:
            if text is not None:
                log.parent.mkdir(parents=True, exist_ok=True)
                log.write_text(text)
            got = mutate.reached_summary(Path(d), 0)
            print(("ok   " if got == want else f"FAIL (got {got!r}) ") + name)
            bad += got != want
    return bad + covering_checks()


def covering_checks():
    """B1056: --covering picks the test classes that name the mutated class (or a class using it)."""
    import mutate
    bad = 0
    tests = {"t.ATests": "A a = new A(); a.big(2); A.x();", "t.BTests": "B b;", "t.UsesTests": "Helper.go();",
             "t.Other": "Abc d;"}
    main_src = {"A": "class A {}", "Helper": "class Helper { A a; }", "Lonely": "class Lonely {}"}
    cases = [
        ("a test naming the class covers it (whole word: Abc isn't A)", mutate.covering("A", tests, main_src, 40), ["t.ATests"]),
        ("none names it: the tests naming a class that uses it", mutate.covering("Zed", tests, {**main_src, "Helper": "Zed z;"}, 40),
         ["t.UsesTests"]),
        ("no test reaches it", mutate.covering("Lonely", tests, main_src, 40), []),
        ("the cap keeps the classes naming it most",
         mutate.covering("A", {**tests, "t.More": "A A A A"}, main_src, 1), ["t.More"]),
    ]
    for name, got, want in cases:
        print(("ok   " if got == want else f"FAIL (got {got!r}) ") + name)
        bad += got != want
    return bad


MOD_JSON = '{\n\t"entrypoints": {\n\t\t"fabric-gametest": [\n\t\t\t"t.ATests",\n\t\t\t"t.OtherTests"\n\t\t]\n\t}\n}\n'
GT = "src/gametest/resources/fabric.mod.json"


def covering_runs(root):
    """End to end on the throwaway mod: the run sees only the covering class, and the file is put back."""
    os.makedirs(os.path.join(root, "src/gametest/resources"), exist_ok=True)
    os.makedirs(os.path.join(root, "src/gametest/java/t"), exist_ok=True)
    with open(os.path.join(root, GT), "w") as f:
        f.write(MOD_JSON)
    with open(os.path.join(root, "src/gametest/java/t/ATests.java"), "w") as f:
        f.write("package t; class ATests { mod.A a; }\n")
    with open(os.path.join(root, "src/gametest/java/t/OtherTests.java"), "w") as f:
        f.write("package t; class OtherTests {}\n")
    only_a = f"! grep -q OtherTests {GT} && grep -q ATests {GT}"
    cases = [
        ("--covering runs only the class naming A, and it kills the mutant",
         ["--covering", "--cmd", f"{only_a} && {CATCHES}"], 0, "KILLED"),
        ("without --covering the run sees every class", ["--cmd", f"grep -q OtherTests {GT}"], 1, "SURVIVED"),
        ("a baseline cut off by --timeout says so (not 'Gradle')", ["--cmd", "sleep 5", "--timeout", "1"], 1,
         "timed out after 1s"),
        ("--budget: no mutant starts that would end past it", ["--cmd", "sleep 2", "--budget", "3"], 0, "Out of time: 1 mutant"),
    ]
    fails = 0
    for name, args, want_code, want_text in cases:
        code, out = mutate(root, *args)
        with open(os.path.join(root, GT)) as f:
            restored = f.read() == MOD_JSON
        ok = code == want_code and want_text in out and restored
        print(("ok   " if ok else "FAIL ") + name)
        if not ok:
            fails += 1
            print(f"     exit {code} (want {want_code}), file restored: {restored}\n" + out[-1200:])
    # A mutated class no test names is NO-TEST, not run and not a kill.
    with open(os.path.join(root, "src/gametest/java/t/ATests.java"), "w") as f:
        f.write("package t; class ATests {}\n")
    code, out = mutate(root, "--covering", "--cmd", "exit 9")
    ok = code == 0 and "NO-TEST" in out and "reached by no test class" in out
    print(("ok   " if ok else "FAIL ") + "a class no test names is NO-TEST and nothing runs")
    if not ok:
        fails += 1
        print(f"     exit {code}\n" + out[-1200:])
    return fails


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
        fails += covering_runs(root)
    print(f"\n{fails} failed" if fails else "\nall passed")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
