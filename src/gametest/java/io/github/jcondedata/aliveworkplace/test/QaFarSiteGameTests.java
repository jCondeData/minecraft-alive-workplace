package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane E, 2026-10-27) for 36.20, village-wide work reach, written from the item's text: "a spot is in reach when it
 * is inside the village's claimed area (the City Plan zones and the hall's radius) ... or within a long reach of the
 * worker's block outside it ... 128"; "every reach becomes a config key"; "the refusal sentences name the new rule";
 * "old sites keep working". On top of FarSiteGameTests: the orchard keeper through his own Field Marker click, the 128
 * boundary from both sides for each job with the number the sentence gives, a spot inside another village (not ours),
 * a City Plan zone carrying the village past the hall's radius, the config file putting every reach into effect, and
 * a far field across a big village surviving the farmer's save and reload.
 */
public class QaFarSiteGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		int site = Builders.MAX_SITE_DISTANCE;
		int quarry = Miners.MAX_DISTANCE;
		int field = Fields.MAX_DISTANCE;
		int tree = AreaJobs.TREE_FARM_REACH;
		int orchard = AreaJobs.ORCHARD_REACH;
		int berry = AreaJobs.BERRY_PLOT_REACH;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			Builders.MAX_SITE_DISTANCE = site;
			Miners.MAX_DISTANCE = quarry;
			Fields.MAX_DISTANCE = field;
			AreaJobs.TREE_FARM_REACH = tree;
			AreaJobs.ORCHARD_REACH = orchard;
			AreaJobs.BERRY_PLOT_REACH = berry;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
	}

	private static void radius(int r) {
		VillageHalls.RADIUS = r;
		VillageHalls.forgetNearest();
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static Villager worker(GameTestHelper helper, Block block, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
								   VillagerProfession job) {
		helper.setBlock(WORK, block);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(WORK), poi, job);
		return v;
	}

	/** A 5 × 5 Field Marker area centred on {@code centre} (absolute), handed over with a right-click; true if taken. */
	private static boolean field(GameTestHelper helper, ServerPlayer p, Villager worker, List<Component> seen, BlockPos centre,
								 BooleanSupplier taken, Runnable forget) {
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(centre.offset(-2, 0, -2)), Optional.of(centre.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		seen.clear();
		QaStageSixGameTests.clickVillager(p, worker, seen);
		boolean ok = taken.getAsBoolean();
		forget.run();
		return ok;
	}

	private static boolean quarry(GameTestHelper helper, ServerPlayer p, Villager miner, List<Component> seen, BlockPos centre) {
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(centre.offset(-2, 0, -2)), Optional.of(centre.offset(2, 0, 2)), 4));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		seen.clear();
		QaStageSixGameTests.clickVillager(p, miner, seen);
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		return site != null;
	}

	private static String refusal(List<Component> seen) {
		return texts(seen).stream().filter(l -> l.contains("keep it within")).findFirst().orElse("(none: " + texts(seen) + ")");
	}

	// --- the orchard keeper, through his own click ------------------------------------------------------------------------

	/** The orchard keeper's Field Marker follows the same rule, and his refusal names the village, the basket and 128. */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteOrchard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteOrchard")
	public void theOrchardKeeperTakesAnOrchardAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager keeper = worker(helper, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> ModAttachments.ORCHARD.remove(keeper));
		BlockPos work = helper.absolutePos(WORK);
		String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
		String basket = ModBlocks.FRUIT_BASKET.getName().getString();
		Runnable forget = () -> ModAttachments.ORCHARD.remove(keeper);
		BooleanSupplier taken = () -> Orchards.orchard(keeper) != null;
		helper.assertTrue(AreaJobs.ORCHARD_REACH == 128, "the orchard keeper's reach isn't 128 but " + AreaJobs.ORCHARD_REACH);

		radius(160);
		helper.assertTrue(field(helper, p, keeper, seen, work.east(150), taken, forget), "an orchard 150 off in a village of radius 160 was refused: " + texts(seen));
		radius(16);
		helper.assertTrue(field(helper, p, keeper, seen, work.east(100), taken, forget), "an orchard 100 outside the village was refused: " + texts(seen));
		helper.assertTrue(!field(helper, p, keeper, seen, work.east(140), taken, forget), "an orchard 140 outside the village was taken");
		String want = "That spot is outside " + village + " and 140 blocks from the " + basket + " the orchard keeper works at — keep it within 128.";
		helper.assertTrue(refusal(seen).equals(want), "the orchard keeper's refusal reads '" + refusal(seen) + "', not '" + want + "'");
		AreaJobs.ORCHARD_REACH = 16;
		helper.assertTrue(!field(helper, p, keeper, seen, work.east(20), taken, forget), "an orchard 20 out was taken with orchardAreaReach 16");
		helper.assertTrue(refusal(seen).endsWith("keep it within 16."), "the low reach isn't named: " + refusal(seen));
		helper.succeed();
	}

	// --- the 128 boundary, both sides ------------------------------------------------------------------------------------

	/**
	 * Outside the village, exactly 128 blocks is in reach and 129 is not, for the miner, the farmer and the lumberjack;
	 * the refusal gives the real distance (129) and the right noun ("That field" for the farmer, "That spot" else).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteBoundary"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteBoundary")
	public void outsideTheVillage128IsInReachAnd129IsNot(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(16);
		String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
		BlockPos work = helper.absolutePos(WORK);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);

		// the miner
		Villager miner = worker(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		Leftovers.after(helper, () -> {
			QuarrySite left = Miners.activeSite(level, miner);
			if (left != null) {
				Miners.cancel(level, left);
			}
		});
		String bench = level.getBlockState(work).getBlock().getName().getString();
		helper.assertTrue(quarry(helper, p, miner, seen, work.east(128)), "a quarry 128 out was refused: " + texts(seen));
		helper.assertTrue(!quarry(helper, p, miner, seen, work.east(129)), "a quarry 129 out was taken");
		String want = "That spot is outside " + village + " and 129 blocks from the " + bench + " the miner works at — keep it within 128.";
		helper.assertTrue(refusal(seen).equals(want), "the miner's refusal reads '" + refusal(seen) + "', not '" + want + "'");
		miner.discard();

		// the farmer
		Villager farmer = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		Runnable forgetField = () -> ModAttachments.FARM_FIELD.remove(farmer);
		Leftovers.after(helper, forgetField);
		helper.assertTrue(field(helper, p, farmer, seen, work.east(128), () -> Fields.hasField(farmer), forgetField), "a field 128 out was refused: " + texts(seen));
		helper.assertTrue(!field(helper, p, farmer, seen, work.north(129), () -> Fields.hasField(farmer), forgetField), "a field 129 out was taken");
		want = "That field is outside " + village + " and 129 blocks from the Composter the farmer works at — keep it within 128.";
		helper.assertTrue(refusal(seen).equals(want), "the farmer's refusal reads '" + refusal(seen) + "', not '" + want + "'");
		farmer.discard();

		// the lumberjack
		Villager lumberjack = worker(helper, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		Runnable forgetFarm = () -> ModAttachments.TREE_FARM.remove(lumberjack);
		Leftovers.after(helper, forgetFarm);
		String block = ModBlocks.CHOPPING_BLOCK.getName().getString();
		helper.assertTrue(field(helper, p, lumberjack, seen, work.west(128), () -> TreeFarms.farm(lumberjack) != null, forgetFarm), "a tree farm 128 out was refused: " + texts(seen));
		helper.assertTrue(!field(helper, p, lumberjack, seen, work.south(129), () -> TreeFarms.farm(lumberjack) != null, forgetFarm), "a tree farm 129 out was taken");
		want = "That spot is outside " + village + " and 129 blocks from the " + block + " the lumberjack works at — keep it within 128.";
		helper.assertTrue(refusal(seen).equals(want), "the lumberjack's refusal reads '" + refusal(seen) + "', not '" + want + "'");
		helper.succeed();
	}

	// --- whose village ----------------------------------------------------------------------------------------------------

	/**
	 * A spot inside another village 200 blocks off is not in reach: "the village" is the one the worker's block stands
	 * in, and the refusal names ours, not theirs.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteOtherVillage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteOtherVillage")
	public void aSpotInsideAnotherVillageIsNotInOurVillagesReach(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(64);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos other = work.east(200);
		level.setBlockAndUpdate(other, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Leftovers.after(helper, () -> level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState()));
		VillageHalls.forgetNearest();
		helper.assertTrue(VillageHalls.nearest(level, other).filter(other::equals).isPresent(), "the other village's hall isn't a hall");
		String ours = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
		String theirs = VillageHalls.name(level, other).getString();
		helper.assertTrue(!ours.equals(theirs), "the two villages have the same name: " + ours);

		Villager farmer = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		Runnable forget = () -> ModAttachments.FARM_FIELD.remove(farmer);
		Leftovers.after(helper, forget);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		helper.assertTrue(!field(helper, p, farmer, seen, other.south(10), () -> Fields.hasField(farmer), forget),
			"a field inside " + theirs + ", 200 blocks from our farmer's composter, was taken");
		helper.assertTrue(refusal(seen).startsWith("That field is outside " + ours + " and "), "the refusal doesn't name our village " + ours + ": " + refusal(seen));
		helper.assertTrue(!refusal(seen).contains(theirs), "the refusal names the other village: " + refusal(seen));
		helper.succeed();
	}

	// --- the City Plan's zones are the village too ------------------------------------------------------------------------

	/**
	 * A zone painted in the City Plan's corner, past the hall's radius, is the village: a field there is taken even with the
	 * long reach at 16; the same spot in the unpainted corner across is refused.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteZone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteZone")
	public void aZonePaintedPastTheHallsRadiusIsInTheVillage(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(64);
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "no hall block entity");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Leftovers.after(helper, () -> entity.setPlan(CityPlan.EMPTY));
		BlockPos corner = hall.offset(58, 0, 58); // about 82 from the hall: past the radius of 64, inside the grid (half 64)
		BlockPos across = hall.offset(58, 0, -58);
		BitSet cells = new BitSet();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int cell = CityPlan.cellAt(hall, corner.offset(dx, 0, dz));
				helper.assertTrue(cell >= 0, "the corner isn't on the City Plan's grid");
				cells.set(cell);
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));

		Villager farmer = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		Runnable forget = () -> ModAttachments.FARM_FIELD.remove(farmer);
		Leftovers.after(helper, forget);
		Fields.MAX_DISTANCE = 16;
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		helper.assertTrue(field(helper, p, farmer, seen, corner, () -> Fields.hasField(farmer), forget),
			"a field in a zone painted past the hall's radius was refused: " + texts(seen));
		helper.assertTrue(!field(helper, p, farmer, seen, across, () -> Fields.hasField(farmer), forget),
			"a field in the unpainted corner, past the radius and the reach of 16, was taken");
		helper.assertTrue(refusal(seen).endsWith("keep it within 16."), "the refusal doesn't give the reach of 16: " + refusal(seen));
		helper.succeed();
	}

	// --- the config file --------------------------------------------------------------------------------------------------

	/** Each reach read from the config file is the one its job uses once the config is applied (and the defaults come back). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFarSiteConfig"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFarSiteConfig")
	public void theConfigFilePutsEachReachIntoEffect(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"configVersion\": 5, \"maxSiteDistance\": 31, \"quarryReach\": 42, \"fieldReach\": 53, \"treeFarmReach\": 64,"
				+ " \"orchardAreaReach\": 75, \"berryPlotReach\": 86}").apply();
			helper.assertTrue(Builders.MAX_SITE_DISTANCE == 31, "maxSiteDistance 31 gave the builder " + Builders.MAX_SITE_DISTANCE);
			helper.assertTrue(Miners.MAX_DISTANCE == 42, "quarryReach 42 gave the miner " + Miners.MAX_DISTANCE);
			helper.assertTrue(Fields.MAX_DISTANCE == 53, "fieldReach 53 gave the farmer " + Fields.MAX_DISTANCE);
			helper.assertTrue(AreaJobs.reach("tree_farm") == 64, "treeFarmReach 64 gave the lumberjack " + AreaJobs.reach("tree_farm"));
			helper.assertTrue(AreaJobs.reach("orchard_area") == 75, "orchardAreaReach 75 gave the orchard keeper " + AreaJobs.reach("orchard_area"));
			helper.assertTrue(AreaJobs.reach("berry_plot") == 86, "berryPlotReach 86 gave the berry breeder " + AreaJobs.reach("berry_plot"));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Builders.MAX_SITE_DISTANCE == 128 && Miners.MAX_DISTANCE == 128 && Fields.MAX_DISTANCE == 128
			&& AreaJobs.TREE_FARM_REACH == 128 && AreaJobs.ORCHARD_REACH == 128 && AreaJobs.BERRY_PLOT_REACH == 128, "the default config doesn't bring every reach back to 128");
		helper.succeed();
	}

	// --- old sites keep working -------------------------------------------------------------------------------------------

	/** A field 150 blocks off across a big village is still the farmer's after he is saved and loaded, and stays his while he works. */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteFieldReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteFieldReload")
	public void aFarFieldInABigVillageSurvivesTheFarmersSaveAndReload(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(160);
		Villager farmer = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(WORK).east(150);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(centre.offset(-2, 0, -2)), Optional.of(centre.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, farmer, seen);
		FieldJob job = ModAttachments.FARM_FIELD.get(farmer);
		helper.assertTrue(job != null, "the far field in the village was refused: " + texts(seen));
		BoundingBox box = job.box();

		CompoundTag tag = new CompoundTag();
		helper.assertTrue(farmer.save(tag), "the farmer wasn't saved");
		farmer.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the farmer didn't load");
		level.addFreshEntity(back);
		Villager again = (Villager) back;
		Leftovers.after(helper, () -> {
			ModAttachments.FARM_FIELD.remove(again);
			again.discard();
		});
		again.setNoAi(false);
		helper.runAfterDelay(100, () -> {
			FieldJob after = ModAttachments.FARM_FIELD.get(again);
			helper.assertTrue(after != null && after.box().equals(box), "the far field was lost after a reload and 100 ticks of work: " + after);
			helper.assertTrue(again.getVillagerData().getProfession() == VillagerProfession.FARMER, "the farmer lost his job after the reload");
			helper.succeed();
		});
	}

	// --- upkeep uses the same rule -----------------------------------------------------------------------------------------

	/**
	 * "Upkeep ... use the same rule": a wall the builder finished 150 blocks from his table, with a plank knocked out, is
	 * not his to repair while the village is small (it lies outside it and past 128), and is once the village (radius 160)
	 * holds it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteUpkeep"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteUpkeep")
	public void aFarBuildingAcrossABigVillageIsRepaired(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		boolean was = io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED;
		boolean asks = io.github.jcondedata.aliveworkplace.build.Upkeep.ASK;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = was;
			io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = asks;
		});
		helper.setBlock(WORK, ModBlocks.BUILDERS_BENCH);
		BlockPos bench = helper.absolutePos(WORK);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		Builders.employ(level, builder, bench);
		BlockPos wallAt = bench.east(150);
		for (int x = 0; x < 4; x++) {
			level.setBlockAndUpdate(wallAt.east(x), Blocks.OAK_PLANKS.defaultBlockState());
		}
		net.minecraft.resources.ResourceLocation wall = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_far_upkeep_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, wallAt, new net.minecraft.core.Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		level.setBlockAndUpdate(wallAt.east(1), Blocks.AIR.defaultBlockState()); // knocked out
		var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), wallAt,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		sites.recordFinished(wall, placement, builder.getUUID());
		Leftovers.after(helper, () -> {
			for (io.github.jcondedata.aliveworkplace.build.BuildSite s : List.copyOf(sites.all())) {
				if (s.placement().equals(placement)) {
					sites.remove(s.id());
				}
			}
			sites.forgetFinished(placement);
			for (int x = 0; x < 4; x++) {
				level.setBlockAndUpdate(wallAt.east(x), Blocks.AIR.defaultBlockState());
			}
		});

		radius(16);
		io.github.jcondedata.aliveworkplace.build.BuildSite none = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
		helper.assertTrue(none == null || !none.placement().equals(placement), "the builder repairs a wall 150 blocks off, outside a village of radius 16");
		if (none != null) {
			sites.remove(none.id());
			ModAttachments.BUILDER_JOB.remove(builder);
		}
		radius(160);
		io.github.jcondedata.aliveworkplace.build.BuildSite repair = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
		helper.assertTrue(repair != null && repair.isRepair() && repair.placement().equals(placement),
			"the builder doesn't repair the wall 150 blocks off inside a village of radius 160: " + (repair == null ? "nothing" : repair.placement()));
		helper.succeed();
	}

	/** A quarry 150 blocks off across a big village is still the miner's after the quarries and the miner are saved and loaded. */
	//$ gametest_ticks_batch AREA '200' '"qaFarSiteQuarryReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteQuarryReload")
	public void aFarQuarryInABigVillageSurvivesASaveAndReload(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(160);
		Villager miner = worker(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos centre = helper.absolutePos(WORK).east(150);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(centre.offset(-2, 0, -2)), Optional.of(centre.offset(2, 0, 2)), 4));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, miner, seen);
		QuarrySite site = Miners.activeSite(level, miner);
		helper.assertTrue(site != null, "the far quarry in the village was refused: " + texts(seen));
		BoundingBox box = site.box();
		java.util.UUID id = site.id();

		io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager quarries = io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(level);
		QuarrySite reread = QuarrySite.load(site.save());
		helper.assertTrue(reread != null, "the quarry didn't read back");
		quarries.remove(id);
		quarries.restore(reread);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(miner.save(tag), "the miner wasn't saved");
		miner.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the miner didn't load");
		level.addFreshEntity(back);
		Villager again = (Villager) back;
		again.setNoAi(true);
		Leftovers.after(helper, () -> {
			QuarrySite left = Miners.activeSite(level, again);
			if (left != null) {
				Miners.cancel(level, left);
			}
			quarries.remove(id);
			again.discard();
		});
		helper.runAfterDelay(40, () -> {
			QuarrySite after = Miners.activeSite(level, again);
			helper.assertTrue(after != null && after.id().equals(id) && after.box().equals(box), "the far quarry was lost after a reload: " + after);
			helper.succeed();
		});
	}
}
