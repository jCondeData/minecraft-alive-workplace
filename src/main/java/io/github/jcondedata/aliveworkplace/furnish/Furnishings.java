package io.github.jcondedata.aliveworkplace.furnish;

import io.github.jcondedata.aliveworkplace.Expansions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Furnished homes (ROADMAP Milestone 37, design note {@code docs/design/M37.md}): builds swap their vanilla furniture
 * for the pieces of the pack's furniture mods (Handcrafted, Lucky's Cozy Home, CobbleFurnies, Beautify) when those
 * mods are installed, by block id only. Set from the config ({@code furnishedBuilds}, {@code furnitureMods}) behind
 * {@link Expansions#M37}; with the switch off, or none of the mods installed, every build is the plain vanilla build.
 */
public final class Furnishings {
	/**
	 * The furniture mods' pieces that hold items (Handcrafted's counters, desks, shelves..., Cozy Home's storage counters,
	 * CobbleFurnies' cabinets): with furnished builds on they are decoration, so no job reads them as chests (37.5).
	 */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> FURNITURE_STORAGE =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("furniture_storage"));

	/** The order the furniture mods are tried in when a piece is chosen (owner's default, M37 design note). */
	public static final List<String> DEFAULT_MODS = List.of("handcrafted", "cozyhome", "cobblefurnies", "beautify");

	/** Builds get modded furniture (config {@code furnishedBuilds}, gated by {@link Expansions#M37}). */
	public static volatile boolean ENABLED = Expansions.on(Expansions.M37);
	/** The mod namespaces tried in order (config {@code furnitureMods}); a mod that isn't installed is passed over. */
	public static volatile List<String> MODS = DEFAULT_MODS;

	private Furnishings() {
	}

	/**
	 * {@code furnitureMods} as the config file holds it, cleaned: each entry trimmed and lower-cased, blanks and repeats
	 * dropped, the defaults for a missing list. An empty list is kept (no mod is tried: every build stays vanilla).
	 */
	public static List<String> cleanMods(List<String> mods) {
		if (mods == null) {
			return new ArrayList<>(DEFAULT_MODS);
		}
		List<String> clean = new ArrayList<>();
		for (String mod : mods) {
			String name = mod == null ? "" : mod.trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && !clean.contains(name)) {
				clean.add(name);
			}
		}
		return clean;
	}
}
