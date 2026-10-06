# The Festival Cup

Needs Cobblemon. ROADMAP 28.16 to 28.22.

## What a player sees

A village with a Village Hall, a finished Arena and at least Village rank holds its festivals as a Festival Cup. The
villages it has trade routes with make up its circuit, and each one sends its Trainer Leader. Players can sign up on the
hall's Cup page too. On Cup day the far villages' delegates walk in, a fair opens on the Arena's fair lane, the villagers
fill the stands from noon, and bouts are fought at the ring until there's a champion. Fireworks follow.

The champion's village **holds the Cup** until a new champion is crowned at that host. It goes on the roll of champions
(Cup page) and into every circuit village's chronicle, and it is named on its hall's name tag ("Holders of the
Thornholm Cup, won on day 12"), in other halls' trade-route lists and on its Village Map ("Holders of the Thornholm
Cup"). The **Cup banner**, a trophy on a banner in the theme's colours (gold on red for the Grand Cup), flies over the
holder: on both faces of its Arena III's champion's pole, else on top of its Village Hall. It comes down when the title
passes. While they hold the Cup, every villager there has the mood reason "our village holds the Cup" (+5).

## How it works

- **The title** (28.21): the newest champion on a host's roll holds that host's Cup. A player who wins stands for a
  village, and that village holds the Cup. The player gets the purse only.
- **The banner** (28.21): put up when the champion is crowned, if the holder's hall is loaded, or else at its hall's
  next round (the hall's round, once a day as it loads). The same goes for taking it down at the old holder. On the pole
  it hangs as two wall banners where the space is free. Elsewhere it is a standing banner on the hall block, if the two
  blocks above it are free, or on the roof over the hall (up to 24 blocks up). Only an Arena whose nearest hall is the
  holder's counts, so a neighbour's Arena is never used. The colours are the theme's firework colours as the nearest
  dyes: the cup in the first, the field in the second (white, or black under a white cup, if the theme has only one).
- **Pride** (28.21): +5 mood for every villager whose nearest hall holds a Cup. Delegates are guests and don't get it.
- **Trainer XP** (28.21): the winning Leader or host trainer gets two more win bonuses (2 × 3 XP). A trainer who isn't
  there gets them banked on its village's caravan entry, paid when it next loads.
- **Seeding** (28.21): at the host's next Cup the defending champion is seeded first. That's the last champion if they
  entered, else the holder village's villager entrant.

## Switches

- `festivalCup` (true): Cups at all. Off: nobody holds a Cup, banners come down at each holder's next round, no pride.
- `cupEveryFestivals` (1): every how many festivals is a Cup.

## Saved data

`aliveworkplace_cups` (per dimension):
- each champion on a host's roll now also keeps the champion's id (`id`, none in older saves, so seeding falls back to
  the village's entrant);
- `banners`: where each holder's Cup banners were put up (none by default).

## Items, blocks, jobs, commands

- Banner pattern `aliveworkplace:cup` (data-driven, `data/aliveworkplace/banner_pattern/cup.json`). Its texture is drawn
  in `tools/textures/art/banners.py`. Its names are "Red Cup", "Yellow Cup" and so on. It isn't on the loom: the banner
  belongs to the village.

## Decisions

- 28.21: the title passes only when a new champion is crowned at that host. A Cup that isn't held (too few entrants,
  called off) leaves the holder in place.
- 28.21: with `festivalCup` off nobody holds a Cup (banners down, no pride, no lines). They come back when it's on again.
- 28.21: "on top of its Village Hall" means the hall block, or the roof over it when the hall stands indoors.

## Known limits

- A Cup banner broken by hand goes back up at the holder's next hall round.

## Proof

- GameTests: `CupChampionGameTests` (the banner on the pole and its colours, passing to a hall, the pride, the chronicle
  in every circuit hall, the name lines, the win bonuses, seeding, saving, `festivalCup` off, a far champion's banner
  when it loads), plus `CupGameTests`, `CupBoutGameTests`, `CupDayGameTests` and `CupMatchGameTests`.
- Showcase scenes: `cup_page`, `cup_bout`, `cup_day`, `cup_match`, `cup_champions`.
