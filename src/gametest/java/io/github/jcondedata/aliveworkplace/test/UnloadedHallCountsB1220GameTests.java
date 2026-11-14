package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug B1220 (bug lane, lane-a-1114-0322): a kill or a revival never loads an unloaded Village Hall's chunk any more. What
 * they do instead: a kill still counts towards the far hall's "anywhere" quest, and a quest it finishes is paid at the
 * hall's next round; a revival that answers a far hall's request is kept and answers it at the hall's next round.
 */
public class UnloadedHallCountsB1220GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static void cleanUp(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
		});
	}

	/** The far hall's chunk loads (someone went there) with its hall, and the hall's round runs. */
	private static VillageHallBlockEntity loadAndRound(ServerLevel level, BlockPos hall) {
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // no new quest this morning
		Stories.round(level, hall, entity);
		return entity;
	}

	/**
	 * The last zombie of a far hall's "kill anywhere" quest: it counts (the quest is full) without loading the hall, and
	 * the quest is paid and taken down at the hall's next round, once it loads.
	 */
	//$ gametest_ticks_batch AREA '60' '"b1220KillPaidLater"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b1220KillPaidLater")
	public void aKillFarFromAnUnloadedHallCountsAndIsPaidWhenItLoads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7400, -7400);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/b1220"), "hall", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Kill("minecraft:zombie", 1, true)), new int[1], List.of());
		Stories.post(level, hall, q);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(level, zombie, helper.makeMockServerPlayerInLevel());
		zombie.discard();
		helper.assertTrue(!loaded(level, hall), "the kill loaded the unloaded hall's chunk");
		helper.assertTrue(q.progress[0] == 1 && q.done(), "the kill didn't count: " + q.progress[0]);
		helper.assertTrue(Stories.open(level, hall).equals(List.of(q)), "paid before the hall loaded: " + Stories.open(level, hall));

		loadAndRound(level, hall);
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "not paid at the hall's round: " + Stories.open(level, hall));
		helper.assertTrue(Stories.finished(level, hall, q.file), "the quest isn't finished in the village");
		helper.succeed();
	}

	/**
	 * A villager comes back while the hall with the request to bring them back is unloaded: the request is kept as it is,
	 * and at the hall's next round, once it loads, the revival answers it (it moves on and comes down).
	 */
	//$ gametest_ticks_batch AREA '60' '"b1220RevivalAnsweredLater"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b1220RevivalAnsweredLater")
	public void aRevivalWhileTheHallIsUnloadedAnswersTheRequestWhenItLoads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7400, 7400);
		cleanUp(helper, level, hall);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		UUID was = UUID.randomUUID();
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/b1220"), "villager", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new RequestErrands.Revive(Optional.of(was), "Bo")), new int[1], List.of());
		q.villager = UUID.randomUUID();
		Stories.post(level, hall, q);
		Villager back = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		back.setNoAi(true);
		RequestErrands.onRevived(level, was, back);
		helper.assertTrue(!loaded(level, hall), "the revival loaded the unloaded hall's chunk");
		helper.assertTrue(q.progress[0] == 0 && PersonalRequests.open(level, hall).equals(List.of(q)), "the request: " + PersonalRequests.open(level, hall));

		loadAndRound(level, hall);
		back.discard();
		helper.assertTrue(q.progress[0] == 1, "the kept revival didn't answer the request");
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "the answered request is still up: " + Stories.open(level, hall));
		helper.succeed();
	}
}
