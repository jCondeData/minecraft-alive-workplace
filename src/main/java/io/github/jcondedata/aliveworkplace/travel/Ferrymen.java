package io.github.jcondedata.aliveworkplace.travel;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

/** Ferrymen sell tickets to the travel posts a player knows; tickets take you there from any post. */
public final class Ferrymen {
	/** How close to a travel post you must be to use a ticket. */
	public static final int USE_RANGE = 16;

	public static boolean isFerryman(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.FERRYMAN;
	}

	/** The post a ferryman works at. */
	@Nullable
	public static TravelNetwork.Post postOf(ServerLevel level, Villager villager) {
		BlockPos pos = Builders.benchPos(villager).orElse(null);
		return pos == null ? null : TravelNetwork.get(level.getServer()).atOrAdd(level, pos);
	}

	/** Offers: a ticket to every other post {@code player} has found, priced by distance. */
	public static void refreshOffers(ServerLevel level, Villager villager, ServerPlayer player) {
		MerchantOffers offers = villager.getOffers();
		offers.clear();
		TravelNetwork network = TravelNetwork.get(level.getServer());
		TravelNetwork.Post here = postOf(level, villager);
		if (here == null) {
			return;
		}
		network.visit(player.getUUID(), here);
		for (TravelNetwork.Post post : network.known(player.getUUID(), here)) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, TravelNetwork.fare(here.pos(), post.pos())), ticket(post), 99, 1, 0f));
		}
		if (offers.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.nowhere").withStyle(ChatFormatting.YELLOW), true);
		}
	}

	/** First slot of the destinations in the ferry screen. */
	public static final int FIRST_DESTINATION_SLOT = 18;

	/**
	 * With CobbleDollars: the ferry screen, a ticket to every post the player knows, paid in CobbleDollars (the
	 * emerald fare × {@link io.github.jcondedata.aliveworkplace.work.Money#DOLLARS_PER_EMERALD}), two clicks to buy.
	 */
	public static void openMenu(ServerPlayer player, Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || postOf(level, villager) == null) {
			return;
		}
		TravelNetwork.get(level.getServer()).visit(player.getUUID(), postOf(level, villager));
		if (TravelNetwork.get(level.getServer()).known(player.getUUID(), postOf(level, villager)).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.nowhere").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		java.util.UUID[] pending = {null};
		io.github.jcondedata.aliveworkplace.work.ChoiceMenu.open(player, villager.getDisplayName(),
			p -> villager.isAlive() && !villager.isSleeping() && p.isAlive() && p.distanceTo(villager) <= 8,
			menu -> render(menu, player, villager, pending));
	}

	/** The ferry screen without showing it (tests). */
	public static io.github.jcondedata.aliveworkplace.work.ChoiceMenu menuForTest(ServerPlayer player, Villager villager) {
		java.util.UUID[] pending = {null};
		return io.github.jcondedata.aliveworkplace.work.ChoiceMenu.detached(player, menu -> render(menu, player, villager, pending));
	}

	private static void render(io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu, ServerPlayer player, Villager villager, java.util.UUID[] pending) {
		menu.clearButtons();
		ServerLevel level = (ServerLevel) villager.level();
		TravelNetwork.Post here = postOf(level, villager);
		if (here == null) {
			return;
		}
		java.util.List<TravelNetwork.Post> known = TravelNetwork.get(level.getServer()).known(player.getUUID(), here);
		for (int i = 0; i < known.size() && FIRST_DESTINATION_SLOT + i < io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE; i++) {
			TravelNetwork.Post post = known.get(i);
			int emeralds = TravelNetwork.fare(here.pos(), post.pos());
			long dollars = (long) emeralds * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD;
			boolean chosen = post.id().equals(pending[0]);
			ItemStack icon = ticket(post);
			icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(post.name()).withStyle(st -> st.withItalic(false)));
			java.util.List<Component> lines = new java.util.ArrayList<>();
			lines.add(Component.translatable("message.aliveworkplace.travel.fare", io.github.jcondedata.aliveworkplace.work.Money.describe(dollars, emeralds))
				.withStyle(io.github.jcondedata.aliveworkplace.work.Money.canAfford(player, dollars, emeralds) ? ChatFormatting.GREEN : ChatFormatting.RED));
			lines.add(chosen
				? Component.translatable("message.aliveworkplace.travel.confirm", post.name()).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("message.aliveworkplace.shop.menu.click").withStyle(ChatFormatting.GRAY));
			icon.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				lines.stream().map(l -> (Component) l.copy().withStyle(st -> st.withItalic(false))).toList()));
			if (chosen) {
				icon.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(FIRST_DESTINATION_SLOT + i, icon, p -> {
				if (!post.id().equals(pending[0])) {
					pending[0] = post.id();
				} else {
					pending[0] = null;
					buyTicket(p, post, dollars, emeralds);
				}
				render(menu, player, villager, pending);
			});
		}
		menu.divider(1);
		ItemStack info = new ItemStack(net.minecraft.world.item.Items.OAK_BOAT);
		info.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(here.name()).withStyle(st -> st.withItalic(false)));
		info.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.stream.Stream.of(
				Component.translatable("message.aliveworkplace.tutor.info_money", io.github.jcondedata.aliveworkplace.work.Money.balance(player))
					.withStyle(ChatFormatting.GREEN),
				Component.translatable("message.aliveworkplace.travel.menu_help").withStyle(ChatFormatting.GRAY))
			.map(l -> (Component) l.copy().withStyle(st -> st.withItalic(false))).toList()));
		menu.button(4, info, null);
	}

	private static void buyTicket(ServerPlayer player, TravelNetwork.Post post, long dollars, int emeralds) {
		if (!io.github.jcondedata.aliveworkplace.work.Money.charge(player, dollars, emeralds)) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.shop.too_poor",
				io.github.jcondedata.aliveworkplace.work.Money.describe(dollars, emeralds)).withStyle(ChatFormatting.RED), true);
			return;
		}
		ItemStack ticket = ticket(post);
		if (!player.getInventory().add(ticket)) {
			player.drop(ticket, false);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6f, 1f);
		player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.bought", post.name()).withStyle(ChatFormatting.GREEN), true);
	}

	public static ItemStack ticket(TravelNetwork.Post post) {
		ItemStack ticket = new ItemStack(ModItems.TRAVEL_TICKET);
		ticket.set(ModComponents.TICKET, new TicketData(post.id(), post.pos(), post.name()));
		return ticket;
	}

	/** Uses a ticket: from near any travel post, to the ticket's post. True if the player went. */
	public static boolean travel(ServerPlayer player, ItemStack ticket) {
		TicketData data = ticket.get(ModComponents.TICKET);
		if (data == null) {
			return false;
		}
		TravelNetwork network = TravelNetwork.get(player.server);
		TravelNetwork.Post destination = network.post(data.post());
		if (destination == null) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.gone", data.name()).withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (network.near(GlobalPos.of(player.level().dimension(), player.blockPosition()), USE_RANGE) == null) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.not_at_post", USE_RANGE).withStyle(ChatFormatting.YELLOW), true);
			return false;
		}
		ServerLevel level = player.server.getLevel(destination.pos().dimension());
		if (level == null) {
			return false;
		}
		BlockPos spot = landing(level, destination.pos().pos());
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1f, 1f);
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
		level.playSound(null, spot, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1f, 0.9f);
		network.visit(player.getUUID(), destination);
		player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.arrived", destination.name()).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/** Somewhere to stand next to the post. */
	static BlockPos landing(ServerLevel level, BlockPos post) {
		level.getChunk(post); // make sure it's loaded
		for (int r = 1; r <= 3; r++) {
			for (BlockPos p : BlockPos.betweenClosed(post.offset(-r, -2, -r), post.offset(r, 3, r))) {
				if (Walker.canStand(level, p)) {
					return p.immutable();
				}
			}
		}
		return post.above();
	}

	private Ferrymen() {
	}
}
