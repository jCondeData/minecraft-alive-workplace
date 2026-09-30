#!/usr/bin/env python3
"""Inventory of what a Fabric mod contains, and which parts no test touches.

  inventory.py [repo] [--area build,mail] [--json out.json] [--all]

Reads the main sources and every test source set (src/*test*/java) and lists:
  - registered things (blocks, items, entities, block entities, professions, game rules...)
  - data saved on entities/levels (attachments) and whether any test saves and reloads it
  - config keys, commands, network payloads, menus, mixins
  - feature areas (packages under the mod's root package) with how many tests reference them
Something counts as tested when a test source mentions its field name, its id in quotes or its
class name. That is evidence of a test, not proof of a good one: read the tests it points at.
Heuristic by design; for anything it can't see (data files, worldgen), use the playbook.
"""
import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from modsrc import repo_root, source_sets, java_files, read, mod_package, strip_comments, GAMETEST_RE  # noqa: E402

KIND_BY_FILE = [
    (r"Block(s)?Entit", "block entity"), (r"Blocks?$", "block"), (r"Items?$", "item"),
    (r"Entit(y|ies)$", "entity"), (r"Villagers?|Professions?|Poi", "profession/poi"),
    (r"GameRules?", "game rule"), (r"Components?", "data component"), (r"Trades?", "trade"),
    (r"Sounds?", "sound"), (r"Effects?", "effect"), (r"Menus?|ScreenHandlers?", "menu"),
    (r"Recipes?", "recipe type"), (r"Tabs?|Groups?", "creative tab"), (r"Attachments?", "attachment"),
    (r"Features?|Structures?", "worldgen"), (r"Particles?", "particle"),
]
FIELD_RE = re.compile(r"public\s+static\s+final\s+[\w<>\[\].,?\s]+?\s+([A-Z][A-Z0-9_]*)\s*=\s*([\w.<>]+)\s*\(\s*\"([a-z0-9_./-]+)\"")
REGISTER_RE = re.compile(r"Registry\.register\(\s*(?:BuiltInRegistries|Registries)\.(\w+)\s*,\s*[^,]*\"([a-z0-9_./-]+)\"")
SAVED_RE = re.compile(r"([A-Z][A-Z0-9_]*)\s*=\s*[\w.]*\bsaved\(\s*\"([a-z0-9_]+)\"")
FABRIC_ATTACH_RE = re.compile(r"([A-Z][A-Z0-9_]*)\s*=\s*AttachmentRegistry\.[\s\S]{0,200}?\"([a-z0-9_]+)\"")
CONFIG_FIELD_RE = re.compile(r"^\s*public\s+(?!static)(boolean|int|long|double|float|String|List<[^>]+>)\s+(\w+)\s*(=|;)", re.M)
LITERAL_RE = re.compile(r"literal\(\s*\"([a-z0-9_-]+)\"\s*\)")
PAYLOAD_RE = re.compile(r"(?:clientbound|serverbound|registerGlobalReceiver|playS2C\(\)\.register|playC2S\(\)\.register|registerReceiver)\(\s*([\w.]+?)\.(?:TYPE|ID)\b")
MENU_RE = re.compile(r"class\s+(\w+)\s+extends\s+(?:AbstractContainerMenu|ScreenHandler)\b")
SAVE_WORDS = re.compile(r"saveWithoutId|\.save\(|\.load\(|readAdditional|saveAdditional|encodeStart|\.parse\(|roundTrip|reload|CODEC\.|ValueOutput|ValueInput|restoreFrom")


CALL_KINDS = [(r"blockentit", "block entity"), (r"entit", "entity"), (r"block", "block"), (r"item", "item"),
              (r"menu|screenhandler", "menu"), (r"poi", "profession/poi"), (r"profession", "profession/poi"),
              (r"sound", "sound"), (r"effect", "effect"), (r"component", "data component"), (r"rule", "game rule"),
              (r"tab|group", "creative tab"), (r"particle", "particle"), (r"recipe", "recipe type")]
TYPE_KINDS = [(r"BlockEntityType", "block entity"), (r"MenuType|ScreenHandlerType", "menu"), (r"EntityType", "entity"),
              (r"PoiType", "profession/poi"), (r"VillagerProfession", "profession/poi"), (r"DataComponentType", "data component"),
              (r"SoundEvent", "sound"), (r"GameRules\.Key", "game rule")]


def kind_of_field(decl, factory):
    """Kind from the declared type, then from the factory call (Reg.blockEntity -> block entity)."""
    head = decl.split("=")[0]
    for pat, kind in TYPE_KINDS:
        if re.search(pat, head):
            return kind
    last = factory.split(".")[-1].lower()
    for pat, kind in CALL_KINDS:
        if re.search(pat, last):
            return kind
    return None


def kind_for(path):
    stem = Path(path).stem
    stem = re.sub(r"^Mod", "", stem)
    for pat, kind in KIND_BY_FILE:
        if re.search(pat, stem):
            return kind
    return "registered"


def label(i):
    if i["id"] == i["name"]:
        return f"`{i['id']}`"
    return f"`{i['name']}` (\"{i['id']}\")" if re.match(r"[A-Z0-9_]+$", i["name"]) else f"`{i['id']}`"


METHOD_RE = re.compile(r"^[ \t]*(?:(?:public|private|protected|static|final|synchronized|default)\s+)*"
                       r"(?:<[^>]+>\s+)?[\w<>\[\],.? ]+?\s+(\w+)\s*\([^;{)]*\)\s*(?:throws\s+[\w., ]+)?\{", re.M)


def method_bodies(src):
    """[(name, body)] for every method in a test source (helpers too), plus '(fields)' for the rest."""
    starts = [(m.start(), m.group(1)) for m in METHOD_RE.finditer(src)
              if m.group(1) not in ("if", "for", "while", "switch", "catch", "synchronized", "return", "new")]
    out = []
    for i, (pos, name) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(src)
        out.append((name, src[pos:end]))
    head = src[:starts[0][0]] if starts else src
    out.append(("(fields)", head))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", nargs="?", default=".")
    ap.add_argument("--area", help="only these feature areas (comma-separated package names)")
    ap.add_argument("--json", help="also write the inventory as JSON here")
    ap.add_argument("--all", action="store_true", help="list tested things too, not only the gaps")
    a = ap.parse_args()

    root = repo_root(a.repo)
    main_dirs, test_dirs = source_sets(root)
    if not main_dirs:
        sys.exit(f"no src/*/java found under {root}")
    pkg = mod_package(main_dirs)
    areas_filter = set(a.area.split(",")) if a.area else None

    # --- test sources
    tests_src = {f: strip_comments(read(f)) for f in java_files(test_dirs)}
    all_tests = "\n".join(tests_src.values())
    gametests = [(f, m.group(1)) for f, s in ((f, read(f)) for f in tests_src) for m in GAMETEST_RE.finditer(s)]
    bodies = [(f, n, b) for f, s in tests_src.items() for n, b in method_bodies(s)]

    def mentioned(*tokens):
        hits = []
        for f, n, b in bodies:
            if any(t and re.search(r"(?<![\w])" + re.escape(t) + r"(?![\w])", b) for t in tokens):
                hits.append(f"{Path(f).stem}.{n}")
        return hits

    # --- main sources
    items = []   # dicts: kind, name, id, file, area, tests
    area_classes = defaultdict(set)
    for f in java_files(main_dirs):
        src = read(f)
        code = strip_comments(src)
        m = re.search(r"^\s*package\s+([\w.]+)\s*;", src, re.M)
        p = m.group(1) if m else ""
        rel = p[len(pkg):].lstrip(".") if p.startswith(pkg) else p
        area = rel.split(".")[0] if rel else "(root)"
        area_classes[area].add(f.stem)
        in_registry = "registry" in f.parts or re.match(r"Mod[A-Z]", f.stem)
        if in_registry:
            k = kind_for(f)
            for fm in FIELD_RE.finditer(code):
                if k == "attachment":
                    continue            # handled below with the save/reload check
                stmt = code[fm.start():code.find(";", fm.end()) + 1]
                items.append(dict(kind=kind_of_field(fm.group(0), fm.group(2)) or k, name=fm.group(1), id=fm.group(3),
                                  file=str(f), area=area, decl=stmt))
        for rm in REGISTER_RE.finditer(code):
            reg = rm.group(1).lower()
            kind = next((k for pat, k in CALL_KINDS if re.search(pat, reg.replace("_", ""))), reg.replace("_", " "))
            items.append(dict(kind=kind, name=rm.group(2), id=rm.group(2), file=str(f), area=area))
        for am in list(SAVED_RE.finditer(code)) + list(FABRIC_ATTACH_RE.finditer(code)):
            stmt = code[max(0, code.rfind("\n", 0, am.start())):code.find(";", am.end()) + 1]
            items.append(dict(kind="saved data", name=am.group(1), id=am.group(2), file=str(f), area=area, decl=stmt))
        if f.stem.endswith("Config"):
            for cm in CONFIG_FIELD_RE.finditer(code):
                items.append(dict(kind="config key", name=cm.group(2), id=cm.group(2), file=str(f), area=area))
        if "command" in f.parts or "CommandDispatcher" in code:
            for lm in sorted(set(LITERAL_RE.findall(code))):
                items.append(dict(kind="command word", name=lm, id=lm, file=str(f), area=area))
        for pm in sorted(set(PAYLOAD_RE.findall(code))):
            items.append(dict(kind="network payload", name=pm.split(".")[-1] if "." in pm else pm, id=pm, file=str(f), area=area))
        for mm in MENU_RE.finditer(code):
            items.append(dict(kind="menu", name=mm.group(1), id=mm.group(1), file=str(f), area=area))
    for mj in root.glob("src/*/resources/*.mixins.json"):
        try:
            d = json.loads(read(mj))
        except json.JSONDecodeError:
            continue
        for side in ("mixins", "server", "client"):
            for cls in d.get(side, []) or []:
                items.append(dict(kind="mixin", name=cls.split(".")[-1], id=cls, file=str(mj), area="mixin"))

    # things declared in a registry class belong to the feature area of the class they create or store
    # (Reg.block("mailbox", mail.MailboxBlock::new) -> mail; saved("builder_job", BuilderJob.CODEC) -> build)
    owner = {}
    for area, classes in area_classes.items():
        for c in classes:
            owner.setdefault(c, area)
    for it in items:
        decl = it.get("decl")
        if not decl:
            continue
        m = re.search(re.escape(pkg) + r"\.(\w+)\.", decl)
        if m and m.group(1) in area_classes:
            it["area"] = m.group(1)
            continue
        for c in re.findall(r"\b([A-Z]\w+)\b", decl.split("=", 1)[-1]):
            a2 = owner.get(c)
            if a2 and a2 not in ("registry", "platform", "mc"):
                it["area"] = a2
                break
    seen = set()
    uniq = []
    for it in items:
        key = (it["kind"], it["name"], it["id"])
        if key not in seen:
            seen.add(key)
            uniq.append(it)
    items = uniq
    for it in items:
        if it["kind"] == "command word":
            it["tests"] = [f"{Path(f).stem}.{n}" for f, n, b in bodies if re.search(r"\"[^\"\n]*\b" + re.escape(it["id"]) + r"\b[^\"\n]*\"", b)]
        else:
            it["tests"] = mentioned(it["name"], f'"{it["id"]}"')
        if it["kind"] == "saved data":
            it["reload_tests"] = [t for t in it["tests"] for f, n, b in bodies
                                  if f"{Path(f).stem}.{n}" == t and SAVE_WORDS.search(b)]
    # a block entity, workstation (POI) or menu registered under a tested block's id is exercised through that block
    tested_ids = {it["id"]: it for it in items if it["tests"] and it["kind"] in ("block", "item", "entity")}
    for it in items:
        if not it["tests"] and it["kind"] in ("block entity", "profession/poi", "menu") and it["id"] in tested_ids:
            it["via"] = f'{tested_ids[it["id"]]["kind"]} {it["id"]}'
            it["tests"] = tested_ids[it["id"]]["tests"]
    if areas_filter:
        items = [it for it in items if it["area"] in areas_filter]

    area_tests = {}
    for area, classes in area_classes.items():
        if areas_filter and area not in areas_filter:
            continue
        hits = set()
        for f, n, b in bodies:
            if (pkg + "." + area + ".") in b or any(re.search(r"(?<![\w])" + re.escape(c) + r"(?![\w])", b) for c in classes):
                hits.add(f"{Path(f).stem}.{n}")
        area_tests[area] = (len(classes), sorted(hits))

    # --- report
    print(f"# Test inventory: {root.name}")
    print(f"mod package `{pkg}`; {len(gametests)} GameTests in {len({f for f, _ in gametests})} files; "
          f"test sources: {', '.join(str(d.relative_to(root)) for d in test_dirs) or 'none'}\n")
    by_kind = defaultdict(list)
    for it in items:
        by_kind[it["kind"]].append(it)
    print("| kind | total | a test mentions it | no test mentions it |")
    print("|---|---|---|---|")
    for k in sorted(by_kind, key=lambda k: -len(by_kind[k])):
        its = by_kind[k]
        n_ok = sum(1 for i in its if i["tests"])
        print(f"| {k} | {len(its)} | {n_ok} | {len(its) - n_ok} |")
    saved = by_kind.get("saved data", [])
    if saved:
        n_rt = sum(1 for i in saved if i.get("reload_tests"))
        print(f"| saved data with a save/reload test | {len(saved)} | {n_rt} | {len(saved) - n_rt} |")

    print("\n## Gaps: nothing in the tests mentions these\n")
    order = ["saved data", "block", "block entity", "item", "entity", "menu", "network payload", "command word",
             "config key", "profession/poi", "game rule", "data component", "mixin"]
    for k in order + sorted(set(by_kind) - set(order)):
        gaps = [i for i in by_kind.get(k, []) if not i["tests"]]
        if gaps:
            print(f"- **{k}** ({len(gaps)}): " + ", ".join(label(i) for i in gaps))
    if saved:
        nort = [i for i in saved if i["tests"] and not i.get("reload_tests")]
        if nort:
            print(f"- **saved data mentioned in tests but never saved and reloaded** ({len(nort)}): "
                  + ", ".join(f"`{i['id']}`" for i in nort))

    print("\n## Feature areas\n")
    print("| area | main classes | test methods referencing it |")
    print("|---|---|---|")
    for area, (n, hits) in sorted(area_tests.items(), key=lambda kv: (len(kv[1][1]), kv[0])):
        print(f"| {area} | {n} | {len(hits)} |")

    if a.all:
        print("\n## Tested\n")
        for it in items:
            if it["tests"]:
                print(f"- {it['kind']} `{it['id']}`: {', '.join(it['tests'][:4])}{' ...' if len(it['tests']) > 4 else ''}")
    if a.json:
        for it in items:
            it.pop("decl", None)
        Path(a.json).write_text(json.dumps(dict(root=str(root), package=pkg, gametests=len(gametests), items=items,
                                                areas={k: dict(classes=v[0], tests=v[1]) for k, v in area_tests.items()}),
                                           indent=1))


if __name__ == "__main__":
    import signal
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)   # quiet when piped into head
    main()
