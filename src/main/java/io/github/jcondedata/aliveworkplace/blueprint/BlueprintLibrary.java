package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where blueprints come from. Everything is backed by Minecraft's own structure template manager,
 * which already merges three sources for us:
 * <ul>
 *   <li>structures shipped in mods/datapacks ({@code data/<ns>/structure/<path>.nbt}) — our starter builds live here;</li>
 *   <li>structures saved in-game with a Structure Block ({@code <world>/generated/<ns>/structures/});</li>
 *   <li>files imported by this mod (converted and written to the generated folder).</li>
 * </ul>
 */
public final class BlueprintLibrary {
	private static final Map<StructureTemplate, Blueprint> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
	/** The template manager's last full list (B412), and how many times it was read. */
	private static volatile Listing listing;
	private static final AtomicInteger rescans = new AtomicInteger();

	public static Optional<Blueprint> get(ServerLevel level, ResourceLocation id) {
		return get(level.getServer(), id);
	}

	public static Optional<Blueprint> get(MinecraftServer server, ResourceLocation id) {
		if (io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.wonderOf(id).isPresent()) {
			// A Wonder's whole blueprint (35.3): its stages' parts merged, kept until the Wonders load again.
			return io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.whole(server, id);
		}
		Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(id);
		if (styled.isPresent()) {
			// A blueprint in another style: the base with its blocks swapped (as drawn if the style is gone).
			Optional<BlueprintStyles.Style> style = BlueprintStyles.get(styled.get().style());
			return get(server, styled.get().base()).map(b -> style.map(s -> BlueprintStyles.apply(s, b, id))
				.orElseGet(() -> new Blueprint(id, b.size(), b.blocks(), b.entities())));
		}
		Optional<ResourceLocation> house = io.github.jcondedata.aliveworkplace.world.VillagePieces.outsideOf(id);
		if (house.isPresent()) {
			// A village house's outside in a style (23.10a): the house without its room.
			return get(server, house.get()).map(b -> io.github.jcondedata.aliveworkplace.world.VillagePieces.outside(b, id));
		}
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureManager().get(id);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
		return template.map(t -> CACHE.computeIfAbsent(t, k -> StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
			Blueprint.fromTemplate(id, k, Lookup.lookup(BuiltInRegistries.BLOCK))))));
	}

	/**
	 * The blueprint library: this mod's blueprints (starter builds, imports, uploads, and anything a
	 * datapack adds under {@code data/aliveworkplace/structure/}) plus every structure players saved
	 * with a Structure Block. World-generation pieces from vanilla and other mods are left out; with
	 * {@code everything} they are included (for operators' commands).
	 */
	public static List<ResourceLocation> list(MinecraftServer server, boolean everything) {
		Set<ResourceLocation> generated = generatedIds(server);
		return templates(server, generated).stream()
			// B412: the one test that turns away almost every structure of a modpack goes first, so the others (each a
			// look through a list of builds) run on the mod's own builds only, not on thousands of other mods' pieces
			.filter(id -> everything || id.getNamespace().equals(AliveWorkplace.MOD_ID) && !isWorldgenPiece(id) || generated.contains(id))
			.filter(id -> everything || cobblemonOk(id))
			.filter(id -> everything || megaOk(id)) // the Mega Shrine: Mega Showdown's blocks (38.7)
			.filter(id -> everything || io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds.listed(id)) // Create's builds (39.4)
			.filter(id -> everything || io.github.jcondedata.aliveworkplace.city.CaravanTrains.listed(id)) // the Train Station (39.7)
			.filter(id -> everything || !soldOnly(id)) // sold by villagers only (34.15a, 34.15b)
			.filter(id -> everything || StarterBlueprints.offered(id)) // an expansion's build, only once it's open (B119)
			.filter(id -> everything || !io.github.jcondedata.aliveworkplace.city.Roads.isSegment(id)) // a Steward's road segments (27.15)
			.sorted()
			.toList();
	}

	/**
	 * Every structure the template manager knows. B412: listing them reads through every data pack and mod jar (in a
	 * modpack, thousands of files), and a builder's level-up lists the library ({@link
	 * io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks#nextForLevelUp}), so in the 40-plot benchmark a single
	 * builder's tick took over 100 ms. The list is kept until the data packs load again (a new resource manager) or the
	 * world's {@code generated} folder (Structure Block saves, imports, uploads) changes, which is cheap to look at.
	 */
	private static List<ResourceLocation> templates(MinecraftServer server, Set<ResourceLocation> generated) {
		Listing cached = listing;
		if (cached != null && cached.server.get() == server && cached.resources.get() == server.getResourceManager()
			&& cached.generated.equals(generated)) {
			return cached.ids;
		}
		rescans.incrementAndGet();
		List<ResourceLocation> ids = server.getStructureManager().listTemplates().toList();
		listing = new Listing(new WeakReference<>(server), new WeakReference<>(server.getResourceManager()), Set.copyOf(generated), ids);
		return ids;
	}

	/** How many times the template manager's whole list was read (B412's test: not once a level-up). */
	public static int rescans() {
		return rescans.get();
	}

	/**
	 * The structures saved in the world's {@code generated} folder ({@code <ns>/structures/<path>.nbt}), found the way
	 * the template manager finds them.
	 */
	private static Set<ResourceLocation> generatedIds(MinecraftServer server) {
		Path generated = server.getWorldPath(LevelResource.GENERATED_DIR);
		if (!Files.isDirectory(generated)) {
			return Set.of();
		}
		Set<ResourceLocation> out = new HashSet<>();
		try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(generated, Files::isDirectory)) {
			for (Path namespace : namespaces) {
				Path folder = namespace.resolve("structures");
				if (!Files.isDirectory(folder)) {
					continue;
				}
				try (Stream<Path> files = Files.find(folder, Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toString().endsWith(".nbt"))) {
					files.forEach(p -> {
						String path = folder.relativize(p).toString().replace(p.getFileSystem().getSeparator(), "/");
						ResourceLocation id = ResourceLocation.tryBuild(namespace.getFileName().toString(), path.substring(0, path.length() - ".nbt".length()));
						if (id != null) {
							out.add(id);
						}
					});
				}
			}
		} catch (IOException | UncheckedIOException e) {
			AliveWorkplace.LOG.warn("Could not list the generated structures in {}: {}", generated, e.toString());
		}
		return out;
	}

	private record Listing(WeakReference<MinecraftServer> server, WeakReference<Object> resources, Set<ResourceLocation> generated,
		List<ResourceLocation> ids) {
	}

	/** Blueprints made of Cobblemon's blocks or for its jobs ({@link StarterBlueprints#COBBLEMON_ONLY}, {@link StarterBlueprints#COBBLEMON_WORKPLACES}) are listed only with Cobblemon. */
	private static boolean cobblemonOk(ResourceLocation id) {
		return io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")
			|| StarterBlueprints.COBBLEMON_ONLY.stream().noneMatch(e -> e.id().equals(id))
			&& StarterBlueprints.COBBLEMON_WORKPLACES.stream().noneMatch(e -> e.id().equals(id));
	}

	/** Blueprints of Mega Showdown's blocks ({@link StarterBlueprints#MEGA_ONLY}) are listed only while those blocks exist and {@code villageMegas} is on. */
	public static boolean megaOk(ResourceLocation id) {
		return StarterBlueprints.MEGA_ONLY.stream().noneMatch(e -> e.id().equals(BlueprintStyles.base(id)))
			|| io.github.jcondedata.aliveworkplace.trainer.MegaShrines.listed();
	}

	/** Blueprints villagers sell and the Blueprint Table doesn't list ({@link StarterBlueprints#SOLD_ONLY}); they still load by id. */
	public static boolean soldOnly(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		return StarterBlueprints.SOLD_ONLY.stream().anyMatch(e -> e.id().equals(base));
	}

	/** Our village pieces ({@code aliveworkplace:village/...}) are for world generation, not the library; research blueprints come from scholars. */
	public static boolean isWorldgenPiece(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) && (id.getPath().startsWith("village/") || id.getPath().startsWith("compat_test/")
			|| id.getPath().startsWith("research/") // research/: drawn up by a village's scholars (see research/Research)
			|| id.getPath().startsWith("realm/") // realm/: drawn on a realm capital's Realm tab (33.14, see realm/RoyalHalls)
			|| id.getPath().startsWith("camp/") // camp/: set up at once by a Settler's Wagon
			|| id.getPath().startsWith(ModdedLibrary.PREFIX) // modded/: the modded library's copies, built in place of their vanilla build (37.12)
			|| id.getPath().startsWith("legend/") // legend/: a found Legend's camp, set down at once (29.9)
			|| id.getPath().startsWith("story/") // story/: set down by a story arc (31.15's Bandit King's fort)
			|| id.getPath().startsWith("train/") // train/: the village's Create train, built by a village at its station (39.7c)
			|| id.getPath().startsWith("wonder/")); // wonder/: a Wonder's stage parts, raised from its Wonder Plan (35.3)
	}

	private BlueprintLibrary() {
	}
}
