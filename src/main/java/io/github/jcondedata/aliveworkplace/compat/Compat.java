package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;

/**
 * Turns on the integrations with other mods that are installed. Each lives in {@code compat/<mod>/} — the only code
 * that touches that mod's classes — and fills in the mod's own extension points ({@link
 * io.github.jcondedata.aliveworkplace.work.Extension}); nothing there is loaded unless the mod is. An integration that
 * fails to start (the mod changed its API) is left off with a line in the log; the game carries on without it.
 * A Minecraft version the other mod has no build for doesn't compile its integration at all: the whole of
 * {@code compat/<mod>/} and its lines here are behind {@code //? if <mod>} (stonecutter.gradle.kts).
 */
public final class Compat {
	public static void init() {
		//? if cobblemon {
		if (Platform.get().isModLoaded("cobblemon")) {
			enable("cobblemon", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCompat.TESTED,
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCompat::init);
		}
		//?}
		//? if cobbledollars {
		if (Platform.get().isModLoaded("cobbledollars")) {
			enable("cobbledollars", io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsCompat.TESTED,
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsCompat::init);
		}
		//?}
		// Create's trains for the villages (39.7a): the owner's port in libs/, compiled against, never required.
		if (Platform.get().isModLoaded("create")) {
			enable("create", io.github.jcondedata.aliveworkplace.compat.create.CreateCompat.TESTED,
				io.github.jcondedata.aliveworkplace.compat.create.CreateCompat::init);
		}
		// Raid Dens' running raids, for the raid helpers' blows at the boss (38.1a): read by name, not its classes.
		if (Platform.get().isModLoaded("cobblemonraiddens")) {
			io.github.jcondedata.aliveworkplace.compat.raiddens.RaidDensRaids.init();
		}
		// The questbook: our chapters copied into FTB Quests' config folder (data only, never FTB Quests' classes).
		if (Platform.get().isModLoaded("ftbquests")) {
			io.github.jcondedata.aliveworkplace.compat.ftbquests.FtbQuestsBook.init();
			AliveWorkplace.LOG.info("Alive Workplace: ftbquests {} questbook on", Platform.get().modVersion("ftbquests"));
		}
	}

	/** Starts {@code modId}'s integration, warning when its version is outside {@code tested} (a Fabric version range). */
	static void enable(String modId, String tested, Runnable init) {
		String version = Platform.get().modVersion(modId);
		if (version == null) {
			return;
		}
		try {
			if (!Platform.get().isModLoaded(modId, tested)) {
				AliveWorkplace.LOG.warn("Alive Workplace: {} {} is outside the versions its integration was tested with ({}); trying it anyway",
					modId, version, tested);
			}
		} catch (IllegalArgumentException e) {
			AliveWorkplace.LOG.warn("Alive Workplace: can't read the tested range {} for {}", tested, modId);
		}
		try {
			init.run();
			AliveWorkplace.LOG.info("Alive Workplace: {} {} integration on", modId, version);
		} catch (LinkageError | RuntimeException e) {
			AliveWorkplace.LOG.error("Alive Workplace: the {} integration is off: that mod isn't what this version was built against", modId, e);
		}
	}

	private Compat() {
	}
}
