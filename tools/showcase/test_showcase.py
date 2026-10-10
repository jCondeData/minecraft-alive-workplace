#!/usr/bin/env python3
"""Tests for the nightly showcase's tools (ROADMAP 22.4), without Minecraft: the catalog and shard plan (scenes.py),
how a scene is judged (process.py), the page (page.py) and the nightly-tests issue (report.py). Run after changing
any of them:

  python3 tools/showcase/test_showcase.py

Each judging test builds a scene's raw output by hand (showcase.json, stills, GIF frames, client.log), the way
tools/screenshots/run.sh leaves it, and checks the verdict against the spec: a scene passes only if its job visibly
did its work and no picture looks broken (missing textures, raw text keys, villagers stuck in walls).
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

import page  # noqa: E402
import process  # noqa: E402
import scenes  # noqa: E402

failures = []


def check(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        failures.append(name)
        if detail:
            print("     " + str(detail).strip().replace("\n", "\n     "))


# --- Building a scene's raw output ------------------------------------------------------------------------------

def grass(w, h, seed):
    """A picture with something in it (not blank): noisy green over blue, like the superflat world."""
    rnd = np.random.default_rng(seed)
    a = np.zeros((h, w, 3), dtype=np.uint8)
    a[: h // 3] = (150, 190, 250)
    a[h // 3:] = (80, 120, 50)
    a = np.clip(a.astype(np.int16) + rnd.integers(-25, 25, size=a.shape), 0, 255).astype(np.uint8)
    return Image.fromarray(a)


def missing_texture(im, x, y, square):
    """Minecraft's missing texture: a 2x2 checkerboard of magenta (#F800F8) and black squares."""
    d = ImageDraw.Draw(im)
    for i in range(2):
        for j in range(2):
            colour = (248, 0, 248) if (i + j) % 2 == 0 else (0, 0, 0)
            d.rectangle((x + i * square, y + j * square, x + (i + 1) * square - 1, y + (j + 1) * square - 1), fill=colour)
    return im


def raw_scene(tmp, name, checks=(("the job did its work", True),), stills=None, frames=6, report=True, problems=(),
              missing_keys=(), other_keys=(), log="", still_edit=None, frame_edit=None):
    """A scene's raw output as run.sh leaves it. stills: file stems to write (default: what the catalog wants)."""
    raw = os.path.join(tmp, "raw", name)
    shutil.rmtree(raw, ignore_errors=True)
    shots = os.path.join(raw, "screenshots")
    os.makedirs(os.path.join(shots, "gif"), exist_ok=True)
    if stills is None:
        stills = []
        for spec, _ in scenes.BY_NAME[name]["stills"]:
            pattern, _, how = spec.partition("@")
            stills += [pattern.replace("*", f"{i:05d}") for i in range(3)] if how else [pattern]
    for i, stem in enumerate(stills):
        im = grass(960, 540, i)
        if still_edit:
            im = still_edit(stem, im)
        im.save(os.path.join(shots, stem + ".png"))
    for i in range(frames):
        im = grass(480, 270, 100 + i)
        # Each frame carries where it is in the scene (green: 0 first, 255 last), to check the GIF runs start to end.
        ImageDraw.Draw(im).rectangle((0, 0, 200, 200), fill=(255, round(i * 255 / max(1, frames - 1)), 0))
        if i == frames - 1:
            ImageDraw.Draw(im).rectangle((0, 0, 200, 200), fill=(0, 0, 255))  # the very last frame: blue
        if frame_edit:
            im = frame_edit(i, im)
        im.save(os.path.join(shots, "gif", f"{20 + i * 10:05d}.png"))
    if report:
        with open(os.path.join(raw, "showcase.json"), "w") as f:
            json.dump({"scene": name, "checks": [{"pass": ok, "what": what, "tick": 100} for what, ok in checks],
                       "problems": list(problems), "missingKeys": list(missing_keys), "otherMissingKeys": list(other_keys),
                       "ticks": 200, "frames": frames, "seconds": 30, "stopped": True}, f)
    with open(os.path.join(raw, "client.log"), "w") as f:
        f.write("[12:00:00] [Render thread/INFO] (Minecraft) Setting user: Player1\n" + log)
    return raw


def judge(tmp, name, **kw):
    exit_code = kw.pop("exit_code", 0)
    raw = raw_scene(tmp, name, **kw)
    out = os.path.join(tmp, "out", name)
    return process.process(name, raw, out, seconds=42, exit_code=exit_code), out


# --- The catalog and the shard plan -----------------------------------------------------------------------------

def catalog_tests(tmp):
    r = subprocess.run([sys.executable, os.path.join(HERE, "scenes.py"), "check"], cwd=ROOT, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    check("catalog: every harness scene is in the catalog, and back", r.returncode == 0, r.stdout)

    # The check notices a scene added to the harness but not the catalog, and a catalog scene the harness lost.
    fake = os.path.join(tmp, "fake")
    dev = os.path.join(fake, "src/devclient/java/io/github/jcondedata/aliveworkplace/devclient")
    os.makedirs(dev)
    os.makedirs(os.path.join(fake, "tools/showcase"))
    shutil.copy(os.path.join(HERE, "scenes.py"), os.path.join(fake, "tools/showcase/scenes.py"))
    names = [s["name"] for s in scenes.SCENES if s["name"] != "builders"]
    java = "".join(f'if ("{n}".equals(System.getProperty("aliveworkplace.scene"))) {{}}\n' for n in names[1:])
    java += 'SCENES.put("brand_new_job", job("x", 1, null));\n'
    with open(os.path.join(dev, "Fake.java"), "w") as f:
        f.write(java)
    r = subprocess.run([sys.executable, os.path.join(fake, "tools/showcase/scenes.py"), "check"], text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    check("catalog: the check fails on a harness scene missing from the catalog",
          r.returncode != 0 and "brand_new_job" in r.stdout, r.stdout)
    check("catalog: the check fails on a catalog scene the harness no longer has",
          r.returncode != 0 and repr(names[0]) in r.stdout, r.stdout)

    bad = [s["name"] for s in scenes.SCENES
           if not (2 <= len(s["stills"]) <= 4 or (len(s["stills"]) == 1 and s["stills"][0][0].endswith("@spread")))]
    check("catalog: every scene shows 2-4 stills (or 4 picked from a gallery)", not bad, bad)
    unlabelled = [s["name"] for s in scenes.SCENES for spec, label in s["stills"] if not label and not spec.endswith("@spread")]
    check("catalog: every still has a label", not unlabelled, unlabelled)
    check("catalog: every scene's group is a page section", {s["group"] for s in scenes.SCENES} <= set(scenes.GROUPS),
          {s["group"] for s in scenes.SCENES} - set(scenes.GROUPS))
    check("catalog: scene names are unique", len(scenes.BY_NAME) == len(scenes.SCENES))

    # 22.3: every job in the README has a scene.
    readme = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
    table = readme.split("## All the jobs at a glance", 1)[1].split("\n## ", 1)[0]
    jobs = [re.sub(r"\s*\((with )?Cobblemon\)$", "", row.split("|")[1].strip()) for row in table.splitlines()
            if row.startswith("| ") and not row.startswith("| Job") and not row.startswith("| ---")]
    groups = {s["group"] for s in scenes.SCENES}
    missing = [j for j in jobs if j not in groups]
    check(f"catalog: each of the README's {len(jobs)} jobs has a scene", len(jobs) >= 40 and not missing, missing)

    # Build families: every blueprint list the mod ships is filmed.
    fams = {"gallery", "decor", "defences", "workshops", "styles", "village", "camp"}
    check("catalog: every build family has a scene", fams <= set(scenes.BY_NAME), fams - set(scenes.BY_NAME))

    # Cobblemon scenes get Cobblemon in the client, the others don't pay for it.
    wrong = [s["name"] for s in scenes.SCENES if (scenes.env(s["name"])["COBBLEMON"] == "true") != s["cobblemon"]]
    check("catalog: run.sh gets COBBLEMON=true exactly for the Cobblemon scenes", not wrong, wrong)
    check("catalog: the battle scene gets Mega Showdown", scenes.env("battle")["MEGA"] == "true"
          and scenes.env("quarry")["MEGA"] == "false")
    check("catalog: a scene's own settings reach run.sh", scenes.env("village").get("WORKSHOP_WEIGHT") == "200")

    # The plan: every scene exactly once, shards short enough for the whole run to finish within the hour.
    for label, last in [("estimates", {}),
                        ("last night's times", {s["name"]: (s["est"] + scenes.STARTUP) * 2 for s in scenes.SCENES}),
                        ("one scene that hung", {"fossil": 620})]:
        m = scenes.matrix(last)["include"]
        names = [n for x in m for n in x["scenes"].split()]
        check(f"plan ({label}): every scene in exactly one shard",
              sorted(names) == sorted(scenes.BY_NAME), set(scenes.BY_NAME) ^ set(names))
        longest = max(scenes.seconds(s, last) for s in scenes.SCENES)
        # A shard with one scene can't be made shorter: city_timelapse alone is half an hour by its estimate (B100).
        shared = [sum(scenes.seconds(scenes.BY_NAME[n], last) for n in x["scenes"].split()) for x in m
                  if len(x["scenes"].split()) > 1]
        worst = max(shared, default=0)
        check(f"plan ({label}): no shard of several scenes runs over half an hour plus its longest scene, or 45 minutes",
              worst <= min(scenes.SHARD_TARGET + longest, 45 * 60), f"{worst / 60:.1f} min")
        check(f"plan ({label}): at most {scenes.MAX_SHARDS} parallel jobs", 1 <= len(m) <= scenes.MAX_SHARDS, len(m))
    last = os.path.join(tmp, "last.json")
    with open(last, "w") as f:
        json.dump({"scenes": [{"name": "quarry", "seconds": 999}, {"name": "gone_scene", "seconds": 5}]}, f)
    check("plan: reads last night's times from the page's showcase.json", scenes.load_last(last) == {"quarry": 999, "gone_scene": 5})
    with open(last, "w") as f:
        f.write("<html>404</html>")
    check("plan: a missing or broken showcase.json falls back to the estimates", scenes.load_last(last) == {}
          and scenes.load_last(os.path.join(tmp, "nope.json")) == {})

    # Stills picked from globs.
    files = [f"frame_{i:03d}" for i in range(9)]
    check("stills: first, middle and last of a series", scenes.pick(files, "frame_*@first") == ["frame_000"]
          and scenes.pick(files, "frame_*@middle") == ["frame_004"] and scenes.pick(files, "frame_*@last") == ["frame_008"])
    spread = scenes.pick(files, "frame_*@spread")
    check("stills: a spread is 4 pictures from first to last", len(spread) == 4 and spread[0] == "frame_000"
          and spread[-1] == "frame_008", spread)
    check("stills: a named picture that isn't there is missing", scenes.pick(files, "03_done") == [])
    check("stills: labels from file names", scenes.label_from("30_starter_cottage_2_back") == "Starter cottage 2 back")


# --- Judging a scene --------------------------------------------------------------------------------------------

def judging_tests(tmp):
    r, out = judge(tmp, "sifter")
    check("judge: a scene whose job did its work and whose pictures look fine passes", r["pass"], r["reasons"])
    check("judge: it gets its 3 labelled stills (Start, At work, Done)", [s["label"] for s in r["stills"]] == ["Start", "At work", "Done"]
          and all(os.path.exists(os.path.join(out, s["file"])) for s in r["stills"]), r["stills"])
    gif = Image.open(os.path.join(out, "anim.gif"))
    gif.seek(0)
    first = gif.convert("RGB").getpixel((100, 100))
    gif.seek(gif.n_frames - 1)
    last = gif.convert("RGB").getpixel((100, 100))
    check("judge: the GIF runs from the first frame to the last", gif.n_frames == 6 and first[1] < 30 and last[2] > 225,
          (gif.n_frames, first, last))

    r, out = judge(tmp, "quarry", frames=400)
    gif = Image.open(os.path.join(out, "anim.gif"))
    gif.seek(0)
    first = gif.convert("RGB").getpixel((100, 100))
    gif.seek(gif.n_frames - 1)
    last = gif.convert("RGB").getpixel((100, 100))
    check("judge: a long scene's GIF is cut down but still runs from the first frame to the last",
          gif.n_frames <= process.GIF_FRAMES + 1 and first[1] < 30 and last[2] > 225 and last[0] < 30, (gif.n_frames, first, last))

    r, _ = judge(tmp, "sifter", checks=())
    check("judge: a scene that never checked its work fails", not r["pass"] and any("never checked" in x for x in r["reasons"]),
          r["reasons"])
    r, _ = judge(tmp, "sifter", checks=(("the sifter sifted gravel", False),))
    check("judge: a scene whose job didn't do its work fails",
          not r["pass"] and "not done: the sifter sifted gravel" in r["reasons"], r["reasons"])
    r, _ = judge(tmp, "sifter", checks=(("the screen opened", True), ("the sifter sifted gravel", False)))
    check("judge: one failed check among passed ones fails the scene", not r["pass"], r["reasons"])
    r, _ = judge(tmp, "sifter", report=False)
    check("judge: a scene that left no showcase.json (crashed before it started) fails", not r["pass"], r["reasons"])
    r, _ = judge(tmp, "village", problems=["a villager (nitwit) is stuck in a wall at 1 2 3, in Snow Block"])
    check("judge: a villager stuck in a wall fails the scene",
          not r["pass"] and any("stuck in a wall" in x for x in r["reasons"]), r["reasons"])
    r, _ = judge(tmp, "hall", missing_keys=["screen.aliveworkplace.hall.title"])
    check("judge: our text shown as a raw key fails the scene",
          not r["pass"] and any("screen.aliveworkplace.hall.title" in x for x in r["reasons"]), r["reasons"])
    r, _ = judge(tmp, "battle", other_keys=["0%", "0/264", "100%"])
    check("judge: another mod's untranslated strings are a note, not a failure (no false alarm on Cobblemon's HUD)",
          r["pass"] and r["warnings"], r["reasons"])

    r, _ = judge(tmp, "sifter", stills=["01_start", "work_00130"])
    check("judge: a still the catalog wants that the scene didn't take fails it",
          not r["pass"] and any("picture missing: 03_done" in x for x in r["reasons"]), r["reasons"])
    r, _ = judge(tmp, "sifter", frames=0)
    check("judge: a scene with no GIF frames fails", not r["pass"] and "no GIF frames" in r["reasons"], r["reasons"])
    r, _ = judge(tmp, "sifter", still_edit=lambda stem, im: Image.new("RGB", im.size, (0, 0, 0)) if stem == "03_done" else im)
    check("judge: a blank (black) still fails", not r["pass"] and any("blank picture" in x for x in r["reasons"]), r["reasons"])

    r, out = judge(tmp, "sifter", still_edit=lambda stem, im: missing_texture(im, 400, 250, 12) if stem == "03_done" else im)
    check("judge: a missing texture (magenta and black) in a still fails",
          not r["pass"] and any("missing-texture magenta in '03_done'" in x for x in r["reasons"]), r["reasons"])
    check("judge: ... and the page gets a close-up of it", r["broken"] and os.path.exists(os.path.join(out, r["broken"][0]["file"])),
          r["broken"])
    r, _ = judge(tmp, "sifter", frame_edit=lambda i, im: missing_texture(im, 200, 120, 6) if i == 3 else im)
    check("judge: a missing texture seen only in the GIF fails", not r["pass"] and any("GIF frame" in x for x in r["reasons"]),
          r["reasons"])
    r, _ = judge(tmp, "sifter", still_edit=lambda stem, im: missing_texture(im, 400, 250, 2) if stem == "03_done" else im)
    check("judge: a speck of magenta smaller than a texel's patch is not a missing texture", r["pass"], r["reasons"])

    def purple_text(stem, im):
        # A toast or tooltip: dark box, magenta text in 2-pixel strokes (what the first GitHub run took for a missing
        # texture on the requests board).
        if stem != "03_done":
            return im
        d = ImageDraw.Draw(im)
        d.rectangle((600, 10, 950, 80), fill=(16, 0, 16))
        for x in range(610, 940, 7):
            d.rectangle((x, 20, x + 1, 36), fill=(248, 0, 248))
            d.rectangle((x, 50, x + 4, 51), fill=(248, 0, 248))
        return im
    r, _ = judge(tmp, "sifter", still_edit=purple_text)
    check("judge: purple text on a dark toast is not a missing texture", r["pass"], r["reasons"])

    def portal(stem, im):
        # A lit Nether portal: purple, not the missing texture's magenta.
        ImageDraw.Draw(im).rectangle((400, 200, 520, 400), fill=(90, 13, 176))
        return im
    r, _ = judge(tmp, "netherworker", still_edit=portal)
    check("judge: a Nether portal's purple is not a missing texture", r["pass"], r["reasons"])

    def magenta_block(stem, im):
        # A real magenta block (magenta wool, a banner): solid magenta with no black checkerboard beside it.
        ImageDraw.Draw(im).rectangle((400, 250, 460, 310), fill=(248, 0, 248))
        return im
    r, _ = judge(tmp, "sifter", still_edit=magenta_block)
    check("judge: a solid magenta block without the black squares is not a missing texture", r["pass"], r["reasons"])

    crash = "---- Minecraft Crash Report ----\n// Oops.\n"
    r, _ = judge(tmp, "sifter", log=crash)
    check("judge: a crash fails the scene", not r["pass"] and any("crashed" in x for x in r["reasons"]), r["reasons"])
    r, _ = judge(tmp, "sifter", exit_code="timeout")
    check("judge: running over the time limit fails the scene", not r["pass"] and "ran over its time limit" in r["reasons"],
          r["reasons"])
    log = ("[12:00:01] [Worker-Main-1/WARN] (Minecraft) Missing textures in model aliveworkplace:village_hall#facing=north:\n"
           "[12:00:01] [Worker-Main-1/WARN] (Minecraft) Missing textures in model othermod:thing#inventory:\n")
    r, _ = judge(tmp, "sifter", log=log)
    check("judge: our missing textures in the log are collected for the run, other mods' are not",
          len(r["assets"]) == 1 and "aliveworkplace:village_hall#facing=north" in r["assets"][0], r["assets"])


# --- The page and the issue -------------------------------------------------------------------------------------

def page_tests(tmp):
    results = os.path.join(tmp, "results")
    os.makedirs(results)
    for name, kw in [("sifter", {}), ("hall", {}), ("quarry", {"checks": (("the miner dug out the quarry", False),)})]:
        raw = raw_scene(os.path.join(tmp, "p"), name, **kw)
        process.process(name, raw, os.path.join(results, name), seconds=60)
    site = os.path.join(tmp, "site")
    s = page.build(results, site, "https://github.com/x/y/actions/runs/1")
    html = open(os.path.join(site, "index.html"), encoding="utf-8").read()
    version = re.search(r'^mod\.version\s*=\s*"([^"]+)"', open(os.path.join(ROOT, "stonecutter.properties.toml")).read(), re.M).group(1)
    head = html.split("<main>", 1)[0]
    check("page: the date and the version are at the top", version in head and str(page.central_now().year) in head, head[-400:])
    check("page: made for a phone (viewport)", 'name="viewport" content="width=device-width' in html)
    check("page: one card per catalog scene", html.count('<article class="card') == len(scenes.SCENES), html.count("<article"))
    check("page: a catalog scene with no result is a FAIL, not left out",
          s["failed"] == len(scenes.SCENES) - 2 and "no result" in html, (s["passed"], s["failed"]))
    check("page: scenes grouped by job, in the README's order",
          html.index('<h2>Builder <small>') < html.index('<h2>Miner <small>') < html.index('<h2>Sifter <small>')
          < html.index('<h2>Village Hall <small>'))
    box = html.split('<div class="box bad">', 1)[1].split("</div>", 1)[0]
    check("page: failures listed first, with why", "#quarry" in box and "not done: the miner dug out the quarry" in box, box[:300])
    card = html.split('id="sifter"', 1)[1].split("</article>", 1)[0]
    check("page: a card has its GIF and its labelled stills", 'src="sifter/anim.gif"' in card and card.count("<figcaption>") == 3
          and "PASS" in card, card[:500])
    check("page: every picture a card shows exists",
          all(os.path.exists(os.path.join(site, p)) for p in re.findall(r'src="([^"]+)"', html)))
    data = json.load(open(os.path.join(site, "showcase.json")))
    check("page: showcase.json has each scene's verdict and time (tomorrow's plan reads it)",
          len(data["scenes"]) == len(scenes.SCENES) and scenes.load_last(os.path.join(site, "showcase.json")) == {
              "sifter": 60, "hall": 60, "quarry": 60})

    def report(summary):
        path = os.path.join(tmp, "summary.json")
        with open(path, "w") as f:
            json.dump(summary, f)
        issue, md = os.path.join(tmp, "issue.md"), os.path.join(tmp, "job.md")
        r = subprocess.run([sys.executable, os.path.join(HERE, "report.py"), path, "--issue", issue, "--summary", md],
                           text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return r.returncode, open(issue).read(), open(md).read()

    base = {"version": "1.2.3", "commit": "abc1234", "run": "https://run", "page": page.PAGE_URL, "assets": []}
    ok = {"name": "sifter", "group": "Sifter", "title": "Sifting gravel", "pass": True, "reasons": [], "seconds": 60}
    bad = {"name": "quarry", "group": "Miner", "title": "A miner digs out a quarry", "pass": False,
           "reasons": ["not done: the miner dug out the quarry"], "seconds": 300}
    code, issue, md = report({**base, "passed": 1, "failed": 0, "scenes": [ok]})
    check("issue: nothing to file when every scene passed", code == 0 and issue == "" and "1 passed, 0 failed" in md, (code, issue))
    code, issue, md = report({**base, "passed": 1, "failed": 1, "scenes": [ok, bad]})
    check("issue: a failed scene files the issue, with the command that reproduces it and why",
          code == 1 and "SCENE=quarry tools/screenshots/run.sh" in issue and "not done: the miner dug out the quarry" in issue
          and f"{page.PAGE_URL}#quarry" in issue and "sifter" not in issue, issue)
    code, issue, md = report({**base, "passed": 1, "failed": 0, "scenes": [ok],
                              "assets": ["Missing textures in model aliveworkplace:village_hall#facing=north:"]})
    check("issue: a missing texture in the log files the issue even when every scene passed",
          code == 1 and "village_hall" in issue, (code, issue))


def main():
    tmp = tempfile.mkdtemp(prefix="showcase-test-")
    try:
        catalog_tests(tmp)
        judging_tests(tmp)
        page_tests(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"\n{len(failures)} failed" if failures else "\nall passed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
