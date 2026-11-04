package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-04, round 2) for ROADMAP 31.22, the eight reforms as stories, written from the spec at what the earlier
 * reform-story tests leave untested: a story's steps refused (the wrong item, the next step's item early, too few, at
 * the wrong hall, kills in another village or before the kill step), the reform already passed before a story could
 * start, a story switched off by name in {@code disabledArcs} (and switched off mid-story: never two quests for one
 * step), edicts switched off, the trades with no fallback asker (Farmer, Guard) missing, two reform stories at once
 * each moving on its own, a real restart at the ask, and the sentences a player reads at the ask exactly as
 * en_us.json has them.
 */
public class QaReformStories1104cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);
	private static final BlockPos ASKER2 = new BlockPos(8, 2, 9);
	/** Another village's hall, out of the first's reach (radius 16 in these tests). */
	private static final BlockPos OTHER = new BlockPos(25, 2, 25);

	// ---- a step refused ---------------------------------------------------------------------------------------------

	/**
	 * The Shift Bell: clocks handed in during the ask don't count, step 2's gold doesn't count for step 1, 10 of 24
	 * clocks count but leave the step open (overnight too, not reformed, no step 2), the rest finishes it taking only
	 * what's still needed, the other village's hall takes nothing, and step 2 the next day starts from nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1104cRefused"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1104cRefused")
	public void qaWrongItemsTooFewOrTheWrongHallDontMoveTheStory(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		helper.setBlock(OTHER, ModBlocks.VILLAGE_HALL);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER);
			Leftovers.after(helper, () -> Arcs.stopAll(level, other));
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(QaReformStoryGameTests.asks(smith), "the Toolsmith didn't ask");

			// The ask: nothing to hand in yet.
			Quest ask = only(helper, level, hall, "the ask");
			give(player, Items.CLOCK, 24);
			give(player, Items.GOLD_INGOT, 24);
			helper.assertTrue(Stories.handIn(player, hall, ask.id) <= 0 && player.getInventory().countItem(Items.CLOCK) == 24,
				"clocks were taken at the ask");
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);

			// Step 1, 24 clocks: the gold (step 2's item) is refused; clear the clocks to hand in a few.
			Quest step1 = only(helper, level, hall, "step 1");
			helper.assertTrue(step1.objectives.get(0) instanceof Objectives.Bring b && b.item().equals("minecraft:clock") && b.count() == 24,
				"step 1 isn't 24 clocks: " + step1.objectives.get(0).json());
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.CLOCK), -1, player.inventoryMenu.getCraftSlots());
			helper.assertTrue(Stories.handIn(player, hall, step1.id) == 0 && player.getInventory().countItem(Items.GOLD_INGOT) == 24,
				"step 2's gold counted for step 1");
			give(player, Items.CLOCK, 10);
			helper.assertTrue(Stories.handIn(player, other, step1.id) <= 0 && player.getInventory().countItem(Items.CLOCK) == 10,
				"the other village's hall took this village's step");
			helper.assertTrue(Stories.handIn(player, hall, step1.id) == 10 && step1.progress[0] == 10, "10 clocks didn't count");
			helper.assertTrue(!Reforms.reformed(entity, edict) && Reforms.progress(entity, edict).step() == 0, "10 of 24 moved the reform on");
			level.setDayTime(day * VillageNeeds.DAY + 1000);
			Arcs.round(level, hall, entity);
			Quest still = only(helper, level, hall, "the next day");
			helper.assertTrue(still.id.equals(step1.id) && still.progress[0] == 10, "the half-done step was replaced overnight: " + still.progress[0]);
			helper.assertTrue(Reforms.shown(entity).isEmpty(), "M30's step went up beside the story");
			give(player, Items.CLOCK, 20);
			helper.assertTrue(Stories.handIn(player, hall, step1.id) == 14 && player.getInventory().countItem(Items.CLOCK) == 6,
				"took more than the 14 still needed: " + player.getInventory().countItem(Items.CLOCK) + " clocks left");
			helper.assertTrue(arcQuests(level, hall).isEmpty(), "step 2 came the same day");

			// Step 2, the next day: 128 gold from nothing; the 24 gold held all along was never taken.
			level.setDayTime((day + 1) * VillageNeeds.DAY + 1000);
			Arcs.round(level, hall, entity);
			Quest step2 = only(helper, level, hall, "step 2");
			helper.assertTrue(step2.objectives.get(0) instanceof Objectives.Bring b && b.item().equals("minecraft:gold_ingot") && b.count() == 128
				&& step2.progress[0] == 0, "step 2 isn't 128 gold from nothing: " + step2.objectives.get(0).json() + " at " + step2.progress[0]);
			helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 24, "the gold was taken without a hand-in");
			helper.assertTrue(!Reforms.reformed(entity, edict), "reformed after one step");
			helper.succeed();
		});
	}

	/**
	 * The Lamplighters' third step (32 monsters, M30's): kills made during the bring steps don't count towards it, and
	 * kills round another village's hall don't count; 32 round this hall end the story reformed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1104cKillsElsewhere"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1104cKillsElsewhere")
	public void qaKillsBeforeTheStepOrInAnotherVillageDontCount(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		helper.setBlock(OTHER, ModBlocks.VILLAGE_HALL);
		Villager porter = QaReformStoryGameTests.villager(helper, ASKER, ModVillagers.PORTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER);
			Leftovers.after(helper, () -> Arcs.stopAll(level, other));
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			String edict = AliveWorkplace.id("curfew").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, porter);
			Zombie near = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 6));
			near.setNoAi(true);
			Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(25, 2, 22));
			far.setNoAi(true);
			for (int n = 0; n < 2; n++) {
				if (n > 0) {
					level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
					Arcs.round(level, hall, entity);
				}
				Quest q = only(helper, level, hall, "step " + (n + 1));
				for (int i = 0; i < 40; i++) {
					Stories.onKill(level, near, player); // (a bring step: kills don't count)
				}
				helper.assertTrue(q.progress[0] == 0, "kills counted for bring step " + (n + 1));
				completeBring(helper, player, hall, q);
			}
			level.setDayTime((day + 1) * VillageNeeds.DAY + 1000);
			Arcs.round(level, hall, entity);
			Quest slay = only(helper, level, hall, "step 3");
			helper.assertTrue(slay.objectives.get(0) instanceof Objectives.Kill k && k.count() == 32 && slay.progress[0] == 0,
				"step 3 isn't 32 monsters from nothing: " + slay.objectives.get(0).json() + " at " + slay.progress[0]);
			for (int i = 0; i < 10; i++) {
				Stories.onKill(level, far, player);
			}
			helper.assertTrue(slay.progress[0] == 0, "kills at the other village counted here: " + slay.progress[0]);
			for (int i = 0; i < 31; i++) {
				Stories.onKill(level, near, player);
			}
			helper.assertTrue(slay.progress[0] == 31 && !Reforms.reformed(entity, edict), "31 of 32 ended it, or didn't count: " + slay.progress[0]);
			Stories.onKill(level, near, player);
			helper.assertTrue(Reforms.reformed(entity, edict) && Arcs.side(level, hall).isEmpty(), "32 kills didn't end the story reformed");
			near.discard();
			far.discard();
			helper.succeed();
		});
	}

	// ---- no story ---------------------------------------------------------------------------------------------------

	/** The Long Shifts already reformed (by M30's own steps, before): a Toolsmith there, but no story and no step, two rounds. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPassed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPassed")
	public void qaAReformAlreadyPassedStartsNoStory(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			long day = startDay(helper);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day - 5)));
			entity.setReforms(List.of(new Reforms.Progress(edict, 3, true, day - 1)));
			for (int r = 0; r < 2; r++) {
				level.setDayTime((day + r - 1) * VillageNeeds.DAY + 1000);
				QaReformStoryGameTests.autoRound(level, hall, entity);
			}
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && !QaReformStoryGameTests.asks(smith), "a story started for a reform already passed");
			helper.assertTrue(Reforms.shown(entity).isEmpty() && arcQuests(level, hall).isEmpty(), "a step went up for a reform already passed");
			CivicEffects.forget();
			Edicts.Edict file = Edicts.find("long_shifts").orElseThrow();
			List<CivicEffects.Effect> here = CivicEffects.of(entity).all().stream().map(CivicEffects.Active::effect).toList();
			helper.assertTrue(here.containsAll(file.boost()) && file.cost().stream().noneMatch(here::contains), "boost kept, cost gone: " + here);
			helper.succeed();
		});
	}

	/** The Shift Bell switched off by name in {@code disabledArcs}: no ask, and M30's own step goes up on the quest page. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cDisabledArc"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cDisabledArc")
	public void qaAStoryOffInDisabledArcsLeavesM30sStep(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		Set<String> off = Arcs.DISABLED;
		Arcs.DISABLED = Set.of("shift_bell");
		Leftovers.after(helper, () -> Arcs.DISABLED = off);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			long day = startDay(helper);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && !QaReformStoryGameTests.asks(smith), "a disabled arc's story started");
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).item().equals("minecraft:clock") && shown.get(0).count() == 24,
				"M30's step 1 (24 clocks) isn't on the page: " + shown);
			helper.succeed();
		});
	}

	/**
	 * The Shift Bell switched off in {@code disabledArcs} while it runs (step 1 up as the story's quest): the village
	 * never has two quests for the same step, the story's and M30's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cDisabledMid"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cDisabledMid")
	public void qaAStorySwitchedOffMidwayNeverDoublesAStep(GameTestHelper helper) {
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
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			only(helper, level, hall, "step 1");
			Arcs.DISABLED = Set.of("shift_bell");
			for (int r = 0; r < 2; r++) {
				level.setDayTime((day + r) * VillageNeeds.DAY + 1000);
				QaReformStoryGameTests.autoRound(level, hall, entity);
				Arcs.round(level, hall, entity);
				int both = arcQuests(level, hall).size() + Reforms.shown(entity).size();
				helper.assertTrue(both <= 1, "round " + (r + 1) + ": " + arcQuests(level, hall).size() + " story quests and " + Reforms.shown(entity).size()
					+ " M30 steps up for the Shift Bell's step 1 at once");
			}
			helper.succeed();
		});
	}

	/** Edicts switched off: the Long Shifts in force but no story starts, nobody is asked, and no step goes up. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cEdictsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cEdictsOff")
	public void qaWithEdictsOffNoReformStoryStarts(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		boolean on = Edicts.ENABLED;
		Leftovers.after(helper, () -> Edicts.ENABLED = on);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), day)));
			Edicts.ENABLED = false;
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && !QaReformStoryGameTests.asks(smith), "a reform story started with edicts off");
			helper.assertTrue(Reforms.shown(entity).isEmpty(), "a reform step went up with edicts off");
			helper.succeed();
		});
	}

	/**
	 * The Common Granary (asked by a Farmer) and the Militia Drill (asked by a Guard) have no fallback asker: with only
	 * a Toolsmith there, neither story starts, the Toolsmith isn't asked, and the reform isn't lost (M30's step is up).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cNoFallback"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cNoFallback")
	public void qaTheGranaryAndTheDrillWaitForTheirOwnTrade(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			for (String edict : List.of("free_bread", "conscription")) {
				VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
				QaReformStoryGameTests.untag(smith);
				long day = startDay(helper);
				entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id(edict).toString(), day)));
				QaReformStoryGameTests.autoRound(level, hall, entity);
				helper.assertTrue(Arcs.side(level, hall).isEmpty() && !QaReformStoryGameTests.asks(smith), edict + ": a story started, asked by the Toolsmith");
				helper.assertTrue(Reforms.shown(entity).size() == 1, edict + ": the reform was lost (no story and no M30 step)");
			}
			helper.succeed();
		});
	}

	// ---- two at once ------------------------------------------------------------------------------------------------

	/**
	 * The Long Shifts and the Curfew in force together: both stories start beside each other, each asked by its own
	 * trade; finishing the Shift Bell to its end leaves the Lamplighters at its first step untouched, and only the Long
	 * Shifts is reformed; then the Lamplighters finish too.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1104cTwo"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1104cTwo")
	public void qaTwoReformStoriesRunSideBySide(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		Villager porter = QaReformStoryGameTests.villager(helper, ASKER2, ModVillagers.PORTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			String shifts = AliveWorkplace.id("long_shifts").toString();
			String curfew = AliveWorkplace.id("curfew").toString();
			entity.setEdicts(List.of(new Edicts.InForce(shifts, day), new Edicts.InForce(curfew, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			List<String> running = Arcs.side(level, hall).stream().map(s -> s.id).toList();
			helper.assertTrue(running.contains("aliveworkplace:shift_bell") && running.contains("aliveworkplace:lamplighters"), "not both stories: " + running);
			helper.assertTrue(QaReformStoryGameTests.asks(smith) && QaReformStoryGameTests.asks(porter), "each trade wasn't asked: smith "
				+ QaReformStoryGameTests.asks(smith) + ", porter " + QaReformStoryGameTests.asks(porter));
			helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith) == InteractionResult.SUCCESS, "talking to the smith didn't count");
			helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, porter) == InteractionResult.SUCCESS, "talking to the porter didn't count");
			Quest lamps = of(helper, level, hall, "aliveworkplace:lamplighters", "the Lamplighters' step 1");
			for (int n = 0; n < 3; n++) {
				if (n > 0) {
					level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
					Arcs.round(level, hall, entity);
				}
				Quest bell = of(helper, level, hall, "aliveworkplace:shift_bell", "the Shift Bell's step " + (n + 1));
				completeBring(helper, player, hall, bell);
				Quest l = of(helper, level, hall, "aliveworkplace:lamplighters", "the Lamplighters after the bell's step " + (n + 1));
				helper.assertTrue(l.id.equals(lamps.id) && l.progress[0] == 0, "the Lamplighters' step moved with the bell's: " + l.progress[0]);
			}
			helper.assertTrue(Reforms.reformed(entity, shifts) && !Reforms.reformed(entity, curfew), "reformed: shifts " + Reforms.reformed(entity, shifts)
				+ ", curfew " + Reforms.reformed(entity, curfew));
			helper.assertTrue(Arcs.side(level, hall).stream().map(s -> s.id).toList().equals(List.of("aliveworkplace:lamplighters")),
				"the stories running after the bell's end: " + Arcs.side(level, hall).stream().map(s -> s.id).toList());
			helper.assertTrue(Reforms.progress(entity, curfew).step() == 0, "the Curfew's reform moved on: " + Reforms.progress(entity, curfew));
			// the Lamplighters, to the end
			completeBring(helper, player, hall, lamps);
			long d = day + 3;
			for (int n = 1; n < 3; n++) {
				level.setDayTime((d + n - 1) * VillageNeeds.DAY + 1000);
				Arcs.round(level, hall, entity);
				Quest q = of(helper, level, hall, "aliveworkplace:lamplighters", "the Lamplighters' step " + (n + 1));
				if (q.objectives.get(0) instanceof Objectives.Bring) {
					completeBring(helper, player, hall, q);
				} else {
					Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 6));
					int count = ((Objectives.Kill) q.objectives.get(0)).count();
					for (int i = 0; i < count; i++) {
						Stories.onKill(level, zombie, player);
					}
					zombie.discard();
				}
			}
			helper.assertTrue(Reforms.reformed(entity, shifts) && Reforms.reformed(entity, curfew) && Arcs.side(level, hall).isEmpty(),
				"both weren't reformed at the end: curfew " + Reforms.reformed(entity, curfew));
			helper.succeed();
		});
	}

	// ---- a restart --------------------------------------------------------------------------------------------------

	/**
	 * The Midwives asked, then a real restart (the stories written to the world's data folder and read back by the
	 * game's loader) before the player talks: one story still runs (a round doesn't start a second), the Nurse still
	 * asks, and talking to her puts up M30's step 1 (48 honey bottles).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cRestart")
	public void qaARealRestartAtTheAskKeepsTheStory(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager nurse = QaReformStoryGameTests.villager(helper, ASKER, ModVillagers.NURSE);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("large_families").toString(), day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1, "the Midwives didn't start");
			restart(level);
			helper.assertTrue(Arcs.side(level, hall).stream().filter(s -> s.id.equals("aliveworkplace:midwives")).count() == 1,
				"the story after the restart: " + Arcs.side(level, hall).stream().map(s -> s.id).toList());
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1 && Reforms.shown(entity).isEmpty(), "a second story or M30's step after the restart");
			Quest ask = only(helper, level, hall, "the ask after the restart");
			helper.assertTrue(ask.objectives.get(0) instanceof Objectives.Talk, "the ask isn't a talk: " + ask.objectives.get(0).json());
			helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, nurse) == InteractionResult.SUCCESS, "talking to the Nurse didn't count");
			Quest step1 = only(helper, level, hall, "step 1 after the restart");
			helper.assertTrue(step1.objectives.get(0) instanceof Objectives.Bring b && b.item().equals("minecraft:honey_bottle") && b.count() == 48,
				"step 1 isn't 48 honey bottles: " + step1.objectives.get(0).json());
			helper.succeed();
		});
	}

	// ---- what the player reads ----------------------------------------------------------------------------------------

	/**
	 * The ask and its steps, as the player reads them, exactly as en_us.json has them: the ask's title names the
	 * Toolsmith ("Ask Toolsmith about the long shifts"), the step's title is "The Shift Bell, step 1 of 3", and after
	 * the last step the village is told "Ashford has reformed the edict Long Shifts with The Shift Bell. ..." (with the
	 * village's own name and the reform's line).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1104cText"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1104cText")
	public void qaTheAskAndStepsReadAsInEnUs(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ServerPlayer player = client.player();
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Map<String, String> en = enUs();
			long day = startDay(helper);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			Quest ask = only(helper, level, hall, "the ask");
			String who = smith.getDisplayName().getString();
			String title = ask.name.map(Component::getString).orElse("");
			helper.assertTrue(title.equals(String.format(en.get("arc.aliveworkplace.shift_bell.ask"), who)) && title.equals("Ask " + who + " about the long shifts"),
				"the ask's title: '" + title + "'");
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			String reform = en.get("reform.aliveworkplace.shift_bell");
			for (int n = 0; n < 3; n++) {
				if (n > 0) {
					level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
					Arcs.round(level, hall, entity);
				}
				Quest q = only(helper, level, hall, "step " + (n + 1));
				String name = q.name.map(Component::getString).orElse("");
				helper.assertTrue(name.equals(reform + ", step " + (n + 1) + " of 3"), "step " + (n + 1) + "'s title: '" + name + "'");
				completeBring(helper, player, hall, q);
			}
			String village = io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, hall).getString();
			String done = String.format(en.get("message.aliveworkplace.reform.done"), village, Edicts.name(edict).getString(), reform,
				en.get("reform.aliveworkplace.shift_bell.line"));
			List<String> seen = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(seen.contains(done), "the village wasn't told '" + done + "': " + seen);
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("%") || s.contains("aliveworkplace.")), "raw text: " + seen);
			helper.succeed();
		});
	}

	private static final Pattern BRITISH = Pattern.compile("(?i)honour|grey|colour|travell|defence|armour|favour|neighbour|centre|labour|cancelled|jeweller|metre|theatre|plough|whilst|realise|organise|apologise|recognise|programme");

	/**
	 * Every en_us.json sentence of the eight reforms and their stories (the reform names and lines, the step titles and
	 * messages, the arcs, chatter and chronicle lines): American English, a %s for each argument the code fills, and no
	 * doubled quotes.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryReformStoryLineInEnUsIsAmericanEnglish(GameTestHelper helper) {
		Map<String, String> en = enUs();
		List<String> names = List.of("shift_bell", "common_granary", "midwives", "watchful_gate", "festival_fund", "fair_ledger", "lamplighters", "militia_drill");
		List<String> bad = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<String, String> e : en.entrySet()) {
			String k = e.getKey();
			boolean ours = k.contains("reform") || names.stream().anyMatch(n -> k.contains("." + n));
			if (!ours) {
				continue;
			}
			checked++;
			String v = e.getValue();
			Matcher m = BRITISH.matcher(v);
			if (m.find()) {
				bad.add(k + ": British '" + m.group() + "'");
			}
			if (v.contains("  ") || !v.equals(v.trim())) {
				bad.add(k + ": stray spaces");
			}
		}
		helper.assertTrue(checked > 100, "only " + checked + " reform lines found in en_us.json");
		helper.assertTrue(bad.isEmpty(), "bad lines: " + bad);
		helper.succeed();
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** Today is a fresh day at 1000 (the next day's morning); returns that day. */
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

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	/** The one arc quest up at the hall. */
	private static Quest only(GameTestHelper helper, ServerLevel level, BlockPos hall, String what) {
		List<Quest> quests = arcQuests(level, hall);
		helper.assertTrue(quests.size() == 1, what + ": " + quests.size() + " story quests up");
		return quests.get(0);
	}

	/** The one quest up of the arc {@code arc}. */
	private static Quest of(GameTestHelper helper, ServerLevel level, BlockPos hall, String arc, String what) {
		List<Quest> quests = arcQuests(level, hall).stream().filter(q -> arc.equals(q.arc)).toList();
		helper.assertTrue(quests.size() == 1, what + ": " + quests.size() + " quests of " + arc + " up");
		return quests.get(0);
	}

	/** Gives exactly what the bring quest still needs and hands it in. */
	private static void completeBring(GameTestHelper helper, ServerPlayer player, BlockPos hall, Quest q) {
		helper.assertTrue(q.objectives.get(0) instanceof Objectives.Bring, "not a bring step: " + q.objectives.get(0).json());
		Objectives.Bring b = (Objectives.Bring) q.objectives.get(0);
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(b.item()));
		int need = b.count() - q.progress[0];
		give(player, item, need);
		helper.assertTrue(Stories.handIn(player, hall, q.id) == need, "not handed in: " + need + " " + b.item());
	}

	private static void give(ServerPlayer player, Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) {
			player.getInventory().add(new ItemStack(item, Math.min(max, left)));
		}
	}

	/** Writes every changed saved data to the world's data folder and drops the stories from memory (a restart). */
	private static void restart(ServerLevel level) {
		Stories.Data.get(level).setDirty();
		DimensionDataStorage storage = level.getDataStorage();
		storage.save();
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove("aliveworkplace_stories");
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static Map<String, String> enUs() {
		try (var in = QaReformStories1104cGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> out = new java.util.LinkedHashMap<>();
			json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
			return out;
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
