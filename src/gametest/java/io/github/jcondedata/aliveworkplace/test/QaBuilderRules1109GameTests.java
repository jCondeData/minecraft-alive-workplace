package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1109-1537), from docs/guide/game-rules.md ("an operator changes them in game with /gamerule ... They take
 * effect at once"; workplaceFreeMaterials "true: builders place blocks without needing materials", workplaceBuildDelay
 * "1 is the fastest", workplaceFoundationDepth "0: no foundations", workplaceLevelGround "0 to 8; 0 leaves the ground
 * alone"), docs/wiki/builder.md ("LANDSCAPE (leveling the ground round the build, dirt) never waits for materials") and
 * CLAUDE.md ("BuilderWork must stay restartable at any tick"). The older tests set these rules before a build or across a
 * restart one at a time; these switch them in the middle of FOUNDATION, STRUCTURE, DECORATION and LANDSCAPE, combined with
 * a save and reload of the site and the Builder, and check the build ends with nothing lost or doubled. Plus the rules'
 * bounds through the player's own entry point, the /gamerule command.
 */
public class QaBuilderRules1109GameTests {
	private static final String HUGE = QaBuilderConfig1102GameTests.HUGE;
	private static final List<Item> HUT_ITEMS = QaBuilderConfig1102GameTests.HUT_ITEMS;

	/** The lawn (huge area): grass at y 3, dirt at y 2, the hut's floor at y 4, the hut at x 12..16, z 12..16. */
	static final int LAWN = 3;
	static final BlockPos BENCH = new BlockPos(2, LAWN + 1, 2);
	static final BlockPos CHEST = new BlockPos(2, LAWN + 1, 4);
	static final BlockPos HUT = new BlockPos(12, LAWN + 1, 12);

	// ------------------------------------------------------------------------------------------------- helpers

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

	// ------------------------------------------------------------------------------------------------- workplaceLevelGround

	/**
	 * workplaceLevelGround set to 0 by a server restart in the middle of LANDSCAPE (an admin changing the rule while the
	 * server is down, after the Builder has filled some of the pits round the hut): "0 leaves the ground alone", so no pit
	 * is filled after the restart, the build still ends, and no dirt is made from nothing or dropped.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109GroundOffLandscape"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109GroundOffLandscape")
	public void levelGroundSetToZeroAtARestartMidLandscapeFillsNoMorePits(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.LEVEL_GROUND, 2);
		lawn(helper);
		List<BlockPos> pits = pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, hutAndDirt(16));
		int dirtStart = 16;
		long[] atRestart = {-1};
		int[] dirtAtRestart = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (atRestart[0] >= 0 || site == null || site.stage() != BuildPlan.Stage.LANDSCAPE) {
				return;
			}
			long n = filled(helper, pits);
			if (n == 0 || n == pits.size()) {
				return;
			}
			QaBuilderConfig1102GameTests.restart(helper, r);
			setInt(level, ModGameRules.LEVEL_GROUND, 0);
			atRestart[0] = n;
			dirtAtRestart[0] = dirtHeld(helper, r);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(atRestart[0] >= 0, "the Builder never got part way through filling the pits in LANDSCAPE: "
				+ QaBuilderConfig1102GameTests.where(helper, r) + "; pits " + show(helper, pits));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			long now = filled(helper, pits);
			helper.assertTrue(now == atRestart[0], "with workplaceLevelGround set to 0 at the restart (" + atRestart[0] + " of " + pits.size()
				+ " pits filled then) the Builder filled " + (now - atRestart[0]) + " more: " + show(helper, pits));
			int dirt = dirtHeld(helper, r);
			helper.assertTrue(dirt <= dirtAtRestart[0] && dirt <= dirtStart, "dirt held went from " + dirtAtRestart[0] + " at the restart to "
				+ dirt + " (started with " + dirtStart + ")");
			helper.assertTrue(QaBuilderConfig1102GameTests.onTheGround(helper, Items.DIRT) == 0, "dirt on the ground after the build");
			noneOnTheGround(helper);
			Map<Item, Integer> left = QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST);
			helper.assertTrue(total(left) == 0, "the hut is built but its materials are still held (doubled): " + left);
		});
	}

	/**
	 * workplaceLevelGround raised from 0 to 2 by a server restart in the middle of DECORATION (an admin changing the rule
	 * while the server is down): the build is leveled 2 out, so every pit in the ring is filled with the chest's dirt
	 * (exactly one dirt a pit, none dropped), and the hut ends as drawn with its materials used exactly once.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109GroundOnDecoration"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109GroundOnDecoration")
	public void levelGroundRaisedAtARestartMidDecorationLevelsEveryPit(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.LEVEL_GROUND, 0);
		lawn(helper);
		List<BlockPos> pits = pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, hutAndDirt(16));
		long[] atRestart = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (atRestart[0] >= 0 || site == null || site.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			atRestart[0] = filled(helper, pits);
			QaBuilderConfig1102GameTests.restart(helper, r);
			setInt(level, ModGameRules.LEVEL_GROUND, 2);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(atRestart[0] >= 0, "the build never reached DECORATION: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(atRestart[0] == 0, "with workplaceLevelGround 0 " + atRestart[0] + " pit(s) were filled before DECORATION");
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			helper.assertTrue(filled(helper, pits) == pits.size(), "workplaceLevelGround was raised to 2 at a restart in DECORATION, yet not every pit in the ring was filled: "
				+ show(helper, pits));
			int dirt = dirtHeld(helper, r);
			helper.assertTrue(dirt == 16 - pits.size(), "the " + pits.size() + " pits were filled but the dirt held went from 16 to " + dirt);
			helper.assertTrue(QaBuilderConfig1102GameTests.onTheGround(helper, Items.DIRT) == 0, "dirt on the ground after the build");
			noneOnTheGround(helper);
			Map<Item, Integer> left = QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST);
			helper.assertTrue(total(left) == 0, "the hut is built but its materials are still held (doubled): " + left);
		});
	}

	/**
	 * workplaceFreeMaterials on, a chest with only dirt, workplaceLevelGround 2 and pits round the hut; the rule is switched
	 * off by a restart in the middle of LANDSCAPE. LANDSCAPE "never waits for materials", so the Builder never stops to
	 * wait; the rest of the 2-block ring is still filled after the restart, now with the chest's dirt (exactly one dirt a
	 * pit), the hut ends as drawn, and none of the hut's blocks turn up in his chest or on the ground.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109FreeOffLandscape"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109FreeOffLandscape")
	public void freeMaterialsSwitchedOffAtARestartMidLandscapeStillLevelsEveryPit(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, true, 4);
		setInt(level, ModGameRules.LEVEL_GROUND, 2);
		lawn(helper);
		List<BlockPos> pits = pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, new ItemStack(Items.DIRT, 16));
		boolean[] off = {false};
		long[] filledAtOff = {-1};
		int[] dirtAtOff = {-1};
		List<String> waited = new ArrayList<>();
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (site == null) {
				return;
			}
			if (!off[0]) {
				if (site.stage() == BuildPlan.Stage.LANDSCAPE && filled(helper, pits) > 0) {
					QaBuilderConfig1102GameTests.restart(helper, r);
					setBool(level, ModGameRules.FREE_MATERIALS, false);
					off[0] = true;
					filledAtOff[0] = filled(helper, pits);
					dirtAtOff[0] = dirtHeld(helper, r);
				}
				return;
			}
			if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && waited.isEmpty()) {
				waited.add(QaBuilderConfig1102GameTests.where(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "the Builder never filled a pit in LANDSCAPE: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(waited.isEmpty(), "LANDSCAPE waited for materials after free materials were switched off: " + waited);
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			helper.assertTrue(filled(helper, pits) == pits.size(), "with workplaceLevelGround 2 and dirt in the chest, after a restart in LANDSCAPE ("
				+ filledAtOff[0] + " of " + pits.size() + " pits filled then) not every pit was filled: " + show(helper, pits) + "; dirt held "
				+ dirtHeld(helper, r) + " of " + dirtAtOff[0]);
			long after = filled(helper, pits) - filledAtOff[0];
			int dirt = dirtHeld(helper, r);
			helper.assertTrue(dirt == dirtAtOff[0] - after, "after free materials were switched off " + after + " pit(s) were filled but the dirt held went from "
				+ dirtAtOff[0] + " to " + dirt);
			helper.assertTrue(QaBuilderConfig1102GameTests.onTheGround(helper, Items.DIRT) == 0, "dirt on the ground after the build");
			Map<Item, Integer> left = QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST);
			helper.assertTrue(total(left) == 0, "free materials put the hut's blocks in the chest or bag: " + left);
			noneOnTheGround(helper);
		});
	}

	// ------------------------------------------------------------------------------------------------- workplaceFreeMaterials

	/**
	 * workplaceFreeMaterials switched on at a restart in the middle of FOUNDATION (the dip under the hut half filled from
	 * the chest): from then on the chest never holds less of any of the hut's materials, the dip is filled to its bottom,
	 * the hut ends as drawn, nothing is dropped, and the chest and bag never hold more than at the switch (nothing made).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109FreeOnFoundation"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109FreeOnFoundation")
	public void freeMaterialsSwitchedOnAtARestartMidFoundationTakesNothingMore(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.FOUNDATION_DEPTH, 12);
		lawn(helper);
		List<BlockPos> dip = dip(helper);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 31), new ItemStack(Items.OAK_PLANKS, 61), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, items);
		helper.assertTrue(!r.plan().steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "setup: the hut over a dip two deep has no foundation steps");
		Map<Item, Integer> chestAtSwitch = new LinkedHashMap<>();
		int[] heldAtSwitch = {-1};
		List<String> taken = new ArrayList<>();
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (chestAtSwitch.isEmpty()) {
				if (site != null && site.stage() == BuildPlan.Stage.FOUNDATION && filled(helper, dip) > 0 && filled(helper, dip) < dip.size()) {
					QaBuilderConfig1102GameTests.restart(helper, r);
					setBool(level, ModGameRules.FREE_MATERIALS, true);
					for (Item item : HUT_ITEMS) {
						chestAtSwitch.put(item, QaBuilderConfig1102GameTests.chestCount(helper, CHEST, item));
					}
					heldAtSwitch[0] = total(QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST));
				}
				return;
			}
			for (Item item : HUT_ITEMS) {
				int now = QaBuilderConfig1102GameTests.chestCount(helper, CHEST, item);
				if (now < chestAtSwitch.get(item) && taken.isEmpty()) {
					taken.add(item + " went from " + chestAtSwitch.get(item) + " to " + now + " in " + QaBuilderConfig1102GameTests.where(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!chestAtSwitch.isEmpty(), "the build never got part way through FOUNDATION: " + QaBuilderConfig1102GameTests.where(helper, r)
				+ "; dip " + show(helper, dip));
			helper.assertTrue(taken.isEmpty(), "with free materials switched on the Builder still took from the chest: " + taken);
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			helper.assertTrue(filled(helper, dip) == dip.size(), "the dip under the hut isn't filled: " + show(helper, dip));
			int held = total(QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST));
			helper.assertTrue(held <= heldAtSwitch[0], "the chest and bag hold " + held + " of the hut's materials, more than the " + heldAtSwitch[0]
				+ " at the switch (made from nothing)");
			noneOnTheGround(helper);
		});
	}

	/**
	 * workplaceFreeMaterials on in the middle of STRUCTURE, a restart while it is on, then off as DECORATION starts: the
	 * chest gives nothing while the rule is on (across the restart), after it is off the Builder takes no more than the
	 * blocks still to place, the hut ends as drawn and nothing is dropped or made.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaRules1109FreeOnOff"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaRules1109FreeOnOff")
	public void freeMaterialsOnMidStructureThroughARestartThenOffForDecorationAddsUp(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		setInt(level, ModGameRules.LEVEL_GROUND, 0);
		lawn(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT, QaBuilderConfig1102GameTests.hutMaterials());
		Map<Item, Integer> chestAtOn = new LinkedHashMap<>();
		int[] heldAtOn = {-1};
		int[] heldAtOff = {-1};
		int[] unfinishedAtOff = {-1};
		boolean[] restarted = {false};
		List<String> taken = new ArrayList<>();
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (site == null) {
				return;
			}
			if (chestAtOn.isEmpty()) {
				if (site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
					setBool(level, ModGameRules.FREE_MATERIALS, true);
					for (Item item : HUT_ITEMS) {
						chestAtOn.put(item, QaBuilderConfig1102GameTests.chestCount(helper, CHEST, item));
					}
					heldAtOn[0] = total(QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST));
				}
				return;
			}
			if (heldAtOff[0] < 0) {
				for (Item item : HUT_ITEMS) {
					int now = QaBuilderConfig1102GameTests.chestCount(helper, CHEST, item);
					if (now < chestAtOn.get(item) && taken.isEmpty()) {
						taken.add(item + " went from " + chestAtOn.get(item) + " to " + now + " in " + QaBuilderConfig1102GameTests.where(helper, r));
					}
				}
				if (!restarted[0] && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 20) {
					QaBuilderConfig1102GameTests.restart(helper, r);
					restarted[0] = true;
				}
				if (restarted[0] && site.stage() == BuildPlan.Stage.DECORATION) {
					setBool(level, ModGameRules.FREE_MATERIALS, false);
					heldAtOff[0] = total(QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST));
					unfinishedAtOff[0] = r.plan().unfinished(level).size();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(heldAtOff[0] >= 0, "never got to switching free materials off in DECORATION (on " + !chestAtOn.isEmpty() + ", restarted "
				+ restarted[0] + "): " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(taken.isEmpty(), "with free materials on the Builder still took from the chest: " + taken);
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			int held = total(QaBuilderConfig1102GameTests.held(helper, r.villager(), CHEST));
			helper.assertTrue(heldAtOff[0] <= heldAtOn[0], "while free materials were on the chest and bag grew from " + heldAtOn[0] + " to " + heldAtOff[0]);
			int took = heldAtOff[0] - held;
			helper.assertTrue(took >= 0 && took <= unfinishedAtOff[0], "after free materials were switched off the Builder took " + took
				+ " item(s) for the " + unfinishedAtOff[0] + " block(s) still to place");
			noneOnTheGround(helper);
		});
	}

	// ------------------------------------------------------------------------------------------------- the rules' bounds

	/**
	 * The bounds the guide gives, through /gamerule as an operator types it: workplaceLevelGround takes 0 and 8 but not 9
	 * or -1, workplaceBuildDelay takes 1 but not 0, workplaceFoundationDepth takes 0 but not -1. A refused value leaves the
	 * rule as it was.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void gameruleCommandKeepsEachBuilderRuleInsideTheGuidesBounds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules rules = level.getGameRules();
		int ground = rules.getInt(ModGameRules.LEVEL_GROUND);
		int delay = rules.getInt(ModGameRules.BUILD_DELAY);
		int depth = rules.getInt(ModGameRules.FOUNDATION_DEPTH);
		Leftovers.after(helper, () -> {
			setInt(level, ModGameRules.LEVEL_GROUND, ground);
			setInt(level, ModGameRules.BUILD_DELAY, delay);
			setInt(level, ModGameRules.FOUNDATION_DEPTH, depth);
		});
		List<String> wrong = new ArrayList<>();
		check(helper, wrong, "workplaceLevelGround", 3, ModGameRules.LEVEL_GROUND, "8", 8);
		check(helper, wrong, "workplaceLevelGround", 8, ModGameRules.LEVEL_GROUND, "9", 8);
		check(helper, wrong, "workplaceLevelGround", 2, ModGameRules.LEVEL_GROUND, "-1", 2);
		check(helper, wrong, "workplaceLevelGround", 2, ModGameRules.LEVEL_GROUND, "0", 0);
		check(helper, wrong, "workplaceBuildDelay", 5, ModGameRules.BUILD_DELAY, "1", 1);
		check(helper, wrong, "workplaceBuildDelay", 5, ModGameRules.BUILD_DELAY, "0", 5);
		check(helper, wrong, "workplaceFoundationDepth", 12, ModGameRules.FOUNDATION_DEPTH, "0", 0);
		check(helper, wrong, "workplaceFoundationDepth", 12, ModGameRules.FOUNDATION_DEPTH, "-1", 12);
		helper.assertTrue(wrong.isEmpty(), "/gamerule let a builder rule out of its bounds: " + wrong);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, List<String> wrong, String name, int from, GameRules.Key<GameRules.IntegerValue> key,
		String typed, int expected) {
		ServerLevel level = helper.getLevel();
		setInt(level, key, from);
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(),
			"gamerule " + name + " " + typed);
		int now = level.getGameRules().getInt(key);
		if (now != expected) {
			wrong.add(name + " " + from + " then /gamerule " + typed + " gave " + now + ", expected " + expected);
		}
	}
}
