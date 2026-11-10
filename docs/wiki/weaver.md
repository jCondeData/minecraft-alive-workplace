# The Shepherd weaves

Milestone 46, item 46.2 (owner, 2026-11-09: each job crafts its own kind of thing; vanilla's Shepherd sells beds,
carpets, banners and paintings). The Shepherd makes the woolen things builders wait for at his loom, and is no longer
anyone's stand-in: the Leatherworker stopped weaving.
Roadmap items: 46.2

## 1. What a player sees

- A builder near a Shepherd who is short of wool, dyed wool, carpet, a bed, a banner, a banner pattern or a painting
  gets it from the Shepherd: he fetches the makings from the builder's chests (and his own), makes it at the loom, step
  by step, and puts it in the builder's chest.
  - Two blue beds from white wool, blue dye and planks: the wool is dyed first, then the beds are made ("making 2 Blue
    Beds (step 1 of 2)" over his head).
  - A banner from string and a stick: the wool is spun from the string first, then the banner.
- Over his head while weaving: "Shepherd · 3 woven" and what he's making. Between jobs he keeps his flock: the flock
  waits while he weaves, and the weaving waits while he shears.
- A missing dye is named on the builder's line: "Can't be made yet: short of 6 Blue Dye to make 2 Blue Beds". Planks
  and sticks are named the same way. With lapis or a cornflower in the chests, he still makes the dye himself.
- A builder with no Shepherd nearby reads "Hire a Shepherd to make Blue Beds"; the Storehouse's board says "Made by:
  the Shepherd (name)" or "Hire a Shepherd to make it".
- A Leatherworker no longer makes wool, carpets, beds, banners or paintings, even with the makings at hand.

## 2. How it works

- `craft/WeaverWork` is a `CrafterWork` at the loom (the crafting table's recipes), added to vanilla Shepherds' work
  next to `ranch/ShepherdWork`; it takes what's on the Shepherd's list (`data/aliveworkplace/tags/item/crafts/shepherd.json`,
  `CraftJobs.Job.SHEPHERD`, whose stand-in is gone). The two share his bag, so each waits while the other is busy (as the Librarian's books and
  enchanting do), and vanilla's routine waits for both.
- Its chains plan the way every crafting job's do (37.14a): every crafting recipe, a few steps down, saved on the
  villager step by step.
- What's short: `Crafting.lacking(..., raw)` plans the shortfall with what's on another job's list (a dye, planks,
  sticks) counted as raw, so the line names that thing, not what makes it. Only the line: the plan he makes is
  unchanged.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The weaving is a crafting job's: the builder's line shows with the job chains (Milestone 37's gate). Packs change what he makes with the `crafts/shepherd` tag. |

## 4. Saved data

Nothing new: a Shepherd's job in hand is the `crafter_job` attachment every crafting job uses, and his bag the
`builder_bag`. A Leatherworker loaded with a weaving job from before 46.2 carries it on to the end (the job is his).

## 5. Items, blocks, jobs, commands

- Data: `data/aliveworkplace/tags/item/crafts/shepherd.json` (wool, carpets, beds, banners, the painting, the four
  banner patterns 1.21.1 crafts). No new blocks, items, jobs or commands.
- Text: `message.aliveworkplace.weaver.title` ("Shepherd · %s woven").

## 6. Decisions

- He still makes a dye (or planks) a step needs when its makings are in the chests: the Leatherworker who wove before
  did, and 46.2 must not leave anything unmakable. Dyes stay on the Florist's list; 46.3 has the Florist make them for
  him.
- The `dyer` showcase scene (a market stall's red wool and carpets) now films the Shepherd at a loom.

## 7. Known limits

- Banner patterns from 1.21.2 on (field masoned, bordure indented) aren't in 1.21.1 and so not on the list.
- The showcase scene `weaver` is filmed on GitHub, not in this session.

## 8. Proof

`ShepherdWeavesGameTests`: the list is his own (no stand-in), two blue beds from white wool, planks and blue dye made
in steps for a builder, a banner spun from string, a missing dye named on the line (not the lapis), a Shepherd saved and
loaded mid-job finishing the beds, and with no Shepherd the Leatherworker leaving the beds alone and the line saying
"Hire a Shepherd to make Blue Beds". `BuilderGameTests.dyerMakesTheConcreteAndRedWoolTheBuilderNeeds` has the Shepherd
dye the red wool. Scene `weaver`.

`QaShepherdWeavesGameTests` (QA, 12): two blue beds with only two blue dyes (white beds dyed), red carpets and blue wool
from white wool and dye (with sheep grazing by the loom), white beds spun from string, a Shepherd saved between the
chain's steps finishing the beds, the loom broken mid-job losing no wool or planks, the dye line word for word, no wool
or string on the line, a Carpenter (and a nitwit or a baby by a loom) never weaving and the line saying "Hire a Shepherd
to make Blue Beds, White Banners", "Shepherd · N woven" and "making 2 White Beds (step 1 of 2)" over his head, and
paintings and flower banner patterns made for a Storehouse's stock orders.
