package io.github.jcondedata.aliveworkplace.mine;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** All quarries in one dimension, saved with the world. */
public final class QuarrySiteManager extends SavedData {
	private static final String NAME = "aliveworkplace_quarries";
	private final Map<UUID, QuarrySite> sites = new LinkedHashMap<>();

	public static QuarrySiteManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(QuarrySiteManager::new, QuarrySiteManager::load, null), NAME);
	}

	public QuarrySite create(UUID owner, String ownerName, ResourceLocation dimension, BoundingBox box, int depth) {
		QuarrySite site = new QuarrySite(UUID.randomUUID(), owner, ownerName, dimension, box, depth);
		add(site);
		return site;
	}

	private void add(QuarrySite site) {
		site.setOnChange(this::setDirty);
		sites.put(site.id(), site);
		setDirty();
	}

	@Nullable
	public QuarrySite get(UUID id) {
		return sites.get(id);
	}

	public void remove(UUID id) {
		if (sites.remove(id) != null) {
			setDirty();
		}
	}

	public Collection<QuarrySite> all() {
		return Collections.unmodifiableCollection(sites.values());
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (QuarrySite site : sites.values()) {
			list.add(site.save());
		}
		tag.put("quarries", list);
		return tag;
	}

	private static QuarrySiteManager load(CompoundTag tag, HolderLookup.Provider registries) {
		QuarrySiteManager manager = new QuarrySiteManager();
		for (Tag t : tag.getList("quarries", Tag.TAG_COMPOUND)) {
			QuarrySite site = QuarrySite.load((CompoundTag) t);
			if (site != null) {
				manager.add(site);
			}
		}
		manager.setDirty(false);
		return manager;
	}
}
