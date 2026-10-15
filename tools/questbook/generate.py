#!/usr/bin/env python3
"""Writes the mod's FTB Quests chapters (the questbook) from the chapters in chapters.py.

    python3 tools/questbook/generate.py          # writes src/main/resources/aliveworkplace/questbook/
    python3 tools/questbook/generate.py --check  # fails if the files on disk differ from what it would write

The files are FTB Quests chapter files (SNBT, one per chapter) in the format FTB Quests 2101.x reads on 1.21.1. They
are written in the subset of SNBT that Minecraft's own parser reads too (commas between entries, every string quoted),
so the compat test can parse them with TagParser. Titles, subtitles and descriptions sit in the chapter files
themselves: FTB Quests reads them from there and moves them into its own lang/en_us.snbt the first time it saves, so we
never have to edit the server's shared lang file.

Every id is a hash of the chapter's and the quest's key, so it stays the same from one release to the next, never
starts above 7 (FTB parses them as signed 64-bit hex) and doesn't clash with the random ids of the pack's own quests.

manifest.txt lists the files in order, each with the expansion milestone it needs (or "-") and the mods it needs
(or "-"): the install hook (compat/ftbquests/FtbQuestsBook) only copies a chapter whose expansion is on and whose mods
are installed.

It also writes the hidden advancements the build quests tick off on (data/aliveworkplace/advancement/built/): one per
blueprint in StarterBlueprints.java, aliveworkplace:built/<blueprint path>, with an "impossible" criterion that
build/BuiltAdvancements grants when a build of it finishes. They hang off a root with no display, so they never show a
tab, a toast or a chat line. Every aliveworkplace:built/ advancement a chapter asks for must be one of them.
"""
import json
import re
import hashlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from chapters import CHAPTERS  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "aliveworkplace", "questbook")
ORDER_BASE = 100  # after the pack's own chapters in FTB Quests' default group
ADV_OUT = os.path.join(ROOT, "src", "main", "resources", "data", "aliveworkplace", "advancement", "built")
BLUEPRINTS = os.path.join(ROOT, "src", "main", "java", "io", "github", "jcondedata", "aliveworkplace", "blueprint",
                          "StarterBlueprints.java")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "aliveworkplace", "lang", "en_us.json")


def ident(*parts):
    digest = hashlib.sha1(("aliveworkplace/" + "/".join(parts)).encode()).hexdigest()[:16].upper()
    first = int(digest[0], 16) & 7
    if first == 0:
        first = 1  # never 0: keeps every id 16 digits, and never the all-zero id
    return "%X%s" % (first, digest[1:])


def s(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def stack(item_id, count=1):
    return "{id: %s, count: %d}" % (s(item_id), count)


def task_snbt(chapter, quest, i, task):
    kind = task[0]
    tid = s(ident(chapter, quest, "task", str(i)))
    if kind == "item":
        _, item_id, count = (task + (1,))[:3]
        extra = ", count: %dL" % count if count > 1 else ""
        return "{id: %s, type: \"item\", item: %s%s}" % (tid, stack(item_id), extra)
    if kind == "check":
        return "{id: %s, type: \"checkmark\", title: %s}" % (tid, s(task[1]))
    if kind == "advancement":
        return "{id: %s, type: \"advancement\", advancement: %s, criterion: \"\"}" % (tid, s(task[1]))
    if kind == "kill":
        return "{id: %s, type: \"kill\", entity: %s, value: %dL}" % (tid, s(task[1]), task[2])
    if kind == "observe":
        # observe_type 0 is a block; the timer is in ticks (a second of looking at it)
        return ("{id: %s, type: \"observation\", observe_type: 0, observation_type: \"block\", to_observe: %s, "
                "timer: 20L, title: %s}") % (tid, s(task[1]), s(task[2]))
    raise ValueError("unknown task kind " + kind)


def reward_snbt(chapter, quest, i, reward):
    kind = reward[0]
    rid = s(ident(chapter, quest, "reward", str(i)))
    if kind == "item":
        _, item_id, count = (reward + (1,))[:3]
        return "{id: %s, type: \"item\", item: %s, count: %d}" % (rid, stack(item_id), count)
    if kind == "xp":
        return "{id: %s, type: \"xp\", xp: %d}" % (rid, reward[1])
    if kind == "unlock":
        # 26.10: the build joins the Blueprint Table of the player's village. FTB Quests runs a reward's command as the
        # player, with {p} their name; elevate_perms gives it the operator level /workplace unlock needs.
        command = "/workplace unlock {p} %s" % reward[1]
        return "{id: %s, type: \"command\", command: %s, elevate_perms: true, silent: true}" % (rid, s(command))
    raise ValueError("unknown reward kind " + kind)


def layout(quests):
    """Columns by depth along the chapter's own dependencies, rows centered in each column."""
    depth = {}
    for q in quests:
        local = [d for d in q.get("deps", []) if ":" not in d]
        for d in local:
            if d not in depth:
                raise ValueError("quest %s depends on %s, which comes later or doesn't exist" % (q["key"], d))
        depth[q["key"]] = 1 + max((depth[d] for d in local), default=-1)
    columns = {}
    deps = {}
    for q in quests:
        columns.setdefault(depth[q["key"]], []).append(q["key"])
        deps[q["key"]] = [d for d in q.get("deps", []) if ":" not in d]
    # Wide enough apart that no two nodes or their lines crowd each other (owner, 2026-10-14), and each column ordered
    # by where its quests' parents sit, so the lines between columns cross as little as possible.
    pos = {}
    for col in sorted(columns):
        keys = columns[col]
        def parent_y(key):
            ys = [pos[d][1] for d in deps[key] if d in pos]
            return sum(ys) / len(ys) if ys else 0.0
        keys = sorted(keys, key=lambda k: (parent_y(k), keys.index(k)))
        for i, key in enumerate(keys):
            pos[key] = (col * 3.0, (i - (len(keys) - 1) / 2.0) * 2.0)
    return pos


def chapter_snbt(index, ch):
    key = ch["key"]
    lines = ["{"]
    lines.append("\tid: %s," % s(ident(key)))
    lines.append("\tgroup: \"\",")
    lines.append("\torder_index: %d," % (ORDER_BASE + index))
    lines.append("\tfilename: %s," % s(ch["file"]))
    lines.append("\ttitle: %s," % s(ch["title"]))
    lines.append("\tsubtitle: [%s]," % s(ch["subtitle"]))
    lines.append("\ticon: %s," % stack(ch["icon"]))
    lines.append("\tdefault_quest_shape: \"\",")
    lines.append("\tdefault_hide_dependency_lines: false,")
    lines.append("\tquests: [")
    pos = layout(ch["quests"])
    out = []
    for q in ch["quests"]:
        qk = q["key"]
        x, y = pos[qk]
        parts = ["\t\t{"]
        parts.append("\t\t\tid: %s," % s(ident(key, qk)))
        parts.append("\t\t\tx: %.1fd," % x)
        parts.append("\t\t\ty: %.1fd," % y)
        if q.get("final"):
            parts.append("\t\t\tshape: \"gear\",")
            parts.append("\t\t\tsize: 1.5d,")
        if q.get("optional"):
            parts.append("\t\t\toptional: true,")
        parts.append("\t\t\ttitle: %s," % s(q["title"]))
        if q.get("subtitle"):
            parts.append("\t\t\tsubtitle: %s," % s(q["subtitle"]))
        parts.append("\t\t\tdescription: [%s]," % ", ".join(s(line) for line in q["desc"]))
        deps = []
        for d in q.get("deps", []):
            if ":" in d:
                other, dq = d.split(":", 1)
                deps.append(ident(other, dq))
            else:
                deps.append(ident(key, d))
        parts.append("\t\t\tdependencies: [%s]," % ", ".join(s(d) for d in deps))
        if q.get("icon"):
            parts.append("\t\t\ticon: %s," % stack(q["icon"]))
        parts.append("\t\t\ttasks: [%s]," % ", ".join(task_snbt(key, qk, i, t) for i, t in enumerate(q["tasks"])))
        parts.append("\t\t\trewards: [%s]" % ", ".join(reward_snbt(key, qk, i, r) for i, r in enumerate(q.get("rewards", []))))
        parts.append("\t\t}")
        out.append("\n".join(parts))
    lines.append(",\n".join(out))
    lines.append("\t],")
    lines.append("\tquest_links: [],")
    lines.append("\timages: []")
    lines.append("}")
    return "\n".join(lines) + "\n"


def check_keys():
    seen = set()
    chapters = {ch["key"]: ch for ch in CHAPTERS}
    for ch in CHAPTERS:
        for q in ch["quests"]:
            full = ch["key"] + ":" + q["key"]
            if full in seen:
                raise ValueError("two quests " + full)
            seen.add(full)
    for ch in CHAPTERS:
        for q in ch["quests"]:
            for d in q.get("deps", []):
                if ":" in d and d not in seen:
                    raise ValueError("%s:%s depends on %s, which doesn't exist" % (ch["key"], q["key"], d))
                if ":" in d and chapters[d.split(":")[0]].get("needs", "-") not in ("-", ch.get("needs", "-")):
                    raise ValueError("%s:%s depends on a chapter that needs mods it doesn't" % (ch["key"], q["key"]))


def blueprint_paths():
    """Every blueprint StarterBlueprints ships, by path; looks (looks/<look>/...) count as their blueprint."""
    src = open(BLUEPRINTS, encoding="utf-8").read()
    paths = re.findall(r'AliveWorkplace\.id\("([^"]+)"\)', src) + re.findall(r'\bworkplace\("([a-z_0-9]+)"\)', src)
    return sorted(set(p for p in paths if not p.startswith("looks/")))


def prettify(path):
    return " ".join(w[:1].upper() + w[1:] for w in re.split(r"[_\-]+", path.rsplit("/", 1)[-1]) if w)


def advancement_files():
    """File (relative to ADV_OUT) to JSON text of every built advancement and their root."""
    lang = json.load(open(LANG, encoding="utf-8"))
    out = {"root.json": json.dumps({"criteria": {"built": {"trigger": "minecraft:impossible"}}}, indent=2) + "\n"}
    for path in blueprint_paths():
        key = "blueprint.aliveworkplace." + path.replace("/", ".")
        title = {"translate": key} if key in lang else {"translate": key, "fallback": prettify(path)}
        adv = {
            "parent": "aliveworkplace:built/root",
            "display": {
                "icon": {"id": "aliveworkplace:blueprint"},
                "title": title,
                "description": {"translate": "advancements.aliveworkplace.built.description"},
                "show_toast": False,
                "announce_to_chat": False,
                "hidden": True,
            },
            "criteria": {"built": {"trigger": "minecraft:impossible"}},
        }
        out[path + ".json"] = json.dumps(adv, indent=2) + "\n"
    return out


def check_advancements(advancements):
    for ch in CHAPTERS:
        for q in ch["quests"]:
            for t in q["tasks"]:
                if t[0] == "advancement" and t[1].startswith("aliveworkplace:built/") \
                        and t[1][len("aliveworkplace:built/"):] + ".json" not in advancements:
                    raise ValueError("%s:%s asks for %s, which no blueprint in StarterBlueprints has"
                                     % (ch["key"], q["key"], t[1]))


def files():
    check_keys()
    result = {}
    manifest = ["# file milestone mods (written by tools/questbook/generate.py)"]
    for i, ch in enumerate(CHAPTERS):
        name = ch["file"] + ".snbt"
        result[name] = chapter_snbt(i, ch)
        manifest.append("%s %s %s" % (name, ch.get("milestone", "-"), ch.get("needs", "-")))
    result["manifest.txt"] = "\n".join(manifest) + "\n"
    return result


def main():
    check = "--check" in sys.argv
    wanted = files()
    advancements = advancement_files()
    check_advancements(advancements)
    os.makedirs(OUT, exist_ok=True)
    stale = []
    for name, text in advancements.items():
        path = os.path.join(ADV_OUT, name)
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old != text:
            stale.append("advancement/built/" + name)
            if not check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w", encoding="utf-8", newline="\n") as f:
                    f.write(text)
    for folder, _, names in os.walk(ADV_OUT):
        for name in names:
            rel = os.path.relpath(os.path.join(folder, name), ADV_OUT).replace(os.sep, "/")
            if rel not in advancements:
                stale.append("advancement/built/" + rel + " (not ours any more)")
                if not check:
                    os.remove(os.path.join(folder, name))
    for name, text in wanted.items():
        path = os.path.join(OUT, name)
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old != text:
            stale.append(name)
            if not check:
                with open(path, "w", encoding="utf-8", newline="\n") as f:
                    f.write(text)
    for name in os.listdir(OUT):
        if name not in wanted:
            stale.append(name + " (not ours any more)")
            if not check:
                os.remove(os.path.join(OUT, name))
    quests = sum(len(ch["quests"]) for ch in CHAPTERS)
    if len(stale) > 12:
        stale = stale[:12] + ["and %d more" % (len(stale) - 12)]
    if check and stale:
        print("questbook out of date: " + ", ".join(stale) + " (run python3 tools/questbook/generate.py)")
        sys.exit(1)
    print("%d chapters, %d quests, %d built advancements%s" % (len(CHAPTERS), quests, len(advancements) - 1, (", wrote " + ", ".join(stale)) if stale and not check else ""))


if __name__ == "__main__":
    main()
