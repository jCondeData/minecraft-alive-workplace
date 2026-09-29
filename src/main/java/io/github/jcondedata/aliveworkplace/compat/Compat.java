package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Turns on the integrations with other mods that are installed. Each lives in {@code compat/<mod>/} — the only code
 * that touches that mod's classes — and fills in the mod's own extension points ({@link
 * io.github.jcondedata.aliveworkplace.work.Extension}); nothing there is loaded unless the mod is. An integration that
 * fails to start (the mod changed its API) is left off with a line in the log; the game carries on without it.
 */
public final class Compat {
	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
			enable("cobblemon", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCompat.TESTED,
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCompat::init);
		}
		if (FabricLoader.getInstance().isModLoaded("cobbledollars")) {
			enable("cobbledollars", io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsCompat.TESTED,
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsCompat::init);
		}
	}

	/** Starts {@code modId}'s integration, warning when its version is outside {@code tested} (a Fabric version range). */
	static void enable(String modId, String tested, Runnable init) {
		FabricLoader.getInstance().getModContainer(modId).ifPresent(mod -> {
			Version version = mod.getMetadata().getVersion();
			try {
				if (!VersionPredicate.parse(tested).test(version)) {
					AliveWorkplace.LOG.warn("Alive Workplace: {} {} is outside the versions its integration was tested with ({}); trying it anyway",
						modId, version.getFriendlyString(), tested);
				}
			} catch (VersionParsingException e) {
				AliveWorkplace.LOG.warn("Alive Workplace: can't read the tested range {} for {}", tested, modId);
			}
			try {
				init.run();
				AliveWorkplace.LOG.info("Alive Workplace: {} {} integration on", modId, version.getFriendlyString());
			} catch (LinkageError | RuntimeException e) {
				AliveWorkplace.LOG.error("Alive Workplace: the {} integration is off: that mod isn't what this version was built against", modId, e);
			}
		});
	}

	private Compat() {
	}
}
