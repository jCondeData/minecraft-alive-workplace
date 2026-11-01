package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1101-0052) for 36.22 (miners dig deeper as they level up) and 36.20 (the quarry's reach), written from the
 * specs and the 2026-10-27 decisions, filling the corners the earlier QA classes leave: 64 dug whole by a Novice and a
 * pit one block deeper refused (the level asked is the real pit's, cut by the world's floor); a 128 marker cut by the
 * floor to 90 needs only a Journeyman; an Expert one layer short of a 128-deep bedrock pit, told Master; strip mines down
 * a long shaft never cut by level; an Expert leveling to Master mid-quarry takes a bedrock pit to the floor; a quarry
 * saved before 36.22 keeps its depth and a cut one keeps its wanted floor; the bedrock marker survives a save; and the
 * quarry reach read from the config file holds at its edge, refused one block past with the sentence in full. Same
 * layout as {@link QaDeepQuarryGameTests}.
 */
public class QaWorkReach1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TOO_DEEP = "message.aliveworkplace.quarry.too_deep";
	private static final String TAIL = "; if they get there before they finish, they'll keep going down.";

	private static int bottom(GameTestHelper helper, QaDeepQuarryGameTests.Deep d) {
		return helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY();
	}

	/** 64, for every miner, at its edge: a Novice digs all 64 with no word of levels. */
	//$ gametest_ticks_batch AREA '400' '"qaWorkReach1101Novice64"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWorkReach1101Novice64")
	public void aNoviceDigsAWhole64PitWithNoWord(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		int top = bottom(helper, d) + 100;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 64));
		helper.assertTrue(site.box().getYSpan() == 64 && !site.wantsDeeper(), "a Novice should take all 64: " + site.box());
		silent(helper, d);
		helper.succeed();
	}

	/**
	 * One block past 64: a 96 marker over ground only 65 deep (the world's floor cuts it). The Novice stops at 64 and is
	 * told, word for word, that all 65 need a Journeyman (the level asked is the real pit's, decision 2).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWorkReach1101Novice65"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWorkReach1101Novice65")
	public void aNoviceOverA65DeepPitStopsAt64AndIsToldJourneyman(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		int top = bottom(helper, d) + 64;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 96));
		int topAbs = helper.absolutePos(new BlockPos(0, top, 0)).getY();
		helper.assertTrue(site.box().getYSpan() == 64 && site.box().maxY() == topAbs && site.wantsDeeper()
			&& site.wantedBottom() == d.level().getMinBuildHeight() + 5, "a Novice should stop 64 down a 65-deep pit: " + site.box() + ", wanted " + site.wantedBottom());
		told(helper, d, d.miner().getDisplayName().getString() + " is at Novice level, so they'll stop 64 blocks down for now. "
			+ "Digging all 65 blocks needs Journeyman level" + TAIL);
		helper.succeed();
	}

	/** A 128 marker over ground the floor cuts to 90: a Journeyman takes all 90, no word (decision 2's own example). */
	//$ gametest_ticks_batch AREA '400' '"qaWorkReach1101Journeyman90"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWorkReach1101Journeyman90")
	public void a128MarkerCutByTheFloorTo90NeedsOnlyAJourneyman(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 3);
		int top = bottom(helper, d) + 89;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, 128));
		helper.assertTrue(site.box().getYSpan() == 90 && site.box().minY() == d.level().getMinBuildHeight() + 5 && !site.wantsDeeper(),
			"a Journeyman should take all of a 128 marker the floor cuts to 90: " + site.box());
		silent(helper, d);
		helper.succeed();
	}

	/**
	 * The bedrock marker one level below Master, at the Expert's own depth: a bedrock pit exactly 128 deep. The Expert
	 * stops one layer above the bottom (those are a Master's) and is told Master, as the marker says.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWorkReach1101ExpertBedrock"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWorkReach1101ExpertBedrock")
	public void anExpertOverA128DeepBedrockPitStopsAboveItsLastLayerAndIsToldMaster(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 4);
		int floor = d.level().getMinBuildHeight() + 5;
		int top = bottom(helper, d) + 127;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().getYSpan() == 127 && site.box().minY() == floor + 1 && site.wantsDeeper() && site.wantedBottom() == floor,
			"an Expert should stop just above a 128-deep bedrock pit's last layer: " + site.box());
		told(helper, d, d.miner().getDisplayName().getString() + " is at Expert level, so they'll stop 127 blocks down for now. "
			+ "Digging all 128 blocks needs Master level" + TAIL);
		helper.succeed();
	}

	/**
	 * Strip mines aren't cut by level: a Novice handed a diamond-level strip mine (Y=-53) far below the marked corners
	 * digs the ladder shaft all the way down, more than 64 blocks, with no word of levels.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWorkReach1101NoviceStrip"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWorkReach1101NoviceStrip")
	public void aNovicesStripMineDownALongShaftIsNotCutByLevel(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		int top = bottom(helper, d) + 120;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(d.level().dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(QaDeepQuarryGameTests.LO, top, QaDeepQuarryGameTests.LO))),
			Optional.of(helper.absolutePos(new BlockPos(QaDeepQuarryGameTests.HI, top, QaDeepQuarryGameTests.HI))),
			QuarryData.STRIP_MINE, Optional.of(-53)));
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, marker);
		int topAbs = helper.absolutePos(new BlockPos(0, top, 0)).getY();
		helper.assertTrue(site.hasShaft() && site.box().minY() == -53 && !site.wantsDeeper(),
			"a Novice's strip mine at Y=-53 should keep its shaft and tunnels: " + site.box() + ", shaft " + site.hasShaft());
		helper.assertTrue(topAbs - site.box().maxY() > 64, "the shaft isn't longer than a Novice's 64: top " + topAbs + ", " + site.box());
		helper.assertTrue(QaStageFourGameTests.find(d.seen(), TOO_DEEP) == null, "told about levels: " + QaStageFourGameTests.keys(d.seen()));
		Component started = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.started_shaft");
		helper.assertTrue(started != null, "no shaft start line: " + QaStageFourGameTests.keys(d.seen()));
		QaStageFourGameTests.assertRendered(helper, started, "ladder shaft down to Y=-53");
		helper.succeed();
	}

	/**
	 * An Expert stopped one layer short of a bedrock pit reaches Master mid-quarry: the pit goes down to the floor, and
	 * the owner reads, word for word, that they'll keep digging, all 128 down.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaWorkReach1101ExpertToMaster"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaWorkReach1101ExpertToMaster")
	public void anExpertWhoBecomesMasterMidQuarryTakesTheBedrockPitToTheFloor(GameTestHelper helper) {
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 4);
		int floor = d.level().getMinBuildHeight() + 5;
		int top = bottom(helper, d) + 127;
		QaDeepQuarryGameTests.cleanUp(helper, d, top);
		QuarrySite site = QaDeepQuarryGameTests.click(helper, d, QaDeepQuarryGameTests.marker(helper, d, top, QuarryData.BEDROCK));
		helper.assertTrue(site.box().minY() == floor + 1 && site.wantsDeeper(), "the Expert didn't stop short: " + site.box());
		d.miner().setVillagerData(d.miner().getVillagerData().setLevel(5));
		String expected = d.miner().getDisplayName().getString() + " reached Master level and will keep digging, down to 128 blocks deep.";
		helper.succeedWhen(() -> {
			Component deeper = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.deeper");
			helper.assertTrue(deeper != null, "not told of digging deeper: " + QaStageFourGameTests.keys(d.seen()) + ", " + site.box());
			helper.assertTrue(deeper.getString().equals(expected), "expected '" + expected + "', told '" + deeper.getString() + "'");
			helper.assertTrue(site.box().minY() == floor && !site.wantsDeeper(), "a Master should take the pit to the floor: " + site.box());
		});
	}

	/**
	 * Saves: a quarry saved before 36.22 (no wanted floor) keeps its depth and wants nothing deeper; a cut quarry keeps
	 * its wanted floor, its depth choice and its box through a save and load.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWorkReach1101Saves"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWorkReach1101Saves")
	public void savedQuarriesKeepTheirDepth(GameTestHelper helper) {
		var dim = helper.getLevel().dimension().location();
		QuarrySite old = new QuarrySite(UUID.randomUUID(), UUID.randomUUID(), "Owner", dim, new BoundingBox(0, -40, 0, 4, 87, 4), 128);
		CompoundTag tag = old.save();
		tag.remove("wanted_bottom");
		QuarrySite loaded = QuarrySite.load(tag);
		helper.assertTrue(loaded.box().equals(old.box()) && loaded.box().getYSpan() == 128 && loaded.depth() == 128 && !loaded.wantsDeeper()
			&& loaded.wantedBottom() == -40, "an old 128 quarry came back as " + loaded.box() + ", depth " + loaded.depth() + ", wanted " + loaded.wantedBottom());

		QuarrySite cut = new QuarrySite(UUID.randomUUID(), UUID.randomUUID(), "Owner", dim, new BoundingBox(0, 24, 0, 4, 87, 4), QuarryData.BEDROCK);
		cut.setWantedBottom(-59);
		CompoundTag cutTag = cut.save();
		helper.assertTrue(cutTag.contains("wanted_bottom", Tag.TAG_INT), "a cut quarry saved no wanted floor: " + cutTag);
		QuarrySite back = QuarrySite.load(cutTag);
		helper.assertTrue(back.box().equals(cut.box()) && back.wantedBottom() == -59 && back.wantsDeeper() && back.depth() == QuarryData.BEDROCK,
			"a cut bedrock quarry came back as " + back.box() + ", wanted " + back.wantedBottom() + ", depth " + back.depth());
		helper.succeed();
	}

	/** The bedrock marker is saved as such: a stack saved and read back still digs to bedrock and its tooltip still says Master. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWorkReach1101Marker"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWorkReach1101Marker")
	public void theBedrockMarkerSurvivesASave(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, QuarryData.EMPTY.withDepth(QuarryData.BEDROCK));
		Tag saved = marker.save(registries);
		ItemStack back = ItemStack.parse(registries, saved).orElse(ItemStack.EMPTY);
		QuarryData data = back.get(ModComponents.QUARRY);
		helper.assertTrue(back.is(ModItems.QUARRY_MARKER) && data != null && data.toBedrock(), "the marker came back as " + back + " " + data);
		List<Component> tip = new ArrayList<>();
		back.getItem().appendHoverText(back, Item.TooltipContext.of(helper.getLevel()), tip, TooltipFlag.NORMAL);
		String text = String.join(" | ", tip.stream().map(Component::getString).toList());
		helper.assertTrue(text.contains("Down to just above bedrock, for Master Miners only"), "the reloaded marker's tooltip: " + text);
		helper.assertTrue(QuarryMarkerItem.depthChoice(QuarryData.BEDROCK, "tooltip.aliveworkplace.quarry.depth").getString().contains("Master"),
			"the bedrock choice doesn't name Master");
		helper.succeed();
	}

	/**
	 * 36.20's quarryReach, read from the config file, at its edge with no village about: a quarry 21 blocks across from
	 * the bench is refused with the sentence in full, one 20 across is taken.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWorkReach1101ConfigEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWorkReach1101ConfigEdge")
	public void theQuarryReachFromTheConfigFileHoldsAtItsEdge(GameTestHelper helper) {
		Leftovers.halls(helper);
		QaDeepQuarryGameTests.Deep d = QaDeepQuarryGameTests.miner(helper, 1);
		try {
			WorkplaceConfig.parse("{\"configVersion\": 5, \"quarryReach\": 20}").apply();
			helper.assertTrue(Miners.MAX_DISTANCE == 20, "quarryReach 20 gave the miner " + Miners.MAX_DISTANCE);
			BlockPos bench = QaDeepQuarryGameTests.BENCH;
			// a 3 x 3 quarry, 4 deep at the bench's height, centred 21 then 20 blocks east of the bench
			ItemStack far = quarry(helper, d, bench.getX() + 20, bench.getZ() - 1);
			d.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, far);
			QaStageFourGameTests.pressOn(d.player(), d.miner(), d.seen());
			helper.assertTrue(Miners.activeSite(d.level(), d.miner()) == null, "a quarry 21 across was taken with a reach of 20");
			Component refused = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.too_far");
			helper.assertTrue(refused != null, "no refusal: " + QaStageFourGameTests.keys(d.seen()));
			String expected = "That spot is 21 blocks from the Miner's Bench the miner works at — keep it within 20.";
			helper.assertTrue(refused.getString().equals(expected), "expected '" + expected + "', told '" + refused.getString() + "'");
			QuarrySite site = QaDeepQuarryGameTests.click(helper, d, quarry(helper, d, bench.getX() + 19, bench.getZ() - 1));
			helper.assertTrue(Math.round(io.github.jcondedata.aliveworkplace.work.Reach.across(helper.absolutePos(bench), site.box().getCenter())) == 20,
				"the taken quarry isn't 20 across: " + site.box());
			QuarrySiteManager.get(d.level()).remove(site.id());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	private static ItemStack quarry(GameTestHelper helper, QaDeepQuarryGameTests.Deep d, int x, int z) {
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(d.level().dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(x, 5, z))), Optional.of(helper.absolutePos(new BlockPos(x + 2, 5, z + 2))), 4));
		return marker;
	}

	private static void told(GameTestHelper helper, QaDeepQuarryGameTests.Deep d, String expected) {
		Component told = QaStageFourGameTests.find(d.seen(), TOO_DEEP);
		helper.assertTrue(told != null, "not told: " + QaStageFourGameTests.keys(d.seen()));
		helper.assertTrue(told.getString().equals(expected), "expected '" + expected + "', told '" + told.getString() + "'");
	}

	private static void silent(GameTestHelper helper, QaDeepQuarryGameTests.Deep d) {
		helper.assertTrue(QaStageFourGameTests.find(d.seen(), TOO_DEEP) == null, "told about levels: " + QaStageFourGameTests.keys(d.seen()));
		helper.assertTrue(QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.started") != null,
			"no start line: " + QaStageFourGameTests.keys(d.seen()));
		for (Component c : d.seen()) {
			helper.assertTrue(!c.getString().contains("aliveworkplace."), "a raw key: " + c.getString());
		}
	}
}
