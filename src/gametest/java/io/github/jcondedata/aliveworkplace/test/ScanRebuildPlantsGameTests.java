package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B793, B794, B795: Scan Tool rebuilds of blocks with no item of their own (a cake with a candle, the stem pieces of
 * hanging vines) and of a pushed-out piston, each scanned with real clicks and built again by a builder at a bench. The
 * first test of each bug is QA's (QaBuilders1102bBugTests on tests/scan-rebuild-1102, lane-b-1102-0038).
 */
public class ScanRebuildPlantsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	/** B793 (QA): a cake with a candle, scanned and built from a chest holding its material list, stands there again. */
	//$ gametest_ticks_batch HUGE '2400' '"b793CandleCake"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b793CandleCake")
	public void aScannedCakeWithACandleIsBuiltAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.RED_CANDLE_CAKE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above());
		helper.setBlock(SRC.above(), Blocks.AIR);
		helper.setBlock(SRC, Blocks.AIR);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.CAKE, 0) == 1 && asked.getOrDefault(Items.RED_CANDLE, 0) == 1,
			"the material list of a cake with a red candle asks " + asked + ", not a cake and a red candle");
		fill(helper, asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " missing " + b.site().missing());
			BlockState top = level.getBlockState(b.origin().above());
			helper.assertTrue(top.is(Blocks.RED_CANDLE_CAKE), "the cake with a candle was built as " + top + " (" + b.site().skipped() + " skipped)");
		});
	}

	/** B793: with no candle in the chest, the builder waits for one and says so; it never builds a bare cake for free. */
	//$ gametest_ticks_batch HUGE '1200' '"b793NoCandle"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "b793NoCandle")
	public void aCakeWithACandleWaitsForTheCandle(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.CANDLE_CAKE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above());
		helper.setBlock(SRC.above(), Blocks.AIR);
		helper.setBlock(SRC, Blocks.AIR);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		chest.setItem(0, new ItemStack(Items.STONE, 4));
		chest.setItem(1, new ItemStack(Items.CAKE, 2));
		chest.setItem(2, new ItemStack(Items.DIRT, 64));
		helper.startSequence().thenWaitUntil(() -> {
			if (!b.site().missing().containsKey(Items.CANDLE)) {
				throw new GameTestAssertException("the builder doesn't wait for the candle: missing " + b.site().missing() + ", " + b.site().stage());
			}
		}).thenExecute(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) != null, "the build ended without its candle");
			BlockState top = level.getBlockState(b.origin().above());
			helper.assertFalse(top.is(Blocks.CAKE) || top.is(Blocks.CANDLE_CAKE), "a cake went down without its candle being paid: " + top);
		}).thenSucceed();
	}

	/** B794 (QA): cave vines and weeping vines three long under a ceiling are built three long again. */
	//$ gametest_ticks_batch HUGE '2400' '"b794HangingVines"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b794HangingVines")
	public void scannedHangingVinesThreeLongAreBuiltThreeLong(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx : new int[] {0, 2}) {
			for (int dy = 0; dy < 4; dy++) {
				helper.setBlock(SRC.offset(dx, dy, 1), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
			}
			helper.setBlock(SRC.offset(dx, 4, 1), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
			helper.setBlock(SRC.offset(dx, 4, 0), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
		}
		helper.setBlock(SRC.offset(0, 3, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 2, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 1, 0), Blocks.CAVE_VINES);
		helper.setBlock(SRC.offset(2, 3, 0), Blocks.WEEPING_VINES_PLANT);
		helper.setBlock(SRC.offset(2, 2, 0), Blocks.WEEPING_VINES_PLANT);
		helper.setBlock(SRC.offset(2, 1, 0), Blocks.WEEPING_VINES);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(2, 4, 1));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.GLOW_BERRIES, 0) == 3 && asked.getOrDefault(Items.WEEPING_VINES, 0) == 3,
			"the material list of two vines three long asks " + asked + ", not 3 glow berries and 3 weeping vines");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			int cave = 0;
			int weeping = 0;
			for (int dy = 1; dy <= 3; dy++) {
				BlockState c = level.getBlockState(b.origin().offset(0, dy, 0));
				BlockState w = level.getBlockState(b.origin().offset(2, dy, 0));
				cave += c.is(Blocks.CAVE_VINES) || c.is(Blocks.CAVE_VINES_PLANT) ? 1 : 0;
				weeping += w.is(Blocks.WEEPING_VINES) || w.is(Blocks.WEEPING_VINES_PLANT) ? 1 : 0;
			}
			helper.assertTrue(cave == 3 && weeping == 3, "the vines three long were built " + cave + " (cave) and " + weeping + " (weeping) long ("
				+ b.site().skipped() + " skipped)");
			helper.assertTrue(level.getBlockState(b.origin().offset(0, 1, 0)).is(Blocks.CAVE_VINES)
				&& level.getBlockState(b.origin().offset(0, 3, 0)).is(Blocks.CAVE_VINES_PLANT), "the cave vines' tip isn't at the bottom");
			helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
		});
	}

	/** B794: twisting vines growing three tall from the ground (stem pieces too) are built three tall from twisting vines. */
	//$ gametest_ticks_batch HUGE '2400' '"b794TwistingVines"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b794TwistingVines")
	public void scannedTwistingVinesThreeTallAreBuiltThreeTall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.NETHERRACK);
		helper.setBlock(SRC.above(1), Blocks.TWISTING_VINES_PLANT);
		helper.setBlock(SRC.above(2), Blocks.TWISTING_VINES_PLANT);
		helper.setBlock(SRC.above(3), Blocks.TWISTING_VINES);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above(3));
		for (int dy = 3; dy >= 0; dy--) {
			helper.setBlock(SRC.above(dy), Blocks.AIR);
		}
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		helper.assertTrue(b.site().plan(level).materials().getOrDefault(Items.TWISTING_VINES, 0) == 3,
			"the material list asks " + b.site().plan(level).materials() + ", not 3 twisting vines");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage());
			for (int dy = 1; dy <= 3; dy++) {
				BlockState s = level.getBlockState(b.origin().above(dy));
				helper.assertTrue(s.is(Blocks.TWISTING_VINES) || s.is(Blocks.TWISTING_VINES_PLANT), "twisting vines piece " + dy + " was built as " + s);
			}
		});
	}

	/** B795 (QA): a sticky piston pushed out by a redstone block is built pushed out with its head. */
	//$ gametest_ticks_batch HUGE '2400' '"b795Piston"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b795Piston")
	public void aScannedPushedOutPistonIsBuiltWithItsHead(GameTestHelper helper) {
		piston(helper, true);
	}

	/** B795: the same piston scanned pushed out, built without its redstone block, stands retracted (no stuck base). */
	//$ gametest_ticks_batch HUGE '2400' '"b795PistonUnpowered"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b795PistonUnpowered")
	public void aPushedOutPistonBuiltWithoutItsPowerStandsRetracted(GameTestHelper helper) {
		piston(helper, false);
	}

	private static void piston(GameTestHelper helper, boolean keepPower) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.STONE);
		}
		helper.setBlock(SRC.above(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(SRC.east(1).above(), Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		QaBuilders1102bGameTests.Built[] b = new QaBuilders1102bGameTests.Built[1];
		helper.startSequence().thenWaitUntil(() -> {
			if (!level.getBlockState(helper.absolutePos(SRC.east(2).above())).is(Blocks.PISTON_HEAD)) {
				throw new GameTestAssertException("setup: the piston hasn't pushed out yet");
			}
		}).thenExecute(() -> {
			// Without power: the scan's box leaves the redstone block out
			ResourceLocation id = QaBuilders1102bGameTests.scan(helper, keepPower ? SRC : SRC.east(1), SRC.east(2).above());
			helper.setBlock(SRC.east(2).above(), Blocks.AIR);
			helper.setBlock(SRC.east(1).above(), Blocks.AIR);
			helper.setBlock(SRC.above(), Blocks.AIR);
			b[0] = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		}).thenWaitUntil(() -> {
			if (BuildSiteManager.get(level).get(b[0].site().id()) != null) {
				throw new GameTestAssertException("still building: " + b[0].site().stage() + " " + b[0].site().status());
			}
		}).thenIdle(10).thenExecute(() -> {
			int at = keepPower ? 1 : 0;
			BlockState base = level.getBlockState(b[0].origin().offset(at, 1, 0));
			BlockState front = level.getBlockState(b[0].origin().offset(at + 1, 1, 0));
			helper.assertTrue(base.is(Blocks.STICKY_PISTON) && base.getValue(PistonBaseBlock.FACING) == Direction.EAST, "the piston was built as " + base);
			if (keepPower) {
				helper.assertTrue(base.getValue(PistonBaseBlock.EXTENDED) && front.is(Blocks.PISTON_HEAD), "the pushed-out piston was built as " + base
					+ " with " + front + " in front");
			} else {
				helper.assertTrue(!base.getValue(PistonBaseBlock.EXTENDED) && front.isAir(), "the unpowered piston was built as " + base + " with " + front
					+ " in front: a stuck pushed-out base");
			}
		}).thenSucceed();
	}

	/**
	 * B812: a big dripleaf on three stems facing east, scanned, is built four tall facing east from a chest holding exactly
	 * its list (four big dripleaves): each stem goes down as a leaf and turns into a stem under the next piece, so nothing is
	 * skipped and nothing is left over or short.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"b812DripleafPaid"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "b812DripleafPaid")
	public void aScannedBigDripleafFourTallIsBuiltFromItsListFacingItsWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockState stem = Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.BigDripleafStemBlock.FACING, Direction.EAST);
		helper.setBlock(SRC, Blocks.MOSS_BLOCK);
		for (int dy = 1; dy <= 3; dy++) {
			helper.setBlock(SRC.above(dy), stem);
		}
		helper.setBlock(SRC.above(4), Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(net.minecraft.world.level.block.BigDripleafBlock.FACING, Direction.EAST));
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above(4));
		for (int dy = 4; dy >= 0; dy--) {
			helper.setBlock(SRC.above(dy), Blocks.AIR);
		}
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.BIG_DRIPLEAF, 0) == 4, "a big dripleaf on three stems asks " + asked + ", not 4 big dripleaves");
		fill(helper, asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " missing " + b.site().missing());
			for (int dy = 1; dy <= 4; dy++) {
				BlockState s = level.getBlockState(b.origin().above(dy));
				helper.assertTrue(s.is(dy < 4 ? Blocks.BIG_DRIPLEAF_STEM : Blocks.BIG_DRIPLEAF), "piece " + dy + " was built as " + s
					+ " (" + b.site().skipped() + " skipped)");
				helper.assertTrue(s.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == Direction.EAST, "piece " + dy + " faces " + s);
			}
			helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
		});
	}

	/**
	 * B812: a big dripleaf column stopped partway (a save, a reload, the builder called away) has a leaf on its top stem's
	 * spot; that counts as built, so the build goes on above it instead of breaking it, and a stem with its stem or leaf
	 * already above it goes down as a stem. A leaf facing the other way is not the stem that was planned.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBigDripleafColumnStoppedPartwayGoesOnFromItsTop(GameTestHelper helper) {
		BlockState stem = Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.BigDripleafStemBlock.FACING, Direction.SOUTH);
		BlockState leaf = Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(net.minecraft.world.level.block.BigDripleafBlock.FACING, Direction.SOUTH);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(leaf, stem), "a leaf on a planned stem's spot doesn't count as built");
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(
			leaf.setValue(net.minecraft.world.level.block.BigDripleafBlock.FACING, Direction.NORTH), stem), "a leaf facing north counts as a stem facing south");
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(stem, leaf), "a stem counts as the planned leaf");
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos.below(), Blocks.MOSS_BLOCK);
		BlockPos abs = helper.absolutePos(pos);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.MaterialRules.grownAs(helper.getLevel(), abs, stem).equals(leaf),
			"a stem with nothing above it doesn't go down as a leaf");
		helper.setBlock(pos.above(), leaf);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.MaterialRules.grownAs(helper.getLevel(), abs, stem) == stem,
			"a stem under its leaf doesn't go down as a stem");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.MaterialRules.grownAs(helper.getLevel(), abs, leaf) == leaf, "a leaf isn't put down as planned");
		helper.succeed();
	}

	/** Fills the supply chest with exactly {@code asked} (and dirt for the foundation). */
	private static void fill(GameTestHelper helper, Map<Item, Integer> asked) {
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		chest.setItem(slot, new ItemStack(Items.DIRT, 64));
	}
}
