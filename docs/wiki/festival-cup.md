# The Festival Cup

Needs Cobblemon. ROADMAP 28.16 to 28.22.

Roadmap items: 28.16, 28.17, 28.18, 28.19, 28.20, 28.21, 28.22

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

### The eight themes (28.22)

Each Cup has a theme, in this order (the host's owner may pick another on the Cup page until sign-up closes). Every
Pokémon battles at the theme's level, and legendary, mythical, Ultra Beast and paradox Pokémon are never allowed. The
fair sells the theme's wares (4 to 16 emeralds each), the feast serves its dish first, the bard plays its disc and the
fireworks and Cup banner take its colours.

| Theme | Who may come | Battle | Wares | Dish | Fireworks | Disc |
|---|---|---|---|---|---|---|
| Blossom Cup | Grass, Bug, Fairy | singles, level 50, bring 3 | Miracle Seed, Silver Powder, Fairy Feather, Leaf Stone, Shiny Stone | Flower Sweet | pink, green | Chirp |
| Little Cup | first-stage Pokémon that can still evolve | singles, level 5, bring 3 | Eviolite, Everstone, Oval Stone, Link Cable, Exp. Candy XS | Casteliacone | yellow, white | Cat |
| Sun Cup | Fire, Water, Electric | doubles, level 50, bring 4 | Charcoal, Mystic Water, Magnet, Fire, Water and Thunder Stones | Lava Cookie | orange, blue | Blocks |
| Workers' Cup | Pokémon that have helped a villager at work on 3 days or more | singles, level 50, bring 3 | Black Belt, Hard Stone, Metal Coat, Soft Sand, Power Weight | Pewter Crunchies | gold, brown | Mall |
| Harvest Cup | Ground, Rock, Normal | singles, level 50, bring 3 | Soft Sand, Hard Stone, Silk Scarf, Sun Stone, Big Root | Leek and Potato Stew | orange, gold | Far |
| Lantern Cup | Ghost, Dark, Psychic; from dusk until dawn | singles, level 50, bring 3 | Spell Tag, Black Glasses, Twisted Spoon, Dusk Stone, Reaper Cloth | Sinister Tea | purple, white | 13 |
| Frost Cup | Ice, Steel, Dragon | singles, level 50, bring 3 | Never-Melt Ice, Metal Coat, Dragon Fang, Ice Stone, Razor Claw | Smoked-Tail Curry | light blue, white | Strad |
| Grand Cup | any Pokémon a village trainer may use; Species and Item Clause | singles, level 100, bring 6 | Ability Capsule, Life Orb, Leftovers, Choice Scarf, Rare Candy | Big Malasada | gold, red | Creator |

Delegates and host trainers field teams that follow the theme; in the Workers' Cup they bring Pokémon of the types that
help villagers at work. A player's team is their party's first eligible Pokémon; the others are named with the reason
they stay home ("it has helped a villager at work on 2 days, and this Cup asks for 3").

## How it works

- **The title** (28.21): the newest champion on a host's roll holds that host's Cup. A player who wins stands for a
  village, and that village holds the Cup. The player gets the purse only.
- **The banner** (28.21): put up when the champion is crowned, if the holder's hall is loaded, or else at its hall's
  next round (the hall's round, once a day as it loads). The same goes for taking it down at the old holder. On the pole
  it hangs as two wall banners where the space is free. Elsewhere it is a standing banner on the hall block, if the two
  blocks above it are free, or on the roof over the hall (up to 24 blocks up). Only an Arena whose nearest hall is the
  holder's counts, so a neighbour's Arena is never used. The colours are the theme's firework colours as the nearest
  dyes: the cup in the first, the field in the second (white, or black under a white cup, if the theme has only one).
- **Themes** (28.22): one file each in `data/aliveworkplace/cups/` (a datapack can add or replace them). Two fields
  are new: `partner_days` (a player's Pokémon must have helped at work on that many days; 0 by default) and
  `worker_types` (villager trainers draw from every type some job's partners are, `Partners.allTypes`; false by
  default). Stage `first` means a first stage that can still evolve.
- **The Workers' counter** (28.22): each day a pastured Pokémon helps a villager while that villager is at work (its
  partner bonus is used for a job step), it gets one more day on a counter in its own Cobblemon data
  (`aliveworkplace_partner_days`, with the last day counted in `aliveworkplace_partner_day`), never twice a day however
  many workers it helps. Cobblemon saves it with the Pokémon, in the pasture, the PC or the party.
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
- 28.22: the Workers' Cup's villager trainers draw from every type some job's partners are (all but Ghost today).
- 28.22: the Lantern Cup's bouts run from dusk (12000) to dawn (24000), when the delegates leave too.
- 28.22: only the Grand Cup has Showdown clauses; the others name none, as the spec lists none.
- 28.21: "on top of its Village Hall" means the hall block, or the roof over it when the hall stands indoors.

## Known limits

- A Cup banner broken by hand goes back up at the holder's next hall round.

## Proof

- GameTests: `CupChampionGameTests` (the banner on the pole and its colours, passing to a hall, the pride, the chronicle
  in every circuit hall, the name lines, the win bonuses, seeding, saving, `festivalCup` off, a far champion's banner
  when it loads), `CupThemesCompatTests` (each theme's team, player filter and fair; the Workers' counter), plus `CupGameTests`, `CupBoutGameTests`, `CupDayGameTests` and `CupMatchGameTests`.
- Showcase scenes: `cup_page`, `cup_bout`, `cup_day`, `cup_match`, `cup_champions`, `cup_themes`.
