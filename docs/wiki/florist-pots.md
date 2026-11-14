# The Florist makes pots

Milestone 46, item 46.3 (owner, 2026-11-09: "Florists should build pots"; each job crafts its own kind of thing). The
Florist makes the flower pots, decorated pots, dyes and moss carpet builders wait for, and is no longer the
Leatherworker's to stand in for. She still grows flowers and fills the empty pots round town.
Roadmap items: 46.3

## 1. What a player sees

- A builder near a Florist who is short of a flower pot, a decorated pot, a dye or moss carpet gets it from her: she
  fetches the makings from the builder's chests (and her own, where the flowers she grew are), makes it at her
  workstation (a Flower Stand or a composter) and puts it in the builder's chest.
  - A flower pot from three bricks; a decorated pot from four; three moss carpets from two moss blocks.
  - From clay: with only clay balls (and coal) about, a Mason with a furnace by his stonecutter fires the bricks into
    the builder's chest first, then she makes the pot. With no Florist about he fires none (the clay stays) and the
    line says "Hire a Florist to make Flower Pots".
  - The dye for a Shepherd's bed: with a Florist nearby the Shepherd leaves the dyes to her; she makes the red dye from
    the poppies in her chest (the blue dye from cornflowers), and he dyes the wool and makes the bed. With no Florist
    about he makes the dye himself, as before.
- Over her head while making something: "Florist · 3 made for builders" and what she's making. Between jobs she keeps
  her garden: the garden waits while she makes something, and the making waits while she picks, grows or pots.
- Missing bricks are named on the builder's line: "Can't be made yet: short of 3 Bricks to make 1 Flower Pot" (the
  bricks, not the clay that makes them).
- A builder with no Florist nearby reads "Hire a Florist to make Flower Pots"; the Storehouse's board says "Made by:
  the Florist (name)" or "Hire a Florist to make it".
- A Leatherworker or a Carpenter never makes pots, dyes or moss carpet, even with the makings at hand.
- Her workstation broken mid-job loses nothing: after a grace time she lets the job go and what was in her bag goes
  to the builder's chest.

## 2. How it works

- `craft/FloristCraftWork` is a `CrafterWork` (the crafting table's recipes) in the Florist's work package next to
  `flower/FloristWork`; it takes what's on her list (`data/aliveworkplace/tags/item/crafts/florist.json`,
  `CraftJobs.Job.FLORIST`, whose stand-in is gone). The two share her bag, so each waits while the other is busy
  (`FloristWork.isBusy`, `CrafterWork.isBusy`; the garden also waits while a crafting job is saved on her).
- A job helping another (`CrafterWork.helps`, `CrafterWork.makingsFor`): for a builder's thing that isn't on its list, whose own
  maker works near the builder, a job works out what that maker's chain lacks with every other job's thing counted raw
  (`Crafting.lacking(..., raw)`), and makes the first of those on its own list, into the builder's chest, under its own
  claim (`builder|item>making`). The Mason helps the Florist (bricks), the Florist helps the Shepherd (dyes).
- `WeaverWork.planFor`: with a Florist working within the village radius, the Shepherd plans with the Florist's things
  counted raw (`Crafting.asRaw`), so he takes dyes made and never makes them from lapis or flowers himself. With none
  about he makes them as before, so nothing he made became unmakable.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The making is a crafting job's: the builder's line and the helping jobs come with the job chains (Milestone 37's gate). Packs change what she makes with the `crafts/florist` tag. |

## 4. Saved data

Nothing new: a Florist's job in hand is the `crafter_job` attachment every crafting job uses, and her bag the
`builder_bag`. A Leatherworker loaded with a pot or dye job from before 46.3 carries it on to the end (the job is his).

## 5. Items, blocks, jobs, commands

- Data: `data/aliveworkplace/tags/item/crafts/florist.json` (flower pot, decorated pot, moss carpet, the sixteen dyes),
  unchanged since 46.1. No new blocks, items, jobs or commands.
- Text: `message.aliveworkplace.florist_craft.title` ("Florist · %s made for builders").

## 6. Decisions

- Only two helping pairs (Mason for the Florist, Florist for the Shepherd), not every job for every other: a general
  rule would have the Carpenter sawing planks for every bed and banner, and each extra pair costs planning every look.
- The Shepherd waits for the Florist's dyes only while a Florist works near him; the Mason fires bricks only while a
  Florist works near the builder (made with nobody to use them, they'd only fill the chest).

## 7. Known limits

- A decorated pot from sherds takes the sherds as they are; nobody makes sherds.
- The showcase scene `florist_pots` is filmed on GitHub, not in this session.

## 8. Proof

`FloristPotsGameTests`: the list is her own (no stand-in), a flower pot from bricks for a builder, a flower pot from
clay with the Mason firing the bricks, the red dye from her poppies for the Shepherd's bed, missing bricks named on the
line (not the clay), a Florist saved and loaded mid-job finishing the pot, and with no Florist the Leatherworker leaving
the pot alone and the line saying "Hire a Florist to make Flower Pots". `QaFloristPots1110cGameTests` (QA): a Florist at a composter, a
decorated pot, moss carpet, no Florist (the Mason fires nothing, the line names her), the whole shortage line word for
word, a Carpenter leaving the pot alone, one hire line for a decorated pot and moss carpet, the Shepherd dyeing himself
with no Florist, blue dye from cornflowers, empty pots still filled, her own pot filled without stalling the build, the
Mason saved and loaded mid-firing, her stand broken mid-job, and the status over her head word for word. Scene
`florist_pots`.
