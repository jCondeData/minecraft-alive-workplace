package io.github.jcondedata.aliveworkplace.platform.fabric;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.Compat;
import net.fabricmc.api.ModInitializer;

/** Fabric's entrypoint ({@code fabric.mod.json}): starts the mod, with other mods' integrations at their usual point. */
public final class AliveWorkplaceFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		AliveWorkplace.init(Compat::init);
	}
}
