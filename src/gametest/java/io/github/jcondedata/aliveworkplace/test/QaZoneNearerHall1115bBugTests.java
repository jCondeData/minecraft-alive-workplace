package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-15), adversarial checks of B805's fix ("a painted cell whose middle is nearer another hall is that hall's
 * village's only: a Stone Wall there makes the nearer village walled, not both"). Same setup as
 * QaZoneNearerHall1115bGameTests: alone in its batch, leftover halls cleared, radius 12 (a cell is one block).
 */
public class QaZoneNearerHall1115bBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos A = new BlockPos(1, 2, 15);
	private static final int CELL_DX = 14;

	private static BlockPos setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		helper.setBlock(A, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return helper.absolutePos(A);
	}

	private static void paint(GameTestHelper helper, BlockPos a, BlockPos... at) {
		BitSet cells = new BitSet();
		for (BlockPos p : at) {
			cells.set(CityPlan.cellAt(a, p));
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(A);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
	}

	/** Records a finished Stone Wall with its middle at {@code middle}, turned {@code turn}; returns its corner (origin). */
	private static BlockPos wall(GameTestHelper helper, BlockPos middle, Rotation turn, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, turn, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: the wall's middle is " + spot + ", not " + middle);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.nameUUIDFromBytes(("qa1115b-bug-" + name).getBytes(StandardCharsets.UTF_8)));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return origin;
	}

	/**
	 * Hall B stands on higher ground: 11 blocks from the cell across the ground (A is 14 across, same height), 10 blocks
	 * up. Across the ground, the way the mod measures villages (VillageHalls: "the nearest across if two do"; reach never
	 * counts up and down), B is the nearer hall and the cell is inside its radius, so a Stone Wall there should wall B only.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneHigherHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneHigherHall")
	public void aNearerHallOnHigherGroundStillTakesTheCell(GameTestHelper helper) {
		BlockPos a = setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos cell = a.east(CELL_DX);
		paint(helper, a, cell);
		helper.setBlock(A.east(CELL_DX + 11).above(10), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos b = helper.absolutePos(A.east(CELL_DX + 11).above(10));
		int aBefore = VillageRanks.score(level, a, 0).buildings();
		BlockPos origin = wall(helper, cell, Rotation.CLOCKWISE_180, "higher");
		helper.assertFalse(Reach.claims(level, a, origin), "the setup: the wall's corner " + origin + " is in village A");
		helper.assertTrue(Sieges.walled(level, b), "the setup: the wall 11 blocks across from hall B doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a),
			"hall B is 11 blocks across from A's painted cell (A 14) but 10 blocks higher: the wall there walls both villages");
		helper.assertTrue(VillageRanks.score(level, a, 0).buildings() == aBefore, "the wall in the cell nearer B (across) also counted for A's rank");
		helper.succeed();
	}

	/**
	 * A Stone Wall whose middle is in a cell A painted that is nearer hall B (inside B's radius), and whose corner lies in
	 * another cell A painted, outside A's radius and nearer A. The rule goes by the building's middle ("those whose
	 * middle lies in the hall's radius or in a cell painted into one of its City Plan zones"; B805: "a Stone Wall there
	 * makes the nearer village walled, not both"), so the wall is B's only.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1115bZoneCornerInA"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1115bZoneCornerInA")
	public void aWallWhoseMiddleIsInTheNearerHallsCellIsThatVillagesOnly(GameTestHelper helper) {
		BlockPos a = setUp(helper);
		ServerLevel level = helper.getLevel();
		// the middle 14 east and 8 south of A (16.1 off); the wall unturned, so its corner is west and north of its middle
		BlockPos cell = a.offset(CELL_DX, 0, 8);
		BlockPos origin = wall(helper, cell, Rotation.NONE, "corner");
		paint(helper, a, cell, origin);
		// hall B 11 east of the middle: nearer the middle than A, farther from the corner than A
		BlockPos bRel = A.offset(CELL_DX + 11, 0, 8);
		helper.setBlock(bRel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos b = helper.absolutePos(bRel);
		long cx = origin.getX() - a.getX();
		long cz = origin.getZ() - a.getZ();
		helper.assertTrue(cx * cx + cz * cz > 144, "the setup: the corner " + origin.subtract(a) + " from A is inside A's radius");
		helper.assertTrue(origin.distSqr(b) > origin.distSqr(a), "the setup: the corner " + origin.subtract(a) + " from A isn't nearer A than B");
		helper.assertFalse(Reach.claims(level, a, cell), "the setup: the middle's cell nearer B is still A's");
		helper.assertTrue(Reach.claims(level, a, origin), "the setup: the corner's cell " + origin.subtract(a) + " isn't A's");
		helper.assertTrue(Sieges.walled(level, b), "the wall whose middle is 11 blocks from hall B doesn't wall B");
		helper.assertFalse(Sieges.walled(level, a), "the wall's middle is in a cell nearer hall B, but its corner (" + origin.subtract(a)
			+ " from A) in another cell A painted makes village A walled too: one building counts for both villages");
		helper.succeed();
	}
}
