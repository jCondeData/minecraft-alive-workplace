package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c-1024-0353, B369): the Storehouse board's linked line (37.9) is a counted line, "1 block" for one block and
 * "2 blocks" for two, like the mod's other counted lines.
 */
public class QaModdedStorageBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * A Storehouse and a Sophisticated controller placed the way a player places it, with one barrel beside it: the
	 * board reads "Linked through a storage controller: 1 block". A second barrel: "... 2 blocks".
	 */
	//$ gametest_batch AREA '"qaBoardOneBlock"'
	@GameTest(template = AREA, batch = "qaBoardOneBlock")
	public void qaTheBoardSaysOneBlockNotOneBlocks(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		BlockPos barrel = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(barrel, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(barrel), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the barrel took nothing");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos at = helper.absolutePos(storehouse);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
		SophisticatedControllerCompatTests.placeAsPlayer(level, helper.absolutePos(new BlockPos(3, 2, 4)),
			SophisticatedControllerCompatTests.block("controller"), player);

		List<String> one = lore(player, at, helper);
		helper.assertTrue(one.contains("Linked through a storage controller: 1 block"), "one barrel linked, the board reads: " + one);

		// A second barrel, linked the way a player links it: placed beside the first and the controller placed again.
		BlockPos second = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlockAndUpdate(second, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		level.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 4)), false);
		SophisticatedControllerCompatTests.placeAsPlayer(level, helper.absolutePos(new BlockPos(3, 2, 4)),
			SophisticatedControllerCompatTests.block("controller"), player);
		List<String> two = lore(player, at, helper);
		helper.assertTrue(two.contains("Linked through a storage controller: 2 blocks"), "two barrels linked, the board reads: " + two);
		helper.succeed();
	}

	private static List<String> lore(ServerPlayer player, BlockPos storehouse, GameTestHelper helper) {
		ItemLore lore = StorehouseBoard.boardForTest(player, storehouse).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore.lines().stream().map(Component::getString).toList();
	}
}
