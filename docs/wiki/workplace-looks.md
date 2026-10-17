# Workplace looks

The twelve workplaces the Steward asks a builder for each have their own building, so a player can tell the trade
from the street. Before, all twelve were the same plains village house with a different room inside (the owner's
playtest, 2026-10-16).

Roadmap items: 26.11

## 1. What a player sees

- **Builder's Workshop**: a saltbox drawing studio (a short steep roof side, a long low one) beside a fenced yard with a
  timber crane, its chain holding a block of stone, and stacks of stone and planks.
- **Carpenter's Workshop**: a long saw shed, its west end a closed workshop, the rest open on braced posts over logs on
  trestles; log piles before it (their ends to the street) and along the back, boards stood on end by the open gable.
- **Kitchen**: a white plastered cookhouse under a hipped roof, a domed brick bread oven bulging out of its side (the
  smoker is its mouth inside) with a tall smoking flue, and herb beds either side of the path.
- **Post Office**: a narrow two-storey house, its timber upper floor jettied over the street with flower boxes, a red
  tiled roof with its gable to the street and a little dovecote on the ridge. A ladder leads upstairs.
- **Guard House**: a squat stone blockhouse with barred arrow slits, a corbelled battlement round a flat roof and a
  timber watch hut on it; a ladder inside leads up.
- **Clinic**: a white pavilion with its gable to the street and a stone-mullioned cross window over the door, and a ward
  across the back with cross windows and cots.
- **Ferry House**: a boathouse under a gambrel roof, a landing stage of planks at the front with a light on a mast.
- **Trainer's House** (Cobblemon): a long low dojo with a deep veranda, before it a fenced sand sparring ring with
  targets.
- **Leader's Hall** (Cobblemon): a stone hall with a portico of four columns under a pediment, steps the whole width,
  a red carpet inside up to the Training Post.
- **Ball Workshop** (Cobblemon): a round tower with an open drying loft of slatted fences (apricorns, as coloured carpets,
  laid out to dry) under a copper cone that greens with the years, and a low workshop beside it.
- **Trade Hall** (Cobblemon): a market hall, its brick-and-timber upper storey on stone arches over an open trading
  floor, an open cupola on the ridge.
- **School** (Cobblemon): a brick U round a little courtyard with the tutor's Training Post in it.

The buildings are bigger than the old 9 x 10 house (up to 17 x 17 x 14), so their plots are bigger; the Ferry House
keeps its 9 x 10 plot (the shore rule measures from its front).

## 2. How it works

- Drawn by `tools/blueprints/workplaces.py`; `village.py`'s `workplace` returns them, and `StarterBlueprints` lists each
  with its size. The blueprint ids are unchanged, so a workplace standing or being built in a save carries on.
- Each keeps exactly one of its job's block (the one the worker takes), no other vanilla job block, a bed, no trapdoor
  in the row along its front where villagers walk (B69), and a ladder to every upper floor.
- The village houses that world generation places are not changed by this.

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

- Builds already standing keep their old look; only new ones are built the new way. A workplace half built when the
  update arrives is finished from the new drawing over what the old one put up.
- The village houses in world generation still share one house per village style.

## 8. Proof

GameTests: `WorkplacesGameTests` (each workplace built by a builder from the player's own entry points, its job block
taken by its worker), `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`.

Showcase scenes: `workplaces` (all twelve placed side by side).
