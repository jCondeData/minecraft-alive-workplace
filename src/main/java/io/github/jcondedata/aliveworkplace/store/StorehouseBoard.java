package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The board at the Storehouse: what the workers nearby are waiting for (see {@link Requests}) — the builders' missing
 * materials, a miner's pickaxe, a lumberjack's axe — and a click hands over what you have, straight into that worker's
 * chests. Only the workers the storehouse's porter would carry for are listed (the owner's and their friends', or the
 * village's own for a village storehouse).
 */
public final class StorehouseBoard {
	public static final int INFO_SLOT = 4;
	/** The stock orders (and, on their page, adding the item in hand). */
	public static final int ORDERS_SLOT = 8;
	/** The Steward's shopping list (27.19). */
	public static final int SHOPPING_SLOT = 6;
	/** Back to the requests, on the orders page. */
	public static final int BACK_SLOT = 0;
	public static final int FIRST_REQUEST = 9;
	/** How far from the storehouse the blocks whose requests it lists may be (35.7: a Wonder may stand 96 from its hall). */
	public static final int BLOCK_RANGE = 160;

	public static void open(ServerPlayer player, BlockPos storehouse) {
		ServerLevel level = Players.level(player);
		Employer owner = Porters.owner(level, storehouse);
		Component title = owner == null ? Component.translatable("screen.aliveworkplace.storehouse.village")
			: Component.translatable("screen.aliveworkplace.storehouse.owned", owner.name());
		// Drawn in the Blueprint Table's style (26.12) on its own menu type; the buttons and clicks are a chest's.
		ChoiceMenu.openOn(io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE_MENU, player, title,
			p -> p.isAlive() && level.getBlockState(storehouse).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE)
				&& p.position().distanceToSqr(Vec3.atCenterOf(storehouse)) <= 64,
			menu -> render(menu, level, storehouse, player));
	}

	/** The same board, not shown to anyone (tests). */
	public static ChoiceMenu boardForTest(ServerPlayer player, BlockPos storehouse) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), storehouse, player));
	}

	private static void render(ChoiceMenu menu, ServerLevel level, BlockPos storehouse, ServerPlayer viewer) {
		menu.clearButtons();
		StorageNetworks.Folded networks = SupplyContainers.networks(level, storehouse);
		List<BlockPos> store = networks.stores();
		long items = SupplyContainers.contents(level, store).values().stream().mapToLong(Long::longValue).sum();
		List<Requests.Request> requests = new ArrayList<>(Requests.near(level, storehouse, Porters.owner(level, storehouse)));
		requests.addAll(io.github.jcondedata.aliveworkplace.colony.Settlers.requests(level, storehouse)); // 33.9: the settlers' missing supplies
		requests.addAll(Requests.atBlocks(level, storehouse, BLOCK_RANGE)); // 35.7: a Wonder's goods, posted by its Foundation Stone
		ItemStack info = new ItemStack(Items.WRITABLE_BOOK);
		info.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.info"), ChatFormatting.GOLD));
		List<Component> lore = new ArrayList<>();
		long free = SupplyContainers.freeSlots(level, store);
		lore.add(plain(Component.translatable("screen.aliveworkplace.storehouse.stock", // B377, B395: each count has its singular
			io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.chests", store.size(), store.size()),
			io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.items", items, items),
			io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.slots", free, free)), ChatFormatting.GRAY));
		roomLine(level, store).ifPresent(lore::add);
		if (networks.linked() > 0) { // 37.9: a Sophisticated Storage controller's blocks are one store
			lore.add(plain(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.linked", networks.linked(), networks.linked()), ChatFormatting.GRAY));
		}
		int joined = StorageNetworks.joinedCount(level, store);
		if (joined > 0) { // 37.10: a Tom's Simple Storage inventory connector in the Storehouse
			lore.add(plain(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.connector", joined, joined), ChatFormatting.GRAY));
		}
		lore.add(plain(Component.translatable(requests.isEmpty() ? "screen.aliveworkplace.storehouse.nothing" : "screen.aliveworkplace.storehouse.click"),
			ChatFormatting.GRAY));
		info.set(DataComponents.LORE, new ItemLore(lore));
		menu.button(INFO_SLOT, info, null);
		shoppingList(menu, level, storehouse);
		ItemStack ordersIcon = new ItemStack(Items.WRITABLE_BOOK);
		int orders = StockOrders.of(level, storehouse).size();
		ordersIcon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.orders", orders), ChatFormatting.AQUA));
		ordersIcon.set(DataComponents.LORE, new ItemLore(List.of(plain(Component.translatable("screen.aliveworkplace.storehouse.orders_hint"), ChatFormatting.GRAY))));
		menu.button(ORDERS_SLOT, ordersIcon, p -> {
			renderOrders(menu, level, storehouse, p);
			menu.broadcastChanges();
		});
		int slot = FIRST_REQUEST;
		for (Requests.Request request : requests) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, icon(level, request, store, viewer), p -> {
				int given = give(p, request);
				if (given > 0) {
					Chat.actionBar(p, Component.translatable(request.forBlock() ? "message.aliveworkplace.storehouse.gave_for" : "message.aliveworkplace.storehouse.gave",
						given, request.what(), job(request)).withStyle(ChatFormatting.GREEN));
					level.playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
				} else {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.storehouse.none", request.what()).withStyle(ChatFormatting.YELLOW));
				}
				render(menu, level, storehouse, p);
				menu.broadcastChanges();
			});
		}
	}

	/**
	 * 37.8: where a slot holds more than a stack (a Sophisticated Storage stack upgrade, a limited barrel), empty slots
	 * undersell the room, so the board adds the room counted in items. Vanilla chests: no line, the board as before.
	 */
	static java.util.Optional<Component> roomLine(ServerLevel level, List<BlockPos> store) {
		if (!io.github.jcondedata.aliveworkplace.Expansions.on(io.github.jcondedata.aliveworkplace.Expansions.M37)) {
			return java.util.Optional.empty();
		}
		io.github.jcondedata.aliveworkplace.platform.ItemStores.Room room = SupplyContainers.room(level, store);
		return room.bigSlots()
			? java.util.Optional.of(plain(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.room", room.freeItems(), room.freeItems()), ChatFormatting.GRAY))
			: java.util.Optional.empty();
	}

	/** 27.19: the Steward's one shopping list for all his builds waiting for materials, by the info. */
	static void shoppingList(ChoiceMenu menu, ServerLevel level, BlockPos storehouse) {
		java.util.Optional<BlockPos> hall = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, storehouse);
		if (hall.isEmpty()) {
			return;
		}
		List<java.util.Map.Entry<net.minecraft.world.item.Item, Integer>> list = io.github.jcondedata.aliveworkplace.city.StewardSafety.shoppingList(level, hall.get());
		if (list.isEmpty()) {
			return;
		}
		List<Component> lines = new java.util.ArrayList<>();
		for (java.util.Map.Entry<net.minecraft.world.item.Item, Integer> e : list) {
			lines.add(plain(io.github.jcondedata.aliveworkplace.city.StewardSafety.item(e.getKey(), e.getValue()), ChatFormatting.GRAY));
		}
		ItemStack icon = new ItemStack(Items.PAPER);
		icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.shopping"), ChatFormatting.YELLOW));
		icon.set(DataComponents.LORE, new ItemLore(lines));
		menu.button(SHOPPING_SLOT, icon, null);
	}

	/**
	 * The orders page: each order (click: keep more, past the most it's dropped), and the item in the player's hand to
	 * order it.
	 */
	static void renderOrders(ChoiceMenu menu, ServerLevel level, BlockPos storehouse, ServerPlayer viewer) {
		menu.clearButtons();
		ItemStack back = new ItemStack(Items.ARROW);
		back.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.back"), ChatFormatting.WHITE));
		menu.button(BACK_SLOT, back, p -> {
			render(menu, level, storehouse, p);
			menu.broadcastChanges();
		});
		ItemStack info = new ItemStack(Items.WRITABLE_BOOK);
		info.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.orders_title"), ChatFormatting.GOLD));
		info.set(DataComponents.LORE, new ItemLore(List.of(plain(Component.translatable("screen.aliveworkplace.storehouse.orders_how"), ChatFormatting.GRAY),
			plain(Component.translatable("screen.aliveworkplace.storehouse.orders_who"), ChatFormatting.GRAY))));
		menu.button(INFO_SLOT, info, null);
		// B351: with the main hand empty, the off hand's item is the one held (as B341/B342's clicks on villagers).
		ItemStack held = viewer.getMainHandItem().isEmpty() ? viewer.getOffhandItem() : viewer.getMainHandItem();
		if (!held.isEmpty() && !StockOrders.of(level, storehouse).containsKey(held.getItem())) {
			ItemStack add = held.copyWithCount(1);
			add.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.order_add", held.getHoverName(),
				StockOrders.STEPS.get(0)), ChatFormatting.GREEN));
			menu.button(ORDERS_SLOT, add, p -> {
				StockOrders.cycle(level, storehouse, held.getItem());
				level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1f);
				renderOrders(menu, level, storehouse, p);
				menu.broadcastChanges();
			});
		}
		List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
		int slot = FIRST_REQUEST;
		for (var order : StockOrders.of(level, storehouse).entrySet()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			net.minecraft.world.item.Item item = order.getKey();
			long have = SupplyContainers.count(level, store, item);
			ItemStack icon = new ItemStack(item, (int) Math.max(1, Math.min(99, order.getValue())));
			icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.order", order.getValue(), item.getDescription()),
				have >= order.getValue() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			int step = StockOrders.STEPS.indexOf(order.getValue());
			icon.set(DataComponents.LORE, new ItemLore(List.of(
				plain(Component.translatable("screen.aliveworkplace.storehouse.order_have", have), ChatFormatting.GRAY),
				plain(step >= 0 && step + 1 < StockOrders.STEPS.size()
					? Component.translatable("screen.aliveworkplace.storehouse.order_more", StockOrders.STEPS.get(step + 1))
					: Component.translatable("screen.aliveworkplace.storehouse.order_drop"), ChatFormatting.DARK_GRAY))));
			menu.button(slot++, icon, p -> {
				StockOrders.cycle(level, storehouse, item);
				level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
				renderOrders(menu, level, storehouse, p);
				menu.broadcastChanges();
			});
		}
	}

	private static ItemStack icon(ServerLevel level, Requests.Request request, List<BlockPos> store, ServerPlayer viewer) {
		ItemStack icon = request.icon().copyWithCount(Math.max(1, Math.min(99, request.count())));
		MutableComponent name = request.count() > 1 && request.item() != null
			? Component.translatable("screen.aliveworkplace.storehouse.wants", request.count(), request.what())
			: request.what().copy();
		icon.set(DataComponents.CUSTOM_NAME, plain(name, ChatFormatting.YELLOW));
		List<Component> lore = new ArrayList<>();
		int away = (int) Math.round(Math.sqrt(request.station().distSqr(viewer.blockPosition())));
		lore.add(plain(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.for", away, job(request), away), ChatFormatting.GRAY));
		if (request.forBlock()) {
			lore.add(plain(Component.translatable(request.block().found() ? "screen.aliveworkplace.storehouse.found" : "screen.aliveworkplace.storehouse.made_by",
				request.block().job()), ChatFormatting.GRAY));
		}
		if (request.item() != null) {
			lore.add(plain(Component.translatable("screen.aliveworkplace.storehouse.in_store", SupplyContainers.count(level, store, request.item())),
				ChatFormatting.GRAY));
		}
		int have = count(viewer.getInventory(), request);
		lore.add(have > 0 ? plain(Component.translatable("screen.aliveworkplace.storehouse.give", Math.min(have, request.count())), ChatFormatting.GREEN)
			: plain(Component.translatable("screen.aliveworkplace.storehouse.you_have_none"), ChatFormatting.DARK_GRAY));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return icon;
	}

	/**
	 * Moves up to what's asked for from {@code player}'s inventory into the worker's chests (a request posted for a block:
	 * its own taker, 35.7); returns how many.
	 */
	static int give(ServerPlayer player, Requests.Request request) {
		if (request.forBlock()) {
			return request.block().taker().take(player, request);
		}
		ServerLevel level = Players.level(player);
		List<BlockPos> chests = SupplyContainers.find(level, request.station(), buildArea(level, request));
		if (chests.isEmpty()) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int left = request.count();
		int given = 0;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !request.accepts().test(stack)) {
				continue;
			}
			ItemStack part = stack.copyWithCount(Math.min(left, stack.getCount()));
			ItemStack rest = SupplyContainers.insert(level, chests, part);
			int moved = part.getCount() - rest.getCount();
			if (moved <= 0) {
				break; // the chests are full
			}
			stack.shrink(moved);
			left -= moved;
			given += moved;
		}
		if (given > 0) {
			inventory.setChanged();
			Requests.given(player, request, given);
		}
		return given;
	}

	private static int count(Inventory inventory, Requests.Request request) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && request.accepts().test(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** A builder's chests don't count inside the building going up; nobody else's are excluded. */
	@Nullable
	private static BoundingBox buildArea(ServerLevel level, Requests.Request request) {
		BuildSite site = request.worker() == null ? null : Builders.activeSite(level, request.worker());
		if (site == null) {
			return null;
		}
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, site.structure())
			.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(site.placement(), b.size()))
			.orElse(null);
	}

	private static Component job(Requests.Request request) {
		if (request.forBlock()) {
			return request.block().name(); // "the Great Cathedral"
		}
		return Component.translatable("entity.minecraft.villager." + request.worker().getVillagerData().getProfession().name());
	}

	private static Component plain(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private StorehouseBoard() {
	}
}
