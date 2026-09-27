# Alive Workplace

Villagers with real jobs. Hand a **Builder** a blueprint and they build it for you: they clear the
site, fetch materials from your chests, put up the walls and roof, and finish with doors, beds and
torches.

![Three builders putting up the starter blueprints](docs/media/timelapse.gif)

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
   sneak-right-click the air to pick it back up. On uneven ground the builder fills in a foundation.
4. **Stock the chests.** Put the materials in any chests or barrels within 8 blocks of the builder's bench
   (hold Shift over the blueprint to see the list).
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
**Healing Center** (with a Cobblemon Healing Machine on the counter when Cobblemon is installed) and the **Supply Shop**.

## Miners
![A miner digging out a quarry](docs/media/miner.gif)

Craft a **Miner's Bench** (cobblestone on top, a stone pickaxe in the middle, planks around) and place it near a
villager without a job. Then:
1. Craft a **Quarry Marker** (stick + red dye + paper). Right-click one corner block, then the opposite corner
   (up to 32 × 32). Sneak-right-click the air to choose the depth (4, 8, 16, 32 or 64); a red outline shows the pit.
2. Put **pickaxes** (and some torches) in a chest within 8 blocks of the Miner's Bench.
3. Give the marker to the miner. They dig the area out from the top down, bring everything back to the chests, and
   leave anything touching lava or water standing so the pit stays dry. When the last pickaxe wears out they wait
   for another.

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
- Blocks from mods that aren't installed turn into air and are left out.

## Commands and gamerules
| | |
|---|---|
| `/workplace sites` | your builds in progress, with a cancel button |
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

## What's next
Next up are more jobs — lumberjacks, farmers and couriers — then Cobblemon trainers and Pokémon work partners. See [ROADMAP.md](ROADMAP.md).

## Building from source
```
./gradlew build          # jar in build/libs, runs the in-game test suite
./gradlew runGameTest    # just the tests
```
Every push is built and tested by GitHub Actions; the jar is attached to each run.

## License
GPL-3.0-or-later. See [LICENSE](LICENSE).
