package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * QA (qa-lane-c-1031-1553) verifying B649 from the 36.22 spec and the 2026-10-31 decision: the "down to just above
 * bedrock" Quarry Marker needs a Master however deep the pit turns out, and the marker's own words (cycle line,
 * tooltip) and the miner's words name the same level as what is dug. Boundaries from both sides: a bedrock pit no
 * deeper than a Novice's 64 still stops above its last layer below Master, a Master digs all of a 128-deep one with no
 * word, and the 96 and 128 markers are dug whole by the level they name and refused (cut at the
 * miner's own depth, told the right level) one level below. Same layout as {@link QaDeepQuarryGameTests}.
 */
public class QaQuarry1031VerifyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TOO_DEEP = "message.aliveworkplace.quarry.too_deep";
	private static final String TAIL = "; if they get there before they finish, they'll keep going down.";

	/**
	 * The player sneak-uses the marker in the air (the item's own use) from 16 all the way round: each line names the
	 * level the choice needs, word for word, the bedrock one a Master; the bedrock marker's tooltip says Master too; no
	 * line or tooltip shows a raw key; and the round comes back to 16.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaQuarry1031Cycle"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaQuarry1031Cycle")
	public void theMarkersCycleNamesEachDeepChoicesLevel(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		ItemStack stack = new ItemStack(ModItems.QUARRY_MARKER);
		d.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
		d.player().setShiftKeyDown(true);
		List<String> expected = List.of("Dig 32 blocks deep.", "Dig 64 blocks deep.", "Dig 96 blocks deep (Journeyman Miners and up).",
			"Dig 128 blocks deep (Expert Miners and up).", "Dig down to just above bedrock (Master Miners only).");
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			int before = d.seen().size();
			d.player().getItemInHand(InteractionHand.MAIN_HAND).use(d.level(), d.player(), InteractionHand.MAIN_HAND);
			helper.assertTrue(d.seen().size() > before, "press " + (i + 1) + " showed nothing");
			String line = d.seen().get(d.seen().size() - 1).getString();
			helper.assertTrue(!line.contains("aliveworkplace.") && !line.contains("merchant.level"), "a raw key on press " + (i + 1) + ": " + line);
			lines.add(line);
			ItemStack now = d.player().getItemInHand(InteractionHand.MAIN_HAND);
			if (i == 4) {
				helper.assertTrue(now.get(ModComponents.QUARRY).depth() == QuarryData.BEDROCK, "press 5 isn't bedrock: " + now.get(ModComponents.QUARRY));
				String tip = tooltip(d, now);
				helper.assertTrue(tip.contains("Down to just above bedrock, for Master Miners only"), "the bedrock tooltip: " + tip);
			}
			String tip = tooltip(d, now);
			helper.assertTrue(!tip.contains("aliveworkplace.") && !tip.contains("merchant.level"), "a raw key in the tooltip after press " + (i + 1) + ": " + tip);
		}
		helper.assertTrue(lines.subList(0, 5).equals(expected), "the cycle read " + lines.subList(0, 5) + ", expected " + expected);
		int back = d.player().getItemInHand(InteractionHand.MAIN_HAND).get(ModComponents.QUARRY).depth();
		helper.assertTrue(back == QuarryData.DEFAULT_DEPTH, "12 presses (16 to bedrock, the strip mine and its three levels, 4, 8) should come back to 16, at " + back + ": " + lines);
		helper.succeed();
	}

	/**
	 * B649, the far side: a bedrock pit only 64 blocks deep, all of it within a Novice's own depth. The marker named a
	 * Master, so the Novice still stops one layer above the bottom and reads, word for word, that all 64 need a Master.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031ShallowBedrock"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031ShallowBedrock")
	public void aNoviceOverAShallowBedrockPitStopsAboveItsLastLayer(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		int floor = d.level().getMinBuildHeight() + 5;
		int top = helper.relativePos(new BlockPos(0, floor, 0)).getY() + 63;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().getYSpan() == 63 && site.box().minY() == floor + 1 && site.wantsDeeper(),
			"a Novice should stop just above a 64-deep bedrock pit's last layer: " + site.box());
		told(helper, d, d.miner().getDisplayName().getString() + " is at Novice level, so they'll stop 63 blocks down for now. "
			+ "Digging all 64 blocks needs Master level" + TAIL);
		helper.succeed();
	}

	/**
	 * The level the bedrock marker names digs it: a Master over a bedrock pit 128 deep (the deepest the test area's bench
	 * reaches; an Expert would stop at 127) takes all of it, no word.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031MasterBedrock"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031MasterBedrock")
	public void aMasterTakesAWholeBedrockPitWithNoWordOfLevels(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 5);
		int floor = d.level().getMinBuildHeight() + 5;
		int top = helper.relativePos(new BlockPos(0, floor, 0)).getY() + 127;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().getYSpan() == 128 && site.box().minY() == floor && !site.wantsDeeper(),
			"a Master should take the whole 128-deep bedrock pit: " + site.box());
		silent(helper, d);
		helper.succeed();
	}

	/** 128 at its limit: an Expert digs all 128 with no word. */
	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031Expert128"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031Expert128")
	public void anExpertDigsAWhole128Pit(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 4);
		int top = helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY() + 127;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 128));
		helper.assertTrue(site.box().getYSpan() == 128 && !site.wantsDeeper(), "an Expert should take all 128: " + site.box());
		silent(helper, d);
		helper.succeed();
	}

	/** 128 one level below: a Journeyman stops at their own 96 and reads, word for word, that all 128 need an Expert, as the marker said. */
	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031Journeyman128"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031Journeyman128")
	public void aJourneymanHandedA128StopsAt96AndIsToldExpert(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 3);
		int top = helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY() + 127;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		String promised = QuarryMarkerItem.depthChoice(128, "tooltip.aliveworkplace.quarry.depth").getString();
		helper.assertTrue(promised.contains("for Expert Miners and up"), "the 128 marker's tooltip: " + promised);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 128));
		helper.assertTrue(site.box().getYSpan() == 96 && site.box().maxY() == helper.absolutePos(new BlockPos(0, top, 0)).getY() && site.wantsDeeper(),
			"a Journeyman should stop 96 down from the top: " + site.box());
		told(helper, d, d.miner().getDisplayName().getString() + " is at Journeyman level, so they'll stop 96 blocks down for now. "
			+ "Digging all 128 blocks needs Expert level" + TAIL);
		helper.succeed();
	}

	/** 96 from both sides: a Journeyman digs all 96 with no word; an Apprentice stops at 64 and is told Journeyman. */
	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031Apprentice96"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031Apprentice96")
	public void anApprenticeHandedA96StopsAt64AndIsToldJourneyman(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 2);
		int top = helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY() + 95;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 96));
		helper.assertTrue(site.box().getYSpan() == 64 && site.wantsDeeper(), "an Apprentice should stop 64 down: " + site.box());
		told(helper, d, d.miner().getDisplayName().getString() + " is at Apprentice level, so they'll stop 64 blocks down for now. "
			+ "Digging all 96 blocks needs Journeyman level" + TAIL);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '400' '"qaQuarry1031Journeyman96"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaQuarry1031Journeyman96")
	public void aJourneymanDigsAWhole96Pit(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 3);
		int top = helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY() + 95;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 96));
		helper.assertTrue(site.box().getYSpan() == 96 && !site.wantsDeeper(), "a Journeyman should take all 96: " + site.box());
		silent(helper, d);
		helper.succeed();
	}

	private static String tooltip(QaDeepQuarryGameTests.Deep d, ItemStack stack) {
		List<Component> tip = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.of(d.level()), tip, TooltipFlag.NORMAL);
		return String.join(" | ", tip.stream().map(Component::getString).toList());
	}

	private static void told(GameTestHelper helper, QaDeepQuarryGameTests.Deep d, String expected) {
		Component told = QaStageFourGameTests.find(d.seen(), TOO_DEEP);
		helper.assertTrue(told != null, "not told: " + QaStageFourGameTests.keys(d.seen()));
		helper.assertTrue(told.getString().equals(expected), "expected '" + expected + "', told '" + told.getString() + "'");
	}

	/** The miner took the pit with no word about levels, and every line the player read is rendered (no raw key). */
	private static void silent(GameTestHelper helper, QaDeepQuarryGameTests.Deep d) {
		helper.assertTrue(QaStageFourGameTests.find(d.seen(), TOO_DEEP) == null, "told about levels: " + QaStageFourGameTests.keys(d.seen()));
		helper.assertTrue(QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.started") != null,
			"no start line: " + QaStageFourGameTests.keys(d.seen()));
		for (Component c : d.seen()) {
			helper.assertTrue(!c.getString().contains("aliveworkplace.") && !c.getString().contains("merchant.level"), "a raw key: " + c.getString());
		}
	}
}
