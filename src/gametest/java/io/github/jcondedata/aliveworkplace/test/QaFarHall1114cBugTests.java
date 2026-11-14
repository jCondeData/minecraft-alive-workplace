package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
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

/**
 * QA (lane-c-1114-0653), the B1212 sweep: a hall loop the B1220-B1225 fixes didn't touch. ReformStories.tick (every 20
 * ticks, for every hall with a reform story's ending) guards with level.isLoaded(hall) only, which B1221 showed is not
 * enough (a chunk nobody is near can be loaded: waiting to unload, or the border ring of a player's view). On a drill
 * day its first beat reads every block 32 round the hall (ReformStories.dummies, level.getBlockState), loading up to 25
 * chunks on the server thread.
 */
public class QaFarHall1114cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** Holds a chunk loaded but not ticking, like the outer ring of a player's view. */
	private static final TicketType<ChunkPos> BORDER = TicketType.create("qa1114c_border", Comparator.comparingLong(ChunkPos::toLong));

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/**
	 * A far village's drill day (31.22's "drill_day" ending) is on, and its hall's chunk is loaded at the edge of someone's
	 * view (loaded, not ticking: nobody is near). Expected (B1212, B1221, B1225's rule: "a village whose hall isn't
	 * entity-ticking loads nothing"): the drill's tick loads no chunk round it; here the chunk 32 blocks out, within the
	 * dummies' reach, that nothing else loads, stays unloaded over three drill ticks.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFarHall1114cDrill"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarHall1114cDrill")
	public void aFarDrillDayLoadsNoChunkRoundItsHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos hall = new BlockPos(origin.getX() + 8800, 70, origin.getZ() - 8800);
		BlockPos out = hall.offset(32, 0, 32);
		ChunkPos chunk = new ChunkPos(hall);
		helper.assertTrue(!loaded(level, hall) && !loaded(level, out), "setup: the far chunks are already loaded");
		long time = level.getDayTime();
		ReformStories.Data data = ReformStories.Data.get(level);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			data.entry(hall).drillDay = -1;
			data.entry(hall).drillDone = false;
			data.setDirty();
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			level.getChunkSource().removeRegionTicket(BORDER, chunk, 0, chunk);
		});
		// The hall's chunk at the edge of a view: loaded (full), not ticking; its neighbours aren't loaded.
		level.getChunkSource().addRegionTicket(BORDER, chunk, 0, chunk);
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		helper.assertTrue(level.isLoaded(hall) && !level.isPositionEntityTicking(hall), "setup: the hall's chunk isn't loaded-but-not-ticking");
		// Drill time today, and today is the far village's drill day.
		level.setDayTime(Math.floorDiv(time, 24000L) * 24000L + ReformStories.DRILL_START + 100);
		ReformStories.Entry e = data.entry(hall);
		e.drillDay = Chronicle.day(level);
		e.drillDone = false;
		data.setDirty();

		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			helper.assertTrue(!loaded(level, out), "the drill loaded a chunk 32 blocks from the far hall nobody is near (tick " + ticks[0] + ")");
			if (ticks[0] >= 70) {
				helper.assertTrue(loaded(level, hall), "setup: the hall's chunk didn't stay loaded");
				helper.succeed();
			}
		});
	}
}
