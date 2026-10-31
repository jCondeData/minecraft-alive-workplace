package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * B639 (the owner, 2026-10-27: the Clinic's ward had one real bed and three "fake beds" of wooden slabs and white carpet,
 * and an open block in the ceiling where the roof's two stair runs meet at the ridge): no build has a cot of slab and
 * carpet where a bed belongs; every Clinic (the builder's, its modded copy, the village looks) has its ward beds whole,
 * heads to a wall; and its roofs have no gap under the ridge.
 */
public class ClinicWardGameTests implements FabricGameTest {
	private static List<ResourceLocation> clinics() {
		List<ResourceLocation> out = new ArrayList<>(List.of(AliveWorkplace.id("clinic"), AliveWorkplace.id("modded/clinic")));
		VillageHouses.STYLES.forEach(style -> out.add(AliveWorkplace.id("village/" + style + "_clinic")));
		return out;
	}

	private static Map<BlockPos, BlockState> template(GameTestHelper helper, ResourceLocation id) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new AssertionError("missing " + id)).blocks()
			.forEach(b -> out.put(b.pos(), b.state()));
		return out;
	}

	private static BlockState at(Map<BlockPos, BlockState> template, BlockPos pos) {
		return template.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	/** A carpet on a bottom slab laid on the floor (not on a trestle or a post): half of a stand-in bed. */
	private static boolean cotHalf(Map<BlockPos, BlockState> template, BlockPos pos) {
		BlockState carpet = at(template, pos);
		BlockState slab = at(template, pos.below());
		BlockState floor = at(template, pos.below(2));
		return carpet.getBlock() instanceof CarpetBlock && slab.getBlock() instanceof SlabBlock
			&& slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM && !floor.isAir()
			&& !(floor.getBlock() instanceof FenceBlock || floor.getBlock() instanceof WallBlock || floor.getBlock() instanceof SlabBlock
				|| floor.getBlock() instanceof StairBlock || floor.getBlock() instanceof RotatedPillarBlock);
	}

	/** No build of ours lays carpets on slabs two in a row, the cot that read as a broken bed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noBuildHasCotsOfSlabAndCarpet(GameTestHelper helper) {
		List<ResourceLocation> ids = new ArrayList<>(BlueprintLibrary.list(helper.getLevel().getServer(), true).stream()
			.filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID)).toList());
		helper.assertTrue(ids.containsAll(clinics()), "the Clinics are not all listed: " + clinics());
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : ids) {
			Map<BlockPos, BlockState> template = template(helper, id);
			template.forEach((pos, state) -> {
				if (!cotHalf(template, pos)) {
					return;
				}
				for (Direction side : new Direction[] {Direction.EAST, Direction.SOUTH}) {
					BlockPos next = pos.relative(side);
					if (cotHalf(template, next) && at(template, next).getBlock() == state.getBlock()) {
						problems.add(id.getPath() + " " + pos + "-" + next);
					}
				}
			});
		}
		helper.assertTrue(ids.size() > 100, "only " + ids.size() + " builds looked at");
		helper.assertTrue(problems.isEmpty(), problems.size() + " cots of slab and carpet, e.g. " + problems.subList(0, Math.min(6, problems.size())));
		helper.succeed();
	}

	/**
	 * Every Clinic's beds are whole (head and foot), heads to a wall; its ward has at least two of them besides any home
	 * bed.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyClinicBedIsWholeWithItsHeadToAWall(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : clinics()) {
			Map<BlockPos, BlockState> template = template(helper, id);
			int heads = 0;
			for (Map.Entry<BlockPos, BlockState> e : template.entrySet()) {
				BlockState state = e.getValue();
				if (!(state.getBlock() instanceof BedBlock)) {
					continue;
				}
				Direction facing = state.getValue(BedBlock.FACING);
				boolean head = state.getValue(BedBlock.PART) == BedPart.HEAD;
				BlockPos otherPos = head ? e.getKey().relative(facing.getOpposite()) : e.getKey().relative(facing);
				BlockState other = at(template, otherPos);
				if (other.getBlock() != state.getBlock() || other.getValue(BedBlock.FACING) != facing
					|| other.getValue(BedBlock.PART) == state.getValue(BedBlock.PART)) {
					problems.add(id.getPath() + " half bed at " + e.getKey());
				}
				if (head) {
					heads++;
				}
				// The modded copy's walls are other mods' blocks (Supplementaries' daub), air on a test server without
				// them: its beds' walls are checked in the builder's Clinic it copies
				if (head && !id.getPath().startsWith("modded/")) {
					BlockState behind = at(template, e.getKey().relative(facing));
					boolean wall = behind.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, facing.getOpposite())
						|| behind.getBlock() instanceof IronBarsBlock || behind.getBlock() instanceof TransparentBlock;
					if (!wall) {
						problems.add(id.getPath() + " bed at " + e.getKey() + " has " + behind + " at its head");
					}
				}
			}
			int wanted = id.getPath().startsWith("village/") ? 3 : 4;
			if (heads < wanted) {
				problems.add(id.getPath() + " has " + heads + " beds, wants " + wanted);
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.toString());
		helper.succeed();
	}

	/**
	 * No Clinic roof leaves an open block under its ridge: where a ridge slab tops two stairs climbing to it from both
	 * sides, the cell under the slab is filled, so the ceiling seen from inside is closed.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theClinicRoofsHaveNoGapUnderTheRidge(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int ridges = 0;
		for (ResourceLocation id : clinics()) {
			Map<BlockPos, BlockState> template = template(helper, id);
			for (Map.Entry<BlockPos, BlockState> e : template.entrySet()) {
				BlockState slab = e.getValue();
				if (!(slab.getBlock() instanceof SlabBlock) || slab.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
					continue;
				}
				BlockPos under = e.getKey().below();
				for (Direction up : new Direction[] {Direction.EAST, Direction.SOUTH}) {
					if (climbsTo(at(template, under.relative(up.getOpposite())), up) && climbsTo(at(template, under.relative(up)), up.getOpposite())) {
						ridges++;
						if (at(template, under).isAir()) {
							problems.add(id.getPath() + " open under the ridge at " + under);
						}
					}
				}
			}
		}
		helper.assertTrue(ridges > 20, "only " + ridges + " ridge cells found");
		helper.assertTrue(problems.isEmpty(), problems.size() + " gaps, e.g. " + problems.subList(0, Math.min(6, problems.size())));
		helper.succeed();
	}

	private static boolean climbsTo(BlockState state, Direction facing) {
		return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM && state.getValue(StairBlock.FACING) == facing;
	}
}
