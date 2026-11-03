package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B831: workplaceBuildDelay changed in the middle of a job takes effect without the worker's job restarting. Lowered,
 * the wait in hand is cut to the new delay; raised, the next wait is the new one; across a save and reload the
 * lowered rule is still read at once. B845: a restart of his work under a huge delay gives no free block.
 */
public class BuildDelayChangeGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static List<Integer> watch(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r) {
		List<Integer> ticks = new ArrayList<>();
		int[] last = {r.plan().unfinished(r.level()).size()};
		helper.onEachTick(() -> {
			if (r.live() == null) {
				return;
			}
			int now = r.plan().unfinished(r.level()).size();
			if (now < last[0]) {
				ticks.add((int) helper.getTick());
			}
			last[0] = now;
		});
		return ticks;
	}

	private static void setDelay(ServerLevel level, int delay) {
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
	}

	private static long between(List<Integer> ticks, int from, int to) {
		return ticks.stream().filter(t -> t > from && t <= to).count();
	}

	/** The pure rule: lowered cuts the wait to the new full one, raised or unchanged leaves it alone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLoweredRuleCutsTheWaitAndARaisedOneLeavesIt(GameTestHelper helper) {
		helper.assertTrue(BuilderLevels.rewait(29_999_000, 30_000_000, 4, () -> 3) == 3, "lowered from 30,000,000 to 4: wait not cut to 3");
		helper.assertTrue(BuilderLevels.rewait(2, 30_000_000, 4, () -> 3) == 2, "lowered: a wait already shorter than the new one grew");
		helper.assertTrue(BuilderLevels.rewait(5, 8, 1, () -> -1) == 0, "lowered to a zero delay: the wait went negative");
		helper.assertTrue(BuilderLevels.rewait(3, 4, 30_000_000, () -> 29_999_999) == 3, "raised: the wait in hand changed");
		helper.assertTrue(BuilderLevels.rewait(3, 4, 4, () -> 0) == 3, "unchanged: the wait in hand changed");
		helper.succeed();
	}

	/**
	 * Huge, lowered to 4 after the first block, then raised back to 30,000,000 after a few more: he builds again at 4,
	 * then stops again (at most the block already due).
	 */
	//$ gametest_ticks_batch AREA '1600' '"delayLoweredThenRaised"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "delayLoweredThenRaised")
	public void theRuleLoweredThenRaisedMidBuildFollowsEachChange(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 30_000_000);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] loweredAt = {-1};
		int[] raisedAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (loweredAt[0] < 0 && (ticks.size() >= 1 || t >= 300)) {
				setDelay(level, 4);
				loweredAt[0] = t;
			} else if (loweredAt[0] >= 0 && raisedAt[0] < 0 && between(ticks, loweredAt[0], t) >= 3) {
				setDelay(level, 30_000_000);
				raisedAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0 && helper.getTick() >= raisedAt[0] + 400, "waiting for the raise and 400 ticks after: lowered at "
				+ loweredAt[0] + ", raised at " + raisedAt[0] + "; blocks at " + ticks);
			helper.assertTrue(raisedAt[0] - loweredAt[0] <= 200, "lowered to 4 at " + loweredAt[0] + " but the third block after only came at " + raisedAt[0]
				+ "; blocks at " + ticks);
			long afterRaise = between(ticks, raisedAt[0], raisedAt[0] + 400);
			helper.assertTrue(r.live() != null, "the hut was finished after the rule went back up to 30,000,000");
			helper.assertTrue(afterRaise <= 1, afterRaise + " blocks in the 400 ticks after the rule went back up to 30,000,000 at " + raisedAt[0]
				+ " (blocks at " + ticks + "); at most the 1 already due was expected");
		});
	}

	/** Lowered from huge to 20, not to the minimum: the cut wait is the new delay, never a rush of blocks. */
	//$ gametest_ticks_batch AREA '1200' '"delayLoweredPaced"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "delayLoweredPaced")
	public void theRuleLoweredMidBuildKeepsTheNewPace(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 30_000_000);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] loweredAt = {-1};
		int[] delay = {-1};
		helper.onEachTick(() -> {
			if (loweredAt[0] < 0 && (ticks.size() >= 1 || helper.getTick() >= 300)) {
				setDelay(level, 20);
				delay[0] = BuilderLevels.delay(level, r.villager());
				loweredAt[0] = (int) helper.getTick();
			}
		});
		helper.runAtTickTime(1000, () -> {
			List<Integer> after = ticks.stream().filter(t -> t > loweredAt[0]).toList();
			helper.assertTrue(after.size() >= 4, "only " + after.size() + " blocks after lowering to 20 at " + loweredAt[0] + " (blocks at " + ticks + "): "
				+ QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(after.get(0) - loweredAt[0] <= delay[0] + 40, "lowered to 20 (" + delay[0] + " ticks a block) at " + loweredAt[0]
				+ " but the next block only came at " + after.get(0));
			for (int i = 1; i < after.size(); i++) {
				int gap = after.get(i) - after.get(i - 1);
				helper.assertTrue(gap >= delay[0] / 2, "after lowering to 20 (" + delay[0] + " ticks a block) two blocks came " + gap + " ticks apart: " + ticks);
			}
			helper.succeed();
		});
	}

	/** Saved and loaded at a huge delay mid-build, then lowered to 4 with no further restart: he builds again within 200 ticks. */
	//$ gametest_ticks_batch AREA '1600' '"delayLoweredAfterReload"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "delayLoweredAfterReload")
	public void theRuleLoweredAfterAReloadLetsTheBuilderBuildAgain(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 30_000_000);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] reloadedAt = {-1};
		int[] loweredAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (reloadedAt[0] < 0 && (ticks.size() >= 1 || t >= 300)) {
				QaBuilderConfig1102GameTests.restart(helper, r);
				reloadedAt[0] = t;
			} else if (reloadedAt[0] >= 0 && loweredAt[0] < 0 && (between(ticks, reloadedAt[0], t) >= 1 || t >= reloadedAt[0] + 300)) {
				helper.assertTrue(r.live() != null, "the build site was lost in the reload");
				setDelay(level, 4);
				loweredAt[0] = t;
			}
		});
		helper.runAtTickTime(1400, () -> {
			helper.assertTrue(loweredAt[0] >= 0, "never lowered: blocks at " + ticks);
			helper.assertTrue(between(ticks, reloadedAt[0], loweredAt[0]) <= 1, "blocks came fast at 30,000,000 after the reload: " + ticks + ", reloaded at "
				+ reloadedAt[0] + ", lowered at " + loweredAt[0]);
			long after = between(ticks, loweredAt[0], loweredAt[0] + 200);
			helper.assertTrue(after >= 3, "lowered to 4 at " + loweredAt[0] + " after a reload at " + reloadedAt[0] + ", but only " + after
				+ " blocks in the next 200 ticks (blocks at " + ticks + "): " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.succeed();
		});
	}

	/**
	 * B845: building at 4, raised to 30,000,000, then his work restarts (the same brain stopping and starting, as at
	 * dusk and dawn; then a save and reload, a fresh brain): no restart gives a free block. At most the one already due
	 * when the rule went up comes.
	 */
	//$ gametest_ticks_batch AREA '1600' '"delayRaisedThenRestarted"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "delayRaisedThenRestarted")
	public void aRestartUnderAHugeDelayGivesNoFreeBlock(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = watch(helper, r);
		int[] raisedAt = {-1};
		int[] stoppedAt = {-1};
		int[] reloadedAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (raisedAt[0] < 0 && ticks.size() >= 3) {
				setDelay(level, 30_000_000);
				raisedAt[0] = t;
			} else if (raisedAt[0] >= 0 && stoppedAt[0] < 0 && t >= raisedAt[0] + 40 && (between(ticks, raisedAt[0], t) >= 1 || t >= raisedAt[0] + 400)) {
				// once the block already due when the rule went up has come: the wait in hand is now the huge one
				r.villager().getBrain().stopAll(level, r.villager()); // his work stops; the brain starts it again next tick
				stoppedAt[0] = t;
			} else if (stoppedAt[0] >= 0 && reloadedAt[0] < 0 && t >= stoppedAt[0] + 200) {
				QaBuilderConfig1102GameTests.restart(helper, r);
				reloadedAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "fewer than 3 blocks at 4 in " + helper.getTick() + " ticks: " + ticks + "; "
				+ QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(reloadedAt[0] >= 0 && helper.getTick() >= reloadedAt[0] + 300, "waiting for the restarts and 300 ticks after");
			helper.assertTrue(r.live() != null, "the build site was lost: " + QaBuilderConfig1102GameTests.where(helper, r));
			long due = between(ticks, raisedAt[0], stoppedAt[0]);
			helper.assertTrue(due <= 1, due + " blocks after the rule went up to 30,000,000 at " + raisedAt[0] + " (blocks at " + ticks
				+ "); at most the 1 already due was expected");
			long afterStop = between(ticks, stoppedAt[0], reloadedAt[0]);
			helper.assertTrue(afterStop == 0, afterStop + " block(s) after his work stopped and started again at " + stoppedAt[0]
				+ " under workplaceBuildDelay 30,000,000 (blocks at " + ticks + "): a restart gave a free block");
			long afterReload = between(ticks, reloadedAt[0], (int) helper.getTick());
			helper.assertTrue(afterReload == 0, afterReload + " block(s) after a reload at " + reloadedAt[0]
				+ " under workplaceBuildDelay 30,000,000 (blocks at " + ticks + "): a restart gave a free block");
		});
	}

	/** Fishermen casting at a huge delay, lowered to 1: the bite in the water comes now, not after the 2^30-step wait. */
	//$ gametest_ticks_batch HUGE '1600' '"delayLoweredFisher"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "delayLoweredFisher")
	public void theRuleLoweredMidCastBringsTheBite(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int was = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> setDelay(level, was));
		setDelay(level, 1 << 30);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 4), new BlockPos(20, 1, 24))) {
			helper.setBlock(p, Blocks.WATER);
		}
		List<Villager> fishers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BlockPos barrel = new BlockPos(2, 2, 3 + i * 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Villager v = helper.spawn(EntityType.VILLAGER, barrel.east(2));
			Jobs.employ(level, v, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
			Fishers.start(level, v, new ItemStack(Items.FISHING_ROD));
			fishers.add(v);
		}
		int[] caughtBefore = {-1};
		helper.runAtTickTime(500, () -> {
			caughtBefore[0] = caught(fishers);
			setDelay(level, 1);
		});
		helper.runAtTickTime(1100, () -> {
			helper.assertTrue(caughtBefore[0] == 0, caughtBefore[0] + " fish caught at workplaceBuildDelay 2^30 before it was lowered");
			int now = caught(fishers);
			helper.assertTrue(now > 0, "workplaceBuildDelay lowered from 2^30 to 1 at tick 500 (a bite then takes 20 to 60 ticks), but four fishermen"
				+ " caught nothing in the next 600 ticks: they still wait out the old bite");
			helper.succeed();
		});
	}

	private static int caught(List<Villager> fishers) {
		int caught = 0;
		for (Villager v : fishers) {
			caught += ModAttachments.FISH_CAUGHT.getOrElse(v, 0);
		}
		return caught;
	}
}
