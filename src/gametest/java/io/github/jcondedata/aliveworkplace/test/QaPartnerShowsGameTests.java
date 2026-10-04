package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (qa-1004-1033) for 28.3's show data: a show's length is "10 to 400" ticks, as the reason a pack maker reads
 * says, and both ends are allowed.
 */
public class QaPartnerShowsGameTests implements FabricGameTest {
	private static String show(int ticks) {
		return "{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"],\"cue\":\"fetch\",\"ticks\":" + ticks + "}";
	}

	/** 10 and 400 ticks are shows; 9 and 401 are refused, and the reason names the range. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAShowLastsTenToFourHundredTicksBothEndsIncluded(GameTestHelper helper) {
		for (int ok : new int[] {10, 400}) {
			PartnerShows.Show s = PartnerShows.parse(AliveWorkplace.id("qa_" + ok), JsonParser.parseString(show(ok)).getAsJsonObject());
			helper.assertTrue(s.ticks() == ok, "a show of " + ok + " ticks reads " + s.ticks());
		}
		for (int bad : new int[] {9, 401, 0, -1}) {
			try {
				PartnerShows.parse(AliveWorkplace.id("qa_bad"), JsonParser.parseString(show(bad)).getAsJsonObject());
				helper.fail("accepted a show of " + bad + " ticks");
			} catch (IllegalArgumentException e) {
				helper.assertTrue(e.getMessage().contains("ticks") && e.getMessage().contains("10 to 400"),
					"the reason for a show of " + bad + " ticks reads '" + e.getMessage() + "'");
			}
		}
		helper.succeed();
	}
}
