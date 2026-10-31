package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0938), B593: B463's fix puts a stuck hauler's load back into a Drop Box, but its Expected also covers
 * a village with none ("the load goes back into the Drop Box (or the nearest open chest it came from) and the golem is
 * free"). With no Drop Box the golem stood holding the load for good.
 */
public class QaHaulerNoDropBoxGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] CHESTS = {new BlockPos(20, 2, 22), new BlockPos(24, 2, 22)};

	/**
	 * B463's other half: a village with no Drop Box. The golem fetches a lumberjack's logs (an open worker's chest) for
	 * the only Storehouse, which is walled in and roofed over. With no Storehouse to reach, the load goes back to "the
	 * nearest open chest it came from" (B463's Expected) and the golem is free, rather than standing with it for good.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerNoDropBox"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerNoDropBox")
	public void aHaulerWithNoDropBoxPutsALoadItCannotStoreBackInTheChestItCameFrom(GameTestHelper helper) {
		village(helper);
		for (BlockPos c : CHESTS) {
			((Container) helper.getBlockEntity(c)).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		}
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 1, 14)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], carried(golem)));
		helper.succeedWhen(() -> {
			helper.assertTrue(most[0] > 0, "the golem never fetched the lumberjacks' logs");
			int back = assertBack(helper, golem, 128);
			LOGGER.info("qa hauler no drop box: the load went back to the lumberjacks' chests ({} logs), the golem free", back);
		});
	}

	/**
	 * B593 after a reload: the load is saved on the golem but where it came from isn't. A golem that wakes up carrying
	 * 64 logs, with no Drop Box and its only Storehouse walled in, puts them into a village worker's chest with room.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerNoDropBoxReload"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerNoDropBoxReload")
	public void aHaulerLoadedWithGoodsAndNoDropBoxPutsThemInAWorkersChest(GameTestHelper helper) {
		village(helper);
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 1, 14)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> assertBack(helper, golem, 64));
	}

	/** The walled-in Storehouse and two lumberjacks with empty chests; no Drop Box. */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ServerLevel level = helper.getLevel();
		// workers share their chests across the village (off by default in GameTests)
		int radius = io.github.jcondedata.aliveworkplace.work.Village.RADIUS;
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 48;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius);
		// the only Storehouse, walled in and roofed over at x 1..8, z 1..7
		for (int x = 1; x <= 8; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 7 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
		helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		// two lumberjacks (a village needs one to look from and one to fetch from)
		for (BlockPos chest : CHESTS) {
			BlockPos block = chest.north();
			helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
			helper.setBlock(chest, Blocks.CHEST);
			Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
			jack.setNoAi(true);
			Jobs.employ(level, jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		}
	}

	/** The golem carries nothing, no log is in the walled-in Storehouse, and {@code expected} are in the lumberjacks' chests. */
	private static int assertBack(GameTestHelper helper, IronGolem golem, int expected) {
		helper.assertTrue(((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.OAK_LOG) == 0, "logs got into the walled-in Storehouse: the test proves nothing");
		int back = 0;
		for (BlockPos c : CHESTS) {
			back += ((Container) helper.getBlockEntity(c)).countItem(Items.OAK_LOG);
		}
		helper.assertTrue(carried(golem) == 0, "no Drop Box, the only Storehouse walled in: the golem stands with its load (" + carried(golem)
			+ " carried, " + back + " of " + expected + " logs in the lumberjacks' chests) at " + golem.blockPosition() + ", walking: " + golem.getNavigation().isInProgress());
		helper.assertTrue(back == expected, "the golem is free but only " + back + " of " + expected + " logs are back in a chest");
		return back;
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}
}
