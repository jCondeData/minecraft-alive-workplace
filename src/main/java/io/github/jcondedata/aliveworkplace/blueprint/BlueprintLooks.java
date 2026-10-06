package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Looks (ROADMAP 28.7a): the same building drawn again in another shape, not just another palette (that's a
 * {@link BlueprintStyles style}). A look of {@code aliveworkplace:pokemon_center} lives at
 * {@code aliveworkplace:looks/<look>/pokemon_center}, and its upgrade at {@code looks/<look>/pokemon_center_2}, so tiers
 * ({@link BlueprintUpgrades}), styles and the Village Map work for looks unchanged. Everything that asks "is this a
 * Pokémon Center?" goes by {@link #base}, and a village's steward builds the look its village draws ({@link #forVillage}).
 */
public final class BlueprintLooks {
	public static final String PREFIX = "looks/";

	/** The looks drawn for each first-tier blueprint, besides the one as drawn (tools/blueprints/pokemon_looks.py). */
	private static final Map<ResourceLocation, List<String>> LOOKS = Map.of(StarterBlueprints.POKEMON_CENTER.id(), List.of("lodge", "plaza"));

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
		ResourceLocation first = pick(id, hall);
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
