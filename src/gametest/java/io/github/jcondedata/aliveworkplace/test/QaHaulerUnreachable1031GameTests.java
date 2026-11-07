package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1031-1238) on B450 and B463 (Hauler Golems, 29.15), from their text: a golem whose nearest Storehouse
 * "can't be reached (walled off, across water)" must "after a while [try] the next reachable Storehouse ... and [keep]
 * hauling"; with no reachable one "the load goes back into the Drop Box ... and the golem is free". The existing tests
 * (GolemHaulerRouteGameTests, QaHaulerGiveUpGameTests, QaHaulerNoDropBoxGameTests) cover one walled-in Storehouse, its
 * wait and a reload; these cover two unreachable ones in a row, one out of reach upward (a pillar, not a wall), and the
 * golem really free after putting the load back: it hauls that same box again once a Storehouse it can reach appears.
 */
public class QaHaulerUnreachable1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos START = new BlockPos(12, 2, 12);

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	private static int in(GameTestHelper helper, BlockPos chest, Item item) {
		Container c = helper.getBlockEntity(chest);
		return c.countItem(item);
	}

	/** Walls in and roofs over x {@code x0..x1}, z {@code z0..z1} (inside: y 2..3). */
	private static void wallIn(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = z0; z <= z1; z++) {
					boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(START.below()), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/**
	 * Two Storehouses walled in, both nearer than the open one: the golem gives up the nearest, then the next (also
	 * unreachable), and still gets the whole load to the open one, none of it into either walled-in chest.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaHaulerTwoWalled"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaHaulerTwoWalled")
	public void aHaulerPastTwoWalledInStorehousesGetsItsLoadToTheThird(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		BlockPos a = new BlockPos(20, 2, 4);
		BlockPos aChest = new BlockPos(21, 2, 4);
		BlockPos b = new BlockPos(3, 2, 4);
		BlockPos bChest = new BlockPos(4, 2, 4);
		BlockPos open = new BlockPos(24, 2, 24);
		BlockPos openChest = new BlockPos(25, 2, 24);
		wallIn(helper, 17, 24, 1, 7);
		wallIn(helper, 1, 8, 1, 7);
		helper.setBlock(a, ModBlocks.STOREHOUSE);
		helper.setBlock(aChest, Blocks.CHEST);
		helper.setBlock(b, ModBlocks.STOREHOUSE);
		helper.setBlock(bChest, Blocks.CHEST);
		helper.setBlock(open, ModBlocks.STOREHOUSE);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.COBBLESTONE, 2 * 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(helper.absolutePos(a).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			"the first walled-in Storehouse isn't the hauler's nearest: the test proves nothing; it heads for " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			helper.assertTrue(in(helper, aChest, Items.COBBLESTONE) == 0 && in(helper, bChest, Items.COBBLESTONE) == 0,
				"cobblestone got into a walled-in Storehouse's chest: the test proves nothing");
			helper.assertTrue(in(helper, openChest, Items.COBBLESTONE) == 2 * 64, "past two walled-in Storehouses the open one's chest has "
				+ in(helper, openChest, Items.COBBLESTONE) + " of " + 2 * 64 + "; the golem carries " + carried(golem) + " at " + golem.position()
				+ ", heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}

	/**
	 * Unreachable without a wall: the nearest Storehouse stands on a stone pillar five blocks up. The golem can walk right
	 * to its foot but never reach it, so it must still give it up and take the load to the open one farther off.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaHaulerPillar"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaHaulerPillar")
	public void aHaulerGivesUpAStorehouseOnAPillarAndUsesTheOpenOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		for (int x = 3; x <= 5; x++) {
			for (int y = 2; y <= 6; y++) {
				for (int z = 2; z <= 5; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos high = new BlockPos(4, 7, 4);
		BlockPos highChest = new BlockPos(4, 7, 3);
		BlockPos open = new BlockPos(24, 2, 24);
		BlockPos openChest = new BlockPos(25, 2, 24);
		helper.setBlock(high, ModBlocks.STOREHOUSE);
		helper.setBlock(highChest, Blocks.CHEST);
		helper.setBlock(open, ModBlocks.STOREHOUSE);
		helper.setBlock(openChest, Blocks.CHEST);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(helper.absolutePos(high).equals(HaulerGolems.storehouse(helper.getLevel(), golem).orElse(null)),
			"the Storehouse on the pillar isn't the hauler's nearest: the test proves nothing; it heads for " + HaulerGolems.storehouse(helper.getLevel(), golem)));
		helper.succeedWhen(() -> {
			helper.assertTrue(in(helper, highChest, Items.OAK_LOG) == 0, "logs got into the chest on the pillar: the test proves nothing");
			helper.assertTrue(in(helper, openChest, Items.OAK_LOG) == 64, "with the nearest Storehouse out of reach on a pillar the open one's chest has "
				+ in(helper, openChest, Items.OAK_LOG) + " of 64 logs; the golem carries " + carried(golem) + " at " + golem.position()
				+ ", heading for " + HaulerGolems.storehouse(helper.getLevel(), golem) + ", walking: " + golem.getNavigation().isInProgress());
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}

	/**
	 * B463's "and the golem is free": the only Storehouse is walled in, so the load goes back into the Drop Box. Then a
	 * Storehouse it can reach is built: the golem, free again, fetches that very load from the box and stores it.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaHaulerFreeAgain"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaHaulerFreeAgain")
	public void aHaulerThatPutItsLoadBackHaulsItOnceAStorehouseCanBeReached(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		BlockPos walled = new BlockPos(3, 2, 4);
		BlockPos walledChest = new BlockPos(4, 2, 4);
		BlockPos box = new BlockPos(20, 2, 20);
		BlockPos later = new BlockPos(24, 2, 12);
		BlockPos laterChest = new BlockPos(25, 2, 12);
		wallIn(helper, 1, 8, 1, 7);
		helper.setBlock(walled, ModBlocks.STOREHOUSE);
		helper.setBlock(walledChest, Blocks.CHEST);
		helper.setBlock(box, ModBlocks.DROP_BOX);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.COBBLESTONE, 64));
		AtomicInteger phase = new AtomicInteger();
		helper.onEachTick(() -> {
			if (phase.get() == 0 && in(helper, box, Items.COBBLESTONE) == 64 && carried(golem) == 0) {
				helper.setBlock(later, ModBlocks.STOREHOUSE);
				helper.setBlock(laterChest, Blocks.CHEST);
				phase.set(1);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase.get() == 1, "the load never went back into the Drop Box (it has " + in(helper, box, Items.COBBLESTONE)
				+ " of 64); the golem carries " + carried(golem) + " at " + golem.position());
			helper.assertTrue(in(helper, walledChest, Items.COBBLESTONE) == 0, "cobblestone got into the walled-in Storehouse's chest");
			helper.assertTrue(in(helper, laterChest, Items.COBBLESTONE) == 64, "after putting its load back the golem should be free: the new open "
				+ "Storehouse's chest has " + in(helper, laterChest, Items.COBBLESTONE) + " of 64; the Drop Box keeps " + in(helper, box, Items.COBBLESTONE)
				+ ", the golem carries " + carried(golem) + " at " + golem.position() + ", heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
			helper.assertTrue(carried(golem) == 0 && in(helper, box, Items.COBBLESTONE) == 0, "the golem carries " + carried(golem)
				+ " and the box keeps " + in(helper, box, Items.COBBLESTONE));
		});
	}
}
