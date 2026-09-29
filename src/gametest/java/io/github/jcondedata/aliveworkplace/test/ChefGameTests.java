package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Chefs cook what the chests have the makings for. */
public class ChefGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Wheat becomes bread, raw beef cooked beef; the other dishes wait for their makings. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void chefCooksBreadAndBeef(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.WHEAT, 9));
		chest.setItem(1, new ItemStack(Items.BEEF, 4));
		chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 20)); // not food: left alone
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.BREAD) == 3 && chest.countItem(Items.WHEAT) == 0,
				chest.countItem(Items.BREAD) + " bread, " + chest.countItem(Items.WHEAT) + " wheat left");
			helper.assertTrue(chest.countItem(Items.COOKED_BEEF) == 4 && chest.countItem(Items.BEEF) == 0,
				chest.countItem(Items.COOKED_BEEF) + " cooked beef, " + chest.countItem(Items.BEEF) + " raw left");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 20, "the chef used the planks");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 7, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
		});
	}
}
