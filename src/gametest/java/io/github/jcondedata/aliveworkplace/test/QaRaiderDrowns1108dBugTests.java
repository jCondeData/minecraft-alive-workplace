package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Found by lane D (2026-11-08): a raider husk (the desert raiders bring them) that falls into a village's moat turns
 * into a zombie after vanilla's 45 seconds under water, and the zombie is no raider any more. Vanilla's
 * {@code Mob.convertTo} copies the name, the gear and "never despawn", but not the entity's tags, so the new zombie
 * loses {@link Threats#RAIDER_TAG}: the raid counts its raiders by that tag ({@code VillageRaids.own}), so with it the
 * last raider the raid ends as beaten while the zombie still walks the village, it isn't sent away at dawn, and it
 * never despawns (it kept "persistence"). ThreatDamage no longer treats it as a threat either. Every threat tag
 * ({@link Threats#THREAT_TAGS}: lairs, bandit camps, bounties, rogues) is lost the same way; only piglins are kept from
 * zombifying (32.10). A raider zombie in water turns into a drowned the same way.
 * Expected: what a raider turns into is still that raid's raider.
 */
public class QaRaiderDrowns1108dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A raider husk held under water in a moat (a one-block well, three deep, glass over it) for vanilla's conversion
	 * time: the zombie it becomes must still be a raider.
	 */
	//$ gametest_ticks_batch AREA '1300' '"qaRaiderHuskDrowns1108d"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaRaiderHuskDrowns1108d")
	public void aRaiderHuskThatTurnsIntoAZombieInTheMoatIsStillARaider(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos well = new BlockPos(8, 2, 8);
		for (int y = 0; y < 4; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = well.offset(dx, y, dz);
					boolean inside = dx == 0 && dz == 0 && y < 3;
					helper.setBlock(p, inside ? Blocks.WATER : Blocks.GLASS);
				}
			}
		}
		Husk husk = helper.spawn(EntityType.HUSK, well);
		husk.setPersistenceRequired(); // as Threats.outfit makes a raider
		husk.addTag(Threats.RAIDER_TAG);
		helper.assertTrue(Threats.isThreatMob(husk), "setup: the husk isn't a raider");
		AABB in = new AABB(helper.absolutePos(well)).inflate(1.5);
		helper.succeedWhen(() -> {
			List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, in, z -> z.isAlive() && z.getType() == EntityType.ZOMBIE);
			helper.assertTrue(!zombies.isEmpty(), "(the husk hasn't turned into a zombie yet)");
			Zombie zombie = zombies.get(0);
			helper.assertTrue(zombie.isPersistenceRequired(), "setup: the zombie didn't keep 'never despawn' from the husk");
			helper.assertTrue(Threats.isThreatMob(zombie),
				"the raider husk turned into a zombie in the moat and the zombie is no raider: tags " + zombie.getTags()
					+ " (the raid ends as beaten while it walks the village, and it never leaves or despawns)");
		});
	}
}
