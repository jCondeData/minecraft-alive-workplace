package io.github.jcondedata.aliveworkplace.compat;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;

/**
 * QA lane E, 2026-11-04 (Create, the Mechanic 39.2, B959 kin): MechanicWork's header promises "a part in hand when the
 * work stops goes back to the store". A zombie that kills the Mechanic on Normal or Hard turns him into a zombie
 * villager: vanilla's Zombie.killedEntity converts him without his equipment (convertTo(..., false)) and drops no death
 * loot, so the store's cogwheel in his hand is simply gone (seen on main: store 1, ground 0, the zombie holds 0). A fix
 * for B959 that only raises the hand's drop chance passes B959's own "killed" test but not this one. Expected: the
 * cogwheel is back in the store or on the ground and the zombie villager holds none (exactly 2, never 3). Fails on main:
 * register with the fix (tests/mechanic-struck-1104).
 */
public class QaCreateZombified1104e2BugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Killed by a zombie on Hard holding the store's cogwheel: 2 cogwheels in store and on the ground, none in the zombie's hand. */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1104_mechanic_zombified"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1104_mechanic_zombified")
	public void aMechanicKilledByAZombieWithACogwheelInHandLeavesItForTheStore(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		ZombieVillager[] zombie = new ZombieVillager[1];
		helper.onEachTick(() -> {
			if (zombie[0] == null && s.mechanic().getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				zombie[0] = QaCreateZombified1104e2CompatTests.zombify(helper, s.mechanic());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie[0] != null, "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(helper.getTick() >= 5, "waiting");
			int store = MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog());
			int ground = QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
			int held = 0;
			for (ItemStack stack : List.of(zombie[0].getMainHandItem(), zombie[0].getOffhandItem())) {
				held += stack.is(QaMechanicPartStruck1104eCompatTests.cog()) ? stack.getCount() : 0;
			}
			helper.assertTrue(store + ground == 2 && held == 0, "of the store's 2 cogwheels " + (store + ground) + " are in the store or on the ground (store "
				+ store + ", ground " + ground + ") and the zombie villager the Mechanic became holds " + held);
			zombie[0].discard();
		});
	}
}
