package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31, lane-c-1031-2153) of B627 from its spec ("every worker's chest with a trip's worth of goods is
 * hauled"), at the edge of the village the hauler serves: a worker whose workstation is exactly the village radius from
 * the Storehouse is one of its workers and is listed; one a block further isn't. A baby (no worker) never is, and a
 * worker's chest that holds nothing is still listed (the trip's-worth check is the hauler's, not the list's).
 */
public class QaHaulerReach1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 4);
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);

	private static Villager lumberjack(GameTestHelper helper, BlockPos block, int logs) {
		BlockPos chest = block.south();
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(chest, Blocks.CHEST);
		if (logs > 0) {
			((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.OAK_LOG, logs));
		}
		Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
		jack.setNoAi(true);
		Jobs.employ(helper.getLevel(), jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		return jack;
	}

	private static boolean listed(List<Village.Stash> stashes, BlockPos chest) {
		return stashes.stream().anyMatch(s -> s.chests().contains(chest));
	}

	/** Village radius 20: chopping blocks 20 east (listed) and 21 south (not) of the Storehouse; a baby's chest never. */
	//$ gametest_batch HUGE '"qaHaulerReach1031"'
	@GameTest(template = HUGE, batch = "qaHaulerReach1031")
	public void qaTheHaulersWorkersReachExactlyTheVillageRadius(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		int radius = Village.RADIUS;
		Village.RADIUS = 20;
		Leftovers.after(helper, () -> Village.RADIUS = radius);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);

		BlockPos atEdge = new BlockPos(23, 2, 4); // 20 east: on the edge, inside
		BlockPos past = new BlockPos(3, 2, 25); // 21 south: one past it
		BlockPos empty = new BlockPos(12, 2, 14); // well inside, an empty chest
		lumberjack(helper, atEdge, 64);
		lumberjack(helper, past, 64);
		lumberjack(helper, empty, 0);
		Villager baby = lumberjack(helper, new BlockPos(12, 2, 22), 64);
		baby.setAge(-24000);

		List<Village.Stash> stashes = Village.workerStashes(helper.getLevel(), helper.absolutePos(STOREHOUSE), null, null);
		helper.assertTrue(listed(stashes, helper.absolutePos(atEdge.south())),
			"a lumberjack whose chopping block is exactly the village radius (20) from the Storehouse isn't one of the hauler's workers: " + stashes);
		helper.assertFalse(listed(stashes, helper.absolutePos(past.south())),
			"a lumberjack whose chopping block is 21 blocks from the Storehouse (radius 20) is listed for its hauler: " + stashes);
		helper.assertTrue(listed(stashes, helper.absolutePos(empty.south())),
			"a lumberjack with an empty chest isn't listed at all (the hauler skips it for want of goods, the list shouldn't): " + stashes);
		helper.assertFalse(listed(stashes, helper.absolutePos(new BlockPos(12, 2, 23))),
			"a baby lumberjack's chest is listed as a worker's: " + stashes);
		helper.succeed();
	}
}
