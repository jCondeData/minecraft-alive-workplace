package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.realm.RealmData;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;

/** QA (nightly mutation 2026-10-11): when a realm order counts as gone, which is when it can't be called off any more. */
public class RealmOrderStateQaGameTests implements FabricGameTest {

	/**
	 * Spec (33.9/33.10): the settlers have left the village in leaving, on the road, arriving and returning, so the
	 * order can't be called off; while gathering or at the muster they are still in the village, so it can.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anOrderIsGoneOnceTheSettlersHaveLeftTheVillage(GameTestHelper helper) {
		GlobalPos mother = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
		for (String state : new String[] {RealmData.LEAVING, RealmData.ON_ROAD, RealmData.ARRIVING, RealmData.RETURNING}) {
			RealmData.Order order = new RealmData.Order(mother, BlockPos.ZERO, Optional.empty(), state, 0, 0, 100);
			helper.assertTrue(order.gone(), state + " should be gone (the settlers have left)");
		}
		for (String state : new String[] {RealmData.GATHERING, RealmData.MUSTER}) {
			RealmData.Order order = new RealmData.Order(mother, BlockPos.ZERO, Optional.empty(), state, 0, 0, 100);
			helper.assertTrue(!order.gone(), state + " should not be gone (the settlers are still in the village)");
		}
		helper.succeed();
	}
}
