package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
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

/**
 * QA (lane-c-1114-0053) bug tests, B1212's follow-up: B1212's spec says no quest check loads a hall's chunk (a hall
 * that isn't loaded keeps its saved quests), and its lead names the other Stories.halls loops. Two still load an
 * unloaded hall's chunk on the server thread: Stories.onKill (every kill a player makes, for each hall holding a kill
 * quest that counts anywhere) and RequestErrands.onRevived (a villager back from the grave, for each hall holding a
 * request to bring them back). Both fail until the mod is fixed.
 */
public class QaEdictsBug1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static Quest quest(ServerLevel level, BlockPos hall, String giver, @org.jetbrains.annotations.Nullable UUID villager, Objectives.Objective objective) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114c"), giver, Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(objective), new int[1], List.of());
		q.villager = villager;
		Stories.post(level, hall, q);
		return q;
	}

	/**
	 * A player kills a zombie far from an unloaded hall whose quest asks for zombies killed anywhere: the kill check
	 * doesn't load the hall's chunk, and the hall keeps its saved quest.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEdictsBug1114cKillAnywhere"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEdictsBug1114cKillAnywhere")
	public void qaAKillAnywhereLoadsNoUnloadedHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7200, 7200);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Quest q = quest(level, hall, "hall", null, new Objectives.Kill("minecraft:zombie", 3, true));
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(level, zombie, helper.makeMockServerPlayerInLevel());
		helper.assertTrue(!loaded(level, hall), "a kill loaded the unloaded hall's chunk");
		helper.assertTrue(Stories.open(level, hall).equals(List.of(q)), "the quest: " + Stories.open(level, hall));
		zombie.discard();
		helper.succeed();
	}

	/**
	 * A villager comes back from the grave while the hall holding the request to bring them back is in an unloaded
	 * chunk: the request check doesn't load it, and the request is still there for when it loads.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEdictsBug1114cRevived"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEdictsBug1114cRevived")
	public void qaARevivalLoadsNoUnloadedHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7200, -7200);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		UUID was = UUID.randomUUID();
		Quest q = quest(level, hall, "villager", UUID.randomUUID(), new RequestErrands.Revive(Optional.of(was), "Bo"));
		Villager back = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		back.setNoAi(true);
		RequestErrands.onRevived(level, was, back);
		helper.assertTrue(!loaded(level, hall), "a revival loaded the unloaded hall's chunk");
		helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(q)), "the request: " + PersonalRequests.open(level, hall));
		back.discard();
		helper.succeed();
	}
}
