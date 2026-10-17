package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.3 with the pack's furniture mods: the Starter Cottage planned with Handcrafted gets Handcrafted chairs,
 * table and bed and costs their items; with CobbleFurnies first it gets CobbleFurnies pieces (and Handcrafted's bed,
 * CobbleFurnies having none); switched off it's the vanilla build; a saved site keeps its choice.
 */
public class FurnishedBuildCompatTests implements FabricGameTest {
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

	private static boolean costs(BuildPlan plan, String item) {
		return plan.steps(BuildPlan.Stage.STRUCTURE).stream().anyMatch(s -> s.requirements().stream()
			.anyMatch(r -> BuiltInRegistries.ITEM.getKey(r.item()).toString().equals(item)))
			|| plan.steps(BuildPlan.Stage.DECORATION).stream().anyMatch(s -> s.requirements().stream()
			.anyMatch(r -> BuiltInRegistries.ITEM.getKey(r.item()).toString().equals(item)));
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void handcraftedFurnishesTheStarterCottage(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(helper.getLevel());
			Map<FurnitureSpot.Kind, String> pieces = pieces(site, plan);
			helper.assertTrue("handcrafted:spruce_chair".equals(pieces.get(FurnitureSpot.Kind.CHAIR)), "chair: " + pieces);
			helper.assertTrue("handcrafted:spruce_table".equals(pieces.get(FurnitureSpot.Kind.TABLE)), "table: " + pieces);
			helper.assertTrue("handcrafted:spruce_fancy_bed".equals(pieces.get(FurnitureSpot.Kind.BED)), "bed: " + pieces);
			helper.assertTrue(costs(plan, "handcrafted:spruce_chair") && costs(plan, "handcrafted:spruce_table") && costs(plan, "handcrafted:spruce_fancy_bed"),
				"the plan doesn't cost the Handcrafted pieces");
			helper.assertTrue(!costs(plan, "minecraft:red_bed"), "the vanilla bed is still costed");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnishingOrder"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnishingOrder")
	public void cobbleFurniesFirstGetsItsPieces(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of("cobblefurnies", "handcrafted");
			BuildSite site = site(helper);
			Map<FurnitureSpot.Kind, String> pieces = pieces(site, site.plan(helper.getLevel()));
			helper.assertTrue("cobblefurnies:spruce_chair".equals(pieces.get(FurnitureSpot.Kind.CHAIR)), "chair: " + pieces);
			helper.assertTrue("cobblefurnies:spruce_table".equals(pieces.get(FurnitureSpot.Kind.TABLE)), "table: " + pieces);
			helper.assertTrue("handcrafted:spruce_fancy_bed".equals(pieces.get(FurnitureSpot.Kind.BED)), "bed (CobbleFurnies has none): " + pieces);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnishingOrder"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnishingOrder")
	public void switchedOffItIsTheVanillaBuild(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
			BuildSite site = site(helper);
			Map<FurnitureSpot.Kind, String> pieces = pieces(site, site.plan(helper.getLevel()));
			helper.assertTrue(pieces.get(FurnitureSpot.Kind.CHAIR).equals("minecraft:spruce_stairs") && pieces.get(FurnitureSpot.Kind.BED).equals("minecraft:red_bed"),
				"furnished with the switch off: " + pieces);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnishingOrder"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnishingOrder")
	public void aSavedSiteKeepsItsHandcraftedPieces(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			site.plan(helper.getLevel());
			site.advance();
			net.minecraft.nbt.CompoundTag saved = site.save();
			Furnishings.MODS = List.of("cobblefurnies");
			BuildSite loaded = BuildSite.load(saved);
			Map<FurnitureSpot.Kind, String> pieces = pieces(loaded, loaded.plan(helper.getLevel()));
			helper.assertTrue("handcrafted:spruce_chair".equals(pieces.get(FurnitureSpot.Kind.CHAIR)), "after a reload: " + pieces);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
