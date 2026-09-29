package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.flower.FloristWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Florists grow flowers round their Flower Stand and fill the empty flower pots nearby. */
public class FloristGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STAND = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);

	/** Bone meal on the grass of the garden brings up flowers; they're picked into the chest. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void floristGrowsAndPicksFlowers(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(7, 1, 7), new BlockPos(15, 1, 10))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		Villager florist = florist(helper, new ItemStack(Items.BONE_MEAL, 16));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			int flowers = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				flowers += chest.getItem(i).is(ItemTags.SMALL_FLOWERS) ? chest.getItem(i).getCount() : 0;
			}
			helper.assertTrue(flowers >= 3, flowers + " flowers in the chest");
			helper.assertTrue(ModAttachments.FLOWERS_GROWN.getOrElse(florist, 0) >= 3, "flowers grown");
		});
	}

	/** Bone meal on a sunflower gives another sunflower, which goes in the chest. */
	//$ gametest_ticks AREA '2000'
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void floristGrowsTallFlowers(GameTestHelper helper) {
		helper.setBlock(new BlockPos(13, 1, 11), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(13, 3, 11), Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		florist(helper, new ItemStack(Items.BONE_MEAL, 4));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SUNFLOWER) >= 1, "no sunflower in the chest");
			helper.assertBlockPresent(Blocks.SUNFLOWER, new BlockPos(13, 2, 11));
		});
	}

	/** An empty flower pot nearby gets a flower from the chest. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void floristFillsFlowerPots(GameTestHelper helper) {
		helper.setBlock(new BlockPos(18, 2, 18), Blocks.FLOWER_POT);
		florist(helper, new ItemStack(Items.POPPY, 4));
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.POTTED_POPPY, new BlockPos(18, 2, 18)));
	}

	private static Villager florist(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(STAND, ModBlocks.FLOWER_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(STAND), ModVillagers.FLOWER_STAND_POI, ModVillagers.FLORIST);
		helper.assertTrue(FloristWork.isFlorist(villager), "not a florist");
		return villager;
	}
}
