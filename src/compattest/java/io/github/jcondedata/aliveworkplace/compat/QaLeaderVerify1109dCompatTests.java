package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
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
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (2026-11-09, lane d), verifying B1091 (only a challenge that starts a battle uses up a Trainer Leader's one a day)
 * and B1102 (a trainer counts toward the village's battle quest only when the player wins) from their specs. Each refusal
 * reason on its own (no Pokémon, the player already battling another trainer, the Leader busy with another player)
 * leaves the day's challenge, so a later valid press starts a battle; a started battle then a same-day press is refused
 * with the "Come back tomorrow!" sentence until the last tick of the day and allowed from the first tick of the next;
 * presses, losses and refusals count nothing; a win counts exactly one even after the battle's end events have had time
 * to arrive; every sentence the player reads is the lang file's.
 */
public class QaLeaderVerify1109dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall (radius 16) whose own rounds post nothing, with a "beat 5 trainers" quest on its board. */
	private static Quest battleQuest(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		PartnerShowsCompatTests.after(helper, () -> VillageHalls.RADIUS = radius);
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

	/** The lang file's English for {@code key}, its %s filled with {@code args} in order. */
	private static String english(String key, String... args) {
		String text = Language.getInstance().getOrDefault(key);
		for (String arg : args) {
			text = text.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(arg));
		}
		return text;
	}

	private static void near(ServerPlayer player, Villager trainer) {
		player.moveTo(trainer.getX() - 1.5, trainer.getY(), trainer.getZ());
	}

	private static boolean spent(ServerPlayer player, Villager leader) {
		return ModAttachments.LEADER_CHALLENGES.getOrElse(leader, Map.of()).containsKey(player.getUUID());
	}

	/**
	 * B has no Pokémon: Challenge is refused with the no-team sentence, writes nothing on the Leader (not even after a
	 * reload of the Leader), counts nothing. With a Pokémon in the party, B's next press the same day starts the battle.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLeaderVerifyNoTeam"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLeaderVerifyNoTeam")
	public void aNoTeamRefusalLeavesTheDay(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qav1ldrA");
		Quest quest = battleQuest(helper);
		List<String> seenB = new ArrayList<>();
		ServerPlayer b = QaPokemonScreens1109dCompatTests.joiner(helper, f.players(), seenB, "qav1ldrB");
		Villager leader = f.trainer();
		near(b, leader);

		Trainers.challenge(b, leader);
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(b), "B battled with no Pokémon");
		helper.assertTrue(seenB.contains(english("message.aliveworkplace.trainer.no_team")), "B's refusal line, saw " + seenB);
		helper.assertFalse(spent(b, leader), "a no-team refusal spent B's challenge of the day");
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(leader.saveWithoutId(new CompoundTag()));
		helper.assertFalse(Trainers.challengedToday(b, copy), "a no-team refusal is a spent challenge after a reload");
		copy.discard();
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after a no-team refusal");

		QaPokemonScreens1109dCompatTests.party(b, "squirtle level=30");
		Trainers.challenge(b, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(b), "B's challenge after the refusal didn't start a battle");
		helper.assertTrue(CobblemonTrainers.isBattling(leader), "the Leader isn't in B's battle");
		helper.assertTrue(Trainers.challengedToday(b, leader), "a started battle didn't use up B's day");
		helper.assertValueEqual(quest.progress[0], 0, "battle quest when B's battle has only started");
		QaPokemonScreens1109dCompatTests.finish(b, false);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after B lost to the Leader");
		helper.assertTrue(seenB.contains(english("message.aliveworkplace.trainer.lost", leader.getDisplayName().getString())),
			"B's 'won this time' line, saw " + seenB);
		helper.succeed();
	}

	/**
	 * A is mid-battle with an ordinary trainer and presses Challenge on the Leader: no battle with the Leader, nothing
	 * spent. A loses to the trainer (counts 0), then challenges the Leader the same day: the battle starts, and the win
	 * counts 1.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLeaderVerifyInBattle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLeaderVerifyInBattle")
	public void anInBattleRefusalLeavesTheDay(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qav2ldrA");
		Quest quest = battleQuest(helper);
		Villager leader = f.trainer();
		ServerPlayer a = f.player();
		BlockPos post = new BlockPos(13, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager other = helper.spawn(EntityType.VILLAGER, post.south());
		other.setNoAi(true);
		Jobs.employ(helper.getLevel(), other, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		helper.assertFalse(Trainers.isLeader(other), "setup: the second trainer is a Leader");

		near(a, other);
		Trainers.challenge(a, other);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(a), "setup: A's battle with the trainer didn't start");
		near(a, leader);
		Trainers.challenge(a, leader);
		helper.assertFalse(CobblemonTrainers.isBattling(leader), "the Leader battled A while A was in another battle");
		helper.assertFalse(spent(a, leader), "a press while already battling spent A's challenge of the day");
		QaPokemonScreens1109dCompatTests.finish(a, false);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after losing to the ordinary trainer");

		Trainers.challenge(a, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(a) && CobblemonTrainers.isBattling(leader),
			"A's challenge after the in-battle refusal didn't start a battle with the Leader");
		QaPokemonScreens1109dCompatTests.finish(a, true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after beating the Leader");
		helper.succeed();
	}

	/**
	 * B battles the Leader; A's press meanwhile is refused as busy (the lang file's sentence) and spends nothing. B wins
	 * (1). A then challenges and wins (2). B's second press that day reads "Come back tomorrow!" and counts nothing. Two
	 * seconds later, once any late end-of-battle events have run, the quest still says 2 and each record one win.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLeaderVerifyBusy"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLeaderVerifyBusy")
	public void aBusyRefusalLeavesTheDayAndEachWinCountsOnce(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qav3ldrA");
		Quest quest = battleQuest(helper);
		Villager leader = f.trainer();
		ServerPlayer a = f.player();
		List<String> seenB = new ArrayList<>();
		ServerPlayer b = QaPokemonScreens1109dCompatTests.joiner(helper, f.players(), seenB, "qav3ldrB");
		QaPokemonScreens1109dCompatTests.party(b, "squirtle level=30");
		near(b, leader);
		near(a, leader);
		String name = leader.getDisplayName().getString();

		Trainers.challenge(b, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(b), "setup: B's battle with the Leader didn't start");
		Trainers.challenge(a, leader);
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(a), "A battled a busy Leader");
		helper.assertTrue(f.seen().contains(english("message.aliveworkplace.trainer.busy", name)), "A's busy line, saw " + f.seen());
		helper.assertFalse(spent(a, leader), "a busy refusal spent A's challenge of the day");
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after a busy refusal");

		QaPokemonScreens1109dCompatTests.finish(b, true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after B beat the Leader");
		Trainers.challenge(a, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(a), "A's challenge after the busy refusal didn't start");
		QaPokemonScreens1109dCompatTests.finish(a, true);
		helper.assertValueEqual(quest.progress[0], 2, "battle quest after A beat the Leader too");

		int before = seenB.size();
		Trainers.challenge(b, leader);
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(b), "B battled the Leader twice in a day");
		helper.assertTrue(seenB.subList(before, seenB.size()).contains(english("message.aliveworkplace.trainer.leader_tomorrow", name)),
			"B's 'Come back tomorrow!' line, saw " + seenB.subList(before, seenB.size()));
		helper.assertValueEqual(quest.progress[0], 2, "battle quest after B's refused second press");

		helper.runAfterDelay(40, () -> {
			helper.assertValueEqual(quest.progress[0], 2, "battle quest two seconds after the two wins");
			helper.assertValueEqual(Trainers.record(a.getUUID(), leader)[0], 1L, "A's wins on the Leader's record");
			helper.assertValueEqual(Trainers.record(b.getUUID(), leader)[0], 1L, "B's wins on the Leader's record");
			helper.succeed();
		});
	}

	/**
	 * The day boundary from both sides (a Minecraft day turns over at sunrise, day time 24000): after a lost battle on
	 * day 0, a press on the day's last tick (23999) is refused and counts nothing; on the next day's first tick (24000)
	 * the battle starts, and its win counts 1.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLeaderVerifyDay"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLeaderVerifyDay")
	public void theNextDayStartsAtSunriseNotBefore(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qav4ldrA");
		Quest quest = battleQuest(helper);
		Villager leader = f.trainer();
		ServerPlayer a = f.player();
		near(a, leader);
		PartnerShowsCompatTests.after(helper, () -> helper.setDayTime(2000));
		String tomorrow = english("message.aliveworkplace.trainer.leader_tomorrow", leader.getDisplayName().getString());

		Trainers.challenge(a, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(a), "setup: A's first battle didn't start");
		QaPokemonScreens1109dCompatTests.finish(a, false);
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after losing");

		helper.setDayTime(23999);
		int before = f.seen().size();
		Trainers.challenge(a, leader);
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(a), "A battled again on the day's last tick");
		helper.assertTrue(f.seen().subList(before, f.seen().size()).contains(tomorrow), "the day's last tick: saw " + f.seen().subList(before, f.seen().size()));
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after the refused last-tick press");

		helper.setDayTime(24000);
		helper.assertFalse(Trainers.challengedToday(a, leader), "the next day's first tick still counts as today");
		Trainers.challenge(a, leader);
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(a), "A's challenge on the next day didn't start");
		QaPokemonScreens1109dCompatTests.finish(a, true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after the next day's win");
		helper.succeed();
	}
}
