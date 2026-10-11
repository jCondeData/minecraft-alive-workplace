# Drought and the Well Keeper

ROADMAP 32.17. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
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
- **In the hall's area**: wheat, carrots, potatoes, beetroots, melon and pumpkin stems and sweet berry bushes grow at a
  quarter of their pace; farmland dries a step on each of its random ticks, even beside water; every water cauldron
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
- **Stems and berry bushes.** The engine's crop check (a mixin on the crop's random tick) now also runs on
  `StemBlock` and `SweetBerryBushBlock` (`DroughtPlantMixin`), so any disaster's `crop_growth` holds them back too.
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
| `wellKeepers` | on from 1.6 | Villagers at a cauldron can be made Well Keepers with a shovel. Off: no Well Keeper job (the cauldron's tooltip leaves it out), and Well Keepers already hired stand idle. |
| `disasters` | every disaster id → true | `drought: false`: no drought ever starts, and one under way ends at once. |

## Saved data

On a Well Keeper: `well_keeper_water` (1: their bucket is full; absent or 0: empty). The drought itself is saved on
the hall by the engine (`disaster`, `disasterSince`, `disasterEnds`). Which farmland was watered today is kept in
memory: after a restart the keeper waters it again.

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
- Showcase scenes: `drought`.
