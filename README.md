# Alive Workplace

Villagers with real jobs. Hand a **Builder** a blueprint and they build it for you: they clear the
site, fetch materials from your chests, put up the walls and roof, and finish with doors, beds and
torches. **Miners** dig out quarries, **Lumberjacks** cut down and replant trees, **Orchard Keepers** pick berries, cocoa and apricorns, **Farmers** look after
your fields and **Fishermen** fish for you, dropping everything off in your chests. **Porters** gather it all in the village storehouse, **Carpenters** and **Masons** make what the builders are missing, **Chefs** cook for the village, **Postmen** carry mail
between players' mailboxes, **Guards** keep monsters away, **Nurses** heal you (and your Pokémon),
**Shopkeepers** run your shop, **Ferrymen** take you between villages, **Bards** play music and — with Cobblemon —
**Trainers** battle you, **Move Tutors** teach your Pokémon new moves, **Ball Smiths** make Poké Balls, **Pokémon Traders** swap Pokémon with you and **Fossil Scientists** revive fossils. Villages grow workshops, guard houses, clinics, post offices, orchard houses, ferry houses, storehouses, carpenter's workshops and kitchens on their own — and with Cobblemon,
trainer's houses, Trainer Leader halls, schools, trade halls, ball workshops and fossil labs (Repurposed Structures' villages too).

![Three builders putting up the starter blueprints](docs/media/timelapse.gif)

![The miner, lumberjack, postman, guard, nurse, shopkeeper and ferryman at their workstations](docs/media/staff.png)

Fabric · Minecraft 1.21.1 · built for the Cobbleverse (Cobblemon) modpack, but works without it.
Builders understand **Chipped**, **Rechiseled** and **Supplementaries** blocks (tested with the real mods).
Install on the **server and every player's game**.

## Getting started
1. **Hire a builder.** Look for a builder's workshop in a village (newly explored villages often have one), or craft
   a **Builder's Bench** and place it near a villager without a job; they take it like any job block.

   ```
   Brick   Brick          Brick
   Planks  Crafting Table Planks
   Planks  Planks         Planks
   ```
2. **Get a blueprint.** Builders sell the starter blueprints (Starter Cottage first; more as they level up).
   Operators can also use `/workplace blueprint <id>`, including anything saved with a Structure Block.
3. **Place it.** Hold the blueprint and right-click the ground where the front of the building should go.
   It faces you, and while you hold it you see the whole building as see-through blocks, exactly where it
   will stand (the gold edge of the outline is the front). Sneak-right-click the ground to turn it;
   sneak-right-click the air to pick it back up. On uneven ground the builder fills in a foundation, and when the
   building is finished they level the ground two blocks around it (dirt, stone and grass above the floor dug away,
   holes filled with dirt; trees, flowers and anything built are left alone). For a build meant to sit in a
   hillside, right-click the air with its blueprint to switch that off for this build (the tooltip says so).
   Water and lava in a blueprint are poured from buckets in the chests (the empty buckets go back).
   Item frames, paintings and armor stands go up last, empty, each paid for with its item from the chests.
4. **Stock the chests.** Put the materials in any chests or barrels within 8 blocks of the builder's bench
   (hold Shift over the blueprint to see the list). Once the blueprint is placed, its tooltip counts what the chests
   by the nearest bench are still short of — blocks already standing in place don't count — so you know when you're
   ready.
5. **Hand it over.** Right-click the builder with the blueprint. The first player to do that hires the builder;
   after that it takes orders from them and the friends they add (`/workplace friend add <player>`).
   Busy builders take up to 5 more blueprints and build them in order. They start work in the morning, sleep at night,
   and tell you if they run out of something. What they are building, how far along it is and what they are
   waiting for floats above their head; sneak-right-click a builder with an empty hand for the full status.

![A builder at work, with the rest of the house still see-through](docs/media/preview.png)

When they finish, the blueprint goes back into the supply chest so you can build it again.

**Changed your mind?** Place the blueprint over the building and **sneak** while giving it to a builder: they take it
down and put the blocks back in the chests.

**More builders, faster builds.** A builder with nothing to do helps with builds near their bench (up to three helpers
per build), sharing the chests and passing each other materials.

**Builders level up as they work**, like villagers you trade with: every level makes them faster (a Master builds
2.5× as fast as a Novice) and unlocks new blueprints to buy — Market Stall, Lookout Tower, then the
**Healing Center** (with a Nurse Station, and a Cobblemon Healing Machine on the counter when Cobblemon is installed)
and the **Supply Shop** (with a Shop Counter) — build them and a nurse and a shopkeeper move in.

![The Starter Cottage II next to a Starter Cottage](docs/media/cottage-upgrade.png)
![Market Stall II, Lookout Tower II, Healing Center II and Supply Shop II next to the builds they upgrade](docs/media/starter-upgrades.png)
![Starter Cottage III: the two-storey cottage with a kitchen wing and a roof terrace on the side](docs/media/cottage-3.png)
![From behind: Market Stall III's storeroom, Lookout Tower III's guardhouse, Healing Center III's berry garden and Supply Shop III's post office](docs/media/third-tiers.png)

**Upgrades.** A blueprint named like another with `_2` on the end (`_3` after that…) is its upgrade. Every starter
build has one: the **Starter Cottage II** adds a second storey, the **Market Stall II** a second stall with a Shop
Counter, the **Lookout Tower II** a Guard Post, a bell and a pointed roof, the **Healing Center II** a ward with four
beds, and the **Supply Shop II** a storeroom and a bedroom upstairs. The cottage goes one step further: the **Starter
Cottage III** adds a kitchen wing on the side with a roof terrace, reached through a new door upstairs (the builder
clears the ground where the wing goes first). The others have a third tier too, each bringing a new villager: the
**Market Stall III** a storeroom shed behind the stalls, the **Lookout Tower III** a guardhouse with a second Guard
Post and two bunks, the **Healing Center III** a fenced berry garden with a Fruit Basket (an orchard keeper), and the
**Supply Shop III** a post office annex with a Postal Desk (a postman). Right-click a finished building with its upgrade
and it lines up exactly over it; the builder takes off what changes and builds only what's new, keeping everything
else. Once a builder finishes a building that has an upgrade, **they sell its blueprint** (and tell you). The
upgrades are in the Blueprint Table too. Your own blueprints work the same way (`my_house` → `my_house_2`).

## All the jobs at a glance
Place the workstation near a villager without a job and they take it. Chests (or barrels) within 8 blocks of it are
where they take tools and supplies from and where their work goes.

| Job | Workstation | In the chests nearby | Then |
| --- | --- | --- | --- |
| Builder | Builder's Bench | the building materials | hand them a placed blueprint |
| Miner | Miner's Bench | pickaxes, torches (ladders for a shaft) | hand them a marked Quarry Marker |
| Lumberjack | Chopping Block | axes (saplings, bone meal) | nothing — or a Field Marker for a tree farm |
| Orchard Keeper | Fruit Basket | berries and seeds to plant, a hoe | nothing — or a Field Marker for an orchard |
| Farmer | Composter (vanilla) | seeds, a hoe, bone meal | a Field Marker (blank: their own farm) |
| Fisherman | Barrel (vanilla) | spare rods (coal for a smoker) | hand them a fishing rod |
| Porter | Storehouse | empty chests: the village's store | nothing |
| Carpenter | Carpenter's Bench | — (uses the builders' wood) | nothing |
| Mason | Stonecutter (vanilla) | — (uses the builders' stone) | nothing |
| Chef | Kitchen Stove | the makings: wheat, raw meat and fish, potatoes... | nothing |
| Postman | Postal Desk | — | Mailboxes for mail; Delivery Notes for hauling |
| Guard | Guard Post | weapons, armor, a bow or crossbow and arrows | nothing |
| Nurse | Nurse Station | — | right-click them to be healed |
| Shopkeeper | Shop Counter | the goods to sell | set the prices in the counter |
| Ferryman | Travel Post | — | buy a Travel Ticket from them |
| Bard | Music Stand | music discs | nothing |
| Trainer (Cobblemon) | Training Post | — | right-click them to battle |
| Trainer Leader (Cobblemon) | Leader's Podium | — | right-click them to battle, once a day |
| Move Tutor (Cobblemon) | Tutor's Desk | — | right-click them for lessons |
| Ball Smith (Cobblemon) | Ball Workbench | apricorns and copper, iron, gold or diamonds | sneak-right-click them to choose the balls |
| Pokémon Trader (Cobblemon) | Trade Board | — | right-click them to trade |
| Fossil Scientist (Cobblemon) | Fossil Lab | — | hand them a fossil |

Sneak-right-click a builder, miner, lumberjack, orchard keeper, farmer, fisherman or postman with an empty hand to see
what they're doing and how to stop them. The recipes are in each job's section below (and in the recipe book).

**Villages work together.** Workers whose workstations are within 48 blocks of each other share their chests: a builder
short of stone takes it from the miner's chests, a lumberjack with a broken axe takes a spare from the builder's. What a
worker makes still goes into its own chests; the others come and get it. No particular building is needed, so it works
in any village (from any mod) and in your own base. Only workers who answer to the same people share: the ones hired by
you or your friends (`/workplace friend add`), and village workers nobody has hired with each other — a friend's
builder won't empty your miner's chests unless you've made them your friend. A **Porter** (see *Porters and the
storehouse*) gathers what everyone makes in one place.

## Miners
![A miner digging out a quarry](docs/media/miner.gif)

Craft a **Miner's Bench** (cobblestone on top, a stone pickaxe in the middle, planks around) and place it near a
villager without a job. Then:
1. Craft a **Quarry Marker** (stick + red dye + paper). Right-click one corner block, then the opposite corner
   (up to 32 × 32). Sneak-right-click the air to choose the depth (4, 8, 16, 32 or 64) or a **strip mine** (here or
   down a shaft); a red
   outline shows the area.
2. Put **pickaxes** (and some torches) in a chest within 8 blocks of the Miner's Bench.
3. Give the marker to the miner. They dig the area out from the top down, bring everything back to the chests, and
   leave anything touching lava or water standing so the pit stays dry. When the last pickaxe wears out they wait
   for another.

![Steps left in the wall of a finished quarry](docs/media/quarry-stairs.png)

**Stairs out of the pit.** In a pit at least 3 × 3 and 3 deep, the miner leaves one block per layer standing as a step,
each a block along the wall from the one above, so steps spiral down the walls from the corner nearest the bench.
Sand and gravel steps are swapped for cobblestone, and gaps (caves) are filled in with stone from the chests.

**Strip mines.** The last choice on the marker digs tunnels instead of a pit: 2 high (the marked blocks and the ones
below them, so mark the corners at head height), along the longer side of the area, with 2 blocks of rock between
them and a tunnel across the end nearest the bench. The rock stays, but any ore in it is dug out: about a third of
the digging for all the ore. Strip mines can be up to 64 blocks long (and 32 wide).

**Strip mines down a shaft.** After the strip mine, the marker offers strip mines at set heights: **Y=16** (iron, and
ancient debris in the Nether), **Y=-16** (redstone, gold and lapis) and **Y=-53** (diamonds); for any other height,
hold the marker and type `/workplace strip <height>`. Mark the corners on the ground: the miner digs a 1-wide ladder shaft straight down from the corner nearest the bench, then the tunnels at that
height, and puts ladders all the way up — about one ladder per block of depth, from the chests (they wait for more if
they run out). On the way down they seal off any water or lava beside the shaft with stone and put a block under
themselves before digging into a cave, so they never fall, and every eight blocks they set a torch in a niche in the
wall across from the ladders (if there are torches in the chests).

**Smelting.** Put a furnace or blast furnace within 8 blocks of the Miner's Bench and some coal or charcoal in the
chests. Every time the miner drops off a haul they take the finished ingots out into the chests, load the raw ores
(and any ore blocks) in, and top up the coal. Anything you put in a furnace yourself is left alone.

## Lumberjacks
![A lumberjack cutting and replanting trees](docs/media/lumberjack.gif)

Craft a **Chopping Block** (a stone axe on top of any log) and place it near a villager without a job, near some
trees. Put **axes** in a chest within 8 blocks of the Chopping Block. The lumberjack cuts the trees within 16 blocks
one at a time (leaves first, then the trunk), plants a sapling of the same wood where each tree stood, and stores
the logs, sticks and apples in the chests. They only cut real trees: logs with placed leaves (houses, posts),
blueprint builds and quarries are left alone. When the last axe breaks they wait for a new one. Dark oaks (and other
2 × 2 trunks) get four saplings back in a square, and huge crimson and warped fungi standing on nylium count as
trees too (a fungus is planted back). **Mangroves** are felled down to their roots (the roots stay) and a propagule
goes in close by, in the water over the mud; **azalea trees** get an azalea bush back, and **cherry trees** a cherry
sapling.

**Stripped logs and charcoal.** When a builder in the village is waiting for stripped logs (or stripped wood), the
lumberjack strips that many of the logs in their chests, and the builder comes to get them. Put a furnace within 8
blocks of the Chopping Block (with a little coal to start it) and they burn logs into charcoal, keeping 32 in the chests.

**Bone meal.** Put bone meal in the chests and, whenever there's no grown tree to fell, the lumberjack gives it to the
saplings on their tree farm and the ones they replanted, until they grow (an azalea bush only grows with bone meal).

**Tree farms.** Mark an area with a **Field Marker** (the farmer's marker, up to 32 × 32 and within 48 blocks of the
Chopping Block) and give it to the lumberjack. They keep it planted with saplings from the chests, in a grid three
blocks apart (dark oak in 2 × 2 squares four apart), and fell what grows there, even if it's further out than the
16 blocks they'd look on their own. Sneak-right-click them with an empty hand to see the farm or stop it.

## Orchard Keepers
![An orchard keeper picking berries, cocoa and apricorns](docs/media/orchard.gif)

Craft a **Fruit Basket** (two sticks in the top corners, then sweet berries, a plank, sweet berries) and place it near a villager
without a job, in your orchard. The keeper walks round everything within 16 blocks of the basket and picks whatever
is ripe: **sweet berries**, **glow berries**, **cocoa pods** and — with Cobblemon — **apricorns** and **berry
plants**. The plants are picked, not broken, so they grow again. The harvest goes into the chests within 8 blocks of
the basket. They reach up into trees with a picking pole and never step into a berry bush.

**Planting an orchard.** Mark an area with a **Field Marker** (within 48 blocks of the basket) and give it to the
keeper. They plant it from the chests: sweet berries as bushes two blocks apart on grass or dirt, and with Cobblemon
berries (on farmland — put a **hoe** in the chests and they till grass and dirt for them) and apricorn seeds (on grass
or dirt, three apart), then pick what grows there too. **Glow berries** are hung from the underside of any solid
ceiling in the orchard (a pergola, a cave roof), two blocks apart like the bushes.
Sneak-right-click the keeper with an empty hand to see the orchard or stop it.

**Berry Farm.** Orchard keepers at Expert level sell the **Berry Farm** blueprint (it's in the Blueprint Table too): a
fenced garden of sweet berry bushes with a Fruit Basket and a harvest chest. The **Berry Farm II** adds a pergola
behind it with glow berries hanging from its roof.

## Farmers
![A farmer harvesting, replanting and sowing a field](docs/media/farmer.gif)

Any **Farmer** villager (the ones with a composter) can look after a field for you. Craft a **Field Marker** (stick +
wheat seeds + paper), right-click one corner block of the field and then the opposite corner (up to 32 × 32), and
give the marker to the farmer. Put **seeds** (and a **hoe**, if there is bare dirt to till) in a chest within 8 blocks
of their composter. They harvest ripe crops and plant them straight back, sow empty farmland, till bare dirt and
grass, cut sugar cane down to its bottom block and pick pumpkins and melons — and Cobblemon's mints, which are
picked for their leaves and planted again. Sweet berry bushes, cocoa pods and glow berries in the field are picked
(the plant stays to grow again). Put **bone meal** in the chest too and, once everything's sown and nothing is ripe,
the farmer uses it on the growing crops. The harvest goes into the chests.
Sneak-right-click the farmer with an empty hand to see how it's going or to stop.

**A village's own farm.** Village farmers look after the farm by their composter **by themselves** as soon as there's a
chest within 8 blocks of the composter: the farmland nearest to it, within 16 blocks, and everything joined to that,
across the water channels between the rows too. The harvest goes into the chest, but they keep some food on them
(wheat baked into bread) to share with the other villagers, so the village keeps growing. Stop one with the link in
their status and they leave it alone; `/gamerule workplaceVillageFarms false` turns it off everywhere. A blank Field
Marker does the same for any farmer straight away.

## Fishermen
![A fisherman villager with a bobber out on the pond](docs/media/fisherman.png)

Hand any **Fisherman** villager (the ones with a barrel) a **fishing rod**. They walk to the nearest water within 16
blocks of their barrel, cast from the shore — the bobber floats on the water on the end of their line and goes under
when a fish bites — and reel in fish (and the odd bit of junk), bringing the catch back to
their barrel and any chests within 8 blocks of it. Rods wear out: put spares in the barrel. Put a **smoker** (or
furnace) next to the barrel and some coal or charcoal in it, and the raw cod and salmon go into the smoker instead,
with the cooked fish coming back out at the next drop-off. Sneak-right-click the fisherman with an empty hand to see
how it's going or to stop.

## Porters and the storehouse
![A porter carrying a miner's stone and ore to the storehouse](docs/media/porter.png)

Craft a **Storehouse** (three planks over a barrel, a chest and a barrel), put chests or barrels within 8 blocks of it
and a villager without a job becomes a **Porter**. The porter walks round the other workers of the village and carries
what they make to the storehouse chests: the miner's ore, coal and spare stone, the lumberjack's logs, the farmer's
harvest, the fisherman's catch, the orchard keeper's fruit. They leave what each job needs: tools, the miner's torches
and ladders and some stone for the stairs, seeds and saplings, bone meal, and ore or fish waiting for a furnace next to
the worker. Builders and ball smiths have nothing to carry away. A worker's goods have to add up to 16 items to be worth
the walk.

The storehouse is part of the village's stock, so a builder short of stone finds it there. One you place (or have a builder build) is yours: its porter works for you, and only carries for
your workers and your friends'. A village's own storehouse carries for the village's workers. A porter carries 9 stacks
a trip, 3 more at each level.

**The requests board.** Right-click the Storehouse to see what the workers nearby are waiting for: each builder's
missing materials, a miner's pickaxe or ladders, a lumberjack's axe, a farmer's seeds, a fisherman's rod. It shows how
much of each is already in the storehouse, and clicking one hands over what you have straight into that worker's
chests. Lumberjacks see the requests too: when a builder is waiting for birch planks (or stairs, doors, logs...), they
fell birch trees first.

![The Storehouse's requests board](docs/media/request-board.png)

**Storehouse builds.** Porters sell the **Storehouse Shed** blueprint (it's in the Blueprint Table too): an open timber
shed with a Storehouse and eight chests. **Storehouse II** adds a second bay with eight more, and **Storehouse III** a
stone warehouse behind with sixteen more. Any building of your own with a Storehouse in it works just as well, and
villages sometimes grow a storehouse of their own.

## Carpenters and masons
![A carpenter taking the spruce fences they made to a builder](docs/media/carpenter.png)
![The Market Stall finished from nothing but spruce logs: the carpenter made the planks, fences, barrels and composter](docs/media/carpenter-stall.png)

Craft a **Carpenter's Bench** (a stick, an iron ingot and a stick over planks, a crafting table and planks) and a
villager becomes a **Carpenter**. When a builder nearby is waiting for something that can be made from what they can
get at (their chests, and their village's), the carpenter fetches the ingredients, makes it at the bench with the
game's own crafting recipes (modded ones too) and takes it to the builder's chests: stairs, slabs, doors, fences and
planks from the builder's logs, sticks, torches, glass panes... up to two steps down count, so fences come from logs by
way of planks and sticks. Only what the rest of the build doesn't need is used: planks for stairs, but not the planks the walls still
want. **Masons** (vanilla villagers at a stonecutter) do the same with the stonecutter's recipes — stone bricks, stairs,
slabs and walls cut from stone — and go about their usual day in between. Villages sometimes grow a carpenter's
workshop.

## Chefs
![A chef cooking at the Kitchen Stove](docs/media/chef.png)

Craft a **Kitchen Stove** (five iron ingots round a smoker, over three cobblestone) and a villager becomes a **Chef**.
Put the makings in a chest within 8 blocks and the chef cooks, a batch at a time, whatever there's enough for: bread,
cookies, pumpkin pie, cake, the stews, and everything a smoker cooks (steak, chicken, fish, baked potatoes). With
Cobblemon they also cook what its Campfire Pot does — **Poké Bait**, **Poké Snacks**, **Poké Cakes**, candied apples and
**Aprijuice** — with Cobblemon's own recipes. What's cooked goes into the same chests, and they stop making a dish once
there are 16 of it. With a porter in the village, the chef also takes the makings from the storehouse, so the farms'
and fishermen's spare harvest turns into food without anyone lifting a finger. Villages sometimes grow a kitchen.

## Mail and postmen
![The mailbox screen](docs/media/mailbox.png)

Craft a **Mailbox** (iron nuggets around a chest, on a fence) and place it: it's yours, and mail sent to you arrives
there (the red flag goes up). To send something, open your mailbox, put items in the top row, write a player's name
and press **Send**. Write something in the **Letter** line and it goes along as a letter (a book they can read), or on
its own if the top row is empty. `/workplace mail` (or **[Track]** after sending) shows where your parcels are. A **Postman** picks it up: craft a **Postal Desk** (paper over planks and a chest) and place it
near a villager without a job. Postmen walk their round (64 blocks around the desk), collecting parcels and putting
them in the right mailbox. Parcels for a mailbox outside the round (another village, another dimension) go with the
night mail and arrive at the next dawn (or at once, by air mail, with a Flying-type Pokémon pastured by the desk).
Only you, your friends (`/workplace friend add`) and operators can open your
mailbox.

**The post office.** No mailbox? Mail for you waits at the post office: right-click any **Postal Desk** and you get
every parcel handed in for you (you're told at dawn when some are waiting). That works for night mail on its way to
your mailbox too, if you'd rather not wait for dawn.

**Courier routes.** When there's no mail, postmen haul between your chests. Craft a **Delivery Note** (paper, a
feather and an ink sac), right-click the container to take from and then the one to bring to (say, the quarry chest
and the builder's chest), and give the note to a postman. They carry everything except tools, weapons and armor — or,
if you hold an item in your other hand and right-click the air with the note, only the kinds you pick. Up to 4 routes
per postman; sneak-right-click them to see their routes, and give them a blank note to end them. With Cobblemon, a
route can start at a **Pasture Block**: the postman empties every chest and barrel within 8 blocks of it — where
Cobbleworkers' Pokémon put what they gather.

## Guards
![A guard fighting off three husks](docs/media/guard.gif)

Craft a **Guard Post** (an iron sword over planks and a shield) and place it near a villager without a job. Put
weapons and armor in a chest within 8 blocks of the post: the guard takes the best of it. Guards fight monsters that
come within 24 blocks of the post at any hour and never run away. Give them a **bow** or a **crossbow** (in the same
chest; a crossbow hits harder) and they
also shoot creepers from a safe distance — without one they leave creepers alone — and pick off other monsters
before they get close; they never shoot when a player, villager or pet is in the way. They have twice a villager's
health and keep the night watch, sleeping in the late morning instead. Players, villagers, animals, pets and
Pokémon are safe from them. **Ring the village bell** and, while everyone else runs home to hide, the guards head
for the bell and fight anything near it for a minute and a half.

![A guard fighting husks with a Machop and a Dratini from a pasture joining in](docs/media/guard-pokemon.gif)

**Fighting beside their Pokémon** (with Cobblemon): keep Fighting or Dragon types in a Pasture Block within 16 blocks
of the Guard Post, and whenever the guard lands a hit on a monster within 20 blocks of them, up to three of them follow
up with a move of their own — you see them turn, attack and the hit land, with Cobblemon's own effects. A move does 2
damage at level 1, 5 at level 50 and 8 at level 100 (once a second each). Monsters turn on the guard, never on the
Pokémon, and the kill counts as the guard's.

![A guard in an iron helmet, chestplate and boots](docs/media/guard-armor.png)

The armor a guard wears shows on them: helmet, chestplate (with shoulder pieces on the folded arms), leggings under
the robe and boots — any armor, including dyed leather and enchanted pieces.

## Nurses
Craft a **Nurse Station** (glass bottles around a glistering melon slice, on white wool) and place it near a villager
without a job. Right-click the nurse with an empty hand to get your health back and bad effects cleared; with
**Cobblemon** installed they heal your whole team too. Once a minute per player (less as they level up). They also
look after hurt villagers and iron golems nearby. Sneak-right-click to trade.

## Shops
Craft a **Shop Counter** (an emerald over planks and a chest) and place it near a villager without a job: the shop is
yours and the villager becomes its **Shopkeeper**. Open the counter to set your prices — in each column, the top slot
is what one sale hands over (for example 16 cobblestone) and the slot below it is the price (for example 1 emerald;
any item works). Put the goods in chests within 8 blocks of the counter. Other players buy by trading with the
shopkeeper as usual; only what's in the chests is for sale, and the payments land in the same chests.

**With CobbleDollars** (the Cobbleverse pack's money), right-clicking the shopkeeper opens the shop's own screen
instead: everything in stock, with emerald prices shown in CobbleDollars (100 per emerald). Click an item, then
click it again to buy. The CobbleDollars go straight to the owner — or, if they're offline, the next time they
join. Items priced in something other than emeralds are paid with those items, as before. Sneak-right-click the
shopkeeper for the usual trade screen. The owner can sneak-right-click the counter to see the latest sales.

**Price Tags** set a price in CobbleDollars directly: craft them (paper, a gold nugget and string make four),
right-click one to set the price with the +/− buttons (or rename it in an anvil to the price — say `250`) and put it in
a counter's price row. That column then costs 250
CobbleDollars. Without CobbleDollars a tag is paid in emeralds (100 CobbleDollars to the emerald, rounded up).

![The shop screen with CobbleDollars prices](docs/media/shop.png)

## Ferrymen and travel posts
Craft a **Travel Post** (a sign over planks and a boat); name it in an anvil first if you want ("Riverside"), then
place it. Right-click posts to add them to the ones you know. A villager without a job takes a post as its
**Ferryman**, who sells **Travel Tickets** to every other post you know (1 emerald per 256 blocks, 8 to another
dimension). Use a ticket within 16 blocks of any travel post and you arrive at the ticket's post. With CobbleDollars,
right-clicking the ferryman shows your destinations with fares in CobbleDollars (100 per emerald); click one twice
to buy its ticket, or sneak-right-click to pay in emeralds. Many villages have a **ferry house** with a travel post
and a ferryman already: the post joins the network under a village name ("Willowbrook"), so right-click it once and
it's on your list.

## Bards
Craft a **Music Stand** (paper on a note block) and place it near a villager without a job. Put music discs in a
chest within 8 blocks: in the morning and in the evening the bard plays them one after another (the discs stay in the
chest). No discs? They make up a tune on the harp.

## Pokémon Trainers (with Cobblemon)
Craft a **Training Post** (a target block on planks) and place it near a villager without a job: they become a
**Trainer**. Right-click a trainer with an empty hand to battle. Every trainer starts as a Novice with two young
Pokémon and ranks up as people battle them — Apprentice, Journeyman, Expert, Master — until they field six fully
evolved Pokémon at level 80–100 and play smart. From Journeyman their Pokémon have better IVs; Experts and Masters
also bring EVs, a matching nature (Adamant or Modest) and a held item (Life Orb, Leftovers, a Choice item…), and a
Master's Pokémon come with four strong attacks picked from everything they can learn (their own type first, then
coverage). Beating one pays **CobbleDollars** (100 for a Novice up to 2,500 for a
Master; emeralds if CobbleDollars isn't installed), once a day per trainer. No badges, no gyms.
With **Radical Cobblemon Trainers** installed (it is in the Cobbleverse pack), a trainer's Pokémon never go above
your level cap by more than their rank allows: a Novice stays 6 levels under your cap, a Journeyman meets it, a
Master goes 5 over. So the village's Master is a real fight whether you're new or far along.

Each village can also have one **Trainer Leader**: craft a **Leader's Podium** (gold ingots either side of a Training
Post, on polished andesite). The leader battles at Expert strength from the start, pays three times the prize, and
takes one challenge a day from each player.

## Move Tutors (with Cobblemon)
Craft a **Tutor's Desk** (a book on planks) and place it near a villager without a job: they become a **Move Tutor**.
Right-click them with an empty hand (sneak to trade instead) to open their lessons: pick one of your Pokémon at the
top, then a move it could learn but won't get from levelling — tutor moves, TM moves and egg moves. Click a lesson
once to choose it and again to pay and teach it. The move goes straight into the Pokémon's moves if it knows fewer
than four, otherwise you can swap it in from the moves page of its summary.

![A Move Tutor's lessons for Pikachu](docs/media/tutor.png)

Lessons cost CobbleDollars: 300 for a weak move, up to 2,400 for the strongest (egg moves count as one step harder;
without CobbleDollars installed, 3 to 24 emeralds). A new tutor
only teaches weaker moves; they rank up with every lesson they give, and a Master teaches everything. Tutors also buy
paper and books, if you need emeralds.

## Ball Smiths (with Cobblemon)
![A Ball Smith making Azure Balls next to an Orchard Keeper](docs/media/ball-smith.png)

Craft a **Ball Workbench** (red dye, a copper ingot and white dye over planks either side of a smithing table) and
place it near a villager without a job: they become a **Ball Smith**. Put apricorns and ball metals in chests within
8 blocks — copper ingots for Poké Balls and the other basic balls, iron for Great Balls and friends, gold for Ultra
Balls, diamonds for the rarest — and the smith turns them into balls with Cobblemon's own recipes, a batch of four at
a time, and puts the balls back in the chests. A new smith only works copper; they learn the harder balls as they
level up (iron at Apprentice, gold at Journeyman, diamonds at Expert). They take turns between the kinds they can
make and stop making a kind once the chests hold 64 of it. They never make Master Balls. Pair them with an Orchard
Keeper (or Cobbleworkers' apricorn pickers) for a steady supply.

**Orders.** Sneak-right-click the smith with an empty hand to pick which balls they make: click a ball to ask for it
(it glows), click again to stop. With nothing picked they make whatever the chests have the makings for.

## Fossil Scientists (with Cobblemon)
Craft a **Fossil Lab** (glass, a brush and glass over three smooth stone) and a villager becomes a **Fossil Scientist**.
Right-click them holding a fossil (for a Galar fossil, hold one half in each hand) and pay 8 emeralds (800 CobbleDollars
with CobbleDollars): they revive it at the lab while they work, about three minutes for a novice and quicker as they
level up. The Pokémon joins your party (or goes to your PC) the moment it's done, or the next time you're online. It's
Cobblemon's own revival, the same Pokémon its machine gives. Right-click with an empty hand to see how it's going; sneak
to trade. A scientist takes on three fossils at a time. Villages sometimes grow a fossil lab, and scientists at
Journeyman level sell the **Research Lab** blueprint (a stone lab with a Fossil Lab and a fossil on show; the
**Research Lab II** adds a museum hall with a big skeleton).

## Pokémon Traders (with Cobblemon)
Craft a **Trade Board** (an item frame on planks) and place it near a villager without a job: they become a
**Pokémon Trader**. Right-click them with an empty hand (sneak to buy Poké Balls and candies instead) to see today's
offers — "my Tinkaton, level 62, for any Fighting type, level 55 or higher". Pick an offer, then one of your
Pokémon that fits (the rest are greyed out, with the reason), and click it twice to swap. A held item comes back to
you. Offers change every in-game day and are the same for everyone; each player gets one trade per trader per day.
A new trader has one offer of a young Pokémon; with every trade they rank up, and a Master has three offers at level
50–70, now and then a shiny one. Experts and Masters also make a **special request** each day: a particular Pokémon
(any of its evolutions will do) for one of theirs at the top of their range — shiny one time in four.

![A Pokémon Trader's offers and your party](docs/media/pokemon-trader.png)

## Pokémon partners and Cobbleworkers (with Cobblemon)
![A Bulbasaur from a pasture helping an orchard keeper](docs/media/partners.png)

Pokémon you keep in a **Pasture Block** within 16 blocks of a villager's workstation help with the job when their
type suits it — each one cuts the time the work takes by 15%, up to three:

| Job | Helpful types |
| --- | --- |
| Builder | Fighting, Rock, Steel |
| Miner | Ground, Rock, Steel |
| Lumberjack | Grass, Bug, Fighting |
| Orchard Keeper | Grass, Bug, Flying |
| Farmer | Grass, Ground, Water |
| Fisherman | Water, Ice |
| Nurse | Fairy, Normal, Psychic |
| Ball Smith | Steel, Fire |
| Porter | Fighting, Normal: each carries 3 more stacks a trip |
| Carpenter, Mason | Fighting, Rock, Steel |
| Chef | Fire, Normal |
| Fossil Scientist | Rock, Psychic |
| Guard | Fighting, Dragon: they **fight beside the guard** (see *Guards*) |
| Postman | Flying: **air mail** — parcels for mailboxes outside the round go straight there instead of at dawn |
| Miner, Fisherman (their furnaces) | Fire: each time the worker tends a furnace or smoker by the workstation, every Fire-type partner smelts 8 of what's in it on the spot, no coal needed |

The line above the villager's head says who is helping ("· with Machop"), and sneak-right-clicking a worker shows
how much faster they are. It works with [Cobbleworkers](https://modrinth.com/mod/cobbleworkers) too: the same
Pokémon can work the pasture for Cobbleworkers and help the villager at the same time, and a postman's courier route
from the pasture (see *Courier routes*) takes what they gather to wherever it is needed.

## Blueprint Table: any build from the internet
Craft a **Blueprint Table** (blue dye on top, cartography table in the middle, planks around) and right-click it.

![The Blueprint Table](docs/media/blueprint-table.png)

- Every blueprint on the server is listed with its size and **exactly what materials it needs**.
- **Get Blueprint** gives you a copy for one **Blank Blueprint** (paper + blue dye makes two).
- **Upload a File…** lists the build files in your own `blueprints` folder (**Open Folder** takes you there).
  Download builds from sites like Planet Minecraft or Minecraft-Schematics as `.litematic` or `.schem`,
  drop them in, pick one and press **Upload**. It becomes a blueprint everyone on the server can use.
- Anything saved with a Structure Block shows up too.

## Builds that use other mods
- **Chipped / Rechiseled** variants: stock the plain block (oak planks, stone bricks…) and the builder turns it into
  whichever variant the blueprint uses, just as the Chipped workbench or the chisel would for free.
- **Supplementaries**: way signs need the fence and the sign, rope knots the rope and the fence, potted plants a pot and
  the plant. Jars, shelves, safes and other containers are built empty — blueprints never hand out items.
- **Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar Concrete** (the Cobbleverse pack's building mods): every
  block costs its own item and builds like any other (tested with the real mods).
- **Storage mods**: Sophisticated Storage chests and barrels and Tom's Storage filing cabinets near the bench work as
  supply chests. Storage-network blocks (Tom's connectors and terminals, the Sophisticated controller) are skipped,
  so nothing is counted twice — the builder uses the chests themselves.
- Blocks from mods that aren't installed turn into air and are left out.

## Commands and gamerules
| | |
|---|---|
| `/workplace sites` | your builds in progress, with a cancel button |
| `/workplace mail` | parcels on their way to and from you, and where they are |
| `/workplace strip <height>` | the Quarry Marker in your hand digs a strip mine at that height, down a ladder shaft |
| `/workplace cancel <id>` | stop a build (placed blocks stay; you get the blueprint back) |
| `/workplace friend add <player>` | let a friend give orders to your builders (`remove`, `list` too) |
| `/workplace blueprints` (op) | list every blueprint the server knows |
| `/workplace blueprint <id>` (op) | get a blueprint item |
| `/workplace import` (op) | import files from `<world>/aliveworkplace/import/` |
| `/gamerule workplaceAllowUploads false` | only operators can upload blueprint files |
| `/gamerule workplaceFreeMaterials true` | builders need no materials (creative towns) |
| `/gamerule workplaceBuildDelay 8` | ticks per block (lower is faster) |
| `/gamerule workplaceBuildersHelp false` | idle builders stop helping with other builds |
| `/gamerule workplaceBuilderOwnership false` | anyone can give orders to any builder |
| `/gamerule workplaceFoundationDepth 12` | how far down builders fill under a build on uneven ground (0 = never) |
| `/gamerule workplaceLevelGround 0` | builders leave the ground around their builds alone (default 2 blocks, up to 8) |
| `/gamerule workplaceVillageFarms false` | village farmers don't take on the farm by their composter by themselves |
| `/gamerule workplaceKeepWorkLoaded false` | builds and quarries stop when nobody is nearby (by default they keep going while the player who ordered them is online) |

**Server config** — `config/aliveworkplace.json` is written with the defaults the first time the game starts (edit it
and restart; out-of-range values are clamped):

| Option | Default | What it does |
| --- | --- | --- |
| `supplyRadius` | 8 | chests and barrels this close to a workstation are its supply chests |
| `maxSiteDistance` | 48 | how far from their bench a builder takes a build |
| `guardRadius` | 24 | how far from the Guard Post guards patrol and fight |
| `lumberjackRadius`, `orchardRadius`, `fisherRadius` | 16 | how far lumberjacks cut, orchard keepers pick and fishers look for water |
| `partnerRadius` | 16 | how close to a workstation pastured Pokémon must be to help |
| `postmanRange` | 64 | how far a postman walks to deliver (mail going farther arrives at dawn) |
| `villageRadius` | 48 | workers whose workstations are this close together share their chests (0 turns sharing off) |
| `dollarsPerEmerald` | 100 | CobbleDollars per emerald for lessons, shop prices and fares |

## Performance
Tested on a real server with the whole Cobbleverse pack: 80 busy workers (40 builders, and miners, lumberjacks,
porters, carpenters and masons) took an average tick from 2 ms to about 7 ms, of the 50 ms a tick may take. Alive
Workplace's own code is about a tenth of that; the rest is what any 80 villagers cost, mostly finding their way
around. (`PERF=true tools/packtest/run.sh` runs the test.)

## What's next
See [ROADMAP.md](ROADMAP.md) for what's planned.

## Building from source
```
./gradlew build          # jar in build/libs, runs the in-game test suite
./gradlew runGameTest    # just the tests
```
Every push is built and tested by GitHub Actions; the jar is attached to each run.

## License
GPL-3.0-or-later. See [LICENSE](LICENSE).
