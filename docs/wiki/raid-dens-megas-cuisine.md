# Raid dens, Megas and Cobblecuisine

Cobblemon depth brings three of the pack's Pokémon mods into village life: Cobblemon Raid Dens (the village knows the
dens around it, and the player hires its trainers to help in a raid), Mega Showdown (more village trainers Mega Evolve,
and the village makes Mega Stones and Key Stones) and Cobblecuisine (the kitchens cook its dishes and farmers grow its
beans). Part of 2.2, behind its gate (`Expansions.M38`). Built so far: the plan, the gate, the test mods, the dens
the village knows (38.2), raid helpers (38.1a, which replaced 38.3's Den Wardens and 38.4's village den challenges), more Mega trainers with their
bracelets (38.5), the Jeweler's Mega Stones and Key Stones with the realm's Mega Bracelet (38.6), the Mega
Shrine with its festival show (38.7), Cobblecuisine on the Chef's, Camp Cook's and Innkeeper's menus (38.8), bean farms (38.9) and the partners' treats and dish luxuries (38.10); the full plan is the design note, docs/design/M38.md.

Roadmap items: 38.1, 38.1a, 38.2, 38.3, 38.3a, 38.4, 38.4a, 38.5, 38.6, 38.6a, 38.7, 38.8, 38.8a, 38.9, 38.10, 38.11, B190, B191, B215, B414, B415, B476

## What a player sees

With Cobblemon Raid Dens installed (and once 2.2 ships), the hall's Defense page has a row of the raid dens within 160
blocks of the hall, nearest first: "Raid den, tier 3, Fire, active" (the icon glows), or "resting" once it has been
beaten and hasn't come back yet, with where it is and the day it was found. With none known it says "No raid den
known". The village map marks each den on its square with a magenta banner named the same way, and its legend counts
the raid dens. A den shows up once someone has been near enough for its chunk to load, the moment a crystal is set
down in a loaded chunk, and when a hall is set down by dens that were there first; a broken den drops off the list.

**Raid helpers (38.1a).** The village never raids a den on its own (owner, 2026-10-19: 38.3's Den Wardens and 38.4's
solo challenges are gone). Under each active den on the Defense page, **Hire a raid helper** hires one of the village's
free trainers for that den, the best first, for `denHelperPrice` emeralds (5; CobbleDollars when installed), up to 3
at once; the tooltip shows the price in the money the click takes ("500 CobbleDollars a helper" with CobbleDollars, "5
emeralds a helper" without) and how many helpers the player has and how many are free. No money: "A raid helper costs 500
CobbleDollars", nothing taken. The helpers follow the player. When the player joins (or hosts) a raid within 16 blocks
of the den, or within a minute of being there, each helper cheers once: one Attack, Defense or Heal Cheer, never twice
a raid. They then stand round the crystal and, when the raid itself can be reached, their Pokémon take a share of the
boss's health every 5 seconds (0.6% times their rank divided by the den's tier, more with a stronger team), never below a
quarter: the last blows are the player's. A helper more than one rank under the den's tier only cheers. A won raid
goes in the chronicle ("<player> and <trainer>'s band beat the tier 3 Fire raid den") and earns the helpers battle
experience; won, lost, the raid gone, a day after the hiring or the switch turned off, they walk home, and nobody can
hire them until they're back. A helper whose chunk isn't loaded for a while (the player logged off by the den, a
server restart) stays hired and follows again once it loads; one that stops being a trainer leaves the hiring. Without CobbleDollars the price is taken in emeralds from the player's inventory.

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

**Mega Stones and Key Stones from the village (38.6).** With Mega Showdown, a Journeyman Jeweler (level 3) cuts, at the
stonecutter, only from makings in the **village store** (the hall's storehouses and kitchens, or without a hall the
storehouses near the stonecutter; 38.6a, the owner: never from their own chest or other workers' chests, and never from
nothing):

| Makings | Makes | Level | Kept in the store |
|---|---|---|---|
| Key Stone Ore, 2 gold ingots, 4 emeralds | a Key Stone | 3 | 2 |
| a Mega Stone Crystal (the block, silk-touched), 2 gold ingots, 4 emeralds | a Mega Stone the village's trainers use | 3 | 1 of each |
| a Mega Stone (what a Mega Stone Crystal drops), 2 gold ingots, 4 emeralds | the same | 3 | 1 of each |
| 4 Mega meteorite ores (any mix, silk-touched), 2 gold ingots, 4 emeralds | the same | 4 | 1 of each |

"A Mega Stone the village's trainers use": the Jeweler looks at the trainers of their village (round the nearest
Village Hall, or within 48 blocks without one; visitors and Cup delegates left out), nearest first, and at the Pokémon
on each one's home team, and cuts the Mega Stone of the first such Pokémon whose stone the store doesn't hold yet
(both Charizardites for a Charizard). A village whose trainers have no Pokémon with a Mega Evolution gets no stones;
the Jeweler never floods the store with all 46. Makings in the store are used up by the cut. While the store lacks
them the Jeweler cuts nothing and the line above their head says what the Storehouse should hold ("needs 4 × Emerald
in the Storehouse", for the stone closest to done); these aren't posted on the requests board, since a hand-over there
goes to the Jeweler's own chest, which doesn't count.

A **Miner** drops off what they dig of Key Stone ore (a Key Stone, or the ore itself with a silk-touch pickaxe), Mega
Stone Crystals (a Mega Stone, or the crystal) and Mega meteorite ores (silk-touched; without silk touch they drop
Cobblemon's evolution stones, which stay in the Miner's chest) in the **village store** (the hall's storehouses and
kitchens) for the Jeweler, instead of their own chest; what the store has no room for goes in their chest as before.

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
The trainer waits at the pedestal until every villager off work has come to the shrine (at most 600 ticks from the
gathering's start, and never past the show's last 100 ticks), so the crowd sees it (B215). Everyone who came, and every
villager off work within 12 blocks, is the happier for it (a villager at work by the shrine, such as a farmer at his
fields, isn't one of the crowd and gets nothing, B420): +3 mood ("watched a Mega Evolution at the shrine") as long as a
festival's good mood lasts (2 days). The shrine counts only while its pedestal stands: with the pedestal broken (or
never placed) there is no show, no chat line and no mood, and once a pedestal is put back the next festival's show is
held again (B421). Once a festival, restart or not: the day each village's show was held is saved, so a server
restart during the show's hours doesn't hold it again (B501), and the next festival day holds its own; then the crowd
goes back to the square for the dancing and fireworks. The show is part of the festival itself: it needs the village's festival (six villagers or more, on its
festival day or one called), and it begins at the festival's first tick after the hall's round has seen it start.

**Cobblecuisine on the menus (38.8).** With Cobblecuisine, the Chef cooks its dishes, each from its own crafting recipe
with the makings from the chests by the stove, the porters' and the butcher's spare (as he cooks everything); since
38.8a only the ones players ask for at the hall's board (below), so he no longer keeps 16 of each. By level: a Novice the salads (Mixed Vegetable, Spore Mushroom, Pumpkin Pie) and
sandwiches (Sweet Jam, Salty Egg, Salty Veggie), an Apprentice also three more salads, two sandwiches and the drinks
(Bouncy Brionne Soda, Teddiursa Iced Coffee, Fruit Punch), a Journeyman six curries, an Expert five cakes, and a Master
the Jigglypuff Fruity Flan and Miltank Mix au Lait (27 dishes). The sandwiches' roasted berries he roasts himself
(the campfire's recipes are now among a cook's). Every dish is a meal from the store whatever its effects, and the
diet counts it as its group: a salad, a sandwich and a curry are three kinds (a varied diet), three salads one. The
Camp Cook's menu has three curries (Beanburger, Mild Honey, Spicy Potato): the pot has no recipe for them, so she
cooks them by their own recipe over the fire, makings out of the chests and the curry into her bag in one go, and
takes them to the chests by the pot (16 kept). What she keeps of every dish is counted however each one was cooked:
a dish out of the pot carries what it was cooked with, and once only plain ones counted, so 15 Poké Snacks became 18
(B465); now 15 become 16 and no more. The Innkeeper serves each guest one of the three drinks once a stay,
from the chests by her counter or else the village store; it counts in the guest's diet, and her status says "served
a guest Bouncy Brionne Soda".

**Dishes on request (38.8a, the owner's change of 2026-10-19).** The Chef no longer cooks Cobblecuisine's dishes on
his own: a player asks for them at the hall's board, on the Trade page's **Dishes** tab (the minecart in the hall's
divider; the tab sits in the row's free slot before Routes and shows only while dishes are loaded). The tab lists every
dish with the Chef's level it needs, how many are asked for and how many the village's store holds; a click asks for 4
more, shift-click 16, right-click takes the request down (at most 64 of a dish wait). Without a Chef in the village
every dish is locked and the tab says a Chef is needed; a dish above the best Chef's level says which level it needs.
The village's Chef (the one whose stove's nearest hall it is) cooks the oldest request his level allows before his own
menu, from the same makings, up to a batch of 8, and carries it to the village's Storehouse (the chests by his stove if
it has none); what he delivers comes off the request. A request whose makings are missing waits. The requests are
saved per dimension (`aliveworkplace_dish_requests`; older worlds start with none) and survive restarts. Unchanged:
the Camp Cook's curries (her own `camp_menu` path), the Innkeeper serving whatever drinks are in her chests or the
store (the Chef now makes them only on request too, since they're on the same dish lists), the Campfire Pot recipes,
Cobblemon's Poké snacks and the vanilla menu. Dishes the Chef stocked before the update stay where they are.

A farmer with Cobblecuisine grows beans for those dishes (38.9): while the store (her chests and the village's stashes)
holds fewer than 16 of a bean color a dish on the menu needs, she takes bean seeds from the chests and sows her
field's empty farmland with them, cuts a bean plant only once it's fully grown, and takes the beans to the store with
the rest of the harvest. Which color comes up is Cobblecuisine's own loot (one of the seven at random), so a short
color brings a field of mixed beans. With 16 of every wanted color, or no bean dish on the menu, she sows none and
the seeds stay in the chest; the ripe beans already growing are still cut, but not sown again.

**Treats for partners and dish luxuries (38.10).** A partner Pokémon (one in a Pasture Block helping a villager at
work) gets one treat a day from the village store: when its worker's day is counted at work, it eats a Poké Puff (any
of the five) or a shake (Low, Medium or High, any color) out of the store (the nearest hall's kitchens and
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
short (sculk and no honeycomb for an EXP Candy XS) nothing is cooked or taken. He never cooks one of his dishes up or
breaks it down into another (B435, B459): a recipe whose makings are all dishes he keeps (six EXP Candies XS into an S
in the pot, Cobblemon's crafting recipes that split an XL into three L, and so on) is not his, so the store's 15 XS
stay XS and a player's XL is only used where a recipe also wants other makings (Mighty Candy: four Kelpsy Berries, four
Honeycombs and the XL make three). Switch: `cuisineDishes` (behind M38's
gate); off, the pot dishes leave the menu except the ones it always had (Poké Bait, Snacks, Cakes, candied apples,
Aprijuice). Scene `campfire_pot`.

## How it works

Every piece reads the three mods by their ids, tags, data files and advancements only, each behind a check that the mod
is loaded; any Cobblemon class stays in `compat/cobblemon/`. Without a mod, its part of the expansion doesn't exist and
nothing else changes.

The gate: `Expansions.M38` is false until the release that finishes the milestone, so its five switches (denHelpers,
denHelperPrice, villageMegas, cuisineDishes, beanFarms) stay off and hidden for players
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
the plain menu and the Campfire Pot dishes, never a listed dish (38.8a: those come from `craft/DishRequests`, a
`SavedData` of hall to dish to count, asked for through `craft/DishesTab` and cooked first by `ChefWork.chooseRequest`;
`CrafterWork.delivered` counts them off); the recipes are found through the recipe manager by their
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
is kept in; 8 by default) and `when` (a named switch: `village_megas` is `villageMegas` with Jewelers on). The files
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
(`BuildSiteManager.finishedNear`), keeping only those whose pedestal spot holds `mega_showdown:pedestal` now (read by
id, and only while that chunk is loaded; B421), and Mega Showdown's pedestal only by its id (`mega_showdown:pedestal`):
`BlueprintLibrary.megaOk` lists the shrine only while that block exists and `villageMegas` is on. `Festivals.tick`
asks it first each festival tick; in the show's hours it takes the crowd (to 4 blocks in front of the dais) and walks
the trainer to the step before the pedestal; there, the trainer waits for the crowd (`WAIT_TICKS`: every villager off
work in the village has been at the crowd's spot once today, or 600 ticks since the gathering began, or the show's last
100 ticks) and the show is held once a day per village, for everyone who came (kept in memory by villager id: the
crowd's spot reaches up to 14 blocks from the pedestal, past the 12 of its watchers) and everyone off work (idle, meeting or playing, or seated, as `Festivals.gatherAt` gathers
them) within 12 blocks; a villager at work there is left out (B420) (kept in memory: a restart
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
| `denHelpers` | on (once 2.2 ships) | Players hire the village's trainers to help in a den's raid. Off: no button, no hiring, and those hired walk home. (Replaced denWardens, denChallenges and denChallengeDays; an old file's keys are dropped.) |
| `denHelperPrice` | 5 (1 to 64) | Emeralds one raid helper costs (100 CobbleDollars an emerald). |
| `villageMegas` | on (once 2.2 ships) | More Mega trainers and their bracelets (38.5), the Jeweler's Mega Stones and Key Stones, the Miner's finds to the store and the realm's Mega Bracelet (38.6), and the Mega Shrine at the Table with its festival show (38.7). Off: only Masters Mega Evolve, as before, nobody wears a bracelet, the Mega recipes don't run, the Miner keeps everything and no bracelet is given. With Jewelers off (M34) the Mega recipes don't run either, and the Miner keeps everything. |

| `cuisineDishes` | on (once 2.2 ships) | Cobblecuisine's dishes asked of the Chef at the hall board's Dishes tab by his level (38.8a), counted by food group for the diet, the Camp Cook's curries and the Innkeeper's drinks (38.8), the partners' daily treat and the Deluxe Shake and Clodsire Eclair luxuries (38.10). Off: the menus and the diet are as before, no treats, and the two luxuries are neither wanted nor taken. |
| `beanFarms` | on (once 2.2 ships) | Farmers sow Cobblecuisine's beans when the store is short of a color a menu dish needs, and cut them when ripe (38.9). Off: farmers never sow beans (ripe ones on a field are still cut, as any crop). |

## Saved data

Each dimension saves its known dens (`aliveworkplace_raid_dens`): a list of `pos`, `tier` (1 to 7), `type`,
`active` and `firstSeen` (the day). An older save has none, and dens are found again as their chunks load. The raid helpers are saved per dimension
(`aliveworkplace_raid_helpers`): each hiring's `player`, `hall`, `den`, `tier`, `type`, `helpers` (their ids), `until`,
`joined`, `raid` (the raid's id, when reached) and `dealt`, and the helpers walking home (`homeward`). An older save
has none. The 38.3 and 38.4 files (`aliveworkplace_den_wardens`, `aliveworkplace_den_challenges`) are left on disk
unread: a guard posted as a warden is simply a guard again. The Mega Bracelets are saved on the overworld (`aliveworkplace_leader_bracelets`): the villages
known to have a Trainer Leader (`leaderHalls`) and, per player, the villages whose Leader they beat (`beaten`) and the
realms whose bracelet they had (`megaBracelet`). An older save has none: nobody has a bracelet yet. A villager who
watched a Mega Shrine's show keeps the day on them (`mega_show_day`, 38.7); an older save has none. The day each
village's shrine show was held is saved per dimension (`aliveworkplace_mega_shrines`: `held`, by the hall's position,
B501); an older save has none, and every village's next show is held as before. Who came to the shrine for a show not
yet held is kept in memory only: after a restart the trainer waits for them again (never past the show's last call). Cobblecuisine's
dishes (38.8) save nothing new: the dishes go in chests and the meals in the villagers' `recent_meals`. Bean farms
(38.9) save nothing new either: whether beans are wanted is worked out again at each look over the field. A partner's treat day (38.10) is kept in the Pokémon's own saved data
(`aliveworkplace_treat_day` in its persistent data, which Cobblemon saves with it); a Pokémon from an older save has
none and gets its treat the first day it helps. The dish luxuries are noted in the villagers' `luxuries_had` like any
luxury. The planned fields
of later items are listed in the design note.

## Items, blocks, jobs, commands

None of our own yet; 38.6 adds four Jeweler recipes (`mega_keystone`, `mega_stone`, `mega_stone_cut`,
`mega_stone_meteorite`) and two item tags (`jeweller_finds`, `mega_meteorite_ores`); 38.7 adds the decoration
blueprint `aliveworkplace:mega_shrine` (tools/blueprints/decor.py, `mega_shrine`); 38.8 adds the dish lists
`data/aliveworkplace/cuisine_dishes/` and three `camp_menu` files; 38.10 adds the luxuries `deluxe_shake` and `clodsire_eclair` (with
`fabric:load_conditions` on Cobblecuisine and `"switch": "cuisineDishes"`, a new optional luxury field) and the item
tags `partner_treats` and `deluxe_shakes` (optional entries). The ids the milestone reads from the three mods (the den block `cobblemonraiddens:raid_crystal_block`
and its states, the cheers, `mega_showdown:keystone`, `cobblecuisine:bean_crop`, the beans and the dishes) are listed in
the design note and checked by `CobblemonDepthCompatTests`.

## Decisions

- Cobblecuisine has seven bean colors, not six as the roadmap said; the plan uses all seven (38.1).
- Bean farms have their own switch, `beanFarms`, as the design note's switch table planned, rather than sharing
  `cuisineDishes`: a server can keep the dishes and grow its beans by hand. Beans are only wanted while dishes are on
  the menu, so `cuisineDishes` off means no bean farms either (38.9).
- "A dish on the menu" is any dish on the loaded lists (every level), not only those the village's Chef can cook
  today: a Chef rises, and the beans take days to grow. The beans a dish needs are the `#cobblecuisine:beans` items its
  own recipes ask for, found by result as the Chef finds them (38.9).
- The store counted is the farmer's chests and her village-mates' stashes. A color can't be chosen: the crop's loot
  picks it, so any short color sows the field with seeds (38.9).
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
- A raid is noticed by Raid Dens' own statistics (`raids_joined`, `raids_hosted`, `tier_<n>_completed`), awarded
  every raid, through a mixin on `ServerPlayer.awardStat`; its advancements (38.3's way) come only once a player.
  Players fight raids in the mod's own dimension, so a raid counts as the den's within 16 blocks of it or a minute
  after the player was there (38.1a).
- The boss is reached by reflection in `compat/raiddens` (RaidHelper.ACTIVE_RAIDS, RaidInstance.isPlayerIn,
  getCurrentHealth, getMaxHealth, getRaidState, healBoss), filling the `RaidAssist` extension; a blow is
  healBoss with a negative share, which syncs every battle's health bar. The raid is only won when a player's own
  battle takes the health to 0, so the helpers can't end it. Without those names the helpers only cheer (38.1a).
- 38.4's loot-table reader (`RaidDenLoot`, `RaidDensLoot`) went with the challenges (38.1a).
- The village's Megas come from where the trainer stands now: a Leader's village is the nearest hall's, so a Leader of
  a City who walks into a Town's reach counts as the Town's (38.5).
- In a Cup final's simulated bouts (two villagers) the Mega Stone holder fights as the Pokémon it is: the bout's small
  formula has no Mega Evolution; only real battles against a player Mega Evolve (38.5).
- The Stadium Day's visitors Mega Evolve in a player's challenge; their exhibition bouts against the home trainers are
  settled with each side's home team, as before (38.5).
- A Mega Stone holder's battle AI Mega Evolves at any tier, Novice included (a Cup finalist can be one) (38.5).
- The Key Stone and Mega Stone recipes take 2 gold ingots and 4 emeralds each; the Key Stone is kept 2 in the store,
  each Mega Stone 1 (38.6).
- Besides the Crystal block the spec names, the Jeweler also cuts a stone from the plain Mega Stone a Crystal drops
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
- The Innkeeper serves only her guests (travelers), one a round (every 10 seconds), once a stay (38.8).
- In local compat runs on the 7 GB dev box, `-PcompatHeap=2G` is needed now that the runtime holds two more mods
  (38.1); CI keeps the default.

## Known limits

Only dens within the map's 128-block square get a banner; farther ones (up to 160 blocks) are on the Defense page
only. A den nobody has been near isn't known. A player who joins a raid farther than 16 blocks from the den, and not within a minute of being there, gets no cheer.
The helpers' Pokémon aren't sent out on the ground: the helpers stand by the crystal, and their blows show as the
boss's health bar dropping and sparks at the helpers. Without the raid reached (a newer Raid Dens) a lost raid isn't
noticed, so the helpers stay until the day is up. On Cobblemon 1.8 (Raid Dens 0.13.2) the reflection is untested.
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
  five switches.
- `RaidDenCompatTests`: `aDenNearAHallIsListed` (its states, its line, the Defense page, dropped once broken),
  `aDenTurnedInactiveShowsInactive`, `aDenFarOffIsntListed` (200 blocks), `aReloadKeepsTheList`, `theMapMarksTheDen`.
- `RaidHelperCompatTests` (38.1a): `hiringChargesAndTheHelperFollows` (the price taken, the best first, they keep up),
  `noMoneyIsRefused`, `theCapIsRespected` (three, and none for a resting den), `eachHelperCheersOnceARaid` (none 40
  blocks off, one each, never twice), `aWonRaidIsWrittenAndTheHelpersGoHome`, `aDayLaterTheHelpersGoHome`,
  `theHelpersWearTheBossDown` (the reflection found the raids; blows by rank, never under a quarter),
  `theButtonAndTheSwitch` (the Defense page's lines; off: no button, no hiring, no cheer), `hiringsSaveAndOldDataLoads`.
- `QaDenHelperCompatTests` (QA, from the spec): `theDefencePageButtonHiresAtExactlyThePrice` (the button clicked as
  a client clicks: exactly the price hires the best trainer, one CobbleDollar short is refused and takes nothing, the
  button counts "1 of 3", they keep up), `emeraldsAtACustomPriceExactlyAndOneShort` (no CobbleDollars, `denHelperPrice`
  7: 6 emeralds refused and kept, 7 taken), `noTrainersInTheVillageMeansNoHelper`,
  `aStaleButtonForADenThatWentToRestIsRefused`, `aHiringSavedAndReadBackStillFollows` (and the walk home survives a
  save), `theRankRuleAndTheQuarterFloor` (every rank on both sides of its tier limit; exactly a quarter from just above
  it, no healing below it; a Novice at tier 3 only cheers), `threeHelpersCheerOnceEachAndAgainNextRaid` (one Attack,
  Defense and Heal, the chat lines word for word, not on the way home, again next raid) and
  `switchedOffMidHireTheHelperWalksHome`.
- `QaDenHelperBugTests.theButtonsPriceMatchesWhatTheClickCharges` (B414): with CobbleDollars the button says "500
  CobbleDollars", the price the click asks for, and emeralds in the inventory are left alone.
  `OptionalModsGameTests.raidHelpersCostEmeraldsWithoutCobbleDollars`: without it the price reads "5 emeralds a helper"
  ("1 emerald" at 1) and the hire's charge takes exactly that many emeralds.
- `QaDenHelperBugTests.aHelperUnloadedForAMomentIsStillHired` (B415): one look with the helper's chunk unloaded keeps
  the hiring, and the helper loaded back follows the player; `aDayLaterUnloadedHelpersAreSentHome`: the day's end still
  sends an unloaded helper home and tells the player; `aHelperNoLongerATrainerLeavesTheHiring`.
- `ConfigGameTests.anOldDenConfigLoads`: a config with 38.3's and 38.4's keys loads (38.1a).
- `QaDenSentencesCompatTests` (QA, from the spec, 38.1a/38.3a/38.4a): `theCheerLinesReadAsWritten` (three helpers: each
  cheer line word for word with "a" or "an" before the cheer's name, then the strike line), `theWinIsWrittenWithThePlayersAndTheHelpersNames`
  (the chronicle names the player and the best helper; the player's win line; the helper's status "helping <player> with a
  raid", then the helper walking home), `aRaidGoneIsToldAndSendsTheHelpersHome` (the "is over" line, home, no chronicle
  line), `aVillageAloneNeverRaidsTheDen` (a Master, a store and an active tier 3 den over four days with no player: the den
  stays active, the store empty, no chronicle line, no Den Warden lines left) and `theSettingsTooltipsTellTheHiring`.

- `MegaTrainerCompatTests` (with Mega Showdown): `aCityLeaderMegaEvolvesATownLeaderDoesNot`,
  `theCupFinalsTrainersHoldAMegaStone` (six seeds, the theme kept, as fighters too, not in earlier rounds; finalists
  in a Cup today wear the bracelet), `theBraceletIsOnExactlyTheMegaTrainers` (City Leader, Master and visitor do;
  Expert, Novice and farmer don't; the clients are told the same), `switchedOffOnlyMastersMegaEvolve`,
  `aLeaderSavedAndLoadedIsTheSame`.
- `MegaTrainerGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `aCupFinalistIsKnown`.
- `MegaShrineCompatTests` (with Mega Showdown): `theShrineIsListedWithMegaShowdown`,
  `theFestivalShowIsHeldAtAFinishedShrine` (the pedestal's place, the watchers' +3, once a festival, through a save),
  `noShowWhenOffWithoutATrainerOrLate` (villageMegas off: not listed, no show; no trainer; past its hours),
  `theShowComesWithTheVillagesFestivalDay` (B191: the hall's round plans the festival and its own tick holds the show),
  `theShowWaitsForTheCrowdAndCountsEveryoneWhoCame` (B215: the trainer waits for a villager still on the way; one at the
  crowd's far edge, past 12 blocks, cheers too; one who never comes doesn't hold it past its last call).
- `QaMegaShrineGapsCompatTests` (with Mega Showdown, B420): `aVillagerAtWorkGetsNoShowMood` (a farmer at work within
  12 blocks gets no show day and no mood), `theCrowdKeepsTheShowMoodAndTheWorkerNoneAfterReload` (the crowd's watcher
  reads +3 and "watched a Mega Evolution at the shrine", the farmer neither, both still so after a save and reload),
  `aBrokenPedestalHoldsNoShow` (B421: the pedestal broken, no show, no chat line, no mood; put back, the show again).
- `MegaShrineGameTests` (without Mega Showdown): `hiddenWithoutMegaShowdown`, `theShowsMoodAndText`.
- QA (lane d, 38.7): `QaMegaShrineCompatTests` (5: no show without a finished shrine, none at a shrine outside the
  village or for another hall, a villager at work far off isn't gathered or waited for, +3 once a show, no show on a day
  without a festival) and `QaMegaShrineGameTests` (3: the lines, exactly +3 for a festival's length, no show without
  Mega Showdown).
- `MegaStoneCompatTests` (with Mega Showdown, 38.6): `theRecipesAndFindsLoad`,
  `aJewellerCutsAMegaStoneForTheVillagesTrainers` (a crystal, gold and emeralds become one stone of the Master's team),
  `aJewellerCutsAKeyStone`, `onlyTheStonesTheStoreLacks` (and none with no trainer), `switchedOffTheRecipesDontRun`
  (`villageMegas` off, Jewelers off), `aMinersKeyStonesGoToTheStore` (dug Key Stone ore: the stones in the store,
  the cobblestone in the Miner's chest), `beatingEveryLeaderOfARealmGivesOneBracelet` (one Leader of two: nothing; a
  loss: nothing; both: one; again: still one; save and load), `braceletEdges` (switch off, a Leader who left, no realm).
- `QaDenMegaCompatTests` (QA, from the spec): `theDenReachIs160BlocksEachWay` (160 blocks off listed, 161 not even
  recorded; on the diagonal 159.8 listed and 161.2 not, so it's a distance, not a square), `aDenFoundAgainIsRecordedOnce`
  (loaded again three days later: one entry, still "Found on day" of the first; a change of tier, type and state read
  in), `aDenTurnedToStoneIsDropped` (and the empty row's two lines), `twoHallsNearOneDenBothListIt` (a third hall 161
  blocks off doesn't), `theDefencePageShowsTheSevenNearest` (eight known, the seven nearest in order, no raw text, nothing
  of wardens or challenges), `everyTierAndTypeReadsAsASentence` (every type and tier the den block has),
  `withTheGateShutNoDenIsFound`, `onlyACitysLeaderMegaEvolves` (Hamlet, Village and Town Leaders and a Leader with no hall
  don't; a Stadium visitor from a Town and a Hamlet's Master do; a City's Expert doesn't), `theBraceletsLinesReadRight`
  (both chat lines word for word, the progress line once for a Leader beaten twice) and
  `aFullInventoryStillGetsTheBracelet` (dropped at the player's feet, one).
- `MegaStoneGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `withoutMegaShowdownNoBracelet`,
  `aTagOutputWithAChooser` (the recipe format's new parts and their errors), `theText`.
- `CuisineCompatTests` (38.8 and 38.8a, the real Cobblecuisine): `withoutAChefTheDishesAreLockedOnTheBoard`,
  `aRequestedLevelOneDishIsCookedAndDelivered` (was 38.8's "a Chef with the makings cooks a level-1 dish and keeps 16": 4 salads
  asked for at the board, 4 cooked, 16 sets of makings left, no more cooked), `anUnrequestedChefCooksNoDish`,
  `aLevelOneChefNeverCooksALevelFourDish` (the board refuses, and a written-in cake request isn't cooked) and
  `anExpertChefCooksTheRequestedCake`, `aChefShortOfTheMakingsWaits` (the request stays up),
  `dishRequestsSurviveSaveAndReload`, `aVillagerWhoAteThreeKindsHasAVariedDiet` (and three
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
- `QaCuisineCompatTests` (QA, from the spec): `theBoardsShiftClickCapAndRightClick` (clicked as a client clicks:
  shift-click asks 16, four make 64 and a fifth click is refused; right-click takes it down; the soda locked for a
  Novice opens exactly at Apprentice; every line and answer word for word, and the three drinks' names),
  `theCampCookShortOfOneMakingCooksNoCurry` (no chicken: no curry, nothing taken), `fifteenOfOneColourSowsBeans` (16
  of every color but 15 green beans: the field is sown), `aBeanOneStageShortIsLeftStanding` (random ticks off: the
  plants one stage short stay, the one ripe plant is cut into the chest), `theDeluxeShakeIsDueOnTheFourthDayNotTheThird`
  (none on day 13, one on day 14; a Burgher takes the class above's éclair), `theEclairIsDueOnTheEighthDayNotTheSeventh`
  (none on day 27, one on day 28) and `aPartnerIsFedOnlyFromTheStoreOnceADay` (puffs in a chest that isn't the store
  are never fed; a store of luxuries and bread feeds nothing the next day).
- `CuisineGameTests.withoutCobblecuisineTheDishLuxuriesAreSkipped` (38.10, without the mod): the files ship, are read
  as nothing, aren't loaded, no class takes them, the Classes page doesn't list them and the treat tag is empty;
  `theText` checks the switch tooltip and the luxuries' names. `ClassGameTests` now expects the two new wants.
- `CampfirePotCompatTests` (38.11): every pot recipe on the menu by type, an EXP Candy and a data pack's Rare Candy
  cooked, none short of a making, none with `cuisineDishes` off, the tooltip. `QaCampfirePotMenuCompatTests` (QA):
  makings in two chests, a shapeless tag recipe, a recipe that makes four, a result with components left out, the old
  dishes with the switch off. `QaCampfirePotStockCompatTests` (QA, with Strawberry Sweets, which no pot recipe uses up):
  `makingsForOneCookMakeOneSweet` (the rest of the makings stay), `sixteenInTheChestMeansNoneCooked`,
  `fifteenInTheChestGetsOneMore` (exactly 16) and `aChefLoadedAgainStillCooksThePotDish`. `QaCuisineCandyBugTests`
(B435): `theChefStopsAtSixteenCandies` (20 makings, 16 EXP Candies XS, 4 left) and `fifteenCandiesGetOneMore`.
`QaCampfirePotCompatTests` (B459, a shaped recipe): Mighty Candy from exactly its makings makes 3, and one berry short
nothing is cooked and the XL stays.
- Scene `partner_treats` (38.10): a Machop at a builder's pasture eats a Poké Puff from the storehouse chest.
- Scene `bean_farm` (38.9): a row ripe, a row green, a row bare; the ripe row cut into the chest and the bare rows sown.

Showcase scenes: `raid_dens`, `den_helpers`, `mega_trainers`, `mega_jeweller`, `cuisine_kitchen`, `bean_farm`, `partner_treats`
