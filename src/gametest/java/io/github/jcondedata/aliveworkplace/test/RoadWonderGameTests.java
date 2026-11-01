package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B753: the Steward's roads keep off a Wonder's ground like any building's. A Wonder begun and laid out but waiting for
 * stage I's goods has no build sites, and a finished one's parts aren't finished buildings (B143), so road routing
 * read neither; it now reads every laid-out Wonder from its own record. Each test checks the street goes straight once
 * the Wonder is forgotten, so the footprints aren't kept stale.
 */
public class RoadWonderGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The Test Folly's stage I, 9 by 9, laid out at x 10..18, z 4..12: across the street's straight line at z 8. */
	private static final BlockPos WONDER_ORIGIN = new BlockPos(10, 2, 4);
	private static final int FOLLY_SIZE = 9;

	/** A street from x 2 to x 27 along z 8, routed now; its nodes in level coordinates. */
	private static List<BlockPos> route(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = QaRoadGameTests.hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(QaRoadGameTests.road(CityPlan.Road.STREET, "stonework",
			QaRoadGameTests.p(helper, hall, 2, 8), QaRoadGameTests.p(helper, hall, 27, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed(), "the street wasn't routed");
		List<BlockPos> nodes = QaRoadGameTests.route(street, hall);
		helper.assertTrue(!nodes.isEmpty() && nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(27, 2, 8))) <= 2,
			"the street stopped short: " + nodes);
		return nodes;
	}

	/** Lays the folly out over the street's line for {@code hall}; finished when asked. */
	private static WonderSite folly(GameTestHelper helper, BlockPos hall, boolean finished) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(site.id()));
		BlockPos origin = helper.absolutePos(WONDER_ORIGIN);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		if (finished) {
			manager.finish(site.id(), 2);
		}
		WonderSite now = manager.site(site.id()).orElseThrow();
		helper.assertTrue(finished ? now.done() : (now.goingUp() && !now.started() && now.parts().isEmpty()),
			"set-up: the Wonder isn't " + (finished ? "finished" : "laid out and waiting for its goods, with no build sites"));
		return now;
	}

	/** The street (3 wide) keeps off the folly's stage I footprint. */
	private static void clearOf(GameTestHelper helper, List<BlockPos> nodes, String what) {
		BlockPos o = helper.absolutePos(WONDER_ORIGIN);
		BoundingBox box = new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + FOLLY_SIZE - 1, o.getY() + 6, o.getZ() + FOLLY_SIZE - 1);
		for (BlockPos n : nodes) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = n.getX() + dx;
					int z = n.getZ() + dz;
					helper.assertTrue(!(x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()),
						"the street runs over the " + what + " Test Folly at " + x + ", " + z + " (" + box + ")");
				}
			}
		}
	}

	private static void straight(GameTestHelper helper, List<BlockPos> nodes) {
		int z = helper.absolutePos(new BlockPos(0, 2, 8)).getZ();
		helper.assertTrue(nodes.stream().allMatch(n -> n.getZ() == z), "with the Wonder forgotten the street still goes round its ground: " + nodes);
	}

	/** A Wonder laid out and waiting for stage I's bread across the street's line: the street goes round its ground. */
	//$ gametest_ticks_batch AREA '40' '"roadWonderWaiting"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadWonderWaiting")
	public void aRoadGoesRoundAWonderWaitingForItsGoods(GameTestHelper helper) {
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		WonderSite site = folly(helper, hall, false);
		clearOf(helper, route(helper, hall), "waiting");
		WonderManager.get(helper.getLevel()).remove(site.id());
		straight(helper, route(helper, hall));
		helper.succeed();
	}

	/** A finished Wonder across the street's line: the street goes round it. */
	//$ gametest_ticks_batch AREA '40' '"roadWonderDone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadWonderDone")
	public void aRoadGoesRoundAFinishedWonder(GameTestHelper helper) {
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		WonderSite site = folly(helper, hall, true);
		clearOf(helper, route(helper, hall), "finished");
		WonderManager.get(helper.getLevel()).remove(site.id());
		straight(helper, route(helper, hall));
		helper.succeed();
	}
}
