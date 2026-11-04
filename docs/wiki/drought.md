# Drought and the Well Keeper

ROADMAP 32.17. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. When the rain stays away a village can fall into a
drought: the crops wilt, the farmland dries, the cauldrons run low and the villagers fret about the wells. A new job,
the **Well Keeper**, carries water to the fields.

Roadmap items: 32.17

## What a player sees

- **A drought comes.** After five days without rain a village may fall into a drought: 5% a day, or 15% a day in a
  desert, savanna or badlands village (which don't wait for the five days), and twice as likely while a desert
  raiders' lair stands near. Players in the village and the hall's owner read "A drought has come to Oakvale: the
  crops wilt and the wells run low." It lasts 3 to 5 days, and rain ends it at once: "The drought in Oakvale is over:
  the wells are filling again." The hall's chronicle notes both, and the wellbeing icon and What next? show it.
- **In the hall's area**: wheat, carrots, potatoes, beetroots, torchflower and pitcher crops, melon and pumpkin stems
  and sweet berry bushes grow at a quarter of their pace; farmland dries a step on each of its random ticks, even
  beside water, and water nearby doesn't wet it again (dry farmland by water stays tilled, but dry, until a Well Keeper
  waters it or it rains); every water cauldron
  loses a level a day; and every villager is 10 less happy, "The wells are low", unless the village has a finished
  **Well** (either tier) or **Fountain**.
- **The Well Keeper** (a villager at a cauldron given a shovel) works the long day shift. In a drought they fill a
  bucket at the nearest well, river or pond and carry it to the village's fields: each bucket wets the 9 farmland
  round where it lands (moisture 7) in a splash, and the crops on them grow at the full pace that day. Above their
  head: "Well Keeper · bucket full", "carrying water to the dry fields", "fetching water".
- **Between droughts** they keep the village's cauldrons full ("filling the cauldrons") and wet farmland that has
  dried right out ("wetting dry farmland").
- **They foretell it.** With a Well Keeper in the village, a drought is foretold a day ahead: "Rowan looks at the wells
  and the sky: a drought will come to Oakvale. Days until it strikes: 1." (32.14's warning lines), and the chronicle
  says "Rowan the Well Keeper foretold a drought."
- **With Cobblemon**, Water-type Pokémon pastured near the cauldron shorten the time it takes to fill and pour a bucket.

## How it works

- **The data.** `data/aliveworkplace/disasters/drought.json` on the [disaster engine](disasters.md): `chance` 0.05;
  `requires` one dry day always (none starts in the rain) and five unless the hall's biome is `minecraft:desert`,
  `#minecraft:is_savanna` or `#minecraft:is_badlands`; `factors` set those biomes' chance to 0.15 and double it with
  a lair of `aliveworkplace:desert_raiders` standing; `duration` 3 to 5 days, `ends_with_rain`; effects `crop_growth`
  0.25, `farmland_dries`, `cauldrons_drain` 1 and `mood` -10 (`mood.aliveworkplace.reason.drought`, `unless_built`
  `aliveworkplace:well`, `aliveworkplace:well_2`, `aliveworkplace:fountain`); `answered_by` and `foretold_by` the Well
  Keeper, `foretell_days` 1; its own lines (`disaster.aliveworkplace.drought.*`, `chronicle.aliveworkplace.drought.*`).
- **Stems, berry bushes and pitcher crops.** The engine's crop check (a mixin on the crop's random tick) now also runs
  on `StemBlock`, `SweetBerryBushBlock` and `PitcherCropBlock` (`DroughtPlantMixin`; a pitcher crop isn't a
  `CropBlock`, B947), so any disaster's `crop_growth` holds them back too. Torchflower crops are `CropBlock`s already.
- **Farmland by water** (`DroughtFarmlandMixin`, B946): `farmland_dries` takes wet farmland down a step, and farmland
  already at moisture 0 within vanilla's reach of water (4 blocks) skips its tick in an afflicted area, so the water
  doesn't wet it back to 7. It isn't turned to dirt either: vanilla keeps farmland by water tilled. In the rain vanilla
  wets it as usual.
- **Days without rain** (`threat/RainLedger`, B948): the last day it rained is saved per dimension, so a restart no
  longer starts the count again.
- **Watered farmland** (`threat/Drought`): a bucket sets the 3 x 3 farmland round the block it's poured on to moisture
  7 and remembers each, in memory, for the day. On a watered block the crop check always lets the crop grow (and
  draws no random number, so it grows exactly as without the drought), and the farmland's random tick keeps its
  moisture that day, drought or not.
- **The Well Keeper** (`work/WellKeeperWork`) works in the WORK activity on the builders' long shift. In a drought
  (their cauldron in an afflicted hall's area) they water the nearest farmland within 24 blocks of the cauldron that
  isn't both wet through and watered today. Otherwise they fill the nearest cauldron of the village (cauldrons are
  found as the leatherworker's POIs, so nothing is scanned) that isn't full, then farmland at moisture 0. Water comes
  from the nearest water source with air above it within 24 blocks of the cauldron (never a cauldron). Filling and
  pouring each take 30 ticks, shortened by water partners (`work/Partners`: Water). Each bucket gains them 2
  experience. When nothing needs water they look again every 5 seconds.

## Switches

| Key | Default | What it does |
|---|---|---|
| `wellKeepers` | on from 1.0 | Villagers at a cauldron can be made Well Keepers with a shovel. Off: no Well Keeper job (the cauldron's tooltip leaves it out), and Well Keepers already hired stand idle. |
| `disasters` | every disaster id → true | `drought: false`: no drought ever starts, and one under way ends at once. |

## Saved data

On a Well Keeper: `well_keeper_water` (1: their bucket is full; absent or 0: empty). The drought itself is saved on
the hall by the engine (`disaster`, `disasterSince`, `disasterEnds`). Which farmland was watered today is kept in
memory: after a restart the keeper waters it again. Per dimension, `aliveworkplace_rain.dat` (`RainLedger`, from
B948): `last_rain_day`, the last day it rained. A world saved before has none and counts the dry days from the first day
the new version sees (0 on that day), as before.

## Items, blocks, jobs, commands

- The job `aliveworkplace:well_keeper`, at a cauldron (shared with the Leatherworker, the Sifter and the Vintner),
  picked with any shovel. Its outfit: a wide straw sun hat with a river-blue band, a teal wool tabard over a long linen
  smock darkened at the wet hem, a leather yoke over the shoulders, a wooden bucket at the hip, a coil of rope, a tin
  dipper and a shovel slung down the back (and a zombie copy).
- Trades: a bucket bought, a water bucket sold (Novice); clay, a cauldron (Apprentice); cobblestone (Journeyman);
  pointed dripstone (Expert); a wet sponge (Master).
- Pokémon partner types: Water.
- No new blocks, items or commands.

## Decisions

- 32.17 (lane b): "desert, savanna and badlands villages 15% a day, others 5% a day after 5 days without rain" is read
  as the dry biomes not waiting for the five days; every drought still needs one day without rain, so none starts in
  the rain that would end it.
- 32.17 (lane b): watered farmland keeps its moisture that day (the drought would otherwise dry it again within hours
  and slow its crops), which is what makes "grow at full pace that day" true.
- 32.17 (lane b): the keeper never fills the bucket at a cauldron: in a drought that would only move the village's
  water from one place to another.
- B946 (lane a): "farmland dries, water or not" is read as water not wetting it again in the drought either: dry
  farmland by water stays dry farmland (not dirt) until a Well Keeper's bucket or the rain, so fields by a pond need
  the keeper like the rest.
- B948 (lane a): the dry days survive a restart (saved), rather than a restart counting from that day, so a server
  that restarts often still gets droughts outside the dry biomes.
- 32.17 (lane b): between droughts only farmland dried right out (moisture 0) is watered, so a keeper doesn't walk
  the fields all day for farmland that is merely damp.

## Known limits

- Watered farmland is kept in memory for the day; a restart forgets it (the keeper waters it again).
- The keeper looks after farmland within 24 blocks of their cauldron, and 4 up or down; a large village wants a
  keeper by each field.
- The Water-type partners' speed-up is tested only where Cobblemon is installed (it needs pastured Pokémon).

## Proof

- `DroughtGameTests.wheatGrowsAtAQuarterPace`: in a drought the crop check lets wheat grow on at most 115 of 400
  random ticks (a fixed `RandomSource`), the wheat grows about a quarter as often as without it, watered wheat exactly
  as often as without it, and watered farmland keeps its moisture while the rest dries.
- `DroughtGameTests.berryBushesGrowSlower`: sweet berry bushes grow less than half as often in a drought.
- `DroughtGameTests.theKeeperWetsDryFarmland`: a Well Keeper wets a dry field from a pond within 600 ticks and gains
  experience.
- `DroughtGameTests.betweenDroughtsCauldronsAndFieldsAreKept`: an empty cauldron is filled and dried-out farmland
  wetted.
- `DroughtGameTests.rainEndsTheDrought`, `DroughtGameTests.aFinishedWellTakesTheMoodAway`,
  `DroughtGameTests.theKeeperForetellsItADayAhead`, `DroughtGameTests.theChanceFollowsTheDryDays`,
  `DroughtGameTests.theJobStartsFromACauldronAndAShovel` (and config off: no job, a hired keeper idle),
  `DroughtGameTests.theBucketSurvivesASave`, `DroughtGameTests.everyNewSentenceHasItsText`; `StationsGameTests`' row.
- `QaDroughtPlague1104dBugTests.farmlandByWaterStaysDryInADrought` (B946): farmland a block from a pond, dried to 1,
  never goes above 1 in 20 random ticks of the drought.
- `QaDroughtPlague1104dBugTests.pitcherCropsAreHeldBack` (B947): pitcher crops grow about a quarter as often in a
  drought as without it.
- `QaDroughtPlague1104dBugTests.aRestartDoesNotMakeItRain` (B948): after 6 dry days in the plains the chance stays 5%
  once the memory is forgotten as at a restart; `DroughtGameTests.theDaysWithoutRainAreSaved`: the last rain day
  survives a save and load, an old save without it counts from that day, and a day set back starts the count again.
- `QaDisastersGameTests.theDroughtsChanceByBiomeAndDryDays` (QA): in the plains no chance on the 4th day without
  rain, 5% from the 5th; 15% in the desert, savanna and badlands from the first dry day; none in the rain.
- `QaDisastersGameTests.aDesertLairDoublesTheDrought`: a standing desert raiders' lair makes it 30% in the desert and
  10% in the plains.
- `QaDisastersGameTests.cauldronsRunDryOneLevelADayOnlyInTheVillage`: a full water cauldron in the village holds 2, 1,
  then 0 over three days of the drought (two rounds a day take one level), and one outside the village keeps its water.
- `QaDisastersGameTests.pumpkinStemsAreHeldBack`: pumpkin stems grow at about a quarter pace.
- `QaDisastersGameTests.wateredFarmlandIsGoodForThatDayOnly`: wheat on farmland watered today passes every crop check;
  the next day it is back to a quarter and the farmland dries again.
- `QaDisastersGameTests.aFountainOrWellIITakesTheMoodAwayAFarWellDoesNot`: a finished Fountain or Well II takes "The
  wells are low" away; a Well finished 60 blocks off, outside the village, doesn't.
- `QaDisastersGameTests.theDroughtAndPlagueReadRight`: the start, end and foretold messages with the village's name,
  the chronicle's lines, the wellbeing line ("Drought (days left: N)") and the mood reason read as above.
- `QaDroughtPlague1104dGameTests` (QA, 2026-11-04): over 30 seeds a drought with no rain lasts 3, 4 or 5 days, on
  every one of them and not a day more (`aDryDroughtLastsItsDaysAndNoMore`); wheat outside the hall's area grows at
  full pace (`cropsOutsideTheVillageGrowAtFullPace`); melon stems are held back (`melonStemsAreHeldBack`); a bucket
  wets the 3 x 3 round it and none of the 16 blocks beyond (`aBucketWetsNineFarmlandNotATenth`); the mood is exactly 10
  less with "The wells are low" and back after the rain (`theMoodIsTenLessAndComesBack`).
- `QaDroughtGuard1104d3GameTests` (QA, 2026-11-04, B946 to B948): with no drought, or outside the hall's area in one,
  a pond wets dry farmland to 7 at its first random tick; in the drought a field by a pond at 7 dries a step a tick to
  0 and never climbs back in 40 more; once rain ends the drought, or `drought` is switched off, the pond wets it again;
  a Well Keeper's bucket wets it to 7 for the day, and the next day it dries again. Pitcher crops outside the area, or
  on watered farmland, grow exactly as often as with no drought; torchflower crops are held to about a quarter. The
  last rain day survives a save and load at every count (to day 2^40), a garbled one counts from the day it's read,
  it is written to `data/aliveworkplace_rain.dat` when the world saves, and rain after a restart starts the count again.
- Showcase scenes: `drought`.
