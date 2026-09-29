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
			ShopLedger ledger = get(server);
			ledger.pending.merge(owner, dollars, Long::sum);
			ledger.setDirty();
		}
	}

	public long pending(UUID owner) {
		return pending.getOrDefault(owner, 0L);
	}

	/** Pays out what the player's shops took while they were away. */
	public static void collect(ServerPlayer player) {
		ShopLedger ledger = get(player.server);
		Long owed = ledger.pending.remove(player.getUUID());
		if (owed == null || owed <= 0) {
			return;
		}
		ledger.setDirty();
		Money.pay(player, owed, 0);
		player.sendSystemMessage(Component.translatable("message.aliveworkplace.shop.while_away", Money.describe(owed, 0))
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
