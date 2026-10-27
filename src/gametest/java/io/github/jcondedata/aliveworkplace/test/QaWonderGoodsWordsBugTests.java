package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.Objectives;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA bug test (24.5's counted sentences, B407/B568's sweep): a Wonder's goods quest (35.7, quests/daily/wonder_goods.json's
 * bring_wonder_goods) asks for the goods line furthest behind as a Bring step, and the Wonders ask for 2 of several of
 * our luxury goods (colossus, great_cathedral, great_library, great_wall, lighthouse: emerald_brooch, gold_circlet,
 * illuminated_book). Those items have no plural.aliveworkplace key, so the quest board, the journal and the tracking bar
 * read "Bring 2 Emerald Brooch".
 */
public class QaWonderGoodsWordsBugTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderGoodsQuestForTwoLuxuriesReadsPlural(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String[][] cases = {{"aliveworkplace:emerald_brooch", "Bring 2 Emerald Brooches"}, {"aliveworkplace:gold_circlet", "Bring 2 Gold Circlets"},
			{"aliveworkplace:illuminated_book", "Bring 2 Illuminated Books"}};
		for (String[] c : cases) {
			String line = new Objectives.Bring(c[0], 2, "station", Optional.of(BlockPos.ZERO)).line().getString();
			if (!line.equals(c[1])) {
				wrong.add("'" + line + "' (expected '" + c[1] + "')");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
