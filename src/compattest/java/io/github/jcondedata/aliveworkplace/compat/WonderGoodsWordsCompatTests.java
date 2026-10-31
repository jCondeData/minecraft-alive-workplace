package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

/**
 * B675: the Stadium's goods quest asks for 2 Great Balls and 2 Ultra Balls (wonders/stadium.json), and read "Bring 2
 * Great Ball" because the lang had no plural for them. These read the quest line a player sees on the board, in the
 * journal and on the tracking bar, for the Stadium's balls and for every modded good any Wonder asks more than one of.
 */
public class WonderGoodsWordsCompatTests implements FabricGameTest {
	private static String bring(String item, int count) {
		return new Objectives.Bring(item, count, "station", Optional.of(BlockPos.ZERO)).line().getString();
	}

	/** The Stadium's goods quest asks for 2 Great Balls and 2 Ultra Balls in the plural, and for 1 in the singular. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumGoodsQuestForTwoBallsReadsPlural(GameTestHelper helper) {
		helper.assertTrue(Wonders.loaded().containsKey(StadiumDays.STADIUM), "the Stadium's file didn't load with Cobblemon");
		List<String> wrong = new ArrayList<>();
		String[][] cases = {{"cobblemon:great_ball", "Great Balls", "Great Ball"}, {"cobblemon:ultra_ball", "Ultra Balls", "Ultra Ball"}};
		for (String[] c : cases) {
			String two = bring(c[0], 2);
			String one = bring(c[0], 1);
			if (!two.equals("Bring 2 " + c[1])) {
				wrong.add("\"" + two + "\" (want \"Bring 2 " + c[1] + "\")");
			}
			if (!one.equals("Bring 1 " + c[2])) {
				wrong.add("\"" + one + "\" (want \"Bring 1 " + c[2] + "\")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Every modded good (not Minecraft's own) that a loaded Wonder asks more than one of has a plural key, so its quest
	 * line never falls back to the singular (a name that is already plural, such as "Noble Robes", has one too).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyModdedWonderGoodAskedInManyReadsPlural(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Wonder wonder : Wonders.loaded().values()) {
			for (Wonder.Stage stage : wonder.stages()) {
				for (Wonder.Good good : stage.goods()) {
					if (good.item().isEmpty() || good.count() <= 1 || good.item().get().getNamespace().equals("minecraft")) {
						continue;
					}
					ResourceLocation id = good.item().get();
					checked++;
					if (!Language.getInstance().has("plural.aliveworkplace." + id.getNamespace() + "." + id.getPath())) {
						wrong.add(wonder.id() + " asks " + good.count() + " " + id + ": \"" + bring(id.toString(), good.count()) + "\"");
					}
				}
			}
		}
		helper.assertTrue(checked >= 2, "setup: only " + checked + " modded goods asked in more than one (the Stadium alone asks 2)");
		helper.assertTrue(wrong.isEmpty(), "no plural: " + String.join("; ", wrong));
		helper.succeed();
	}
}
