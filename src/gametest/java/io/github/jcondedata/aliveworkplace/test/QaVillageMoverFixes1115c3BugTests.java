package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA bug tests (lane c, 2026-11-15, pass 3), NOT registered: they fail on main. B1274's fix works snow layers out as
 * broken with Silk Touch by a marker; at 8 layers vanilla's Silk Touch loot is one snow block, and MoveSite.eachOfMany
 * then adds one snow for each of the other 7 layers (it finds no snow item in the whole block's drop), so 8 snow layers on
 * a crate drop as a snow block plus 7 snow. moving-buildings.md: "A block that is several of its item drops all of them"
 * and never twice: 8 layers are 8 snow (or, as vanilla's Silk Touch, the one snow block), never both.
 * <p>
 * B1275's fix (a block with DOUBLE_BLOCK_HALF, no block entity and an item drops as its own item) also catches a grown
 * pitcher crop, whose block item is the pitcher pod: it comes back as a pod instead of the pitcher plant.
 */
public class QaVillageMoverFixes1115c3BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.is(item) ? s.getCount() : 0;
		}
		return n;
	}

	/** 8 snow layers worked out as dropped on a crate (MoveSite.itself): 8 snow, or one snow block, never a mix. */
	//$ gametest_batch HUGE '"qaC3BugSnowEight"'
	@GameTest(template = HUGE, batch = "qaC3BugSnowEight")
	public void eightSnowLayersOnACrateAreEightSnowNotABlockAndSeven(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, helper.absolutePos(new BlockPos(2, 2, 2)),
			Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8));
		int snow = count(drops, Items.SNOW);
		int blocks = count(drops, Items.SNOW_BLOCK);
		helper.assertTrue(snow == 8 && blocks == 0 && drops.size() == 8 || snow == 0 && blocks == 1 && drops.size() == 1,
			"8 snow layers on a crate dropped as " + drops + " (" + blocks + " snow block(s) and " + snow + " snow): expected 8 snow (or one snow block), never both");
		helper.succeed();
	}

	/** 8 snow layers on his crate in a full move: 8 snow (or one snow block), never both, and nothing left standing. */
	//$ gametest_batch HUGE '"qaC3BugSnowEightMove"'
	@GameTest(template = HUGE, batch = "qaC3BugSnowEightMove")
	public void eightSnowLayersOnHisCrateInAMoveAreEightSnow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac3bugsnow8");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "8 snow layers on his crate");
			int snow = QaMoverCrateDrop1114eGameTests.count(helper, Items.SNOW);
			int blocks = QaMoverCrateDrop1114eGameTests.count(helper, Items.SNOW_BLOCK);
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, "snowballs from 8 snow layers");
			helper.assertTrue(snow == 8 && blocks == 0 || snow == 0 && blocks == 1, "8 snow layers on his crate came back as " + blocks
				+ " snow block(s) and " + snow + " snow: expected 8 snow (or one snow block), never both");
			helper.succeed();
		});
	}

	/**
	 * B1275's fix, a regression: a fully grown pitcher crop (age 4, two blocks tall) with its lower half dropped on a crate
	 * is worked out as the block's own item, a pitcher pod (the seed), though a player breaking it gets the pitcher plant
	 * (vanilla's loot, and what MoveSite gave before the fix). A grown plant must not come back as its seed: always one
	 * pitcher plant. Tried 16 times (the old loot had no roll for it; the new rule has none either).
	 */
	//$ gametest_batch HUGE '"qaC3BugPitcherCrop"'
	@GameTest(template = HUGE, batch = "qaC3BugPitcherCrop")
	public void aGrownPitcherCropOnACrateIsAPitcherPlantNotAPod(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockState lo = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 4);
		BlockState up = lo.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
		level.setBlock(lower.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(lower, lo, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower.above(), up, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.assertTrue(Block.getDrops(lo, level, lower, null).stream().anyMatch(st -> st.is(Items.PITCHER_PLANT)),
			"setup: a player breaking the grown pitcher crop doesn't get a pitcher plant: " + Block.getDrops(lo, level, lower, null));
		for (int i = 0; i < 16; i++) {
			List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, lower, lo);
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.PITCHER_PLANT) && drops.get(0).getCount() == 1,
				"a grown pitcher crop's lower half on a crate dropped as " + drops + ", not one pitcher plant (the grown plant came back as its seed)");
		}
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.succeed();
	}
}
