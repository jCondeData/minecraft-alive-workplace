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

And the same for a village's key moments (data/aliveworkplace/advancement/village/): one per MOMENTS entry,
aliveworkplace:village/<moment>, granted by hall/VillageMoments (a rank reached, the first caravan, festival...).

The main line never asks for a build the player can't get (add_unlocks): a spine quest's built/<X> whose X isn't in
blueprint_unlocks/starting.json and no earlier quest unlocks gets ("unlock", X) on its spine predecessor, and every
blueprint a quest unlocks must be in BlueprintUnlocks.QUESTBOOK. Both fail the run (and --check) when they can't hold.

A quest with its own "milestone" or "needs" gets a manifest line under its chapter's, "quest <id> <milestone> <mods>":
the install hook leaves it out of the chapter while that expansion is off or those mods are missing, and the quests that
hang on it then hang on what it hung on. The layout is a story: the chapter's spine (its quests that aren't optional, each
depending on the one before) runs left to right on one row, and each optional side quest sits above or below the spine
quest it branches off, a chain of them going further out.
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
STARTING = os.path.join(ROOT, "src", "main", "resources", "data", "aliveworkplace", "blueprint_unlocks", "starting.json")
UNLOCKS_JAVA = os.path.join(ROOT, "src", "main", "java", "io", "github", "jcondedata", "aliveworkplace", "table", "BlueprintUnlocks.java")
MOMENT_OUT = os.path.join(ROOT, "src", "main", "resources", "data", "aliveworkplace", "advancement", "village")
# hall/VillageMoments grants these (its constants and VillageMoments.rank); titles are lang keys
# advancements.aliveworkplace.village.<moment>
MOMENTS = ["rank_village", "rank_town", "rank_city", "caravan", "festival", "raid", "hall_quest", "steward", "colony", "wonder",
           "camp_broken"]
# Quests that complete by themselves on a village moment (36.2, 36.3): quest key to its moment. Each must have that
# moment's advancement as its only task, no checkmark, and say it completes by itself.
MOMENT_QUESTS = {"warparty": "camp_broken"}
COLUMN, ROW = 3.0, 2.0  # wide enough apart that no two nodes or their lines crowd each other (owner, 2026-10-14)


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


def local_deps(q):
    return [d for d in q.get("deps", []) if ":" not in d]


def layout(chapter, quests):
    """The spine on row 0, one column each; a spine quest with side quests gets a column for every two of them (one
    above, one below), and a side quest that hangs on another side quest goes one row further out from it."""
    spine = [q for q in quests if not q.get("optional")]
    keys = [q["key"] for q in quests]
    for i, q in enumerate(quests):
        for d in local_deps(q):
            if d not in keys[:i]:
                raise ValueError("%s:%s depends on %s, which comes later or doesn't exist" % (chapter, q["key"], d))
    spine_keys = [q["key"] for q in spine]
    for i, q in enumerate(spine):
        local = local_deps(q)
        if any(d not in spine_keys for d in local):
            raise ValueError("%s:%s is on the spine but hangs on a side quest" % (chapter, q["key"]))
        if i > 0 and spine_keys[i - 1] not in local:
            raise ValueError("%s:%s doesn't follow the spine quest before it, %s" % (chapter, q["key"], spine_keys[i - 1]))
    direct = {k: [] for k in spine_keys}
    for q in quests:
        if q.get("optional"):
            local = local_deps(q)
            if not local:
                raise ValueError("%s:%s is a side quest that hangs on nothing in its chapter" % (chapter, q["key"]))
            if local[0] in direct:
                direct[local[0]].append(q["key"])
    pos = {}
    col = 0
    for k in spine_keys:
        pos[k] = (col * COLUMN, 0.0)
        for j, side in enumerate(direct[k]):
            pos[side] = ((col + j // 2) * COLUMN, -ROW if j % 2 == 0 else ROW)
        col += max(1, (len(direct[k]) + 1) // 2)
    taken = set(pos.values())
    for q in quests:
        if q["key"] in pos:
            continue
        x, y = pos[local_deps(q)[0]]
        step = -ROW if y < 0 else ROW
        y += step
        while (x, y) in taken:
            y += step
        pos[q["key"]] = (x, y)
        taken.add((x, y))
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
    pos = layout(key, ch["quests"])
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
    quests = {ch["key"] + ":" + q["key"]: q for ch in CHAPTERS for q in ch["quests"]}
    for ch in CHAPTERS:
        for q in ch["quests"]:
            if q.get("milestone") and ch.get("milestone"):
                raise ValueError("%s:%s has a milestone in a chapter that has one" % (ch["key"], q["key"]))
            for d in q.get("deps", []):
                if ":" in d and d not in seen:
                    raise ValueError("%s:%s depends on %s, which doesn't exist" % (ch["key"], q["key"], d))
                if ":" in d and chapters[d.split(":")[0]].get("needs", "-") not in ("-", ch.get("needs", "-")):
                    raise ValueError("%s:%s depends on a chapter that needs mods it doesn't" % (ch["key"], q["key"]))
                if ":" in d and (quests[d].get("milestone") or quests[d].get("needs")):
                    # the install hook only reroutes dependencies inside a chapter
                    raise ValueError("%s:%s depends on %s in another chapter, which can be left out" % (ch["key"], q["key"], d))


def blueprint_paths():
    """Every blueprint StarterBlueprints ships, by path; looks (looks/<look>/...) count as their blueprint."""
    src = open(BLUEPRINTS, encoding="utf-8").read()
    paths = re.findall(r'AliveWorkplace\.id\("([^"]+)"\)', src) + re.findall(r'\bworkplace\("([a-z_0-9]+)"[,)]', src)
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


def moment_files():
    """File (relative to MOMENT_OUT) to JSON text of every village moment's advancement and their root."""
    lang = json.load(open(LANG, encoding="utf-8"))
    out = {"root.json": json.dumps({"criteria": {"done": {"trigger": "minecraft:impossible"}}}, indent=2) + "\n"}
    for moment in MOMENTS:
        key = "advancements.aliveworkplace.village." + moment
        if key not in lang:
            raise ValueError("no lang entry " + key)
        adv = {
            "parent": "aliveworkplace:village/root",
            "display": {
                "icon": {"id": "aliveworkplace:village_hall"},
                "title": {"translate": key},
                "description": {"translate": "advancements.aliveworkplace.village.description"},
                "show_toast": False,
                "announce_to_chat": False,
                "hidden": True,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        }
        out[moment + ".json"] = json.dumps(adv, indent=2) + "\n"
    return out


def check_advancements(advancements):
    for ch in CHAPTERS:
        for q in ch["quests"]:
            for t in q["tasks"]:
                if t[0] == "advancement" and t[1].startswith("aliveworkplace:built/") \
                        and t[1][len("aliveworkplace:built/"):] + ".json" not in advancements:
                    raise ValueError("%s:%s asks for %s, which no blueprint in StarterBlueprints has"
                                     % (ch["key"], q["key"], t[1]))
                if t[0] == "advancement" and t[1].startswith("aliveworkplace:village/") \
                        and t[1][len("aliveworkplace:village/"):] not in MOMENTS:
                    raise ValueError("%s:%s asks for %s, which isn't one of MOMENTS" % (ch["key"], q["key"], t[1]))


def check_moment_quests():
    found = {}
    for ch in CHAPTERS:
        for q in ch["quests"]:
            if q["key"] in MOMENT_QUESTS:
                found[q["key"]] = q
    for key, moment in MOMENT_QUESTS.items():
        q = found.get(key)
        if q is None:
            raise ValueError("MOMENT_QUESTS names %s, which no chapter has" % key)
        if moment not in MOMENTS:
            raise ValueError("%s completes on %s, which isn't one of MOMENTS" % (key, moment))
        if q["tasks"] != [("advancement", "aliveworkplace:village/" + moment)]:
            raise ValueError("%s's task should be aliveworkplace:village/%s alone, not %s" % (key, moment, q["tasks"]))
        text = " ".join(q["desc"])
        if "checkmark" in text or "completes by itself" not in q["desc"][-1]:
            raise ValueError("%s's text should end saying it completes by itself, with no checkmark" % key)


def gating(chapter, quest):
    """The milestones and mods a quest needs to be in the book: its chapter's and its own."""
    out = set()
    for thing in (chapter, quest):
        if thing.get("milestone"):
            out.add(thing["milestone"])
        if thing.get("needs", "-") != "-":
            out.update(thing["needs"].split(","))
    return out


def add_unlocks():
    """The main line never asks for a build the player can't get (26.10): a spine quest's built/<X> whose X isn't in
    starting.json and no earlier quest unlocks puts ("unlock", X) on the quest it depends on, its spine predecessor.
    Raises if a spine build is still out of reach (no predecessor, or one the book can leave out when this quest stays).
    Returns every blueprint a quest unlocks."""
    start = set(json.load(open(STARTING, encoding="utf-8"))["blueprints"])
    quests = {ch["key"] + ":" + q["key"]: (ch, q) for ch in CHAPTERS for q in ch["quests"]}
    unlocked = {}  # blueprint -> the gating of the quest that unlocks it
    for ch in CHAPTERS:
        for q in ch["quests"]:
            for r in q.get("rewards", []):
                if r[0] == "unlock":
                    unlocked.setdefault(r[1], gating(ch, q))
            if q.get("optional"):
                continue
            for t in q["tasks"]:
                if t[0] != "advancement" or not t[1].startswith("aliveworkplace:built/"):
                    continue
                bp = "aliveworkplace:" + t[1][len("aliveworkplace:built/"):]
                need = gating(ch, q)
                if bp in start or (bp in unlocked and unlocked[bp] <= need):
                    continue
                deps = q.get("deps", [])
                if not deps:
                    raise ValueError("%s:%s asks for %s, which is locked, and has no quest before it to unlock it"
                                     % (ch["key"], q["key"], bp))
                full = deps[0] if ":" in deps[0] else ch["key"] + ":" + deps[0]
                pch, pq = quests[full]
                if not gating(pch, pq) <= need:
                    raise ValueError("%s:%s asks for %s, which is locked, and the quest before it (%s) can be left out of the book"
                                     % (ch["key"], q["key"], bp, full))
                pq["rewards"] = list(pq.get("rewards", [])) + [("unlock", bp)]
                unlocked[bp] = gating(pch, pq)
    return set(unlocked)


def check_questbook_hints(unlocks):
    """Every blueprint a quest unlocks is in BlueprintUnlocks.QUESTBOOK, so the Table says it comes from the questbook."""
    names = dict((path, name) for name, path in re.findall(r'Entry ([A-Z0-9_]+) = new Entry\(AliveWorkplace\.id\("([^"]+)"\)',
                                                          open(BLUEPRINTS, encoding="utf-8").read()))
    java = open(UNLOCKS_JAVA, encoding="utf-8").read()
    block = re.search(r"Set<ResourceLocation> QUESTBOOK = ids\(Stream\.of\((.*?)\)\);", java, re.S)
    listed = set(re.findall(r"StarterBlueprints\.([A-Z0-9_]+)", block.group(1))) if block else set()
    missing = sorted(bp for bp in unlocks if names.get(bp.split(":", 1)[1]) not in listed)
    if missing:
        raise ValueError("quests unlock %s, which BlueprintUnlocks.QUESTBOOK doesn't list" % ", ".join(missing))


def files():
    check_keys()
    check_questbook_hints(add_unlocks())
    result = {}
    manifest = ["# file milestone mods (written by tools/questbook/generate.py)"]
    for i, ch in enumerate(CHAPTERS):
        name = ch["file"] + ".snbt"
        result[name] = chapter_snbt(i, ch)
        manifest.append("%s %s %s" % (name, ch.get("milestone", "-"), ch.get("needs", "-")))
        for q in ch["quests"]:
            if q.get("milestone") or q.get("needs"):
                manifest.append("quest %s %s %s" % (ident(ch["key"], q["key"]), q.get("milestone", "-"), q.get("needs", "-")))
    result["manifest.txt"] = "\n".join(manifest) + "\n"
    return result


def main():
    check = "--check" in sys.argv
    wanted = files()
    advancements = advancement_files()
    check_advancements(advancements)
    check_moment_quests()
    os.makedirs(OUT, exist_ok=True)
    stale = []
    moments = moment_files()
    for out_dir, label, files_ in ((ADV_OUT, "advancement/built/", advancements), (MOMENT_OUT, "advancement/village/", moments)):
        for name, text in files_.items():
            path = os.path.join(out_dir, name)
            old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
            if old != text:
                stale.append(label + name)
                if not check:
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    with open(path, "w", encoding="utf-8", newline="\n") as f:
                        f.write(text)
        for folder, _, names in os.walk(out_dir):
            for name in names:
                rel = os.path.relpath(os.path.join(folder, name), out_dir).replace(os.sep, "/")
                if rel not in files_:
                    stale.append(label + rel + " (not ours any more)")
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
    print("%d chapters, %d quests, %d built advancements, %d village moments%s" % (len(CHAPTERS), quests, len(advancements) - 1,
          len(moments) - 1, (", wrote " + ", ".join(stale)) if stale and not check else ""))


if __name__ == "__main__":
    main()
