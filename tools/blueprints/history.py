#!/usr/bin/env python3
"""Ship the older drawings of the mod's blueprints (ROADMAP 36.21, "Refresh to the latest look").

A build already standing was built from the drawing of its day. The hall's Refresh button compares the build with
the older drawings shipped here to tell what the Builder put there (a leftover the new drawing no longer has) from what
a player added. This copies, from git history, every earlier version of each blueprint changed since --since (default:
the 1.0 release, the builds on players' servers) and every earlier version of the blueprints named with --all, to
src/main/resources/data/aliveworkplace/blueprint_history/<blueprint path>/<commit>.nbt. It also always copies the
drawing of the latest release (the newest v* tag, as <tag>.nbt), so a blueprint redrawn since that release always ships
the drawing players have built. Versions equal to the current drawing, or to one already shipped (by content, so a run
after a rebase or merge adds no copies), are left out. Run it after redrawing a blueprint, before the release.

    python3 tools/blueprints/history.py [--since 2026-10-17] [--all stone_house stone_house_2 ...]
    python3 tools/blueprints/history.py --check   # exit 1 if a blueprint changed since the release lacks its drawing

B661/B662: B639 redrew the Clinic without a run of this tool, and the next run (B648's, in a lane worktree whose
commits were then merged under new hashes) shipped only the in-between drawing, so the Clinic's released drawing was
missing. The release tag's drawing is now copied directly, and --check catches a release drawing that is missing.
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


def shipped(name):
    folder = OUT / name
    return {sha(p.read_bytes()) for p in folder.glob("*.nbt")} if folder.is_dir() else set()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--since", default="2026-10-17")
    ap.add_argument("--all", nargs="*", default=["stone_house", "stone_house_2", "stone_house_3", "stone_house_4"])
    ap.add_argument("--release", default=None, help="the release whose drawings must ship (default: the newest v* tag)")
    ap.add_argument("--check", action="store_true", help="write nothing; exit 1 if a release drawing is missing")
    args = ap.parse_args()
    release = args.release or (git("describe", "--tags", "--abbrev=0", "--match", "v*", "HEAD") or "").strip() or None
    files = [f for f in git("ls-files", STRUCTURES + "*.nbt").split() if "/modded/" not in f]
    written = 0
    missing = []
    for path in files:
        name = path[len(STRUCTURES):-len(".nbt")]
        current = (ROOT / path).read_bytes()
        seen = {sha(current)} | shipped(name)
        if args.check:
            old = git("show", f"{release}:{path}", binary=True) if release else None
            if old and sha(old) not in seen:
                missing.append(name)
            continue
        since = [] if name in args.all else ["--since=" + args.since]
        versions = [(commit, commit + "^") for commit in git("log", "--format=%h", *since, "--", path).split()]
        if release:
            versions.append((release, release))
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
    if args.check:
        for name in missing:
            print(f"missing: {name} has no copy of its {release} drawing in {OUT.relative_to(ROOT)}")
        print(f"{len(missing)} blueprints changed since {release} lack that drawing")
        sys.exit(1 if missing else 0)
    print(f"{written} older drawings in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
