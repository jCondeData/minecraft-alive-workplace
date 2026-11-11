package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA lane E, 2026-11-11 (B1164, B436's test): a hall loaded with its chunk has its first round after the wait and a gap
 * after any other hall's first, "or at its turn if that comes sooner" (VillageHallBlockEntity.roundDue). Its regular turn
 * every {@code CHECK_EVERY} ticks is never held back, so a hall whose turn falls after the wait but before its place in
 * the queue has its first round at its turn, inside the gap of the hall before it. HallFirstRoundGameTests' four halls
 * excused only a turn during the wait, so whenever one hall's turn fell in that later window (where the four halls stand
 * and the world's clock decide it) the test failed with nothing wrong in the mod. This test sets that case up on purpose.
 */
public class QaHallFirstRound1111eGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Ticks until the hall at {@code pos}'s regular turn (0: this tick), as VillageHallBlockEntity.roundDue counts it. */
	private static int ticksToTurn(ServerLevel level, BlockPos pos) {
		return Math.floorMod(-(level.getGameTime() + pos.hashCode()), VillageNeeds.CHECK_EVERY);
	}

	/**
	 * Two halls read with their chunks together: the first has no turn for a long while and has its first round when the
	 * wait is over; the second stands where its turn comes a few ticks after the wait, before the gap behind the first is
	 * over, and has its first round at that turn, not held back by the gap.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaHallFirstRoundTurn1111e"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaHallFirstRoundTurn1111e")
	public void aHallWhoseTurnComesInTheGapHasItsFirstRoundAtItsTurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 6;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		// The second hall: its turn 20 to 150 ticks from now. The wait is set to end 5 ticks before that turn, so the turn
		// comes after the wait and before the gap behind the first hall is over. The first hall: no turn of its own until
		// well after the test. (A spot's turn is its position's hash, which runs in blocks of neighbouring values, so the
		// wait is fitted to the spot rather than the spot to a wait.)
		BlockPos second = null;
		int turnB = -1;
		for (int x = 20; x <= 27 && second == null; x++) {
			for (int z = 2; z <= 27 && second == null; z++) {
				int turn = ticksToTurn(level, helper.absolutePos(new BlockPos(x, 2, z)));
				if (turn >= 20 && turn <= 150) {
					second = new BlockPos(x, 2, z);
					turnB = turn;
				}
			}
		}
		int wait = turnB - 5;
		int gap = 20;
		BlockPos first = null;
		for (int x = 2; x <= 10 && first == null && second != null; x++) {
			for (int z = 2; z <= 27 && first == null; z++) {
				if (ticksToTurn(level, helper.absolutePos(new BlockPos(x, 2, z))) > wait + gap + 60) {
					first = new BlockPos(x, 2, z);
				}
			}
		}
		if (first == null || second == null) {
			// CHECK_EVERY is 600 and over 200 spots are tried for each, so this would take a clock no world has.
			helper.fail("setup: no spot for the halls (first " + first + ", second " + second + ")");
			return;
		}
		int wasWait = VillageHallBlockEntity.FIRST_ROUND_WAIT;
		int wasGap = VillageHallBlockEntity.FIRST_ROUND_GAP;
		VillageHallBlockEntity.FIRST_ROUND_WAIT = wait;
		VillageHallBlockEntity.FIRST_ROUND_GAP = gap;
		Leftovers.after(helper, () -> {
			VillageHallBlockEntity.FIRST_ROUND_WAIT = wasWait;
			VillageHallBlockEntity.FIRST_ROUND_GAP = wasGap;
		});
		helper.setBlock(first, ModBlocks.VILLAGE_HALL);
		helper.setBlock(second, ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(first);
		BlockPos b = helper.absolutePos(second);
		VillageHallBlockEntity readA = HallFirstRoundGameTests.restarted(level, a);
		VillageHallBlockEntity readB = HallFirstRoundGameTests.restarted(level, b);
		helper.assertTrue(readA.awaitingFirstRound() && readB.awaitingFirstRound(), "setup: both halls were read with their chunks and wait");
		helper.assertTrue(ticksToTurn(level, b) == turnB, "setup: the second hall's turn is still " + turnB + " ticks away");
		long start = level.getGameTime();
		long[] firstRound = {-1, -1};
		helper.onEachTick(() -> {
			long tick = level.getGameTime() - start;
			BlockPos[] halls = {a, b};
			for (int i = 0; i < 2; i++) {
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(halls[i]);
				if (firstRound[i] < 0 && hall != null && hall.needs() != null) {
					firstRound[i] = tick;
				}
			}
		});
		int lastWait = wait;
		int lastGap = gap;
		int lastTurn = turnB;
		helper.runAfterDelay(wait + gap + 40, () -> {
			helper.assertTrue(firstRound[0] >= lastWait && firstRound[0] <= lastWait + 1,
				"the first hall had its first round when the wait of " + lastWait + " was over, not at " + firstRound[0]);
			// the round ran in the tick before the look at it, or in that one
			helper.assertTrue(firstRound[1] >= lastTurn && firstRound[1] <= lastTurn + 1,
				"the second hall had its first round at its turn, " + lastTurn + " ticks in, not at " + firstRound[1]);
			helper.assertTrue(firstRound[1] - firstRound[0] < lastGap,
				"its turn wasn't held back by the gap of " + lastGap + " behind the first hall: " + firstRound[0] + " and " + firstRound[1]);
			helper.succeed();
		});
	}
}
