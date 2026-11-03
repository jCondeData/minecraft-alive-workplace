package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1103-1839) for what a player sees of a build before it starts (ROADMAP 26.9, 37.3), written from the spec:
 * <ul>
 * <li>with furnished builds switched off, or no furniture mod in the order, the Table's preview and materials and the
 * Steward desk's "Needs most" are the vanilla build's (a guard for the B893 fix and its siblings: the pieces only when
 * the server builds them);</li>
 * <li>the held Blueprint's preview (PreviewNetworking) draws the same furnished blocks a builder handed that Blueprint
 * plans to place, for the Starter Cottage as drawn and in Dark Oak.</li>
 * </ul>
 */
public class QaBuildViews1103gGameTests implements FabricGameTest {
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";

	/**
	 * Switched off ({@code furnishedBuilds: false}, the test mod still listed) and with an order naming only a mod that
	 * isn't installed: the Table's preview and materials list and the Steward desk's needs for the Starter Cottage are
	 * exactly the vanilla build's.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBuildViews1103gOk"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBuildViews1103gOk")
	public void withNoPiecesBuiltEveryViewShowsTheVanillaBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(40, 2, 40)),
			Rotation.NONE, Mirror.NONE);
		try {
			for (String config : List.of("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\"]}",
				"{\"furnishedBuilds\": true, \"furnitureMods\": [\"no_such_furniture_mod\"]}")) {
				WorkplaceConfig.parse(config).apply();
				for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
					Blueprint drawn = BlueprintLibrary.get(level, id).orElseThrow();
					helper.assertTrue(FurnitureChoice.forNewBuild(id).isVanilla(), id + ": " + config + " still furnishes a new build");
					Map<String, String> vanillaPreview = cells(TableServer.buildPreview(id, drawn));
					Map<String, String> shown = cells(TableServer.preview(level.getServer(), id, false).orElseThrow());
					if (!shown.equals(vanillaPreview)) {
						problems.add(id + " " + config + ": the Table's preview " + diff(shown, vanillaPreview));
					}
					Map<String, Integer> vanilla = asMap(TableServer.materials(drawn));
					Map<String, Integer> listed = asMap(TableServer.details(level, null, id).orElseThrow().materials());
					if (!listed.equals(vanilla)) {
						problems.add(id + " " + config + ": the Table lists " + listed + " (vanilla " + vanilla + ")");
					}
					StewardDesk.Proposal proposal = new StewardDesk.Proposal(1, AliveWorkplace.id("qa_rule"), false, id, placement, "", "", "",
						List.of(), 0L, 0, false, List.of(), "");
					List<StewardDesk.Need> needs = StewardDesk.needs(level, helper.absolutePos(new BlockPos(0, 2, 0)), proposal);
					helper.assertTrue(!needs.isEmpty() && needs.size() <= StewardDesk.MATERIALS, id + ": the desk lists " + needs.size() + " materials");
					int last = Integer.MAX_VALUE;
					for (StewardDesk.Need need : needs) {
						String item = BuiltInRegistries.ITEM.getKey(need.item()).toString();
						if (need.count() != vanilla.getOrDefault(item, 0)) {
							problems.add(id + " " + config + ": the desk says " + need.count() + " " + item + " (vanilla " + vanilla.get(item) + ")");
						}
						if (need.count() > last) {
							problems.add(id + ": the desk's needs aren't biggest first");
						}
						last = need.count();
						if (need.inStore() != 0) {
							problems.add(id + ": with no builder the desk says " + need.inStore() + " " + item + " in store");
						}
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
	 * With the test mod first in the order, the held Blueprint's preview (what PreviewNetworking sends for its id) draws
	 * the same blocks as the build a builder's new site plans for that Blueprint (its saved furniture choice), cell for
	 * cell, as drawn and in Dark Oak; and it isn't the vanilla build.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBuildViews1103gOk"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBuildViews1103gOk")
	public void theHeldBlueprintsPreviewDrawsWhatTheSiteBuilds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			Furnishings.MODS = List.of(PIECES);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			for (ResourceLocation id : List.of(COTTAGE, BlueprintStyles.styled(COTTAGE, "dark_oak"))) {
				Blueprint drawn = BlueprintLibrary.get(level, id).orElseThrow();
				BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", id, placement);
				helper.assertTrue(site.plan(level) != null, id + ": no plan");
				helper.assertTrue(!site.furniture().isVanilla(), id + ": the site took no pieces, the test proves nothing");
				Map<String, String> built = blocks(PreviewNetworking.build(site.furniture().apply(drawn, id)));
				// What the server sends for a held Blueprint's preview request (PreviewNetworking.send)
				Map<String, String> shown = blocks(PreviewNetworking.build(FurnitureChoice.forNewBuild(id).apply(BlueprintLibrary.get(level.getServer(), id).orElseThrow(), id)));
				if (!shown.equals(built)) {
					problems.add(id + ": the preview " + diff(shown, built));
				}
				if (shown.equals(blocks(PreviewNetworking.build(drawn)))) {
					problems.add(id + ": the preview is the vanilla build");
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Each preview cell (x y z) with the block drawn there. */
	static Map<String, String> cells(TablePayloads.Preview preview) {
		Map<String, String> out = new TreeMap<>();
		int[] b = preview.blocks();
		for (int i = 0; i + 3 < b.length; i += 4) {
			out.put(b[i] + " " + b[i + 1] + " " + b[i + 2], Block.stateById(preview.palette().get(b[i + 3])).toString());
		}
		return out;
	}

	static String diff(Map<String, String> shown, Map<String, String> want) {
		List<String> out = new ArrayList<>();
		java.util.Set<String> keys = new java.util.TreeSet<>(shown.keySet());
		keys.addAll(want.keySet());
		for (String k : keys) {
			String a = shown.get(k);
			String b = want.get(k);
			if (!java.util.Objects.equals(a, b)) {
				out.add("at " + k + " shows " + a + " (built: " + b + ")");
			}
			if (out.size() >= 4) {
				out.add("...");
				break;
			}
		}
		return String.join(", ", out);
	}

	static Map<String, Integer> asMap(List<TablePayloads.Material> materials) {
		Map<String, Integer> out = new TreeMap<>();
		materials.forEach(m -> out.merge(m.item().toString(), m.count(), Integer::sum));
		return out;
	}

	private static Map<String, String> blocks(PreviewNetworking.Data data) {
		Map<String, String> out = new TreeMap<>();
		int[] b = data.blocks();
		for (int i = 0; i + 3 < b.length; i += 4) {
			out.put(b[i] + " " + b[i + 1] + " " + b[i + 2], Block.stateById(data.palette().get(b[i + 3])).toString());
		}
		return out;
	}
}
