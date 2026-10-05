# Changelog

Everything that changed in Alive Workplace, newest first. Each version has up to four parts:

- **Added**: new jobs, blocks, builds, screens and settings.
- **Changed**: things that now work differently. Read this part before updating a world you care about.
- **Fixed**: bugs that are gone.
- **Dev** (or **Tests**): checks and tools behind the scenes. Nothing changes in game; players can skip it.

Versions are written `X.Y.Z`; the jar (and Mod Menu) adds the Minecraft version, as in `1.0.0+1.21.1`. The mod is made
never to break a saved world: updating keeps your builds, your villagers' jobs and your settings. Versions before
1.0.0 were pre-releases; their notes are kept below as the mod's history.

Found a bug or have an idea? Open an issue: https://github.com/jCondeData/minecraft-alive-workplace/issues (the form
asks for the steps, `latest.log` and any crash report).

## Unreleased

### Changed
- **Expansions still being built are off until the release that finishes them** (B76): 0.139.0 switched on parts of
  1.1 to 1.4 that aren't done yet. The Steward (1.1); partner shows, the nurse at the Healing Machine and the Berry
  Breeder, Camp Cook, Habitat Keeper, Daycare Keeper and Gem Grower jobs (1.2); Legends, the Gifted and strange moods
  (1.3); and edicts, the Work Horn, Village Banners, Cradles, Harvest Idols and tonics (1.4) now stay off, even in a
  config file 0.139.0 wrote with them on, and their switches leave the settings screen until then. Nothing saved is
  lost: a Steward, a Legend or an edict comes back as it was when its expansion is released.

### Fixed
- **A build saved by 0.138.0 or earlier keeps its progress on upgrade** (B64): opened with a newer jar, a site half
  through a raised foundation (or in its walls) dropped to a few percent the first time it loaded. It now works out
  the progress it showed from the saved stage and step.
- **A farmer's carrots and potatoes reach the chests** (B77): a village farmer kept up to 32 of each in his bag as
  seed stock, so a Farmstead's field could be harvested without anything landing in its chest. He now takes them from the
  chests to plant with, like seeds, and puts the whole harvest away.
### Added
- **Old houses, found and measured** (27.20): in zones with "Renew" on, the Steward looks for houses no builder built
  (round a bed or a workstation) and measures each by its blocks. The desk lists them ("Old houses: 4, 3 can be renewed")
  with where each stands and why one is kept (a chest inside, a player built there, not of village blocks, out of the
  zone), and a click outlines them all in the world. Packs add their village blocks to the tag
  `aliveworkplace:village_house_blocks`. Nothing is rebuilt yet.
- **The Steward is safe by design** (27.19): his plans, roads and walls never go into Keep Clear, another village or a
  protected village that isn't his owner's, and a ledger of what players built in a village (from 1.1 on) keeps his
  plans and walls out of those spots unless the owner approves one by hand. His sites leave a player's block where it
  is ("a player's block is in the way" on the desk); when his builds wait for materials, the desk and the Storehouse
  board show one shopping list (the owner hears it once a day, caravans bring it), and he proposes nothing new while
  two builds have waited a whole day.
- **Edicts and civic items in the village's life** (30.21): villagers talk of each edict in force and of each reformed
  one ("Long shifts again... my back.", "The shift bell's rung. Home we go."), of a rush, their tonic, their guild and
  the village's colours; "What next?" now points out a free edict slot, a reform step waiting, Festival Season with too
  little in the treasury, a guild without its Guildhall, Large Families without a Cradle and harvest season without a
  Harvest Idol; the README has *Edicts* and *Civic items* sections with every edict, tonic and guild.
- **Guild Charters, the Guildhall and the Builders' Guild** (30.17): craft a Guild Charter (three paper, an emerald, a
  gold ingot, red dye) and sneak-right-click a Master in a village of Village rank or more: they become the Guild Master
  of their trade's guild (one per trade, one per rank above Hamlet; refusals say why), told to the village, in the
  chronicle, the hall's list and their status, and they sell the Guildhall I and II blueprints. A guild is founded once
  a finished Guildhall stands for it; the Builders' Guild then makes Builders, Carpenters, Masons and Dyers 15% faster
  and lets 5 idle builders help at a build (not 3). When a Guild Master dies, the most experienced member takes over.
  The Book of Edicts' last row shows each guild. Guilds are data files (`data/<ns>/guilds/`); `guilds` and
  `guildsPerRank` in the config.
- **The Miners', Smiths' and Woodsmen's Guilds** (30.18): members work 15% faster once founded. Miners' pickaxes and
  the netherworker's gear wear half as fast (Miners, Sifters, Netherworkers); each ingot a Weaponsmith mends with puts
  back a third of the durability, not a quarter (Armorers, Toolsmiths, Weaponsmiths, Tinkerers, Ball Smiths); axes and
  fishing rods wear half as fast (Lumberjacks, Fletchers, Fishermen). New guild perks for packs: `tool_wear` and
  `mend_per_unit`.
### Fixed
- A Pathfinder waiting for a player who fell behind now stands still instead of drifting a few blocks back toward
  their table or strolling off (B75).
### Added
- **Roads between villages** (27.17): two villages with a trade route each build their half of a street to the other,
  from the end of their nearest road and in the style of the zone it starts from, up to 256 blocks or halfway; the
  halves meet halfway, and a half that can't reach that far ends at a milestone (a stone post with a lantern and a sign
  naming the other village and how far it is). Only loaded land is planned. Caravans on a finished road arrive in three
  quarters of the time, and both chronicles note the road. Settings `caravanRoads` and `caravanRoadReach`.
- **Walls along the wall line** (27.18): a village raided in the last 7 days, or with bandits camped nearby, has its
  Steward propose a wall on the hall's "What next?" — along the City Plan's wall line, or a line of his own 4 blocks
  round the zones, drawn on the plan so you see it before you approve. Walls are kits as data
  (`data/aliveworkplace/wall_kits/<name>.json`): **Palisade** up to a Village (Palisade, Palisade Gate and the new
  **Palisade Tower**, a log watch platform with a ladder and a lookout under a dark oak roof) and **Stone** from a Town
  (Stone Wall, Wall Tower, Gatehouse), a Town replacing its palisade a piece at a time. A tower stands at every corner
  and at least every 28 blocks, the segments between fit whole, a gate goes wherever a road crosses (shut at night by
  the guards as ever), and each piece sits at its own ground height with its foundation under it. At most 3 wall sites
  are open at once, and the whole wall counts as one building for the village's rank. New setting `stewardWalls`.
- **Lamps, bridges and steps** (27.16): streets and avenues get the Street Lamp in their road's style every 16 blocks on
  alternate sides and before every crossing (never by a door), lanes a lantern post every 12; they count as Street
  Lamps for beauty and light the homes near them. A road that meets water or a drop deeper than 2 blocks, up to 16
  wide, gets a bridge in its style (rails, a pillar every 4 blocks down to the bed, a stair up at each end); a wider
  gap stops the road at the bank, and the Steward's desk says why. Crossings are paved square.
- **The Harvest, Herders' and Scholars' Guilds** (30.19): members work 15% faster once founded. The village's own farms
  and the Orchard Keepers' rounds reach 24 blocks from the composter or basket, not 16 (Farmers, Orchard Keepers,
  Florists, Beekeepers, Composters, Chefs); shepherds, ranchers and butchers breed up to 12 of a kind (not 8) and a hired
  butcher keeps 14 (not 10) (Shepherds, Butchers, Ranchers); research levels cost a quarter less paper, books and
  emeralds, rounded up (Scholars, Teachers, Librarians, Cartographers). New guild perks for packs: `work_reach`,
  `herd_size` and `research_cost`.
- **The Healers', Merchants', Wardens' and Trainers' Guilds** (30.20): with these every trade but the Bard has a guild.
  Healers (Nurses, Clerics, Undertakers): 15% faster, the village's ill get well in two days instead of three, nurses
  and undertakers look 48 blocks out instead of 32. Merchants (Shopkeepers, Innkeepers, Ferrymen, Postmen, Porters):
  15% faster, travellers cost a quarter less to hire, porters carry 3 more stacks. Wardens (Guards): guards train on
  the dummies up to Master instead of Expert and hit 10% harder. Trainers (only with Cobblemon: Trainers, Trainer
  Leaders, Move Tutors, Pokémon Traders, Fossil Scientists): lessons and revivals cost a fifth less, trainers rank up a
  quarter faster. Guild files may carry `fabric:load_conditions`. New guild perks for packs: `recovery_days`,
  `work_radius`, `hire_price`, `carry`, `train_up_to`, `strength`, `lesson_price` and `trainer_xp`.

## 0.139.0 — 2026-10-05

### Added
- **The Seer** (29.16), a Rare Legend in a deep purple hooded robe sewn with silver stars: they come to the village's
  finished Chapel at midnight under a full moon, 1 time in 2, or are born to a Cleric; they like jewels, keep to the
  Chapel by day and drift with end-rod motes at night. Each dawn they foretell, in chat and first on the hall's
  "What next?", whether raiders come that night and from which side, the next festival and market day, and the next
  day's Legend guest; with a Seer the night's raid and the next day's guest are rolled at dawn, so they are never
  wrong. A wedding at the Chapel with the Seer there is blessed: the couple is 10 happier for 7 days and their first
  baby comes within 2 days when a bed is free. `Legends.foretold` gives M32 its two days' warning.
- **The Golem Smith** (29.15), a Legendary Legend: a Master Tinkerer may be inspired in a happy village with 4 iron
  golems (their Masterwork is a heavy core, "The Heart of <name>"; they like wine). From the chests by their smithing
  table they build a golem every 2 days, up to one per 5 villagers: a **Hauler Golem** (4 iron blocks, a carved pumpkin,
  a chest) carries what the workers make to the Storehouse, 9 stacks a trip; a **Farmhand Golem** (an iron hoe instead
  of the chest) harvests and replants the village's fields into the field's chest; a **Wall Sentry** (a shield) holds
  the first point of a Patrol Map you give it, with twice a golem's health, throwing attackers back. Sneak-right-click
  the Smith to choose which comes next. Each golem wears its role (a crate pack, a straw hat, a helm), has its name
  over its head and a line on the hall's guards button; the Smith mends golems twice as fast as a Tinkerer and wears a
  leather apron, goggles and iron-banded gloves.
- **Roads** (27.15): the roads drawn on the City Plan get built. The Steward has each approved road's way found over
  the ground (its width kept clear, round water, buildings and anything a player built, steps of one block at most) and
  hands it, 24 blocks at a time, to the village's idle builders when no building waits (two at once at most). Each road
  is paved in its style: As drawn (dirt path, coarse dirt and gravel edges), Stonework (stone bricks with cracked ones,
  cobblestone edges), Sandstone, Dark Oak (deepslate), Cherry (polished diorite) and, with Cobblemon, Apricorn (bricks
  with mud brick edges), with stairs up and down each step. Every new building's door joins the nearest road with a
  lane (villages without roads keep the dirt path to the bell). Roads aren't buildings: the rank, the map and homes
  leave them out. Road styles are data (`road_styles/`). Config `stewardRoads`.
- **Farmstead, Fisher's Hut, Weaver's Cottage and Bandstand** (27.14), new blueprints in the Blueprint Table: a
  farmhouse with one bed beside a field of farmland round a water channel, a scarecrow and a composter (II: a barn and a
  second field); a shore hut with a jetty out over the water on log posts and a barrel (II: a smokehouse with a smoker
  and a boat shed); a cottage with a loom and a fenced sheep pen (II: a dye garden); and an open eight-sided bandstand
  with a jukebox, which adds 3 to a village's beauty. The Steward now builds them for a Farmer, a Fisherman, a Shepherd
  and a Bard left without a workstation. A Fisher's Hut goes only on a shore, with water within 4 blocks of its front
  and no more than 3 deep under its jetty; a fisherman fishes from the end of a jetty before the bank. Builders put no
  foundation under a top slab or upside-down stairs (they hang, they don't stand).
- **Smithy, Mason's Yard, Fletcher's Lodge and Map Room** (27.13), new blueprints in the Blueprint Table: a stone forge
  open to the street with a blast furnace, smithing table and grindstone (II: a coal and ore store with a second blast
  furnace); a fenced yard of cut stone with a lean-to over a stonecutter (II: a second stonecutter and a hoist); a log
  cabin with a log pile, a straw target and a fletching table (II: a drying-rack wing with a second one); and a narrow
  tower house with a cartography table and a lookout at the top. The Steward now builds them for an Armorer or Miner,
  a Mason, a Fletcher or Lumberjack, and a Cartographer left without a workstation (or a job the village wants with no
  free block).
- **The Old Sage** (29.14), a Rare Legend: once a village has finished 5 research levels, a hermit's hut of mossy stone
  and spruce (a lectern, bookshelves, a cauldron, an herb garden) appears 150-250 blocks out, and the village's players
  hear a rumour with its distance and direction. The Sage asks three riddles from a pool of eight, each answered by
  handing over an item; a wrong one gets a shake of the head and, after two misses, a hint. Three right answers and the
  Sage comes to the village the next morning as a guest (also born to a Scholar). Likes books. They work the
  **Ancient Lore** at a lectern: Old Tongues, Star Charts, Herb Lore, Deep Memory, Runes of Warding, Old Harvests and
  one last pick for good (the Undying Flame, the Golden Age or the Iron Pact). Grey robe, long beard, gnarled staff.
- **The Pathfinder** (29.13), a Rare Cartographer Legend: found at a ruined portal once one of the village's explorers is
  an Expert, or born to a Cartographer; likes clothes. Their own expeditions range twice as far and bring back more
  maps, now and then a trial key or an echo shard. Sneak-right-click them with 8 food in their chests and pick a
  Stronghold, an Ancient City or Trial Chambers: they hand you a map to the nearest within 3,000 blocks and lead the
  way, waiting when you fall behind, catching up when you're far off and fighting whatever attacks either of you. At
  the place they plant a banner; right-click them for Home and after 5 seconds standing still you're both back at the
  Village Hall. Once a day; a logout or a death calls it off. Every trip goes in the chronicle. A hooded travel cloak,
  a pack and a lantern.
- **Seasons and the Harvest Idol** (30.14): the hall's festival icon now names the season and its day ("Autumn:
  harvest season, day 3 of 16"). The Harvest Idol, a straw figure crowned with wheat on a wooden post (a hay bale,
  three wheat, a stick and a gold ingot), makes the crops within 32 blocks grow 25% faster in harvest season (autumn),
  with golden sparkles rising from it; a second idol adds nothing. Vanilla crops and, with Cobblemon, its berries,
  apricorns, mints and Hearty Grains (tag `aliveworkplace:idol_crops`). `harvestIdols` in the config.
- **The Steward's civic rules** (27.12): he now wishes for a Clinic (a Healing Center from a Village) when two or more
  are ill and nobody nurses them, a Graveyard after a death in a village of 8, a Schoolhouse for 3 children, a Library
  with 6 villagers and no scholar, a Chapel while a couple courts, a Lookout Tower (a Barracks in a Town) while guards are
  short, a Street Lamp by the darkest beds, a Well, Park Bench, Fountain and Gazebo while the village has little beauty,
  and a Market Square for a Town. Nine new rule conditions for packs: `guards_short`, `raided_within`,
  `bandit_camp_near`, `ill`, `dark_beds`, `beauty_below`, `children_at_least`, `courting_couples`, `died_within`, and
  `no_worker`.
- **Four more tonics** (30.16), each with its own icon: the Cleric brews **Smith's Draught** (a glass bottle, blaze
  powder and two iron nuggets; for Armorers, Toolsmiths, Weaponsmiths, Tinkerers and Ball Smiths) and **Scholar's
  Infusion** (a glass bottle, an amethyst shard and glow berries; for Scholars, Teachers, Librarians, Cartographers and
  Fossil Scientists); the Chef cooks **Harvest Cordial** (a glass bottle, an apple, wheat and sugar; for Farmers,
  Orchard Keepers, Florists, Beekeepers, Composters, Shepherds, Butchers, Ranchers and Chefs) and **Woodsman's Broth**
  (a bowl, cooked salmon, a carrot and a brown mushroom; for Lumberjacks, Fletchers, Fishermen, Porters and Postmen).
  Each makes its villagers 25% faster for a day. Tooltips now say "2 Iron Nuggets", not "2 Iron Nugget".
- **Tonics: Miner's Brew and Builder's Tea** (30.15): right-click a villager with a tonic that suits their job and they
  drink it, working 25% faster for a day (20 minutes; their status line shows "25% faster (Miner's Brew, 19 min left)").
  Another tonic starts the day again; they never stack, and the work pace cap still holds. One that doesn't suit them is
  refused and you keep it ("Dara has no use for Miner's Brew."). Players can't craft them: the Cleric brews Miner's
  Brew (a glass bottle, glowstone dust, coal and sugar; for Miners, Sifters and Netherworkers) after the guards'
  potions, the Chef cooks Builder's Tea (a glass bottle, two sweet berries and sugar; for Builders, Carpenters, Masons
  and Dyers) after the menu, from the chests by their station and the store, keeping 4 of each. Tonics are data
  (`data/<namespace>/tonics/<id>.json`), so a server can make any item one; the tooltip says what each does, for which
  jobs and who makes it from what. `tonics` in the config.
- **Strange moods and Masterworks** (29.10): once a day a Master in a happy village whose trade an inspired Legend
  names may be taken by a strange mood (1 in 8). She claims her workstation under a purple line and asks for three
  rare materials in the chest beside it within 3 days (the chronicle, the hall, the Storehouse board and the village's
  players hear of it). Brought, she makes a named Masterwork ("The Ember Ladle", lore naming her, the village, the day
  and the materials, with a glint) for the player who brought the most, and becomes the Legend; a Masterwork in an item
  frame in the village is 3 beauty. Not brought, she sulks a week (less happy, half pace) and the village has no mood
  for 10 days. Config `strangeMoods`.
- **Research trees for Legends, as data** (29.11): a Legend can bring a research tree of their own
  (`data/<ns>/research_trees/<tree>.json`, so packs can add more). While they live in the village it gets its own tab
  on the research screen (or sneak-right-click a Legend with no trade). Choose a topic there and the Legend pays for it
  from the chests by a lectern near their home and researches it there; idle scholars help at half speed, and nothing
  moves while the Legend is on strike. Topics can wait for a village count or be one-of-a-kind picks. New village
  effects for trees and edicts: wellbeing, illness (chance and length), raid chance, XP, loot luck and named switches.
  Old halls keep their research as it was.
- **The Master Architect** (29.12), the first Legendary Legend: a guest at the inn once the village is a Town with
  finished buildings in 3 styles. Builders within 32 blocks of the Architect work twice as fast, and every 3 days the Architect hands the
  least busy builder the next upgrade of a finished building (homes first), or redraws it in the new **Grand** style
  (stone-brick plinths, polished andesite and deepslate trim, dark-oak frames, deepslate-tile roofs), rebuilding only
  the blocks that change. Sneak-right-click the Architect to pause it; a strike stops it. A long blue coat, a brass compass and a
  rolled drawing.
- **Legends found in the world** (29.9): standing in a ruined portal, a pillager outpost or a shipwreck, a player whose
  village has earned a Legend found there may come on their camp: a traveller beside the portal (talk to them), a
  prisoner in an iron cage at the tower's foot (break the bars) or a castaway on the nearest beach (hand them a cooked
  meal). Freed, they thank you, walk off and come to your Village Hall the next morning as a guest. Each structure is
  used once; `legendSites` in the config turns it off.
- **The Village Hall's own screen** (30.4a): the hall, all its pages and the Book of Edicts now open on a drawn, vanilla-style window instead of a chest grid: the village's figures on a sunken plaque, the page's actions on an etched toolbar, the page on its own panel, every icon on a raised button that lights up under the mouse. Clicks, pages and permissions are unchanged.
- **The Village Banner** (30.13): any banner and a gold ingot make a Village Banner of that design (its tooltip lists
  it). Right-click the Village Hall with it to make the design the village's colours (it isn't used up; the chronicle
  notes it); place it to hang the design anywhere. The colours then show: a builder who finishes a building in the
  village hangs a wall banner in them over its front door when a banner of their base colour is in the chests (they
  never ask for one); guards gearing up and hired mercenaries carry plain shields painted in them (a shield a player
  painted is left alone); the hall's trade routes page shows each village by its banner and the Book of Edicts the
  village's own, with the colours on its last row; and a festival with a banner in the colours within 16 blocks of the
  square lifts moods by 20 instead of 15, for 3 days instead of 2. `villageBanners` in the config.
- **The Cradle** (30.12): a wooden cradle on rockers with a wool blanket, crafted from planks, sticks and white wool.
  Put one within 4 blocks of a bed in a village with a hall and it becomes a nursery village: children grow up in half
  the time and one more baby a day may be born (three a day with Large Families). At night a child of the house sleeps
  in the cradle. More cradles add nothing. The hall's beds icon and the Book of Edicts' last row say whether the village
  has one; `cradles` in the config.
- **The Work Horn** (30.11): a brass-banded horn crafted from a goat horn, a gold ingot and an emerald. Hold it up in
  a village like a goat horn and, when it sounds, every grown villager works 50% faster for 5 minutes (the speed cap
  still holds), with sparks over them. Once a village a day; afterwards the villagers are worn out (10 less happy) until
  dawn. Only the hall's owner and friends can call a rush in an owned village. The Book of Edicts' last row shows the
  horn ready or used. `workHorns` in the config.
- **A workplace for every worker** (27.11): twelve of the village houses are now in the Blueprint Table for builders to
  build: Builder's Workshop, Carpenter's Workshop, Kitchen, Post Office, Guard House, Clinic and Ferry House, and with
  Cobblemon the Trainer's House, Leader's Hall, Ball Workshop, Trade Hall and School. Each has its job block and a bed;
  the worker who needs it moves in. The Steward now builds a workplace for every worker left without a workstation:
  Kitchen for chefs and butchers, Guard House (Barracks from a Town) for guards and weaponsmiths, Clinic (Healing
  Center from a Village) for nurses and clerics, Library for librarians and scholars, and so on for 27 jobs, six more
  with Cobblemon. He also builds one for a job the village wants when no block for it is free: a Guard House when
  guards are short and no grindstone or Guard Post is free. A Ferry House goes on the shore, with water within 4 blocks of its door. With no builder he asks you
  to place a Blueprint Table and give a villager the job.
- **House looks** (23.10a): the Village Hall's Builds button opens House looks, a list of the village's own houses
  (workshops, clinics, guard houses...). The village's leader (the hall's owner and their friends) can give any of them
  another village style's outside (plains, desert, savanna, snowy or taiga); the village's builder rebuilds the outside
  in place and leaves the room inside, its job block and its chests as they were. Builders now also pick up what falls
  off a block they take down (a lantern under a porch roof) instead of waiting for it.
- A village habitat of its own (Cobblemon 1.8, ROADMAP 28.14): an Expert Habitat Keeper puts one natural Habitat Block under the finished Habitat Garden's mossy centre stone (it still looks like moss), with Pokémon that suit the biome (`data/aliveworkplace/village_habitats`, 20 biome files). One per village; taking the garden down removes it without a drop. Keepers visit the Habitat Blocks round their pasture each day and the hall's list shows who comes today. On Cobblemon 1.7 the keeper's page says it needs 1.8. Config `villageHabitats`.
- **Builds for the Pokémon jobs** (28.13, with Cobblemon): the Camp Kitchen (an open timber shelter round a Campfire
  Pot, benches and a grain store; II adds a Hearty Grain plot and a smokehouse), the Berry Nursery (fenced farmland beds
  in pairs, a composter and a potting bench; II adds a greenhouse with four more beds) and the Daycare (a barn with a
  straw-floored nursery and a paddock round a Pasture Block; II adds a second paddock and a hatchery), the Habitat Garden
  (a wild garden in a hedge with a pond, a Saccharine tree and a Pasture Block round a mossy centre stone; II adds a
  keeper's hide on stilts and a second pond) and the Gem Grotto (a stone shed over a lava pool behind glass, ledges for
  tumblestones, a stonecutter and an amethyst niche; II adds a deeper chamber with four Deepslate Crystal Cores, plain
  deepslate before Cobblemon 1.8). In the Blueprint Table and sold by Journeyman Camp Cooks, Berry Breeders, Daycare
  Keepers, Habitat Keepers and Gem Growers; the Gem Grotto needs no Cobblemon. Builders put the pot on the Campfire Pot.
- **The Daycare Keeper** (28.12, with Cobblemon): a villager at a Pasture Block, picked with an egg. Leave one pair of
  Pokémon per player (three pairs per keeper); her screen says how well they get along. Each dawn she may find an egg
  (70/50/20%, +10% at Expert), 4 emeralds each to collect: a real Cobbreeding egg with Cobbreeding, otherwise the level-1
  hatchling with inherited IVs, Everstone nature, ball, hidden ability and egg moves. Her pairs go to their PCs if she
  dies. Config `daycareKeepers`.
- **Gifted villagers** (29.6): about one villager in 30 has a rare gift as well as their traits, shown in gold on the
  Village Hall's list. Prodigy learns three times as fast; Iron Will never panics and keeps working through raids and
  the bell; Silver Tongue's trades are 20% cheaper; a Night Owl works from dusk to dawn and sleeps from mid-morning to
  mid-afternoon (builders and miners too). A Gifted villager sparkles when they level up, and the chronicle notes one
  who joins from the inn or grows up. Gifts are data (`data/<ns>/gifted/<id>.json`); config `giftedChance` (30; 0: none).
- **Legends who visit** (29.8): once a day a Legend whose village qualifies may come as a guest (1 time in 4 by
  default; Open Gates makes it likelier): to the inn in the morning instead of a traveller, with the traders on market
  day, to a big festival's fireworks, to a finished Chapel at a full-moon midnight, or to the Village Hall in the
  morning. Guests are announced, take no job and stay up to 3 days; right-click one for their terms (who they are, what
  they'd bring, what they want with ticks and crosses, days left). The round every need is met they settle: they claim
  the bed in the tier III home, become a Master of their trade (taking a free workstation) and go in the chronicle.
  Otherwise they leave on the third evening when nobody's looking, the chronicle says what they missed, and they don't
  come back for a week.
- **Four more gifts, and Legends born** (29.7): Lucky (luck +3 on explorer finds, Netherworker trips, sifting and
  fishing; the sifting tables now weigh their rare finds by luck), Hardy (never ill, twice a villager's health), Beloved
  (everyone whose bed is within 16 blocks of theirs is +5 mood, "a beloved neighbour") and Born Leader (workers of their
  trade within 16 blocks work 10% faster). A child of two schooled Masters grows up a Legend 1 time in 20 (a Rare one
  born to a parent's trade, when the village qualifies and the slot is free), else Gifted 1 time in 4, and the chronicle
  says so. Inn travellers are Gifted 1 time in 10, shown on the hire screen, at twice the price.
- **Legends' needs and strikes** (29.5): once a day the Village Hall checks what each settled Legend needs: a home of
  their own (their bed in a finished tier III building, shared with nobody but their spouse), their luxury once a week
  (wine, jewels, books or fine clothes, taken from a chest in their home or else the village store, +10 mood; for now
  honey bottles, amethyst shards and emeralds, books, and leather armour, through the `aliveworkplace:luxury/*` item
  tags) and a happy village (average mood 60, or wellbeing 60% with moods off). After a 3-day grace, a need unmet two
  days running starts a strike: their powers stop, their trade's work stops, they picket by the hall by day under a red
  "On strike: a home of my own" line, and the hall, the Legends page and the chronicle say so. The day every need is
  met they go back to work. Legends never leave: no inn departure, no despawning, and the hall's call-home passes them
  by. `legendNeeds` in the config turns it off.
- **Legends on the hall, and how they look** (29.4): the Village Hall has a Legends page (a nether star in the page
  row): the village's own Legends first, then every Legend as a card with its rarity, how it comes, each condition with
  the village's progress, the luxury it likes, its powers, "lives in ..." for a Legendary taken elsewhere and the Mythic
  line. The hall's list puts Legends first, name and title in gold, with their powers, needs (tick or cross) and any
  strike in red. "What next?" names a Legend one condition short, villagers chat about Legends and guests, and Legends
  wear an outfit over their trade's (a gold circlet and wine-red cape until each has its own), sparkle every 10 seconds
  and have their name in gold over their head. README has a new Legends section.
- **The Steward's rules for homes and storage** (27.10): with beds short he first upgrades a home whose next tier
  sleeps more (beds counted from the blueprints), then wishes for a Starter Cottage, Stone House or Terrace in Homes, or
  an Inn in Market for a town. He also asks for a Storehouse and its upgrades and a Market Stall in Market, and a Berry
  Farm and a Ranch in Farms when food is short.
- **Legends: rarities, caps and the server's record** (29.3): the server keeps a saved record of every Legend. A Rare
  Legend comes once to each village, a Legendary one once to each world, and a village holds Mythic ones by its rank
  (`mythicLegendCap` in the config file, default Hamlet 0, Village 0, Town 1, City 2). Rare and Legendary arrivals are
  told to the village and the hall's owner, Mythic ones to every player in gold with the village's direction from spawn;
  all go in the chronicle under a nether star. A Legend in a grave or turned zombie keeps their slot and comes back as
  themselves; with no grave their slot frees after 7 days; Legends join a hall placed again.
- **Conscription** (30.10): a new edict under a stone sword. While the village is raided (a monster or bandit raid, or a
  pillager raid there) every grown villager who isn't ill fights beside the guards: they wake, never panic, hold a stone
  sword made for the raid (never taken from a chest, gone when the raid ends) and go for the nearest raider within 24
  blocks, 3 damage a blow, 15% more if Strong. Children and the ill hide as before, and conscripts can fall like anyone
  else. The cost: all work in the village stops during the raid and until noon the next day. Reform **The Militia
  Drill** (32 iron swords, 32 shields, clear out 48 monsters): everyone still fights, but work stops only for villagers
  with a raider within 24 blocks, and the morning after is a normal day.
- **Curfew** (30.9): a new edict under a bell. From dusk to dawn every grown villager but the guards and mercenaries
  goes to bed, and a monster can't hurt a villager asleep in their bed; monster raids and bandit camps are half as
  likely, and the nights count as safe in the village's wellbeing. The cost: nobody trades with players at night
  ("Curfew: come back in the morning."), a festival ends at dusk without fireworks, market traders leave at dusk, and
  netherworkers and explorers don't set out after midday. It can't be in force alongside Open Gates. Reform **The
  Lamplighters** (24 lanterns, 8 glowstone, clear out 8 monsters): raids stay half as likely and the nights safe, but
  trading, festivals, markets and night work go on.
- **Festival Season and Tithe** (30.8): the two treasury edicts. **Festival Season** holds a festival every 4 days
  instead of 8; each costs the treasury 3 emeralds and 1 more for every 4 villagers, taken on the festival's morning.
  When the treasury can't pay there's no festival: the chronicle says there was no money and the village is 5 less
  happy that day ("disappointed"). A festival called with a cake stays free, and the hall's festival icon shows the
  cost and what the treasury holds. Its reform **The Festival Fund** (4 cakes, 32 firework rockets, 8 note blocks) keeps
  them every 4 days for free. **Tithe** puts a tenth of the emeralds players pay the village's villagers into the
  treasury (up to its cap), but their emerald prices are 10% higher (rounded: 20 becomes 22, trades under 5 emeralds
  don't change). Its reform **The Fair Ledger** (4 books and quills, 16 gold ingots, beat one of the village's trainers,
  or clear out 8 monsters without one) keeps the tithe at the usual prices. Data packs get the effect types
  `festival_every`, `festival_cost`, `tithe` and `trade_prices`.
- **Open Gates** (30.7): a new edict. Inns in the village take 4 guests (not 2) and up to two travellers arrive a
  morning; market days bring one more trader. The cost: bandits make camp near the village twice as often. Open Gates
  can't be in force alongside Curfew. Its reform **The Watchful Gate** (16 iron ingots, 32 arrows, clear out 12
  monsters) keeps the travellers coming with bandits no likelier than usual. The Book of Edicts tells it in words. Data
  packs get the effect types `inn`, `market_traders`, `bandit_camps` and `legend_visits` (read once Legends visit inns).
- **Free Bread and Large Families** (30.6): two new edicts. **Free Bread** makes everyone fed in the last day 10
  happier ("free bread"), but the village eats 30% more (the hall takes 3 extra meals for every 10 eaten); its reform
  **The Common Granary** (64 wheat, 16 hay bales, 8 barrels) takes the extra food away. **Large Families** lets up to
  two babies be born a day, but a baby needs 24 meals in the store (not 16), the family eats 12 (not 8) and villagers
  fall ill 50% more often; its reform **The Midwives** (8 honey bottles, 16 white wool, 4 golden carrots) keeps two
  babies a day with the usual food and sickness. The Book of Edicts tells both in words. Data packs get the effect
  types `food_use`, `births` and `sickness`.
- The **Gem Grower** (28.11): sneak-right-click a villager by a stonecutter with an amethyst shard. She picks the full
  amethyst clusters round budding amethyst (never the budding block); with Cobblemon she plants tumblestones against
  lava or magma and picks the full clusters, and with Cobblemon 1.8 sets Type Gem Blocks against Deepslate Crystal Cores,
  picks the stage-3 clusters and makes Blank TMs from shards and glass (up to 8). Beds are data files
  (`gem_beds/<name>.json`); sneak-right-click her to pick which she keeps. Rock and Steel partners. Config `gemGrowers`.
- With Cobblemon, the **Habitat Keeper** (28.10): sneak-right-click a villager by a Pasture Block with a honey bottle.
  She keeps Poké Snacks set out on up to three lure spots (marked with a Field Marker, or grass 16-32 blocks out) and
  sets out another when one is eaten up; picks a lure (a type or egg group, Alphas on 1.8) and asks the Camp Cook for
  snacks seasoned with its berries; slathers Saccharine logs with honey and plants Saccharine saplings; and tells the
  village of shiny, rare and Alpha wild Pokémon (chronicle, the hall's list). Flying and Grass partners. Config
  `habitatKeepers`, `habitatSightings` (on).
- **The Steward gives jobs and picks research** (27.9): each morning every grown jobless villager (never a nitwit or a
  child) gets a free workstation for the village's biggest gap: a builder while there's none, a farmer while food is
  short, guards while they're short, a porter at a free Storehouse, a scholar while research is idle, then the nearest
  block. At a shared block he gives its other jobs too: the composter in a Berry Farm with no Orchard Keeper gets one.
  In Ask me first the morning's jobs are one proposal ("Give 3 villagers jobs: Dara, Farmer at the Composter 12 blocks
  east; ..."). When a scholar works with nothing to research, he picks the next topic: Fortification after a raid this
  week, Medicine with 2 or more ill, Green Thumb while food is short, Logistics with the store 80% full, else Swift
  Hands, Hearth and Kinship, never one that can't be taken up yet; in Ask me first it's a proposal. Run the village does
  both by itself; Rest does neither. Both are noted in the chronicle.
- **The Steward's desk** (27.8): with a Steward, the hall's "What next?" page is his desk: his level, the mode (**Ask me
  first**, **Run the village**, **Rest**), his open builds (shift-click cancels one), up to 9 proposals and the tips as
  before. Each proposal shows the building and its style, why, where, the five materials it needs most against the
  store, and which builder takes it after what; its page has **Approve**, **Decline** (not proposed again for 3 days),
  **Show me** (its outline glows for 30 seconds), **Another spot** and **Another style**, and the desk has **Approve
  all**. Approving starts the build for that builder, owned by the hall's owner, and notes it in the chronicle (new
  kind: Plans); upgrades go on the building's own spot. In Run the village he approves them himself each morning and
  tells the owner in one line; Rest plans nothing. Unanswered proposals lapse after 3 days; the owner hears once a
  morning when new ones are waiting. Same rights as the plan. Config `stewardSelfRun` (off: every village asks first).
- **Finding a plot** (27.7): for each building he wishes for, the Steward finds where it fits in a zone of its kind, in
  the zone's style: nearest the hall first, its front facing the nearest road on the plan (else the hall), the
  footprint and 2 blocks round it inside the zone and off its roads, the ground under it within 4 blocks of level, at
  most a tenth over water and none over lava, nothing in the way but natural ground, plants and trees (packs add more
  with the block tag `aliveworkplace:steward_clearable`), 2 blocks clear of every build site and building, within a
  builder's reach of a Blueprint Table, and never the same building mirrored the same way within 24 blocks. The search
  reads at most 64 columns of ground a tick per village and keeps its answer until the plan or a build changes.
- The **Steward's rules** (27.6): what the Steward wants is data, `data/<namespace>/steward_rules/<name>.json` (conditions
  such as `beds_short`, `food_short`, `jobless`, `store_full`, `research_idle`; one effect: build, upgrade, give jobs,
  research or ask a player; priority, reason, cooldown, max, rank and mods). Each morning, back at the hall, he ranks
  the rules that hold into the day's wishes and says the first over his head ("build a Stone House in a Homes zone: 3
  villagers have no bed"); they're saved with the hall. `/workplace steward explain` lists every rule for the nearest
  hall with each condition's number and whether it held. 13 starter rules; they read the same numbers as the hall's
  "What next?" tips, so the two always agree. A broken rule file is skipped with a warning naming the file and field.
- **Reforms, and The Shift Bell** (30.5): while an edict is in force, the hall's quest page shows its reform's next step
  in the row below the daily quests (a book and quill; it never expires). A new step goes up each morning after the last
  was done, and each pays emeralds like a quest (a quarter more a rank). The last step reforms the edict for that
  village for good: its boost stays, its cost goes, fireworks go up over the hall, the village is told and the chronicle
  and the Book of Edicts keep it. Progress is kept while an edict is lifted. Long Shifts' reform is **The Shift Bell**:
  bring 4 clocks, 8 gold ingots and 32 bread, and the village keeps working 20% faster without the mood loss. Data
  packs give their edicts a `reform` with `bring`, `slay` or `battle` steps (a battle falls back to its `fallback` step
  without Cobblemon or a trainer).
- The **Book of Edicts** (30.4): the Village Hall's lectern button (slot 9), or a Village Ledger used while sneaking,
  opens the village's laws: its edict slots (in force with their days, free, or locked until the next rank), and every
  edict with its boost in green and its cost in red. Click an edict twice to proclaim it, once in force to lift it; only
  the hall's owner, friends and operators may. The hall's name icon lists the edicts in force, and the people list's
  page arrows moved to its bottom corners (25 people a page).
- The **Steward** (27.5): sneak-right-click a grown villager beside a Village Hall with that hall's City Plan and he
  becomes its Steward, one per hall. Each morning he walks his rounds holding the plan (each zone, the storehouse),
  three seconds at each stop, then goes back to the hall for the day. He trades City Plans, Blank Blueprints and
  Village Ledgers and buys paper and books; his level will set how many of his builds may be open at once (1 to 4,
  capped by the village's rank and `stewardMaxOpenBuilds`). New outfit; breaking the hall ends the job; config `steward`.
- With Cobblemon, the **Camp Cook**: sneak-right-click a villager by a Campfire Pot with Hearty Grains. She cooks in the
  pot itself (makings in, seasonings on top, lid shut, the pot's own cooking time) from the chest beside it: Poké Snacks
  and Bait, Aprijuice, Exp. Candy, Ponigiri, stews, curry and more, 16 of each, and the sweets and candies for a
  Storehouse's stock orders. Her menu is data (`camp_menu/*.json`); her meals feed the village and count for Diet;
  farmers sow Hearty Grains and Vivichoke. Fire and Normal partners. Config `campCooks` (on).
- **Legends, the engine** (1.3's first piece): Legends are read from data packs (`data/<ns>/legends/<id>.json`, so
  server owners can add their own), each with the conditions a village must meet (rank, villagers, finished buildings
  and styles, Masters of a trade, emeralds the treasury has ever taken in, caravan routes, meals, festival crowd,
  animals at work, research, iron golems, full moon, first City, another Legend), the luxury they like and their
  powers: workers near them work faster (never more than twice as fast) and villagers near them are happier. A new
  `legend` profession with its own outfit, `/workplace legend list|make <id>|clear` for ops, and a `legends` switch in
  the config. No Legend ships yet: the twelve come in the next updates.
- With Cobblemon, the **Berry Breeder**: sneak-right-click a villager by a composter with any Cobblemon berry. Her
  berry book (sneak-right-click her) lists every berry Cobblemon knows (data packs' too): the ones the village has found
  lit, the rest with the pairs that make them. Click one and she works out the chain from the village's berries, plants
  each step's two parents side by side in the farmland round her composter (or a Field Marker's plot), puts Growth and
  Surprise Mulch on them, picks the fruit and notes every new berry in the Village Hall. She sells common berries,
  mulch and, from Journeyman, the berries her village has found; Grass and Bug Pokémon help her, and a Bug partner
  flits between the paired plants. Config `berryBreeders` (on).
- **The City Plan** (the first piece of 1.1, villages that build themselves): a new item, crafted from a Map and a
  Blank Blueprint. Right-click a Village Hall to bind it; right-clicked in the village it tells you which zone of the
  plan you stand in. Each hall now keeps its village's plan (zones of eight kinds: Homes, Workshops, Farms, Market,
  Civic, Gardens, Defences, Keep Clear, which packs can add to), and a broken hall carries its plan to wherever it's
  put down. Only the hall's owner, their friends and operators may change a plan.
- **Painting the plan**: right-click the air with a bound City Plan to open the plan screen: the village map with a
  grid over it, each zone tinted in its colour and named, Keep Clear hatched and build sites going up outlined. Add,
  rename and delete zones, set each one's kind, style and "renew old houses" switch, paint cells with the brush or
  drag an area, erase (right-click erases too), undo the last 10 changes, and read the legend.
- **Roads and the wall line on the plan**: the plan screen's Road tool draws a road point by point (double-click or
  Enter to end) as a lane (1 wide), a street (3) or an avenue (5), in the style of the zone it starts in unless you pick
  one; right-click a road to take it off. The Wall tool draws one wall line round the village, open or closed. A plan
  holds up to 24 roads of up to 64 points, and roads you draw are approved for the builders. While you hold the City
  Plan, the zones' edges, the roads and the wall line show on the ground round you in their colours, and the hall's
  map button now draws the zones and roads on the map, ready to hang in an item frame by the hall.
- With Cobblemon, the **Pokémon Center**: a bright hall with a glass front under a red roof, Cobblemon's Healing
  Machine on the counter, a PC beside it, potions and benches; **Pokémon Center II** adds a lodge with four beds and a
  trade corner with a Shop Counter, and a garden with a Pasture Block. In the Blueprint Table (with Cobblemon only)
  and sold by Journeyman Nurses; the Village Hall's "What next?" suggests one to a village of Village rank.
- A Healing Machine is a Nurse workstation too (hand the villager there a honey bottle). Right-click a nurse working at
  one and she puts your team in the machine, free; while she's on shift it stays charged. Config
  `nurseHealingMachine` (on). Builders now build Cobblemon's two-block PC and Pasture Block.
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
- Ready for 1.0: the mod's version now names its Minecraft version, as the jar's name does (`0.138.0+1.21.1` today,
  `1.0.0+1.21.1` at 1.0), so Mod Menu, crash reports and bug forms say which game it is for. Mod Menu links to the
  source and the issue tracker; the README's license section says what GPL-3.0 allows and credits MineColonies, whose
  ideas two jobs take; this changelog starts with how to read it.
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
- **Edicts**, the village's laws, and the first one, **Long Shifts**: everyone works 20% faster, but every grown
  villager is 10 less happy ("long shifts" in the hall's list). A Hamlet may keep 1 edict in force, a Village 2, a Town
  3, a City 4; an edict stays at least 3 days (`edictMinDays`), and a village that drops a rank loses its newest.
  Everyone in the village is told, and the chronicle keeps it. For now operators proclaim and lift them with
  `/workplace edict proclaim|lift <id>` in the village (the Book of Edicts on the hall comes next); only the hall's
  owner, their friends and operators may. Data packs can add edicts (`data/<namespace>/edicts/`) or switch ours off
  (`"enabled": false`); `villageEdicts` turns them all off.

### Changed
- **Reforms are a real grind** (30.1a): every edict's reform now asks for hundreds of items, handed in over as many
  trips as it takes (The Shift Bell: 24 clocks, 128 gold ingots, 300 bread, where it was 4, 8 and 32; the clearing-out
  steps ask for 32 to 40 monsters). What a step already has is kept; the emeralds each step pays are unchanged.
- **A Steward must be a seasoned Builder** (27.1a): only a Builder of Journeyman level or higher can be appointed
  Steward (he starts the job as a Novice Steward); others are refused with what's needed. Stewards already appointed
  keep their job. The City Plan now also takes a Heart of the Sea (Map + Blank Blueprint + Heart of the Sea).
- Builders passing materials to a crewmate close by make only a soft sound (no item flies), and the whole server plays
  at most 4 of those sounds every half second, so many busy crews can't cause lag; the rest are skipped.
- Seasons last 16 days now (a 64-day year), so festivals come every 16 days. A config file that still holds the old
  default of 8 moves to 16 by itself; any other length you chose stays.
- **One pace, one cap**: everything that makes a worker faster (Pokémon partners, a well-kept village, Swift Hands,
  Diligent, a happy mood, Craftsmanship, Expeditions) now adds up to at most **twice the usual pace**, set by the new
  `maxWorkPace` option (100 to 400 percent, default 200). Sickness, a bad mood, Lazy and a badly kept village still slow
  a worker after that (ill: half the capped pace), and a worker's level (Novice to Master) stays outside the cap. A
  builder's status and the hall's people list show the total and why ("100% faster: at the cap (Machop from the
  pasture, a happy mood)"). Teachers, scholars, ranchers, crafters' Craftsmanship and explorers' and netherworkers'
  rests and trips now get the whole pace too, not only their partners. **This slows down the very fastest workers**:
  a Master with three partners in a well-kept village was already past twice as fast.
- **Partners count once**: sifters, beekeepers, florists, composters and explorers searching a stop counted their
  Pokémon partners twice; now once (two partners: 70% of the usual time, not 49%). **These five jobs are slower with
  partners than before.**

### Fixed
- A crew's last blocks no longer wait on a walk to the chests (B66): a helper with nothing left to help with brings
  the lead what the last blocks need, where it used to carry it back to the chests (the build's last lantern) for the
  lead to walk over and fetch. A crew of four built the stone house in 28-35% of the time alone in 15 runs (it was up
  to 40%, and 44-47% on CI).
- The Pathfinder really stands still while the player catches up: between its checks, its everyday routine could walk
  it off (5 blocks once).
- Villagers no longer get stuck against the flower boxes beside the steps of our village houses (every village type):
  the pots now stand on upside-down stair sills instead of head-height trapdoors, which villagers mistook for open
  ground (B69).
- The showcase client starts every scene again without Cobblemon: the Habitat Keeper scene's wild Eevee is spawned
  from a Cobblemon-only helper, so loading the scenes no longer crashes (B59).
- A pastured Pidgey (or any flying partner) taking the postman's air mail no longer vanishes into your PC: it climbs
  only as high as its Pasture Block lets it roam, instead of flying past that and being sent back (B52).
- Every switch on the settings screen fits its button again, on or off: shorter names for Nurse Healing Machines,
  Villages Work Unseen, Legend Needs, Strikes, Stewards Run Villages and Legends at Ruins (each tooltip still says it
  all) (B61).
- Berry Breeders count their Pokémon partners once: two partners make mulching take 70% of the usual time, not 49%,
  so a partnered breeder stays under the `maxWorkPace` cap like every other worker (B53).
- A builder whose path leads somewhere it can't actually walk (under a trapdoor flower box, up into a loft) now hops
  there after two seconds standing still instead of five, so a crew's last blocks no longer drag on.
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
- A full build with the compat GameTests now finishes in the 7 GB dev container: the compat server gets a 2.5 GB heap
  and the Gradle daemon hands its unused memory back while it waits (B55).

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
