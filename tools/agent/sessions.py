#!/usr/bin/env python3
"""Coordinate the sessions that work on this repo (the owner's chat and the scheduled night runs).

The roadmap on origin/main is the shared state. Every status change goes straight to main as its own small
commit, re-checked against the newest main each time, and git refuses the second of two simultaneous pushes,
so two sessions can never both claim one item or one milestone. Work itself lives on a branch item/<id> and
reaches main only through `land`, which merges the newest main in and builds first, so main stays green.
Nothing here rebases or force-pushes.

  status [--as NAME]                         who is working on what, and what to take next
  claim ID --as NAME [--force]               claim an item and switch to its branch
  land ID --as NAME [--keep-open | --no-review]   merge main in, build, tick "review: pending", push to main
  brief                                      print what every session reads first: the rules, Bugs, decisions, Notes
  show ID [ID...]                            print an item (and its milestone's heading) from origin/main
  verify ID [ID...] --as NAME [--note TEXT]  the QA lane: mark landed items independently tested
  ship --as NAME                             the QA lane: land the passing tests on a qa/ branch (no roadmap item)
  review ID --as NAME --message TEXT FILE... hand in a review package: commits it to the `reviews` branch
  pause ID --as NAME --note "done..; next.." [--blocked WHY]   push unfinished work, free the item
  unblock ID --as NAME --note WHY            the thing it waited for has happened
  reply "approve 22.3, B1" | "veto 22.3: why" | "change 22.3: what" --as NAME
  bug "what, when, expected; Test: name" --as NAME
  handoff "in progress / next / traps" --as NAME

NAME: "chat" for the owner's chat; for a scheduled run, the lane and its UTC start: "lane-a-MMDD-HHMM",
"qa-MMDD-HHMM" (older runs used "night-MMDD-HHMM"). A claim is live while its claim time or its branch's last
commit is recent: 6 hours for the chat, 75 minutes for a scheduled run (runs last about an hour, so an older claim
belongs to a run that was cut off). A session holds at most MAX_CLAIMS live claims at once.
"""
import argparse
import datetime as dt
import os
import re
import subprocess
import sys
import tempfile

ROADMAP = "ROADMAP.md"
STALE_CHAT = dt.timedelta(hours=6)
STALE_RUN = dt.timedelta(minutes=75)
REVIEW_CAP = 10
MAX_CLAIMS = 3
BUILD = "./gradlew --max-workers=1 build"
ITEM = re.compile(r"^(\s*- \[)([ x])(\] \*\*)(B\d+[a-z]?|\d+\.\d+[a-z]?)(\*\*)(.*)$")
SECTION = re.compile(r"^## (?:Milestone (\d+)|(Bugs))\b")


def now():
    return dt.datetime.now(dt.timezone.utc)


def stamp():
    return now().strftime("%Y-%m-%d %H:%MZ")


def today():
    return now().strftime("%Y-%m-%d")


def git(*args, check=True, cwd=None):
    r = subprocess.run(["git", *args], cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and r.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed:\n{r.stderr.strip()}")
    return r


def die(text, code=1):
    print(text, file=sys.stderr)
    sys.exit(code)


def clean(text):
    """Owner or session words that go inside a status mark: brackets instead of parentheses."""
    return " ".join(text.replace("(", "[").replace(")", "]").split())


def lead_safe(text):
    """Text that starts an item line must not start with '(' (it would read as a status mark): the leading
    parenthesis and its partner become brackets."""
    text = " ".join(text.split())
    if not text.startswith("("):
        return text
    depth = 0
    for k, ch in enumerate(text):
        depth += {"(": 1, ")": -1}.get(ch, 0)
        if depth == 0:
            return "[" + text[1:k] + "]" + text[k + 1:]
    return "[" + text[1:]


def msg(text, who=None):
    """Commit message: the session name, then any lines from $COMMIT_TRAILERS (e.g. attribution)."""
    text = f"{text} [{who}]" if who else text
    extra = os.environ.get("COMMIT_TRAILERS", "").strip()
    return f"{text}\n\n{extra}" if extra else text


# ---- roadmap parsing -------------------------------------------------------------------------

def split_marks(rest):
    """' (claimed: x, t) (blocked: y) **Title**' -> (['claimed: x, t', 'blocked: y'], ' **Title**')."""
    marks, i = [], 0
    while True:
        j = i
        while j < len(rest) and rest[j] == " ":
            j += 1
        if j >= len(rest) or rest[j] != "(":
            return marks, rest[i:]
        depth, k = 0, j
        while k < len(rest):
            depth += {"(": 1, ")": -1}.get(rest[k], 0)
            if depth == 0:
                break
            k += 1
        if depth:
            return marks, rest[i:]
        marks.append(rest[j + 1:k])
        i = k + 1


def parse(text):
    items, area = [], None
    for n, line in enumerate(text.splitlines()):
        s = SECTION.match(line)
        if s:
            area = f"M{s.group(1)}" if s.group(1) else "Bugs"
            continue
        if line.startswith("## "):
            area = None
            continue
        m = ITEM.match(line)
        if m and area:
            marks, _ = split_marks(m.group(6))
            items.append({"id": m.group(4), "done": m.group(2) == "x", "area": area, "line": n, "marks": marks})
    return items


def find(items, item_id):
    return next((it for it in items if it["id"] == item_id), None)


def mark(item, kind):
    return next((x for x in item["marks"] if x.split(":")[0].split(" ")[0] == kind), None)


def claim_info(marks):
    c = next((x for x in marks if x.startswith("claimed")), None)
    if not c:
        return None
    m = re.match(r"claimed:\s*([^,]+),\s*(\d{4}-\d\d-\d\d \d\d:\d\dZ)", c)
    if not m:
        return {"who": c.split(":", 1)[-1].split(",")[0].strip(), "at": None}
    at = dt.datetime.strptime(m.group(2), "%Y-%m-%d %H:%MZ").replace(tzinfo=dt.timezone.utc)
    return {"who": m.group(1).strip(), "at": at}


def rewrite(text, item_id, fn):
    """Apply fn(checked, marks) -> (checked, marks) to one item's line."""
    lines = text.split("\n")
    for n, line in enumerate(lines):
        m = ITEM.match(line)
        if m and m.group(4) == item_id:
            marks, title = split_marks(m.group(6))
            checked, marks = fn(m.group(2) == "x", marks)
            lead = "".join(f" ({x})" for x in marks)
            lines[n] = f"{m.group(1)}{'x' if checked else ' '}{m.group(3)}{item_id}{m.group(5)}{lead}{title}"
            return "\n".join(lines)
    die(f"No item {item_id} in {ROADMAP}.")


def block_end(lines, n):
    """Index after item line n and its continuation lines (indented deeper, or blank lines inside the block)."""
    indent = len(lines[n]) - len(lines[n].lstrip())
    end = n + 1
    while end < len(lines):
        ln = lines[end]
        if ln.strip() and len(ln) - len(ln.lstrip()) <= indent:
            break
        end += 1
    while end > n + 1 and not lines[end - 1].strip():
        end -= 1
    return end


# ---- repo state ------------------------------------------------------------------------------

def fetch():
    # A shallow clone can't merge a branch another session started from an older main, so fetch deep history. A
    # bounded depth, because some git servers refuse --unshallow; on a full clone, no depth at all.
    deepen = ["--depth=1000"] if git("rev-parse", "--is-shallow-repository").stdout.strip() == "true" else []
    git("fetch", "--quiet", "--prune", *deepen, "origin", "+refs/heads/main:refs/remotes/origin/main",
        "+refs/heads/item/*:refs/remotes/origin/item/*")


def main_roadmap():
    return git("show", f"origin/main:{ROADMAP}").stdout


def remote_branch(item_id):
    """(sha, commit time, subject) of origin/item/<id>, or None."""
    r = git("log", "-1", "--format=%H%x09%ct%x09%s", f"refs/remotes/origin/item/{item_id}", "--", check=False)
    if r.returncode or not r.stdout.strip():
        return None
    sha, ts, subject = r.stdout.strip().split("\t", 2)
    return sha, dt.datetime.fromtimestamp(int(ts), dt.timezone.utc), subject


def is_ancestor(a, b="HEAD"):
    return git("merge-base", "--is-ancestor", a, b, check=False).returncode == 0


def ago(t):
    if not t:
        return "unknown time"
    mins = int((now() - t).total_seconds() // 60)
    return f"{mins}m ago" if mins < 90 else f"{mins / 60:.1f}h ago"


def is_chat(who):
    return who == "chat" or who.startswith("chat-")


def unverified(items):
    """Landed items the QA lane hasn't tested yet."""
    return [it for it in items if it["done"] and not mark(it, "verified")
            and (mark(it, "review") or mark(it, "approved"))]


def claims(items):
    out = []
    for it in items:
        c = claim_info(it["marks"])
        if not c or it["done"]:
            continue
        rb = remote_branch(it["id"])
        last = max([t for t in (c["at"], rb and rb[1]) if t], default=None)
        limit = STALE_CHAT if is_chat(c["who"]) else STALE_RUN
        live = last is None or now() - last < limit
        out.append({**it, **c, "branch": rb, "live": live})
    return out


def push_main_change(transform, message, who, retries=5):
    """Apply transform(roadmap text) on the newest origin/main in a scratch worktree and push it. If main moved
    meanwhile, start again from the newest main, so every check in transform sees the latest state."""
    for _ in range(retries):
        fetch()
        with tempfile.TemporaryDirectory() as tmp:
            wt = os.path.join(tmp, "wt")
            git("worktree", "add", "--quiet", "--detach", wt, "origin/main")
            try:
                path = os.path.join(wt, ROADMAP)
                with open(path, encoding="utf-8") as f:
                    before = f.read()
                after = transform(before)
                if after == before:
                    return
                with open(path, "w", encoding="utf-8") as f:
                    f.write(after)
                git("add", ROADMAP, cwd=wt)
                git("commit", "--quiet", "-m", msg(message, who), cwd=wt)
                if git("push", "--quiet", "origin", "HEAD:main", check=False, cwd=wt).returncode == 0:
                    return
            finally:
                git("worktree", "remove", "--force", wt, check=False)
    die("Couldn't push the roadmap change: main kept moving or the push is refused. Try again in a minute.")


def push_item_change(item_id, fn, message, who):
    push_main_change(lambda text: rewrite(text, item_id, fn), message, who)


def need_clean():
    if git("status", "--porcelain", "--untracked-files=no").stdout.strip():
        die("Commit your changes first (stage files by name).")


# ---- commands --------------------------------------------------------------------------------

def cmd_status(a):
    fetch()
    items = parse(main_roadmap())
    cl = claims(items)
    print(f"Sessions (from origin/main {ROADMAP}; stale after {STALE_CHAT} for the chat, {STALE_RUN} for a "
          f"scheduled run, without a claim or push):")
    for c in cl:
        branch = f"branch pushed {ago(c['branch'][1])}: {c['branch'][2]}" if c["branch"] else "no branch pushed yet"
        state = "LIVE" if c["live"] else "STALE, may be taken over"
        print(f"  {c['id']:6} {c['area']:5} {c['who']:18} claimed {ago(c['at'])}; {branch}  [{state}]")
    if not cl:
        print("  none")
    for it in items:
        if not it["done"] and mark(it, "paused"):
            rb = remote_branch(it["id"])
            print(f"  {it['id']:6} {it['area']:5} paused, free to continue on item/{it['id']}: {rb and rb[2]}")
    busy = sorted({c["area"] for c in cl if c["live"] and c["who"] != a.as_ and c["area"] != "Bugs"})
    print("Busy milestones (another session is working there):", ", ".join(busy) or "none")
    pending = [it for it in items if mark(it, "review")]
    at_cap = len(pending) >= REVIEW_CAP
    print(f"Pending review: {len(pending)} (cap {REVIEW_CAP})" +
          (f": {', '.join(it['id'] for it in pending)}" if pending else ""))
    waiting = {it["area"] for it in pending if it["area"] != "Bugs"} if at_cap else set()
    if at_cap:
        print("  At the cap: nothing in", ", ".join(sorted(waiting)) or "-",
              "until he reviews; bugs, tests, measurements and other milestones are fine.")
    todo = unverified(items)
    print(f"Landed, not yet verified by QA: {len(todo)}" + (f": {', '.join(it['id'] for it in todo)}" if todo else ""))
    if a.as_.startswith("qa"):
        print("Next for you (QA):", ", ".join(it["id"] for it in todo[:6]) if todo else
              "the queue is empty: the QA milestone's own items, or the tester's Full tier")
        return
    held_by_others = {c["id"] for c in cl if c["live"] and c["who"] != a.as_}

    def free(it):
        return (not it["done"] and not mark(it, "blocked") and it["id"] not in held_by_others
                and it["area"] not in busy and it["area"] not in waiting)

    open_items = [it for it in items if free(it)]
    first = (next((it for it in open_items if it["area"] == "Bugs"), None)
             or next((it for it in open_items if mark(it, "vetoed") or re.search(r"[a-z]$", it["id"])), None)
             or min(open_items, key=lambda it: (int(it["area"][1:]), it["line"]), default=None))
    print("Next item for you:", f"{first['id']} ({first['area']})" if first else
          "nothing free: do testing work (the safety-net milestone's items, or the tester's Full tier)")


def cmd_claim(a):
    state = {}

    def transform(text):
        items = parse(text)
        it = find(items, a.id) or die(f"No item {a.id} in {ROADMAP}.")
        if it["done"]:
            die(f"{a.id} is already ticked.")
        if mark(it, "blocked") and not a.force:
            die(f"{a.id} is blocked ({mark(it, 'blocked')}). If what it waited for has happened, run unblock first; "
                f"use --force only if the owner asked for it.")
        mine = [c["id"] for c in claims(items) if c["who"] == a.as_ and c["live"] and c["id"] != a.id]
        if len(mine) >= MAX_CLAIMS and not a.force:
            die(f"You already hold {len(mine)} live claims ({', '.join(mine)}; at most {MAX_CLAIMS}). Land or pause "
                f"one first.")
        for c in claims(items):
            if c["who"] == a.as_ or not c["live"] or a.force:
                continue
            if c["id"] == a.id:
                die(f"{a.id} is claimed by {c['who']} and still live. Take another item.")
            if c["area"] == it["area"] != "Bugs":
                die(f"{c['who']} is working in {it['area']} ({c['id']}). Take an item in another milestone.")
        old = claim_info(it["marks"])
        state["took_over"] = old["who"] if old and old["who"] != a.as_ else None

        def fn(checked, marks):
            drop = ("claimed", "paused", "blocked") if a.force else ("claimed", "paused")
            return checked, [x for x in marks if not x.startswith(drop)] + [f"claimed: {a.as_}, {stamp()}"]
        return rewrite(text, a.id, fn)

    push_main_change(transform, f"Claim {a.id}", a.as_)
    took = state.get("took_over")
    print(f"Claimed {a.id} as {a.as_}." + (f" (Took over from {took}: say so in your handoff.)" if took else ""))
    branch = f"item/{a.id}"
    if git("status", "--porcelain", "--untracked-files=no").stdout.strip():
        print(f"Your working tree has uncommitted changes, so you're still on the same branch. Commit them, then:\n"
              f"  git switch {branch}  (or: git switch -c {branch} origin/item/{a.id}, or origin/main if it's new)")
        return
    rb = remote_branch(a.id)
    if git("rev-parse", "--verify", "--quiet", f"refs/heads/{branch}", check=False).returncode == 0:
        git("switch", "--quiet", branch)
        if rb and git("merge", "--quiet", "--no-edit", f"origin/{branch}", check=False).returncode:
            git("merge", "--abort", check=False)
            die(f"On {branch}; merging origin/{branch} gives conflicts. Resolve them: git merge origin/{branch}.", 3)
        print(f"On your local {branch}" + (f", with origin/{branch} merged in." if rb else "."))
    elif rb:
        git("switch", "--quiet", "-c", branch, f"origin/{branch}")
        print(f"On {branch}, continuing earlier work. Its last commit says:\n")
        print(git("log", "-1", "--format=%B", "HEAD").stdout.strip())
    else:
        git("switch", "--quiet", "-c", branch, "origin/main")
        print(f"On a new branch {branch} from origin/main. Push it with: git push -u origin {branch}")


def own_claim_or_die(a, fetch_first=True):
    if fetch_first:
        fetch()
    it = find(parse(main_roadmap()), a.id) or die(f"No item {a.id}.")
    c = claim_info(it["marks"])
    if not c or c["who"] != a.as_:
        die(f"You ({a.as_}) don't hold the claim on {a.id} (holder: {c and c['who']}). Push your work to "
            f"item/{a.id}-{a.as_}, say so in your handoff, and leave the item.", 2)


def others_work_or_die(a):
    rb = remote_branch(a.id)
    if rb and not is_ancestor(rb[0]):
        die(f"origin/item/{a.id} has commits you don't have (another session pushed there). Merge them first: "
            f"git merge origin/item/{a.id}", 3)
    return rb


def build_env():
    env = dict(os.environ)
    jdk = os.path.expanduser("~/.local/jdk-25")
    if not env.get("JAVA_HOME") and os.path.isdir(jdk):
        env["JAVA_HOME"], env["PATH"] = jdk, f"{jdk}/bin:{env.get('PATH', '')}"
    return env


def same_build(built, head):
    """True when head differs from the commit last built green only in ROADMAP.md, which no build step reads, so that
    build stands for head too. Main moves every few minutes with claims, handoffs and verify marks; rebuilding for
    those made land build 5 times in 45 minutes and give up with green work (B16). CI still builds every push."""
    if not built:
        return False
    changed = git("diff", "--name-only", built, head).stdout.split()
    return all(f == ROADMAP for f in changed)


def cmd_land(a):
    own_claim_or_die(a)
    if git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != f"item/{a.id}":
        die(f"Switch to item/{a.id} first.")
    need_clean()
    built = None
    for attempt in range(1, 6):
        if attempt > 1:
            fetch()
            own_claim_or_die(a, fetch_first=False)
        others_work_or_die(a)
        if git("merge", "--quiet", "--no-edit", "-m", f"Merge main into item/{a.id}", "origin/main",
               check=False).returncode:
            git("merge", "--abort", check=False)
            die("Merging the newest main in gives conflicts. Resolve them by hand: git merge origin/main, fix the "
                "files (ROADMAP.md: keep both sides' lines), git add them by name, git commit; then land again.", 3)
        head = git("rev-parse", "HEAD").stdout.strip()
        if same_build(built, head):
            print(f"[land {attempt}] main only moved in ROADMAP.md since the green build: not building again.",
                  flush=True)
        elif head != built:
            print(f"[land {attempt}] building {head[:9]}: {a.build}", flush=True)
            if subprocess.run(a.build, shell=True, env=build_env()).returncode:
                die("The build failed on top of the newest main (output above). If it's the setup (Java not found, "
                    "out of memory, a download), fix that and land again; if it's a test or compile error, fix it "
                    "on this branch and land again.", 4)
            built = head
        with open(ROADMAP, encoding="utf-8") as f:
            text = f.read()

        def fn(checked, marks):
            if a.keep_open:
                return checked, [x for x in marks if not x.startswith(("claimed", "paused"))]
            keep = [x for x in marks if not x.startswith(("claimed", "paused", "vetoed", "blocked"))]
            return True, keep + [f"approved auto {today()}" if a.no_review else f"review: pending {today()}"]

        with open(ROADMAP, "w", encoding="utf-8") as f:
            f.write(rewrite(text, a.id, fn))
        git("add", ROADMAP)
        git("commit", "--quiet", "-m", msg(f"Land more of {a.id}" if a.keep_open else
                                           f"Mark {a.id} done (accepted: nothing to see)" if a.no_review else
                                           f"Mark {a.id} built (review pending)", a.as_))
        if git("push", "--quiet", "origin", "HEAD:main", check=False).returncode == 0:
            print(f"Landed {a.id} on main at {git('rev-parse', '--short', 'HEAD').stdout.strip()}." +
                  (" The item stays open and unclaimed." if a.keep_open else
                   " Accepted without a review: list it in your next message." if a.no_review else
                   " Send its review package now."))
            fetch()
            rb = remote_branch(a.id)
            if rb and is_ancestor(rb[0]):
                git("push", "--quiet", f"--force-with-lease=refs/heads/item/{a.id}:{rb[0]}", "origin",
                    "--delete", f"item/{a.id}", check=False)
            return
        git("reset", "--quiet", "--hard", "HEAD~1")  # only the bookkeeping commit made just above
        print("main moved while building; merging it in and building again.", flush=True)
    die("main kept moving; try land again in a few minutes.")


def cmd_ship(a):
    branch = git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip()
    if not branch.startswith("qa/"):
        die("ship lands a QA branch (qa/<name>-<date>); builders use land.")
    need_clean()
    built = None
    for attempt in range(1, 6):
        fetch()
        if git("merge", "--quiet", "--no-edit", "-m", f"Merge main into {branch}", "origin/main",
               check=False).returncode:
            git("merge", "--abort", check=False)
            die("Merging the newest main in gives conflicts: git merge origin/main, fix, commit, ship again.", 3)
        head = git("rev-parse", "HEAD").stdout.strip()
        if same_build(built, head):
            print(f"[ship {attempt}] main only moved in ROADMAP.md since the green build: not building again.",
                  flush=True)
        elif head != built:
            print(f"[ship {attempt}] building {head[:9]}: {a.build}", flush=True)
            if subprocess.run(a.build, shell=True, env=build_env()).returncode:
                die("The build failed. A failing test is a bug: move it to tests/<topic>, add the bug, ship the "
                    "rest.", 4)
            built = head
        if git("push", "--quiet", "origin", "HEAD:main", check=False).returncode == 0:
            print(f"Shipped {branch} to main at {head[:9]}.")
            return
        print("main moved while building; merging it in and building again.", flush=True)
    die("main kept moving; try again in a few minutes.")


def cmd_review(a):
    """Commit a review package to the `reviews` branch (never fetched by lanes), for the owner's digest."""
    paths = [os.path.abspath(f) for f in a.files]
    for f in paths:
        if not os.path.isfile(f):
            die(f"No file {f}.")
        if os.path.getsize(f) > 8 * 1024 * 1024:
            die(f"{os.path.basename(f)} is over 8 MB: shrink it (a 640-wide MP4 instead of a GIF, a JPEG sheet).")
    for attempt in range(5):
        exists = git("fetch", "--quiet", "origin", "+refs/heads/reviews:refs/remotes/origin/reviews",
                     check=False).returncode == 0
        with tempfile.TemporaryDirectory() as tmp:
            wt = os.path.join(tmp, "wt")
            if exists:
                git("worktree", "add", "--quiet", "--detach", wt, "origin/reviews")
            else:
                git("worktree", "add", "--quiet", "--detach", wt, "origin/main")
                git("checkout", "--quiet", "--orphan", "reviews-new", cwd=wt)
                git("rm", "-rf", "--quiet", ".", cwd=wt)
            try:
                folder = os.path.join(wt, "reviews", a.id)
                os.makedirs(folder, exist_ok=True)
                import shutil
                for f in paths:
                    shutil.copy(f, folder)
                with open(os.path.join(folder, "message.md"), "w", encoding="utf-8") as fh:
                    fh.write(f"{a.message.strip()}\n\n(from {a.as_}, {stamp()}; not yet sent)\n")
                git("add", "reviews", cwd=wt)
                git("commit", "--quiet", "-m", msg(f"Review package for {a.id}", a.as_), cwd=wt)
                if git("push", "--quiet", "origin", "HEAD:refs/heads/reviews", check=False, cwd=wt).returncode == 0:
                    print(f"Review package for {a.id} is on the reviews branch; the next digest sends it.")
                    return
            finally:
                git("worktree", "remove", "--force", wt, check=False)
                git("branch", "-D", "reviews-new", check=False)
    die("Couldn't push to the reviews branch; try again in a minute.")


def cmd_pause(a):
    own_claim_or_die(a)
    need_clean()
    branch = f"item/{a.id}"
    on_branch = git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() == branch
    has_work = on_branch and git("rev-list", "--count", "origin/main..HEAD").stdout.strip() != "0"
    if has_work:
        others_work_or_die(a)
        git("commit", "--quiet", "--allow-empty", "-m", msg(f"Pause {a.id}\n\n{a.note}", a.as_))
        if git("push", "--quiet", "origin", f"HEAD:refs/heads/{branch}", check=False).returncode:
            die(f"Couldn't push {branch}. Push your work as {branch}-{a.as_}, and say so in your handoff.")
    has_branch = has_work or remote_branch(a.id) is not None

    def fn(checked, marks):
        keep = [x for x in marks if not x.startswith(("claimed", "paused", "blocked"))]
        where = f"; work on {branch}" if has_branch else ""
        if a.blocked:
            return checked, keep + [f"blocked: {clean(a.blocked)}{where}"]
        return checked, keep + ([f"paused: {branch}, {stamp()}"] if has_branch else [])

    push_item_change(a.id, fn, f"{'Block' if a.blocked else 'Pause'} {a.id}", a.as_)
    print(f"{a.id} released" + (f"; the work is on {branch}." if has_branch else "."))


def cmd_unblock(a):
    def fn(checked, marks):
        if not any(x.startswith("blocked") for x in marks):
            die(f"{a.id} isn't blocked.")
        return checked, [x for x in marks if not x.startswith("blocked")]
    push_item_change(a.id, fn, f"Unblock {a.id}: {' '.join(a.note.split())}", a.as_)
    print(f"{a.id} is open again.")


def cmd_reply(a):
    m = re.match(r"\s*(approve|veto|change)\s+(.+)$", a.text, re.S | re.I)
    if not m:
        die('Expected "approve <ids>", "veto <id>: <why>" or "change <id>: <what>".')
    verb, rest = m.group(1).lower(), m.group(2).strip()
    if verb == "approve":
        ids = [x for x in re.split(r"[\s,]+", rest) if x]

        def transform(text):
            for i in ids:
                def fn(checked, marks, i=i):
                    if not checked:
                        die(f"{i} isn't built yet, so it can't be approved.")
                    keep = [x for x in marks if not x.startswith(("review", "approved"))]
                    return True, keep + [f"approved {today()}"]
                text = rewrite(text, i, fn)
            return text
        push_main_change(transform, f"Record the owner's approval of {', '.join(ids)}", a.as_)
        print(f"Recorded: approved {', '.join(ids)}.")
        return
    i, sep, words = rest.partition(":")
    i, words = i.strip(), words.strip()
    if not sep or not words:
        die(f'Expected "{verb} <id>: <his words>".')
    if verb == "veto":
        def fn(checked, marks):
            keep = [x for x in marks if not x.startswith(("review", "approved", "vetoed", "claimed", "verified"))]
            return False, keep + [f"vetoed {today()}: {clean(words)}"]
        push_item_change(i, fn, f"Record the owner's veto of {i}", a.as_)
        print(f"Recorded: {i} vetoed. It's redone next; revert it first if later work would build on it "
              f"(claim it, git revert its commits on the branch, land --keep-open).")
        return
    base = re.sub(r"(?<=\d)[a-z]$", "", i)
    state = {}

    def transform(text):
        lines = text.split("\n")
        n = next((k for k, ln in enumerate(lines) if (mm := ITEM.match(ln)) and mm.group(4) == base), None)
        if n is None:
            die(f"No item {base}.")
        used = {x["id"] for x in parse(text)}
        state["sub"] = next((f"{base}{c}" for c in "abcdefghijklmnopqrstuvwxyz" if f"{base}{c}" not in used), None) \
            or die(f"{base} has run out of sub-item letters: add a new item instead.")
        indent = " " * (len(lines[n]) - len(lines[n].lstrip()) + 2)
        lines.insert(block_end(lines, n),
                     f"{indent}- [ ] **{state['sub']}** Change from the owner ({today()}): {lead_safe(words)}")
        return "\n".join(lines)
    push_main_change(transform, f"Record the owner's change to {i}", a.as_)
    print(f"Recorded as {state['sub']}. Do it next.")


def cmd_bug(a):
    state = {}

    def transform(text):
        lines = text.split("\n")
        items = parse(text)
        nums = [int(re.match(r"B(\d+)", x["id"]).group(1)) for x in items if x["id"].startswith("B")]
        bugs = [x for x in items if x["area"] == "Bugs" and not re.search(r"[a-z]$", x["id"])]
        if bugs:
            at = block_end(lines, bugs[-1]["line"])
        else:
            at = next(k for k, ln in enumerate(lines) if ln.startswith("## Bugs")) + 1
            while at < len(lines) and not lines[at].startswith("## "):
                at += 1
            while at > 0 and not lines[at - 1].strip():
                at -= 1
        state["id"] = f"B{max(nums, default=0) + 1}"
        lines.insert(at, f"- [ ] **{state['id']}** {lead_safe(a.text)} (found by {a.as_}, {today()})")
        return "\n".join(lines)
    push_main_change(transform, "Add a bug", a.as_)
    print(f"Added {state['id']}. Bugs jump the queue: it's the next item.")


def cmd_show(a):
    fetch()
    lines = main_roadmap().split("\n")
    items = parse("\n".join(lines))
    for i in a.ids:
        it = find(items, i) or die(f"No item {i}.")
        head = next((lines[k] for k in range(it["line"], -1, -1) if lines[k].startswith("## ")), "")
        print(head)
        print("\n".join(lines[it["line"]:block_end(lines, it["line"])]))
        print()


def cmd_brief(a):
    """The roadmap without its milestones: the long part a session only needs one item of (use show)."""
    fetch()
    lines = main_roadmap().split("\n")
    first = next((k for k, l in enumerate(lines) if l.startswith("## Milestone")), len(lines))
    rest = next((k for k, l in enumerate(lines) if k > first and l.startswith("## ") and not l.startswith("## Milestone")),
                len(lines))
    print("\n".join(short_fixed_bugs(lines[:first])))
    print("[Milestones left out: `sessions.py show <id>` prints an item; `status` names yours.]\n")
    print("\n".join(lines[rest:]))


def short_fixed_bugs(lines):
    """Fixed bugs as one short line each (`show` prints one in full). Every run reads the brief and keeps it in its
    context for every later turn, and the fixed bugs' history was half of it."""
    out, skipping = [], False
    for line in lines:
        m = ITEM.match(line)
        if m:
            skipping = m.group(2) == "x" and m.group(4).startswith("B")
            if skipping:
                title = re.sub(r"\s+", " ", split_marks(m.group(6))[1]).strip()
                out.append(f"- [x] **{m.group(4)}** {title[:90] + '…' if len(title) > 90 else title}")
                continue
        elif skipping and line.startswith((" ", "\t")) and line.strip():
            continue
        else:
            skipping = False
        out.append(line)
    return out


def cmd_verify(a):
    note = f": {clean(a.note)}" if a.note else ""

    def transform(text):
        for i in a.ids:
            def fn(checked, marks, i=i):
                if not checked:
                    die(f"{i} isn't landed, so there's nothing to verify yet.")
                return True, [x for x in marks if not x.startswith("verified")] + [f"verified {today()}{note}"]
            text = rewrite(text, i, fn)
        return text
    push_main_change(transform, f"Verify {', '.join(a.ids)}", a.as_)
    print(f"Verified {', '.join(a.ids)}.")


def cmd_handoff(a):
    kind = re.sub(r"-[\d-]+$", "", a.as_)

    def transform(text):
        lines = text.split("\n")
        start = next(k for k, ln in enumerate(lines) if ln.startswith("## Notes"))
        k = start + 1
        while k < len(lines) and not lines[k].startswith("## "):
            if lines[k].startswith(f"- **{kind} handoff**"):
                del lines[k:block_end(lines, k)]
                continue
            k += 1
        end = k
        while end > start + 1 and not lines[end - 1].strip():
            end -= 1
        lines.insert(end, f"- **{kind} handoff** ({a.as_}, {stamp()}): {' '.join(a.text.split())}")
        return "\n".join(lines)
    push_main_change(transform, "Handoff", a.as_)
    print(f"Handoff written (it replaced the previous {kind} handoff).")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("status")
    p.add_argument("--as", dest="as_", default="")
    for name in ("claim", "land", "pause", "unblock"):
        p = sub.add_parser(name)
        p.add_argument("id")
        p.add_argument("--as", dest="as_", required=True)
        if name == "claim":
            p.add_argument("--force", action="store_true", help="only when the owner asked for this item")
        if name == "land":
            p.add_argument("--build", default=BUILD)
            g = p.add_mutually_exclusive_group()
            g.add_argument("--keep-open", action="store_true",
                           help="land part of an item (or a revert) without ticking it")
            g.add_argument("--no-review", action="store_true",
                           help="nothing a player can see or feel changed: accept it without the owner's review")
        if name in ("pause", "unblock"):
            p.add_argument("--note", required=True, help="pause: what's done and what's next; unblock: why")
        if name == "pause":
            p.add_argument("--blocked", help="why it can't go on (owner: <question> / tester: <why>)")
    sub.add_parser("brief")
    p = sub.add_parser("ship")
    p.add_argument("--as", dest="as_", required=True)
    p.add_argument("--build", default=BUILD)
    p = sub.add_parser("review")
    p.add_argument("id")
    p.add_argument("files", nargs="+")
    p.add_argument("--as", dest="as_", required=True)
    p.add_argument("--message", required=True, help="the package's message (docs/agent/review.md)")
    p = sub.add_parser("show")
    p.add_argument("ids", nargs="+")
    p = sub.add_parser("verify")
    p.add_argument("ids", nargs="+")
    p.add_argument("--as", dest="as_", required=True)
    p.add_argument("--note", help="what was tested, or the report's link")
    for name in ("reply", "bug", "handoff"):
        p = sub.add_parser(name)
        p.add_argument("text")
        p.add_argument("--as", dest="as_", required=True)
    a = ap.parse_args()
    os.chdir(git("rev-parse", "--show-toplevel").stdout.strip())
    {"status": cmd_status, "claim": cmd_claim, "land": cmd_land, "pause": cmd_pause, "unblock": cmd_unblock,
     "reply": cmd_reply, "bug": cmd_bug, "handoff": cmd_handoff, "show": cmd_show, "verify": cmd_verify, "brief": cmd_brief,
     "ship": cmd_ship, "review": cmd_review}[a.cmd](a)


if __name__ == "__main__":
    main()
