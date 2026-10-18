package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 37.8 without any storage mod: the free room the Storehouse and the porters read is counted as before for
 * vanilla chests and barrels (empty slots), and the board shows no "room for N items" line. The modded side (stack
 * upgrades, limited barrels) is in the compat tests ({@code SophisticatedStorageCompatTests}).
 */
public class StorageRoomGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STOREHOUSE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BARREL = new BlockPos(4, 2, 2);

	/** A chest with a part stack and a sword, an empty barrel: 52 empty slots, 52 free stacks, no big slots, no extra line. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void vanillaRoomIsCountedAsBefore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(BARREL, Blocks.BARREL);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		chest.setItem(1, new ItemStack(Items.IRON_SWORD));
		chest.setItem(2, new ItemStack(Items.ENDER_PEARL, 10));
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(STOREHOUSE), null);
		helper.assertTrue(store.size() == 2, "store chests: " + store);
		int free = SupplyContainers.freeSlots(level, store);
		helper.assertTrue(free == 24 + 27, "empty slots: " + free);
		ItemStores.Room room = SupplyContainers.room(level, store);
		helper.assertTrue(room.freeSlots() == free && room.freeStacks() == free && !room.bigSlots(), "room: " + room);
		helper.assertTrue(room.freeItems() == 51L * 64 + 24 + 6, "free items: " + room.freeItems());
		helper.assertTrue(SupplyContainers.freeStacks(level, store) == free, "a porter's free stacks: " + SupplyContainers.freeStacks(level, store));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		var lore = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		Component stock = lore.lines().stream().filter(l -> key(l, "screen.aliveworkplace.storehouse.stock")).findFirst().orElse(null);
		helper.assertTrue(stock != null && String.valueOf(((TranslatableContents) stock.getContents()).getArgs()[2]).equals(String.valueOf(free)),
			"the stock line: " + stock);
		helper.assertFalse(lore.lines().stream().anyMatch(l -> key(l, "screen.aliveworkplace.storehouse.room")), "a room line for vanilla chests: " + lore.lines());
		helper.succeed();
	}

	/** A full vanilla store stays full for a porter: a part stack of each item isn't room for a stack. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aFullVanillaStoreIsFullForPorters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 63));
		}
		List<BlockPos> store = List.of(helper.absolutePos(CHEST));
		ItemStores.Room room = SupplyContainers.room(level, store);
		helper.assertTrue(room.freeSlots() == 0 && room.freeStacks() == 0 && room.freeItems() == 27 && !room.bigSlots(), "room: " + room);
		helper.assertTrue(SupplyContainers.freeStacks(level, store) == 0, "a porter sees room in a full chest");
		helper.succeed();
	}

	private static boolean key(Component c, String key) {
		return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}
}
