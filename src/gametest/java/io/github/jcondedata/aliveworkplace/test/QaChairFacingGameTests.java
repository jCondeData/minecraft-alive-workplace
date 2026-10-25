package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * QA (lane-b-1025-1838), from B148's spec ("every chair stair in a blueprint faces its table, seat open toward the table"),
 * not from the fix or tools/blueprints/check.py: every build the Blueprint Table lists is read from the .nbt the mod
 * loads, and every chair beside a table must have that table on its open side.
 * <ul>
 * <li>A <b>chair</b> is a bottom-half stair with nothing on its seat (no collision in the block over it), standing on
 * something (collision in the block under it).</li>
 * <li>Its <b>open side</b> comes from the stair's real collision shape, not from the FACING name: the side whose upper
 * half (y 0.5 to 1) of the block is empty; the tall back fills the other.</li>
 * <li>A <b>table</b> beside it (same layer, one of the four sides) is a fence or wall post with a pressure plate or
 * carpet on top, or a top-half slab (a table top at seat height) with nothing full on it (a candle, lantern, pot is
 * fine). Those are the tables the shipped builds use.</li>
 * </ul>
 * The same check runs on each build turned the four ways and mirrored, with the positions and block states transformed
 * exactly as a Build Site places them (StructureTemplate.transform, state.mirror then rotate): a chair must still face
 * its table however the player turned the blueprint.
 */
public class QaChairFacingGameTests implements FabricGameTest {

	static boolean listed(ResourceLocation id) {
		// the modded/ looks need their mods' blocks, missing here
		return id.getNamespace().equals("aliveworkplace") && !id.getPath().startsWith("modded/");
	}

	private static VoxelShape shape(BlockState state) {
		return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private static BlockState at(Map<BlockPos, BlockState> blocks, BlockPos pos) {
		return QaBasementGameTests.at(blocks, pos);
	}

	static boolean chair(Map<BlockPos, BlockState> blocks, BlockPos pos) {
		BlockState state = blocks.get(pos);
		if (state == null || !(state.getBlock() instanceof StairBlock) || state.getValue(BlockStateProperties.HALF) != Half.BOTTOM) {
			return false;
		}
		return shape(at(blocks, pos.above())).isEmpty() && !shape(at(blocks, pos.below())).isEmpty();
	}

	/** The sides of a stair whose upper half has no collision: where a seat on it opens. */
	static List<Direction> openSides(BlockState stair) {
		List<Direction> out = new ArrayList<>();
		List<AABB> boxes = shape(stair).toAabbs();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			double x0 = d == Direction.EAST ? 0.5 : 0, x1 = d == Direction.WEST ? 0.5 : 1;
			double z0 = d == Direction.SOUTH ? 0.5 : 0, z1 = d == Direction.NORTH ? 0.5 : 1;
			AABB half = new AABB(x0 + 1e-3, 0.5 + 1e-3, z0 + 1e-3, x1 - 1e-3, 1 - 1e-3, z1 - 1e-3);
			if (boxes.stream().noneMatch(b -> b.intersects(half))) {
				out.add(d);
			}
		}
		return out;
	}

	static boolean table(Map<BlockPos, BlockState> blocks, BlockPos pos) {
		BlockState state = at(blocks, pos);
		BlockState top = at(blocks, pos.above());
		if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)) {
			return top.getBlock() instanceof BasePressurePlateBlock || top.getBlock() instanceof CarpetBlock;
		}
		if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.TOP) {
			return !top.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		}
		return false;
	}

	/** Every chair beside a table in {@code blocks} that doesn't open toward one; counts chairs into {@code count[0]}. */
	static List<String> backs(Map<BlockPos, BlockState> blocks, int[] count) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			BlockPos pos = e.getKey();
			if (!chair(blocks, pos)) {
				continue;
			}
			List<Direction> tables = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (table(blocks, pos.relative(d))) {
					tables.add(d);
				}
			}
			if (tables.isEmpty()) {
				continue;
			}
			count[0]++;
			List<Direction> open = openSides(e.getValue());
			if (open.isEmpty()) {
				out.add("chair " + pos.toShortString() + " (" + e.getValue() + ") has no open side");
			} else if (tables.stream().noneMatch(open::contains)) {
				out.add("chair " + pos.toShortString() + " facing=" + e.getValue().getValue(StairBlock.FACING) + " opens " + open
					+ ", table on its " + tables + " side");
			}
		}
		return out;
	}

	/** The blueprint's blocks where a Build Site turned by {@code rotation} and flipped by {@code mirror} puts them. */
	static Map<BlockPos, BlockState> placed(Map<BlockPos, BlockState> blocks, Rotation rotation, Mirror mirror) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			out.put(StructureTemplate.transform(e.getKey(), mirror, rotation, BlockPos.ZERO), e.getValue().mirror(mirror).rotate(rotation));
		}
		return out;
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBuildsChairsOpenTowardTheirTable(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int builds = 0;
		int[] chairs = {0};
		for (ResourceLocation id : BlueprintLibrary.list(helper.getLevel().getServer(), true)) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (blueprint == null || !listed(id)) {
				continue;
			}
			builds++;
			List<String> bad = backs(QaBasementGameTests.blocks(blueprint), chairs);
			if (!bad.isEmpty()) {
				problems.add(id.getPath() + ": " + bad);
			}
		}
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"[QA B148] " + builds + " builds, " + chairs[0] + " chairs at tables checked, " + problems.size() + " builds with chairs facing away"));
		helper.assertTrue(builds > 100 && chairs[0] > 150, "only " + builds + " builds and " + chairs[0] + " chairs at tables checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " builds with chairs not facing their table: " + String.join("; ", problems));
		helper.succeed();
	}

	/** The same, with every build turned the four ways and mirrored as a Build Site places it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBuildsChairsStillFaceTheirTableTurnedAndMirrored(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int builds = 0;
		int[] chairs = {0};
		for (ResourceLocation id : BlueprintLibrary.list(helper.getLevel().getServer(), true)) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (blueprint == null || !listed(id)) {
				continue;
			}
			builds++;
			Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(blueprint);
			for (Mirror mirror : new Mirror[] {Mirror.NONE, Mirror.FRONT_BACK}) {
				for (Rotation rotation : Rotation.values()) {
					List<String> bad = backs(placed(blocks, rotation, mirror), chairs);
					if (!bad.isEmpty()) {
						problems.add(id.getPath() + " " + rotation + "/" + mirror + ": " + bad);
					}
				}
			}
		}
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"[QA B148] " + builds + " builds x 8 placements, " + chairs[0] + " placed chairs at tables checked, " + problems.size() + " bad placements"));
		helper.assertTrue(builds > 100 && chairs[0] > 8 * 150, "only " + builds + " builds and " + chairs[0] + " placed chairs checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " placements with chairs not facing their table: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Mutation check on shipped data: the Manor as loaded passes, and with any one of its chairs at a table turned round
	 * (in a copy of its blocks, never the file) the check names exactly that chair.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void turningOneOfTheManorsChairsRoundIsCaught(GameTestHelper helper) {
		Blueprint manor = BlueprintLibrary.get(helper.getLevel(), ResourceLocation.fromNamespaceAndPath("aliveworkplace", "manor")).orElseThrow();
		Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(manor);
		int[] count = {0};
		helper.assertTrue(backs(blocks, count).isEmpty(), "the Manor as shipped has a chair facing away");
		int seen = count[0];
		int caught = 0;
		for (Map.Entry<BlockPos, BlockState> e : new ArrayList<>(blocks.entrySet())) {
			if (!chair(blocks, e.getKey())) {
				continue;
			}
			boolean atTable = Direction.Plane.HORIZONTAL.stream().anyMatch(d -> table(blocks, e.getKey().relative(d)));
			if (!atTable) {
				continue;
			}
			BlockState was = e.getValue();
			blocks.put(e.getKey(), was.setValue(StairBlock.FACING, was.getValue(StairBlock.FACING).getOpposite()));
			List<String> bad = backs(blocks, new int[1]);
			blocks.put(e.getKey(), was);
			if (bad.size() == 1 && bad.get(0).contains(e.getKey().toShortString())) {
				caught++;
			}
		}
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"[QA B148] Manor: " + seen + " chairs at tables, " + caught + " caught when turned round"));
		helper.assertTrue(seen >= 5 && caught == seen, "of the Manor's " + seen + " chairs at tables, only " + caught + " were caught turned round");
		helper.succeed();
	}

	/**
	 * The rule itself, on vanilla's own blocks: an oak stair facing north (its back on the north side) opens south; a
	 * chair with a fence-and-plate table south of it passes, the same chair turned round fails.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theChairRuleTellsAChairFacingAwayFromOneFacingItsTable(GameTestHelper helper) {
		BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		helper.assertTrue(openSides(north).equals(List.of(Direction.SOUTH)), "an oak stair facing north opens " + openSides(north));
		Map<BlockPos, BlockState> room = new HashMap<>();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 2; z++) {
				room.put(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		room.put(new BlockPos(0, 1, 0), north);
		room.put(new BlockPos(0, 1, 1), Blocks.OAK_FENCE.defaultBlockState());
		room.put(new BlockPos(0, 2, 1), Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
		int[] count = {0};
		List<String> good = backs(room, count);
		room.put(new BlockPos(0, 1, 0), north.setValue(StairBlock.FACING, Direction.SOUTH));
		List<String> bad = backs(room, count);
		// a top slab as the table, the chair facing it, then sideways to it
		room.put(new BlockPos(0, 1, 1), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		room.remove(new BlockPos(0, 2, 1));
		room.put(new BlockPos(0, 1, 0), north);
		List<String> slabGood = backs(room, count);
		room.put(new BlockPos(0, 1, 0), north.setValue(StairBlock.FACING, Direction.EAST));
		List<String> sideways = backs(room, count);
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"[QA B148] rule check: facing " + good + ", away " + bad + ", slab " + slabGood + ", sideways " + sideways));
		helper.assertTrue(count[0] == 4, "the chair was seen at a table " + count[0] + " times of 4");
		helper.assertTrue(good.isEmpty() && slabGood.isEmpty(), "a chair facing its table was flagged: " + good + slabGood);
		helper.assertTrue(bad.size() == 1 && sideways.size() == 1, "a chair facing away or sideways wasn't flagged: " + bad + sideways);
		helper.succeed();
	}
}
