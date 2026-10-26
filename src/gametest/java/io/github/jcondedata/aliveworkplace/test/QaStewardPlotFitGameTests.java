package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.7 (finding a plot), from the spec, beyond PlotGameTests and QaStewardPlotsGameTests: with two roads on
 * the plan the front faces the nearer one; blocks in {@code aliveworkplace:steward_clearable} (a pack's natural plants)
 * are cleared, a player's carved pumpkin is not. The Well's spot as in PlotGameTests: box x 8..14, z 10..16 (helper).
 */
public class QaStewardPlotFitGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private static void ground(GameTestHelper helper) {
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
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
	}

	private static CityPlan homes(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(HALL);
		BitSet out = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				out.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, out);
	}

	private static Plots.Verdict check(GameTestHelper helper, CityPlan plan, Rotation turn) {
		return Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", WELL, helper.absolutePos(SPOT), turn, Mirror.NONE);
	}

	/**
	 * Two roads on the plan, a lane east at helper x = 28 (17 blocks from the spot's centre) and one north at z = 4 (9
	 * blocks): the front faces the nearer, north; with only the east lane it faces east.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaPlotTwoRoads"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotTwoRoads")
	public void theFrontFacesTheNearerOfTwoRoads(GameTestHelper helper) {
		ground(helper);
		CityPlan east = homes(helper).addRoad(new CityPlan.Road(List.of(new BlockPos(26, 0, -12), new BlockPos(26, 0, 11)), CityPlan.Road.LANE, ""));
		helper.assertTrue(check(helper, east, Rotation.CLOCKWISE_90).plot().isPresent(), "east lane only: facing east refused: " + check(helper, east, Rotation.CLOCKWISE_90));
		helper.assertTrue(check(helper, east, Rotation.NONE).reason().equals(Optional.of(Plots.Reason.FACING)), "east lane only: facing north allowed");
		CityPlan both = east.addRoad(new CityPlan.Road(List.of(new BlockPos(4, 0, -10), new BlockPos(27, 0, -10)), CityPlan.Road.LANE, ""));
		Plots.Verdict north = check(helper, both, Rotation.NONE);
		helper.assertTrue(north.plot().isPresent(), "two roads: facing the nearer (north) refused: " + north);
		helper.assertTrue(check(helper, both, Rotation.CLOCKWISE_90).reason().equals(Optional.of(Plots.Reason.FACING)),
			"two roads: facing the farther (east) allowed: " + check(helper, both, Rotation.CLOCKWISE_90));
		helper.succeed();
	}

	/** The steward_clearable tag's plants (pumpkin, melon, moss carpet, a mushroom) are cleared; a carved pumpkin is a player's. */
	//$ gametest_ticks_batch AREA '20' '"qaPlotClearable"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotClearable")
	public void theClearableTagsPlantsAreClearedACarvedPumpkinIsNot(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		Block[] natural = {Blocks.PUMPKIN, Blocks.MELON, Blocks.MOSS_CARPET, Blocks.BROWN_MUSHROOM};
		BlockPos[] at = {new BlockPos(9, 2, 11), new BlockPos(13, 2, 11), new BlockPos(9, 2, 15), new BlockPos(13, 2, 15)};
		for (int i = 0; i < natural.length; i++) {
			helper.assertTrue(natural[i].defaultBlockState().is(Plots.CLEARABLE), "setup: " + natural[i] + " isn't in the tag");
			helper.setBlock(at[i], natural[i]);
		}
		Plots.Verdict v = check(helper, plan, Rotation.COUNTERCLOCKWISE_90);
		helper.assertTrue(v.plot().isPresent(), "the tag's plants block the plot: " + v);
		helper.setBlock(new BlockPos(11, 2, 13), Blocks.CARVED_PUMPKIN);
		helper.assertTrue(check(helper, plan, Rotation.COUNTERCLOCKWISE_90).reason().equals(Optional.of(Plots.Reason.BLOCKED)),
			"a carved pumpkin is cleared: " + check(helper, plan, Rotation.COUNTERCLOCKWISE_90));
		helper.succeed();
	}
}
