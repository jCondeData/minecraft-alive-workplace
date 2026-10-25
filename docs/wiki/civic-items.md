# Civic items: the Cradle, the Village Banner and the Harvest Idol

Three things a player makes for a village with a Village Hall: a Cradle for its children, a Village Banner for its
colors and a Harvest Idol for its fields in autumn. Part of 1.4 (Edicts and civic items): switched on in 1.0 with
every other expansion.

Roadmap items: 30.12, 30.13, 30.14

## What a player sees

**The Cradle.** Craft one from planks, sticks and white wool and set it near a bed in a village with a hall. The
village becomes a nursery village: its children grow up in half the time and one more baby a day may be born (with
the Large Families edict as well, up to three a day). At night a child of the house sleeps in the cradle, seated in it
the way a villager sits in a boat, and gets up at dawn. The hall's beds icon and the last row of the Book of Edicts say
"A Cradle near a bed: children grow up twice as fast, one more baby a day", or that there's none.

**The Village Banner.** Craft any banner with a gold ingot: the Village Banner keeps that banner's design, and its
tooltip lists the base color and every pattern. Right-click the Village Hall with it to make the design the village's
colors (the banner isn't used up, and the chronicle notes it; a second banner replaces the colors). Placed on the
ground or against a wall, it hangs like a banner. The colors show over the front doors of the village's finished
buildings, on its guards' and mercenaries' plain shields, on the hall's trade routes page and the Book of Edicts, and
at festivals: a banner in the colors near the festival square makes the festival better and longer.

**Seasons and the Harvest Idol.** The world has a village year of spring, summer, autumn and winter; autumn is
harvest season, and the hall's festival icon names the season and its day ("Autumn: harvest season, day 3 of 16").
Craft a Harvest Idol from a hay bale, three wheat, a stick and a gold ingot and set it by the fields: in harvest season
the crops within 32 blocks of it grow 25% faster, and golden sparkles rise from it now and then.

## How it works

- **Cradle reach.** A cradle counts when a bed's home point (the bed's head) is within 4 blocks of it, straight-line,
  and the cradle is within the hall's village radius. A cradle 5 blocks from the nearest bed, or just outside the
  village, counts for nothing; break the bed and it stops counting. More cradles don't add more.
- **Growing up.** Each hall round ages the village's children by the round's length again, so a newborn grows up in
  12000 ticks instead of 24000. Grown villagers are never aged.
- **Births.** The usual day between births becomes half a day (12000 ticks) with a cradle and a third of a day (8000)
  with a cradle and Large Families.
- **At night** the hall's round seats the nearest child (within 8 blocks) in each empty cradle; adults are never
  seated. At dawn, or when the cradle is broken, the child gets up and the seat goes. A child seated when the world
  saves is still seated when it loads.
- **Village Banner crafting** is shapeless: exactly one banner and one gold ingot anywhere in the grid (two banners
  make nothing). The banner is used up.
- **Festivals under the banner.** A banner in the colors within 16 blocks of the festival square lifts the festival's
  mood to 20 (from 15) for 3 days (from 2). A banner of another design, or 17 blocks off, doesn't count.
- **The calendar** counts from the world's day on the overworld's clock, the same for every dimension: four seasons of
  `seasonDays` (16) days. Harvest season starts on the first tick of day 2 x `seasonDays` + 1 and ends on the last
  tick of day 3 x `seasonDays`, every year.
- **The idol.** When a crop near an idol takes a random tick in harvest season, one time in four it takes another. That
  is about 25% more growth: measured over 2000 random ticks it was 20 to 30% for wheat and, over ten fixed seeds, about
  24% for carrots, sweet berries and nether wart. Idols don't stack. Their places are kept in a small set per
  dimension, rebuilt as their chunks load, so nothing is scanned: an idol in the Nether helps nether wart in the Nether
  and nothing in the overworld. The crops are the block tag `aliveworkplace:idol_crops`: wheat, carrots, potatoes,
  beetroots, melon and pumpkin stems, sweet berries, cocoa, nether wart, torchflower and pitcher crops, and with
  Cobblemon its berries, apricorns, mints and Hearty Grains.

## Switches

| Key | Default | What it does |
|---|---|---|
| `cradles` | on | A Cradle near a bed makes a nursery village. Off: cradles are furniture (no faster growing, no extra baby, nobody seated). |
| `villageBanners` | on | Village Banners can be crafted and set a village's colors. Off: neither, and no colors show; colors already set stay saved. |
| `harvestIdols` | on | Idols make crops grow faster in harvest season. Off: idols are ornaments (no extra growth, no sparkles). |
| `seasonDays` | 16 | Days in each season (1 to 120). |

## Saved data

- On the Village Hall: `bannerBase` and `bannerPatterns`, the village's colors (a hall saved before banners has none).
- On a villager: `festival_banner_day`, the day of the last festival enjoyed under the banner (none: a plain festival).
- The cradle seat entity (`aliveworkplace:cradle_seat`) saves with its child riding it.
- The calendar's `last_day` (the last day it announced). Idols save nothing of their own: the set is rebuilt from
  their blocks as chunks load.

## Items, blocks, jobs, commands

- Blocks: `aliveworkplace:cradle` (a point of interest, so the hall finds it without scanning),
  `aliveworkplace:harvest_idol`.
- Item: `aliveworkplace:village_banner`.
- Entity: `aliveworkplace:cradle_seat`.
- Tag: `aliveworkplace:idol_crops`.
- No jobs or commands.

## Decisions

- 30.14: the idol uses the village calendar of 22.6 (the season clock already in the mod) rather than a clock of its
  own. 22.6a (owner, 2026-10-04): each season is 16 days, a 64-day year (the spec's 8 was the old default).
- 30.13: the banner isn't used up at the hall, as a Name Tag isn't; builders hang the colors only when a banner of the
  colors' base color is in their chests, and never ask for one.
- 30.13: a shield a player painted is left alone.

## Known limits

- "Within 4 blocks of a bed" and "within 32 blocks of an idol" are straight-line distances; corners of a 4 or 32 block
  box are out of reach.
- The idol's 25% is an average: over a short run one field can be 15% or 30% ahead.
- Seasons follow the overworld's clock; a server that stops the daylight cycle stops the calendar too.

## Proof

- GameTests: `CradleGameTests` (5: half the time to grow up, the extra baby and Large Families, no bed near, a child
  seated at night and saved with its seat, the switch off and the recipe), `VillageBannerGameTests` (7: the recipe,
  the colors and a reload, the hall used by a player, the banner over the door, shields, the routes page, festivals),
  `HarvestIdolGameTests` (6: 25% in autumn, nothing in summer, at 33 blocks or from a second idol, the reload, the
  season on days 1, 17 and 32, crops, sparkles and recipe, the switch off), `QaHallPagesSeasonsGameTests`,
  `SeasonSoakGameTests`.
- `QaCivicItemsGameTests` (10, the QA lane's): a cradle at 4 blocks from a bed counts and at 5 doesn't, nor after the
  bed is broken; a cradle just outside the village doesn't count; a player places and breaks a cradle and the hall's
  beds icon follows; an adult is never seated; the real crafting grid makes a Village Banner and its tooltip lists the
  design; new colors replace the old and the banner hangs on a wall; harvest season's first and last tick for two
  years at 16 and 8 days, in every dimension; a player places and breaks an idol; carrots, sweet berries and nether
  wart grow a quarter faster; an idol in the Nether doesn't reach the overworld.
- Showcase scenes: `cradle`, `village_banner`, `harvest_idol`.
