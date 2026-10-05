package io.github.jcondedata.aliveworkplace.habitat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A habitat of the village's own (ROADMAP 28.14, Cobblemon 1.8). Cobblemon's Habitat Block has no recipe and drops
 * nothing; in survival it only comes in Cobblemon's habitat structures. Here:
 * <ul>
 * <li><b>Tending:</b> a Habitat Keeper visits the natural-mode Habitat Blocks within {@link #TEND_RANGE} blocks of her
 * pasture once a day (found among the loaded chunks' block entities, never by a block scan), and the hall's list shows
 * each one's phase today ("Lush Cenote, today: Lotad, Wooper, Goomy"), from the block's saved pool and Cobblemon's
 * {@code data/cobblemon/habitat_pools}.</li>
 * <li><b>Founding:</b> an Expert keeper in a village with a finished Habitat Garden puts one natural-mode Habitat Block
 * in place of its mossy centre stone ({@link StarterBlueprints#HABITAT_GARDEN_CENTRE}), mimicking the moss, with the
 * pool {@code data/aliveworkplace/village_habitats/<name>.json} gives the biome there. One per village (kept on the
 * hall); it lies inside the village, so the hall's protection covers it; taking the garden down removes it without a
 * drop.</li>
 * </ul>
 * Cobblemon's block is known by id and set up through its saved settings (the same NBT its own structures carry), so
 * nothing here touches Cobblemon's classes. Config {@code villageHabitats}; waits quietly on Cobblemon 1.7.
 */
public final class VillageHabitats {
	/** Config switch {@code villageHabitats}: off, keepers found no Habitat Block (they still tend the ones there are). */
	public static boolean ENABLED = true;
	/** How far from her pasture a keeper tends Habitat Blocks. */
	public static final int TEND_RANGE = 64;
	/** The villager level that founds the village's habitat (Expert). */
	public static final int EXPERT = 4;
	/** Most species a phase line names. */
	public static final int SPECIES_SHOWN = 5;
	public static final ResourceLocation HABITAT_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "habitat_block");
	/** Cobblemon's natural spawning style (the activated one waits for redstone). */
	public static final String NATURAL = "cobblemon:natural";
	/** The data folder of the biome-to-pool files. */
	public static final String FOLDER = "village_habitats";
	/** What the village's habitat mimics: the garden's moss centre stone it replaces. */
	public static final ResourceLocation MIMIC = ResourceLocation.fromNamespaceAndPath("minecraft", "moss_block");
	/** Cobblemon's own structures' range of influence for a natural block. */
	public static final int RANGE_OF_INFLUENCE = 12;

	private VillageHabitats() {
	}

	/** Whether this server's Cobblemon is too old for Habitat Blocks (1.7), so the keeper's page can say so. */
	public static boolean needsNewerCobblemon() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && !PokemonFeatures.HABITATS.available();
	}

	public static boolean isHabitat(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(HABITAT_BLOCK);
	}

	/** A Habitat Block in natural mode (its {@code activated_style} off). */
	public static boolean isNatural(BlockState state) {
		if (!isHabitat(state)) {
			return false;
		}
		Property<?> activated = state.getBlock().getStateDefinition().getProperty("activated_style");
		return !(activated instanceof BooleanProperty b) || !state.getValue(b);
	}

	public static boolean isGarden(ResourceLocation structure) {
		return structure.equals(StarterBlueprints.HABITAT_GARDEN.id()) || structure.equals(StarterBlueprints.HABITAT_GARDEN_2.id());
	}

	/** The world spot of a placed garden's centre stone. */
	public static BlockPos centre(BlueprintData.Placement placement) {
		return placement.origin().offset(StructureTemplate.transform(StarterBlueprints.HABITAT_GARDEN_CENTRE, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	// ---- Founding ----

	/**
	 * Where {@code keeper} would found the village's habitat now: the centre stone (still moss) of a finished Habitat
	 * Garden by her village's hall, if she is Expert, the village has none yet and Cobblemon has Habitat Blocks.
	 */
	@Nullable
	public static BlockPos foundingSpot(ServerLevel level, Villager keeper, BlockPos pasture) {
		if (!ENABLED || !PokemonFeatures.HABITATS.available() || BuilderLevels.level(keeper) < EXPERT) {
			return null;
		}
		VillageHallBlockEntity hall = hall(level, pasture);
		if (hall == null || has(level, hall)) {
			return null;
		}
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall.getBlockPos(), VillageHalls.RADIUS)) {
			if (!isGarden(f.structure()) || !f.placement().dimension().equals(level.dimension().location())) {
				continue;
			}
			BlockPos centre = centre(f.placement());
			if (level.isLoaded(centre) && level.getBlockState(centre).is(Blocks.MOSS_BLOCK)) {
				return centre;
			}
		}
		return null;
	}

	/** Whether the village of {@code hall} already has its habitat (forgetting one that is gone). */
	static boolean has(ServerLevel level, VillageHallBlockEntity hall) {
		BlockPos at = hall.habitat();
		if (at == null) {
			return false;
		}
		if (level.isLoaded(at) && !isHabitat(level.getBlockState(at))) {
			hall.setHabitat(null);
			return false;
		}
		return true;
	}

	@Nullable
	static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos)
			.map(level::getBlockEntity)
			.filter(VillageHallBlockEntity.class::isInstance)
			.map(VillageHallBlockEntity.class::cast)
			.orElse(null);
	}

	/** {@code keeper} founds the village's habitat at {@code spot}: true if the Habitat Block is there. */
	public static boolean found(ServerLevel level, Villager keeper, BlockPos spot) {
		VillageHallBlockEntity hall = hall(level, spot);
		if (hall == null || has(level, hall)) {
			return false;
		}
		ResourceLocation pool = poolFor(level, spot);
		if (pool == null || !place(level, spot, pool)) {
			return false;
		}
		hall.setHabitat(spot);
		Chronicle.record(level, spot, Chronicle.Kind.BUILT, Component.translatable("chronicle.aliveworkplace.habitat_founded",
			keeper.getDisplayName(), poolName(level, pool)));
		return true;
	}

	/** Places a natural-mode Habitat Block with {@code pool} at {@code pos}, mimicking moss: false without Cobblemon's block. */
	public static boolean place(ServerLevel level, BlockPos pos, ResourceLocation pool) {
		Block block = BuiltInRegistries.BLOCK.getOptional(HABITAT_BLOCK).orElse(null);
		if (block == null) {
			return false;
		}
		BlockState state = block.defaultBlockState();
		state = with(state, "activated_style", false);
		state = with(state, "cancels_regular_spawns", true);
		level.removeBlockEntity(pos);
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
			return false;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity == null) {
			return false;
		}
		try {
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			// The settings Cobblemon's own habitat structures carry for a natural block.
			tag.putString("SpawningStyle", NATURAL);
			tag.putString("PoolId", pool.toString());
			tag.putString("MimicId", MIMIC.toString());
			tag.putString("PhaseOrder", "FULL_RANDOM");
			tag.putString("Modifiers", "");
			tag.putBoolean("ReplaceSpawns", true);
			tag.putInt("RangeOfInfluence", RANGE_OF_INFLUENCE);
			tag.remove("DisplaySpecies");
			// A new block saves Cobblemon's empty custom pool inline (a Name and its Spawns); with those left in, its
			// PoolId names a pool with no spawns. Without them Cobblemon reads the pool from habitat_pools by its id.
			tag.remove("Name");
			tag.remove("Spawns");
			entity.loadWithComponents(tag, level.registryAccess());
			entity.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		} catch (RuntimeException | LinkageError e) {
			AliveWorkplace.LOG.warn("Alive Workplace: couldn't set up the village's Habitat Block at {}: {}", pos, e.toString());
			return false;
		}
		return true;
	}

	private static BlockState with(BlockState state, String name, boolean value) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property instanceof BooleanProperty b ? state.setValue(b, value) : state;
	}

	/** The saved settings of the Habitat Block entity at {@code pos} (empty if there is none). */
	public static CompoundTag settings(ServerLevel level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity == null ? new CompoundTag() : entity.saveWithoutMetadata(level.registryAccess());
	}

	/**
	 * The Cobblemon pool for a village habitat at {@code pos}: the first {@code village_habitats} file (by name) whose
	 * {@code biomes} take the biome there, else the file with no {@code biomes} (anywhere else).
	 */
	@Nullable
	public static ResourceLocation poolFor(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		ResourceLocation fallback = null;
		for (JsonObject file : files(level.getServer().getResourceManager()).values()) {
			ResourceLocation pool = file.has("pool") ? ResourceLocation.tryParse(file.get("pool").getAsString()) : null;
			if (pool == null) {
				continue;
			}
			if (!file.has("biomes")) {
				if (fallback == null) {
					fallback = pool;
				}
			} else if (matches(biome, file.get("biomes"))) {
				return pool;
			}
		}
		return fallback;
	}

	/** The {@code village_habitats} files, by id. */
	public static Map<ResourceLocation, JsonObject> files(ResourceManager resources) {
		Map<ResourceLocation, JsonObject> out = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				out.put(e.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Alive Workplace: couldn't read {}: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	/** {@code biomes}: a biome id, a {@code #tag}, or a list of them. */
	static boolean matches(Holder<Biome> biome, JsonElement biomes) {
		if (biomes.isJsonArray()) {
			for (JsonElement one : (JsonArray) biomes) {
				if (matches(biome, one)) {
					return true;
				}
			}
			return false;
		}
		String text = biomes.getAsString();
		if (text.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(text.substring(1));
			return tag != null && biome.is(TagKey.create(Registries.BIOME, tag));
		}
		ResourceLocation id = ResourceLocation.tryParse(text);
		return id != null && biome.is(ResourceKey.create(Registries.BIOME, id));
	}

	// ---- Removal ----

	/** A garden was taken down: its habitat goes too, without a drop. */
	public static void onTakenDown(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		if (!isGarden(structure)) {
			return;
		}
		BlockPos centre = centre(placement);
		if (level.isLoaded(centre) && isHabitat(level.getBlockState(centre))) {
			level.removeBlockEntity(centre);
			level.setBlock(centre, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		VillageHallBlockEntity hall = hall(level, centre);
		if (hall != null && centre.equals(hall.habitat())) {
			hall.setHabitat(null);
		}
	}

	// ---- Tending ----

	/** Natural Habitat Blocks within {@link #TEND_RANGE} of {@code pasture}, from the loaded chunks' block entities. */
	public static List<BlockPos> tended(ServerLevel level, BlockPos pasture) {
		List<BlockPos> out = new ArrayList<>();
		int r = TEND_RANGE;
		for (int cx = (pasture.getX() - r) >> 4; cx <= (pasture.getX() + r) >> 4; cx++) {
			for (int cz = (pasture.getZ() - r) >> 4; cz <= (pasture.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					BlockPos at = entity.getBlockPos();
					if (at.distSqr(pasture) <= (double) r * r && isNatural(entity.getBlockState())) {
						out.add(at.immutable());
					}
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(pasture)));
		return out;
	}

	/** "Lush Cenote, today: Lotad, Wooper, Goomy" for the Habitat Block at {@code pos}. */
	public static Component todayLine(ServerLevel level, BlockPos pos) {
		CompoundTag settings = settings(level, pos);
		ResourceLocation pool = ResourceLocation.tryParse(settings.getString("PoolId"));
		int phase = HabitatKeepers.snacks() == null ? 0 : HabitatKeepers.snacks().habitatPhase(level.getBlockEntity(pos));
		List<String> species = pool == null ? List.of() : species(level.getServer().getResourceManager(), pool, phase);
		MutableComponent names = Component.empty();
		for (int i = 0; i < Math.min(SPECIES_SHOWN, species.size()); i++) {
			names.append(i == 0 ? Component.empty() : Component.literal(", "))
				.append(Component.translatable("cobblemon.species." + species.get(i) + ".name"));
		}
		if (species.size() > SPECIES_SHOWN) {
			names.append(Component.literal(", …"));
		}
		if (species.isEmpty()) {
			names.append(Component.translatable("screen.aliveworkplace.hall.habitat_nobody"));
		}
		return Component.translatable("screen.aliveworkplace.hall.habitat_today",
			pool == null ? Component.translatable("block.cobblemon.habitat_block") : poolName(level, pool), names);
	}

	/** A pool's display name, from its file's {@code name} (a translation key). */
	public static Component poolName(ServerLevel level, ResourceLocation pool) {
		JsonObject file = poolFile(level.getServer().getResourceManager(), pool);
		if (file != null && file.has("name")) {
			return Component.translatable(file.get("name").getAsString());
		}
		return Component.literal(pool.getPath());
	}

	@Nullable
	static JsonObject poolFile(ResourceManager resources, ResourceLocation pool) {
		ResourceLocation path = ResourceLocation.fromNamespaceAndPath(pool.getNamespace(), "habitat_pools/" + pool.getPath() + ".json");
		Optional<Resource> resource = resources.getResource(path);
		if (resource.isEmpty()) {
			return null;
		}
		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** The species (lower-case ids) a pool spawns in {@code phase} (every phase when 0), in the file's order. */
	public static List<String> species(ResourceManager resources, ResourceLocation pool, int phase) {
		JsonObject file = poolFile(resources, pool);
		if (file == null || !file.has("spawns")) {
			return List.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (JsonElement e : file.getAsJsonArray("spawns")) {
			JsonObject spawn = e.getAsJsonObject();
			if (!spawn.has("species") || phase > 0 && spawn.has("phases") && !inPhases(spawn.get("phases").getAsString(), phase)) {
				continue;
			}
			String name = spawn.get("species").getAsString().trim().split(" ")[0].toLowerCase(Locale.ROOT);
			if (name.contains(":")) {
				name = name.substring(name.indexOf(':') + 1);
			}
			out.add(name);
		}
		return List.copyOf(out);
	}

	/** "1-9", "2", "1,3-4". */
	static boolean inPhases(String phases, int phase) {
		for (String part : phases.split(",")) {
			String p = part.trim();
			try {
				int dash = p.indexOf('-');
				if (dash > 0 ? phase >= Integer.parseInt(p.substring(0, dash).trim()) && phase <= Integer.parseInt(p.substring(dash + 1).trim())
					: phase == Integer.parseInt(p)) {
					return true;
				}
			} catch (NumberFormatException e) {
				return true;
			}
		}
		return false;
	}

	/** The hall list's lines for a keeper: today's phase of each habitat she tends, or why there will be none. */
	public static List<Component> hallLines(Villager keeper) {
		if (!HabitatKeepers.isKeeper(keeper)) {
			return List.of();
		}
		if (needsNewerCobblemon()) {
			return List.of(Component.translatable("screen.aliveworkplace.hall.habitat_needs_18"));
		}
		return ModAttachments.HABITAT_TODAY.getOrElse(keeper, List.of());
	}

	/** Today's day number, for the once-a-day visit. */
	public static long day(ServerLevel level) {
		return level.getDayTime() / 24000L;
	}
}
