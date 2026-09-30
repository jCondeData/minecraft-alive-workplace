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
	private record House(net.minecraft.world.level.block.Block block, VillagerProfession job) {
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
			java.util.Map.entry("fossil_lab", new House(ModBlocks.TRAINING_POST, ModVillagers.FOSSIL_SCIENTIST)),
			java.util.Map.entry("flower_shop", new House(Blocks.COMPOSTER, ModVillagers.FLORIST)),
			java.util.Map.entry("ranch_house", new House(Blocks.SMOKER, ModVillagers.RANCHER)),
			java.util.Map.entry("schoolhouse", new House(Blocks.LECTERN, ModVillagers.TEACHER)),
			java.util.Map.entry("inn_room", new House(ModBlocks.SHOP_COUNTER, ModVillagers.INNKEEPER)),
			java.util.Map.entry("mortuary", new House(Blocks.BREWING_STAND, ModVillagers.UNDERTAKER)),
			java.util.Map.entry("tinkers_shop", new House(Blocks.SMITHING_TABLE, ModVillagers.TINKERER)),
			java.util.Map.entry("sifting_shed", new House(Blocks.CAULDRON, ModVillagers.SIFTER)),
			java.util.Map.entry("compost_yard", new House(Blocks.COMPOSTER, ModVillagers.COMPOSTER)));
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
				helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), house.getValue().block()).size() == 1,
					id + " should have one " + house.getValue().block().getName().getString());
				// Exactly one job block, so its villager isn't joined by another for a second block's vanilla job.
				long jobSites = jobSites(template);
				helper.assertTrue(jobSites == 1, id + " has " + jobSites + " job blocks");
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
		Leftovers.clear(helper); // (a jobless villager from a neighbouring test can take the bench first)
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(VillageHouses.workshop("plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 9));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER,
			"villager is still " + villager.getVillagerData().getProfession()));
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
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setFinalizeEntities(true), net.minecraft.util.RandomSource.create(1), 2);
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
}
