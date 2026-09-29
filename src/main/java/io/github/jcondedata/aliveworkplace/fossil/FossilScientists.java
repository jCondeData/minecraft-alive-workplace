package io.github.jcondedata.aliveworkplace.fossil;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Fossil Scientists (with Cobblemon): hand one a fossil (a Galar fossil's two halves, one in each hand) and pay, and they
 * bring it back to life at their Fossil Lab while they work — the Pokémon goes to your party (or PC) when it's done, or
 * the next time you're online. It's Cobblemon's own revival (the same Pokémon its machine gives), without the machine.
 */
public final class FossilScientists {
	public static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
	/** Ticks of work a revival takes for a novice (shorter at each level and with Pokémon partners). Tests shorten it. */
	public static int REVIVE_TICKS = 3600;
	/** Price of a revival, in emeralds (or CobbleDollars at the usual rate). */
	public static final int PRICE = 8;
	/** Fossils a scientist takes on at once. */
	public static final int MAX_QUEUE = 3;
	public static final int XP_PER_REVIVAL = 8;

	public static boolean isScientist(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.FOSSIL_SCIENTIST;
	}

	/** Whether {@code stack} is a fossil (on either side; the client knows Cobblemon's fossils too). */
	public static boolean isFossil(ItemStack stack) {
		return COBBLEMON && FossilLab.EXTENSION.call(lab -> lab.isFossil(stack), false);
	}

	public static List<Revival> queue(Villager villager) {
		return villager.getAttachedOrElse(ModAttachments.FOSSIL_REVIVALS, List.of());
	}

	static void setQueue(Villager villager, List<Revival> queue) {
		if (queue.isEmpty()) {
			villager.removeAttached(ModAttachments.FOSSIL_REVIVALS);
		} else {
			villager.setAttached(ModAttachments.FOSSIL_REVIVALS, List.copyOf(queue));
		}
	}

	/** The player right-clicked the scientist holding a fossil: take it on (and the payment). */
	public static void handOver(ServerPlayer player, Villager scientist) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		List<ItemStack> items = new ArrayList<>(List.of(main.copyWithCount(1)));
		ResourceLocation fossil = FossilLab.EXTENSION.call(lab -> lab.fossil(items), null);
		boolean both = false;
		if (fossil == null && isFossil(off)) {
			items.add(off.copyWithCount(1));
			fossil = FossilLab.EXTENSION.call(lab -> lab.fossil(items), null);
			both = fossil != null;
		}
		if (fossil == null) {
			tell(player, Component.translatable(FossilLab.EXTENSION.call(lab -> lab.partOfOne(items.subList(0, 1)), false) ? "message.aliveworkplace.fossil.other_half"
				: "message.aliveworkplace.fossil.unknown"), ChatFormatting.YELLOW);
			return;
		}
		List<Revival> queue = new ArrayList<>(queue(scientist));
		if (queue.size() >= MAX_QUEUE) {
			tell(player, Component.translatable("message.aliveworkplace.fossil.busy", scientist.getDisplayName(), MAX_QUEUE), ChatFormatting.YELLOW);
			return;
		}
		long dollars = (long) PRICE * Money.DOLLARS_PER_EMERALD;
		if (!Money.charge(player, dollars, PRICE)) {
			tell(player, Component.translatable("message.aliveworkplace.fossil.cant_afford", Money.describe(dollars, PRICE)), ChatFormatting.RED);
			return;
		}
		List<ResourceLocation> names = items.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem())).toList();
		if (!player.getAbilities().instabuild) {
			main.shrink(1);
			if (both) {
				off.shrink(1);
			}
		}
		int ticks = Math.max(20, BuilderLevels.delay(REVIVE_TICKS, scientist));
		queue.add(new Revival(player.getUUID(), player.getGameProfile().getName(), fossil, names, ticks, ticks));
		setQueue(scientist, queue);
		scientist.level().playSound(null, scientist, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		int ahead = queue.size() - 1;
		tell(player, Component.translatable(ahead == 0 ? "message.aliveworkplace.fossil.taken" : "message.aliveworkplace.fossil.queued",
			scientist.getDisplayName(), what(names), Math.max(1, Math.round(ticks * (ahead + 1) / 1200f)), ahead), ChatFormatting.GREEN);
	}

	/** Right-click with an empty hand: collect what's ready for this player, or hear how it's going. */
	public static void check(ServerPlayer player, Villager scientist) {
		if (!COBBLEMON) {
			tell(player, Component.translatable("message.aliveworkplace.fossil.no_cobblemon"), ChatFormatting.GRAY);
			return;
		}
		if (deliver((ServerLevel) scientist.level(), scientist, player) > 0) {
			return;
		}
		List<Revival> mine = queue(scientist).stream().filter(r -> r.owner().equals(player.getUUID())).toList();
		if (mine.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.fossil.hint", scientist.getDisplayName(), Money.describe((long) PRICE * Money.DOLLARS_PER_EMERALD, PRICE)),
				ChatFormatting.GRAY);
			return;
		}
		for (Revival r : mine) {
			tell(player, Component.translatable("message.aliveworkplace.fossil.progress", what(r.items()), Math.round(r.progress() * 100)), ChatFormatting.GRAY);
		}
	}

	/** Hands over the finished revivals that belong to {@code player}; returns how many. */
	static int deliver(ServerLevel level, Villager scientist, ServerPlayer player) {
		List<Revival> queue = new ArrayList<>(queue(scientist));
		int delivered = 0;
		for (Revival r : List.copyOf(queue)) {
			if (!r.ready() || !r.owner().equals(player.getUUID())) {
				continue;
			}
			Component pokemon = FossilLab.EXTENSION.call(lab -> lab.revive(player, r.fossil()), null);
			queue.remove(r);
			if (pokemon == null) {
				continue; // that fossil isn't in Cobblemon's data any more
			}
			delivered++;
			tell(player, Component.translatable("message.aliveworkplace.fossil.revived", scientist.getDisplayName(), pokemon), ChatFormatting.GREEN);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
			scientist.setAttached(ModAttachments.FOSSILS_REVIVED, scientist.getAttachedOrElse(ModAttachments.FOSSILS_REVIVED, 0) + 1);
			BuilderLevels.addXp(level, scientist, XP_PER_REVIVAL, player.getUUID());
		}
		if (delivered > 0) {
			setQueue(scientist, queue);
		}
		return delivered;
	}

	/** "Dome Fossil", or "Fossilized Bird and Fossilized Drake". */
	static Component what(List<ResourceLocation> items) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				out.append(Component.translatable("message.aliveworkplace.partners.and"));
			}
			out.append(BuiltInRegistries.ITEM.get(items.get(i)).getDescription());
		}
		return out;
	}

	static void tell(Player player, Component text, ChatFormatting color) {
		player.sendSystemMessage(text.copy().withStyle(color));
	}

	private FossilScientists() {
	}
}
