package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The names players gave their own builds (B458): a Scan Tool renamed "Jesse's Café" in an anvil, or an uploaded file
 * "Jesse's Café.nbt". A blueprint's id (and its file) can only hold a-z, 0-9 and _, so the name as typed is kept here,
 * by id, and {@link Blueprints#displayName} shows it wherever the build is named. Saved with the world (overworld data,
 * older worlds start with none and their builds keep the names made from their ids), and sent to every player who joins
 * and to everyone when a new name is given, since the Blueprint Table and the blueprint item name builds on the client.
 */
public final class BlueprintNames {
	/** Names by blueprint id: on the server, its saved names; on a client, the ones its server sent. */
	private static volatile Map<ResourceLocation, String> names = new ConcurrentHashMap<>();
	/** The server whose saved names {@link #names} holds (loaded once per server run). */
	private static MinecraftServer loadedFor;

	/** S2C: names of player builds; {@code all} means every name the server has (on join), otherwise new ones. */
	public record Sync(Map<ResourceLocation, String> names, boolean all) implements CustomPacketPayload {
		/** Sorted by id, so the packet's bytes don't depend on a map's order (a packet reads back as the same bytes). */
		public Sync {
			names = java.util.Collections.unmodifiableSortedMap(new java.util.TreeMap<>(names));
		}

		public static final Type<Sync> TYPE = new Type<>(AliveWorkplace.id("blueprint_names"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.stringUtf8(256)), Sync::names,
			ByteBufCodecs.BOOL, Sync::all,
			Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		Platform.get().clientbound(Sync.TYPE, Sync.CODEC);
		Platform.get().onServerStarting(server -> {
			names = new ConcurrentHashMap<>();
			loadedFor = null;
		});
		Platform.get().onServerTick(BlueprintNames::ensureLoaded);
		Platform.get().onPlayerJoin(player -> {
			ensureLoaded(player.server);
			send(player, new Sync(Map.copyOf(names), true));
		});
	}

	/** The name a player gave this build, if they gave one. */
	public static Optional<String> get(ResourceLocation id) {
		return Optional.ofNullable(names.get(id));
	}

	/**
	 * Remembers {@code name} as what {@code id} is called, saves it with the world and tells every player. A blank name, or
	 * one that reads the same as the name made from the id, forgets any name it had: it's called by its id.
	 */
	public static void put(MinecraftServer server, ResourceLocation id, String name) {
		ensureLoaded(server);
		String clean = name == null ? "" : name.strip();
		if (clean.equals(Blueprints.prettify(id))) {
			clean = "";
		}
		if (clean.length() > 128) {
			// Cut on a code point: an emoji is two chars, and half of one can't be sent to players.
			int cut = Character.isHighSurrogate(clean.charAt(127)) ? 127 : 128;
			clean = clean.substring(0, cut);
		}
		if (clean.isEmpty() && !names.containsKey(id)) {
			return;
		}
		if (clean.isEmpty()) {
			names.remove(id);
		} else {
			names.put(id, clean);
		}
		Data.get(server).put(id, clean);
		Sync sync = new Sync(Map.of(id, clean), false);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, sync);
		}
	}

	/** A client got names from its server. */
	public static void received(Sync sync) {
		if (sync.all()) {
			names = new ConcurrentHashMap<>(sync.names());
		} else {
			// an empty name: the build is called by its id again
			sync.names().forEach((id, name) -> {
				if (name.isEmpty()) {
					names.remove(id);
				} else {
					names.put(id, name);
				}
			});
		}
	}

	/**
	 * Keeps the name a player gave a build saved under {@code id}, whose id was made from {@code fileName} under
	 * {@code folder} ({@link io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter#uniqueId}). An anvil
	 * name ({@code asTyped}) is kept exactly as typed. A file name (without its extension) is kept only if its id lost
	 * something of it, such as an apostrophe or an accent, with its underscores read as spaces: "my_house.nbt" stays
	 * "My House" as before. A taken id's "_2", "_3"... becomes " 2", " 3".
	 */
	public static void keep(MinecraftServer server, ResourceLocation id, String folder, String fileName, boolean asTyped) {
		String typed = asTyped && fileName.endsWith(".nbt") ? fileName.substring(0, fileName.length() - 4) : fileName;
		if (!asTyped) {
			int slash = Math.max(typed.lastIndexOf('/'), typed.lastIndexOf('\\'));
			if (slash >= 0) {
				typed = typed.substring(slash + 1);
			}
			int dot = typed.lastIndexOf('.');
			if (dot > 0) {
				typed = typed.substring(0, dot);
			}
		}
		if (typed.isBlank() || !asTyped && typed.matches("[A-Za-z0-9_ \\-]*")) {
			put(server, id, ""); // a build saved again under an id that had a name is called by its id now
			return;
		}
		String base = folder + "/" + io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter.sanitize(fileName);
		String path = id.getPath();
		String suffix = path.startsWith(base) && path.length() > base.length() ? path.substring(base.length()).replace('_', ' ') : "";
		put(server, id, (asTyped ? typed.strip() : typed.replace('_', ' ').strip()) + suffix);
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (loadedFor == server) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		loadedFor = server;
		names.putAll(Data.get(server).names);
	}

	private static void send(ServerPlayer player, Sync sync) {
		if (Platform.get().canSend(player, Sync.TYPE)) {
			Platform.get().send(player, sync);
		}
	}

	/** The saved names (overworld data). */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_blueprint_names";
		private final Map<ResourceLocation, String> names = new HashMap<>();

		public static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void put(ResourceLocation id, String name) {
			if (name.isEmpty() ? names.remove(id) != null : !name.equals(names.put(id, name))) {
				setDirty();
			}
		}

		public Optional<String> name(ResourceLocation id) {
			return Optional.ofNullable(names.get(id));
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag all = new CompoundTag();
			names.forEach((id, name) -> all.putString(id.toString(), name));
			tag.put("names", all);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			CompoundTag all = Nbt.getCompound(tag, "names");
			for (String key : Nbt.keys(all)) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id != null) {
					data.names.put(id, Nbt.getString(all, key));
				}
			}
			return data;
		}
	}

	private BlueprintNames() {
	}
}
