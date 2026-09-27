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
		return pos == null ? null : TravelNetwork.get(level.getServer()).at(GlobalPos.of(level.dimension(), pos));
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
