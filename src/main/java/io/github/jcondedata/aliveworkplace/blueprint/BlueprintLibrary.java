package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
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

	public static Optional<Blueprint> get(ServerLevel level, ResourceLocation id) {
		return get(level.getServer(), id);
	}

	public static Optional<Blueprint> get(MinecraftServer server, ResourceLocation id) {
		Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(id);
		if (styled.isPresent()) {
			// A blueprint in another style: the base with its blocks swapped (as drawn if the style is gone).
			Optional<BlueprintStyles.Style> style = BlueprintStyles.get(styled.get().style());
			return get(server, styled.get().base()).map(b -> style.map(s -> BlueprintStyles.apply(s, b, id))
				.orElseGet(() -> new Blueprint(id, b.size(), b.blocks(), b.entities())));
		}
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureManager().get(id);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
		return template.map(t -> CACHE.computeIfAbsent(t, k -> StarterBlueprints.withFeatures(
			Blueprint.fromTemplate(id, k, Lookup.lookup(BuiltInRegistries.BLOCK)))));
	}

	/**
	 * The blueprint library: this mod's blueprints (starter builds, imports, uploads, and anything a
	 * datapack adds under {@code data/aliveworkplace/structure/}) plus every structure players saved
	 * with a Structure Block. World-generation pieces from vanilla and other mods are left out; with
	 * {@code everything} they are included (for operators' commands).
	 */
	public static List<ResourceLocation> list(MinecraftServer server, boolean everything) {
		Path generated = server.getWorldPath(LevelResource.GENERATED_DIR);
		return server.getStructureManager().listTemplates()
			.filter(id -> everything || cobblemonOk(id))
			.filter(id -> everything || id.getNamespace().equals(AliveWorkplace.MOD_ID) && !isWorldgenPiece(id)
				|| Files.isRegularFile(generated.resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt")))
			.sorted()
			.toList();
	}

	/** Blueprints made of Cobblemon's blocks ({@link StarterBlueprints#COBBLEMON_ONLY}) are listed only with Cobblemon. */
	private static boolean cobblemonOk(ResourceLocation id) {
		return io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")
			|| StarterBlueprints.COBBLEMON_ONLY.stream().noneMatch(e -> e.id().equals(id));
	}

	/** Our village pieces ({@code aliveworkplace:village/...}) are for world generation, not the library; research blueprints come from scholars. */
	public static boolean isWorldgenPiece(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) && (id.getPath().startsWith("village/") || id.getPath().startsWith("compat_test/")
			|| id.getPath().startsWith("research/") // research/: drawn up by a village's scholars (see research/Research)
			|| id.getPath().startsWith("camp/")); // camp/: set up at once by a Settler's Wagon
	}

	private BlueprintLibrary() {
	}
}
