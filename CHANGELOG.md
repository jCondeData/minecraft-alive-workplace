# Changelog

## Unreleased

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
