package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31) for B665/B668 and 36.20, written from the spec ("up and down never count": every reach is measured
 * across the ground, x and z only, and the refusal names that distance rounded up), for what ReachAcrossGameTests and
 * QaFarReach1028* leave: the farmer's field, the orchard keeper's orchard, the berry breeder's plot and the lumberjack's
 * tree farm well above and below their station; diagonal spots (90/90 = 127.3 across taken, 91/91 = 128.7 refused as
 * 129, and 128/1 = 128.004 refused as 129); and a build and a quarry far above and below the bench. Every boundary is
 * offered from both sides through the player's own right-click with the Field Marker, Quarry Marker or placed Blueprint,
 * with no hall anywhere near (the worker's block reach only), and every refusal is compared in full.
 */
public class QaReachAcross1031GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The station is raised this far above the test area, so spots 40 blocks below it stay well above the world's floor. */
	private static final int RAISE = 60;

	interface Offer {
		/** True if the spot centred {@code off} from the station (x and z; y as given) was taken (then forgotten again). */
		boolean take(Vec3i off);
	}

	record Worker(String name, BlockPos station, Offer offer, IntSupplier reach) {
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		});
		QaFarReach1028bGameTests.noHalls(helper);
	}

	/** The job's station {@link #RAISE} above the test area (stone under it), its worker next to it, no AI. */
	private static Villager station(GameTestHelper helper, Block block, BlockPos[] at) {
		ServerLevel level = helper.getLevel();
		BlockPos station = helper.absolutePos(new BlockPos(3, 2, 3)).above(RAISE);
		at[0] = station;
		level.setBlockAndUpdate(station.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(station, block.defaultBlockState());
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(station.getX() + 1.5, station.getY(), station.getZ() + 0.5);
		v.setNoAi(true);
		level.addFreshEntity(v);
		Leftovers.after(helper, () -> {
			v.discard();
			level.setBlockAndUpdate(station, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(station.below(), Blocks.AIR.defaultBlockState());
		});
		helper.assertTrue(Reach.village(level, station).isEmpty(), "setup: the station is in a village: " + Reach.village(level, station));
		return v;
	}

	private static Worker areaJob(GameTestHelper helper, ServerPlayer p, List<Component> seen, String name, Block block, ResourceKey<PoiType> poi,
								  VillagerProfession profession, Attachment<FieldJob> type, IntSupplier reach) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = new BlockPos[1];
		Villager v = station(helper, block, at);
		BlockPos work = at[0];
		Jobs.employ(level, v, work, poi, profession);
		Leftovers.after(helper, () -> type.remove(v));
		return new Worker(name, work, off -> {
			p.setItemInHand(InteractionHand.MAIN_HAND, QaVillageReachGameTests.fieldMarker(level, work.offset(off)));
			QaStageSixGameTests.clickVillager(p, v, seen);
			boolean taken = type.get(v) != null;
			type.remove(v);
			return taken;
		}, reach);
	}

	private static Worker worker(GameTestHelper helper, String job, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		switch (job) {
			case "farmer":
				return areaJob(helper, p, seen, job, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, ModAttachments.FARM_FIELD, () -> Fields.MAX_DISTANCE);
			case "lumberjack":
				return areaJob(helper, p, seen, job, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK, ModAttachments.TREE_FARM,
					() -> AreaJobs.TREE_FARM_REACH);
			case "orchard":
				return areaJob(helper, p, seen, job, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER, ModAttachments.ORCHARD,
					() -> AreaJobs.ORCHARD_REACH);
			case "berry":
				return areaJob(helper, p, seen, job, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER, ModAttachments.BERRY_PLOT,
					() -> AreaJobs.BERRY_PLOT_REACH);
			case "miner": {
				BlockPos[] at = new BlockPos[1];
				Villager v = station(helper, ModBlocks.MINERS_BENCH, at);
				BlockPos bench = at[0];
				Jobs.employ(level, v, bench, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
				Leftovers.after(helper, () -> {
					QuarrySite left = Miners.activeSite(level, v);
					if (left != null) {
						Miners.cancel(level, left);
					}
				});
				return new Worker(job, bench, off -> {
					// a 5 × 5 quarry, its top at the spot, 4 deep
					BlockPos top = bench.offset(off);
					ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
					marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(top.offset(-2, 0, -2)), Optional.of(top.offset(2, 0, 2)), 4));
					p.setItemInHand(InteractionHand.MAIN_HAND, marker);
					QaStageSixGameTests.clickVillager(p, v, seen);
					QuarrySite site = Miners.activeSite(level, v);
					if (site != null) {
						Miners.cancel(level, site);
					}
					return site != null;
				}, () -> Miners.MAX_DISTANCE);
			}
			case "builder": {
				BlockPos[] at = new BlockPos[1];
				Villager v = station(helper, ModBlocks.BLUEPRINT_TABLE, at);
				BlockPos table = at[0];
				Builders.employ(level, v, table);
				Leftovers.after(helper, () -> QaVillageReachGameTests.forgetSites(level, v));
				Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
				return new Worker(job, table, off -> {
					// the hut's outline centred off.x / off.z from the table across the ground, its floor off.y above the table's
					BlueprintData.Placement level0 = QaFarReach1028bGameTests.standing(level, TEST_HUT, table, table.offset(off.getX(), 0, off.getZ()));
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), level0.origin().above(off.getY()), Rotation.NONE, Mirror.NONE);
					BlockPos centre = BlueprintOutline.bounds(placement, size).getCenter();
					helper.assertTrue(centre.getX() - table.getX() == off.getX() && centre.getZ() - table.getZ() == off.getZ()
							&& placement.origin().getY() - table.getY() == off.getY(),
						"setup: the hut isn't centred " + off.toShortString() + " from the table: centre " + centre.toShortString() + ", origin "
							+ placement.origin().toShortString() + ", table " + table.toShortString());
					p.setItemInHand(InteractionHand.MAIN_HAND, QaFarReach1028bGameTests.blueprint(level, TEST_HUT, placement));
					QaStageSixGameTests.clickVillager(p, v, seen);
					BuildSite site = Builders.activeSite(level, v);
					QaVillageReachGameTests.forgetSites(level, v);
					return site != null;
				}, () -> Builders.MAX_SITE_DISTANCE);
			}
			default:
				throw new IllegalArgumentException(job);
		}
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static void taken(GameTestHelper helper, Worker w, List<Component> seen, int dx, int dy, int dz) {
		seen.clear();
		Vec3i off = new Vec3i(dx, dy, dz);
		double across = Math.sqrt((double) dx * dx + (double) dz * dz);
		helper.assertTrue(w.offer().take(off), w.name() + ": a spot " + String.format("%.2f", across) + " across the ground (" + off.toShortString()
			+ ", " + dy + " up) was refused: " + texts(seen));
	}

	/** {@code off} is refused with exactly {@code sentence}, whose distance must be the across-the-ground one rounded up. */
	private static void refused(GameTestHelper helper, Worker w, List<Component> seen, int dx, int dy, int dz, String sentence) {
		seen.clear();
		Vec3i off = new Vec3i(dx, dy, dz);
		double across = Math.sqrt((double) dx * dx + (double) dz * dz);
		String spot = w.name() + ": a spot " + String.format("%.2f", across) + " across the ground (" + off.toShortString() + ", " + dy + " up)";
		helper.assertFalse(w.offer().take(off), spot + " was taken");
		helper.assertTrue(texts(seen).contains(sentence), spot + ": expected \"" + sentence + "\", saw " + texts(seen));
	}

	private static String sentence(String job, int distance) {
		return switch (job) {
			case "farmer" -> "That field is " + distance + " blocks from the Composter the farmer works at — keep it within 128.";
			case "lumberjack" -> "That spot is " + distance + " blocks from the Chopping Block the lumberjack works at — keep it within 128.";
			case "orchard" -> "That spot is " + distance + " blocks from the Fruit Basket the orchard keeper works at — keep it within 128.";
			case "berry" -> "That spot is " + distance + " blocks from the Composter the berry breeder works at — keep it within 128.";
			case "miner" -> "That spot is " + distance + " blocks from the Miner's Bench the miner works at — keep it within 128.";
			case "builder" -> "That spot is " + distance + " blocks from the builder's Blueprint Table — keep it within 128.";
			default -> throw new IllegalArgumentException(job);
		};
	}

	/**
	 * One job's reach with height in it: 128 across, {@code dy} above and below (a straight line would be past 128) taken;
	 * 129 across above and below refused naming 129 (a straight line would name more); the diagonal 90/90 (127.3)
	 * taken and 91/91 (128.7) refused naming 129, both with height; 128/1 (128.004) refused naming 129 (rounded up).
	 */
	private static void heightAndDiagonal(GameTestHelper helper, String job, int dy) {
		setUp(helper);
		List<Component> seen = new ArrayList<>();
		Worker w = worker(helper, job, seen);
		helper.assertTrue(w.reach().getAsInt() == 128, job + "'s long reach isn't 128 but " + w.reach().getAsInt());
		String at129 = sentence(job, 129);
		taken(helper, w, seen, 128, dy, 0);
		taken(helper, w, seen, 128, -dy, 0);
		taken(helper, w, seen, 0, dy, -128);
		refused(helper, w, seen, 129, dy, 0, at129);
		refused(helper, w, seen, 129, -dy, 0, at129);
		refused(helper, w, seen, 0, -dy, -129, at129);
		taken(helper, w, seen, 90, dy, 90);
		taken(helper, w, seen, -90, -dy, 90);
		refused(helper, w, seen, 91, dy, 91, at129);
		refused(helper, w, seen, -91, -dy, -91, at129);
		refused(helper, w, seen, 128, 0, 1, at129);
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qaReachAcross1031] " + job + ": 128 across " + dy
			+ " up and down taken, 129 refused as 129; diagonal 90/90 taken, 91/91 and 128/1 refused as 129"));
		helper.succeed();
	}

	/** The farmer's field: its middle 128 across and 25 above or below the composter is taken; 129 is refused as 129. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Farmer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Farmer")
	public void aFieldFarAboveOrBelowTheComposterIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "farmer", 25);
	}

	/** The orchard keeper's orchard, 20 above or below the Fruit Basket. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Orchard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Orchard")
	public void anOrchardFarAboveOrBelowTheBasketIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "orchard", 20);
	}

	/** The berry breeder's plot, 20 above or below the composter. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Berry"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Berry")
	public void aBerryPlotFarAboveOrBelowTheComposterIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "berry", 20);
	}

	/** The lumberjack's tree farm, 30 above or below the Chopping Block. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Lumberjack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Lumberjack")
	public void aTreeFarmFarAboveOrBelowTheChoppingBlockIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "lumberjack", 30);
	}

	/** A build standing 40 above or below the Blueprint Table, and on the diagonal. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Builder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Builder")
	public void aBuildFarAboveOrBelowTheTableIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "builder", 40);
	}

	/** A quarry whose top is 40 above or below the Miner's Bench, and on the diagonal. */
	//$ gametest_ticks_batch AREA '200' '"qaReachAcross1031Miner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachAcross1031Miner")
	public void aQuarryFarAboveOrBelowTheBenchIsMeasuredAcrossTheGround(GameTestHelper helper) {
		heightAndDiagonal(helper, "miner", 40);
	}
}
