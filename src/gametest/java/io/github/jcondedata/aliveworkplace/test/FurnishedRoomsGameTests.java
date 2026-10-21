package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnishingTable;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Support;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * B260 (the owner, 2026-10-20: furnished interiors looked cluttered and unfinished): in every furnished blueprint (a home
 * or workplace of 37.4 and 37.5, whose drawing records pieces beyond the chairs, tables and beds found by themselves),
 * every piece stands on, hangs from or leans against what its kind needs, none stands in a door's way, and every bed's
 * head is against a wall; a modded piece goes only where what it needs is there, and a two-block-tall piece gets its top.
 */
public class FurnishedRoomsGameTests implements FabricGameTest {
	/** The kinds found in any drawing by themselves; a blueprint with a spot of another kind is a furnished one. */
	private static final Set<Kind> FOUND = EnumSet.of(Kind.CHAIR, Kind.TABLE, Kind.BED);

	/** What each kind may stand on or against (any of them); every kind not listed stands on the floor. */
	private static Set<Support> needs(Kind kind) {
		return switch (kind) {
			case LAMP -> EnumSet.of(Support.FLOOR, Support.CEILING);
			case SHELF -> EnumSet.of(Support.FLOOR, Support.WALL);
			case PICTURE, BLINDS, TRELLIS -> EnumSet.of(Support.WALL);
			case HANGING_POT -> EnumSet.of(Support.CEILING);
			default -> EnumSet.of(Support.FLOOR);
		};
	}

	private record Furnished(ResourceLocation id, List<FurnitureSpot> spots, Map<BlockPos, BlockState> template) {
	}

	private static List<Furnished> furnished(GameTestHelper helper) {
		List<Furnished> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, List<FurnitureSpot>> e : BlueprintFurniture.all().entrySet()) {
			if (!e.getKey().getNamespace().equals(AliveWorkplace.MOD_ID) || e.getValue().stream().allMatch(s -> FOUND.contains(s.kind()))) {
				continue;
			}
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), e.getKey()).orElse(null);
			if (blueprint == null) {
				continue; // FurnitureSpotGameTests reports a spots file without its blueprint
			}
			Map<BlockPos, BlockState> template = new HashMap<>();
			blueprint.blocks().forEach(b -> template.put(b.pos(), b.state()));
			out.add(new Furnished(e.getKey(), e.getValue(), template));
		}
		helper.assertTrue(out.size() >= 40, "only " + out.size() + " furnished blueprints found");
		return out;
	}

	private static BlockState at(Map<BlockPos, BlockState> template, BlockPos pos) {
		return template.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	/** A block whose face toward {@code side} holds a piece up or behind it (a window counts as a wall). */
	private static boolean holds(BlockState state, Direction side, boolean windowsToo) {
		if (state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side)) {
			return true;
		}
		return windowsToo && (state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof TransparentBlock);
	}

	/** What the spot stands on or against in the template, as this test reads it. */
	private static Set<Support> supportIn(Map<BlockPos, BlockState> template, FurnitureSpot spot) {
		Set<Support> out = EnumSet.noneOf(Support.class);
		List<BlockPos> cells = spot.positions();
		int low = cells.stream().mapToInt(BlockPos::getY).min().orElse(0);
		int high = cells.stream().mapToInt(BlockPos::getY).max().orElse(0);
		for (BlockPos cell : cells) {
			if (cell.getY() == low && holds(at(template, cell.below()), Direction.UP, false)) {
				out.add(Support.FLOOR);
			}
			BlockPos behind = cell.relative(spot.facing().getOpposite());
			if (holds(at(template, behind), spot.facing(), true)) {
				out.add(Support.WALL);
			}
			BlockState over = at(template, cell.above());
			if (cell.getY() == high && (holds(over, Direction.DOWN, false) || over.getBlock() instanceof ChainBlock)) {
				out.add(Support.CEILING);
			}
		}
		return out;
	}

	private static void report(GameTestHelper helper, List<String> problems, String what) {
		helper.assertTrue(problems.isEmpty(), problems.size() + " " + what + ", e.g. " + problems.subList(0, Math.min(6, problems.size())));
	}

	/** Nothing floats: a lamp stands or hangs, a picture and blinds are on a wall, everything else stands on a floor. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyFurnishedPieceHasWhatItStandsOn(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Furnished f : furnished(helper)) {
			for (FurnitureSpot spot : f.spots()) {
				Set<Support> has = supportIn(f.template(), spot);
				if (has.stream().noneMatch(needs(spot.kind())::contains)) {
					problems.add(f.id().getPath() + " " + spot.kind().id() + "@" + spot.pos() + " has only " + has);
				}
				if (spot.support() == null) {
					problems.add(f.id().getPath() + " " + spot.kind().id() + "@" + spot.pos() + " has no support written");
				}
			}
		}
		report(helper, problems, "pieces float");
		helper.succeed();
	}

	/** No piece within two blocks of a door, in or out, along the way through it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noFurnitureStandsInADoorsWay(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Furnished f : furnished(helper)) {
			Map<BlockPos, FurnitureSpot> taken = new HashMap<>();
			f.spots().forEach(s -> s.positions().forEach(p -> taken.put(p, s)));
			f.template().forEach((pos, state) -> {
				if (!(state.getBlock() instanceof DoorBlock) || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
					return;
				}
				Direction way = state.getValue(DoorBlock.FACING);
				for (int d : new int[] {1, 2, -1, -2}) {
					for (int dy = 0; dy < 2; dy++) {
						FurnitureSpot in = taken.get(pos.relative(way, d).above(dy));
						if (in != null) {
							problems.add(f.id().getPath() + " " + in.kind().id() + "@" + in.pos() + " in the way of the door at " + pos);
						}
					}
				}
			});
		}
		report(helper, problems, "pieces in doors' way");
		helper.succeed();
	}

	/** Every bed in a furnished blueprint has its head against a wall (or a window in it), never out in the room. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBedHasItsHeadToAWall(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int beds = 0;
		for (Furnished f : furnished(helper)) {
			for (Map.Entry<BlockPos, BlockState> e : f.template().entrySet()) {
				BlockState state = e.getValue();
				if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
					continue;
				}
				beds++;
				Direction facing = state.getValue(BedBlock.FACING);
				BlockState behind = at(f.template(), e.getKey().relative(facing));
				if (!holds(behind, facing.getOpposite(), true)) {
					problems.add(f.id().getPath() + " bed@" + e.getKey() + " has " + behind + " at its head");
				}
			}
		}
		helper.assertTrue(beds > 30, "only " + beds + " beds in furnished blueprints");
		report(helper, problems, "beds face into nothing");
		helper.succeed();
	}

	/**
	 * The Stone House the owner reported: downstairs the way from the door to the ladder is clear (no chair, no clock
	 * beside the door), upstairs the attic is a bedroom (a bed to the gable, a nightstand, a chest at its foot, a hung lamp).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStoneHouseReadsAsAHome(GameTestHelper helper) {
		ResourceLocation id = AliveWorkplace.id("stone_house");
		Map<BlockPos, BlockState> template = new HashMap<>();
		BlueprintLibrary.get(helper.getLevel(), id).orElseThrow().blocks().forEach(b -> template.put(b.pos(), b.state()));
		List<FurnitureSpot> spots = BlueprintFurniture.of(id);
		for (FurnitureSpot spot : spots) {
			for (BlockPos p : spot.positions()) {
				helper.assertFalse(p.getX() >= 4 && p.getX() <= 6 && p.getZ() <= 3 && p.getY() <= 2,
					spot.kind().id() + " at " + p + " stands by the door");
			}
		}
		FurnitureSpot clock = spots.stream().filter(s -> s.kind() == Kind.CLOCK).findFirst().orElseThrow();
		helper.assertTrue(clock.support() != null && clock.support().contains(Support.FLOOR), "the clock's support is " + clock.support());
		long upstairsBeds = spots.stream().filter(s -> s.kind() == Kind.BED && s.pos().getY() == 6).count();
		long nightstands = spots.stream().filter(s -> s.kind() == Kind.NIGHTSTAND && s.pos().getY() == 6).count();
		helper.assertTrue(upstairsBeds == 1 && nightstands == 1, "the attic has " + upstairsBeds + " beds and " + nightstands + " nightstands");
		long chests = template.entrySet().stream().filter(e -> e.getKey().getY() == 6 && e.getValue().is(Blocks.CHEST)).count();
		long hung = template.entrySet().stream().filter(e -> e.getValue().is(Blocks.LANTERN) && e.getKey().getY() >= 6
			&& e.getValue().getValue(BlockStateProperties.HANGING)).count();
		long standing = template.entrySet().stream().filter(e -> e.getValue().is(Blocks.LANTERN) && e.getKey().getY() == 6).count();
		helper.assertTrue(chests == 1 && hung == 1 && standing == 0, "the attic has " + chests + " chests, " + hung + " hung lanterns, "
			+ standing + " on the floor");
		helper.succeed();
	}

	/**
	 * The furnishing tables' pieces that need a floor, a wall or a ceiling go only where the spot has it (B260: a Beautify
	 * picture frame on a wall, a Handcrafted shelf mid-air), and a two-block-tall piece gets its top half, or none at all.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void piecesGoOnlyWhereWhatTheyNeedIs(GameTestHelper helper) {
		helper.assertTrue(FurnishingTable.of("beautify").map(t -> t.pieces().get(Kind.PICTURE).needs() == Support.FLOOR).orElse(false),
			"Beautify's picture frame doesn't need a floor");
		helper.assertTrue(FurnishingTable.of("handcrafted").map(t -> t.pieces().get(Kind.SHELF).needs() == Support.FLOOR).orElse(false),
			"Handcrafted's shelf doesn't need a floor");
		helper.assertTrue(FurnishingTable.of("cozyhome").map(t -> t.pieces().get(Kind.CLOCK).needs() == Support.FLOOR).orElse(false),
			"Cozy Home's clock doesn't need a floor");
		List<FurnitureSpot.Cell> tall = List.of(new FurnitureSpot.Cell(BlockPos.ZERO, Blocks.STRIPPED_OAK_LOG.defaultBlockState()),
			new FurnitureSpot.Cell(new BlockPos(0, 1, 0), Blocks.OAK_TRAPDOOR.defaultBlockState()));
		FurnishingTable.Piece lilac = new FurnishingTable.Piece(List.of("minecraft:lilac"), null, Map.of(), "oak", "white",
			List.of(new FurnishingTable.FootCell(BlockPos.ZERO, Map.of())), Support.FLOOR);
		FurnitureSpot onWall = new FurnitureSpot(Kind.CLOCK, new BlockPos(1, 2, 1), Direction.SOUTH, "oak", null, tall, EnumSet.of(Support.WALL));
		helper.assertTrue(lilac.resolve(onWall) == null, "a piece that needs a floor went on a wall");
		FurnitureSpot onFloor = new FurnitureSpot(Kind.CLOCK, new BlockPos(1, 1, 1), Direction.SOUTH, "oak", null, tall, EnumSet.of(Support.FLOOR, Support.WALL));
		Map<BlockPos, BlockState> cells = lilac.resolve(onFloor);
		helper.assertTrue(cells != null && cells.get(BlockPos.ZERO).is(Blocks.LILAC)
				&& cells.get(BlockPos.ZERO).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
				&& cells.get(new BlockPos(0, 1, 0)).is(Blocks.LILAC)
				&& cells.get(new BlockPos(0, 1, 0)).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER,
			"the tall piece is " + cells);
		FurnitureSpot low = new FurnitureSpot(Kind.CLOCK, new BlockPos(1, 1, 1), Direction.SOUTH, "oak", null, tall.subList(0, 1), EnumSet.of(Support.FLOOR));
		helper.assertTrue(lilac.resolve(low) == null, "a two-tall piece went where there is room for its lower half only");
		FurnitureSpot unknown = new FurnitureSpot(Kind.CLOCK, new BlockPos(1, 1, 1), Direction.SOUTH, "oak", null, tall);
		helper.assertTrue(lilac.resolve(unknown) != null, "a spot of a datapack (support not written) refuses the piece");
		helper.succeed();
	}
}
