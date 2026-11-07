package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-e-1103-0412, saves) for B837's saved count of CLEAR passes ({@code clear_passes}, "Saved; 0 in older saves"):
 * how often CLEAR went back over its list for sand or gravel that fell in, capped per build so a site never loops. A save
 * and load keeps the count, so a reload doesn't hand a site its passes back, and a site from before B837 starts at none.
 * The cap is checked through the end-of-CLEAR check itself (BuilderWork.fellIn) on the loaded site: one pass short of it a
 * sand block in a cleared spot sends the lead back; at it, the sand is counted as left, never claimed done.
 */
public class QaClearPassesSaved1103eGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int MOST = 16; // BuilderWork.MOST_CLEAR_PASSES

	private static BuildSite site(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE));
	}

	private static boolean fellIn(ServerLevel level, BuildSite site, BuildPlan plan, BlockPos bench) throws ReflectiveOperationException {
		Method m = BuilderWork.class.getDeclaredMethod("fellIn", ServerLevel.class, BuildSite.class, BuildPlan.class, BlockPos.class);
		m.setAccessible(true);
		return (boolean) m.invoke(null, level, site, plan, bench);
	}

	/** The first CLEAR step the blueprint leaves empty: where sand that fell in must be dug out again. */
	private static BlockPos emptySpot(BuildPlan plan) {
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.CLEAR)) {
			if (step.state().isAir()) {
				return step.pos();
			}
		}
		return null;
	}

	/** 3 passes and the cap's 16 come back from a save; a site saved with none writes no count and loads with none. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void clearPassesSurviveASaveAndAnOlderSiteHasNone(GameTestHelper helper) {
		BuildSite fresh = site(helper);
		helper.assertTrue(fresh.plan(helper.getLevel()) != null, "setup: test hut blueprint missing");
		CompoundTag none = fresh.save();
		helper.assertTrue(!none.contains("clear_passes"), "a site with no extra CLEAR pass wrote clear_passes " + none.get("clear_passes"));
		helper.assertTrue(BuildSite.load(none).clearPasses() == 0, "a site saved before B837 (no clear_passes) loaded with "
			+ BuildSite.load(none).clearPasses() + " passes");
		for (int passes : new int[] {3, MOST}) {
			BuildSite s = site(helper);
			for (int i = 0; i < passes; i++) {
				s.backOverClear();
			}
			BuildSite back = BuildSite.load(s.save());
			helper.assertTrue(back.clearPasses() == passes, passes + " CLEAR passes went into the save, " + back.clearPasses() + " came back");
			helper.assertTrue(back.stage() == BuildPlan.Stage.CLEAR, "a site sent back over CLEAR loaded in " + back.stage());
		}
		helper.succeed();
	}

	/**
	 * Sand in a spot CLEAR had emptied, on a site loaded from a save: one pass short of the cap the lead goes back over
	 * CLEAR; loaded at the cap he doesn't (a reload never restarts the count) and the sand is counted as left.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theCapOnClearPassesHoldsAcrossASave(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(0, 2, 0));
		BuildSite probe = site(helper);
		BuildPlan plan = probe.plan(level);
		helper.assertTrue(plan != null, "setup: test hut blueprint missing");
		BlockPos spot = emptySpot(plan);
		helper.assertTrue(spot != null, "setup: the test hut leaves no spot empty");
		// placed without updates so it can't fall before the check, as sand that has just come to rest there
		level.setBlock(spot, Blocks.SAND.defaultBlockState(), 2 | 16);
		try {
			BuildSite shortOfIt = site(helper);
			for (int i = 0; i < MOST - 1; i++) {
				shortOfIt.backOverClear();
			}
			BuildSite loadedShort = BuildSite.load(shortOfIt.save());
			helper.assertTrue(loadedShort.stage() == BuildPlan.Stage.CLEAR, "setup: the loaded site isn't in CLEAR: " + loadedShort.stage());
			helper.assertTrue(fellIn(level, loadedShort, plan, bench), "with " + (MOST - 1) + " of " + MOST
				+ " CLEAR passes, loaded from a save, sand in a cleared spot didn't send the lead back over CLEAR");
			helper.assertTrue(loadedShort.clearPasses() == MOST, "that pass wasn't counted: " + loadedShort.clearPasses());

			BuildSite atIt = BuildSite.load(loadedShort.save());
			int skipped = atIt.skipped();
			helper.assertTrue(!fellIn(level, atIt, plan, bench), "loaded at the cap of " + MOST
				+ " CLEAR passes, sand in a cleared spot sent the lead back over CLEAR again (" + atIt.clearPasses() + " passes): the save gave the site its passes back");
			helper.assertTrue(atIt.skipped() > skipped, "at the cap the sand left in a cleared spot wasn't counted as left (skipped "
				+ skipped + " -> " + atIt.skipped() + ")");
		} finally {
			level.setBlock(spot, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		helper.succeed();
	}
}
