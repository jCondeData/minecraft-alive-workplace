# The showcase

A filmed scene for every job, screen and feature a player can see, so any of them can be shown, judged and checked
again at any time; every night GitHub films them all and publishes the owner's showcase page. Part of Milestone 22
(Safety net).

Roadmap items: 22.3

## What a player sees

Nothing in the game: the showcase is for the owner and the lanes. On the showcase page
(https://jcondedata.github.io/minecraft-alive-workplace/) each scene has a short GIF from start to end and two to four
stills with labels, grouped by job in the order of the README's job table, each marked PASS or FAIL with the reasons.

## How it works

**A scene** is one run of the real game client under a virtual display (`tools/screenshots/run.sh` with
`SCENE=<name>`). The screenshot harness (`ScreenshotHarness`, or `JobScenes` for a job scene, in `src/devclient/`)
stages the set (the job's block, the villager, the chests), lets the job happen in a real world, takes the stills and
records checks: each check is a sentence about what the player should see happen ("the lumberjack replanted") and
whether it did.

**The catalog** (`tools/showcase/scenes.py`) lists every scene: the job or group it belongs to, its title, a line on
what it shows, its expected time and the stills the page shows. It holds 304 scenes (2026-11-07). A scene that needs
an optional mod says so (Cobblemon, the furniture mods, Create, Waystones), and `run.sh` loads that mod for it.
`scenes.py check` fails when the harness and the catalog disagree, so a scene can't be added to one and forgotten in
the other.

**Judging** (`tools/showcase/process.py`). A scene passes only when it recorded at least one check and every check
passed, and no picture looks broken: a villager stuck in a wall, a raw translation key on screen, missing-texture
magenta, a blank or missing still, a crash, or running over its time.

**Every night** (`.github/workflows/showcase.yml`, 03:37 UTC) GitHub films every scene in parallel jobs of about half
an hour each, balanced by the times of the last run, and publishes the page. Each failed scene goes into the
`nightly-tests` issue with the command that reproduces it, and each one is a bug (`sessions.py bug`). A push that
changes a scene films just that scene (ROADMAP 22.8).

**The rule** (22.3): every job and every screen in the README has a scene, and a new feature a player can see gets its
scene in the harness and the catalog in the same commit as the feature.

## Switches

none

## Saved data

none (each scene's results go to `run/screenshots/showcase.json` in the run, and the page keeps the last night's
times in its own `showcase.json`)

## Items, blocks, jobs, commands

- `tools/screenshots/run.sh` with `SCENE=<name>`: film one scene locally (up to about 15 minutes; only one
  Minecraft process at a time on the dev box).
- `python3 tools/showcase/scenes.py list | check | changed BASE HEAD`: the catalog's own commands.
- `python3 tools/showcase/shard.py` and `tools/showcase/page.py`: film a few scenes and build the page locally.
- Actions › showcase › Run workflow, with `scenes` set to some names (empty: all).

## Decisions

- One scene per job and screen, filmed in the real client rather than drawn by hand, so the owner judges looks from
  the same pictures a player would see (22.3; CLAUDE.md, "Reporting to the owner").
- Scenes run on GitHub, not in the lanes' sessions: filming took 10 to 22% of a lane's run (owner, 2026-10-04, the
  speed review), so lanes add the scene and GitHub films it after the push.
- A scene with no check can't pass: a GIF alone doesn't prove the job did its work (22.4).
- Two to four stills a scene, so the page stays quick to read (`test_showcase.py` checks it).

## Known limits

- Scenes stage their own sets, so they show a feature working, not every way it can break: the GameTests do that.
- A scene can fail for the harness's own reasons (the camera pushed by a villager, a hall read in the same tick it was
  placed: B130, B219); those are bugs too, and are fixed in the scene.
- Some scenes fail now and then on GitHub's machines only (a long Wonder time-lapse, a fossil revival): each such night
  is filed and read like any other failure.

## Proof

The catalog's own checks: `tools/showcase/test_showcase.py`, `test_scenes_changed.py` and `test_showcase_keys.py`,
and `scenes.py check` in the showcase workflow. Every nightly run from 2026-11-01 to 2026-11-06 passed 302 to 304 of
304 scenes.

Showcase scenes: `job_plate`, `personal_request`, `friendship`, `conscription`, `fire`
