package io.github.jcondedata.aliveworkplace.blueprint;

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
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureManager().get(id);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
		return template.map(t -> CACHE.computeIfAbsent(t, k -> Blueprint.fromTemplate(id, k, BuiltInRegistries.BLOCK.asLookup())));
	}

	/** Every structure id the server can see, minus vanilla worldgen pieces (villages, fossils...). */
	public static List<ResourceLocation> list(MinecraftServer server, boolean includeVanillaWorldgen) {
		return server.getStructureManager().listTemplates()
			.filter(id -> includeVanillaWorldgen || !isVanillaWorldgen(id))
			.sorted()
			.toList();
	}

	private static boolean isVanillaWorldgen(ResourceLocation id) {
		// Structure-block saves land in the minecraft namespace at the top level; worldgen pieces are nested.
		return id.getNamespace().equals("minecraft") && id.getPath().contains("/");
	}

	private BlueprintLibrary() {
	}
}
