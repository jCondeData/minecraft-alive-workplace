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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B450: a Hauler Golem (29.15) whose nearest Storehouse it can't get to (walled in) doesn't stand at the end of its
 * path with its load forever: it gives that one up and takes the load to the next nearest.
 */
public class GolemHaulerRouteGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** Walled in on every side and roofed over: the interior is x 2..7, y 2..3, z 2..6. */
	private static final BlockPos WALLED = new BlockPos(3, 2, 4);
	private static final BlockPos WALLED_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos OPEN = new BlockPos(24, 2, 24);
	private static final BlockPos OPEN_CHEST = new BlockPos(25, 2, 24);

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	//$ gametest_ticks_batch AREA '1200' '"haulerWalledStorehouse"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerWalledStorehouse")
	public void haulerTakesItsLoadToTheNextStorehouseWhenTheNearestIsWalledIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		for (int x = 1; x <= 8; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 7 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
		helper.setBlock(WALLED, ModBlocks.STOREHOUSE);
		helper.setBlock(WALLED_CHEST, Blocks.CHEST);
		helper.setBlock(OPEN, ModBlocks.STOREHOUSE);
		helper.setBlock(OPEN_CHEST, Blocks.CHEST);
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.COBBLESTONE, 3 * 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(helper.absolutePos(WALLED).equals(HaulerGolems.storehouse(helper.getLevel(),
			golem.blockPosition()).orElse(null)), "the walled-in Storehouse isn't the hauler's nearest: the test proves nothing"));
		helper.succeedWhen(() -> {
			Container open = helper.getBlockEntity(OPEN_CHEST);
			Container walled = helper.getBlockEntity(WALLED_CHEST);
			helper.assertTrue(walled.countItem(Items.COBBLESTONE) == 0, "cobblestone got into the walled-in Storehouse's chest");
			helper.assertTrue(open.countItem(Items.COBBLESTONE) == 3 * 64, "the open Storehouse's chest has " + open.countItem(Items.COBBLESTONE)
				+ " of " + 3 * 64 + " cobblestone; the golem carries " + carried(golem) + " at " + golem.position() + ", heading for "
				+ HaulerGolems.storehouse(helper.getLevel(), golem) + " (open one " + helper.absolutePos(OPEN) + ")");
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}
}
