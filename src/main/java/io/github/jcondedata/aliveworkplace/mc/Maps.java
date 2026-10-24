package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Filled maps (how a map's data is made and saved changes between versions). */
public final class Maps {
	/**
	 * The centre the game gives a new map asked for round ({@code x}, {@code z}) at {@code scale}: snapped to that scale's
	 * grid of squares 128 &lt;&lt; scale wide, offset by 64 (as {@code MapItemSavedData.createFresh} does on 1.21.1).
	 */
	public static int gridCentre(double at, byte scale) {
		int size = 128 * (1 << scale);
		return (int) Math.floor((at + 64.0) / size) * size + size / 2 - 64;
	}

	/**
	 * A new filled map (tracking position, unlimited tracking) centred exactly on ({@code centerX}, {@code centerZ}), not
	 * on the game's grid: the map's saved data carries any centre, as an explorer map's would if written by hand (B368).
	 */
	public static ItemStack filledAt(ServerLevel level, int centerX, int centerZ, byte scale) {
		ItemStack map = MapItem.create(level, centerX, centerZ, scale, true, true);
		MapId id = map.get(DataComponents.MAP_ID);
		MapItemSavedData fresh = id == null ? null : level.getMapData(id);
		if (fresh == null || (fresh.centerX == centerX && fresh.centerZ == centerZ)) {
			return map;
		}
		CompoundTag tag = fresh.save(new CompoundTag(), level.registryAccess());
		tag.putInt("xCenter", centerX);
		tag.putInt("zCenter", centerZ);
		MapItemSavedData moved = MapItemSavedData.load(tag, level.registryAccess());
		moved.setDirty();
		level.setMapData(id, moved);
		return map;
	}

	private Maps() {
	}
}
