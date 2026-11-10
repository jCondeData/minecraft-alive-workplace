package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests (qa-lane-b-1109-1537), failing on the code of 2026-11-09. docs/guide/game-rules.md says of the mod's game
 * rules: "an operator changes them in game with /gamerule ... They take effect at once." workplaceLevelGround and
 * workplaceFoundationDepth don't for a build already under way: the site's plan is worked out once, with the rules of the
 * moment it starts, and only again after a server restart or a reload of the site (so the same change mid-build does
 * something or nothing depending on whether the server restarted; QaBuilderRules1109GameTests shows it does take effect
 * across a restart). Self-contained so it travels alone on the branch tests/builder-rules-1109.
 */
public class QaBuilderRules1109BugTests {
	private static final String HUGE = QaBuilderConfig1102GameTests.HUGE;
	private static final List<Item> HUT_ITEMS = QaBuilderConfig1102GameTests.HUT_ITEMS;
	static final int LAWN = 3;
	static final BlockPos BENCH = new BlockPos(2, LAWN + 1, 2);
	static final BlockPos CHEST = new BlockPos(2, LAWN + 1, 4);
	static final BlockPos HUT = new BlockPos(12, LAWN + 1, 12);

	static void lawn(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, LAWN - 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, LAWN, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	/** Pits one block deep in the lawn round the hut: the whole ring 1 out (west and east sides) and two spots 2 out. */
	static List<BlockPos> pits(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (int z = 12; z <= 16; z++) {
			out.add(new BlockPos(11, LAWN, z));
			out.add(new BlockPos(17, LAWN, z));
		}
		out.add(new BlockPos(10, LAWN, 14));
		out.add(new BlockPos(18, LAWN, 14));
		for (BlockPos p : out) {
			helper.setBlock(p, Blocks.AIR);
		}
		return out;
	}

	static long filled(GameTestHelper helper, List<BlockPos> spots) {
		return spots.stream().filter(p -> !helper.getBlockState(p).isAir()).count();
	}

	static String show(GameTestHelper helper, List<BlockPos> spots) {
		return spots.stream().map(p -> p.toShortString() + "=" + helper.getBlockState(p).getBlock().getName().getString()).collect(Collectors.joining(", "));
	}

	/** A dip two deep under three of the hut's columns; the six emptied spots. */
	static List<BlockPos> dip(GameTestHelper helper) {
		List<BlockPos> spots = new ArrayList<>();
		for (int x = 13; x <= 15; x++) {
			BlockPos up = new BlockPos(x, LAWN, 14);
			helper.setBlock(up, Blocks.AIR);
			helper.setBlock(up.below(), Blocks.AIR);
			spots.add(up);
			spots.add(up.below());
		}
		return spots;
	}

	static int total(Map<Item, Integer> held) {
		return held.values().stream().mapToInt(Integer::intValue).sum();
	}

	static int dirtHeld(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r) {
		return QaBuilderConfig1102GameTests.chestCount(helper, CHEST, Items.DIRT)
			+ QaBuilderConfig1102GameTests.bag(r.villager()).getOrDefault(Items.DIRT, 0);
	}

	static void noneOnTheGround(GameTestHelper helper) {
		for (Item item : HUT_ITEMS) {
			int n = QaBuilderConfig1102GameTests.onTheGround(helper, item);
			helper.assertTrue(n == 0, n + " " + item + " on the ground after the build");
		}
	}

	static void setInt(ServerLevel level, GameRules.Key<GameRules.IntegerValue> key, int value) {
		level.getGameRules().getRule(key).set(value, level.getServer());
	}

	static void setBool(ServerLevel level, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
		level.getGameRules().getRule(key).set(value, level.getServer());
	}

	static ItemStack[] hutAndDirt(int dirt) {
		return new ItemStack[]{new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH), new ItemStack(Items.DIRT, dirt)};
	}

	/**
	 * game-rules.md: the rules "take effect at once". workplaceLevelGround set to 0 in the middle of STRUCTURE (no restart):
	 * "0 leaves the ground alone", so the pits round the hut are still pits when it is done.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109GroundOffLive"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109GroundOffLive")
	public void levelGroundSetToZeroMidStructureLeavesThePitsAlone(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.LEVEL_GROUND, 2);
		lawn(helper);
		List<BlockPos> pits = pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, hutAndDirt(16));
		boolean[] off = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!off[0] && site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
				setInt(level, ModGameRules.LEVEL_GROUND, 0);
				off[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "the build never reached STRUCTURE cursor 5: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(filled(helper, pits) == 0, "workplaceLevelGround was set to 0 in the middle of STRUCTURE, yet the Builder filled "
				+ filled(helper, pits) + " of the " + pits.size() + " pits round the hut: " + show(helper, pits));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
		});
	}

	/**
	 * game-rules.md: the rules "take effect at once". workplaceFoundationDepth raised from 0 to 12 while the Builder is
	 * still clearing the site (no restart): the dip under the hut is filled before the hut goes up on it.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109DepthOnLive"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109DepthOnLive")
	public void foundationDepthRaisedFromZeroWhileClearingFillsTheDip(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.FOUNDATION_DEPTH, 0);
		setInt(level, ModGameRules.LEVEL_GROUND, 0);
		lawn(helper);
		List<BlockPos> dip = dip(helper);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 31), new ItemStack(Items.OAK_PLANKS, 61), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, items);
		boolean[] on = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!on[0] && site != null && site.stage() == BuildPlan.Stage.CLEAR) {
				setInt(level, ModGameRules.FOUNDATION_DEPTH, 12);
				on[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(on[0], "setup: the build was never seen in CLEAR: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(filled(helper, dip) == dip.size(), "workplaceFoundationDepth was raised to 12 while the site was being cleared, yet "
				+ (dip.size() - filled(helper, dip)) + " of the dip's " + dip.size() + " spots under the hut are empty: " + show(helper, dip));
			noneOnTheGround(helper);
		});
	}
}
