package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA for 36.22: the "down to just above bedrock" marker says it is "for Master Miners only", and the spec gives Master
 * that choice ("Expert 128, Master down to just above bedrock"). A Novice handed it over a pit 100 blocks deep must then
 * be told that digging all of it needs Master, the level the marker named, not a lower one worked out from the depth.
 */
public class QaDeepQuarryWordsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch AREA '400' '"qaDeepQuarryWords"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaDeepQuarryWords")
	public void aBedrockMarkerNeedsTheMasterItNames(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		int floor = d.level().getMinBuildHeight() + 5;
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		int top = bottom + 99;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		String promised = QuarryMarkerItem.depthChoice(QuarryData.BEDROCK, "tooltip.aliveworkplace.quarry.depth").getString();
		helper.assertTrue(promised.contains("Master"), "the marker's tooltip: " + promised);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().getYSpan() == 64 && site.wantsDeeper(), "a Novice should stop 64 down: " + site.box());
		Component told = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.too_deep");
		helper.assertTrue(told != null, "not told: " + QaStageFourGameTests.keys(d.seen()));
		helper.assertTrue(told.getString().contains("Master"),
			"the marker said '" + promised + "', but the player was told: '" + told.getString() + "'");
		helper.succeed();
	}

	/**
	 * B649: an Expert handed the bedrock marker over a pit 100 blocks deep (within an Expert's 128) still doesn't dig
	 * all of it with no word: they stop one layer short, the owner reads that all 100 blocks need a Master, word for
	 * word, and leveling up to Expert again (deepen) takes it no lower; only a Master's level reaches the bottom.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaDeepQuarryWordsExpert"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaDeepQuarryWordsExpert")
	public void anExpertStopsShortOfABedrockPitAndIsToldMaster(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 4);
		int floor = d.level().getMinBuildHeight() + 5;
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		int top = bottom + 99;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().getYSpan() == 99 && site.box().minY() == floor + 1 && site.wantsDeeper(),
			"an Expert should stop just above the bedrock pit's last layer: " + site.box());
		Component told = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.too_deep");
		helper.assertTrue(told != null, "not told: " + QaStageFourGameTests.keys(d.seen()));
		String expected = d.miner().getDisplayName().getString() + " is at Expert level, so they'll stop 99 blocks down for now. "
			+ "Digging all 100 blocks needs Master level; if they get there before they finish, they'll keep going down.";
		helper.assertTrue(told.getString().equals(expected), "expected '" + expected + "', told '" + told.getString() + "'");
		helper.assertTrue(!site.deepenTo(QuarryData.lowestFor(4, site.box().maxY(), site.wantedBottom(), site.depth())),
			"an Expert takes a bedrock pit no deeper: " + site.box());
		helper.assertTrue(site.deepenTo(QuarryData.lowestFor(5, site.box().maxY(), site.wantedBottom(), site.depth()))
			&& site.box().minY() == floor && !site.wantsDeeper(), "a Master takes it to the bottom: " + site.box());
		helper.succeed();
	}
}
