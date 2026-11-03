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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane-b-1103-1839), ROADMAP 37.3: "The Blueprint Table's materials list and the builder's shopping list show the
 * pieces this server will build". With a furniture "mod" first in the order (the test pack's
 * {@code aliveworkplace_test_pieces}, its table a block of planks), the Table's "All materials" list for the Starter
 * Cottage (as drawn and in Dark Oak) must be the furnished build's materials, the ones the builder will ask for, not the
 * vanilla build's.
 */
public class QaFurnishedTable1103fBugTests implements FabricGameTest {
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";

	private static Map<String, Integer> asMap(List<TablePayloads.Material> materials) {
		Map<String, Integer> out = new TreeMap<>();
		materials.forEach(m -> out.merge(m.item().toString(), m.count(), Integer::sum));
		return out;
	}

	/** The Table lists the furnished cottage's materials (the pieces this server builds), as drawn and styled. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedTable1103f"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedTable1103f")
	public void theTablesMaterialsListThePiecesThisServerBuilds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
				Blueprint drawn = BlueprintLibrary.get(level, id).orElse(null);
				if (drawn == null) {
					problems.add(id + " isn't in the library");
					continue;
				}
				Furnishings.MODS = List.of();
				Map<String, Integer> vanilla = asMap(TableServer.materials(drawn));
				// A player looks at the build on the Table while no furniture mod is in the order: the vanilla list
				Map<String, Integer> before = asMap(TableServer.details(level, null, id).orElseThrow().materials());
				if (!before.equals(vanilla)) {
					problems.add(id + ": with no mod in the order the Table shows " + diff(before, vanilla));
				}
				Furnishings.MODS = List.of(PIECES);
				Map<String, Integer> furnished = asMap(TableServer.materials(FurnitureChoice.forNewBuild(id).apply(drawn, id)));
				helper.assertTrue(!furnished.equals(vanilla), id + ": the test pieces don't change the build's materials, the test proves nothing");
				Map<String, Integer> shown = asMap(TableServer.details(level, null, id).orElseThrow().materials());
				if (!shown.equals(furnished)) {
					problems.add(id + ": the Table shows " + diff(shown, furnished) + (shown.equals(vanilla) ? " (the vanilla build's list)" : ""));
				}
				// The switch turned off again: the vanilla list once more
				WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
				Map<String, Integer> off = asMap(TableServer.details(level, null, id).orElseThrow().materials());
				if (!off.equals(vanilla)) {
					problems.add(id + ": switched off, the Table shows " + diff(off, vanilla));
				}
				new WorkplaceConfig().apply();
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static String diff(Map<String, Integer> shown, Map<String, Integer> want) {
		List<String> out = new ArrayList<>();
		java.util.Set<String> keys = new java.util.TreeSet<>(shown.keySet());
		keys.addAll(want.keySet());
		for (String k : keys) {
			int a = shown.getOrDefault(k, 0);
			int b = want.getOrDefault(k, 0);
			if (a != b) {
				out.add(k + " " + a + " (the build uses " + b + ")");
			}
		}
		return String.join(", ", out);
	}
}
