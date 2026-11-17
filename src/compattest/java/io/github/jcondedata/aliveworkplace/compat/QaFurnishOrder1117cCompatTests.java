package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-17) for ROADMAP 37.3, the furnishing table, written from the spec with the pack's furniture mods: "each
 * spot becomes the piece of the first mod in {@code furnitureMods} whose block exists in the registry, else it keeps
 * its vanilla blocks", set through the config file itself ({@link WorkplaceConfig#parse}, the server owner's entry point);
 * the wiki's "an empty list means vanilla"; "the choice is saved on the site, so a restart doesn't change a build in
 * progress", even when the owner switches furnished builds off and empties the order before the restart; and "the
 * Blueprint Table's materials list ... show[s] the pieces this server will build": each piece costed once per spot.
 */
public class QaFurnishOrder1117cCompatTests implements FabricGameTest {
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");

	private static BuildSite site(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
	}

	/** What the plan builds at each kind's first spot, by block id. */
	private static Map<FurnitureSpot.Kind, String> pieces(BuildSite site, BuildPlan plan) {
		Map<BlockPos, BlockState> steps = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> steps.put(s.pos(), s.state()));
		}
		Map<FurnitureSpot.Kind, String> out = new HashMap<>();
		for (FurnitureSpot spot : BlueprintFurniture.of(COTTAGE)) {
			BlockState state = steps.get(site.placement().origin().offset(spot.pos()));
			out.putIfAbsent(spot.kind(), state == null ? "nothing" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		}
		return out;
	}

	/** Every item the plan costs, by id. */
	private static Map<String, Integer> cost(BuildPlan plan) {
		Map<String, Integer> out = new HashMap<>();
		plan.materials().forEach((item, n) -> out.merge(BuiltInRegistries.ITEM.getKey(item).toString(), n, Integer::sum));
		return out;
	}

	private static boolean anyModded(Map<String, Integer> cost) {
		return cost.keySet().stream().anyMatch(id -> id.startsWith("handcrafted:") || id.startsWith("cobblefurnies:")
			|| id.startsWith("beautify:") || id.startsWith("cozyhome:"));
	}

	/** Each planned step costing a furniture mod's item: where (from the origin), what, and the spot there if any. */
	private static List<String> modded(BuildSite site, BuildPlan plan) {
		List<String> out = new java.util.ArrayList<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				Map<String, Integer> one = new HashMap<>();
				s.requirements().forEach(r -> one.put(BuiltInRegistries.ITEM.getKey(r.item()).toString(), 1));
				if (anyModded(one)) {
					BlockPos rel = s.pos().subtract(site.placement().origin());
					String spot = BlueprintFurniture.of(COTTAGE).stream().filter(f -> f.pos().equals(rel)).map(f -> f.kind().name()).findFirst().orElse("no spot");
					out.add(stage + " " + rel.toShortString() + " " + s.state() + " (" + spot + ")");
				}
			}
		}
		return out;
	}

	/** The Blueprint Table's materials list for the cottage, by item id. */
	private static Map<String, Integer> tableList(GameTestHelper helper) {
		Map<String, Integer> out = new HashMap<>();
		for (TablePayloads.Material m : TableServer.details(helper.getLevel(), null, COTTAGE).orElseThrow().materials()) {
			out.merge(m.item().toString(), m.count(), Integer::sum);
		}
		return out;
	}

	private static long spots(FurnitureSpot.Kind kind) {
		return BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == kind).count();
	}

	/**
	 * The config file's order names a mod that isn't installed first, then CobbleFurnies, then Handcrafted: the unknown
	 * mod is passed over, CobbleFurnies' chair and table are chosen, and Handcrafted's bed (CobbleFurnies has none).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furnish_order_1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furnish_order_1117c")
	public void anUnknownModFirstInTheOrderIsPassedOver(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"furnitureMods\": [\"no_such_furniture_mod\", \"cobblefurnies\", \"handcrafted\"]}").apply();
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(helper.getLevel());
			Map<FurnitureSpot.Kind, String> pieces = pieces(site, plan);
			helper.assertTrue("cobblefurnies:spruce_chair".equals(pieces.get(FurnitureSpot.Kind.CHAIR)), "chair: " + pieces);
			helper.assertTrue("cobblefurnies:spruce_table".equals(pieces.get(FurnitureSpot.Kind.TABLE)), "table: " + pieces);
			helper.assertTrue("handcrafted:spruce_fancy_bed".equals(pieces.get(FurnitureSpot.Kind.BED)), "bed: " + pieces);
			Map<String, Integer> cost = cost(plan);
			helper.assertFalse(cost.containsKey("handcrafted:spruce_chair"), "costs Handcrafted's chair though CobbleFurnies' is built: " + cost);
			helper.assertTrue(cost.containsKey("cobblefurnies:spruce_chair"), "doesn't cost CobbleFurnies' chair: " + cost);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * The wiki: "an empty list means vanilla". With Handcrafted and CobbleFurnies installed and the order emptied in the
	 * config file, every furniture spot of the cottage is vanilla; with the modded library off as well, the cottage plans
	 * and costs no modded item at all, and the Blueprint Table lists the vanilla bed.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furnish_order_1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furnish_order_1117c")
	public void anEmptyOrderIsTheVanillaBuildThoughTheModsArePresent(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"furnitureMods\": []}").apply();
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(helper.getLevel());
			Map<FurnitureSpot.Kind, String> pieces = pieces(site, plan);
			helper.assertTrue("minecraft:red_bed".equals(pieces.get(FurnitureSpot.Kind.BED)), "bed with an empty order: " + pieces);
			helper.assertTrue("minecraft:spruce_stairs".equals(pieces.get(FurnitureSpot.Kind.CHAIR)), "chair with an empty order: " + pieces);
			// The modded library (37.12, its own switch) dresses the drawing itself with a Handcrafted pot; with it off
			// too, nothing modded is left anywhere: the empty order adds no piece of its own.
			WorkplaceConfig.parse("{\"furnitureMods\": [], \"moddedLibrary\": false}").apply();
			site = site(helper);
			plan = site.plan(helper.getLevel());
			Map<String, Integer> cost = cost(plan);
			helper.assertFalse(anyModded(cost), "an empty order costs modded pieces: " + modded(site, plan));
			Map<String, Integer> table = tableList(helper);
			helper.assertFalse(anyModded(table), "the Blueprint Table lists modded pieces with an empty order: " + table);
			helper.assertTrue(table.getOrDefault("minecraft:red_bed", 0) > 0, "the Blueprint Table doesn't list the vanilla bed: " + table);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * With the default order (Handcrafted first), the Blueprint Table's materials list names the pieces this server will
	 * build: one Handcrafted chair per chair spot and one fancy bed per bed spot (a two-block bed costed once, not per
	 * half), and no vanilla red bed. The builder's plan costs the same.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furnish_order_1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furnish_order_1117c")
	public void theTablesListCostsEachPieceOncePerSpot(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			long chairs = spots(FurnitureSpot.Kind.CHAIR);
			long beds = spots(FurnitureSpot.Kind.BED);
			long tables = spots(FurnitureSpot.Kind.TABLE);
			helper.assertTrue(chairs > 0 && beds > 0 && tables > 0, "the cottage has " + chairs + " chair, " + beds + " bed and " + tables + " table spots");
			Map<String, Integer> table = tableList(helper);
			helper.assertTrue(table.getOrDefault("handcrafted:spruce_chair", 0) == chairs,
				"the Blueprint Table lists " + table.get("handcrafted:spruce_chair") + " Handcrafted chairs for " + chairs + " chair spots: " + table);
			helper.assertTrue(table.getOrDefault("handcrafted:spruce_fancy_bed", 0) == beds,
				"the Blueprint Table lists " + table.get("handcrafted:spruce_fancy_bed") + " fancy beds for " + beds + " bed spots: " + table);
			helper.assertTrue(table.getOrDefault("handcrafted:spruce_table", 0) == tables,
				"the Blueprint Table lists " + table.get("handcrafted:spruce_table") + " Handcrafted tables for " + tables + " table spots: " + table);
			helper.assertFalse(table.containsKey("minecraft:red_bed"), "the Blueprint Table still lists the vanilla bed: " + table);
			Map<String, Integer> cost = cost(site(helper).plan(helper.getLevel()));
			for (String piece : List.of("handcrafted:spruce_chair", "handcrafted:spruce_fancy_bed", "handcrafted:spruce_table")) {
				helper.assertTrue(cost.getOrDefault(piece, 0).equals(table.getOrDefault(piece, 0)),
					"the builder's plan costs " + cost.get(piece) + " " + piece + ", the table lists " + table.get(piece));
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * A cottage begun with Handcrafted is saved mid-build; before the restart the owner switches furnished builds off
	 * and empties the order. The loaded site still builds and costs the Handcrafted pieces it began with (a restart
	 * doesn't change a build in progress), while a new cottage planned now is vanilla.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furnish_order_1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furnish_order_1117c")
	public void aSavedSiteKeepsItsPiecesThoughTheSwitchAndOrderChanged(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			site.plan(helper.getLevel());
			site.advance();
			CompoundTag saved = site.save();
			WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": []}").apply();
			BuildSite loaded = BuildSite.load(saved);
			BuildPlan plan = loaded.plan(helper.getLevel());
			Map<FurnitureSpot.Kind, String> pieces = pieces(loaded, plan);
			helper.assertTrue("handcrafted:spruce_chair".equals(pieces.get(FurnitureSpot.Kind.CHAIR))
					&& "handcrafted:spruce_fancy_bed".equals(pieces.get(FurnitureSpot.Kind.BED))
					&& "handcrafted:spruce_table".equals(pieces.get(FurnitureSpot.Kind.TABLE)),
				"the build in progress changed after the restart: " + pieces);
			Map<String, Integer> cost = cost(plan);
			helper.assertTrue(cost.containsKey("handcrafted:spruce_fancy_bed") && !cost.containsKey("minecraft:red_bed"),
				"the loaded site's shopping list changed: " + cost);
			// Saved again and loaded again: still the same choice.
			BuildSite twice = BuildSite.load(loaded.save());
			helper.assertTrue("handcrafted:spruce_chair".equals(pieces(twice, twice.plan(helper.getLevel())).get(FurnitureSpot.Kind.CHAIR)),
				"lost the choice on a second save and load");
			// A new site planned now follows the new config: vanilla.
			BuildSite fresh = site(helper);
			Map<FurnitureSpot.Kind, String> now = pieces(fresh, fresh.plan(helper.getLevel()));
			helper.assertTrue("minecraft:red_bed".equals(now.get(FurnitureSpot.Kind.BED)), "a new cottage with the switch off: " + now);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Handcrafted's fancy bed is two blocks (a foot and a head; the plan builds it as one step, the other half placed with
	 * it). In every turn and mirror of the cottage each bed spot plans one fancy bed whose other half has room (the plan
	 * puts nothing solid there), and the plan costs one bed item per spot: never a vanilla bed beside it, never a bed
	 * paid twice.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_furnish_order_1117c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_furnish_order_1117c")
	public void theFancyBedIsWholeInEveryTurnAndMirror(GameTestHelper helper) {
		ResourceLocation bed = ResourceLocation.parse("handcrafted:spruce_fancy_bed");
		long beds = spots(FurnitureSpot.Kind.BED);
		try {
			new WorkplaceConfig().apply();
			for (Rotation rotation : Rotation.values()) {
				for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK, Mirror.LEFT_RIGHT)) {
					BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
						helper.absolutePos(new BlockPos(1, 2, 1)), rotation, mirror);
					BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
					BuildPlan plan = site.plan(helper.getLevel());
					Map<BlockPos, BlockState> planned = new HashMap<>();
					List<BuildPlan.Step> halves = new java.util.ArrayList<>();
					for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
						for (BuildPlan.Step st : plan.steps(stage)) {
							planned.put(st.pos(), st.state());
							if (BuiltInRegistries.BLOCK.getKey(st.state().getBlock()).equals(bed)) {
								halves.add(st);
							}
						}
					}
					String where = rotation + "/" + mirror;
					helper.assertTrue(halves.size() == beds, where + ": " + halves.size() + " fancy beds planned for " + beds + " bed spots");
					for (BuildPlan.Step half : halves) {
						BlockState state = half.state();
						helper.assertTrue(state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
							&& state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART), where + ": " + state);
						net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
						boolean foot = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
							== net.minecraft.world.level.block.state.properties.BedPart.FOOT;
						BlockPos other = half.pos().relative(foot ? facing : facing.getOpposite());
						BlockState there = planned.get(other);
						helper.assertTrue(there == null || there.isAir() || there.getBlock() == state.getBlock(),
							where + ": the fancy bed at " + half.pos() + " (" + state + ") has its other half where the plan puts " + there);
					}
					Map<String, Integer> cost = cost(plan);
					helper.assertTrue(cost.getOrDefault(bed.toString(), 0) == beds, where + ": costs " + cost.get(bed.toString()) + " fancy beds for " + beds + " spots");
					helper.assertFalse(cost.containsKey("minecraft:red_bed"), where + ": also costs a vanilla bed: " + cost);
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
