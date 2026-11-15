package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-15, third run), failing in the base game alone: a recipe slot that takes any log, with four logs of
 * four woods in the chest, reads "3 Oak Logs" short: every slot picks the first wood that's there. crafter.md (B409):
 * "The line names what the chest is short of"; the smoker's recipe takes any 4 logs, so nothing is. Moves to main with the fix.
 */
public class QaLogsSlot1115e3BugGameTests implements FabricGameTest {

	/** Four logs of four woods, or stripped logs and wood there: nothing is short (the tag takes them all). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anyFourLogsOrWoodMakeIt(GameTestHelper helper) {
		Map<Item, Integer> mixed = QaLogsSlot1115e3GameTests.smoker(helper, Map.of(Items.COBBLESTONE, 8L, Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L,
			Items.CHERRY_LOG, 1L));
		Map<Item, Integer> stripped = QaLogsSlot1115e3GameTests.smoker(helper, Map.of(Items.COBBLESTONE, 8L, Items.STRIPPED_OAK_LOG, 2L, Items.DARK_OAK_WOOD, 2L));
		Crafting.Plan made = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SMOKER, 1, Map.of(Items.COBBLESTONE, 8L, Items.OAK_LOG, 1L,
			Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L, Items.CHERRY_LOG, 1L));
		String wrong = (mixed.isEmpty() ? "" : "four logs of four woods: a smoker lacks " + mixed + "; ")
			+ (made != null ? "" : "four logs of four woods: the Carpenter's plan can't make the smoker; ")
			+ (stripped.isEmpty() ? "" : "2 stripped oak logs and 2 dark oak wood: a smoker lacks " + stripped);
		helper.assertTrue(wrong.isEmpty(), wrong);
		helper.succeed();
	}

	/** The same with planks: a chest from 4 oak and 4 spruce planks; nothing is short and the plan makes it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromTwoWoodsPlanksIsMade(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_PLANKS, 4L, Items.SPRUCE_PLANKS, 4L);
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		Crafting.Plan made = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		helper.assertTrue(lack.isEmpty() && made != null, "a chest from 4 oak and 4 spruce planks lacks " + lack + ", plan " + (made == null ? "none" : "made"));
		helper.succeed();
	}

}
