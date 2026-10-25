package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * CobbleDollars a shop took while its owner was offline (a balance can only be changed on a player who is
 * online). Paid out, with a note, the next time the owner joins. Stored once per server.
 */
public final class ShopLedger extends SavedData {
	private static final String NAME = "aliveworkplace_shops";
	/** A full inventory of emeralds: a bigger sum without CobbleDollars is paid over several joins, not dropped in a heap. */
	static final int MOST_EMERALDS_A_JOIN = 36 * 64;

	private final Map<UUID, Long> pending = new HashMap<>();

	public static ShopLedger get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShopLedger::new, ShopLedger::load, null), NAME);
	}

	public static void init() {
		Platform.get().onPlayerJoin(ShopLedger::collect);
	}

	/** Pays {@code dollars} to the shop's owner now if they are online, or when they next join. */
	public static void payOwner(MinecraftServer server, UUID owner, long dollars) {
		if (dollars <= 0) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Money.pay(player, dollars, 0);
		} else {
			get(server).owe(owner, dollars);
		}
	}

	/** Adds {@code dollars} to what the owner is paid when they next join. */
	public void owe(UUID owner, long dollars) {
		if (dollars > 0) {
			pending.merge(owner, dollars, Long::sum);
			setDirty();
		}
	}

	public long pending(UUID owner) {
		return pending.getOrDefault(owner, 0L);
	}

	/**
	 * Pays out what the player's shops took while they were away. Without CobbleDollars (taken out of the pack, or
	 * broken by an update since the sales) the takings are paid in emeralds at the server's rate, and what doesn't
	 * make a whole emerald (or more than an inventory of them) stays owed: paid in CobbleDollars once they're back, or
	 * at a later join (B416).
	 */
	public static void collect(ServerPlayer player) {
		ShopLedger ledger = get(player.server);
		Long owed = ledger.pending.get(player.getUUID());
		if (owed == null || owed <= 0) {
			return;
		}
		long rest = 0;
		int emeralds = 0;
		if (!Money.cobbleDollars()) {
			long rate = Math.max(1, Money.DOLLARS_PER_EMERALD);
			emeralds = (int) Math.min(MOST_EMERALDS_A_JOIN, owed / rate);
			if (emeralds == 0) {
				return; // not a whole emerald yet: kept owed
			}
			rest = owed - emeralds * rate;
		}
		long paid = owed - rest;
		if (rest > 0) {
			ledger.pending.put(player.getUUID(), rest);
		} else {
			ledger.pending.remove(player.getUUID());
		}
		ledger.setDirty();
		Money.pay(player, paid, emeralds);
		player.sendSystemMessage(Component.translatable("message.aliveworkplace.shop.while_away", Money.describe(paid, emeralds))
			.withStyle(ChatFormatting.GREEN));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag owed = new CompoundTag();
		pending.forEach((owner, amount) -> owed.putLong(owner.toString(), amount));
		tag.put("pending", owed);
		return tag;
	}

	private static ShopLedger load(CompoundTag tag, HolderLookup.Provider registries) {
		ShopLedger ledger = new ShopLedger();
		CompoundTag owed = Nbt.getCompound(tag, "pending");
		for (String key : Nbt.keys(owed)) {
			try {
				ledger.pending.put(UUID.fromString(key), Nbt.getLong(owed, key));
			} catch (IllegalArgumentException ignored) {
				// not a player id
			}
		}
		return ledger;
	}
}
