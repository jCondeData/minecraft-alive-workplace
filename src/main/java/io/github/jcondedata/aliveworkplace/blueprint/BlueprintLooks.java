package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Looks (ROADMAP 28.7a): the same building drawn again in another shape, not just another palette (that's a
 * {@link BlueprintStyles style}). A look of {@code aliveworkplace:pokemon_center} lives at
 * {@code aliveworkplace:looks/<look>/pokemon_center}, and its upgrade at {@code looks/<look>/pokemon_center_2}, so tiers
 * ({@link BlueprintUpgrades}), styles and the Village Map work for looks unchanged. Everything that asks "is this a
 * Pokémon Center?" goes by {@link #base}, and a village's steward builds the look its village draws ({@link #forVillage}).
 */
public final class BlueprintLooks {
	public static final String PREFIX = "looks/";
	/**
	 * The village cultures a look can be named for (49.10a; {@code VillageWaystones.culture}, plains as drawn): a family
	 * whose looks are all cultures is built in its village's culture ({@link #forVillage(ServerLevel, ResourceLocation,
	 * BlockPos)}), not by the hall hash {@link #pick} uses.
	 */
	public static final List<String> CULTURES = List.of("desert", "savanna", "snowy", "taiga");

	/** The looks drawn for each first-tier blueprint, besides the one as drawn (tools/blueprints/pokemon_looks.py). */
	private static final Map<ResourceLocation, List<String>> LOOKS = Map.of(StarterBlueprints.POKEMON_CENTER.id(), List.of("lodge", "plaza"),
		// 41.1: the Waystone Plaza in each village culture's style, plains as drawn (tools/blueprints/waystones.py)
		io.github.jcondedata.aliveworkplace.hall.VillageWaystones.PLAZA, io.github.jcondedata.aliveworkplace.hall.VillageWaystones.LOOKS,
		// 49.10a: the Starter Cottage in each village culture's climate, every level (tools/blueprints/js/starter_cottage_<culture>)
		StarterBlueprints.STARTER_COTTAGE.id(), CULTURES,
		// 49.10b: the Stone House likewise (tools/blueprints/js/stone_house_<culture>); its snowy look went back to the
		// critic three times and waits for the owner, so a snowy village builds it as drawn
		StarterBlueprints.STONE_HOUSE.id(), List.of("desert", "savanna", "taiga"),
		// 49.10b: the Townhouse likewise (tools/blueprints/js/townhouse_<culture>); its snowy and taiga looks went back to
		// the critic three times and wait for the owner, so those villages build it as drawn
		StarterBlueprints.TOWNHOUSE.id(), List.of("desert", "savanna"),
		// 49.10c: the Terrace likewise (tools/blueprints/js/terrace_<culture>), all four (snowy passed its critic in round 3)
		StarterBlueprints.TERRACE.id(), CULTURES,
		// 49.10d: the Weaver's Cottage (both levels) and the Daub House likewise (tools/blueprints/js/weavers_cottage_<culture>,
		// house_t1_b_<culture>); the cultures each has a look for are listed beside its entry in StarterBlueprints
		StarterBlueprints.WEAVERS_COTTAGE.id(), StarterBlueprints.WEAVERS_COTTAGE_CULTURES,
		StarterBlueprints.DAUB_HOUSE.id(), StarterBlueprints.DAUB_HOUSE_CULTURES);

	/** The blueprint {@code id} is a look of ({@code looks/lodge/pokemon_center_2} is {@code pokemon_center_2}); {@code id} itself if none. */
	public static ResourceLocation base(ResourceLocation id) {
		String path = id.getPath();
		if (!path.startsWith(PREFIX)) {
			return id;
		}
		int slash = path.indexOf('/', PREFIX.length());
		if (slash < 0 || slash == path.length() - 1) {
			return id;
		}
		return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(slash + 1));
	}

	/** The look's name ({@code lodge}), or empty for a blueprint as drawn. */
	public static Optional<String> lookOf(ResourceLocation id) {
		String path = id.getPath();
		int slash = path.indexOf('/', PREFIX.length());
		return path.startsWith(PREFIX) && slash > PREFIX.length() ? Optional.of(path.substring(PREFIX.length(), slash)) : Optional.empty();
	}

	/** {@code base} in the look {@code look} ("" as drawn). */
	public static ResourceLocation look(ResourceLocation base, String look) {
		ResourceLocation plain = base(base);
		return look.isEmpty() ? plain : ResourceLocation.fromNamespaceAndPath(plain.getNamespace(), PREFIX + look + "/" + plain.getPath());
	}

	/** Every look of {@code id} (any tier): as drawn first, then the others in order; just {@code id} when it has none. */
	public static List<ResourceLocation> of(ResourceLocation id) {
		ResourceLocation plain = base(id);
		List<String> looks = LOOKS.get(firstTier(plain));
		if (looks == null) {
			return List.of(id);
		}
		List<ResourceLocation> out = new ArrayList<>();
		out.add(plain);
		looks.forEach(l -> out.add(look(plain, l)));
		return List.copyOf(out);
	}

	/** The look a village at {@code hall} builds of {@code id}: the same one every time for that village, and villages differ. */
	public static ResourceLocation pick(ResourceLocation id, BlockPos hall) {
		List<ResourceLocation> all = of(id);
		long h = hall.asLong() * 0x9E3779B97F4A7C15L;
		h ^= h >>> 31;
		return all.get((int) Math.floorMod(h, (long) all.size()));
	}

	/** Every look of {@code id}, the village's own ({@link #pick}) first: a steward tries the others only where it doesn't fit. */
	public static List<ResourceLocation> forVillage(ResourceLocation id, BlockPos hall) {
		return ordered(pick(id, hall), id);
	}

	/** Whether {@code id}'s family has looks, all of them village cultures ({@link #CULTURES}): built by culture, not by hash. */
	public static boolean byCulture(ResourceLocation id) {
		List<String> looks = LOOKS.get(firstTier(base(id)));
		return looks != null && !looks.isEmpty() && CULTURES.containsAll(looks);
	}

	/**
	 * The look of {@code id} for a village of {@code culture} (49.10a): that culture's look where the family is drawn in
	 * cultures and has it, as drawn for plains (or a culture it has no look for); for other families, {@link #pick}.
	 */
	public static ResourceLocation pick(ResourceLocation id, String culture, BlockPos hall) {
		if (!byCulture(id)) {
			return pick(id, hall);
		}
		ResourceLocation wanted = look(id, CULTURES.contains(culture) ? culture : "");
		return of(id).contains(wanted) ? wanted : base(id);
	}

	/**
	 * Every look of {@code id}, the village's own first: its culture's ({@link #pick(ResourceLocation, String, BlockPos)})
	 * for a family drawn in cultures, else the hall hash's; the others after it, tried only where it doesn't fit.
	 */
	public static List<ResourceLocation> forVillage(ServerLevel level, ResourceLocation id, BlockPos hall) {
		return byCulture(id) ? ordered(pick(id, io.github.jcondedata.aliveworkplace.hall.VillageWaystones.culture(level, hall), hall), id)
			: forVillage(id, hall);
	}

	/** {@code first}, then every other look of {@code id}: as drawn, then the rest in order. */
	private static List<ResourceLocation> ordered(ResourceLocation first, ResourceLocation id) {
		List<ResourceLocation> out = new ArrayList<>();
		out.add(first);
		of(id).stream().filter(l -> !l.equals(first)).forEach(out::add);
		return List.copyOf(out);
	}

	private static ResourceLocation firstTier(ResourceLocation id) {
		ResourceLocation base = id;
		for (int i = 0; i < 100; i++) {
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				break;
			}
			base = lower.get();
		}
		return base;
	}

	private BlueprintLooks() {
	}
}
