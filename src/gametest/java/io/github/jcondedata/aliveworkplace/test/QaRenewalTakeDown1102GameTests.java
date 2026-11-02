package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for 27.21 (old villages renewed) and B789 from the spec ("the builder takes it down as a deconstruction, its blocks
 * going to the store; once that is done the new one goes up on the cleared plot"). RenewalGameTests does each take-down
 * at once by hand; here the village's own builder does it on foot, as a player sees it, both when the owner approves the
 * renewal and when the Steward does (Run the village, approver null). It must leave STARTING, take blocks down, and hand
 * over to the rebuild.
 */
public class QaRenewalTakeDown1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");

	private static void takeDown(GameTestHelper helper, boolean byOwner) {
		RenewalGameTests.Village v = RenewalGameTests.village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		OldHouses.House house = RenewalGameTests.measure(helper, v, RenewalGameTests.beds(level, old).get(0));
		StewardDesk.Proposal proposal = RenewalGameTests.propose(helper, v, house);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), byOwner ? v.owner() : null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the renewal wasn't started: " + outcome);
		BuildSite down = RenewalGameTests.site(helper, v);
		helper.assertTrue(down.isDeconstruction(), "setup: the first site isn't the take-down");
		helper.assertTrue(v.builder().getUUID().equals(down.builder()), "the village's builder isn't given the take-down: " + down.builder());
		var id = down.id();
		helper.succeedWhen(() -> {
			List<Renewals.Renewal> active = Renewals.active(level, v.hall());
			BuildSite now = active.isEmpty() ? null : BuildSiteManager.get(level).get(active.get(0).site());
			BuildSite takeDown = BuildSiteManager.get(level).get(id);
			String state = takeDown == null ? "gone" : takeDown.status() + ", " + takeDown.placed() + " placed, " + takeDown.skipped()
				+ " skipped, stage " + takeDown.stage();
			helper.assertTrue(now != null && !now.isDeconstruction(),
				"the old house isn't taken down and handed to the rebuild yet: take-down " + state + "; builder at "
					+ helper.relativePos(v.builder().blockPosition()) + ", renewals under way " + active.size());
		});
	}

	/** Approved by the owner: the builder walks over, takes the old house down and the rebuild site follows. */
	//$ gametest_ticks_batch AREA '6000' '"qaRenewTakeDownOwner1102"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRenewTakeDownOwner1102")
	public void qaTheBuilderTakesTheOldHouseDownOnFoot(GameTestHelper helper) {
		takeDown(helper, true);
	}

	/** Approved by the Steward himself (Run the village, no player): the same, never stuck at STARTING (B789). */
	//$ gametest_ticks_batch AREA '6000' '"qaRenewTakeDownSteward1102"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRenewTakeDownSteward1102")
	public void qaARenewalTheStewardApprovesIsTakenDownToo(GameTestHelper helper) {
		takeDown(helper, false);
	}
}
