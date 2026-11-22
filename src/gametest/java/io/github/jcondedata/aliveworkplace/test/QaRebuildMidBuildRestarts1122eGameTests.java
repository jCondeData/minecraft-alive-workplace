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
 * QA lane E (2026-11-22, lane-e-1122-1913): B1371's promise (tools/blueprints/REBUILD.md, "sites mid-build keep their old
 * drawing until a refresh"; CHANGELOG) held over more than one restart. The fix's own test restarts the world once, right
 * when the rebuilt drawing ships; a server restarts again and again while a house goes up, and every restart after the
 * first must still build the old drawing, not switch to the new one half way. Written from the commit message and
 * REBUILD.md after a mutation spot-check (saving the site without the drawing it was begun from) survived the suite.
 */
public class QaRebuildMidBuildRestarts1122eGameTests implements FabricGameTest {

	private static String first(List<BlockPos> spots) {
		return spots.stream().limit(6).map(p -> p.getX() + "," + p.getY() + "," + p.getZ()).toList() + (spots.size() > 6 ? "..." : "");
	}

	/** The new drawing's own blocks (stone bricks, spruce planks): none may appear in a house begun from the old one. */
	private static List<BlockPos> newBlocks(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>(where(helper, Blocks.STONE_BRICKS));
		out.addAll(where(helper, Blocks.SPRUCE_PLANKS));
		return out;
	}

	/**
	 * A house of the old drawing caught with its floor down when the rebuilt drawing ships and the world restarts; then the
	 * world restarts twice more while it goes on building (a few blocks after the first restart, and again a few blocks
	 * later). Wanted: the old house whole (25 cobblestone, 15 oak planks), none of the new drawing's blocks, the site ended.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1122eMidBuildRestarts"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1122eMidBuildRestarts")
	public void aSiteMidBuildKeepsItsOldDrawingThroughLaterRestarts(GameTestHelper helper) {
		ResourceLocation id = id(helper, "restarts");
		Stage s = stage(helper, id, false);
		Villager[] builder = {builder(helper, s)};
		BuildSite site = Builders.start(s.level(), builder[0], s.owner(), id, s.placement());
		helper.assertTrue(site != null, "(setup) the old drawing didn't start");
		UUID siteId = site.id();
		int[] restarts = {0};
		int[] planksAtLast = {0};
		helper.onEachTick(() -> {
			if (restarts[0] >= 3) {
				return;
			}
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			if (restarts[0] == 0) {
				// the whole floor down (its back rows lie outside the new drawing) and the first walls
				if (where(helper, Blocks.COBBLESTONE).size() == 25 && planks >= 1 && planks <= 6) {
					restarts[0] = 1;
					planksAtLast[0] = planks;
					rebuild(s.level(), id, false);
					builder[0] = reload(helper, s.level(), builder[0]);
				}
			} else if (planks >= planksAtLast[0] + 2 && planks < 15) {
				// a later restart, a couple of the old walls' blocks after the last one, the house still unfinished
				restarts[0]++;
				planksAtLast[0] = planks;
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarts[0] >= 2, "(setup) only " + restarts[0] + " restarts caught mid-build");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(siteId) == null, "the site hasn't ended");
			int cobble = where(helper, Blocks.COBBLESTONE).size();
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			List<BlockPos> mixed = newBlocks(helper);
			helper.assertTrue(cobble == 25 && planks == 15 && mixed.isEmpty(), "after " + restarts[0] + " restarts a house begun from the old drawing ended with "
				+ cobble + " of its 25 cobblestone and " + planks + " of its 15 oak planks, and " + mixed.size()
				+ " blocks of the new drawing (stone bricks, spruce) at " + first(mixed));
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qa1122e] restarts " + restarts[0] + ": old house whole (" + cobble + " cobblestone, " + planks + " oak planks), no new blocks"));
		});
	}
}
