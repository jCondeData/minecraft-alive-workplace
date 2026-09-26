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

### Next
- [ ] **Blueprint Table** block + screen: browse the server's blueprint library, pick one, get a blueprint; copy blueprints (Blank Blueprint = paper + blue dye)
- [ ] **Import `.litematic` and `.schem`** (Sponge/WorldEdit v2+v3) files → convert to structure NBT in `<world>/generated/aliveworkplace/structures/`. Unknown modded blocks become air with a warning.
- [ ] **Upload from the client** at the Blueprint Table (chunked packets, size limit, op/owner permission), so nobody has to touch the server file manager
- [ ] **Materials list** before starting: on the Blueprint Table screen and in the blueprint tooltip once placed
- [ ] **Ghost preview** (client render of the translucent blocks) replacing the particle outline; show what's left to build on active sites
- [ ] **Status above the builder's head** (e.g. `Starter Cottage 62% — needs 40 Glass`) via synced attachment + name-tag style renderer
- [ ] **Build queue**: hand a busy builder more blueprints; they do them in order
- [ ] **Crews**: several builders on one site split the steps
- [ ] **Deconstruct** blueprint mode: take a build down and return the blocks
- [ ] **Permissions**: only the owner (and friends they add) can command their builders; `/workplace friend add`
- [ ] **Builder levels**: XP per block, villager level-ups from work; higher level = faster, longer reach, better trades
- [ ] **Foundations**: fill air under the footprint down to the ground (optional per blueprint); optional ground levelling around the site
- [ ] Fluids (water/lava via buckets), potted plants (pot + plant), entities in templates (item frames, armor stands)
- [ ] **Builder's house** added to village generation (so builders appear naturally)
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
