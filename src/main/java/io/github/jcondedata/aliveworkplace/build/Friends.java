package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Who may give orders to whose builders. A builder works for the player who hired it and for the
 * friends that player added with {@code /workplace friend add}; operators can always step in.
 * Stored once per server (in the overworld's data folder).
 */
public final class Friends extends SavedData {
	private static final String NAME = "aliveworkplace_friends";

	/** player → (friend → friend's name) */
	private final Map<UUID, Map<UUID, String>> friends = new HashMap<>();

	public static Friends get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Friends::new, Friends::load, null), NAME);
	}

	public boolean add(UUID player, UUID friend, String friendName) {
		boolean added = friends.computeIfAbsent(player, p -> new LinkedHashMap<>()).put(friend, friendName) == null;
		setDirty();
		return added;
	}

	public boolean remove(UUID player, UUID friend) {
		Map<UUID, String> list = friends.get(player);
		boolean removed = list != null && list.remove(friend) != null;
		setDirty();
		return removed;
	}

	public Map<UUID, String> of(UUID player) {
		return friends.getOrDefault(player, Map.of());
	}

	/** True if {@code player} may direct work that belongs to {@code boss}. */
	public boolean mayDirect(UUID boss, UUID player) {
		return boss.equals(player) || of(boss).containsKey(player);
	}

	/** True if {@code player} may give this builder orders (hand over blueprints). */
	public static boolean mayCommand(Player player, Villager builder) {
		if (!Rules.on(player.level(), ModGameRules.BUILDER_OWNERSHIP) || player.hasPermissions(2)) {
			return true;
		}
		Employer employer = ModAttachments.BUILDER_EMPLOYER.get(builder);
		return employer == null || player.level().getServer() == null || get(player.level().getServer()).mayDirect(employer.id(), player.getUUID());
	}

	/** The builder's employer, hiring {@code player} as one if it has none yet. */
	@Nullable
	public static Employer hire(ServerPlayer player, Villager builder) {
		Employer employer = ModAttachments.BUILDER_EMPLOYER.get(builder);
		if (employer == null) {
			employer = new Employer(player.getUUID(), player.getGameProfile().getName());
			ModAttachments.BUILDER_EMPLOYER.set(builder, employer);
		}
		return employer;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Map<UUID, String>> e : friends.entrySet()) {
			for (Map.Entry<UUID, String> f : e.getValue().entrySet()) {
				CompoundTag entry = new CompoundTag();
				Nbt.putUuid(entry, "player", e.getKey());
				Nbt.putUuid(entry, "friend", f.getKey());
				entry.putString("name", f.getValue());
				list.add(entry);
			}
		}
		tag.put("friends", list);
		return tag;
	}

	private static Friends load(CompoundTag tag, HolderLookup.Provider registries) {
		Friends out = new Friends();
		for (Tag t : Nbt.getList(tag, "friends", Tag.TAG_COMPOUND)) {
			CompoundTag entry = (CompoundTag) t;
			if (Nbt.hasUuid(entry, "player") && Nbt.hasUuid(entry, "friend")) {
				out.friends.computeIfAbsent(Nbt.getUuid(entry, "player"), p -> new LinkedHashMap<>()).put(Nbt.getUuid(entry, "friend"), Nbt.getString(entry, "name"));
			}
		}
		return out;
	}
}
