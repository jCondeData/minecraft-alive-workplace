package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug test (qa-lane-b-1031-1238) on B450: its text names "across water" as a way a Storehouse can't be reached, and
 * expects the golem to try the next reachable one and keep hauling. Here the nearest Storehouse stands on an island in a
 * moat two deep with steep banks: the golem walks into the moat, can't climb out, gives up both Storehouses from the
 * bottom and stands there with its whole load and no target.
 */
public class QaHaulerMoatBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos START = new BlockPos(12, 2, 12);

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	private static int in(GameTestHelper helper, BlockPos chest, Item item) {
		Container c = helper.getBlockEntity(chest);
		return c.countItem(item);
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(START.below()), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/**
	 * B450's other example, "across water": the nearest Storehouse stands on a small island in a moat two deep and three
	 * wide. Whether the golem wades across or gives it up, it must never stand with its load: within the timeout every log
	 * is in a Storehouse's chest (the island's or the open one's) and the golem carries nothing.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaHaulerMoat"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaHaulerMoat")
	public void aHaulerWhoseNearestStorehouseIsAcrossWaterStillGetsItsLoadHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				boolean island = x >= 4 && x <= 6 && z >= 4 && z <= 6;
				boolean moat = !island && x >= 1 && x <= 9 && z >= 1 && z <= 9;
				for (int y = 0; y <= 1; y++) {
					helper.setBlock(new BlockPos(x, y, z), moat ? Blocks.WATER : Blocks.STONE);
				}
			}
		}
		BlockPos isle = new BlockPos(5, 2, 5);
		BlockPos isleChest = new BlockPos(5, 2, 4);
		BlockPos open = new BlockPos(24, 2, 24);
		BlockPos openChest = new BlockPos(25, 2, 24);
		helper.setBlock(isle, ModBlocks.STOREHOUSE);
		helper.setBlock(isleChest, Blocks.CHEST);
		helper.setBlock(open, ModBlocks.STOREHOUSE);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(helper.absolutePos(isle).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			"the island's Storehouse isn't the hauler's nearest: the test proves nothing; it heads for " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			int stored = in(helper, isleChest, Items.OAK_LOG) + in(helper, openChest, Items.OAK_LOG);
			helper.assertTrue(stored == 64, "with the nearest Storehouse across water the chests hold " + stored + " of 64 logs (island "
				+ in(helper, isleChest, Items.OAK_LOG) + ", open " + in(helper, openChest, Items.OAK_LOG) + "); the golem carries " + carried(golem)
				+ " at " + golem.position() + ", heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}

}
