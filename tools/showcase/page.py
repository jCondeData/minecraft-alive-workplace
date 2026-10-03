#!/usr/bin/env python3
"""Builds the showcase page from every shard's results.

    python3 tools/showcase/page.py --results build/showcase --site build/showcase-site [--run-url URL]

The results folder has one folder per scene (result.json, anim.gif, still-N.jpg, more-N.jpg), however many shards made
them. The site gets index.html (one phone-friendly page, grouped by job, failures first), a folder per scene with its
pictures, and showcase.json (the verdicts and times, which the next night's plan reads to balance its shards).
Scenes in the catalog with no result count as failed.
"""
import argparse
import datetime as dt
import html
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import scenes  # noqa: E402

PAGE_URL = "https://jcondedata.github.io/minecraft-alive-workplace/"


def version():
    text = open(os.path.join(ROOT, "stonecutter.properties.toml"), encoding="utf-8").read()
    m = re.search(r'^mod\.version\s*=\s*"([^"]+)"', text, re.M)
    return m.group(1) if m else "?"


def commit():
    sha = os.environ.get("GITHUB_SHA")
    if not sha:
        r = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, stdout=subprocess.PIPE)
        sha = r.stdout.strip()
    return sha[:7]


def central_now():
    try:
        from zoneinfo import ZoneInfo
        return dt.datetime.now(ZoneInfo("America/Chicago"))
    except Exception:  # no tz database: UTC it is
        return dt.datetime.now(dt.timezone.utc)


def load(results_dir):
    out = {}
    for name in os.listdir(results_dir) if os.path.isdir(results_dir) else []:
        path = os.path.join(results_dir, name, "result.json")
        if os.path.exists(path):
            with open(path) as f:
                out[name] = json.load(f)
    for s in scenes.SCENES:
        if s["name"] not in out:
            out[s["name"]] = {"scene": s["name"], "title": s["title"], "group": s["group"], "what": s["what"],
                              "pass": False, "reasons": ["no result: its job stopped before this scene ran"],
                              "checks": [], "problems": [], "warnings": [], "assets": [], "stills": [], "more": [],
                              "gif": None, "frames": 0, "seconds": None}
    return out


E = html.escape

CSS = """
:root{--bg:#f6f5f2;--card:#fff;--ink:#1d1d1b;--muted:#6b6a66;--line:#e3e1dc;--pass:#1f7a3f;--pass-bg:#e5f3ea;
--fail:#b3261e;--fail-bg:#fbe9e7;--accent:#2f5d8a;--shade:rgba(0,0,0,.04)}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#141413;--card:#1e1e1c;--ink:#ecebe7;--muted:#a09e98;
--line:#34332f;--pass:#6fcf8f;--pass-bg:#16301f;--fail:#ff8a80;--fail-bg:#3a1a17;--accent:#8ab4e0;--shade:rgba(255,255,255,.04)}}
:root[data-theme="dark"]{--bg:#141413;--card:#1e1e1c;--ink:#ecebe7;--muted:#a09e98;--line:#34332f;--pass:#6fcf8f;
--pass-bg:#16301f;--fail:#ff8a80;--fail-bg:#3a1a17;--accent:#8ab4e0;--shade:rgba(255,255,255,.04)}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
header{padding:20px 16px 8px;max-width:820px;margin:0 auto}
h1{font-size:22px;margin:0 0 4px;letter-spacing:-.01em}.meta{color:var(--muted);font-size:14px}
.meta a{color:var(--accent)}
.score{display:flex;gap:8px;flex-wrap:wrap;margin:14px 0 4px}
.chip{border-radius:999px;padding:4px 12px;font-size:14px;font-weight:600;background:var(--shade)}
.chip.pass{background:var(--pass-bg);color:var(--pass)}.chip.fail{background:var(--fail-bg);color:var(--fail)}
main{max-width:820px;margin:0 auto;padding:0 16px 40px}
.box{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px;margin:12px 0}
.box.bad{border-color:var(--fail);background:var(--fail-bg)}
.box h2{font-size:16px;margin:0 0 6px}.box ul{margin:0;padding-left:18px}.box li{margin:3px 0}
.box a{color:var(--ink)}
details.jump{margin:12px 0}details.jump summary{cursor:pointer;color:var(--accent);font-size:15px}
nav.groups{display:flex;gap:6px;flex-wrap:wrap;margin:10px 0}
nav.groups a{font-size:13px;padding:3px 9px;border-radius:999px;background:var(--card);border:1px solid var(--line);
color:var(--ink);text-decoration:none}
nav.groups a.has-fail{border-color:var(--fail);color:var(--fail)}
section>h2{font-size:18px;margin:28px 0 8px;display:flex;gap:8px;align-items:baseline}
section>h2 small{color:var(--muted);font-weight:400;font-size:13px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;margin:12px 0;overflow:hidden}
.card.fail{border-color:var(--fail)}
.head{display:flex;justify-content:space-between;gap:10px;align-items:flex-start;padding:12px 14px 6px}
.head h3{font-size:16px;margin:0}.head code{font-size:12px;color:var(--muted)}
.badge{flex:none;font-size:12px;font-weight:700;border-radius:6px;padding:3px 8px;letter-spacing:.03em}
.badge.pass{background:var(--pass-bg);color:var(--pass)}.badge.fail{background:var(--fail-bg);color:var(--fail)}
.why{padding:0 14px 8px;font-size:14px;color:var(--muted)}.why ul{margin:0;padding:0;list-style:none}
.why li.bad{color:var(--fail)}.why li.ok{color:var(--muted)}
.gif{display:block;width:100%;background:#000;aspect-ratio:16/9;object-fit:contain}
.stills{display:grid;grid-template-columns:1fr 1fr;gap:6px;padding:8px}
.stills figure{margin:0}.stills img{width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block;background:#000}
.stills figcaption{font-size:13px;color:var(--muted);padding:3px 2px 0}
.foot{display:flex;justify-content:space-between;padding:0 14px 12px;font-size:13px;color:var(--muted)}
.foot a{color:var(--accent)}
.toggle{font-size:14px;display:flex;gap:6px;align-items:center;margin:10px 0}
body.only-fail .card:not(.fail){display:none}body.only-fail section:not(.has-fail){display:none}
footer{max-width:820px;margin:0 auto;padding:0 16px 40px;color:var(--muted);font-size:13px}
"""


def card(r):
    ok = r["pass"]
    lines = []
    if ok:
        lines += [f'<li class="ok">✓ {E(c["what"])}</li>' for c in r.get("checks", [])]
    else:
        lines += [f'<li class="bad">✗ {E(x)}</li>' for x in r.get("reasons", [])]
        lines += [f'<li class="ok">✓ {E(c["what"])}</li>' for c in r.get("checks", []) if c.get("pass")]
    notes = [w for w in r.get("warnings", []) if not w.startswith("another mod's text key")]  # Cobblemon's HUD: noise here
    lines += [f'<li class="ok">note: {E(w)}</li>' for w in notes[:3]]
    name = r["scene"]
    gif = f'<img class="gif" src="{name}/{r["gif"]}" alt="{E(r["title"])}, start to end" loading="lazy">' if r.get("gif") else ""
    stills = "".join(
        f'<figure><a href="{name}/{s["file"]}"><img src="{name}/{s["file"]}" alt="{E(s["label"])}" loading="lazy"></a>'
        f'<figcaption>{E(s["label"])}</figcaption></figure>' for s in r.get("stills", []))
    more = f'<a href="{name}/index.html">all {len(r["more"]) + len(r["stills"])} pictures</a>' if r.get("more") else ""
    if r.get("broken"):
        stills += "".join(f'<figure><a href="{name}/{b["file"]}"><img src="{name}/{b["file"]}" alt="{E(b["label"])}" loading="lazy"></a>'
                          f'<figcaption>⚠ {E(b["label"])}</figcaption></figure>' for b in r["broken"])
    secs = f'{round(r["seconds"] / 60, 1)} min' if r.get("seconds") else ""
    if r.get("log"):
        more = f'<a href="{name}/{r["log"]}">the game\'s log</a> ' + more
    return (f'<article class="card {"pass" if ok else "fail"}" id="{name}"><div class="head"><div><h3>{E(r["title"])}</h3>'
            f'<code>SCENE={name}</code></div><span class="badge {"pass" if ok else "fail"}">{"PASS" if ok else "FAIL"}</span></div>'
            f'<div class="why"><ul>{"".join(lines)}</ul></div>{gif}'
            f'{f"<div class=stills>{stills}</div>" if stills else ""}<div class="foot"><span>{secs}</span>{more}</div></article>')


def more_page(r):
    items = r.get("stills", []) + r.get("more", [])
    figs = "".join(f'<figure><a href="{s["file"]}"><img src="{s["file"]}" alt="{E(s["label"])}" loading="lazy"></a>'
                   f'<figcaption>{E(s["label"])}</figcaption></figure>' for s in items)
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<title>{E(r["title"])}</title><style>{CSS}</style></head><body><header><h1>{E(r["title"])}</h1>'
            f'<div class="meta"><a href="../index.html#{r["scene"]}">← back to the showcase</a></div></header>'
            f'<main><div class="card"><div class="stills">{figs}</div></div></main></body></html>')


def build(results_dir, site, run_url):
    results = load(results_dir)
    if os.path.isdir(site):
        shutil.rmtree(site)
    os.makedirs(site)
    for name, r in results.items():
        src = os.path.join(results_dir, name)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(site, name), ignore=shutil.ignore_patterns("result.json"))
            if r.get("more"):
                with open(os.path.join(site, name, "index.html"), "w") as f:
                    f.write(more_page(r))
    order = {g: i for i, g in enumerate(scenes.GROUPS)}
    by_group = {}
    for r in results.values():
        by_group.setdefault(r["group"], []).append(r)
    groups = sorted(by_group, key=lambda g: order.get(g, 999))
    catalog_order = {s["name"]: i for i, s in enumerate(scenes.SCENES)}
    for g in groups:
        by_group[g].sort(key=lambda r: catalog_order.get(r["scene"], 999))

    failed = [r for r in results.values() if not r["pass"]]
    failed.sort(key=lambda r: (order.get(r["group"], 999), catalog_order.get(r["scene"], 999)))
    assets = sorted({a for r in results.values() for a in r.get("assets", [])})
    now = central_now()
    ver, sha = version(), commit()
    date = now.strftime("%a %-d %b %Y, %-I:%M %p ") + (now.tzname() or "")
    total = len(results)

    parts = [f'<!doctype html><html lang="en"><head><meta charset="utf-8">'
             f'<meta name="viewport" content="width=device-width,initial-scale=1"><title>Alive Workplace showcase</title>'
             f'<style>{CSS}</style></head><body><header><h1>Alive Workplace: nightly showcase</h1>'
             f'<div class="meta">{E(date)} · version <b>{E(ver)}</b> · main @ {E(sha)}'
             + (f' · <a href="{E(run_url)}">the run</a>' if run_url else "") + '</div>'
             f'<div class="score"><span class="chip pass">{total - len(failed)} passed</span>'
             f'<span class="chip {"fail" if failed else ""}">{len(failed)} failed</span>'
             f'<span class="chip">{total} scenes</span></div></header><main>']
    if failed or assets:
        items = "".join(f'<li><a href="#{r["scene"]}">{E(r["group"])}: {E(r["title"])}</a> — {E("; ".join(r["reasons"][:2]))}</li>'
                        for r in failed)
        items += "".join(f"<li>{E(a)}</li>" for a in assets[:15])
        parts.append(f'<div class="box bad"><h2>Look at these first</h2><ul>{items}</ul></div>')
        parts.append('<label class="toggle"><input type="checkbox" onchange="document.body.classList.toggle(\'only-fail\',this.checked)">'
                     ' Show only what failed</label>')
    nav = "".join(f'<a href="#g{i}" class="{"has-fail" if any(not r["pass"] for r in by_group[g]) else ""}">{E(g)}</a>'
                  for i, g in enumerate(groups))
    parts.append(f'<details class="jump"><summary>Jump to a job</summary><nav class="groups">{nav}</nav></details>')
    for i, g in enumerate(groups):
        rs = by_group[g]
        bad = sum(1 for r in rs if not r["pass"])
        parts.append(f'<section id="g{i}" class="{"has-fail" if bad else ""}"><h2>{E(g)} <small>'
                     f'{len(rs) - bad}/{len(rs)} passed</small></h2>' + "".join(card(r) for r in rs) + "</section>")
    parts.append('</main><footer>Made every night on GitHub\'s machines by <code>.github/workflows/showcase.yml</code>: each scene '
                 'is the real game, filmed by <code>tools/screenshots/run.sh</code>. A scene passes only if its job visibly '
                 'did its work and no picture looks broken. Failures open the <code>nightly-tests</code> issue.</footer></body></html>')
    with open(os.path.join(site, "index.html"), "w") as f:
        f.write("".join(parts))
    open(os.path.join(site, ".nojekyll"), "w").close()
    summary = {"date": now.isoformat(), "version": ver, "commit": sha, "run": run_url, "page": PAGE_URL,
               "passed": total - len(failed), "failed": len(failed), "assets": assets,
               "scenes": [{"name": r["scene"], "group": r["group"], "title": r["title"], "pass": r["pass"],
                           "reasons": r["reasons"], "seconds": r.get("seconds")} for r in
                          sorted(results.values(), key=lambda r: catalog_order.get(r["scene"], 999))]}
    with open(os.path.join(site, "showcase.json"), "w") as f:
        json.dump(summary, f, indent=1)
    return summary


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--results", required=True)
    p.add_argument("--site", required=True)
    p.add_argument("--run-url", default=os.environ.get("RUN_URL", ""))
    a = p.parse_args()
    s = build(a.results, a.site, a.run_url)
    print(f"{s['passed']} passed, {s['failed']} failed, {len(s['assets'])} asset problems -> {a.site}/index.html")


if __name__ == "__main__":
    main()
