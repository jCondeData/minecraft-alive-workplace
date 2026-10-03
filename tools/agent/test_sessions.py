#!/usr/bin/env python3
"""Tests for sessions.py, on throwaway local repos (nothing touches GitHub). Run after changing the helper:

  python3 tools/agent/test_sessions.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
CLEAN_ENV = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}

ROADMAP = """# Roadmap

## Bugs

- [ ] **B1** First bug. Test: `aTest`.
- [ ] **B2** Second bug.
- [ ] **B3** Third bug.
- [ ] **B4** Fourth bug.

## Milestone 21: Safety net

- [ ] **21.1** Tester set up.
- [ ] **21.2** Full run.

## Milestone 22: Builders

- [ ] **22.1** Soak test.
  More text about it.
- [ ] **22.2** Stuck recovery.
- [ ] **22.3** (blocked: owner, which one) Choice.

## Milestone 23: Visuals

- [ ] **23.1** Textures.
- [ ] **23.2** Outfits.

## Notes / blocked

- (Sessions: notes go here.)
"""

failures = []


def sh(cmd, cwd, env=None, ok=True):
    r = subprocess.run(cmd, cwd=cwd, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       env={**CLEAN_ENV, **(env or {})})
    if ok is True and r.returncode:
        raise SystemExit(f"FAILED ({r.returncode}): {cmd}\n{r.stdout}")
    return r


def check(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        failures.append(name)
        if detail:
            print("     " + detail.strip().replace("\n", "\n     "))


def main():
    tmp = tempfile.mkdtemp(prefix="sessions-test-")
    try:
        run(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"\n{len(failures)} failed" if failures else "\nall passed")
    sys.exit(1 if failures else 0)


def run(tmp):
    sh("git init -q --bare -b main origin.git", tmp)
    seed = os.path.join(tmp, "seed")
    sh(f"git clone -q {tmp}/origin.git seed", tmp)
    os.makedirs(os.path.join(seed, "tools/agent"))
    shutil.copy(os.path.join(HERE, "sessions.py"), os.path.join(seed, "tools/agent/sessions.py"))
    with open(os.path.join(seed, "ROADMAP.md"), "w") as f:
        f.write(ROADMAP)
    with open(os.path.join(seed, "CHANGELOG.md"), "w") as f:
        f.write("# Changelog\n\n## Unreleased\n")
    with open(os.path.join(seed, ".gitattributes"), "w") as f:
        f.write("CHANGELOG.md merge=union\n")
    with open(os.path.join(seed, "Code.java"), "w") as f:
        f.write("v1\n")
    sh("git -c user.email=s@x -c user.name=s add -A && git -c user.email=s@x -c user.name=s commit -qm init "
       "&& git push -q origin main", seed)
    for n in ("A", "B", "C"):
        sh(f"git clone -q {tmp}/origin.git {n} && cd {n} && git config user.email {n}@x && git config user.name {n}",
           tmp)
    A, B, C = (os.path.join(tmp, n) for n in "ABC")

    def S(where, args, ok=True, env=None):
        return sh(f"python3 tools/agent/sessions.py {args}", where, env=env, ok=ok)

    def roadmap():
        sh("git fetch -q origin", A)
        return sh("git show origin/main:ROADMAP.md", A).stdout

    def line(item):
        return next((ln for ln in roadmap().splitlines() if f"**{item}**" in ln), "")

    # Claims and the one-session-per-milestone rule.
    r = S(A, "claim 22.1 --as chat")
    check("claim puts you on the item branch", "item/22.1" in sh("git branch --show-current", A).stdout, r.stdout)
    r = S(B, "claim 22.1 --as night-1", ok=None)
    check("a live claim can't be claimed again", r.returncode == 1 and "claimed by chat" in r.stdout, r.stdout)
    r = S(B, "claim 22.2 --as night-1", ok=None)
    check("a busy milestone is refused", r.returncode == 1 and "working in M22" in r.stdout, r.stdout)
    r = S(B, "claim 22.3 --as night-1", ok=None)
    check("a blocked item is refused", r.returncode == 1 and "blocked" in r.stdout, r.stdout)
    r = S(B, "status --as night-1")
    check("status names the next free item", "Next item for you: B1" in r.stdout, r.stdout)

    # Two sessions claim the same item at the same instant: the pre-push hook lets C win first.
    hook = os.path.join(B, ".git/hooks/pre-push")
    with open(hook, "w") as f:
        f.write(f"#!/bin/sh\n[ -f {tmp}/hook.done ] && exit 0\ntouch {tmp}/hook.done\n"
                f"cd {C} && env -u GIT_DIR -u GIT_WORK_TREE -u GIT_INDEX_FILE "
                f"python3 tools/agent/sessions.py claim 23.1 --as chat2 >/dev/null 2>&1\nexit 0\n")
    os.chmod(hook, 0o755)
    r = S(B, "claim 23.1 --as night-1", ok=None)
    os.remove(hook)
    check("simultaneous claims: the second one backs off", r.returncode == 1 and "chat2" in r.stdout
          and "chat2" in line("23.1") and "night-1" not in line("23.1"), r.stdout + line("23.1"))

    # Same race, different items in one milestone (reviewer finding 4).
    with open(hook, "w") as f:
        f.write(f"#!/bin/sh\n[ -f {tmp}/hook2.done ] && exit 0\ntouch {tmp}/hook2.done\n"
                f"cd {C} && env -u GIT_DIR -u GIT_WORK_TREE -u GIT_INDEX_FILE "
                f"python3 tools/agent/sessions.py claim 21.1 --as chat2 >/dev/null 2>&1\nexit 0\n")
    os.chmod(hook, 0o755)
    r = S(B, "claim 21.2 --as night-1", ok=None)
    os.remove(hook)
    check("simultaneous claims in one milestone: the second one backs off",
          r.returncode == 1 and "night-1" not in line("21.2"), r.stdout + line("21.2"))

    # Landing, with a push race and a changelog both sides edit.
    sh("git switch -q main && git pull -q", B)
    S(B, "claim B1 --as night-1")
    sh("printf 'fix\\n' > fix.txt && sed -i 's/## Unreleased/## Unreleased\\n- B1 fixed./' CHANGELOG.md && "
       "git add fix.txt CHANGELOG.md && git commit -qm 'Fix B1' && git push -q -u origin item/B1", B)
    sh("printf 'soak\\n' > Code.java && sed -i 's/## Unreleased/## Unreleased\\n- Soak test./' CHANGELOG.md && "
       "git add Code.java CHANGELOG.md && git commit -qm 'Add soak' && git push -q -u origin item/22.1", A)
    race = os.path.join(tmp, "race.sh")
    with open(race, "w") as f:
        f.write(f"#!/bin/sh\nif [ ! -f {tmp}/race.done ]; then touch {tmp}/race.done; cd {B} && "
                f"python3 tools/agent/sessions.py land B1 --as night-1 --build true >/dev/null; fi\n")
    os.chmod(race, 0o755)
    r = S(A, f"land 22.1 --as chat --build {race}")
    log = sh("git log --format=%s origin/main", A).stdout
    cl = sh("git show origin/main:CHANGELOG.md", A).stdout
    check("land survives another landing mid-build", "main moved" in r.stdout and "[x] **22.1** (review: pending"
          in line("22.1") and "[x] **B1** (review: pending" in line("B1"), r.stdout)
    check("both changelog lines are kept", "B1 fixed" in cl and "Soak test" in cl, cl)
    check("landed branches are deleted", "item/22.1" not in sh("git ls-remote origin", A).stdout)
    check("commits say which session made them", "Claim 22.1 [chat]" in log, log)

    # Conflicts and failing builds stop land with the right exit codes.
    sh("git switch -q main && git pull -q", A)
    sh("git switch -q main && git pull -q", B)
    S(A, "claim B2 --as chat")
    sh("printf 'A\\n' > Code.java && git commit -qam 'A edits'", A)
    S(B, "claim B3 --as night-2")
    sh("printf 'B\\n' > Code.java && git commit -qam 'B edits'", B)
    S(A, "land B2 --as chat --build true")
    r = S(B, "land B3 --as night-2 --build true", ok=None)
    check("a conflict stops land with exit 3", r.returncode == 3, r.stdout)
    sh("git merge -q origin/main; printf 'A+B\\n' > Code.java && git add Code.java && git commit -qm merged", B)
    r = S(B, "land B3 --as night-2 --build false", ok=None)
    check("a failing build stops land with exit 4", r.returncode == 4, r.stdout)

    # Pause, and the next session continues the branch.
    r = S(B, "pause B3 --as night-2 --note 'done: merged; next: fix the build (it needs X)'")
    check("pause frees the item", "(paused: item/B3" in line("B3"), line("B3"))
    sh("git switch -q main 2>/dev/null; git pull -q", C)
    r = S(C, "claim B3 --as chat2")
    check("the next session continues from the paused branch", "next: fix the build" in r.stdout, r.stdout)

    # A night claim goes stale after 75 minutes, a chat claim after 6 hours (reviewer finding 1).
    sh("git switch -q main && git pull -q", B)
    S(B, "claim 22.2 --as night-3")
    sh("git switch -q main && git pull -q && sed -i 's/(claimed: night-3, [^)]*)/(claimed: night-3, "
       "2020-01-01 00:00Z)/' ROADMAP.md && git commit -qam age && git push -q origin main", C)
    r = S(A, "status --as chat")
    check("an old night claim is stale", "STALE" in r.stdout and "22.2" in r.stdout, r.stdout)
    sh("git switch -q main && git pull -q", A)
    r = S(A, "claim 22.2 --as chat")
    check("a stale claim can be taken over", "Took over from night-3" in r.stdout, r.stdout)
    r = S(B, "land 22.2 --as night-3 --build true", ok=None)
    check("land refuses a claim that was taken over (exit 2)", r.returncode == 2, r.stdout)

    # Re-claiming keeps unpushed local commits (reviewer finding 3).
    sh("printf 'local\\n' > local.txt && git add local.txt && git commit -qm 'local only'", A)
    sh("git switch -q main", A)
    r = S(A, "claim 22.2 --as chat")
    check("re-claiming keeps local commits", "local only" in sh("git log --format=%s -3", A).stdout, r.stdout)

    # land refuses when someone pushed to the item branch meanwhile (reviewer finding 2).
    sh("git push -q -u origin item/22.2", A)
    sh("git fetch -q && git switch -q -c other origin/item/22.2 && printf 'x\\n' > x.txt && git add x.txt && "
       "git commit -qm 'their work' && git push -q origin HEAD:item/22.2", B)
    r = S(A, "land 22.2 --as chat --build true", ok=None)
    check("land refuses to drop another session's pushed work", r.returncode == 3 and "commits you don't have"
          in r.stdout, r.stdout)

    # Work with nothing to see is accepted without a review.
    sh("git switch -q main 2>/dev/null; git pull -q", C)
    S(C, "claim 21.1 --as chat2 --force")
    sh("printf 't\\n' > t.txt && git add t.txt && git commit -qm 'Add tests'", C)
    r = S(C, "land 21.1 --as chat2 --build true --no-review")
    check("--no-review accepts it automatically", "[x] **21.1** (approved auto " in line("21.1"), r.stdout + line("21.1"))

    # A shallow clone (as the scheduled runs make) still works.
    sh(f"git clone -q --depth 1 file://{tmp}/origin.git D && cd D && git config user.email d@x && git config user.name d",
       tmp)
    r = S(os.path.join(tmp, "D"), "status --as night-9")
    check("status works in a shallow clone", "Next item for you" in r.stdout, r.stdout)

    # Owner replies, bugs, handoffs, unblock.
    sh("git switch -q main 2>/dev/null; git pull -q", C)
    S(C, "reply 'approve 22.1, B1' --as chat2")
    check("approve", "(approved " in line("22.1") and "(approved " in line("B1"))
    S(C, "reply 'veto 22.1: 1) the chart is unreadable 2) too big (way too big)' --as chat2")
    check("veto keeps parentheses out of the mark", line("22.1").startswith("- [ ] **22.1** (vetoed ")
          and "[way too big]" in line("22.1") and "More text" in roadmap(), line("22.1"))
    S(C, "reply 'change 22.1: smaller' --as chat2")
    S(C, "reply 'change 22.1a: even smaller' --as chat2")
    rm = roadmap()
    check("changes become sub-items with the next letter", "**22.1a** Change from the owner" in rm and
          "**22.1b** Change from the owner" in rm and "22.1aa" not in rm, rm)
    S(C, "bug '(client) crash on join' --as chat2")
    check("bugs get the next number", "- [ ] **B5** [client] crash on join" in roadmap(), roadmap())
    S(C, "handoff 'first' --as night-0930-0100")
    S(C, "handoff 'second' --as night-0930-0300")
    rm = roadmap()
    check("one handoff per kind", "second" in rm and ": first" not in rm, rm)
    S(C, "unblock 22.3 --as chat2 --note 'owner chose'")
    check("unblock", "(blocked" not in line("22.3"), line("22.3"))

    # QA lane: landed items wait for verification; verify marks them; a veto clears it.
    sh("git switch -q main 2>/dev/null; git pull -q", C)
    r = S(C, "status --as qa-1003-0100")
    check("QA status lists landed, unverified items", "Next for you (QA):" in r.stdout and "B1" in r.stdout, r.stdout)
    S(C, "verify B1 21.1 --as qa-1003-0100 --note 'spec tests (3), mutants 2/2'")
    check("verify marks items", "(verified " in line("B1") and "[3]" in line("B1") and "(verified " in
          line("21.1"), line("B1"))
    r = S(C, "verify 23.2 --as qa-1003-0100", ok=None)
    check("an open item can't be verified", r.returncode == 1, r.stdout)
    r = S(C, "show 22.1 B1")
    check("show prints the items and their milestones", "## Milestone 22" in r.stdout and "More text about it" in
          r.stdout and "**B1**" in r.stdout, r.stdout)

    # The QA lane ships tests; review packages go to the reviews branch.
    sh("git switch -q -c qa/tests-1003 origin/main && printf 'test\\n' > QaTest.java && git add QaTest.java && "
       "git commit -qm 'QA tests'", C)
    r = S(C, "ship --as qa-1003-0100 --build true")
    check("ship lands a QA branch on main", "Shipped" in r.stdout and "QaTest.java" in sh("git ls-tree --name-only "
          "origin/main", C).stdout, r.stdout)
    sh("printf 'png' > sheet.png", tmp)
    r = S(C, f"review 22.1 {tmp}/sheet.png --as lane-a-1003-0100 --message 'Review 22.1: soak test'")
    S(C, f"review 23.2 {tmp}/sheet.png --as lane-b-1003-0100 --message 'Review 23.2'")
    sh("git fetch -q origin reviews", C)
    files = sh("git ls-tree -r --name-only FETCH_HEAD", C).stdout
    check("review packages land on the reviews branch", "reviews/22.1/sheet.png" in files and
          "reviews/23.2/message.md" in files and "ROADMAP.md" not in files, r.stdout + files)
    sh("git switch -q main 2>/dev/null; git pull -q", C)

    r = S(C, "brief")
    check("brief leaves the milestones out", "## Bugs" in r.stdout and "## Notes" in r.stdout and "**22.1**" not in
          r.stdout, r.stdout)
    b1 = next((l for l in r.stdout.splitlines() if "**B1**" in l), "")
    check("brief shortens fixed bugs to one line", b1.startswith("- [x] **B1**") and "verified" not in b1, r.stdout)

    # A lane's claim goes stale like a night run's; a session holds at most three claims.
    S(C, "bug 'sixth' --as chat2")
    S(C, "bug 'seventh' --as chat2")
    sh("git switch -q main 2>/dev/null; git pull -q", A)
    S(A, "claim B7 --as lane-b-1003-0100")
    sh("git switch -q main && git pull -q && sed -i 's/(claimed: lane-b-1003-0100, [^)]*)/(claimed: lane-b-1003-0100, "
       "2020-01-01 00:00Z)/' ROADMAP.md && git commit -qam age && git push -q origin main", C)
    r = S(B, "status --as chat")
    check("an old lane claim is stale", "STALE" in next((l for l in r.stdout.splitlines() if "B7" in l), ""),
          r.stdout)
    sh("git switch -q main 2>/dev/null; git pull -q", B)
    for item in ("B4", "B5", "B6"):
        S(B, f"claim {item} --as lane-c-1003-0100")
        sh("git switch -q main 2>/dev/null", B)
    S(C, "bug 'eighth' --as chat2")
    r = S(B, "claim B8 --as lane-c-1003-0100", ok=None)
    check("a fourth live claim is refused", r.returncode == 1 and "at most 3" in r.stdout, r.stdout)

    # Bookkeeping on main during a build (a claim, a bug) doesn't make land build again (B16).
    S(C, "bug 'ninth' --as chat2")
    sh("git switch -q main 2>/dev/null; git pull -q", A)
    S(A, "claim B9 --as chat3")
    sh("printf 'x\\n' > Other.java && git add Other.java && git commit -qm 'Other' && git push -q -u origin item/B9", A)
    count = os.path.join(tmp, "builds")
    bump = os.path.join(tmp, "bump.sh")
    with open(bump, "w") as f:
        f.write(f"#!/bin/sh\necho b >> {count}\nif [ ! -f {tmp}/bump.done ]; then touch {tmp}/bump.done; cd {B} && "
                f"git switch -q main 2>/dev/null; git pull -q; python3 tools/agent/sessions.py bug 'mid-build' "
                f"--as night-5 >/dev/null; fi\n")
    os.chmod(bump, 0o755)
    r = S(A, f"land B9 --as chat3 --build {bump}")
    with open(count) as f:
        builds = len(f.read().split())
    check("a roadmap-only change on main mid-build doesn't build again", builds == 1 and "not building again" in
          r.stdout and "[x] **B9**" in line("B9") and "mid-build" in roadmap(), r.stdout)

    # QA (qa-1003-1833, B16): the other side of the boundary. A code change on main mid-build, even one that comes
    # with a ROADMAP.md change, still means a second build: the green build didn't test it.
    S(C, "bug 'eleventh' --as chat2")
    sh("git switch -q main 2>/dev/null; git pull -q", A)
    S(A, "claim B11 --as qa-9")
    sh("printf 'y\\n' > Mine.java && git add Mine.java && git commit -qm 'Mine' && git push -q -u origin item/B11", A)
    count2 = os.path.join(tmp, "builds2")
    bump2 = os.path.join(tmp, "bump2.sh")
    with open(bump2, "w") as f:
        f.write(f"#!/bin/sh\necho b >> {count2}\nif [ ! -f {tmp}/bump2.done ]; then touch {tmp}/bump2.done; cd {B} && "
                f"git switch -q main 2>/dev/null; git pull -q; python3 tools/agent/sessions.py bug 'code-mid-build' "
                f"--as night-6 >/dev/null; printf 'z\\n' > Theirs.java; git add Theirs.java; git commit -qm Theirs; git pull -q --rebase; "
                f"git push -q; fi\ntrue\n")
    os.chmod(bump2, 0o755)
    r = S(A, f"land B11 --as qa-9 --build {bump2}")
    with open(count2) as f:
        builds = len(f.read().split())
    check("a code change on main mid-build is built again", builds == 2 and "not building again" not in r.stdout
          and "[x] **B11**" in line("B11"), r.stdout + f" builds={builds}")

    # QA (qa-1003-1833, B16): ship gets the same rule as land: a roadmap-only change mid-build, one build.
    sh("git switch -q main 2>/dev/null; git pull -q; git switch -q -c qa/b16-1003 origin/main && printf 't\\n' > "
       "QaB16.java && git add QaB16.java && git commit -qm 'QA B16'", A)
    count3 = os.path.join(tmp, "builds3")
    bump3 = os.path.join(tmp, "bump3.sh")
    with open(bump3, "w") as f:
        f.write(f"#!/bin/sh\necho b >> {count3}\nif [ ! -f {tmp}/bump3.done ]; then touch {tmp}/bump3.done; cd {B} && "
                f"git switch -q main 2>/dev/null; git pull -q; python3 tools/agent/sessions.py bug 'ship-mid-build' "
                f"--as night-7 >/dev/null; fi\ntrue\n")
    os.chmod(bump3, 0o755)
    r = S(A, f"ship --as qa-1003-0200 --build {bump3}")
    with open(count3) as f:
        builds = len(f.read().split())
    check("ship: a roadmap-only change on main mid-build doesn't build again", builds == 1 and "Shipped" in r.stdout
          and "not building again" in r.stdout and "ship-mid-build" in roadmap(), r.stdout + f" builds={builds}")

    # Nothing left behind: no stray worktrees.
    check("no stray worktrees", len(sh("git worktree list", C).stdout.strip().splitlines()) == 1)


if __name__ == "__main__":
    main()
