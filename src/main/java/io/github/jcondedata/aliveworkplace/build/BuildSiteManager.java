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
		manager.setDirty(false);
		return manager;
	}
}
