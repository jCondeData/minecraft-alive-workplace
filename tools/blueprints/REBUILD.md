# Rebuilding a family from scratch (ROADMAP Milestones 49-52)

The owner, 2026-11-16: "rebuild all the current builds with this skillset and logic". His fear: "the builders will rely
too much on the previous builds and be afraid to start from scratch to really enhance a build". So the person who
designs the new building never sees the old one. Every family (all its levels, its village and modded variants) goes
through the same four steps, each by a **fresh subagent** (no model override; never Sonnet for the brief, design or
critic):

1. **Blind brief.** The brief writer reads the old family and writes only what the building must *do* to
   `tools/blueprints/briefs/<family>.md` (template below). Nothing about how it looks.
2. **Blind design.** The designer gets only the brief's path. It may not open the old recipe or Python drawing, the
   `.nbt`s, renders, `blueprint_history/`, showcase stills or the family's wiki pictures. It writes three rough concepts
   (massing, roof, plan) in its design notes, builds the strongest (not the safest) with the parts library, and saves
   it as a candidate.
3. **Better or back.** The critic sees old and new side by side (the sheet below: renders from four sides, cutaways,
   eye-level furnished shots) and scores them (scorecard below). The new one ships only if it is clearly better on at
   least three of the four scores. Otherwise it goes back to the designer with the critic's notes, twice at most; after
   that it goes to the owner as it stands, saying so.
4. **Ship.** The lane replaces the family's drawings (ids never change; `history.py` keeps the old ones), moves any
   GameTest pinned to an old coordinate to the new one (keeping what it checks, dropping none), removes the family from
   `owner_rules_baseline.json`, and does the family's Done when (ROADMAP Milestone 49 intro).

Rules that hold throughout:

- **Families, not drawings.** Levels are drawn together, each growing out of the last (Level II adds a wing, a floor or
  a yard to Level I's house, not a different house).
- **Two versions only for the flagships** (starter_cottage, village_hall, inn, storehouse, builders_workshop): A and B by
  two different designers from the same brief. Every other family gets one.
- **Saves stay safe.** Ids never change; standing buildings and sites mid-build keep their old drawing until a refresh
  (36.21).
- **The owner's rules are checks** (`check.py`, below), plus everything `holes.py` and `STYLE.md` already check.

## The kit

| Piece | Where | Use |
|---|---|---|
| Parts library | `tools/blueprints/js/parts/` (`index.mjs` lists it) | frames, walls, roofs and gables, hearths, kitchens, stairs and lofts, porches, railings, furniture. Every part takes the family's palette; none picks a block itself. A part a family needs that another could use is added here. |
| Example family | `tools/blueprints/js/house_t1_b/` (the Daub House, 36.23 B) | `plan.mjs` (story, palette, masses, rooms, openings, every furniture footprint, the walking line) then `recipe.mjs` (passes: block-out, openings, depth and detail, interior, ground). Copy its shape, not its look. |
| Runner | minecraft-builder skill (`MC_BUILDER=<skill folder>`) | `node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955`; the recipe writes `furniture.json` beside it. `CUT=<y>` cuts everything above y (cutaways). The skill needs `npm install` once (in a scratch copy of the skill folder if it is read-only). |
| Adapter | `tools/blueprints/rebuild.py` | loads the recipe's `.nbt` and `furniture.json` into a kit `Build`, records the furniture spots, and saves it the usual way (check.py, finish(), `blueprint_furniture/`). Candidates go to a folder outside `structure/` until the critic passes them. |
| Owner's rules | `tools/blueprints/check.py` (`owner_problems`) | (1) a fence, wall or pane run that turns a corner without its post; (2) a lamp under 2 blocks tall (a lantern) standing on a floor: it goes on a table, side table or slab, on a post, or hangs; (3) plates and crockery anywhere but a kitchen (a stove, furnace, smoker or counter within 4 across and 3 up or down) or a table with a chair at it. Tests: `python3 tools/blueprints/test_owner_rules.py`. `python3 tools/blueprints/check.py [--owner]` checks every shipped blueprint. |
| Baseline | `tools/blueprints/owner_rules_baseline.json` | the blueprints drawn before the owner's rules, each with its count; their owner-rule problems print as one "(baselined)" line. A rebuild removes its family's names. Never add one. |
| Renders | `tools/blueprints/render/shoot.mjs` | `node shoot.mjs <out> a.nbt b.nbt --views front,back,left,right,top`. Chromium: set `executablePath` in a scratch copy if Playwright's version is missing; don't change the repo for it. |
| Furnished shots | `SCENE=rebuild_<family>` (`RebuildScene`) | the family's first drawing (or `REBUILD_BLUEPRINT=<id>`) built as a server with the furniture mods would build it, filmed from four sides and at eye level once per room. Rooms come from the furniture spots; the camera stands on a free floor cell, never in a wall, never on or behind a seat, with a clear line to the room's furniture. Add the family to `RebuildScene.FILMED` and its scene to `tools/showcase/scenes.py`. |
| Old-vs-new sheet | `tools/blueprints/sheet.py` | `python3 tools/blueprints/sheet.py --title "<Family>" --renders <dir> --old-name <old> --new-name <new> --views front,back,left,right,top -o build/rebuild/<family>_sheet.png`; or `--old a.png ... --new b.png ... --labels ...` for shots and cutaways. |

## The brief template (`tools/blueprints/briefs/<family>.md`)

```markdown
# <Family name>: the brief

Family id(s): <id>, <id>_2, ... (never renamed)   Variants: village (<biomes>), modded, looks (<list>)

## What it is for
<One paragraph: who uses it and what they do there, in the game's terms.>

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|

## Must have (each level)
- Job block(s): <block, how many, where the code looks for it (e.g. "within the footprint", "reachable from the door")>
- Beds and residents: <count per level, what the code counts>
- Storage: <chests/barrels the job reads, how many>
- Furniture kinds a job or test needs: <kind: count>
- Anything else the code or a test reads (a bell, a lectern, water, a fence pen, a sign...)

## Fits
- Plot: <max width x depth x height> per level; y = 0 sits on the clicked block; the front is the z = 0 side
- Front and way in: <which side the door faces; a path or step the village plans expect>
- Neighbours: <party walls, rows, what the village planner places beside it>

## Rules tests check
- <Every GameTest assertion that depends on the drawing, with the test class and what it checks, e.g.
  "WellGameTests.wellHasWater: a water source the bucket can reach from outside">
- The owner's rules (check.py) and STYLE.md's checks.

## Free
<What the designer may change freely: everything not listed above. Say it in one line.>
```

## The scorecard (the critic fills one per family)

Four scores, each 1-5 for OLD and for NEW, with one sentence of evidence that points at a picture on the sheet:

| Score | What it asks | 1 | 3 | 5 |
|---|---|---|---|---|
| **Silhouette** | Does it read as one strong, believable shape from 20 blocks away, from every side? | a box or a flat slab | a clear shape, one weak side | a shape you'd recognise in outline, every side worked |
| **Depth** | Do the walls and roof have layers: frame proud of infill, overhangs, sills, trim, recesses? | flat walls, no overhang | some depth on the front | depth on every face, light and shadow at every edge |
| **Interior** | Rooms that make sense, furnished, lit, the way a family or worker uses them (eye-level shots)? | empty or a box of blocks | rooms with furniture, some odd | every room planned, furnished and lit, a walking line that works |
| **Detail** | Small things that tell a story without clutter: palette discipline, plants in soil, joined railings, lamps on tables? | none, or noise | some, uneven | considered everywhere, nothing that breaks the owner's rules |

Verdict: **SHIP** when NEW beats OLD by at least one point on three or more scores and loses on none by two;
otherwise **BACK** with at most five numbered notes, most important first, each naming the view it comes from.

## The three prompts (word for word; fill the angle brackets, change nothing else)

### 1. The brief writer

> You are writing the brief for rebuilding the **<family>** family of the Alive Workplace Minecraft mod from scratch
> (ROADMAP Milestone 49, `tools/blueprints/REBUILD.md`). Read CLAUDE.md, then REBUILD.md's "The brief template".
> Read everything about the old family: its drawing (grep `tools/blueprints/*.py` and `tools/blueprints/js/` for
> `<family>`), its `.nbt`s under `src/main/resources/data/aliveworkplace/structure/` (load them with
> `tools/blueprints/holes.py`'s `load`), its `blueprint_furniture/<id>.json`, its wiki page in `docs/wiki/`, every
> GameTest that names it (`grep -rn "<family>" src/gametest src/compattest`) and the Java that reads it (job blocks,
> beds, storage, upgrades, village plans). Write `tools/blueprints/briefs/<family>.md` from the template: only what
> the building must DO (purpose, each level and what it adds, job blocks, beds and residents, storage, the furniture
> kinds a job or a test needs, the plot it must fit, the front, every rule a test checks, with the test's class and
> method). Write NOTHING about how it looks: no materials, colours, shapes, roof forms, sizes beyond the plot limit,
> styles, or what the old one has that no code or test needs. If you are unsure whether something is a requirement,
> find the code that reads it; if no code or test reads it, it is free. Don't change any other file. Report the brief's
> path and the three requirements a designer is likeliest to miss.

### 2. The blind designer

> You are designing the **<family>** family of the Alive Workplace Minecraft mod from scratch (ROADMAP Milestone 49).
> Your whole requirement is the brief at `<tools/blueprints/briefs/<family>.md>`; read it, CLAUDE.md, and
> `tools/blueprints/REBUILD.md` ("The kit"). Use the minecraft-architect skill and `tools/blueprints/STYLE.md`
> (including "From online research"), and the minecraft-builder skill for the JavaScript recipe. **Forbidden:** opening
> or searching for the old family's recipe or Python drawing, its `.nbt`s, its renders, `blueprint_history/`, the
> showcase stills or pages, or its wiki pictures; asking anyone what it looked like. You start from nothing. Write
> `tools/blueprints/js/<family>/` the way `tools/blueprints/js/house_t1_b/` is laid out (plan.mjs first: story, palette
> 60/30/10, masses, rooms, openings, every furniture footprint, the walking line; then recipe.mjs in passes), using and
> growing the shared library `tools/blueprints/js/parts/` (pass your palette in; never hard-code a block in a part).
> Begin plan.mjs's design notes with THREE rough concepts (massing, roof, plan, one line of story each), say which is
> strongest and why, and build that one: the strongest, not the safest. Draw every level together, each growing out of
> the last. Save each drawing as a candidate with `python3 tools/blueprints/rebuild.py <family> <id> --candidate`
> (never over the live `.nbt`); it must print no CHECK line, and `holes.py` must pass. Render it from five views and a
> cutaway per floor (`tools/blueprints/render/shoot.mjs`, `CUT=<y>`), look at every picture, and fix what reads badly
> before you stop. Report the candidate paths, the renders, and one paragraph on what makes it better than a generic
> <family>.

### 3. The critic

> You are the critic for the rebuilt **<family>** family of the Alive Workplace Minecraft mod (ROADMAP Milestone 49).
> You did not design either version and have no stake in either. Read `tools/blueprints/REBUILD.md` ("The scorecard")
> and the brief `<tools/blueprints/briefs/<family>.md>`. Look at every picture on the sheet `<sheet.png>` (OLD left,
> NEW right: four sides, top, cutaways) and the eye-level shots `<shots folder or "not filmed">`. For each of the four
> scores (silhouette, depth, interior, detail) give OLD and NEW a 1-5 with one sentence of evidence naming the view.
> Check NEW against the brief: list any requirement it misses (that alone means BACK). Then the verdict: SHIP when NEW
> beats OLD by at least one point on three or more scores and loses on none by two; otherwise BACK with at most five
> numbered notes, most important first, each naming the view it comes from and saying what to change, not how it
> should look in general. Be strict: a new building that is only different is not better. Don't change any file;
> write your scorecard as your report.

## The dry run (49.1: the Well)

The Well is the dry run's family: brief, blind design (candidate in `build/rebuild/well/`, or on `wip/rebuild-well`),
critic, sheet, filmed by `SCENE=rebuild_well`. The live `well` changes only if the critic says SHIP and its GameTests
pass. Proven so far: the parts library rebuilds the Daub House cell for cell (2,366 cells, 0 differing, no CHECK line).
