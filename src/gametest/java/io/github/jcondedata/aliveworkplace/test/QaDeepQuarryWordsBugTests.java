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
}
