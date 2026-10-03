# Alive Workplace

**Villagers with real jobs.** Hand a villager a blueprint and they build it for you. Others dig quarries, fell trees, farm, cook and keep monsters away. Give the village a Village Hall and it starts to grow: names, needs, quests and a history of its own.

**Fabric · Minecraft 1.21.1 · needs Fabric API · install on the server and every player's game · Cobblemon optional**

![Three builders putting up the Market Stall, the Starter Cottage and the Lookout Tower](https://jcondedata.github.io/minecraft-alive-workplace/builders/anim.gif)

## Builders: hand them a blueprint, they build it

Place a **Blueprint Table** near a villager without a job and they become a **Builder**. Buy a blueprint from them, right-click the ground where the front should go, stock the chests near the table and hand the blueprint over. They clear the site, fetch materials, put up the walls and roof, and finish with doors, beds and torches.

While you hold the blueprint you see the whole building as see-through blocks, exactly where it will stand, and its tooltip says what the chests are still short of.

![The see-through preview of the Starter Cottage, half built](https://jcondedata.github.io/minecraft-alive-workplace/preview/still-2.jpg)

- **Dozens of blueprints**: houses, an inn, a library, a ranch, barracks, walls and gates, a fountain, a chapel.
- **Upgrades**: most buildings have a II, many a III. The builder adds only what's new.
- **Styles**: Stonework, Sandstone, Dark Oak or Cherry (Apricorn with Cobblemon), or mirrored.
- **Your own builds**: upload `.litematic` and `.schem` files, scan what you built with the **Scan Tool**, or draw towers and domes with the **Shape Planner**.
- Builders **level up** (a Master builds 2.5 times as fast as a Novice), help each other and repair what they built.

## Villagers with real jobs

Most jobs share a vanilla block. Stand a villager by it and sneak-right-click them with the job's item: a pickaxe at a blast furnace makes a **Miner**, a sword at a grindstone a **Guard**. Workers take tools from the chests near their workstation and put their work there.

![A miner digging out a quarry](https://jcondedata.github.io/minecraft-alive-workplace/quarry/anim.gif)

**Gathering**

- **Miner**: digs out the quarry or strip mine you mark, and smelts the ore
- **Lumberjack**: fells and replants the trees nearby
- **Farmer** and **Orchard Keeper**: harvest and replant fields, berries and cocoa
- **Fisherman**: fishes from the shore or a boat
- Also the **Beekeeper**, **Florist**, **Sifter**, **Composter**, **Shepherd**, **Butcher** and **Rancher**, and the **Cartographer** and **Netherworker**, who go on expeditions

**Making and carrying**

- **Porter**: carries everyone's goods to the village **Storehouse**
- **Carpenter**, **Mason**, **Leatherworker** and **Tinkerer**: make the parts a builder is missing
- **Chef**: cooks for the village
- **Armorer**, **Toolsmith**, **Weaponsmith**, **Fletcher**, **Cleric** and **Librarian**: make tools, mend gear, and arm, brew for and enchant the guards

**Looking after the village**

- **Guard**: fights monsters, rides a horse or a camel, and follows your **Rally Banner** into a fight
- **Nurse**: heals you and cures the ill
- **Shopkeeper**, **Postman** and **Ferryman**: your shop, your mail and travel between your Travel Posts
- Also the **Bard**, **Teacher**, **Scholar**, **Innkeeper** and **Undertaker**

Workers near each other share their chests, so a builder short of stone takes it from the miner's.

## Villages that grow

Craft a **Village Hall**, put it in the middle of your village and right-click it: its beds and food, what the workers are waiting for, and everyone who lives there with their job, level and what they're doing right now.

![The Village Hall's list, showing a builder's level, trait and what they need](https://jcondedata.github.io/minecraft-alive-workplace/hall/still-3.jpg)

- Villagers get **names**, **traits** and **moods**. They eat from the village store, fall ill now and then, marry, and have children when there's a free bed and food.
- The village grows from a **Hamlet** to a **City**, puts up **quests**, and keeps a **chronicle** of what happened there.
- **Festivals**, **market days**, **research**, and night **raids** and **bandit camps** for the guards.
- **Protection**: shift-click the hall's name tag and only you, your friends and operators can build or open chests in the village. Off until you turn it on.
- A **Village Ledger** opens the hall's screen from anywhere nearby.

Villages also grow buildings of their own: workshops, guard houses, clinics, post offices, kitchens and inns.

![A newly generated village with a builder's workshop](https://jcondedata.github.io/minecraft-alive-workplace/village/still-3.jpg)

## With Cobblemon

Cobblemon is optional. With it installed, villagers take Pokémon jobs too:

- **Trainer**: battle them; they rank up from Novice to Master as people battle them. Each village can have one **Trainer Leader**.
- **Move Tutor**: teaches tutor, TM and egg moves.
- **Ball Smith**: makes Poké Balls from apricorns.
- **Pokémon Trader**: swaps Pokémon, with new offers every day.
- **Fossil Scientist**: revives fossils at Cobblemon's Fossil Analyzer.
- **Rancher**: a daycare where your Pokémon gain experience.

![A guard on patrol with a Machop and a Dratini from a pasture at his side](https://jcondedata.github.io/minecraft-alive-workplace/guard_pokemon/still-1.jpg)

Pokémon in a Pasture Block near a workstation help with the job when their type suits it, and Fighting and Dragon types fight beside the guards. With CobbleDollars, prices and rewards are in CobbleDollars.

## Getting started

![A settlers' camp at sunset: the covered wagon, the campfire and the bedrolls](https://jcondedata.github.io/minecraft-alive-workplace/camp/still-2.jpg)

1. Read the Guide Book: every player gets one the first time they join.
2. Find a village, or craft a **Settler's Wagon** and right-click open ground: two settlers make camp with a Blueprint Table and a chest of supplies. The first settler is your builder.
3. No builder yet? Place a **Blueprint Table** near a villager without a job.
4. Get the **Starter Cottage** blueprint (builders sell it; the wagon's chest has one) and right-click the ground where its front should go.
5. Put the materials in chests within 8 blocks of the Blueprint Table. Hold Shift over the blueprint for the list.
6. Right-click the builder with the blueprint. The line over their head shows their progress and what they're waiting for.

The recipes are in the recipe book.

## Good to know

- **Both sides need it**: the server and every player's game, with Fabric API, on Minecraft 1.21.1.
- **Existing worlds are fine**: villagers already there can take the new jobs, and villages you explore next can come with a builder's workshop.
- **Made for the Cobbleverse modpack**, and works without it. Builders understand **Chipped**, **Rechiseled** and **Supplementaries** blocks.
- **Server owners**: `config/aliveworkplace.json` turns off moods, sickness, raids and more. In a test with the whole Cobbleverse pack, 80 busy workers took the average tick from about 2 ms to 7 ms, of the 50 ms a tick may take.
- Still in 0.x: things may change before 1.0.

## Links

- [The full guide to every job](https://github.com/jCondeData/minecraft-alive-workplace#readme)
- [Showcase: every job filmed in game, every night](https://jcondedata.github.io/minecraft-alive-workplace/)
- [Report a bug](https://github.com/jCondeData/minecraft-alive-workplace/issues)
- [Source code](https://github.com/jCondeData/minecraft-alive-workplace) (GPL-3.0-or-later)
- [Changelog](https://github.com/jCondeData/minecraft-alive-workplace/blob/main/CHANGELOG.md)
