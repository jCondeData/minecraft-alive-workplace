# The Festival Cup

Needs Cobblemon. ROADMAP 28.16 to 28.22.

Roadmap items: 28.16, 28.17, 28.18, 28.19, 28.20, 28.21, 28.22

## What a player sees

A village with a Village Hall, a finished Arena and at least Village rank holds its festivals as a Festival Cup (every
festival by default, or every second or third one: `cupEveryFestivals`). The villages it has trade routes with make up
its circuit, and each one sends its Trainer Leader (or, without one, its best Trainer). Players can sign up on the
hall's Cup page too, and the players in the circuit's villages are told in chat when sign-up opens and on the evening
before the Cup. On Cup day the far villages' delegates walk in, a fair opens on the Arena's fair lane, the villagers
fill the stands from noon, and bouts are fought at the ring until there's a champion. Fireworks follow.

The champion's village **holds the Cup** until a new champion is crowned at that host. It goes on the roll of champions
(Cup page) and into every circuit village's chronicle, and it is named on its hall's name tag ("Holders of the
Thornholm Cup, won on day 12"), in other halls' trade-route lists and on its Village Map ("Holders of the Thornholm
Cup"). The **Cup banner**, a trophy on a banner in the theme's colors (gold on red for the Grand Cup), flies over the
holder: on both faces of its Arena III's champion's pole, else on top of its Village Hall. It comes down when the title
passes. While they hold the Cup, every villager there has the mood reason "our village holds the Cup" (+5).

### The Arena (28.16)

Three tiers, each an upgrade of the one before, in the Blueprint Table only when Cobblemon is installed; an Expert
Trainer Leader sells the first one's blueprint (no Leader below Expert does).

- **Arena** (25 × 19): a 15 × 9 battle ring of packed mud inside a white line, with a half-way line and a center
  circle; a trainer's box on a dais at each end with a lamp post; benches for 12 along the back; two banner poles and
  a notice board at the front.
- **Arena II** (25 × 29): stands on both long sides (30 seats), a gate arch, lanterns round the ring, and a fair lane
  with two striped kiosks behind the back stand.
- **Arena III** (25 × 29): a covered grandstand on stone terraces (52 seats), a trainers' room at each end with a
  Healing Machine, and the champion's pole before the gate.

The hall's "What next?" suggests an Arena to a Cobblemon village of Village rank or more that has a grown-up Trainer
Leader and no Arena yet (not before the 1.2 expansion is switched on).

### Bouts between villagers (28.18)

A bout between two village trainers is an exhibition at the ring. Each trainer stands in their box and sends out one
Pokémon at a time beside the ring (real Pokémon, never catchable, removed if the game is saved with them out); the
faster one moves first and they take turns. Damage follows a small formula (the theme's level, the move's power, attack
against defense, the same-type bonus and type effectiveness), from `data/aliveworkplace/type_chart.json`: the games'
18-type chart, every match-up as in the games since Fairy came in, and a Pokémon with two types takes both multipliers
(Ice on Dragonite is 4x, Ground on Charizard nothing). A move the chart says can't touch the target does no damage; a
Pokémon with no attacking move uses Struggle. A fainted Pokémon is called back and the next comes out, one on one even
in a doubles Cup. A bout lasts 90 seconds at most, then the side with more of its team's health left wins, whichever
box it stands in; a bout neither side can win by its moves (a Normal-moved Snorlax against a Ghost-moved Gengar) still
ends at 90 seconds with a winner. Each bout is played from its own seed, so a bout saved halfway carries on exactly the
same after a restart.

### The eight themes (28.22)

Each Cup has a theme, in this order (the host's owner may pick another on the Cup page until sign-up closes). Every
Pokémon battles at the theme's level, and legendary, mythical, Ultra Beast and paradox Pokémon are never allowed. The
fair sells the theme's wares (4 to 16 emeralds each), the feast serves its dish first, the bard plays its disc and the
fireworks and Cup banner take its colors.

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
they stay home ("it has helped a villager at work on 2 days, and this Cup asks for 3"). In the Grand Cup, Species and
Item Clause hold for every team: a player's second of one species ("Species Clause: one Gastly is going already") or a
second Pokémon holding one item ("Item Clause: a Pokémon already going holds Leftovers") stays home, and a trainer's
team never has two of one species or two holding one item.

## How it works

- **The host** (28.17): `festivalCup` on, Cobblemon installed, a Village Hall, Village rank or more and a finished
  Arena (any tier, any rotation) within the hall's reach. The Cup page says which of these is missing.
- **The circuit** (28.17): the host and every village with a hall that has a trade route with it, either way, seven
  villages at most, nearest first. A village beyond the seventh, or with no route, is on no circuit and can't sign up.
- **The calendar** (28.17): a Cup is held on the host's own festival day, on every `cupEveryFestivals`th festival
  counted from that day. The next Cup is today's until its bouts end (midnight; dawn for a theme that runs all night),
  then the next one. Sign-up opens when the festival before ends (13000, dusk under the "stay in" policy; before then Sign up
  only says when it opens) and closes when the Cup's day dawns: then the entrants and the bracket are drawn and kept. The players in the circuit's villages
  are told when it opens and at dusk the evening before, once each.
- **Entrants** (28.17): each circuit village's Trainer Leader, else its best Trainer, else nobody. A far village that
  isn't loaded sends the one its hall's last round wrote on the trade-route list (name, tier and XP). Players sign up
  for the village whose hall they're at: one they own, one whose owner made them a friend, or one nobody owns, two
  players a village (not two a Cup). Up to two of the host's other Trainers fill the bracket.
- **The bracket** (28.17): 4, 8 for a Town host, 16 for a City host; the smallest of these that fits everyone. A full
  bracket leaves the lowest seeds out (17 entrants in a City's Cup: the 17th is out). Seeds: the villages' Leaders and
  Trainers by tier (a Leader first at a tier), then the players in sign-up order, then the host's Trainers. Byes go to
  the top seeds, and seeds 1 and 2 are in different halves. Fewer than 4 entrants: no Cup, a plain festival, and the
  page says so.
- **The theme** (28.17): the next one in order after the last Cup's. The host's owner (anyone while nobody owns the
  hall) may click through to another on the Cup page until sign-up closes, not after.
- **Cup day** (28.19): the far villages' delegates arrive at 1000 (not before), one per far entrant, from the side
  their home lies; they never keep a job site or a bed, are saved with the villager, and leave at dawn out of sight (at
  once if `festivalCup` is switched off that day). The fair is a market day's traders and two more. The stands open at
  noon (6000; not at 5999) and close at the theme's end time.
- **Players' bouts** (28.20): the call gives two minutes (2,400 ticks) to click [I'm ready] inside the Arena; a tick
  before, nothing is decided. A player who isn't ready loses by walkover, and the winner of a walkover is paid like any
  win: 100,000 a bout and 600,000 for the final (the bout and 500,000 more), half again (150,000 and 900,000) only at a
  City host (a Town pays the plain purse); without CobbleDollars in emeralds at `dollarsPerEmerald`. A player with no
  eligible Pokémon loses the bout without a battle and isn't paid.
- **The title** (28.21): the newest champion on a host's roll holds that host's Cup. A player who wins stands for a
  village, and that village holds the Cup. The player gets the purse only.
- **The banner** (28.21): put up when the champion is crowned, if the holder's hall is loaded, or else at its hall's
  next round (the hall's round, once a day as it loads). The same goes for taking it down at the old holder. On the pole
  it hangs as two wall banners where the space is free. Elsewhere it is a standing banner on the hall block, if the two
  blocks above it are free, or on the roof over the hall (up to 24 blocks up). Only an Arena whose nearest hall is the
  holder's counts, so a neighbor's Arena is never used. The colors are the theme's firework colors as the dyes whose
  own color is nearest (the Workers' Cup flies a gold cup on brown): the cup in the first, the field in the second (white, or black under a white cup, if the theme has only one).
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

| Key | Default | What it does |
|---|---|---|
| `festivalCup` | true | Cups at all. Off: no Cup is set, nobody can sign up or pick a theme, the Cup page says it's switched off; nobody holds a Cup, banners come down at each holder's next round, no pride. |
| `cupEveryFestivals` | 1 | Every how many festivals is a Cup (2: every second one). |

## Saved data

`aliveworkplace_cups` (per dimension, by host; 28.17): the theme, the day, whether the owner picked the theme, the
last theme, the signed-up players, the drawn entrants and bracket, the results, the roll of champions, and the days
the players were told. A Cup saved with none of these loads with no theme, no day and sign-up open.

The trade-route list (`Caravans`) keeps each village's Trainer Leader (or best Trainer): id, name, tier, XP and
whether it's a Leader (none by default).

Later additions:
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
- 28.21: a village that holds several hosts' Cups flies one banner and keeps it, with its
  pride, until it holds none. A block on the hall is never replaced: the banner goes on the highest block over it.

## Known limits

- A Cup banner broken by hand goes back up at the holder's next hall round.
- Found by QA (2026-10-25), not fixed yet (B491): the Sign up button works before sign-up has opened, while the card
  still says "Sign-up opens after the festival on day N".
- Operators picking the theme of an owned host isn't tested (the test server gives operators no permission level).

## Proof

- GameTests: `CupChampionGameTests` (the banner on the pole and its colors, passing to a hall, the pride, the chronicle
  in every circuit hall, the name lines, the win bonuses, seeding, saving, `festivalCup` off, a far champion's banner
  when it loads), `CupThemesCompatTests` (each theme's team, player filter and fair; the Workers' counter; the Grand Cup's clauses and their reasons), plus `CupGameTests`, `CupBoutGameTests`, `CupDayGameTests` and `CupMatchGameTests`; QA: `QaCupChampionGameTests` (a player champion's village flies the banner and the player gets no trainer XP, the chronicle line word for word, a village holding two Cups, a Cup that isn't held keeping the holder, a block on the hall never replaced, an old holder away when the title passes), `QaCupThemesGameTests` (the eight shipped files against the spec: rules, wares, dish, vanilla disc, banner colors, Cup page rules), `QaCupThemesCompatTests` (each theme's types from both sides with two-type Pokémon, the Little Cup's stage, the Workers' reasons at 0 and 1 days, the Grand Cup's bans and their reasons), QA: `QaCupDayGameTests` (the day's numbers from both sides, the walkover purse, a City's purse, the players' sentences) and `QaCupMatchCompatTests` (no eligible Pokémon, a players' bout's purse), `QaCupWatchCompatTests` ([Watch] on a players' bout), `QaCupClausesCompatTests` (the Grand Cup's clauses on delegates' and players' teams) and `QaCupBannerColoursBugGameTests` (the Workers' Cup banner gold on brown).
- The Arena: `ArenaGameTests` (a builder finishes each tier, `Arenas.find` in every rotation and mirrored, "What
  next?"), `ArenaCompatTests` (the Expert Trainer Leader's trade, Arena III's Healing Machines),
  `QaArenaGameTests.everyArenaHasAFifteenByNineRingAndTheFirstFits`.
- QA (28.17): `QaCupGameTests` (sign-up opening a tick either side of the festival's end and who's told, the next Cup
  rolling over when the bouts end, sign-up from every circuit village but an eighth, the bracket's limits and one over,
  `festivalCup` off, the theme only until sign-up closes, the bracket line with 3 byes, and with one: "A bracket of 8,
  with 1 bye for the top seed").
- QA (28.18): `QaCupBoutGameTests` (all 324 match-ups of the chart against the games', two-type multipliers; on time the
  side with more health wins from either box and never past 90 seconds; a bout nobody can hurt in ends on time; a
  moveless Pokémon struggles).
- Showcase scenes: `cup_page`, `cup_bout`, `cup_day`, `cup_match`, `cup_champions`, `cup_themes`.
