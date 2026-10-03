# Alive Workplace 0.138.0

Alive Workplace gives villagers real jobs. Hand a Builder a blueprint and they build it from the materials in your chests, while miners, lumberjacks, farmers, guards and many others work the village around them; a Village Hall lets the village grow. This is the first version on CurseForge: earlier versions and the full history are on [GitHub](https://github.com/jCondeData/minecraft-alive-workplace/blob/main/CHANGELOG.md).

Needs Fabric API on Minecraft 1.21.1, on the server and in every player's game. Cobblemon is optional. Updating from a version from GitHub: take the old Alive Workplace jar out of `mods`; your world and config carry over.

## Added

- **Guards ride camels**: a guard rides a saddled camel as well as a horse, donkey or mule. A camel needs no taming, only a saddle.

## Changed

- **Fewer job blocks.** Our jobs now work at vanilla blocks, shared with the vanilla job there. Stand a villager by the block and sneak-right-click them with the job's item: at a composter, sweet berries make an Orchard Keeper, a flower a Florist and bone meal a Composter; a pickaxe at a blast furnace makes a Miner, a sword at a grindstone a Guard, a music disc at a jukebox a Bard, and so on. Hold Shift over a workstation to see its jobs. A villager without a job still takes a vanilla block's vanilla job by themselves. Builders work at the Blueprint Table.
- **For existing worlds:** 26 old job blocks (the Builder's Bench, the Guard Post, the Fruit Basket...) can't be crafted any more, but the ones already in your world keep working. Village houses, the Settler's Wagon camp and the blueprints use the new blocks, and a village house whose block is shared comes with its worker.
- **Fossil Scientists work at Cobblemon's Fossil Analyzer**: stand a villager by one and sneak-right-click them with a fossil. They still revive the fossil themselves, without the rest of the machine. Village fossil labs and the Research Lab blueprint have an analyzer now. Scientists already working at a Training Post or a Fossil Lab keep working there.
- **A new look**: every workstation, villager outfit and item is redrawn in Minecraft's own style. Workstations are built from their wood or stone like the crafting table, outfits are cleaner, and a job's hat no longer has the biome's hat showing through it.
- **New item icons**: the Blueprint is clipped to a drawing board, the Shape Planner is a brass compass, the Village Ledger lies open, the Delivery Note is a clipboard, the Travel Ticket has a sailing boat, the Price Tag a $ sign, the Quarry Marker a chequered flag on a stake, and the Scan Tool is a drafting pencil. Blueprints and the Patrol Map look like maps, and the markers and the Rally Banner are held like tools.
- **Villagers sit when they ride**: a guard on horseback sits in the saddle, and a ferryman or fisher sits in the boat at the oars.
- **Post office**: a postman's Mailbox is the post office's counter. With no mailbox of your own, pick up your parcels there.

## Fixed

- Villagers that come with some vanilla desert houses no longer suffocate in the walls.
- Our village houses no longer leave invisible holes in the ground in front of their doors.
- **Village protection**: a stranger's arrows, tridents and thrown potions no longer hurt a protected village's villagers and animals, and a Village Ledger bound before the village was protected no longer opens its hall for a stranger.
- Guards get off their horse, and fishers come ashore, as soon as their shift ends.
- Giving a job from the Village Hall no longer fails when the villager's old workstation was broken while they were far away.
- Bandits raiding a village at night can no longer be drawn into a vanilla raid.
- A bed at the far end of a big building now counts as a home in it.
- The Village Hall's "What next?" tips about homes and the next rank show the right numbers.
- An imported blueprint with a long number at the end of its name (`house_20260929`) no longer upsets the moods of the villagers who live in it.
