package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane b, 2026-11-14), the bug tests for B1239 (B1195's other door). B1195 refused a scan holding nothing the
 * Blueprint Table's preview draws (only water, lava or a bubble column), because the Table listed it with an empty
 * picture. A file uploaded at the Table goes through BlueprintImporter instead, which still refuses only a file with no
 * non-air block: a .nbt holding only water or only lava is imported, listed, and previews as nothing. Expected (as
 * B1195): refused like an empty file ("Could not import ...: ..."), nothing listed. The other side passes on main
 * (QaUploadPreview1114bGameTests: a chest alone, and stone with water, still import).
 */
public class QaUploadPreview1114bBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qaUploadWater1114b"'
	@GameTest(template = AREA, batch = "qaUploadWater1114b")
	public void anUploadedPondOfWaterIsRefused(GameTestHelper helper) {
		QaUploadPreview1114bGameTests.assertRefused(helper, Blocks.WATER, "water");
	}

	//$ gametest_batch AREA '"qaUploadLava1114b"'
	@GameTest(template = AREA, batch = "qaUploadLava1114b")
	public void anUploadedLavaPoolIsRefused(GameTestHelper helper) {
		QaUploadPreview1114bGameTests.assertRefused(helper, Blocks.LAVA, "lava");
	}
}
