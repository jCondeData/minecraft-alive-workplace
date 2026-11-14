package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.story.Objectives;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (qa-lane-c-1112-0052), B1212's Expected "no chunk is loaded by a quest check" on the paths the fix didn't cover:
 * both name the far village with {@code VillageHalls.name}, which looks the hall's block entity up and so loads its chunk
 * on the server thread. Failing tests for the builder (branch tests/unloaded-hall-1112c).
 */
public class QaUnloadedHall1112cBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + 7600);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static Quest personal(ServerLevel level, UUID giver, Objectives.Objective objective) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1112c"), "villager", Optional.empty(), "Lia", now, now + 24000L * 5,
			List.of(objective), new int[1], List.of());
		q.villager = giver;
		return q;
	}

	/**
	 * {@code /workplace quests} near this village, while the player tracks a request of a far village whose hall isn't
	 * loaded: the tracked request is listed, and the far hall's chunk stays unloaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1112cBugChatList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1112cBugChatList")
	public void listingQuestsWithAFarTrackedQuestDoesNotLoadItsHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		// a hall another batch left near this area (PrinterGameTests' 'Bring 16 Bread', in a full build) adds its quests
		// to the list as the player's nearest village
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 0);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 5));
		Quest quest = personal(level, UUID.randomUUID(), new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty()));
		quest.helpers.put(me.getUUID(), 0);
		Stories.post(level, hall, quest);
		helper.assertTrue(QuestJournal.trackById(me, quest.id) && !loaded(level, hall), "tracking by id failed or loaded the hall");
		List<Component> lines = QuestJournal.chatList(me);
		boolean listed = lines.stream().anyMatch(c -> c.getString().contains("3"));
		helper.assertTrue(lines.size() == 2 && listed, "the list: " + lines.stream().map(Component::getString).toList());
		helper.assertTrue(!loaded(level, hall), "/workplace quests loaded the far tracked quest's hall chunk (to name its village)");
		me.discard();
		helper.succeed();
	}

	/**
	 * A helper who lost the reply right-clicks the relative empty-handed, in the relative's own village far from the
	 * giver's (31.10): the reply is written again, and the giver's far hall's chunk stays unloaded.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1112cBugReplyAgain"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1112cBugReplyAgain")
	public void aReplyWrittenAgainFarFromTheGiverDoesNotLoadTheirHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 600);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		Villager hal = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Hal");
		ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
		RequestErrands.Deliver deliver = new RequestErrands.Deliver(Optional.empty(), Optional.of(hal.getUUID()), "Hal", "Ashford", "brother");
		Quest quest = personal(level, UUID.randomUUID(), deliver);
		quest.helpers.put(me.getUUID(), 0);
		quest.progress[0] = 1; // the letter was delivered; the reply is on its way back
		Stories.post(level, hall, quest);
		helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, hal), "the lost reply wasn't written again");
		helper.assertTrue(RequestErrands.isCarried(me.getMainHandItem()) || me.getInventory().items.stream().anyMatch(RequestErrands::isCarried),
			"no reply in the inventory");
		helper.assertTrue(!loaded(level, hall), "writing the reply again loaded the giver's far hall chunk (to name its village)");
		me.discard();
		helper.succeed();
	}
}
