#!/usr/bin/env python3
"""Where a session's AI usage went, and a log of it across lanes, so costs can be cut where it's safe.

  python3 tools/agent/usage.py                          this session: totals, by model, by kind of work
  python3 tools/agent/usage.py --log --as NAME [--note TEXT]
                                                        also append one line to usage/<date>.jsonl on the
                                                        `usage` branch (every lane does this as its run ends)
  python3 tools/agent/usage.py --report [--days N]      all lanes' logged runs: cost per run, per lane, per item

Reads Claude Code's own transcript of this session (~/.claude/projects/*/<session>.jsonl) and its subagents'
(<session>/subagents/*.jsonl). Cost is the API-equivalent price: subscription limits aren't published per model,
but they follow the same token counts, so the price is the best common yardstick. Prices per million tokens
(platform.claude.com/docs/en/about-claude/pricing, 2026-10-03): input, 5-minute cache write, 1-hour cache write,
cache read, output.
"""
import argparse
import collections
import datetime as dt
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

PRICES = {  # $ per million tokens
    "opus-5-5": (4, 5, 8, 0.20, 20),
    "sonnet-5-5": (2, 2.5, 4, 0.20, 10),
    "haiku-4-5": (1, 1.25, 2, 0.10, 5),
    "fable-5-1": (10, 12.5, 20, 0.25, 50),
    "opus-5": (5, 6.25, 10, 0.50, 25),
    "sonnet-5": (2, 2.5, 4, 0.20, 10),
}
BASH_KINDS = [  # first match wins
    ("build: land/ship", r"sessions\.py (land|ship)"),
    ("build: gradle", r"gradlew"),
    ("screenshots", r"screenshots/run\.sh|showcase|make_gif|ffmpeg|sheet\.py"),
    ("roadmap helper", r"sessions\.py"),
    ("git", r"^\s*(cd [^;&]+[;&]+\s*)?git |\bgit (log|diff|show|status|fetch|push|pull|merge|commit|add)"),
    ("tests/tools (python)", r"python3? "),
    ("reading files (shell)", r"\b(cat|sed -n|head|tail|grep|rg|find|ls|wc)\b"),
]


def price(model):
    key = next((k for k in PRICES if k in (model or "")), None)
    return PRICES.get(key, PRICES["opus-5-5"]), key or "unknown"


def cost(usage, model):
    p, _ = price(model)
    cc = usage.get("cache_creation") or {}
    w1h = cc.get("ephemeral_1h_input_tokens", 0)
    w5m = cc.get("ephemeral_5m_input_tokens", usage.get("cache_creation_input_tokens", 0) - w1h)
    return (usage.get("input_tokens", 0) * p[0] + w5m * p[1] + w1h * p[2]
            + usage.get("cache_read_input_tokens", 0) * p[3] + usage.get("output_tokens", 0) * p[4]) / 1e6


def bash_kind(cmd):
    for name, rx in BASH_KINDS:
        if re.search(rx, cmd or ""):
            return name
    return "other shell"


def tool_kind(name, inp):
    if name == "Bash":
        return bash_kind(inp.get("command", ""))
    if name in ("Read", "Grep", "Glob"):
        return "reading files"
    if name in ("Edit", "Write", "NotebookEdit"):
        return "writing code"
    if name in ("Agent", "Task"):
        return "starting subagents"
    if name in ("WebFetch", "WebSearch"):
        return "web"
    if name.startswith("mcp__"):
        return "connectors: " + name.split("__")[1]
    if name in ("SendUserFile", "SendUserMessage"):
        return "messages"
    return "other tools"


def read_transcript(path):
    """Assistant messages (deduplicated: one message spans several lines), and tool results by kind."""
    msgs, order, kinds_by_id, result_chars = {}, [], {}, collections.Counter()
    first = last = None
    compactions = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                d = json.loads(line)
            except ValueError:
                continue
            ts = d.get("timestamp")
            if ts:
                first = first or ts
                last = ts
            if d.get("type") == "system" and d.get("subtype") == "compact_boundary":
                compactions += 1
            m = d.get("message") or {}
            if d.get("type") == "assistant" and m.get("usage"):
                mid = m.get("id") or d.get("uuid")
                if mid not in msgs:
                    order.append(mid)
                    msgs[mid] = {"model": m.get("model"), "usage": m["usage"], "kinds": []}
                else:
                    msgs[mid]["usage"] = m["usage"]
                for c in m.get("content") or []:
                    if c.get("type") == "tool_use":
                        k = tool_kind(c.get("name", ""), c.get("input") or {})
                        msgs[mid]["kinds"].append(k)
                        kinds_by_id[c.get("id")] = k
            elif d.get("type") == "user" and isinstance(m.get("content"), list):
                for c in m["content"]:
                    if c.get("type") == "tool_result":
                        body = c.get("content")
                        size = len(json.dumps(body)) if not isinstance(body, str) else len(body)
                        result_chars[kinds_by_id.get(c.get("tool_use_id"), "other tools")] += size
    return [msgs[i] for i in order], result_chars, first, last, compactions


def summarize(main_path):
    out = {"by_model": collections.defaultdict(lambda: {"cost": 0.0, "input": 0, "cache_write": 0, "cache_read": 0,
                                                         "output": 0, "turns": 0}),
           "by_kind": collections.Counter(), "subagents": [], "result_chars": collections.Counter()}
    msgs, rc, first, last, comp = read_transcript(main_path)
    out["result_chars"].update(rc)
    peak = 0

    def add(ms, label=None):
        nonlocal peak
        total = 0.0
        for m in ms:
            u, c = m["usage"], cost(m["usage"], m["model"])
            _, key = price(m["model"])
            b = out["by_model"][key]
            b["cost"] += c
            b["input"] += u.get("input_tokens", 0)
            b["cache_write"] += u.get("cache_creation_input_tokens", 0)
            b["cache_read"] += u.get("cache_read_input_tokens", 0)
            b["output"] += u.get("output_tokens", 0)
            b["turns"] += 1
            total += c
            if label:
                out["by_kind"][label] += c
            else:
                ks = m["kinds"] or ["thinking and answering"]
                for k in ks:
                    out["by_kind"][k] += c / len(ks)
                peak = max(peak, u.get("input_tokens", 0) + u.get("cache_read_input_tokens", 0)
                           + u.get("cache_creation_input_tokens", 0))
        return total

    out["main_cost"] = add(msgs)
    folder = main_path[:-len(".jsonl")]
    for sp in sorted(glob.glob(os.path.join(folder, "subagents", "*.jsonl"))):
        sm, src, *_ = read_transcript(sp)
        meta = {}
        try:
            with open(sp[:-len(".jsonl")] + ".meta.json", encoding="utf-8") as f:
                meta = json.load(f)
        except (OSError, ValueError):
            pass
        model = next((m["model"] for m in sm if m.get("model")), "?")
        c = add(sm, label="subagents")
        out["subagents"].append({"what": str(meta.get("description") or meta.get("agentType") or os.path.basename(sp))[:60],
                                 "model": model, "cost": round(c, 2), "turns": len(sm)})
    out["total_cost"] = sum(b["cost"] for b in out["by_model"].values())
    out["turns"] = len(msgs)
    out["peak_context"] = peak
    out["compactions"] = comp
    out["start"], out["end"] = first, last
    return out


def find_session(explicit=None):
    if explicit:
        return explicit
    base = os.environ.get("CLAUDE_CONFIG_DIR", os.path.expanduser("~/.claude"))
    files = glob.glob(os.path.join(base, "projects", "*", "*.jsonl"))
    if not files:
        sys.exit("No session transcript found under ~/.claude/projects.")
    return max(files, key=os.path.getmtime)


def minutes(a, b):
    try:
        f = lambda s: dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
        return round((f(b) - f(a)).total_seconds() / 60)
    except (TypeError, ValueError):
        return None


def print_summary(s):
    print(f"Session: {s['turns']} turns, {minutes(s['start'], s['end'])} min, peak context {s['peak_context']:,} tokens, "
          f"{s['compactions']} compactions. API-equivalent cost ${s['total_cost']:.2f}"
          f" (this session ${s['main_cost']:.2f}, subagents ${s['total_cost'] - s['main_cost']:.2f}).")
    print("\nBy model:")
    for k, b in sorted(s["by_model"].items(), key=lambda x: -x[1]["cost"]):
        print(f"  {k:12} ${b['cost']:8.2f}  turns {b['turns']:5}  input {b['input']:>11,}  cache write "
              f"{b['cache_write']:>11,}  cache read {b['cache_read']:>13,}  output {b['output']:>10,}")
    print("\nBy kind of work (each turn's cost split over the tools it called):")
    for k, c in s["by_kind"].most_common():
        print(f"  {k:28} ${c:8.2f}  {100 * c / max(s['total_cost'], 1e-9):5.1f}%")
    print("\nTool output put into the context (characters), by kind:")
    for k, n in s["result_chars"].most_common(8):
        print(f"  {k:28} {n:>12,}")
    if s["subagents"]:
        print(f"\nSubagents ({len(s['subagents'])}):")
        for a in sorted(s["subagents"], key=lambda a: -a["cost"]):
            print(f"  ${a['cost']:7.2f}  {a['model']:18} {a['turns']:4} turns  {a['what']}")


def git(*args, cwd=None, check=True):
    r = subprocess.run(["git", *args], cwd=cwd, text=True, capture_output=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(args)}: {r.stderr.strip()}")
    return r


def log_run(s, who, note):
    """Append one JSON line to usage/<date>.jsonl on the `usage` branch (never fetched by lanes)."""
    rec = {"who": who, "note": note, "logged": dt.datetime.now(dt.timezone.utc).isoformat(timespec="minutes"),
           "start": s["start"], "end": s["end"], "minutes": minutes(s["start"], s["end"]),
           "cost": round(s["total_cost"], 3), "main_cost": round(s["main_cost"], 3), "turns": s["turns"],
           "peak_context": s["peak_context"], "compactions": s["compactions"],
           "by_model": {k: {kk: (round(vv, 3) if isinstance(vv, float) else vv) for kk, vv in b.items()}
                        for k, b in s["by_model"].items()},
           "by_kind": {k: round(v, 3) for k, v in s["by_kind"].items()},
           "result_chars": dict(s["result_chars"]), "subagents": s["subagents"]}
    day = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")
    for _ in range(5):
        exists = git("fetch", "--quiet", "origin", "+refs/heads/usage:refs/remotes/origin/usage",
                     check=False).returncode == 0
        with tempfile.TemporaryDirectory() as tmp:
            wt = os.path.join(tmp, "wt")
            if exists:
                git("worktree", "add", "--quiet", "--detach", wt, "origin/usage")
            else:
                git("worktree", "add", "--quiet", "--detach", wt, "HEAD")
                git("checkout", "--quiet", "--orphan", "usage-new", cwd=wt)
                git("rm", "-rf", "--quiet", ".", cwd=wt)
            try:
                os.makedirs(os.path.join(wt, "usage"), exist_ok=True)
                with open(os.path.join(wt, "usage", f"{day}.jsonl"), "a", encoding="utf-8") as f:
                    f.write(json.dumps(rec) + "\n")
                git("add", "usage", cwd=wt)
                git("commit", "--quiet", "-m", f"Usage of {who}", cwd=wt)
                if git("push", "--quiet", "origin", "HEAD:refs/heads/usage", cwd=wt, check=False).returncode == 0:
                    print(f"\nLogged to the usage branch (usage/{day}.jsonl).")
                    return
            finally:
                git("worktree", "remove", "--force", wt, check=False)
                git("branch", "-D", "usage-new", check=False)
    print("\nCouldn't push the usage log; it's not important, carry on.")


def report(days):
    git("fetch", "--quiet", "origin", "+refs/heads/usage:refs/remotes/origin/usage")
    names = git("ls-tree", "--name-only", "origin/usage", "usage/").stdout.split()
    since = dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)
    runs = []
    for n in names:
        for line in git("show", f"origin/usage:{n}").stdout.splitlines():
            try:
                r = json.loads(line)
            except ValueError:
                continue
            if dt.datetime.fromisoformat(r["logged"]) >= since:
                runs.append(r)
    if not runs:
        sys.exit("No runs logged in that time.")
    git("fetch", "--quiet", "origin", "+refs/heads/main:refs/remotes/origin/main", check=False)
    landed = collections.Counter()
    for subj in git("log", "origin/main", f"--since={since.isoformat()}", "--format=%s").stdout.splitlines():
        m = re.match(r"Mark \S+ (built|done).*\[(\S+)\]$", subj)
        v = re.match(r"Verify (.+) \[(\S+)\]$", subj)
        if m:
            landed[m.group(2)] += 1
        elif v:
            landed[v.group(2)] += len(v.group(1).split(", "))
    lane = lambda who: re.sub(r"-\d{4}-\d{4}$", "", who)
    by = collections.defaultdict(lambda: {"runs": 0, "cost": 0.0, "items": 0, "minutes": 0, "kinds": collections.Counter(),
                                          "models": collections.Counter()})
    for r in runs:
        b = by[lane(r["who"])]
        b["runs"] += 1
        b["cost"] += r["cost"]
        b["minutes"] += r.get("minutes") or 0
        b["items"] += landed.get(r["who"], 0)
        b["kinds"].update(r.get("by_kind", {}))
        b["models"].update({k: v["cost"] for k, v in r.get("by_model", {}).items()})
    total = sum(b["cost"] for b in by.values())
    print(f"{len(runs)} runs in the last {days} day(s): API-equivalent ${total:.2f}")
    print(f"\n{'lane':12} {'runs':>4} {'$ total':>9} {'$/run':>7} {'items':>5} {'$/item':>7}  biggest costs")
    for k, b in sorted(by.items(), key=lambda x: -x[1]["cost"]):
        top = ", ".join(f"{n} {100 * c / max(b['cost'], 1e-9):.0f}%" for n, c in b["kinds"].most_common(3))
        per_item = f"{b['cost'] / b['items']:7.2f}" if b["items"] else "      -"
        print(f"{k:12} {b['runs']:4} {b['cost']:9.2f} {b['cost'] / b['runs']:7.2f} {b['items']:5} {per_item}  {top}")
    kinds = collections.Counter()
    for b in by.values():
        kinds.update(b["kinds"])
    print("\nAll lanes, by kind of work:")
    for n, c in kinds.most_common(12):
        print(f"  {n:28} ${c:8.2f}  {100 * c / max(total, 1e-9):5.1f}%")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--session", help="a transcript .jsonl (default: this session's)")
    ap.add_argument("--log", action="store_true")
    ap.add_argument("--as", dest="as_")
    ap.add_argument("--note", default="")
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--days", type=float, default=1)
    a = ap.parse_args()
    if a.report:
        report(a.days)
        return
    s = summarize(find_session(a.session))
    print_summary(s)
    if a.log:
        if not a.as_:
            sys.exit("--log needs --as <you>")
        log_run(s, a.as_, a.note)


if __name__ == "__main__":
    main()
