package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane E, 2026-11-04: the two cases of {@link QaMechanicPartStruck1104eCompatTests} that fail on main (bug filed
 * with them): a Mechanic struck into a witch, or killed, with the cogwheel he took from the store in his hand, and the
 * cogwheel vanishes with him. Register this class with the fix.
 */
public class QaMechanicPartStruck1104eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Struck into a witch holding the cogwheel from the store: the 2 cogwheels are still in the store or on the ground. */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1104_mechanic_struck"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1104_mechanic_struck")
	public void aMechanicStruckWithACogwheelInHandLeavesIt(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		boolean[] struck = new boolean[1];
		helper.onEachTick(() -> {
			if (!struck[0] && s.mechanic().getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				struck[0] = true;
				QaMechanicPartStruck1104eCompatTests.strike(helper.getLevel(), s.mechanic());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struck[0], "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(s.mechanic().isRemoved(), "lightning didn't turn the Mechanic into a witch (difficulty "
				+ helper.getLevel().getDifficulty() + ")");
			int store = MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog());
			int ground = QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
			helper.assertTrue(store + ground == 2, "of the store's 2 cogwheels only " + (store + ground) + " are left (store " + store
				+ ", ground " + ground + "): the one the Mechanic carried vanished with him when lightning turned him into a witch");
		});
	}

	/** Killed holding the cogwheel from the store (not by a player): the cogwheel is still in the store or on the ground. */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1104_mechanic_killed"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1104_mechanic_killed")
	public void aMechanicKilledWithACogwheelInHandLeavesIt(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		boolean[] killed = new boolean[1];
		helper.onEachTick(() -> {
			if (!killed[0] && s.mechanic().getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				killed[0] = true;
				s.mechanic().hurt(helper.getLevel().damageSources().generic(), 1000f);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0], "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(!s.mechanic().isAlive(), "the Mechanic survived");
			int store = MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog());
			int ground = QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
			helper.assertTrue(store + ground == 2, "of the store's 2 cogwheels only " + (store + ground) + " are left (store " + store
				+ ", ground " + ground + "): the one the Mechanic carried vanished when he died");
		});
	}

}
