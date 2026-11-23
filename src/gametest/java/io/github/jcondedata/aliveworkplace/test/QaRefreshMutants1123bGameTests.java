package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane B (2026-11-23, lane-b-1123-1239): tests for mutation survivors in the old-drawing code (B1435, B1432, B1500),
 * each from the spec, on {@link RebuildDrawings1117e}'s house (an old 5 x 5 drawing rebuilt to a 7 x 3 one, front at z 0).
 */
public class QaRefreshMutants1123bGameTests implements FabricGameTest {

	/**
	 * B1500 (builder.md: a refresh taken up again starts from the drawing it was begun from "while the build still stands
	 * as it"; Refreshes: "while the build still stands as that drawing more than as today's"). A second older drawing is
	 * shipped that is exactly today's, so the house stands as it no more than as today's (a tie). Remembered as the
	 * drawing a refresh was begun from, it must not steer the refresh: the refresh is offered from the old drawing the
	 * house stands as, and the remembered key is dropped.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRefreshMutants1123bTie"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRefreshMutants1123bTie")
	public void aRememberedDrawingTheHouseStandsAsNoMoreThanTodaysDoesNotSteerTheRefresh(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tie");
		Stage s = stage(helper, id, false);
		standOld(helper, s, false, false);
		rebuild(s.level(), id, false);
		Refreshes.addHistory(id, "same_as_today", newBlueprint(id, false));
		helper.runAfterDelay(2, () -> {
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BuildSiteManager.Finished f = manager.finishedIn(s.level()).stream().filter(b -> b.placement().equals(s.placement())).findFirst()
				.orElseThrow(() -> new IllegalStateException("the house isn't recorded as finished"));
			String guessed = Refreshes.change(s.level(), f).orElseThrow(() -> new IllegalStateException("the old house would change nothing")).from();
			helper.assertTrue("before_rebuild".equals(guessed), "(setup) with nothing remembered the refresh is offered from " + guessed + ", not the old drawing");
			manager.setRefreshBegun(s.placement(), "same_as_today");
			String from = Refreshes.change(s.level(), f).orElseThrow(() -> new IllegalStateException("the old house would change nothing")).from();
			helper.assertTrue("before_rebuild".equals(from),
				"a remembered drawing the house stands as no more than as today's steered the refresh: offered from " + from);
			helper.assertTrue(manager.refreshBegun(s.placement()) == null,
				"the remembered drawing was kept: " + manager.refreshBegun(s.placement()));
			helper.succeed();
		});
	}

	/**
	 * B1435 (builder.md: a building standing as an older drawing of its id "keeps the ground under that drawing's box
	 * too", each "also keeps today's drawing's box"): every edge row of the old house's ground counts as under another
	 * build, its front row (z 0) and side columns as well as its back row (z 4, only the old drawing's), and the rows just
	 * outside it don't.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRefreshMutants1123bGround"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRefreshMutants1123bGround")
	public void everyEdgeOfAnOldDrawingHousesGroundIsUnderIt(GameTestHelper helper) {
		ResourceLocation id = id(helper, "ground");
		Stage s = stage(helper, id, false);
		standOld(helper, s, false, false);
		rebuild(s.level(), id, false);
		helper.runAfterDelay(2, () -> {
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BlueprintData.Placement other = new BlueprintData.Placement(s.level().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 14)),
				Rotation.NONE, Mirror.NONE);
			List<String> wrong = new ArrayList<>();
			int[][] inside = {{2, 0}, {0, 2}, {4, 2}, {2, 4}, {0, 0}, {4, 4}, {6, 0}, {6, 2}};
			for (int[] xz : inside) {
				BlockPos at = helper.absolutePos(HOUSE.offset(xz[0], 0, xz[1]));
				if (!manager.underAnotherBuild(s.level(), other, at)) {
					wrong.add("(" + xz[0] + ", " + xz[1] + ") isn't under the house");
				}
			}
			int[][] outside = {{2, -1}, {-1, 2}, {2, 5}, {7, 1}};
			for (int[] xz : outside) {
				BlockPos at = helper.absolutePos(HOUSE.offset(xz[0], 0, xz[1]));
				if (manager.underAnotherBuild(s.level(), other, at)) {
					wrong.add("(" + xz[0] + ", " + xz[1] + ") outside counts as under the house");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the old house's ground, relative to its corner: " + wrong);
			helper.succeed();
		});
	}

	/**
	 * B1432 (builder.md: the house stands if its spot "still holds a block someone put there (not air, ground, plants or
	 * a kept block)"; "a take-down of a build already gone forgets it"): the unknown-drawing house already gone, with only
	 * grass grown on its spot (grass blocks, short grass and a fern), is forgotten once its take-down ends.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaRefreshMutants1123bGrass"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaRefreshMutants1123bGrass")
	public void aTakeDownOfAGoneHouseWithGrassOnItsSpotForgetsIt(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ResourceLocation id = id(helper, "grass");
		Stage base = stage(helper, id, true);
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Stage s = new Stage(base.level(), base.id(), base.placement(), owner);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			install(s.level(), s.id(), newBlueprint(s.id(), false)); // no history: the old drawing is unknown
			for (BlockPos p : BlockPos.betweenClosed(HOUSE, HOUSE.offset(6, 3, 4))) {
				put(helper, p.immutable(), Blocks.AIR.defaultBlockState());
			}
			for (int x = 1; x <= 3; x++) {
				put(helper, HOUSE.offset(x, 0, 1), Blocks.GRASS_BLOCK.defaultBlockState());
			}
			put(helper, HOUSE.offset(1, 1, 1), Blocks.SHORT_GRASS.defaultBlockState());
			put(helper, HOUSE.offset(2, 1, 1), Blocks.FERN.defaultBlockState());
			put(helper, HOUSE.offset(3, 1, 1), Blocks.SHORT_GRASS.defaultBlockState());
			reload(helper, s.level(), null);
			helper.assertTrue(QaOldDrawings1121cGameTests.listed(s), "(setup) the gone house isn't listed before its take-down");
			builder[0] = builder(helper, s);
			net.minecraft.world.item.ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(s.id(), null);
			blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem
				.data(blueprint).orElseThrow().withPlacement(java.util.Optional.of(s.placement())));
			Builders.assign(s.owner(), builder[0], blueprint, true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still taking it down");
			helper.assertTrue(helper.getBlockState(HOUSE.offset(1, 1, 1)).is(Blocks.SHORT_GRASS), "(setup) the grass on the spot is gone");
			helper.assertTrue(!QaOldDrawings1121cGameTests.listed(s), "the hall still lists a house of which only grass is left after its take-down (said: "
				+ QaStageFourGameTests.keys(seen) + ")");
		});
	}
}
