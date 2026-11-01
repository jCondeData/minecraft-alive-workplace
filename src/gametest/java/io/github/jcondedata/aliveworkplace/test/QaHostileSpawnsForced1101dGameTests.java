package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1101-1310), from B208/B211/B709's specs: "a refused mob is placed directly while forceThreatSpawns (or
 * forceVillageSpawns) is on", and when "the world refused it again when placed directly" the spawn reports it isn't in
 * the world and the log says so (kills the survivors HostileSpawns:143, :144 and :197 of a mutation spot check). The
 * world refuses an entity a second time when one with its UUID is already in it, as after a duplicated save entry.
 */
public class QaHostileSpawnsForced1101dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A camp's captain whose UUID is a villager's: refused, refused again when forced, reported not in the world. */
	//$ gametest_ticks_batch AREA '40' '"qaForcedTwiceRefused1101d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaForcedTwiceRefused1101d")
	public void aFoeTheWorldRefusesEvenWhenForcedIsReportedNotInTheWorld(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean force = HostileSpawns.FORCE;
		try {
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			Villager holder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			holder.setNoAi(true);
			Vindicator captain = EntityType.VINDICATOR.create(level);
			captain.setUUID(holder.getUUID());
			captain.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
			boolean in = HostileSpawns.add(level, captain, "QA camp");
			helper.assertTrue(!in, "expected the spawn to report the captain not in the world (its UUID is taken), it reported it in");
			helper.assertTrue(level.getEntity(holder.getUUID()) == holder, "the villager holding the UUID was replaced: " + level.getEntity(holder.getUUID()));
			helper.assertTrue(level.getEntitiesOfClass(Vindicator.class, helper.getBounds().inflate(4), v -> true).isEmpty(),
				"expected no captain standing in the world");
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Could not spawn minecraft:vindicator for the QA camp") && said.contains("the world refused it again when placed directly"),
				"expected the log to say the world refused the captain again when placed directly, it said: '" + said + "'");
			helper.succeed();
		} finally {
			HostileSpawns.FORCE = force;
			HostileSpawns.forget();
		}
	}

	/** The same for a village's golem (forceVillageSpawns): reported not in the world, and said why. */
	//$ gametest_ticks_batch AREA '40' '"qaForcedTwiceRefusedGolem1101d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaForcedTwiceRefusedGolem1101d")
	public void aVillageGolemTheWorldRefusesEvenWhenForcedIsReportedNotInTheWorld(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean force = HostileSpawns.FORCE_VILLAGE;
		try {
			HostileSpawns.FORCE_VILLAGE = true;
			HostileSpawns.forget();
			Villager holder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			holder.setNoAi(true);
			IronGolem golem = EntityType.IRON_GOLEM.create(level);
			golem.setUUID(holder.getUUID());
			golem.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
			boolean in = HostileSpawns.addVillage(level, golem, "QA smith's golem");
			helper.assertTrue(!in, "expected the spawn to report the golem not in the world (its UUID is taken), it reported it in");
			helper.assertTrue(level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(4), g -> true).isEmpty(),
				"expected no golem standing in the world");
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Could not spawn minecraft:iron_golem for the QA smith's golem") && said.contains("the world refused it again when placed directly"),
				"expected the log to say the world refused the golem again when placed directly, it said: '" + said + "'");
			helper.succeed();
		} finally {
			HostileSpawns.FORCE_VILLAGE = force;
			HostileSpawns.forget();
		}
	}
}
