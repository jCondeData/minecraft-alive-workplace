package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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
 * QA lane E (2026-11-17, lane-e-1117-1013): the cases of {@link QaRebuildSaves1117eGameTests} that FAIL on main because
 * of the mod (not registered in fabric.mod.json, so main stays green; register it to reproduce). Each is REBUILD.md's
 * "standing buildings and sites mid-build keep their old drawing until a refresh (36.21)" against a drawing rebuilt
 * under the same id ({@link RebuildDrawings1117e}).
 */
public class QaRebuildSaves1117eBugTests implements FabricGameTest {

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

	/**
	 * A finished house of the old drawing, not damaged at all, after the drawing is rebuilt: with builderRepairs on (not
	 * asking first), the builder's round finds nothing to repair. Wanted: no repair started, and none of the new
	 * drawing's blocks put into the old house.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRebuild1117eBugUndamaged"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRebuild1117eBugUndamaged")
	public void anUndamagedHouseOfTheOldDrawingIsNotRepairedIntoTheNewOne(GameTestHelper helper) {
		ResourceLocation id = id(helper, "bugundamaged");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		boolean enabled = Upkeep.ENABLED;
		boolean ask = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = ask;
		});
		Villager[] builder = {null};
		BuildSite[] repair = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			repair[0] = Upkeep.look(s.level(), builder[0]);
		});
		helper.runAfterDelay(800, () -> {
			List<BlockPos> mixed = newBlocks(helper);
			helper.assertTrue(repair[0] == null && mixed.isEmpty(), "an undamaged house of the old drawing was "
				+ (repair[0] != null ? "set for repair" : "not set for repair") + " and got " + mixed.size()
				+ " blocks of the new drawing (stone bricks, spruce) at " + first(mixed));
			helper.succeed();
		});
	}

	/**
	 * A finished house of the old drawing with three blocks broken (two outside the new drawing's footprint, one on a spot
	 * the new drawing fills otherwise), after the drawing is rebuilt: the repair puts back what it was built with. Wanted:
	 * the three blocks back as the old drawing had them, none of the new drawing's blocks.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaRebuild1117eBugRepair"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaRebuild1117eBugRepair")
	public void aDamagedHouseOfTheOldDrawingIsRepairedToItsOwnDrawing(GameTestHelper helper) {
		ResourceLocation id = id(helper, "bugrepair");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		BlockPos backWall = new BlockPos(0, 1, 4);
		BlockPos backFloor = new BlockPos(2, 0, 4);
		BlockPos frontWall = new BlockPos(1, 1, 0);
		for (BlockPos p : List.of(backWall, backFloor, frontWall)) {
			put(helper, at(p), Blocks.AIR.defaultBlockState());
		}
		boolean enabled = Upkeep.ENABLED;
		boolean ask = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = ask;
		});
		Villager[] builder = {null};
		boolean[] started = {false};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			started[0] = Upkeep.look(s.level(), builder[0]) != null;
			helper.assertTrue(started[0], "the damaged house wasn't set for repair");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started[0], "not started");
			helper.assertTrue(sites(s) == 0, "still repairing");
			List<BlockPos> mixed = newBlocks(helper);
			boolean back = helper.getLevel().getBlockState(helper.absolutePos(at(backWall))).is(Blocks.OAK_PLANKS)
				&& helper.getLevel().getBlockState(helper.absolutePos(at(backFloor))).is(Blocks.COBBLESTONE)
				&& helper.getLevel().getBlockState(helper.absolutePos(at(frontWall))).is(Blocks.OAK_PLANKS);
			helper.assertTrue(back && mixed.isEmpty(), "the repaired house of the old drawing: its 3 broken blocks "
				+ (back ? "back" : "NOT all back (back wall " + helper.getLevel().getBlockState(helper.absolutePos(at(backWall))).getBlock()
				+ ", back floor " + helper.getLevel().getBlockState(helper.absolutePos(at(backFloor))).getBlock() + ", front wall "
				+ helper.getLevel().getBlockState(helper.absolutePos(at(frontWall))).getBlock() + ")")
				+ ", and " + mixed.size() + " blocks of the new drawing put in at " + first(mixed));
		});
	}

	/**
	 * Level 1 of the old drawing upgraded through the Upgrades page to the rebuilt family's Level 2 (another footprint):
	 * when it's done, nothing of the old Level 1 is left standing outside the new level (half-old, half-new for good).
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eBugUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eBugUpgrade")
	public void anUpgradeOverTheOldDrawingLeavesNoHalfOfTheOldHouse(GameTestHelper helper) {
		ResourceLocation id = id(helper, "bugupgrade");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		UpgradesPage.Outcome[] outcome = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			ResourceLocation next = io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(id);
			install(s.level(), next, newBlueprint(next, true));
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			outcome[0] = UpgradesPage.order(s.level(), helper.absolutePos(HALL), s.owner(), s.placement());
			helper.assertTrue(outcome[0].ok(), "(setup) the upgrade was refused: " + outcome[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(outcome[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still upgrading");
			List<BlockPos> oldLeft = new ArrayList<>(where(helper, Blocks.COBBLESTONE));
			oldLeft.addAll(where(helper, Blocks.OAK_PLANKS));
			helper.assertTrue(oldLeft.isEmpty(), "the upgraded house keeps " + oldLeft.size() + " blocks of the old Level 1 (cobblestone, oak planks) at "
				+ first(oldLeft) + "; the bed " + (helper.getLevel().getBlockState(helper.absolutePos(at(BED_FOOT))).is(Blocks.RED_BED) ? "still stands" : "is gone"));
		});
	}
}
