package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnishingTable;
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

	/**
	 * A saved choice whose spot no longer matches the blueprint's spots file (a data pack took spots away, or moved or
	 * changed one) builds that spot vanilla, and a spot past the end of the file is passed over too, never a crash.
	 * B641: a piece finds its spot by place and kind when its index moved (a redrawn file inserted spots before it), so
	 * "gone" means no spot of the file has its place and kind: the past-the-end piece stands where no spot is; and a
	 * piece whose index is past the end but whose place and kind are a spot's is built on that spot.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPieceWhoseSpotIsGoneBuildsVanilla(GameTestHelper helper) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(COTTAGE);
		FurnitureSpot table = spot(FurnitureSpot.Kind.TABLE);
		Map<BlockPos, BlockState> plank = Map.of(BlockPos.ZERO, Blocks.SPRUCE_PLANKS.defaultBlockState());
		Blueprint vanilla = BlueprintLibrary.get(helper.getLevel(), COTTAGE).orElseThrow();
		Map<String, FurnitureChoice> gone = Map.of(
			"a spot past the end of the file", new FurnitureChoice(Map.of(spots.size(), new FurnitureChoice.Placed(table.pos().offset(100, 0, 0), table.kind(), TEST_TABLE, plank))),
			"a spot that moved", new FurnitureChoice(Map.of(spots.indexOf(table), new FurnitureChoice.Placed(table.pos().above(), table.kind(), TEST_TABLE, plank))),
			"a spot of another kind", new FurnitureChoice(Map.of(spots.indexOf(table), new FurnitureChoice.Placed(table.pos(), FurnitureSpot.Kind.BED, TEST_TABLE, plank))));
		gone.forEach((what, choice) -> {
			Blueprint built = choice.apply(vanilla, spots);
			helper.assertTrue(built.blocks().equals(vanilla.blocks()), what + " changed the build");
		});
		// The spot as the file has it does take the piece (the check above isn't vacuous)
		Blueprint furnished = new FurnitureChoice(Map.of(spots.indexOf(table), new FurnitureChoice.Placed(table.pos(), table.kind(), TEST_TABLE, plank)))
			.apply(vanilla, spots);
		helper.assertTrue(furnished.blocks().stream().anyMatch(e -> e.pos().equals(table.pos()) && e.state().is(Blocks.SPRUCE_PLANKS)),
			"the matching spot didn't get its piece");
		// B641: a stale index (past the end) with the spot's place and kind lands on that spot, and only there
		Blueprint moved = new FurnitureChoice(Map.of(spots.size(), new FurnitureChoice.Placed(table.pos(), table.kind(), TEST_TABLE, plank)))
			.apply(vanilla, spots);
		helper.assertTrue(moved.blocks().equals(furnished.blocks()), "a piece whose index moved didn't land on its spot as the indexed one does");
		helper.succeed();
	}

	/**
	 * B166: a bed spot only takes a piece a villager can claim and sleep in (a bed whose head is a HOME point of
	 * interest). A table whose "bed" is planks is passed over for the next mod's real bed, and alone it leaves the vanilla bed.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBedSpotOnlyTakesABedVillagersSleepIn(GameTestHelper helper) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(COTTAGE);
		List<Integer> beds = new java.util.ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			if (spots.get(i).kind() == FurnitureSpot.Kind.BED) {
				beds.add(i);
			}
		}
		helper.assertTrue(!beds.isEmpty(), "the cottage has no bed spot");
		FurnitureChoice planks = FurnitureChoice.choose(spots, List.of("aliveworkplace_test_plank_bed"), true);
		for (int i : beds) {
			helper.assertTrue(!planks.spots().containsKey(i), "a bed spot became planks: " + planks.spots().get(i));
		}
		FurnitureChoice next = FurnitureChoice.choose(spots, List.of("aliveworkplace_test_plank_bed", "aliveworkplace_test_beds"), true);
		for (int i : beds) {
			FurnitureChoice.Placed placed = next.spots().get(i);
			helper.assertTrue(placed != null && placed.mod().equals("aliveworkplace_test_beds"), "a bed spot didn't take the next mod's bed: " + placed);
			helper.assertTrue(FurnitureChoice.sleepable(placed.cells()), "the vanilla bed piece isn't sleepable");
		}
		// A saved choice whose bed isn't one (a mod's bed swapped for a block that isn't) loads without it: the vanilla bed is built
		BlockPos bedPos = spots.get(beds.get(0)).pos();
		FurnitureChoice planked = new FurnitureChoice(Map.of(beds.get(0), new FurnitureChoice.Placed(bedPos, FurnitureSpot.Kind.BED, "x",
			Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()))));
		helper.assertTrue(FurnitureChoice.load(planked.save()).isVanilla(), "a saved bed that isn't a bed was kept");
		helper.assertTrue(!FurnitureChoice.sleepable(Map.of(BlockPos.ZERO, Blocks.RED_BED.defaultBlockState())),
			"a bed's foot alone counted as sleepable");
		helper.succeed();
	}

	/**
	 * Every kind of mistake in a furnishing table is named, so the log line says what to fix: no pieces object (an empty
	 * file, pieces that aren't an object), an unknown kind, a facing that isn't front, back, left or right.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachMistakeInAFurnishingTableIsNamed(GameTestHelper helper) {
		String[][] cases = {
			{"{}", "no \"pieces\" object"},
			{"{\"pieces\": []}", "no \"pieces\" object"},
			{"{\"pieces\": {\"throne\": {\"block\": \"minecraft:oak_stairs\"}}}", "unknown kind throne"},
			{"{\"pieces\": {\"chair\": {\"block\": \"minecraft:oak_stairs\", \"facing\": \"up\"}}}", "chair: facing up is not front, back, left or right"},
		};
		for (String[] c : cases) {
			String message = null;
			try {
				FurnishingTable.read("aliveworkplace_test_mistakes", com.google.gson.JsonParser.parseString(c[0]));
			} catch (IllegalArgumentException ex) {
				message = ex.getMessage();
			} catch (RuntimeException ex) {
				message = "not named (" + ex + ")";
			}
			helper.assertTrue(message != null && message.contains(c[1]), "for " + c[0] + " the reader said " + message + ", wanted " + c[1]);
		}
		FurnishingTable good = FurnishingTable.read("aliveworkplace_test_mistakes",
			com.google.gson.JsonParser.parseString("{\"pieces\": {\"chair\": {\"block\": \"minecraft:{wood}_stairs\", \"facing\": \"back\"}}}"));
		helper.assertTrue(good.pieces().containsKey(FurnitureSpot.Kind.CHAIR) && "back".equals(good.pieces().get(FurnitureSpot.Kind.CHAIR).facing()),
			"a good table read wrong: " + good);
		helper.succeed();
	}
}
