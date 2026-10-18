# Raid dens, Megas and Cobblecuisine

Cobblemon depth brings three of the pack's Pokémon mods into village life: Cobblemon Raid Dens (the village knows the
dens around it, guards watch them and its trainers challenge them), Mega Showdown (more village trainers Mega Evolve,
and the village makes Mega Stones and Key Stones) and Cobblecuisine (the kitchens cook its dishes and farmers grow its
beans). Part of 2.2, behind its gate (`Expansions.M38`). Built so far: the plan, the gate, the test mods, the dens
the village knows (38.2), Den Wardens (38.3), the village's den challenges (38.4), more Mega trainers with their
bracelets (38.5), the Jeweller's Mega Stones and Key Stones with the realm's Mega Bracelet (38.6), the Mega
Shrine with its festival show (38.7), Cobblecuisine on the Chef's, Camp Cook's and Innkeeper's menus (38.8), bean farms (38.9) and the partners' treats and dish luxuries (38.10); the full plan is the design note, docs/design/M38.md.

Roadmap items: 38.1, 38.2, 38.3, 38.4, 38.5, 38.6, 38.6a, 38.7, 38.8, 38.9, 38.10, 38.11, B190, B191

## What a player sees

With Cobblemon Raid Dens installed (and once 2.2 ships), the hall's Defense page has a row of the raid dens within 160
blocks of the hall, nearest first: "Raid den, tier 3, Fire, active" (the icon glows), or "resting" once it has been
beaten and hasn't come back yet, with where it is and the day it was found. With none known it says "No raid den
known". The village map marks each den on its square with a magenta banner named the same way, and its legend counts
the raid dens. A den shows up once someone has been near enough for its chunk to load, the moment a crystal is set
down in a loaded chunk, and when a hall is set down by dens that were there first; a broken den drops off the list.

**Den Wardens (38.3).** Under each den on the Defense page, **Post a warden** sends the village's nearest free guard
there. By day the warden walks to the den and stands watch beside it, nudging wild Pokémon off the crystal; at dusk
they walk back to their post and their evening goes on as usual; next morning they go back. The slot then reads
"Warden: <name>", and a click recalls them to normal duty. When a watched den glows again after being beaten, the
chronicle says so: "The Fire raid den (40 blocks north) glows again". A player who joins a raid (or wins a
tier) within 16 blocks of a watched den gets the warden's cheer: one Attack, Defense or Heal Cheer, once a raid.

**Den challenges (38.4).** Every 3 days (`denChallengeDays`), by day, a village with a Trainer Leader or a Master
sends its best one (the highest rank; one who lost lately stays home) against an active den the village knows: up to
tier 3 for a Leader or a Master (tier 4 and up are for players: owner, 2026-10-18), the highest tier first, then the nearest. A den with a player within 8 blocks
is theirs, and nobody is sent to it. The challenge is settled at once, like a Stadium bout: 45% at one rank over the
den's tier, 15% more or less for each rank above or below (never under 10% or over 90%). Players in the village or
within 32 blocks of the den are told as the trainer sets off and how it ended, so they can walk over and watch the den
go dark. A win rolls the den's own tier loot (`cobblemonraiddens:raid/tier/<tier>`, as a Raid Pouch of that tier
would) into the village store, turns the den inactive (`is_active` false; Raid Dens' own reset brings it back,
`can_reset` untouched) and writes the chronicle: "<the trainer> beat the tier 3 Fire raid den and brought home 5
things". A loss leaves the den glowing, sends the trainer home tired for a day and writes "... lost to the tier
4 Fire raid den". Each try counts as a battle for the trainer and earns experience (more for a win).

**More Mega trainers (38.5).** With Mega Showdown installed, Masters still carry a Mega Stone, and now so do: a Trainer
Leader whose village (the nearest Village Hall) is a City, the two trainers of a Festival Cup's final (on the Cup's
day, until the final is decided; a delegate standing in for one too), and the Stadium Day's visiting trainers. One
Pokémon of their team holds its own Mega Stone (the first that has one; otherwise the last is swapped for one that
does, the same level and training), and their battle AI Mega Evolves it the first turn it can, at any tier. A Town's
Leader (or a Hamlet's or a Village's) holds none. In a Cup final the Mega Pokémon must fit the theme (types, stage,
banned labels); with no Mega Evolution fitting it (the Little Cup), the team stays as it is. Every trainer who can
Mega Evolve wears a **Mega Bracelet** over their outfit: a gold band round the left wrist with the rainbow Key Stone
set in it (`textures/entity/villager/mega_bracelet.png`, drawn by `tools/textures/art/megas.py`). Without Mega
Showdown there are no extra stones, no bracelets and nothing is sent.

**Mega Stones and Key Stones from the village (38.6).** With Mega Showdown, a Journeyman Jeweller (level 3) cuts, at the
stonecutter, only from makings in the **village store** (the hall's storehouses and kitchens, or without a hall the
storehouses near the stonecutter; 38.6a, the owner: never from their own chest or other workers' chests, and never from
nothing):

| Makings | Makes | Level | Kept in the store |
|---|---|---|---|
| Key Stone Ore, 2 gold ingots, 4 emeralds | a Key Stone | 3 | 2 |
| a Mega Stone Crystal (the block, silk-touched), 2 gold ingots, 4 emeralds | a Mega Stone the village's trainers use | 3 | 1 of each |
| a Mega Stone (what a Mega Stone Crystal drops), 2 gold ingots, 4 emeralds | the same | 3 | 1 of each |
| 4 Mega meteorite ores (any mix, silk-touched), 2 gold ingots, 4 emeralds | the same | 4 | 1 of each |

"A Mega Stone the village's trainers use": the Jeweller looks at the trainers of their village (round the nearest
Village Hall, or within 48 blocks without one; visitors and Cup delegates left out), nearest first, and at the Pokémon
on each one's home team, and cuts the Mega Stone of the first such Pokémon whose stone the store doesn't hold yet
(both Charizardites for a Charizard). A village whose trainers have no Pokémon with a Mega Evolution gets no stones;
the Jeweller never floods the store with all 46. Makings in the store are used up by the cut. While the store lacks
them the Jeweller cuts nothing and the line above their head says what the Storehouse should hold ("needs 4 × Emerald
in the Storehouse", for the stone closest to done); these aren't posted on the requests board, since a hand-over there
goes to the Jeweller's own chest, which doesn't count.

A **Miner** drops off what they dig of Key Stone ore (a Key Stone, or the ore itself with a silk-touch pickaxe), Mega
Stone Crystals (a Mega Stone, or the crystal) and Mega meteorite ores (silk-touched; without silk touch they drop
Cobblemon's evolution stones, which stay in the Miner's chest) in the **village store** (the hall's storehouses and
kitchens) for the Jeweller, instead of their own chest; what the store has no room for goes in their chest as before.

A player who beats the **Trainer Leader of every village of a realm** that has one gets a **Mega Bracelet** from the
capital's hall: it goes into their inventory (or drops at their feet when it's full) with "You've beaten every Trainer
Leader of the Barony of Oakmere! The hall of Oakmere gives you a Mega Bracelet." Before that, each new village's Leader
beaten tells them "Trainer Leaders of the Barony of Oakmere beaten: 1 of 3. ...". Once per player per realm. A Leader
beaten counts once and for good; a village counts as having a Leader when its hall's last round saw one (so a village
whose Leader left drops out of the count).

With Mega Showdown, the Blueprint Table lists the **Mega Shrine** among the decorations (38.7): Mega Showdown's pedestal
on a two-step dais of Mega meteorite bricks, with chiseled meteorite posts crowned with amethyst and soul lanterns (7 by
6 by 7, beauty 2). Without Mega Showdown it isn't listed (its blocks don't exist). On a festival day, for its first
hour and a half (from the festival's start, 1,500 ticks), a village with a finished shrine holds its show there: the
villagers off work gather in front of the shrine instead of at the square, and a village trainer (one who can Mega
Evolve first, else the nearest) climbs to the pedestal and Mega Evolves their ace: a flash, a burst of light, a beacon
chord, and "<trainer> Mega Evolves their ace at the Mega Shrine, and the village cheers!" in chat for the players near.
Every villager within 12 blocks is the happier for it: +3 mood ("watched a Mega Evolution at the shrine") as long as a
festival's good mood lasts (2 days). Once a festival; then the crowd goes back to the square for the dancing and
fireworks. The show is part of the festival itself: it needs the village's festival (six villagers or more, on its
festival day or one called), and it begins at the festival's first tick after the hall's round has seen it start.

**Cobblecuisine on the menus (38.8).** With Cobblecuisine, the Chef cooks its dishes after the plain menu, each from
its own crafting recipe with the makings from the chests by the stove, the porters' and the butcher's spare (as he
cooks everything), keeping 16 of each: a Novice the salads (Mixed Vegetable, Spore Mushroom, Pumpkin Pie) and
sandwiches (Sweet Jam, Salty Egg, Salty Veggie), an Apprentice also three more salads, two sandwiches and the drinks
(Bouncy Brionne Soda, Teddiursa Iced Coffee, Fruit Punch), a Journeyman six curries, an Expert five cakes, and a Master
the Jigglypuff Fruity Flan and Miltank Mix au Lait (27 dishes). The sandwiches' roasted berries he roasts himself
(the campfire's recipes are now among a cook's). Every dish is a meal from the store whatever its effects, and the
diet counts it as its group: a salad, a sandwich and a curry are three kinds (a varied diet), three salads one. The
Camp Cook's menu has three curries (Beanburger, Mild Honey, Spicy Potato): the pot has no recipe for them, so she
cooks them by their own recipe over the fire, makings out of the chests and the curry into her bag in one go, and
takes them to the chests by the pot (16 kept). The Innkeeper serves each guest one of the three drinks once a stay,
from the chests by her counter or else the village store; it counts in the guest's diet, and her status says "served
a guest Bouncy Brionne Soda".

A farmer with Cobblecuisine grows beans for those dishes (38.9): while the store (her chests and the village's stashes)
holds fewer than 16 of a bean colour a dish on the menu needs, she takes bean seeds from the chests and sows her
field's empty farmland with them, cuts a bean plant only once it's fully grown, and takes the beans to the store with
the rest of the harvest. Which colour comes up is Cobblecuisine's own loot (one of the seven at random), so a short
colour brings a field of mixed beans. With 16 of every wanted colour, or no bean dish on the menu, she sows none and
the seeds stay in the chest; the ripe beans already growing are still cut, but not sown again.

**Treats for partners and dish luxuries (38.10).** A partner Pokémon (one in a Pasture Block helping a villager at
work) gets one treat a day from the village store: when its worker's day is counted at work, it eats a Poké Puff (any
of the five) or a shake (Low, Medium or High, any colour) out of the store (the nearest hall's kitchens and
storehouses, or without a hall the storehouses near the workstation), with hearts and 6 more friendship, the same as
the Rancher's berry treat. Once a day however many workers it helps; with nothing in the store, no treat. Two dishes
become luxuries, wanted only with Cobblecuisine: a **Deluxe Shake** (any of the six) for Burghers every 4 days, and
the **Clodsire Eclair** for Nobles every 8. They are wants, not needs, like the Amethyst Ring: a household that has
had one lately pays its 10% more tax, and the hall's Classes page lists them. As with every luxury, a household takes
one from the store at dawn when it is due, and a household also takes the class above's (an Artisan
may take a Deluxe Shake, a Burgher an éclair). Without Cobblecuisine the two luxury files are skipped quietly and the Classes page doesn't list them.

**Campfire Pot recipes (38.11).** With Cobblemon (Cobblecuisine not needed), the Chef's menu also has every Campfire
Pot recipe's dish: the results of every recipe of the pot's two recipe types (`cobblemon:cooking_pot` and
`cobblemon:cooking_pot_shapeless`), read from the loaded recipes by type, so another mod's or a data pack's pot recipes
come too. With Cobblemon 1.7.3 and Mega Showdown that is about 90 dishes: the EXP Candies (XS to XL), the IV and EV candies (Health,
Mighty, Tough, Smart, Courage, Quick and their opposites), the medicines (Potions, Revives, the status heals, X items),
the sweets, mochi and teas. A result with its own components (a potion's contents) is left out. He cooks them like his
other dishes, at his Kitchen Stove (no pot needed there), only when the chests by the stove and the porters' and
butcher's spare hold every making, and keeps 16 of each in the chests by the stove, which are the village store's
kitchen: players take them from there and the partners' treats and the hall read the same store. With the makings
short (sculk and no honeycomb for an EXP Candy XS) nothing is cooked or taken. Switch: `cuisineDishes` (behind M38's
gate); off, the pot dishes leave the menu except the ones it always had (Poké Bait, Snacks, Cakes, candied apples,
Aprijuice). Scene `campfire_pot`.

## How it works

Every piece reads the three mods by their ids, tags, data files and advancements only, each behind a check that the mod
is loaded; any Cobblemon class stays in `compat/cobblemon/`. Without a mod, its part of the expansion doesn't exist and
nothing else changes.

The gate: `Expansions.M38` is false until the release that finishes the milestone, so its six switches (denWardens,
denChallenges, denChallengeDays, villageMegas, cuisineDishes, beanFarms) stay off and hidden for players
whatever a config file says. GameTests open the gate. Each switch comes into the config with the item that builds it.

The compat test runtime now loads Cobblemon Raid Dens 0.11.3 (with GeckoLib 4.9.2) and Cobblecuisine 2.0.1, the pack's
versions, next to Mega Showdown 1.8.4, which was already there; so does the screenshot harness's Pokémon set
(`-Pcobblemon=true`). The Cobblemon 1.8 runs swap Raid Dens to 0.13.2 and keep Cobblecuisine 2.0.1, which has no build
for 1.8.

## How the dishes are listed (38.8)

`craft/CuisineDishes` (core) reads `data/<ns>/cuisine_dishes/*.json` (merged, the first file of a dish wins): each
`{"requires": "cobblecuisine", "dishes": [{"id", "level" (1 to 5, default 1), "group", "inn"}]}`. A file whose mod
isn't installed is skipped, and so is a dish whose item doesn't exist; a bad entry skips its file with a warning in
the log. The shipped lists are `salads`, `sandwiches`, `drinks`, `curries` and `cakes`. `Chefs.menu(level, chef)` is
the plain menu and then the dishes up to the chef's level; the recipes are found through the recipe manager by their
result (`Crafting.Kind.KITCHEN`, which now also holds the campfire's recipes, after the smoker's and the crafting
table's). `Diet` counts a dish by `"group:" + group` instead of its id, `VillageNeeds.isMeal` takes every dish, the
Camp Cook's curries are ordinary `camp_menu` files (`cobblecuisine_*.json`, skipped while `cuisineDishes` is off), and
`inn/InnDrinks` serves the `inn` dishes. A guest has had their drink while one of the drinks is among their last five
meals (`recent_meals`), so nothing new is saved.

## How the bracelet reaches the players (38.5)

`trainer/MegaBracelets` (core) decides who can Mega Evolve; `compat/cobblemon` builds the team with the stone from it
(`CobblemonTrainers.teamOf`). Every 40 ticks of a trainer's life the server checks again and, when the answer changed,
sends a small packet (`aliveworkplace:mega_bracelet`: the entity id and yes or no) to every player who sees them, and
again whenever a player starts seeing one who wears it. The client draws `MegaBraceletLayer` over the villager's
outfit, class and Legend looks. A village that becomes a City (or drops back), a Cup's final and a visitor's day so
show within two seconds. Nothing is saved: it all follows from the village's rank, the Cup and the visitor records.

## How the Mega Stones are chosen (38.6)

The luxury recipe format grew three optional parts, so the stones stay data: an output named by a tag and a chooser
(`"output": {"tag": "#mega_showdown:mega_stone", "for": "village_trainers"}`), `keep` (how many of each good the store
is kept in; 8 by default) and `when` (a named switch: `village_megas` is `villageMegas` with Jewellers on). The files
`data/aliveworkplace/luxury_recipes/mega_*.json` load only with Cobblemon and Mega Showdown (Fabric load conditions).
The chooser `village_trainers` (core `jeweller/MegaStones`) asks Cobblemon for each trainer's team
(`CobblemonMegas.teamStones`: the same seeded team they field at home) and remembers the answer per trainer until
their rank or Mega changes. The Miner's finds are the item tag `#aliveworkplace:jeweller_finds` (with
`#aliveworkplace:mega_meteorite_ores`), every entry optional, so a pack can add its own.

The bracelet (core `trainer/LeaderBracelets`): each hall's round notes whether its village has a Trainer Leader of its
own; a player's win against a Leader (Cobblemon's battle end, the same call that pays the prize) marks the Leader's
village (the nearest hall) for that player, then checks the realm the village is in.

## How the shrine's show runs (38.7)

`trainer/MegaShrines` (core) reads the shrine by its blueprint id from the finished builds near the hall
(`BuildSiteManager.finishedNear`), and Mega Showdown's pedestal only by its id (`mega_showdown:pedestal`):
`BlueprintLibrary.megaOk` lists the shrine only while that block exists and `villageMegas` is on. `Festivals.tick`
asks it first each festival tick; in the show's hours it takes the crowd (to 4 blocks in front of the dais) and walks
the trainer to the step before the pedestal; there, the show is held once a day per village (kept in memory: a restart
mid-show may hold it again, and the mood is the same). The Mega Evolution is shown as light and sound at the pedestal,
not by a Pokémon sent out.

## How dens are found (38.2)

When a chunk loads, its block entities are looked at (never an area of blocks); a Raid Dens crystal among them is
looked at on the level's next tick and recorded if a Village Hall is within 160 blocks. Its tier, type and whether it's
active are read from its block state by property name (`raid_tier`, `raid_type`, `is_active`), so no Raid Dens class
is needed. Every 10 seconds, and whenever the Defense page or the map asks, the dens in loaded chunks are read again:
one that changed is updated, one that's gone is dropped. A den in an unloaded chunk keeps what was last seen.

## Switches

| Key | Default | What it does |
|---|---|---|
| `denWardens` | on (once 2.2 ships) | Guards can be posted to raid dens. Off: no new wardens, and those posted go back to normal duty. |
| `denChallenges` | on (once 2.2 ships) | Village trainers challenge active dens. Off: no challenges. |
| `denChallengeDays` | 3 (1 to 30) | Days between a village's challenges. |
| `villageMegas` | on (once 2.2 ships) | More Mega trainers and their bracelets (38.5), the Jeweller's Mega Stones and Key Stones, the Miner's finds to the store and the realm's Mega Bracelet (38.6), and the Mega Shrine at the Table with its festival show (38.7). Off: only Masters Mega Evolve, as before, nobody wears a bracelet, the Mega recipes don't run, the Miner keeps everything and no bracelet is given. With Jewelers off (M34) the Mega recipes don't run either, and the Miner keeps everything. |

| `cuisineDishes` | on (once 2.2 ships) | Cobblecuisine's dishes on the Chef's menu by his level, counted by food group for the diet, the Camp Cook's curries and the Innkeeper's drinks (38.8), the partners' daily treat and the Deluxe Shake and Clodsire Eclair luxuries (38.10). Off: the menus and the diet are as before, no treats, and the two luxuries are neither wanted nor taken. |
| `beanFarms` | on (once 2.2 ships) | Farmers sow Cobblecuisine's beans when the store is short of a colour a menu dish needs, and cut them when ripe (38.9). Off: farmers never sow beans (ripe ones on a field are still cut, as any crop). |

## Saved data

Each dimension saves its known dens (`aliveworkplace_raid_dens`): a list of `pos`, `tier` (1 to 7), `type`,
`active` and `firstSeen` (the day). An older save has none, and dens are found again as their chunks load. The wardens are saved per dimension too
(`aliveworkplace_den_wardens`): each `guard` (their id) with their `den` and the players `cheered` this raid. An older
save has no wardens. The den challenges are saved per dimension (`aliveworkplace_den_challenges`): each hall's
`lastDenChallenge` (the day) and each tired trainer's `tiredUntil` (game time). An older save has none: every village
may challenge at once. The Mega Bracelets are saved on the overworld (`aliveworkplace_leader_bracelets`): the villages
known to have a Trainer Leader (`leaderHalls`) and, per player, the villages whose Leader they beat (`beaten`) and the
realms whose bracelet they had (`megaBracelet`). An older save has none: nobody has a bracelet yet. A villager who
watched a Mega Shrine's show keeps the day on them (`mega_show_day`, 38.7); an older save has none. Cobblecuisine's
dishes (38.8) save nothing new: the dishes go in chests and the meals in the villagers' `recent_meals`. Bean farms
(38.9) save nothing new either: whether beans are wanted is worked out again at each look over the field. A partner's treat day (38.10) is kept in the Pokémon's own saved data
(`aliveworkplace_treat_day` in its persistent data, which Cobblemon saves with it); a Pokémon from an older save has
none and gets its treat the first day it helps. The dish luxuries are noted in the villagers' `luxuries_had` like any
luxury. The planned fields
of later items are listed in the design note.

## Items, blocks, jobs, commands

None of our own yet; 38.6 adds four Jeweller recipes (`mega_keystone`, `mega_stone`, `mega_stone_cut`,
`mega_stone_meteorite`) and two item tags (`jeweller_finds`, `mega_meteorite_ores`); 38.7 adds the decoration
blueprint `aliveworkplace:mega_shrine` (tools/blueprints/decor.py, `mega_shrine`); 38.8 adds the dish lists
`data/aliveworkplace/cuisine_dishes/` and three `camp_menu` files; 38.10 adds the luxuries `deluxe_shake` and `clodsire_eclair` (with
`fabric:load_conditions` on Cobblecuisine and `"switch": "cuisineDishes"`, a new optional luxury field) and the item
tags `partner_treats` and `deluxe_shakes` (optional entries). The ids the milestone reads from the three mods (the den block `cobblemonraiddens:raid_crystal_block`
and its states, the cheers, `mega_showdown:keystone`, `cobblecuisine:bean_crop`, the beans and the dishes) are listed in
the design note and checked by `CobblemonDepthCompatTests`.

## Decisions

- Cobblecuisine has seven bean colours, not six as the roadmap said; the plan uses all seven (38.1).
- Bean farms have their own switch, `beanFarms`, as the design note's switch table planned, rather than sharing
  `cuisineDishes`: a server can keep the dishes and grow its beans by hand. Beans are only wanted while dishes are on
  the menu, so `cuisineDishes` off means no bean farms either (38.9).
- "A dish on the menu" is any dish on the loaded lists (every level), not only those the village's Chef can cook
  today: a Chef rises, and the beans take days to grow. The beans a dish needs are the `#cobblecuisine:beans` items its
  own recipes ask for, found by result as the Chef finds them (38.9).
- The store counted is the farmer's chests and her village-mates' stashes. A colour can't be chosen: the crop's loot
  picks it, so any short colour sows the field with seeds (38.9).
- The partners' treats and the dish luxuries share `cuisineDishes`, as the design note's switch table assigns them
  (no new switch); a luxury file can now name a switch (`"switch"`), and while it is off the luxury is not loaded
  for anyone (38.10).
- The design's "existing treat field" doesn't exist: partners had no treat or mood of their own. The treat is the
  Rancher's (6 friendship with hearts), and its day is a new field in the Pokémon's own saved data, beside the Workers'
  Cup counter (38.10).
- A partner is fed when its worker's day is counted at work (`Partners.countDay`, the Workers' Cup's hook), so a store
  empty at that moment means no treat until the next day's count. Puffs and shakes are fed for friendship only: the
  shakes' EVs are Cobblecuisine's own feeding and aren't given (38.10).
- The dish luxuries are wants, not needs: a Burgher or Noble without Cobblecuisine must still rise and stay, so a need
  that can't exist there would break the ladder (38.10).
- A Cobblecuisine dish is found by the recipe whose result it is, not by the recipe's id: some differ
  (`cake_honey_fruit` makes `fruit_honey_cake`) (38.1).
- Dens are recorded when their chunk loads near a village, never by scanning (38.2's spec).
- The den list isn't switched: it only records and shows (design note M38). It's behind the 2.2 gate and needs Raid
  Dens (38.2).
- The Defense page shows up to seven dens, nearest first (38.2).
- A raid is noticed by Raid Dens' own advancements (`join_raid` and the seven tiers) being granted near a watched den,
  since players fight raids in the mod's own dimension; "once a raid" means until the den glows again (38.3).
- A warden stands two blocks off the crystal on the side toward home, and walks a leg of 16 blocks at a time, as
  scouts do (38.3).
- A Raid Dens tier table can't be rolled with a plain chest context: its pools check the Raid Pouch being opened, a
  loot parameter only Raid Dens holds. `compat/raiddens` reads its three public fields (`RaidLootContexts.RAID_POUCH`,
  `BONUS_LOOT`, `RAID_POUCH_USE`) by name and fills `RaidDenLoot`; Raid Dens stays off the compile classpath. If the
  fields are gone in a later Raid Dens, a won challenge still turns the den dark but brings no loot, with a line in the
  log (38.4).
- The challenge is looked at every 30 seconds by day; a village that tried (won or lost) waits `denChallengeDays`
  days, and a village with no free Leader or Master, or no den in reach, tries again next look (38.4).
- The village's Megas come from where the trainer stands now: a Leader's village is the nearest hall's, so a Leader of
  a City who walks into a Town's reach counts as the Town's (38.5).
- In a Cup final's simulated bouts (two villagers) the Mega Stone holder fights as the Pokémon it is: the bout's small
  formula has no Mega Evolution; only real battles against a player Mega Evolve (38.5).
- The Stadium Day's visitors Mega Evolve in a player's challenge; their exhibition bouts against the home trainers are
  settled with each side's home team, as before (38.5).
- A Mega Stone holder's battle AI Mega Evolves at any tier, Novice included (a Cup finalist can be one) (38.5).
- The Key Stone and Mega Stone recipes take 2 gold ingots and 4 emeralds each; the Key Stone is kept 2 in the store,
  each Mega Stone 1 (38.6).
- Besides the Crystal block the spec names, the Jeweller also cuts a stone from the plain Mega Stone a Crystal drops
  without silk touch, and (as a Master, level 4) from 4 Mega meteorite ores, which is what the Miner keeps them for
  (38.6).
- A Miner's Key Stone ore drops a Key Stone without silk touch: that goes to the store too (38.6).
- The Mega Bracelet is once per player per realm, as the roadmap says (the design note's open question 2 stays the
  owner's); it is Mega Showdown's plain `mega_bracelet`, given straight into the inventory with the capital's hall
  named, not picked up at the hall (38.6).
- A realm's Leader count is the member villages whose hall's last round saw a Trainer Leader of their own (38.6).
- The design note's example curry, Bean Medley Curry, is left off the lists: its recipe asks for `#cobblemon:mint_leaf`,
  a tag Cobblemon 1.7.3 doesn't have, so it can't be cooked; the curries kept all have makings that exist (38.8).
- Dishes whose recipes want a tag nobody fills (`#cobblemon:cooked_meat`: the Hefty, Five Alarm and Ham sandwiches) are
  left off too, and so are the `[LEGACY]` dishes and the Clodsire Eclair and Deluxe shakes (luxuries in 38.10) (38.8).
- The drinks come at level 2 (an Apprentice), between the salads and the curries; a Master adds the Fruity Flan
  (group `dessert`) and the Miltank Mix au Lait (a drink the inn doesn't serve) (38.8).
- The Camp Cook cooks a Cobblecuisine curry in one go, not over the pot's cooking time: the Campfire Pot has no recipe
  for it. At most 4 at a time (38.8).
- The Innkeeper serves only her guests (travellers), one a round (every 10 seconds), once a stay (38.8).
- In local compat runs on the 7 GB dev box, `-PcompatHeap=2G` is needed now that the runtime holds two more mods
  (38.1); CI keeps the default.

## Known limits

Only dens within the map's 128-block square get a banner; farther ones (up to 160 blocks) are on the Defense page
only. A den nobody has been near isn't known. A player who joins a raid from farther than 16 blocks (or whose advancement was granted earlier) gets no cheer. Only
one warden per den. A challenge is settled at once: there's no battle to watch, only the den going dark. A won
challenge gives no Raid Dens Pokémon, only the loot. On Cobblemon 1.8 (Raid Dens 0.13.2) the loot fields are untested.
The Camp Cook's curries have no cooking time and no lid shut (she cooks them over the fire at once). The Innkeeper
serves no players, and a guest served from the store needs a Village Hall in reach. The shrine's Mega Evolution is light, sound and a chat line at the pedestal, not a
Pokémon sent out and Mega Evolved: our trainers' Pokémon exist only in battle. The show takes the festival's first
hour and a half, then the square has the crowd. Our renderer has no model for the pedestal (a block entity), so the
shrine's render shows its plinth bare; the showcase scene shows it in the game. A realm whose villages have no hall round yet (none loaded since 38.6)
counts only the Leaders already beaten; a village whose Leader dies counts until its hall's next round. The bracelet's look is checked in the
showcase scene only (a client is needed to draw it).
Cobblecuisine on Cobblemon 1.8 is untested beyond the id test.

## Proof

- `CobblemonDepthCompatTests`: `theThreeModsAreLoaded`, `raidDenIdsExist`, `megaIdsExist` and `cuisineIdsExist`
  list every id the milestone reads and fail naming any that's missing.
- `ExpansionGateGameTests.cobblemonDepthIsGated`: the gate is shut for players and open in GameTests, for each of the
  six switches.
- `RaidDenCompatTests`: `aDenNearAHallIsListed` (its states, its line, the Defense page, dropped once broken),
  `aDenTurnedInactiveShowsInactive`, `aDenFarOffIsntListed` (200 blocks), `aReloadKeepsTheList`, `theMapMarksTheDen`.
- `DenWardenCompatTests`: `aWardenWalksToTheDenAndBackAtDusk`, `aDenGlowingAgainIsWrittenOnce`,
  `aRaiderByAWatchedDenGetsOneCheer` (one a raid, none 40 blocks off, another next raid),
  `aRecalledWardenGoesBackToNormalDuty`, `aWardenOfAGoneDenOrSwitchedOffIsFree`, `wardensSurviveASave`.
- `DenChallengeCompatTests`: `aWinBringsTheLootHomeAndTheDenRests` (the tier's loot in the store, the den inactive, the
  chronicle line), `aLossLeavesTheDenActive` (and the trainer tired, not sent again), `aTierSixDenIsNeverChallenged`,
  `aDenInUseByAPlayerIsntChallenged`, `aBrokenDenIsntChallenged`, `tiredTrainersSurviveASave`.

- `MegaTrainerCompatTests` (with Mega Showdown): `aCityLeaderMegaEvolvesATownLeaderDoesNot`,
  `theCupFinalsTrainersHoldAMegaStone` (six seeds, the theme kept, as fighters too, not in earlier rounds; finalists
  in a Cup today wear the bracelet), `theBraceletIsOnExactlyTheMegaTrainers` (City Leader, Master and visitor do;
  Expert, Novice and farmer don't; the clients are told the same), `switchedOffOnlyMastersMegaEvolve`,
  `aLeaderSavedAndLoadedIsTheSame`.
- `MegaTrainerGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `aCupFinalistIsKnown`.
- `MegaShrineCompatTests` (with Mega Showdown): `theShrineIsListedWithMegaShowdown`,
  `theFestivalShowIsHeldAtAFinishedShrine` (the pedestal's place, the watchers' +3, once a festival, through a save),
  `noShowWhenOffWithoutATrainerOrLate` (villageMegas off: not listed, no show; no trainer; past its hours).
- `MegaShrineGameTests` (without Mega Showdown): `hiddenWithoutMegaShowdown`, `theShowsMoodAndText`.
- `MegaStoneCompatTests` (with Mega Showdown, 38.6): `theRecipesAndFindsLoad`,
  `aJewellerCutsAMegaStoneForTheVillagesTrainers` (a crystal, gold and emeralds become one stone of the Master's team),
  `aJewellerCutsAKeyStone`, `onlyTheStonesTheStoreLacks` (and none with no trainer), `switchedOffTheRecipesDontRun`
  (`villageMegas` off, Jewelers off), `aMinersKeyStonesGoToTheStore` (dug Key Stone ore: the stones in the store,
  the cobblestone in the Miner's chest), `beatingEveryLeaderOfARealmGivesOneBracelet` (one Leader of two: nothing; a
  loss: nothing; both: one; again: still one; save and load), `braceletEdges` (switch off, a Leader who left, no realm).
- `MegaStoneGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `withoutMegaShowdownNoBracelet`,
  `aTagOutputWithAChooser` (the recipe format's new parts and their errors), `theText`.
- `CuisineCompatTests` (38.8, the real Cobblecuisine): `aChefWithTheMakingsCooksALevelOneDishAndKeeps16` (a Novice,
  20 sets of a salad's makings: 16 salads and 4 sets left), `aLevelOneChefNeverCooksALevelFourDish` and
  `anExpertChefCooksTheCake`, `aChefShortOfTheMakingsWaits`, `aVillagerWhoAteThreeKindsHasAVariedDiet` (and three
  salads are one kind), `theSwitchOffLeavesTheMenusAsTheyWere`, `theCampCookMakesACurry`,
  `theCampCookMakesNoCurryWithTheSwitchOff`, `theInnkeeperServesAGuestADrinkOnceAStay`.
- `CobblemonDepthCompatTests.cuisineDishListIdsExist`: every dish on the shipped lists and camp menu files is an item
  with a crafting recipe whose every ingredient some item fills.
- `CuisineGameTests` (without Cobblecuisine): `withoutCobblecuisineTheMenuIsTodays`, `dishListsAreReadAsTheirFormatSays`,
  `beanFarmSwitchText` (38.9).
- `BeanFarmCompatTests` (38.9, the real Cobblecuisine): `menuWantsTheBeansOfItsRecipes` (the salad's green bean, all
  in the tag, none with the dishes off; ripe only at the top age), `farmerPlantsBeansTheMenuWants`,
  `noBeansPlantedWhenTheStoreHasEnough`, `noBeansWithTheSwitchOff`, `farmerHarvestsOnlyRipeBeansIntoTheStore` (no
  seeds in the store to start with; the green half left, the beans in the chest, the cut half sown again);
  `CobblemonDepthCompatTests.cuisineIdsExist` checks the crop and seed ids `farm/BeanFarms` reads.
- `CuisineTreatsCompatTests` (38.10, the real Cobblecuisine): `aPartnerIsFedOnceADayFromTheStore` (nothing from an
  empty store; a puff taken and 6 friendship; not twice a day; a shake the next day; the day through a save and load
  of the Pokémon), `theSwitchOffFeedsNothing` (no treat, the luxuries not loaded or wanted), `aBurgherTakesADeluxeShakeWhenDue`
  (both files load, all six shakes count, one taken on day 10, none on day 12, one on day 14);
  `CobblemonDepthCompatTests.cuisineIdsExist` checks every puff, shake, deluxe shake and the éclair.
- `CuisineGameTests.withoutCobblecuisineTheDishLuxuriesAreSkipped` (38.10, without the mod): the files ship, are read
  as nothing, aren't loaded, no class takes them, the Classes page doesn't list them and the treat tag is empty;
  `theText` checks the switch tooltip and the luxuries' names. `ClassGameTests` now expects the two new wants.
- Scene `partner_treats` (38.10): a Machop at a builder's pasture eats a Poké Puff from the storehouse chest.
- Scene `bean_farm` (38.9): a row ripe, a row green, a row bare; the ripe row cut into the chest and the bare rows sown.

Showcase scenes: `raid_dens`, `den_warden`, `den_challenge`, `mega_trainers`, `mega_jeweller`, `cuisine_kitchen`, `bean_farm`, `partner_treats`
