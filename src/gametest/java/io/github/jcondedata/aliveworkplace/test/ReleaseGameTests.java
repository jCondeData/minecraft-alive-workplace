package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** What a player or a bug report sees of the release itself (ROADMAP 26.3). */
public class ReleaseGameTests implements FabricGameTest {
	/**
	 * The mod's version (Mod Menu, crash reports, the bug form) reads like the jar's name, {@code <version>+<minecraft>},
	 * for example 1.0.0+1.21.1, and the loader reads it as a proper version, so other mods can depend on a range of it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theModsVersionNamesItsMinecraftVersion(GameTestHelper helper) {
		String version = Platform.get().modVersion("aliveworkplace");
		String minecraft = Platform.get().modVersion("minecraft");
		helper.assertTrue(version != null && minecraft != null, "versions: mod " + version + ", minecraft " + minecraft);
		helper.assertTrue(version.endsWith("+" + minecraft), "the mod's version " + version + " should end in +" + minecraft);
		String release = version.substring(0, version.length() - minecraft.length() - 1);
		helper.assertTrue(release.matches("\\d+\\.\\d+\\.\\d+"), "the release part should be X.Y.Z, not " + release);
		helper.assertTrue(Platform.get().isModLoaded("aliveworkplace", ">=" + release),
			"the loader should read " + version + " as version " + release);
		helper.assertTrue(!Platform.get().isModLoaded("aliveworkplace", ">" + release),
			"build metadata (+" + minecraft + ") must not count as a newer version than " + release);
		helper.succeed();
	}
}
