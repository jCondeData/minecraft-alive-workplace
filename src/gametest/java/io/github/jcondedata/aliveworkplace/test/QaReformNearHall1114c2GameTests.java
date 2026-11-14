package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1114-1252), B1232's other side: ReformStories.tick now skips a hall that isn't entity-ticking (nobody near).
 * Its fix test proves a far drill day loads nothing; these prove the fix doesn't silence a hall someone is near: its Shift
 * Bell rings, its first lamplit night and drill day are written down and its villagers drill; and a far hall's lamplit
 * line and bell, held back while nobody was near, come once someone is.
 */
public class QaReformNearHall1114c2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** Holds a chunk loaded but not ticking, like the outer ring of a player's view. */
	private static final TicketType<ChunkPos> BORDER = TicketType.create("qa1114c2_border", Comparator.comparingLong(ChunkPos::toLong));
	private static final BlockPos HALL = new BlockPos(4, 2, 4);
	private static final BlockPos BELL = new BlockPos(4, 2, 8);
	private static final BlockPos DUMMY = new BlockPos(10, 2, 4);

	private static List<String> lines(ServerLevel level, BlockPos hall, String part) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(e -> e.text().getString()).filter(t -> t.contains(part)).toList();
	}

	private static String lamplit(ServerLevel level, BlockPos hall) {
		return "The first lamplit night in " + VillageHalls.name(level, hall).getString() + ": the streets glowed, and nobody hurried home.";
	}

	private static void restore(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			ReformStories.Entry e = data.entry(hall);
			e.drillDay = -1;
			e.drillDone = false;
			e.lamplit = false;
			e.bell = Long.MIN_VALUE;
			data.setDirty();
		});
	}

	/**
	 * A hall in the test area (entity-ticking: someone is near) with a Shift Bell, a first lamplit night due and a drill
	 * day: the bell rings at the work day's start and end, the lamplit line is written once, a villager by the dummy
	 * spars, and the drill's end is written once: "Drill day in X: 1 villager sparred at the dummies."
	 */
	//$ gametest_ticks_batch AREA '100' '"qaReformNear1114c2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformNear1114c2")
	public void aHallSomeoneIsNearStillRingsWritesItsLinesAndDrills(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		restore(helper, hall);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BELL, Blocks.BELL);
		helper.setBlock(DUMMY, ModBlocks.TRAINING_DUMMY);
		Villager dora = QaMoverResidents1110cGameTests.villager(helper, DUMMY.south(), "Dora");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.isPositionEntityTicking(hall), "setup: the test area's hall isn't entity-ticking");
			ReformStories.Data data = ReformStories.Data.get(level);
			ReformStories.Entry e = data.entry(hall);
			e.bell = helper.absolutePos(BELL).asLong();
			long day = Math.floorDiv(level.getDayTime(), 24000L) + 1;

			level.setDayTime(day * 24000L + ReformStories.RING_START + 5);
			int rings = ReformStories.RINGS.get();
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings + 1, "the Shift Bell of a hall someone is near didn't ring the work day in");

			level.setDayTime(day * 24000L + ReformStories.DRILL_START + 100);
			e.drillDay = Chronicle.day(level);
			e.drillDone = false;
			ReformStories.forget(level, hall);
			int drilled = ReformStories.DRILLED.get();
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.DRILLED.get() > drilled, "Dora, standing by the Training Dummy of a hall someone is near, didn't spar on drill day");

			level.setDayTime(day * 24000L + ReformStories.DRILL_END + 20);
			ReformStories.tick(level);
			ReformStories.tick(level);
			String village = VillageHalls.name(level, hall).getString();
			List<String> drill = lines(level, hall, "Drill day in ");
			helper.assertTrue(drill.equals(List.of("Drill day in " + village + ": 1 villager sparred at the dummies.")),
				"the drill day's line at a hall someone is near: " + drill);
			helper.assertTrue(e.drillDone, "the drill day of a hall someone is near never ended");

			level.setDayTime(day * 24000L + ReformStories.RING_END + 5);
			rings = ReformStories.RINGS.get();
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings + 1, "the Shift Bell of a hall someone is near didn't ring the work day out");

			e.lamplit = true;
			level.setDayTime(day * 24000L + ReformStories.NIGHT + 100);
			ReformStories.tick(level);
			ReformStories.tick(level);
			helper.assertTrue(lines(level, hall, "lamplit night").equals(List.of(lamplit(level, hall))),
				"the first lamplit night at a hall someone is near: " + lines(level, hall, "lamplit night"));
			helper.assertFalse(e.lamplit, "the lamplit line is still due after it was written");
			dora.discard();
			helper.succeed();
		});
	}

	/**
	 * A far hall nobody is near (its chunk loaded at the edge of a view, not ticking) has its first lamplit night due and a
	 * Shift Bell: at night no line is written and at the work day's start the bell doesn't ring, and the line stays due.
	 * Someone comes near (the chunk ticks): on a later night the line comes, once, and the bell rings in the next morning.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaReformFarThenNear1114c2"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaReformFarThenNear1114c2")
	public void aFarHallsLamplitLineAndBellComeOnceSomeoneIsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos hall = new BlockPos(((origin.getX() + 8600) >> 4 << 4) + 4, 70, ((origin.getZ() - 8600) >> 4 << 4) + 4);
		BlockPos bell = hall.offset(4, 0, 4); // the same chunk
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "setup: the far chunk is already loaded");
		restore(helper, hall);
		Leftovers.after(helper, () -> {
			if (level.isLoaded(hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(bell, Blocks.AIR.defaultBlockState(), 3);
			}
			level.setChunkForced(chunk.x, chunk.z, false);
			level.getChunkSource().removeRegionTicket(BORDER, chunk, 0, chunk);
		});
		level.getChunkSource().addRegionTicket(BORDER, chunk, 0, chunk);
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
		helper.assertTrue(level.isLoaded(hall) && !level.isPositionEntityTicking(hall), "setup: the hall's chunk isn't loaded-but-not-ticking");
		ReformStories.Data data = ReformStories.Data.get(level);
		ReformStories.Entry e = data.entry(hall);
		e.lamplit = true;
		e.bell = bell.asLong();
		long day = Math.floorDiv(level.getDayTime(), 24000L) + 1;

		level.setDayTime(day * 24000L + ReformStories.NIGHT + 100);
		ReformStories.tick(level);
		helper.assertTrue(lines(level, hall, "lamplit night").isEmpty(), "the lamplit line was written at a far hall nobody is near");
		helper.assertTrue(e.lamplit, "the far hall's lamplit line is no longer due though nobody saw it written");
		level.setDayTime((day + 1) * 24000L + ReformStories.RING_START + 5);
		int rings = ReformStories.RINGS.get();
		ReformStories.tick(level);
		helper.assertTrue(ReformStories.RINGS.get() == rings, "the Shift Bell of a far hall nobody is near rang");

		level.setChunkForced(chunk.x, chunk.z, true); // someone comes near
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || !level.isPositionEntityTicking(hall)) {
				return;
			}
			done[0] = true;
			level.setDayTime((day + 1) * 24000L + ReformStories.NIGHT + 100);
			ReformStories.tick(level);
			ReformStories.tick(level);
			helper.assertTrue(lines(level, hall, "lamplit night").equals(List.of(lamplit(level, hall))),
				"the far hall's first lamplit night, held back while nobody was near, isn't written once on a later night someone is: " + lines(level, hall, "lamplit night"));
			helper.assertFalse(e.lamplit, "the lamplit line is still due after it was written");
			level.setDayTime((day + 2) * 24000L + ReformStories.RING_START + 5);
			int before = ReformStories.RINGS.get();
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == before + 1, "the far hall's Shift Bell doesn't ring once someone is near");
			helper.succeed();
		});
	}
}
