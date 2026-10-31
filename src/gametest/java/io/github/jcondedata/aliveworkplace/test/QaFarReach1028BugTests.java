package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-28) bug tests for 36.20: a quarry marked 125 blocks out (inside the miner's long reach of 128) with the
 * Quarry Marker's standard depth 64 is refused ("... 129 blocks from the Miner's Bench ... keep it within 128."),
 * while the same spot marked 96 deep is taken: a pit of 64 or less is measured to the middle of its depth, a deeper one
 * to the middle of its top. The spec: "a spot is in reach when ... within a long reach of the worker's block outside it:
 * mines ... 128", and Reach's own rule "Up and down never count".
 *
 * <p>(The unloaded-chunks test of this pass, B666, stays on tests/far-reach until that bug is fixed.)
 */
public class QaFarReach1028BugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** True if a 5 × 5 quarry, its top at the bench's height {@code east} blocks east, {@code depth} deep, is taken. */
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

	//$ gametest_ticks_batch AREA '200' '"qa1028QuarryDepthReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028QuarryDepthReach")
	public void aQuarry125OutIsInReachWhateverItsDepth(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		// the bench high in the air (above the test area), so a 96-deep pit under it stays above the world's floor
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3)).above(100);
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			level.setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(bench.below(), Blocks.AIR.defaultBlockState());
		});
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

		helper.assertTrue(offer(helper, p, miner, bench, 125, 96, seen), "control: the 96-deep quarry 125 blocks out was refused: "
			+ seen.stream().map(Component::getString).toList());
		helper.assertTrue(offer(helper, p, miner, bench, 125, 4, seen), "control: the 4-deep quarry 125 blocks out was refused: "
			+ seen.stream().map(Component::getString).toList());
		boolean taken = offer(helper, p, miner, bench, 125, 64, seen);
		helper.assertTrue(taken, "the 64-deep quarry 125 blocks out (the 96-deep one there was taken) was refused: "
			+ seen.stream().map(Component::getString).toList());
		helper.succeed();
	}
}
