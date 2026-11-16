//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Pokémon a daycare had to give back while their trainer's PC was full (B1295, B1296, B1287): a keeper's or a
 * rancher's death. They wait here, saved with the world, and go into the trainer's PC as soon as it has a free place
 * while the trainer is online (a slot freed, the next login). Not Cobblemon's PC overflow: Cobblemon 1.7.3 saves that
 * store but never loads it back, and shows it nowhere. Worlds from before start with nobody waiting.
 */
public final class WaitingPokemon extends SavedData {
	public static final String NAME = "aliveworkplace_waiting_pokemon";

	private record Trainer(String name, List<CompoundTag> pokemon) {
	}

	private final Map<UUID, Trainer> waiting = new LinkedHashMap<>();
	/** Trainers told this login that Pokémon wait for them (not saved: told again at the next login). */
	private static final Set<UUID> TOLD = new HashSet<>();
	@Nullable
	private static MinecraftServer server;

	static void init() {
		Platform.get().onServerStarting(s -> {
			server = s;
			TOLD.clear();
		});
		Platform.get().onServerTick(WaitingPokemon::tick);
		Platform.get().onPlayerJoin(player -> TOLD.remove(player.getUUID()));
	}

	public static WaitingPokemon get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WaitingPokemon::new, WaitingPokemon::load, null), NAME);
	}

	/** Keeps {@code pokemon} for {@code owner} until their PC has room, and tells them if they're online. */
	static void keep(MinecraftServer server, UUID owner, String ownerName, Pokemon pokemon) {
		get(server).add(owner, ownerName, pokemon.saveToNBT(server.registryAccess(), new CompoundTag()));
		AliveWorkplace.LOG.info("{}'s PC is full: {} waits for a free place", ownerName, pokemon.getSpecies().getName());
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.daycare.waiting_for_pc", pokemon.getDisplayName(false))
				.withStyle(ChatFormatting.GOLD));
			TOLD.add(owner);
		}
	}

	public void add(UUID owner, String ownerName, CompoundTag pokemon) {
		waiting.computeIfAbsent(owner, o -> new Trainer(ownerName, new ArrayList<>())).pokemon().add(pokemon.copy());
		setDirty();
	}

	/** The Pokémon waiting for {@code owner}, read from their saved form (unreadable ones left out). */
	public List<Pokemon> waitingFor(UUID owner, RegistryAccess access) {
		List<Pokemon> out = new ArrayList<>();
		Trainer trainer = waiting.get(owner);
		if (trainer != null) {
			for (CompoundTag tag : trainer.pokemon()) {
				Pokemon pokemon = read(tag, access);
				if (pokemon != null) {
					out.add(pokemon);
				}
			}
		}
		return out;
	}

	/** How many entries wait for {@code owner} with the Pokémon {@code id} (tests: 0 or 1). */
	public static int count(MinecraftServer server, UUID owner, UUID id) {
		return (int) get(server).waitingFor(owner, server.registryAccess()).stream().filter(p -> p.getUuid().equals(id)).count();
	}

	/** {@link #count} on the server that's running (tests that only hold a PC). */
	public static int count(UUID owner, UUID id) {
		return server == null ? 0 : count(server, owner, id);
	}

	public int size() {
		return waiting.values().stream().mapToInt(t -> t.pokemon().size()).sum();
	}

	private static void tick(MinecraftServer server) {
		WaitingPokemon data = get(server);
		if (data.waiting.isEmpty()) {
			return;
		}
		for (UUID owner : List.copyOf(data.waiting.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				data.handOver(player);
			}
		}
	}

	/** Moves what waits for {@code player} into their PC while it has room; tells them what came and what still waits. */
	public void handOver(ServerPlayer player) {
		Trainer trainer = waiting.get(player.getUUID());
		if (trainer == null) {
			return;
		}
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		RegistryAccess access = player.registryAccess();
		Iterator<CompoundTag> it = trainer.pokemon().iterator();
		while (it.hasNext() && pc.getFirstAvailablePosition() != null) {
			Pokemon pokemon = read(it.next(), access);
			if (pokemon == null || !pc.add(pokemon)) {
				continue; // unreadable: kept, never deleted
			}
			it.remove();
			setDirty();
			Chat.chat(player, Component.translatable("message.aliveworkplace.daycare.came_to_pc", pokemon.getDisplayName(false))
				.withStyle(ChatFormatting.GREEN));
		}
		if (trainer.pokemon().isEmpty()) {
			waiting.remove(player.getUUID());
			TOLD.remove(player.getUUID());
			setDirty();
		} else if (TOLD.add(player.getUUID())) {
			Chat.chat(player, Words.counted("message.aliveworkplace.daycare.still_waiting", trainer.pokemon().size(), trainer.pokemon().size())
				.withStyle(ChatFormatting.GOLD));
		}
	}

	@Nullable
	private static Pokemon read(CompoundTag tag, RegistryAccess access) {
		try {
			return Pokemon.Companion.loadFromNBT(access, tag.copy());
		} catch (RuntimeException e) {
			AliveWorkplace.LOG.error("A Pokémon waiting for a free PC place could not be read", e);
			return null;
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider access) {
		ListTag trainers = new ListTag();
		waiting.forEach((owner, trainer) -> {
			CompoundTag t = new CompoundTag();
			t.putUUID("Owner", owner);
			t.putString("Name", trainer.name());
			ListTag list = new ListTag();
			trainer.pokemon().forEach(p -> list.add(p.copy()));
			t.put("Pokemon", list);
			trainers.add(t);
		});
		tag.put("Trainers", trainers);
		return tag;
	}

	public static WaitingPokemon load(CompoundTag tag, HolderLookup.Provider access) {
		WaitingPokemon data = new WaitingPokemon();
		for (Tag t : tag.getList("Trainers", Tag.TAG_COMPOUND)) {
			CompoundTag trainer = (CompoundTag) t;
			if (!trainer.hasUUID("Owner")) {
				continue;
			}
			List<CompoundTag> pokemon = new ArrayList<>();
			for (Tag p : trainer.getList("Pokemon", Tag.TAG_COMPOUND)) {
				pokemon.add((CompoundTag) p);
			}
			if (!pokemon.isEmpty()) {
				data.waiting.put(trainer.getUUID("Owner"), new Trainer(trainer.getString("Name"), pokemon));
			}
		}
		return data;
	}
}
//?}
