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

52.6: --check also runs wonder_history.py --check (every Wonder's drawing archived with its stage layout and named in its
data file); wonder_history.py, which wonders.save_wonder runs, writes those.

B680: the modded library's copies (structure/modded/<path>, 37.12) were skipped, so a modded build (whose older drawings
Refreshes.historyId looks up under blueprint_history/modded/<path>) was never offered the refresh, and --check, which
looked only at the newest release, passed. Every blueprint is covered now, modded copies included, and --check
compares every release since --since (by git blob id, so it reads no file contents) with what ships.

B1407: a modded build's site saves the fingerprint of the VANILLA drawing it was begun from, so the mod finds a vanilla
key K, then looks K up in the modded history, where that day's modded copy shipped under another key (only new content
ships). Each modded folder now has an aliases.json ({vanilla key: modded key}) and --check checks it.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
STRUCTURES = "src/main/resources/data/aliveworkplace/structure/"
OUT = ROOT / "src/main/resources/data/aliveworkplace/blueprint_history"
ALIASES = "aliases.json"


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
        unaliased = check_aliases(files)
        for name, key in unaliased:
            print(f"missing: {name} has no copy (or alias) of the modded drawing of the day of its vanilla key {key}")
        missing = missing + unaliased
        if missing:
            print("Fix: run python3 tools/blueprints/history.py and commit what it writes under blueprint_history")
        # 52.6: every Wonder's drawing is archived (blueprint_history/wonder/<id>/<key>.json and its parts) and named in its
        # data file, so a Wonder begun with it is still counted, repaired and continued with it after a redraw
        import wonder_history
        wonders_ok = wonder_history.check()
        sys.exit(1 if missing or not wonders_ok else 0)
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
    aliases = write_aliases(files)
    print(f"{written} older drawings in {OUT.relative_to(ROOT)}, {aliases} modded keys by their vanilla drawing's key")


def rev_of(key):
    """The git revision an older drawing's key was copied from: a release tag as itself, a commit as its parent."""
    if git("rev-parse", "--verify", "--quiet", f"refs/tags/{key}"):
        return key
    return f"{key}^"


def moddeds_of_day(files):
    """B1407: {modded name: {vanilla key: the modded copy's bytes at that key's revision}} for each shipped vanilla key."""
    out = {}
    for path in files:
        name = path[len(STRUCTURES):-len(".nbt")]
        if not name.startswith("modded/"):
            continue
        vanilla = name[len("modded/"):]
        folder = OUT / vanilla
        if not folder.is_dir():
            continue
        days = {}
        for p in sorted(folder.glob("*.nbt")):
            data = git("show", f"{rev_of(p.stem)}:{path}", binary=True)
            if data:
                days[p.stem] = data
        out[name] = days
    return out


def write_aliases(files):
    """B1407: a site mid-build of a modded build saves the VANILLA drawing it was begun from, whose key the mod finds in
    the vanilla history; the modded copy of that day ships under its own key (a version is shipped once, under the key of
    the first commit or release that had it). Each modded folder's aliases.json names, for each vanilla key, the modded
    key holding the modded copy of that day (Refreshes reads it), and a modded copy of a day not shipped at all (and not
    today's) is shipped under the vanilla key itself. Returns how many aliases were written."""
    count = 0
    for name, days in moddeds_of_day(files).items():
        folder = OUT / name
        current = sha((ROOT / (STRUCTURES + name + ".nbt")).read_bytes())
        by_digest = {sha(p.read_bytes()): p.stem for p in sorted(folder.glob("*.nbt"))} if folder.is_dir() else {}
        aliases = {}
        for key, data in sorted(days.items()):
            digest = sha(data)
            if digest == current:
                continue  # (the modded copy of its day is today's: nothing older to keep)
            if digest not in by_digest:
                folder.mkdir(parents=True, exist_ok=True)
                (folder / f"{key}.nbt").write_bytes(data)
                by_digest[digest] = key
            if by_digest[digest] != key:
                aliases[key] = by_digest[digest]
        target = folder / ALIASES
        if aliases:
            text = json.dumps(aliases, indent=1, sort_keys=True) + "\n"
            if not target.is_file() or target.read_text() != text:
                target.write_text(text)
            count += len(aliases)
        elif target.is_file():
            target.unlink()
    return count


def check_aliases(files):
    """B1407: the vanilla keys whose modded copy of their day is neither today's, shipped under the key, nor aliased."""
    missing = []
    for name, days in moddeds_of_day(files).items():
        folder = OUT / name
        current = sha((ROOT / (STRUCTURES + name + ".nbt")).read_bytes())
        shipped_keys = {p.stem: sha(p.read_bytes()) for p in folder.glob("*.nbt")} if folder.is_dir() else {}
        target = folder / ALIASES
        aliases = json.loads(target.read_text()) if target.is_file() else {}
        for key, data in days.items():
            digest = sha(data)
            if digest != current and shipped_keys.get(aliases.get(key, key)) != digest:
                missing.append((name, key))
    return missing


if __name__ == "__main__":
    main()
