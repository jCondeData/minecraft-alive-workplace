package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.VillageMachines;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane E, Create integration), B243a: villagers' Create machine work uses only the village's own chests and machines,
 * "inside the village's builds" (owner, 2026-10-20); a player's own base elsewhere is never touched. Core only: which
 * blocks count as the village's ({@link VillageMachines#village}), checked against where a builder actually puts each
 * block of a building for every turn and flip (the template position moved by the placement, as the build plan does),
 * so a machine one block outside a turned workshop is the player's and one on its outermost row is the village's.
 */
public class QaVillageMachinesGameTests implements FabricGameTest {
	private static final AtomicInteger BUILDINGS = new AtomicInteger();

	/** A finished building of {@code size} at {@code origin}, recorded as the builders record one; forgotten after the test. */
	private static BlueprintData.Placement building(GameTestHelper helper, ResourceLocation dimension, BlockPos origin, Vec3i size, Rotation rotation,
		Mirror mirror) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("qa_test/village_machines_" + BUILDINGS.incrementAndGet());
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, origin, size, false, Blocks.AIR);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: the building's blueprint isn't known");
		helper.assertTrue(BlueprintLibrary.get(level, id).get().size().equals(size), "setup: the blueprint's size isn't " + size);
		BlueprintData.Placement placement = new BlueprintData.Placement(dimension, origin, rotation, mirror);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		return placement;
	}

	/** Every block a builder places for a building of {@code size} at {@code placement} (BuildPlan's own move of each one). */
	private static Set<BlockPos> placed(BlueprintData.Placement placement, Vec3i size) {
		Set<BlockPos> out = new HashSet<>();
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					out.add(placement.origin().offset(StructureTemplate.transform(new BlockPos(x, y, z), placement.mirror(), placement.rotation(), BlockPos.ZERO)));
				}
			}
		}
		return out;
	}

	/**
	 * For each of the four turns and three flips, every block in and around an uneven building (5 wide, 3 tall, 7 deep):
	 * a block the builders placed counts as the village's, and every block one or two past its walls, floor or roof
	 * doesn't.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void exactlyTheBuildingsOwnBlocksAreTheVillagesForEveryTurnAndFlip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3i size = new Vec3i(5, 3, 7);
		BlockPos origin = helper.absolutePos(new BlockPos(0, 120, 0));
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (Mirror mirror : Mirror.values()) {
			for (Rotation rotation : Rotation.values()) {
				BlueprintData.Placement placement = building(helper, level.dimension().location(), origin, size, rotation, mirror);
				try {
					Set<BlockPos> inside = placed(placement, size);
					for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-9, -2, -9), origin.offset(9, 4, 9))) {
						boolean village = VillageMachines.village(level, pos);
						helper.assertTrue(village == inside.contains(pos), (village ? "a block outside" : "a block inside") + " a finished building turned "
							+ rotation + " and flipped " + mirror + " was taken " + (village ? "for the village's" : "for a player's") + ": " + pos.subtract(origin)
							+ " from its origin");
					}
				} finally {
					sites.forgetFinished(placement);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Taking a building down (forgetting it) makes its machines a player's at once, and finishing one makes them the
	 * village's at once: no stale answer for the 200 ticks the outlines are kept.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBuildingFinishedOrTakenDownCountsAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 140, 0));
		BlockPos machine = origin.offset(1, 1, 1);
		helper.assertTrue(!VillageMachines.village(level, machine), "setup: the spot is already in a building");
		BlueprintData.Placement placement = building(helper, level.dimension().location(), origin, new Vec3i(3, 3, 3), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(VillageMachines.village(level, machine), "a machine in a building finished this tick isn't the village's yet");
		BuildSiteManager.get(level).forgetFinished(placement);
		helper.assertTrue(!VillageMachines.village(level, machine), "a machine in a building taken down this tick is still the village's");
		helper.succeed();
	}

	/**
	 * A building finished in the Nether at the same numbers doesn't make an Overworld player's base at those numbers the
	 * village's.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBuildingInAnotherDimensionDoesntCountHere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 160, 0));
		BlueprintData.Placement placement = building(helper, Level.NETHER.location(), origin, new Vec3i(3, 3, 3), Rotation.NONE, Mirror.NONE);
		try {
			helper.assertTrue(!VillageMachines.village(level, origin.offset(1, 1, 1)), "a building finished in the Nether made the same spot in the "
				+ "Overworld the village's");
		} finally {
			BuildSiteManager.get(level).forgetFinished(placement);
		}
		helper.succeed();
	}
}
