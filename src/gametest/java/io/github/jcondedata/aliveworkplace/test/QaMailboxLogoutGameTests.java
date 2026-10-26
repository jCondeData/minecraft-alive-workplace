package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1026-1016): what a player leaves in a Mailbox's outgoing row is theirs until they press Send. MailboxMenu
 * says "unsent items go back to the player" when the screen closes. A player who logs out (or whose game crashes) with
 * the screen open and items in the top row loses none of them: as with vanilla's crafting grid, each comes back to the
 * bag saved in their player file or drops where they stood (vanilla drops a closing screen's items for a player who has
 * left), never vanishes.
 */
public class QaMailboxLogoutGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '100' '"qaMailLogout"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMailLogout")
	public void itemsLeftInTheOutgoingRowAreNotLostAtLogout(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos boxAt = new BlockPos(2, 2, 2);
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		box.setOwner(player.getUUID(), player.getGameProfile().getName());
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.APPLE, 9));
		BlockPos at = helper.absolutePos(boxAt);
		helper.getBlockState(boxAt).useWithoutItem(helper.getLevel(), player,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)); // the player's right click
		helper.assertTrue(player.containerMenu instanceof MailboxMenu, "using the mailbox didn't open its screen: " + player.containerMenu);
		MailboxMenu menu = (MailboxMenu) player.containerMenu;
		// Shift-click the apples from the hotbar (the menu's last 9 slots) into the outgoing row, as a player would.
		menu.quickMoveStack(player, menu.slots.size() - 9);
		helper.assertTrue(menu.hasOutgoing() && player.getInventory().countItem(Items.APPLE) == 0,
			"shift-clicking didn't move the 9 apples into the outgoing row");

		player.connection.onDisconnect(new DisconnectionDetails(Component.literal("qa logout")));

		Path file = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
		helper.assertTrue(Files.exists(file), "no player file was written at logout: " + file);
		int apples = 0;
		try {
			CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			ListTag inventory = saved.getList("Inventory", Tag.TAG_COMPOUND);
			for (int i = 0; i < inventory.size(); i++) {
				CompoundTag stack = inventory.getCompound(i);
				if (stack.getString("id").equals("minecraft:apple")) {
					apples += stack.contains("count") ? stack.getInt("count") : 1;
				}
			}
		} catch (java.io.IOException e) {
			throw new AssertionError("couldn't read the player file: " + e);
		}
		int onTheGround = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4))) {
			if (e.getItem().is(Items.APPLE)) {
				onTheGround += e.getItem().getCount();
			}
		}
		helper.assertTrue(apples + onTheGround == 9, "of the 9 apples left in the outgoing row, " + apples + " are in the bag saved at logout and "
			+ onTheGround + " on the ground where the player stood");
		helper.assertTrue(!menu.hasOutgoing(), "the outgoing row still holds items after logout");
		helper.succeed();
	}
}
