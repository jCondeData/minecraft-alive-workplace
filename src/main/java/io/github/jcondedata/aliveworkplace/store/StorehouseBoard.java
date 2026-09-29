package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
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
	static final int INFO_SLOT = 4;
	static final int FIRST_REQUEST = 9;

	public static void open(ServerPlayer player, BlockPos storehouse) {
		ServerLevel level = player.serverLevel();
		Employer owner = Porters.owner(level, storehouse);
		Component title = owner == null ? Component.translatable("screen.aliveworkplace.storehouse.village")
			: Component.translatable("screen.aliveworkplace.storehouse.owned", owner.name());
		ChoiceMenu.open(player, title,
			p -> p.isAlive() && level.getBlockState(storehouse).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE)
				&& p.position().distanceToSqr(Vec3.atCenterOf(storehouse)) <= 64,
			menu -> render(menu, level, storehouse, player));
	}

	/** The same board, not shown to anyone (tests). */
	public static ChoiceMenu boardForTest(ServerPlayer player, BlockPos storehouse) {
		return ChoiceMenu.detached(player, menu -> render(menu, player.serverLevel(), storehouse, player));
	}

	private static void render(ChoiceMenu menu, ServerLevel level, BlockPos storehouse, ServerPlayer viewer) {
		menu.clearButtons();
		List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
		long items = SupplyContainers.contents(level, store).values().stream().mapToLong(Long::longValue).sum();
		List<Requests.Request> requests = Requests.near(level, storehouse, Porters.owner(level, storehouse));
		ItemStack info = new ItemStack(Items.WRITABLE_BOOK);
		info.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.storehouse.info"), ChatFormatting.GOLD));
		info.set(DataComponents.LORE, new ItemLore(List.of(
			plain(Component.translatable("screen.aliveworkplace.storehouse.stock", store.size(), items, SupplyContainers.freeSlots(level, store)), ChatFormatting.GRAY),
			plain(Component.translatable(requests.isEmpty() ? "screen.aliveworkplace.storehouse.nothing" : "screen.aliveworkplace.storehouse.click"),
				ChatFormatting.GRAY))));
		menu.button(INFO_SLOT, info, null);
		int slot = FIRST_REQUEST;
		for (Requests.Request request : requests) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, icon(level, request, store, viewer), p -> {
				int given = give(p, request);
				if (given > 0) {
					p.displayClientMessage(Component.translatable("message.aliveworkplace.storehouse.gave", given, request.what(), job(request))
						.withStyle(ChatFormatting.GREEN), true);
					level.playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
				} else {
					p.displayClientMessage(Component.translatable("message.aliveworkplace.storehouse.none", request.what()).withStyle(ChatFormatting.YELLOW), true);
				}
				render(menu, level, storehouse, p);
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
		lore.add(plain(Component.translatable("screen.aliveworkplace.storehouse.for", job(request), away), ChatFormatting.GRAY));
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

	/** Moves up to what's asked for from {@code player}'s inventory into the worker's chests; returns how many. */
	static int give(ServerPlayer player, Requests.Request request) {
		ServerLevel level = player.serverLevel();
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
		BuildSite site = Builders.activeSite(level, request.worker());
		if (site == null) {
			return null;
		}
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, site.structure())
			.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(site.placement(), b.size()))
			.orElse(null);
	}

	private static Component job(Requests.Request request) {
		return Component.translatable("entity.minecraft.villager." + request.worker().getVillagerData().getProfession().name());
	}

	private static Component plain(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private StorehouseBoard() {
	}
}
