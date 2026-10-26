package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-e-1026-1016): every screen the mod registers, generated from the menu registry, like the payload and component
 * generators. The server's menu and the client's copy (what the screen draws and clicks) are two different classes for
 * most of them (a ChoiceMenu on the server, a ChoiceView or PokemonTradeView on the client). The server sends slot
 * contents and numbers by index, so the two must agree on every index: the same number of slots, each slot showing the
 * same container's same index (the buttons, then the player's bag in the same order), and the same number of data slots
 * (the hall's open section and date). Otherwise a client draws a player's items in the wrong place, a click lands on the
 * wrong button, or the screen's numbers go to the wrong field.
 */
public class QaEveryMenuGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** For each of the mod's menu types: its server menu and its client copy for {@code player}. */
	private static Map<MenuType<?>, Pair> pairs(GameTestHelper helper, ServerPlayer player) {
		Map<MenuType<?>, Pair> pairs = new LinkedHashMap<>();
		pairs.put(ModBlocks.VILLAGE_HALL_MENU, new Pair(
			() -> ChoiceMenu.openHall(player, Component.literal("qa"), p -> true, m -> {}),
			(id, inv) -> new ChoiceView(id, inv)));
		pairs.put(ModBlocks.STOREHOUSE_MENU, new Pair(
			() -> ChoiceMenu.openOn(ModBlocks.STOREHOUSE_MENU, player, Component.literal("qa"), p -> true, m -> {}),
			(id, inv) -> ModBlocks.STOREHOUSE_MENU.create(id, inv)));
		pairs.put(ModBlocks.POKEMON_TRADER_MENU, new Pair(
			() -> ChoiceMenu.openOn(ModBlocks.POKEMON_TRADER_MENU, player, Component.literal("qa"), p -> true, m -> {}),
			(id, inv) -> ModBlocks.POKEMON_TRADER_MENU.create(id, inv)));
		BlockPos boxAt = new BlockPos(2, 2, 2);
		pairs.put(ModBlocks.MAILBOX_MENU, new Pair(
			() -> {
				helper.setBlock(boxAt, ModBlocks.MAILBOX);
				MailboxBlockEntity box = helper.getBlockEntity(boxAt);
				return new MailboxMenu(9, player.getInventory(), box);
			},
			(id, inv) -> new MailboxMenu(id, inv, helper.absolutePos(boxAt))));
		return pairs;
	}

	record Pair(java.util.function.Supplier<AbstractContainerMenu> server, BiFunction<Integer, Inventory, AbstractContainerMenu> client) {
	}

	private static int dataSlots(AbstractContainerMenu menu) {
		try {
			Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
			field.setAccessible(true);
			return ((List<?>) field.get(menu)).size();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("AbstractContainerMenu.dataSlots isn't there: " + e);
		}
	}

	/** What a slot shows: the player's bag or the screen's own container, and which index of it. */
	private static String shows(Slot slot) {
		return (slot.container instanceof Inventory ? "bag" : "own") + "#" + slot.getContainerSlot();
	}

	/** Every menu type in the mod's namespace has a pair here, so a new screen can't skip this check. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyMenuTypeIsListed(GameTestHelper helper) {
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		TreeSet<String> registered = new TreeSet<>();
		for (ResourceLocation id : BuiltInRegistries.MENU.keySet()) {
			if (id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				registered.add(id.toString());
			}
		}
		TreeSet<String> listed = new TreeSet<>();
		for (MenuType<?> type : pairs(helper, player).keySet()) {
			listed.add(BuiltInRegistries.MENU.getKey(type).toString());
		}
		helper.assertTrue(registered.equals(listed), "the mod registers the menus " + registered + " but this test pairs " + listed);
		helper.succeed();
	}

	/** Each server menu and its client copy agree on the menu type, every slot and the number of data slots. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyServerMenuAndItsClientCopyAgreeSlotForSlot(GameTestHelper helper) {
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<MenuType<?>, Pair> e : pairs(helper, player).entrySet()) {
			String name = String.valueOf(BuiltInRegistries.MENU.getKey(e.getKey()));
			AbstractContainerMenu server = e.getValue().server().get();
			helper.assertTrue(server != null, name + ": the server menu didn't open");
			AbstractContainerMenu client = e.getValue().client().apply(server.containerId, player.getInventory());
			if (server.getType() != e.getKey() || client.getType() != e.getKey()) {
				wrong.add(name + ": types " + BuiltInRegistries.MENU.getKey(server.getType()) + " / " + BuiltInRegistries.MENU.getKey(client.getType()));
			}
			if (server.slots.size() != client.slots.size()) {
				wrong.add(name + ": " + server.slots.size() + " slots on the server, " + client.slots.size() + " on the client");
			} else {
				for (int i = 0; i < server.slots.size(); i++) {
					String s = shows(server.slots.get(i));
					String c = shows(client.slots.get(i));
					if (!s.equals(c)) {
						wrong.add(name + ": slot " + i + " is " + s + " on the server and " + c + " on the client");
						break;
					}
				}
			}
			int sd = dataSlots(server);
			int cd = dataSlots(client);
			if (sd != cd) {
				wrong.add(name + ": " + sd + " data slots on the server, " + cd + " on the client");
			}
			player.closeContainer();
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
