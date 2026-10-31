# The Bandit King: his fort, the King and his story

The Bandit King's fort rises a stage a dawn out beyond a village, and the King waits in his keep with his treasure.
ROADMAP 31.15; the story that brings him, `arcs/bandit_king.json`, is 31.16. Builds on the arc engine's `place`
(31.4), bandit camps (`BanditCamps`, 32.3's lairs) and the bounty captains' names (31.13). Part of the 1.5 expansion.

Roadmap items: 31.15, 31.16, B659

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
  blocks, in his dimension, see his red boss bar; it goes away when they walk off, go to another dimension, log out or
  die. At half health he blows a goat horn and four bandits come out of the keep, once.
- **His men keep to the fort**: none wanders past the palisade much, even after the area unloads and loads again.
- **The treasure chest** (`aliveworkplace:chests/bandit_king`): 16 to 32 emeralds, 4 to 8 gold ingots, 1 to 3
  diamonds, an enchanted iron sword or crossbow, and always **the King's Crown** (the golden helmet, Protection III and
  Unbreaking III, named). The King wears his crown but never drops it, nor the rest of his gear: it's in the chest.

- **The story** (31.16, `arcs/bandit_king.json`, a `threat` arc) starts in a Town or bigger that has broken up at least
  2 bandit camps, 8% a day, with `banditCamps` on. Its chapters:
  1. **Smoke on three hills**: three bandit camps (the `camp/bandit_camp` blueprint) 80 to 104 blocks out, each with a
     "Captain of the Bandit King" in iron (36 extra health) and 3 or 4 bandits, and his war banner by the fire. Break
     the three banners (each drops "The Bandit King's War Banner", and counts). Villagers say "All the camps fly the
     same banner now."
  2. **The King's fort**, the next dawn: the fort rises a stage a dawn (above). Allies, each optional: **the neighbors'
     pledge** (finish any quest at another village with a hall within caravan range: 3 of its guards come for the
     storm), **swords for hire** (24 emeralds at the hall: 6 mercenaries), **the deserter** (Pell waits by the first
     camp; bring him 16 bread and he tells of the postern, which the keep leaves open, and joins the village after).
  3. **The storm**, once the keep stands: the allies come to the hall; raise a Rally Banner within 48 blocks of the fort
     and they follow it with your guards. His raiders (the village's bandit raid, half again as big) come every second
     night until he falls. If he stands after 10 days, **the King's tribute**: he takes a third of the treasury and
     rides off with his band, the fort is left a ruin, moods drop 10 for 3 days, and the story ends badly.
  - **Ending**: his band leaves the fort (the treasure chest is yours), a festival the next evening, +150 reputation
    and the honor **Kingslayer** to everyone who fought in the storm, Pell joins the village (no job), the allies go
    home, and no bandit camps come for 10 days. Every chapter, banner, ally, raid and ending has its chronicle line.

## How it works

- `guard/BanditFort` holds the fort: its three blueprints (`aliveworkplace:story/bandit_fort`, `_2`, `_3`), where it may
  stand (`BanditFort.fits`: the camps' test `BanditCamps.fits` widened to the fort's 29 x 29, so no more uneven or
  blocked columns than a camp allows for its size; dry; and only natural ground, plants and trees in its box, with no
  section a player built in, `PlayerBuilt`), `BanditFort.site` (fit, loaded ground 110 to 150 blocks out) and
  `BanditFort.place` (stage 0 clears the grass and bushes and fills the ground under it as `Lairs.found` does; later
  stages are set down over it).
- **In an arc**: a `place` effect with `"stages"` set to the three blueprints and the rule `"fort": true` (110 to 150
  blocks, `BanditFort.NEAR` and `FAR`; `"fort": [near, far]` sets another ring).
  `ArcEffects.spot` takes fit ground there if it's loaded now, else a column in that ring; `ArcEffects.build` builds the
  first stage on the first column round it (out to 16 blocks) where `BanditFort.fits`, once a player is within 96
  blocks, then the next stage at each next dawn (as every staged `place`). After each stage `BanditFort.placed` brings
  its men, fills the treasure chest and sets the postern (open while the arc has the flag `postern_open`).
- **The men** are vindicators and pillagers named Bandit, tagged `aliveworkplace_fort`; where each keeps to (the yard's
  middle, 17 blocks; a tower man his platform, 2 blocks) is in a tag, so a leash lost to a reload is put back.
- `guard/BanditKing` makes the King, his crown (`BanditKing.crown`) and his name (the lang key
  `entity.aliveworkplace.bandit_king`, from `bounties/captains.json`'s `first_names`). Every 10 ticks `BanditFort.tick`
  looks over the fort's loaded men: leashes, the King's boss bar (players within 32 blocks; each update first walks the
  bar's own players and takes off any who left his level, logged out, died or are out of range, B659; a bar whose King
  isn't loaded in any dimension is dropped) and his horn (at half health,
  once: the tag `aliveworkplace_bandit_king_horn`; the four called bandits come out at the keep's door and go for whoever
  last hurt him).

- **The story** (31.16): `story/BanditKingStory` does what the arc's effects can't say: the count of bandit camps broken
  up (a `Lairs.onBroken` listener for the bandit culture, saved per village as `bandit_camps_broken` in
  `aliveworkplace_stories`, 0 in older saves) read by the condition `bandit_camps_broken` (`rules/BanditCampsBroken`,
  false with `banditCamps` off); the war banner set beside each camp when the arc builds it and counted when a player
  breaks it near the camp (flags `banner_<camp>`, then `banners_taken`); the allies (flags `ally_neighbours`,
  `ally_mercenaries`, `ally_pell`; Pell's quest sets `postern_open` and opens the postern of a keep already standing);
  the `bandit_king` effect with `step` `storm` (the allies come: guards with `MERCENARY_UNTIL` set 11 days on, so they
  leave as mercenaries do), `tribute` and `ending`; every 40 ticks the raids (at dusk of the storm's even days, flag
  `raid_night`) and the allies enlisted on a raised Rally Banner within 48 blocks of the fort; the King's death (flag
  `king_dead`, the killer and the players within 48 blocks credited as the chapter's helpers). The ending's flag
  `no_bandit_camps` (10 days) keeps bandits out of `Lairs.round`.

## Items, blocks, jobs, commands

- Blueprints `aliveworkplace:story/bandit_fort`, `aliveworkplace:story/bandit_fort_2`, `aliveworkplace:story/bandit_fort_3`
  (drawn in `tools/blueprints/bandit_fort.py`; not in the blueprint library or the village map).
- Loot table `aliveworkplace:chests/bandit_king`; the King's Crown is a named golden helmet
  (`item.aliveworkplace.kings_crown`), not a new item.
- No new blocks, jobs or commands.

## Switches

None of its own: the story runs under `storyArcs` (and `disabledArcs` with `bandit_king`), and starts only with
`banditCamps` on. Its raids need `villageRaids`.

## Saved data

A fort placed by an arc is an arc place in `aliveworkplace_stories` (its `stages`, `placed`, `origin`, `rotation`).
31.16 adds `bandit_camps_broken` to each village's entry there (missing in older saves: 0); everything else of the
story is in the arc's flags and roles. The men carry their tags (`aliveworkplace_fort`, their home, the King's door and
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

- 31.16: the allies are guards with a mercenary's leave time (11 days), so they leave the way hired mercenaries do
  when the storm ends; the neighbors' guards don't walk home, they leave in a puff like mercenaries.
- 31.16: Pell's quest is a `talk` the arc counts only once he has his 16 bread (without it he asks for it).
- 31.16: "half again as big" raids are sized as a raid on a village of 6 + 1.5 times its villagers (the plain raid is
  3 + villagers / 4), still capped as every raid.
- 31.16: a camp never built (no clear ground) counts as its banner taken.

## Known limits

- An arc marked `threat` should not start in a village set At peace or at threat level 0 (31.4), but today it
  still does (B699; failing tests on the tests/threat-arcs-peace-1031 branch).
- The camps, Pell and the fort are built or come only once a player is within 96 blocks, as every arc place.
- With no fit ground within 16 blocks of the arc's column the fort isn't built (the arc logs it and its place is
  skipped).
- Open bugs found by QA on 2026-10-28 (failing tests on the tests/bandit-king-1028 branch): about 1 fort site in 40 lies up to
  half a block outside 110 to 150 blocks (`BanditFort.site` rounds each axis on its own, as the camps' ring did before
  B205); and a player who goes to another dimension while near the King keeps seeing his boss bar.

## Proof

- GameTests: `BanditFortGameTests`: the three stages on uneven ground (`threeStagesPlaceInOrderOnUnevenGround`), the
  arc's way (`theArcPlacesAStageADawn`), the King (`theKingsGearHealthAndBossBar`), the horn
  (`theHornCallsFourBanditsOnce`), the chest (`theTreasureChestAlwaysHasTheCrown`).
- GameTests: `BanditKingStoryGameTests`: the arc walked from the banners to the King's fall with all three allies
  (`banditKingStoryWalk`), the tribute (`banditKingTribute`), the trigger and the saved count (`banditKingTrigger`).
- GameTests: `QaBanditKing1028GameTests` (QA, from the spec): the fort rule's ring on unloaded ground, the boss bar's
  32-block edge and its health, the horn at exactly half health and not again after a reload, war banners broken by a
  player (a plain banner and one far from the camps don't count; a camp counts once), the Town rank of the trigger,
  the fort resumed from a save mid-arc, every sentence a player reads, and the chest's one enchanted weapon.
- GameTests: `QaBanditKingBugTests`: the bar leaves a player who goes to the Nether
  (`qaKingsBarLeavesAPlayerWhoGoesToTheNether`, B659).
- Showcase scenes `bandit_fort` and `bandit_king`.
