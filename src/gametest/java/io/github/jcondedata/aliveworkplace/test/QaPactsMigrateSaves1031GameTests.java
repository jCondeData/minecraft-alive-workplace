package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, 2026-10-31), B700 across an update: the corners of "pre-1.7 routes to another owner's village become
 * pacts once ... with gifts on for the sender" (docs/design/M33.md) that the B700 tests leave open: only the sending
 * side gives, a pact that was already there is left as it was, routes between one owner's own villages make no pact,
 * and an owner who turns the migrated gifts off keeps them off across a save and reload.
 */
public class QaPactsMigrateSaves1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A route only 0 to 1: one pact, the sender (0) gives, the receiver (1) doesn't, since it never sent anything. */
	//$ gametest_ticks_batch HUGE '100' '"qaMigrateOneWay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMigrateOneWay")
	public void aOneWayRouteGivesOnlyFromItsSender(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && !pact.alliance() && pact.endsAt() == -1, "the old route didn't become an open trade pact: " + pact);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)), "the sender's cargo isn't free after the update");
		helper.assertFalse(Pacts.gives(level, land.hall(1), land.hall(0)), "the receiver, who sent nothing before 1.7, gives its cargo away after the update");
		helper.assertTrue(caravans.routesFrom(land.hall(0)).contains(land.hall(1)), "the old route stopped");
		helper.assertFalse(caravans.routesFrom(land.hall(1)).contains(land.hall(0)), "the update made a route back");
		helper.succeed();
	}

	/** A pact made after 1.7 (gifts off both ways) whose routes run both ways is left exactly as it was by the migration. */
	//$ gametest_ticks_batch HUGE '100' '"qaMigrateKeepsPact"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMigrateKeepsPact")
	public void aPactFromBeforeTheMigrationIsLeftAsItWas(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Pacts.Pact old = new Pacts.Pact(land.at(0), land.at(1), false, 1234L, -1, false, false);
		Pacts.Data.get(server).put(old);
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)) && caravans.toggleRoute(land.hall(1), land.hall(0)), "setup: routes");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(old.equals(pact), "the migration changed a pact that was already there: was " + old + ", now " + pact);
		helper.assertFalse(Pacts.gives(level, land.hall(0), land.hall(1)) || Pacts.gives(level, land.hall(1), land.hall(0)), "the migration turned gifts on for a pact whose owners had them off");
		helper.succeed();
	}

	/** Routes between one owner's own villages (0 and 2) make no pact; his route to the neighbour (0 to 1) does. */
	//$ gametest_ticks_batch HUGE '100' '"qaMigrateSameOwner"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMigrateSameOwner")
	public void routesBetweenOneOwnersVillagesMakeNoPact(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(2)) && caravans.toggleRoute(land.hall(2), land.hall(0)), "setup: routes between his own villages");
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route to the neighbour");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(2)) == null, "a pact between one owner's own villages: " + Pacts.Data.get(server).pact(land.at(0), land.at(2)));
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) != null, "no pact for the route to the neighbour");
		helper.assertTrue(caravans.routesFrom(land.hall(0)).contains(land.hall(2)) && caravans.routesFrom(land.hall(2)).contains(land.hall(0)), "routes between his own villages stopped");
		helper.succeed();
	}

	/**
	 * The owner turns the migrated gifts off; the pacts are saved and loaded (a restart): his gifts stay off, the
	 * migration doesn't run again, and its once-only mark is saved.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaMigrateGiftsOffSaved"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaMigrateGiftsOffSaved")
	public void giftsTurnedOffAfterTheMigrationStayOffAfterARestart(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		HolderLookup.Provider registries = server.registryAccess();
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)), "setup: the migration gave gifts on");
		helper.assertFalse(Pacts.toggleGifts(level, land.hall(0), land.at(1), land.owner()), "the owner's click didn't turn his gifts off");
		CompoundTag saved = Pacts.Data.get(server).save(new CompoundTag(), registries);
		helper.assertTrue(saved.getBoolean("migrated"), "the migration's once-only mark isn't saved: " + saved);
		server.overworld().getDataStorage().set("aliveworkplace_pacts", Pacts.Data.load(saved, registries));
		helper.runAfterDelay(45, () -> {
			Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
			helper.assertTrue(pact != null, "the pact is gone after the restart");
			helper.assertFalse(Pacts.gives(level, land.hall(0), land.hall(1)), "the owner turned his gifts off, but they were on again after a restart: " + pact);
			helper.succeed();
		});
	}
}
