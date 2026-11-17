package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-c-1117-0953): Daub House claims the mod doesn't meet yet. Each test fails today; the builder commits it with
 * the fix (or the wiki changes, if the owner keeps the mod's way).
 */
public class QaDaubHouse1117cBugTests implements FabricGameTest {
	/**
	 * docs/wiki/daub-house.md, "In villages": "desert: sandstone on a sandstone plinth, jungle wood, a red sandstone
	 * roof". Every other style swaps the mud-brick daub panels (savanna white terracotta, snowy calcite, taiga spruce
	 * boards), but the desert piece keeps all 113 mud-brick panels: its walls are mud brick, not sandstone.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void desertDaubHouseWallsAreSandstoneNotMudBrick(GameTestHelper helper) {
		Set<String> names = QaDaubHouse1117cGameTests.names(QaDaubHouse1117cGameTests.cells(QaDaubHouse1117cGameTests.template(helper,
			VillageHouses.home("desert", QaDaubHouse1117cGameTests.DAUB.id().getPath()))));
		helper.assertTrue(!names.contains("mud_bricks"), "the desert Daub House's daub walls are still mud bricks, not sandstone");
		helper.succeed();
	}
}
