package io.github.jcondedata.aliveworkplace.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Villagers (and zombie villagers) that come with a structure template stand in their own block (bugs B6 and B10).
 *
 * <p>Vanilla's village "villagers" templates store their villager at x+0.72, z+0.63 of its block, off its centre, and a
 * block above the house floor. A villager is 0.6 wide, so its box reaches 0.02 into the next block over. Where a house
 * gives it a 1-wide gap (vanilla's desert_small_house_7 corridor, for one) it falls onto the wall or step beside the gap
 * instead of the floor, with its head in the ceiling slab, and suffocates. Centred in its block, its whole box stays
 * over the template's own air column, so it drops onto the floor like everywhere else.
 *
 * <p>The only villager left where the template put it is one whose spot is free while the centre is not (a fence post
 * in the middle of its block, say). Abandoned (zombie) villages store their zombie villagers the same way (B10).
 */
public final class StructureVillagers {
	private StructureVillagers() {
	}

	/** Called as a villager or zombie villager is finalised; acts only on those placed by a structure template. */
	public static void settle(Mob villager, ServerLevelAccessor level, MobSpawnType type) {
		if (type != MobSpawnType.STRUCTURE) {
			return;
		}
		double x = villager.getBlockX() + 0.5;
		double z = villager.getBlockZ() + 0.5;
		AABB centred = villager.getBoundingBox().move(x - villager.getX(), 0, z - villager.getZ());
		if (level.noCollision(villager, villager.getBoundingBox()) && !level.noCollision(villager, centred)) {
			return;
		}
		villager.moveTo(x, villager.getY(), z, villager.getYRot(), villager.getXRot());
	}
}
