# Each job's own list (who crafts what)

Milestone 46 (owner, 2026-11-09: "the carpenter shouldn't build brewing stands, or anything that doesn't have wood").
Every job that crafts for builders, stock orders and Wonders makes only its own kind of thing. 46.1 puts the lists in
place; 46.2 taught the Shepherd to weave at his loom ([The Shepherd weaves](weaver.md)), 46.3 the Florist to make pots
and dyes ([The Florist makes pots](florist-pots.md)), 46.4 the Cleric to make the magic blocks ([The Cleric makes the
magic blocks](cleric-crafts.md)), 46.5 the Beekeeper to make candles ([Candles and leather goods](candles-leather.md));
and 46.6 brought the Glassblower ([The Glassblower](glassblower.md)).
Roadmap items: 46.1, 46.2, 46.3, 46.4, 46.5, 46.6, B1142, B1170

## 1. What a player sees

- The Carpenter makes wooden things only: planks, slabs, stairs, fences, gates, doors, trapdoors, signs, ladders,
  chests, barrels, boats, crafting tables, torches, campfires, scaffolding, armor stands, levers, grindstones, smokers,
  beehives, note blocks, the Shop Counter. A builder short of a brewing stand, a flower pot, a banner, stained glass or a
  lantern is never served by the Carpenter, even with the makings in his chest.
- The Mason makes stone, bricks and clay things: every stonecutter cut, bricks fired from clay, terracotta and dyed or
  glazed terracotta, concrete powder and concrete (he mixes the powder and sets it), sandstone, mossy stone. Dyed
  terracotta and concrete need no furnace (he makes them at his bench).
- The Tinkerer makes metal and redstone: his `tinkering` list plus rails, tripwire hooks, redstone torches, daylight
  detectors, copper bulbs, minecarts, chains and lanterns.
- The Leatherworker makes leather goods: item frames and glow item frames, leads, leather from rabbit hide, leather
  armor. He no longer takes "anything colored": wool, carpets, beds and banners are the Shepherd's, stained glass the
  Glassblower's (made by the Mason until 46.6), terracotta and concrete the Mason's, and since 46.5 candles the
  Beekeeper's. A Leatherworker by a builder short of red wool, with white wool and red dye in the chest, leaves them
  alone: the builder's line reads "Hire a Shepherd to make Red Wool".
- The Librarian makes books, paper, bookshelves, lecterns, enchanting tables and cartography tables.
- Every crafting job but the Carpenter also uses the makings kept in the chests by his own workstation (stone bricks by
  the Mason's stonecutter, iron by the Tinker's Bench, leather and sticks by the cauldron, planks and books by the
  lectern) for a builder's request. Before, the builder waited "short of" with them sitting there (B1170). Builders
  still don't take from those chests themselves.
- The Shepherd (since 46.2) weaves at his loom: wool from string, dyed wool, carpets, beds, banners, banner patterns and
  paintings ([The Shepherd weaves](weaver.md)).
- The Florist (since 46.3) makes flower pots, decorated pots, dyes and moss carpet at her stand
  ([The Florist makes pots](florist-pots.md)).
- The Cleric (since 46.4) makes brewing stands, blaze powder, eyes of ender, ender chests, end rods and beacons at his
  brewing stand ([The Cleric makes the magic blocks](cleric-crafts.md)).
- The Beekeeper (since 46.5) makes candles, colored candles and honeycomb blocks at her Apiary
  ([Candles and leather goods](candles-leather.md)).
- The Glassblower (since 46.6) fires glass and makes panes, stained glass, tinted glass and bottles at his own Glass
  Kiln ([The Glassblower](glassblower.md)). Until then the Mason was his stand-in, so nothing that was made before
  became unmakable; no list has a stand-in today.
- When nobody in the village makes a part a builder waits for, his line under "Short of" names the job to hire:
  "Hire a Carpenter to make Oak Stairs", "Hire a Cleric to make Brewing Stands" (the Librarian's until 46.4). Things of
  different jobs are grouped: "Hire a Carpenter to make Barrels; Hire a Tinkerer to make Lanterns". Every countable
  thing on a job's list reads in the plural ("Hire a Tinkerer to make Minecarts"); things counted by the block keep
  their name ("Hire a Mason to make Red Concrete"). Concrete, which no recipe makes (the Mason sets the powder in
  water), counts like the rest of his list. Only what no job makes at all still reads "Nobody here makes".
- The Storehouse's board, under "You have", says for each thing a worker asks for who makes or gathers it:
  - "Made by: the Carpenter (Ada)" when one works within the village's reach (just "the Carpenter" if they have no
    name), else "Hire a Carpenter to make it";
  - stripped logs: the Lumberjack, who strips them for builders ("Made by: the Lumberjack (Bo)" or "Hire a Lumberjack
    to make it");
  - "Gathered by: the Lumberjack (Bo)" for raw things, else the job to hire: "Hire a Lumberjack to cut it" (logs),
    "Hire a Miner to dig it" (ore, raw ore, coal, gems, stone, sand, gravel, clay), "Hire a Shepherd to shear it"
    (wool), "Hire a Farmer to grow it", "Hire a Fisherman to catch it", "Hire a Beekeeper to collect it", "Hire a
    Florist to grow it" (flowers);
  - a raw thing shows only its gatherer (wool: the Shepherd; raw iron: the Miner), though some can also be crafted;
  - "No job makes or gathers this" only for what no job in the mod gathers or makes (a dragon egg).

## 2. How it works

- `craft/CraftJobs` reads the lists. For an item: the first job's tag that holds it (in the order Carpenter, Mason,
  Glassblower, Tinkerer, Shepherd, Florist, Cleric, Beekeeper, Leatherworker, Librarian); else, if a stonecutter
  recipe makes it, the Mason; else its crafting recipe's ingredients: logs or planks (or wooden slabs, stairs,
  fences) to the Carpenter, else stone or bricks to the Mason, else ingots to the Tinkerer, else wool or dye to the
  Shepherd, else the Carpenter if some ingredient is wood (a stick, a chest). The rule reads the first recipe's first
  options, and when nothing fits, every option of every crafting recipe for the item. An item from any mod on no list
  that fits no rule (bread, a diamond helmet, Create's Gearbox) stays with whoever made it before: the Carpenter at the
  crafting table, the Mason at his kiln (B1142: before the fix, 689 modded items had no job).
- `maker` is the job that makes it today: the list's job, or its stand-in while that job doesn't craft yet.
- Each crafting job's `wants` asks `CraftJobs.makes(level, job, item)`; the Mason also keeps crushing gravel and sand.
- B1170: `CrafterWork.choose` pools the chests by the crafter's own workstation (`CrafterWork.ownMakings`) with the
  builder's chests and the village stores for a builder's request, unless they're already among them, belong to
  another builder, or the builder is on another side; the Carpenter's aren't (B778, [Job chains](crafter.md) section
  6). `Village.takesPart` is unchanged, so their chunks, the job board, the hauler golems and builders see nothing
  new.
- The shortage line checks for a worker of the maker's job within the village's reach of the builder's table.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The lists always apply; the builder's shortage line shows with the job chains (Milestone 37's gate). Packs change the split with the tags below. |

## 4. Saved data

Nothing new is saved: the lists are tags, read every time. Old saves load unchanged; a crafter's job in hand from
before carries on.

## 5. Items, blocks, jobs, commands

- Data (packs can change it): `data/aliveworkplace/tags/item/crafts/<job>.json` for carpenter, mason, glassblower,
  tinkerer, shepherd, florist, cleric, beekeeper, leatherworker, librarian. The Tinkerer's holds
  `#aliveworkplace:tinkering`.
- `data/aliveworkplace/tags/item/gathers/<job>.json` for lumberjack, miner, shepherd, farmer, fisherman, beekeeper,
  florist. The Miner's takes `#c:ores` and `#c:raw_materials` when another mod fills them.
- Text: `message.aliveworkplace.job_chain.hire`, `screen.aliveworkplace.storehouse.made_by_worker(_unnamed)`,
  `hire_maker`, `gathered_by(_unnamed)`, `hire_gatherer.<job>`, `no_job`.
- No new blocks, items, jobs or commands here (the Glassblower and his Glass Kiln came in 46.6).

## 6. Decisions

- Stand-ins rather than idle lists (46.1 said nothing made today may become unmakable): each list's stand-in is the job
  that made those things before 46.1. The "Hire a ..." lines name the stand-in, since that is who would make it today:
  never a job that can't (the Glassblower wasn't in the game until 46.6).
- Clay and sand are the Miner's to gather (his quarry keeps what it digs).
- The ingredient rule goes in the spec's order (the first category any ingredient matches), so a cradle of wool,
  planks and sticks is the Carpenter's.

## 7. Known limits

- The ingredient rule reads a modded item's first crafting recipe and the first item each ingredient accepts; only when
  those fit no rule does it look at every option of every crafting recipe, so a modded item whose first options match
  gets that job even if a later option would fit better. A modded item that fits no rule goes to the Carpenter
  (Create's mechanical parts too), as before 46.1 (B1142).
- An item on two lists is the first list's: the lever is on the Carpenter's list and in the Tinkerer's `tinkering` tag,
  and only the Carpenter makes it. A pack that takes the lever off the Carpenter's list hands it to the Tinkerer.
- A thing a pack adds to a list reads by the game's own name on the hire line when the mod has no plural for it
  ("Hire a Cleric to make Fire Charge").
- Before 46.6 the "Hire a ..." lines for glass named the stand-in ("Hire a Mason to make Glass Panes"); they name the
  Glassblower now.
- The showcase scene is filmed on GitHub, not in this session.

## 8. Proof

`QaCrafterOwnChest1111GameTests` (B1170): with the makings only in the crafter's own chest, the Mason cuts a builder's
stone brick slabs, the Tinkerer makes his iron bars, the Librarian his bookshelf and the Leatherworker the item frame
the blueprint hangs, each built.
`JobListsGameTests`: the owner's five never the Carpenter's, the Mason's concrete and dyed terracotta (through a
builder's request, the concrete across a reload), an untagged wooden block (the Bounty Board) the Carpenter's by its
recipe, every item made before 46.1 still has a job, the "Hire a ..." line for each job, the Storehouse's lines, the
board naming the Lumberjack for a builder short of logs, and a flower pot left unmade and named with its job absent.
`QaModdedCraftJobs1110eCompatTests` (QA, with the pack's mods): every modded item with a crafting recipe has a
job, Create's Gearbox by name. `QaCreateMakers1110e4CompatTests` (QA, with Create): the named job really makes Create's
parts for a builder (the Carpenter Gearboxes from casings, shafts and planks through the cogwheels, also reloaded
mid-chain with none doubled; the Tinkerer Fluid Pipes from copper), the builder's "Hire a Carpenter to make Gearbox"
line, the Storehouse's lines for both and "No job makes or gathers this" for the Precision Mechanism.
`QaOldCarpenterJob1110eGameTests` (QA, an old save): a Carpenter loaded with a brewing stand's job and makings from
before 46.1 (no profession stamp) finishes it for the builder, nothing lost or doubled.
`QaJobCraftListsGameTests` (QA): every row of the Milestone 46 table on its own job's list and made today by that job
or its stand-in, no job making another's things (the Leatherworker no glass, terracotta or concrete), the ingredient
rule's order and the tag winning over it, every stonecutter cut the Mason's (or, for copper, the Tinkerer's), the "Hire a
..." line grouped by job in the plural, the Storehouse's line for every gatherer and only for grown workers in reach,
and through a builder's request the Mason making stained glass while a Carpenter leaves stained glass and lanterns alone.
`QaJobCraftListsBugTests` (QA, B1132-B1134): the hire line in the plural for countable things that had no plural
(minecarts, redstone torches, copper bulbs, panes, beacons, paintings, leads, glow item frames), the Storehouse naming
the Lumberjack for stripped logs, and "Hire a Mason to make Red Concrete".
`QaJobLists1110cGameTests` (QA): a pack's tag (the test mod's data puts a fire charge on the Cleric's list: only the
Cleric makes it, and the Storehouse and the hire line name him), the hire line for more of every job's list (plurals,
mass nouns as named, leather pants and boots not doubled, concrete and another color's powder on one Mason line), the
gatherer of every wood, the nether stems, ores, sand, gravel, clay and wool (by name, unnamed, never a child or one out
of reach), stripped logs of six woods naming the Lumberjack, all sixteen colors of every colored kind on its own job's
list and never the Leatherworker's, every listed thing made by exactly one job, a Leatherworker leaving red wool to the
Shepherd through a builder's request, and the Storehouse board for stripped birch logs and lime concrete.
