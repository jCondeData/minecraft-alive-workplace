package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.Comparator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;

/**
 * Lane D QA (2026-11-14), B1232 from its spec: "a drill day at a Village Hall nobody is near loads the chunks around it.
 * ReformStories.tick ... guards each hall only with level.isLoaded(hall) ... The Shift Bell ring and the lamplit and
 * drill-end chronicle lines in the same loop have the same guard (not tested). Expected: a hall nobody is near loads no
 * chunk (skip it with level.isPositionEntityTicking(hall))", and the fix's own promise: "The lamplit line waits for a
 * night someone is near." The drill itself is QaFarHall1114cBugTests's; a near village's drill (a Guard by the hall
 * counted, "Drill day in X: 1 villager sparred by the hall.") is already QaDrillHallWords1102GameTests's, which runs the
 * same tick, so it isn't repeated here.
 */
public class QaFarReform1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** Holds a chunk loaded but not ticking, like the outer ring of a player's view. */
	private static final TicketType<ChunkPos> BORDER = TicketType.create("qa1114d_reform_border", Comparator.comparingLong(ChunkPos::toLong));
	/** ReformStories.Entry.NONE (package-private): no Shift Bell. */
	private static final long NO_BELL = Long.MIN_VALUE;

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A spot {@code dx}, {@code dz} from the test, 4 blocks into its chunk on both axes. */
	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int x = (origin.getX() + dx) & ~15;
		int z = (origin.getZ() + dz) & ~15;
		return new BlockPos(x + 4, 70, z + 4);
	}

	/** Today's (or {@code time}'s day's) {@code at} o'clock. */
	private static long at(long time, long at) {
		return Math.floorDiv(time, 24000L) * 24000L + at;
	}

	private static String lamplitLine(ServerLevel level, BlockPos hall) {
		return "The first lamplit night in " + VillageHalls.name(level, hall).getString() + ": the streets glowed, and nobody hurried home.";
	}

	/** How many lines of the hall's chronicle are the first lamplit night's (any village's wording). */
	private static long lamplitLines(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(c -> c.text().getString()).filter(l -> l.startsWith("The first lamplit night in ")).count();
	}

	private static boolean wrote(ServerLevel level, BlockPos hall, String line) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().anyMatch(c -> c.kind() == Chronicle.Kind.STORY && c.text().getString().equals(line));
	}

	/** A Village Hall in a far chunk held loaded but not ticking (nobody near). */
	private static void farHall(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		ChunkPos chunk = new ChunkPos(hall);
		level.getChunkSource().addRegionTicket(BORDER, chunk, 0, chunk);
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "setup: no hall block entity at the far hall");
		helper.assertTrue(level.isLoaded(hall) && !level.isPositionEntityTicking(hall), "setup: the far hall's chunk isn't loaded-but-not-ticking");
	}

	private static void dropFarHall(ServerLevel level, BlockPos hall) {
		ChunkPos chunk = new ChunkPos(hall);
		level.setChunkForced(chunk.x, chunk.z, false);
		if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
		}
		level.getChunkSource().removeRegionTicket(BORDER, chunk, 0, chunk);
	}

	/**
	 * "The lamplit and drill-end chronicle lines in the same loop have the same guard ... a hall nobody is near loads no
	 * chunk" and the fix's "The lamplit line waits for a night someone is near": a far village (31.22's lamplighters ending,
	 * its first lamplit night still to write) whose hall's chunk is loaded but not ticking, at night. For 60 ticks no
	 * lamplit line is written and the night is still owed; once someone is back (its chunk ticks), still at night, the line
	 * a player reads ("The first lamplit night in X: the streets glowed, and nobody hurried home.") is written exactly once
	 * and the night is no longer owed.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa1114dReformLamplitFar"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1114dReformLamplitFar")
	public void aFarVillagesLamplitNightWaitsForSomeoneThenIsWrittenOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8800, 8800);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		long time = level.getDayTime();
		long night = at(time, ReformStories.NIGHT + 1000);
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			data.entry(hall).lamplit = false;
			data.setDirty();
			dropFarHall(level, hall);
		});
		farHall(helper, level, hall);
		level.setDayTime(night);
		data.entry(hall).lamplit = true;
		data.setDirty();
		String line = lamplitLine(level, hall);

		int[] ticks = {0};
		int[] back = {-1};
		helper.onEachTick(() -> {
			ticks[0]++;
			level.setDayTime(night); // (held at night throughout)
			if (ticks[0] <= 60) {
				helper.assertTrue(!level.isPositionEntityTicking(hall), "setup: the far hall's chunk began ticking by itself (tick " + ticks[0] + ")");
				helper.assertTrue(lamplitLines(level, hall) == 0, "a far village nobody is near wrote its lamplit night (tick " + ticks[0] + ")");
				helper.assertTrue(data.entry(hall).lamplit, "a far village nobody is near spent its lamplit night unwritten (tick " + ticks[0] + ")");
				if (ticks[0] == 60) {
					FarChunks.bringBack(level, chunk);
				}
				return;
			}
			if (back[0] < 0) {
				if (!level.isPositionEntityTicking(hall)) {
					helper.assertTrue(ticks[0] < 700, "setup: the force-loaded far hall's chunk never became entity-ticking");
					return;
				}
				back[0] = ticks[0];
			}
			long count = lamplitLines(level, hall);
			helper.assertTrue(count <= 1, "the lamplit night was written " + count + " times once someone was back");
			if (ticks[0] >= back[0] + 100) {
				helper.assertTrue(wrote(level, hall, line), "someone back at the far village at night, and no lamplit line [" + line + "]: "
					+ ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().map(c -> c.text().getString()).toList());
				helper.assertTrue(count == 1, "the lamplit night was written " + count + " times");
				helper.assertTrue(!data.entry(hall).lamplit, "the lamplit night was written but is still owed (it would be written again)");
				helper.succeed();
			}
		});
	}

	/**
	 * The fix's "The lamplit line waits for a night someone is near": someone comes back to the far village at noon. No
	 * lamplit line in daylight (and the night is still owed); when night falls the line is written, once.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa1114dReformLamplitNoon"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1114dReformLamplitNoon")
	public void aVillageReturnedToAtNoonStillWaitsForNightForItsLamplitLine(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8800, 8800);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		long time = level.getDayTime();
		long noon = at(time, 6000);
		long night = at(time, ReformStories.NIGHT + 1000);
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			data.entry(hall).lamplit = false;
			data.setDirty();
			dropFarHall(level, hall);
		});
		farHall(helper, level, hall);
		level.setDayTime(noon);
		data.entry(hall).lamplit = true;
		data.setDirty();
		String line = lamplitLine(level, hall);
		FarChunks.bringBack(level, chunk);

		int[] ticks = {0};
		int[] back = {-1};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (back[0] < 0) {
				level.setDayTime(noon);
				helper.assertTrue(lamplitLines(level, hall) == 0, "the lamplit line was written at noon (tick " + ticks[0] + ")");
				if (!level.isPositionEntityTicking(hall)) {
					helper.assertTrue(ticks[0] < 640, "setup: the force-loaded far hall's chunk never became entity-ticking");
					return;
				}
				back[0] = ticks[0];
			}
			int since = ticks[0] - back[0];
			if (since <= 80) {
				level.setDayTime(noon);
				helper.assertTrue(lamplitLines(level, hall) == 0, "someone back at noon: the lamplit night was written in daylight (" + since + " ticks after)");
				helper.assertTrue(data.entry(hall).lamplit, "someone back at noon: the lamplit night was spent in daylight without a line");
				return;
			}
			level.setDayTime(night);
			long count = lamplitLines(level, hall);
			helper.assertTrue(count <= 1, "the lamplit night was written " + count + " times");
			if (since >= 180) {
				helper.assertTrue(wrote(level, hall, line), "night fell with someone at the village and no lamplit line [" + line + "]");
				helper.assertTrue(!data.entry(hall).lamplit, "the lamplit night was written but is still owed");
				helper.succeed();
			}
		});
	}

	/**
	 * "The Shift Bell ring ... in the same loop [has] the same guard ... a hall nobody is near loads no chunk": at the
	 * start of the work day (RING_START), a far village nobody is near rings no bell: its bell, in the hall's own loaded
	 * (not ticking) chunk, never shakes; another far village's bell stands in a chunk that isn't loaded, and ringing it
	 * would load it: it stays unloaded. The test's own village, where someone is, still rings its bell (the fix didn't
	 * silence the villages people are at).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1114dReformBell"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1114dReformBell")
	public void aFarShiftBellStaysStillAndLoadsNothingWhileANearOneRings(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos farHall = far(helper, -8800, -8800);
		BlockPos farBell = farHall.offset(3, 0, 3); // (the same chunk)
		BlockPos otherHall = farHall.offset(-160, 0, 0);
		BlockPos otherBell = otherHall.offset(48, 0, 0); // (three chunks east of its hall)
		BlockPos nearHall = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos nearBell = helper.absolutePos(new BlockPos(5, 2, 2));
		helper.assertTrue(!loaded(level, farHall) && !loaded(level, otherHall) && !loaded(level, otherBell), "setup: the far chunks are already loaded");
		helper.assertTrue(level.isPositionEntityTicking(nearHall), "setup: the test's own village isn't entity-ticking");
		long time = level.getDayTime();
		long ring = at(time, ReformStories.RING_START + 5);
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			for (BlockPos h : new BlockPos[] {farHall, otherHall, nearHall}) {
				data.entry(h).bell = NO_BELL;
			}
			data.setDirty();
			if (loaded(level, farBell) && level.getBlockState(farBell).is(Blocks.BELL)) {
				level.setBlock(farBell, Blocks.AIR.defaultBlockState(), 2);
				level.setBlock(farBell.below(), Blocks.AIR.defaultBlockState(), 2);
			}
			dropFarHall(level, farHall);
			dropFarHall(level, otherHall);
		});
		farHall(helper, level, farHall);
		farHall(helper, level, otherHall);
		level.setBlock(farBell.below(), Blocks.STONE.defaultBlockState(), 2);
		level.setBlock(farBell, Blocks.BELL.defaultBlockState(), 2);
		helper.assertTrue(level.getBlockEntity(farBell) instanceof BellBlockEntity be && !be.shaking, "setup: no still bell at the far hall");
		helper.assertTrue(!loaded(level, otherBell), "setup: the other far bell's chunk got loaded");
		helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 2, 2), Blocks.BELL);
		helper.assertTrue(level.getBlockEntity(nearBell) instanceof BellBlockEntity be && !be.shaking, "setup: no still bell in the test's village");
		data.entry(farHall).bell = farBell.asLong();
		data.entry(otherHall).bell = otherBell.asLong();
		data.entry(nearHall).bell = nearBell.asLong();
		data.setDirty();
		helper.assertTrue(farBell.equals(ReformStories.bell(level, farHall)) && nearBell.equals(ReformStories.bell(level, nearHall)), "setup: the bells aren't saved");
		level.setDayTime(ring);

		int[] ticks = {0};
		boolean[] nearRang = {false};
		helper.onEachTick(() -> {
			ticks[0]++;
			level.setDayTime(ring); // (held in the ring window: three ring beats)
			helper.assertTrue(!level.isPositionEntityTicking(farHall), "setup: the far hall's chunk began ticking by itself");
			helper.assertTrue(!loaded(level, otherBell), "a far village nobody is near rang its Shift Bell and loaded the bell's chunk (tick " + ticks[0] + ")");
			helper.assertTrue(level.getBlockEntity(farBell) instanceof BellBlockEntity be && !be.shaking, "a far village nobody is near rang its Shift Bell (tick " + ticks[0] + ")");
			if (level.getBlockEntity(nearBell) instanceof BellBlockEntity be && be.shaking) {
				nearRang[0] = true;
			}
			if (ticks[0] >= 70) {
				helper.assertTrue(nearRang[0], "the Shift Bell of the village someone is at didn't ring at the start of the work day");
				helper.succeed();
			}
		});
	}
}
