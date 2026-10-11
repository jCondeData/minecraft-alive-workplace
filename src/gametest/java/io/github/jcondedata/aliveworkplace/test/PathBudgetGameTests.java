package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.PathBudget;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * 25.3: villagers' path searches share a budget a tick per level ({@link PathBudget}). A walk held back keeps its
 * target and starts on a later tick, one held back long enough goes ahead over the budget, and a crowd asking at once
 * is spread over several ticks with every walk started.
 */
public class PathBudgetGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		return villager;
	}

	private static boolean near(GameTestHelper helper, Villager villager, BlockPos target) {
		return villager.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(target))) < 2.0;
	}

	/** With the budget spent, a worker's walk keeps its target and gets no path; once there is room it walks there. */
	//$ gametest_ticks_batch AREA '200' '"pathBudgetHeld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "pathBudgetHeld")
	public void aHeldBackWalkStartsOnALaterTick(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos target = new BlockPos(3, 2, 18);
		Villager villager = villager(helper, new BlockPos(3, 2, 3));
		long deferred = PathBudget.deferred(level);
		PathBudget.spentUntil(level, level.getGameTime() + 6);
		Walker.requestWalk(villager, helper.absolutePos(target), 0.6f, 1, 0);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(villager.getNavigation().getPath() == null, "the villager got a path while the budget was spent");
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
					.map(t -> t.getTarget().currentBlockPosition().equals(helper.absolutePos(target))).orElse(false),
				"the walk target was dropped while it waited for the budget");
			helper.assertTrue(PathBudget.deferred(level) > deferred, "no walk was counted as held back");
		});
		helper.runAfterDelay(5, () -> helper.succeedWhen(() -> helper.assertTrue(near(helper, villager, target),
			"the villager never walked to the target after the budget had room again")));
	}

	/** A walk held back for {@link PathBudget#MOST_WAIT} ticks gets its search even though the budget stays spent. */
	//$ gametest_ticks_batch AREA '200' '"pathBudgetWait"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "pathBudgetWait")
	public void aWalkHeldBackLongEnoughGoesAhead(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos target = new BlockPos(3, 2, 18);
		Villager villager = villager(helper, new BlockPos(3, 2, 3));
		PathBudget.spentUntil(level, level.getGameTime() + 1000);
		Walker.requestWalk(villager, helper.absolutePos(target), 0.6f, 1, 0);
		helper.runAfterDelay(PathBudget.MOST_WAIT - 4, () -> helper.assertTrue(villager.getNavigation().getPath() == null,
			"the villager got a path before it had waited " + PathBudget.MOST_WAIT + " ticks"));
		helper.runAfterDelay(PathBudget.MOST_WAIT + 4, () -> helper.assertTrue(villager.getNavigation().getPath() != null,
			"the villager still had no path after waiting " + PathBudget.MOST_WAIT + " ticks"));
		helper.runAfterDelay(PathBudget.MOST_WAIT + 5, () -> helper.succeedWhen(() -> {
			helper.assertTrue(near(helper, villager, target), "the villager never walked to the target");
			PathBudget.spentUntil(level, level.getGameTime());
		}));
	}

	/** 30 villagers asking at once: at most a budget's worth of new paths a tick at first, and all of them walk. */
	//$ gametest_ticks_batch AREA '300' '"pathBudgetCrowd"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "pathBudgetCrowd")
	public void aCrowdIsSpreadOverTicksAndAllWalk(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Villager> crowd = new ArrayList<>();
		List<BlockPos> targets = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			BlockPos from = new BlockPos(2 + (i % 6) * 2, 2, 2 + (i / 6) * 2);
			Villager villager = villager(helper, from);
			crowd.add(villager);
			BlockPos target = from.offset(0, 0, 14);
			targets.add(target);
			Walker.requestWalk(villager, helper.absolutePos(target), 0.6f, 1, 0);
		}
		Set<Villager> walked = new HashSet<>();
		Set<Villager> arrived = new HashSet<>();
		int[] most = {0};
		int[] retry = new int[crowd.size()];
		int[] ticks = {0};
		helper.onEachTick(() -> {
			int before = walked.size();
			for (int i = 0; i < crowd.size(); i++) {
				Villager villager = crowd.get(i);
				if (villager.getNavigation().getPath() != null) {
					walked.add(villager);
				}
				// (Once there, a crowd of idle villagers wanders off to meet each other: arriving is what counts.)
				if (near(helper, villager, targets.get(i))) {
					arrived.add(villager);
				} else if (!arrived.contains(villager)) {
					// As a worker's job does each tick until it gets there.
					retry[i] = Walker.requestWalk(villager, helper.absolutePos(targets.get(i)), 0.6f, 1, retry[i]);
				}
			}
			if (++ticks[0] < PathBudget.MOST_WAIT) {
				most[0] = Math.max(most[0], walked.size() - before);
			}
		});
		helper.runAfterDelay(PathBudget.MOST_WAIT, () -> {
			helper.assertTrue(most[0] <= PathBudget.PER_TICK,
				most[0] + " villagers got a path in one tick (the budget is " + PathBudget.PER_TICK + ")");
			helper.assertTrue(walked.size() >= PathBudget.PER_TICK, "only " + walked.size() + " villagers got a path in " + PathBudget.MOST_WAIT + " ticks");
		});
		helper.runAfterDelay(PathBudget.MOST_WAIT + 1, () -> helper.succeedWhen(() ->
			helper.assertTrue(arrived.size() == crowd.size(), arrived.size() + " of " + crowd.size() + " villagers reached their targets")));
	}
}
