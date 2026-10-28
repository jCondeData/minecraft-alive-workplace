package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Resting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * B300: no furnishing floats. Every furnished blueprint, vanilla and with a furniture table whose pieces are lower than
 * the vanilla ones (the test pack's one-block table for a fence-and-carpet one), has nothing small standing on air; a
 * furnished home put up block by block has nothing resting on nothing; and the builder's last look over its decoration
 * stage sets a pot whose shelf is gone down on the next block, and drops what can't stay.
 */
public class FloatingFurnishingsGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation HOME = AliveWorkplace.id("stone_house_4");

	/** The positions in {@code blueprint} where a small thing ({@link Resting#standsOnFloor}) stands on air. */
	private static List<String> floating(Blueprint blueprint) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		int minY = Integer.MAX_VALUE;
		for (Blueprint.Entry e : blueprint.blocks()) {
			at.put(e.pos(), e.state());
			minY = Math.min(minY, e.pos().getY());
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
			BlockPos pos = e.getKey();
			BlockState state = e.getValue();
			if (!Resting.standsOnFloor(state) || pos.getY() == minY) {
				continue;
			}
			BlockState above = at.get(pos.above());
			if (above != null && above.getBlock() instanceof ChainBlock) {
				continue; // a pot hanging from a chain
			}
			BlockState below = at.get(pos.below());
			if (below == null || below.isAir()) {
				out.add(blueprint.id().getPath() + ": " + state.getBlock().getDescriptionId() + " at " + pos.toShortString());
			}
		}
		return out;
	}

	/** Every furnished blueprint: nothing small stands on air, vanilla or with lower pieces swapped in. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noFurnishedBlueprintHasAFloatingPiece(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<ResourceLocation, List<FurnitureSpot>> e : BlueprintFurniture.all().entrySet()) {
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), e.getKey()).orElse(null);
			if (base == null) {
				continue;
			}
			checked++;
			problems.addAll(floating(base));
			FurnitureChoice lower = FurnitureChoice.choose(e.getValue(), List.of(FurnishingTableGameTests.TEST_TABLE), true);
			problems.addAll(floating(lower.apply(base, e.getValue())).stream().map(p -> "furnished " + p).toList());
		}
		helper.assertTrue(checked > 50, "only " + checked + " furnished blueprints were found");
		helper.assertTrue(problems.isEmpty(), problems.size() + " floating piece(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	/**
	 * The cause the owner met: a fence-and-plate table with a pot on it, and a furniture mod whose table is one block
	 * (the test pack's planks): the plate's cell goes, and the pot comes down onto the new table, not left in the air.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPotOnALowerTableSitsOnIt(GameTestHelper helper) {
		BlockPos leg = new BlockPos(1, 1, 1);
		BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
		BlockState plate = Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState();
		List<Blueprint.Entry> entries = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				entries.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), null));
			}
		}
		entries.add(new Blueprint.Entry(leg, fence, null));
		entries.add(new Blueprint.Entry(leg.above(), plate, null));
		entries.add(new Blueprint.Entry(leg.above(2), Blocks.POTTED_RED_TULIP.defaultBlockState(), null));
		entries.add(new Blueprint.Entry(leg.above(3), Blocks.AIR.defaultBlockState(), null));
		Blueprint base = new Blueprint(AliveWorkplace.id("b300_table"), new Vec3i(3, 5, 3), entries, List.of());
		List<FurnitureSpot> spots = List.of(new FurnitureSpot(FurnitureSpot.Kind.TABLE, leg, Direction.NORTH, "spruce", null,
			List.of(new FurnitureSpot.Cell(BlockPos.ZERO, fence), new FurnitureSpot.Cell(new BlockPos(0, 1, 0), plate))));
		helper.assertTrue(floating(base).isEmpty(), "the drawing itself floats: " + floating(base));
		FurnitureChoice lower = FurnitureChoice.choose(spots, List.of(FurnishingTableGameTests.TEST_TABLE), true);
		helper.assertTrue(!lower.isVanilla(), "the test table wasn't chosen");
		Blueprint built = lower.apply(base, spots);
		Map<BlockPos, BlockState> at = new HashMap<>();
		built.blocks().forEach(b -> at.put(b.pos(), b.state()));
		helper.assertTrue(at.get(leg).is(Blocks.SPRUCE_PLANKS), "the table is " + at.get(leg));
		helper.assertTrue(at.get(leg.above()).is(Blocks.POTTED_RED_TULIP), "the pot isn't on the table: " + at.get(leg.above()));
		helper.assertTrue(at.get(leg.above(2)).isAir(), "the pot is still in the air: " + at.get(leg.above(2)));
		helper.assertTrue(floating(built).isEmpty(), "floating: " + floating(built));
		helper.succeed();
	}

	/**
	 * A furnished home put up block by block (the last Stone House, with the test pack's one-block tables) has nothing resting on nothing,
	 * and the builder's last look over it finds nothing to set down.
	 */
	//$ gametest_batch HUGE_AREA '"floatingHome"'
	@GameTest(template = HUGE_AREA, batch = "floatingHome")
	public void aBuiltFurnishedHomeHasNothingFloating(GameTestHelper helper) {
		Leftovers.clear(helper);
		Blueprint base = BlueprintLibrary.get(helper.getLevel(), HOME).orElseThrow();
		List<FurnitureSpot> spots = BlueprintFurniture.of(HOME);
		Blueprint built = FurnitureChoice.choose(spots, List.of(FurnishingTableGameTests.TEST_TABLE), true).apply(base, spots);
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(built, placement, helper.getLevel(), 0);
		List<BuildPlan.Step> steps = new ArrayList<>(plan.steps(BuildPlan.Stage.FOUNDATION));
		steps.addAll(plan.steps(BuildPlan.Stage.STRUCTURE));
		steps.addAll(plan.steps(BuildPlan.Stage.DECORATION));
		for (BuildPlan.Step s : steps) {
			helper.getLevel().setBlock(s.pos(), s.state(), Block.UPDATE_CLIENTS);
		}
		List<String> problems = new ArrayList<>();
		for (BuildPlan.Step s : steps) {
			BlockState world = helper.getLevel().getBlockState(s.pos());
			if (Resting.unsupported(helper.getLevel(), s.pos(), world)) {
				problems.add(world.getBlock().getDescriptionId() + " at " + helper.relativePos(s.pos()).toShortString());
			}
		}
		helper.assertTrue(problems.isEmpty(), "resting on nothing: " + problems);
		int fixed = Resting.settle(helper.getLevel(), plan);
		helper.assertTrue(fixed == 0, "the builder's last look moved " + fixed + " piece(s) of a sound build");
		helper.succeed();
	}

	/**
	 * The safety net: a pot whose shelf never went in comes down onto the block under it; a candle over a gap comes
	 * down to the floor; a wall torch with no wall is dropped as an item; a pot hanging from a chain stays.
	 */
	//$ gametest_batch '"aliveworkplace_test:build_area"' '"floatingNet"'
	@GameTest(template = "aliveworkplace_test:build_area", batch = "floatingNet")
	public void aPieceLeftOnNothingIsSetDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Blueprint.Entry> entries = new ArrayList<>();
		entries.add(new Blueprint.Entry(new BlockPos(0, 0, 0), Blocks.SPRUCE_PLANKS.defaultBlockState(), null));
		entries.add(new Blueprint.Entry(new BlockPos(0, 1, 0), Blocks.SPRUCE_SLAB.defaultBlockState(), null)); // the shelf, never built
		entries.add(new Blueprint.Entry(new BlockPos(0, 2, 0), Blocks.POTTED_POPPY.defaultBlockState(), null));
		entries.add(new Blueprint.Entry(new BlockPos(2, 2, 0), Blocks.CANDLE.defaultBlockState(), null));
		entries.add(new Blueprint.Entry(new BlockPos(4, 2, 0), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), null));
		entries.add(new Blueprint.Entry(new BlockPos(5, 3, 0), Blocks.CHAIN.defaultBlockState(), null));
		entries.add(new Blueprint.Entry(new BlockPos(5, 2, 0), Blocks.POTTED_DANDELION.defaultBlockState(), null));
		Blueprint blueprint = new Blueprint(AliveWorkplace.id("b300_test"), new Vec3i(6, 4, 1), entries, List.of());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(1, 2, 2)), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement, helper.getLevel(), 0);
		List<BuildPlan.Step> steps = new ArrayList<>(plan.steps(BuildPlan.Stage.FOUNDATION));
		steps.addAll(plan.steps(BuildPlan.Stage.STRUCTURE));
		steps.addAll(plan.steps(BuildPlan.Stage.DECORATION));
		Map<net.minecraft.world.level.block.Block, BlockPos> at = new HashMap<>();
		for (BuildPlan.Step s : steps) {
			at.put(s.state().getBlock(), s.pos());
			if (!s.state().is(Blocks.SPRUCE_SLAB)) { // the shelf is missing
				helper.getLevel().setBlock(s.pos(), s.state(), Block.UPDATE_CLIENTS);
			}
		}
		BlockPos pot = at.get(Blocks.POTTED_POPPY), candle = at.get(Blocks.CANDLE), torch = at.get(Blocks.WALL_TORCH), hanging = at.get(Blocks.POTTED_DANDELION);
		helper.getLevel().setBlock(at.get(Blocks.CHAIN).above(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS); // the chain's beam
		helper.assertTrue(helper.getLevel().getBlockState(pot).is(Blocks.POTTED_POPPY), "the pot wasn't placed");
		String before = java.util.stream.IntStream.range(-3, 2).mapToObj(i -> i + "=" + helper.getLevel().getBlockState(pot.above(i))).toList().toString();
		int fixed = Resting.settle(helper.getLevel(), plan);
		String after = java.util.stream.IntStream.range(-3, 2).mapToObj(i -> i + "=" + helper.getLevel().getBlockState(pot.above(i))).toList().toString();
		helper.assertTrue(fixed == 3, "set down or dropped " + fixed + ", not 3; the pot's column before " + before + ", after " + after);
		helper.assertTrue(helper.getLevel().getBlockState(pot.below()).is(Blocks.POTTED_POPPY), "no Blocks.POTTED_POPPY at " + pot.below().toShortString());
		helper.assertTrue(!helper.getLevel().getBlockState(pot).is(Blocks.POTTED_POPPY), "still Blocks.POTTED_POPPY at " + pot.toShortString());
		helper.assertTrue(!helper.getLevel().getBlockState(candle).is(Blocks.CANDLE), "still Blocks.CANDLE at " + candle.toShortString());
		BlockPos floor = candle.below();
		while (helper.getLevel().getBlockState(floor).isAir() && floor.getY() > helper.absolutePos(BlockPos.ZERO).getY()) {
			floor = floor.below();
		}
		helper.assertTrue(helper.getLevel().getBlockState(floor).is(Blocks.CANDLE), "no Blocks.CANDLE at " + floor.toShortString());
		helper.assertTrue(!helper.getLevel().getBlockState(torch).is(Blocks.WALL_TORCH), "still Blocks.WALL_TORCH at " + torch.toShortString());
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(torch).inflate(2),
			i -> i.getItem().is(Items.TORCH)).isEmpty(), "the wall torch wasn't dropped as an item");
		helper.assertTrue(helper.getLevel().getBlockState(hanging).is(Blocks.POTTED_DANDELION), "no Blocks.POTTED_DANDELION at " + hanging.toShortString());
		helper.succeed();
	}

	/** The test pack's one-block tables and upside-down stair chairs, and its two-block beds. */
	private static final List<String> LOWER = List.of(FurnishingTableGameTests.TEST_TABLE, "aliveworkplace_test_beds");

	/**
	 * B641 (the owner, 2026-10-27: one Terrace kitchen furnished, the other with spruce stand-ins and crockery in the
	 * air): every home of the Terrace and Terrace II, put up as drawn, turned and mirrored, with pieces lower than the
	 * vanilla ones, has its table and chair swapped and nothing resting on nothing, in both halves (and the third home).
	 */
	//$ gametest_batch HUGE_AREA '"floatingTerrace"'
	@GameTest(template = HUGE_AREA, batch = "floatingTerrace")
	public void everyHalfOfTheTerracesHasItsPiecesAndNothingFloating(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<String> problems = new ArrayList<>();
		int halves = 0;
		for (String name : List.of("terrace", "terrace_2")) {
			ResourceLocation id = AliveWorkplace.id(name);
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
			List<FurnitureSpot> spots = BlueprintFurniture.of(id);
			Blueprint built = FurnitureChoice.choose(spots, LOWER, true).apply(base, spots);
			for (Rotation rotation : List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180)) {
				for (Mirror mirror : List.of(Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK)) {
					BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
						helper.absolutePos(new BlockPos(6, 1, 6)), rotation, mirror);
					BuildPlan plan = BuildPlan.create(built, placement, helper.getLevel(), 0);
					List<BuildPlan.Step> steps = new ArrayList<>(plan.steps(BuildPlan.Stage.FOUNDATION));
					steps.addAll(plan.steps(BuildPlan.Stage.STRUCTURE));
					steps.addAll(plan.steps(BuildPlan.Stage.DECORATION));
					for (BuildPlan.Step st : steps) {
						helper.getLevel().setBlock(st.pos(), st.state(), Block.UPDATE_CLIENTS);
					}
					String where = name + " " + rotation + "/" + mirror + ": ";
					for (FurnitureSpot spot : spots) {
						BlockPos at = placement.origin().offset(StructureTemplate.transform(spot.pos(), mirror, rotation, BlockPos.ZERO));
						BlockState world = helper.getLevel().getBlockState(at);
						if (spot.kind() == FurnitureSpot.Kind.TABLE) {
							halves++;
							if (!world.is(Blocks.SPRUCE_PLANKS)) {
								problems.add(where + "the table at " + spot.pos().toShortString() + " is " + world);
							}
						} else if (spot.kind() == FurnitureSpot.Kind.CHAIR && !(world.getBlock() instanceof net.minecraft.world.level.block.StairBlock
							&& world.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP)) {
							problems.add(where + "the chair at " + spot.pos().toShortString() + " is " + world);
						}
					}
					for (BuildPlan.Step st : steps) {
						BlockState world = helper.getLevel().getBlockState(st.pos());
						if (Resting.unsupported(helper.getLevel(), st.pos(), world)) {
							problems.add(where + world.getBlock().getDescriptionId() + " rests on nothing at " + helper.relativePos(st.pos()).toShortString());
						}
					}
					int fixed = Resting.settle(helper.getLevel(), plan);
					if (fixed != 0) {
						problems.add(where + "the builder's last look moved " + fixed + " piece(s)");
					}
					for (BuildPlan.Step st : steps) {
						helper.getLevel().setBlock(st.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		helper.assertTrue(halves == 9 * 5, "kitchens checked: " + halves);
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	/**
	 * B641's cause: a Terrace furnished by the spots file before B281 (no east kitchen table and chair, so its pieces are
	 * saved under the indices of that file). Against today's file its four beds keep their pieces (their indices moved by
	 * two); it is known to be stale, and brought to today's spots both kitchens are furnished, as a refresh builds it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTerraceFurnishedBeforeB281KeepsItsBedsAndIsBroughtToBothKitchens(GameTestHelper helper) {
		ResourceLocation id = AliveWorkplace.id("terrace");
		Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		List<FurnitureSpot> today = BlueprintFurniture.of(id);
		List<FurnitureSpot> before = today.stream().filter(s -> !(s.pos().getY() == 1 && s.pos().getX() >= 7)).toList();
		// (its beds the test pack's, lime: told apart from the drawing's red and blue ones)
		Map<Integer, FurnitureChoice.Placed> saved = new HashMap<>();
		FurnitureChoice.choose(before, LOWER, true).spots().forEach((i, p) -> {
			Map<BlockPos, BlockState> cells = new HashMap<>();
			p.cells().forEach((c, state) -> cells.put(c, state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
				? Blocks.LIME_BED.withPropertiesOf(state) : state));
			saved.put(i, new FurnitureChoice.Placed(p.pos(), p.kind(), p.mod(), cells));
		});
		FurnitureChoice old = FurnitureChoice.load(new FurnitureChoice(saved).save());
		helper.assertTrue(old.staleFor(today), "the pre-B281 choice isn't seen as stale");
		helper.assertTrue(!FurnitureChoice.choose(today, LOWER, true).staleFor(today), "a choice of today's file is seen as stale");
		Map<BlockPos, BlockState> at = new HashMap<>();
		old.apply(base, today).blocks().forEach(b -> at.put(b.pos(), b.state()));
		for (FurnitureSpot spot : today) {
			if (spot.kind() == FurnitureSpot.Kind.BED) {
				helper.assertTrue(at.get(spot.pos()).is(Blocks.LIME_BED), "the bed at " + spot.pos().toShortString() + " lost its piece: " + at.get(spot.pos()));
			}
		}
		helper.assertTrue(at.get(new BlockPos(4, 1, 4)).is(Blocks.SPRUCE_PLANKS), "the west table lost its piece: " + at.get(new BlockPos(4, 1, 4)));
		helper.assertTrue(at.get(new BlockPos(7, 1, 4)).is(Blocks.SPRUCE_FENCE), "the east table wasn't furnished then: " + at.get(new BlockPos(7, 1, 4)));
		Map<BlockPos, BlockState> brought = new HashMap<>();
		old.refreshedFor(today, FurnitureChoice.choose(today, LOWER, true)).apply(base, today).blocks().forEach(b -> brought.put(b.pos(), b.state()));
		for (BlockPos table : List.of(new BlockPos(4, 1, 4), new BlockPos(7, 1, 4))) {
			helper.assertTrue(brought.get(table).is(Blocks.SPRUCE_PLANKS), "brought to today's spots, the table at " + table.toShortString() + " is " + brought.get(table));
		}
		helper.succeed();
	}
}
