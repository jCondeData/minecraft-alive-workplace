# Changelog

## Unreleased

### Added
- Server owners get two new settings for big villages: `maxWorkersPerVillage` (jobless villagers stop taking free
  workstations once a village has that many workers; nobody loses a job) and `workerPathRange` (how far workers look
  for a path in one go). Every config option, including each village system's on/off switch, is now in the README.
- Mirroring a blueprint you already placed (on the style screen) flips it where it stands, and its ghost shows it at
  once. Cancelling a build gives the blueprint back still placed: hand it back to carry on, or click the ground to
  move it; what the builder carried goes back to its chests.
- Builders are now tested against getting stuck: five long runs trap a builder in water, beside lava, in holes, behind
  fences, in a room with a door, on its own roof and through chunk reloads; it gets out every time, finishes the build
  and never breaks a block it placed.
- Importing builds tells you more: blocks from mods that aren't installed are named in the message ("…became air:
  create:shaft, create:andesite_casing"), a build made only of such blocks is refused naming them, a file cut short in
  a download says so, and a damaged Litematica file names the region. Builds from Minecraft 1.12 (before the
  flattening) and WorldEdit's first `.schem` version import, and big builds (48 × 8 × 48) read the same from
  `.litematic`, `.schem` and `.nbt`.
- **A settings screen**: with Mod Menu installed, the mod's Configure button opens every setting of
  `config/aliveworkplace.json` as sliders and on/off buttons, each with a tooltip saying what it does. Closing the
  screen saves the file and puts the settings into effect in your own worlds; a server keeps its own file.
- **Partners at work**: a Fighting-type Pokémon pastured by a Builder's Bench is now seen helping: when the builder
  fetches materials it walks over to the work (never leaving its pasture's range) carrying them, lends a punch and walks
  back. Shows are data packs (`data/<namespace>/partner_shows/`); setting "Partners at Work" turns them off.
- **Partners at work for everyone else**: a Ground, Rock or Steel partner digs alongside the Miner in a shower of
  crumbs; a Water or Ice one swims round the Fisherman's bobber; a Psychic one floats a book among enchanting glyphs
  beside the Scholar (and, Psychic or Normal, beside the Teacher in class); a Fairy, Normal or Psychic one sends a pink
  pulse over whoever the Nurse heals or cures; a Poison or Grass one stirs the Composter's bin (green bubbles); a Grass
  or Fairy one sprinkles the Florist's garden; a Bug or Grass one circles the hive being harvested; a Ground or Rock one
  shakes the dust from the Sifter's sieve; a Fire or Dark one walks the Netherworker to the portal, flames at its feet;
  a Flying or Ground one scouts ahead as the Cartographer sets out; a Normal or Ground one walks beside the wild horse
  the Rancher is breaking in.
- **Partners at work in the post, the forge and the kitchen**: a Flying partner by the Postal Desk takes the air mail
  up with a bundle, climbs out of sight and lands back empty-handed, and on the round flies ahead to the next mailbox.
  A Fire partner breathes fire into the furnace, blast furnace or smoker each time it smelts on the spot for an Armorer,
  Miner or Fisherman. By the Chef's stove a Fire partner fans the flames and a Normal one carries the dish to the
  chest. A Steel or Fire partner sparks at the Toolsmith's or Ball Smith's table, then carries the new tool or the
  batch of balls to the chest; a Steel or Fighting one holds the worn piece at the Weaponsmith's grindstone; a Flying
  one brings the Fletcher a feather and a Bug one string; an Electric or Steel one sparks over the Tinkerer's work and
  over the iron golem being mended.
- **Partners at work on building and the land**: pastured Pokémon now help more workers, each by its type. For the
  builder, a Fighting partner shoulders the logs and planks and punches each block home, a Rock one carries the stone
  and a Steel one the iron parts (bars, doors, chains, lanterns). A Fighting or Normal partner hauls a barrel along on
  the Porter's round; a Fighting, Rock or Steel one holds the board or stone at the Carpenter's or Mason's table. A Water
  partner waters the patch the Farmer tends (that farmland turns fully moist), a Grass one sparkles over the crops and a
  Ground one walks the furrow being tilled. A Fighting partner punches the Lumberjack's trunk and a Grass or Bug one
  brings the sapling to the stump; a Flying or Bug one flutters through the plant the Orchard Keeper picks. Show files
  can now limit what's carried with `carry_tag`, and the toolbox has `crack` and `dust`.
- **Cobblemon 1.8**: tested with Cobblemon 1.8.1 as well as the pack's 1.7.3 (every Pokémon job passes its tests on
  both); 1.8 no longer logs an "outside the tested versions" warning.
- Names read the same everywhere: the Fisherman is never "Fisher", and the mod's blocks and items keep their capitals
  in messages ("Travel Post", "Shop Counter", "Field Marker", "Delivery Note", "Training Dummy").
- Bug reports on GitHub now use a form that asks for the version, the steps, `latest.log` and any crash report.
- Over a builder's head, under its progress: the three materials the build is shortest of, with counts (or that it
  has everything), and where the builder takes materials from (its chests, or the storehouse, by its bench at x y z).
- **The village calendar**: the world now has four seasons of 8 days (`seasonDays` in the config), each with a festival
  on its middle day: the Blossom Fair, the Midsummer Games, the Harvest Feast and the Lantern Night. The Village Hall
  shows the season, the day and the next festival; nothing else changes with the seasons yet (coming expansions use it).
- **Room on the Village Hall's screen**: a row of page tabs under the hall's buttons, with the calendar as the first
  tab and room for eight more pages. Every button stays where it was; the villager list starts one row lower.
- **The Guide Book**: every player is given one the first time they join (on an existing world too, after updating);
  right-click it to read how the mod works, page by page, each page an in-game screenshot with a few short steps:
  getting started, giving villagers jobs, builders and blueprints, the Village Hall, every kind of job, guards, and
  with Cobblemon the Pokémon jobs. Lost it? Craft another from a book and wheat.
- The server log now says "Builder stalled" when a builder's site makes no progress for 30 seconds (once per stall,
  with the build, its stage and what's missing), so a stuck builder shows up without anyone watching. For testers,
  `/workplace soak` (benchmark servers only) sets 10 builders on the whole starter set in hilly woods for 2 days (or more) and
  checks that no item was duplicated or lost.

- **A material list you can take away**: hold a Book and Quill in your other hand and right-click with a blueprint.
  The book lists everything the build needs, biggest first, as a checklist; placed near a Blueprint Table, what its
  chests already hold is taken off, so it says exactly what is still to bring.
- **Villages keep working when nobody is near**: while anyone is online, every worker's workstation (and the chunk the
  worker is in) stays loaded, so a village far from all players carries on, also right after a restart. Server owners
  who'd rather pause them set `keepVillagesWorking` to `false` in `config/aliveworkplace.json`.
- `/workplace soak <days> split` (testers, benchmark servers only): the soak with each builder's materials split
  between at most 3 chests by its bench and the village storehouses.

### Changed
- Seasons last 16 days now (a 64-day year), so festivals come every 16 days. A config file that still holds the old
  default of 8 moves to 16 by itself; any other length you chose stays.

### Fixed
- **Helpers really speed a build up now**: two builders on one build take about half the time of one, four about a
  third (the stone house: 4200 ticks alone, about 2110 with two, 1220-1470 with four; before, 63-71% and 46%). Helpers
  fetch for a stretch of work instead of a handful per block, pass each other materials when close instead of walking
  over, go for the free block nearest them, help with the end of each stage, and the lead takes work within reach
  instead of walking off while a helper is on its next block.
- A build's progress no longer drops after a server restart while its foundation is being filled (it showed 12%,
  then 1%).
- Builders use the village storehouse on their own: one whose bag is full while its chests are full (or it has none)
  empties it into the storehouse's chests instead of dropping it on the ground, and so does a builder finishing a
  build; a storehouse no longer drops out of the village while its porter is out on a far errand; and a builder whose
  next site is far away takes its first materials from the storehouse too.
- Screens checked at GUI scales 2 and 4: the Shop Counter's title no longer runs off its panel ("Goods on top, prices
  below"), and the Blueprint Table's list no longer draws a blueprint's size over its selection box, nor 3-digit
  material counts over the next icon.
- Messages with a number say "1 parcel" and "3 parcels" instead of "parcel(s)", and item counts read "28× Spruce
  Planks" everywhere (the Village Hall's requests, research costs, the shop's sales log), as the builder's already did.
- A builder whose next build is far from its chests takes the first materials along before clearing the site, instead
  of walking all the way back for them once the site is clear (it stood idle for over half a minute).
- With Chipped or Rechiseled installed, a builder carrying a variant of the block it needed (taken in place of
  another block of the same kind) no longer stands waiting for materials with nothing on its missing list: it turns
  the variant into the block it needs and carries on.
- The Village Hall's name tag now says what a shift-click will do: protect an open village, or open a protected one
  to everyone again (it always said "protect").
- Masons and carpenters helping one builder no longer use up the stone or wood another builder's build is waiting
  for: from that builder's chests they take only what's spare.
- Nurses, shopkeepers and ferrymen keep their block when they get stuck on the way back to it, instead of giving it up
  after a minute and taking another worker's.
- With several builders in a village, a builder who runs short no longer empties another builder's chests of what
  that builder's own build needs (which left it waiting for glass panes it had been given): it takes only what's spare.
- Builders on a hillside, with their site far up the slope from their bench, keep placing through clearing,
  foundation and levelling without half-minute pauses (now covered by tests).
- With Cobblemon, a herder no longer stands waiting under a pastured Pokémon that's flying about: they get on with
  other work and brush or milk it once it has come down.
- Every flower now makes a Florist at a composter, pink petals and spore blossoms too, and the composter's tooltip
  says "Any flower" instead of "A small flower".
- Miners, postmen, tutors, Pokémon traders and trainers keep their workstation too: after a minute stuck, or over 100
  blocks from it, they gave it up and could take another worker's.
- A builder keeps their own bench. A builder who got stuck for a minute, or wandered over 100 blocks from their bench,
  gave it up and took the nearest free one, often another builder's, so two builders could swap benches over and over.
- A builder always takes its materials from the chests by the bench its build was started from. Two builders with
  benches close together could swap benches mid-build and use each other's materials, so one build ended a few
  blocks short and its builder waited for materials its own chests had held.
- A builder no longer stands waiting for materials with nothing on its missing list when the last of a block is
  in a helper's pockets: helpers keep only what the block they are working on needs and pass the rest over.
- Two villagers no longer end up on one workstation when a job is changed by hand or at the Village Hall: a worker far
  from a block that was broken and put back no longer takes it back from its new (unloaded) owner, a job chosen at a
  replaced block holds it, and a Village Hall assignment that fails leaves the worker's own block alone.
- A tall flower (sunflower, lilac, rose bush, peony) given to a farmer at their composter now makes a Florist, as the
  Guide Book says; before, only small flowers did and a tall one silently did nothing.
- Builders levelling the ground carry enough dirt from their chests for the whole hollow, instead of walking back for
  every block (and keep the dirt they dug up for it), so tidying up around a build far from its chests no longer crawls.
- Warding now protects the whole village, corners included: a creeper or TNT going off in a corner of a warded
  village, or just past its edge, no longer breaks the village's blocks.
- The Village Hall's Guards and Mercenaries buttons, and Research's Drill topic, no longer show an iron sword's
  "When in Main Hand: 6 Attack Damage, 1.6 Attack Speed" lines under their own text.
- Giving a villager a new job no longer frees a workstation someone else now works at when that other villager is
  far away in an unloaded chunk (their block had been broken and put back while the first villager was away).
- Leaving the game in the middle of a ferry ride lands you by the far post before you are saved, so you come back
  there, and the ferryman stays at his jetty. The ride now ends on the server thread as you leave, not on the network
  thread.
- The nightly modpack test ran without the pack's configs, datapacks and bundled mods (the pack stores them unreadable
  for anyone but an administrator), so its performance soak measured idle villagers. It now loads all of them, and
  stops if any are missing.
- The orchard house test that failed about 1 run in 50: it placed the house with its air, which dug a pit round it in
  the test floor that the orchard keeper could fall into. Villages never place that air, so players weren't affected.
- Giving a worker a new job (with an item or from the Village Hall) no longer frees another villager's workstation,
  when the worker's old one was broken while they were away and put back for someone else; and a far-off worker who
  still remembers that block isn't sent back to it.
- A lumberjack whose felled tree dropped no sapling (always with a Silk Touch axe) now replants that stump as soon as a
  sapling of its kind is in the chests, instead of forgetting it.
- The mod's recipes now show in the recipe book, each as soon as you hold one of its ingredients (none ever did).
- Zombie villagers in abandoned desert villages no longer get stuck in a house's wall (the same narrow corridor that
  trapped living villagers before 0.138.0).

### Tests
- The showcase's soak scene passes again: its check reads "items off: none" before the village chunk count, and a
  villager brushing an open door's panel is no longer reported as stuck in a wall (B42, B44).
- The builder soak no longer counts a supply run to far chests (30 s there and back without a block placed) as a
  stall: the split soak now ends with 0 stalls (B40).
- The showcase's title check measures from the title to the panel's right edge, so the player inventory's
  "Crafting" no longer fails the `missing` scene (B41).
- Every value the mod saves on a villager (all 72: a builder's site and bag, a lumberjack's tree farm, a couple's
  marriage, every worker's count...) is now tested to survive the villager being saved and loaded again.

## 0.138.0 — 2026-10-02

### Changed
- **New item icons, picked by the owner**: the Blueprint is clipped to a drawing board, the Blank Blueprint is two
  sheets, the Shape Planner is a brass compass, the Village Ledger lies open, the Delivery Note is a clipboard, the
  Travel Ticket has a sailing boat, the Price Tag a $ sign, the Rally Banner is an upright standard with crossed swords,
  the Settler's Wagon is seen from the side, the Patrol Map and Field Marker are maps on vanilla's map outline, the
  Quarry Marker has a chequered swallowtail flag on a stake, and the Scan Tool is a drafting pencil.
- **Fewer job blocks**: our jobs now work at vanilla blocks, shared with the vanilla job there. Stand a villager by the
  block and sneak-right-click them with the job's item: a composter makes an Orchard Keeper with sweet berries, a
  Florist with a flower and a Composter with bone meal; a blast furnace makes a Miner with a pickaxe; a grindstone a
  Guard with a sword; a jukebox a Bard with a music disc, and so on (hold Shift over a workstation to see its jobs).
  A jobless villager still takes a vanilla block for the vanilla job by themselves. Builders work at the Blueprint
  Table. 26 job blocks can't be crafted any more (the Builder's Bench, the Guard Post, the Fruit Basket...), but the
  ones in your world keep working. Village houses, the camp and the blueprints use the new blocks, and a village house
  whose block is shared comes with its worker.
- **Fossil Scientists work at Cobblemon's Fossil Analyzer** instead of a block of ours: stand a villager by one and
  sneak-right-click them with a fossil. They still revive the fossil themselves, without the rest of the machine. The
  village fossil labs and the Research Lab blueprint have an analyzer now; scientists already working at a Training Post
  or a Fossil Lab keep working there.
- A postman's mailbox is the post office's counter: with no mailbox of your own, pick up your parcels there.
- **A new look**: every workstation, villager outfit and item is redrawn in Minecraft's own style. Workstations are
  built from their wood or stone like the crafting table, with no more grainy speckle. The outfits are cleaner, and a
  job's hat no longer has the biome's hat showing through it. Items take the shape of their vanilla kind: the Village
  Ledger lies like a book, blueprints and the Patrol Map like maps, and the markers and the Rally Banner are held like
  tools.
- **Cavalry on camels**: a guard rides a saddled camel too, as well as a horse, donkey or mule. A camel needs no taming,
  only a saddle.
- **Villagers sit when they ride**: a guard on horseback sits in the saddle with their legs forward, and a ferryman or
  a fisher sits in the boat at the oars, instead of standing on the horse's back or looking sunk in the boat.

### Fixed
- Villagers that come with a generated village house no longer suffocate in its walls: some vanilla desert houses put
  their villager in a narrow corridor, where it landed on the step beside it with its head in the ceiling.
- A blueprint with a long number at the end of its name (`house_20260929`, from an imported file) no longer breaks the
  moods of the villagers who live in it; only up to three digits count as a tier.
- A bed at the far end of a big building (a large scan, say) now counts as a home in it.
- The Village Hall's "What next?" tips about homes and the next rank showed the wrong numbers.
- **Village protection**: a stranger's arrows, tridents and thrown potions no longer hurt a protected village's
  villagers and animals; and a Village Ledger bound before the village was protected no longer opens its hall for a
  stranger (who could collect the treasury with it).
- Guards get off their horse, and fishers come ashore, as soon as their shift ends (they rode or floated on for up to
  a minute).
- Giving a job from the Village Hall no longer fails with an error when the villager's old workstation was broken
  while they were far away.
- Bandits raiding a village at night can no longer be drawn into a vanilla raid.
- Our village houses no longer leave invisible holes in the ground in front of their doors (villagers and players
  could fall into them, and a new villager sometimes never reached the workshop's bench).

### Dev
- A nightly showcase: GitHub films every job, screen and build family in the real game each night and publishes one
  page with a GIF, stills and a pass or fail per scene (https://jcondedata.github.io/minecraft-alive-workplace/). 33
  new screenshot scenes cover every job and screen that had none.

## 0.137.0 — 2026-09-29

### Added
- **Fishing from boats**: put a boat in a fisher's barrel and, when there's a lake or the sea nearby, they row out to
  open water and fish from the boat — where, as for a player, one catch in twenty is treasure (enchanted books, bows,
  name tags, saddles...). They row back with every few fish and put the boat away.
- **Ferry rides**: a Travel Ticket no longer blinks you across — you sit in a boat for a few seconds, rowed off by the
  ferryman when he's about, the view fades, and you step out by the post you were going to.
- **Cavalry**: leave a tamed, saddled horse (or donkey or mule) near a Guard Post, off its lead, and the guard rides it
  on patrol and into fights. They get down to spar at a Training Dummy and leave the horse where they are at the end of
  their shift. A horse on a lead stays tied up.

## 0.136.0 — 2026-09-29

### Added
- **Homes**: a villager's home is the building a builder put up that their bed is in. A tier II house (a Stone House II,
  a Cottage II...) makes its people happier (+5), tier III more (+10); the Village Hall's list says where each villager
  lives, and "What next?" suggests better homes when most of the village sleeps in first-tier houses or in ones no
  builder put up.

### Changed
- **Redrawn builds** (architect review, round 2): the Starter Cottage gets a gabled porch on fence posts (a porch hood
  on II and III); the Stone House a wall dormer over the door that lights the attic (II and III: over the middle bay,
  plaster with a little diorite in it); the Lookout Tower moss low on its walls and lanterns on beam ends by the door
  (III: a lantern at the guardhouse door and an archery butt); the Market Stall a scalloped awning edge (III: gable
  windows, a soffit and centred windows on the storeroom); the Market Square peaked kiosk canopies and stock beside them.
  Sizes are unchanged, so blueprints already placed still fit.
- **More redrawn builds** (round 2, second batch): the Library's gables are timber-framed over the stone, with a beam
  that runs on round the study tower, a soffit under the eaves and lamps at the steps; each home of the Terrace gets a
  dormer over its bedroom window; the Graveyard's lych-gate a little slate roof. The Inn, Tinker's Workshop, Nether
  Gate and Berry Farm passed the review as they are.
- **Walls and village houses** (round 2, third batch): moss low on the Stone Wall, the Wall Tower (with a spruce band
  at its top floor) and the Gatehouse (with a portcullis drawn up under its outer arch and lanterns either side of the
  way in); new village houses get a lantern over the door, and desert ones the ends of their roof beams showing under
  the parapet. The well, fountain, gazebo, street lamp, park bench and palisade passed as they are.

## 0.135.0 — 2026-09-29

### Added
- **Village protection** (a setting on the Village Hall, off until its owner turns it on): shift-click the hall's name
  tag and only you, your friends (`/workplace friend add`) and operators can break, place or open things in the village,
  or hurt its villagers and animals; everyone can still come in, open doors, trade and ring the bell. Whoever places the
  hall owns it (an older hall goes to whoever protects it first); `villageProtection` in the config switches it off.

## 0.134.0 — 2026-09-29

### Added
- **What next?** on the Village Hall's screen (the compass): what the village lacks, most pressing first, and how to put
  each right — a builder, beds, food, a Storehouse, guards, bandits, the ill, dark beds, jobs, a scholar, decorations,
  upgrades and the next rank.
- **Treasury**: every morning a village with a Village Hall puts by its takings (a fifth of an emerald a worker, more
  for a well-kept village and a higher rank); click the hall's name on its screen to collect them.

### Changed
- The jar is now called `alive-workplace-<version>+1.21.1.jar` (the Minecraft version it's for). When updating a server
  or a game, take the old Alive Workplace jar out of `mods` as usual; nothing else changes (same mod id, saves, config).
- Under the hood, no gameplay changes: the mod is now built the way that lets one codebase make a jar for each
  Minecraft version (a Stonecutter build; for now there is one, 1.21.1). Everything it needs from Fabric goes through
  one small layer and the Minecraft calls that change in newer versions through another, and the build checks the rest
  of the mod keeps to that. The Cobblemon and CobbleDollars integrations switch themselves off with a line in the log,
  instead of crashing, if a future version of those mods changes its API. The build runs on JDK 25 (the game still
  runs on Java 21).

## 0.133.0 — 2026-09-29

### Added
- **Warding** (research, after Fortification): explosions no longer break blocks in the village.
- **Shape Planner** (a Blank Blueprint and a compass): plan a box, cylinder, dome, sphere, cone, pyramid or arch — any
  size up to 32, solid or hollow, in a block you carry — and draw it up as a blueprint for a builder.
- **Village Map**: the Village Hall's map button draws the village on an empty map — centred on the hall, the land as
  it is that day, a coloured banner on every finished building (workplaces named) and the legend on its tooltip.
- **Library III**: an enchanting room behind the reading hall, under its own lower slate roof — an Enchanting Table
  ringed by fifteen bookshelves with candles, a lectern (a librarian moves in) and a chest for the lapis.

### Changed
- The **Ranch** is a proper barn now: a hayloft gable with open barn doors facing the paddock, stable aisles either
  side under a slate roof; the Ranch II adds a stable wing and a brick silo.
- The **Schoolhouse** is white plaster now (not pink), with a little gabled porch over the school bell and a lantern
  cupola on the ridge.
- The **Storehouse** is a granary now: a stone ground floor with open loading doors, a loft above with a hoist over its
  door and a dark oak roof; II's shed and III's warehouse range have their roofs running into it.
- Librarians enchant stronger with bookshelves round their table: a level more for every three (a full ring of fifteen
  lifts a Novice from level 10 to 15; a Master stays at 30).
- The Market Stall's striped awning is a finer slope (carpet steps between the stripes).

## 0.132.0 — 2026-09-29

### Added
- **Daycare** (with Cobblemon): leave up to two Pokémon with a Rancher; they gain experience while they're there, and
  you collect them for an emerald plus one per level gained.
- **Village Ledger** (a book and an emerald): bind it at the Village Hall, then open the hall's screen from anywhere
  nearby.
- **Chapel** (a decoration: nave, pews, a bell tower with a slate spire), where the village's weddings are held.
- **Couples**: villagers court and marry (a wedding at the bell with fireworks lifts the whole village's mood), are
  happier together, have the village's babies first, and mourn a partner who dies.

## 0.131.0 — 2026-09-29

### Added
- **Bandit camps**: bandits make camp near villages of Village rank or more and raid them at night until their chief
  falls. Break the camp up for its loot.
- **Festivals**: every eight days (or sooner, with a cake at the hall) the village gathers at the bell after work for a
  feast, music and fireworks; moods lift, and players are Heroes of the Village for the evening.
- **Chatter**: villagers off work near you now and then say something over their heads about their mood or the
  village's news (festivals, bandits, raids), or just hello.
- **Better-looking builds**, reviewed with a new building skill: the **Healing Center** is now a white clinic with dark
  posts, a red roof and a gabled porch (II: a ward behind under a lower roof; III: the berry garden), the **Supply Shop**
  a timber shop with its gable to the street, a blue-slate roof and a striped awning over display windows (II: the
  shopkeeper's house behind; III: the post office), the **Barracks** got a gabled stone porch, buttresses and lintels,
  the houses we add to generated villages got roofs that contrast with their walls, chimneys, door hoods and flower
  boxes, the **Flower Shop** a dark oak roof, the **Apiary Garden**'s honey shed a gable roof, and the **Research Lab**
  a copper roof (it greens over the years; builders count greened or waxed copper as built).

### Fixed
- Floating bits found by the new build checks: the lantern beams in village houses, the Starter Cottage II attic, the
  Lookout Tower III guardhouse and the Storehouse III now run wall to wall; the Lookout Tower II's lanterns hang on
  chains; the Flower Shop's front pots stand on pedestals; the Supply Shop III's doorway no longer takes the ladder's
  wall.

## 0.130.0 — 2026-09-29

### Added
- **Seven more research topics** for the scholars: Logistics (porters carry more), Craftsmanship (crafters faster),
  Medicine (less illness), Fortification (guards turn aside blows), Commerce (more market traders, cheaper mercenaries),
  Expeditions (explorers and netherworkers back sooner) and Green Thumb (more bone meal from compost).
- **Patrol Map**: mark up to eight points and hand it to a guard; by day they walk your route.
- **Rally Banner**: enlist up to twelve guards, raise the banner, and they follow you and fight at your side.
- New builds: the **Compost Yard** and the **Sifting Shed**, each with an upgrade.

## 0.129.0 — 2026-09-29

### Added
- **Stock orders** at the Storehouse: "keep 64 stone bricks" — the village's crafters make what runs short from what's in
  the store.
- **Repairs**: idle builders put back the blocks missing from the buildings they finished (only holes are filled).
- Masons are the village's **kiln**: with a furnace by the stonecutter they fire stone, smooth stone, terracotta,
  bricks and nether bricks for the builders.

## 0.128.0 — 2026-09-29

### Added
- **Diet**: villagers pick meals they haven't had lately; a varied diet lifts their mood, the same food every day
  lowers it. The hall shows how many kinds of meal the store has.
- **Mercenaries**: hire three fighters in iron at the Village Hall for 12 emeralds; they fight for the village until
  dawn.
- **Composters** (new job, the Compost Bin): the village's scraps and rotten flesh become bone meal. Villages sometimes
  grow a Compost Yard.

## 0.127.0 — 2026-09-29

### Added
- **Scan Tool**: mark two corners of something you've built and save it as a blueprint (for a Blank Blueprint) — it's
  in the Blueprint Table for everyone. Name it by renaming the tool in an anvil.
- **Mirror** a blueprint: a new button on the style screen builds it flipped left to right.
- **Drop Box** (barrel + hopper): anything put in goes to the storehouse with the next porter.

## 0.126.0 — 2026-09-29

### Added
- **Netherworkers** (new job, the Nether Brazier): with food in the chests and a Nether portal nearby they go through
  it on expeditions and come back with the Nether's goods — netherrack, quartz, glowstone, nether wart and more; with
  a pickaxe, an axe, a sword and armor they bring back more (a diamond pickaxe now and then turns up ancient debris),
  and with Cobblemon the odd Fire or Dusk Stone.
- **Nether Gate** blueprint (and its upgrade): an obsidian portal frame in a blackstone arch — the builder lights it when
  it's done if there's a flint and steel in the chests. II adds a gatehouse roof, a storehouse and a nether wart garden.

## 0.125.0 — 2026-09-29

### Added
- **Tinkerers** (new job, the Tinker's Bench): they make the redstone and iron parts builders are waiting for — pistons,
  rails, hoppers, repeaters, lanterns, iron bars, doors, copper blocks — firing raw ore into ingots first when that's all
  there is (a coal for every 8 ore). Between jobs they mend the village's hurt iron golems with iron ingots.
- **Tinker's Workshop** blueprint (and its upgrade): a brick workshop with a forge, a smoking chimney and open trusses;
  II adds a storage loft, a cart track and a lightning rod.
- Villages now grow Tinker's Shops and Sifting Sheds too.

## 0.124.0 — 2026-09-29

### Added
- **Sifters** (new job, the Sieve): gravel, sand, dirt and soul sand shaken through for flint, seeds, nuggets, clay,
  quartz and the odd gem — with Cobblemon, now and then an evolution stone.

## 0.123.0 — 2026-09-29

### Added
- **Moods**: every grown villager in a village with a Village Hall has a mood from their own day (fed, a bed, a job,
  health, decorations near home, company), shown with its reasons on the hall's list. Unhappy villagers work slower,
  happy ones faster. (`villagerMoods` in the config.)

## 0.122.0 — 2026-09-29

### Added
- **Families**: babies remember their parents (the hall's list says whose child they are); grown up and without a job,
  they take up a parent's trade when the village has a free workstation for it. The chronicle notes it.

## 0.121.0 — 2026-09-29

### Added
- **Village ranks**: Hamlet, Village, Town and City, from villagers, finished buildings and research. Each rank pays
  (better quest rewards, more caravan routes, more market traders, room to grow), and a rank up is celebrated with
  fireworks.

## 0.120.0 — 2026-09-29

### Added
- **Kinds of guard**: a guard with a shield is a **Knight** and blocks blows from in front; one holding a healing
  potion is a **Medic** and gives potions to the wounded; with a bow, an **Archer** as before. Sneak-right-click a guard
  with one of these to hand it over.
- The **Barracks** (two Guard Posts, bunks, training dummies) and the **Barracks II** (two more).

## 0.119.0 — 2026-09-29

### Added
- **Walls and gates** in the Blueprint Table: the Palisade, the Palisade Gate, the Stone Wall, the Wall Tower and the
  Gatehouse. In a village with a Village Hall and a guard, the gates are shut at nightfall and opened in the morning.

## 0.118.0 — 2026-09-29

### Added
- **Village raids**: at night, monsters may raid a village with a Village Hall and 8 or more villagers — more often and
  in greater numbers the bigger it is, some in iron when it has many guards. The bell rings, the guards defend the whole
  village, and the chronicle remembers it. (`villageRaids` in the config.)
- In a pillager raid, guards no longer hide: they patrol and fight across the raid's area.

## 0.117.0 — 2026-09-29

### Added
- **Caravans**: villages with a Village Hall can supply each other. On the hall's Trade Routes page, pick villages to
  send caravans to; once a day a caravan takes them what their workers are waiting for from your Storehouses.

## 0.116.0 — 2026-09-29

### Added
- **Market days**: once a week a village with a Village Hall and a Market Square holds a market — two travelling
  traders come to the square for the day, each with a blueprint to sell too. (`marketDays` in the config.)

## 0.115.0 — 2026-09-29

### Added
- The **Settler's Wagon**: right-click open ground and two settlers make camp — a covered wagon with a chest of
  supplies (and a Village Hall), a Builder's Bench, a campfire and two bedrolls. One settler is your builder from the
  start. A village can begin anywhere.

### Fixed
- The blueprints in village builder's workshops' chests showed old sizes before they were placed.

## 0.114.0 — 2026-09-29

### Added
- **Give a villager a job from the Village Hall**: click a jobless villager on the hall's list to see the village's free
  workstations, and click one to give them that job.
- **Call everyone home**: the bell on the hall's screen brings back the villagers who live or work in the village but
  wandered off.

## 0.113.0 — 2026-09-29

### Added
- **The village chronicle**: a page of the Village Hall (the book in the middle) with what happened in the village, day
  by day — births, deaths, revivals, travellers, finished buildings, quests, research and new Masters.

## 0.112.0 — 2026-09-29

### Added
- **Sickness**: in a village with a Village Hall villagers fall ill now and then (more often hungry or without a bed);
  the ill work at half pace until they get well after three days, or a **Nurse** cures them with honey, milk or a
  healing potion from her chest. (`villagerSickness` in the config.)

## 0.111.0 — 2026-09-29

### Added
- **Names**: villagers in a village with a Village Hall get a first name of their own.
- **Traits**: every villager has one or two — Diligent, Lazy, Nimble, Clever, Strong, Cheerful, Glutton or Frugal —
  that change how fast they work and walk, how quickly they learn, how hard they hit, how often they eat and how happy
  the village is. The hall lists them. (`villagerNames`, `villagerTraits` in the config.)

## 0.110.0 — 2026-09-29

### Added
- **Styles**: sneak-right-click the air with a blueprint to build it in **Stonework**, **Sandstone**, **Dark Oak**,
  **Cherry** or (with Cobblemon) **Apricorn** wood instead of the timber it was drawn in — any blueprint, your own too.
  Materials, preview and upgrades follow the style. Data packs can add styles (`blueprint_styles/*.json`).

## 0.109.0 — 2026-09-29

### Added
- The **Stone House** in three tiers (Blueprint Table): a stone cottage with two beds, II a timber-framed upper storey
  with two more, III a stone wing at the back with two more.

## 0.108.0 — 2026-09-29

### Added
- **Decorations** in the Blueprint Table: the **Well** (and Well II, with a roof), the **Street Lamp**, the **Park
  Bench**, the **Fountain**, the **Gazebo** and the **Market Square** (with the village bell, where villagers meet).
- Decorations near a Village Hall add to the village's **beauty**: each point is 1% more wellbeing, up to 10% (shown on
  the hall's Wellbeing icon).

## 0.107.0 — 2026-09-29

### Added
- Six new buildings with upgrades, in the Blueprint Table: the **Schoolhouse**, the **Library**, the **Ranch**, the
  **Apiary Garden**, the **Flower Shop** and the **Graveyard** — each with its job's workstation, so building one brings
  a teacher, a scholar, a rancher, a beekeeper, a florist or an undertaker.

## 0.106.0 — 2026-09-29

### Added
- Villages now grow a **flower shop** (florist), a **ranch house** (rancher), a **schoolhouse** (teacher), an **inn**
  (innkeeper) and a **mortuary** (undertaker) now and then, in every village style, each with its job block, a chest
  of starting supplies and a bed.

## 0.105.0 — 2026-09-29

### Added
- **Paths**: a builder who finishes a building lays a dirt path from its door to the village's bell or Village Hall,
  round water, trees and other buildings (`builderPaths` in the config turns it off).

## 0.104.0 — 2026-09-29

### Added
- **Scholars and research** (new job, Scholar's Desk): a research tree kept in the Village Hall, paid for in paper,
  books and emeralds — Swift Hands (every job faster), Hearth (wellbeing), Drill (guards hit harder), Kinship (more
  Pokémon partners), Lore (schooled children start as Journeymen) and Architecture, which draws up the **Town Hall**
  blueprint: a stone hall with a bell tower and a Village Hall inside. Sneak-right-click a scholar to choose.

## 0.103.0 — 2026-09-29

### Added
- **Village quests**: each morning the Village Hall puts up a quest for players — bring what a worker is waiting for
  (or food, or something the village can use), clear out monsters round the village, or beat a village trainer (with
  Cobblemon). Hand things in from the hall's new Quests page; the reward is paid in emeralds or CobbleDollars.

## 0.102.0 — 2026-09-29

### Added
- **Graves and Undertakers**: a grown villager with a job or a name who dies leaves a grave. An Undertaker (new job,
  Undertaker's Table) with a golden apple, a healing potion or a totem of undying brings them back as they were — job,
  level, trades and name. The Village Hall counts the graves.

## 0.101.0 — 2026-09-29

### Added
- **Innkeepers** (new job, Inn Counter; the Inn blueprint has one): each morning, while there's a free bed, a traveller
  comes to stay. Travellers know a trade already (Apprentice, Journeyman, now and then Expert): right-click one to hire
  them for emeralds or CobbleDollars, and they join the village and start their first job at that level. Unhired
  travellers move on after two days.

## 0.100.0 — 2026-09-29

### Added
- **Teachers** (new job, Teacher's Desk): in the day they call the village's children over for lessons. A child who's
  been to school starts their first job as an Apprentice, with the Novice and Apprentice trades.

## 0.99.0 — 2026-09-29

### Added
- **Villages grow**: with a Village Hall, when there's a free bed, 16 meals in the store and the village is doing well,
  two villagers have a baby (at most one a day, up to 40 villagers — `villageGrowthCap`). The hall says what the
  village still needs to grow.
- **Terrace** and **Inn** blueprints (with upgrades): row houses with four beds (six in the Terrace II) and an inn with
  a tavern and six beds (the Inn II adds a stable with a Feed Trough). In the Blueprint Table.

### Changed
- Builders with no way to walk to where they need to be (upstairs by a ladder, behind a party wall) hop there after
  a moment instead of trying for five seconds: builds with upper floors go a lot quicker.

## 0.98.0 — 2026-09-29

### Added
- **Village needs**: in a village with a Village Hall, grown villagers eat once a day from the store (the chef's
  cooking first), like a bed of their own, guards and light. The hall shows the village's wellbeing, and it sets the
  pace of all the work there — up to 25% faster when everyone's fed, housed and safe, up to 20% slower when they're
  hungry. The hall's list says who's hungry or has no bed.

## 0.97.0 — 2026-09-29

### Added
- **Village Hall** (new block): right-click it for the village at a glance — villagers, beds, food in store, guards,
  requests and buildings going up, then everyone who lives there with their job, level, what they're doing and waiting
  for and where they are. Click a villager to make them glow. The village gets a made-up name, or use a Name Tag on the
  hall to name it.

## 0.96.0 — 2026-09-29

### Added
- **Training Dummy** (hay bale, sticks and wool): guards spar with one near their Guard Post between fights and gain
  experience from it, up to Expert. Players can give it a whack too.

## 0.95.0 — 2026-09-29

### Added
- **Ranchers** (new job, Feed Trough workstation): they break in the wild horses, donkeys and llamas round the trough,
  put the saddles, horse armor and carpets from the chests on the tamed ones, and breed them (golden carrots for
  horses, hay for llamas, cactus for camels). With Cobblemon they groom the pastured Pokémon nearby once a day, raising
  their friendship — more with a berry from the chests as a treat.

## 0.94.0 — 2026-09-29

### Added
- **Florists** (new job, Flower Stand workstation): they grow the biome's flowers with bone meal on the grass round
  their stand (and more tall flowers from tall flowers), pick them into the chests for the dyer and the builders, and
  fill the empty flower pots within 24 blocks.

## 0.93.0 — 2026-09-29

### Added
- **Beekeepers** (new job, Apiary workstation): they harvest the beehives within 16 blocks — honey bottles with glass
  bottles from the chests, honeycomb with shears — keep the hives in flowers and breed bees up to three a hive. A
  campfire under a hive keeps the bees calm.

## 0.92.0 — 2026-09-29

### Added
- **Farmers grow plantation crops**: sugar cane, cactus, bamboo and kelp in their fields are cut down to the bottom
  block and planted — sugar cane and cactus on sand, more of whatever grows next to a bare spot — and a farmer takes
  every kind of seed in the chests along, so one field can mix wheat and sugar cane.
- **Farmers compost** the seeds piling up in their chests (over 64 of a kind) into bone meal for the field.

## 0.91.0 — 2026-09-29

### Added
- **Butchers tend pastured Pokémon** (with Cobblemon): with a bucket, bottle, brush or bone meal in the chest by their
  smoker they do what a player can do to the Pokémon in a Pasture Block nearby — milk a Miltank, fill a bucket with lava
  from a Slugma, brush birds for feathers and Cottonee for string, bone-meal a Cacnea for cactus — all 139 of
  Cobblemon's item interactions, read from its data.

## 0.90.0 — 2026-09-29

### Added
- **Cartographers go exploring**: with food from the chests by their cartography table (and a sword or axe if there is
  one) they set out on expeditions around the village and bring back what the land has — flint, clay, nuggets, the odd
  emerald or diamond, each biome's own finds, meat and monster drops when armed, and with Cobblemon apricorns, berries,
  evolution stones and fossils. Every third expedition, with an empty map in the chests, they draw an Explorer's Map
  to a place nearby. Hire one with a compass (sneak-right-click); `explorerRange` in the config.

## 0.89.0 — 2026-09-29

### Changed
- **Every starter build has a new look**, all 22 blueprints and tiers: a timber-framed cottage with shuttered windows,
  flower boxes and a smoking chimney (its upgrades add a jettied upper storey and a kitchen wing), a market stall under
  a striped awning, a stone watchtower with a crenellated platform and a steep spire, a Healing Center and a Supply
  Shop under stepped concrete roofs with signs over their doors, an open-fronted timber storehouse, a walled berry
  garden and a stone research lab with a skylight. Buildings already standing stay as they are; their upgrades now
  build onto the new designs.
- **Village houses** (workshops, guard houses, clinics, post offices...) are timber-framed now too, with an
  overhanging roof, shutters and a lantern, in each village type's own materials (flat-roofed in the desert).
- Builders can take down a campfire or a bell when an upgrade needs the spot (a chimney growing taller).

## 0.88.0 — 2026-09-29

### Added
- **Masons crush and fire for the builders**: cobblestone into gravel, gravel into sand, and sand into glass when
  there's a furnace by their stonecutter.

## 0.87.0 — 2026-09-29

### Added
- **Librarians enchant the village's gear**: with an Enchanting Table near their lectern and lapis in their chest, they
  enchant the guards' weapons, armor and bows and the other workers' tools, stronger as they level up. They also make
  books, bookshelves and lecterns for the builders. Sneak-right-click with lapis to hire one.

### Fixed
- Tests that turn village sharing on now turn it off again even when they fail (one failure could fail the next
  batches).

## 0.86.0 — 2026-09-29

### Added
- **Clerics brew potions for the guards**: healing, regeneration and strength from the chest by their brewing stand
  (they fill glass bottles at water or a cauldron), and take them to guards who are short. Sneak-right-click with a
  glass bottle to hire one.
- **Guards drink potions**: a guard carries up to three, drinks healing or regeneration below half health and
  strength as a fight starts.

### Fixed
- Brewing stands, jukeboxes, lecterns and crafters near a workstation are no longer treated as chests (a builder
  could take the potions out of a brewing stand).
- A village farmer test that could fail on an unlucky harvest.

## 0.85.0 — 2026-09-28

### Added
- **Leatherworkers dye for the builders**: coloured wool, carpet, glass, terracotta, candles, beds and dyes a builder
  is waiting for are made from what the builder can get at, and concrete powder is hardened into concrete in the
  cauldron.

## 0.84.0 — 2026-09-28

### Added
- **Shepherds shear and breed the sheep** around their loom (shears and wheat in the chest), and with Cobblemon shear
  pastured Wooloo and Dubwool. Sneak-right-click with shears to hire one.
- **Butchers keep the herd**: cows, pigs, chickens and rabbits around their smoker are bred, eggs picked up and cows
  milked into the buckets in the chest; chefs use the milk and eggs. A butcher hired with a lead (sneak-right-click)
  also keeps each kind at 10 grown animals, taking the rest for meat — never babies, named or leashed ones.

## 0.83.0 — 2026-09-28

### Added
- **Fletchers make the guards' bows and arrows**: a guard without a bow gets a crossbow (with iron) or a bow; a guard
  short of special arrows gets spectral ones made from glowstone.
- **Guards shoot special arrows**: spectral and tipped arrows in a guard's chests go into their quiver (up to 16) and
  are shot first.

## 0.82.0 — 2026-09-28

### Added
- **Weaponsmiths mend worn gear**: worn tools, weapons and armor in the chests by their grindstone, by the guards'
  posts and at the other workers are mended with ingots (or planks, diamonds, leather...) from the village's chests —
  a quarter of the durability per ingot, like an anvil — and put back where they were.
- **Weaponsmiths make swords**: a guard with no weapon (and none in their chests) gets an iron sword, or a stone one.

## 0.81.0 — 2026-09-28

### Added
- **Toolsmiths make the workers' tools**: when a miner needs a pickaxe, a lumberjack an axe or a fisherman a rod, the
  village's toolsmith makes one (iron from the storehouse, else stone; diamond only from their own chest) and takes it
  to that worker's chests. Sneak-right-click a toolsmith with an iron ingot to hire them.

### Changed
- Hiring an armorer is now a **sneak**-right-click with coal, so right-clicking with coal still opens their trades.

## 0.80.0 — 2026-09-28

### Added
- **Armorers smelt for the village**: an Armorer with a chest by their blast furnace smelts the ore in it with coal or
  charcoal, and fetches more ore (and fuel) from the storehouse and from miners who don't smelt their own. A porter
  takes the ingots to the storehouse.
- **Armorers make the guards' armor**: a guard with nothing in an armor slot gets an iron piece made from the
  armorer's iron and brought to their Guard Post's chest. Right-click an armorer with coal to hire them.

## 0.79.0 — 2026-09-28

### Added
- **Trainers send their Pokémon out** beside them in battle (and call them back afterwards), like players do.
- **Mega Evolution** (with Mega Showdown, which is in the Cobbleverse pack): every Master trainer's team has an ace
  holding its Mega Stone, and the trainer Mega Evolves it in battle.

## 0.78.0 — 2026-09-28

### Added
- **Guards fight beside their Pokémon** (with Cobblemon): Fighting and Dragon types kept in a Pasture Block near the
  Guard Post follow up every hit the guard lands with a move of their own, animation and all. Stronger Pokémon hit
  harder; monsters turn on the guard, not the Pokémon.

## 0.77.0 — 2026-09-28

### Added
- **Berry Farm** blueprint (and **Berry Farm II**): a fenced garden of sweet berry bushes with a Fruit Basket, then a
  pergola with glow berries. Orchard keepers sell it at Expert level; it's in the Blueprint Table too.
- **Research Lab** blueprint (and **Research Lab II**): a stone lab with a Fossil Lab and a fossil on show, then a
  museum hall with a big skeleton. Fossil scientists sell it at Journeyman level.
- **Glow berries in orchards**: orchard keepers hang glow berries from any solid ceiling in their orchard.

## 0.76.0 — 2026-09-28

### Added
- **Strip mines at any height**: hold a Quarry Marker and type `/workplace strip <height>` (the marker still offers
  Y=16, -16 and -53 by sneak-right-clicking). Heights in between say what ores to expect there.
- **Lit shafts**: every eight blocks down a ladder shaft, the miner sets a torch in a niche across from the ladders.
- **Lumberjacks strip logs** for a builder in the village who's waiting for stripped logs or wood, from the logs in
  their chests, and **burn charcoal** in a furnace by the Chopping Block (up to 32 in the chests; a little coal starts
  it). With no trees to fell, they see to both.

## 0.75.0 — 2026-09-28

### Added
- **Fossil Scientists** (with Cobblemon): a new job at the Fossil Lab (glass, a brush and glass over three smooth
  stone). Hand one a fossil (a Galar fossil's two halves, one in each hand) and 8 emeralds or 800 CobbleDollars, and they
  revive it while they work; the Pokémon joins your party (or PC) when it's done, or the next time you're online. It's
  Cobblemon's own revival. Villages with Cobblemon sometimes grow a fossil lab.

## 0.74.0 — 2026-09-28

### Added
- **Chefs**: a new job at the Kitchen Stove (five iron ingots round a smoker, over three cobblestone). The chef cooks
  whatever the chests nearby have the makings for — bread, cookies, pumpkin pie, cake, stews, steak, fish, baked
  potatoes — and with Cobblemon its Campfire Pot dishes: Poké Bait, Poké Snacks, Poké Cakes, candied apples and
  Aprijuice. They stop at 16 of each dish, and take the makings from the village's storehouse too. Villages sometimes
  grow a kitchen.

## 0.73.1 — 2026-09-28

### Changed
- **Workers are much lighter on the server.** A worker whose path to somewhere failed used to ask for a new one every
  tick; now it waits a second before trying again (and hops when stuck, as before). Lumberjacks look for trees without
  reading every block around them, and don't work out the whole tree again every tick while chopping. In the full
  Cobbleverse pack, 80 busy workers now take about 7 ms of the 50 ms each tick has, down from 10 ms; lumberjacks cost a
  seventh of what they did.

## 0.73.0 — 2026-09-28

### Added
- **Carpenters**: a new job at the Carpenter's Bench (a stick, an iron ingot and a stick over planks, a crafting table and
  planks). When a builder nearby is waiting for something that can be crafted from what they can get at — stairs,
  slabs, doors, fences, planks from logs, sticks, glass panes... — the carpenter fetches the ingredients, makes it and
  brings it to the builder's chests. They never use what the rest of the build still needs. Villages sometimes grow a
  carpenter's workshop.
- **Masons cut stone for the builders**: a vanilla Mason (at a stonecutter) does the same with the stonecutter's recipes,
  cutting stone bricks, stairs, slabs and walls from the builder's stone, and goes about their usual day in between.

## 0.72.0 — 2026-09-28

### Added
- **The requests board**: right-click a Storehouse to see what the workers nearby are waiting for — a builder's missing
  materials, a miner's pickaxe or ladders, a lumberjack's axe, a farmer's seeds, a fisherman's rod — with how much of it
  the storehouse already holds. Click one to hand over yours: it goes straight into that worker's chests.
- **Lumberjacks fell what's wanted first**: when a builder nearby is waiting for a kind of wood (logs, planks, stairs,
  doors...), their village's lumberjacks fell that kind of tree before any other.

## 0.71.0 — 2026-09-28

### Added
- **Porters and the Storehouse**: a new job. Craft a Storehouse (three planks over a barrel, a chest and a barrel), put
  chests around it, and a villager becomes a Porter who walks round the village's workers and carries what they make —
  ore, logs, crops, fish, fruit — to the storehouse chests, leaving what each job needs (tools, torches, seeds, saplings,
  some stone). Builders and everyone else find things in the storehouse. A storehouse you place is yours: its porter only
  carries for your workers and your friends'.
- **Storehouse builds**: the Storehouse Shed blueprint (sold by porters, and in the Blueprint Table) with two upgrades,
  Storehouse II (a second bay) and Storehouse III (a stone warehouse behind); villages of every type sometimes grow a
  small storehouse with a porter.

### Fixed
- A double chest's items were counted twice by the workers (a builder could think it had enough when it didn't).

## 0.70.0 — 2026-09-28

### Added
- **Villages work together**: workers whose workstations are within 48 blocks of each other share their chests. A
  builder out of stone walks over and takes it from the miner's chests; a miner, lumberjack, farmer or fisherman whose
  tool broke takes a spare from another worker's chests, and farmers take seeds the same way. Only workers who answer to
  the same people share (hired by you or your friends; village workers nobody has hired with each other). The distance
  is `villageRadius` in `config/aliveworkplace.json` (0 turns it off).

## 0.69.0 — 2026-09-28

### Added
- **Village farmers look after their farm by themselves**: put a chest near a village farmer's composter and they take
  on the farm there — harvest, replant, till — and fill the chest. They keep some food (wheat baked into bread) to share
  with the other villagers, so the village still grows. Stop one from their status and they leave it alone for good;
  the gamerule `workplaceVillageFarms` turns it off.

## 0.68.1 — 2026-09-28

### Fixed
- A Master trainer's Pokémon that can learn hardly any attacks on its side (say a physical attacker with mostly
  special moves) now gets its best attacks of either kind; a Smeargle keeps Sketch. (A test that picked random teams
  caught a Smeargle and failed one of 0.68.0's builds; it now checks 40 teams every time.)

### Changed
- The README starts with a table of every job: its workstation, what goes in the chests and what to hand the villager.

## 0.68.0 — 2026-09-28

### Added
- **Fishermen cast a real bobber**: it floats on the water on the end of a line from their rod, dips under when a fish
  bites, and comes back in with the catch.

## 0.67.0 — 2026-09-28

### Added
- **Master trainers' Pokémon have real movesets** (with Cobblemon): four strong attacks that fit their nature and held
  item, picked from everything they can learn — their own type first, then moves covering other types. No more
  level-up leftovers on a level 100 team.

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
