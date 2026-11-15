package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bug test (lane c, 2026-11-15), NOT registered: fails on main. B1258's fix (MoveSite.eachOfMany) names snow layers,
 * and moving-buildings.md says a block that is several of its item drops all of them, but 4 snow layers on his crate
 * drop as 1 snow item: vanilla's snow loot table has a pool condition {@code entity_properties this {}} (it needs a
 * breaking entity), MoveSite.itself asks with no entity, so the Silk Touch and bare-hand loot both give nothing, the
 * fallback is one snow item, and eachOfMany sees no item for one layer either, so it adds nothing. Same house, Mover and
 * spot as MoverCrateNoRoomB1234GameTests.
 * <p>
 * Tall grass (B1260's two-block things): with its UPPER half on the crate and its lower half already put up under it, the
 * drop is tall grass's own loot, which for the upper half (lower half below) is wheat seeds 1 time in 8 (random_chance
 * 0.125; Silk Touch isn't shears), rolled twice (the Silk Touch loot, then the bare-hand loot), so about 1 move in 4 the
 * whole tall grass comes back as one wheat seed (14 of 64 drops here), and the others as one tall grass item (MoveSite.itself's own-item fallback). With its LOWER half on the crate it is always a tall grass
 * item. Seen in a full move (QaMoverCrateFixes1115cGameTests' first run: "0 minecraft:tall_grass round the test, not 1").
 */
public class QaMoverCrateFixes1115cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * Four snow layers on his crate: a player breaking them with Silk Touch gets 4 snow (vanilla's loot), so 4 snow must
	 * lie there (nothing packed is deleted), and no snowballs as well.
	 */
	//$ gametest_batch AREA '"qaC1115cSnowLayers"'
	@GameTest(template = AREA, batch = "qaC1115cSnowLayers")
	public void fourSnowLayersOnHisCrateAreFourSnow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qac1115csnow");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
		helper.runAfterDelay(2, () -> {
			BlockState snow = level.getBlockState(helper.absolutePos(new BlockPos(4, 3, 4)));
			helper.assertTrue(snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == 4, "setup: the snow is now " + snow);
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "four snow layers on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, "snowballs from the snow layers");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW, 4, "the block's four snow layers");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, "snow left standing");
			helper.succeed();
		});
	}

	/**
	 * Tall grass's upper half dropped on a crate with its lower half standing under it, worked out 64 times as MoveSite
	 * does (MoveSite.itself): the whole tall grass is one tall grass item every time, as moving-buildings.md says ("drops
	 * once, as the whole thing's one item"), never a wheat seed by chance. About 1 drop in 4 is a seed, so 64 drops all
	 * pass by luck far less than once in a million runs.
	 */
	//$ gametest_batch AREA '"qaC1115cTallGrassUpper"'
	@GameTest(template = AREA, batch = "qaC1115cTallGrassUpper")
	public void tallGrassWithItsUpperHalfOnACrateIsAlwaysOneTallGrass(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
		level.setBlock(lower, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		BlockState upper = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, lower.above(), upper);
			if (drops.size() != 1 || !drops.get(0).is(Items.TALL_GRASS) || drops.get(0).getCount() != 1) {
				wrong.add(drops.toString());
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of 64 tall grass drops weren't one tall grass: " + wrong);
		helper.succeed();
	}
}
