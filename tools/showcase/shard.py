#!/usr/bin/env python3
"""Runs a list of screenshot scenes one after another and turns each into the showcase's pictures and verdict.

    python3 tools/showcase/shard.py --scenes "quarry chef hall" --out build/showcase

For each scene: tools/screenshots/run.sh with the catalog's settings, then process.py on what it left in
versions/1.21.1/run/screenshots. Results land in <out>/<scene>/ (result.json, anim.gif, still-N.jpg). A scene that
crashes or runs over its time still gets a result: a failed one.
"""
import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import scenes  # noqa: E402
import process  # noqa: E402

RUN = os.path.join(ROOT, "versions", "1.21.1", "run", "screenshots")


def run_scene(name, out, limit):
    env = dict(os.environ)
    env.update(scenes.env(name))
    for stale in ("showcase.json", "client.log"):
        path = os.path.join(RUN, stale)
        if os.path.exists(path):
            os.remove(path)
    start = time.time()
    print(f"::group::scene {name}", flush=True)
    # Its own process group, so a scene that runs over its time goes down with Xvfb, Gradle and the client.
    proc = subprocess.Popen([os.path.join(ROOT, "tools/screenshots/run.sh")], cwd=ROOT, env=env, start_new_session=True)
    try:
        code = proc.wait(timeout=limit)
    except subprocess.TimeoutExpired:
        code = "timeout"
        for sig in (signal.SIGTERM, signal.SIGKILL):
            try:
                os.killpg(proc.pid, sig)
            except ProcessLookupError:
                break
            time.sleep(10)
        proc.wait()
    seconds = round(time.time() - start)
    print("::endgroup::", flush=True)
    raw = os.path.join(out, "_raw", name)
    shutil.rmtree(raw, ignore_errors=True)
    os.makedirs(raw, exist_ok=True)
    if os.path.isdir(os.path.join(RUN, "screenshots")):
        shutil.copytree(os.path.join(RUN, "screenshots"), os.path.join(raw, "screenshots"))
    for f in ("showcase.json", "client.log"):
        if os.path.exists(os.path.join(RUN, f)):
            shutil.copy(os.path.join(RUN, f), raw)
    latest = os.path.join(RUN, "logs", "latest.log")
    if not os.path.exists(os.path.join(raw, "client.log")) and os.path.exists(latest) and os.path.getmtime(latest) >= start:
        shutil.copy(latest, os.path.join(raw, "client.log"))  # run.sh was stopped before it could copy its log
    result = process.process(name, raw, os.path.join(out, name), seconds=seconds, exit_code=code)
    if not result["pass"] and os.path.exists(os.path.join(raw, "client.log")):
        # What a night run needs to find out why: the end of the game's log, next to the scene's pictures.
        with open(os.path.join(raw, "client.log"), errors="replace") as f:
            tail = f.readlines()[-4000:]
        with open(os.path.join(out, name, "client-log.txt"), "w") as f:
            f.writelines(tail)
        result["log"] = "client-log.txt"
        with open(os.path.join(out, name, "result.json"), "w") as f:
            json.dump(result, f, indent=1)
    shutil.rmtree(raw, ignore_errors=True)  # the frames are in the GIF now; keep the artifact small
    mark = "PASS" if result["pass"] else "FAIL"
    print(f"{mark} {name} ({seconds}s): " + "; ".join(result["reasons"] or [c["what"] for c in result["checks"]]), flush=True)
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--scenes", required=True, help="space-separated scene names")
    p.add_argument("--out", required=True)
    a = p.parse_args()
    os.makedirs(a.out, exist_ok=True)
    results = []
    for name in a.scenes.split():
        s = scenes.BY_NAME[name]
        limit = max(10 * 60, 3 * (s["est"] + scenes.STARTUP))
        results.append(run_scene(name, a.out, limit))
    failed = [r["scene"] for r in results if not r["pass"]]
    print(f"{len(results) - len(failed)} of {len(results)} scenes passed" + (f"; failed: {' '.join(failed)}" if failed else ""))


if __name__ == "__main__":
    main()
