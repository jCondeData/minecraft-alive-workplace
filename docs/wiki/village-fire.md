# Village fire and the Firewarden

ROADMAP 32.16. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. Wooden villages can catch fire: from lightning, a
chimney spark on a dry day or a raider's burning arrow. The fire spreads by the game's own rules but only through what
the builders built, a new job, the **Firewarden**, puts it out, and the builders put back what burned.

Roadmap items: 32.16

## What a player sees

- **A fire starts.** In a thunderstorm, lightning that strikes in a village may set the roof of a finished wooden
  build alight. On a dry day a chimney spark may do the same, and a pirate gunner's burning arrow may light the
  wooden block it hits. Players in the village and the hall's owner read "Fire in the Starter Cottage at Oakvale!".
- **It spreads, but only through the builders' work.** The game's own fire burns the roof and walls the builders
  placed. A block a player placed, even one nailed onto a burning house, never catches; a fire with nothing of the
  builders' left to burn goes out once it's old.
- **The Firewarden** (a villager at a grindstone given a water bucket) runs to the nearest fire, day or night, and
  throws a bucket over it: every fire within two blocks goes out in a hiss of steam. They carry two buckets and refill
  them at water or a water cauldron near their grindstone. Above their head: "Firewarden · 1 of 2 buckets full",
  "putting out a fire".
- **The bucket brigade.** From Journeyman a Firewarden rings the village bell first, and two villagers who aren't at
  work within 24 blocks run to the fire with buckets, putting out a fire block every two seconds.
- **With Cobblemon**, Water-type Pokémon pastured near the grindstone help: every two seconds each uses a move on a
  fire in reach, with its own animation and Cobblemon's water splash, and the fire there goes out.
- **When it's out**, players read "The fire in the Starter Cottage at Oakvale is out." and the hall's chronicle says
  "Fire in the Starter Cottage: 18 blocks burned; Rowan the Firewarden put it out." (or "the bucket brigade put it
  out", or "before it burned out").
- **The builders put back what burned** on their next look round the village's buildings, once the fire is out,
  even with `builderRepairs` off (32.16a, see [Sieges](sieges.md)): a fire never destroys a build for good.

## How it works

- **The data.** `data/aliveworkplace/disasters/village_fire.json` is a disaster file (see [Disasters](disasters.md))
  that the daily roll never starts (its `chance` is 0); its `ignition` block holds the three chances: `lightning`
  0.334 (1 in 3), `chimney` 0.02 a day while `chimney_when` holds (`dry_days` 5) or a drought is under way, and
  `burning_arrow` 0.25 (1 in 4). Its `when` (`wood_share` 0.3) keeps fires to wooden villages, and its `factors`
  make each chance 1.25 times likelier at a wood share of 0.5 and again at 0.7 ("likelier the more wood"). Switched off
  under `disasters` (`village_fire: false`), nothing here happens.
- **Lightning** (a mixin on the bolt's strike): a real, not visual-only, bolt in a village while it thunders rolls the
  chance; the roof of the finished build nearest the strike catches, on top of its highest wooden block still
  standing. **Burning arrows** (a mixin where an arrow hits a block): only from a raider of a culture (the gunners of
  the pirates, 32.8), only with the mobGriefing game rule on, and only on a builder's wooden block. **The chimney
  spark** is rolled in the hall's round, once a day.
- **Builders' blocks.** A block counts as the builders' when a finished build's blueprint has a block there and the
  world still holds it (the same rule as rams at gates, `MaterialRules.matches`). In a village the fire's burn and
  its spread to the air beside a block (a mixin on `FireBlock`) only take builders' blocks; with `siegeDamage` off it
  takes none. Outside villages fire is vanilla's.
- **Noticed, never scanned.** A mixin on where fire blocks are placed tells the village it's in; each village with
  fire keeps the list of its fire blocks in memory. Firewardens, the brigade and the Pokémon look only at that list.
  A fire lit on netherrack (a hearth) is left alone.
- **The Firewarden** works in their CORE activity, so at any hour. They answer fires within 48 blocks of their
  grindstone; they throw from 3.5 blocks away, or, for a fire with nowhere near it to stand (a pitched roof's ridge),
  from the open ground below it, up to 12 blocks away and 7 out from under it (never from under a roof); each bucket
  gains them 2 experience plus 1 per fire block it put out.
  An empty bucket is filled at the nearest water source or water cauldron within 16 blocks of the grindstone (a water
  source fills both, a cauldron fills one and is emptied, as a bucket empties it in vanilla).
- **The brigade.** The bell swings and rings, but only the two called answer it (the bell's own ring would send the
  village to hide). They are released when the fire is over.
- **It's over** when no fire block has burned for 2 seconds; the one who put out the most fire blocks is named.

## Switches

| Key | Default | What it does |
|---|---|---|
| `firewardens` | on from 1.0 | Villagers at a grindstone can be made Firewardens with a water bucket. Off: no Firewarden job (the grindstone's tooltip leaves it out), and Firewardens already hired stand idle. |
| `disasters` | every disaster id → true | `village_fire: false`: no lightning, chimney or arrow fires, and the fire rules above are off (fire is vanilla's everywhere). |
| `siegeDamage` | on from 1.0 | Off: a village fire burns nothing at all, the builders' blocks included, and goes out where it started. |

The mobGriefing game rule off keeps raiders' arrows from lighting anything; doFireTick off stops fire spreading
anywhere, as in vanilla.

## Saved data

On a Firewarden: `firewarden_water` (how many of their two buckets are full; absent: both full). The village's fires
(their blocks, what burned, who doused them, the brigade) are kept in memory: after a restart the fires still
burning are noticed again as they spread, and the chronicle counts from then.
Every builder's block the fire burned is also saved, on `damaged` in `aliveworkplace_threats` (32.16a), so the
builders put it back after a restart too.

## Items, blocks, jobs, commands

- The job `aliveworkplace:firewarden`, at a grindstone (shared with the Weaponsmith and the Guard), picked with a water
  bucket. Its outfit: a red leather fire helmet with a brass comb and shield, a long navy coat with brass buttons and a
  straw-yellow band at the hem and cuffs, a leather belt, gloves and a hatchet across the back (and a zombie copy).
- Trades: buckets bought, a water bucket sold (Novice); clay, a campfire (Apprentice); kelp, bricks (Journeyman); a
  potion of fire resistance (Expert); a lightning rod (Master).
- Pokémon partner types: Water.
- No new blocks, items or commands.

## Decisions

- 32.16 (lane b): the village fire is a disaster file, so the config's `disasters` map switches it, but it isn't a
  days-long disaster: its fires are short events kept per village, and the daily roll never starts it (`chance` 0,
  no `fires_a_day`). The design note's `fires_a_day` effect stays for datapacks.
- 32.16 (lane b): "likelier the more wood" is the `when` gate (a wood share of 0.3) plus two `factors` of 1.25.
- 32.16 (lane b): a fire with nothing of the builders' left beside it goes out once it's old (age 15), so a fire
  stopped at a player's wall doesn't smolder there forever.
- 32.16 (lane b): the brigade's bell is a swing and a ring only, so the rest of the village doesn't run and hide.
- 32.16 (lane b): the Firewarden starts with both buckets full.

## Known limits

- Fires are held in memory: one burning across a restart is picked up again only where it spreads.
- The builders wait for the fire to be out within 16 blocks of a build before they repair it.
- The water source search looks within 16 blocks of the grindstone and 6 up or down.
- The Water-type partners' path is tested only in the compat tests (it needs Cobblemon).

## Proof

- `FireGameTests.aFirewardenPutsTheFireOut`: a Firewarden 15 blocks away puts a roof fire out within 400 ticks, gains
  experience, and the chronicle names them.
- `FireGameTests.withoutAFirewardenItStillBurns`: with no Firewarden it still burns at 400 ticks.
- `FireGameTests.theFireNeverSpreadsToAPlayersBlock`: planks the test placed against the burning roof, with fire on
  them, are all still there after 400 ticks.
- `FireGameTests.withSiegeDamageOffNothingBurns`, `FireGameTests.theJobStartsFromAGrindstoneAndAWaterBucket`,
  `FireGameTests.lightningSetsTheRoofAlight`, `FireGameTests.aRaidersBurningArrowLightsTheRoof`,
  `FireGameTests.aJourneymanCallsABucketBrigade`, `FireGameTests.anEmptyFirewardenRefillsAtWater`,
  `FireGameTests.theWaterCarriedSurvivesASave`, `FireGameTests.theBuilderPutsBackWhatBurned`,
  `FireGameTests.switchedOffAFirewardenStandsIdle`, `FireGameTests.everyNewSentenceHasItsText`.
- `PastureCompatTests.pasturedWaterTypesDouseTheFire` (with Cobblemon): a Squirtle by the grindstone puts a fire out.
- Showcase scenes: `fire`.
