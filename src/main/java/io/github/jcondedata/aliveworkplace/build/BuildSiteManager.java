package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** All build sites in one dimension, saved with the world. */
public final class BuildSiteManager extends SavedData {
	private static final String NAME = "aliveworkplace_build_sites";

	private final Map<UUID, BuildSite> sites = new LinkedHashMap<>();

	/** A building a builder finished here: what, where and for whom (so an upgrade can line up with it). */
	public record Finished(ResourceLocation structure, BlueprintData.Placement placement, UUID owner) {
	}

	/** Most finished buildings remembered per dimension (the oldest are forgotten first). */
	private static final int MAX_FINISHED = 2000;
	private final java.util.ArrayDeque<Finished> finished = new java.util.ArrayDeque<>();

	/** Remembers a finished building (replacing an older one on the same spot, such as the tier it upgraded). */
	public void recordFinished(ResourceLocation structure, BlueprintData.Placement placement, UUID owner) {
		finished.removeIf(f -> f.placement().equals(placement));
		finished.addLast(new Finished(structure, placement, owner));
		while (finished.size() > MAX_FINISHED) {
			finished.removeFirst();
		}
		setDirty();
	}

	/** Forgets the building on this spot (it was taken down). */
	public void forgetFinished(BlueprintData.Placement placement) {
		if (finished.removeIf(f -> f.placement().equals(placement))) {
			setDirty();
		}
	}

	/** The finished building of {@code structure} whose outline contains {@code pos}, if any. */
	public java.util.Optional<Finished> finishedAt(ServerLevel level, ResourceLocation structure, net.minecraft.core.BlockPos pos) {
		for (Finished f : finished) {
			if (f.structure().equals(structure) && f.placement().dimension().equals(level.dimension().location())) {
				boolean inside = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, structure)
					.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), b.size()).isInside(pos))
					.orElse(false);
				if (inside) {
					return java.util.Optional.of(f);
				}
			}
		}
		return java.util.Optional.empty();
	}

	public static BuildSiteManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BuildSiteManager::new, BuildSiteManager::load, null), NAME);
	}

	public BuildSite create(UUID owner, String ownerName, ResourceLocation structure, BlueprintData.Placement placement) {
		BuildSite site = new BuildSite(UUID.randomUUID(), owner, ownerName, structure, placement);
		add(site);
		return site;
	}

	private void add(BuildSite site) {
		site.setOnChange(this::setDirty);
		sites.put(site.id(), site);
		setDirty();
	}

	@Nullable
	public BuildSite get(UUID id) {
		return sites.get(id);
	}

	public void remove(UUID id) {
		if (sites.remove(id) != null) {
			setDirty();
		}
	}

	public Collection<BuildSite> all() {
		return Collections.unmodifiableCollection(sites.values());
	}

	public List<BuildSite> ownedBy(UUID owner) {
		return sites.values().stream().filter(s -> s.owner().equals(owner)).toList();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (BuildSite site : sites.values()) {
			list.add(site.save());
		}
		tag.put("sites", list);
		ListTag done = new ListTag();
		for (Finished f : finished) {
			CompoundTag entry = new CompoundTag();
			entry.putString("structure", f.structure().toString());
			entry.put("placement", BlueprintData.Placement.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, f.placement()).getOrThrow());
			entry.putUUID("owner", f.owner());
			done.add(entry);
		}
		tag.put("finished", done);
		return tag;
	}

	private static BuildSiteManager load(CompoundTag tag, HolderLookup.Provider registries) {
		BuildSiteManager manager = new BuildSiteManager();
		ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			BuildSite site = BuildSite.load(list.getCompound(i));
			if (site != null) {
				manager.add(site);
			}
		}
		ListTag done = tag.getList("finished", Tag.TAG_COMPOUND);
		for (int i = 0; i < done.size(); i++) {
			CompoundTag entry = done.getCompound(i);
			ResourceLocation structure = ResourceLocation.tryParse(entry.getString("structure"));
			var placement = BlueprintData.Placement.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get("placement")).result();
			if (structure != null && placement.isPresent() && entry.hasUUID("owner")) {
				manager.finished.addLast(new Finished(structure, placement.get(), entry.getUUID("owner")));
			}
		}
		manager.setDirty(false);
		return manager;
	}
}
