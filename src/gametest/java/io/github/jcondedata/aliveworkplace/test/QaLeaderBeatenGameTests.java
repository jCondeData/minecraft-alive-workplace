package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
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
 * B1102: pressing Challenge on a Trainer Leader counted as beating a village trainer (the village's battle quest, its
 * battle reform steps), before the once-a-day check and before any battle. A trainer counts only when the player wins
 * the battle ({@link Trainers#battleOver}), a Leader like any other trainer, and only once per win.
 */
public class QaLeaderBeatenGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall (radius 16) whose own rounds post nothing, with a "beat 5 trainers" quest on its board. */
	private static Quest battleQuest(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/battle"), "hall", Optional.empty(), "Ana", now,
			now + VillageQuests.LASTS, List.of(new Objectives.Battle(5)), new int[1], List.of());
		Stories.post(level, helper.absolutePos(HALL), quest);
		return quest;
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

	/** Pressing Challenge on a Leader counts nothing; a lost battle counts nothing; a won one counts once. */
	//$ gametest_ticks_batch AREA '60' '"qaLeaderBeaten"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLeaderBeaten")
	public void aLeaderCountsOnlyWhenBeaten(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Quest quest = battleQuest(helper);
		Villager leader = trainer(helper, new BlockPos(4, 2, 4), ModVillagers.TRAINER_LEADER);
		helper.assertTrue(Trainers.isLeader(leader), "setup: not a Leader");
		ServerPlayer player = player(helper);

		Trainers.challenge(player, leader);
		Trainers.challenge(player, leader);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after two Challenge presses and no battle");

		Trainers.battleOver(level.getServer(), leader.getUUID(), player.getUUID(), false);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after losing to the Leader");

		Trainers.battleOver(level.getServer(), leader.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after beating the Leader once");
		helper.succeed();
	}

	/** An ordinary village trainer beaten counts too, through the same win. */
	//$ gametest_ticks_batch AREA '60' '"qaTrainerBeaten"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaTrainerBeaten")
	public void anOrdinaryTrainerCountsWhenBeaten(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Quest quest = battleQuest(helper);
		Villager trainer = trainer(helper, new BlockPos(4, 2, 4), ModVillagers.TRAINER);
		ServerPlayer player = player(helper);

		Trainers.challenge(player, trainer);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after a Challenge press and no battle");
		Trainers.battleOver(level.getServer(), trainer.getUUID(), player.getUUID(), true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after beating the trainer");
		helper.succeed();
	}
}
