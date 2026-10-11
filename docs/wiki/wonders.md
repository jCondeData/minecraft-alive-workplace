# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): the data files, requirements and caps, the
Wonders page, the Wonder Plan and the Foundation Stone with its stages are built, behind the 2.0 switch (see Known limits); the full plan is the design note, docs/design/M35.md.

Roadmap items: 35.1, 35.2, 35.3, 35.4, 35.5

## What a player sees

**The Wonders page** (35.3): a Wonders tab on the Village Hall (a spare tab, so the tabs kept for add-ons stay free).
Each Wonder on the server's list is a card: its icon, name and power, then "Needs:" with each requirement ticked (✔) or
crossed (✘) with the village's progress ("Finished Chapel: 2 of 3", "Standing in for the Master Architect: …"), the cap
("Wonders: 0 of 1 for a Town") and what a click does. A Wonder going up here shows its stage and how far along it is
("Going up: stage 2 of 3, The Upper Storey (33% along)"), a finished one its day.

**Draw up the plans**: with every line ticked and the caps clear, a click on the card pays 32 emeralds (the realm's
treasury if the village is in a realm and realms are on, else the hall's treasury, else the player's own emeralds; free
in creative when neither treasury has them) and gives a **Wonder Plan**, a rolled blueprint with a gold seal. Only the
hall's owner, their friends and operators may. Before that, a click says what's missing and takes nothing.

**The Wonder Plan** holds the whole Wonder's blueprint:

- held and placed, it shows the whole Wonder as see-through blocks, like any blueprint;
- right-click the ground to place it (its front faces you), sneak-right-click the ground to turn it, sneak-right-click
  the air to pick it up again or, not placed, open the style screen, where it can only be mirrored (each Wonder keeps
  its own blocks);
- it goes down only with its centre within the hall's reach (64 blocks), every corner within 96 blocks of the hall, and
  not over a finished building or another Wonder; otherwise it says why ("It would stand over the finished Chapel");
- a Book and Quill in the other hand writes the whole Wonder's material list.

**The Foundation Stone** (35.4): right-click a builder of the village (their bench within 64 blocks of the hall) with
the placed plan. A **Foundation Stone**, a carved stone with a brass plaque, goes down just outside the Wonder's front
corner (the next free spot round it if that one's taken); everyone on the server reads "Owner has begun the Test Folly
in Testholm!", the chronicle gets "Owner laid the Foundation Stone of the Test Folly", and the builder starts stage I.
The chests within 8 blocks of the stone are the Wonder's stockpile. Refused, saying why, when the plan isn't placed,
the builder has no bench or works for another village or another player, the caps no longer allow it, or there's no
room for the stone.

- **Right-click the stone**: "The Test Folly of Testholm", "Stage 1 of 3, The Ground Storey: 34% built", then the
  stage's material list, each line "40 Stone Bricks, 12 still to bring" or "… all there", what's still to bring first.
- **Each stage starts when every part of the one before is done**, and the builder it was handed to takes it on (the
  owner reads "Stage 2 of 3 of the Test Folly begins: The Upper Storey").
- **Sneak-break the stone** (the owner or an operator) or `/workplace cancel` on a part to abandon the Wonder: what's
  built stays, the open parts end, the plan comes back (to the owner, else by the stone) and the chronicle says so. A
  plain break says to sneak; anyone else is refused. Builders never clear it and pistons can't push it; a finished
  Wonder's stone breaks like any block (it drops nothing).

**Crews** (35.5): every builder of the village (their bench within 64 blocks of the hall) with no build of their own
joins the Wonder's crew, up to 12 (the `wonderCrew` switch, and no more than the Wonder's file allows). The stone's
status says who leads and how many are on it ("Led by Odo, with a crew of 8 (at most 12)"), and so does the Wonder's
card on the hall's Wonders page. Click **[Call every builder]** under the stone's status (the owner or an operator,
within 8 blocks of the stone; `/workplace wonders call`) and the busy builders come too, each as they finish what
they're on: "Every builder is called". The lead is the Master Architect when he's one of the village's builders,
else the builder it was handed to; each next stage goes to the highest-level builder of the crew.

- Each member takes a **column** of its own (the part split at least 8 × 8: a 64-wide part has 8-block columns, the
  9-wide folly 1-block ones), the free one with work nearest to them, and works it in the stage's order: clearing from the top, the rest
  from the bottom. Nobody works more than two layers above the part's lowest unfinished layer (a block the lead gave up
  on, out of reach, doesn't hold the rest back; levelling the ground round it isn't layered), and no structure block
  goes up before the block the drawing puts under it.
- Crews add hands, not speed: each builder keeps their own pace. Eight builders raise the folly in about 3,500 ticks
  against about 12,400 for one alone (at one block a second): a third of the time or less; the quarter the item asks
  for isn't reached yet.
- The crew fetches from the chests round the stone and the village's stores, and passes materials to each other.

**What next?** names the Wonder closest to ready once the village is a Town with room for one: "The Great Cathedral can
be begun", or the first line it lacks ("The Great Cathedral is nearly in reach: Finished Chapel: 2 of 3").

Still to come (the design note): scaffolding where builders can't reach; goods from every job, dedicated at the stone stage by stage; and a finished
Wonder announced to the server, celebrated with a festival, and its powers on while at least 90% of it stands.

| Wonder | Rank | Legend (stand-in) | Other requirements | Powers |
|---|---|---|---|---|
| Great Cathedral | Town | the Seer (a Master Cleric) | 3 finished Chapels | Undertakers revive anywhere in the village for free; blessed weddings; beauty |
| Lighthouse | Town | the Merchant Prince (3 caravan routes) | on the coast | caravans reach twice as far in half the time; ferry fares halved; raid warnings |
| Great Library | Town | the Old Sage (a Master Scholar) | 20 research levels | a second research tier |
| Great Wall | Town | the Golem Smith (4 iron golems and a Master Tinkerer) | a finished Gatehouse | raids gather outside it; golem sentries on the walk |
| Observatory | Town | the Seer (a Master Cleric) | none | the Sky Chart; the Coming days page |
| Hanging Gardens | Town | the Grand Chef (a Master Chef, 8 kinds of meal) | none | a quarter more harvest across the realm; faster births |
| Colossus | City | the Founder (City rank) | none | more mood across the realm; more and better travellers; beauty |
| Stadium (with Cobblemon) | Town | the Pokémon Professor (25 pastured Pokémon of 10 types) | Cobblemon | Stadium Days with visiting trainers; hosts the Festival Cup |

Every Wonder also needs the Master Architect (stand-in: a Master builder).

## How it works

**A Wonder is a data file** (35.2), `data/<namespace>/wonders/<id>.json`, read whenever data packs load, so a server
owner can add their own Wonder with a data pack and it works like ours. The file holds:

- `name` and `description`: lang keys. `icon`: any item.
- `requirements`: a list of conditions (below).
- `stages`: each a `name`, its blueprint `parts` (a structure id and its `offset` from the Wonder's corner), and its
  `goods`, each `{"item"` or `"tag", "count", "job"}`, with an optional `potion` to name a kind of potion.
- `powers`: `[{"type": ..., numbers}]`. The types are listed in the design note; each Wonder's item makes its own work.
- `crew` (default 12, at most 32) and `enabled` (false: the file is skipped). `fabric:load_conditions` are honoured,
  so a Wonder can need a mod (the Stadium will need Cobblemon).

A file with a mistake (an unknown condition or power type, a missing field, a count of 0, an unknown item, job or
potion, a stage without parts) is skipped and the log says which file and why; the other files still load.

**Requirements** use the same condition toolbox as Legends (`rank_at_least`, `research_levels`, `finished`,
`villagers`, `job_level`, `caravan_routes`, `iron_golems`, `meal_kinds`, `pastured_pokemon`, …), plus two types new
in 35.2:

- `coastal`: an ocean or beach within 48 blocks of the hall. It is the same coast check the drowned pirates use, looked
  at once a day per hall, so both agree on what a coast is.
- `mod_loaded` (`mod`): that mod is installed.

`legend` gained an optional `stand_in`, a list of conditions: while Legends are off, `wonderLegends` is off, or that
Legend's file isn't loaded, the stand-in list must hold instead (all of it). A `legend` without one reads as before.
`pastured_pokemon` counts through the Cobblemon census; without Cobblemon it is always false.

**Every Wonder needs the Master Architect** to draw up its plans. The mod adds this line to every Wonder itself (with a
Master builder as the stand-in), so no file can leave it out.

**The caps** (checked before the requirements):

- a village may hold `wonderCaps` Wonders by rank: Hamlet 0, Village 0, Town 1, City 2 (finished or going up);
- one of each kind per village;
- one Wonder going up per village at a time;
- `wonderServerLimit` villages on the server may each build the same kind (0: no limit);
- a Wonder in `disabledWonders` can't be started.

Each refusal says why ("Wonders: 1 of 1 for a Town.", "This village already has the Great Cathedral.").

**A Wonder's blueprints** (35.3): each stage's parts are structure files, `structure/wonder/<id>/stage_<n>_<part>.nbt`,
each at most 48 a side and only what that stage adds to the ones before (stage I also clears the site). The whole
Wonder, `<namespace>:wonder/<id>`, is those parts laid at their offsets, merged once and kept until data packs load
again; it is what the plan shows. Nothing under `wonder/` is ever on the Blueprint Table. A Wonder whose part is
missing can't have its plans drawn up (the log names the part). For pack makers drawing their own: the blueprint kit's
`wonder_stages()` (`tools/blueprints/kit.py`) takes the Wonder drawn one stage at a time and writes the parts and their
offsets; `tools/blueprints/wonders.py` draws the test folly with it.

Each Wonder in progress or finished is recorded per dimension, with its village's hall, the player who started it, its
stage and the days it was begun and finished.

**Stages as build sites** (35.4): each stage's parts become ordinary build sites, placed with the Wonder's turn and
mirror at their offsets, pointing back at the Wonder, with the Foundation Stone as their bench (so a builder fetches from
the chests round the stone and empties his bag there). Only stage I fills a foundation (later stages stand on the stage
below), only the last stage levels the ground round it, and no part hands a blueprint back. Once a second the Wonders
check their stage: when every part's site is done or gone, the next stage's sites are made for the Wonder's builder, or
the Wonder is finished. The check reads only saved data, so the order holds through a restart at any tick. While
`wonders` is off nothing moves on; it all waits, saved.

## Switches

All behind the 2.0 switch: until 2.0 is finished they are off and hidden, whatever the config file says.

| Switch | Default | Off means |
|---|---|---|
| `wonders` | on | no new Wonders; finished ones keep their blocks, everything stays saved |
| `wonderLegends` | on | each Wonder's Legends are replaced by their stand-ins |
| `wonderServerLimit` | 0 (0 to 64) | how many villages may each build the same kind; 0 is no limit |
| `wonderCaps` | 0, 0, 1, 2 | file only: Wonders per village by rank (each 0 to 8; a short list is filled from the defaults) |
| `disabledWonders` | empty | file only: Wonder ids that can't be started (`great_cathedral` or `pack:id`) |
| `wonderCrew` | 12 (1 to 32) | the most builders on one Wonder's crew, its lead included |

Later items add two more: builders' scaffolding, and how much of a Wonder must stand for its powers.

## Saved data

A new saved file per dimension, `aliveworkplace_wonders`, with a list of `sites`: each with its `id`, `wonder`, `hall`,
`owner` and `ownerName`, `stage` (default 1), `begun` and `finished` (absent until it is). A world saved before 35.2 has
no such file and loads unchanged. 35.4 adds, all optional: `placement`, `stone`, `parts` (the current stage's build
sites) and `builder` on a site; 35.5 adds `called` (default false: every builder called to the crew). A crew member's
job is the ordinary saved builder job (as a helper), so a crew comes back together after a restart; the columns are
worked out again; `wonder` and `no_foundation` on a build site (written only when set, so an ordinary
build saves exactly as before).

## Items, blocks, jobs, commands

- `/workplace wonders call`: the stone's [Call every builder], for the owner or an operator within 8 blocks of it.

- `/workplace wonders`: every Wonder on the server, one line each: its name, village, owner and stage ("stage 2 of 3,
  …" or "finished on day 12"). While `wonders` is off it says "Wonders are off."

- **Wonder Plan** (`aliveworkplace:wonder_plan`): drawn up on the Wonders page; not crafted, not in the creative tab.

- **Foundation Stone** (`aliveworkplace:foundation_stone`): laid by a Wonder; not crafted, not in the creative tab,
  drops nothing.

## Decisions

- One Legend can serve two Wonders (the Seer for the Cathedral and the Observatory): a requirement only checks that the
  Legend lives in the village.
- The Master Architect requirement is added to every Wonder by the mod, so a server owner's own Wonder needs it too.
- Builders' scaffolding comes with 2.0, for every build (an owner question in the design note).

## Known limits

- None of our eight Wonders' files ships yet (35.9 to 35.23); only the test folly. Everything is behind the 2.0 switch.
- A lone builder can't reach blocks far over anywhere he can stand: the folly's finial lantern, five blocks over the
  inside of its closed dome, is skipped until scaffolding (35.6).
- Drawing up the plans doesn't reserve the Wonder: two plans of one Wonder can be drawn; the caps hold when it's begun.
- A stage's `parts` are checked to exist when the plans are drawn up (the log names a missing one), not when loaded.
- Powers are read and checked for their type only; what each does comes with its Wonder.

## Proof

The design note, docs/design/M35.md. GameTests in `WonderDataGameTests`: the test folly's file loads (gametest
resources only), broken files are skipped with their reasons, a Wonder from a data pack on disk loads beside ours,
every requirement type holds and fails, `legend` falls back to its stand-in, the caps (a Town with one can't start a
second and a City can, the same kind twice, one going up, `wonderServerLimit 1`, disabled, off), `/workplace wonders`,
the switches and the gate, and the saved sites. No showcase scene (nothing to see yet).

GameTests in `WonderPlanGameTests` (35.3): the folly's three parts merge into the tower as drawn (compared block by
block with the drawing), no part sets air over an earlier stage's block, parts are at most 48 a side, the whole
blueprint is cached and never on the Blueprint Table; the page's card ticks every line and offers the plans, one unmet
line (no Master builder) shows crossed, the cap line, and "What next?" names the folly; Draw up the plans takes the
player's 32 emeralds or the treasury's, refuses without the money, over the cap and for a stranger, taking nothing;
the plan is refused outside the village and over a finished building and goes down elsewhere in all four turns.
Showcase scene `wonders_page` (group Wonders): the folly's card, then the placed plan's see-through tower.

GameTests in `WonderStageGameTests` and `WonderSiteGameTests` (35.4): the parts at their offsets in all four turns and
mirrored, stage II never before stage I; handing the plan over lays the stone at the front corner (turned and mirrored
too), begins the Wonder in the chronicle and queues stage I with the stone as bench; refused unplaced, with Wonders off,
to a builder without a bench, of another village, and for a second of the same kind; the stone's status with empty and
full chests; the stone refused to a stranger and to a plain break, the owner's sneak-break abandoning it with the plan
back; one real builder raising the folly from the chests round the stone in each of the four turns, plain and
mirrored (8 tests), no stage II block during stage I and stage I complete when stage II begins, ending as drawn (but
the finial lantern, 35.6); a save and reload mid-stage II of the Wonders, every build site and the builder, finished as
drawn; old saves unchanged. Showcase scene `wonder_site`: the plan placed, the stone going down, stage I under way and
the stone's status.

GameTests in `WonderCrewGameTests` (35.5): eight builders raise the folly with no two on one column on any tick, no
structure block up with the drawing's block under it missing, all eight on the crew at once, and done in at most a
third of the time one builder alone takes on the same folly (measured in the same test, first; 35.5 asks a quarter); a save and reload mid-stage II with every
member, the crew back together and the folly finished as drawn; `wonderCrew 3` holds the crew to 3, nobody joins with
Wonders off, and a member lost mid-job is replaced; a builder with its own build queued joins only once every builder is
called, a stranger can't call, the stone names the crew and offers the call, the highest-level builder leads.
Showcase scene `wonder_crew` (group Wonders): ten builders on the folly.
