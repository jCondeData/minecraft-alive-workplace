# Roadmap

Work top to bottom. **Builders are the top priority**, then the other work jobs, then trainers,
then Pokémon partners. Each item should land with gametests. Tick the box in the same commit that
finishes it, and add a line to `CHANGELOG.md`.

Owner decisions are recorded in **Design decisions** at the bottom. Don't change them without asking.

---

## Milestone 1 — Builders

### Done (0.1.0)
- [x] Builder profession + **Builder's Bench** workstation (jobless villagers claim it like vanilla job blocks)
- [x] **Blueprint** item: right-click the ground to place it (it faces you), sneak-right-click the ground to turn it, sneak-right-click the air to pick it back up; particle outline while held
- [x] Hand a placed blueprint to a builder → build site saved with the world
- [x] Builder AI: clears the site, fetches materials from any storage within 8 blocks of the bench (Fabric transfer API, so modded storage works), builds bottom-up, then does decorations (torches, doors, beds…), returns the blueprint
- [x] Handles doors/beds/tall plants, rotation, block-entity data (signs, banners) without copying chest contents
- [x] Waits for missing materials and tells the owner exactly what is missing; sneak-right-click a builder for status
- [x] Hops to a spot it can stand on when it can't walk somewhere (walled in, over a gap)
- [x] Picks up where it left off after night, panic, chunk unload or server restart
- [x] Starter blueprints: Starter Cottage, Market Stall, Lookout Tower — sold by builders (levels 1–3)
- [x] Gamerules `workplaceFreeMaterials`, `workplaceBuildDelay`; `/workplace` command (blueprints, sites, cancel)
- [x] Builders work a longer shift (1000–11000) and sleep at night

### Done (0.2.0)
- [x] **Blueprint Table** block + screen: browse the server's blueprint library, see size and materials, take a blueprint for a Blank Blueprint (paper + blue dye); taking the same blueprint again is how you copy one
- [x] **Import `.litematic` and `.schem`** (Sponge/WorldEdit v1–v3) and `.nbt` → stored as structure templates in `<world>/generated/aliveworkplace/structures/`; old block names upgraded by the data fixer; unknown modded blocks become air and are counted; `/workplace import` for a server folder
- [x] **Upload from the client** at the Blueprint Table (30 KB pieces, 8 MB limit, `workplaceAllowUploads` gamerule, ops always allowed)
- [x] **Materials list** on the Blueprint Table screen
- [x] Library only lists real blueprints (ours, uploads, Structure Block saves), not other mods' world-generation pieces

### Done (0.3.0)
- [x] **Foundations**: builds on uneven ground get the gap under their floor filled down to the ground (dirt under grass, the floor's own block when it is a plain block, else cobblestone); `workplaceFoundationDepth` gamerule (12, 0 = off)
- [x] **See-through preview**: holding a placed blueprint shows the building as translucent blocks; blocks already built disappear from it (the particle outline stays for the footprint and front edge)
- [x] **Status above the builder's head**: build name, percentage, progress bar and what they are doing / waiting for (server sends it once a second to nearby players)

### Next
- [x] Materials list in the blueprint tooltip (hold Shift)
- [ ] "What's still missing" in the tooltip once a blueprint is handed over / placed near a builder's chests
- [x] **Build queue**: hand a busy builder up to 5 more blueprints; they do them in order (queued sites reserve their spot, show in `/workplace sites` and can be cancelled)
- [x] **Crews**: idle builders with a bench within 48 blocks of a build help out (up to 3 per site): they work ahead of the lead builder on the same stage, fetch from the lead's chests, pass spare materials to each other, and go home when it's done (`workplaceBuildersHelp` gamerule)
- [x] **Deconstruct**: sneak-give a placed blueprint to a builder and they take that building down (decorations first, then top-down), returning exactly what each block cost to the supply chests; blocks that differ from the blueprint and anything that holds items are left standing
- [ ] **Permissions**: only the owner (and friends they add) can command their builders; `/workplace friend add`
- [x] **Builder levels**: 1 XP per 5 blocks placed + 10 per finished build, vanilla level thresholds; level-ups unlock the next trades; each level is faster (Master takes 40% of the base time per block)
- [x] **More blueprints**: Healing Center (level 4, holds a Cobblemon Healing Machine when Cobblemon is installed) and Supply Shop (level 5)
- [ ] Optional ground levelling around the site (foundations are done; per-blueprint opt-out later)
- [x] Potted plants (cost a flower pot + the plant)
- [x] **Building-mod support** (tested with the real mods in `src/compattest`): Chipped and Rechiseled variants can be built from the plain block (free conversions, like their workbench/chisel); Supplementaries blocks build correctly (way signs cost the fence and the sign, rope knots the rope and the fence), and no blueprint data can hand out items, mobs or locked safes (containers never get contents)
- [ ] Fluids (water/lava via buckets), entities in templates (item frames, armor stands)
- [x] **Builder's workshop** in village generation (all 5 village types, weight 3 in the house pools ≈ every other village): a Builder's Bench, a supply chest (`aliveworkplace:chests/village_builders_workshop`, sometimes a starter blueprint) and a villager who takes the bench
- [ ] Builder's workshops for modded villages (Towns and Towers etc.) — add their house pools by id
- [ ] Config file (`config/aliveworkplace.json`) mirroring gamerules plus supply radius, reach, max site distance
- [ ] Upgrades: blueprints can declare a next tier that builds over the previous one

## Milestone 2 — Other work jobs
- [ ] **Miner**: given a Quarry Marker area, digs it out layer by layer, stores ore/stone in chests, places torches, never digs under itself into lava/void
- [ ] **Lumberjack**: fells trees in an area, replants saplings, stores logs
- [ ] **Farmer upgrade**: works marked fields beyond vanilla farmers, stores the harvest
- [ ] **Fisher**: fishes at nearby water, stores the catch
- [ ] **Courier / hauler**: moves items between marked chests (e.g. quarry → builder stash)

## Milestone 3 — Trainers (Cobblemon; optional dependency)
- [ ] Optional Cobblemon integration layer (mod must still load without Cobblemon)
- [ ] **Trainer** profession + **Training Post** workstation; trainer posts generate in villages so every village has trainers
- [ ] 5 tiers like trade levels — Novice → Apprentice → Journeyman → Expert → Master — team size and level scale from very easy to extremely hard (Master: full team, lv 80–100, competitive sets, smarter AI, Mega Evolution if Mega Showdown allows)
- [ ] Trainers **level up when you battle them** (XP per battle, like trading); levels are shared server-wide
- [ ] Rewards: **CobbleDollars** only, scaling with tier; **no badges, no gyms**
- [ ] **Trainer Leader**: one per village, starts at Expert strength, pays the most, one rematch per in-game day, no badge
- [ ] Use RCT API for battles/AI if it can drive arbitrary entities; otherwise Cobblemon's NPC battle API
- [ ] Tune level numbers against the pack's RCT level caps

## Milestone 4 — Pokémon partners & Cobblemon jobs
- [ ] Assign one of your Pokémon to a villager; it follows them and boosts the job by type (Fighting → building, Ground/Rock → mining, Grass → farming, Water → fishing, Fire → smelting, Flying → hauling)
- [ ] Pokémon-themed blueprints that come with staff: Pokémon Center (Nurse), Poké Mart (Clerk, sells for CobbleDollars), Berry Farm, Fossil Lab
- [ ] Jobs: Nurse (Healing Machine), Orchard Keeper (apricorns, berries, mints), Chef (Campfire Pot: Lure Cakes, Aprijuice), Ball Smith (apricorns → balls), Fossil Scientist
- [ ] Cobbleworkers compatibility: villagers haul from Pokémon pasture output

## Milestone 5 — Release
- [ ] Publish on Modrinth and CurseForge as **Alive Workplace** (Minecraft brand rules: don't lead the name with "Minecraft")
- [ ] Screenshots/GIFs, a short wiki (how to build, how to import blueprints)
- [ ] Test inside the full Cobbleverse 1.7.x pack (mod conflicts, performance with many builders)

---

## Design decisions (from the owner)
- Target **Fabric 1.21.1** to match the Cobbleverse pack. The mod is needed on both server and clients.
- **Builders are the priority.** Giving villagers jobs is the core of the mod.
- Trainers: normal villagers get a Trainer job at different levels; they **level up as you battle them** (like trading); range from really easy to extremely difficult; **no badges and no gym leaders**; each village gets a **Trainer Leader** that starts difficult and pays money only.
- Cobblemon integration must be optional.
- License: GPL-3.0-or-later (lets us adapt MineColonies code, which is GPL-3.0-or-later, with attribution).
- Builds from the internet come in as files (.litematic/.schem/.nbt); we don't scrape sites. Only ship our own original builds.

## Notes / blocked
- (autonomous sessions: write anything you could not finish or need the owner to decide here)
