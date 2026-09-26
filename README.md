# Alive Workplace

Villagers with real jobs. Hand a **Builder** a blueprint and they build it for you: they clear the
site, fetch materials from your chests, put up the walls and roof, and finish with doors, beds and
torches.

![Three builders putting up the starter blueprints](docs/media/timelapse.gif)

Fabric · Minecraft 1.21.1 · built for the Cobbleverse (Cobblemon) modpack, but works without it.
Install on the **server and every player's game**.

## Getting started
1. **Hire a builder.** Craft a **Builder's Bench** and place it near a villager without a job; they take it
   like any job block.

   ```
   Brick   Brick          Brick
   Planks  Crafting Table Planks
   Planks  Planks         Planks
   ```
2. **Get a blueprint.** Builders sell the starter blueprints (Starter Cottage, Market Stall, Lookout Tower).
   Operators can also use `/workplace blueprint <id>`, including anything saved with a Structure Block.
3. **Place it.** Hold the blueprint and right-click the ground where the front of the building should go.
   It faces you; a blue outline shows the footprint and the gold edge is the front.
   Sneak-right-click the ground to turn it; sneak-right-click the air to pick it back up.
4. **Stock the chests.** Put the materials in any chests or barrels within 8 blocks of the builder's bench.
5. **Hand it over.** Right-click the builder with the blueprint. They start work in the morning, sleep at night,
   and tell you if they run out of something. Sneak-right-click a builder with an empty hand to see progress.

When they finish, the blueprint goes back into the supply chest so you can build it again.

## Commands and gamerules
| | |
|---|---|
| `/workplace sites` | your builds in progress, with a cancel button |
| `/workplace cancel <id>` | stop a build (placed blocks stay; you get the blueprint back) |
| `/workplace blueprints` (op) | list every blueprint the server knows |
| `/workplace blueprint <id>` (op) | get a blueprint item |
| `/gamerule workplaceFreeMaterials true` | builders need no materials (creative towns) |
| `/gamerule workplaceBuildDelay 8` | ticks per block (lower is faster) |

## What's next
Builders first: a Blueprint Table for picking and uploading `.litematic`/`.schem` files, a
see-through preview, build queues and crews. Then miners, lumberjacks and couriers, then Cobblemon
trainers and Pokémon work partners. See [ROADMAP.md](ROADMAP.md).

## Building from source
```
./gradlew build          # jar in build/libs, runs the in-game test suite
./gradlew runGameTest    # just the tests
```
Every push is built and tested by GitHub Actions; the jar is attached to each run.

## License
GPL-3.0-or-later. See [LICENSE](LICENSE).
