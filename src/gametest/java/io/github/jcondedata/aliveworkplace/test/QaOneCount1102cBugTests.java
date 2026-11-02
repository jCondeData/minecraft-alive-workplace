package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (1102c) bugs: village sentences that read wrong at a count of one (the plural controls pass on main in
 * QaOneCount1102cGameTests): the hall's Food button with one kind of meal, its Mercenaries button with one mercenary left,
 * and the chronicle of a raid of one fought off.
 */
public class QaOneCount1102cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Bread only: one kind of meal; the line should say "1 kind", not "1 kinds". */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cOneMealKind"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cOneMealKind")
	public void qaOneKindOfMealReadsSingular(GameTestHelper helper) {
		QaOneCount1102cGameTests.mealKinds(helper, List.of(new ItemStack(Items.BREAD, 5)), "1 kind of meal in the store (3 or more for a varied diet)");
	}

	/** Two of the three fell: "1 mercenary is here until dawn". */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cOneMercenary"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cOneMercenary")
	public void qaOneMercenaryLeftReadsSingular(GameTestHelper helper) {
		QaOneCount1102cGameTests.mercenaries(helper, 2, "1 mercenary is here until dawn");
	}

	/** One zombie came and was slain: "(1 monster)". */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cRaidOfOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cRaidOfOne")
	public void qaRaidOfOneFoughtOffReadsSingular(GameTestHelper helper) {
		QaOneCount1102cGameTests.raidWon(helper, 1, "The village fought off the raid (1 monster)");
	}
}
