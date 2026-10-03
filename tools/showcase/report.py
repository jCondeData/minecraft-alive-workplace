#!/usr/bin/env python3
"""The nightly-tests issue text for a showcase run, and the run's job summary.

    python3 tools/showcase/report.py SITE/showcase.json --issue issue.md --summary summary.md

Exits 0 when there's nothing to report (every scene passed, no broken assets), 1 when issue.md should be filed.
"""
import argparse
import json
import sys


def main():
    p = argparse.ArgumentParser()
    p.add_argument("summary_json")
    p.add_argument("--issue", required=True)
    p.add_argument("--summary", required=True)
    a = p.parse_args()
    with open(a.summary_json) as f:
        s = json.load(f)
    failed = [x for x in s["scenes"] if not x["pass"]]
    page = s["page"]
    rows = "\n".join(f"| {'✅' if x['pass'] else '❌'} | `{x['name']}` | {x['group']}: {x['title']} | "
                     f"{'; '.join(x['reasons'][:3]) if not x['pass'] else ''} | "
                     f"{round(x['seconds'] / 60, 1) if x.get('seconds') else ''} |" for x in s["scenes"])
    with open(a.summary, "w") as f:
        f.write(f"## Showcase: {s['passed']} passed, {s['failed']} failed\n\nVersion {s['version']}, commit {s['commit']}. "
                f"Page: {page}\n\n| | Scene | What | Why it failed | Minutes |\n|---|---|---|---|---|\n{rows}\n")
        if s["assets"]:
            f.write("\n### Missing textures or models in the client log\n" + "\n".join(f"- {x}" for x in s["assets"]) + "\n")
    if not failed and not s["assets"]:
        open(a.issue, "w").close()
        return 0
    lines = [f"Showcase run failed: {s['run']}", "", f"Page: {page}#{failed[0]['name']}" if failed else f"Page: {page}", "",
             f"{s['failed']} of {s['passed'] + s['failed']} scenes failed (version {s['version']}, commit {s['commit']}).", ""]
    for x in failed:
        lines.append(f"- **{x['group']}: {x['title']}** (`SCENE={x['name']} tools/screenshots/run.sh`)")
        lines += [f"  - {r}" for r in x["reasons"][:6]]
    if s["assets"]:
        lines += ["", "Missing textures or models in the client log (every scene):"] + [f"- {x}" for x in s["assets"][:30]]
    lines += ["", "Each failed scene is a bug (`sessions.py bug`): reproduce it with the command above, fix it, and check the "
              "scene passes. How a scene is judged: `tools/showcase/process.py`."]
    with open(a.issue, "w") as f:
        f.write("\n".join(lines) + "\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
