# The Cleric makes the magic blocks

Milestone 46, item 46.4 (owner, 2026-11-09: "the carpenter shouldn't build brewing stands"; each job crafts its own kind
of thing). The Cleric makes the brewing stands, blaze powder, eyes of ender, ender chests, end rods and beacons builders
wait for, and is no longer the Librarian's to stand in for. He still brews the guards' potions and the tonics.
Roadmap items: 46.4, B1144

## 1. What a player sees

- A builder near a Cleric who is short of a brewing stand, blaze powder, an eye of ender, an ender chest, an end rod or
  a beacon gets it from him: he fetches the makings from the builder's chests (and his own), makes it at his brewing
  stand and puts it in the builder's chest.
  - A brewing stand from a blaze rod and three cobblestone.
  - An ender chest from a blaze rod, an ender pearl and eight obsidian, step by step: the rod ground to blaze powder,
    the eye of ender made, then the chest.
  - Four end rods from one blaze rod and one popped chorus fruit.
  - A beacon from five glass, three obsidian and a nether star.
- Over his head while making something: "Cleric · 3 made for builders" and what he's making. Between jobs he keeps
  brewing: the potions wait while he makes something, and the making waits while he brews, fills bottles or takes a
  potion to a guard.
- A missing blaze rod is named on the builder's line: "Can't be made yet: short of 1 Blaze Rod to make 1 Brewing
  Stand". Blaze rods come from the Nether; no job makes them. A beacon short of its star reads "Can't be made yet: short
  of 1 Nether Star to make 1 Beacon", and nothing is taken from the chest meanwhile; an ender chest one obsidian short
  reads "Can't be made yet: short of 1 Obsidian to make 1 Ender Chest". Blaze powder already at hand is used as it is:
  no blaze rod is needed for the eye of ender then.
- A builder with no Cleric nearby reads "Hire a Cleric to make Brewing Stands"; the Storehouse's board says "Made by:
  the Cleric (name)" or "Hire a Cleric to make it".
- A Librarian no longer makes these, even with the makings at hand; he keeps his books, bookshelves, lecterns,
  enchanting and cartography tables. Nor does a Carpenter, or a Nurse working at a brewing stand: with only them about
  the line still says "Hire a Cleric to make Brewing Stands". With two of his things wanted it names both: "Hire a
  Cleric to make Brewing Stands, End Rods".

## 2. How it works

- `craft/ClericCraftWork` is a `CrafterWork` (the crafting table's recipes) in the Cleric's work package next to
  `brew/AlchemistWork`; it takes what's on his list (`data/aliveworkplace/tags/item/crafts/cleric.json`,
  `CraftJobs.Job.CLERIC`, whose stand-in is gone). The two share his bag, so each waits while the other is busy
  (`AlchemistWork.isBusy`, `CrafterWork.isBusy`; the brewing also waits while a crafting job is saved on him). Vanilla's
  own Cleric routine waits while either is busy.
- What's short is planned with every other job's thing counted raw (as the Florist's and Shepherd's are), so the line
  names what a player hands over. A blaze rod is on no list and has no recipe, so it is named as it is.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The making is a crafting job's: the builder's line comes with the job chains (Milestone 37's gate). Packs change what he makes with the `crafts/cleric` tag. |

## 4. Saved data

Nothing new: a Cleric's job in hand is the `crafter_job` attachment every crafting job uses, and his bag the
`builder_bag`. A Librarian loaded with a brewing stand job from before 46.4 carries it on to the end (the job is his).

## 5. Items, blocks, jobs, commands

- Data: `data/aliveworkplace/tags/item/crafts/cleric.json` (brewing stand, blaze powder, eye of ender, ender chest, end
  rod, beacon), unchanged since 46.1. No new blocks, items, jobs or commands.
- Text: `message.aliveworkplace.cleric_craft.title` ("Cleric · %s made for builders").

## 6. Decisions

- No helping pair (`CrafterWork.helps`): the makings on the Cleric's list are his own (blaze powder) or gathered
  (blaze rods, ender pearls, obsidian, cobblestone, nether stars), so no other job's crafting feeds him.
- He makes at his brewing stand with the crafting table's recipes, as the Florist does at her stand: no crafting table
  is needed by him.

## 7. Known limits

- A beacon needs a nether star, which no job makes or gathers: a player hands it over.
- The showcase scene `cleric_crafts` is filmed on GitHub, not in this session.

## 8. Proof

`ClericCraftsGameTests`: the list is his own (no stand-in), a brewing stand from a blaze rod and cobblestone for a
builder, an ender chest step by step from a blaze rod, an ender pearl and obsidian, the missing blaze rod named on the
line (not cobblestone, not "Hire"), a Cleric saved and loaded mid-job finishing the stand, his healing potions brewed
alongside a builder's stand, and with no Cleric the Librarian leaving the stand alone and the line saying "Hire a Cleric
to make Brewing Stands". Scene `cleric_crafts`.

`QaClericCrafts1110cGameTests` (QA, from the spec): a beacon and four end rods made for builders, the missing nether
star and the missing blaze rod lines word for word, a Carpenter and a Nurse at a brewing stand never making a brewing
stand (the line names the Cleric), the hire line for two of his things, the ender chest's steps (powder, eye, chest)
with a save and reload between them, his brewing stand broken mid-job losing nothing, and his status ("Cleric · 1 made
for builders", "making 1 Brewing Stand", "taking 1 Brewing Stand to the builder"), and a blaze rod in his own chest making a builder's stand (B1144: the Cleric takes part
in the village, `Village.takesPart`, so his chests are a stash the builder's requests draw on).

`QaJobCrafts1110cGameTests` (QA): an ender chest one obsidian short (nothing taken, the line word for word), and an
ender chest made from blaze powder at hand with no blaze rod.
