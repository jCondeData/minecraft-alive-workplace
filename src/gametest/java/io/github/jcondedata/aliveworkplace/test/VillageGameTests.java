package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Our houses in villages. */
public class VillageGameTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyVillageTypeCanGrowABuildersWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			helper.assertTrue(pool != null, "no village house pool for " + style);
			String workshop = VillageHouses.workshop(style).toString();
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().contains(workshop));
			helper.assertTrue(listed, style + " villages can't grow a builder's workshop");

			StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop(style))
				.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("missing " + workshop));
			List<StructureTemplate.StructureBlockInfo> benches = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.BLUEPRINT_TABLE);
			helper.assertTrue(benches.size() == 1, workshop + " should have one Blueprint Table");
			List<StructureTemplate.StructureBlockInfo> jigsaws = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
			boolean entrance = jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("name").equals("minecraft:building_entrance"));
			boolean villager = jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("pool").equals("aliveworkplace:village/" + style + "/workers"));
			helper.assertTrue(entrance && villager, workshop + " needs a street connection and a villager spawn");
			// Only jobless adults move in (the village's own pool can give a nitwit, a baby or, with CobbleDollars, a merchant).
			StructureTemplatePool workers = pools.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/" + style + "/workers"));
			helper.assertTrue(workers != null, "no worker pool for " + style);
			var elements = ((StructureTemplatePoolAccessor) workers).aliveworkplace$rawTemplates();
			helper.assertTrue(elements.size() == 1 && elements.get(0).getFirst().toString().contains("minecraft:village/" + style + "/villagers/unemployed"),
				style + " worker pool: " + elements);
			helper.assertTrue(level.getStructureManager().get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/" + style + "/villagers/unemployed")).isPresent(),
				"no vanilla unemployed villager template for " + style);
			boolean loot = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).stream()
				.map(StructureTemplate.StructureBlockInfo::nbt)
				.anyMatch(n -> n != null && n.getString("LootTable").equals("aliveworkplace:chests/village_builders_workshop"));
			helper.assertTrue(loot, workshop + " should have a supply chest");
		}
		helper.succeed();
	}

	/** A house, its job block, and the job its villager comes with (null: a jobless one, who takes the block's own job). */
	/** A house's job block (or, for another mod's block, its id: {@code block} is null) and its villager's job. */
	private record House(net.minecraft.world.level.block.Block block, VillagerProfession job, net.minecraft.resources.ResourceLocation modBlock) {
		House(net.minecraft.world.level.block.Block block, VillagerProfession job) {
			this(block, job, null);
		}
	}

	/**
	 * Trainer's houses, guard houses, clinics and post offices...: in every village type's pool, each with its one job
	 * block (the one its job works at since ROADMAP 21.1a) and its villager: a jobless one from the worker pool where the
	 * block's own job is the house's, else one who already has the house's job (a composter would make a farmer).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyVillageTypeCanGrowTheOtherHouses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		java.util.Map<String, House> houses = java.util.Map.ofEntries(
			java.util.Map.entry("trainers_house", new House(ModBlocks.TRAINING_POST, null)),
			java.util.Map.entry("guard_house", new House(Blocks.GRINDSTONE, ModVillagers.GUARD)),
			java.util.Map.entry("clinic", new House(Blocks.BREWING_STAND, ModVillagers.NURSE)),
			java.util.Map.entry("post_office", new House(ModBlocks.MAILBOX, ModVillagers.POSTMAN)),
			java.util.Map.entry("leaders_hall", new House(ModBlocks.TRAINING_POST, ModVillagers.TRAINER_LEADER)),
			java.util.Map.entry("school", new House(ModBlocks.TRAINING_POST, ModVillagers.TUTOR)),
			java.util.Map.entry("trade_hall", new House(ModBlocks.SHOP_COUNTER, ModVillagers.POKEMON_TRADER)),
			java.util.Map.entry("orchard_house", new House(Blocks.COMPOSTER, ModVillagers.ORCHARD_KEEPER)),
			java.util.Map.entry("ball_workshop", new House(Blocks.SMITHING_TABLE, ModVillagers.BALL_SMITH)),
			java.util.Map.entry("ferry_house", new House(ModBlocks.TRAVEL_POST, null)),
			java.util.Map.entry("storehouse", new House(ModBlocks.STOREHOUSE, null)),
			java.util.Map.entry("carpenters_workshop", new House(Blocks.CRAFTING_TABLE, ModVillagers.CARPENTER)),
			java.util.Map.entry("kitchen", new House(Blocks.SMOKER, ModVillagers.CHEF)),
			// Cobblemon's Fossil Analyzer (ROADMAP 21.1c); the house only grows with Cobblemon, so here it's checked by name
			java.util.Map.entry("fossil_lab", new House(null, ModVillagers.FOSSIL_SCIENTIST, ModVillagers.FOSSIL_ANALYZER_BLOCK)),
			java.util.Map.entry("flower_shop", new House(Blocks.COMPOSTER, ModVillagers.FLORIST)),
			java.util.Map.entry("ranch_house", new House(Blocks.SMOKER, ModVillagers.RANCHER)),
			java.util.Map.entry("schoolhouse", new House(Blocks.LECTERN, ModVillagers.TEACHER)),
			java.util.Map.entry("inn_room", new House(ModBlocks.SHOP_COUNTER, ModVillagers.INNKEEPER)),
			java.util.Map.entry("mortuary", new House(Blocks.BREWING_STAND, ModVillagers.UNDERTAKER)),
			java.util.Map.entry("tinkers_shop", new House(Blocks.SMITHING_TABLE, ModVillagers.TINKERER)),
			java.util.Map.entry("sifting_shed", new House(Blocks.CAULDRON, ModVillagers.SIFTER)),
			java.util.Map.entry("compost_yard", new House(Blocks.COMPOSTER, ModVillagers.COMPOSTER)),
			// The luxury jobs' houses (ROADMAP 34.13): a cauldron would make a leatherworker and a loom a shepherd
			java.util.Map.entry("winery", new House(Blocks.CAULDRON, ModVillagers.VINTNER)),
			java.util.Map.entry("tailors_shop", new House(Blocks.LOOM, ModVillagers.TAILOR)),
			// ROADMAP 34.14: a cartography table would make a cartographer and a stonecutter a mason
			java.util.Map.entry("print_shop", new House(Blocks.CARTOGRAPHY_TABLE, ModVillagers.PRINTER)),
			java.util.Map.entry("jewellers_workshop", new House(Blocks.STONECUTTER, ModVillagers.JEWELLER)),
			// The Pokémon jobs' houses (ROADMAP 28.15): only with Cobblemon, so Cobblemon's blocks are checked by name
			java.util.Map.entry("pokemon_center", new House(null, ModVillagers.NURSE, ModVillagers.HEALING_MACHINE_BLOCK)),
			java.util.Map.entry("camp_kitchen", new House(null, ModVillagers.CAMP_COOK, ModVillagers.CAMPFIRE_POT_BLOCK)),
			java.util.Map.entry("berry_nursery", new House(Blocks.COMPOSTER, ModVillagers.BERRY_BREEDER)),
			java.util.Map.entry("daycare", new House(null, ModVillagers.DAYCARE_KEEPER, ModVillagers.PASTURE_BLOCK)),
			java.util.Map.entry("gem_grotto", new House(Blocks.STONECUTTER, ModVillagers.GEM_GROWER)));
		// No Cobblemon here: the Pokémon houses stay out of the pools.
		helper.assertTrue(VillageHouses.houseNames().equals(List.of("guard_house", "clinic", "post_office", "orchard_house", "ferry_house", "storehouse", "carpenters_workshop", "kitchen",
				"flower_shop", "ranch_house", "schoolhouse", "inn_room", "mortuary", "tinkers_shop", "sifting_shed", "compost_yard", "winery", "tailors_shop",
				"print_shop", "jewellers_workshop")),
			"houses without Cobblemon: " + VillageHouses.houseNames());
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			for (var house : houses.entrySet()) {
				var id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/" + style + "_" + house.getKey());
				boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
					.anyMatch(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id.toString()) + "(?![a-z_0-9]).*"));
				helper.assertTrue(listed == VillageHouses.houseNames().contains(house.getKey()),
					style + " villages " + (listed ? "grow" : "can't grow") + " a " + house.getKey());
				StructureTemplate template = level.getStructureManager().get(id)
					.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("missing " + id));
				var modBlock = house.getValue().modBlock();
				if (modBlock == null) {
					helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), house.getValue().block()).size() == 1,
						id + " should have one " + house.getValue().block().getName().getString());
				} else {
					long found = named(level, id, modBlock);
					long halves = modBlock.equals(ModVillagers.PASTURE_BLOCK) ? 2 : 1; // the Pasture Block is two blocks tall
					helper.assertTrue(found == halves, id + " should have one " + modBlock + ", not " + found + " blocks of it");
				}
				// Exactly one job block, so its villager isn't joined by another for a second block's vanilla job (another
				// mod's block is no job block while that mod is missing, as here).
				long jobSites = jobSites(template);
				long expected = modBlock == null || net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(modBlock) ? 1 : 0;
				helper.assertTrue(jobSites == expected, id + " has " + jobSites + " job blocks");
				net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
				net.minecraft.nbt.ListTag entities = saved.getList("entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
				boolean spawn = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW).stream()
					.anyMatch(j -> j.nbt() != null && j.nbt().getString("pool").equals("aliveworkplace:village/" + style + "/workers"));
				if (house.getValue().job() == null) {
					helper.assertTrue(spawn && entities.isEmpty(), id + " should spawn a jobless villager from the worker pool");
				} else {
					String job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(house.getValue().job()).toString();
					helper.assertTrue(!spawn && entities.size() == 1 && entities.getCompound(0).getCompound("nbt").getCompound("VillagerData")
						.getString("profession").equals(job), id + " should come with a " + job + ": " + entities);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * B69: no village house (every style, the workshop and every house, the Cobblemon ones too) nor any of the 12 buildable
	 * workplace copies has a trapdoor in its front walk row (z = 1, the row along the front wall, at a walker's feet or
	 * head: y = 0 and 1). Mob pathfinding takes any trapdoor for open ground, so a villager heading for the door along
	 * the wall walked into the old top-half trapdoor flower boxes beside the steps and stayed stuck. Read from the files,
	 * so a block of a mod that isn't installed can't hide one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noVillageHouseHasATrapdoorInItsFrontWalkRow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var resources = level.getServer().getResourceManager();
		List<net.minecraft.resources.ResourceLocation> files = new java.util.ArrayList<>(resources
			.listResources("structure/village", r -> r.getPath().endsWith(".nbt")).keySet().stream()
			.filter(r -> r.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID)).sorted().toList());
		helper.assertTrue(files.size() >= 115, "only " + files.size() + " village templates found");
		for (String workplace : List.of("builders_workshop", "carpenters_workshop", "kitchen", "post_office", "guard_house", "clinic",
				"ferry_house", "trainers_house", "leaders_hall", "ball_workshop", "trade_hall", "school")) {
			files.add(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("structure/" + workplace + ".nbt"));
		}
		List<String> problems = new java.util.ArrayList<>();
		for (var file : files) {
			CompoundTag tag;
			try (var in = resources.getResourceOrThrow(file).open()) {
				tag = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			} catch (java.io.IOException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("can't read " + file + ": " + e);
			}
			net.minecraft.nbt.ListTag palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
			net.minecraft.nbt.ListTag blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag block = blocks.getCompound(i);
				net.minecraft.nbt.ListTag pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
				String name = palette.getCompound(block.getInt("state")).getString("Name");
				if (pos.getInt(2) == 1 && pos.getInt(1) <= 1 && name.endsWith("_trapdoor")) {
					problems.add(file.getPath() + " " + name + " at " + pos);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " trapdoors in front walk rows: " + problems);
		helper.succeed();
	}

	/**
	 * How many blocks named {@code block} the template {@code id} has, read from its file: a block of a mod that isn't
	 * installed loads as air, so the loaded template no longer knows its name.
	 */
	private static long named(ServerLevel level, net.minecraft.resources.ResourceLocation id, net.minecraft.resources.ResourceLocation block) {
		var file = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
		net.minecraft.nbt.CompoundTag tag;
		try (var in = level.getServer().getResourceManager().getResourceOrThrow(file).open()) {
			tag = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
		} catch (java.io.IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("can't read " + file + ": " + e);
		}
		net.minecraft.nbt.ListTag palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
		net.minecraft.nbt.ListTag blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
		long count = 0;
		for (int i = 0; i < blocks.size(); i++) {
			if (palette.getCompound(blocks.getCompound(i).getInt("state")).getString("Name").equals(block.toString())) {
				count++;
			}
		}
		return count;
	}

	/** Job-site blocks (ours and vanilla's: any job's workstation) in a template. */
	private static long jobSites(StructureTemplate template) {
		net.minecraft.nbt.CompoundTag tag = template.save(new net.minecraft.nbt.CompoundTag());
		net.minecraft.nbt.ListTag palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
		java.util.List<net.minecraft.world.level.block.state.BlockState> states = new java.util.ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
		}
		long count = 0;
		net.minecraft.nbt.ListTag blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			var state = states.get(blocks.getCompound(i).getInt("state"));
			if (net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state).filter(h -> net.minecraft.core.registries.BuiltInRegistries
				.VILLAGER_PROFESSION.stream().anyMatch(p -> p != VillagerProfession.NONE && p.heldJobSite().test(h))).isPresent()) {
				count++;
			}
		}
		return count;
	}

	/** A jobless villager inside a freshly generated workshop takes the bench and becomes a builder. */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '2400' '"aVillagerMovesIntoTheWorkshop"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 2400, batch = "aVillagerMovesIntoTheWorkshop")
	public void aVillagerMovesIntoTheWorkshop(GameTestHelper helper) {
		workshopTest(helper, new BlockPos(7, 2, 9), true);
	}

	/**
	 * B4: a jobless villager standing outside the workshop, by its side wall two blocks from the Blueprint Table, walks
	 * round to the door and takes it. It once never did, 30 times out of 30: the house's structure_void row in front of
	 * the door was placed as a hole in the ground (B5), and the path round to the door went through it.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '2400' '"aVillagerOutsideTheWorkshopFindsItsTable"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 2400, batch = "aVillagerOutsideTheWorkshopFindsItsTable")
	public void aVillagerOutsideTheWorkshopFindsItsTable(GameTestHelper helper) {
		workshopTest(helper, new BlockPos(4, 2, 10), true);
	}

	/**
	 * B2: villages place the workshop as a legacy pool element, which skips the template's air, so the air cells in its
	 * bottom layer (around the walls, like vanilla's houses) never cut into the ground. Placing the template with its air
	 * cut a ring through the test floor around the house, two blocks deep with the air under the floor; about one
	 * villager in 175 wandered out of the door, dropped into it and walked round it for the rest of the test (a villager
	 * put in the ring stayed there 30 times out of 30). Placed like a village's it still failed 1 in 100: the structure_void
	 * row in front of the door was placed too (B5). With B5 fixed, 100 of 100 in-suite repeats passed (2026-10-02), and
	 * 100 of 100 of the villager outside the side wall (B4).
	 */
	static void workshopTest(GameTestHelper helper, BlockPos spawn, boolean likeAVillage) {
		Leftovers.clear(helper); // (a jobless villager from a neighbouring test can take the bench first)
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop("plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		StructurePlaceSettings settings = new StructurePlaceSettings();
		if (likeAVillage) {
			settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		}
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
		Villager villager = helper.spawn(EntityType.VILLAGER, spawn);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.setDayTime(2000);
		BlockPos bench = origin.offset(2, 1, 7);
		// Where they went, every second, and when they first stood lower than the bench (the three failures seen were all
		// under the test floor).
		List<String> trail = new java.util.ArrayList<>();
		String[] firstLow = {"never"};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			BlockPos d = villager.blockPosition().subtract(bench);
			if (d.getY() < 0 && firstLow[0].equals("never")) {
				firstLow[0] = "tick " + ticks[0] + " at bench" + offset(d);
			}
			if (ticks[0] % 20 == 0 && trail.size() < 40) {
				trail.add(offset(d));
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER,
			"villager is still " + villager.getVillagerData().getProfession() + workshopClues(helper, villager, bench)
				+ " | first below the bench: " + firstLow[0] + " | trail: " + String.join(" ", trail)));
	}

	/**
	 * What a jobless villager near the workshop's bench saw (B2: this test failed a few times in full-suite runs and never
	 * alone), so the next failure explains itself: where they are, what they aimed for, whether the bench is still there
	 * and free, and which other villagers and free workstations are within a villager's job search.
	 */
	private static String workshopClues(GameTestHelper helper, Villager villager, BlockPos bench) {
		ServerLevel level = helper.getLevel();
		var poi = level.getPoiManager();
		StringBuilder s = new StringBuilder();
		BlockPos at = villager.blockPosition();
		s.append(" | at bench").append(offset(at.subtract(bench))).append(villager.isAlive() ? "" : " (dead)")
			.append(helper.getBounds().contains(villager.position()) ? " in the area" : " OUTSIDE the area")
			.append(", on ").append(level.getBlockState(at.below()).getBlock().getDescriptionId())
			.append(", in ").append(level.getBlockState(at).getBlock().getDescriptionId());
		s.append(", job site ").append(villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
			.map(g -> "bench" + offset(g.pos().subtract(bench))).orElse("-"));
		s.append(", potential ").append(villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE)
			.map(g -> "bench" + offset(g.pos().subtract(bench))).orElse("-"));
		s.append(" | bench ").append(level.getBlockState(bench).getBlock().getDescriptionId())
			.append(poi.getType(bench).map(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI)).orElse(false) ? " poi" : " NO-POI")
			.append(poi.getCountInRange(h -> true, bench, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE) > 0 ? " free" : " TAKEN");
		s.append(" | villagers near: ");
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48), v -> v != villager)
			.forEach(v -> s.append(v.getVillagerData().getProfession()).append('@').append("bench").append(offset(v.blockPosition().subtract(bench))).append(' '));
		s.append("| free job sites near: ");
		poi.getInRange(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), bench, 48,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
			.forEach(r -> s.append("bench").append(offset(r.getPos().subtract(bench))).append(' '));
		return s.toString();
	}

	private static String offset(BlockPos d) {
		return String.format("%+d,%+d,%+d", d.getX(), d.getY(), d.getZ());
	}

	/**
	 * Bug B5 (tester): every village house template stored structure_void blocks (the workshop's whole front row at its
	 * bottom layer). Villages place houses as legacy pool elements, whose processors ignore only air and structure
	 * blocks, so the structure_void was placed as a real block: a row of holes with no collision in front of the door,
	 * where the ground was. A villager walking out of the workshop fell into it. Placed here with the same processors
	 * as a village (and the jigsaw replacement, whose final state is structure_void too).
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '100' '"aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 100, batch = "aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld")
	public void aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop("plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		StructurePlaceSettings settings = new StructurePlaceSettings()
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR)
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE);
		template.placeInWorld(level, origin, origin, settings, net.minecraft.util.RandomSource.create(1), 2);
		List<String> voids = new java.util.ArrayList<>();
		net.minecraft.core.Vec3i size = template.getSize();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (level.getBlockState(p).is(Blocks.STRUCTURE_VOID)) {
				BlockPos d = p.subtract(origin);
				voids.add(d.getX() + "," + d.getY() + "," + d.getZ());
			}
		}
		helper.assertTrue(voids.isEmpty(), voids.size() + " structure_void blocks placed in the world at template " + voids);
		helper.succeed();
	}
	/**
	 * B5, for every village template (all styles, the workshop and every house, the Cobblemon ones too): placed through the
	 * real pool element our villages use (legacy, empty processor list, jigsaws turned into their final state), a house
	 * puts no structure_void in the world, and the ground strip in front of its door stays the ground it landed on.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '200' '"everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 200, batch = "everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor")
	public void everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var processors = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
		var empty = processors.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PROCESSOR_LIST,
			net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty")));
		List<net.minecraft.resources.ResourceLocation> ids = level.getServer().getResourceManager()
			.listResources("structure/village", r -> r.getPath().endsWith(".nbt")).keySet().stream()
			.filter(r -> r.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID))
			.map(r -> r.withPath(r.getPath().substring("structure/".length(), r.getPath().length() - ".nbt".length())))
			.sorted().toList();
		helper.assertTrue(ids.size() >= 115, "only " + ids.size() + " village templates found");
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
		net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			origin.offset(-1, 0, -1), origin.offset(10, 11, 11));
		List<String> problems = new java.util.ArrayList<>();
		for (var id : ids) {
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
			// The houses whose villager comes with its job bring them along: they don't stay for the next house.
			level.getEntitiesOfClass(Villager.class, net.minecraft.world.phys.AABB.of(box).inflate(2)).forEach(Villager::discard);
			for (int x = 0; x < 9; x++) {
				level.setBlock(origin.offset(x, 0, 0), Blocks.DIRT.defaultBlockState(), 2);
			}
			var element = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.legacy(id.toString(), empty)
				.apply(StructureTemplatePool.Projection.RIGID);
			boolean placed = element.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
				origin, origin, net.minecraft.world.level.block.Rotation.NONE, box, net.minecraft.util.RandomSource.create(1),
				net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING, false);
			if (!placed) {
				problems.add(id + " did not place");
				continue;
			}
			int voids = 0;
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(p).is(Blocks.STRUCTURE_VOID)) {
					voids++;
				}
			}
			int ground = 0;
			for (int x = 0; x < 9; x++) {
				if (level.getBlockState(origin.offset(x, 0, 0)).is(Blocks.DIRT)) {
					ground++;
				}
			}
			boolean houseThere = !level.getBlockState(origin.offset(4, 1, 2)).isAir(); // the door
			if (voids > 0 || ground != 9 || !houseThere) {
				problems.add(id + ": " + voids + " structure_void, " + ground + "/9 ground kept, door " + level.getBlockState(origin.offset(4, 1, 2)));
			}
		}
		level.getEntitiesOfClass(Villager.class, net.minecraft.world.phys.AABB.of(box).inflate(2)).forEach(Villager::discard);
		helper.assertTrue(problems.isEmpty(), problems.size() + " village templates: " + problems);
		helper.succeed();
	}

	/**
	 * Since 21.1a the orchard house has a composter, which a jobless villager would take as a farmer: its villager comes
	 * as an orchard keeper instead and takes the house's composter by themselves.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '1200' '"theOrchardHousesVillagerTakesItsComposter"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 1200, batch = "theOrchardHousesVillagerTakesItsComposter")
	public void theOrchardHousesVillagerTakesItsComposter(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate template = level.getStructureManager().get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/plains_orchard_house"))
			.orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		// Placed as a village places it (B11): the village's pool element skips the template's air. Placed with its air, the
		// air ring around the house at y=0 dug a pit into the test floor, and a keeper who wandered into it before taking
		// the composter could never path back to it (2 in 100 in-suite runs).
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setFinalizeEntities(true)
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR),
			net.minecraft.util.RandomSource.create(1), 2);
		BlockPos composter = template.filterBlocks(origin, new StructurePlaceSettings(), Blocks.COMPOSTER).get(0).pos();
		net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.of(template.getBoundingBox(new StructurePlaceSettings(), origin)).inflate(2);
		helper.succeedWhen(() -> {
			List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
			helper.assertTrue(villagers.size() == 1, villagers.size() + " villagers in the house");
			Villager keeper = villagers.get(0);
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER, "a " + keeper.getVillagerData().getProfession());
			helper.assertTrue(keeper.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
				.map(g -> g.pos().equals(composter)).orElse(false), "works at " + keeper.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE));
		});
	}

	/**
	 * ROADMAP 34.13: the village's Winery comes with its Vintner, who takes the house's cauldron (a jobless villager
	 * would take it as a leatherworker) and keeps the job.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '1200' '"theWinerysVintnerTakesItsVat"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 1200, batch = "theWinerysVintnerTakesItsVat")
	public void theWinerysVintnerTakesItsVat(GameTestHelper helper) {
		theHousesWorkerTakesItsBlock(helper, "winery", ModVillagers.VINTNER, Blocks.CAULDRON);
	}

	/** ROADMAP 34.13: the village's Tailor's Shop comes with its Tailor, who takes the house's loom (a jobless villager would take it as a shepherd). */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '1200' '"theTailorsShopsTailorTakesItsLoom"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 1200, batch = "theTailorsShopsTailorTakesItsLoom")
	public void theTailorsShopsTailorTakesItsLoom(GameTestHelper helper) {
		theHousesWorkerTakesItsBlock(helper, "tailors_shop", ModVillagers.TAILOR, Blocks.LOOM);
	}

	/** ROADMAP 34.14: the village's Print Shop comes with its Printer, who takes the house's cartography table (a jobless villager would take it as a cartographer). */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '1200' '"thePrintShopsPrinterTakesItsPress"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 1200, batch = "thePrintShopsPrinterTakesItsPress")
	public void thePrintShopsPrinterTakesItsPress(GameTestHelper helper) {
		theHousesWorkerTakesItsBlock(helper, "print_shop", ModVillagers.PRINTER, Blocks.CARTOGRAPHY_TABLE);
	}

	/** ROADMAP 34.14: the village's Jeweller's Workshop comes with its Jeweller, who takes the house's stonecutter (a jobless villager would take it as a mason). */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '1200' '"theJewellersWorkshopsJewellerTakesItsBench"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 1200, batch = "theJewellersWorkshopsJewellerTakesItsBench")
	public void theJewellersWorkshopsJewellerTakesItsBench(GameTestHelper helper) {
		theHousesWorkerTakesItsBlock(helper, "jewellers_workshop", ModVillagers.JEWELLER, Blocks.STONECUTTER);
	}

	/**
	 * A village house of every style placed as a village places it (its pool element skips the template's air, B11): its
	 * one villager, already in {@code job}, takes the house's {@code block} as his job site and still has the job then.
	 */
	private static void theHousesWorkerTakesItsBlock(GameTestHelper helper, String house, VillagerProfession job, net.minecraft.world.level.block.Block block) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		// Two styles side by side (plains, with its gable roof, and the flat-roofed desert one): the room inside is the same in all five.
		List<String> styles = List.of("plains", "desert");
		List<BlockPos> blocks = new java.util.ArrayList<>();
		List<net.minecraft.world.phys.AABB> areas = new java.util.ArrayList<>();
		for (int i = 0; i < styles.size(); i++) {
			var id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("village/" + styles.get(i) + "_" + house);
			StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("missing " + id));
			BlockPos origin = helper.absolutePos(new BlockPos(1 + 11 * i, 1, 4));
			template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setFinalizeEntities(true)
				.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR),
				net.minecraft.util.RandomSource.create(1), 2);
			blocks.add(template.filterBlocks(origin, new StructurePlaceSettings(), block).get(0).pos());
			areas.add(net.minecraft.world.phys.AABB.of(template.getBoundingBox(new StructurePlaceSettings(), origin)).inflate(1));
		}
		helper.succeedWhen(() -> {
			for (int i = 0; i < styles.size(); i++) {
				List<Villager> villagers = level.getEntitiesOfClass(Villager.class, areas.get(i));
				helper.assertTrue(villagers.size() == 1, villagers.size() + " villagers in the " + styles.get(i) + " " + house);
				Villager worker = villagers.get(0);
				helper.assertTrue(worker.getVillagerData().getProfession() == job, "the " + styles.get(i) + " " + house + "'s villager is a " + worker.getVillagerData().getProfession());
				BlockPos at = blocks.get(i);
				helper.assertTrue(worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().equals(at)).orElse(false), "the " + styles.get(i) + " " + house + "'s villager works at "
					+ worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE) + ", not its " + block.getName().getString() + " at " + at);
			}
		});
	}

	/**
	 * Bug B6: vanilla's villager templates put their villager at x+0.72, z+0.63 of its block, a block above the floor.
	 * In desert_small_house_7 the villager's spot is a 1-wide corridor with a step beside it and top slabs above, so the
	 * villager landed on the step with its head in the ceiling and suffocated (3 desert villagers in every showcase
	 * village run). The house and its villager piece are placed as the village jigsaw places them (the villager piece's
	 * jigsaw on top of the house's, at (5,1,3)); the villager must end up standing on the corridor floor, not in a wall.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '200' '"aVillagerFromAVanillaDesertHouseCorridorStandsOnTheFloor"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 200, batch = "aVillagerFromAVanillaDesertHouseCorridorStandsOnTheFloor")
	public void aVillagerFromAVanillaDesertHouseCorridorStandsOnTheFloor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		var processors = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
		var empty = processors.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PROCESSOR_LIST,
			net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty")));
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
		net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			origin.offset(-1, 0, -1), origin.offset(9, 6, 8));
		var house = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
			.legacy("minecraft:village/desert/houses/desert_small_house_7", empty).apply(StructureTemplatePool.Projection.RIGID);
		helper.assertTrue(house.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, net.minecraft.world.level.block.Rotation.NONE, box, net.minecraft.util.RandomSource.create(1),
			net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING, false), "the house did not place");
		var villagerPiece = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
			.legacy("minecraft:village/desert/villagers/unemployed", empty).apply(StructureTemplatePool.Projection.RIGID);
		BlockPos spot = origin.offset(5, 1, 3);
		helper.assertTrue(villagerPiece.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			spot, spot, net.minecraft.world.level.block.Rotation.NONE, box, net.minecraft.util.RandomSource.create(1),
			net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING, false), "the villager piece did not place");
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, net.minecraft.world.phys.AABB.of(box));
		helper.assertTrue(villagers.size() == 1, villagers.size() + " villagers placed, expected the house's one");
		Villager villager = villagers.get(0);
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(villager.isAlive(), "the villager died");
			helper.assertFalse(villager.isInWall(), String.format("the villager is stuck in a wall at %.2f %.2f %.2f",
				villager.getX() - origin.getX(), villager.getY() - origin.getY(), villager.getZ() - origin.getZ()));
			helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager was hurt: " + villager.getHealth());
			helper.assertTrue(villager.getBlockY() == origin.getY() + 1, String.format("the villager is at house y %.2f, not on the corridor floor (1)",
				villager.getY() - origin.getY()));
			helper.succeed();
		});
	}
}
