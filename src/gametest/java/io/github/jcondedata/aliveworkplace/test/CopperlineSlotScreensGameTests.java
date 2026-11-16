package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 44.1: the Mailbox, the Drop Box and the Shop Counter drawn in Copperline round their real item slots. Each is
 * opened the player's way (using the block); every slot's well must sit inside the window, clear of the lintel, the
 * whisper line and (except the mailbox's parcel) the side panel, and no two wells may overlap; shift-clicks still move
 * items both ways; and the counter's price list, set on its screen, still sells through the shopkeeper's trade screen.
 */
public class CopperlineSlotScreensGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static void use(GameTestHelper helper, BlockPos pos, ServerPlayer player) {
		BlockPos at = helper.absolutePos(pos);
		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		return player;
	}

	/**
	 * The Copperline frame round {@code menu}'s slots: every well inside the window and off the lintel and the whisper
	 * line, no two overlapping, and inside the side panel exactly the slots {@code inPanel} says.
	 */
	private static void checkFrame(GameTestHelper helper, AbstractContainerMenu menu, int frameH, int gutter, int panelW, String what,
		java.util.function.IntPredicate inPanel) {
		int[] win = CopperSlots.window(frameH, gutter, panelW);
		int[] lintel = CopperSlots.lintel(frameH, gutter, panelW);
		int[] whisper = CopperSlots.whisper(frameH, gutter, panelW);
		int[] panel = CopperSlots.panel(frameH, panelW);
		List<Slot> slots = menu.slots;
		for (Slot slot : slots) {
			int[] well = CopperSlots.well(slot.x, slot.y);
			String name = what + " slot " + slot.index + " at " + slot.x + "," + slot.y;
			helper.assertTrue(well[0] >= win[0] + 2 && well[1] >= win[1] && well[0] + well[2] <= win[0] + win[2] - 2
				&& well[1] + well[3] <= win[1] + win[3], name + " sticks out of the window " + java.util.Arrays.toString(win));
			helper.assertFalse(CopperLayout.overlap(well, lintel), name + " is under the lintel");
			helper.assertFalse(CopperLayout.overlap(well, whisper), name + " is under the whisper line");
			boolean inside = well[0] >= panel[0] && well[1] >= panel[1] && well[0] + well[2] <= panel[0] + panel[2]
				&& well[1] + well[3] <= panel[1] + panel[3];
			if (inPanel.test(slot.index)) {
				helper.assertTrue(inside, name + " should sit in the side panel " + java.util.Arrays.toString(panel));
			} else {
				helper.assertFalse(CopperLayout.overlap(well, panel), name + " is under the side panel");
			}
			for (Slot other : slots) {
				if (other.index > slot.index) {
					helper.assertFalse(CopperLayout.overlap(well, CopperSlots.well(other.x, other.y)),
						name + " overlaps slot " + other.index);
				}
			}
		}
		// The window fits a GUI scale 2 screen (427x240) with the plaque on it.
		int[] origin = CopperSlots.origin(427, 240, frameH, gutter, panelW);
		helper.assertTrue(origin[0] + win[0] >= 0 && origin[0] + win[0] + win[2] <= 427
			&& origin[1] + win[1] - CopperLayout.PLAQUE_RISE >= 0 && origin[1] + win[1] + win[3] <= 240,
			what + "'s window does not fit 427x240");
	}

	/** The mailbox opens on its own menu; its parcel sits in the side panel; shift-clicks fill and empty the parcel, take the mail and never post into the inbox. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void mailboxSlotsFitCopperlineAndShiftClicksStillWork(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.MAILBOX);
		MailboxBlockEntity mailbox = helper.getBlockEntity(pos);
		ServerPlayer player = player(helper);
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof MailboxMenu, "using the mailbox should open it, not " + player.containerMenu);
		MailboxMenu menu = (MailboxMenu) player.containerMenu;
		helper.assertTrue(menu.slots.size() == MailboxMenu.OUT + MailboxBlockEntity.SIZE + 36, menu.slots.size() + " slots");
		checkFrame(helper, menu, MailboxMenu.FRAME_H, 0, CopperSlots.PANEL_W, "the mailbox's", i -> i < MailboxMenu.OUT);

		int pack = MailboxMenu.OUT + MailboxBlockEntity.SIZE; // the first slot of the player's pack
		player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 10));
		menu.clicked(pack, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(menu.slots.get(0).getItem().is(Items.COBBLESTONE) && menu.slots.get(0).getItem().getCount() == 10,
			"shift-clicking the pack's cobblestone should fill the parcel's first slot, not " + menu.slots.get(0).getItem());
		helper.assertTrue(menu.hasOutgoing(), "the parcel should hold the cobblestone");
		menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(!menu.hasOutgoing() && player.getInventory().countItem(Items.COBBLESTONE) == 10,
			"shift-clicking the parcel should put the cobblestone back in the pack");

		mailbox.receive(new ItemStack(Items.DIAMOND, 3));
		menu.broadcastChanges();
		int inbox = -1;
		for (int i = MailboxMenu.OUT; i < pack; i++) {
			if (menu.slots.get(i).getItem().is(Items.DIAMOND)) {
				inbox = i;
			}
		}
		helper.assertTrue(inbox >= 0, "the mail should show in the inbox slots");
		menu.setCarried(new ItemStack(Items.DIRT, 4));
		int empty = inbox == MailboxMenu.OUT ? MailboxMenu.OUT + 1 : MailboxMenu.OUT;
		menu.clicked(empty, 0, ClickType.PICKUP, player);
		helper.assertTrue(menu.slots.get(empty).getItem().isEmpty() && menu.getCarried().is(Items.DIRT),
			"nothing goes into the inbox by hand");
		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(inbox, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3 && mailbox.isEmpty(),
			"shift-clicking the mail should take it into the pack");
		helper.succeed();
	}

	/** The Drop Box opens on its own Copperline menu, still a three-row chest on the box; shift-clicks go both ways. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void dropBoxSlotsFitCopperlineAndShiftClicksStillWork(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.DROP_BOX);
		DropBoxBlockEntity box = helper.getBlockEntity(pos);
		ServerPlayer player = player(helper);
		use(helper, pos, player);
		helper.assertTrue(player.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.DROP_BOX_MENU && m.getContainer() == box
			&& m.getRowCount() == 3, "using the Drop Box should open its own menu on the box, not " + player.containerMenu);
		ChestMenu menu = (ChestMenu) player.containerMenu;
		checkFrame(helper, menu, 114 + 3 * 18, 0, CopperSlots.PANEL_W, "the Drop Box's", i -> false);

		player.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 20));
		menu.clicked(DropBoxBlockEntity.SIZE, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(box.getItem(0).is(Items.OAK_LOG) && box.getItem(0).getCount() == 20,
			"shift-clicking the pack's logs should put them in the box, not " + box.getItem(0));
		helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 0, "the logs should have left the pack");
		menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(box.isEmpty() && player.getInventory().countItem(Items.OAK_LOG) == 20, "shift-clicking the box should take the logs back");
		helper.succeed();
	}

	/**
	 * The owner sets a column on the counter's Copperline screen (a shift-click for the goods, a click for the price); a
	 * customer then buys it on the shopkeeper's trade screen: the goods come out of the chest, the payment goes in and
	 * the sale is logged.
	 */
	//$ gametest_ticks_batch AREA '200' '"copperline_counter_sale"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "copperline_counter_sale")
	public void shopCounterPricesSetOnItsScreenStillSell(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterPos = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

		ServerPlayer owner = player(helper);
		use(helper, counterPos, owner);
		helper.assertTrue(owner.containerMenu instanceof ChestMenu m && m.getType() == ModBlocks.SHOP_COUNTER_MENU && m.getContainer() == counter
			&& m.getRowCount() == 2, "using the counter should open its own price list menu, not " + owner.containerMenu);
		ChestMenu menu = (ChestMenu) owner.containerMenu;
		checkFrame(helper, menu, 114 + 2 * 18, CopperSlots.LABEL_GUTTER, CopperSlots.PANEL_W, "the counter's", i -> false);

		owner.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 16));
		menu.clicked(2 * ShopCounterBlockEntity.COLUMNS, 0, ClickType.QUICK_MOVE, owner);
		helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
			"shift-clicking the sample should put it in the first column's goods, not " + counter.goods(0));
		menu.setCarried(new ItemStack(Items.EMERALD, 2));
		menu.clicked(ShopCounterBlockEntity.COLUMNS, 0, ClickType.PICKUP, owner);
		helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2, "the price should be 2 emeralds, not " + counter.price(0));
		owner.closeContainer();

		Villager shopkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), shopkeeper, helper.absolutePos(counterPos), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		ServerPlayer customer = player(helper);
		customer.getInventory().add(new ItemStack(Items.EMERALD, 5));
		shopkeeper.mobInteract(customer, InteractionHand.MAIN_HAND);
		helper.assertTrue(customer.containerMenu instanceof MerchantMenu, "talking to the shopkeeper should open the trade screen, not " + customer.containerMenu);
		MerchantMenu trade = (MerchantMenu) customer.containerMenu;
		helper.assertTrue(shopkeeper.getOffers().size() == 1 && shopkeeper.getOffers().get(0).getResult().is(Items.COBBLESTONE),
			"the shopkeeper should offer the counter's cobblestone: " + shopkeeper.getOffers().size() + " offers");
		trade.setSelectionHint(0); // what the trade screen sends when the customer picks the offer
		trade.tryMoveItems(0);
		trade.clicked(2, 0, ClickType.PICKUP, customer); // takes the goods off the result slot
		helper.assertTrue(trade.getCarried().is(Items.COBBLESTONE) && trade.getCarried().getCount() == 16,
			"the customer should hold 16 cobblestone, not " + trade.getCarried());
		customer.closeContainer();
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 48, chest.countItem(Items.COBBLESTONE) + " cobblestone left in the chest, expected 48");
		helper.assertTrue(chest.countItem(Items.EMERALD) == 2, "the chest should hold the 2 emeralds paid, not " + chest.countItem(Items.EMERALD));
		helper.assertTrue(counter.goods(0).getCount() == 16 && counter.price(0).getCount() == 2, "the price list should keep its column");
		var log = counter.sales();
		helper.assertTrue(log.size() == 1 && log.get(0).goods().is(Items.COBBLESTONE) && log.get(0).buyer().equals(customer.getGameProfile().getName()),
			"the sales log should list the customer's sale: " + log);
		helper.succeed();
	}
}
