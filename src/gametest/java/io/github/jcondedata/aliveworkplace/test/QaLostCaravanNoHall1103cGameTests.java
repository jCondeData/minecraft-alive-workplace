package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * QA (lane-c, 2026-11-03) of B626's action-bar line from the spec: with the Lost Caravan's receiving Village Hall
 * broken, only a player who brings all the lost goods within {@link LostCaravanStory#REACH} blocks of where it stood,
 * while the village's quest is at "deliver the goods", is told the hall is gone, and keeps the goods. Far away (just
 * past the reach), empty-handed, or with no quest at that step, nobody is told. The nightly mutation run found the
 * first "and" of that condition turned to "or" survived.
 */
public class QaLostCaravanNoHall1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static boolean told(List<QaLostCaravanGameTests.Seen> seen) {
		return QaLostCaravanGameTests.texts(seen, true).stream().anyMatch(t -> t.contains("Village Hall is gone"));
	}

	private static void give(ServerPlayer player) {
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		player.getInventory().add(new ItemStack(Items.BREAD, 12));
	}

	private static boolean keeps(ServerPlayer player) {
		return player.getInventory().countItem(Items.OAK_LOG) == 32 && player.getInventory().countItem(Items.BREAD) == 12;
	}

	/** Whether the arc's village has its quest at the deliver-the-goods step. */
	private static boolean deliverStep(ServerLevel level, ArcState s) {
		return LostCaravanStory.running(level).stream().filter(r -> r.state() == s).flatMap(r -> Stories.open(level, r.hall()).stream())
			.anyMatch(q -> s.id.equals(q.arc) && q.current() >= 0 && q.objectives.get(q.current()) instanceof LostCaravanStory.DeliverGoods);
	}

	/** Two villages, the caravan lost, chapter 3 (deliver the goods), the receiving hall broken. */
	private static Object[] brokenAtDeliver(GameTestHelper helper) {
		BlockPos[] ab = LostCaravanGameTests.begin(helper);
		ServerLevel level = helper.getLevel();
		ArcState s = LostCaravanGameTests.lose(helper, ab[0], ab[1]);
		helper.assertTrue(Arcs.next(level, ab[0]) && Arcs.next(level, ab[0]) && s.chapter == 2, "setup: not in chapter 3: " + s.chapter);
		helper.assertTrue(deliverStep(level, s), "setup: the village's quest isn't at the deliver step");
		level.setBlock(ab[1], Blocks.AIR.defaultBlockState(), 3);
		return new Object[] {ab[1], s};
	}

	/** (a) The carrier with the quest, near the broken hall, is told, and keeps the goods. */
	//$ gametest_ticks_batch AREA '100' '"qaLcNoHall1103cTold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLcNoHall1103cTold")
	public void qaTheCarrierNearTheBrokenHallIsToldAndKeepsTheGoods(GameTestHelper helper) {
		BlockPos b = (BlockPos) brokenAtDeliver(helper)[0];
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer p = QaLostCaravanGameTests.listener(helper, seen, b.getX() + 4.5, b.getY(), b.getZ() + 0.5, "LcNhNear");
		give(p);
		LostCaravanStory.check(p);
		helper.assertTrue(told(seen), "the carrier near the broken hall wasn't told: " + QaLostCaravanGameTests.texts(seen, true));
		helper.assertTrue(keeps(p), "the goods were taken at a broken hall");
		QaLostCaravanGameTests.english(helper, QaLostCaravanGameTests.texts(seen, true));
		LOG.info("[QA 1103c] B626: the carrier at the broken hall is told and keeps the goods");
		helper.succeed();
	}

	/** (b) The reach's edge: exactly REACH blocks across is told; a tenth of a block past it is not. */
	//$ gametest_ticks_batch AREA '100' '"qaLcNoHall1103cReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLcNoHall1103cReach")
	public void qaACarrierJustPastTheReachIsNotTold(GameTestHelper helper) {
		BlockPos b = (BlockPos) brokenAtDeliver(helper)[0];
		int reach = LostCaravanStory.REACH;
		List<QaLostCaravanGameTests.Seen> outSeen = new ArrayList<>();
		ServerPlayer out = QaLostCaravanGameTests.listener(helper, outSeen, b.getX() + 0.5 + reach + 0.1, b.getY(), b.getZ() + 0.5, "LcNhOut");
		give(out);
		LostCaravanStory.check(out);
		helper.assertFalse(told(outSeen), "a carrier " + (reach + 0.1) + " blocks off was told the hall is gone: " + QaLostCaravanGameTests.texts(outSeen, true));
		helper.assertTrue(keeps(out), "the far carrier's goods were taken");

		List<QaLostCaravanGameTests.Seen> inSeen = new ArrayList<>();
		ServerPlayer in = QaLostCaravanGameTests.listener(helper, inSeen, b.getX() + 0.5, b.getY(), b.getZ() + 0.5 + reach, "LcNhIn");
		give(in);
		LostCaravanStory.check(in);
		helper.assertTrue(told(inSeen), "a carrier exactly " + reach + " blocks off wasn't told: " + QaLostCaravanGameTests.texts(inSeen, true));
		helper.assertTrue(keeps(in), "the edge carrier's goods were taken at a broken hall");
		LOG.info("[QA 1103c] B626: told at {} blocks, not at {}", reach, reach + 0.1);
		helper.succeed();
	}

	/** (c) Near the broken hall with the quest at the deliver step, but with only some of the goods: not told. */
	//$ gametest_ticks_batch AREA '100' '"qaLcNoHall1103cEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLcNoHall1103cEmpty")
	public void qaAPlayerWithoutTheGoodsIsNotTold(GameTestHelper helper) {
		BlockPos b = (BlockPos) brokenAtDeliver(helper)[0];
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer p = QaLostCaravanGameTests.listener(helper, seen, b.getX() + 2.5, b.getY(), b.getZ() + 2.5, "LcNhEmpty");
		LostCaravanStory.check(p);
		helper.assertFalse(told(seen), "an empty-handed player was told the hall is gone: " + QaLostCaravanGameTests.texts(seen, true));
		p.getInventory().add(new ItemStack(Items.OAK_LOG, 32)); // the logs but not the bread
		p.getInventory().add(new ItemStack(Items.BREAD, 11));
		LostCaravanStory.check(p);
		helper.assertFalse(told(seen), "a player short of the goods was told the hall is gone: " + QaLostCaravanGameTests.texts(seen, true));
		LOG.info("[QA 1103c] B626: no line without all the goods");
		helper.succeed();
	}

	/** (d) Carrying the goods near the broken hall, but the village's quest isn't at the deliver step: not told. */
	//$ gametest_ticks_batch AREA '100' '"qaLcNoHall1103cNoQuest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLcNoHall1103cNoQuest")
	public void qaACarrierWithoutTheDeliverQuestIsNotTold(GameTestHelper helper) {
		BlockPos[] ab = LostCaravanGameTests.begin(helper);
		ServerLevel level = helper.getLevel();
		ArcState s = LostCaravanGameTests.lose(helper, ab[0], ab[1]); // chapter 1: nobody has been asked to bring them yet
		helper.assertTrue(!deliverStep(level, s), "setup: the quest is already at the deliver step in chapter " + (s.chapter + 1));
		BlockPos b = ab[1];
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer p = QaLostCaravanGameTests.listener(helper, seen, b.getX() + 3.5, b.getY(), b.getZ() + 0.5, "LcNhEarly");
		give(p);
		LostCaravanStory.check(p);
		helper.assertFalse(told(seen), "a carrier was told the hall is gone before any deliver quest: " + QaLostCaravanGameTests.texts(seen, true));

		// then chapter 3, but the deliver quest belongs to another story (not this caravan's): still nobody to tell
		helper.assertTrue(Arcs.next(level, ab[0]) && Arcs.next(level, ab[0]) && s.chapter == 2, "setup: not in chapter 3: " + s.chapter);
		helper.assertTrue(deliverStep(level, s), "setup: chapter 3 has no deliver quest");
		for (Quest q : Stories.open(level, ab[0])) {
			if (s.id.equals(q.arc)) {
				q.arc = "aliveworkplace:qa_other_story";
			}
		}
		helper.assertTrue(!deliverStep(level, s), "setup: this caravan's deliver quest is still up");
		LostCaravanStory.check(p);
		helper.assertFalse(told(seen), "a carrier was told the hall is gone with no deliver quest up: " + QaLostCaravanGameTests.texts(seen, true));
		helper.assertTrue(keeps(p), "the goods were taken");
		LOG.info("[QA 1103c] B626: no line without the deliver quest");
		helper.succeed();
	}
}
