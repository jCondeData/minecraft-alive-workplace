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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B450: a Hauler Golem (29.15) whose nearest Storehouse it can't get to (walled in) doesn't stand at the end of its
 * path with its load forever: it gives that one up and takes the load to the next nearest. B456: likewise an empty
 * hauler whose nearest Drop Box is walled in gives it up and fetches from the next one.
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

	/** Walls in and roofs over x/z {@code lo}..{@code hi}, y 2..3 inside (the shell is the outer ring and y 4). */
	private static void wallIn(GameTestHelper helper, int lo, int hi) {
		for (int x = lo; x <= hi; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = lo; z <= hi; z++) {
					boolean shell = x == lo || x == hi || z == lo || z == hi || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
	}

	/** An empty hauler whose nearest Drop Box (to the Storehouse) is walled in fetches from the open one instead. */
	//$ gametest_ticks_batch AREA '1200' '"haulerWalledDropBox"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerWalledDropBox")
	public void haulerFetchesFromTheNextDropBoxWhenTheNearestIsWalledIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		BlockPos storehouse = new BlockPos(24, 2, 24);
		BlockPos storeChest = new BlockPos(25, 2, 24);
		BlockPos walledBox = new BlockPos(17, 2, 17);
		BlockPos openBox = new BlockPos(4, 2, 4);
		wallIn(helper, 14, 20);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		helper.setBlock(walledBox, ModBlocks.DROP_BOX);
		helper.setBlock(openBox, ModBlocks.DROP_BOX);
		Container walled = helper.getBlockEntity(walledBox);
		Container open = helper.getBlockEntity(openBox);
		for (int i = 0; i < 3; i++) {
			walled.setItem(i, new ItemStack(Items.OAK_LOG, 64));
			open.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 10)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(storeChest);
			helper.assertTrue(walled.countItem(Items.OAK_LOG) == 3 * 64, "the walled-in Drop Box lost logs: the golem got in, the test proves nothing");
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 3 * 64, "the Storehouse's chest has " + store.countItem(Items.COBBLESTONE)
				+ " of " + 3 * 64 + " cobblestone; the open box " + open.countItem(Items.COBBLESTONE) + ", the golem carries " + carried(golem)
				+ " at " + golem.position());
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}
}
