package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.smith.BallRecipes;
import io.github.jcondedata.aliveworkplace.smith.BallSmiths;
import io.github.jcondedata.aliveworkplace.travel.Ferrymen;
import io.github.jcondedata.aliveworkplace.travel.FerryRides;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c, 2026-11-09) for ROADMAP 42.3 and 42.4, their Cobblemon and CobbleDollars parts: the Ball Smith's orders, the
 * Berry Breeder's book and the Habitat Keeper's lures, and the Shop and Ferry paid in CobbleDollars, on the list screen.
 * Written from the spec ("only how it looks changes": every click, price and refusal works as before), through the
 * player's own right-click and the chest-click packets the list screen sends: switching rows, the stock or the villager
 * gone between Buy and Confirm, a full inventory, item prices, the row a click lands on at the edges, save and reload,
 * and every sentence rendered with real arguments.
 */
public class QaJobShopLists1109cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	// ---------------------------------------------------------------- helpers

	/** A right-click (sneaking or not) with an empty hand on {@code villager}, as a client sends it; the menu it opened or null. */
	static ChoiceMenu interact(ServerPlayer player, Villager villager, boolean sneak) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
		return player.containerMenu instanceof ChoiceMenu menu ? menu : null;
	}

	static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager, boolean sneak) {
		ChoiceMenu menu = interact(player, villager, sneak);
		helper.assertTrue(menu != null, "the right-click didn't open a screen: " + player.containerMenu);
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		return menu;
	}

	/** A left click on {@code slot} as the list screen sends it. */
	static void click(ServerPlayer p, int containerId, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + mark + " " + menu.icon(slot));
		return mark;
	}

	static ChoiceList.Info info(GameTestHelper helper, ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info;
			}
		}
		throw new AssertionError("the menu has no line of its own");
	}

	static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	/** The rows on screen: slot to mark. */
	static Map<Integer, ChoiceList.Mark> rows(ChoiceMenu menu) {
		Map<Integer, ChoiceList.Mark> out = new HashMap<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Mark m = ChoiceList.mark(menu.icon(slot));
			if (m != null && m.kind() == ChoiceList.Kind.ROW) {
				out.put(slot, m);
			}
		}
		return out;
	}

	/** A shop: a counter with lots set by {@code lots} (column, goods, emerald price), a chest at (2,2,4); the shopkeeper. */
	static Villager shop(GameTestHelper helper, ServerPlayer owner) {
		helper.setDayTime(2000);
		BlockPos counterPos = new BlockPos(2, 2, 2);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		counter.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterPos), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		keeper.setNoAi(true);
		return keeper;
	}

	static ShopCounterBlockEntity counter(GameTestHelper helper) {
		return helper.getBlockEntity(new BlockPos(2, 2, 2));
	}

	static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(new BlockPos(2, 2, 4));
	}

	static void lot(GameTestHelper helper, int column, ItemStack goods, ItemStack price) {
		counter(helper).setItem(column, goods);
		counter(helper).setItem(ShopCounterBlockEntity.COLUMNS + column, price);
	}

	static int itemsNear(GameTestHelper helper, ServerPlayer player, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	static void fillInventory(ServerPlayer player) {
		for (int i = 0; i < 36; i++) {
			player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		}
	}

	// ---------------------------------------------------------------- the shop (42.4)

	/**
	 * Buy on one lot, then Buy on another: the gold Confirm moves to the second and nothing is charged; back to the first,
	 * then Confirm there buys the first lot only, for its own price (200), once; the owner (online) gets the 200 and the
	 * buyer reads "Bought 16 Cobblestone for 200 CobbleDollars".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopSwitch1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopSwitch1109c")
	public void shopSwitchingRowsMovesConfirmAndChargesOnlyTheConfirmedLot(GameTestHelper helper) {
		List<String> ownerSeen = new ArrayList<>();
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, ownerSeen);
		Villager keeper = shop(helper, owner);
		lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		lot(helper, 1, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.EMERALD, 3));
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		chest(helper).setItem(1, new ItemStack(Items.IRON_INGOT, 8));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 1000);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		int cobble = Shops.FIRST_GOODS_SLOT;
		int iron = Shops.FIRST_GOODS_SLOT + 1;
		click(buyer, menu.containerId, cobble);
		click(buyer, menu.containerId, iron);
		helper.assertTrue(!row(helper, menu, cobble).on() && row(helper, menu, iron).on()
			&& key(row(helper, menu, cobble).button()).equals("message.aliveworkplace.shop.menu.button_buy"),
			"Buy on iron didn't move the gold Confirm off cobblestone: " + row(helper, menu, cobble) + " / " + row(helper, menu, iron));
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "switching rows charged: " + CobbleDollarsBank.balance(buyer));
		click(buyer, menu.containerId, cobble);
		helper.assertTrue(row(helper, menu, cobble).on() && !row(helper, menu, iron).on() && CobbleDollarsBank.balance(buyer) == 1000,
			"back to cobblestone: it should be the one waiting, nothing paid: " + CobbleDollarsBank.balance(buyer));
		click(buyer, menu.containerId, cobble);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 800, "Confirm on cobblestone didn't charge its 200: " + CobbleDollarsBank.balance(buyer));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 16 && buyer.getInventory().countItem(Items.IRON_INGOT) == 0,
			"bought: " + buyer.getInventory().countItem(Items.COBBLESTONE) + " cobblestone, " + buyer.getInventory().countItem(Items.IRON_INGOT) + " iron");
		helper.assertTrue(chest(helper).countItem(Items.COBBLESTONE) == 24 && chest(helper).countItem(Items.IRON_INGOT) == 8, "the chest after the sale");
		helper.assertTrue(CobbleDollarsBank.balance(owner) == ownerBefore + 200, "the owner wasn't paid 200: " + CobbleDollarsBank.balance(owner));
		String bought = Component.translatable("message.aliveworkplace.shop.bought", 16, new ItemStack(Items.COBBLESTONE).getHoverName(),
			Money.describe(200, 2)).getString();
		helper.assertTrue(bought.equals("Bought 16 Cobblestone for 200 CobbleDollars") && seen.contains(bought), "the buyer read: " + seen);
		helper.assertTrue(ownerSeen.stream().anyMatch(s -> s.equals("listTutor bought 16× Cobblestone from your shop for 200 CobbleDollars")),
			"the owner read: " + ownerSeen);
		// the row now says the stock left (24 / 16 = 1 lot) and isn't gold
		helper.assertTrue(row(helper, menu, cobble).second().getString().equals("1 in stock") && !row(helper, menu, cobble).on(),
			"cobblestone's row after the sale: " + row(helper, menu, cobble).second().getString());
		helper.succeed();
	}

	/**
	 * Buy, then the stock is taken out of the chest before Confirm: Confirm says "Cobblestone is sold out." and charges
	 * nothing; nothing reaches the buyer.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopSoldOut1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopSoldOut1109c")
	public void shopStockGoneBetweenBuyAndConfirmChargesNothing(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = shop(helper, owner);
		lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 500);
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		helper.assertTrue(row(helper, menu, Shops.FIRST_GOODS_SLOT).on(), "Buy didn't wait for Confirm");
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 10)); // someone took 6: not a whole lot left
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 500, "charged for stock that was gone: " + CobbleDollarsBank.balance(buyer));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0 && chest(helper).countItem(Items.COBBLESTONE) == 10,
			"cobblestone moved: buyer " + buyer.getInventory().countItem(Items.COBBLESTONE) + ", chest " + chest(helper).countItem(Items.COBBLESTONE));
		helper.assertTrue(seen.contains("Cobblestone is sold out."), "the buyer read: " + seen);
		helper.succeed();
	}

	/**
	 * A full inventory: Confirm still sells (charged once) and the lot lands at the buyer's feet, whole, instead of being
	 * lost.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopFull1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopFull1109c")
	public void shopFullInventoryDropsTheLotAtTheBuyersFeet(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = shop(helper, owner);
		lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 500);
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		fillInventory(buyer);
		int before = itemsNear(helper, buyer, Items.COBBLESTONE);
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 300, "charged " + (500 - CobbleDollarsBank.balance(buyer)) + ", not 200");
		helper.assertTrue(itemsNear(helper, buyer, Items.COBBLESTONE) - before == 16 && chest(helper).countItem(Items.COBBLESTONE) == 16,
			"the 16 cobblestone weren't dropped for a full inventory: " + (itemsNear(helper, buyer, Items.COBBLESTONE) - before));
		helper.succeed();
	}

	/**
	 * With CobbleDollars, a lot priced in items (2 diamonds) still sells for the items: its price reads "2× Diamond" (red
	 * with one diamond, green with three); with one it's refused ("That costs 2× Diamond."), with three it takes two and
	 * puts them in the shop's chest, and no CobbleDollars move.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopItemPrice1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopItemPrice1109c")
	public void shopItemPricedLotPaysInItemsNotDollars(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = shop(helper, owner);
		lot(helper, 0, new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.DIAMOND, 2));
		chest(helper).setItem(0, new ItemStack(Items.OAK_LOG, 16));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 1000);
		buyer.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 1));
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		int slot = Shops.FIRST_GOODS_SLOT;
		ChoiceList.Mark mark = row(helper, menu, slot);
		helper.assertTrue(mark.price().getString().equals("2× Diamond") && colored(mark.price(), ChatFormatting.RED),
			"the item price with one diamond: " + mark.price().getString() + " " + mark.price().getStyle());
		click(buyer, menu.containerId, slot);
		click(buyer, menu.containerId, slot);
		helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == 1 && buyer.getInventory().countItem(Items.OAK_LOG) == 0
			&& CobbleDollarsBank.balance(buyer) == 1000, "sold for one diamond (or for dollars)");
		helper.assertTrue(seen.contains("That costs 2× Diamond."), "the refusal: " + seen);
		buyer.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 2));
		helper.runAfterDelay(3, () -> {
			// the row doesn't watch diamonds (only the money): reopen, as a player would after a refusal
			ChoiceMenu again = open(helper, buyer, keeper, false);
			helper.assertTrue(colored(row(helper, again, slot).price(), ChatFormatting.GREEN), "three diamonds and the price isn't green: " + row(helper, again, slot));
			click(buyer, again.containerId, slot);
			click(buyer, again.containerId, slot);
			helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == 1 && buyer.getInventory().countItem(Items.OAK_LOG) == 8,
				"three diamonds: " + buyer.getInventory().countItem(Items.DIAMOND) + " left, " + buyer.getInventory().countItem(Items.OAK_LOG) + " logs");
			helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "dollars taken for an item price: " + CobbleDollarsBank.balance(buyer));
			helper.assertTrue(chest(helper).countItem(Items.DIAMOND) == 2, "the diamonds didn't reach the shop's chest: " + chest(helper).countItem(Items.DIAMOND));
			helper.succeed();
		});
	}

	/**
	 * The row a click lands on: lots only in the counter's first and last columns sit in slots 18 and 18 + 8; the empty
	 * columns between, the money line and the divider take no click; and a shop with nothing in stock shows no rows and
	 * says "Nothing in stock right now".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopEdges1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopEdges1109c")
	public void shopEdgeColumnsAndEmptyShop(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = shop(helper, owner);
		int last = ShopCounterBlockEntity.COLUMNS - 1;
		lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		lot(helper, last, new ItemStack(Items.GLASS, 4), new ItemStack(Items.EMERALD, 1));
		lot(helper, 4, new ItemStack(Items.BRICKS, 4), new ItemStack(Items.EMERALD, 1)); // priced but not in stock: no row
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 500);
		// nothing in the chest yet: no rows, the hint says so
		ChoiceMenu empty = open(helper, buyer, keeper, false);
		helper.assertTrue(rows(empty).isEmpty(), "rows with nothing in stock: " + rows(empty).keySet());
		helper.assertTrue(key(info(helper, empty).hint()).equals("message.aliveworkplace.shop.menu.empty")
			&& info(helper, empty).hint().getString().equals("Nothing in stock right now"), "the empty shop's hint: " + info(helper, empty).hint());
		buyer.closeContainer();
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		chest(helper).setItem(1, new ItemStack(Items.GLASS, 4));
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		helper.assertTrue(rows(menu).keySet().equals(Set.of(Shops.FIRST_GOODS_SLOT, Shops.FIRST_GOODS_SLOT + last)),
			"the rows: " + rows(menu).keySet());
		helper.assertTrue(menu.icon(Shops.FIRST_GOODS_SLOT + last).is(Items.GLASS), "the last column's row: " + menu.icon(Shops.FIRST_GOODS_SLOT + last));
		for (int slot : new int[]{4, 9, 13, 17, Shops.FIRST_GOODS_SLOT + 1, Shops.FIRST_GOODS_SLOT + 4, Shops.FIRST_GOODS_SLOT + last + 1, ChoiceMenu.SIZE - 1}) {
			click(buyer, menu.containerId, slot);
			click(buyer, menu.containerId, slot);
		}
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 500 && rows(menu).values().stream().noneMatch(ChoiceList.Mark::on)
			&& buyer.getInventory().countItem(Items.BRICKS) == 0, "a click off the rows did something: " + CobbleDollarsBank.balance(buyer));
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT + last);
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT + last);
		helper.assertTrue(buyer.getInventory().countItem(Items.GLASS) == 4 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0
			&& CobbleDollarsBank.balance(buyer) == 400, "the last column's row bought " + buyer.getInventory().countItem(Items.GLASS) + " glass");
		helper.succeed();
	}

	/**
	 * The shopkeeper gone between Buy and Confirm (killed): the screen closes, and a Confirm that arrives anyway charges
	 * nothing and hands nothing over. A shopkeeper whose counter is broken refuses the screen: "This shopkeeper has no
	 * counter to sell from."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopGone1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopGone1109c")
	public void shopkeeperGoneMidPurchaseChargesNothing(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = shop(helper, owner);
		lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 500);
		ChoiceMenu menu = open(helper, buyer, keeper, false);
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		keeper.discard();
		click(buyer, menu.containerId, Shops.FIRST_GOODS_SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 500 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"a Confirm after the shopkeeper was gone sold: " + CobbleDollarsBank.balance(buyer));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "the screen stayed open with the shopkeeper gone: " + buyer.containerMenu);
			Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
			Jobs.employ(helper.getLevel(), second, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
			second.setNoAi(true);
			helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
			seen.clear();
			ChoiceMenu none = interact(buyer, second, false);
			helper.assertTrue(none == null, "a shopkeeper without a counter opened " + none);
			helper.assertTrue(seen.contains("This shopkeeper has no counter to sell from."), "the refusal: " + seen);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the ferry (42.4)

	/** Two posts besides the ferry's own: one near (fare 1 emerald), one 600 blocks off (fare 3); both known to the player. */
	private record Ferry(Villager ferryman, TravelNetwork.Post near, TravelNetwork.Post far, TravelNetwork network) {
	}

	private static Ferry ferry(GameTestHelper helper, ServerPlayer player) {
		helper.setDayTime(2000);
		var dim = helper.getLevel().dimension();
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(6, 2, 6);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		GlobalPos homeAt = GlobalPos.of(dim, helper.absolutePos(home));
		GlobalPos nearAt = GlobalPos.of(dim, helper.absolutePos(away));
		GlobalPos farAt = GlobalPos.of(dim, helper.absolutePos(home).offset(600, 0, 0));
		network.add(homeAt, "QaHarbor");
		TravelNetwork.Post near = network.add(nearAt, "QaLighthouse");
		TravelNetwork.Post far = network.add(farAt, "QaFarIsle");
		PartnerShowsCompatTests.after(helper, () -> {
			network.remove(homeAt);
			network.remove(nearAt);
			network.remove(farAt);
		});
		network.visit(player.getUUID(), near);
		network.visit(player.getUUID(), far);
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		ferryman.setNoAi(true);
		return new Ferry(ferryman, near, far, network);
	}

	private static int slotOf(ChoiceMenu menu, String post) {
		for (int slot = Ferrymen.FIRST_DESTINATION_SLOT; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(ModItems.TRAVEL_TICKET) && menu.icon(slot).getHoverName().getString().equals(post)) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * Two destinations, each at its own fare ("100 CobbleDollars", "300 CobbleDollars"); Buy on one then Buy on the other
	 * moves the gold Confirm and charges nothing; Confirm pays the far fare (300), hands over the far post's ticket (not the
	 * near one's) and says "Ticket to QaFarIsle bought: use it near a Travel Post."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFerrySwitch1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFerrySwitch1109c")
	public void ferrySwitchingDestinationsSellsTheConfirmedTicketAtItsFare(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		Ferry f = ferry(helper, player);
		CobbleDollarsBank.add(player, 1000);
		ChoiceMenu menu = open(helper, player, f.ferryman(), false);
		int near = slotOf(menu, "QaLighthouse");
		int far = slotOf(menu, "QaFarIsle");
		helper.assertTrue(near >= 0 && far >= 0 && rows(menu).size() == 2, "the destinations: " + rows(menu).keySet() + " near " + near + " far " + far);
		helper.assertTrue(row(helper, menu, near).price().getString().equals("100 CobbleDollars")
			&& row(helper, menu, far).price().getString().equals("300 CobbleDollars"),
			"the fares: " + row(helper, menu, near).price().getString() + " / " + row(helper, menu, far).price().getString());
		click(player, menu.containerId, near);
		click(player, menu.containerId, far);
		helper.assertTrue(!row(helper, menu, near).on() && row(helper, menu, far).on() && CobbleDollarsBank.balance(player) == 1000,
			"Buy on the far post after the near one: near " + row(helper, menu, near) + ", balance " + CobbleDollarsBank.balance(player));
		click(player, menu.containerId, far);
		helper.assertTrue(CobbleDollarsBank.balance(player) == 700, "the far fare wasn't 300: " + CobbleDollarsBank.balance(player));
		int tickets = 0;
		java.util.UUID to = null;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(ModItems.TRAVEL_TICKET)) {
				tickets += s.getCount();
				to = s.get(ModComponents.TICKET).post();
			}
		}
		helper.assertTrue(tickets == 1 && f.far().id().equals(to), "the ticket bought: " + tickets + " to " + to + " (far is " + f.far().id() + ")");
		helper.assertTrue(seen.contains("Ticket to QaFarIsle bought: use it near a Travel Post."), "the buyer read: " + seen);
		helper.succeed();
	}

	/**
	 * The ferry taken: the ticket bought on the list, used (right-click) next to the ferry's post, sets off for the
	 * Lighthouse (a ride starts) and is used up.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFerryRide1109c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFerryRide1109c")
	public void ferryTicketFromTheListTakesThePlayerThere(GameTestHelper helper) {
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Ferry f = ferry(helper, player);
		CobbleDollarsBank.add(player, 100);
		ChoiceMenu menu = open(helper, player, f.ferryman(), false);
		int near = slotOf(menu, "QaLighthouse");
		click(player, menu.containerId, near);
		click(player, menu.containerId, near);
		helper.assertTrue(CobbleDollarsBank.balance(player) == 0 && player.getInventory().countItem(ModItems.TRAVEL_TICKET) == 1,
			"exactly the fare (100) didn't buy the ticket: " + CobbleDollarsBank.balance(player));
		player.closeContainer();
		int at = -1;
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getItem(i).is(ModItems.TRAVEL_TICKET)) {
				at = i;
			}
		}
		ItemStack ticket = player.getInventory().getItem(at).copy();
		player.getInventory().setItem(at, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.MAIN_HAND, ticket);
		Vec3 start = player.position();
		player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getInventory().countItem(ModItems.TRAVEL_TICKET) == 0, "the ticket wasn't used up");
		helper.assertTrue(FerryRides.riding(player) || player.position().distanceTo(start) > 2, "using the ticket by the post didn't set off");
		helper.succeed();
	}

	/** A full inventory: the ticket is still sold once and lands at the player's feet. */
	//$ gametest_ticks_batch AREA '100' '"qaFerryFull1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFerryFull1109c")
	public void ferryFullInventoryDropsTheTicket(GameTestHelper helper) {
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Ferry f = ferry(helper, player);
		CobbleDollarsBank.add(player, 500);
		ChoiceMenu menu = open(helper, player, f.ferryman(), false);
		int near = slotOf(menu, "QaLighthouse");
		fillInventory(player);
		click(player, menu.containerId, near);
		click(player, menu.containerId, near);
		helper.assertTrue(CobbleDollarsBank.balance(player) == 400, "charged " + (500 - CobbleDollarsBank.balance(player)) + ", not 100");
		helper.assertTrue(itemsNear(helper, player, ModItems.TRAVEL_TICKET) == 1, "the ticket wasn't dropped for a full inventory: " + itemsNear(helper, player, ModItems.TRAVEL_TICKET));
		helper.succeed();
	}

	/**
	 * The ferryman falls asleep between Buy and Confirm: the screen closes and a Confirm that arrives anyway sells nothing.
	 * A player who knows no other post gets no screen and reads "You don't know any other Travel Posts yet. Find one and
	 * right-click it."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFerrySleep1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFerrySleep1109c")
	public void ferrymanAsleepMidPurchaseAndNowhereToGo(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		Ferry f = ferry(helper, player);
		CobbleDollarsBank.add(player, 500);
		ChoiceMenu menu = open(helper, player, f.ferryman(), false);
		int near = slotOf(menu, "QaLighthouse");
		click(player, menu.containerId, near);
		// a bed for him (foot at 3,2,3, head at 3,2,4) so he stays asleep
		var foot = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
		helper.setBlock(new BlockPos(3, 2, 3), foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		helper.setBlock(new BlockPos(3, 2, 4), foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		f.ferryman().startSleeping(helper.absolutePos(new BlockPos(3, 2, 4)));
		helper.assertTrue(f.ferryman().isSleeping(), "the ferryman isn't asleep");
		click(player, menu.containerId, near);
		helper.assertTrue(CobbleDollarsBank.balance(player) == 500 && player.getInventory().countItem(ModItems.TRAVEL_TICKET) == 0,
			"a sleeping ferryman sold a ticket: " + CobbleDollarsBank.balance(player));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the screen stayed open with the ferryman asleep");
			f.ferryman().stopSleeping();
			ServerPlayer stranger = TutorListMenuCompatTests.player(helper, seen);
			seen.clear();
			ChoiceMenu none = interact(stranger, f.ferryman(), false);
			helper.assertTrue(none == null, "a player who knows no post got " + none);
			helper.assertTrue(seen.contains("You don't know any other Travel Posts yet. Find one and right-click it."), "they read: " + seen);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the job lists (42.3)

	/**
	 * The Ball Smith's list matches what the chest offered: one row per kind of ball in her recipes (none cut off), the
	 * easiest first; at Apprentice the balls of her level or below show no level, those above show the level they need
	 * (and Master shows none). Two picks by click survive a save and reload: the reloaded smith's list shows both gold and
	 * "Makes the picked balls".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithList1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithList1109c")
	public void smithListMatchesHerRecipesAndPicksSurviveReload(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		smith.setNoAi(true);
		smith.setVillagerData(smith.getVillagerData().setLevel(2));
		Map<Item, Integer> tiers = new HashMap<>();
		for (BallRecipes.BallRecipe r : BallRecipes.all(helper.getLevel())) {
			tiers.merge(r.result().getItem(), r.tier(), Math::min);
		}
		helper.assertTrue(tiers.size() > 5, "too few ball kinds to test: " + tiers);
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		ChoiceMenu menu = open(helper, player, smith, true);
		Map<Integer, ChoiceList.Mark> rows = rows(menu);
		Set<Item> shown = new HashSet<>();
		int lastTier = 0;
		StringBuilder bad = new StringBuilder();
		for (int slot = BallSmiths.FIRST_BALL_SLOT; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Mark m = rows.get(slot);
			if (m == null) {
				continue;
			}
			Item ball = menu.icon(slot).getItem();
			shown.add(ball);
			Integer tier = tiers.get(ball);
			if (tier == null) {
				bad.append("\n  not in her recipes: ").append(ball);
				continue;
			}
			if (tier < lastTier) {
				bad.append("\n  out of order: ").append(ball).append(" tier ").append(tier).append(" after ").append(lastTier);
			}
			lastTier = tier;
			String price = m.price() == null ? null : key(m.price());
			if (tier <= 2 && price != null) {
				bad.append("\n  an Apprentice shows a level for tier ").append(tier).append(": ").append(ball).append(" ").append(price);
			}
			if (tier > 2 && !("merchant.level." + tier).equals(price)) {
				bad.append("\n  tier ").append(tier).append(" without its level: ").append(ball).append(" ").append(price);
			}
		}
		helper.assertTrue(shown.equals(tiers.keySet()), "rows " + shown.size() + " vs her kinds " + tiers.size() + ": missing "
			+ tiers.keySet().stream().filter(b -> !shown.contains(b)).toList());
		helper.assertTrue(bad.isEmpty(), "the smith's rows:" + bad);
		// pick two by click
		List<Integer> slots = new ArrayList<>(rows.keySet());
		slots.sort(Integer::compare);
		int a = slots.get(0);
		int b = slots.get(slots.size() - 1);
		Item ballA = menu.icon(a).getItem();
		Item ballB = menu.icon(b).getItem();
		click(player, menu.containerId, a);
		click(player, menu.containerId, b);
		helper.assertTrue(new HashSet<>(BallSmiths.orders(smith)).equals(Set.of(BuiltInRegistries.ITEM.getKey(ballA), BuiltInRegistries.ITEM.getKey(ballB))),
			"orders after two picks: " + BallSmiths.orders(smith));
		helper.assertTrue(BallSmiths.wants(smith, new ItemStack(ballA)) && !BallSmiths.wants(smith, new ItemStack(menu.icon(slots.get(1)).getItem())),
			"she should make only the picked balls");
		player.closeContainer();
		// save and reload the smith
		CompoundTag tag = new CompoundTag();
		smith.save(tag);
		smith.discard();
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(tag);
		helper.getLevel().addFreshEntity(loaded);
		loaded.setNoAi(true);
		helper.assertTrue(new HashSet<>(BallSmiths.orders(loaded)).equals(Set.of(BuiltInRegistries.ITEM.getKey(ballA), BuiltInRegistries.ITEM.getKey(ballB))),
			"orders after a reload: " + BallSmiths.orders(loaded));
		ChoiceMenu again = open(helper, player, loaded, true);
		helper.assertTrue(row(helper, again, a).on() && row(helper, again, b).on() && !row(helper, again, slots.get(1)).on(), "the reloaded list's gold rows");
		helper.assertTrue(key(info(helper, again).money()).equals("screen.aliveworkplace.ball_smith.mode_only")
			&& info(helper, again).money().getString().equals("Makes the picked balls"), "the top after a reload: " + info(helper, again).money().getString());
		loaded.discard();
		helper.succeed();
	}

	/** A Master smith shows no level on any row; the smith killed while her orders are open closes them, and a late click changes nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaSmithGone1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithGone1109c")
	public void masterSmithShowsNoLevelsAndHerDeathClosesTheOrders(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		smith.setNoAi(true);
		smith.setVillagerData(smith.getVillagerData().setLevel(5));
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		ChoiceMenu menu = open(helper, player, smith, true);
		helper.assertTrue(rows(menu).values().stream().allMatch(m -> m.price() == null), "a Master smith's rows show a level: "
			+ rows(menu).values().stream().filter(m -> m.price() != null).map(m -> m.price().getString()).toList());
		int first = BallSmiths.FIRST_BALL_SLOT;
		row(helper, menu, first);
		smith.kill();
		click(player, menu.containerId, first);
		helper.assertTrue(BallSmiths.orders(smith).isEmpty(), "a click after her death gave an order: " + BallSmiths.orders(smith));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "her orders stayed open after her death");
			helper.succeed();
		});
	}

	/**
	 * The berry book's paging at its edges: Previous on the first page goes round to the last ("page N of N"), a berry
	 * there can be made her goal by click (the goal saved on her, its row gold), and Next from the last page comes back to
	 * page 1. Without a plot there's no Stop row; with a goal the Clear row names it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBerryPages1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBerryPages1109c")
	public void berryBookPagesWrapAndAGoalOnTheLastPage(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos composter = new BlockPos(2, 2, 2);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		helper.setBlock(new BlockPos(1, 2, 7), ModBlocks.VILLAGE_HALL);
		Villager breeder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Jobs.employ(helper.getLevel(), breeder, helper.absolutePos(composter), net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		breeder.setNoAi(true);
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		ChoiceMenu menu = open(helper, player, breeder, true);
		ChoiceList.Mark next = ChoiceList.mark(menu.icon(53));
		helper.assertTrue(next != null && next.kind() == ChoiceList.Kind.PAGE, "the book has one page only; nothing to test: " + menu.icon(53));
		String page1 = menu.icon(49).getHoverName().getString();
		helper.assertTrue(page1.startsWith("Berry book · page 1 of "), "page 1's name: " + page1);
		String pages = page1.substring("Berry book · page 1 of ".length());
		click(player, menu.containerId, 45);
		String last = menu.icon(49).getHoverName().getString();
		helper.assertTrue(last.equals("Berry book · page " + pages + " of " + pages), "Previous from page 1: " + last);
		helper.assertTrue(menu.icon(50).isEmpty() || ChoiceList.mark(menu.icon(50)) == null, "a Stop row without a plot: " + menu.icon(50));
		// the last bred berry on the last page, its parents put in her chest so she can make it
		var mutations = BerryBreeders.data().mutations();
		int slot = -1;
		for (int s = 44; s >= 0 && slot < 0; s--) {
			if (ChoiceList.mark(menu.icon(s)) != null) {
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(menu.icon(s).getItem());
				var pairs = io.github.jcondedata.aliveworkplace.berry.BerryChains.madeFrom(mutations, id);
				if (!pairs.isEmpty()) {
					slot = s;
					Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
					chest.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(pairs.get(0).a()), 4));
					chest.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(pairs.get(0).b()), 4));
				}
			}
		}
		helper.assertTrue(slot >= 0, "the last page has no bred berry");
		ItemStack berry = menu.icon(slot).copy();
		click(player, menu.containerId, slot);
		ResourceLocation goal = BerryBreeders.goal(breeder);
		helper.assertTrue(goal != null && BuiltInRegistries.ITEM.get(goal) == berry.getItem(), "the goal after clicking " + berry + ": " + goal);
		helper.assertTrue(row(helper, menu, slot).on(), "the goal's row isn't gold");
		ChoiceList.Mark clear = row(helper, menu, 48);
		helper.assertTrue(clear.second() != null && clear.second().getString().equals(BerryBreeders.name(goal).getString()), "the Clear row: " + clear.second());
		click(player, menu.containerId, 53);
		helper.assertTrue(menu.icon(49).getHoverName().getString().equals(page1), "Next from the last page: " + menu.icon(49).getHoverName().getString());
		// the goal is saved on her
		CompoundTag tag = new CompoundTag();
		breeder.save(tag);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(goal.equals(BerryBreeders.goal(copy)), "her goal after a reload: " + BerryBreeders.goal(copy));
		helper.succeed();
	}

	/**
	 * The lures list matches the chest's: a row per lure, no two alike, none hidden under "Any snack" or the top line;
	 * a click on the first and on the last lure each says "<her name> now sets out snacks for <lure>" and the top line
	 * follows; her lure is saved on her; the keeper gone closes the screen and a late click changes nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLures1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLures1109c")
	public void luresListEdgesSaysItAndSurvivesReload(GameTestHelper helper) {
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		ChoiceMenu menu = open(helper, player, keeper, true);
		List<String> lures = HabitatKeepers.lures();
		int onPage = Math.min(lures.size(), 45);
		Set<String> names = new HashSet<>();
		for (int i = 0; i < onPage; i++) {
			ChoiceList.Mark m = row(helper, menu, i);
			names.add(menu.icon(i).getHoverName().getString());
			helper.assertTrue(key(m.button()).equals("message.aliveworkplace.habitat_keeper.lures.button_choose"), "lure " + i + "'s button: " + m.button());
		}
		helper.assertTrue(names.size() == onPage, "two lure rows read alike: " + names.size() + " names for " + onPage + " rows");
		helper.assertTrue(ChoiceList.info(menu.icon(HabitatKeepers.LURES_INFO)) != null && HabitatKeepers.LURES_INFO >= 45 && HabitatKeepers.ANY_SLOT >= 45,
			"the top line or Any snack sits on a lure row");
		for (int i : new int[]{0, onPage - 1}) {
			String lure = lures.get(i);
			seen.clear();
			click(player, menu.containerId, i);
			helper.assertTrue(lure.equals(HabitatKeepers.lure(keeper)), "clicking row " + i + " set " + HabitatKeepers.lure(keeper) + ", not " + lure);
			String said = keeper.getDisplayName().getString() + " now sets out snacks for " + HabitatKeepers.lureName(lure).getString();
			helper.assertTrue(seen.contains(said), "she should say \"" + said + "\": " + seen);
			helper.assertTrue(info(helper, menu).money().getString().equals("Her lure now: " + HabitatKeepers.lureName(lure).getString()),
				"the top line: " + info(helper, menu).money().getString());
			helper.assertTrue(row(helper, menu, i).on() && rows(menu).values().stream().filter(ChoiceList.Mark::on).count() == 1,
				"one gold row only after choosing row " + i);
		}
		String chosen = lures.get(onPage - 1);
		CompoundTag tag = new CompoundTag();
		keeper.save(tag);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(chosen.equals(HabitatKeepers.lure(copy)), "her lure after a reload: " + HabitatKeepers.lure(copy));
		keeper.discard();
		click(player, menu.containerId, 0);
		helper.assertTrue(chosen.equals(HabitatKeepers.lure(keeper)), "a click after she was gone changed her lure to " + HabitatKeepers.lure(keeper));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the lures stayed open with her gone");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- words

	/**
	 * Every sentence the Shop, Ferry, smith, book and lure lists show is in the language file, filled with real arguments
	 * (no "%s" or raw key left) and in American English.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void shopFerryAndJobListWordsReadRight(GameTestHelper helper) {
		Language en = Language.getInstance();
		Pattern british = Pattern.compile("(?i)\\b(colour|favour|honour|grey|traveller|travellers|cancelled|defence|armour|jeweller|centre|labour|catalogue)\\b");
		List<String> keys = List.of(
			"message.aliveworkplace.shop.menu.list_hint", "message.aliveworkplace.shop.menu.column_goods", "message.aliveworkplace.shop.menu.column_stock",
			"message.aliveworkplace.shop.menu.column_price", "message.aliveworkplace.shop.menu.button_buy", "message.aliveworkplace.shop.menu.button_confirm",
			"message.aliveworkplace.shop.menu.empty", "message.aliveworkplace.shop.menu.stock", "message.aliveworkplace.shop.too_poor",
			"message.aliveworkplace.shop.bought", "message.aliveworkplace.shop.sold_out", "message.aliveworkplace.travel.list_hint",
			"message.aliveworkplace.travel.column_destination", "message.aliveworkplace.travel.column_fare", "message.aliveworkplace.travel.bought",
			"message.aliveworkplace.travel.nowhere", "screen.aliveworkplace.ball_smith.mode_any", "screen.aliveworkplace.ball_smith.mode_only",
			"screen.aliveworkplace.ball_smith.list_hint", "screen.aliveworkplace.ball_smith.column_ball", "screen.aliveworkplace.ball_smith.column_level",
			"screen.aliveworkplace.orders.column_order", "screen.aliveworkplace.orders.button_pick", "screen.aliveworkplace.orders.button_unpick",
			"screen.aliveworkplace.ball_smith.on", "screen.aliveworkplace.ball_smith.off",
			"message.aliveworkplace.berry_breeder.book.found_short", "message.aliveworkplace.berry_breeder.book.column_berry",
			"message.aliveworkplace.berry_breeder.book.column_book", "message.aliveworkplace.berry_breeder.book.column_from",
			"message.aliveworkplace.berry_breeder.book.button_set", "message.aliveworkplace.berry_breeder.book.button_goal",
			"message.aliveworkplace.berry_breeder.book.button_clear", "message.aliveworkplace.berry_breeder.book.button_stop",
			"message.aliveworkplace.berry_breeder.book.lit_short", "message.aliveworkplace.berry_breeder.book.unfound",
			"message.aliveworkplace.berry_breeder.book.wild_short", "message.aliveworkplace.berry_breeder.book.pair",
			"message.aliveworkplace.habitat_keeper.lures.list_hint", "message.aliveworkplace.habitat_keeper.lures.column_lure",
			"message.aliveworkplace.habitat_keeper.lures.column_berries", "message.aliveworkplace.habitat_keeper.lures.button_choose",
			"message.aliveworkplace.habitat_keeper.lures.button_chosen", "message.aliveworkplace.habitat_keeper.lures.any_second");
		StringBuilder bad = new StringBuilder();
		for (String key : keys) {
			if (!en.has(key)) {
				bad.append("\n  missing: ").append(key);
				continue;
			}
			String text = en.getOrDefault(key);
			if (text.isBlank() || text.equals(key)) {
				bad.append("\n  empty: ").append(key);
			}
			if (british.matcher(text).find()) {
				bad.append("\n  British: ").append(key).append(" = ").append(text);
			}
		}
		Component oran = Component.literal("Oran Berry");
		List<Component> filled = List.of(
			Component.translatable("message.aliveworkplace.shop.menu.stock", 3),
			Component.translatable("message.aliveworkplace.shop.too_poor", Money.describe(1500, 15)),
			Component.translatable("message.aliveworkplace.shop.bought", 16, Component.literal("Cobblestone"), Money.describe(200, 2)),
			Component.translatable("message.aliveworkplace.shop.sold_out", Component.literal("Cobblestone")),
			Component.translatable("message.aliveworkplace.travel.bought", "Lighthouse"),
			Component.translatable("message.aliveworkplace.berry_breeder.book.found_short", 4, 68),
			Component.translatable("message.aliveworkplace.berry_breeder.book.pair", oran, Component.literal("Cheri Berry")),
			Component.translatable("message.aliveworkplace.habitat_keeper.lures.now", HabitatKeepers.lureName("typing/fire")),
			Component.translatable("message.aliveworkplace.habitat_keeper.lures.now", HabitatKeepers.lureName(null)));
		for (Component c : filled) {
			String s = c.getString();
			if (s.contains("%s") || s.contains("%d") || s.contains("aliveworkplace.") || s.contains("%1$")) {
				bad.append("\n  unfilled: ").append(s);
			}
		}
		helper.assertTrue(Money.describe(150000, 1500).getString().equals("150,000 CobbleDollars"), "thousands: " + Money.describe(150000, 1500).getString());
		helper.assertTrue(Component.translatable("message.aliveworkplace.shop.too_poor", Money.describe(1500, 15)).getString().equals("That costs 1,500 CobbleDollars."),
			"too poor: " + Component.translatable("message.aliveworkplace.shop.too_poor", Money.describe(1500, 15)).getString());
		helper.assertTrue(bad.isEmpty(), "the list screens' words:" + bad);
		helper.succeed();
	}
}
