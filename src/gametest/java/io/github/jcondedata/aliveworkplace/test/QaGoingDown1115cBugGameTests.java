package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaGoingDown1115cGameTests.check;
import static io.github.jcondedata.aliveworkplace.test.QaGoingDown1115cGameTests.lack;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-15), failing in the plain suite (no Create), so B1278 is not Create's: crafter.md (B409) "The line
 * names what the chest is short of, not some other way to make it ... It goes further down only when that uses something
 * that's there". With none of an ingredient and none of what its other recipe uses, the planner still goes down and names
 * the deeper input: rabbit hide for leather, string for white wool. Not registered; moves to QaGoingDown1115cGameTests
 * (and fabric.mod.json) with the fix. Helpers: QaGoingDown1115cGameTests.
 */
public class QaGoingDown1115cBugGameTests implements FabricGameTest {
	private static final String AREA = QaGoingDown1115cGameTests.AREA;

	/**
	 * With no leather and no rabbit hide, a book (3 paper there), an item frame (8 sticks there) and a bookshelf (cane and
	 * planks there) lack Leather, never Rabbit Hide: no hide is there. Failing when written: {rabbit_hide=4} and
	 * {rabbit_hide=36}.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cBugRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cBugRecipes")
	public void withNoLeatherTheRecipesLackLeatherNotRabbitHide(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a book from 3 paper", lack(helper, Crafting.Kind.CRAFTING, Items.BOOK, 1, Items.PAPER, 3), Map.of(Items.LEATHER, 1));
		check(wrong, "an item frame from 8 sticks", lack(helper, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Items.STICK, 8), Map.of(Items.LEATHER, 1));
		check(wrong, "3 bookshelves from 27 cane and 18 planks", lack(helper, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, Items.SUGAR_CANE, 27, Items.OAK_PLANKS, 18),
			Map.of(Items.LEATHER, 9));
		helper.assertTrue(wrong.isEmpty(), "the wrong thing is named short: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * A white banner from its stick with no wool and no string lacks 6 White Wool (wool is shorn), never String. Failing when
	 * written: {string=24}.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cBugRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cBugRecipes")
	public void aBannerFromNothingLacksWoolNotString(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a white banner from 1 stick", lack(helper, Crafting.Kind.CRAFTING, Items.WHITE_BANNER, 1, Items.STICK, 1), Map.of(Items.WHITE_WOOL, 6));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * A Librarian and three bookshelves with 27 sugar cane and 18 planks but no leather and no rabbit hide: the builder's
	 * line reads "Can't be made yet: short of 9 Leather to make 3 Bookshelves" (never Rabbit Hide), and nothing is taken.
	 * Failing when written: "short of 36 Rabbit Hides to make 3 Bookshelves".
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaGoingDown1115cShelvesNoLeather"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaGoingDown1115cShelvesNoLeather")
	public void bookshelvesWithNoLeatherNameNineLeather(GameTestHelper helper) {
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.OAK_PLANKS, 18)};
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, items);
		QaJobChainsGameTests.librarian(helper);
		helper.runAfterDelay(1400, () -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String got = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 9 Leather to make 3 Bookshelves".equals(got),
				"the builder's line: '" + got + "', want 'Can't be made yet: short of 9 Leather to make 3 Bookshelves' (no rabbit hide is there)");
			QaGoingDown1115cGameTests.assertUntouched(helper, a.chest(), items);
			helper.succeed();
		});
	}
}
