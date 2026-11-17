package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-17, lane-e-1117-1013), B1371: the mid-build case of QaRebuildSaves1117eBugTests, moved here by
 * lane-a-1117-1223 when B1369 and B1370 were fixed and that class was registered; registered once B1371 was fixed.
 */
public class QaRebuildMidBuild1117eBugTests implements FabricGameTest {

	/** The first few spots of {@code spots}, short enough for the test's failure sign. */
	private static String first(List<BlockPos> spots) {
		return spots.stream().limit(6).map(p -> p.getX() + "," + p.getY() + "," + p.getZ()).toList() + (spots.size() > 6 ? "..." : "");
	}

	private static List<BlockPos> newBlocks(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>(where(helper, Blocks.STONE_BRICKS));
		out.addAll(where(helper, Blocks.SPRUCE_PLANKS));
		return out;
	}

	/**
	 * A site half-way through the old drawing's STRUCTURE when the rebuilt drawing ships and the world restarts. REBUILD.md:
	 * sites mid-build keep their old drawing. Caught once the floor is down and a few walls stand. Wanted: the old house whole (25 cobblestone, 15 oak planks) and none of the
	 * new drawing's blocks.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eBugMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eBugMidBuild")
	public void aSiteMidBuildKeepsItsOldDrawingAfterTheRebuild(GameTestHelper helper) {
		ResourceLocation id = id(helper, "bugmid");
		Stage s = stage(helper, id, false);
		Villager[] builder = {builder(helper, s)};
		BuildSite site = Builders.start(s.level(), builder[0], s.owner(), id, s.placement());
		helper.assertTrue(site != null, "(setup) the old drawing didn't start");
		UUID siteId = site.id();
		boolean[] swapped = {false};
		helper.onEachTick(() -> {
			if (swapped[0]) {
				return;
			}
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			// late in the structure: the whole floor down (the back two rows outside the new footprint too) and some walls
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			if (where(helper, Blocks.COBBLESTONE).size() == 25 && planks >= 2 && planks <= 12) {
				swapped[0] = true;
				rebuild(s.level(), id, false);
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(swapped[0], "(setup) never caught half-way");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(siteId) == null, "the site hasn't ended");
			int cobble = where(helper, Blocks.COBBLESTONE).size();
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			List<BlockPos> mixed = newBlocks(helper);
			helper.assertTrue(cobble == 25 && planks == 15 && mixed.isEmpty(), "a house begun from the old drawing ended with " + cobble
				+ " of its 25 cobblestone and " + planks + " of its 15 oak planks, and " + mixed.size() + " blocks of the new drawing (stone bricks, spruce) at "
				+ first(mixed));
		});
	}
}
