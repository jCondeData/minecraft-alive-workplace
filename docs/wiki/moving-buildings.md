# Moving a finished building

The owner's Milestone 45: a building the Village Hall knows can be moved within its village by a new Mover job, who
takes it down top-down and puts it up again at the new spot with nothing lost. Being built in order; so far only the
first step (45.1) exists, and nothing of it is visible to a player yet.

Roadmap items: 45.1

## What a player sees

Nothing yet. The Hall's Move page (45.2) and the Mover (45.3) come next.

## How it works

**A building's snapshot (45.1).** `move/MovePlan` captures a building as a moving plan:

- every block in its box with its full state, and each block entity's data (a chest's items in their slots, a sign's
  text, a banner's patterns, a modded block's data), all by id, never by another mod's classes;
- its entities: armor stands, item frames, paintings and any other non-living thing standing in it (modded furniture
  entities too), saved by id without their UUID;
- its front, its bed heads, and its residents: villagers whose home or job site lies in the box, with those beds and
  workstations.

A building built from a blueprint or grown by the village is captured from its Finished record (its blueprint's size,
outline and turn give the box and the front); `captureAt(hall, pos)` finds the building at a spot. Any box with a
front can be captured too, for a building without a blueprint. An empty box, a record whose blueprint is gone or a
spot no building covers gives no plan.

A plan can be turned a quarter, a half or three quarters: stairs, doors and beds (both halves), chests, furnaces and
other facing blocks, standing signs and hanging frames all turn with it, and the lowest corner stays the lowest corner.
It saves as NBT with a version (a newer or broken plan loads as none), so a move can be saved mid-way.

## Items, blocks, jobs, commands

None yet: the Mover job and the Hall's Move page come in 45.2 and 45.3.

## Switches

None yet; the move's config switch comes with the Mover (45.3).

## Saved data

Nothing is saved yet. A plan's NBT (`MovePlan.save`, `version` 1: a block-state palette, blocks, things, beds, workstations, residents) is what the Mover's site will keep mid-move.

## Decisions

- Plans only turn, never mirror: a move keeps the building's handedness.
- Entities are saved without their UUID, so a rebuilt frame or stand is a new entity, never a duplicate of one still standing.

## Proof

`MovePlanGameTests` (4): a captured house rebuilt 14 blocks away matches block for block (`MaterialRules.matches`) with
its chest, sign, banner, frame and armor stand; the same at 90, 180 and 270 degrees; a building the hall knows
captured with its front and resident; empty and unknown buildings give no plan.

## Known limits

- A player's own house has no record the hall knows (nothing registers one yet), so it is captured only as a box.
- `place()` puts everything up at once; the Mover's step-by-step rebuild (45.3) must empty the original containers so nothing is doubled.
