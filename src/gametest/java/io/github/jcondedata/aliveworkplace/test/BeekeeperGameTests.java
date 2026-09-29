package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/** Beekeepers harvest the hives near their Apiary and keep them in flowers. */
public class BeekeeperGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos APIARY = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HIVE = new BlockPos(9, 3, 9);

	/** A full hive over a campfire gives a honey bottle for a glass bottle from the chest. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void beekeeperBottlesHoney(GameTestHelper helper) {
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		hive(helper, 5);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1 && chest.countItem(Items.GLASS_BOTTLE) == 1,
				"honey " + chest.countItem(Items.HONEY_BOTTLE) + ", bottles " + chest.countItem(Items.GLASS_BOTTLE));
			helper.assertTrue(helper.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is still full");
			helper.assertTrue(ModAttachments.HIVES_HARVESTED.getOrElse(keeper, 0) == 1, "harvests counted");
		});
	}

	/** With only shears, the hive gives three honeycomb and the shears come back worn. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void beekeeperShearsHoneycomb(GameTestHelper helper) {
		beekeeper(helper, new ItemStack(Items.SHEARS));
		hive(helper, 5);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 3, "honeycomb " + chest.countItem(Items.HONEYCOMB));
			boolean worn = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				worn |= chest.getItem(i).is(Items.SHEARS) && chest.getItem(i).getDamageValue() == 1;
			}
			helper.assertTrue(worn, "the shears didn't come back worn");
		});
	}

	/** Flowers from the chest go on the grass round a hive with too few near it. */
	//$ gametest_ticks AREA '2000'
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void beekeeperPlantsFlowers(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(7, 1, 7), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		hive(helper, 0);
		helper.succeedWhen(() -> {
			int flowers = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 1, 5), new BlockPos(13, 5, 13))) {
				flowers += helper.getBlockState(p).is(BlockTags.FLOWERS) ? 1 : 0;
			}
			helper.assertTrue(flowers >= 4, flowers + " flowers");
		});
	}

	private static Villager beekeeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(APIARY, ModBlocks.APIARY);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(APIARY), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		helper.assertTrue(BeekeeperWork.isBeekeeper(villager), "not a beekeeper");
		return villager;
	}

	/** A hive on a lit campfire (bees stay calm), with honey. */
	private static void hive(GameTestHelper helper, int honey) {
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(HIVE, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, honey));
	}
}
