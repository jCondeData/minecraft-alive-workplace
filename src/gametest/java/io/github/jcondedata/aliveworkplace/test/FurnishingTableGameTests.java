package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * ROADMAP 37.3 without any furniture mod: the vanilla build is unchanged; a test pack's furnishing table
 * ({@code aliveworkplace_test_pieces}, vanilla blocks standing in for a mod's) proves the choice, the footprint rule,
 * the switch, and that a site saved mid-build keeps its choice. The mods themselves: {@code FurnishedBuildCompatTests}.
 */
public class FurnishingTableGameTests implements FabricGameTest {
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String TEST_TABLE = "aliveworkplace_test_pieces";

	private static BuildSite site(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
	}

	private static Map<BlockPos, BlockState> stepStates(BuildPlan plan) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> out.put(s.pos(), s.state()));
		}
		return out;
	}

	private static FurnitureSpot spot(FurnitureSpot.Kind kind) {
		return BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
	}

	/** No furniture mod installed: the Starter Cottage's plan is the vanilla build, block for block. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutFurnitureModsTheBuildIsVanilla(GameTestHelper helper) {
		helper.assertTrue(Furnishings.ENABLED && Furnishings.MODS.equals(Furnishings.DEFAULT_MODS), "the defaults should be on in GameTests");
		BuildSite site = site(helper);
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(site.furniture().isVanilla(), "a choice was made without any furniture mod: " + site.furniture());
		Blueprint vanilla = BlueprintLibrary.get(helper.getLevel(), COTTAGE).orElseThrow();
		BuildPlan expected = BuildPlan.create(vanilla, site.placement(), helper.getLevel(), 0);
		helper.assertTrue(stepStates(plan).equals(stepStates(expected)), "the plan differs from the vanilla build");
		helper.succeed();
	}

	/** A table's piece takes each spot it fits, its other cells become air; one that doesn't fit stays vanilla. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void theFirstModWithAFittingPieceIsChosen(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of("handcrafted", TEST_TABLE);
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(helper.getLevel());
			Map<BlockPos, BlockState> steps = stepStates(plan);
			BlockPos origin = site.placement().origin();
			FurnitureSpot table = spot(FurnitureSpot.Kind.TABLE);
			FurnitureSpot chair = spot(FurnitureSpot.Kind.CHAIR);
			FurnitureSpot bed = spot(FurnitureSpot.Kind.BED);
			BlockState atTable = steps.get(origin.offset(table.pos()));
			helper.assertTrue(atTable != null && atTable.is(Blocks.SPRUCE_PLANKS), "the table is " + atTable);
			helper.assertTrue(!steps.containsKey(origin.offset(table.pos()).above()) || steps.get(origin.offset(table.pos()).above()).isAir(),
				"the table's plate is still built: " + steps.get(origin.offset(table.pos()).above()));
			BlockState atChair = steps.get(origin.offset(chair.pos()));
			helper.assertTrue(atChair != null && atChair.is(Blocks.SPRUCE_STAIRS) && atChair.getValue(StairBlock.HALF) == Half.TOP
				&& atChair.getValue(StairBlock.FACING) == chair.facing().getOpposite(), "the chair is " + atChair);
			// The test bed's footprint is three long: it doesn't fit the two-block bed, which stays vanilla.
			BlockState atBed = steps.get(origin.offset(bed.pos()));
			helper.assertTrue(atBed != null && atBed.is(Blocks.RED_BED), "the bed should stay vanilla, is " + atBed);
			helper.assertTrue(plan.steps(BuildPlan.Stage.STRUCTURE).stream().anyMatch(s -> s.pos().equals(origin.offset(table.pos()))
				&& s.requirements().stream().anyMatch(r -> r.item() == Blocks.SPRUCE_PLANKS.asItem())), "the table doesn't cost planks");
			helper.assertTrue(site.furniture().spots().values().stream().allMatch(p -> p.mod().equals(TEST_TABLE)), "chosen: " + site.furniture());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** {@code furnishedBuilds} off: vanilla, whatever the order says. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void switchedOffTheBuildIsVanilla(GameTestHelper helper) {
		try {
			WorkplaceConfig config = WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + TEST_TABLE + "\"]}");
			config.apply();
			helper.assertTrue(FurnitureChoice.forNewBuild(COTTAGE).isVanilla(), "furnished with the switch off");
			BuildSite site = site(helper);
			site.plan(helper.getLevel());
			helper.assertTrue(site.furniture().isVanilla(), "the site chose furniture with the switch off");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** A site saved mid-build keeps its choice after a reload, though the order changed; an old save builds vanilla. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void aSiteSavedMidBuildKeepsItsChoice(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of(TEST_TABLE);
			BuildSite site = site(helper);
			site.plan(helper.getLevel());
			site.advance();
			CompoundTag saved = site.save();
			Furnishings.MODS = Furnishings.DEFAULT_MODS;
			BuildSite loaded = BuildSite.load(saved);
			BuildPlan plan = loaded.plan(helper.getLevel());
			FurnitureSpot table = spot(FurnitureSpot.Kind.TABLE);
			BlockState atTable = stepStates(plan).get(loaded.placement().origin().offset(table.pos()));
			helper.assertTrue(atTable != null && atTable.is(Blocks.SPRUCE_PLANKS), "after the reload the table is " + atTable);
			// A site from before 37.3 (no furniture fields): vanilla, chosen by nobody.
			CompoundTag old = site(helper).save();
			old.remove("furniture_unchosen");
			Furnishings.MODS = List.of(TEST_TABLE);
			BuildSite oldSite = BuildSite.load(old);
			oldSite.plan(helper.getLevel());
			helper.assertTrue(oldSite.furniture().isVanilla(), "an old save got furniture: " + oldSite.furniture());
			// A not-yet-planned new site saved and loaded still chooses when planned.
			BuildSite fresh = BuildSite.load(site(helper).save());
			fresh.plan(helper.getLevel());
			helper.assertTrue(!fresh.furniture().isVanilla(), "a new site saved before its first plan lost its choice");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** A saved piece whose block no longer exists (its mod was removed) is dropped: that spot builds, and upkeep puts back, vanilla. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRemovedModsPieceTurnsBackToVanilla(GameTestHelper helper) {
		FurnitureSpot table = spot(FurnitureSpot.Kind.TABLE);
		ListTag list = new ListTag();
		CompoundTag tag = new CompoundTag();
		tag.putInt("spot", BlueprintFurniture.of(COTTAGE).indexOf(table));
		tag.putLong("pos", table.pos().asLong());
		tag.putString("kind", "table");
		tag.putString("mod", "gonemod");
		ListTag cells = new ListTag();
		CompoundTag cell = new CompoundTag();
		cell.putLong("at", BlockPos.ZERO.asLong());
		cell.putString("state", "gonemod:oak_table");
		cells.add(cell);
		tag.put("cells", cells);
		list.add(tag);
		FurnitureChoice choice = FurnitureChoice.load(list);
		helper.assertTrue(choice.isVanilla(), "a piece of a missing mod was kept: " + choice);
		Blueprint vanilla = BlueprintLibrary.get(helper.getLevel(), COTTAGE).orElseThrow();
		helper.assertTrue(choice.apply(vanilla, COTTAGE) == vanilla, "the vanilla blueprint was changed");
		helper.succeed();
	}
}
