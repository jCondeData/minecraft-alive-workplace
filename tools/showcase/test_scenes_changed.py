#!/usr/bin/env python3
"""Tests for `scenes.py changed` (ROADMAP 22.8): which scenes a push films. Runs on throwaway git repos.

    python3 tools/showcase/test_scenes_changed.py
"""
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenes  # noqa: E402

HARNESS = '''package x;

public class ScreenshotHarness {
	public void tick() {
		if ("hall".equals(System.getProperty("aliveworkplace.scene"))) {
			hallScene(mc, server);
		}
		if ("gallery".equals(System.getProperty("aliveworkplace.scene")) || "decor".equals(System.getProperty("aliveworkplace.scene"))
			|| "styles".equals(System.getProperty("aliveworkplace.scene"))) {
			galleryScene(mc, server);
		}
	}

	private void hallScene(Minecraft mc, MinecraftServer server) {
		tick++;
		shot(mc, "01_hall");
	}

	private void galleryScene(Minecraft mc, MinecraftServer server) {
		tick++;
	}

	static void pointAt(Minecraft mc, int slot) {
		move(slot);
	}
}
'''

JOBS = '''package x;

final class JobScenes {
	static {
		SCENES.put("beekeeper", job("the beekeeper harvested", 2400, (level, player) -> {
			level.setBlock(HIVE);
		}));
		SCENES.put("florist", job("the florist grew flowers", 2400, (level, player) -> {
			level.setBlock(STAND);
		}));
	}

	static Job job(String what, int ticks, Setup setup) {
		return new Job(what, ticks, setup);
	}
}
'''

CATALOG = '''SCENES = [
    # Village-wide
    S("hall", "Village Hall", "The hall", "opened", 60,
      [("01_hall", "Hall")]),
    job("beekeeper", "Beekeeper", "Bees", "harvested"),
]
'''


def git(root, *args):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout.strip()


def repo():
    root = tempfile.mkdtemp()
    git(root, "init", "-q")
    git(root, "config", "user.email", "t@t")
    git(root, "config", "user.name", "t")
    os.makedirs(os.path.join(root, scenes.DEVCLIENT))
    os.makedirs(os.path.join(root, "tools/showcase"))
    os.makedirs(os.path.join(root, "tools/screenshots"))
    write(root, scenes.DEVCLIENT + "ScreenshotHarness.java", HARNESS)
    write(root, scenes.DEVCLIENT + "JobScenes.java", JOBS)
    write(root, "tools/showcase/scenes.py", CATALOG)
    write(root, "tools/screenshots/run.sh", "echo run\n")
    write(root, "README.md", "x\n")
    git(root, "add", "-A")
    git(root, "commit", "-qm", "base")
    return root


def write(root, path, text):
    with open(os.path.join(root, path), "w") as f:
        f.write(text)


def edit(root, path, old, new):
    full = os.path.join(root, path)
    text = open(full).read()
    assert old in text, (path, old)
    write(root, path, text.replace(old, new, 1))
    git(root, "commit", "-qam", "edit")


def case(name, edits, want):
    root = repo()
    base = git(root, "rev-parse", "HEAD")
    for path, old, new in edits:
        edit(root, path, old, new)
    got = scenes.changed(base, "HEAD", root)
    ok = got == want
    print(("ok   " if ok else "FAIL ") + name + ("" if ok else f": got {got}, want {want}"))
    return ok


H, J, C = scenes.DEVCLIENT + "ScreenshotHarness.java", scenes.DEVCLIENT + "JobScenes.java", "tools/showcase/scenes.py"
# Only names in the real catalog count; these tests use real scene names.
results = [
    case("a line in hallScene films the hall", [(H, 'shot(mc, "01_hall");', 'shot(mc, "01_hall");\n\t\tshot(mc, "02_hall");')], ["hall"]),
    case("a scene method shared by several scenes films them all",
         [(H, "private void galleryScene(Minecraft mc, MinecraftServer server) {\n\t\ttick++;",
           "private void galleryScene(Minecraft mc, MinecraftServer server) {\n\t\ttick += 2;")], ["decor", "gallery", "styles"]),
    case("a shared helper films every scene", [(H, "move(slot);", "move(slot + 1);")], None),
    case("a job scene's own lines film it", [(J, "level.setBlock(STAND);", "level.setBlock(STAND);\n\t\t\tlevel.setBlock(POT);")], ["florist"]),
    case("JobScenes' helpers film every scene", [(J, "return new Job(what, ticks, setup);", "return new Job(what, ticks * 2, setup);")], None),
    case("two scenes in one push", [(H, 'shot(mc, "01_hall");', 'shot(mc, "01_hall_");'),
                                    (J, "level.setBlock(HIVE);", "level.setBlock(HIVE.above());")], ["beekeeper", "hall"]),
    case("a catalog entry's stills film that scene", [(C, '[("01_hall", "Hall")]', '[("01_hall", "The hall")]')], ["hall"]),
    case("the screenshot tools film every scene", [("tools/screenshots/run.sh", "echo run", "echo run fast")], None),
    case("a change outside the showcase films nothing", [("README.md", "x", "y")], []),
]


def check(name, ok):
    print(("ok   " if ok else "FAIL ") + name)
    return ok


m = scenes.matrix({}, {"hall"})
results.append(check("the matrix for one scene is one shard with it", m == {"include": [{"shard": "01", "scenes": "hall", "minutes": m["include"][0]["minutes"]}]}))
results.append(check("no scenes, no shards", scenes.matrix({}, set()) == {"include": []}))
results.append(check("every scene by default", sum(len(s["scenes"].split()) for s in scenes.matrix({})["include"]) == len(scenes.SCENES)))
import page  # noqa: E402
empty = tempfile.mkdtemp()
results.append(check("a push's page lists only its own scenes", set(page.load(empty, {"hall"})) == {"hall"}))
results.append(check("a full run's page lists every scene, missing ones failed",
                     len(page.load(empty)) == len(scenes.SCENES) and not any(r["pass"] for r in page.load(empty).values())))
print("\nall passed" if all(results) else "\nFAILED")
sys.exit(0 if all(results) else 1)
