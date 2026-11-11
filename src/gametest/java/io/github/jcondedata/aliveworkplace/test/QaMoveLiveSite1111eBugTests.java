package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-11), 45.6: "a building with a live build site [is] refused with a reason" (moving-buildings.md:
 * "builders are still working on it; move it once they're done"). A's move to S is confirmed; then, before the Mover
 * starts it, the builders start work on A (a repair or an upgrade: a live build site at A's own corner). The Mover must
 * not take A down under the builders: the move waits (nothing packed) while the site is live, and starts once it is done.
 * Village and helpers from QaMoveSpotSaves1111eGameTests.
 */
public class QaMoveLiveSite1111eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** A's move confirmed, then a live build site on A: the Mover's work doesn't start taking A down. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveLive1111eWaits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveLive1111eWaits")
	public void aConfirmedMoveWaitsWhileBuildersWorkOnTheBuilding(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		UUID[] made = new UUID[1];
		Leftovers.after(helper, () -> {
			if (made[0] != null) {
				sites.remove(made[0]);
			}
		});
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			BuildSite repair = sites.create(UUID.randomUUID(), "Builder", HUT,
				new BlueprintData.Placement(v.level().dimension().location(), v.a(), Rotation.NONE, Mirror.NONE));
			made[0] = repair.id();
			helper.assertTrue(!repair.isDone(), "the build site on A isn't live (setup)");
			Optional<MoveSite> work = MoveSites.get(v.level()).work(v.level(), v.hall());
			for (int i = 0; i < 20 && work.isPresent() && work.get().stage() != MoveSite.Stage.DONE; i++) {
				MoveSites.get(v.level()).step(v.level(), work.get());
			}
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(now[0] == before[0] && now[1] == 0, "with the builders working on A the Mover "
				+ (work.isPresent() ? "started its move (" + work.get().stage() + ")" : "had nothing to do") + " and took " + (before[0] - now[0])
				+ " of A's " + before[0] + " blocks down under them; " + now[1] + " stand at S");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(), "A's confirmed move was dropped, not kept waiting");
			// the builders finish: the move goes ahead
			sites.remove(repair.id());
			made[0] = null;
			Optional<MoveSite> later = MoveSites.get(v.level()).work(v.level(), v.hall());
			helper.assertTrue(later.isPresent() && later.get().from().equals(v.a()), "once the builders are done A's move doesn't start: " + later);
			helper.succeed();
		});
	}
}
