# Changelog

## Unreleased

## 0.66.0 — 2026-09-28

### Added
- **Orchard keepers till the ground for berries** (with Cobblemon): put a hoe in the chests by the Fruit Basket and they
  till grass and dirt in their orchard into farmland to plant Cobblemon berries — no need to prepare the farmland first.

## 0.65.1 — 2026-09-28

### Fixed
- A test that grew a random huge fungus sometimes failed, which stopped 0.64.0 from being released on its own: test trees
  now grow the same shape every run. **0.64.0's Fire-type partners** (a Fire-type Pokémon pastured near a Miner's Bench
  or a fisherman's barrel smelts 8 of the ores or fish in each furnace there whenever the worker tends it) are in
  0.65.0 and later.

## 0.65.0 — 2026-09-28

### Added
- **Third tiers for every starter build**, each bringing a new villager: Market Stall III (a storeroom shed with chests
  behind the stalls), Lookout Tower III (a guardhouse with a second Guard Post and two bunks), Healing Center III (a
  fenced berry garden with a Fruit Basket for an orchard keeper) and Supply Shop III (a post office annex with a Postal
  Desk). Builders who finish a tier II sell the tier III.

## 0.64.0 — 2026-09-28

### Added
- **Fire-type partners at the furnaces** (with Cobblemon): a Fire-type Pokémon pastured near a Miner's Bench or a
  fisherman's barrel smelts 8 of the ores (or fish) in each furnace there every time the worker tends it — on the
  spot, no coal, straight into the chests. Up to three partners.

## 0.63.0 — 2026-09-28

### Added
- **Price Tags without an anvil**: right-click a Price Tag to set its price with +/− buttons (1, 10, 100, 1000). Renaming
  it in an anvil still works.

## 0.62.0 — 2026-09-28

### Added
- **The post office**: mail for a player with no mailbox waits at the post office — right-click any Postal Desk to pick
  up every parcel handed in for you (night mail on its way to your mailbox too). At dawn you're told when some are
  waiting, and `/workplace mail` says where they are. Before, such parcels were stuck for good.

## 0.61.0 — 2026-09-28

### Added
- **Village farms**: give a farmer a blank Field Marker (no corners marked) and they take on the farm by their
  composter — the nearest farmland within 16 blocks and everything joined to it, across the water channels too.

## 0.60.0 — 2026-09-28

### Added
- **Lumberjacks fell mangroves**, down to the roots (the roots stay), and plant a propagule close by in the water over
  the mud. **Azalea trees** get an azalea bush back instead of an oak sapling; **cherry trees** a cherry sapling.
- **Lumberjacks use bone meal**: with bone meal in the chests, whenever there's no grown tree to fell they give it to
  the saplings on their tree farm and the ones they replanted until they grow.

### Changed
- Workers (builders, miners, lumberjacks and the rest) can now stand in shallow water and on waterlogged blocks, so
  they get to work in swamps and streams. A lumberjack chops a tree from wherever one of its logs can be reached, and
  skips trees nobody can get to instead of trying the same one forever.

## 0.59.0 — 2026-09-28

### Added
- **Strip mines down a ladder shaft**: after "strip mine" the Quarry Marker now offers strip mines at Y=16 (iron;
  ancient debris in the Nether), Y=-16 (redstone, gold, lapis) and Y=-53 (diamonds). Mark the corners on the ground and
  the miner digs a ladder shaft down from the corner nearest their bench, digs the tunnels there, and ladders the
  shaft all the way up (ladders from the chests). Water and lava beside the shaft are sealed off, and caves in its way
  are bridged so the miner never falls.
- Strip mines can be up to 64 blocks long (still up to 32 wide).

## 0.58.0 — 2026-09-28

### Added
- **Starter Cottage III**: the third tier of the cottage adds a kitchen wing on the east side (a smoker, a barrel, a
  table and its own door) with a roof terrace on top, reached through a new door from the upper floor. Builders who
  finish a Starter Cottage II now sell it.

### Fixed
- An upgrade that puts a door (or a bed) where there was a wall: the builder now takes down the bit of wall where the
  door's top half goes, instead of skipping the door.

## 0.57.0 — 2026-09-28

### Added
- **Guards show their armor**: the helmet, chestplate, leggings and boots a guard wears are drawn on them, fitted to
  the villager's shape (dyed leather and enchantment shine included). Any villager wearing armor shows it the same way.

## 0.56.0 — 2026-09-28

### Added
- **Air mail**: with a Flying-type Pokémon in a pasture near the Postal Desk, parcels for mailboxes outside the
  postman's round are delivered as soon as they're handed in, instead of with the next dawn's mail.

## 0.55.0 — 2026-09-28

### Added
- **Ball Smith orders**: sneak-right-click a Ball Smith with an empty hand to choose which balls they make. With
  nothing chosen they make whatever the chests have the makings for, as before.

## 0.54.0 — 2026-09-28

### Added
- **Orchard Keepers plant orchards**: give one a Field Marker with an area marked and they plant it from the chests
  by the Fruit Basket — sweet berry bushes, and with Cobblemon berries (on farmland) and apricorn trees — then pick
  what grows there. Sneak-right-click the keeper to see the orchard or stop it.

## 0.53.0 — 2026-09-28

### Added
- **Price Tags** for shops: craft them from paper, a gold nugget and string, rename one in an anvil to a price
  (`250`) and put it in a Shop Counter's price row. The column then costs exactly that many CobbleDollars; without
  CobbleDollars it's paid in emeralds (100 to the emerald, rounded up).

## 0.52.0 — 2026-09-28

### Added
- **Guards use crossbows**: a crossbow in the guard's chest beats a bow. Bolts fly faster and straighter and hit
  harder; the guard reloads a little slower than with a bow.

## 0.51.0 — 2026-09-28

### Added
- **Letters**: the mailbox screen has a *Letter* line. Whatever you write there goes with the parcel as a letter (a
  book the recipient can read), or on its own if you're not sending anything else.
- **Mail tracking**: `/workplace mail` (or **[Track]** after posting) lists the parcels on their way to and from you
  and where they are: waiting for a postman, in a postman's bag, or in the night mail.

## 0.50.0 — 2026-09-28

### Added
- **Fishers smoke their catch**: put a smoker (or furnace) near the fisherman's barrel and coal or charcoal in the
  barrel. At every drop-off the raw cod and salmon go in and the cooked fish come out into the barrel.

## 0.49.0 — 2026-09-28

### Added
- **Farmers use bone meal**: put bone meal in the chest by the composter and, once the field is sown and nothing is
  ripe, the farmer uses it on the growing crops (never on grass).
- **Farmers pick sweet berries and cocoa** (and glow berries) growing in their field, leaving the plants to grow again.

## 0.48.0 — 2026-09-28

### Added
- **Tree farms**: give a lumberjack a Field Marker with an area marked (within 48 blocks of their Chopping Block) and
  they keep it planted with saplings from the chests, in a grid three blocks apart, and fell the trees that grow
  there. Dark oak goes in as 2 × 2 squares. Sneak-right-click the lumberjack to see the farm or stop it.
- Lumberjacks fell **huge crimson and warped fungi** standing on nylium, and plant a fungus back.

## 0.47.0 — 2026-09-28

### Added
- **Strip mines**: sneak-right-click the air with a Quarry Marker until it says *Strip mine*, then mark the corners at
  head height. The miner digs 2-high tunnels with 2 blocks of rock between them (and one across the end nearest the
  bench), and digs out any ore in that rock: all the ore for about a third of the digging.

### Changed
- A villager who becomes a guard gets the extra health straight away (40), instead of starting at 20 and healing up.

## 0.46.0 — 2026-09-28

### Added
- **Miners smelt their ore**: put a furnace or blast furnace near the Miner's Bench and coal or charcoal in the
  chests. At every drop-off the miner takes the finished ingots out into the chests, loads raw ores and ore blocks
  in, and tops up the coal. Anything you put in a furnace yourself is left alone.

### Changed
- Furnaces, blast furnaces and smokers near a workstation no longer count as supply chests (a builder or miner could
  put leftovers into their input slot).

## 0.45.1 — 2026-09-28

The first release with the 0.44.0 and 0.45.0 changes below (a flaky test stopped those builds before they were released).

### Fixed
- Quarries that the bug fixed in 0.45.0 had already stretched over the ground around the Miner's Bench now stop by
  themselves, before the miner digs any more: the miner hands back a blank Quarry Marker and tells you (if you're
  online) to mark the corners again. (The advice in 0.45.0 to reuse the returned marker was wrong: it carried the
  stretched corners.)

## 0.45.0 — 2026-09-28

### Added
- **Stairs out of the pit**: miners leave a staircase spiralling down the walls of quarries at least 3 × 3 and 3 deep
  (one step per layer, starting at the corner nearest the bench), so you and they can walk out. Sand and gravel steps
  are swapped for cobblestone and gaps are filled in. Quarries started before this update keep digging without stairs.

### Fixed
- **Miners no longer dig up the ground around their Miner's Bench.** Since 0.18.0, keeping a quarry loaded while its
  owner was online stretched the quarry to take in everything within 8 blocks of the bench (and down to the bench's
  level), so the miner dug that too. Quarries started from now on stay the size you marked; a quarry already under
  way may have been stretched already: sneak-right-click the miner, click **[Stop this quarry]**, and give them the
  marker they hand back (it still has your corners).

## 0.44.0 — 2026-09-28

### Added
- **An upgrade for every starter build**: **Market Stall II** (a second stall with a Shop Counter), **Lookout Tower II**
  (a Guard Post at the foot of the ladder, a bell and a pointed roof), **Healing Center II** (a ward with four beds)
  and **Supply Shop II** (a storeroom and the shopkeeper's bedroom upstairs).
- **Builders sell upgrades**: once a builder finishes a build that has an upgrade, they add its blueprint to their
  trades and tell you. Works for your own blueprints too (`my_house` → `my_house_2`).

## 0.43.0 — 2026-09-28

### Added
- **Upgrades**: the new **Starter Cottage II** blueprint adds a second storey to a finished Starter Cottage. Right-click
  the cottage with it and it lines up exactly; the builder only takes the old roof off and builds what's new. Your
  own blueprints can have upgrades too: name them `my_house_2`, `my_house_3`…

## 0.42.0 — 2026-09-28

### Added
- **Server config file** `config/aliveworkplace.json`: how far supply chests, builds, guards, lumberjacks, orchard
  keepers, fishers, Pokémon partners and postmen reach, and how many CobbleDollars an emerald price is worth.

### Fixed
- A guard with a bow no longer ignores a creeper that's out of bow range: they walk closer (keeping their distance)
  and shoot.

## 0.41.0 — 2026-09-28

### Added
- **Leave the ground as it is, per build**: right-click the air with a blueprint to switch off levelling around that
  build (for houses meant to sit in a hillside); right-click again to switch it back. The tooltip shows it.

## 0.40.0 — 2026-09-28

### Changed
- **Stronger trainers bring trained teams**: from Journeyman their Pokémon have better IVs, and Experts, Masters and
  Trainer Leaders add EVs, a fitting nature and a held item (Life Orb, Leftovers, Choice items, Focus Sash…). Same
  species as before. With an RCT level cap, the same trained Pokémon are simply brought down to the cap.

### Added
- **Special requests** from Expert and Master Pokémon Traders: besides their usual offers, one a day asks for a
  particular Pokémon (any of its evolutions will do) and gives one of theirs at the top of their range — shiny one
  time in four.

## 0.39.0 — 2026-09-28

### Added
- **Ferry houses** in villages: a travel post with a ferryman, which joins the travel network under a village name
  ("Willowbrook", "Amberhollow"…) the first time someone uses it. Right-click it once and ferrymen elsewhere sell
  tickets there.

## 0.38.0 — 2026-09-28

### Added
- **Guards use bows.** Put a bow in a guard's chest and they carry it: they shoot creepers from a safe distance
  (backing away if one comes close), shoot fliers and pick off monsters before they get close, then switch to the
  sword. They never shoot while a player, villager or pet is in the line of fire.

### Checked
- Farmers look after fields of Cobblemon mints too: ripe mints are picked (the leaves go to the chest) and planted
  again. Now covered by a test.
- A full Cobbleverse server boots cleanly with 0.37.0, and a new Repurposed Structures village had a workshop, a Trainer
  Leader's hall and an orchard house.

## 0.37.0 — 2026-09-28

### Added
- Villages grow **orchard houses** (a Fruit Basket, a chest of fruit and an indoor bed of sweet berry bushes) and, with
  Cobblemon, **ball workshops** (a Ball Workbench and a chest of copper and dye), each with a villager to work there.

## 0.36.0 — 2026-09-28

### Added
- **Ball Smiths** (Ball Workbench, with Cobblemon): they turn the apricorns and ingots in the chests nearby into Poké
  Balls with Cobblemon's own recipes — copper balls at first, then iron, gold and diamond ones as they level up —
  taking turns between kinds and stopping at 64 of each. Steel and Fire Pokémon in a pasture nearby help.

## 0.35.0 — 2026-09-27

### Added
- **Shops take CobbleDollars.** With CobbleDollars installed, right-clicking a shopkeeper opens the shop's own screen:
  everything in stock, emerald prices shown and paid in CobbleDollars (100 per emerald), two clicks to buy. The money
  goes straight to the shop's owner, or waits for them until they next join. Sneak-right-click for the old trade
  screen.
- **Shop sales log**: the owner sneak-right-clicks the Shop Counter to see the latest sales (who bought what, for how
  much, on which day) and the shop's totals.
- **Ferry fares in CobbleDollars**: with CobbleDollars, a ferryman's screen lists your destinations with their fares
  in CobbleDollars; two clicks buy the ticket.

### Fixed
- Every new workshop, guard house, clinic and other staffed house now gets a jobless adult villager. They used to come
  from the village's own villager pool, which sometimes gave a nitwit (who never works), a baby or, with
  CobbleDollars, a Cobble Merchant.

## 0.34.0 — 2026-09-27

### Added
- **Pokémon partners** (with Cobblemon): Pokémon kept in a Pasture Block within 16 blocks of a workstation help that
  villager when their type suits the job — Fighting/Rock/Steel types build, Ground/Rock/Steel types mine, Grass
  types tend orchards, trees and fields, Water types fish, and so on. Each cuts the work time by 15% (up to three);
  the villager's overhead line says who is helping.
- **Cobbleworkers support**: a postman's courier route can start at a Pasture Block and empties every chest and
  barrel around it (where Cobbleworkers' Pokémon put what they gather).

## 0.33.0 — 2026-09-27

### Added
- **Orchard Keepers** (Fruit Basket workstation): they pick ripe sweet berries, glow berries and cocoa pods — and,
  with Cobblemon, apricorns and berry plants — within 16 blocks of the basket, and store the harvest in the chests
  nearby. The plants are picked, not broken, so they grow again. They trade fruit, apricorns and berries.

### Fixed
- Workers no longer choose a berry bush, fire, powder snow or a cobweb as a place to stand.

## 0.32.0 — 2026-09-27

### Added
- Villages grow three more houses with Cobblemon installed: a **Trainer Leader's hall** (common, so most villages
  get a leader), a **school** with a Move Tutor and a **trade hall** with a Pokémon Trader. Without Cobblemon,
  villages no longer grow trainer's houses (their villagers had nothing to do).

### Fixed
- The villager moving into a new clinic or post office could become a leatherworker or librarian (the house had a
  cauldron or a lectern). Those are gone; every staffed house now has exactly one job block.

## 0.31.0 — 2026-09-27

### Added
- **Guards answer the village bell.** Ring it and, while everyone else runs home to hide, the guards head for the
  bell and fight anything near it for a minute and a half.

### Checked
- Booted a real Cobbleverse 1.7.42 server (all 136 mods) with Alive Workplace: it starts cleanly, and Repurposed
  Structures villages generate with our houses.

## 0.30.0 — 2026-09-27

### Changed
- **Trainers match your Radical Cobblemon Trainers level cap** (RCT is in the Cobbleverse pack): their Pokémon go
  no higher than your cap −6 (Novice), −3, ±0, +3 or +5 (Master). Weaker teams stay as they are. Without RCT nothing
  changes.

## 0.29.0 — 2026-09-27

### Fixed
- Builders (and every other worker) saw **Sophisticated Storage** chests and barrels as empty and waited for
  materials that were right there. They now use them like any chest.

### Changed
- Storage-network blocks (Tom's Storage connectors, terminals and cables; the Sophisticated Storage controller and
  its links) no longer count as supply chests, so the chests behind them aren't counted twice.
- Tested with the Cobbleverse pack's own building mods: Handcrafted, Beautify, CobbleFurnies, Carved Wood and Moar
  Concrete (every block builds and costs its item).

## 0.28.0 — 2026-09-27

### Changed
- **Move Tutor lessons cost CobbleDollars** when that mod is installed (300 to 2,400, the money you win from
  trainers); emeralds as before without it. Trainer prizes now go straight into your CobbleDollars balance instead
  of through a command.

### Added
- **Repurposed Structures villages** (in the Cobbleverse pack) now grow our builder's workshops, trainer's houses,
  guard houses, clinics and post offices too, in the closest matching style (birch and oak villages get plains
  houses, badlands desert ones, and so on). Nether and ocean villages are left alone.

## 0.27.0 — 2026-09-27

### Added
- **Water and lava in blueprints**: fountains, pools and fireplaces get built too. The builder pours each still water
  or lava block from a bucket in the chests once the walls are up (flowing water fills in by itself) and puts the
  empty bucket back. Blueprint materials list the buckets.
- **Item frames, paintings and armor stands in blueprints** go up when the building is finished, paid for with an
  item frame, painting or armor stand from the chests, and turned to match the building. They're always put up
  empty: a blueprint never hands out what was in a frame or on a stand. Works with structure files, .litematic and
  .schem. If some are missing, the builder says so; stock them and hand the blueprint over again.

## 0.26.0 — 2026-09-27

### Added
- **Ground levelling**: when a building is finished, the builder tidies the ground two blocks around it — natural dirt,
  stone, sand and grass sticking up above the floor is dug away (up to 6 blocks high) and holes at floor level are
  filled with dirt. Trees, flowers, farmland, water and anything built are left alone, and it never holds a build
  up waiting for dirt. `/gamerule workplaceLevelGround` sets the width (0 turns it off).

## 0.25.0 — 2026-09-27

### Added
- **What's still missing**: a placed blueprint's tooltip now says what the chests by the nearest Builder's Bench
  are short of (hold Shift for the list), or that everything is there. Blocks already standing where the blueprint
  wants them don't count. It updates every couple of seconds while the blueprint is in your inventory.

## 0.24.0 — 2026-09-27

### Added
- **Pokémon Traders** (with Cobblemon). Craft a **Trade Board** (an item frame on planks) for a villager and
  right-click them to see today's offers: one of their Pokémon for any of yours of a given type and level. Pick an
  offer and a Pokémon that fits, click twice, and they swap (held items come back to you). New offers every in-game
  day, one trade per player per trader per day; traders rank up with every trade — more offers, stronger Pokémon,
  the odd shiny at Master. Sneak-right-click to buy Poké Balls, Exp. Candy, Rare Candy and more for emeralds.

## 0.23.0 — 2026-09-27

### Added
- **Move Tutors** (with Cobblemon). Craft a **Tutor's Desk** (a book on planks) for a villager and right-click them:
  pick one of your Pokémon and a move it could learn (tutor, TM and egg moves), click twice, pay in emeralds and it
  knows the move — straight into its moves if there's room, otherwise ready to swap in from its summary. New tutors
  teach weaker moves; they rank up with every lesson until a Master teaches anything. Tutors buy paper and books too.

### Fixed
- Trainers without a name tag no longer show their job twice ("Novice Trainer Trainer").

## 0.22.0 — 2026-09-27

### Added
- Villages grow more of our houses: a **trainer's house** (often — most new villages get one), a **guard house** with a
  chest of starting gear, a **clinic** and a **post office**, each in the village's own style and with a villager who
  takes the job. Only villages generated after updating get them.

## 0.21.0 — 2026-09-27

### Added
- **Trainer Leaders.** Craft a **Leader's Podium** (gold ingots either side of a Training Post, on polished andesite)
  and a villager becomes the village's Trainer Leader: they battle at Expert strength from day one, pay three times
  the usual prize, and take one challenge a day from each player. One leader per village — if there are several, the
  most experienced one takes the challenges.

## 0.20.0 — 2026-09-27

### Added
- **Pokémon Trainers** (with Cobblemon). Craft a **Training Post** (a target block on planks) and a villager becomes a
  Trainer. Right-click one with an empty hand to battle their team. Trainers start as Novices (2 Pokémon around level
  10) and rank up every time anyone battles them, all the way to Master (6 fully evolved Pokémon, level 80–100, and a
  much smarter battle AI). Beating a trainer pays CobbleDollars (emeralds if CobbleDollars isn't installed) once a day
  per trainer. No badges, no gyms.

## 0.19.0 — 2026-09-27

### Added
- **Bards.** Craft a **Music Stand** (paper on a note block) and a villager becomes a Bard. Twice a day — a morning
  set and an evening set while the village gathers — they play the music discs from the chests near the stand, one
  after another (the discs stay in the chest). With no discs they make up a tune on the harp.

## 0.18.0 — 2026-09-27

### Added
- The **Healing Center** blueprint now has a Nurse Station behind the counter and the **Supply Shop** a Shop Counter,
  so building them brings in a nurse and a shopkeeper (the first player to open the shop's counter owns it).
- Builds and quarries keep going while you're away: as long as the player who ordered them is online, the chunks
  with the site, the workstation, its chests and the worker stay loaded. Turn it off with
  `/gamerule workplaceKeepWorkLoaded false`. Nothing is kept loaded for players who are offline.

## 0.17.0 — 2026-09-27

### Added
- **Courier routes.** Postmen now also carry things between your chests when there's no mail. Craft a **Delivery
  Note** (paper, a feather and an ink sac), right-click the container to take from and then the one to bring to —
  for example your miner's chest and your builder's chest — and give it to a postman. By default they carry
  everything except tools, weapons and armor; hold an item in your other hand and right-click the air with the
  note to carry only that kind (up to 9 kinds). A postman runs up to 4 routes; give them a blank note to end them.

## 0.16.0 — 2026-09-27

### Added
- **Ferrymen and travel posts.** Craft a **Travel Post** (a sign over planks and a boat), name it in an anvil if you
  like, and place it in each village or base you want to connect; a villager takes it as a **Ferryman**. Right-click a
  post to add it to the posts you know. A ferryman sells **Travel Tickets** to every other post you know — 1 emerald
  per 256 blocks. Use a ticket near any travel post and you arrive next to the ticket's post.

## 0.15.0 — 2026-09-27

### Added
- **Shops.** Craft a **Shop Counter** (an emerald over planks and a chest) and place it: it's your shop, and a
  villager takes it as a **Shopkeeper**. Open the counter to set prices: in each column, the top slot is what one
  sale hands over (say 16 cobblestone) and the slot under it is the price (say an emerald — any item works). Put the
  goods in chests within 8 blocks of the counter. Players trade with the shopkeeper like any villager; what's for
  sale is whatever the chests hold, and the payment goes into the chests for you.

### Fixed
- Builders, miners and other workers could take items out of a mailbox within 8 blocks of their workstation, or
  drop their haul into it. Mailboxes are now private.

## 0.14.0 — 2026-09-27

### Added
- **Nurses.** Craft a **Nurse Station** (glass bottles around a glistering melon slice, on white wool) and a villager
  becomes a Nurse. Right-click a nurse with an empty hand to get your health back and poison, wither and other bad
  effects cleared — and with Cobblemon installed, your whole team healed too (not during a battle). Once a minute per
  player, less as the nurse levels up. While at work, nurses also look after hurt villagers and iron golems nearby,
  guards included. Sneak-right-click to trade: they sell healing potions, honey and golden food.

## 0.13.0 — 2026-09-27

### Added
- **Guards.** Craft a **Guard Post** (an iron sword over planks and a shield) and a villager becomes a Guard. Put
  weapons and armor in a chest near the post and they take the best of it. Guards fight any monster that comes
  within 24 blocks of their post, day or night — except creepers — and never run away. They have twice a villager's
  health, heal between fights and hit harder as they level up. They keep the night watch and sleep in the late
  morning. Players, villagers, animals, pets and Pokémon are safe from them.

## 0.12.0 — 2026-09-27

### Added
- **Mail and postmen.** Craft a **Mailbox** and place it: mail for you arrives there and a red flag goes up. Open it,
  put items in the top row, write a player's name and press Send. Craft a **Postal Desk** and a villager becomes a
  **Postman**: they collect parcels from mailboxes within 64 blocks of the desk and carry them to the recipient's
  mailbox. Parcels for mailboxes further away go with the night mail and arrive at dawn. Players hear when mail
  arrives and when theirs was delivered.

## 0.11.0 — 2026-09-27

### Added
- **Fishermen fish for you.** Hand any Fisherman villager (the ones with a barrel) a fishing rod. They walk to the
  nearest water within 16 blocks of their barrel, cast from the shore and reel in fish (and the odd bit of junk —
  no treasure), bringing the catch back to their barrel and the chests next to it. Rods wear out; put spares in
  the barrel. What they're doing and how much they've caught shows above their heads; sneak-right-click them for
  details and a button to stop.

## 0.10.0 — 2026-09-27

### Added
- **Farmers look after your fields.** Craft a **Field Marker** (stick + wheat seeds + paper), right-click two
  opposite corners of a field (up to 32 × 32) and give the marker to any Farmer villager. They harvest what's
  ripe and plant the same crop straight back, sow empty farmland with seeds from the chests near their composter,
  till bare dirt and grass with a hoe from those chests, cut sugar cane down to its bottom block, pick pumpkins
  and melons, and put the harvest in the chests. Works with modded crops too. Their field and what they're doing
  shows above their heads; sneak-right-click them for details and a button to stop. Farmers also sell the marker.

### Changed
- Workers can now stand on farmland and dirt paths while they work.

### Fixed
- A lumberjack whose tree dropped no sapling left the stump bare; they now bring one from the chests.

## 0.9.0 — 2026-09-27

### Added
- **Lumberjacks** — the third job. Craft a **Chopping Block** (a stone axe on top of any log) and a villager without
  a job takes it. Put **axes** in a chest within 8 blocks of the block: the lumberjack cuts down the trees within
  16 blocks, one at a time — leaves first, then the trunk — plants a sapling of the same wood where each tree stood,
  and stores the logs, sticks and apples in the chests. Only real trees are cut: logs someone placed (houses, posts,
  anything with placed leaves), blueprint builds and quarries are left alone. Axes wear out; when the last one breaks
  they wait for another. What they're doing and how many trees they've cut shows above their heads. Lumberjacks
  level up like the other workers and trade sticks and apples for emeralds, and sell logs, saplings and an iron axe.

## 0.8.0 — 2026-09-27

### Added
- **Miners** — the second job. Craft a **Miner's Bench** (cobblestone, a stone pickaxe and planks) and a villager
  without a job takes it. Mark out a quarry with a **Quarry Marker** (stick + red dye + paper): right-click two
  opposite corners, sneak-right-click the air to choose how deep (4 to 64 blocks), then give it to the miner.
  They dig it out layer by layer with a pickaxe from the chests near their bench (bring spares — pickaxes wear out
  and better ones dig harder blocks), put everything they dig into those chests, light the pit with torches if
  there are any, and never break through to lava or water. Progress shows above their heads; sneak-right-click
  them for details. Miners level up like builders and trade coal, ores, torches and pickaxes.

### Fixed
- The villager in a builder's workshop could become a fisherman (the workshop had a barrel) instead of a builder.

## 0.7.0 — 2026-09-26

### Added
- Villages grow builder's workshops (plains, desert, savanna, snowy and taiga styles, in roughly every other new
  village): a small house with a Builder's Bench, a bed, and a chest of building supplies that sometimes holds a
  Starter Cottage or Market Stall blueprint. The villager living there becomes a builder on their own.
- Taking buildings down: place a blueprint over a building and **sneak** while giving it to a builder. They take it
  down carefully (torches and doors first, then from the roof down) and put every block back in the supply chests —
  glass comes back as glass. Only blocks that match the blueprint are touched; chests, furnaces and other blocks that
  hold things are left standing.
- Builders have a boss: the first player to hand a builder a blueprint hires it. After that only they, the friends
  they add with `/workplace friend add <player>` (and operators) can give it blueprints or cancel its builds, and it
  only helps its boss's and their friends' builds. `/workplace friend list` and `/workplace friend remove` manage the
  list; `/gamerule workplaceBuilderOwnership false` turns this off.

## 0.6.0 — 2026-09-26

### Added
- Crews: builders with nothing to do help with builds near their bench (up to 3 helpers per build). They work
  alongside the builder in charge, share the same supply chests, pass each other spare materials, and head home when
  the build is done. Their heads say who they're helping. Handing a helper its own blueprint takes it off the crew.
  Turn it off with `/gamerule workplaceBuildersHelp false`.

### Changed
- "Waiting for materials" messages come at most once a minute per build, and don't count materials a crew mate is
  already carrying as missing.

## 0.5.0 — 2026-09-26

### Added
- Works with **Chipped** and **Rechiseled**: builds that use their block variants (herringbone oak planks, oak beams…)
  can be built from the plain block — the builder "chips" it on the spot, just like the workbench or chisel does for free.
  Missing-material messages, the Blueprint Table and the blueprint tooltip name the plain block ("12 Oak Planks").
- Works with **Supplementaries**: way signs (they cost the fence and the sign), rope knots, timber frames, flower
  boxes, jars, item shelves, flags, blackboards and the rest build correctly. Book piles are left out (they're made of books).
- Potted plants are built (they cost a flower pot and the plant) instead of being skipped.

### Fixed
- Blueprint block data can no longer hand out free items or mobs from any mod: containers (chests, jars, shelves,
  safes…) are always built empty, and item or mob data hidden inside other blocks is removed. Safes and locks are
  never copied with their old owner or password.

### Dev
- `./gradlew runCompatGameTest` (part of `build`) runs in-game tests with Chipped, Rechiseled and Supplementaries installed.

## 0.4.0 — 2026-09-26

### Added
- Builders level up by building: every 5 blocks placed and every finished build earn villager XP, and they level up
  just like villagers you trade with. Each level makes them faster (a Master builder works 2.5× as fast as a Novice)
  and unlocks their next blueprints for sale. Their owner gets a message when they level up, and sneak-right-clicking
  a builder shows their level and XP.
- Build queue: hand a busy builder more placed blueprints (up to 5) and they build them one after another.
  Queued builds reserve their spot, show up in `/workplace sites` and the builder's status ("Next up: …"),
  and can be cancelled on their own.
- Hold Shift over a blueprint to see the materials it needs.
- Two new blueprints: the **Healing Center** (white walls, red roof; sold by level 4 builders) with a Cobblemon
  Healing Machine on the counter when Cobblemon is installed, and the **Supply Shop** (blue roof, shelves and a
  counter; sold by level 5 builders).

## 0.3.0 — 2026-09-26

### Added
- See-through preview: hold a placed blueprint to see the whole building as ghost blocks where it will stand.
  Blocks that are already built disappear from the preview, so you can watch it fill in.
- Builders show what they are working on above their heads: the build's name, percentage, a progress bar,
  and what they are doing or waiting for (e.g. "Needs: 12 Glass").
- Foundations: on uneven ground, builders fill the gap under the floor down to the ground (dirt under grass,
  the floor's own block when it is a plain block, otherwise cobblestone) instead of leaving the house on stilts.
  `workplaceFoundationDepth` gamerule sets how deep they go (default 12, 0 turns it off).

## 0.2.1 — 2026-09-26

### Fixed
- Builders could freeze when standing on the spot they needed to build (found in the first playtest):
  they now actually step aside, and hop if they can't.
- Builders pick work spots that aren't where the building still needs blocks, so they stop getting in their own way.
- Pokémon, animals and other mobs standing on a build spot get shooed off (and moved next to the site if they won't budge);
  players in the way get a polite message. Builders work on other blocks meanwhile instead of waiting.
- Sneak-right-clicking a builder now says what it is waiting for (e.g. "Waiting for Bulbasaur to move out of the way").

### Dev
- `./gradlew runGameTest -PbuilderDebug=true` logs every builder's decisions.

## 0.2.0 — 2026-09-26

### Added
- Blueprint Table: browse every blueprint on the server with its size and full materials list, and take a copy for a Blank Blueprint (craft paper + blue dye).
- Upload your own builds: put `.litematic` or `.schem` files (e.g. from Planet Minecraft) in your `blueprints` folder, pick one at the Blueprint Table and press Upload — it becomes a blueprint everyone on the server can use.
- Imports read Litematica files, WorldEdit/Sponge schematics (v1–v3) and vanilla structure files; old block names are upgraded automatically and blocks from missing mods are reported.
- `/workplace import` (ops) imports files dropped into `<world>/aliveworkplace/import/`.
- `workplaceAllowUploads` gamerule.

### Fixed
- The blueprint list no longer shows other mods' world-generation structures.

## 0.1.0 — 2026-09-26

### Added
- Builder villager profession and the Builder's Bench workstation.
- Blueprints: place (faces you), rotate, pick up; particle outline preview; hand to a builder to start a build.
- Builder AI: clears the site, fetches materials from storage near the bench (modded storage included),
  builds structure then decorations, waits and reports missing materials, returns the blueprint when done.
- Starter blueprints sold by builders: Starter Cottage, Market Stall, Lookout Tower.
- `/workplace` command and the `workplaceFreeMaterials` / `workplaceBuildDelay` gamerules.
- Headless in-game test suite covering building, waiting for materials, clearing, rotation, cancelling and the starter builds.
