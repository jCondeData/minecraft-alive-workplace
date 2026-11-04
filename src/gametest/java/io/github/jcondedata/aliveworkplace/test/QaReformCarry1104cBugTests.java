package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane C, 2026-11-04) for ROADMAP 31.22, from the wiki's promise in edicts.md: "With storyArcs off, the story listed
 * in disabledArcs, ... the steps go up on the quest page as before, and progress made either way carries over." The
 * earlier tests cover whole steps (a finished step is never asked again) and never two quests for one step; these hand
 * in part of a step (10 of the Shift Bell's 24 clocks) and then switch the story off, or on, between the two ways.
 * Expected: the 10 clocks the player already gave still count; the step asks only the 14 left.
 */
public class QaReformCarry1104cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);
	private static final String EDICT = AliveWorkplace.id("long_shifts").toString();

	/** 10 of 24 clocks handed to the story's step 1, then the story switched off by name: M30's step 1 asks only 14 more. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cCarryOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cCarryOff")
	public void qaHalfAStepGivenToTheStoryStillCountsWhenItIsSwitchedOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		Set<String> off = Arcs.DISABLED;
		Leftovers.after(helper, () -> Arcs.DISABLED = off);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(EDICT, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			List<Quest> story = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			helper.assertTrue(story.size() == 1, "setup: " + story.size() + " story quests up after the ask");
			Quest q = story.get(0);
			player.getInventory().add(new ItemStack(Items.CLOCK, 10));
			helper.assertTrue(Stories.handIn(player, hall, q.id) == 10 && q.progress[0] == 10, "setup: 10 clocks not taken by the story: " + q.progress[0]);
			Arcs.DISABLED = Set.of("shift_bell");
			for (int r = 0; r < 2; r++) {
				level.setDayTime((day + r) * VillageNeeds.DAY + 1000);
				QaReformStoryGameTests.autoRound(level, hall, entity);
				Arcs.round(level, hall, entity);
			}
			List<VillageQuests.Quest> m30 = steps(entity);
			helper.assertTrue(m30.size() == 1 && m30.get(0).reform().get().step() == 0,
				"setup: M30's step 1 isn't up once the story is switched off: " + m30);
			VillageQuests.Quest step = m30.get(0);
			helper.assertTrue(step.progress() == 10, "the 10 clocks given to the story don't count on M30's step 1: "
				+ step.progress() + " of " + step.count() + " (the player lost them)");
			helper.succeed();
		});
	}

	/** 10 of 24 clocks handed in on M30's step 1 with storyArcs off; switched on, the story's step 1 asks only 14 more. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cCarryOn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cCarryOn")
	public void qaHalfAnM30StepStillCountsWhenTheStoryIsSwitchedOn(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		boolean enabled = Arcs.ENABLED;
		Leftovers.after(helper, () -> Arcs.ENABLED = enabled);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(EDICT, day)));
			Arcs.ENABLED = false;
			QaReformStoryGameTests.autoRound(level, hall, entity);
			List<VillageQuests.Quest> m30 = steps(entity);
			helper.assertTrue(m30.size() == 1, "setup: M30's step 1 isn't up with storyArcs off: " + m30);
			player.getInventory().add(new ItemStack(Items.CLOCK, 10));
			helper.assertTrue(VillageQuests.handIn(player, hall, m30.get(0).id()) == 10, "setup: 10 clocks not handed in on M30's step");
			helper.assertTrue(steps(entity).size() == 1 && steps(entity).get(0).progress() == 10, "setup: M30's step isn't at 10: " + steps(entity));
			Arcs.ENABLED = true;
			level.setDayTime(day * VillageNeeds.DAY + 1000);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			Arcs.round(level, hall, entity);
			List<Quest> story = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			List<VillageQuests.Quest> left = steps(entity);
			if (story.isEmpty()) { // the story didn't take over: M30's step must still hold the clocks
				helper.assertTrue(left.size() == 1 && left.get(0).progress() == 10, "no story and M30's 10 clocks gone: " + left);
			} else {
				helper.assertTrue(story.size() == 1 && left.isEmpty(), story.size() + " story quests and " + left.size() + " M30 steps up for one step");
				helper.assertTrue(story.get(0).progress[0] == 10, "the 10 clocks handed in on M30's step don't count on the story's step 1: "
					+ story.get(0).progress[0] + " (the player lost them)");
			}
			helper.succeed();
		});
	}

	private static List<VillageQuests.Quest> steps(VillageHallBlockEntity entity) {
		return entity.quests().stream().filter(q -> q.reform().map(r -> r.edict().equals(EDICT)).orElse(false)).toList();
	}

	private static long startDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long before = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(before));
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
		return day;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		return player;
	}
}
