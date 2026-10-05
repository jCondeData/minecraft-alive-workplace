package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The ledger of what players built (27.19), one per dimension: every 16×16×16 section in which a player placed or broke a
 * block within a Village Hall's area. The Steward's plans, roads and walls keep out of marked sections unless the
 * owner approves that one by hand. Kept from 1.1 on: older worlds start with an empty ledger.
 */
public final class PlayerBuilt extends SavedData {
	public static final String NAME = "aliveworkplace_player_built";
	private final LongSet sections = new LongOpenHashSet();

	public static PlayerBuilt get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlayerBuilt::new, PlayerBuilt::load, null), NAME);
	}

	/** A player placed or broke the block at {@code pos}: its section is marked if a hall's area takes it in. */
	public static void changed(ServerLevel level, BlockPos pos) {
		if (VillageHalls.nearest(level, pos).isPresent()) {
			get(level).mark(pos);
		}
	}

	public void mark(BlockPos pos) {
		if (sections.add(SectionPos.asLong(pos))) {
			setDirty();
		}
	}

	public boolean marked(BlockPos pos) {
		return sections.contains(SectionPos.asLong(pos));
	}

	/** Whether any section {@code box} reaches into is marked. */
	public boolean marked(BoundingBox box) {
		if (sections.isEmpty()) {
			return false;
		}
		for (int sx = SectionPos.blockToSectionCoord(box.minX()); sx <= SectionPos.blockToSectionCoord(box.maxX()); sx++) {
			for (int sy = SectionPos.blockToSectionCoord(box.minY()); sy <= SectionPos.blockToSectionCoord(box.maxY()); sy++) {
				for (int sz = SectionPos.blockToSectionCoord(box.minZ()); sz <= SectionPos.blockToSectionCoord(box.maxZ()); sz++) {
					if (sections.contains(SectionPos.asLong(sx, sy, sz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public int size() {
		return sections.size();
	}

	/** Forgets the marks of the sections {@code box} reaches into (tests: an area handed to the next test). */
	public void forget(BoundingBox box) {
		boolean changed = sections.removeIf((long s) -> box.intersects(new BoundingBox(SectionPos.sectionToBlockCoord(SectionPos.x(s)),
			SectionPos.sectionToBlockCoord(SectionPos.y(s)), SectionPos.sectionToBlockCoord(SectionPos.z(s)),
			SectionPos.sectionToBlockCoord(SectionPos.x(s)) + 15, SectionPos.sectionToBlockCoord(SectionPos.y(s)) + 15,
			SectionPos.sectionToBlockCoord(SectionPos.z(s)) + 15)));
		if (changed) {
			setDirty();
		}
	}

	/** Forgets every mark (tests). */
	public void clear() {
		sections.clear();
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putLongArray("sections", sections.toLongArray());
		return tag;
	}

	public static PlayerBuilt load(CompoundTag tag, HolderLookup.Provider registries) {
		PlayerBuilt ledger = new PlayerBuilt();
		for (long s : Nbt.getLongArray(tag, "sections")) {
			ledger.sections.add(s);
		}
		return ledger;
	}
}
