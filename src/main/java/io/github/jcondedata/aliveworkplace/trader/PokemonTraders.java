package io.github.jcondedata.aliveworkplace.trader;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * Pokémon Traders (with Cobblemon installed): villagers at a Trade Board who swap one of their Pokémon for
 * one of yours. Their offers change every in-game day ("my Growlithe for any Water type, level 20 or
 * more"); each player makes one trade per trader per day. More experienced traders have more offers and
 * stronger Pokémon.
 */
public final class PokemonTraders {
	public static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
	/** How far a player can walk from the trader before the trade screen closes. */
	public static final double REACH = 8;
	/** Trader XP for every trade. */
	public static final int XP_PER_TRADE = 6;

	public static boolean isTrader(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.POKEMON_TRADER;
	}

	/** 1 (Novice) to 5 (Master). */
	public static int tier(Villager villager) {
		return BuilderLevels.level(villager);
	}

	public static long day(Villager trader) {
		return trader.level().getDayTime() / 24000L;
	}

	/** "Journeyman Pokémon Trader", or "Journeyman Pokémon Trader Ada" for a named one. */
	public static Component title(Villager trader) {
		Component rank = BuilderLevels.levelName(tier(trader));
		return trader.hasCustomName()
			? Component.translatable("message.aliveworkplace.pokemon_trader.title_named", rank, trader.getCustomName())
			: Component.translatable("message.aliveworkplace.pokemon_trader.title", rank);
	}

	/** Whether {@code player} has already traded with this trader today. */
	public static boolean tradedToday(Villager trader, UUID player) {
		Long last = trader.getAttachedOrElse(ModAttachments.POKEMON_TRADES, Map.<UUID, Long>of()).get(player);
		return last != null && last == day(trader);
	}

	/** Right-click on a trader: the trade screen. */
	public static void open(ServerPlayer player, Villager trader) {
		if (!COBBLEMON) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.pokemon_trader.no_cobblemon").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (trader.isSleeping()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.pokemon_trader.asleep", trader.getDisplayName())
				.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.open(player, trader);
	}

	/** A trade went through: remember it for today, and the trader gains experience. */
	public static void traded(ServerPlayer player, Villager trader) {
		Map<UUID, Long> trades = new HashMap<>(trader.getAttachedOrElse(ModAttachments.POKEMON_TRADES, Map.of()));
		long today = day(trader);
		trades.values().removeIf(d -> d < today - 1); // forget old days
		trades.put(player.getUUID(), today);
		trader.setAttached(ModAttachments.POKEMON_TRADES, Map.copyOf(trades));
		trader.setAttached(ModAttachments.POKEMON_TRADE_COUNT, trader.getAttachedOrElse(ModAttachments.POKEMON_TRADE_COUNT, 0) + 1);
		int tierBefore = tier(trader);
		BuilderLevels.addXp((ServerLevel) trader.level(), trader, XP_PER_TRADE, null);
		if (tier(trader) > tierBefore) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.pokemon_trader.ranked_up", trader.getDisplayName(),
				BuilderLevels.levelName(tier(trader))).withStyle(ChatFormatting.GOLD));
		}
	}

	private PokemonTraders() {
	}
}
