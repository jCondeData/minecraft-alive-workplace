package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-11-09, lane d), verifying B1091 and B1102 from their specs, without Cobblemon: a won battle counts for the
 * village the trainer belongs to (its nearest hall), not for another village, and for no village at all when the trainer
 * stands outside every village; each win of an ordinary trainer counts, so two wins in a day count two; a press of
 * Challenge without Cobblemon is refused and neither spends a Leader's day nor counts.
 */
public class QaLeaderVerify1109dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	/** A hall at {@code at} whose own rounds post nothing, with a "beat 5 trainers" quest on its board. */
	private static Quest battleQuest(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/battle"), "hall", Optional.empty(), "Ana", now,
			now + VillageQuests.LASTS, List.of(new Objectives.Battle(5)), new int[1], List.of());
		Stories.post(level, helper.absolutePos(at), quest);
		return quest;
	}

	private static void radius(GameTestHelper helper, int r) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = r;
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager trainer = helper.spawn(EntityType.VILLAGER, at);
		trainer.setNoAi(true);
		trainer.setVillagerData(trainer.getVillagerData().setProfession(job).setLevel(1));
		return trainer;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/**
	 * Two villages 23 blocks apart (radius 8): a win over village A's trainer counts for A's quest only, a win over B's
	 * Leader for B's only, and a win over a trainer between them, in neither village, counts for neither.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaLeaderVerifyVillages"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaLeaderVerifyVillages")
	public void aWinCountsOnlyForTheTrainersOwnVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 8);
		ServerLevel level = helper.getLevel();
		Quest a = battleQuest(helper, new BlockPos(3, 2, 3));
		Quest b = battleQuest(helper, new BlockPos(26, 2, 26));
		Villager ofA = trainer(helper, new BlockPos(5, 2, 5), ModVillagers.TRAINER);
		Villager leaderOfB = trainer(helper, new BlockPos(24, 2, 24), ModVillagers.TRAINER_LEADER);
		Villager between = trainer(helper, new BlockPos(14, 2, 14), ModVillagers.TRAINER);
		ServerPlayer player = player(helper);

		Trainers.battleOver(level.getServer(), ofA.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(a.progress[0], 1, "village A's quest after beating A's trainer");
		helper.assertValueEqual(b.progress[0], 0, "village B's quest after beating A's trainer");

		Trainers.battleOver(level.getServer(), leaderOfB.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(a.progress[0], 1, "village A's quest after beating B's Leader");
		helper.assertValueEqual(b.progress[0], 1, "village B's quest after beating B's Leader");

		Trainers.battleOver(level.getServer(), between.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(a.progress[0], 1, "village A's quest after beating a trainer of no village");
		helper.assertValueEqual(b.progress[0], 1, "village B's quest after beating a trainer of no village");
		helper.succeed();
	}

	/** An ordinary trainer can be battled again the same day: two wins count two, a loss in between nothing. */
	//$ gametest_ticks_batch BIG '60' '"qaLeaderVerifyTwoWins"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaLeaderVerifyTwoWins")
	public void eachWinOverAnOrdinaryTrainerCounts(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 16);
		ServerLevel level = helper.getLevel();
		Quest quest = battleQuest(helper, new BlockPos(9, 2, 9));
		Villager trainer = trainer(helper, new BlockPos(4, 2, 4), ModVillagers.TRAINER);
		ServerPlayer player = player(helper);

		Trainers.battleOver(level.getServer(), trainer.getUUID(), player.getUUID(), true);
		Trainers.battleOver(level.getServer(), trainer.getUUID(), player.getUUID(), false);
		Trainers.battleOver(level.getServer(), trainer.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(quest.progress[0], 2, "battle quest after win, loss, win against one trainer in a day");
		long[] record = Trainers.record(player.getUUID(), trainer);
		helper.assertValueEqual(record[0], 2L, "wins on the record");
		helper.assertValueEqual(record[1], 1L, "losses on the record");
		helper.succeed();
	}

	/**
	 * Without Cobblemon, Challenge on the Leader is refused with the "needs Cobblemon" sentence: it spends no challenge
	 * of the day (nothing written on the Leader) and counts nothing for the quest, however often it is pressed.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaLeaderVerifyNoCobblemon"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaLeaderVerifyNoCobblemon")
	public void aChallengeWithoutCobblemonSpendsAndCountsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 16);
		Quest quest = battleQuest(helper, new BlockPos(9, 2, 9));
		Villager leader = trainer(helper, new BlockPos(4, 2, 4), ModVillagers.TRAINER_LEADER);
		ServerPlayer player = player(helper);

		for (int i = 0; i < 3; i++) {
			Trainers.challenge(player, leader);
		}
		helper.assertValueEqual(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.trainer.no_cobblemon").getString().contains("aliveworkplace"),
			false, "the no-Cobblemon refusal has English text");
		helper.assertValueEqual(Trainers.refusal(player, leader).getString(),
			net.minecraft.network.chat.Component.translatable("message.aliveworkplace.trainer.no_cobblemon").getString(), "the refusal after three presses");
		helper.assertFalse(Trainers.challengedToday(player, leader), "a refused press without Cobblemon spent the Leader's day");
		helper.assertTrue(ModAttachments.LEADER_CHALLENGES.getOrElse(leader, java.util.Map.of()).isEmpty(), "a refused press wrote a challenge on the Leader");
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after three refused presses");
		helper.succeed();
	}
}
