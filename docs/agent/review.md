# Review packages: how the owner approves work

After every finished roadmap item (and every bug fix) that changes something a player can see or feel, playtest it
with the bot and send the owner a review package. He judges looks from screenshots and behaviour from GIFs, often on
his phone, and replies with `approve`, `veto` or `change`. Work carries on while he reviews; only approved work is
released.

He wants to be hands-off: don't send him packages for work with nothing to see (tests, tooling, CI, internal fixes,
measurements). Land those with `sessions.py land <id> --as <you> --no-review` and list them in one line each in your
next message, with the numbers if there are any.

## Contents
- What goes in a package
- Making it
- Sending it
- His replies
- Rules

## What goes in a package

1. **One sheet image** (`tools/review/sheet.py`). This is the thing he looks at, so it must show the change on its
   own:
   - 4–12 in-game screenshots from the bot, each labelled with what it shows;
   - before/after pairs whenever something that already existed changed;
   - for textures and outfits, the pixel-art skill's preview sheet (slots at 1x–3x, the villager on every biome)
     next to the in-game shots;
   - for builds, the front and back of each build, and each style.
2. **A GIF whenever something behaves**: a job being done, an animation, a build going up, a screen being used, a
   fixed behaviour. This is how he checks that it works, so it must show the whole thing happening, start to end.
   - The screenshot harness saves `frame_*.png` every 30 ticks; `tools/screenshots/make_gif.py out.gif --crop … --width
     640` stitches them (only the default builders scene does this by itself).
   - Hand it in as an MP4, which stays small and plays on his phone (under 30 seconds):
     `ffmpeg -framerate 14 -pattern_type glob -i 'frame_*.png' -vf scale=640:-2 -pix_fmt yuv420p clip.mp4`.
   - Stills alone are enough only for things that don't move (a texture, a screen's layout).
3. **The message**, short and in this shape:

```
Review 23.3: "What do you need?" at a glance
What changed: hovering a build site now shows how much is built, the 3 items it needs most, and the chest the
builder takes from. (In game: look at a builder's site.)
Please judge: 1) Is the overlay too big at GUI scale 2? 2) Should the chest be named or just highlighted?
Tester: PASS WITH RISKS: 6 new tests, 5/6 mutants killed; not tested: Sodium on the client.
Scene: SCENE=builder_hud. Set up by command: noon, a half-built house, 2 supply chests.
Reply: approve 23.3 · veto 23.3: why · change 23.3: what
```

Keep "What changed" to a player's view: no class names. "Please judge" has one to three real questions, the choices
that are his to make. Never just ask "looks good?".

Performance work is the exception to "nothing to see": players feel it. Its package is a before/after table of the
numbers (tick times, memory), with a chart if it helps, in the same message shape.

## Making it

1. Pick or write the scene. Every player-visible feature has a `SCENE` in `tools/screenshots/run.sh` (ROADMAP 22.3).
   A new feature adds its scene in the same commit, so its package can be made again later.
2. Run it: `SCENE=<name> tools/screenshots/run.sh`, alone on the machine (7 GB). Long scenes go in the background;
   poll them.
3. **Look at every screenshot yourself before sending.** Missing textures (magenta and black), raw translation keys,
   clipped text, floating or buried blocks, and villagers in walls are bugs. Fix them first, or name them in the
   message.
4. Build the sheet:
   `python3 tools/review/sheet.py --title "23.3 …" --out build/review/23.3/sheet.png shot1.png before.png:Before after.png:After`
5. Keep the package in `build/review/<item>/` (not committed; the scene can make it again).

## Sending it

- **Lanes** hand the package in with `sessions.py review <id> sheet.jpg clip.mp4 --as <you> --message "<the
  message>"` right after `land`. The next digest (8 AM and 6 PM Central) sends it to him (`docs/agent/sessions.md`).
  Each file under 8 MB: a JPEG sheet (`--out …/sheet.jpg`) and a 640-wide MP4 of the motion.
- **The owner's chat** sends its packages to him directly (SendUserFile), with the message in the same turn.
- One package per item, right after `sessions.py land` (which ticks it `(review: pending)`), never batched at the end
  of a run: a run can be cut off before it ends.
- If an item is pending review and no package for it reached him (a run was cut off; the `Mark <id> built` commit
  names the session), make the package again from its scene and send it.

## His replies

Record each one straight away with `sessions.py reply "<his reply>" --as <you>` (ROADMAP.md, "His replies"):
- `approve 23.3`: accepted.
- `veto 23.3: <why>`: reopened with his reason; send a new package when it's redone.
- `change 23.3: <what>`: a sub-item with his words, done next. Its package covers only the change.
- Anything else he writes about a package is design input. Record it under Design decisions if it is a lasting rule.

## Rules

- **In-game shots are required** for anything a player sees in game, taken by the bot client. Preview sheets
  (pixel-art skill) and build renders are welcome as extra material, labelled as such, but never replace the
  in-game shots.
- **Show the mod doing the work.** A scene may set the stage with commands (time, weather, a test village, filled
  chests), but the result on screen must come from the mod's own behaviour: a builder really building, a screen
  really opened. List what the scene set up by command in the message, so a staged result can't pass as a real
  one.
- Never send a package for work that isn't green, or that the tester failed.
- If the bot can't run (no display, out of memory), say so in the message and send what you have. Never call it
  playtested.
