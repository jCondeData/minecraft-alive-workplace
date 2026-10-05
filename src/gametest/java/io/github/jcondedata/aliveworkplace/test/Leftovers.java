package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Batches reuse the same spots, and only what stands inside a test's area is cleared between them: a villager or mob
 * that wandered outside (a guard backing away from a creeper, a builder hopping about) is still there when the next
 * batch starts, and walks into it. A leftover guard whose post was at the same spot took the new guard's sword; a
 * leftover villager can take a workstation. Tests that run alone in their batch clear everything around them first.
 */
final class Leftovers {
	/** How far around the test area to clear: more than the reach of any worker looking for its workstation. */
	private static final double RADIUS = 48;

	/** Removes every entity but players near the test area. Only for tests alone in their batch. */
	static void clear(GameTestHelper helper) {
		AABB around = helper.getBounds().inflate(RADIUS);
		for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, around, e -> !(e instanceof Player))) {
			e.discard();
		}
	}

	/**
	 * Logs out every mock player near the test area. Many tests make one, teleport it into their area and never log it
	 * out, so it stands there into later batches at the same spot: a Legend guest who leaves only with nobody within 24
	 * blocks stayed because an earlier batch's player was standing by the hall. Only for tests alone in their batch
	 * (batches run one after another, so no other test's player is still in use).
	 */
	static void players(GameTestHelper helper) {
		AABB around = helper.getBounds().inflate(RADIUS);
		var list = helper.getLevel().getServer().getPlayerList();
		for (net.minecraft.server.level.ServerPlayer p : java.util.List.copyOf(helper.getLevel().players())) {
			if (around.contains(p.position())) {
				list.remove(p);
			}
		}
	}

	/**
	 * Removes every Village Hall whose village reaches the test area. A hall another batch's test left nearby (outside
	 * its own area, so never cleared) makes the villagers here its village, with its needs' pace and its edicts: a
	 * well-kept hall left by the Steward's homes tests sped up legendPace's builder past its own numbers. Only for tests
	 * alone in their batch.
	 */
	static void halls(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + (int) Math.ceil(Math.max(box.getXsize(), box.getZsize()) / 2);
		java.util.List<net.minecraft.core.BlockPos> halls = level.getPoiManager().findAll(
			h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.VILLAGE_HALL_POI), p -> true,
			net.minecraft.core.BlockPos.containing(box.getCenter()), reach, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList();
		for (net.minecraft.core.BlockPos hall : halls) {
			level.removeBlock(hall, false);
		}
		io.github.jcondedata.aliveworkplace.hall.VillageNeeds.forget();
	}

	/**
	 * Turns village sharing on for this test ({@code Village.RADIUS}, normally off in tests) and off again when the test
	 * ends, passed or failed — a failed test that left it on made the next batches' builders share chests and fail too.
	 */
	static void village(GameTestHelper helper, int radius) {
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius;
		after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0);
	}

	/** Runs {@code reset} when the test ends, passed or failed (a setting the test changed for itself). */
	static void after(GameTestHelper helper, Runnable reset) {
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			net.minecraft.gametest.framework.GameTestInfo info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
			info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
				@Override
				public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {
				}

				@Override
				public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old, net.minecraft.gametest.framework.GameTestInfo test,
						net.minecraft.gametest.framework.GameTestRunner runner) {
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private Leftovers() {
	}
}
