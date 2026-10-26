package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 36.20, village-wide work reach. A spot is in reach when it lies in the village the worker's block stands in, wherever
 * in it, or within the job's long reach of the block outside it (128 by default, a config key each). Each job is tried
 * through the player's own entry point (a right-click on the worker with the placed Blueprint, the Quarry Marker or the
 * Field Marker): a spot far across a big village (hall radius 160, the spot 150 off) is taken, one 100 blocks outside
 * the village is taken, one 140 out is refused with the sentence naming the village; with no hall only the long reach
 * counts (the old sentence); the reach set low (16) and high (256). The builder's far site survives a save and reload;
 * the Steward plans a plot 90 blocks from the table inside the village; the config keys clamp and an old file's 48 moves.
 */
public class FarSiteGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	/** One job's way in: hand it a spot {@code east} blocks east of its block; true if it was taken (and forgets it). */
	private interface Job {
		boolean offer(int east);
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/**
	 * The whole rule for one job: far across a big village, 100 outside it, 140 out refused (naming the village), no hall,
	 * the reach low and high. {@code reach} reads and sets the job's long reach; {@code tail} is the refusal's ending.
	 */
	private static void wholeRule(GameTestHelper helper, Job job, List<Component> seen, IntSupplier reach, IntConsumer setReach, String tail) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		String village = VillageHalls.name(level, hall).getString();
		helper.assertTrue(reach.getAsInt() == 128, "the long reach isn't 128 but " + reach.getAsInt());

		// a big village: anywhere in it, whatever the distance
		VillageHalls.RADIUS = 160;
		VillageHalls.forgetNearest();
		seen.clear();
		helper.assertTrue(job.offer(150), "a spot 150 blocks off, inside a village of radius 160, was refused: " + texts(seen));

		// a small village: outside it, the long reach
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		seen.clear();
		helper.assertTrue(job.offer(100), "a spot 100 blocks off, outside the village, was refused: " + texts(seen));
		seen.clear();
		helper.assertTrue(!job.offer(140), "a spot 140 blocks off, outside the village, was taken");
		helper.assertTrue(texts(seen).stream().anyMatch(l -> l.startsWith("That ") && l.contains(" is outside " + village + " and ") && l.endsWith(tail + " — keep it within 128.")),
			"the refusal doesn't name the village and the reach: " + texts(seen));

		// no hall: the worker's block reaches only so far
		helper.setBlock(HALL, Blocks.AIR);
		VillageHalls.forgetNearest();
		helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(WORK)).isEmpty(), "a hall is left near the test");
		seen.clear();
		helper.assertTrue(!job.offer(140), "a spot 140 blocks off, with no village, was taken");
		helper.assertTrue(texts(seen).stream().anyMatch(l -> l.startsWith("That ") && !l.contains("outside") && l.endsWith(tail + " — keep it within 128.")),
			"with no village the refusal isn't the plain one: " + texts(seen));
		seen.clear();
		helper.assertTrue(job.offer(100), "a spot 100 blocks off, with no village, was refused: " + texts(seen));

		// the config key low and high
		int was = reach.getAsInt();
		Leftovers.after(helper, () -> setReach.accept(was));
		setReach.accept(16);
		seen.clear();
		helper.assertTrue(!job.offer(20), "a spot 20 blocks off was taken with the reach at 16");
		helper.assertTrue(texts(seen).stream().anyMatch(l -> l.endsWith("keep it within 16.")), "the low reach isn't named: " + texts(seen));
		setReach.accept(256);
		seen.clear();
		helper.assertTrue(job.offer(200), "a spot 200 blocks off was refused with the reach at 256: " + texts(seen));
		setReach.accept(was);
	}

	// --- the builder ---------------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '200' '"farSiteBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "farSiteBuilder")
	public void theBuilderBuildsAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(WORK);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("far"));
		ServerPlayer p = c.player();
		Job job = east -> {
			p.setItemInHand(InteractionHand.MAIN_HAND, QaBuilderLimitsGameTests.placedHut(level, bench, new Vec3i(east, 0, 0)));
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
			BuildSite site = Builders.activeSite(level, builder);
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			return site != null;
		};
		wholeRule(helper, job, c.seen(), () -> Builders.MAX_SITE_DISTANCE, r -> Builders.MAX_SITE_DISTANCE = r, "from the builder's Blueprint Table");
		helper.succeed();
	}

	/** A build far across a big village is still the builder's after a save and reload, and he keeps it while he works. */
	//$ gametest_ticks_batch AREA '200' '"farSiteReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "farSiteReload")
	public void aFarBuildInTheVillageSurvivesASaveAndReload(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		VillageHalls.RADIUS = 160;
		VillageHalls.forgetNearest();
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(WORK);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("farR"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaBuilderLimitsGameTests.placedHut(level, bench, new Vec3i(150, 0, 0)));
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "the far build in the village was refused: " + texts(c.seen()));
		var placement = site.placement();

		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(builder.save(tag), "the builder wasn't saved");
		builder.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load");
		level.addFreshEntity(back);
		Villager again = (Villager) back;
		forgetSitesOf(helper, again);
		helper.runAfterDelay(40, () -> {
			BuildSite after = Builders.activeSite(level, again);
			helper.assertTrue(after != null && after.placement().equals(placement), "the far build was lost after a reload: " + after);
			helper.succeed();
		});
	}

	// --- the miner -----------------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '200' '"farSiteMiner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "farSiteMiner")
	public void theMinerDigsAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.MINERS_BENCH);
		Villager miner = helper.spawn(EntityType.VILLAGER, AT);
		miner.setNoAi(true);
		BlockPos bench = helper.absolutePos(WORK);
		Jobs.employ(level, miner, bench, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			QuarrySite left = Miners.activeSite(level, miner);
			if (left != null) {
				Miners.cancel(level, left);
			}
		});
		Job job = east -> {
			// a 5 × 5 quarry centred on the spot
			BlockPos at = bench.east(east);
			ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
			marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2)), 4));
			p.setItemInHand(InteractionHand.MAIN_HAND, marker);
			QaStageSixGameTests.clickVillager(p, miner, seen);
			QuarrySite site = Miners.activeSite(level, miner);
			if (site != null) {
				Miners.cancel(level, site);
			}
			return site != null;
		};
		wholeRule(helper, job, seen, () -> Miners.MAX_DISTANCE, r -> Miners.MAX_DISTANCE = r, "the miner works at");
		helper.succeed();
	}

	// --- the farmer ----------------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '200' '"farSiteFarmer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "farSiteFarmer")
	public void theFarmerFarmsAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, AT);
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, helper.absolutePos(WORK), PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> ModAttachments.FARM_FIELD.remove(farmer));
		Job job = east -> fieldJob(helper, p, farmer, seen, east, () -> Fields.hasField(farmer), () -> ModAttachments.FARM_FIELD.remove(farmer));
		wholeRule(helper, job, seen, () -> Fields.MAX_DISTANCE, r -> Fields.MAX_DISTANCE = r, "the farmer works at");
		helper.succeed();
	}

	// --- the lumberjack (the area jobs: tree farm, orchard, berry plot) ------------------------------------------------

	//$ gametest_ticks_batch AREA '200' '"farSiteLumberjack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "farSiteLumberjack")
	public void theLumberjackPlantsAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.CHOPPING_BLOCK);
		Villager lumberjack = helper.spawn(EntityType.VILLAGER, AT);
		lumberjack.setNoAi(true);
		Jobs.employ(level, lumberjack, helper.absolutePos(WORK), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> ModAttachments.TREE_FARM.remove(lumberjack));
		Job job = east -> fieldJob(helper, p, lumberjack, seen, east, () -> TreeFarms.farm(lumberjack) != null, () -> ModAttachments.TREE_FARM.remove(lumberjack));
		wholeRule(helper, job, seen, () -> AreaJobs.TREE_FARM_REACH, r -> AreaJobs.TREE_FARM_REACH = r, "the lumberjack works at");
		helper.succeed();
	}

	/** A 5 × 5 Field Marker area centred {@code east} blocks east of the worker's block, handed over with a right-click. */
	private static boolean fieldJob(GameTestHelper helper, ServerPlayer p, Villager worker, List<Component> seen, int east,
									java.util.function.BooleanSupplier taken, Runnable forget) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(WORK).east(east);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, worker, seen);
		boolean ok = taken.getAsBoolean();
		forget.run();
		return ok;
	}

	/** The orchard keeper's and berry breeder's reaches are their own keys, 128 by default. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyReachIsAConfigKeyWithItsRange(GameTestHelper helper) {
		WorkplaceConfig defaults = WorkplaceConfig.parse("{\"configVersion\": 5}");
		helper.assertTrue(defaults.maxSiteDistance == 128 && defaults.quarryReach == 128 && defaults.fieldReach == 128 && defaults.treeFarmReach == 128
			&& defaults.orchardAreaReach == 128 && defaults.berryPlotReach == 128, "a reach doesn't default to 128");
		String[] keys = {"maxSiteDistance", "quarryReach", "fieldReach", "treeFarmReach", "orchardAreaReach", "berryPlotReach"};
		StringBuilder low = new StringBuilder("{\"configVersion\": 5");
		StringBuilder high = new StringBuilder("{\"configVersion\": 5");
		for (String key : keys) {
			low.append(", \"").append(key).append("\": 1");
			high.append(", \"").append(key).append("\": 999");
		}
		WorkplaceConfig l = WorkplaceConfig.parse(low + "}");
		WorkplaceConfig h = WorkplaceConfig.parse(high + "}");
		helper.assertTrue(l.maxSiteDistance == 16 && l.quarryReach == 16 && l.fieldReach == 16 && l.treeFarmReach == 16 && l.orchardAreaReach == 16 && l.berryPlotReach == 16,
			"a reach isn't clamped up to 16");
		helper.assertTrue(h.maxSiteDistance == 256 && h.quarryReach == 256 && h.fieldReach == 256 && h.treeFarmReach == 256 && h.orchardAreaReach == 256 && h.berryPlotReach == 256,
			"a reach isn't clamped down to 256");
		for (String key : keys) {
			helper.assertTrue(WorkplaceConfig.RANGES.get(key).min() == 16 && WorkplaceConfig.RANGES.get(key).max() == 256, key + "'s range isn't 16 to 256");
		}
		// a file from before 36.20 holding the old default 48 moves to 128; a choice of 100, or 48 in a new file, stays
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 4, \"maxSiteDistance\": 48}").maxSiteDistance == 128, "an old file's 48 didn't move to 128");
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 4, \"maxSiteDistance\": 100}").maxSiteDistance == 100, "a choice of 100 was lost");
		helper.assertTrue(WorkplaceConfig.parse("{\"configVersion\": 5, \"maxSiteDistance\": 48}").maxSiteDistance == 48, "a choice of 48 was lost");
		// the orchard's and berry plot's keys are the ones AreaJobs reads
		int orchard = AreaJobs.ORCHARD_REACH;
		int berry = AreaJobs.BERRY_PLOT_REACH;
		AreaJobs.ORCHARD_REACH = 20;
		AreaJobs.BERRY_PLOT_REACH = 30;
		try {
			helper.assertTrue(AreaJobs.reach("orchard_area") == 20 && AreaJobs.reach("berry_plot") == 30 && AreaJobs.reach("tree_farm") == AreaJobs.TREE_FARM_REACH,
				"an area job reads another's reach");
		} finally {
			AreaJobs.ORCHARD_REACH = orchard;
			AreaJobs.BERRY_PLOT_REACH = berry;
		}
		helper.succeed();
	}

	// --- the Steward ---------------------------------------------------------------------------------------------------

	/**
	 * The Steward plans across the whole village: with the builder's outside reach at the old 48, a Well 90 blocks from the
	 * only Blueprint Table fits when the table stands in the village (hall radius 128), and is refused (no table in reach)
	 * when the village is too small to hold the table.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:huge_area"' '40' '"farSiteSteward"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 40, batch = "farSiteSteward")
	public void theStewardPlansAPlotNinetyBlocksFromTheTableInTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(2, 2, 14);
		BlockPos spot = new BlockPos(11, 2, 13);
		int radius = VillageHalls.RADIUS;
		int reach = Builders.MAX_SITE_DISTANCE;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Builders.MAX_SITE_DISTANCE = reach;
			VillageHalls.forgetNearest();
		});
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		for (BuildSite site : new ArrayList<>(BuildSiteManager.get(level).all())) {
			if (site.placement().origin().closerThan(a, 200)) {
				BuildSiteManager.get(level).remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			if (f.placement().origin().closerThan(a, 200)) {
				BuildSiteManager.get(level).forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		// no other table near: only the one 90 blocks off
		level.getPoiManager().findAll(t -> t.is(ModVillagers.BLUEPRINT_TABLE_POI) || t.is(ModVillagers.BUILDERS_BENCH_POI), t -> true, hall, 250, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(t -> level.setBlockAndUpdate(t, Blocks.AIR.defaultBlockState()));
		BlockPos table = hall.west(80);
		// a hall an earlier batch left nearer the table would make it that village's
		level.getPoiManager().findAll(t -> t.is(ModVillagers.VILLAGE_HALL_POI), t -> !t.equals(hall), table, 200, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(t -> level.setBlockAndUpdate(t, Blocks.AIR.defaultBlockState()));
		level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		Leftovers.after(helper, () -> level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState()));
		Builders.MAX_SITE_DISTANCE = 48;

		VillageHalls.RADIUS = 128;
		VillageHalls.forgetNearest();
		ResourceLocation well = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("well");
		CityPlan plan = homes(helper, hall);
		Plots.Verdict in = Plots.check(level, hall, plan, "homes", well, helper.absolutePos(spot), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(in.plot().isPresent(), "the Well 90 blocks from the table in the village didn't fit: " + in.reason());
		double off = Math.sqrt(helper.absolutePos(spot).distSqr(table));
		helper.assertTrue(off > 85 && off < 95, "the Well is " + off + " blocks from the table, not about 90");
		Plots.Search search = Plots.search(level, hall, plan, new Plots.Request(List.of(well), "homes"));
		search.finish();
		helper.assertTrue(search.result().isPresent(), "the Steward's search found no plot in the village");

		VillageHalls.RADIUS = 64; // the table 80 from the hall is now outside the village: only the 48 reach
		VillageHalls.forgetNearest();
		plan = homes(helper, hall);
		Plots.Verdict out = Plots.check(level, hall, plan, "homes", well, helper.absolutePos(spot), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(out.reason().equals(Optional.of(Plots.Reason.NO_TABLE)), "the Well 90 blocks from a table outside the village: " + out);
		helper.succeed();
	}

	/** A Homes zone over helper x 6..29, z 2..25 (cells of the hall's grid at the current radius). */
	private static CityPlan homes(GameTestHelper helper, BlockPos hall) {
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				int cell = CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z)));
				if (cell >= 0) {
					cells.set(cell);
				}
			}
		}
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
	}

	private static void forgetSitesOf(GameTestHelper helper, Villager builder) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
	}
}
