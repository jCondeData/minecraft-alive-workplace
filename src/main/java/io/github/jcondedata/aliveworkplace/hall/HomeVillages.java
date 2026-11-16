package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Each player's own village (B1318), for things that belong to the player rather than to where they stand, such as a
 * questbook reward that unlocks a build ({@code /workplace unlock}). It is, in order: the village the player stands in
 * when they own it, the hall they last came to own, the village they were last in for a rank's moment
 * ({@link VillageMoments}), a village the realm list ({@link RealmData.Known}) says they own (saves from before this
 * record), and last the village where they stand. Saved with the overworld; an older save starts with none.
 */
public final class HomeVillages extends SavedData {
	static final String NAME = "aliveworkplace_home_villages";
	private static final Codec<Map<UUID, GlobalPos>> MAP = Codec.unboundedMap(UUIDUtil.STRING_CODEC, GlobalPos.CODEC);

	final Map<UUID, GlobalPos> owned = new HashMap<>();
	final Map<UUID, GlobalPos> ranked = new HashMap<>();

	public static HomeVillages get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HomeVillages::new, HomeVillages::load, null), NAME);
	}

	/** {@code player} now owns the hall at {@code hall}. */
	public static void owns(ServerLevel level, UUID player, net.minecraft.core.BlockPos hall) {
		put(get(level.getServer()).owned, level, player, hall);
	}

	/** {@code player} was in the village round {@code hall} as it reached a rank. */
	public static void ranked(ServerLevel level, UUID player, net.minecraft.core.BlockPos hall) {
		put(get(level.getServer()).ranked, level, player, hall);
	}

	private static void put(Map<UUID, GlobalPos> map, ServerLevel level, UUID player, net.minecraft.core.BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall.immutable());
		if (!at.equals(map.put(player, at))) {
			get(level.getServer()).setDirty();
		}
	}

	/** The hall {@code player} last came to own, as saved (whether or not it still stands). */
	public Optional<GlobalPos> ownedBy(UUID player) {
		return Optional.ofNullable(owned.get(player));
	}

	/** The village {@code player} was last in for a rank, as saved. */
	public Optional<GlobalPos> rankedWith(UUID player) {
		return Optional.ofNullable(ranked.get(player));
	}

	/** {@code player}'s own village (see the class), or empty when no village is theirs and none is where they stand. */
	public static Optional<GlobalPos> of(ServerPlayer player) {
		ServerLevel here = io.github.jcondedata.aliveworkplace.mc.Players.level(player);
		MinecraftServer server = here.getServer();
		UUID id = player.getUUID();
		Optional<net.minecraft.core.BlockPos> near = VillageHalls.nearest(here, player.blockPosition());
		if (near.isPresent() && here.getBlockEntity(near.get()) instanceof VillageHallBlockEntity entity && id.equals(entity.owner())) {
			return Optional.of(GlobalPos.of(here.dimension(), near.get()));
		}
		HomeVillages data = get(server);
		for (Map<UUID, GlobalPos> map : java.util.List.of(data.owned, data.ranked)) {
			GlobalPos at = map.get(id);
			if (at != null && stands(server, at, map == data.owned ? id : null)) {
				return Optional.of(at);
			}
		}
		Optional<GlobalPos> known = RealmData.get(server).knownVillages().stream()
			.filter(k -> k.owner().map(id::equals).orElse(false) && stands(server, k.hall(), id))
			.max(Comparator.comparingLong(RealmData.Known::seen))
			.map(RealmData.Known::hall);
		if (known.isPresent()) {
			return known;
		}
		return near.map(hall -> GlobalPos.of(here.dimension(), hall));
	}

	/**
	 * Whether the hall at {@code at} still stands (and, with {@code owner}, is still theirs). Asking loads the hall's
	 * chunk: fine for a one-off such as a reward.
	 */
	private static boolean stands(MinecraftServer server, GlobalPos at, @org.jetbrains.annotations.Nullable UUID owner) {
		ServerLevel level = server.getLevel(at.dimension());
		return level != null && level.getBlockEntity(at.pos()) instanceof VillageHallBlockEntity entity
			&& (owner == null || owner.equals(entity.owner()));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		MAP.encodeStart(ops, owned).result().ifPresent(t -> tag.put("owned", t));
		MAP.encodeStart(ops, ranked).result().ifPresent(t -> tag.put("ranked", t));
		return tag;
	}

	public static HomeVillages load(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		HomeVillages data = new HomeVillages();
		read(ops, tag.getCompound("owned"), data.owned);
		read(ops, tag.getCompound("ranked"), data.ranked);
		return data;
	}

	/** Reads one map entry by entry, so a damaged key or place loses only that entry, never the good ones. */
	private static void read(com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops, CompoundTag map, Map<UUID, GlobalPos> into) {
		for (String key : map.getAllKeys()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			GlobalPos.CODEC.parse(ops, map.get(key)).result().ifPresent(at -> into.put(id, at));
		}
	}
}
