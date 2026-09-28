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

	private Leftovers() {
	}
}
