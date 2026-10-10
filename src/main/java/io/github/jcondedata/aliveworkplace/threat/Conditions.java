package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

/**
 * The shared toolbox of conditions on a village (ROADMAP 32.2; the disasters of 32.15 use it too): where a raider
 * culture comes. Every key is optional and all given must hold:
 * <ul>
 * <li>{@code biomes}: the hall's biome is one of these ids, or in one of these tags ({@code #minecraft:is_forest});</li>
 * <li>{@code coast}: an ocean or beach biome within {@link #COAST} blocks of the hall;</li>
 * <li>{@code nether_link}: a lit Nether portal or a finished Nether Gate in the hall's area;</li>
 * <li>{@code min_rank}: the village's rank is at least this ({@code hamlet}, {@code village}, {@code town}, {@code city});</li>
 * <li>{@code min_villagers}: the village has at least this many villagers.</li>
 * </ul>
 * A key the toolbox doesn't know is logged and ignored.
 */
public record Conditions(List<String> biomes, boolean coast, boolean netherLink, Optional<VillageRanks.Rank> minRank, int minVillagers) {
	/** No conditions: holds everywhere. */
	public static final Conditions ANY = new Conditions(List.of(), false, false, Optional.empty(), 0);
	/** How far from the hall the sea may be for {@code coast}. */
	public static final int COAST = 48;
	private static final Set<String> KEYS = Set.of("biomes", "coast", "nether_link", "min_rank", "min_villagers");

	/** Reads the conditions in {@code json}; throws {@link IllegalArgumentException} naming what's wrong. */
	public static Conditions read(JsonObject json, String file) {
		for (String key : json.keySet()) {
			if (!KEYS.contains(key)) {
				AliveWorkplace.LOG.warn("{}: the condition '{}' isn't known and is ignored", file, key);
			}
		}
		List<String> biomes = new ArrayList<>();
		if (json.has("biomes")) {
			for (JsonElement e : json.getAsJsonArray("biomes")) {
				String biome = e.getAsString();
				if (ResourceLocation.tryParse(biome.startsWith("#") ? biome.substring(1) : biome) == null) {
					throw new IllegalArgumentException("'" + biome + "' is no biome id or #tag");
				}
				biomes.add(biome);
			}
		}
		Optional<VillageRanks.Rank> rank = Optional.empty();
		if (json.has("min_rank")) {
			String name = json.get("min_rank").getAsString();
			try {
				rank = Optional.of(VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("min_rank must be hamlet, village, town or city, not " + name);
			}
		}
		int villagers = json.has("min_villagers") ? json.get("min_villagers").getAsInt() : 0;
		if (villagers < 0) {
			throw new IllegalArgumentException("min_villagers can't be " + villagers);
		}
		return new Conditions(List.copyOf(biomes), json.has("coast") && json.get("coast").getAsBoolean(),
			json.has("nether_link") && json.get("nether_link").getAsBoolean(), rank, villagers);
	}

	/**
	 * Whether every condition holds for the village round {@code hall}; {@code villagers} is its head count (asked for only
	 * when a condition needs it). The cheap checks come first.
	 */
	public boolean test(ServerLevel level, BlockPos hall, IntSupplier villagers) {
		if (minRank.isPresent() && VillageRanks.of(level, hall).ordinal() < minRank.get().ordinal()) {
			return false;
		}
		if (minVillagers > 0 && villagers.getAsInt() < minVillagers) {
			return false;
		}
		if (!biomes.isEmpty() && !inBiome(level.getBiome(hall))) {
			return false;
		}
		if (coast && !coast(level, hall)) {
			return false;
		}
		return !netherLink || netherLink(level, hall).isPresent();
	}

	private boolean inBiome(Holder<Biome> biome) {
		for (String entry : biomes) {
			if (entry.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(entry.substring(1))))
				: biome.is(ResourceLocation.parse(entry))) {
				return true;
			}
		}
		return false;
	}

	/** An ocean or beach biome within {@link #COAST} blocks of {@code hall} (every 16 blocks; no chunk is loaded for it). */
	public static boolean coast(ServerLevel level, BlockPos hall) {
		for (int dx = -COAST; dx <= COAST; dx += 16) {
			for (int dz = -COAST; dz <= COAST; dz += 16) {
				Holder<Biome> biome = level.getBiome(hall.offset(dx, 0, dz));
				if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The village's way to the Nether: the nearest lit Nether portal in the hall's area, or else a finished Nether Gate
	 * there (its origin); empty when it has neither.
	 */
	public static Optional<BlockPos> netherLink(ServerLevel level, BlockPos hall) {
		Optional<BlockPos> portal = level.getPoiManager().findClosest(h -> h.is(PoiTypes.NETHER_PORTAL), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.filter(p -> !level.isLoaded(p) || level.getBlockState(p).is(Blocks.NETHER_PORTAL));
		if (portal.isPresent()) {
			return portal;
		}
		ResourceLocation gate = StarterBlueprints.NETHER_GATE.id();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			ResourceLocation id = BlueprintStyles.base(f.structure());
			boolean upgrade = BlueprintUpgrades.tier(id) > 1 && id.getNamespace().equals(gate.getNamespace()) && id.getPath().startsWith(gate.getPath() + "_");
			if (id.equals(gate) || upgrade) {
				return Optional.of(f.placement().origin());
			}
		}
		return Optional.empty();
	}
}
