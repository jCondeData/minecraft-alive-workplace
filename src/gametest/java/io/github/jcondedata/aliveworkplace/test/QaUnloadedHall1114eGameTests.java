package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane E QA (lane-e-1114-0113), B1212 from its spec ("no chunk is loaded by a quest check; a hall that isn't loaded keeps
 * its saved quests"), from the sides its own tests leave out: a hall a player visited and then left (its chunk loaded
 * once, then waiting to unload while the quest checks run), and the quest lookups by id a player's click makes.
 */
public class QaUnloadedHall1114eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A villager's own request (31.9) to bring 3 bread, posted at {@code hall}. */
	private static Quest request(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114e"), "villager", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty())), new int[1], List.of());
		q.villager = UUID.randomUUID();
		Stories.post(level, hall, q);
		return q;
	}

	/**
	 * A hall with a posted request, visited (its chunk loaded) and left: with the level's own quest ticks running (the
	 * errand tick every 40) its chunk unloads as soon as a control hall's beside it with no stories, and the request is
	 * still listed, unloaded, afterwards.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1114eVisitedHallUnloads"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1114eVisitedHallUnloads")
	public void aVisitedHallWithQuestsUnloadsOnceLeft(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7200, -7200);
		BlockPos control = far(helper, 7200 + 96, -7200);
		helper.assertTrue(!loaded(level, hall) && !loaded(level, control), "setup: the far chunks are already loaded");
		Leftovers.after(helper, () -> {
			Stories.Data.get(level).remove(hall);
			for (BlockPos h : List.of(hall, control)) {
				if (loaded(level, h) && level.getBlockState(h).is(ModBlocks.VILLAGE_HALL)) {
					level.setBlock(h, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		});
		level.getChunk(hall);
		level.getChunk(control);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		level.setBlock(control, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		Quest quest = request(level, hall);
		helper.assertTrue(Stories.open(level, hall).equals(List.of(quest)), "setup: the request isn't open: " + Stories.open(level, hall));

		helper.succeedWhen(() -> {
			helper.assertTrue(!loaded(level, control), "the control hall's chunk hasn't unloaded yet");
			helper.assertTrue(!loaded(level, hall), "the hall with a quest is kept loaded while the control hall's beside it unloaded");
			helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(quest)) && !loaded(level, hall),
				"once unloaded, its request: " + PersonalRequests.open(level, hall));
		});
	}

	/**
	 * A player's clicks that look a quest up by id (the journal's "track", a request's "accept") over every hall: an id
	 * that is no quest's, and an id that is the far unloaded hall's own request, never load the hall's chunk; the journal
	 * still finds the far one.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114eLookupsUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eLookupsUnloaded")
	public void lookingAQuestUpByIdNeverLoadsAFarHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7200, -7200);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		Quest quest = request(level, hall);
		var player = helper.makeMockServerPlayerInLevel();

		helper.assertTrue(!QuestJournal.trackById(player, UUID.randomUUID()), "tracked a quest that doesn't exist");
		helper.assertTrue(!loaded(level, hall), "tracking an unknown id loaded the far hall's chunk");
		helper.assertTrue(!PersonalRequests.accept(player, UUID.randomUUID()), "accepted a request that doesn't exist");
		helper.assertTrue(!loaded(level, hall), "accepting an unknown id loaded the far hall's chunk");
		helper.assertTrue(QuestJournal.trackById(player, quest.id), "the journal didn't find the far hall's request");
		helper.assertTrue(!loaded(level, hall), "tracking the far hall's request loaded its chunk");
		helper.succeed();
	}
}
