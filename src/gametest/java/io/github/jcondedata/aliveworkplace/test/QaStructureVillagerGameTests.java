package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane (qa-1003-1333): adversarial tests for the bug fix B10, written from their specs.
 */
public class QaStructureVillagerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * B10's fix centres zombie villagers that come with a structure template. Zombie villagers that arrive any other way
	 * (a spawn egg, a villager turned by a zombie) must keep the exact spot they were put at, as in vanilla.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void b10ASpawnEggZombieVillagerKeepsItsSpot(GameTestHelper helper) {
		Vec3 spot = helper.absoluteVec(new Vec3(4.72, 2, 4.63));
		ZombieVillager zombie = EntityType.ZOMBIE_VILLAGER.create(helper.getLevel());
		zombie.moveTo(spot.x, spot.y, spot.z, 0, 0);
		zombie.setNoAi(true);
		zombie.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.SPAWN_EGG, null);
		helper.getLevel().addFreshEntity(zombie);
		helper.assertTrue(Math.abs(zombie.getX() - spot.x) < 1e-6 && Math.abs(zombie.getZ() - spot.z) < 1e-6,
			String.format("a spawn-egg zombie villager was moved from %.2f,%.2f to %.2f,%.2f", spot.x, spot.z, zombie.getX(), zombie.getZ()));
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void b10AVillagerTurnedByAZombieKeepsItsSpot(GameTestHelper helper) {
		Vec3 spot = helper.absoluteVec(new Vec3(4.72, 2, 4.63));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		villager.setNoAi(true);
		villager.moveTo(spot.x, spot.y, spot.z, 0, 0);
		// As Zombie.killedEntity turns a villager: convertTo, then finalizeSpawn as a CONVERSION.
		ZombieVillager zombie = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
		helper.assertTrue(zombie != null, "setup: the villager didn't turn");
		zombie.setNoAi(true);
		zombie.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.CONVERSION, null);
		helper.assertTrue(Math.abs(zombie.getX() - spot.x) < 1e-6 && Math.abs(zombie.getZ() - spot.z) < 1e-6,
			String.format("a villager turned into a zombie villager was moved from %.2f,%.2f to %.2f,%.2f", spot.x, spot.z, zombie.getX(), zombie.getZ()));
		helper.succeed();
	}

	/** B10 in open space: a zombie villager a structure puts at the off-centre spot stands in the middle of its block. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void b10AStructureZombieVillagerInTheOpenStandsInItsBlock(GameTestHelper helper) {
		Vec3 spot = helper.absoluteVec(new Vec3(4.72, 2, 4.63));
		ZombieVillager zombie = EntityType.ZOMBIE_VILLAGER.create(helper.getLevel());
		zombie.moveTo(spot.x, spot.y, spot.z, 0, 0);
		zombie.setNoAi(true);
		zombie.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.STRUCTURE, null);
		helper.getLevel().addFreshEntity(zombie);
		Vec3 centre = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		helper.assertTrue(Math.abs(zombie.getX() - centre.x) < 1e-6 && Math.abs(zombie.getZ() - centre.z) < 1e-6
				&& zombie.blockPosition().equals(helper.absolutePos(new BlockPos(4, 2, 4))),
			String.format("a structure zombie villager stands at %.2f,%.2f, not its block's centre %.2f,%.2f", zombie.getX(), zombie.getZ(), centre.x, centre.z));
		helper.succeed();
	}
}
