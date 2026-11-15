package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane D, 2026-11-15), ROADMAP 41.1/41.2 with the real Waystones mod: "a waystone by every Village Hall": the stone
 * of a plaza still going up (its builder not done, the stone already set) counts for the village when the plaza is
 * within the hall radius, the edge included. Kills the VillageWaystones:244 boundary survivor ({@code <=} to {@code <},
 * a plaza going up exactly the radius from the hall): with it the hall's round drops the village off the network and
 * leaves its stone unnamed.
 */
public class QaWaystonePlazaAtRadius1115dCompatTests implements FabricGameTest {
	static final String AREA = WaystoneQaCompatTests.AREA;
	private static final int RADIUS = 20;

	/** The plaza at {@code origin} with every block set (its stone too), but recorded as a site still going up. */
	private static BuildSite plazaGoingUp(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, WaystoneQaCompatTests.placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		return BuildSiteManager.get(level).create(UUID.randomUUID(), "Jesse", VillageWaystones.PLAZA, WaystoneQaCompatTests.placement(helper, origin));
	}

	/**
	 * Ashford's hall at (1, 2, 1) and its plaza going up with its corner exactly {@value #RADIUS} blocks east (the radius
	 * set to {@value #RADIUS}): the hall's round names the stone after Ashford and keeps the village on the network.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaystonePlazaAtRadius1115d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaystonePlazaAtRadius1115d")
	public void aPlazaGoingUpRightAtTheHallRadiusIsTheVillagesOwn(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = RADIUS;
		BlockPos plazaAt = WaystoneQaCompatTests.HALL_A.offset(RADIUS, 0, 0);
		BlockPos hall = helper.absolutePos(WaystoneQaCompatTests.HALL_A);
		BuildSite site = null;
		try {
			WaystoneQaCompatTests.hall(helper, WaystoneQaCompatTests.HALL_A, "Ashford", UUID.randomUUID());
			site = plazaGoingUp(helper, plazaAt);
			helper.assertTrue(helper.absolutePos(plazaAt).distSqr(hall) == (double) RADIUS * RADIUS, "setup: the plaza is not exactly the radius from the hall");
			helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(plazaAt)).map(hall::equals).orElse(false),
				"setup: Ashford's hall is not the plaza's nearest: " + VillageHalls.nearest(level, helper.absolutePos(plazaAt)));
			helper.assertFalse(level.getBlockState(WaystoneQaCompatTests.stone(helper, plazaAt)).isAir(), "setup: the plaza's waystone block isn't there");
			VillageWaystones.round(level, hall);
			helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, plazaAt).equals(Optional.of("Ashford")),
				"a plaza going up right at the hall radius: its stone is " + WaystoneQaCompatTests.nameAt(helper, plazaAt) + ", not Ashford");
			helper.assertTrue(VillageWaystones.standing(level, hall), "a plaza going up right at the hall radius: Ashford is off the waystone network");
			helper.assertTrue(VillageWaystones.stones(level, hall).contains(WaystoneQaCompatTests.stone(helper, plazaAt)),
				"Ashford's stones leave out its plaza going up at the radius: " + VillageWaystones.stones(level, hall));
		} finally {
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			VillageWaystones.Data.get(level).forget(hall);
			VillageHalls.RADIUS = radius;
		}
		helper.succeed();
	}
}
