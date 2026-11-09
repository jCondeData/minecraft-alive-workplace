package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;

/**
 * QA (lane c, 2026-11-09) for ROADMAP 42.4, the Price Tag as a list screen, without CobbleDollars (the gametest server
 * has no Bank, so prices are paid in emeralds). Written from the spec, docs/wiki/list-screen.md and docs/guide/price-tag.md,
 * through the player's own right-click and chest-click packets: the lines a player reads with their real numbers, the
 * lowest price and stepping below it, the highest price and stepping past it, the tag leaving the hand while the screen
 * is open, clicks that aren't button presses, the tag in the off hand, two players, and a price that survives a save and
 * reopens where it left off. Complements BuilderToolListsGameTests.thePriceTagIsAList and
 * QaBuilderToolLists1109bGameTests.priceTagBoundariesAndSave.
 */
public class QaPriceTagList1109cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final int MINUS_1000 = PriceTagItem.FIRST_STEP_SLOT;
	private static final int MINUS_100 = PriceTagItem.FIRST_STEP_SLOT + 1;
	private static final int MINUS_10 = PriceTagItem.FIRST_STEP_SLOT + 2;
	private static final int MINUS_1 = PriceTagItem.FIRST_STEP_SLOT + 3;
	private static final int PLUS_1 = PriceTagItem.FIRST_STEP_SLOT + 5;
	private static final int PLUS_10 = PriceTagItem.FIRST_STEP_SLOT + 6;
	private static final int PLUS_100 = PriceTagItem.FIRST_STEP_SLOT + 7;
	private static final int PLUS_1000 = PriceTagItem.FIRST_STEP_SLOT + 8;
	private static final long MAX = 1_000_000_000L;

	private static ServerPlayer player(GameTestHelper helper) {
		return QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
	}

	private static ChoiceMenu use(GameTestHelper helper, ServerPlayer player, InteractionHand hand) {
		player.gameMode.useItem(player, helper.getLevel(), player.getItemInHand(hand), hand);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the price screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list screen");
		return menu;
	}

	private static void click(ServerPlayer p, int containerId, int slot, int button, ClickType type) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(containerId, p.containerMenu.getStateId(), slot, button, type,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		click(p, menu.containerId, slot, 0, ClickType.PICKUP);
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static String top(GameTestHelper helper, ChoiceMenu menu) {
		return JobListMenusGameTests.info(helper, menu).money().getString();
	}

	private static String row(ChoiceMenu menu) {
		return menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString();
	}

	/** What the spec says the price is paid as without CobbleDollars: the dollars at the server's rate, rounded up. */
	private static String emeralds(long dollars) {
		long rate = Math.max(1, Money.DOLLARS_PER_EMERALD);
		long n = Math.max(1, (dollars + rate - 1) / rate);
		return n + (n == 1 ? " emerald" : " emeralds");
	}

	private static long price(ItemStack stack) {
		return PriceTagItem.dollars(stack);
	}

	/**
	 * The happy path as the player reads it, with no CobbleDollars: +1 shows "1 emerald" at the top and "Price: 1 (paid as
	 * 1 emerald)" on the price's row (singular), +100 more shows the plural rounded up, the tag's own tooltip agrees, the
	 * title and every price-tag line resolve in en_us.json in American English, and Done closes the screen with the
	 * price kept.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cRead"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cRead")
	public void priceTagLinesReadRightWithoutCobbleDollars(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.assertTrue(!Money.cobbleDollars(), "the gametest server has a Bank: this class tests the emerald path");
		ServerPlayer p = player(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		click(p, menu, PLUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == 1, "+1 made " + price(p.getMainHandItem()));
		helper.assertTrue(top(helper, menu).equals(emeralds(1)), "the top reads '" + top(helper, menu) + "', expected '" + emeralds(1) + "'");
		helper.assertTrue(row(menu).equals("Price: 1 (paid as " + emeralds(1) + ")"), "the price's row reads '" + row(menu) + "'");
		click(p, menu, PLUS_100);
		helper.assertTrue(price(p.getMainHandItem()) == 101, "+100 made " + price(p.getMainHandItem()));
		helper.assertTrue(top(helper, menu).equals(emeralds(101)), "the top reads '" + top(helper, menu) + "', expected '" + emeralds(101) + "'");
		helper.assertTrue(row(menu).equals("Price: 101 (paid as " + emeralds(101) + ")"), "the price's row reads '" + row(menu) + "'");
		List<Component> tooltip = new ArrayList<>();
		p.getMainHandItem().getItem().appendHoverText(p.getMainHandItem(), Item.TooltipContext.of(helper.getLevel()), tooltip, TooltipFlag.NORMAL);
		helper.assertTrue(!tooltip.isEmpty() && tooltip.get(0).getString().equals("Price: " + emeralds(101)), "the tag's tooltip: " + tooltip);
		// every line the screen and the tag can show
		List<String> keys = List.of("screen.aliveworkplace.price_tag.title", "screen.aliveworkplace.price_tag.price", "screen.aliveworkplace.price_tag.none",
			"screen.aliveworkplace.price_tag.done", "screen.aliveworkplace.price_tag.steps", "screen.aliveworkplace.price_tag.list_hint",
			"tooltip.aliveworkplace.price_tag.price", "tooltip.aliveworkplace.price_tag.how", "tooltip.aliveworkplace.price_tag.use",
			"tooltip.aliveworkplace.price_tag.too_dear", "message.aliveworkplace.money.emeralds", "message.aliveworkplace.money.emeralds.one");
		List<String> british = List.of("colour", "grey", "armour", "defence", "cancelled", "favour", "centre", "metre", "realis", "organis", "licence",
			"jeweller", "behaviour", "honour", "catalogue", "cheque", "travell");
		Language lang = Language.getInstance();
		for (String k : keys) {
			helper.assertTrue(lang.has(k), "missing from en_us.json: " + k);
			String text = lang.getOrDefault(k).toLowerCase(Locale.ROOT);
			helper.assertTrue(!text.isBlank() && !text.equals(k), "empty line: " + k);
			for (String word : british) {
				helper.assertTrue(!text.contains(word), k + " is British English ('" + word + "'): " + text);
			}
		}
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.price_tag.title").equals("Set the price"), "the title: " + lang.getOrDefault("screen.aliveworkplace.price_tag.title"));
		helper.assertTrue(menu.icon(PriceTagItem.DONE_SLOT).getHoverName().getString().equals("Done"), "Done reads " + menu.icon(PriceTagItem.DONE_SLOT).getHoverName().getString());
		click(p, menu, PriceTagItem.DONE_SLOT);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "Done didn't close the screen");
		helper.assertTrue(price(p.getMainHandItem()) == 101, "Done changed the price: " + price(p.getMainHandItem()));
		helper.succeed();
	}

	/**
	 * The lowest price, 1, from both sides: +1 from nothing is 1, -1 from 1 is "No price yet" (never 0 or negative), -10
	 * from 5 and -1000 from 999 rub it out too, and +1 afterwards starts again from 1.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cLow"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cLow")
	public void priceTagLowestPriceAndSteppingBelowIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG, 2));
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		click(p, menu, PLUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == 1, "+1 from nothing made " + price(p.getMainHandItem()));
		click(p, menu, MINUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == -1 && p.getMainHandItem().get(DataComponents.CUSTOM_NAME) == null,
			"-1 from 1 left " + p.getMainHandItem().getHoverName().getString());
		helper.assertTrue(key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none")
			&& row(menu).equals("No price yet"), "the screen at nothing: top '" + top(helper, menu) + "', row '" + row(menu) + "'");
		for (int i = 0; i < 5; i++) {
			click(p, menu, PLUS_1);
		}
		click(p, menu, MINUS_10);
		helper.assertTrue(price(p.getMainHandItem()) == -1, "-10 from 5 left " + price(p.getMainHandItem()));
		click(p, menu, PLUS_1000);
		click(p, menu, MINUS_1);
		click(p, menu, MINUS_1000);
		helper.assertTrue(price(p.getMainHandItem()) == -1, "-1000 from 999 left " + price(p.getMainHandItem()));
		click(p, menu, MINUS_100);
		helper.assertTrue(price(p.getMainHandItem()) == -1, "-100 from nothing made " + price(p.getMainHandItem()));
		click(p, menu, PLUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == 1 && row(menu).equals("Price: 1 (paid as " + emeralds(1) + ")"),
			"+1 after rubbing out: " + price(p.getMainHandItem()) + ", row '" + row(menu) + "'");
		helper.assertTrue(p.getMainHandItem().getCount() == 2 && p.containerMenu == menu, "the stack or the screen changed");
		helper.succeed();
	}

	/**
	 * The highest price a tag holds (1,000,000,000, written in an anvil): the screen shows it, +1000 and +1 stay at it
	 * (no overflow, no wrap to nothing), -1 is 999,999,999 and +1000 from there stops at the most again.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cHigh"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cHigh")
	public void priceTagHighestPriceAndSteppingPastIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(String.valueOf(MAX))); // as an anvil writes it
		p.setItemInHand(InteractionHand.MAIN_HAND, tag);
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		helper.assertTrue(top(helper, menu).equals(emeralds(MAX)) && row(menu).contains(String.valueOf(MAX)),
			"the most at open: top '" + top(helper, menu) + "', row '" + row(menu) + "'");
		click(p, menu, PLUS_1000);
		helper.assertTrue(price(p.getMainHandItem()) == MAX, "+1000 past the most made " + price(p.getMainHandItem()));
		click(p, menu, PLUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == MAX, "+1 past the most made " + price(p.getMainHandItem()));
		click(p, menu, MINUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == MAX - 1 && row(menu).contains(String.valueOf(MAX - 1)),
			"-1 from the most: " + price(p.getMainHandItem()) + ", row '" + row(menu) + "'");
		click(p, menu, PLUS_1000);
		helper.assertTrue(price(p.getMainHandItem()) == MAX && top(helper, menu).equals(emeralds(MAX)),
			"+1000 from 999,999,999: " + price(p.getMainHandItem()) + ", top '" + top(helper, menu) + "'");
		helper.succeed();
	}

	/**
	 * The tag taken out of the hand while the screen is open (moved to the backpack, paper in its place): a + click
	 * changes neither the paper nor the tag, and the screen closes on the next ticks. A different tag put in the hand
	 * instead keeps its own price when nothing is clicked.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cTaken"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cTaken")
	public void priceTagScreenStopsWhenTheTagLeavesTheHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		for (int i = 0; i < 5; i++) {
			click(p, menu, PLUS_1);
		}
		ItemStack tag = p.getMainHandItem();
		helper.assertTrue(price(tag) == 5, "five +1 made " + price(tag));
		p.getInventory().setItem(9, tag);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
		helper.assertTrue(!menu.stillValid(p), "the screen is still valid with paper in hand");
		click(p, menu, PLUS_1000);
		helper.assertTrue(p.getMainHandItem().is(Items.PAPER) && p.getMainHandItem().get(DataComponents.CUSTOM_NAME) == null,
			"the paper got a price: " + p.getMainHandItem().getHoverName().getString());
		helper.assertTrue(price(p.getInventory().getItem(9)) == 5, "the tag in the backpack changed to " + price(p.getInventory().getItem(9)));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "the price screen is still open without the tag: " + p.containerMenu);
			helper.assertTrue(price(p.getInventory().getItem(9)) == 5 && p.getMainHandItem().is(Items.PAPER), "something changed after the close");
			helper.succeed();
		});
	}

	/**
	 * Clicks that aren't presses do nothing: a hotbar swap, a throw, a middle-click clone or a double-click on +1000 leave
	 * the price, and a click on the tag's own hotbar slot below the screen neither picks it up nor changes it. A right
	 * click and a shift-click on +1 are still presses (they add on).
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cClicks"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cClicks")
	public void priceTagIgnoresClicksThatArentPresses(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG, 3));
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		click(p, menu, PLUS_10);
		helper.assertTrue(price(p.getMainHandItem()) == 10, "+10 made " + price(p.getMainHandItem()));
		click(p, menu.containerId, PLUS_1000, 0, ClickType.SWAP); // number key 1: the tag's own hotbar slot
		click(p, menu.containerId, PLUS_1000, 0, ClickType.THROW);
		click(p, menu.containerId, PLUS_1000, 2, ClickType.CLONE);
		click(p, menu.containerId, PLUS_1000, 0, ClickType.PICKUP_ALL);
		helper.assertTrue(price(p.getMainHandItem()) == 10, "a click that isn't a press stepped the price to " + price(p.getMainHandItem()));
		int hotbar0 = ChoiceMenu.SIZE + 27; // the menu's slot for hotbar slot 0
		click(p, menu.containerId, hotbar0, 0, ClickType.PICKUP);
		click(p, menu.containerId, hotbar0, 0, ClickType.QUICK_MOVE);
		click(p, menu.containerId, -999, 0, ClickType.PICKUP);
		helper.assertTrue(p.getMainHandItem().is(ModItems.PRICE_TAG) && p.getMainHandItem().getCount() == 3 && menu.getCarried().isEmpty()
			&& price(p.getMainHandItem()) == 10, "the tag moved or changed: hand " + p.getMainHandItem() + ", carried " + menu.getCarried());
		helper.assertTrue(menu.icon(PLUS_1000).is(Items.LIME_DYE) && p.getInventory().countItem(Items.LIME_DYE) == 0 && p.getInventory().countItem(Items.RED_DYE) == 0,
			"a button's icon reached the inventory");
		click(p, menu.containerId, PLUS_1, 1, ClickType.PICKUP);
		helper.assertTrue(price(p.getMainHandItem()) > 10, "a right click on +1 didn't add on: " + price(p.getMainHandItem()));
		long before = price(p.getMainHandItem());
		click(p, menu.containerId, PLUS_1, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(price(p.getMainHandItem()) > before, "a shift-click on +1 didn't add on: " + price(p.getMainHandItem()));
		helper.succeed();
	}

	/**
	 * The tag in the off hand (a stone in the main hand): its screen sets the off-hand tag, never a priced tag elsewhere,
	 * and taking it out of the off hand ends the screen.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cOffhand"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cOffhand")
	public void priceTagInTheOffHandSetsThatTag(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		ItemStack other = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(other, 77);
		p.getInventory().setItem(5, other);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.PRICE_TAG));
		ChoiceMenu menu = use(helper, p, InteractionHand.OFF_HAND);
		click(p, menu, PLUS_100);
		click(p, menu, PLUS_10);
		helper.assertTrue(price(p.getOffhandItem()) == 110, "the off-hand tag says " + price(p.getOffhandItem()));
		helper.assertTrue(p.getMainHandItem().is(Items.STONE) && p.getMainHandItem().get(DataComponents.CUSTOM_NAME) == null, "the stone got a price");
		helper.assertTrue(price(p.getInventory().getItem(5)) == 77, "the other tag changed to " + price(p.getInventory().getItem(5)));
		helper.assertTrue(row(menu).contains("110") && top(helper, menu).equals(emeralds(110)), "the screen: top '" + top(helper, menu) + "', row '" + row(menu) + "'");
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		helper.assertTrue(!menu.stillValid(p), "the screen is still valid with the off hand empty");
		helper.succeed();
	}

	/**
	 * Two players: one forging a click on the other's screen id changes nothing; then each with a screen of their own sets
	 * only their own tag, and one pressing Done leaves the other's screen open.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cTwo"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cTwo")
	public void priceTagTwoPlayersEachSetTheirOwn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer a = player(helper);
		ServerPlayer b = player(helper);
		helper.assertTrue(a != b && !a.getUUID().equals(b.getUUID()), "the two players are one");
		a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
		b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
		ChoiceMenu menuA = use(helper, a, InteractionHand.MAIN_HAND);
		click(b, menuA.containerId, PLUS_1000, 0, ClickType.PICKUP); // b has no screen open
		helper.assertTrue(price(a.getMainHandItem()) == -1 && price(b.getMainHandItem()) == -1,
			"a forged click set a: " + price(a.getMainHandItem()) + ", b: " + price(b.getMainHandItem()));
		ChoiceMenu menuB = use(helper, b, InteractionHand.MAIN_HAND);
		helper.assertTrue(menuA != menuB, "the two players share one screen");
		click(a, menuA, PLUS_100);
		click(b, menuB, PLUS_10);
		click(b, menuB, PLUS_10);
		helper.assertTrue(price(a.getMainHandItem()) == 100 && price(b.getMainHandItem()) == 20,
			"a says " + price(a.getMainHandItem()) + " (wanted 100), b says " + price(b.getMainHandItem()) + " (wanted 20)");
		helper.assertTrue(row(menuA).contains("100") && row(menuB).contains("20") && !row(menuB).contains("100"),
			"the rows: a '" + row(menuA) + "', b '" + row(menuB) + "'");
		click(a, menuA, PriceTagItem.DONE_SLOT);
		helper.assertTrue(a.containerMenu == a.inventoryMenu && b.containerMenu == menuB, "Done closed the wrong screen");
		click(b, menuB, MINUS_1);
		helper.assertTrue(price(a.getMainHandItem()) == 100 && price(b.getMainHandItem()) == 19,
			"after a's Done: a " + price(a.getMainHandItem()) + ", b " + price(b.getMainHandItem()));
		helper.succeed();
	}

	/**
	 * The price chosen on the screen survives a save and load of the tag, and right-clicking the loaded tag opens the
	 * screen on that price (top and row) and steps on from it.
	 */
	//$ gametest_batch AREA '"qaPriceTag1109cSave"'
	@GameTest(template = AREA, batch = "qaPriceTag1109cSave")
	public void priceTagPriceSurvivesSaveAndReopensOnIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer p = player(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG, 2));
		ChoiceMenu menu = use(helper, p, InteractionHand.MAIN_HAND);
		click(p, menu, PLUS_100);
		click(p, menu, PLUS_100);
		for (int i = 0; i < 5; i++) {
			click(p, menu, PLUS_10);
		}
		click(p, menu, PriceTagItem.DONE_SLOT);
		helper.assertTrue(price(p.getMainHandItem()) == 250 && p.containerMenu == p.inventoryMenu, "set 250: " + price(p.getMainHandItem()));
		var reg = helper.getLevel().registryAccess();
		ItemStack loaded = ItemStack.parse(reg, p.getMainHandItem().save(reg)).orElseThrow();
		helper.assertTrue(price(loaded) == 250 && loaded.getCount() == 2, "the price was lost in a save: " + loaded);
		p.setItemInHand(InteractionHand.MAIN_HAND, loaded);
		ChoiceMenu again = use(helper, p, InteractionHand.MAIN_HAND);
		helper.assertTrue(again != menu, "the old screen was reused");
		helper.assertTrue(top(helper, again).equals(emeralds(250)) && row(again).equals("Price: 250 (paid as " + emeralds(250) + ")"),
			"reopened: top '" + top(helper, again) + "', row '" + row(again) + "'");
		click(p, again, MINUS_1);
		helper.assertTrue(price(p.getMainHandItem()) == 249, "-1 from the loaded 250 made " + price(p.getMainHandItem()));
		helper.succeed();
	}
}
