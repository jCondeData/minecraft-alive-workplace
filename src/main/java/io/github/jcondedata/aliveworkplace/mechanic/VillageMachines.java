package io.github.jcondedata.aliveworkplace.mechanic;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B243: whose machines and chests villagers' Create machine work may use. Only the village's own: a machine or a store
 * chest counts when it stands inside a building the mod's builders finished (an entry of
 * {@link BuildSiteManager#finishedIn}, inside its blueprint's outline). A player's own millstone, press, mixer or crushing
 * wheels, and a player's chests by a toolbox or a workstation, stand outside every such building: nobody feeds them, takes
 * from them or carries their output away. A worker with no village machine does no machine work.
 */
public final class VillageMachines {
	/** Ticks the outlines are kept before they are read again (sooner when a building is added or forgotten). */
	private static final int KEEP = 200;
	private static final Map<ServerLevel, Outlines> CACHE = new WeakHashMap<>();

	private record Outlines(int changes, long readAt, List<BoundingBox> boxes) {
	}

	private VillageMachines() {
	}

	/** The outlines of every building the builders finished in {@code level}. */
	static List<BoundingBox> outlines(ServerLevel level) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		long now = level.getGameTime();
		synchronized (CACHE) {
			Outlines cached = CACHE.get(level);
			if (cached != null && cached.changes() == manager.changes() && now >= cached.readAt() && now - cached.readAt() < KEEP) {
				return cached.boxes();
			}
		}
		List<BoundingBox> boxes = new ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			BlueprintLibrary.get(level, f.structure()).ifPresent(b -> boxes.add(BlueprintOutline.bounds(f.placement(), b.size())));
		}
		List<BoundingBox> out = List.copyOf(boxes);
		synchronized (CACHE) {
			CACHE.put(level, new Outlines(manager.changes(), now, out));
		}
		return out;
	}

	/** Whether {@code pos} stands inside a building the village's builders finished. */
	public static boolean village(ServerLevel level, BlockPos pos) {
		for (BoundingBox box : outlines(level)) {
			if (box.isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** The village's store by {@code post} for machine work: the chests near it that stand inside a village building. */
	public static List<BlockPos> stores(ServerLevel level, BlockPos post) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos chest : SupplyContainers.find(level, post, null)) {
			if (village(level, chest)) {
				out.add(chest);
			}
		}
		return out;
	}
}
