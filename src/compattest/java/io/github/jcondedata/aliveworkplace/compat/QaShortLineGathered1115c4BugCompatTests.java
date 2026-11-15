package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1115-1252), failing on main: with Chipped loaded, Chipped's planks (in the planks tag) that the chest does hold
 * are not used for a crafting table or a bookshelf; the line names Oak Logs nobody has instead. crafter.md: the line names
 * what the chest is short of, and goes further down only when that uses something that's there. Sticks and a torch from
 * the same planks are fine (QaShortLineGathered1115c4CompatTests). Not registered: moves to main with the fix.
 */
public class QaShortLineGathered1115c4BugCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/** Read off the table's recipes: a crafting table from 4 Chipped planks, a bookshelf from 6 and 3 books, lack nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chippedPlanksThereMakeACraftingTableAndABookshelf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item chipped = chippedPlanks();
		Map<String, String> wrong = new LinkedHashMap<>();
		Crafting.Kind k = Crafting.Kind.CRAFTING;
		QaCreateRivals1115cCompatTests.check(wrong, "a crafting table from 4 " + chipped, Crafting.lacking(level, k, Items.CRAFTING_TABLE, 1, Map.of(chipped, 4L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "a bookshelf from 6 " + chipped + " and 3 books", Crafting.lacking(level, k, Items.BOOKSHELF, 1,
			Map.of(chipped, 6L, Items.BOOK, 3L)), Map.of());
		helper.assertTrue(wrong.isEmpty(), "Chipped planks there not used: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of 1 bookshelf, 6 Chipped planks and 3 books there, a Librarian: he makes it. */
	//$ gametest_ticks_batch AREA '1600' '"qaShortLineChippedShelf1115c4"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortLineChippedShelf1115c4")
	public void aLibrarianMakesTheBookshelfFromChippedPlanks(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item chipped = chippedPlanks();
		ItemStack[] items = {new ItemStack(chipped, 6), new ItemStack(Items.BOOK, 3)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.BOOKSHELF, 1, 1, "qachipshelf4"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		QaCreateRivals1115cCompatTests.librarian(helper);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(QaCreateRivals1115cCompatTests.CHEST);
			helper.assertTrue(chest.countItem(chipped) == 0 && chest.countItem(Items.BOOK) == 0,
				"the Librarian never used the 6 Chipped planks and 3 books: the builder's line reads '" + QaCrafterOwnModdedStore1111eCompatTests.line(helper, a)
					+ "'; site " + QaCreateRivals1115cCompatTests.site(helper, a));
		});
	}

	private static Item chippedPlanks() {
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
				return h.value();
			}
		}
		throw new GameTestAssertException("setup: no Chipped planks in the planks tag");
	}
}
