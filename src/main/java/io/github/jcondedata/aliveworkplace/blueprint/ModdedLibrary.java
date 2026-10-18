package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The two build libraries (ROADMAP 37.12, owner 2026-10-18). The vanilla library is every blueprint as it ships, never
 * changed. The modded library is a copy of each home and workshop dressed with the pack's building mods (Rechiseled,
 * Supplementaries, Carved Wood, Chipped, Handcrafted), drawn by {@code tools/blueprints/modded_library.py} into
 * {@code data/<ns>/structure/modded/<path>.nbt}. A build of {@code <ns>:<path>} is built from its copy when the switch
 * {@code moddedLibrary} is on (behind {@link Expansions#M37}) and every block of the copy is a real block on this server;
 * otherwise (a mod missing, the switch off, no copy) it is the vanilla build, furnished as before (37.3).
 *
 * <p>The choice is made once, when a build is planned, and saved with its furniture ({@code FurnitureChoice#modded}),
 * so a config change or a removed mod never changes a build that is going up or standing. The copy has the vanilla
 * build's id (and so its furniture spots, upgrades and basement): only its blocks differ. Ids are read from the copy's
 * own file, so a mod that is missing is seen by its block ids alone (nothing of the mods is compiled against).
 */
public final class ModdedLibrary implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("modded_library");
	/** Where the copies live in a data pack: {@code data/<ns>/structure/modded/<path>.nbt}. */
	public static final String FOLDER = "structure/modded";
	/** The prefix of a copy's own template id ({@code aliveworkplace:modded/starter_cottage}). */
	public static final String PREFIX = "modded/";

	/** Builds use the modded library when they can (config {@code moddedLibrary}, gated by {@link Expansions#M37}). */
	public static volatile boolean ENABLED = Expansions.on(Expansions.M37);

	/** A copy: its file (kept packed until built) and the block ids it uses. */
	private record Copy(byte[] file, Set<ResourceLocation> blocks) {
	}

	private static volatile Map<ResourceLocation, Copy> copies = Map.of();
	private static final Map<ResourceLocation, Blueprint> BUILT = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onDataReload(ID, new ModdedLibrary());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, Copy> found = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".nbt")).entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath();
			ResourceLocation base = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".nbt".length()));
			try (InputStream in = e.getValue().open()) {
				byte[] bytes = in.readAllBytes();
				found.put(base, new Copy(bytes, blocksOf(read(bytes))));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping modded build {}: {}", file, ex.getMessage());
			}
		}
		copies = Collections.unmodifiableMap(found);
		BUILT.clear();
		AliveWorkplace.LOG.info("Modded build library: {} builds", copies.size());
	}

	private static CompoundTag read(byte[] bytes) throws java.io.IOException {
		return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
	}

	private static Set<ResourceLocation> blocksOf(CompoundTag tag) {
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		ListTag palette = Nbt.getList(tag, "palette", Tag.TAG_COMPOUND);
		for (int i = 0; i < palette.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(Nbt.compoundAt(palette, i), "Name"));
			if (id != null) {
				ids.add(id);
			}
		}
		return ids;
	}

	/** The builds that have a modded copy, sorted. */
	public static List<ResourceLocation> builds() {
		List<ResourceLocation> ids = new ArrayList<>(copies.keySet());
		ids.sort(null);
		return ids;
	}

	/** Whether {@code id} has a modded copy at all. */
	public static boolean hasCopy(ResourceLocation id) {
		return copies.containsKey(id);
	}

	/** The blocks of {@code id}'s copy this server doesn't have (its mods are missing); empty when it can be built. */
	public static List<ResourceLocation> missing(ResourceLocation id) {
		Copy copy = copies.get(id);
		if (copy == null) {
			return List.of();
		}
		return copy.blocks().stream().filter(b -> !BuiltInRegistries.BLOCK.containsKey(b)).toList();
	}

	/** Whether {@code id} has a copy and every block of it is a real block here. */
	public static boolean complete(ResourceLocation id) {
		return hasCopy(id) && missing(id).isEmpty();
	}

	/** Whether a new build of {@code id} is built from the modded library now (the switch, and a complete copy). */
	public static boolean use(ResourceLocation id) {
		return ENABLED && complete(id);
	}

	/**
	 * {@code id}'s modded copy as a blueprint with {@code id}'s own id (its features and basement as the vanilla build
	 * has them), or empty when there is no copy or a block of it is missing here.
	 */
	public static Optional<Blueprint> copy(ResourceLocation id) {
		if (!complete(id)) {
			return Optional.empty();
		}
		Blueprint built = BUILT.get(id);
		if (built == null) {
			try {
				CompoundTag tag = read(copies.get(id).file());
				built = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
					Blueprint.fromStructureNbt(id, tag, Lookup.lookup(BuiltInRegistries.BLOCK))));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Can't read the modded build of {}: {}", id, ex.getMessage());
				return Optional.empty();
			}
			BUILT.put(id, built);
		}
		return Optional.of(built);
	}

	private ModdedLibrary() {
	}
}
