package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA bug tests (lane-b-1103-1839): places a player sees a build's blocks or materials that ignore the furniture this
 * server builds (the same class as B893, the Table's materials list), and a styled workplace that loses its desks.
 * ROADMAP 37.3: "the preview and the Blueprint Table's materials list and the builder's shopping list show the pieces
 * this server will build". The test pack's {@code aliveworkplace_test_pieces} stands in for a furniture mod (its chair
 * an upturned stair of the spot's wood, its table a block of planks).
 */
public class QaBuildViews1103gBugTests implements FabricGameTest {
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";

	/**
	 * The Blueprint Table's 3D preview (26.9) of the Starter Cottage, as drawn and in Dark Oak, with a furniture mod first
	 * in the order, draws the furnished build (the test mod's chair, an upturned stair, on the chair spot), as the held
	 * Blueprint's preview (PreviewNetworking) and the builder do: not the vanilla build.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBuildViews1103g"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBuildViews1103g")
	public void theTablesPreviewDrawsThePiecesThisServerBuilds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
				Blueprint drawn = BlueprintLibrary.get(level, id).orElse(null);
				helper.assertTrue(drawn != null, id + " isn't in the library");
				Furnishings.MODS = List.of(PIECES);
				Blueprint furnished = FurnitureChoice.forNewBuild(id).apply(drawn, id);
				Map<String, String> want = QaBuildViews1103gGameTests.cells(TableServer.buildPreview(id, furnished));
				helper.assertTrue(!want.equals(QaBuildViews1103gGameTests.cells(TableServer.buildPreview(id, drawn))),
					id + ": the test pieces don't change the preview, the test proves nothing");
				TablePayloads.Preview sent = TableServer.preview(level.getServer(), id, false).orElse(null);
				if (sent == null) {
					problems.add(id + ": the Table sends no preview");
					continue;
				}
				Map<String, String> shown = QaBuildViews1103gGameTests.cells(sent);
				if (!shown.equals(want)) {
					problems.add(id + ": the Table's preview " + QaBuildViews1103gGameTests.diff(shown, want));
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The Steward's desk in the Village Hall lists, under "Needs most:", how many of each material a proposed build
	 * takes. With a furniture mod first in the order, each count is what the builder of that build will ask for (the
	 * furnished build's materials), for every furnished build whose top five materials the pieces change.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaBuildViews1103g"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaBuildViews1103g")
	public void theStewardDesksNeedsCountTheFurnishedPieces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		try {
			Furnishings.MODS = List.of(PIECES);
			BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0));
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(40, 2, 40)),
				Rotation.NONE, Mirror.NONE);
			for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
				if (BlueprintFurniture.of(id).stream().noneMatch(s -> s.kind() == FurnitureSpot.Kind.TABLE || s.kind() == FurnitureSpot.Kind.CHAIR)) {
					continue;
				}
				Blueprint drawn = BlueprintLibrary.get(level, id).orElse(null);
				if (drawn == null) {
					continue;
				}
				Map<String, Integer> vanilla = QaBuildViews1103gGameTests.asMap(TableServer.materials(drawn));
				Map<String, Integer> furnished = QaBuildViews1103gGameTests.asMap(TableServer.materials(FurnitureChoice.forNewBuild(id).apply(drawn, id)));
				StewardDesk.Proposal proposal = new StewardDesk.Proposal(1, AliveWorkplace.id("qa_rule"), false, id, placement, "", "", "",
					List.of(), 0L, 0, false, List.of(), "");
				List<StewardDesk.Need> needs = StewardDesk.needs(level, hall, proposal);
				boolean changed = false;
				for (StewardDesk.Need need : needs) {
					String item = BuiltInRegistries.ITEM.getKey(need.item()).toString();
					if (!java.util.Objects.equals(vanilla.get(item), furnished.get(item))) {
						changed = true;
					}
				}
				if (!changed) {
					continue; // the pieces don't change what the desk lists for this build
				}
				checked++;
				for (StewardDesk.Need need : needs) {
					String item = BuiltInRegistries.ITEM.getKey(need.item()).toString();
					int want = furnished.getOrDefault(item, 0);
					if (need.count() != want) {
						problems.add(id + ": the desk says " + need.count() + " " + item + " (the builder will ask for " + want + ")");
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked > 0, "no build's listed materials change with the test pieces, the test proves nothing");
		helper.assertTrue(problems.isEmpty(), problems.size() + " wrong counts: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/**
	 * The owner's answer (2026-10-18, ROADMAP 37.5): the Healing Center's and the Schoolhouse's desks are CobbleFurnies'
	 * Poké Ball desks "at every level". A Healing Center or Schoolhouse built in a style (a city zone's style, 35.x, or
	 * one picked at the Table, 26.11) is still that build, so it keeps the Poké Ball desks: the style only swaps its wood
	 * and stone.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBuildViews1103g"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBuildViews1103g")
	public void aStyledHealingCenterOrSchoolhouseKeepsItsPokeBallDesks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (ResourceLocation base : BlueprintLibrary.list(level.getServer(), false)) {
			if (!FurnitureChoice.hasPokeBallDesks(base)) {
				continue;
			}
			for (BlueprintStyles.Style style : BlueprintStyles.all()) {
				ResourceLocation id = BlueprintStyles.styled(base, style.name());
				if (!style.isFor(base) || BlueprintLibrary.get(level, id).isEmpty()) {
					continue;
				}
				if (BlueprintFurniture.of(id).stream().noneMatch(s -> s.kind() == FurnitureSpot.Kind.DESK)) {
					continue;
				}
				checked++;
				if (!FurnitureChoice.hasPokeBallDesks(id)) {
					problems.add(id.toString());
				}
			}
		}
		helper.assertTrue(checked > 0, "no styled Healing Center or Schoolhouse with desks, the test proves nothing");
		helper.assertTrue(problems.isEmpty(), problems.size() + " of " + checked + " styled builds lose their Poké Ball desks: "
			+ String.join(", ", problems.subList(0, Math.min(5, problems.size()))));
		helper.succeed();
	}
}
