package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (1115b), mutants of MaterialRules: the Builder's "already built" check, hanging blocks, liquid pouring and
 * the extra costs read from block-entity data.
 */
public class QaMaterialRulesMutants1115bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	/** B865: the fresh stem a builder planted counts as the grown stem, and nothing else does. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void attachedStemMatchesOnlyItsOwnFreshStem(GameTestHelper helper) {
		BlockState attachedMelon = Blocks.ATTACHED_MELON_STEM.defaultBlockState();
		helper.assertTrue(MaterialRules.matches(Blocks.MELON_STEM.defaultBlockState(), attachedMelon), "a fresh melon stem isn't the grown one");
		helper.assertTrue(MaterialRules.matches(Blocks.PUMPKIN_STEM.defaultBlockState(), Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState()),
			"a fresh pumpkin stem isn't the grown one");
		helper.assertFalse(MaterialRules.matches(Blocks.STONE.defaultBlockState(), attachedMelon), "stone counts as a melon stem");
		helper.assertFalse(MaterialRules.matches(Blocks.AIR.defaultBlockState(), attachedMelon), "air counts as a melon stem");
		helper.assertFalse(MaterialRules.matches(Blocks.PUMPKIN_STEM.defaultBlockState(), attachedMelon), "a pumpkin stem counts as a melon stem");
		helper.succeed();
	}

	/** The final rule: same block, properties that matter equal means built; one that matters differing means not. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void sameBlockMatchesIgnoringNeighbourProperties(GameTestHelper helper) {
		BlockState wanted = Blocks.OAK_FENCE.defaultBlockState();
		BlockState joined = wanted.setValue(BlockStateProperties.NORTH, true);
		helper.assertTrue(MaterialRules.matches(joined, wanted), "a fence joined to a neighbour isn't the fence that was built");
		BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
		helper.assertFalse(MaterialRules.matches(stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), stairs),
			"stairs facing another way count as built");
		helper.assertTrue(MaterialRules.matches(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y)),
			"the same log doesn't match");
		helper.succeed();
	}

	/** Still water and lava are poured from a bucket (decoration); flowing water is not; a bubble column is water. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void sourceLiquidIsPouredFlowingIsNot(GameTestHelper helper) {
		helper.assertTrue(MaterialRules.classify(Blocks.WATER.defaultBlockState()) == MaterialRules.Kind.DECORATION, "still water isn't poured");
		helper.assertTrue(MaterialRules.classify(Blocks.LAVA.defaultBlockState()) == MaterialRules.Kind.DECORATION, "still lava isn't poured");
		helper.assertTrue(MaterialRules.classify(Blocks.BUBBLE_COLUMN.defaultBlockState()) == MaterialRules.Kind.DECORATION, "a bubble column isn't poured");
		BlockState flowing = Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3);
		helper.assertTrue(MaterialRules.classify(flowing) == MaterialRules.Kind.SKIP, "flowing water is built");
		helper.succeed();
	}

	/** Extra costs from block-entity data are added to a block that has an item of its own (a Mimic block's wrapped block). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void dataCostsAddToABlockWithItsOwnItem(GameTestHelper helper) {
		CompoundTag mimic = new CompoundTag();
		mimic.putString("Name", "minecraft:stone");
		CompoundTag nbt = new CompoundTag();
		nbt.put("Mimic", mimic);
		List<MaterialRules.Requirement> with = MaterialRules.requirements(Blocks.OAK_FENCE.defaultBlockState(), nbt);
		helper.assertTrue(with.contains(new MaterialRules.Requirement(Items.OAK_FENCE, 1)), "the fence itself isn't in " + with);
		helper.assertTrue(with.contains(new MaterialRules.Requirement(Items.STONE, 1)), "the wrapped stone isn't in " + with);
		helper.assertTrue(MaterialRules.requirements(Blocks.OAK_FENCE.defaultBlockState(), null).size() == 1, "no data, one item");
		helper.succeed();
	}

	/** B794: vines and a stalactite hang from the block above (built top-down); a stalagmite and a plain block do not. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyHangingBlocksGoTopDown(GameTestHelper helper) {
		BlockState drip = Blocks.POINTED_DRIPSTONE.defaultBlockState();
		helper.assertTrue(MaterialRules.hangsDown(drip.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)), "a stalactite doesn't hang");
		helper.assertFalse(MaterialRules.hangsDown(drip.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)), "a stalagmite hangs");
		helper.assertTrue(MaterialRules.hangsDown(Blocks.WEEPING_VINES.defaultBlockState()), "weeping vines don't hang");
		helper.assertFalse(MaterialRules.hangsDown(Blocks.STONE.defaultBlockState()), "stone hangs");
		helper.succeed();
	}
}
