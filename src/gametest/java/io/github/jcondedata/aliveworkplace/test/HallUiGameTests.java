package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Village Hall's own screen (ROADMAP 30.4a): the hall, its pages and the Book of Edicts open on the hall's menu type
 * (drawn by the client's VillageHallMenuScreen, not a chest), other choice screens keep the chest; the client's copy has
 * the server's slots in the same order with every button inside the drawn window, apart, and only real icons active;
 * clicking it changes nothing on the client.
 */
public class HallUiGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** The drawn window (VillageHallMenuScreen.WIDTH and HEIGHT; the client class isn't on the server's path). */
	private static final int WIDTH = 196;
	private static final int HEIGHT = 160;

	/** The owner opens the hall: its own menu type; the lectern button opens the Book in it; the Book on its own too. */
	//$ gametest_ticks_batch AREA '100' '"hallUiOpens"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallUiOpens")
	public void hallOpensOnItsOwnScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		BlockPos hall = helper.absolutePos(HALL);
		owner.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			CivicEffects.forget();
			Moods.forget();
		});
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		helper.runAfterDelay(5, () -> {
			VillageHallScreen.open(owner, hall);
			helper.assertTrue(owner.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + owner.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) owner.containerMenu;
			helper.assertTrue(menu.getType() == ModBlocks.VILLAGE_HALL_MENU, "the hall opened as " + menu.getType());
			menu.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, owner);
			helper.assertTrue(owner.containerMenu == menu && menu.icon(EdictBook.HEADER).is(Items.LECTERN),
				"the lectern button didn't open the Book in the same screen: " + menu.icon(EdictBook.HEADER));
			owner.closeContainer();

			VillageHallScreen.openRemote(owner, hall);
			helper.assertTrue(owner.containerMenu.getType() == ModBlocks.VILLAGE_HALL_MENU, "the Ledger's hall opened as " + owner.containerMenu.getType());
			owner.closeContainer();

			EdictBook.open(owner, hall);
			helper.assertTrue(owner.containerMenu instanceof ChoiceMenu book && book.getType() == ModBlocks.VILLAGE_HALL_MENU
				&& book.icon(EdictBook.HEADER).is(Items.LECTERN), "the Book on its own opened as " + owner.containerMenu.getType());
			owner.closeContainer();

			// A choice screen that isn't the hall's keeps the chest.
			ChoiceMenu.open(owner, Component.literal("Other"), p -> true, m -> m.button(0, new ItemStack(Items.PAPER), null));
			helper.assertTrue(owner.containerMenu.getType() == MenuType.GENERIC_9x6, "another choice screen opened as " + owner.containerMenu.getType());
			owner.closeContainer();
			helper.succeed();
		});
	}

	/** The client's copy: the server's slots in order, buttons inside the window and apart, only real icons active, clicks inert. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void hallViewLayout(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		ChoiceView view = new ChoiceView(1, player.getInventory());
		ChoiceMenu server = ChoiceMenu.detached(player, m -> { });
		helper.assertTrue(view.slots.size() == server.slots.size(), view.slots.size() + " slots on the client, " + server.slots.size() + " on the server");
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			Slot a = view.slots.get(i);
			helper.assertTrue(a.getContainerSlot() == i, "slot " + i + " shows button " + a.getContainerSlot());
			// a button's 18x18 cell inside the window's 6-pixel frame and under the title
			helper.assertTrue(a.x - 1 >= 6 && a.x + 17 <= WIDTH - 6 && a.y - 1 >= 16 && a.y + 17 <= HEIGHT - 6,
				"button " + i + " at " + a.x + "," + a.y + " leaves the window");
			for (int j = 0; j < i; j++) {
				Slot b = view.slots.get(j);
				helper.assertTrue(Math.abs(a.x - b.x) >= 20 || Math.abs(a.y - b.y) >= 20, "buttons " + j + " and " + i + " touch");
			}
			helper.assertFalse(a.isActive(), "empty button " + i + " is drawn");
		}
		for (int i = ChoiceMenu.SIZE; i < view.slots.size(); i++) {
			helper.assertFalse(view.slots.get(i).isActive(), "the player's slot " + i + " is shown");
		}
		Slot book = view.slots.get(VillageHallScreen.BOOK);
		book.set(new ItemStack(Items.LECTERN));
		helper.assertTrue(book.isActive(), "the lectern isn't drawn");
		Slot divider = view.slots.get(13);
		divider.set(new ItemStack(Items.GRAY_STAINED_GLASS_PANE));
		helper.assertFalse(divider.isActive(), "a divider pane is drawn as a button");
		view.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, player);
		view.clicked(VillageHallScreen.BOOK, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(book.getItem().is(Items.LECTERN) && view.getCarried().isEmpty(), "a click moved the lectern: " + view.getCarried());
		helper.succeed();
	}
}
