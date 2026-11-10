# Update rehearsal: 0.153.0 to main 899349b2d (with Create)

Lane E QA, 2026-11-10 (lane-e-1110-1613): the Cobbleverse pack with the owner's Create port (`libs/create-fabric-6.0.10.0+mc1.21.1.jar`, Fabric Loader 0.19.3, "create 6.0.10.0+mc1.21.1 integration on" in all three server logs), 1 in-game day each half, Gradle daemons stopped first. Run: `OLD=0.153.0 LOADER=0.19.3 DAYS=1 JAR=build/new-main-lane-e-1613.jar REPORT=docs/owner/rehearsal-0.153.0-to-main-create.md tools/packtest/rehearsal.sh`, the old jar built by hand on JDK 25 in the v0.153.0 worktree (that tag already calls `Rehearsal.init`, so the script's added line must not be added twice). The new jar is main at 899349b2d: everything unreleased since 0.153.0 (Milestone 46's job crafting lists and the Glassblower, the Mover's 45.4 to 45.6 and the day's fixes).

**Verdict:** passed, nothing lost. One thing to watch: the class seeding window's tick p95 was 48.2 ms (p50 14.2 ms), just under the 50 ms target and about twice the 24.7 ms of the 0.152.0 to 0.153.0 rehearsal this morning; `/tick query` once seeded p95 22.7 ms (7.2 ms then); the one-day sprints are equal (3.05 ms a tick with the new jar, 3.02 ms with the old jar's control). Not profiled, so not attributed; one run only.


A world made with **0.153.0** (the last release before 1.8) on the Cobbleverse pack, saved, then opened with **new-main-lane-e-1613.jar** with every expansion switched on, as 1.8 will ship, and run for 1 in-game days (`tools/packtest/rehearsal.sh`, ROADMAP 34.23).

## Per village

| Village | Villagers (seeded / before / after) | Classes seeded | Jobs kept | Treasury takings a day, the same days (old jar / 1.8) | Rank (before / after) | Containers equal |
|---|---|---|---|---|---|---|
| Hamlet | 6 / 6 / 6 | 6 peasant | 4 of 4 | 0.78 / 0.78 emeralds | hamlet / hamlet | yes (10 containers, 659 items) |
| Town | 22 / 22 / 22 | 22 peasant | 14 of 14 | 4.54 / 4.54 emeralds | town / town | yes (11 containers, 619 items) |
| City | 36 / 36 / 36 | 36 peasant | 26 of 26 | 9.10 / 9.10 emeralds | city / city | yes (16 containers, 735 items) |

Mail: 1 parcel(s) before (in_transit), 1 after (in_transit). Build sites: structure before, structure after.

## Tick times while the classes were seeded

`Rehearsal seeding: every hall seeded after 780 ticks; tick p50 14.2 ms, p95 48.2 ms, worst 669.8 ms` (target: p95 under 50 ms, no tick over 50 ms of ours).

`/tick query` once seeded: P50: 9.7ms P95: 22.7ms P99: 31.0ms, sample: 100

## Log audit

`tools/modtest/logaudit.py` over the three runs' server logs, exit code 0:

```
# Log audit: 3 files
- build/rehearsal/old-server.log
- build/rehearsal/new-server.log
- build/rehearsal/control-server.log

## error (27 lines, 9 distinct)

- 3x `[17:50:18] [main/ERROR]: Registry 'minecraft:wolf_sound_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'minecraft:cow_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'minecraft:chicken_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'minecraft:pig_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'vanillabackport:wolf_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'vanillabackport:frog_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'vanillabackport:cat_variant' was empty after loading`
- 3x `[17:50:18] [main/ERROR]: Registry 'vanillabackport:sulfur_cube_archetypes' was empty after loading`
- 3x `[17:50:43] [main/ERROR]: Unable to apply dex addition cobblemon:mega_z as the sub-dex cobblemon:mega_z does not exist`

## perf (6 lines, 3 distinct)

- 4x `[17:51:37] [Server thread/WARN]: Can't keep up! Is the server overloaded? Running 23208ms or 464 ticks behind`
- 1x `[17:50:18] [Mappings Cache Thread #1/INFO]: Verified cache of yarn mappings (took 1475ms)`
- 1x `[17:50:18] [Mappings Cache Thread #1/INFO]: Cached mappings have been built (took 1851ms)`

## warn (271 lines, 76 distinct)

- 45x `[17:49:45] [main/WARN]: Error loading class: net/minecraft/class_881 (java.lang.ClassNotFoundException: net/minecraft/class_881)`
- 9x `[17:52:00] [Server thread/WARN]: Skipping Entity with id cobblemon_pokemon_entity_placeholder`
- 3x `[17:49:41] [ForkJoinPool-1-worker-1/WARN]: Mod com_github_rtyley_animated-gif-lib-for-java uses the version animated-gif-lib-1.7 which isn't compatible with Loader's extended semantic version format (Could not parse version number component 'animated'!), SemVer is recommended for reliably evaluating`
- 3x `[17:49:42] [ForkJoinPool-1-worker-1/WARN]: Mod nochatrestrictions uses the version Fabric-MC1.21.11-v1.0.0 which isn't compatible with Loader's extended semantic version format (Could not parse version number component 'Fabric'!), SemVer is recommended for reliably evaluating dependencies and priori`
- 3x `[17:49:42] [ForkJoinPool-1-worker-2/WARN]: The mod "accessories_compat_layer" contains invalid entries in its mod json:`
    - Unsupported root entry "#COMMENT_breaks" at line 53 column 20
- 3x `[17:49:42] [ForkJoinPool-1-worker-2/WARN]: Mod legendarymonuments uses the version Cobbleverse which isn't compatible with Loader's extended semantic version format (Could not parse version number component 'Cobbleverse'!), SemVer is recommended for reliably evaluating dependencies and prioritizing `
- 3x `[17:49:44] [main/WARN]: Option 'mixin.bugfix.paper_chunk_patches' overriden (by mods [c2me]) to 'false'`
- 3x `[17:49:44] [main/WARN]: Option 'mixin.perf.cache_strongholds' overriden (by mods [c2me]) to 'false'`
- 3x `[17:49:44] [main/WARN]: Option 'mixin.bugfix.chunk_deadlock' overriden (by mods [c2me]) to 'false'`
- 3x `[17:49:45] [main/WARN]: Error loading class: ca/spottedleaf/starlight/common/integration/v0/ChunkSystemHooks (java.lang.ClassNotFoundException: ca/spottedleaf/starlight/common/integration/v0/ChunkSystemHooks)`
- 3x `[17:49:45] [main/WARN]: Error loading class: juuxel/adorn/block/variant/BlockVariantSets (java.lang.ClassNotFoundException: juuxel/adorn/block/variant/BlockVariantSets)`
- 3x `[17:49:46] [main/WARN]: Error loading class: net/minecraft/class_1088$class_7778 (java.lang.ClassNotFoundException: net/minecraft/class_1088$class_7778)`
- 3x `[17:49:46] [main/WARN]: Error loading class: jeresources/api/util/BiomeHelper (java.lang.ClassNotFoundException: jeresources/api/util/BiomeHelper)`
- 3x `[17:49:46] [main/WARN]: Error loading class: yalter/mousetweaks/Main (java.lang.ClassNotFoundException: yalter/mousetweaks/Main)`
```

## Verdict

**Passed.**

## Limits

- The old jar is 0.153.0 built from its tag with one file added (`command/Rehearsal.java`, the seeding and these reports, benchmark-only): the published jar has no way to seed three villages.
- The villages are staged by a command: beds in the open, no houses, so every household is seeded a Peasant; a real server's better homes give Artisans and Burghers. Each worker has a workstation; the store's food isn't eaten, so wellbeing is the same middling value with and without 1.8.
- Takings are compared with the same saved world kept on the old jar for the same days (the control), because they move from day to day by themselves.
- The worst tick while seeding is not attributed (no profile): the seeding window is the server's first seconds after start, when the pack is still settling, so it may be the pack's or ours. A profile of that window would tell.

## Each village

### Hamlet

- Jobs before: 2 farmer, 1 fisherman, 1 shepherd.
- Jobs lost: on opening none; by the end none.
- Treasury: 20.88 emeralds before (total taken 0.88), 21.66 after (total 1.66); the old jar's first day took 0.88. Wellbeing 0.60 before, 0.47 on the old jar's last day, 0.47 on 1.8's (fed 2 / 0 / 0 of 6 adults). Research levels 0 / 0.
- Couples (villagers with a partner): 2 before, 2 after; children 0 / 0.

| Container | Before (saved by the old jar) | As the new jar loaded it | After the days (workers use their stock) |
|---|---|---|---|
| 1000,100,1006 | diamond 12, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 12, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 12, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 |
| 1003,100,1006 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 |
| 1044,-28,976 | fairy_feather 1, fighting_gem 2, flying_gem 1, level_ball 2, lure_ball 3, bone 8, cobweb 6, golden_apple 1, gunpowder 7, poisonous_potato 1, rotten_flesh 6, string 3 | fairy_feather 1, fighting_gem 2, flying_gem 1, level_ball 2, lure_ball 3, bone 8, cobweb 6, golden_apple 1, gunpowder 7, poisonous_potato 1, rotten_flesh 6, string 3 | fairy_feather 1, fighting_gem 2, flying_gem 1, level_ball 2, lure_ball 3, bone 8, cobweb 6, golden_apple 1, gunpowder 7, poisonous_potato 1, rotten_flesh 6, string 3 |
| 1044,-28,980 | citrine_ball 3, cleanse_tag 1, flying_gem 1, ground_gem 1, ice_gem 1, love_ball 2, net_ball 2, cobweb 6, enchanted_book 1, gunpowder 4, lead 1, poisonous_potato 2, rotten_flesh 13 | citrine_ball 3, cleanse_tag 1, flying_gem 1, ground_gem 1, ice_gem 1, love_ball 2, net_ball 2, cobweb 6, enchanted_book 1, gunpowder 4, lead 1, poisonous_potato 2, rotten_flesh 13 | citrine_ball 3, cleanse_tag 1, flying_gem 1, ground_gem 1, ice_gem 1, love_ball 2, net_ball 2, cobweb 6, enchanted_book 1, gunpowder 4, lead 1, poisonous_potato 2, rotten_flesh 13 |
| 969,-46,1038 | heal_ball 2, ice_gem 1, metal_coat 1, moon_ball 3, premier_ball 2, sachet 1, bone 7, gunpowder 6, lead 1, name_tag 1, raw_iron 4, saddle 1, string 9 | heal_ball 2, ice_gem 1, metal_coat 1, moon_ball 3, premier_ball 2, sachet 1, bone 7, gunpowder 6, lead 1, name_tag 1, raw_iron 4, saddle 1, string 9 | heal_ball 2, ice_gem 1, metal_coat 1, moon_ball 3, premier_ball 2, sachet 1, bone 7, gunpowder 6, lead 1, name_tag 1, raw_iron 4, saddle 1, string 9 |
| 972,-23,989 | fire_gem 2, roseate_ball 3, coal 7, cobweb 2, golden_apple 1, raw_gold 3, rotten_flesh 12, string 7 | fire_gem 2, roseate_ball 3, coal 7, cobweb 2, golden_apple 1, raw_gold 3, rotten_flesh 12, string 7 | fire_gem 2, roseate_ball 3, coal 7, cobweb 2, golden_apple 1, raw_gold 3, rotten_flesh 12, string 7 |
| 973,-23,986 | dragon_gem 1, electric_gem 1, fairy_gem 1, metal_coat 1, relic_coin 3, vivichoke_seeds 2, bone 9, coal 6, glow_berries 1, lead 1, raw_iron 2, rotten_flesh 7, string 7 | dragon_gem 1, electric_gem 1, fairy_gem 1, metal_coat 1, relic_coin 3, vivichoke_seeds 2, bone 9, coal 6, glow_berries 1, lead 1, raw_iron 2, rotten_flesh 7, string 7 | dragon_gem 1, electric_gem 1, fairy_gem 1, metal_coat 1, relic_coin 3, vivichoke_seeds 2, bone 9, coal 6, glow_berries 1, lead 1, raw_iron 2, rotten_flesh 7, string 7 |
| 984,-26,995 | black_apricorn_seed 1, dusk_ball 2, fairy_gem 1, level_ball 2, relic_coin 2, steel_gem 1, verdant_ball 3, bone 9, bucket 1, cobweb 2, enchanted_book 1, glow_berries 1, music_disc_11 1, rotten_flesh 7 | black_apricorn_seed 1, dusk_ball 2, fairy_gem 1, level_ball 2, relic_coin 2, steel_gem 1, verdant_ball 3, bone 9, bucket 1, cobweb 2, enchanted_book 1, glow_berries 1, music_disc_11 1, rotten_flesh 7 | black_apricorn_seed 1, dusk_ball 2, fairy_gem 1, level_ball 2, relic_coin 2, steel_gem 1, verdant_ball 3, bone 9, bucket 1, cobweb 2, enchanted_book 1, glow_berries 1, music_disc_11 1, rotten_flesh 7 |
| 986,100,1016 |  |  |  |
| 997,100,1006 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 |

### Town

- Jobs before: 1 armorer, 1 builder, 1 cartographer, 1 cleric, 3 farmer, 1 fisherman, 1 fletcher, 1 librarian, 1 mason, 1 scholar, 1 shepherd, 1 toolsmith.
- Jobs lost: on opening none; by the end none.
- Treasury: 24.70 emeralds before (total taken 4.70), 29.24 after (total 9.24); the old jar's first day took 4.70. Wellbeing 0.62 before, 0.58 on the old jar's last day, 0.58 on 1.8's (fed 2 / 0 / 0 of 20 adults). Research levels 4 / 4.
- Couples (villagers with a partner): 6 before, 6 after; children 2 / 2.

| Container | Before (saved by the old jar) | As the new jar loaded it | After the days (workers use their stock) |
|---|---|---|---|
| 1284,-50,970 | air_balloon 1, peat_block 1, red_apricorn_seed 1, relic_coin 4, slate_ball 3, gunpowder 26, music_disc_11 2, raw_gold 3, rotten_flesh 2, saddle 1 | air_balloon 1, peat_block 1, red_apricorn_seed 1, relic_coin 4, slate_ball 3, gunpowder 26, music_disc_11 2, raw_gold 3, rotten_flesh 2, saddle 1 | air_balloon 1, peat_block 1, red_apricorn_seed 1, relic_coin 4, slate_ball 3, gunpowder 26, music_disc_11 2, raw_gold 3, rotten_flesh 2, saddle 1 |
| 1286,-50,967 | cleanse_tag 1, safari_ball 2, slate_ball 3, sport_ball 2, water_gem 1, bucket 1, cobweb 6, music_disc_stal 1, name_tag 1, rotten_flesh 11, string 8 | cleanse_tag 1, safari_ball 2, slate_ball 3, sport_ball 2, water_gem 1, bucket 1, cobweb 6, music_disc_stal 1, name_tag 1, rotten_flesh 11, string 8 | cleanse_tag 1, safari_ball 2, slate_ball 3, sport_ball 2, water_gem 1, bucket 1, cobweb 6, music_disc_stal 1, name_tag 1, rotten_flesh 11, string 8 |
| 1295,100,1016 |  |  |  |
| 1297,100,1006 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 |
| 1298,100,1016 |  |  |  |
| 1300,100,1006 | diamond 13, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 13, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 13, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 |
| 1303,100,1006 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 |
| 1307,100,1016 |  |  |  |
| 1322,-48,961 | black_apricorn_seed 1, grass_gem 1, icy_rock 1, red_apricorn_seed 1, relic_coin 3, amethyst_shard 6, copper_ingot 2, gunpowder 2, music_disc_stal 1, rotten_flesh 6, string 4 | black_apricorn_seed 1, grass_gem 1, icy_rock 1, red_apricorn_seed 1, relic_coin 3, amethyst_shard 6, copper_ingot 2, gunpowder 2, music_disc_stal 1, rotten_flesh 6, string 4 | black_apricorn_seed 1, grass_gem 1, icy_rock 1, red_apricorn_seed 1, relic_coin 3, amethyst_shard 6, copper_ingot 2, gunpowder 2, music_disc_stal 1, rotten_flesh 6, string 4 |
| 1328,-48,959 | eject_button 1, never_melt_ice 1, premier_ball 2, rock_gem 1, slate_ball 2, bone 7, coal 7, enchanted_golden_apple 1, lead 1, name_tag 1, raw_gold 2, raw_iron 3, rotten_flesh 8, string 8 | eject_button 1, never_melt_ice 1, premier_ball 2, rock_gem 1, slate_ball 2, bone 7, coal 7, enchanted_golden_apple 1, lead 1, name_tag 1, raw_gold 2, raw_iron 3, rotten_flesh 8, string 8 | eject_button 1, never_melt_ice 1, premier_ball 2, rock_gem 1, slate_ball 2, bone 7, coal 7, enchanted_golden_apple 1, lead 1, name_tag 1, raw_gold 2, raw_iron 3, rotten_flesh 8, string 8 |
| 1332,-39,997 | electric_gem 1, fairy_feather 1, icy_rock 1, psychic_gem 1, slate_ball 3, bone 6, cobweb 3, glow_berries 3, golden_apple 1, gunpowder 9, name_tag 1, raw_iron 1, rotten_flesh 5 | electric_gem 1, fairy_feather 1, icy_rock 1, psychic_gem 1, slate_ball 3, bone 6, cobweb 3, glow_berries 3, golden_apple 1, gunpowder 9, name_tag 1, raw_iron 1, rotten_flesh 5 | electric_gem 1, fairy_feather 1, icy_rock 1, psychic_gem 1, slate_ball 3, bone 6, cobweb 3, glow_berries 3, golden_apple 1, gunpowder 9, name_tag 1, raw_iron 1, rotten_flesh 5 |

### City

- Jobs before: 1 armorer, 1 ball_smith, 1 builder, 1 butcher, 1 chef, 1 cleric, 3 farmer, 1 fossil_scientist, 1 innkeeper, 1 jeweller, 1 librarian, 1 netherworker, 1 nurse, 1 pokemon_trader, 1 postman, 1 printer, 1 rancher, 1 scholar, 1 shopkeeper, 1 teacher, 1 tinkerer, 1 trainer_leader, 1 tutor, 1 undertaker.
- Jobs lost: on opening none; by the end none.
- Treasury: 29.78 emeralds before (total taken 9.78), 38.88 after (total 18.88); the old jar's first day took 9.78. Wellbeing 0.57 before, 0.50 on the old jar's last day, 0.50 on 1.8's (fed 6 / 0 / 0 of 32 adults). Research levels 7 / 7.
- Couples (villagers with a partner): 4 before, 6 after; children 4 / 4.
- Daycare boarders: 2 before, 2 after. Fossils reviving (ticks left): [108846] before, [97085] after. Builders on a site: 1 before, 1 after.

| Container | Before (saved by the old jar) | As the new jar loaded it | After the days (workers use their stock) |
|---|---|---|---|
| 1554,8,1024 | azure_ball 2, premier_ball 5, clock 1, flint 2, flint_and_steel 1, golden_horse_armor 1, golden_pickaxe 1, golden_sword 1, iron_nugget 16, obsidian 2 | azure_ball 2, premier_ball 5, clock 1, flint 2, flint_and_steel 1, golden_horse_armor 1, golden_pickaxe 1, golden_sword 1, iron_nugget 16, obsidian 2 | azure_ball 2, premier_ball 5, clock 1, flint 2, flint_and_steel 1, golden_horse_armor 1, golden_pickaxe 1, golden_sword 1, iron_nugget 16, obsidian 2 |
| 1583,100,1016 |  |  |  |
| 1589,100,1016 |  |  |  |
| 1589,100,1019 |  |  |  |
| 1592,100,1019 |  |  |  |
| 1595,100,1016 |  |  |  |
| 1597,100,1006 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 | baked_potato 32, bread 64, carrot 48, cooked_cod 16 |
| 1598,100,1016 |  |  |  |
| 1600,100,1006 | diamond 14, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 14, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 | diamond 14, emerald 40, enchanted_book 1, gold_ingot 20, name_tag 3 |
| 1603,100,1006 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 | cobblestone 64, glass_pane 32, oak_planks 64, torch 32 |
| 1607,100,1019 |  |  |  |
| 1610,100,1016 |  |  |  |
| 1610,100,1019 |  |  |  |
| 1612,100,986 | cobblestone 128, oak_log 16, oak_planks 64, stone_bricks 64, water_bucket 1 | cobblestone 128, oak_log 16, oak_planks 64, stone_bricks 64, water_bucket 1 | cobblestone 128, oak_log 16, oak_planks 64, stone_bricks 64, water_bucket 1 |
| 1613,100,1019 |  |  |  |
| 1619,100,1016 |  |  |  |

