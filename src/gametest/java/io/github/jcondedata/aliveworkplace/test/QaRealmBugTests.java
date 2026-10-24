package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * QA tests (lane-c-1024-0353) for bugs in the settlers' setting out (33.9), each committed with its fix (B357).
 */
public class QaRealmBugTests implements FabricGameTest {
	private static final String AREA = QaRealmGameTests.AREA;

	/**
	 * 33.9: "Right-click the hall with the charter, its spot chosen". With the charter in the OFF hand and the main hand
	 * empty, the owner's right-click opens the hall's page and sends nobody (as B338's insignia did); the off hand
	 * should send the settlers as the main hand does.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmBugOffHand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmBugOffHand")
	public void theCharterInTheOffHandSendsTheSettlers(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		v.owner().setItemInHand(InteractionHand.OFF_HAND, QaRealmGameTests.charter(v));
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		helper.assertTrue(v.order() != null, "the off hand's charter sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ", menu "
			+ v.owner().containerMenu.getClass().getSimpleName() + ")");
		helper.assertTrue(v.owner().getOffhandItem().isEmpty(), "the charter stayed in the off hand");
		helper.succeed();
	}

	/**
	 * The same with the charter in the main hand on a sneak-click: vanilla skips the hall's own use for a sneaking
	 * player holding anything (B336), the charter's ground click passes on the hall, and its air click opens the map,
	 * so nobody is sent. Expected: the sneak-click on the hall sends the settlers too.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmBugSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmBugSneak")
	public void aSneakClickOnTheHallWithTheCharterSendsTheSettlers(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, QaRealmGameTests.charter(v));
		v.owner().setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		v.owner().setShiftKeyDown(false);
		helper.assertTrue(v.order() != null, "a sneak-click on the hall sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ")");
		helper.assertTrue(!v.owner().getMainHandItem().is(ModItems.COLONY_CHARTER), "the charter stayed in hand");
		helper.succeed();
	}

}
