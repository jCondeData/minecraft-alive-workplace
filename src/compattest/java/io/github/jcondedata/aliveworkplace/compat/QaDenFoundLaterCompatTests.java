package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-05) for B190, from its Expected: "a den within 160 blocks of a hall is listed in a real world, including a
 * den placed after the hall's chunk is already loaded". RaidDenFoundCompatTests sets dens down a few blocks from the hall,
 * and QaDenMegaCompatTests proves the 160-block reach by calling the chunk-load path by hand; these put both together,
 * with nothing calling the detection: the dens are set down as a player or world generation sets them (the block placed,
 * its chunk kept loaded the way a player standing there keeps it), exactly 160 and 161 blocks off, both ways round (the
 * den after the hall, and the hall after the den).
 */
public class QaDenFoundLaterCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	/** The first way in which a point {@code steps} off {@code hall} has no other hall within reach (other batches' halls stand). */
	private static int[] clearWay(GameTestHelper helper, BlockPos hall, int steps) {
		for (int[] way : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (RaidDens.hallNear(helper.getLevel(), hall.offset(steps * way[0], 0, steps * way[1])).isEmpty()) {
				return way;
			}
		}
		throw new GameTestAssertException("setup: other tests' halls are within reach of " + steps + " blocks off this hall every way");
	}

	private static boolean known(GameTestHelper helper, BlockPos den) {
		return RaidDens.Sites.get(helper.getLevel()).dens().stream().anyMatch(d -> d.pos().equals(den));
	}

	/** Keeps the chunks of {@code spots} loaded for the test (as a player standing by them would), and puts back blocks and chunks after. */
	private static List<BlockPos> keep(GameTestHelper helper, List<BlockPos> spots) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> placed = new ArrayList<>();
		List<BlockPos> forced = new ArrayList<>(); // only the chunks this test forced are let go after (B992)
		for (BlockPos p : spots) {
			if (level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true)) {
				forced.add(p);
			}
		}
		PartnerShowsCompatTests.after(helper, () -> {
			for (BlockPos p : placed) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			for (BlockPos p : placed) {
				RaidDens.near(level, p);
			}
			for (BlockPos p : forced) {
				level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, false);
			}
		});
		return placed;
	}

	/**
	 * The hall stands and its chunk is long loaded; afterwards a den is set down exactly 160 blocks off and another 161
	 * blocks off (the only hall near either is this one). The first is listed with no one calling the detection; the second
	 * is neither listed nor recorded.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDenLaterReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDenLaterReach")
	public void aDenSetDownLaterIsTheVillagesAt160NotAt161(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		int[] way = clearWay(helper, hall, 161);
		BlockPos in = hall.offset(160 * way[0], 0, 160 * way[1]);
		BlockPos out = hall.offset(161 * way[0], 0, 161 * way[1]);
		List<BlockPos> placed = keep(helper, List.of(in, out));
		BlockState den = RaidDenCompatTests.den("tier_four", "dragon", true);
		helper.startSequence()
			.thenIdle(20) // the hall and its chunk settle; the dens come well after
			.thenExecute(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, in).isEmpty(), "a den is listed before it's there"))
			.thenExecute(() -> {
				level.setBlockAndUpdate(in, den);
				level.setBlockAndUpdate(out, den);
				placed.add(in);
				placed.add(out);
			})
			.thenWaitUntil(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, in).isPresent(),
				"a den set down 160 blocks from the standing hall isn't listed: " + RaidDens.near(level, hall)))
			.thenIdle(5)
			.thenExecute(() -> {
				helper.assertTrue(RaidDenCompatTests.listed(helper, hall, out).isEmpty(), "a den set down 161 blocks off is listed");
				helper.assertFalse(known(helper, out), "a den set down 161 blocks from the only hall was recorded");
				RaidDens.Den found = RaidDenCompatTests.listed(helper, hall, in).get();
				helper.assertTrue(found.active() && found.tier() >= 1, "the den 160 blocks off: " + found);
			})
			.thenSucceed();
	}

	/**
	 * The dens come first, 160 and 161 blocks from where the hall will stand (no hall near either yet, so neither is any
	 * village's); then the hall is set down. It finds the den 160 blocks off without the chunk loading again, and not the
	 * one 161 blocks off.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDenHallLaterReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDenHallLaterReach")
	public void aHallSetDownLaterFindsTheDenAt160NotAt161(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int[] way = clearWay(helper, hall, 161);
		BlockPos in = hall.offset(160 * way[0], 0, 160 * way[1]);
		BlockPos out = hall.offset(161 * way[0], 0, 161 * way[1]);
		List<BlockPos> placed = keep(helper, List.of(in, out));
		BlockState den = RaidDenCompatTests.den("tier_one", "grass", false);
		helper.assertTrue(RaidDens.hallNear(level, in).isEmpty(), "setup: another hall is within reach of the den 160 blocks off");
		level.setBlockAndUpdate(in, den);
		level.setBlockAndUpdate(out, den);
		placed.add(in);
		placed.add(out);
		helper.startSequence()
			.thenIdle(20)
			.thenExecute(() -> helper.assertFalse(known(helper, in) || known(helper, out), "a den with no hall near was recorded"))
			.thenExecute(() -> helper.setBlock(HALL, ModBlocks.VILLAGE_HALL))
			.thenWaitUntil(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, in).isPresent(),
				"the new hall didn't find the den 160 blocks off: " + RaidDens.near(level, hall)))
			.thenIdle(5)
			.thenExecute(() -> {
				helper.assertTrue(RaidDenCompatTests.listed(helper, hall, out).isEmpty(), "the new hall lists the den 161 blocks off");
				helper.assertFalse(known(helper, out), "the den 161 blocks from the only hall was recorded");
				RaidDens.Den found = RaidDenCompatTests.listed(helper, hall, in).get();
				helper.assertTrue(found.tier() == 1 && found.type().equals("grass") && !found.active(), "the den 160 blocks off: " + found);
			})
			.thenSucceed();
	}
}
