# The Bandit King: his fort and the King

The Bandit King's fort rises a stage a dawn out beyond a village, and the King waits in his keep with his treasure.
ROADMAP 31.15; the story that brings him, `arcs/bandit_king.json`, is 31.16. Builds on the arc engine's `place`
(31.4), bandit camps (`BanditCamps`, 32.3's lairs) and the bounty captains' names (31.13). Part of the 1.5 expansion.

Roadmap items: 31.15

## What a player sees

- **The fort** goes up 110 to 150 blocks from the hall, one stage each dawn, each standing on the one before (same
  29 x 29 footprint, same corner, same front):
  1. a palisade of sharpened spruce logs round a yard of trampled earth, a gate standing open under a log lintel with
     two skulls, a fire ringed with seats, four tents, a horse pen and a cart of plunder; four bandits in the yard;
  2. two watchtowers on the front corners (stone footings, log posts, a ladder up to a plank platform with a fence
     parapet, a dark oak roof) and a lean-to stable where the pen was; a pillager on each tower;
  3. the King's keep at the back: a hall of cobblestone and timber under a dark oak roof, a stone chimney, his war
     banner (black, a red skull) over the door, a throne between two more banners, a long table, a store lean-to, the
     treasure chest, and a postern door in the palisade behind it (placed shut, or open when the story says so).
- **The King**: a vindicator named "Grimwald, the Bandit King" (a first name from the bounty captains' list), crowned
  with a golden helmet, in an iron chestplate, leggings and boots, with a diamond axe and 150 health. Players within 32
  blocks see his red boss bar. At half health he blows a goat horn and four bandits come out of the keep, once.
- **His men keep to the fort**: none wanders past the palisade much, even after the area unloads and loads again.
- **The treasure chest** (`aliveworkplace:chests/bandit_king`): 16 to 32 emeralds, 4 to 8 gold ingots, 1 to 3
  diamonds, an enchanted iron sword or crossbow, and always **the King's Crown** (the golden helmet, Protection III and
  Unbreaking III, named). The King wears his crown but never drops it, nor the rest of his gear: it's in the chest.

## How it works

- `guard/BanditFort` holds the fort: its three blueprints (`aliveworkplace:story/bandit_fort`, `_2`, `_3`), where it may
  stand (`BanditFort.fits`: the camps' test `BanditCamps.fits` widened to the fort's 29 x 29, so no more uneven or
  blocked columns than a camp allows for its size; dry; and only natural ground, plants and trees in its box, with no
  section a player built in, `PlayerBuilt`), `BanditFort.site` (fit, loaded ground 110 to 150 blocks out) and
  `BanditFort.place` (stage 0 clears the grass and bushes and fills the ground under it as `Lairs.found` does; later
  stages are set down over it).
- **In an arc**: a `place` effect with `"stages"` set to the three blueprints and the rule `"fort": [110, 150]`.
  `ArcEffects.spot` takes fit ground there if it's loaded now, else a column in that ring; `ArcEffects.build` builds the
  first stage on the first column round it (out to 16 blocks) where `BanditFort.fits`, once a player is within 96
  blocks, then the next stage at each next dawn (as every staged `place`). After each stage `BanditFort.placed` brings
  its men, fills the treasure chest and sets the postern (open while the arc has the flag `postern_open`).
- **The men** are vindicators and pillagers named Bandit, tagged `aliveworkplace_fort`; where each keeps to (the yard's
  middle, 17 blocks; a tower man his platform, 2 blocks) is in a tag, so a leash lost to a reload is put back.
- `guard/BanditKing` makes the King, his crown (`BanditKing.crown`) and his name (the lang key
  `entity.aliveworkplace.bandit_king`, from `bounties/captains.json`'s `first_names`). Every 10 ticks `BanditFort.tick`
  looks over the fort's loaded men: leashes, the King's boss bar (players within 32 blocks) and his horn (at half health,
  once: the tag `aliveworkplace_bandit_king_horn`; the four called bandits come out at the keep's door and go for whoever
  last hurt him).

## Items, blocks, jobs, commands

- Blueprints `aliveworkplace:story/bandit_fort`, `aliveworkplace:story/bandit_fort_2`, `aliveworkplace:story/bandit_fort_3`
  (drawn in `tools/blueprints/bandit_fort.py`; not in the blueprint library or the village map).
- Loot table `aliveworkplace:chests/bandit_king`; the King's Crown is a named golden helmet
  (`item.aliveworkplace.kings_crown`), not a new item.
- No new blocks, jobs or commands.

## Switches

None of its own: the fort comes only with the Bandit King's arc (31.16), under `storyArcs`.

## Saved data

Nothing new in saved files: a fort placed by an arc is an arc place in `aliveworkplace_stories` (its `stages`,
`placed`, `origin`, `rotation`). The men carry their tags (`aliveworkplace_fort`, their home, the King's door and
horn) in their own entity data.

## Decisions

- 31.15: "his men keep to the fort" is the whole yard (17 blocks round its middle), not the keep: the King comes out to
  fight in the yard; the tower pillagers keep to their platforms.
- 31.15: the treasure chest is filled when the keep is placed, so it holds the crown however long the King stands; the
  King's own crown never drops.
- 31.15: the King's men aren't a lair's band (no `aliveworkplace_bandit` tag), so killing them never counts against a
  bandit camp by the same village.
- 31.15: the fort waits for a player within 96 blocks before a stage is built, as every arc place does; a stage a
  player didn't come near for is built when one does (each next stage at the next dawn after).

## Known limits

- Until 31.16's arc lands nothing places the fort in a survival world: it is built and tested, ready for the arc.
- With no fit ground within 16 blocks of the arc's column the fort isn't built (the arc logs it and its place is
  skipped).

## Proof

- GameTests: `BanditFortGameTests`: the three stages on uneven ground (`threeStagesPlaceInOrderOnUnevenGround`), the
  arc's way (`theArcPlacesAStageADawn`), the King (`theKingsGearHealthAndBossBar`), the horn
  (`theHornCallsFourBanditsOnce`), the chest (`theTreasureChestAlwaysHasTheCrown`).
- Showcase scene `bandit_fort`.
