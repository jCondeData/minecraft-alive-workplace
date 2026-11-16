package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.move.MoveFit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall's Map section (Copperline, ROADMAP 47.2, {@code docs/design/copperline/boards/HallMap.png}): the village
 * from above, drawn by the client from what this puts on the Map button while the section is open. The land is the Move
 * page's village map ({@link MovePage.MapView}: map colors, roads and water), with the Hall, the village's edge and every
 * finished building (its box and name, {@link MoveFit#others}). Only the open section carries it, so the other sections
 * send nothing more; the land is read again at most every five seconds per hall.
 */
public final class HallMap {
	/** The mark on the Map button holding the view. */
	public static final String MARK = "aliveworkplace:hall_map";
	/** How long a hall's land is kept before it is read again (ticks). */
	private static final long KEEP = 100;

	private record Kept(long tick, MovePage.MapView map) {
	}

	private static final Map<BlockPos, Kept> LAND = new ConcurrentHashMap<>();

	/** Puts the village's map on {@code icon} (the Map button). */
	static void attach(ItemStack icon, ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		Kept kept = LAND.get(hall);
		if (kept == null || now - kept.tick() > KEEP || now < kept.tick()) {
			kept = new Kept(now, MovePage.MapView.of(level, hall));
			LAND.put(hall.immutable(), kept);
		}
		MovePage.MapView map = kept.map();
		List<MoveFit.Other> others = MoveFit.others(level, hall, null);
		int[] boxes = new int[others.size() * 4];
		ListTag names = new ListTag();
		for (int i = 0; i < others.size(); i++) {
			BoundingBox b = others.get(i).box();
			boxes[i * 4] = b.minX();
			boxes[i * 4 + 1] = b.minZ();
			boxes[i * 4 + 2] = b.maxX();
			boxes[i * 4 + 3] = b.maxZ();
			names.add(StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(others.get(i).name(), level.registryAccess())));
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("ox", map.ox());
		tag.putInt("oz", map.oz());
		tag.putInt("step", map.step());
		tag.putByteArray("colors", map.colors());
		tag.putByteArray("kinds", map.kinds());
		tag.putInt("hallX", hall.getX());
		tag.putInt("hallZ", hall.getZ());
		tag.putInt("radius", VillageHalls.RADIUS);
		tag.putIntArray("others", boxes);
		tag.put("names", names);
		CompoundTag data = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.put(MARK, tag);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
	}

	/** The map on the Map button {@code icon} (null: none, the section isn't open). */
	@Nullable
	public static CompoundTag view(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(MARK)) {
			return null;
		}
		CompoundTag tag = data.copyTag().getCompound(MARK);
		return tag.getByteArray("colors").length == MovePage.MAP * MovePage.MAP ? tag : null;
	}

	/** How many buildings the view has. */
	public static int buildings(CompoundTag view) {
		return view.getIntArray("others").length / 4;
	}

	/** Building {@code i}'s box in blocks: min x, min z, max x, max z. */
	public static int[] box(CompoundTag view, int i) {
		int[] o = view.getIntArray("others");
		return new int[]{o[i * 4], o[i * 4 + 1], o[i * 4 + 2], o[i * 4 + 3]};
	}

	/** The building under block ({@code x}, {@code z}) (-1: none); the smallest wins where boxes overlap. */
	public static int buildingAt(CompoundTag view, int x, int z) {
		int best = -1;
		long bestArea = Long.MAX_VALUE;
		for (int i = 0; i < buildings(view); i++) {
			int[] b = box(view, i);
			if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) {
				long area = (long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
				if (area < bestArea) {
					best = i;
					bestArea = area;
				}
			}
		}
		return best;
	}

	/** Building {@code i}'s name as the server wrote it (a component's JSON). */
	public static String nameJson(CompoundTag view, int i) {
		ListTag names = view.getList("names", net.minecraft.nbt.Tag.TAG_STRING);
		return i >= 0 && i < names.size() ? names.getString(i) : "";
	}

	/** Forgets the land read (tests). */
	public static void forget() {
		LAND.clear();
	}

	/** The pieces of the legend, in order: the layer each switches, and whether it is on at first. */
	public static final List<String> LAYERS = List.of("buildings", "names", "roads", "edge");

	/** Which of {@link #LAYERS} the boxes {@code list} hold, as a bit set, all on. */
	public static int allLayers() {
		return (1 << LAYERS.size()) - 1;
	}

	/** A legend row's height, on a normal and a small screen; the Draw a map button's. */
	public static final int LAYER_H = 14;
	public static final int LAYER_H_SMALL = 12;
	public static final int DRAW_H = 16;

	/**
	 * Where the Map section's parts sit on {@code body} [x, y, w, h]: the square map, the panel at its right (the layers,
	 * the building selected) and the Draw a map button at the panel's foot, each [x, y, w, h].
	 */
	public static int[][] layout(int[] body, boolean small) {
		int x = body[0];
		int y = body[1];
		int w = body[2];
		int h = body[3];
		int panelW = small ? Math.max(80, w * 40 / 100) : Math.max(100, Math.min(140, w * 36 / 100));
		int s = Math.max(48, Math.min(h - 2, w - panelW - 8));
		int px = x + s + 8;
		int[] map = {x, y + 1, s, s};
		int[] panel = {px, y, x + w - px, h - DRAW_H - 4};
		int[] draw = {px, y + h - DRAW_H, x + w - px, DRAW_H};
		return new int[][]{map, panel, draw};
	}

	private HallMap() {
	}
}
