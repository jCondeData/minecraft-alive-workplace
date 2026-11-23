package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1123-0953): trading at the board (docs/wiki/board-trade.md, ROADMAP 33.5), on what
 * {@code BoardTradeGameTests}, {@code QaShopBoards1123cGameTests}, {@code QaTradeBoardGameTests} and
 * {@code ProtectionSpecGameTests} leave unchecked: the line "above the hotbar", a shift-click sale of three bundles
 * each at the price the one before left, worn armor, stacks that keep what they are (a name) both ways, the singular
 * at one emerald (purchase line and nugget), a sale with the first Storehouse's chest full going to a second
 * Storehouse, and a refusal at a cent under the marked-up price. Uses the gametest data pack's goods (Test Timber:
 * logs, 16 a bundle, base 1 emerald; Test Arms: iron sword or chestplate, 1 a bundle, base 3 emeralds).
 */
public class QaBoardTrade1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final BlockPos STOREHOUSE_2 = new BlockPos(4, 2, 9);
	private static final BlockPos CHEST_2 = new BlockPos(4, 2, 11);
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	private static final ResourceLocation ARMS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_arms");

	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	private static String said(ChoiceMenu menu, int slot) {
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		return lore.isEmpty() ? "" : lore.get(0);
	}

	private static Market.Price price(GameTestHelper helper, BlockPos hall, ResourceLocation good) {
		return Caravans.Data.get(helper.getLevel()).market(hall).prices().get(good);
	}

	private static int has(ServerPlayer p, Item item) {
		return BoardTradeGameTests.has(p, item);
	}

	private static int count(Container c, Item item) {
		return BoardTradeGameTests.count(c, item);
	}

	/** A connected player who keeps every line shown above their hotbar. */
	private static ServerPlayer listener(GameTestHelper helper, List<String> hotbar) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "board-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (overlay) {
					hotbar.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				if (overlay) {
					hotbar.add(message.getString());
				}
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/**
	 * board-trade.md: "After a click, the first line of that good's tooltip says what came of it ... The same line shows
	 * above the hotbar." A sale and a refusal each show their line above the hotbar, word for word the tooltip's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardHotbar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardHotbar")
	public void theLineAlsoShowsAboveTheHotbar(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		List<String> hotbar = new ArrayList<>();
		ServerPlayer player = listener(helper, hotbar);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			hotbar.clear();
			menu.press(timber, player);
			String sale = "You sell 16 × Test Timber to " + village + " for 1 emerald.";
			helper.assertTrue(said(menu, timber).equals(sale), "the tooltip said: " + said(menu, timber));
			helper.assertTrue(hotbar.contains(sale), "above the hotbar: " + hotbar);
			// nothing to spare now (16 in the chest): a right click is refused, and says so above the hotbar too
			hotbar.clear();
			menu.clicked(timber, 1, ClickType.PICKUP, player);
			String no = village + " has no bundle of Test Timber to spare: its Storehouse keeps 16 of everything back.";
			helper.assertTrue(said(menu, timber).equals(no), "the refusal in the tooltip: " + said(menu, timber));
			helper.assertTrue(hotbar.contains(no), "the refusal above the hotbar: " + hotbar);
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "The price moves 2% a bundle ... A shift-click pays each bundle at the price the one before left"
	 * and "A shift-click is settled once for all its bundles", paid down to the whole emerald. 48 logs at 1.50: 1.50 +
	 * 1.47 + 1.44 = 4.41, paid 4; the treasury gives 4; the price ends at 1.41, three steps down.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardThreeBundles"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardThreeBundles")
	public void aShiftClickSaleOfThreeBundlesPaysEachAtThePriceTheOneBeforeLeft(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 48));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("Click: sell a bundle (you carry 48). Shift-click: all it will take"),
				"the tooltip: " + BoardTradeGameTests.lore(menu.icon(timber)));
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && count(BoardTradeGameTests.chest(helper), Items.OAK_LOG) == 48, "sold " + count(BoardTradeGameTests.chest(helper), Items.OAK_LOG));
			helper.assertTrue(has(player, Items.EMERALD) == 4 && entity.treasury() == 600, "4.41 paid as " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			Market.Price after = price(helper, hall, TIMBER);
			helper.assertTrue(after.cents() == 141 && after.nudge() == -3, "the price after three bundles: " + after);
			helper.assertTrue(said(menu, timber).equals("You sell 48 × Test Timber to " + village + " for 4 emeralds."), "what it said: " + said(menu, timber));
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We pay: 1.41 emeralds"), "the board after: " + BoardTradeGameTests.lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "What counts as the good. Its items, but no worn tools or armor". A worn iron chestplate isn't a
	 * bundle of Test Arms (the tooltip offers to buy, and a click sells nothing); carried beside a whole one, only the
	 * whole one is sold, for 3 emeralds.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardWornArmor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardWornArmor")
	public void wornArmorIsntSoldButWholeArmorIs(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, ARMS, 300);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			ItemStack worn = new ItemStack(Items.IRON_CHESTPLATE);
			worn.setDamageValue(50);
			player.getInventory().add(worn);
			ChoiceMenu menu = board(helper, player, hall);
			int arms = BoardTradeGameTests.slot(helper, menu, "Test Arms");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(arms)).contains("Click: buy a bundle. Shift-click: all you can"),
				"a worn chestplate counted as a bundle: " + BoardTradeGameTests.lore(menu.icon(arms)));
			menu.press(arms, player);
			helper.assertTrue(has(player, Items.IRON_CHESTPLATE) == 1 && entity.treasury() == 1000 && count(BoardTradeGameTests.chest(helper), Items.IRON_CHESTPLATE) == 0,
				"the village bought a worn chestplate: " + said(menu, arms));

			player.getInventory().add(new ItemStack(Items.IRON_CHESTPLATE));
			menu = board(helper, player, hall);
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(arms)).contains("Click: sell a bundle (you carry 1). Shift-click: all it will take"),
				"a whole chestplate beside a worn one: " + BoardTradeGameTests.lore(menu.icon(arms)));
			menu.clicked(arms, 0, ClickType.QUICK_MOVE, player);
			Container chest = BoardTradeGameTests.chest(helper);
			helper.assertTrue(count(chest, Items.IRON_CHESTPLATE) == 1 && has(player, Items.IRON_CHESTPLATE) == 1, "chest " + count(chest, Items.IRON_CHESTPLATE)
				+ ", player " + has(player, Items.IRON_CHESTPLATE));
			boolean wholeSold = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				wholeSold |= chest.getItem(i).is(Items.IRON_CHESTPLATE) && chest.getItem(i).getDamageValue() == 0;
			}
			helper.assertTrue(wholeSold && player.getInventory().items.stream().anyMatch(s -> s.is(Items.IRON_CHESTPLATE) && s.getDamageValue() == 50),
				"the worn chestplate was sold instead of the whole one");
			helper.assertTrue(has(player, Items.EMERALD) == 3 && entity.treasury() == 700, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "Stacks keep what they are". Sixteen oak logs named "Heirloom" sold reach the chest still named;
	 * bought back, they come out named.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardKeepsName"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardKeepsName")
	public void aNamedStackKeepsItsNameBothWays(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 16)); // the 16 kept back
			ItemStack named = new ItemStack(Items.OAK_LOG, 16);
			named.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom"));
			player.getInventory().add(named);
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			int namedInChest = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				if (s.is(Items.OAK_LOG) && s.has(DataComponents.CUSTOM_NAME) && s.getHoverName().getString().equals("Heirloom")) {
					namedInChest += s.getCount();
				}
			}
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && namedInChest == 16, "the named logs in the chest: " + namedInChest + "; said " + said(menu, timber));
			// buy one bundle back: the named logs come out as they went in (the plain 16 or the named 16; either way, 16 named or 16 plain, nothing renamed)
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			menu.clicked(timber, 1, ClickType.PICKUP, player);
			int namedHeld = 0;
			int plainHeld = 0;
			for (ItemStack s : player.getInventory().items) {
				if (s.is(Items.OAK_LOG)) {
					if (s.has(DataComponents.CUSTOM_NAME)) {
						helper.assertTrue(s.getHoverName().getString().equals("Heirloom"), "a log came out renamed: " + s.getHoverName().getString());
						namedHeld += s.getCount();
					} else {
						plainHeld += s.getCount();
					}
				}
			}
			helper.assertTrue(namedHeld + plainHeld == 16 && count(chest, Items.OAK_LOG) == 16, "bought back " + namedHeld + " named and " + plainHeld + " plain: " + said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md, Money: "the buyer charged up to the whole emerald, and the difference stays with the village";
	 * the nugget says "Treasury: 12.4 emeralds". At 0.60 a bundle costs 0.66, charged 1: the line reads "for 1 emerald"
	 * (singular), an empty treasury then holds 1 and the nugget says "Treasury: 1 emerald". With no emerald the click
	 * is refused with the marked-up price.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardOneEmerald"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardOneEmerald")
	public void aOneEmeraldPurchaseReadsSingularAndNoEmeraldBuysNothing(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 60);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(0);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We sell: 0.66 emeralds"), "the board: " + BoardTradeGameTests.lore(menu.icon(timber)));
			// no emerald: refused, nothing moves
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && count(chest, Items.OAK_LOG) == 48 && entity.treasury() == 0, "bought with no money");
			helper.assertTrue(said(menu, timber).equals("You can't pay 0.66 emeralds for a bundle of Test Timber."), "the refusal: " + said(menu, timber));
			// one emerald: one bundle, the whole emerald to the treasury
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 0 && entity.treasury() == 100, "the purchase: "
				+ has(player, Items.OAK_LOG) + " logs, treasury " + entity.treasury());
			helper.assertTrue(said(menu, timber).equals("You buy 16 × Test Timber from " + village + " for 1 emerald."), "what it said: " + said(menu, timber));
			helper.assertTrue(menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: 1 emerald"),
				"the nugget: " + menu.icon(TradePage.TREASURY).getHoverName().getString());
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "A bundle ... leaves your inventory for the Storehouses' chests" (every Storehouse's). With the
	 * first Storehouse's chest full of cobblestone, a sale goes into the second Storehouse's chest; and 20 logs in each
	 * Storehouse's chest spare one bundle between them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardTwoStorehouses"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardTwoStorehouses")
	public void aSecondStorehouseTakesWhatTheFirstHasNoRoomFor(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.setBlock(STOREHOUSE_2, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_2, Blocks.CHEST);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			Container first = BoardTradeGameTests.chest(helper);
			Container second = helper.getBlockEntity(CHEST_2);
			for (int i = 0; i < first.getContainerSize(); i++) {
				first.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && count(second, Items.OAK_LOG) == 16 && entity.treasury() == 900,
				"the sale with the first Storehouse full: the second holds " + count(second, Items.OAK_LOG) + "; said " + said(menu, timber));

			first.clearContent();
			second.clearContent();
			first.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			second.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			menu = board(helper, player, hall);
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("Bundles to spare in the Storehouse: 1"),
				"20 + 20 logs in two Storehouses: " + BoardTradeGameTests.lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: a buyer is stopped by "what they can pay"; the marked-up price is "10% over". At 1.50 a bundle
	 * costs 1.65, charged 2: one emerald short (1) buys nothing; 2 buy one bundle; and a shift-click with 3 emeralds
	 * buys one bundle only (1.65 + 1.68 = 3.33 over 3).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardPayEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardPayEdge")
	public void aBuyerPaysWholeEmeraldsAndOneShortBuysNothing(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(0);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 1 && entity.treasury() == 0, "one emerald bought a 1.65 bundle");
			helper.assertTrue(said(menu, timber).equals("You can't pay 1.65 emeralds for a bundle of Test Timber."), "the refusal: " + said(menu, timber));
			player.getInventory().add(new ItemStack(Items.EMERALD, 2)); // 3
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 1 && entity.treasury() == 200, "the shift-click with 3 emeralds: "
				+ has(player, Items.OAK_LOG) + " logs, " + has(player, Items.EMERALD) + " emeralds left, treasury " + entity.treasury());
			helper.assertTrue(said(menu, timber).equals("You buy 16 × Test Timber from " + village + " for 2 emeralds. You can't pay for more."), "what it said: " + said(menu, timber));
			helper.succeed();
		});
	}
}
