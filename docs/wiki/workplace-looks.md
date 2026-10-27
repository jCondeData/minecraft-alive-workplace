# Workplace looks

The twelve workplaces the Steward asks a builder for each have their own building, so a player can tell the trade
from the street, and so does every job's house that villages grow by themselves, the Weaver's Cottage and the Inn.
Before, all of them were the same timber house with a different room inside (the owner's playtest, 2026-10-16).

Roadmap items: 26.11, 36.8, B170, 36.21

## 1. What a player sees

- **Builder's Workshop**: a saltbox drawing studio (a short steep roof side, a long low one) beside a fenced yard with a
  timber crane, its chain holding a block of stone, and stacks of stone and planks.
- **Carpenter's Workshop**: a long saw shed, its west end a closed workshop, the rest open on braced posts over logs on
  trestles; log piles before it (their ends to the street) and along the back, boards stood on end by the open gable.
- **Kitchen**: a white plastered cookhouse under a hipped roof, a domed brick bread oven bulging out of its side (the
  smoker is its mouth inside) with a tall smoking flue, and herb beds either side of the path.
- **Post Office**: a narrow two-story house, its timber upper floor jettied over the street with flower boxes, a red
  tiled roof with its gable to the street and a little dovecote on the ridge. A ladder leads upstairs.
- **Guard House**: a squat stone blockhouse with barred arrow slits, a corbelled battlement round a flat roof and a
  timber watch hut on it; a ladder inside leads up.
- **Clinic**: a white pavilion with its gable to the street and a stone-mullioned cross window over the door, and a ward
  across the back with cross windows and cots.
- **Ferry House**: a boathouse under a gambrel roof, a landing stage of planks at the front with a light on a mast.
- **Trainer's House** (Cobblemon): a long low dojo with a deep veranda, before it a fenced sand sparring ring with
  targets.
- **Leader's Hall** (Cobblemon): a stone hall with a portico of four columns under a pediment, steps the whole width,
  a red carpet inside up to the Training Post (it starts a step in from the door: a carpet right under the door's low
  lintel stopped villagers, who are taller than players, in the doorway).
- **Ball Workshop** (Cobblemon): a round tower with an open drying loft of slatted fences (apricorns, as colored carpets,
  laid out to dry) under a copper cone that greens with the years, and a low workshop beside it.
- **Trade Hall** (Cobblemon): a market hall, its brick-and-timber upper story on stone arches over an open trading
  floor, an open cupola on the ridge.
- **School** (Cobblemon): a brick U round a little courtyard with the tutor's Training Post in it.

Village houses (grown by world generation, one per job, each in its village's own materials: oak, cobblestone and
calcite on the plains; sandstone with flat roofs in the desert; acacia and white terracotta on the savanna; stone,
calcite and spruce in the snow; spruce and mossy cobblestone in the taiga):

- **Builder's Workshop**: a saltbox studio (a stepped flat roof in the desert) with a crane yard.
- **Carpenter's Workshop**: a closed workshop and an open saw shed under one long roof, log piles front and back.
- **Kitchen**: a hipped cookhouse with a domed brick oven and a tall flue on its side, herb beds before it.
- **Post Office**: a stone ground floor, a timber upper floor jettied over the street, a dovecote on the ridge.
- **Guard House**: a stone blockhouse with arrow slits, a battlement and a watch hut on the roof.
- **Clinic**: a pavilion with a stone cross window over the door and a long ward across the back.
- **Ferry House**: a boathouse under a gambrel roof, a signal mast with two lamps, an upturned boat on trestles.
- **Storehouse**: a tall granary, stone below and boards above, a hoist beam with a hay bale on its chain, a lean-to.
- **Orchard House**: a low hipped cottage beside a fenced orchard of blossoming fruit trees.
- **Flower Shop**: a shop with a glasshouse wing full of flower beds.
- **Ranch House**: a log cabin and a stable wing with stall gates and a hay loft.
- **Schoolhouse**: a one-room clapboard schoolhouse under a steep roof, a lamp belfry on its gable.
- **Inn**: two stories, a balcony along the front, a chimney up the gable.
- **Mortuary**: a narrow chapel with buttresses, lancet windows and a spirelet, a small graveyard with candles.
- **Tinker's Shop**: a glass monitor roof along the ridge and a tall brick smokestack.
- **Sifting Shed**: a long lean-to over a workroom and an open shed with gravel and sand heaps and a sieve.
- **Compost Yard**: a hut under a mossy turf roof with azaleas on it, three compost bays.
- **Winery**: a stone cellar house with casks before it and a pergola hung with glow-berry grapes.
- **Tailor's Shop**: two display windows, a striped awning, banners, a mansard roof.
- **Print Shop**: a tall narrow house with crow-stepped gables and a lamp on an iron arm.
- **Jeweler's Workshop**: a barred stone strongroom with a dome, an amethyst finial and gems on pedestals.
- With Cobblemon: the **Trainer's House** a long dojo with a veranda and banners; the **Leader's Hall** a portico of
  columns; the **Ball Workshop** a round drying tower with a copper spire; the **Trade Hall** a market hall on an arcade
  with a cupola; the **School** a hall with a clock tower; the **Fossil Lab** a stone lab under a glass skylight with a
  bone skeleton on show; the **Pokémon Center** a white house under a red hipped roof; the **Camp Kitchen** an A-frame
  and a fire pit with a spit; the **Berry Nursery** a cottage thatched with hay and watered berry beds; the
  **Daycare** a cottage with a round window and a playground; the **Gem Grotto** a mound of stone and moss with
  amethyst growing out of it.

Two buildable ones from the Blueprint Table changed too: the **Weaver's Cottage** is a stone workroom with an open
drying loft on top, dyed cloth hung in its bays, under a low hipped roof (the sheep pen and dye garden are as before);
the **Inn** has its guest story jettied over the street on brackets, under two gables side by side, its banner on a
tall post at the corner.

The buildings are bigger than the old 9 x 10 house (up to 17 x 17 x 14), so their plots are bigger; the Ferry House
keeps its 9 x 10 plot (the shore rule measures from its front).

## 2. How it works

- Drawn by `tools/blueprints/workplaces.py`; `village.py`'s `workplace` returns them, and `StarterBlueprints` lists each
  with its size. The blueprint ids are unchanged, so a workplace standing or being built in a save carries on.
- Each keeps exactly one of its job's block (the one the worker takes), no other vanilla job block, a bed, no trapdoor
  in the row along its front where villagers walk (B69), and a ladder to every upper floor.
- The village houses are drawn by `tools/blueprints/village_looks.py` (one drawing per job, given the village style's
  materials) and furnished by `village.py` as before: the street jigsaw, the door at the same spot, the job's room, the
  bed and the villager (or the spawn jigsaw) where they were, so `VillageHouses` and the village pools are unchanged.
  Their footprints grew (up to 13 x 17 x 13); the four luxury houses stay at most 10 wide.
- The Weaver's Cottage (`countryside.py`) and the Inn (`starter.py`) keep their sizes, rooms and job blocks.

## 3. Switches

None.

## 4. Saved data

None new.

## 5. Items, blocks, jobs, commands

None new.

## 6. Decisions

The owner (2026-10-16): "make new completely different exteriors for all different kinds of workshops ... nothing
should look identical". Each building differs in footprint, roof form and materials and has one signature from its
trade. The Ferry House kept its plot size so the shore rule and its tests stay as they are.

## 7. Known limits

- Builds already standing keep their old look until their owner asks: since 36.21 the Village Hall's Repairs page
  shows **Refresh to the latest look** on every finished build whose blueprint was redrawn since, and a Builder brings
  it up to the new drawing (the old drawing's leftovers away, the new pieces in; chests, beds in use, job blocks and a
  player's own blocks stay; nothing doubled, a furnished build's old pieces included; builder.md). Only the older drawings the mod ships are recognized (those changed since
  1.0). A workplace half built when the update arrives is finished from the new drawing over what the old one put up.
- Village houses already grown keep the old look; new chunks grow the new ones. The larger houses fit a village's
  street a little less often than the old small one. Measured (36.8, 50 villages of each type): villages stay as
  full as before (houses per village moved by at most 1% for every type), but vanilla houses take the room, so a
  village grows fewer of ours (2.2 to 2.5 per village, from 3 to 4; desert villages about 0.4, in 14 of 50).

## 8. Proof

GameTests: `WorkplacesGameTests` (each workplace built by a builder from the player's own entry points, its job block
taken by its worker), `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`, `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`,
`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`, `CountryWorkplacesGameTests`.

Showcase scenes: `workplaces` (all twelve placed side by side), `refresh_old_house` (an old Stone House before and after its refresh, 36.21).

Measurement (not part of `build`): `tools/modtest/village_density.sh` runs `VillageDensityMeasure` alone, 50 village
starts per type at fixed seeds, and prints houses per village (mean, lowest, vanilla, ours) and footprint per type; the
before and after table is in the ROADMAP Notes (2026-10-18, 36.8).
