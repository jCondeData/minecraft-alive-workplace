//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.berry.Berries;
import com.cobblemon.mod.common.api.berry.Berry;
import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

// Cobblemon's berry data for the Berry Breeder (ROADMAP 28.9): every berry and its mutations, read from the loaded
// berry registry, so a data pack's berries come too. Only touched when Cobblemon is installed.
public final class CobblemonBerries implements BerryChains.BerryData {
	@Override
	public List<ResourceLocation> berries() {
		List<ResourceLocation> ids = new ArrayList<>();
		for (Berry berry : Berries.all()) {
			ids.add(berry.getIdentifier());
		}
		return ids;
	}

	@Override
	public List<BerryChains.Mutation> mutations() {
		List<BerryChains.Mutation> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Berry berry : Berries.all()) {
			for (Map.Entry<ResourceLocation, ResourceLocation> e : berry.getMutations().entrySet()) {
				ResourceLocation a = berry.getIdentifier();
				ResourceLocation b = e.getKey();
				// each pair once, whichever berry lists it
				String key = a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
				if (seen.add(key)) {
					out.add(a.compareTo(b) <= 0 ? new BerryChains.Mutation(a, b, e.getValue()) : new BerryChains.Mutation(b, a, e.getValue()));
				}
			}
		}
		return out;
	}
}
//?}
