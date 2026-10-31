package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1031-0637) for B260 from its spec, the parts FurnishedRoomsGameTests doesn't check: in every furnished
 * blueprint (a home or workplace whose drawing records pieces beyond chairs, tables and beds), "lanterns hung or on a
 * table" (never standing on the room's floor) and "chests against a wall or at the foot of a bed" (never placed at random
 * out in the room). Read from the blueprints as the builder places them, with no furniture mod (vanilla pieces).
 */
public class QaFurnishedRooms1031GameTests implements FabricGameTest {
	private static final Set<Kind> FOUND = EnumSet.of(Kind.CHAIR, Kind.TABLE, Kind.BED);

	/**
	 * Lanterns standing on a floor that isn't a furnished room's, each with its reason (left for the owner to judge; ROADMAP
	 * Notes, lane-b-1031-0637): the Schoolhouse's open belfry (fence posts and a stair roof, a lantern where a bell would
	 * hang) and the Library's 3x3 lookout at the top of its tower ladder (II-IV).
	 */
	private static final Set<String> NOT_A_ROOM = Set.of("schoolhouse 5, 11, 4", "schoolhouse_2 5, 11, 4", "library_2 14, 9, 6",
		"library_3 14, 9, 6", "library_4 14, 9, 6");

	private record Furnished(ResourceLocation id, Map<BlockPos, BlockState> template) {
	}

	private static List<Furnished> furnished(GameTestHelper helper) {
		List<Furnished> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, List<FurnitureSpot>> e : BlueprintFurniture.all().entrySet()) {
			if (!e.getKey().getNamespace().equals(AliveWorkplace.MOD_ID) || e.getValue().stream().allMatch(s -> FOUND.contains(s.kind()))) {
				continue;
			}
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), e.getKey()).orElse(null);
			if (blueprint == null) {
				continue;
			}
			Map<BlockPos, BlockState> template = new HashMap<>();
			blueprint.blocks().forEach(b -> template.put(b.pos(), b.state()));
			out.add(new Furnished(e.getKey(), template));
		}
		helper.assertTrue(out.size() >= 40, "only " + out.size() + " furnished blueprints found");
		return out;
	}

	private static BlockState at(Map<BlockPos, BlockState> template, BlockPos pos) {
		return template.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	/** Under a roof: something solid over it within 8 blocks (a room, not a porch post or a garden). */
	private static boolean indoors(Map<BlockPos, BlockState> template, BlockPos pos) {
		for (int dy = 1; dy <= 8; dy++) {
			if (!at(template, pos.above(dy)).isAir()) {
				return true;
			}
		}
		return false;
	}

	private static boolean sturdyToward(BlockState state, Direction side) {
		return state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side);
	}

	/**
	 * A lantern standing (not hanging) inside a room sits on its floor when the block under it is part of a level floor
	 * (the four blocks round that one are floor too) and the lantern has open room on at least two sides: a lantern on a
	 * table, a counter, a shelf or a ledge has open air beside the block it stands on.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noLanternStandsOnARoomsFloor(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int lanterns = 0;
		for (Furnished f : furnished(helper)) {
			for (Map.Entry<BlockPos, BlockState> e : f.template().entrySet()) {
				BlockState state = e.getValue();
				if (!(state.getBlock() instanceof LanternBlock) || !indoors(f.template(), e.getKey())) {
					continue;
				}
				lanterns++;
				if (state.getValue(LanternBlock.HANGING)) {
					continue;
				}
				BlockPos pos = e.getKey();
				BlockPos under = pos.below();
				if (!sturdyToward(at(f.template(), under), Direction.UP)) {
					continue; // on a fence post, a chain, a slab table: not the floor
				}
				int floorAround = 0;
				int open = 0;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (sturdyToward(at(f.template(), under.relative(d)), Direction.UP) && at(f.template(), pos.relative(d)).isAir()) {
						floorAround++;
					}
					if (at(f.template(), pos.relative(d)).isAir()) {
						open++;
					}
				}
				if ((floorAround == 4 || (floorAround >= 3 && open >= 3)) && !NOT_A_ROOM.contains(f.id().getPath() + " " + pos.toShortString())) {
					problems.add(f.id().getPath() + " lantern@" + pos.toShortString());
				}
			}
		}
		helper.assertTrue(lanterns > 20, "only " + lanterns + " indoor lanterns in furnished blueprints");
		helper.assertTrue(problems.isEmpty(), problems.size() + " lanterns stand on a room's floor (B260: hung or on a table), e.g. "
			+ problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	/**
	 * Every chest inside a furnished build stands against something: a wall or a solid block on at least one side, a
	 * bed (at its foot), or its other half; none stands alone out in the room.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyChestStandsAgainstAWallOrABed(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int chests = 0;
		for (Furnished f : furnished(helper)) {
			for (Map.Entry<BlockPos, BlockState> e : f.template().entrySet()) {
				if (!(e.getValue().getBlock() instanceof ChestBlock) || !indoors(f.template(), e.getKey())) {
					continue;
				}
				chests++;
				boolean against = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockState side = at(f.template(), e.getKey().relative(d));
					if (sturdyToward(side, d.getOpposite()) || side.getBlock() instanceof BedBlock || side.getBlock() instanceof ChestBlock
						|| !side.isAir() && !side.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
						against = true;
						break;
					}
				}
				if (!against) {
					problems.add(f.id().getPath() + " chest@" + e.getKey().toShortString());
				}
			}
		}
		helper.assertTrue(chests > 20, "only " + chests + " indoor chests in furnished blueprints");
		helper.assertTrue(problems.isEmpty(), problems.size() + " chests stand alone in a room (B260: against a wall or at a bed's foot), e.g. "
			+ problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}
}
