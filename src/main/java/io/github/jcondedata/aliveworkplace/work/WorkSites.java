package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where the workers of one dimension work (roadmap 23.6): each worker's workstation chunk and the chunk it was last
 * seen in, saved with the world, so {@link KeepLoaded} can keep villages working when nobody is near them, also right
 * after a restart, before anyone has been back. A worker not seen for {@link #FORGET} ticks (gone, fired, or its chunk
 * no longer kept) is forgotten.
 */
public final class WorkSites extends SavedData {
	private static final String NAME = "aliveworkplace_work_sites";
	/** How often the loaded workers are looked at. */
	public static final int EVERY = 100;
	/** A worker not seen for this long (2 minutes) is forgotten. */
	public static final long FORGET = 2400;

	/** One worker: its workstation's chunk, the chunk it was last in, and when it was last seen (game time). */
	public record Site(long station, long at, long seen) {
	}

	private final Map<UUID, Site> sites = new HashMap<>();

	public static WorkSites get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorkSites::new, WorkSites::load, null), NAME);
	}

	/** Every {@link #EVERY} ticks: notes where each loaded worker works and is, and forgets those long gone. */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % EVERY != 0) {
			return;
		}
		WorkSites sites = get(level);
		long now = level.getGameTime();
		for (Villager villager : level.getEntities(EntityType.VILLAGER, WorkSites::isWorker)) {
			BlockPos station = Builders.benchPos(villager).orElse(null);
			if (station != null) {
				sites.see(villager.getUUID(), new ChunkPos(station).toLong(), villager.chunkPosition().toLong(), now);
			}
		}
		sites.forgetBefore(now - FORGET);
	}

	/** A grown villager in one of our jobs, or a vanilla job that works for the village. */
	public static boolean isWorker(Villager villager) {
		if (villager.isBaby() || !villager.isAlive()) {
			return false;
		}
		var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
		return key != null && key.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID) || Village.takesPart(villager);
	}

	public void see(UUID worker, long station, long at, long now) {
		Site old = sites.put(worker, new Site(station, at, now));
		if (old == null || old.station() != station || old.at() != at) {
			setDirty();
		}
	}

	public void forget(UUID worker) {
		if (sites.remove(worker) != null) {
			setDirty();
		}
	}

	public void forgetBefore(long time) {
		if (sites.values().removeIf(s -> s.seen() < time)) {
			setDirty();
		}
	}

	public Map<UUID, Site> all() {
		return Collections.unmodifiableMap(sites);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		sites.forEach((worker, site) -> {
			CompoundTag entry = new CompoundTag();
			Nbt.putUuid(entry, "worker", worker);
			entry.putLong("station", site.station());
			entry.putLong("at", site.at());
			entry.putLong("seen", site.seen());
			list.add(entry);
		});
		tag.put("sites", list);
		return tag;
	}

	static WorkSites load(CompoundTag tag, HolderLookup.Provider registries) {
		WorkSites out = new WorkSites();
		for (Tag t : Nbt.getList(tag, "sites", Tag.TAG_COMPOUND)) {
			CompoundTag entry = (CompoundTag) t;
			if (Nbt.hasUuid(entry, "worker")) {
				out.sites.put(Nbt.getUuid(entry, "worker"), new Site(Nbt.getLong(entry, "station"), Nbt.getLong(entry, "at"), Nbt.getLong(entry, "seen")));
			}
		}
		return out;
	}

	/** A copy through save and load, for tests. */
	public WorkSites reloaded(HolderLookup.Provider registries) {
		return load(save(new CompoundTag(), registries), registries);
	}
}
