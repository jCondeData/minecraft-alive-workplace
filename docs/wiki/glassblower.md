# The Glassblower

Milestone 46, item 46.6 (owner, 2026-11-09; each job crafts its own kind of thing). A new job, the Glassblower, at his
own Glass Kiln: he fires glass from sand (a coal or charcoal for every eight) and makes the panes, stained glass with the
Florist's dyes, tinted glass and bottles builders wait for. He replaces the stand-in 46.1 left in place: the Mason fired
the glass until now.
Roadmap items: 46.6

## 1. What a player sees

- A new block, the **Glass Kiln**: a brick kiln drawn like vanilla's furnaces, a glowing mouth with a blowpipe across it,
  a round flue on top and an ash door on the side. Recipe: eight bricks round a furnace, an iron ingot on top. In the
  mod's creative tab. A jobless villager takes a free one by himself and becomes a **Glassblower** (outfit: a tan
  leather apron with a pocket of jacks, rolled linen sleeves, a red kerchief and green-tinted goggles). A plain furnace
  is no workstation, so villagers never take a player's furnaces.
- A builder near a Glassblower who is short of glass gets it from him: he fetches the makings from the builder's chests
  (and the village's), makes them at his kiln and puts them in the builder's chest.
  - Glass from sand, a coal or charcoal for every eight fired (nine glass take two coal).
  - Glass panes from sand, the glass fired first (a builder with sand and coal gets panes).
  - Stained glass and stained panes with a dye: red stained glass from eight sand, a coal and a red dye.
  - Tinted glass from glass and four amethyst shards; glass bottles for the Storehouse's stock orders.
- Over his head while making something: "Glassblower · 3 made" (a count of things made: sixteen panes count sixteen)
  and what he's making; the kiln crackles and flames at its
  mouth while he works.
- With sand but no fuel: "Can't be made yet: short of 1 Coal to make 1 Glass Pane". Nine sand on one coal is a coal
  short too (the ninth glass needs a second one). A missing dye is named as the dye ("short of 1 Red Dye to make 1 Red
  Stained Glass"), and a missing shard as the shard ("short of 1 Amethyst Shard to make 1 Tinted Glass").
- His Glass Kiln broken mid-job loses nothing: put back, the panes still come. A nitwit or a child never takes a kiln.
- With no Glassblower nearby: "Hire a Glassblower to make Glass Panes" (it said "Hire a Mason" before). The Storehouse's
  board says "Made by: the Glassblower (name)" or "Hire a Glassblower to make it".
- The **Glassblower's Workshop** in the Blueprint Table (13 x 12 x 11, one tier): a brick glasshouse in a spruce frame
  under a dark oak roof, light blue and yellow stained panes in its front windows, the Glass Kiln facing the door between
  two chests, a bed, shelves of pots, and a brick kiln house with a smoking chimney and a heap of sand against the east
  wall. The Steward plans it when the village has eight villagers and no Glassblower, or a Glassblower lost his kiln.
- He trades too: sand and coal bought, glass, panes, bottles, stained and tinted glass and a spyglass sold.

## 2. How it works

- Registry: block `aliveworkplace:glass_kiln` (`ModBlocks.GLASS_KILN`, a `BuildersBenchBlock` from bricks, light 9), POI
  `aliveworkplace:glass_kiln` (in `#acquirable_job_site`), profession `aliveworkplace:glassblower`
  (`ModVillagers.GLASSBLOWER`, a worker: the long shift).
- `craft/GlassblowerWork` is a `CrafterWork` of the kiln's kind (`Crafting.Kind.KILN`: the furnace's building-block
  recipes and the crafting table's), whole chains a bagful at a time, with a coal or charcoal for every
  `GLASS_PER_FUEL` (8) fired; what's short is planned with the fuel counted as a raw material and every
  other job's thing raw, so the line names the coal and the dye. It takes only the `crafts/glassblower` list.
- `CraftJobs.Job.GLASSBLOWER` now names the job; its stand-in (the Mason) is gone, so the Mason no longer fires glass.
  He helps instead (`MasonWork.helps`): with a Glassblower at work by a builder whose glass lacks sand, the Mason crushes
  it from gravel or cobblestone into the builder's chests (`CrafterWork.makingsFor` reads a Glassblower's lack with the
  kiln's recipes).
- The blueprint is drawn in `tools/blueprints/workplaces.py` (`glassblowers_workshop`), with its modded-library copy;
  Steward rules `steward_rules/glassblowers_workshop.json` and `workplace_glassblowers_workshop.json`, renewal list
  `steward_renewal/glassblower.json`; on the Village Map it is a workshop.
- Art: `tools/textures/art/workstations_3.py` (`glass_kiln`) and `villagers_2.py` (`glassblower`).

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of his own) | | The making is a crafting job's: the builder's line comes with the job chains (Milestone 37's gate). Packs change what he makes with the `crafts/glassblower` tag. |

## 4. Saved data

Nothing new: his job in hand is the `crafter_job` attachment every crafting job uses, his bag the `builder_bag`. New
registry ids only (`glass_kiln`, `glassblower`, the `glassblowers_workshop` blueprint); old saves load unchanged.

## 5. Items, blocks, jobs, commands

- Block and item: Glass Kiln (`aliveworkplace:glass_kiln`), recipe `recipe/glass_kiln.json`, loot table, recipe-book
  unlock with bricks, a furnace or an iron ingot.
- Job: Glassblower (`aliveworkplace:glassblower`), outfit and zombie outfit.
- Blueprint: Glassblower's Workshop (`aliveworkplace:glassblowers_workshop`).
- Text: `block.aliveworkplace.glass_kiln`, `entity.minecraft.villager.glassblower`,
  `message.aliveworkplace.glassblower.title` ("Glassblower · %s made"), `blueprint.aliveworkplace.glassblowers_workshop`,
  `steward.aliveworkplace.why.glassblower` ("%2$s villagers and nobody to make glass for the builders"), plurals.

## 6. Decisions

- The kiln is its own block, not a furnace, so no villager ever takes a player's furnace (the owner's spec).
- He fires sand only; crushing stays the Mason's, who now crushes sand for him (so a builder with only cobblestone still
  gets glass, as before 46.6, with a Mason and a Glassblower both at work).
- Bottles are tested through the Storehouse's stock orders, as no blueprint asks for them.
- Older tests where the spec moved the maker now use the Glassblower: `BuilderGameTests` (the Mason crushes gravel, the
  Glassblower fires the glass), `JobListsGameTests`, `QaJobCraftListsGameTests` and the glass tests of
  `QaMixedFuelGameTests`.

## 7. Known limits

- A village without a Glassblower gets no glass from its Mason any more: the builder's line says to hire one.
- The showcase scene `glassblower` is filmed on GitHub, not in this session.

## 8. Proof

`GlassblowerGameTests`: his list is his own (the Mason makes none of it); glass panes for a builder from six sand and a
coal; nine glass take two coal; red stained glass from sand, coal and a red dye; tinted glass; bottles for a stock
order; no fuel named on the line word for word; no Glassblower ("Hire a Glassblower to make Glass Panes", a Mason by the
builder taking nothing); saved and loaded mid-job; a jobless villager by a furnace stays jobless while one by a Glass
Kiln becomes a Glassblower; the kiln's recipe, name and creative tab; the workshop in the Blueprint Table, its size,
its one kiln and no furnace, on the map, and in two Steward rules. Scene `glassblower`.

`QaJobCrafts1110cGameTests` (QA, from the spec): eight sand on exactly one coal (the spare coal left), nine sand on one
coal (not built, no sand lost, the line one coal short), a blue stained glass pane from sand, coal and a dye, the
missing red dye and the missing amethyst shard named word for word with nothing taken, his status title, the kiln
broken mid-job and put back (the panes still come), saved and loaded between the glass and the stained glass, a nitwit,
a child and a working Mason leaving a free kiln alone, and the kiln dropping itself when mined.
