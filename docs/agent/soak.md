# Builder soak (23.1): runs

`SOAK=true tools/packtest/run.sh` on Cobbleverse 1.7.42 (Java 21). Raw lines from the server log.

## Run 1 — lane-c-1003-1433, 2026-10-03 (first run, not triaged)

- Sprint: 48000 ticks at 453 ticks/s (2.21 ms/tick), so 2 in-game days take about 2 real minutes.
- 9 of 22 builds finished; 20 stall lines. Item counts are not yet meaningful: the check compares against every build's full needs, so unfinished builds show as leftovers (+).
- graveyard, healing_center and storehouse count as unfinished although their sites are gone (finished): the check uses the plan made at the start; the builder's plan may differ (trees, terrain). To check.

```
14:55:54 Soak: 10 builders, 22 builds, 11466 items in 25 chests
14:57:31 Builder stalled 30 s: aliveworkplace:barracks at 1101, 83, 1000 (stage STRUCTURE, status WORKING, 385 placed, 0 skipped, 0 kinds missing) [stall #1]
14:57:32 Builder stalled 30 s: aliveworkplace:healing_center at 1083, 81, 1026 (stage LANDSCAPE, status WORKING, 464 placed, 0 skipped, 0 kinds missing) [stall #2]
14:57:32 Builder stalled 30 s: aliveworkplace:inn at 1083, 85, 1104 (stage CLEAR, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #10]
14:57:32 Builder stalled 30 s: aliveworkplace:library at 1101, 79, 1078 (stage CLEAR, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #8]
14:57:32 Builder stalled 30 s: aliveworkplace:schoolhouse at 1031, 79, 1078 (stage CLEAR, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #7]
14:57:32 Builder stalled 30 s: aliveworkplace:sifting_shed at 1101, 85, 1052 (stage LANDSCAPE, status WORKING, 340 placed, 44 skipped, 0 kinds missing) [stall #6]
14:57:32 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage CLEAR, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #3]
14:57:32 Builder stalled 30 s: aliveworkplace:supply_shop at 1013, 85, 1052 (stage LANDSCAPE, status WORKING, 461 placed, 0 skipped, 0 kinds missing) [stall #5]
14:57:32 Builder stalled 30 s: aliveworkplace:terrace at 1013, 86, 1104 (stage STRUCTURE, status WORKING, 392 placed, 0 skipped, 0 kinds missing) [stall #9]
14:57:32 Builder stalled 30 s: aliveworkplace:tinkers_workshop at 1031, 80, 1026 (stage FOUNDATION, status WORKING, 162 placed, 0 skipped, 0 kinds missing) [stall #4]
14:58:01 Builder stalled 30 s: aliveworkplace:graveyard at 1119, 81, 1000 (stage FOUNDATION, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #11]
14:58:11 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing) [stall #12]
14:58:18 Builder stalled 30 s: aliveworkplace:compost_yard at 1031, 86, 1052 (stage LANDSCAPE, status FETCHING, 459 placed, 0 skipped, 0 kinds missing) [stall #15]
14:58:18 Builder stalled 30 s: aliveworkplace:graveyard at 1119, 81, 1000 (stage LANDSCAPE, status WORKING, 476 placed, 0 skipped, 0 kinds missing) [stall #17]
14:58:18 Builder stalled 30 s: aliveworkplace:inn at 1083, 85, 1104 (stage STRUCTURE, status WORKING, 187 placed, 175 skipped, 0 kinds missing) [stall #20]
14:58:18 Builder stalled 30 s: aliveworkplace:library at 1101, 79, 1078 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 490 placed, 0 skipped, 0 kinds missing) [stall #16]
14:58:18 Builder stalled 30 s: aliveworkplace:nether_gate at 1101, 88, 1026 (stage FOUNDATION, status WORKING, 603 placed, 0 skipped, 0 kinds missing) [stall #14]
14:58:18 Builder stalled 30 s: aliveworkplace:ranch at 1031, 83, 1104 (stage CLEAR, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #19]
14:58:18 Builder stalled 30 s: aliveworkplace:schoolhouse at 1031, 79, 1078 (stage CLEAR, status WORKING, 0 placed, 144 skipped, 0 kinds missing) [stall #18]
14:58:18 Builder stalled 30 s: aliveworkplace:tinkers_workshop at 1031, 80, 1026 (stage LANDSCAPE, status FETCHING, 657 placed, 0 skipped, 0 kinds missing) [stall #13]
```

Sites at the end (`/workplace sites`):

```
Builder — Stone House — 88%
  Building the structure · waiting for materials
Builder — Flower Shop — waiting until Stone House is finished
  [Cancel this build]
Builder — Tinker's Workshop — 100%
  Levelling the ground around it · fetching materials
Builder — Nether Gate — 79%
  Laying the foundation · working
Builder — Compost Yard — 100%
  Levelling the ground around it · working
Builder — Schoolhouse — 12%
  Building the structure · working
Builder — Library — 65%
  Building the structure · waiting for materials
Builder — Ranch — 0%
  Clearing the site · working
Builder — Inn — 33%
  Building the structure · working
Builder — Apiary Garden — waiting until Inn is finished
  [Cancel this build]
```

Result line:

```
Soak result: 9/22 builds finished in 48198 ticks; 20 stalls; items off: allium +8, andesite +51, azure_bluet +1, 
barrel +3, beehive +5, bell +1, birch_door +1, birch_planks +215, birch_stairs +16, birch_trapdoor +14, 
black_concrete +10, blue_bed +2, blue_orchid +1, bookshelf +2, bricks +48, brown_carpet +4, campfire +4, 
cartography_table +1, cauldron +4, chest +5, chiseled_polished_blackstone +6, coarse_dirt +58, cobblestone +358, 
composter +1, cornflower +4, cracked_polished_blackstone_bricks +30, cracked_stone_bricks +1, dandelion +2, 
dark_oak_door +2, dark_oak_log +246, dark_oak_pressure_plate +1, dark_oak_slab +8, dark_oak_stairs +64, 
dark_oak_trapdoor +16, deepslate_tile_slab +38, deepslate_tile_stairs +216, deepslate_tiles +1, dirt +1539, fern +2, 
flower_pot +17, gilded_blackstone +2, glass_pane +66, granite +10, green_bed +2, hay_block +9, honey_block +1, ladder 
+10, lantern +27, lectern +1, light_blue_bed +1, lightning_rod +1, lily_of_the_valley +1, mossy_stone_bricks +5, 
oak_fence +46, oak_fence_gate +1, oak_planks +55, oak_stairs +14, oak_trapdoor +2, obsidian +10, orange_tulip +5, 
oxeye_daisy +4, pink_tulip +1, polished_andesite +3, polished_blackstone +42, polished_blackstone_brick_slab +2, 
polished_blackstone_brick_stairs +17, polished_blackstone_brick_wall +10, polished_blackstone_bricks +174, 
polished_blackstone_wall +2, polished_diorite +37, polished_granite +8, poppy +5, red_bed +3, red_carpet +7, 
red_tulip +9, red_wool +4, smoker +1, soul_campfire +2, soul_lantern +4, spruce_door +2, spruce_fence +38, 
spruce_fence_gate +1, spruce_log +61, spruce_planks +321, spruce_pressure_plate +2, spruce_slab +37, spruce_stairs 
+389, spruce_trapdoor +76, stone +78, stone_brick_slab +2, stone_brick_stairs +2, stone_bricks +18, 
stripped_birch_log +29, stripped_oak_log +21, stripped_spruce_log +6, white_concrete +115, white_wool +3; unfinished: 
stone_house, flower_shop, graveyard, tinkers_workshop, healing_center, nether_gate, compost_yard, storehouse, 
schoolhouse, library, ranch, inn, apiary_garden
```

## Run 2 — lane-c-1003-1433, after stalls count only on-shift time

Same layout (the soak is deterministic): 9 of 22 builds again, 2 stalls inside the 2 days, 2 more as the sprint ended. Sprint 448 ticks/s.

```
15:08:00 Builder stalled 30 s: aliveworkplace:graveyard at 1119, 81, 1000 (stage FOUNDATION, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #1]
15:08:07 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing) [stall #2]
15:08:33 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing) [stall #3]
15:08:33 Builder stalled 30 s: aliveworkplace:inn at 1083, 85, 1104 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 1 placed, 179 skipped, 3 kinds missing) [stall #4]
15:08:34 Sprint completed with 448 ticks per second, or 2.23 ms per tick
Soak result: 9/22 builds finished in 48070 ticks; 2 stalls; items off: allium +8, andesite +65, azure_bluet +3, 
barrel +8, beehive +5, bell +1, birch_door +1, birch_planks +215, birch_stairs +16, birch_trapdoor +14, 
black_concrete +10, blue_bed +2, blue_orchid +1, bookshelf +2, brewing_stand +1, bricks +48, brown_carpet +4, 
campfire +5, candle +4, cartography_table +1, cauldron +4, chest +5, chiseled_polished_blackstone +6, coarse_dirt 
+58, cobblestone +399, composter +1, cornflower +4, cracked_polished_blackstone_bricks +33, cracked_stone_bricks +3, 
dandelion +2, dark_oak_door +3, dark_oak_log +249, dark_oak_pressure_plate +1, dark_oak_slab +8, dark_oak_stairs +64, 
dark_oak_trapdoor +16, deepslate_tile_slab +47, deepslate_tile_stairs +247, deepslate_tiles +28, dirt +1539, fern +2, 
flower_pot +26, gilded_blackstone +2, glass_pane +66, granite +10, green_bed +2, hay_block +10, honey_block +1, 
ladder +10, lantern +27, lectern +1, light_blue_bed +1, lightning_rod +1, lily_of_the_valley +4, mossy_stone_bricks 
+7, oak_fence +46, oak_fence_gate +1, oak_planks +55, oak_stairs +14, oak_trapdoor +2, obsidian +10, orange_tulip +5, 
oxeye_daisy +6, pink_tulip +1, polished_andesite +3, polished_blackstone +48, polished_blackstone_brick_slab +2, 
polished_blackstone_brick_stairs +17, polished_blackstone_brick_wall +10, polished_blackstone_bricks +197, 
polished_blackstone_wall +2, polished_diorite +37, polished_granite +8, poppy +7, red_bed +3, red_carpet +7, 
red_tulip +9, red_wool +4, shop_counter +1, smoker +1, soul_campfire +2, soul_lantern +7, spruce_door +2, 
spruce_fence +44, spruce_fence_gate +1, spruce_log +61, spruce_planks +393, spruce_pressure_plate +2, spruce_slab 
+39, spruce_stairs +406, spruce_trapdoor +76, stone +89, stone_brick_slab +2, stone_brick_stairs +4, stone_bricks 
+48, stripped_birch_log +29, stripped_oak_log +21, stripped_spruce_log +6, white_concrete +115, white_wool +3; 
unfinished: stone_house, flower_shop, graveyard, tinkers_workshop, healing_center, nether_gate, compost_yard, 
storehouse, schoolhouse, library, ranch, inn, apiary_garden
```

To triage next: graveyard stuck in FOUNDATION with 0 placed (on a slope); stone_house waits for 1 kind although the chests held exactly plan.materials() (miscount?); inn 179 skipped and 3 kinds missing; whether 2 days is enough: builders work about 7,000 of every 24,000 ticks (the villager WORK shift).

## Run 3 — lane-c-1003-1532, 6 days, item check by ledger

`SOAK=true SOAK_DAYS=6 tools/packtest/run.sh`. The item check now uses `MaterialLedger` (stocked + gained − built in −
dropped = left in every container and bag in the soak's ground), and a site counts as finished when it leaves the list.
Sprint 206 ticks/s this time (4.84 ms/tick), so 6 days took about 12 minutes.

**21 of 22 builds in 6 days, no item duplicated or lost.** 10 stall lines, now with the missing items:

```
15:59:07 Builder stalled 30 s: aliveworkplace:graveyard at 1119, 81, 1000 (stage FOUNDATION, status WORKING, 0 placed, 0 skipped, 0 kinds missing) [stall #1]
15:59:24 Builder stalled 30 s: aliveworkplace:library at 1101, 79, 1078 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 220 placed, 0 skipped, 0 kinds missing) [stall #2]
15:59:27 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing: 1 minecraft:deepslate_tile_stairs) [stall #3]
16:00:23 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing: 1 minecraft:deepslate_tile_stairs) [stall #4]
16:00:27 Builder stalled 30 s: aliveworkplace:tinkers_workshop at 1031, 80, 1026 (stage LANDSCAPE, status WORKING, 656 placed, 0 skipped, 0 kinds missing) [stall #5]
16:00:59 Builder stalled 30 s: aliveworkplace:tinkers_workshop at 1031, 80, 1026 (stage LANDSCAPE, status WORKING, 669 placed, 0 skipped, 0 kinds missing) [stall #6]
16:01:55 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing: 1 minecraft:deepslate_tile_stairs) [stall #7]
16:03:44 Builder stalled 30 s: aliveworkplace:stone_house at 1031, 83, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 365 placed, 48 skipped, 1 kinds missing: 1 minecraft:deepslate_tile_stairs) [stall #8]
16:05:36 Builder stalled 30 s: aliveworkplace:flower_shop at 1049, 82, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 159 placed, 6 skipped, 0 kinds missing) [stall #9]
16:05:47 Builder stalled 30 s: aliveworkplace:flower_shop at 1049, 82, 1000 (stage STRUCTURE, status WAITING_FOR_MATERIALS, 171 placed, 6 skipped, 0 kinds missing) [stall #10]
16:07:09 Sprint completed with 206 ticks per second, or 4.84 ms per tick
```

```
Soak result: 21/22 builds finished in 144199 ticks (6.0 days); 10 stalls; items off: none; unfinished: flower_shop
```

Triage (each became a Bug: B21 waiting with nothing missing, B22 stone house stairs, B23 terrain stages on slopes):
- library (stall #2) and flower_shop (#9, #10): WAITING_FOR_MATERIALS with 0 kinds missing; flower_shop is still
  unfinished after 6 days.
- stone_house (#3, #4, #7, #8): waits for 1 deepslate_tile_stairs although its chests held exactly its start plan's
  materials and the ledger shows nothing lost: its plan at build time needs one more than the start-time list.
- graveyard (#1): 30 s with 0 placed at the start of FOUNDATION on a slope (every run so far); tinkers_workshop (#5,
  #6): LANDSCAPE crawls (13 blocks in 30 s).
- Days: about 6 in-game days for 21 of the 22 builds (builders work ~7,000 of every 24,000 ticks). The item asks for
  2 days; whether the bar stays at 2 is the owner's call.
