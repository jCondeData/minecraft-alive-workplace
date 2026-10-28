# Villager jobs

How a villager gets a job in this mod, what every job has in common (chests, levels, pace, status lines), and the
list of all the jobs with the block and item that start each.

Roadmap items: 21.1a, 21.1c, 24.1, 30.2, 28.8, 28.9, 28.10, 28.11, 28.12, 34.10, 34.12, 34.11, 26.6, 26.13, 26.12, 36.11, 36.12, 36.18, B97, B101, B103, B226, B224, B220, B221, B292, B333, B334, B344, B348, B349, B444, B445, B466, 40.3, 36.20, 36.22

## What a player sees

**Vanilla jobs work as in vanilla:** put the block near a jobless villager and they take it (a block several jobs
share gives one of its jobs at random, likeliest the one the village has fewest of: [Random jobs](random-jobs.md),
36.17). Most of the mod's jobs share a vanilla block. To start one of ours, stand the villager within about 4 blocks of the block and
**sneak-right-click them holding the job's item**. A villager already working there switches the same way, and the
block's own job comes back with its own item (wheat for a Farmer, coal for an Armorer). You are told what happened
("Dara took the Miner job at the Blast Furnace."), or what is missing (no such block near, or every one taken).
A jobless villager already on the way to a block they picked themselves lets it go, so another villager can take it.
Hold Shift over a workstation in your inventory to see its jobs and their items. A job whose switch is off (or that
needs Cobblemon, without it) isn't listed.

**Look at a villager to see its job and level (B221).** Point your crosshair at any working villager, from up to
about 3 blocks away, and a name plate over its head reads "Fisherman, Journeyman". One with a name of its own reads
"Dara — Fisherman, Journeyman". Every job shows, ours and vanilla's; a jobless villager or a nitwit shows nothing new.

**Retraining (26.6).** A villager who already has a job (vanilla, ours or another mod's, traded with or not; never a
child or a nitwit) becomes a Builder at a free Blueprint Table within about 4 blocks, sneak-right-clicked with a
Blueprint or a Blank Blueprint. The first click shows the price ("Dara (Librarian, Journeyman) can be retrained as a
Builder for 20 emeralds. Their trades and level are lost: they start again as a Novice."; "an Armorer" for a job
that starts with a vowel); a second click within 5
seconds pays and retrains them. The price goes by level: Novice 5, Apprentice 10, Journeyman 20, Expert 35, Master
50 emeralds (`retrainPrices`), or 100 CobbleDollars an emerald with the Cobbleverse pack. Too little money: "Retraining
Dara costs 5 emeralds: you're 2 emeralds short. Nothing changed." A jobless villager becomes a Builder the same way
for free. A builder given another job hands their builds back first: what's built stays, the blueprint comes back to
its owner still placed, and another builder carries on from there. The Village Hall's **Change job** (36.18, see
[The Village Hall](village-hall.md)) retrains a worker to any free job block in the village, or the Builder, for the
same price, with no need to bring them to the block: the job and the block's claim are set on the paying click, and one
who can't walk there within about a minute is moved beside it. Since 40.3 the same is in the hall's People table:
**Job ▾** on a villager's row lists every job, those with a free block in the village saying how many and which
("Mason · 1 free Stonecutter", "Bard · 1 free Music Stand"; a beehive nobody works at counts for the Beekeeper), the rest grayed with the block to place ("Mason · place a Stonecutter first"), the Builder among
them and **No job** last (free, two clicks: their trades and level go and their block is left free). The price by
their level is on top before anything is paid, a first click shows the offer and a second pays. A jobless villager is
given a free block for nothing, in one click. Children, nitwits and Legends can't; game rule `workplaceRetraining` off
hides Job ▾.

**Fishermen per village (26.13).** A barrel is the Fisherman's workstation, and builds are full of storage barrels,
so a village takes only so many fishermen: 1, plus 1 more for every 10 villagers within the village radius (48 blocks)
of the barrel. Once a village has its fishermen, a jobless villager passes free barrels by and takes another free
workstation instead (or stays jobless if there is none). A fisherman the village already has keeps his job when it is
over the cap, across a restart too, and a job given by order (the hall's job menu) isn't held back. Only living
fishermen within the radius count: when one dies, or stands farther from the barrel, the barrel is free for the next.

**Built-in workstations (B168).** A workstation built in under solid blocks, like the Kitchen's smoker in the mouth of
its brick oven, is still taken: a villager who picks it walks to the open floor beside it (the side it faces first), not
onto a chest or worktop nearby, and takes it from there.

Chests and barrels within 8 blocks of the workstation are that worker's supply chests: tools and materials come out
of them and the work goes into them. A line over a worker's head says what they are doing or waiting for (its count reads "Netherworker · 1 expedition", "2
expeditions", in the singular at one, B444), and
sneak-right-clicking a worker with an empty hand shows their status, ending with their level in their own job
("Apprentice Miner · 10/70 XP to the next level") and how fast that makes them.

| Job | Workstation and item | Class |
|---|---|---|
| Builder | Blueprint Table (+ a Blueprint or a Blank Blueprint to retrain a working villager) | any |
| Miner | Blast Furnace + a pickaxe | any |
| Lumberjack | Fletching Table + an axe | any |
| Orchard Keeper | Composter + sweet berries, glow berries or an apple | any |
| Farmer | Composter (vanilla) | any |
| Florist | Composter + a small flower | any |
| Composter | Composter + bone meal | any |
| Beekeeper | Beehive or Bee Nest + a glass bottle or shears | any |
| Sifter | Cauldron + gravel, sand, red sand or soul sand | any |
| Vintner | Cauldron + sweet berries, glow berries or an apple | any |
| Tailor | Loom + string | any |
| Printer | Cartography Table + an ink sac | any |
| Leatherworker (dyer) | Cauldron (vanilla) | any |
| Scholar | Lectern + paper | Burgher |
| Teacher | Lectern + a book | Artisan |
| Librarian (scribe) | Lectern (vanilla) | any |
| Tinkerer | Smithing Table + redstone | Artisan |
| Toolsmith | Smithing Table (vanilla) | any |
| Netherworker | Cartography Table + netherrack | Artisan |
| Cartographer (explorer) | Cartography Table (vanilla) | any |
| Undertaker | Brewing Stand + a golden apple or a totem | Burgher |
| Nurse | Brewing Stand + a honey bottle | Artisan |
| Cleric (alchemist) | Brewing Stand (vanilla) | any |
| Rancher | Smoker + a saddle or a golden carrot | any |
| Chef | Smoker + raw meat, raw fish or a potato | Artisan |
| Butcher (herder) | Smoker (vanilla) | any |
| Carpenter | Crafting Table + planks | any |
| Mason | Stonecutter (vanilla) | any |
| Gem Grower | Stonecutter + an amethyst shard | any |
| Jeweler | Stonecutter + a gold nugget | Burgher |
| Armorer (smelter) | Blast Furnace (vanilla) | any |
| Weaponsmith | Grindstone (vanilla) | any |
| Guard | Grindstone + a sword | any |
| Firewarden | Grindstone + a water bucket | any |
| Well Keeper | Cauldron + a shovel | any |
| Physician | Brewing stand + a glistering melon slice | any |
| Mechanic (with Create) | Create Toolbox, any color (2.3) | any |
| Furnicrafter (with CobbleFurnies) | CobbleFurnies' Furni Crafter (2.2) | any |
| Fletcher | Fletching Table (vanilla) | any |
| Shepherd | Loom (vanilla) | any |
| Fisherman | Barrel (vanilla) | any |
| Porter | Storehouse | any |
| Postman | Mailbox + paper | any |
| Shopkeeper | Shop Counter | Artisan |
| Innkeeper | Shop Counter + a bed | Artisan |
| Ferryman | Travel Post | any |
| Bard | Jukebox + a music disc | any |
| Steward | Village Hall + its City Plan, on a Journeyman Builder | any |
| Trainer (Cobblemon) | Training Post | any |
| Trainer Leader (Cobblemon) | Training Post + a block of gold | Burgher |
| Move Tutor (Cobblemon) | Training Post + a book | Artisan |
| Ball Smith (Cobblemon) | Smithing Table + an apricorn | Artisan |
| Pokémon Trader (Cobblemon) | Shop Counter + a Poké Ball | Artisan |
| Fossil Scientist (Cobblemon) | Cobblemon's Fossil Analyzer + a fossil | Artisan |
| Berry Breeder (Cobblemon) | Composter + any Cobblemon berry | any |
| Camp Cook (Cobblemon) | Campfire Pot + Hearty Grains | any |
| Habitat Keeper (Cobblemon) | Pasture Block + a honey bottle | any |
| Daycare Keeper (Cobblemon) | Pasture Block + an egg | any |

The Class column only counts once classes are on (see [Classes](classes.md)). Each job's own page or README section
says what it does all day.

## How it works

**Job plates (B221).** The client already knows every villager's job and level (vanilla sends them, for the outfit
and the level badge), so the plate is drawn on the client from those: `JobPlate` composes the text
(`message.aliveworkplace.plate.job`, "%s, %s", with the job's name and vanilla's own level names Novice to Master;
`message.aliveworkplace.plate.named`, "%s — %s", in front of a name), `JobPlateShownMixin` lets an unnamed working
villager show a plate when it's the crosshair's target (vanilla only does that for a named mob), and
`JobPlateTextMixin` puts the text on the plate as it's drawn. A Builder's status above its head moves up out of the
plate's way.

**A fresh hire starts at once (B220).** A Fisherman handed a rod or a Builder handed a blueprint during their work day
is at it within a tick or two. A villager taking our longer hours (a hired Fisherman, a job picked with an item) gets a
new brain, and vanilla's own brain setup has just looked at vanilla's hours; a brain looks at its schedule at most once
a second, so it used to keep vanilla's activity (late in the day, gossiping) for a second. `VillagerMixin` now lets the
new hours apply at once (`BrainAccessor`). Outside their hours the hand-off is taken as before, the villager keeps their
hours (a night's hire sleeps first) and the player is told when the work starts (`Jobs.startsLater`).

**A Miner keeps all they dig (B224).** Stone of every kind, gravel and dirt go into the chests by the blast furnace with
the ores; with those chests full (or none), the rest goes to the village Storehouse, and ores get the room first.

**Fewer job blocks (21.1a).** Related jobs share a block. A jobless villager by a vanilla block takes the vanilla
job by itself, as in vanilla (since 36.17, one of the block's jobs at random: [Random jobs](random-jobs.md)); the
player picks one of ours with its item. A crafting table, a beehive, a jukebox or a
Mailbox never takes a jobless villager by itself. Where one item fits two blocks (paper, a book, a glass bottle), the
block they work at, or else the nearest, decides. The older job blocks (the Builder's Bench, the Fruit Basket...)
can't be crafted any more, but ones already placed keep working, so old worlds are fine.

**Picking is not hiring.** Picking a job gives the villager the job. Hiring a vanilla worker whose job the mod
extends (an Armorer as a smelter, a Toolsmith...) is still that job's item on a villager who already has the job:
hired, they work with your own workers; unhired, for their village.

**Retraining (26.6).** `work/Retraining` extends the pick (`Stations.choose`, then `Stations.take`), so the owner,
class and free-block checks are the same. Only the Builder costs: the other jobs' items have switched a working
villager for free since 21.1a, and their tests rely on it (ROADMAP Notes, 26.6). The offer is kept in memory only, per
player, for 100 ticks; the second click goes through every check again before it pays (`Money`, so CobbleDollars
through the `Bank` extension point). Paying sets the villager to level 1 with no experience; changing the profession
drops vanilla's trades, and the new job's are made afresh. A builder changing job, paid or not, first lets go of
their work as on death, minus the dropping: queued builds are canceled (blueprints back), a crew member stops
helping, a Wonder's part goes on to its crew (`WonderCrews.handOn`), and their own build is canceled with the
blueprint returned still placed.

**Villages share.** Workers whose workstations are within `villageRadius` blocks (48) of each other are one
village: one short of something takes it from another's chests. Only workers who answer to the same people share
(yours and your friends', or village workers nobody hired).
A Storehouse in the village counts by its block, whether or not its Porter is about (asleep at home far off, in a
chunk that isn't loaded, or gone): it is shared as its owner's (the player who placed or had it built, or nobody for
a village one), so a Builder with no chests by its bench builds from the Storehouse's chests (B226).

**Pace.** Every job's speed goes through one rule: the worker's level, then every bonus (partners, a well-kept
village, research, traits, mood, edicts, guilds, tonics) up to the cap `maxWorkPace`, then penalties (illness, an unhappy
mood, the Lazy trait, a badly kept village), which multiply after the cap, so an ill worker at the cap works at exactly
half the capped pace. The level is applied outside the cap: a capped Master is still 2.5 times a capped Novice. The
worker's status line (sneak-right-click) and the hall's People details say the total and its sources ("Works 62%
faster (...)", "100% faster: at the cap (...)", "37% slower (... held back by being ill ...)"); a worker at the usual
pace gets no pace line. The Builder's page has the level table.

**Requests.** What workers are waiting for (materials, a pickaxe, seeds) is posted on the Storehouse's board, where
players can hand things over; the Village Hall lists it too. The board is drawn like the Blueprint Table (26.12): the
requests as a list on the left (what, how many, for whom and how far), the store's numbers, the Steward's shopping
list and the stock orders "at a glance" on the right, and a request under the mouse read out in full. A click hands
over what you have, as before, and the request drops by that much at once, so a quick second click hands
nothing more than is still needed (B344). On the stock orders page, the item in your hand (the off hand's when the
main hand is empty, B351) gets the "Order" button.

**Miners make their own pickaxe (36.11).** A Miner whose pickaxe wore out, or who has none, first looks in their own
chests for one; with none there, they make one at their Blast Furnace from what they mined themselves: three
Cobblestone (or Cobbled Deepslate or Blackstone) from their bag and their chests, and two Sticks from there or from the
village store, or two Planks cut into sticks (the spare sticks are kept). A Journeyman makes iron when there are three
Iron Ingots in their chests (the blast furnace's own, often), an Expert or Master diamond with three Diamonds: always
the best they can. It takes three seconds at the furnace, the line over their head says "making a pickaxe", and the
owner reads "Dara made a new Stone Pickaxe at their workstation from what they mined." Nothing is taken unless all of
it is there, so a Miner saved and loaded mid-way simply starts it again. Without the materials they wait as before
("needs a pickaxe"), and the Storehouse board asks for what they lack, the cobblestone first and then the sticks,
beside "A pickaxe" (which the Toolsmith may still make): handed over, it goes into their chests and they make it.

**Miners dig deeper as they level up (36.22).** The Quarry Marker's depths go 4, 8, 16, 32, 64 (every miner), then
96 (Journeyman and up), 128 (Expert and up) and "down to just above bedrock" (Master only: the world's floor plus 5,
so Y=-59 in the Overworld), then the strip mines. The deeper choices say the level they need when cycled ("Dig 128
blocks deep (Expert Miners and up).") and in the tooltip. Handed to a miner whose level doesn't reach that far, the
quarry is started anyway but cut off at their depth (64 for a Novice or Apprentice, counted from the marked top), and
the player reads "Dara is at Novice level, so they'll stop 64 blocks down for now. Digging all 128 blocks needs Expert
level; if they get there before they finish, they'll keep going down." Nothing below that is dug. A miner who levels
up while the quarry is open (every work tick `Miners.deepen` checks) takes the floor down as far as the new level
reaches, and the owner reads "Dara reached Expert level and will keep digging, down to 128 blocks deep." The pit is
dug top-down, so deepening only adds layers at the end: the blocks already visited keep their place and the spiral
stair carries on down to the new floor. A quarry finished short of the marker's depth says so ("...stopped 64 blocks
down: digging all 128 blocks needs Expert level. Hand them the marker again once they get there."), and the marker
comes back with its depth. The level is checked against the real pit (a 128 marker cut off by the world's floor at
90 deep only needs a Journeyman). A deep pit (more than 64) counts its distance to the bench from the middle of its
top, where the miner starts down. The safety rules hold all the way down: blocks next to water or lava and blocks too
hard for the pickaxe are left standing.

**Working with nobody near.** While anyone is online, every worker's workstation chunk stays loaded, so villages
keep working when no player is near (`keepVillagesWorking`). Builds and quarries follow the game rule
`workplaceKeepWorkLoaded`.

**Caps for server owners (25.5).** With `maxWorkersPerVillage` above 0, a jobless villager doesn't take a free
workstation once that many are taken nearby; nobody who has a job loses it. `workerPathRange` limits how far a
worker looks for a path in one go; farther walks are made in legs.

**Recipes (B101).** Crafters (carpenters, masons, chefs, tinkerers, the luxury makers) plan with the game's own
recipes, so modded ones count. The mod sorts them by what they make, and works out which items each ingredient takes,
once when the server starts and again after every `/reload`, not on a worker's first look: on a big modpack that first
look went through every recipe in one tick and stalled the server for a third of a second. A plan tries at most
500 recipes (a fence from logs tries five), and all of a world's crafters together at most 1,500 in one tick;
past that, a crafter looks again a few seconds later.

**No first-day stalls (B103).** The mod's classes are loaded on a background thread while the server starts, so the
first hall round, village-mates check or cradle of a new world doesn't wait for them in the middle of a tick (on a big
pack every class loaded goes through other mods' transformers first). The same thread also runs the collection and
shape code a child growing up uses once on throwaway data, so the first child of a Cradle to grow up doesn't load it
mid-tick. Supply chests are looked for only in loaded chunks, and the search never waits for a chunk. The nearest
Village Hall to a spot is searched for once a tick (a worker's pace asks for it for each of its sources), the list of
finished buildings a bed is looked up in (for a villager's home) is worked out once a tick, and the hall's
list of free workstations finds each kind of workstation's job once.

**A shop sells only what its chests hold (B348).** A Shopkeeper's offers are built from the stock when you talk to
them, but the sale itself is checked at the moment of the trade: if the chests by the Shop Counter no longer hold a
whole lot (the owner took it back while you had the trades open, a hopper drained it, another buyer got there first),
the goods don't appear for your payment, a click on them takes nothing, your emeralds stay in the slot and the offer
shows as sold out. Talk to the shopkeeper again once the shop is restocked. Every sale still takes its goods out of
the chests and puts the payment in.

**Hoppers stay out of the price list (B349).** A hopper under a Shop Counter pulls nothing out of it, and a hopper,
dropper or pipe pointed at it pushes nothing in, so nobody can empty or change someone's prices with automation. Only
the counter's own screen changes them, and only the owner, friends and operators open it. The chests next to the
counter are ordinary chests: hoppers still fill and drain those. The same holds for Create's funnels (an extracting
funnel on a counter takes nothing) and for a Tom's Simple Storage inventory connector touching it, which leaves the
counter out of its network.

**Hoppers stay out of mailboxes (B525).** A hopper under a Mailbox pulls no mail out of it, and a hopper, dropper or
pipe (Create's funnels and chutes too) pointed at it pushes nothing in, so nobody can empty someone's mail or fill
their inbox with junk. Mail only comes in through the post, and only leaves through the mailbox's own screen, which
only the owner, friends and operators open. Mail already in a mailbox stays there. An extracting Create funnel on a
mailbox takes nothing, and a Tom's Simple Storage inventory connector touching one leaves it out of its network.

**Item prices count the off hand (B350).** With CobbleDollars, a shop's own screen pays a price that isn't in emeralds
(say 3 diamonds) with the price items you carry: the bag, the hotbar and the off hand all count, and the payment comes
from the bag and hotbar first, then the off hand. Armor you wear never pays.

**Outfits (24.1).** Each of the mod's professions has a vanilla-style outfit and a zombie villager version.

**A Quarry build that refills (36.12).** The Quarry blueprint (`tools/blueprints/quarry.py`, in the Blueprint Table
with "Jobs: Armorer/Miner (1)") is a curbed, fenced rim round an 8 x 9 pit, steps cut down its east side to a landing
four deep, a hoist, and the Miner's shed with the blast furnace and a double chest. The pit (the build's x 1-8, z 2-10,
y -1 to -10) is not in the file: it is the ground as it lies. Every 5 seconds `mine/QuarryBuilds.hire` looks at the
finished Quarries: one with no quarry site of its own is given to the Miner whose job site is its blast furnace and
who has no quarry, as a `QuarrySite` that remembers the build's origin (no marker; the owner, if online,
reads "Dara works the Quarry's pit now..."). The Miner digs it like a marked-out pit (stairs spiral down from the wall
nearest the furnace). Every second `QuarryBuilds.regrow` grows back what is due: one block every day / `quarryRegrowth`
ticks (100 a day: the 720-block pit takes 7.2 days to fill), at most 32 in one go, each into the lowest open air in the
pit (never where an entity stands, never over a block that isn't air, so a player's block stays), picked from a
weighted mix out of 1000 (stone 820, coal 80, copper 50, iron 25, redstone 10, gold 10, diamond 5), the deepslate kind in
the pit's lower third or below y 0, with a random fixed by the site's id, the spot and the count grown. Time unloaded
catches up when the pit loads again; nothing is banked while the pit is full. When the cursor reaches the end, the
Miner drops off the finds and waits at the shed ("waiting at the shed for the pit to grow back") until a layer's worth
(72 blocks) has grown since the last pass, then starts again from the top. A Quarry Marker handed to them ends the
pit's site and starts the marked one; once that is done or canceled the pit is given back. Canceling the pit's site
gives no marker back (there was none), and the Miner takes the pit again within 5 seconds.

## Switches

| Key | Default | What it does |
|---|---|---|
| `supplyRadius` | 8 (2 to 32) | Chests this close to a workstation are its supply chests |
| `villageRadius` | 48 (0 to 128) | Workers this close together share chests; 0: no sharing |
| `maxWorkersPerVillage` | 0 (0 to 500) | The most workers per village; 0: no limit |
| `fishermanCap` | on | A village takes only so many fishermen; off: as many as it has free barrels |
| `fishermenPerVillage` | 1 (1 to 20) | The fishermen every village takes, with the cap on |
| `villagersPerFisherman` | 10 (1 to 100) | One more fisherman for every this many villagers, with the cap on |
| `workerPathRange` | 48 (16 to 128) | How far a worker looks for a path in one go |
| `keepVillagesWorking` | on | Villages keep working while no player is near |
| `minerCraftsPickaxe` | on | A Miner with no pickaxe makes one at their workstation from what they mined; off: they wait for one |
| `quarryBuild` | on | The Quarry build's Miner digs its pit without a marker and it grows back; off: not offered, nothing grows back |
| `quarryRegrowth` | 100 (0 to 2400) | Blocks that grow back in a Quarry's pit in a Minecraft day; 0: none |
| `maxWorkPace` | 200 (100 to 400) | The cap on every speed bonus together, in percent of the usual pace |
| `workplaceKeepWorkLoaded` | true | Game rule: builds and quarries keep running while their player is online |
| `workplaceVillageFarms` | true | Game rule: a village farmer with no field takes on the farm by their composter |
| `workplaceRetraining` | true | Game rule: a villager who has a job can be retrained for a price (any job at the Village Hall, Builder at a Blueprint Table); false: the old rule |
| `retrainPrices` | 5, 10, 20, 35, 50 | Emeralds to retrain, Novice to Master (each 0 to 1000; in the file only) |
| `vintners` | on from 1.0 | The Vintner job |
| `tailors` | on from 1.0 | The Tailor job |
| `printers` | on from 1.0 | The Printer job |
| `berryBreeders` | on from 1.0 | The Berry Breeder job |
| `campCooks` | on from 1.0 | The Camp Cook job |
| `habitatKeepers` | on from 1.0 | The Habitat Keeper job |
| `daycareKeepers` | on from 1.0 | The Daycare Keeper job |
| `gemGrowers` | on from 1.0 | The Gem Grower job |
| `jewellers` | on from 1.0 | The Jeweler job |
| `firewardens` | on from 1.0 | The Firewarden job |
| `wellKeepers` | on from 1.0 | The Well Keeper job |
| `physicians` | on from 1.0 | The Physician job |
| `mechanics` | on from 2.3 | The Mechanic job (only with Create) |
| `furnicrafters` | on from 2.2 | The Furnicrafter job (only with CobbleFurnies) |
| `machineWork` | on from 2.3 | Farmers, armorers, chefs and miners feed Create's machines (only with Create) |

## Saved data

A villager's job is vanilla's own profession and job-site memory. On top of it the mod saves small attachments on
the villager (`registry/ModAttachments`), each absent or zero by default: the job in hand (`builder_job`,
`miner_job`, `farm_field`, `tree_farm`, `orchard`, `courier_routes`, `patrol_route`), who hired them
(`builder_employer`), and a counter per job that the hall shows (`trees_felled`, `fish_caught`, `mail_delivered`
and so on). A quarry (`aliveworkplace_quarries`) of a Quarry build saves `refill_build` (the build's origin),
`last_growth`, `grown` and `grown_since_pass` (36.12); all absent in older saves and marked-out quarries. A quarry
cut off above its marker's depth by the miner's level saves `wanted_bottom`, the floor the marker asked for (36.22);
absent, the box's own floor (every quarry saved before, and every one dug to its full depth). The marker saves a
"down to bedrock" depth as `depth: -1`. Status lines and the requests board are not saved: workers post again after a restart.

## Items, blocks, jobs, commands

- Workstations of our own: Blueprint Table (`aliveworkplace:blueprint_table`), Storehouse
  (`aliveworkplace:storehouse`), Mailbox (`aliveworkplace:mailbox`), Shop Counter (`aliveworkplace:shop_counter`),
  Travel Post (`aliveworkplace:travel_post`), Training Post (`aliveworkplace:training_post`).
- Older job blocks that still work where placed: Builder's Bench (`aliveworkplace:builders_bench`), Miner's Bench
  (`aliveworkplace:miners_bench`), Chopping Block (`aliveworkplace:chopping_block`).
- Markers: Quarry Marker (`aliveworkplace:quarry_marker`), Field Marker (`aliveworkplace:field_marker`), Delivery
  Note (`aliveworkplace:delivery_note`), Patrol Map (`aliveworkplace:patrol_map`). A Delivery Note marks the chest,
  barrel or Pasture Block it right-clicks without opening it (B333), and a note already carrying 9 kinds answers a
  10th with `message.aliveworkplace.route.filter_full` and stays as it is (B334).
- Texts: `message.aliveworkplace.job.chosen`, `message.aliveworkplace.job.needs_station`,
  `message.aliveworkplace.job.station_taken`, `message.aliveworkplace.job.class_needed`,
  `tooltip.aliveworkplace.station.hint`, `message.aliveworkplace.retrain.offer`, `.confirm`, `.short`, `.done`,
  `.build_dropped`.
- Commands: `/workplace friend add|remove|list`, `/workplace mail`, `/workplace strip <height>`.

## Decisions

- 21.1a (owner, 2026-09-30): not every villager needs a new custom table; items already in the game should give a
  villager their job. So jobs share vanilla blocks and are picked with an item.
- 21.1c (owner, 2026-10-03): the Fossil Scientist works at Cobblemon's own Fossil Analyzer.
- 36.20 (owner, 2026-10-26, "village-wide"): a quarry, a field, a tree farm, an orchard or a berry plot may be anywhere
  in the village the worker's block stands in (within the hall's radius, or in a painted City Plan zone), and outside it
  within 128 blocks of the block, each job's own config key: `quarryReach`, `fieldReach`, `treeFarmReach`,
  `orchardAreaReach`, `berryPlotReach` (16 to 256). Past it, the refusal names the village: "That spot is outside
  Mapleridge and 140 blocks from the Miner's Bench the miner works at — keep it within 128." (`<kind>.too_far_village`).
  A worker with no village round their block has only the 128. The worker walks out to a far area in legs
  (`workerPathRange`) and waits there while its chunks aren't loaded.
- B453: a Field Marker or Quarry Marker area too far from the worker is refused in that worker's own words, naming the
  block they work at: "That field is 143 blocks from the Composter the farmer works at — keep it within 128."
  (`message.aliveworkplace.field.too_far`, `message.aliveworkplace.quarry.too_far`,
  `message.aliveworkplace.tree_farm.too_far`, `message.aliveworkplace.orchard_area.too_far`,
  `message.aliveworkplace.berry_plot.too_far`); only the builder's line speaks of the Blueprint Table.
- 21.1a: don't break old worlds. The replaced job blocks stay registered, and blocks that became workstations get
  their record when an old chunk loads.
- 25.5: every "needs" system has its own switch, because babysitting is the top complaint about big colony mods.
- 23.6 (owner's call): villages keep working when no player is near.
- 26.13 (owner, 2026-10-16, chosen over "barrels only hire near water" and "swap storage barrels"): storage barrels in
  builds made most villagers fishermen, so fishermen are capped per village. The village is every villager within
  `villageRadius` of the barrel, as for the worker cap; fishermen are counted by their job, so one whose barrel is
  broken still counts. The cap is off in GameTests (tests side by side would count each other's villagers): its own
  tests switch it on.
- B220 (bug lane, 2026-10-19): a villager handed their job's item outside their work hours keeps their hours and says
  when they start ("Fred starts work when their work day begins, in about 8 minutes."), rather than starting at once:
  starting early would need a saved override of the schedule, and a fresh hire at night would leave their bed.
- B221 (bug lane, 2026-10-19, owner's report): the job and level show on the name plate of the villager you look at,
  drawn by the client, rather than by renaming villagers on the server. Every player on the owner's server has the
  mod (its blocks and items need it), so the client already has the job and level and no packet is needed; a
  server-side name would have been saved into every villager's name, shown in chat and death messages, and fought a
  player's own name tag. Vanilla jobs show too ("Farmer, Master"): a player asks "who is this?" of every villager.
  A named villager keeps its name first.
- 26.6 (owner's playtest, 2026-10-14): in Cobblemon villages nearly every villager works, so a working villager can
  be retrained as a Builder for emeralds. Whether the other jobs' switches should cost too is a question in the ROADMAP
  Notes; for now they stay free.
- 36.11 (owner, 2026-10-19): a Miner makes their own pickaxe instead of waiting for the player or the Toolsmith. A
  pickaxe already in their chests comes first (it's there to be used), then making one, then a spare from another
  worker's chests. Iron from Journeyman and diamond from Expert; the head comes only from their own bag and chests
  (what they mined), the sticks may come from the village store too.
- 36.12 (owner, 2026-10-19): the Quarry's pit is the ground it's built on, not blocks the builder places (no stone or
  ore to bring); the regrowth fills only air, bottom up, so the Miner's own steps and a player's blocks stay. The Miner
  starts again after a layer's worth has grown back, not after every block. A canceled pit is taken again: to stop it
  for good, switch `quarryBuild` off or give the Miner another workstation.

## Known limits

- A job needs a free block of its kind within about 4 blocks of the villager when it is picked.
- Jobs given by order (a player's pick, the Steward) aren't held back by the worker cap or the fisherman cap.
- A jobless villager checks the fisherman cap when he looks for a workstation and on the way to it: if the village
  grows later, he looks again by himself, but a fisherman over the cap is never let go to make room.
- The five Pokémon jobs and the Gem Grower are behind 1.2's gate, the Vintner, the Tailor, the Printer and the Jeweler
  behind 1.8's, and the Firewarden, the Well Keeper and the Physician behind 1.6's; 1.0 opens them all.
- The job plate shows only on a client with the mod (every client on a server with it), and only for the villager
  under the crosshair, as close as vanilla's reach. Jade or WTHIT tooltips don't get it (no compat yet).
- A Miner makes only pickaxes, never their ladders or torches; the board asks for one missing material at a time.
- This page lists how each job starts, not what it does: jobs without a wiki page yet are in the README.

## Proof

GameTests: `QaTooFarWordsBugTests` (3, B453: the farmer, the miner and the lumberjack refuse a far area naming their own block), `StationsGameTests` (12: every item gives its job at its block, a picked job stays, switching at the
same block, a switched-off job leaves its block's tooltip), `StationsSpecGameTests` (17), `StationsBugGameTests` (12), `StationsFixesGameTests`,
`StationsRetakeGameTests`, `RetrainingGameTests` (7: a Librarian with trades and another mod's worker become Builders
for the shown price, too little money changes nothing, a builder mid-site hands the build back, children and nitwits
refuse, save and reload, the switch off), `HallPeopleJobGameTests` (10, 40.3: the hall's Job ▾, see
[The Village Hall](village-hall.md)), `StationsCompatTests` (11, the Pokémon jobs), `OutfitGameTests`, `ConfigGameTests`,
`HandOffGameTests` (5, B220: a fisherman handed a rod and a builder handed a blueprint are at work within 10 ticks,
mid-shift and late in it; before the shift and at night they say when they start, and a night's hire stays in bed),
`FishermanCapGameTests` (5, 26.13: cap reached, the cap grows with the village, the switch off, the file's numbers,
save and reload), `QaFishermanCapGameTests` (2, the QA lane's for 26.13: a dead fisherman and one outside the radius
free the barrel), `PaceGameTests`, `QaPace1027GameTests` (10, the QA lane's for 30.2: each cap
at 100 to 400 and the config's clamps, ill and lazy after the cap, a bonus under the cap kept, two partners counted
once, every job family's pace and the level outside the cap), `ClassWarmupGameTests` (B103: what is loaded ahead, every class loads, chests only in loaded chunks,
the shape warm-up, the nearest hall within a tick, a home finished or taken down within a tick, free workstations with
their jobs), `JobPlateGameTests` (3, B221: a level-3 fisherman reads "Fisherman, Journeyman", a Builder and a
Master Farmer, a named fisherman "Dara — Fisherman, Journeyman" with her saved name untouched, a jobless villager, a
nitwit and a pig unchanged), `MinerPickaxeGameTests` (7, 36.11: cobblestone and sticks become a stone pickaxe and the quarry is dug,
a worn-out pickaxe made again from the bag and cut planks, missing materials on the Storehouse board and handed over
there, save and reload mid-way, the switch off, iron and diamond as the miner rises, the new sentences),
`QuarryBuildGameTests` (10, 36.12: listed with its Jobs line and nothing drawn in the pit, the Miner takes and mines the
pit, regrowth bottom up at the set rate with a player's block kept and nothing outside touched, a day grows back only a
day's worth, the weighted mix, save and reload of the site and mid-dig, the switch off, the Quarry Marker still works,
the new sentences), `QaStageThreeBugTests` (B333: a survival player's right-click marks a chest, then a barrel, and
neither opens; B334: a tenth kind on a full note), `QaStageThreeGameTests` (a sneak-click clears the note, a non-container
is refused, up to 9 kinds). `ShopStockGameTests` (5, B348: the chest emptied while the trades are open sells
nothing and shows sold out, emptied after the payment is in hands over nothing and keeps the emerald, less than a lot
left isn't sold, a restocked shop sells again, a normal sale takes the goods from the chest), `ShopCounterHopperGameTests` (3,
B349: a hopper under the counter pulls nothing out while one under a chest beside it does, a hopper on top pushes
nothing in, the owner still sets and takes back a price on the counter's screen)), `QaMailboxHopperBugTests` (2, B525: a hopper under a Mailbox pulls no mail while one under a chest beside it does, a hopper on top posts nothing), `MailboxHopperGameTests` (2, B525: the owner opens the mailbox, takes the delivered mail and can't put items into the inbox; mail and owner survive a save), `QaMailboxHopperBugTests#theTransferApiTakesNoMailAndPostsNothingFromAnySide` and `QaShopCounterTransferGameTests` (Fabric's transfer API, the way Create and pipe mods reach blocks, from all six faces takes and puts nothing), `QaCreateFunnelMailboxCompatTests` and `QaCreateFunnelCounterCompatTests` (a real extracting Create funnel takes nothing, while on a chest it empties it), `QaTomsPrivateBoxesCompatTests` (Tom's connector joins the chest beside it, not the mailbox or counter). The client mixins themselves are proved by the scene.

Showcase scenes: `job_plate` (a Journeyman fisherman, Dara the Expert builder and a jobless villager, each looked at), `miner_pickaxe` (a worn pickaxe breaks, a new one is made at the blast furnace), `stations` (one composter, four jobs), `retrain` (a Librarian retrained as a Builder), `staff` (every workstation with its villager), `outfits`
(every outfit, and as a zombie), `berry_breeder`, `camp_cook`, `habitat_keeper`, `daycare_keeper`, `gem_grower`, `jeweller`,
`vintner`, `tailor`, `printer`.
