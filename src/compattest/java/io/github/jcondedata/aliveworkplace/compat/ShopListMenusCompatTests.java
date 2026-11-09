package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.travel.Ferrymen;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Shop and the Ferry with CobbleDollars on the list screen (ROADMAP 42.4), through the player's own clicks (a
 * right-click with an empty hand, as a client sends it, then the chest-click packets the list screen sends): the money at
 * the top, a row per lot or destination with its price (green when the player can pay it, red when not) and Buy, gold
 * Confirm once chosen; the second click pays, a price too dear is refused as before, and the prices follow the player's
 * money while the screen is open.
 */
public class ShopListMenusCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A right-click with an empty hand on {@code villager}, as a client sends it: the list screen it opens. */
	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		var view = ModBlocks.CHOICE_LIST_MENU.create(7, player.getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.slots.size() == menu.slots.size(), "the client's copy is " + view);
		return menu;
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + mark + " " + menu.icon(slot));
		return mark;
	}

	private static ChoiceList.Info info(GameTestHelper helper, ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info;
			}
		}
		throw new AssertionError("the menu has no line of its own (money, hint, headings)");
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	/**
	 * The shop: cobblestone at 2 emeralds (200 CobbleDollars) and iron at 6 (600) for a buyer with 500: the money at the
	 * top, the headings, cobblestone's row green with its stock and Buy, iron's red. Buy turns cobblestone's row gold with
	 * Confirm and charges nothing; Confirm pays 200. Iron is refused (too little money) and nothing moves; 400 more while
	 * the screen is open turn iron green with no click, and it's bought.
	 */
	//$ gametest_ticks_batch AREA '100' '"shopList"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "shopList")
	public void theShopIsAList(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos counterPos = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		int columns = ShopCounterBlockEntity.COLUMNS;
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(columns, new ItemStack(Items.EMERALD, 2));
		counter.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
		counter.setItem(columns + 1, new ItemStack(Items.EMERALD, 6));
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		chest.setItem(1, new ItemStack(Items.IRON_INGOT, 8));
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		counter.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterPos), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		keeper.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 500);
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't there");

		ChoiceMenu menu = open(helper, buyer, keeper);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(info.money() != null && info.money().getString().equals(Money.balance(buyer).getString())
			&& key(info.hint()).equals("message.aliveworkplace.shop.menu.list_hint") && info.columns().size() == 3, "the shop's own line: " + info);
		int cobble = Shops.FIRST_GOODS_SLOT;
		int iron = Shops.FIRST_GOODS_SLOT + 1;
		helper.assertTrue(menu.icon(cobble).is(Items.COBBLESTONE) && menu.icon(iron).is(Items.IRON_INGOT), "the lots: " + menu.icon(cobble) + " " + menu.icon(iron));
		ChoiceList.Mark mark = row(helper, menu, cobble);
		helper.assertTrue(!mark.on() && key(mark.button()).equals("message.aliveworkplace.shop.menu.button_buy")
			&& key(mark.second()).equals("message.aliveworkplace.shop.menu.stock") && colored(mark.price(), ChatFormatting.GREEN), "cobblestone's row: " + mark);
		helper.assertTrue(colored(row(helper, menu, iron).price(), ChatFormatting.RED), "iron at 600 isn't red for 500: " + row(helper, menu, iron));
		click(buyer, menu, cobble);
		mark = row(helper, menu, cobble);
		helper.assertTrue(mark.on() && key(mark.button()).equals("message.aliveworkplace.shop.menu.button_confirm") && CobbleDollarsBank.balance(buyer) == 500,
			"Buy didn't wait for Confirm: " + mark + ", " + CobbleDollarsBank.balance(buyer));
		click(buyer, menu, cobble);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 300 && buyer.getInventory().countItem(Items.COBBLESTONE) == 16 && !row(helper, menu, cobble).on(),
			"Confirm didn't buy: " + CobbleDollarsBank.balance(buyer));
		click(buyer, menu, iron);
		click(buyer, menu, iron);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 300 && buyer.getInventory().countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.IRON_INGOT) == 8,
			"iron was sold for too little money");
		helper.assertTrue(seen.stream().anyMatch(l -> l.contains("too_poor") || l.contains("600")), "the refusal wasn't said: " + seen);
		CobbleDollarsBank.add(buyer, 400);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(colored(row(helper, menu, iron).price(), ChatFormatting.GREEN), "700 didn't turn iron green while open: " + row(helper, menu, iron));
			helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(buyer).getString()), "the money at the top didn't follow");
			click(buyer, menu, iron);
			click(buyer, menu, iron);
			helper.assertTrue(CobbleDollarsBank.balance(buyer) == 100 && buyer.getInventory().countItem(Items.IRON_INGOT) == 4, "iron not bought: "
				+ CobbleDollarsBank.balance(buyer));
			helper.succeed();
		});
	}

	/**
	 * The ferry: the money at the top, the Lighthouse's row with its fare (100) red for a player with 50 and Buy; Buy then
	 * Confirm is refused and no ticket is handed over; 200 more turn the fare green while open, and Buy, Confirm sell the
	 * ticket for 100.
	 */
	//$ gametest_ticks_batch AREA '100' '"ferryList"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "ferryList")
	public void theFerryIsAList(GameTestHelper helper) {
		helper.setDayTime(2000);
		var dim = helper.getLevel().dimension();
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(6, 2, 6);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		network.add(GlobalPos.of(dim, helper.absolutePos(home)), "Harbor");
		var lighthouse = network.add(GlobalPos.of(dim, helper.absolutePos(away)), "Lighthouse");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		network.visit(player.getUUID(), lighthouse);
		CobbleDollarsBank.add(player, 50);
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		ferryman.setNoAi(true);

		ChoiceMenu menu = open(helper, player, ferryman);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(info.money() != null && info.money().getString().equals(Money.balance(player).getString())
			&& key(info.hint()).equals("message.aliveworkplace.travel.list_hint") && info.columns().size() == 3, "the ferry's own line: " + info);
		int slot = Ferrymen.FIRST_DESTINATION_SLOT;
		helper.assertTrue(menu.icon(slot).is(ModItems.TRAVEL_TICKET) && menu.icon(slot).getHoverName().getString().equals("Lighthouse"), "the row: " + menu.icon(slot));
		ChoiceList.Mark mark = row(helper, menu, slot);
		helper.assertTrue(!mark.on() && key(mark.button()).equals("message.aliveworkplace.shop.menu.button_buy") && colored(mark.price(), ChatFormatting.RED),
			"the Lighthouse's row for 50: " + mark);
		click(player, menu, slot);
		helper.assertTrue(row(helper, menu, slot).on() && key(row(helper, menu, slot).button()).equals("message.aliveworkplace.shop.menu.button_confirm"),
			"Buy didn't turn gold with Confirm");
		click(player, menu, slot);
		helper.assertTrue(CobbleDollarsBank.balance(player) == 50 && player.getInventory().countItem(ModItems.TRAVEL_TICKET) == 0, "a ticket for too little money");
		helper.assertTrue(seen.stream().anyMatch(l -> l.contains("too_poor") || l.contains("100")), "the refusal wasn't said: " + seen);
		CobbleDollarsBank.add(player, 200);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(colored(row(helper, menu, slot).price(), ChatFormatting.GREEN), "250 didn't turn the fare green while open: " + row(helper, menu, slot));
			click(player, menu, slot);
			click(player, menu, slot);
			helper.assertTrue(CobbleDollarsBank.balance(player) == 150 && player.getInventory().countItem(ModItems.TRAVEL_TICKET) == 1,
				"no ticket: " + CobbleDollarsBank.balance(player));
			helper.succeed();
		});
	}
}
