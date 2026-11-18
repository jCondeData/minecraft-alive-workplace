#!/usr/bin/env python3
"""Keep every Wonder drawing a village may have begun (ROADMAP 52.6, the Wonders' old-drawing fallback).

A Wonder is begun with the drawing of its day and saves which one (its data file's "drawing" key, recorded on the Wonder
when it is begun: WonderSite.drawing). A Wonder begun with a drawing that has since been redrawn is still counted,
repaired and continued with that drawing's stages (WonderDrawings in the mod). So every drawing a data file has ever
named is shipped under blueprint_history, the way tools/blueprints/history.py ships the older drawings of buildings:

    src/main/resources/data/aliveworkplace/blueprint_history/wonder/<id>/<key>.json          the data file of the day
    src/main/resources/data/aliveworkplace/blueprint_history/wonder/<id>/stage_<n>_<i>/<key>.nbt   each stage part of it

A drawing's key ("drawn_" and 9 hex digits) is worked out from its stage parts and their offsets (the parts' blocks, not
their gzip bytes), so the same drawing always has the same key. Today's drawing is archived too, so a redraw that
overwrites the files by any path still leaves the drawing it replaced in the history.

    python3 tools/blueprints/wonder_history.py [id ...]   # archive each Wonder's drawing, and write its key in its data file
    python3 tools/blueprints/wonder_history.py --check    # exit 1 if a data file's key is missing, stale or not archived

wonders.save_wonder() calls archive() before it writes a new drawing (the old one is kept) and after (the new one's key is
written and it is archived). A drawing the working files no longer hold (redrawn by another path, then this run) is taken
from git HEAD when its key is the one HEAD's data file named.
"""
import gzip
import hashlib
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RESOURCES = os.path.join(ROOT, "src/main/resources/data")
NAMESPACE = "aliveworkplace"
DATA = os.path.join(RESOURCES, NAMESPACE, "wonders")
HISTORY = os.path.join(RESOURCES, NAMESPACE, "blueprint_history/wonder")
PREFIX = "drawn_"


def dumps(data):
    """A Wonder data file's text, as wonders.save_wonder writes it (offsets on one line)."""
    text = json.dumps(data, indent=2)
    return re.sub(r"\[\s+(-?\d+),\s+(-?\d+),\s+(-?\d+)\s+\]", r"[\1, \2, \3]", text) + "\n"


def part_file(part, resources=RESOURCES):
    """The structure file of a stage part id ("aliveworkplace:wonder/<id>/stage_1_0")."""
    namespace, path = part.split(":", 1)
    return os.path.join(resources, namespace, "structure", path + ".nbt")


def parts(data):
    """[(part id, offset)] of every stage of a data file, in order."""
    return [(p["part"], list(p.get("offset", [0, 0, 0]))) for stage in data["stages"] for p in stage["parts"]]


def key_of(data, read):
    """The key of the drawing {data} names, its part files read with {read}(part id) -> gzip bytes (None: missing)."""
    h = hashlib.sha1()
    for part, offset in parts(data):
        raw = read(part)
        if raw is None:
            return None
        h.update(f"{part} {offset}\n".encode())
        h.update(gzip.decompress(raw))
    return PREFIX + h.hexdigest()[:9]


def read_file(path):
    try:
        with open(path, "rb") as f:
            return f.read()
    except FileNotFoundError:
        return None


def git_show(path):
    rel = os.path.relpath(path, ROOT)
    out = subprocess.run(["git", "show", f"HEAD:{rel}"], cwd=ROOT, capture_output=True, check=False)
    return out.stdout if out.returncode == 0 else None


def archived(wonder_id, key, data):
    """Whether the drawing {key} of {wonder_id} is in the history whole: its data file and every part."""
    folder = os.path.join(HISTORY, wonder_id)
    if not os.path.isfile(os.path.join(folder, key + ".json")):
        return False
    return all(os.path.isfile(os.path.join(folder, part.split(":", 1)[1].rsplit("/", 1)[1], key + ".nbt")) for part, _ in parts(data))


def store(wonder_id, key, data, read):
    """Writes the drawing {key} (its data file {data}, its parts read with {read}) into the history; False if a part is missing."""
    folder = os.path.join(HISTORY, wonder_id)
    blobs = {}
    for part, _ in parts(data):
        raw = read(part)
        if raw is None:
            return False
        blobs[part] = raw
    for part, raw in blobs.items():
        target = os.path.join(folder, part.split(":", 1)[1].rsplit("/", 1)[1], key + ".nbt")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if read_file(target) != raw:
            with open(target, "wb") as f:
                f.write(raw)
    kept = dict(data)
    kept["drawing"] = key
    with open(os.path.join(folder, key + ".json"), "w") as f:
        f.write(dumps(kept))
    return True


def archive(wonder_id, quiet=False):
    """Archives {wonder_id}'s drawing as its working files have it (and the one its data file names, if that was redrawn
    since and is still in git HEAD), and writes the drawing's key into its data file. Returns today's key (None: no file)."""
    path = os.path.join(DATA, wonder_id + ".json")
    text = read_file(path)
    if text is None:
        return None
    data = json.loads(text)
    today = key_of(data, lambda part: read_file(part_file(part)))
    if today is None:
        raise SystemExit(f"wonder/{wonder_id}: a stage part named in {os.path.relpath(path, ROOT)} is missing")
    named = data.get("drawing")
    if named and named != today:
        # redrawn since its key was written: the drawing it named is the one standing Wonders were begun with
        head = git_show(path)
        head_data = json.loads(head) if head else None
        if not archived(wonder_id, named, head_data or data):
            if head_data is not None and key_of(head_data, lambda part: git_show(part_file(part))) == named:
                store(wonder_id, named, head_data, lambda part: git_show(part_file(part)))
                if not quiet:
                    print(f"wonder/{wonder_id}: archived its earlier drawing {named} from git HEAD")
            else:
                print(f"wonder/{wonder_id}: WARNING its earlier drawing {named} is neither archived nor in git HEAD")
    if not archived(wonder_id, today, data):
        store(wonder_id, today, data, lambda part: read_file(part_file(part)))
        if not quiet:
            print(f"wonder/{wonder_id}: archived drawing {today}")
    if named != today:
        data["drawing"] = today
        with open(path, "w") as f:
            f.write(dumps(data))
    return today


def check():
    problems = []
    for name in sorted(os.listdir(DATA)):
        if not name.endswith(".json"):
            continue
        wonder_id = name[:-len(".json")]
        data = json.loads(read_file(os.path.join(DATA, name)))
        today = key_of(data, lambda part: read_file(part_file(part)))
        if data.get("drawing") != today:
            problems.append(f"wonder/{wonder_id}: its data file names drawing {data.get('drawing')}, its stage parts are {today}")
        elif not archived(wonder_id, today, data):
            problems.append(f"wonder/{wonder_id}: its drawing {today} isn't in blueprint_history/wonder/{wonder_id}")
    for p in problems:
        print(p)
    if problems:
        print("Fix: run python3 tools/blueprints/wonder_history.py and commit what it writes (data files and blueprint_history/wonder)")
    return not problems


def main():
    args = sys.argv[1:]
    if "--check" in args:
        sys.exit(0 if check() else 1)
    ids = args or sorted(n[:-len(".json")] for n in os.listdir(DATA) if n.endswith(".json"))
    for wonder_id in ids:
        print(f"wonder/{wonder_id}: {archive(wonder_id)}")


if __name__ == "__main__":
    main()
