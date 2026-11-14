#!/usr/bin/env python3
"""Ship the older drawings of the mod's blueprints (ROADMAP 36.21, "Refresh to the latest look").

A build already standing was built from the drawing of its day. The hall's Refresh button compares the build with
the older drawings shipped here to tell what the Builder put there (a leftover the new drawing no longer has) from what
a player added. This copies, from git history, every earlier version of each blueprint changed since --since (default:
the 1.0 release, the builds on players' servers) and every earlier version of the blueprints named with --all, to
src/main/resources/data/aliveworkplace/blueprint_history/<blueprint path>/<commit>.nbt (the modded library's copies to
blueprint_history/modded/<path>/). It also always copies the drawing of every release since --since (each v* tag, as
<tag>.nbt), so a blueprint redrawn since a release always ships the drawing players have built. Versions equal to the current drawing, or to one already shipped (by content, so a run
after a rebase or merge adds no copies), are left out. Run it after redrawing a blueprint, before the release.

    python3 tools/blueprints/history.py [--since 2026-10-17] [--all stone_house stone_house_2 ...]
    python3 tools/blueprints/history.py --check   # exit 1 if a release's drawing (since --since) isn't shipped

B661/B662: B639 redrew the Clinic without a run of this tool, and the next run (B648's, in a lane worktree whose
commits were then merged under new hashes) shipped only the in-between drawing, so the Clinic's released drawing was
missing. The release tag's drawing is now copied directly, and --check catches a release drawing that is missing.

B680: the modded library's copies (structure/modded/<path>, 37.12) were skipped, so a modded build (whose older drawings
Refreshes.historyId looks up under blueprint_history/modded/<path>) was never offered the refresh, and --check, which
looked only at the newest release, passed. Every blueprint is covered now, modded copies included, and --check
compares every release since --since (by git blob id, so it reads no file contents) with what ships.
"""
import argparse
import hashlib
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
STRUCTURES = "src/main/resources/data/aliveworkplace/structure/"
OUT = ROOT / "src/main/resources/data/aliveworkplace/blueprint_history"


def git(*args, binary=False):
    out = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=False)
    if out.returncode != 0:
        return None
    return out.stdout if binary else out.stdout.decode()


def sha(data):
    return hashlib.sha1(data).hexdigest()


def blob(data):
    """The id git gives a file with these contents (so tags can be compared through git ls-tree)."""
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def releases(since):
    """The v* tags made on or after {since}, oldest first (some early ones aren't on HEAD's history: still released)."""
    out = git("for-each-ref", "--sort=creatordate", "--format=%(refname:short) %(creatordate:short)",
              "refs/tags/v*") or ""
    return [tag for tag, day in (line.split() for line in out.splitlines() if line.strip()) if day >= since]


def tree(rev):
    """{structure file path: blob id} at {rev}."""
    out = git("ls-tree", "-r", rev, "--", STRUCTURES) or ""
    return {line.split("\t", 1)[1]: line.split()[2] for line in out.splitlines() if line.endswith(".nbt")}


def check(files, since, release):
    """The blueprints (with the release) whose drawing at a release since {since} is neither today's nor shipped."""
    tags = [release] if release else releases(since)
    known = {}
    for path in files:
        name = path[len(STRUCTURES):-len(".nbt")]
        folder = OUT / name
        known[path] = {blob((ROOT / path).read_bytes())} | (
            {blob(p.read_bytes()) for p in folder.glob("*.nbt")} if folder.is_dir() else set())
    missing = []
    for tag in tags:
        for path, digest in tree(tag).items():
            if path in known and digest not in known[path]:
                missing.append((path[len(STRUCTURES):-len(".nbt")], tag))
    return tags, missing


def shipped(name):
    folder = OUT / name
    return {sha(p.read_bytes()) for p in folder.glob("*.nbt")} if folder.is_dir() else set()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--since", default="2026-10-17")
    ap.add_argument("--all", nargs="*", default=["stone_house", "stone_house_2", "stone_house_3", "stone_house_4"])
    ap.add_argument("--release", default=None,
                    help="the release whose drawings must ship (default: the newest v* tag; with --check, every one since --since)")
    ap.add_argument("--check", action="store_true", help="write nothing; exit 1 if a release drawing is missing")
    args = ap.parse_args()
    # (every blueprint, the modded library's copies under modded/ too: B680)
    files = git("ls-files", STRUCTURES + "*.nbt").split()
    if args.check:
        tags, missing = check(files, args.since, args.release)
        for name, tag in missing:
            print(f"missing: {name} has no copy of its {tag} drawing in {OUT.relative_to(ROOT)}")
        print(f"{len({n for n, _ in missing})} blueprints lack a drawing of a release since {args.since} ({len(tags)} releases checked)")
        if missing:
            print("Fix: run python3 tools/blueprints/history.py and commit what it writes under blueprint_history")
        sys.exit(1 if missing else 0)
    release = args.release or (git("describe", "--tags", "--abbrev=0", "--match", "v*", "HEAD") or "").strip() or None
    tags = releases(args.since)
    written = 0
    for path in files:
        name = path[len(STRUCTURES):-len(".nbt")]
        current = (ROOT / path).read_bytes()
        seen = {sha(current)} | shipped(name)
        since = [] if name in args.all else ["--since=" + args.since]
        versions = [(commit, commit + "^") for commit in git("log", "--format=%h", *since, "--", path).split()]
        # every release's drawing since --since (as <tag>.nbt), the newest included: some releases aren't on HEAD's history
        versions += [(tag, tag) for tag in tags if tag != release] + ([(release, release)] if release else [])
        for key, rev in versions:
            old = git("show", f"{rev}:{path}", binary=True)
            if not old:
                continue
            digest = sha(old)
            if digest in seen:
                continue
            seen.add(digest)
            target = OUT / name / f"{key}.nbt"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(old)
            written += 1
    print(f"{written} older drawings in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
