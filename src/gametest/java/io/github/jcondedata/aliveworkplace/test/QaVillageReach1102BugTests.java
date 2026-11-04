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
 * QA (2026-11-02), found while verifying B620: a City Plan cell belongs to one village (painting refuses a cell nearer
 * another hall: CityPlans.ownCells, "such a cell is that village's"), but a cell village A painted before hall B was placed
 * nearer it stays A's for Reach.claims and finishedInVillage, so a building finished there counts for both villages.
 * Fails on the code as of b929a7d2b; kept off main (sessions.md, the QA lane, step 3).
 */
public class QaVillageReach1102BugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A's zone painted first, then hall B placed so those cells lie in B's radius: a wall there is B's, not A's too. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Overlap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Overlap")
	public void aCellNearerALaterHallIsThatVillagesOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		BlueprintData.Placement[] recorded = new BlueprintData.Placement[1];
		Leftovers.after(helper, () -> {
			if (recorded[0] != null) {
				BuildSiteManager.get(level).forgetFinished(recorded[0]);
			}
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;

		// village A, alone, paints a Homes zone 13-15 blocks east of its hall (past its radius)
		BlockPos aRel = new BlockPos(4, 2, 15);
		helper.setBlock(aRel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos a = helper.absolutePos(aRel);
		VillageHallBlockEntity aHall = helper.getBlockEntity(aRel);
		BitSet cells = new BitSet();
		for (int dx = 13; dx <= 15; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				cells.set(CityPlan.cellAt(a, a.offset(dx, 0, dz)));
			}
		}
		aHall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		BlockPos middle = a.offset(14, 0, 0);
		int cell = CityPlan.cellAt(a, middle);
		helper.assertFalse(CityPlan.nearerAnotherHall(level, a, cell), "the setup: the cell is nearer another hall before B is placed");

		// later, hall B 8 blocks east of that zone
		BlockPos bRel = new BlockPos(26, 2, 15);
		helper.setBlock(bRel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos b = helper.absolutePos(bRel);
		helper.assertTrue(CityPlan.nearerAnotherHall(level, a, cell), "the setup: A's painted cell isn't nearer hall B");
		helper.assertTrue(middle.closerThan(b, 12), "the setup: the spot isn't inside B's radius");

		int aBefore = VillageRanks.score(level, a, 0).buildings();
		int bBefore = VillageRanks.score(level, b, 0).buildings();
		// a Stone Wall whose middle is on that cell; turned so its corner lies off A's grid
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, Rotation.CLOCKWISE_180, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.CLOCKWISE_180, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: the wall's middle is " + spot);
		helper.assertFalse(Reach.claims(level, a, origin), "the setup: the wall's corner is in village A");
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.nameUUIDFromBytes("qa-overlap-1102".getBytes(StandardCharsets.UTF_8)));
		recorded[0] = placement;

		helper.assertTrue(Sieges.walled(level, b), "a Stone Wall inside hall B's radius doesn't make village B walled");
		helper.assertTrue(VillageRanks.score(level, b, 0).buildings() == bBefore + 1, "the wall didn't count for village B's rank");
		helper.assertFalse(Sieges.walled(level, a),
			"a Stone Wall in a cell nearer hall B (inside B's radius) also makes village A walled: one building counts for both villages");
		helper.assertTrue(VillageRanks.score(level, a, 0).buildings() == aBefore,
			"the wall in B's cell also counted for village A's rank: " + aBefore + " -> " + VillageRanks.score(level, a, 0).buildings());
		helper.succeed();
	}
}
