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
- [x] **Permissions**: a builder works for whoever hires it first (hands it a blueprint); only that player, their friends (`/workplace friend add|remove|list`) and ops can give it blueprints or cancel its builds; hired builders only help their employer's and friends' builds (`workplaceBuilderOwnership` gamerule)
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
- [x] **Miner** (Miner's Bench workstation, Quarry Marker item): digs the marked area out layer by layer with pickaxes from the chests (tool tier and durability count; waits for a new one), drops everything off in the chests near the bench, lights the pit with torches from the chests, leaves blocks touching lava/water, containers and anything too hard, never goes below 5 above the world floor; levels up like builders; trades coal/ores, sells markers, torches and pickaxes
- [ ] Miner follow-ups: ladders or stairs out of deep pits, strip-mining tunnels (not just open pits), smelting helper
- [x] **Lumberjack** (Chopping Block workstation): fells natural trees within 16 blocks of the block (only trees: at least 4 natural leaves, trunk on dirt; player-placed logs, builds and quarries are left alone), clears the leaves, replants a sapling of the same wood, keeps up to 16 saplings of each kind and stores the rest in the chests near the block; axes from those chests wear out (waits for a new one); levels up like builders; trades sticks/apples, sells logs, saplings and an iron axe
- [ ] Lumberjack follow-ups: plant saplings on marked empty ground (tree farms), big 2×2 trees (dark oak, jungle, spruce) planted as 2×2, nether "trees" (stems/wart blocks)
- [x] **Farmer upgrade** (Field Marker item, any vanilla Farmer): give a farmer a marked field (up to 32×32, within 48 blocks of their composter) and they harvest ripe crops (any `CropBlock`, so modded crops too; nether wart, pumpkins/melons off a stem, sugar cane above the bottom block), plant the same crop straight back, sow empty farmland/soul sand with seeds from the chests near the composter (the crop next to it, else what there is most of), till bare dirt/grass with a hoe from the chests, and store the harvest in those chests; their vanilla routine is paused while the field needs work; longer shift like our workers; `/workplace cancel <farmer uuid>` (clickable in the status) stops it
- [ ] Farmer follow-ups: bone meal from the chests, cocoa, sweet berries (they hurt villagers), a Field Marker for villages' own farms
- [x] **Fisher** (any vanilla Fisherman): hand one a fishing rod and they fish the nearest still water within 16 blocks of their barrel (standing on the shore), reeling in the vanilla fishing loot (fish and junk, no treasure), storing every fifth catch in the barrel and chests next to it; rods wear out and spares come from those containers; vanilla routine paused while working; longer shift; `/workplace cancel <fisherman uuid>` stops it
- [ ] Fisher follow-ups: a real bobber on the water, fishing from boats/docks, smoking the catch
- [ ] **Courier / hauler**: moves items between marked chests (e.g. quarry → builder stash)

## Milestone 2b — Village life (roles from MyNPCs, rebuilt as villager jobs)
The owner pointed at *My NPCs* (MIT): admin-configured NPCs with roles. We don't copy its approach (op-made NPCs,
setup screens); we take the roles that fit a friends' Cobblemon server and make them jobs villagers take at a
workstation, like the rest of the mod.
- [x] **Postman + Mailboxes** (their Mailman): a Mailbox belongs to whoever places it (their newest one is their
  address; owner, friends and ops can open it); its screen posts the outgoing row to a named player; a Postman
  (Postal Desk workstation) collects parcels from mailboxes within 64 blocks of the desk and delivers to recipients
  on the round; anything else is handed in and arrives at the next dawn (`PostOffice` saved data; the flag shows
  there's mail; comparators read it)
- [ ] Postman follow-ups: the same villager runs Courier routes; letters with a written message; parcel tracking
- [x] **Guard** (Guard Post workstation): fights monsters (not creepers) within 24 blocks of the post at any hour
  (combat is in their CORE activity, and they never panic), with the best weapon and armor from the chests near the
  post; 40 health, heals between fights, +10% damage per level, XP per kill; night-watch schedule (patrol evening
  to mid-morning, sleep until early afternoon); never targets players, villagers, golems, animals, pets or Pokémon
- [ ] Guard follow-ups: bows/crossbows (shoot creepers from range), armor shown on the villager model, guards in
  village generation, rallying to the bell when it rings
- [ ] **Nurse** (Cobblemon, optional): takes a Cobblemon Healing Machine as workstation; talk to them to heal your
  whole party (per-player cooldown). The Healing Center blueprint comes with one.
- [ ] **Shopkeeper** (their Item Trader): a Shop Counter you stock from a chest; the villager sells your items at
  prices you set, for emeralds or CobbleDollars (when installed), and keeps the takings for you.
- [ ] **Ferryman** (their Teleporter): Travel Posts in villages; pay to travel to any village post you have visited.
- [ ] **Bard**: plays music discs from a chest near their stage in the evening while the village gathers.
- [ ] Keep worksites loaded while their employer is online (their Chunk Loader), behind a gamerule.
- Cobblemon roles go to the Cobblemon milestones: Pokémon Trainer (Milestone 3), Move Tutor and Pokémon Trader (Milestone 4).
- Not planned: Banker, Item Giver, Dialogue editor, Puppet, Spawner, Follower/Companion — tools for admins building
  adventure maps rather than jobs for villagers.

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
- [ ] **Move Tutor**: teaches a Pokémon moves it could learn, for items or CobbleDollars
- [ ] **Pokémon Trader**: offers a fixed Pokémon for one you bring (species/level rules, per-player limits)

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
- *My NPCs* does some of what we want "in a different way" the owner doesn't love: rebuild the roles that fit the pack
  as villager jobs (Milestone 2b), not as admin-configured NPCs.

## Notes / blocked
- (autonomous sessions: write anything you could not finish or need the owner to decide here)
