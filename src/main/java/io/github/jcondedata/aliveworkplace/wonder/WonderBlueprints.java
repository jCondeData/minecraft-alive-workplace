package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A Wonder's whole blueprint (35.3), {@code <namespace>:wonder/<path>} for the Wonder {@code <namespace>:<path>}: its
 * stages' parts laid at their offsets, stage by stage, a later stage's block over an earlier stage's air. The Wonder Plan
 * shows it as see-through blocks, and its material list and size come from it. Built once per loaded Wonder and kept
 * until the Wonders are loaded again. {@link BlueprintLibrary#get} asks here first; the Blueprint Table never lists
 * {@code wonder/} (its parts are only built through the Wonder's own sites).
 */
public final class WonderBlueprints {
	/** The folder a Wonder's blueprints live in: {@code structure/wonder/<id>/stage_<n>_<part>.nbt}. */
	public static final String FOLDER = "wonder/";

	private record Cached(Wonder wonder, Blueprint blueprint) {
	}

	private static final Map<ResourceLocation, Cached> CACHE = new ConcurrentHashMap<>();

	/** The whole blueprint's id of the Wonder {@code wonder}: {@code aliveworkplace:great_cathedral} → {@code aliveworkplace:wonder/great_cathedral}. */
	public static ResourceLocation idOf(ResourceLocation wonder) {
		return ResourceLocation.fromNamespaceAndPath(wonder.getNamespace(), FOLDER + wonder.getPath());
	}

	/** The loaded Wonder whose whole blueprint {@code id} is, if it is one (a part, {@code wonder/<id>/stage_1_0}, isn't). */
	public static Optional<Wonder> wonderOf(ResourceLocation id) {
		if (!id.getPath().startsWith(FOLDER)) {
			return Optional.empty();
		}
		return Optional.ofNullable(Wonders.loaded().get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(FOLDER.length()))));
	}

	/**
	 * The whole blueprint {@code id} if it is a Wonder's (empty when it isn't one, or a part is missing: nothing half
	 * drawn is ever shown or built).
	 */
	public static Optional<Blueprint> whole(MinecraftServer server, ResourceLocation id) {
		Optional<Wonder> wonder = wonderOf(id);
		if (wonder.isEmpty()) {
			return Optional.empty();
		}
		Cached cached = CACHE.get(id);
		if (cached != null && cached.wonder() == wonder.get()) {
			return Optional.of(cached.blueprint());
		}
		Optional<Blueprint> merged = merge(server, id, wonder.get());
		merged.ifPresent(b -> CACHE.put(id, new Cached(wonder.get(), b)));
		return merged;
	}

	/** Lays every part of {@code wonder} at its offset, stage by stage, into one blueprint called {@code id}. */
	static Optional<Blueprint> merge(MinecraftServer server, ResourceLocation id, Wonder wonder) {
		Map<BlockPos, Blueprint.Entry> blocks = new LinkedHashMap<>();
		List<Blueprint.EntityEntry> entities = new ArrayList<>();
		int sx = 0;
		int sy = 0;
		int sz = 0;
		for (Wonder.Stage stage : wonder.stages()) {
			for (Wonder.Part part : stage.parts()) {
				Optional<Blueprint> piece = BlueprintLibrary.get(server, part.part());
				if (piece.isEmpty()) {
					return Optional.empty();
				}
				BlockPos at = part.offset();
				for (Blueprint.Entry e : piece.get().blocks()) {
					BlockPos pos = e.pos().offset(at);
					Blueprint.Entry before = blocks.get(pos);
					if (before != null && !before.state().isAir() && e.state().isAir()) {
						continue; // a later stage never takes away what an earlier one put up
					}
					blocks.put(pos, new Blueprint.Entry(pos, e.state(), e.nbt()));
				}
				for (Blueprint.EntityEntry e : piece.get().entities()) {
					entities.add(new Blueprint.EntityEntry(e.pos().add(at.getX(), at.getY(), at.getZ()), e.nbt()));
				}
				Vec3i size = piece.get().size();
				sx = Math.max(sx, at.getX() + size.getX());
				sy = Math.max(sy, at.getY() + size.getY());
				sz = Math.max(sz, at.getZ() + size.getZ());
			}
		}
		return Optional.of(new Blueprint(id, new Vec3i(sx, sy, sz), List.copyOf(blocks.values()), List.copyOf(entities)));
	}

	/** The parts of {@code wonder} whose structure file isn't there, in stage order. */
	public static List<ResourceLocation> missingParts(MinecraftServer server, Wonder wonder) {
		List<ResourceLocation> out = new ArrayList<>();
		for (Wonder.Stage stage : wonder.stages()) {
			for (Wonder.Part part : stage.parts()) {
				if (BlueprintLibrary.get(server, part.part()).isEmpty()) {
					out.add(part.part());
				}
			}
		}
		return out;
	}

	/** Forgets every whole blueprint (the Wonders were loaded again). */
	public static void forget() {
		CACHE.clear();
	}

	private WonderBlueprints() {
	}
}
