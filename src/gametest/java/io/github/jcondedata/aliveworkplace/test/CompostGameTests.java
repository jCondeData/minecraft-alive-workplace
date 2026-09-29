package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.compost.CompostWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
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

/** Composters: the village's scraps into bone meal. */
public class CompostGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BIN = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager composter(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(BIN, ModBlocks.COMPOST_BIN);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(BIN), ModVillagers.COMPOST_BIN_POI, ModVillagers.COMPOSTER);
		return villager;
	}

	/** What composts and how much: the vanilla composter's chances as shares, rotten flesh half a layer, stone nothing. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void whatComposts(GameTestHelper helper) {
		helper.assertTrue(Math.abs(CompostWork.layers(new ItemStack(Items.WHEAT_SEEDS)) - 0.3f) < 0.001f, "seeds");
		helper.assertTrue(CompostWork.layers(new ItemStack(Items.ROTTEN_FLESH)) == 0.5f, "rotten flesh");
		helper.assertTrue(CompostWork.layers(new ItemStack(Items.PUMPKIN_PIE)) == 1f, "pie");
		helper.assertFalse(CompostWork.isCompostable(new ItemStack(Items.STONE)), "stone composts");
		helper.succeed();
	}

	/** Ten rotten flesh make five layers: one bone meal in the chest; with nothing left, the composter asks for scraps. */
	@GameTest(template = AREA)
	public void rottenFleshBecomesBoneMeal(GameTestHelper helper) {
		Villager composter = composter(helper, new ItemStack(Items.ROTTEN_FLESH, 10));
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < 10; i++) {
			helper.assertTrue(CompostWork.compost(helper.getLevel(), composter, helper.absolutePos(BIN)), "nothing to compost at " + i);
		}
		helper.assertTrue(chest.countItem(Items.ROTTEN_FLESH) == 0 && chest.countItem(Items.BONE_MEAL) == 1,
			"flesh " + chest.countItem(Items.ROTTEN_FLESH) + ", bone meal " + chest.countItem(Items.BONE_MEAL));
		helper.assertTrue(composter.getAttachedOrElse(ModAttachments.BONE_MEAL_MADE, 0) == 1, "counted");
		helper.assertFalse(CompostWork.compost(helper.getLevel(), composter, helper.absolutePos(BIN)), "composted air");
		helper.assertTrue(!Requests.of(helper.getLevel(), composter).isEmpty(), "no request for scraps");
		helper.succeed();
	}

	/** At work, a composter composts on their own. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void aComposterAtWork(GameTestHelper helper) {
		composter(helper, new ItemStack(Items.KELP, 20));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> helper.assertTrue(chest.countItem(Items.KELP) <= 16, "kelp left: " + chest.countItem(Items.KELP)));
	}
}
