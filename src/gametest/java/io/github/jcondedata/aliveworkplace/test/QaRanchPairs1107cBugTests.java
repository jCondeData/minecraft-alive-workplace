package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane qa-lane-c-1107-1853), B979 follow-up: a pair is fed only when there's food for both, so exactly 2 golden carrots
 * for 2 ready horses standing together must feed both (and give a foal), never just one.
 */
public class QaRanchPairs1107cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TROUGH = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);

	/** Two tamed horses 3 apart and exactly 2 golden carrots: both are fed, none is left, and they have a foal. */
	//$ gametest_ticks_batch AREA '2000' '"qaRanchPairTwoCarrots"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRanchPairTwoCarrots")
	public void rancherFeedsBothOfAPairWithExactlyTwoCarrots(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 5));
		Horse b = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 8));
		for (Horse h : new Horse[] {a, b}) {
			h.setTamed(true);
			h.setNoAi(true);
		}
		helper.setDayTime(2000);
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.GOLDEN_CARROT, 2));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		helper.assertTrue(RancherWork.isRancher(villager), "not a rancher");
		boolean[] letGo = {false};
		boolean[] fed = {false, false};
		helper.onEachTick(() -> {
			fed[0] |= a.isInLove();
			fed[1] |= b.isInLove();
			if (!letGo[0] && a.isInLove() && b.isInLove()) {
				letGo[0] = true;
				a.setNoAi(false);
				b.setNoAi(false);
			}
		});
		helper.runAtTickTime(1500, () -> {
			long foals = helper.getEntities(EntityType.HORSE).stream().filter(Horse::isBaby).count();
			helper.assertTrue(letGo[0] && foals >= 1, "with exactly 2 golden carrots: horse A fed " + fed[0] + ", horse B fed " + fed[1] + ", foals " + foals + ", carrots left in the chest " + chest.countItem(Items.GOLDEN_CARROT));
			helper.succeed();
		});
	}
}
