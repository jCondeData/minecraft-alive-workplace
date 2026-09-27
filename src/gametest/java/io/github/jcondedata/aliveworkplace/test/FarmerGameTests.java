package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Farmers looking after a marked field on a real (headless) server. */
public class FarmerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager farmer(GameTestHelper helper, BlockPos corner1, BlockPos corner2, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(corner1), helper.absolutePos(corner2)));
		return villager;
	}

	/** Ripe wheat is cut, wheat goes back in the ground at once, the harvest ends up in the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerHarvestsAndReplants(GameTestHelper helper) {
		BlockPos water = new BlockPos(10, 1, 10);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 12))) {
			if (p.equals(water)) {
				helper.setBlock(p, Blocks.WATER);
			} else {
				helper.setBlock(p, Blocks.FARMLAND);
				helper.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			}
		}
		Villager villager = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(12, 1, 12));
		helper.succeedWhen(() -> {
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 2, 8), new BlockPos(12, 2, 12))) {
				if (!p.below().equals(water)) {
					helper.assertBlockPresent(Blocks.WHEAT, p);
				}
			}
			int harvested = villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0);
			helper.assertTrue(harvested >= 24, "only " + harvested + " harvested");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.WHEAT) >= 20, "only " + chest.countItem(Items.WHEAT) + " wheat in the chest");
		});
	}

	/** Bare grass is tilled with the hoe from the chest and sown with the carrots from the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerTillsAndSowsFromTheChest(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(11, 1, 9), Blocks.WATER);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.SHORT_GRASS);
		farmer(helper, new BlockPos(8, 1, 8), new BlockPos(10, 1, 10), new ItemStack(Items.STONE_HOE), new ItemStack(Items.CARROT, 16));
		helper.succeedWhen(() -> {
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
				helper.assertBlockPresent(Blocks.FARMLAND, p);
				helper.assertBlockPresent(Blocks.CARROTS, p.above());
			}
		});
	}

	/** Sugar cane is cut down to its bottom block, which is left to grow back. */
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void farmerCutsSugarCane(GameTestHelper helper) {
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(8, 1, 9), Blocks.WATER);
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(8, y, 8), Blocks.SUGAR_CANE);
		}
		farmer(helper, new BlockPos(7, 1, 7), new BlockPos(9, 1, 9));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SUGAR_CANE, new BlockPos(8, 2, 8));
			helper.assertBlockNotPresent(Blocks.SUGAR_CANE, new BlockPos(8, 3, 8));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 2, chest.countItem(Items.SUGAR_CANE) + " sugar cane in the chest");
		});
	}
}
