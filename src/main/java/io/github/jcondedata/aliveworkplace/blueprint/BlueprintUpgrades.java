package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * Upgrades go by name: a blueprint called {@code <name>_2} upgrades {@code <name>}, {@code <name>_3} upgrades
 * {@code <name>_2}, and so on — the Starter Cottage's upgrade is {@code starter_cottage_2}, and a player's own
 * {@code my_house_2.litematic} upgrades their {@code my_house}. An upgrade shares its base's origin and front, so
 * it's built right over the finished base (only what changed gets built).
 */
public final class BlueprintUpgrades {
	private static final Pattern TIER = Pattern.compile("^(.+)_(\\d+)$");

	/** The blueprint {@code id} upgrades, going by its name (it may not exist). */
	public static Optional<ResourceLocation> baseOf(ResourceLocation id) {
		Matcher m = TIER.matcher(id.getPath());
		if (!m.matches()) {
			return Optional.empty();
		}
		int tier = Integer.parseInt(m.group(2));
		if (tier < 2 || tier > 99) {
			return Optional.empty();
		}
		String base = tier == 2 ? m.group(1) : m.group(1) + "_" + (tier - 1);
		return Optional.of(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), base));
	}

	/** The name of the blueprint that would upgrade {@code id} ({@code <name>_2}, or the next tier; it may not exist). */
	public static ResourceLocation upgradeOf(ResourceLocation id) {
		Matcher m = TIER.matcher(id.getPath());
		if (m.matches()) {
			int tier = Integer.parseInt(m.group(2));
			if (tier >= 2 && tier < 99) {
				return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), m.group(1) + "_" + (tier + 1));
			}
		}
		return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_2");
	}

	private BlueprintUpgrades() {
	}
}
