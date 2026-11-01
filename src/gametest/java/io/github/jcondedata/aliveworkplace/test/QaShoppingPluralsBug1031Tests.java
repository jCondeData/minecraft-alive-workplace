package io.github.jcondedata.aliveworkplace.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Bug test (qa-lane-b-1031-1238) on B698's fix: the shopping list now counts in the plural only for items the lang has a
 * {@code plural.aliveworkplace.*} name for (about 50 vanilla items, mostly food, tools and workstations), so materials the
 * mod's own blueprints use (spruce logs, glass panes, ladders, oak doors) still read in the singular: "12 Spruce Log,
 * 6 Glass Pane". B698's Expected is the plural on the desk, the board and the daily message.
 */
public class QaShoppingPluralsBug1031Tests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Building materials from the mod's blueprints, each needed more than once, read in the plural on the desk and board. */
	//$ gametest_ticks_batch AREA '40' '"qaShopPluralMaterials"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaShopPluralMaterials")
	public void blueprintMaterialsReadInThePlural(GameTestHelper helper) {
		QaShoppingPlurals1031GameTests.Village v = QaShoppingPlurals1031GameTests.village(helper, helper.makeMockServerPlayerInLevel());
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.SPRUCE_LOG, 12);
		missing.put(Items.GLASS_PANE, 6);
		missing.put(Items.LADDER, 4);
		missing.put(Items.OAK_DOOR, 2);
		QaShoppingPlurals1031GameTests.waitingSite(helper, v, missing);
		String line = "Shopping list: 12 Spruce Logs, 6 Glass Panes, 4 Ladders, 2 Oak Doors";
		List<String> desk = QaShoppingPlurals1031GameTests.deskLines(v);
		helper.assertTrue(desk.contains(line), "the desk doesn't read \"" + line + "\": " + desk);
		List<String> board = QaShoppingPlurals1031GameTests.boardLines(helper, v);
		List<String> expected = List.of("12 Spruce Logs", "6 Glass Panes", "4 Ladders", "2 Oak Doors");
		helper.assertTrue(board.equals(expected), "the Storehouse's board lists " + board + ", not " + expected);
		helper.succeed();
	}
}
