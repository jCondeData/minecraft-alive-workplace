package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-e-1026-1016): the mod's own menus under every click a client can send. A seeded run of 1,500 random clicks
 * (every {@link ClickType}, every slot, the "outside" slot -999, every button a vanilla client uses, shift and the
 * number keys) on a survival player's screens, checked after each click:
 * <ul>
 *   <li>the Mailbox (MailboxMenu): no item is made or lost (the player's bag, the outgoing row, the inbox, the cursor and
 *       anything thrown on the ground add up to what was there), the inbox never gains anything ("mail only comes in
 *       through the post"), and closing the screen hands the unsent top row back to the player;</li>
 *   <li>a button screen (ChoiceMenu, the Village Hall, the Storehouse and every job's choices): "nothing can be taken out
 *       or put in": the player's bag and the icons stay exactly as they were, the cursor stays empty, nothing is dropped,
 *       and a button runs once for each left, right or shift click on it, never for anything else.</li>
 * </ul>
 * Only clicks the server lets through (ServerGamePacketListenerImpl checks the slot index) are sent: a slot of the menu,
 * or -999 and -1 for the clicks that use them.
 */
public class QaMenuClickFuzzGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final int CLICKS = 1500;
	private static final int[] BUTTONS = {0, 1, 2, 4, 5, 6, 8, 9, 10, 40};

	/** A click a real client could send for a menu of {@code size} slots. */
	record Click(int slot, int button, ClickType type) {
		static Click random(RandomSource random, int size) {
			ClickType type = ClickType.values()[random.nextInt(ClickType.values().length)];
			int button;
			if (type == ClickType.SWAP) {
				button = random.nextInt(10) == 0 ? 40 : random.nextInt(9);
			} else {
				button = BUTTONS[random.nextInt(BUTTONS.length)];
			}
			boolean outsideOk = type == ClickType.PICKUP || type == ClickType.QUICK_CRAFT;
			int slot;
			int roll = random.nextInt(20);
			if (outsideOk && roll == 0) {
				slot = -999;
			} else if (outsideOk && roll == 1) {
				slot = -1;
			} else {
				slot = random.nextInt(size);
			}
			return new Click(slot, button, type);
		}
	}

	/** How many of each item {@code stacks} hold. */
	private static Map<Item, Integer> count(Iterable<ItemStack> stacks) {
		Map<Item, Integer> counts = new HashMap<>();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static List<ItemStack> dropped(GameTestHelper helper, ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(6))) {
			out.add(e.getItem());
		}
		return out;
	}

	private static void fillBag(ServerPlayer player) {
		var inv = player.getInventory();
		inv.clearContent();
		inv.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		inv.setItem(1, new ItemStack(Items.COBBLESTONE, 13));
		inv.setItem(2, new ItemStack(Items.IRON_SWORD));
		inv.setItem(4, new ItemStack(Items.EGG, 7));
		inv.setItem(9, new ItemStack(Items.OAK_LOG, 40));
		inv.setItem(20, new ItemStack(Items.EMERALD, 64));
		inv.setItem(21, new ItemStack(Items.EMERALD, 1));
		inv.setItem(35, new ItemStack(Items.ENDER_PEARL, 16));
		inv.setItem(40, new ItemStack(Items.BREAD, 5)); // the off hand
	}

	private static List<ItemStack> bag(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			out.add(player.getInventory().getItem(i));
		}
		return out;
	}

	/**
	 * 1,500 random clicks on a Mailbox screen holding mail and an outgoing row: nothing is made or lost, the inbox never
	 * gains, and closing gives the top row back. (Kills the inbox slot's mayPlace -> true and a quickMoveStack that
	 * sends the player's items into the inbox or forgets to shrink the source.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMenuFuzzMailbox"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMenuFuzzMailbox")
	public void randomClicksOnAMailboxNeitherMakeNorLoseItems(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos boxAt = new BlockPos(3, 2, 3);
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		box.setOwner(player.getUUID(), player.getGameProfile().getName());
		box.receive(new ItemStack(Items.DIAMOND, 5));
		box.receive(new ItemStack(Items.EMERALD, 30));
		box.receive(new ItemStack(Items.WRITABLE_BOOK));
		fillBag(player);
		MailboxMenu menu = new MailboxMenu(3, player.getInventory(), box);
		player.containerMenu = menu;
		menu.getSlot(0).set(new ItemStack(Items.APPLE, 9));
		menu.getSlot(5).set(new ItemStack(Items.COBBLESTONE, 20));

		Map<Item, Integer> inboxStart = count(inbox(box));
		Map<Item, Integer> start = everything(helper, player, menu, box);
		RandomSource random = RandomSource.create(20261026L);
		int size = menu.slots.size();
		for (int n = 0; n < CLICKS; n++) {
			Click click = Click.random(random, size);
			menu.clicked(click.slot(), click.button(), click.type(), player);
			Map<Item, Integer> now = everything(helper, player, menu, box);
			helper.assertTrue(now.equals(start), "click " + n + " " + click + " changed what there is: " + start + " -> " + now);
			for (Map.Entry<Item, Integer> e : count(inbox(box)).entrySet()) {
				helper.assertTrue(e.getValue() <= inboxStart.getOrDefault(e.getKey(), 0),
					"click " + n + " " + click + " put " + e.getKey() + " into the inbox (" + e.getValue() + ", was "
						+ inboxStart.getOrDefault(e.getKey(), 0) + ")");
			}
		}

		Map<Item, Integer> beforeClose = everything(helper, player, menu, box);
		player.containerMenu = player.inventoryMenu;
		menu.removed(player);
		helper.assertTrue(!menu.hasOutgoing(), "closing the mailbox left items in the outgoing row");
		helper.assertTrue(menu.getCarried().isEmpty(), "closing the mailbox left " + menu.getCarried() + " on the cursor");
		Map<Item, Integer> afterClose = everything(helper, player, menu, box);
		helper.assertTrue(afterClose.equals(beforeClose), "closing the mailbox changed what there is: " + beforeClose + " -> " + afterClose);
		helper.succeed();
	}

	private static List<ItemStack> inbox(MailboxBlockEntity box) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < box.getContainerSize(); i++) {
			out.add(box.getItem(i));
		}
		return out;
	}

	/** The bag, the menu's own slots that aren't the bag (the outgoing row), the inbox, the cursor and the ground. */
	private static Map<Item, Integer> everything(GameTestHelper helper, ServerPlayer player, AbstractContainerMenu menu, MailboxBlockEntity box) {
		List<ItemStack> all = new ArrayList<>(bag(player));
		for (int i = 0; i < MailboxMenu.OUT; i++) {
			all.add(menu.getSlot(i).getItem());
		}
		all.addAll(inbox(box));
		all.add(menu.getCarried());
		all.addAll(dropped(helper, player));
		return count(all);
	}

	/**
	 * 1,500 random clicks on a button screen: the bag and the icons stay exactly as they were (stack by stack), the
	 * cursor stays empty, nothing is dropped, and each button runs exactly once per left, right or shift click on it.
	 * (Kills ChoiceMenu#clicked falling through to the chest's own click, and a press on the wrong click types.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMenuFuzzChoice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMenuFuzzChoice")
	public void randomClicksOnAButtonScreenMoveNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		fillBag(player);
		int[] presses = new int[ChoiceMenu.SIZE];
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> {
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				int s = slot;
				if (slot % 9 == 4) {
					m.button(slot, new ItemStack(Items.PAPER), null); // a label: no action
				} else if (slot % 7 == 3) {
					m.button(slot, new ItemStack(Items.BOOK, 2), p -> presses[s]++);
				} else if (slot % 5 != 0) {
					m.button(slot, new ItemStack(Items.EMERALD, 1 + slot % 3), p -> presses[s]++);
				}
			}
		});
		player.containerMenu = menu;
		List<ItemStack> bagStart = copies(bag(player));
		List<ItemStack> iconsStart = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			iconsStart.add(menu.icon(slot).copy());
		}
		int[] expected = new int[ChoiceMenu.SIZE];
		RandomSource random = RandomSource.create(1026L);
		int size = menu.slots.size();
		for (int n = 0; n < CLICKS; n++) {
			Click click = Click.random(random, size);
			boolean presses_ = click.slot() >= 0 && click.slot() < ChoiceMenu.SIZE
				&& (click.type() == ClickType.PICKUP || click.type() == ClickType.QUICK_MOVE);
			if (presses_ && hasAction(click.slot())) {
				expected[click.slot()]++;
			}
			menu.clicked(click.slot(), click.button(), click.type(), player);
			helper.assertTrue(menu.getCarried().isEmpty(), "click " + n + " " + click + " put " + menu.getCarried() + " on the cursor");
			helper.assertTrue(same(bag(player), bagStart), "click " + n + " " + click + " changed the player's bag: " + bag(player));
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				helper.assertTrue(ItemStack.matches(menu.icon(slot), iconsStart.get(slot)),
					"click " + n + " " + click + " changed icon " + slot + ": " + iconsStart.get(slot) + " -> " + menu.icon(slot));
			}
			helper.assertTrue(dropped(helper, player).isEmpty(), "click " + n + " " + click + " dropped " + dropped(helper, player));
			helper.assertFalse(menu.shiftClicked() || menu.rightClicked(), "click " + n + " " + click + " left shift or right set after the press");
		}
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			helper.assertTrue(presses[slot] == expected[slot], "button " + slot + " ran " + presses[slot] + " times for " + expected[slot] + " clicks");
		}
		helper.succeed();
	}

	private static boolean hasAction(int slot) {
		return slot % 9 != 4 && (slot % 7 == 3 || slot % 5 != 0);
	}

	private static List<ItemStack> copies(List<ItemStack> stacks) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : stacks) {
			out.add(s.copy());
		}
		return out;
	}

	private static boolean same(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}
}
