package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.GameRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/** QA (lane-c-1123-0953) bug tests for ROADMAP 28.11: one gem bed reads as one; a broken stonecutter is said. */
public class QaGemGrower1123cBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * Clears what an earlier batch left within her scan (16 blocks round the stonecutter, which reaches outside the test
	 * area): budding amethyst and its buds, and containers. Only for tests alone in their batch.
	 */
	static void clearAround(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.containers(helper, 20);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB area = helper.getBounds();
		net.minecraft.world.phys.AABB around = area.inflate(20);
		for (BlockPos p : BlockPos.betweenClosed((int) around.minX, (int) Math.max(around.minY, level.getMinBuildHeight()), (int) around.minZ,
				(int) around.maxX, (int) around.maxY, (int) around.maxZ)) {
			if (area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {
				continue;
			}
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (s.is(Blocks.BUDDING_AMETHYST) || s.getBlock() instanceof AmethystClusterBlock) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	static void noRandomTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.IntegerValue ticking = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
		int was = ticking.get();
		ticking.set(0, level.getServer());
		Leftovers.after(helper, () -> ticking.set(was, level.getServer()));
	}

	static void cluster(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing));
	}

	/** What she says over her head (the line), or "" with no status yet. */
	static String line(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return e == null ? "" : e.line().getString();
	}

	private static String title(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return e == null ? "" : e.title().getString();
	}

	static String said(GameTestHelper helper, Villager grower) {
		return "'" + line(helper, grower) + "' (title '" + title(helper, grower) + "') at " + helper.relativePos(grower.blockPosition())
			+ ", beds " + GemGrowers.beds(grower) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks();
	}

	/** One budding block with nothing ripe: once found she reads "Tending 1 gem bed", never "Tending 1 gem beds". */
	//$ gametest_ticks_batch AREA '1200' '"qaGem1123cOne"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaGem1123cOne")
	public void qaOneBedReadsTendingOneGemBed(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(8, 2, 2), Blocks.BUDDING_AMETHYST);
		Villager grower = GemGrowerGameTests.grower(helper);
		helper.succeedWhen(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(grower, level.getGameTime());
			String line = e == null ? "" : e.line().getString();
			helper.assertTrue(GemGrowers.beds(grower).size() == 1 && line.startsWith("Tending"), "not tending one bed yet: '" + line + "'");
			helper.assertTrue(line.equals("Tending 1 gem bed"), "one bed reads: '" + line + "'");
		});
	}

	/**
	 * Her stonecutter broken mid-job (she is tending a bed): the ripe cluster is left, she stays a Gem Grower, and 300 ticks
	 * later the line over her head says "Her stonecutter is gone" (wiki: "Her stonecutter broken: she stops and says so").
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaGem1123cCutter"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaGem1123cCutter")
	public void qaHerStonecutterBrokenSheSaysItIsGone(GameTestHelper helper) {
		clearAround(helper);
		noRandomTicks(helper);
		helper.setBlock(new BlockPos(8, 2, 2), Blocks.BUDDING_AMETHYST);
		Villager grower = GemGrowerGameTests.grower(helper);
		long[] broken = {-1};
		java.util.Set<String> seen = new java.util.LinkedHashSet<>();
		helper.onEachTick(() -> {
			if (broken[0] >= 0) {
				seen.add(line(helper, grower) + "/" + grower.getVillagerData().getProfession() + "/" + GemGrowers.isGrower(grower)
					+ "/" + io.github.jcondedata.aliveworkplace.build.Builders.benchPos(grower).isPresent());
			}
			if (broken[0] < 0 && line(helper, grower).startsWith("Tending")) {
				helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
				cluster(helper, new BlockPos(8, 2, 2).above(), Direction.UP);
				broken[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] >= 0, "she never started tending: " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(new BlockPos(8, 2, 2).above()).is(Blocks.AMETHYST_CLUSTER), "she picked with no stonecutter");
			helper.assertTrue(helper.getTick() >= broken[0] + 300, "waiting 300 ticks after the break");
			helper.assertTrue(GemGrowers.isGrower(grower), "she lost her job: " + grower.getVillagerData().getProfession());
			helper.assertTrue(line(helper, grower).equals("Her stonecutter is gone"), "she says " + said(helper, grower) + "; seen " + seen);
		});
	}
}
