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
 * QA (lane-e-1025-1314) on B450's text: a Hauler Golem whose Storehouse can't be reached "after a while ... tries the
 * next reachable Storehouse (or drops back to the Drop Box) and keeps hauling". The fix covers the next Storehouse; with
 * the only Storehouse in reach walled in, the golem gives it up, finds no other, and stands holding its load (target
 * none, not walking) for good: the goods never reach a chest and the golem hauls nothing more.
 */
public class QaHaulerGiveUpBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The only Storehouse walled in, an open Drop Box: the load goes back into the Drop Box and the golem is free. */
	//$ gametest_ticks_batch AREA '2000' '"qaHaulerOnlyStorehouseWalled"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaHaulerOnlyStorehouseWalled")
	public void aHaulerWhoseOnlyStorehouseIsWalledInPutsItsLoadBackInTheDropBox(GameTestHelper helper) {
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
		BlockPos walledChest = new BlockPos(4, 2, 4);
		helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(walledChest, Blocks.CHEST);
		BlockPos box = new BlockPos(20, 2, 20);
		helper.setBlock(box, ModBlocks.DROP_BOX);
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.COBBLESTONE, 64));
		helper.succeedWhen(() -> {
			Container dropBox = helper.getBlockEntity(box);
			Container walled = helper.getBlockEntity(walledChest);
			helper.assertTrue(walled.countItem(Items.COBBLESTONE) == 0, "cobblestone got into the walled-in Storehouse's chest: the test proves nothing");
			helper.assertTrue(dropBox.countItem(Items.COBBLESTONE) == 64, "the only Storehouse is walled in and the Drop Box has "
				+ dropBox.countItem(Items.COBBLESTONE) + " of 64 cobblestone back; the golem carries " + HaulerGolems.load(golem) + " at "
				+ golem.position() + ", heading for " + HaulerGolems.storehouse(helper.getLevel(), golem) + ", walking: "
				+ golem.getNavigation().isInProgress());
		});
	}
}
