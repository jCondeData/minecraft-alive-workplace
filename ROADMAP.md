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
- [x] "What's still missing" in the tooltip of a placed blueprint: every 2 s while it's in a player's inventory the
  server compares its plan (blocks already in place skipped, foundation included) with the chests by the nearest
  Builder's Bench within 48 blocks and stores the shortfall in a synced, unsaved component (`SupplyReport`); after
  hand-over the builder's own status and sneak-click list cover it
- [x] **Build queue**: hand a busy builder up to 5 more blueprints; they do them in order (queued sites reserve their spot, show in `/workplace sites` and can be cancelled)
- [x] **Crews**: idle builders with a bench within 48 blocks of a build help out (up to 3 per site): they work ahead of the lead builder on the same stage, fetch from the lead's chests, pass spare materials to each other, and go home when it's done (`workplaceBuildersHelp` gamerule)
- [x] **Deconstruct**: sneak-give a placed blueprint to a builder and they take that building down (decorations first, then top-down), returning exactly what each block cost to the supply chests; blocks that differ from the blueprint and anything that holds items are left standing
- [x] **Permissions**: a builder works for whoever hires it first (hands it a blueprint); only that player, their friends (`/workplace friend add|remove|list`) and ops can give it blueprints or cancel its builds; hired builders only help their employer's and friends' builds (`workplaceBuilderOwnership` gamerule)
- [x] **Builder levels**: 1 XP per 5 blocks placed + 10 per finished build, vanilla level thresholds; level-ups unlock the next trades; each level is faster (Master takes 40% of the base time per block)
- [x] **More blueprints**: Healing Center (level 4, holds a Cobblemon Healing Machine when Cobblemon is installed) and Supply Shop (level 5)
- [x] Ground levelling around the site: a LANDSCAPE stage after DECORATION digs natural ground (dirt/sand/stone/
  gravel/clay/terracotta/snow, grass and ferns; not trees, flowers, farmland, paths, block entities) out of a ring
  `workplaceLevelGround` blocks wide (default 2, 0 = off) from the floor up to 6 blocks, then fills holes at floor
  level with dirt up to 3 deep (never water); it skips holes rather than wait for dirt
- [ ] Per-blueprint opt-out of levelling (e.g. builds meant to sit in a hillside)
- [x] Potted plants (cost a flower pot + the plant)
- [x] **Building-mod support** (tested with the real mods in `src/compattest`): Chipped and Rechiseled variants can be built from the plain block (free conversions, like their workbench/chisel); Supplementaries blocks build correctly (way signs cost the fence and the sign, rope knots the rope and the fence), and no blueprint data can hand out items, mobs or locked safes (containers never get contents)
- [x] Fluids: still water/lava (any fluid with a bucket) is a DECORATION step costing its filled bucket; the empty
  bucket (the item's crafting remainder) goes back in the bag; flowing liquid is skipped; waterlogging is still stripped
- [x] Entities in templates: item frames, glow item frames, paintings and armor stands (not markers) are read from
  structure NBT, .litematic (region Entities, relative to the region Position) and .schem (relative to the minimum
  corner), cleaned of contents/UUID/invulnerability (`BlueprintEntities`), written back into imported templates, and
  put up in `Builders.finish` for their item from the chests, rotated like a structure template (`BuildEntities`);
  counted in materials and the "still missing" report
- [x] **Builder's workshop** in village generation (all 5 village types, weight 3 in the house pools ≈ every other village): a Builder's Bench, a supply chest (`aliveworkplace:chests/village_builders_workshop`, sometimes a starter blueprint) and a villager who takes the bench
- [x] Our houses in modded villages: the Cobbleverse pack (1.7.42) has **Repurposed Structures** (no Towns and Towers),
  so the workshop and staffed houses join its 11 overworld village house pools (`VillageHouses.MODDED_HOUSE_POOLS`,
  closest vanilla style each; not crimson/warped/ocean); it uses vanilla jigsaw names; compat-tested with RS 7.5.21
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
- [x] **Courier / hauler** (done by Postmen): a Delivery Note marks a source and a destination container (plus an
  optional list of items to carry; by default everything but tools, weapons and armor); a postman takes up to 4
  routes and runs them whenever there's no mail, carrying a bagful per trip and taking back what doesn't fit; a blank
  note ends all routes and returns the notes

## Milestone 2b — Village life (roles from MyNPCs, rebuilt as villager jobs)
The owner pointed at *My NPCs* (MIT): admin-configured NPCs with roles. We don't copy its approach (op-made NPCs,
setup screens); we take the roles that fit a friends' Cobblemon server and make them jobs villagers take at a
workstation, like the rest of the mod.
- [x] **Postman + Mailboxes** (their Mailman): a Mailbox belongs to whoever places it (their newest one is their
  address; owner, friends and ops can open it); its screen posts the outgoing row to a named player; a Postman
  (Postal Desk workstation) collects parcels from mailboxes within 64 blocks of the desk and delivers to recipients
  on the round; anything else is handed in and arrives at the next dawn (`PostOffice` saved data; the flag shows
  there's mail; comparators read it)
- [ ] Postman follow-ups: letters with a written message; parcel tracking
- [x] **Guard** (Guard Post workstation): fights monsters (not creepers) within 24 blocks of the post at any hour
  (combat is in their CORE activity, and they never panic), with the best weapon and armor from the chests near the
  post; 40 health, heals between fights, +10% damage per level, XP per kill; night-watch schedule (patrol evening
  to mid-morning, sleep until early afternoon); never targets players, villagers, golems, animals, pets or Pokémon
- [ ] Guard follow-ups: bows/crossbows (shoot creepers from range), armor shown on the villager model, guards in
  village generation, rallying to the bell when it rings
- [x] **Nurse** (Nurse Station workstation): right-click with an empty hand (sneak to trade) to get full health and
  harmful effects cleared, and with Cobblemon installed the whole party healed (not mid-battle); per-player
  cooldown of a minute, shorter as the nurse levels; at work they also heal hurt villagers and iron golems nearby
  (guards!); sells healing potions, honey, golden apples/carrots. Cobblemon is compile-only (`compat/cobblemon`),
  tested with the real Cobblemon 1.7.3 in `runCompatGameTest`
- [x] Nurse Station in the Healing Center blueprint; Shop Counter in the Supply Shop blueprint
- [ ] Nurse follow-ups: Cobblemon's Healing Machine as a second workstation
- [x] **Shopkeeper** (their Item Trader): the Shop Counter (owned by whoever places it; owner, friends and ops open
  it) is a 9×2 price list — top slot what one sale hands over, below it the price (any item); the Shopkeeper's
  offers are rebuilt from the chests near the counter whenever someone talks to them (sold out = no stock), a sale
  takes the goods out of those chests and puts the payment in; workers never use counters or mailboxes as supply
  chests (`PrivateContainer`)
- [ ] Shopkeeper follow-ups: prices in CobbleDollars when that mod is installed, a sales log for the owner
- [x] **Ferryman** (their Teleporter): Travel Posts (named in an anvil) form one network (`TravelNetwork` saved
  data); right-clicking a post, placing it or talking to its ferryman adds it to the posts you know; a Ferryman sells
  Travel Tickets to every other post you know for 1 emerald per 256 blocks (1–16; 8 across dimensions); a ticket
  works within 16 blocks of any post and lands you next to its post
- [ ] Ferryman follow-ups: travel posts in village generation, a boat ride animation, CobbleDollars fares
- [x] **Bard** (Music Stand workstation): a morning set (1000–3500) and an evening set (9000–12500) at the stand,
  playing the music discs from the chests nearby in turn (discs stay in the chest; "Now playing" like a jukebox) or,
  with none, a made-up pentatonic harp tune with a bass beat; sells note blocks, goat horns and a few discs
- [x] Keep worksites loaded while their employer is online (their Chunk Loader): builds and quarries whose owner is
  online keep the chunks of the site, the workstation and its chests, and the worker's own chunk loaded (expiring
  tickets renewed every 5 s; capped per job); gamerule `workplaceKeepWorkLoaded` (on)
- Cobblemon roles go to the Cobblemon milestones: Pokémon Trainer (Milestone 3), Move Tutor and Pokémon Trader (Milestone 4).
- Not planned: Banker, Item Giver, Dialogue editor, Puppet, Spawner, Follower/Companion — tools for admins building
  adventure maps rather than jobs for villagers.

## Milestone 3 — Trainers (Cobblemon; optional dependency)
- [x] Optional Cobblemon integration layer: `compat/cobblemon` is the only code touching Cobblemon classes, called only
  when it's loaded; Cobblemon 1.7.3 is compile-only and runs in `runCompatGameTest`
- [x] **Trainer** profession + **Training Post** workstation: right-click (empty hand) to battle; a real Cobblemon battle
  (`TrainerBattleActor` + `BattleRegistry.startBattle`, gen 9 singles) against the trainer's team
- [x] Trainer posts generate in villages: a **trainer's house** (Training Post, targets, a bed, a villager spawn) in all
  5 village types at weight 6, plus **guard houses** (3, with a chest of starting gear), **clinics** (2) and **post
  offices** (2) — same 7×8×8 shell as the builder's workshop (`staffed_house` in `tools/blueprints/generate.py`)
- [x] 5 tiers = villager level, Novice → Master: team 2/3/4/5/6 at lv 5–12 / 15–25 / 30–42 / 50–65 / 80–100; basic
  Pokémon for beginners, fully evolved at the top, never legendary/mythical/ultra beast/paradox; the team is fixed per
  trainer and tier (seeded by the villager); Novice uses Cobblemon's random AI, higher tiers `StrongBattleAI(tier)`
- [ ] Master extras: competitive sets (items, natures, EVs), Mega Evolution if Mega Showdown allows
- [x] Trainers **level up when you battle them** (+5 XP a battle, +3 more when they win; vanilla level thresholds);
  levels are the villager's, so shared server-wide
- [x] Rewards: **CobbleDollars** (100/250/500/1000/2500 by tier, via `/cobbledollars give`), once per in-game day per
  player per trainer; emeralds when CobbleDollars isn't installed; **no badges, no gyms**
- [x] **Trainer Leader** (Leader's Podium: gold, a Training Post and polished andesite): battles at Expert strength from
  the start (Master once they reach level 5), pays 3× a trainer's prize, one challenge per player per in-game day;
  if a village has several, only the most experienced within 64 blocks takes challenges; no badge
- [x] ~~RCT API~~: Cobblemon's own `TrainerBattleActor` battles for any villager, no RCT needed
- [x] Tune levels against the pack's RCT level caps: RCT 0.18.1 (in the pack) exposes `LevelUtils.levelCap(Player)`;
  read by reflection (`compat/rct/RctLevelCaps`, no dependency), a tier's ceiling is cap −6/−3/0/+3/+5; Pokémon above
  it are re-created at the ceiling (same species). Not compat-tested with RCT itself (it spawns trainers around test
  players); the scaling is tested with a given cap, and the fallback without RCT

## Milestone 4 — Pokémon partners & Cobblemon jobs
- [ ] Assign one of your Pokémon to a villager; it follows them and boosts the job by type (Fighting → building, Ground/Rock → mining, Grass → farming, Water → fishing, Fire → smelting, Flying → hauling)
- [ ] Pokémon-themed blueprints that come with staff: Pokémon Center (Nurse), Poké Mart (Clerk, sells for CobbleDollars), Berry Farm, Fossil Lab
- [ ] Jobs: Nurse (Healing Machine), Orchard Keeper (apricorns, berries, mints), Chef (Campfire Pot: Lure Cakes, Aprijuice), Ball Smith (apricorns → balls), Fossil Scientist
- [ ] Cobbleworkers compatibility: villagers haul from Pokémon pasture output
- [x] **Move Tutor** (Tutor's Desk workstation): right-click (empty hand) opens a lesson screen (`work/ChoiceMenu`, a
  server-side six-row chest of buttons, no client screen needed): the party on top, the chosen Pokémon's tutor/TM/egg
  moves it can't use yet below, paged; click twice to pay emeralds and teach (into the moveset if there's room, else
  the benched moves it can swap in). Grade 1–5 by power (≤50/70/85/100/more; status 3; egg +1), price 3/6/10/16/24;
  a tutor teaches up to their level and gains 2 + grade XP a lesson
- [x] Move Tutor prices in CobbleDollars (100 per emerald: 300–2,400) through `work/Money` (CobbleDollars balance via
  `compat/cobbledollars/CobbleDollarsBank`, the only code touching it; falls back to emeralds without the mod or if
  its API changes); trainer prizes use it too; compat-tested with CobbleDollars 2.0.0 Beta-5.1 (the pack's version)
- [ ] Move Tutor follow-ups: tutors in village generation
- [x] **Pokémon Trader** (Trade Board workstation): the day's offers (seeded by trader and day, the same for everyone):
  their Pokémon (young ones for beginners, fully evolved at the top; never legendary/mythical/ultra beast/paradox) for
  any of yours of a type they don't have, level ≥ theirs − 5; 1/2/2/3/3 offers at lv 5–15 / 15–25 / 25–35 / 35–50 /
  50–70, 1 in 10 shiny at Master; one trade per player per trader per in-game day; the Pokémon you give must be
  tradeable, its held item comes back; 6 XP a trade. Sneak for item trades (balls, candies, ability capsule)
- [ ] Pokémon Trader follow-ups: species wanted by name at higher tiers, traders in village generation

## Milestone 5 — Release
- [ ] Publish on Modrinth and CurseForge as **Alive Workplace** (Minecraft brand rules: don't lead the name with "Minecraft")
- [ ] Screenshots/GIFs, a short wiki (how to build, how to import blueprints)
- [x] Compat tests with the pack's (1.7.42) building mods — Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar
  Concrete (every block has a cost; a sample of each gets built) — and storage: Sophisticated Storage (fixed: counts
  used `Long.MAX_VALUE`, which it reads as 0), Tom's Simple Storage (network blocks skipped as supply chests).
  Lucky's Cozyhome can't be loaded in the Mojang-mapped dev environment (its `getItems()` clashes on remap), so it's
  untested; Chipped and Supplementaries aren't in the pack but stay tested
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
