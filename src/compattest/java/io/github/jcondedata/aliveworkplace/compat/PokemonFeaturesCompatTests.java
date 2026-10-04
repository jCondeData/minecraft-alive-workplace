package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP 28.2: the parts of Cobblemon that came in 1.8 are off under the pack's 1.7.3 and on under 1.8.1
 * ({@code -Pcobblemon18=true}), and the integration itself is on with either.
 */
public class PokemonFeaturesCompatTests implements FabricGameTest {
	private static boolean on18() {
		return Platform.get().isModLoaded("cobblemon", ">=1.8.0");
	}

	/** Habitats, Type Gems, the TM Machine and Alphas follow the installed Cobblemon: all off on 1.7, all on from 1.8. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFourFeaturesFollowTheCobblemonVersion(GameTestHelper helper) {
		boolean on = on18();
		String version = Platform.get().modVersion("cobblemon");
		for (PokemonFeatures feature : PokemonFeatures.values()) {
			helper.assertTrue(feature.available() == on,
				feature + " is " + (feature.available() ? "on" : "off") + " with Cobblemon " + version);
			if (feature.blockId() != null) {
				helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(feature.blockId()) == on,
					feature.blockId() + " registered: " + BuiltInRegistries.BLOCK.containsKey(feature.blockId()) + " with Cobblemon " + version);
			}
		}
		helper.succeed();
	}

	/** The installed Cobblemon is inside the tested range, so the integration starts without a warning. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theInstalledCobblemonIsATestedVersion(GameTestHelper helper) {
		String tested = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCompat.TESTED;
		helper.assertTrue(Platform.get().isModLoaded("cobblemon", tested),
			"Cobblemon " + Platform.get().modVersion("cobblemon") + " is outside " + tested);
		helper.succeed();
	}
}
