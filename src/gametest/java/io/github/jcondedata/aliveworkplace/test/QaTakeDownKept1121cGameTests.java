package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.takeDown;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-21, lane-c-1121-2153): B1432's fix (builder.md: "A take-down of a build of an older drawing that isn't
 * shipped takes nothing down ...; the Builder says so ("took nothing down") and the hall keeps the build ...; a take-down
 * of a build already gone forgets it"; CHANGELOG: "the house can still be moved, repaired or refreshed"), tested from that
 * spec beyond {@code QaOldDrawings1121cGameTests#aTakeDownThatRemovesNothingKeepsTheHousesRecord} (the record kept): the
 * owner's chat line (the new sentence, rendered, and not the "took down" one), every block still standing, the record
 * surviving a restart, a second take-down still possible, and the other side, a house of an unknown drawing already gone,
 * forgotten. Built on {@link RebuildDrawings1117e} with the old drawing left out of the history (as for the ~10 files
 * changed before history.py tracked them).
 */
public class QaTakeDownKept1121cGameTests implements FabricGameTest {

	/** The stage with an owner whose chat lines land in {@code seen} (and who owns the house and the take-down). */
	private static Stage listened(GameTestHelper helper, String what, List<Component> seen) {
		ResourceLocation id = id(helper, what);
		Stage base = stage(helper, id, true);
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Stage(base.level(), base.id(), base.placement(), owner);
	}

	private static int standing(GameTestHelper helper) {
		return QaOldDrawings1121cGameTests.oldBlocks(helper).size() + where(helper, Blocks.RED_BED).size();
	}

	private static boolean listed(Stage s) {
		return QaOldDrawings1121cGameTests.listed(s);
	}

	/**
	 * The sneak take-down of a finished old-drawing house whose drawing nothing knows: all 42 blocks still stand, the owner
	 * reads "... took nothing down ..." (rendered, naming the builder's blueprint, no raw key) and not "took down", the
	 * hall keeps the house across a restart, and the house can be handed over for a take-down again.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaTdKept1121cNothing"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaTdKept1121cNothing")
	public void aTakeDownThatTookNothingSaysSoAndTheHouseStaysListedAfterARestart(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		Stage s = listened(helper, "tdsay", seen);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			install(s.level(), s.id(), newBlueprint(s.id(), false)); // no history: the old drawing is unknown
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still taking it down");
			helper.assertTrue(standing(helper) == 42, "the take-down that couldn't know the house took " + (42 - standing(helper)) + " of its 42 blocks");
			Component nothing = QaStageFourGameTests.find(seen, "message.aliveworkplace.finished_deconstruct_nothing");
			helper.assertTrue(nothing != null, "the owner wasn't told nothing came down: " + QaStageFourGameTests.keys(seen));
			QaStageFourGameTests.assertRendered(helper, nothing, "took nothing down", "Village Hall");
			helper.assertTrue(QaStageFourGameTests.find(seen, "message.aliveworkplace.finished_deconstruct") == null,
				"the owner was also told the house was taken down: " + QaStageFourGameTests.keys(seen));
			helper.assertTrue(listed(s), "the hall forgot the house nothing was taken from");
			reload(helper, s.level(), null);
			helper.assertTrue(listed(s), "after a restart the hall forgot the house the take-down left standing");
			BuildSite again = takeDown(helper, s, builder[0]);
			helper.assertTrue(again.placement().equals(s.placement()), "a second take-down started somewhere else: " + again.placement().origin());
			BuildSiteManager.get(s.level()).remove(again.id());
		});
	}

	/**
	 * The other side: the same unknown-drawing house already gone (every block of it removed, by hand or by a creeper) when
	 * its take-down is handed over: once that take-down ends the hall forgets it (builder.md: "a take-down of a build
	 * already gone forgets it"), and nothing new stands on its spot.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaTdKept1121cGone"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaTdKept1121cGone")
	public void aTakeDownOfAnUnknownDrawingHouseAlreadyGoneForgetsIt(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		Stage s = listened(helper, "tdgone", seen);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			install(s.level(), s.id(), newBlueprint(s.id(), false)); // no history: the old drawing is unknown
			for (BlockPos p : BlockPos.betweenClosed(HOUSE, HOUSE.offset(6, 3, 4))) {
				put(helper, p.immutable(), Blocks.AIR.defaultBlockState());
			}
			reload(helper, s.level(), null);
			helper.assertTrue(listed(s), "(setup) the gone house isn't listed before its take-down");
			builder[0] = builder(helper, s);
			Builders.assign(s.owner(), builder[0], takeDownStack(s), true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still taking it down");
			helper.assertTrue(!listed(s), "the hall still lists a house of which nothing stands after its take-down (said: "
				+ QaStageFourGameTests.keys(seen) + ")");
			helper.assertTrue(standing(helper) == 0, "blocks of the gone house came back: " + standing(helper));
		});
	}

	/** The house's placed blueprint as a sneak hand-over sends it. */
	private static net.minecraft.world.item.ItemStack takeDownStack(Stage s) {
		net.minecraft.world.item.ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(s.id(), null);
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem
			.data(blueprint).orElseThrow().withPlacement(java.util.Optional.of(s.placement())));
		return blueprint;
	}
}
