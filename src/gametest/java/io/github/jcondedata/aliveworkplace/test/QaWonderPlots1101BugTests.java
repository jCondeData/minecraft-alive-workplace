package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1101-0038), from B714's spec ("a Wonder going up with a placement" holds its ground, like any build in
 * progress) and 27.x's plot rule ("clear of every build site and finished building of anyone's"): the Steward's plot
 * search must not pick a spot inside a Wonder that is begun and laid out but waiting for its stage's goods. Such a
 * Wonder has no build sites and isn't finished, and B714's fix taught the Wonder Plan and the player's blueprint about
 * it, but not {@code Plots}, which looks only at build sites and finished buildings.
 */
public class QaWonderPlots1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The Well's spot nearest the hall (box x 8..14, z 10..16, as in PlotGameTests). */
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);
	/** The folly's stage I (9 by 9) laid out at x 7..15, z 9..17: over the whole of the Well's spot. */
	private static final BlockPos WONDER_ORIGIN = new BlockPos(7, 2, 9);

	/** Flat grass over dirt, the hall and a Blueprint Table; no builds left from earlier batches near the area. */
	private static BlockPos ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 22; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		return hall;
	}

	/** A Homes zone at helper x 6..29, z 2..25. */
	private static CityPlan homes(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(HALL);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
	}

	private static Plots.Verdict atSpot(GameTestHelper helper, CityPlan plan) {
		return Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", WELL, helper.absolutePos(SPOT),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	/**
	 * The Steward's plot check over open ground that a Wonder (begun, laid out, its Foundation Stone set outside the spot,
	 * stage I waiting for its bread) will stand on. Control first: with no Wonder the Well fits there. Expected: once the
	 * Wonder is laid out, the spot is refused (as {@code BUILD}, like any build in progress), and fits again when the
	 * Wonder is gone.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWonderPlots1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWonderPlots1101")
	public void qaTheStewardPlansNoBuildingInsideAWonderWaitingForItsGoods(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		CityPlan plan = homes(helper);
		helper.runAfterDelay(2, () -> {
			Plots.Verdict before = atSpot(helper, plan);
			helper.assertTrue(before.plot().isPresent(), "set-up: the Well doesn't fit on the open ground: " + before);

			WonderManager manager = WonderManager.get(level);
			WonderSite waiting = manager.begin(FOLLY, hall, null, "", 1);
			Leftovers.after(helper, () -> manager.remove(waiting.id()));
			BlockPos origin = helper.absolutePos(WONDER_ORIGIN);
			BlockPos stone = helper.absolutePos(new BlockPos(6, 2, 8));
			manager.place(waiting.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), stone);
			WonderSite now = manager.site(waiting.id()).orElseThrow();
			helper.assertTrue(now.goingUp() && !now.started() && now.parts().isEmpty(), "set-up: a Wonder waiting for its goods, no build sites");

			Plots.Verdict inside = atSpot(helper, plan);
			helper.assertTrue(inside.plot().isEmpty(), "the Steward's plot check takes the Well's spot inside the waiting Test Folly "
				+ "(stage I laid out over it): " + inside.plot().map(p -> p.box().toString()).orElse("") + "; expected refused (BUILD)");

			manager.remove(waiting.id());
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "with the Wonder gone the spot is still refused: " + atSpot(helper, plan));
			helper.succeed();
		});
	}

	/**
	 * The same gap for a finished Wonder: its parts are no longer finished buildings (B143), so the plot rule's
	 * {@code CLEAR} blocks round "every finished building" don't hold for it. With the folly finished on x -1..7 (its
	 * record laid out there), the Well's spot at x 8..14 touches its east wall. Expected: refused (BUILD), as next to any
	 * finished building.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWonderPlotsDone1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWonderPlotsDone1101")
	public void qaTheStewardKeepsHisPlotsClearOfAFinishedWonder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		CityPlan plan = homes(helper);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "set-up: the Well doesn't fit on the open ground: " + atSpot(helper, plan));
			WonderManager manager = WonderManager.get(level);
			WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
			Leftovers.after(helper, () -> manager.remove(site.id()));
			BlockPos origin = helper.absolutePos(new BlockPos(-1, 2, 9));
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
			manager.finish(site.id(), 2);
			helper.assertTrue(manager.site(site.id()).map(WonderSite::done).orElse(false), "set-up: the Wonder isn't finished");

			Plots.Verdict touching = atSpot(helper, plan);
			helper.assertTrue(touching.plot().isEmpty(), "the Steward's plot check takes the Well's spot against the finished Test Folly's wall "
				+ "(no gap, CLEAR is " + Plots.CLEAR + "): " + touching.plot().map(p -> p.box().toString()).orElse("") + "; expected refused (BUILD)");
			helper.succeed();
		});
	}
}
