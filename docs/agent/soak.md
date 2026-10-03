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
