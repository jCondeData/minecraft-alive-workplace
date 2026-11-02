package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (2026-11-02, B822 verification), from docs/wiki/builder.md ("Speed. Exactly workplaceBuildDelay ticks per block
 * ... times a share by Builder level 1 to 5: 100%, 100%, 85%, 70%, 55%, 40% ... A bigger delay is never faster, up to
 * the rule's largest value") and the rule tables ("8 (min 1)", "Ticks per block for a level 1 Builder"; config-switches:
 * "8 (1 or more)"). Covers what QaBuilderConfig1102BugTests leaves out: the exact share at every level on both sides of
 * the old overflow edges (21,474,836 / 21,474,837 for 100%, 53,687,091 / 53,687,092 for 40%), a higher level never
 * slower, real blocks timed one by one at a novice's and a Master's pace, a Master Builder at the largest value, the
 * rule raised in the middle of a build, a save and reload in the middle of a build at the largest value, and /gamerule
 * itself refusing values under 1 and taking the largest one.
 */
public class QaBuilderDelay1102GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final int[] PERCENT = {100, 100, 85, 70, 55, 40};
	private static final int[] BASES = {1, 2, 3, 7, 8, 9, 100, 21_474_836, 21_474_837, 30_000_000, 53_687_091, 53_687_092, 1_000_000_000,
		Integer.MAX_VALUE - 1, Integer.MAX_VALUE};

	/** The ticks at which the hut lost an unfinished block (one per placed block; a door's two halves count once). */
	private static List<Integer> watch(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r) {
		return watch(helper, r, new ArrayList<>());
	}

	/**
	 * Like {@link #watch(GameTestHelper, QaBuilderConfig1102GameTests.Run)}, also writing into {@code delays} the
	 * Builder's delay as it stood when each block went down. The delay includes his {@link
	 * io.github.jcondedata.aliveworkplace.work.Pace pace} (mood, traits, the village), which moves during a long suite
	 * run, so a block's wait is compared with the delay in effect when the block before it was placed.
	 */
	private static List<Integer> watch(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r, List<Integer> delays) {
		List<Integer> ticks = new ArrayList<>();
		int[] last = {r.plan().unfinished(r.level()).size()};
		helper.onEachTick(() -> {
			if (r.live() == null) {
				return;
			}
			int now = r.plan().unfinished(r.level()).size();
			if (now < last[0]) {
				ticks.add((int) helper.getTick());
				delays.add(BuilderLevels.delay(r.level(), r.villager()));
			}
			last[0] = now;
		});
		return ticks;
	}

	private static void setDelay(ServerLevel level, int delay) {
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
	}

	private static void setLevel(Villager villager, int lvl) {
		villager.setVillagerData(villager.getVillagerData().setLevel(lvl));
	}

	// ------------------------------------------------------------------------------------------------- pure

	/** The share at every level, exactly (rounded to whole ticks), on both sides of the old int overflow edges. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theDelayIsTheRuleTimesTheLevelShareOnBothSidesOfTheOldOverflow(GameTestHelper helper) {
		for (int base : BASES) {
			int previous = Integer.MAX_VALUE;
			for (int lvl = 1; lvl <= 5; lvl++) {
				int d = BuilderLevels.delay(base, lvl);
				long want = Math.round(base * (double) PERCENT[lvl] / 100.0);
				helper.assertTrue(d == want, "level " + lvl + ", workplaceBuildDelay " + base + ": " + d + " ticks a block, wanted " + want + " ("
					+ PERCENT[lvl] + "%)");
				helper.assertTrue(d <= previous, "level " + lvl + " is slower than level " + (lvl - 1) + " at workplaceBuildDelay " + base + ": " + d
					+ " > " + previous);
				helper.assertTrue(d <= base, "level " + lvl + " at " + base + " is slower than a novice: " + d);
				previous = d;
			}
		}
		// Levels outside 1..5 behave as the nearest level (a modded level 0 or 6 never overflows or goes negative).
		helper.assertTrue(BuilderLevels.delay(Integer.MAX_VALUE, 0) == Integer.MAX_VALUE, "level 0 at the largest delay: " + BuilderLevels.delay(Integer.MAX_VALUE, 0));
		helper.assertTrue(BuilderLevels.delay(Integer.MAX_VALUE, 9) == BuilderLevels.delay(Integer.MAX_VALUE, 5), "level 9 is not a Master");
		helper.succeed();
	}

	/** A real villager, through the game rule, at each level: never slower than the level below, never faster with a bigger rule. */
	//$ gametest_batch AREA '"qaDelayVillagerLevels"'
	@GameTest(template = AREA, batch = "qaDelayVillagerLevels")
	public void aRealBuilderIsNeverSlowerAtAHigherLevelNorFasterWithABiggerRule(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 8);
		Villager villager = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
		int[] last = new int[6];
		for (int base : BASES) {
			setDelay(level, base);
			int previous = Integer.MAX_VALUE;
			for (int lvl = 1; lvl <= 5; lvl++) {
				setLevel(villager, lvl);
				int d = BuilderLevels.delay(level, villager);
				helper.assertTrue(d >= 0, "level " + lvl + " at workplaceBuildDelay " + base + " gives a negative delay " + d);
				helper.assertTrue(d <= previous, "level " + lvl + " is slower than the level below at " + base + ": " + d + " > " + previous);
				helper.assertTrue(d >= last[lvl], "level " + lvl + ": workplaceBuildDelay " + base + " gives " + d + " ticks, under the " + last[lvl]
					+ " of a smaller rule");
				last[lvl] = d;
				previous = d;
			}
		}
		for (int lvl = 1; lvl <= 5; lvl++) {
			helper.assertTrue(last[lvl] > 100_000_000, "level " + lvl + " at the rule's largest value only waits " + last[lvl] + " ticks a block");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------- real blocks, timed

	private static void timed(GameTestHelper helper, int lvl, int rule) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, rule);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		setLevel(r.villager(), lvl);
		List<Integer> delays = new ArrayList<>();
		List<Integer> ticks = watch(helper, r, delays);
		helper.runAtTickTime(900, () -> {
			int expected = BuilderLevels.delay(level, r.villager());
			helper.assertTrue(ticks.size() >= 4, "only " + ticks.size() + " blocks placed in 900 ticks at workplaceBuildDelay " + rule + " (level " + lvl
				+ ", " + expected + " ticks a block): " + QaBuilderConfig1102GameTests.where(helper, r));
			int exact = 0;
			for (int i = 1; i < ticks.size(); i++) {
				int gap = ticks.get(i) - ticks.get(i - 1);
				int want = delays.get(i - 1);
				helper.assertTrue(gap >= want, "level " + lvl + " at workplaceBuildDelay " + rule + ": blocks " + i + " and " + (i + 1) + " only " + gap
					+ " ticks apart, under the " + want + " ticks a block when the first went down; placed at " + ticks + ", delays " + delays);
				if (gap == want) {
					exact++;
				}
			}
			helper.assertTrue(exact > 0, "level " + lvl + " at workplaceBuildDelay " + rule + ": no two blocks exactly the delay apart; placed at "
				+ ticks + ", delays " + delays);
			helper.succeed();
		});
	}

	/** A novice at 30 ticks a block: blocks come exactly 30 ticks apart, never closer. */
	//$ gametest_ticks_batch AREA '1000' '"qaDelayTimedNovice"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaDelayTimedNovice")
	public void aNoviceAtThirtyTicksPlacesBlocksExactlyThirtyTicksApart(GameTestHelper helper) {
		timed(helper, 1, 30);
	}

	/** A Master at 30: 40% of it, 12 ticks, and never closer. */
	//$ gametest_ticks_batch AREA '1000' '"qaDelayTimedMaster"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaDelayTimedMaster")
	public void aMasterAtThirtyTicksPlacesBlocksTwelveTicksApart(GameTestHelper helper) {
		timed(helper, 5, 30);
	}

	/**
	 * "Each level also makes them faster": a Novice at 30 ticks made a Master after three blocks lays the rest 12 ticks
	 * apart (never closer), from the block after the one in hand.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaDelayLevelUpMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaDelayLevelUpMidBuild")
	public void aBuilderLeveledUpMidBuildBuildsFasterFromTheNextBlock(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 30);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> delays = new ArrayList<>();
		List<Integer> ticks = watch(helper, r, delays);
		int[] upAt = {-1};
		int[] novice = {-1};
		helper.onEachTick(() -> {
			if (upAt[0] < 0 && ticks.size() >= 3) {
				novice[0] = BuilderLevels.delay(level, r.villager());
				setLevel(r.villager(), 5);
				upAt[0] = ticks.size();
			}
		});
		helper.runAtTickTime(1100, () -> {
			int master = BuilderLevels.delay(level, r.villager());
			helper.assertTrue(upAt[0] > 0, "never three blocks: " + ticks);
			helper.assertTrue(master < novice[0], "a Master waits " + master + " ticks a block, a Novice " + novice[0]);
			List<Integer> after = ticks.subList(upAt[0], ticks.size());
			helper.assertTrue(after.size() >= 4, "only " + after.size() + " blocks after the level-up: " + ticks);
			// The block in hand at the level-up keeps its Novice wait; from the next one on, each wait is the Master's delay
			// as it stood when the block before went down (his pace moves with mood and the village during a suite run).
			int exact = 0;
			for (int i = upAt[0] + 1; i < ticks.size(); i++) {
				int gap = ticks.get(i) - ticks.get(i - 1);
				int want = delays.get(i - 1);
				helper.assertTrue(want < novice[0], "after the level-up block " + i + " was laid with a wait of " + want + " ticks, not under a Novice's "
					+ novice[0] + "; delays " + delays);
				helper.assertTrue(gap >= want, "after the level-up blocks " + i + " and " + (i + 1) + " are only " + gap + " ticks apart, under "
					+ want + "; placed at " + ticks + ", delays " + delays);
				if (gap == want) {
					exact++;
				}
			}
			helper.assertTrue(exact > 0, "after the level-up no two blocks are exactly the Master's delay apart (" + master + " now, a Novice's "
				+ novice[0] + "); placed at " + ticks + ", delays " + delays + ", leveled after block " + upAt[0]);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------------- huge values on a real build

	private static void barelyMoves(GameTestHelper helper, int lvl, int rule) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, rule);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		setLevel(r.villager(), lvl);
		List<Integer> ticks = watch(helper, r);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(r.live() != null, "level " + lvl + " at workplaceBuildDelay " + rule + " finished the hut within 400 ticks");
			helper.assertTrue(ticks.size() <= 1, "level " + lvl + " at workplaceBuildDelay " + rule + " placed " + ticks.size()
				+ " blocks in 400 ticks (at " + ticks + "); at most 1 was expected");
			helper.succeed();
		});
	}

	/** Just past the old edge for a novice (21,474,837 x 100 overflowed an int). */
	//$ gametest_ticks_batch AREA '600' '"qaDelayEdgeNovice"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDelayEdgeNovice")
	public void aNoviceJustPastTheOldOverflowEdgeStillBuildsSlowly(GameTestHelper helper) {
		barelyMoves(helper, 1, 21_474_837);
	}

	/** A Master just past his own old edge (53,687,092 x 40 overflowed). */
	//$ gametest_ticks_batch AREA '600' '"qaDelayEdgeMaster"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDelayEdgeMaster")
	public void aMasterJustPastHisOldOverflowEdgeStillBuildsSlowly(GameTestHelper helper) {
		barelyMoves(helper, 5, 53_687_092);
	}

	/** A Master at the rule's largest value. */
	//$ gametest_ticks_batch AREA '600' '"qaDelayMaxMaster"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDelayMaxMaster")
	public void aMasterAtTheLargestDelayStillBuildsSlowly(GameTestHelper helper) {
		barelyMoves(helper, 5, Integer.MAX_VALUE);
	}

	// ------------------------------------------------------------------------------------------------- changed mid-build

	/** Raised from 4 to 30,000,000 after a few blocks: at most the block already due comes after the change. */
	//$ gametest_ticks_batch AREA '1200' '"qaDelayRaised"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaDelayRaised")
	public void theRuleRaisedMidBuildSlowsTheBuilderAtOnce(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] changedAt = {-1};
		helper.onEachTick(() -> {
			if (changedAt[0] < 0 && ticks.size() >= 3) {
				setDelay(level, 30_000_000);
				changedAt[0] = (int) helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(changedAt[0] >= 0 && helper.getTick() >= changedAt[0] + 400, "waiting for 3 blocks and 400 ticks after: " + ticks);
			long after = ticks.stream().filter(t -> t > changedAt[0]).count();
			helper.assertTrue(r.live() != null, "the hut was finished after workplaceBuildDelay was raised to 30,000,000");
			helper.assertTrue(after <= 1, after + " blocks placed in the 400 ticks after workplaceBuildDelay went from 4 to 30,000,000 (at "
				+ ticks + ", changed at " + changedAt[0] + "); at most the 1 already due was expected");
		});
	}

	// ------------------------------------------------------------------------------------------------- save and reload

	/**
	 * At the largest value, saved and loaded after the first block: the site survives, the reload does not turn into a
	 * block every tick (at most one block more in 400 ticks), and with the rule back at 1 the hut is finished as drawn.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaDelayMaxReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaDelayMaxReload")
	public void aReloadMidBuildAtTheLargestDelayStaysSlowAndFinishesWhenLowered(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, Integer.MAX_VALUE);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] reloadedAt = {-1};
		boolean[] lowered = {false};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (reloadedAt[0] < 0 && (ticks.size() >= 1 || t >= 300)) {
				QaBuilderConfig1102GameTests.restart(helper, r);
				reloadedAt[0] = (int) t;
			} else if (reloadedAt[0] >= 0 && !lowered[0] && t >= reloadedAt[0] + 400) {
				long after = ticks.stream().filter(x -> x > reloadedAt[0]).count();
				helper.assertTrue(r.live() != null, "the build site was lost in the reload: " + QaBuilderConfig1102GameTests.where(helper, r));
				helper.assertTrue(after <= 1, after + " blocks in the 400 ticks after a reload at workplaceBuildDelay " + Integer.MAX_VALUE + " (at "
					+ ticks + ", reloaded at " + reloadedAt[0] + "); at most 1 was expected");
				setDelay(level, 1);
				// A fresh Builder entity starts its work over, so the lowered rule is read at once.
				QaBuilderConfig1102GameTests.restart(helper, r);
				lowered[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lowered[0], "not lowered yet");
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------------- the command

	/** "8 (min 1)", no top: /gamerule refuses 0 and negatives and keeps the old value, and takes 1 and 2147483647. */
	//$ gametest_batch AREA '"qaDelayCommand"'
	@GameTest(template = AREA, batch = "qaDelayCommand")
	public void theGameruleCommandRefusesUnderOneAndTakesTheLargestValue(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 8);
		var server = level.getServer();
		var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, "gamerule workplaceBuildDelay 0");
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == 8, "/gamerule workplaceBuildDelay 0 set it to "
			+ level.getGameRules().getInt(ModGameRules.BUILD_DELAY));
		server.getCommands().performPrefixedCommand(source, "gamerule workplaceBuildDelay -5");
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == 8, "/gamerule workplaceBuildDelay -5 set it to "
			+ level.getGameRules().getInt(ModGameRules.BUILD_DELAY));
		server.getCommands().performPrefixedCommand(source, "gamerule workplaceBuildDelay 1");
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == 1, "/gamerule workplaceBuildDelay 1 was refused");
		server.getCommands().performPrefixedCommand(source, "gamerule workplaceBuildDelay 2147483647");
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == Integer.MAX_VALUE, "/gamerule workplaceBuildDelay 2147483647 was refused: "
			+ level.getGameRules().getInt(ModGameRules.BUILD_DELAY));
		helper.succeed();
	}
}
