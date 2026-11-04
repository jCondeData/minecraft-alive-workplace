package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-b-1104-0038, 2026-11-04), from the texts of B893 and B901 ("the Table lists exactly what the builder will
 * ask for", "the Table's preview draws what the builder will build", and a changed order or furnishedBuilds switch must
 * not leave a stale list or preview): the Table's 3D preview follows the furniture order back and forth while the
 * server runs, and the Table's materials list is the very list a builder handed that build asks for.
 */
public class QaTableFurnished1104GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	private static final String PIECES = "aliveworkplace_test_pieces";

	/**
	 * One server, one build looked at again and again on the Table while the owner changes the config: no furniture mod
	 * (the vanilla build), the test furniture mod first (the furnished build), furnishedBuilds switched off (vanilla
	 * again), then on again (furnished again). Each time the preview the Table sends is the one for that moment, as
	 * drawn and in Dark Oak, never the preview cached a step before.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaTableFurnished1104"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaTableFurnished1104")
	public void theTablesPreviewFollowsTheFurnitureOrderBackAndForth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
				Blueprint drawn = BlueprintLibrary.get(level, id).orElse(null);
				helper.assertTrue(drawn != null, id + " isn't in the library");
				Map<String, String> vanilla = QaBuildViews1103gGameTests.cells(TableServer.buildPreview(id, drawn));
				Furnishings.MODS = List.of(PIECES);
				Map<String, String> furnished = QaBuildViews1103gGameTests.cells(TableServer.buildPreview(id, FurnitureChoice.forNewBuild(id).apply(drawn, id)));
				helper.assertTrue(!furnished.equals(vanilla), id + ": the test pieces don't change the preview, the test proves nothing");
				String[] steps = {"no furniture mod", "the test mod first", "furnishedBuilds off", "furnishedBuilds on again"};
				for (int step = 0; step < steps.length; step++) {
					switch (step) {
						case 0 -> WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": []}").apply();
						case 1 -> WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
						case 2 -> WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
						default -> WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
					}
					if (step == 1 || step == 3) {
						// The config names the mod; the test pack's "mod" isn't a loaded mod, so it is put in the order by hand
						Furnishings.MODS = List.of(PIECES);
					}
					Map<String, String> want = step == 1 || step == 3 ? furnished : vanilla;
					TablePayloads.Preview sent = TableServer.preview(level.getServer(), id, false).orElse(null);
					if (sent == null) {
						problems.add(id + " with " + steps[step] + ": the Table sends no preview");
						continue;
					}
					Map<String, String> shown = QaBuildViews1103gGameTests.cells(sent);
					if (!shown.equals(want)) {
						problems.add(id + " with " + steps[step] + ": the Table's preview " + QaBuildViews1103gGameTests.diff(shown, want)
							+ (shown.equals(furnished) ? " (the furnished preview)" : shown.equals(vanilla) ? " (the vanilla preview)" : ""));
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * With the test furniture mod first, the Starter Cottage's "All materials" list on the Table is, item by item, the
	 * list of the build site a builder gets for that very blueprint (what he will ask for).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaTableFurnished1104Site"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaTableFurnished1104Site")
	public void theTablesListIsTheListTheBuilderAsksFor(GameTestHelper helper) {
		sameList(helper, COTTAGE);
	}

	/** The same in Dark Oak (a style picked at the Table, 26.11): its list is the Dark Oak site's, pieces included. */
	//$ gametest_ticks_batch HUGE '200' '"qaTableFurnished1104SiteDarkOak"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaTableFurnished1104SiteDarkOak")
	public void theTablesDarkOakListIsTheListTheBuilderAsksFor(GameTestHelper helper) {
		sameList(helper, BlueprintStyles.styled(COTTAGE, "dark_oak"));
	}

	/** One builder per test: buildBack sets the build rules and puts them back at the end, once. */
	private static void sameList(GameTestHelper helper, ResourceLocation id) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<String> diff = new ArrayList<>();
		Map<String, Integer> table = new TreeMap<>();
		Map<String, Integer> vanilla = Map.of();
		Map<String, Integer> site = new TreeMap<>();
		try {
			Furnishings.MODS = List.of(PIECES);
			for (TablePayloads.Material m : TableServer.details(level, null, id).orElseThrow().materials()) {
				table.merge(m.item().toString(), m.count(), Integer::sum);
			}
			vanilla = QaBuildViews1103gGameTests.asMap(TableServer.materials(BlueprintLibrary.get(level, id).orElseThrow()));
			QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
			for (Map.Entry<Item, Integer> e : b.site().plan(level).materials().entrySet()) {
				site.merge(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), Integer::sum);
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(!site.equals(vanilla), id + ": the builder's list is the vanilla build's, the test pieces weren't used");
		java.util.Set<String> keys = new java.util.TreeSet<>(table.keySet());
		keys.addAll(site.keySet());
		for (String k : keys) {
			if (!table.getOrDefault(k, 0).equals(site.getOrDefault(k, 0))) {
				diff.add(k + " " + table.getOrDefault(k, 0) + " (the builder asks " + site.getOrDefault(k, 0) + ")");
			}
		}
		helper.assertTrue(diff.isEmpty(), id + ": the Table lists " + String.join(", ", diff));
		helper.succeed();
	}
}
