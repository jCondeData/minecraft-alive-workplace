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
			List<StructureTemplate.StructureBlockInfo> benches = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), ModBlocks.BUILDERS_BENCH);
			helper.assertTrue(benches.size() == 1, workshop + " should have one Builder's Bench");
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

	/** Trainer's houses, guard houses, clinics and post offices: in every village type's pool, each with its job block. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyVillageTypeCanGrowTheOtherHouses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		java.util.Map<String, net.minecraft.world.level.block.Block> houses = java.util.Map.ofEntries(
			java.util.Map.entry("trainers_house", ModBlocks.TRAINING_POST),
			java.util.Map.entry("guard_house", ModBlocks.GUARD_POST),
			java.util.Map.entry("clinic", ModBlocks.NURSE_STATION),
			java.util.Map.entry("post_office", ModBlocks.POSTAL_DESK),
			java.util.Map.entry("leaders_hall", ModBlocks.LEADERS_PODIUM),
			java.util.Map.entry("school", ModBlocks.TUTORS_DESK),
			java.util.Map.entry("trade_hall", ModBlocks.TRADE_BOARD),
			java.util.Map.entry("orchard_house", ModBlocks.FRUIT_BASKET),
			java.util.Map.entry("ball_workshop", ModBlocks.BALL_WORKBENCH),
			java.util.Map.entry("ferry_house", ModBlocks.TRAVEL_POST),
			java.util.Map.entry("storehouse", ModBlocks.STOREHOUSE),
			java.util.Map.entry("carpenters_workshop", ModBlocks.CARPENTERS_BENCH),
			java.util.Map.entry("kitchen", ModBlocks.KITCHEN_STOVE),
			java.util.Map.entry("fossil_lab", ModBlocks.FOSSIL_LAB),
			java.util.Map.entry("flower_shop", ModBlocks.FLOWER_STAND),
			java.util.Map.entry("ranch_house", ModBlocks.FEED_TROUGH),
			java.util.Map.entry("schoolhouse", ModBlocks.TEACHERS_DESK),
			java.util.Map.entry("inn_room", ModBlocks.INN_COUNTER),
			java.util.Map.entry("mortuary", ModBlocks.UNDERTAKERS_TABLE),
			java.util.Map.entry("tinkers_shop", ModBlocks.TINKERS_BENCH),
			java.util.Map.entry("sifting_shed", ModBlocks.SIEVE),
			java.util.Map.entry("compost_yard", ModBlocks.COMPOST_BIN));
		// No Cobblemon here: the Pokémon houses stay out of the pools.
		helper.assertTrue(VillageHouses.houseNames().equals(List.of("guard_house", "clinic", "post_office", "orchard_house", "ferry_house", "storehouse", "carpenters_workshop", "kitchen",
				"flower_shop", "ranch_house", "schoolhouse", "inn_room", "mortuary", "tinkers_shop", "sifting_shed", "compost_yard")),
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
				helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), house.getValue()).size() == 1,
					id + " should have one " + house.getValue().getName().getString());
				// Exactly one job block, so the villager who moves in takes ours (a lectern would make a librarian).
				long jobSites = jobSites(template);
				helper.assertTrue(jobSites == 1, id + " has " + jobSites + " job blocks");
			}
		}
		helper.succeed();
	}

	/** Job-site blocks (ours and vanilla's) in a template. */
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
			if (net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
				.filter(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()) {
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
	 * B2: villages place the workshop as a legacy pool element, which skips the template's air, so the air cells in its
	 * bottom layer (around the walls, like vanilla's houses) never cut into the ground. Placing the template with its air
	 * cut a ring through the test floor around the house, two blocks deep with the air under the floor; about one
	 * villager in 175 wandered out of the door, dropped into it and walked round it for the rest of the test (a villager
	 * put in the ring stayed there 30 times out of 30). Placed like a village's it still failed 1 in 100: the structure_void row in front of the door is placed too (B5).
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
			.append(poi.getType(bench).map(h -> h.is(ModVillagers.BUILDERS_BENCH_POI)).orElse(false) ? " poi" : " NO-POI")
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

	/**
	 * Bug (tester, B2): every village house template stores structure_void blocks (the workshop's whole front row at its
	 * bottom layer). Villages place houses as legacy pool elements, whose processors ignore only air and structure
	 * blocks, so the structure_void is placed as a real block: a row of holes with no collision in front of the door,
	 * where the ground was. A villager walking out of the workshop fell into it (b2rep_58: under the bench's front row).
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '100' '"aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 100, batch = "aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld")
	public void aVillagePlacedWorkshopLeavesNoStructureVoidInTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop("plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		StructurePlaceSettings settings = new StructurePlaceSettings()
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		template.placeInWorld(level, origin, origin, settings, net.minecraft.util.RandomSource.create(1), 2);
		List<String> voids = new java.util.ArrayList<>();
		net.minecraft.core.Vec3i size = template.getSize();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) {
				voids.add(offset(p.subtract(origin)));
			}
		}
		helper.assertTrue(voids.isEmpty(), voids.size() + " structure_void blocks placed in the world at template " + voids);
		helper.succeed();
	}

	private static String offset(BlockPos d) {
		return String.format("%+d,%+d,%+d", d.getX(), d.getY(), d.getZ());
	}
}
