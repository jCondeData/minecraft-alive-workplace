#!/usr/bin/env python3
"""The update rehearsal's report (ROADMAP 34.23), from the JSON files Rehearsal.java wrote.

  rehearsal.py <reports dir> <new server.log> <logaudit.txt> <audit exit code> <old version> <new jar> <out.md>

Per village: the classes seeded, the jobs kept, the treasury's takings and the rank before and after (never lower), and
every container's items as the old jar saved them and as the new one loaded them (equal); the tick p95 while the classes
were seeded (target: under 50 ms, a tick's budget at 20 a second) and the log audit. Exit code 1 when anything fails.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

RANKS = ["HAMLET", "VILLAGE", "TOWN", "CITY"]
P95_TARGET_MS = 50.0


def load(d, name):
    p = Path(d) / f"{name}.json"
    return json.loads(p.read_text()) if p.exists() else None


def by_name(report):
    return {v["name"]: v for v in (report or {}).get("villages", [])}


def jobs(village):
    return {p["uuid"]: p["job"] for p in village.get("villagers", []) if p["job"] != "minecraft:none"}


def two(x):
    return f"{x:.2f}" if isinstance(x, (int, float)) else "?"


def short(job):
    return job.split(":", 1)[1]


def main():
    d, log, audit_file, audit_code, old, new_jar, out = sys.argv[1:8]
    days = 2
    villages = json.loads((Path(d) / "villages.json").read_text())
    before, opened, after = load(d, "before"), load(d, "opened"), load(d, "after")
    control = load(d, "control")
    loaded = load(d, "opened-containers")
    if after:
        days = max(1, round((after["gameTime"] - before["gameTime"]) / 24000))
    text = Path(log).read_text(errors="replace")
    seeding = re.search(r"Rehearsal seeding: (.*)", text)
    p95 = re.search(r"p95 ([\d.]+) ms", seeding.group(1)) if seeding else None
    audit = Path(audit_file).read_text(errors="replace") if Path(audit_file).exists() else ""
    failures = []
    lines = [f"# The 1.8 update rehearsal", "",
             f"A world made with **{old}** (the last release before 1.8) on the Cobbleverse pack, saved, then opened with "
             f"**{new_jar}** with every expansion switched on, as 1.8 will ship, and run for {days} in-game days "
             f"(`tools/packtest/rehearsal.sh`, ROADMAP 34.23).", ""]
    if not (before and opened and after and loaded):
        missing = [n for n, r in (("before", before), ("opened", opened), ("after", after), ("opened-containers", loaded)) if not r]
        failures.append("reports missing: " + ", ".join(missing))
    B, O, A, L, C = by_name(before), by_name(opened), by_name(after), by_name(loaded), by_name(control)
    lines += ["## Per village", "",
              "| Village | Villagers (seeded / before / after) | Classes seeded | Jobs kept | Treasury takings a day, the same days (old jar / 1.8) | Rank (before / after) | Containers equal |",
              "|---|---|---|---|---|---|---|"]
    details = []
    for v in villages:
        n = v["name"]
        b, o, a, l, c = B.get(n, {}), O.get(n, {}), A.get(n, {}), L.get(n, {}), C.get(n, {})
        classes = Counter(p["class"] or "none" for p in a.get("villagers", []))
        cls = ", ".join(f"{c} {k}" for k, c in sorted(classes.items(), key=lambda kv: -kv[1])) or "none"
        jb, jo, ja = jobs(b), jobs(o), jobs(a)
        lost_open = sorted(short(jb[u]) for u in jb if jo.get(u) != jb[u])
        lost_after = sorted(short(jb[u]) for u in jb if ja.get(u) != jb[u])
        kept = len(jb) - len(lost_after)
        if lost_open:
            failures.append(f"{n}: jobs lost on opening: {', '.join(lost_open)}")
        if lost_after:
            failures.append(f"{n}: jobs lost by the end: {', '.join(lost_after)}")
        # Takings move from day to day by themselves (who ate, who slept): 1.8's days are compared with the same days of
        # the same saved world kept on the old jar (the control).
        take_after = (a.get("treasuryTotal", 0) - b.get("treasuryTotal", 0)) / days
        take_before = (c.get("treasuryTotal", 0) - b.get("treasuryTotal", 0)) / days if c else b.get("treasuryTotal", 0)
        if not c:
            failures.append(f"{n}: no control run to compare the takings with")
        if take_after < take_before:
            failures.append(f"{n}: the treasury took less a day with 1.8 ({take_after / 100:.2f}) than the old jar the same days ({take_before / 100:.2f})")
        rb, ra = b.get("rank", "?"), a.get("rank", "?")
        if rb in RANKS and ra in RANKS and RANKS.index(ra) < RANKS.index(rb):
            failures.append(f"{n}: rank fell from {rb} to {ra}")
        cb, cl = b.get("containers", {}), l.get("containers", {})
        differ = sorted(set(cb) | set(cl))
        differ = [p for p in differ if cb.get(p) != cl.get(p)]
        if differ:
            failures.append(f"{n}: containers differ on loading at {', '.join(differ)}")
        if a.get("classesVersion", 0) < 1:
            failures.append(f"{n}: the hall wasn't seeded (classesVersion {a.get('classesVersion')})")
        lines.append(f"| {n} | {v['size']} / {len(b.get('villagers', []))} / {len(a.get('villagers', []))} | {cls} | "
                     f"{kept} of {len(jb)} | {take_before / 100:.2f} / {take_after / 100:.2f} emeralds | {rb.lower()} / {ra.lower()} | "
                     f"{'yes' if not differ else 'NO'} ({len(cb)} containers, {sum(sum(x.values()) for x in cb.values())} items) |")
        det = [f"### {n}", "",
               f"- Jobs before: {', '.join(f'{c} {k}' for k, c in sorted(Counter(short(j) for j in jb.values()).items()))}.",
               f"- Jobs lost: on opening {', '.join(lost_open) or 'none'}; by the end {', '.join(lost_after) or 'none'}.",
               f"- Treasury: {b.get('treasury', 0) / 100:.2f} emeralds before (total taken {b.get('treasuryTotal', 0) / 100:.2f}), "
               f"{a.get('treasury', 0) / 100:.2f} after (total {a.get('treasuryTotal', 0) / 100:.2f}); the old jar's first day took "
               f"{b.get('treasuryTotal', 0) / 100:.2f}. Wellbeing {two(b.get('wellbeing'))} before, {two(c.get('wellbeing'))} on the old jar's last day, "
               f"{two(a.get('wellbeing'))} on 1.8's (fed {b.get('fed', '?')} / {c.get('fed', '?')} / {a.get('fed', '?')} of {b.get('adults', '?')} adults). "
               f"Research levels {b.get('research')} / {a.get('research')}.",
               f"- Couples (villagers with a partner): {sum(1 for p in b.get('villagers', []) if p['partner'])} before, "
               f"{sum(1 for p in a.get('villagers', []) if p['partner'])} after; children {sum(1 for p in b.get('villagers', []) if p['child'])} / "
               f"{sum(1 for p in a.get('villagers', []) if p['child'])}."]
        boarders = (sum(p["boarders"] for p in b.get("villagers", [])), sum(p["boarders"] for p in a.get("villagers", [])))
        revivals = ([p["revivalLeft"] for p in b.get("villagers", []) if p["revivals"]], [p["revivalLeft"] for p in a.get("villagers", []) if p["revivals"]])
        builders = (sum(1 for p in b.get("villagers", []) if p["builderJob"]), sum(1 for p in a.get("villagers", []) if p["builderJob"]))
        if any(boarders) or any(revivals) or any(builders):
            det.append(f"- Daycare boarders: {boarders[0]} before, {boarders[1]} after. Fossils reviving (ticks left): {revivals[0]} before, "
                       f"{revivals[1]} after. Builders on a site: {builders[0]} before, {builders[1]} after.")
            if boarders[1] < boarders[0]:
                failures.append(f"{n}: daycare boarders lost")
            if len(revivals[1]) < len(revivals[0]):
                failures.append(f"{n}: a fossil revival was lost")
        det.append("")
        det.append("| Container | Before (saved by the old jar) | As the new jar loaded it | After the days (workers use their stock) |")
        det.append("|---|---|---|---|")
        ca = a.get("containers", {})
        for p in sorted(set(cb) | set(cl) | set(ca)):
            fmt = lambda c: ", ".join(f"{short(k)} {x}" for k, x in sorted(c.items())) if c is not None else "(gone)"
            det.append(f"| {p} | {fmt(cb.get(p))} | {fmt(cl.get(p))} | {fmt(ca.get(p))} |")
        details += det + [""]
    pb, pa = (before or {}).get("parcels", []), (after or {}).get("parcels", [])
    sb, sa = (before or {}).get("sites", []), (after or {}).get("sites", [])
    if {p["id"] for p in pb} - {p["id"] for p in pa} and not any(p["status"] != "POSTED" for p in pa):
        failures.append("a parcel in the post vanished")
    lost_sites = {s["id"] for s in sb} - {s["id"] for s in sa}
    if lost_sites:
        failures.append(f"{len(lost_sites)} build site(s) vanished")
    lines += ["", f"Mail: {len(pb)} parcel(s) before ({', '.join(p['status'].lower() for p in pb) or 'none'}), {len(pa)} after "
              f"({', '.join(p['status'].lower() for p in pa) or 'none'}). Build sites: {', '.join(s['stage'].lower() for s in sb) or 'none'} before, "
              f"{', '.join(s['stage'].lower() for s in sa) or 'none'} after.", ""]
    lines += ["## Tick times while the classes were seeded", ""]
    if seeding:
        lines.append(f"`Rehearsal seeding: {seeding.group(1).strip()}` (target: p95 under {P95_TARGET_MS:.0f} ms, no tick over 50 ms of ours).")
        if not p95 or float(p95.group(1)) >= P95_TARGET_MS:
            failures.append(f"tick p95 while seeding {p95.group(1) if p95 else '?'} ms (target under {P95_TARGET_MS:.0f})")
        if "NOT every hall" in seeding.group(1):
            failures.append("not every hall was seeded")
    else:
        lines.append("No `Rehearsal seeding:` line: not measured.")
        failures.append("seeding tick times not measured")
    queries = re.findall(r"Percentiles: (.*)", text)
    if queries:
        lines.append(f"\n`/tick query` once seeded: {queries[0].strip()}")
    lines += ["", "## Log audit", "", f"`tools/modtest/logaudit.py` over the three runs' server logs, exit code {audit_code}:", "", "```"]
    lines += [l for l in audit.splitlines()[:40]]
    lines += ["```", ""]
    if str(audit_code) != "0":
        failures.append("the log audit isn't clean")
    lines += ["## Verdict", ""]
    lines += ["**Passed.**"] if not failures else ["**Did not pass:**", ""] + [f"- {f}" for f in failures]
    lines += ["", "## Limits", "",
              f"- The old jar is {old} built from its tag with one file added (`command/Rehearsal.java`, the seeding and these "
              "reports, benchmark-only): the published jar has no way to seed three villages.",
              "- The villages are staged by a command: beds in the open, no houses, so every household is seeded a Peasant; a "
              "real server's better homes give Artisans and Burghers. Each worker has a workstation; the store's food isn't "
              "eaten, so wellbeing is the same middling value with and without 1.8.",
              "- Takings are compared with the same saved world kept on the old jar for the same days (the control), because "
              "they move from day to day by themselves.",
              "- The worst tick while seeding is not attributed (no profile): the seeding window is the server's first seconds "
              "after start, when the pack is still settling, so it may be the pack's or ours. A profile of that window would tell."]
    lines += ["", "## Each village", ""] + details
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text("\n".join(lines) + "\n")
    print("\n".join(lines[:40]))
    print("Rehearsal passed." if not failures else "The rehearsal did not pass: " + "; ".join(failures))
    sys.exit(0 if not failures else 1)


if __name__ == "__main__":
    main()
