package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B665/B668 (36.20): a worker's long reach is measured across the ground, so up and down never count. A quarry is in
 * the miner's reach at any depth, a tall build in the builder's at any height, and a spot one block past the reach is
 * still refused however deep or tall, with the refusal naming the distance across the ground.
 */
public class ReachAcrossGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void reachIgnoresUpAndDown(GameTestHelper helper) {
		BlockPos from = new BlockPos(10, 64, 10);
		helper.assertTrue(Reach.within(from, from.east(128).below(90), 128), "128 across and 90 down was out of reach");
		helper.assertTrue(Reach.within(from, from.east(128).above(90), 128), "128 across and 90 up was out of reach");
		helper.assertFalse(Reach.within(from, from.east(129), 128), "129 across at the same height was in reach");
		helper.assertFalse(Reach.within(from, from.east(129).below(64), 128), "129 across and 64 down was in reach");
		// diagonal: 91 and 91 is 128.7 across, past 128
		helper.assertFalse(Reach.within(from, from.offset(91, 0, 91), 128), "91 east and 91 south (128.7) was in reach");
		helper.assertTrue(Reach.within(from, from.offset(90, -40, 90), 128), "90 east and 90 south (127.3), 40 down, was out of reach");
		helper.assertTrue(Math.abs(Reach.across(from, from.offset(3, 50, 4)) - 5) < 1e-9, "3 and 4 across, 50 up, isn't 5 across");
		helper.succeed();
	}

	/** True if a 5 x 5 quarry, its top at the bench's height {@code east} blocks east, {@code depth} deep, is taken. */
	private static boolean offer(GameTestHelper helper, ServerPlayer p, Villager miner, BlockPos bench, int east, int depth, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		BlockPos at = bench.east(east);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2)), depth));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		seen.clear();
		QaStageSixGameTests.clickVillager(p, miner, seen);
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		return site != null;
	}

	private static String said(List<Component> seen) {
		return String.join(" | ", seen.stream().map(Component::getString).toList());
	}

	//$ gametest_ticks_batch AREA '200' '"reachAcrossQuarry"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reachAcrossQuarry")
	public void aQuarryIsMeasuredAcrossTheGroundAtAnyDepth(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		// high in the air (above the test area), so a 96-deep pit under the bench stays above the world's floor
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3)).above(100);
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			level.setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(bench.below(), Blocks.AIR.defaultBlockState());
		});
		// a small village round the bench, so the far spots are outside it and only the long reach counts
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		level.setBlockAndUpdate(bench, ModBlocks.MINERS_BENCH.defaultBlockState());
		level.setBlockAndUpdate(bench.below(), Blocks.STONE.defaultBlockState());
		Villager miner = EntityType.VILLAGER.create(level);
		miner.moveTo(bench.getX() + 1.5, bench.getY(), bench.getZ() + 0.5);
		miner.setNoAi(true);
		level.addFreshEntity(miner);
		Leftovers.after(helper, miner::discard);
		Jobs.employ(level, miner, bench, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);

		int reach = Miners.MAX_DISTANCE;
		for (int depth : new int[] {4, QuarryData.ANY_LEVEL_DEPTH, 96}) {
			helper.assertTrue(offer(helper, p, miner, bench, reach, depth, seen),
				"a " + depth + "-deep quarry centred " + reach + " out was refused: " + said(seen));
			helper.assertFalse(offer(helper, p, miner, bench, reach + 1, depth, seen),
				"a " + depth + "-deep quarry centred " + (reach + 1) + " out was taken");
			String words = said(seen);
			helper.assertTrue(words.contains((reach + 1) + " blocks") && words.contains("within " + reach),
				"the " + depth + "-deep quarry " + (reach + 1) + " out wasn't refused at " + (reach + 1) + " blocks: " + words);
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '200' '"reachAcrossBuild"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reachAcrossBuild")
	public void aTallBuildJustPastTheReachIsRefusedAtItsDistanceAcross(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.forgetNearest();
		});
		QaFarReach1028bGameTests.noHalls(helper);
		BlockPos work = new BlockPos(3, 2, 3);
		helper.setBlock(work, ModBlocks.BLUEPRINT_TABLE);
		BlockPos bench = helper.absolutePos(work);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setNoAi(true);
		Builders.employ(level, builder, bench);
		QaFarReach1028bGameTests.forgetSitesOf(helper, builder);
		Vec3i size = BlueprintLibrary.get(level, PILLAR).orElseThrow().size();
		helper.assertTrue(size.getY() > 2, "setup: the test pillar isn't tall: " + size);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("across"));
		int reach = Builders.MAX_SITE_DISTANCE;
		int east = reach + 1;
		BlueprintData.Placement placement = QaFarReach1028bGameTests.standing(level, PILLAR, bench, bench.east(east));
		BlockPos centre = BlueprintOutline.bounds(placement, size).getCenter();
		helper.assertTrue(centre.getX() - bench.getX() == east && centre.getZ() == bench.getZ() && centre.getY() > bench.getY(),
			"setup: the pillar's centre isn't " + east + " east and above the table: " + centre.toShortString() + " from " + bench.toShortString());
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaFarReach1028bGameTests.blueprint(level, PILLAR, placement));
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		if (site != null) {
			BuildSiteManager.get(level).remove(site.id());
		}
		helper.assertTrue(site == null, "a tall pillar centred " + east + " out was taken");
		String words = said(c.seen());
		// across the ground it is exactly 129, not the 129.x a straight line to its raised centre would round up to 130
		helper.assertTrue(words.contains("is " + east + " blocks") && words.contains("within " + reach),
			"the pillar " + east + " out wasn't refused at " + east + " blocks: " + words);
		helper.succeed();
	}
}
