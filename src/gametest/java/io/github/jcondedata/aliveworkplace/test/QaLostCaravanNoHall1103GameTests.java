package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
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
 * QA (lane-c-1103-0353) of B626's message from the spec: with the Lost Caravan's receiving hall broken, a player who
 * brings the lost goods to where it stood is told the Village Hall is gone and keeps them. Only that player: one who
 * stands there with nothing of the goods, or carries them far from the village, is told nothing (the nightly mutation
 * run of 2026-11-02 found nothing checked this: LostCaravanStory's "within and carries" turned to "or" survived).
 */
public class QaLostCaravanNoHall1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static boolean toldHallGone(List<QaLostCaravanGameTests.Seen> seen) {
		return QaLostCaravanGameTests.texts(seen, true).stream().anyMatch(t -> t.contains("Village Hall is gone"));
	}

	/**
	 * In chapter 3 with the receiving hall broken: a player at its spot with empty hands, then one 200 blocks off with
	 * all the goods, hear nothing; the control, the carrier at its spot, hears that the hall is gone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanNoHall1103"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanNoHall1103")
	public void qaOnlyTheCarrierAtTheVillageIsToldItsHallIsGone(GameTestHelper helper) {
		BlockPos[] ab = LostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = LostCaravanGameTests.lose(helper, a, b);
		helper.assertTrue(Arcs.next(level, a) && Arcs.next(level, a) && s.chapter == 2, "setup: not in chapter 3: " + s.chapter);
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3); // the receiving hall broken

		List<QaLostCaravanGameTests.Seen> emptyHanded = new ArrayList<>();
		ServerPlayer idle = QaLostCaravanGameTests.listener(helper, emptyHanded, b.getX() + 3.5, b.getY(), b.getZ() + 0.5, "LcIdle");
		LostCaravanStory.check(idle);
		helper.assertFalse(toldHallGone(emptyHanded), "a player with none of the goods was told the hall is gone: "
			+ QaLostCaravanGameTests.texts(emptyHanded, true));

		List<QaLostCaravanGameTests.Seen> farSeen = new ArrayList<>();
		ServerPlayer far = QaLostCaravanGameTests.listener(helper, farSeen, b.getX() + 200.5, b.getY(), b.getZ() + 0.5, "LcFar");
		far.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		far.getInventory().add(new ItemStack(Items.BREAD, 12));
		LostCaravanStory.check(far);
		helper.assertFalse(toldHallGone(farSeen), "a carrier 200 blocks from the village was told the hall is gone: "
			+ QaLostCaravanGameTests.texts(farSeen, true));
		helper.assertTrue(far.getInventory().countItem(Items.OAK_LOG) == 32 && far.getInventory().countItem(Items.BREAD) == 12, "the far carrier's goods were taken");

		List<QaLostCaravanGameTests.Seen> carrierSeen = new ArrayList<>();
		ServerPlayer carrier = QaLostCaravanGameTests.listener(helper, carrierSeen, b.getX() + 3.5, b.getY(), b.getZ() + 0.5, "LcCarrier2");
		carrier.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		carrier.getInventory().add(new ItemStack(Items.BREAD, 12));
		LostCaravanStory.check(carrier);
		helper.assertTrue(toldHallGone(carrierSeen), "the carrier at the village wasn't told the hall is gone: " + QaLostCaravanGameTests.texts(carrierSeen, true));
		helper.assertTrue(carrier.getInventory().countItem(Items.OAK_LOG) == 32, "the goods were taken at a broken hall");
		LOG.info("[QA 1103] B626: only the carrier at the broken hall's village was told it is gone");
		helper.succeed();
	}
}
