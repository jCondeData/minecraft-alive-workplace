#!/usr/bin/env python3
"""Ship the older drawings of the mod's blueprints (ROADMAP 36.21, "Refresh to the latest look").

A build already standing was built from the drawing of its day. The hall's Refresh button compares the build with
the older drawings shipped here to tell what the Builder put there (a leftover the new drawing no longer has) from what
a player added. This copies, from git history, every earlier version of each blueprint changed since --since (default:
the 1.0 release, the builds on players' servers) and every earlier version of the blueprints named with --all, to
src/main/resources/data/aliveworkplace/blueprint_history/<blueprint path>/<commit>.nbt. Versions equal to the current
drawing, or to one already copied, are left out. Run it after redrawing a blueprint, before the release.

    python3 tools/blueprints/history.py [--since 2026-10-17] [--all stone_house stone_house_2 ...]
"""
import argparse
import hashlib
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
STRUCTURES = "src/main/resources/data/aliveworkplace/structure/"
OUT = ROOT / "src/main/resources/data/aliveworkplace/blueprint_history"


def git(*args, binary=False):
    out = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=False)
    if out.returncode != 0:
        return None
    return out.stdout if binary else out.stdout.decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--since", default="2026-10-17")
    ap.add_argument("--all", nargs="*", default=["stone_house", "stone_house_2", "stone_house_3", "stone_house_4"])
    args = ap.parse_args()
    files = [f for f in git("ls-files", STRUCTURES + "*.nbt").split() if "/modded/" not in f]
    written = 0
    for path in files:
        name = path[len(STRUCTURES):-len(".nbt")]
        since = [] if name in args.all else ["--since=" + args.since]
        commits = git("log", "--format=%h", *since, "--", path).split()
        current = (ROOT / path).read_bytes()
        seen = {hashlib.sha1(current).hexdigest()}
        for commit in commits:
            old = git("show", f"{commit}^:{path}", binary=True)
            if not old:
                continue
            digest = hashlib.sha1(old).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)
            target = OUT / name / f"{commit}.nbt"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(old)
            written += 1
    print(f"{written} older drawings in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
