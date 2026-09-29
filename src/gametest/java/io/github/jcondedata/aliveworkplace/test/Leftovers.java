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
