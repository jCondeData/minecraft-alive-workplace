# Changelog

## Unreleased

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
