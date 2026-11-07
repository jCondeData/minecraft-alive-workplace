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
 * Expected: the 10 clocks the player already gave still count; the step asks only the 14 left. The two ways that
 * lose them (story switched off, story switched on) are QaReformCarry1104cBugTests (B-numbers in the ROADMAP); here, the
 * edict lifted and proclaimed again mid-step, which keeps them.
 */
public class QaReformCarry1104cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);
	private static final String EDICT = AliveWorkplace.id("long_shifts").toString();

	/**
	 * edicts.md: "Progress is kept per edict, also while the edict is lifted, and a step handed in halfway stays half
	 * done." 10 of 24 clocks handed to the Shift Bell's step 1, the Long Shifts lifted (after its 3 days) and proclaimed
	 * again: whichever way step 1 comes back (the story again, or M30's step), it holds the 10 clocks, and only once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cCarryLift"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cCarryLift")
	public void qaHalfAStoryStepStaysHalfDoneOverTheEdictLiftedAndProclaimedAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(EDICT, day - 10)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			List<Quest> story = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			helper.assertTrue(story.size() == 1, "setup: " + story.size() + " story quests up after the ask");
			player.getInventory().add(new ItemStack(Items.CLOCK, 10));
			helper.assertTrue(Stories.handIn(player, hall, story.get(0).id) == 10, "setup: 10 clocks not taken by the story");
			helper.assertTrue(Edicts.lift(level, hall, null, EDICT).done(), "setup: the Long Shifts couldn't be lifted");
			level.setDayTime(day * VillageNeeds.DAY + 1000);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			Arcs.round(level, hall, entity);
			Edicts.Edict edict = Edicts.get(net.minecraft.resources.ResourceLocation.parse(EDICT)).orElseThrow();
			helper.assertTrue(Edicts.proclaim(level, hall, null, edict).done(), "setup: the Long Shifts couldn't be proclaimed again");
			for (int r = 1; r <= 2; r++) {
				level.setDayTime((day + r) * VillageNeeds.DAY + 1000);
				QaReformStoryGameTests.autoRound(level, hall, entity);
				Arcs.round(level, hall, entity);
				ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
				Arcs.round(level, hall, entity);
			}
			List<Quest> again = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			List<VillageQuests.Quest> m30 = steps(entity);
			helper.assertTrue(again.size() + m30.size() == 1, again.size() + " story quests and " + m30.size() + " M30 steps up for step 1");
			int held = again.isEmpty() ? m30.get(0).progress() : again.get(0).progress[0];
			helper.assertTrue(held == 10, "step 1 came back holding " + held + " of the 10 clocks handed in before the edict was lifted ("
				+ (again.isEmpty() ? "M30's step" : "the story's step") + ")");
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
