package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Wonders of one dimension (M35), saved with it as {@code aliveworkplace_wonders}: every {@link WonderSite}, going
 * up or finished, in the order they were begun. A world saved before M35 has no such file and reads as empty.
 */
public final class WonderManager extends SavedData {
	public static final String NAME = "aliveworkplace_wonders";

	private final Map<UUID, WonderSite> sites = new LinkedHashMap<>();
	/**
	 * The helpers' searches for the found lines of each site's waiting stage (35.7b), saved beside the sites as
	 * {@code searches} (a world saved before has none: nothing searched yet).
	 */
	private final Map<UUID, List<Search>> searches = new LinkedHashMap<>();

	/**
	 * A helper's search for goods line {@code line} (the item it was for, so a changed file never counts it for another
	 * line): how many ticks of play it has gone on, and whether the helper was in the village when last seen (kept for
	 * while the village is unloaded and its players are away).
	 */
	public record Search(int line, ResourceLocation item, long ticks, boolean seen) {
		public static final com.mojang.serialization.Codec<Search> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			com.mojang.serialization.Codec.INT.fieldOf("line").forGetter(Search::line),
			ResourceLocation.CODEC.fieldOf("item").forGetter(Search::item),
			com.mojang.serialization.Codec.LONG.optionalFieldOf("ticks", 0L).forGetter(Search::ticks),
			com.mojang.serialization.Codec.BOOL.optionalFieldOf("seen", false).forGetter(Search::seen)
		).apply(i, Search::new));
	}

	private record Searches(UUID site, List<Search> lines) {
		static final com.mojang.serialization.Codec<Searches> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			net.minecraft.core.UUIDUtil.CODEC.fieldOf("site").forGetter(Searches::site),
			Search.CODEC.listOf().fieldOf("lines").forGetter(Searches::lines)
		).apply(i, Searches::new));
	}

	/** The helpers' searches for {@code id}'s waiting stage (35.7b). */
	public List<Search> searches(UUID id) {
		return searches.getOrDefault(id, List.of());
	}

	/** Records the helpers' searches for {@code id}'s waiting stage; saved, but the Wonders' powers stay as they were. */
	public void setSearches(UUID id, List<Search> now) {
		if (!searches(id).equals(now)) {
			if (now.isEmpty()) {
				searches.remove(id);
			} else {
				searches.put(id, List.copyOf(now));
			}
			setDirty();
		}
	}

	public static WonderManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WonderManager::new, WonderManager::load, null), NAME);
	}

	/** Every Wonder site on the server, dimension by dimension. */
	public static List<Located> everywhere(MinecraftServer server) {
		List<Located> out = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (WonderSite site : get(level).sites()) {
				out.add(new Located(level, site));
			}
		}
		return out;
	}

	/** A site and the dimension it's in. */
	public record Located(ServerLevel level, WonderSite site) {
	}

	public List<WonderSite> sites() {
		return List.copyOf(sites.values());
	}

	/** The Wonders of the village round {@code hall}, going up or finished. */
	public List<WonderSite> in(BlockPos hall) {
		return sites.values().stream().filter(s -> s.hall().equals(hall)).toList();
	}

	public Optional<WonderSite> site(UUID id) {
		return Optional.ofNullable(sites.get(id));
	}

	/** Records a Wonder begun in the village round {@code hall} on {@code day}, at its first stage. */
	public WonderSite begin(ResourceLocation wonder, BlockPos hall, @Nullable UUID owner, String ownerName, long day) {
		WonderSite site = new WonderSite(UUID.randomUUID(), wonder, hall.immutable(), Optional.ofNullable(owner), ownerName == null ? "" : ownerName,
			1, day, Optional.empty());
		sites.put(site.id(), site);
		changed();
		return site;
	}

	/** Moves a site to stage {@code stage}. */
	public void setStage(UUID id, int stage) {
		WonderSite site = sites.get(id);
		if (site != null) {
			if (site.stage() != stage) {
				searches.remove(id); // 35.7b: a stage's searches end with it
			}
			sites.put(id, site.atStage(stage));
			changed();
		}
	}

	/** Marks a site finished on {@code day}. */
	public void finish(UUID id, long day) {
		WonderSite site = sites.get(id);
		if (site != null) {
			sites.put(id, site.finishedOn(day));
			changed();
		}
	}

	/** Records where a site's Wonder went down and where its Foundation Stone is (35.4). */
	public void place(UUID id, io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement, BlockPos stone) {
		WonderSite site = sites.get(id);
		if (site != null) {
			sites.put(id, site.placedAt(placement, stone));
			changed();
		}
	}

	/** Records the build sites of a site's current stage (35.4). */
	public void setParts(UUID id, List<UUID> parts) {
		WonderSite site = sites.get(id);
		if (site != null) {
			sites.put(id, site.withParts(parts));
			changed();
		}
	}

	/** Records the builder a site's stages go to (35.4). */
	public void setBuilder(UUID id, UUID builder) {
		WonderSite site = sites.get(id);
		if (site != null && !site.builder().equals(Optional.of(builder))) {
			sites.put(id, site.withBuilder(builder));
			changed();
		}
	}

	/** Forgets a site's lead (B467: the lead died or was retrained and nobody took their part over yet). */
	public void clearBuilder(UUID id) {
		WonderSite site = sites.get(id);
		if (site != null && site.builder().isPresent()) {
			sites.put(id, site.withoutBuilder());
			changed();
		}
	}

	/** Records whether every builder of the village was called to the Wonder's crew (35.5). */
	public void setCalled(UUID id, boolean called) {
		WonderSite site = sites.get(id);
		if (site != null && site.called() != called) {
			sites.put(id, site.withCalled(called));
			changed();
		}
	}

	/** Records whether a site's current stage was dedicated (35.7). */
	public void setDedicated(UUID id, boolean dedicated) {
		WonderSite site = sites.get(id);
		if (site != null && site.dedicated() != dedicated) {
			sites.put(id, site.withDedicated(dedicated));
			changed();
		}
	}

	/** Records the goods handed in at the stone for a site's current stage (35.7). */
	public void setDelivered(UUID id, List<WonderSite.Handed> delivered) {
		WonderSite site = sites.get(id);
		if (site != null && !site.delivered().equals(delivered)) {
			sites.put(id, site.withDelivered(delivered));
			changed();
		}
	}

	/** Records how a finished site is kept up: its last count, whether it's damaged, its festival (35.8). */
	public void setKept(UUID id, WonderSite.Kept kept) {
		WonderSite site = sites.get(id);
		if (site != null && !site.kept().equals(kept)) {
			sites.put(id, site.withKept(kept));
			changed();
		}
	}

	/** Something changed: saved with the world, and the Wonders' powers looked up again (35.8, {@link Wonders#power}). */
	private void changed() {
		setDirty();
		Wonders.forgetPowers();
	}

	/** Forgets a site (a cancelled Wonder, or a test's). */
	public void remove(UUID id) {
		searches.remove(id);
		if (sites.remove(id) != null) {
			changed();
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		WonderSite.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(sites.values())).result().ifPresent(t -> tag.put("sites", t));
		List<Searches> all = searches.entrySet().stream().map(e -> new Searches(e.getKey(), e.getValue())).toList();
		if (!all.isEmpty()) {
			Searches.CODEC.listOf().encodeStart(NbtOps.INSTANCE, all).result().ifPresent(t -> tag.put("searches", t));
		}
		return tag;
	}

	public static WonderManager load(CompoundTag tag, HolderLookup.Provider registries) {
		WonderManager manager = new WonderManager();
		Tag list = tag.get("sites");
		if (list != null) {
			WonderSite.CODEC.listOf().parse(NbtOps.INSTANCE, list)
				.resultOrPartial(err -> AliveWorkplace.LOG.warn("Wonders: {}", err))
				.ifPresent(all -> all.forEach(s -> manager.sites.put(s.id(), s)));
		}
		Tag found = tag.get("searches"); // 35.7b: absent in a world saved before
		if (found != null) {
			Searches.CODEC.listOf().parse(NbtOps.INSTANCE, found)
				.resultOrPartial(err -> AliveWorkplace.LOG.warn("Wonders' searches: {}", err))
				.ifPresent(all -> all.forEach(s -> {
					if (manager.sites.containsKey(s.site())) {
						manager.searches.put(s.site(), List.copyOf(s.lines()));
					}
				}));
		}
		Wonders.forgetPowers(); // 35.8: the powers worked out again from what was loaded
		return manager;
	}
}
