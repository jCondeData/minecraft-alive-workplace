package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1278, B1277 (crafter.md, B409: "The line names what the chest is short of, not some other way to make it ... It goes
 * further down only when that uses something that's there"): with no leather at all a book, an item frame or bookshelves
 * name Leather, not the Rabbit Hide nobody has; with hide there it still goes down to it. A piston from nothing names
 * Redstone Dust, never the Redstone Ore a player only gets with Silk Touch (B489's kind).
 */
public class ShortLineGatheredGameTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));

	/** No leather at all: a book from 3 paper and an item frame from 8 sticks each lack 1 Leather, not 4 Rabbit Hide. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withNoLeatherABookOrFrameLacksLeather(GameTestHelper helper) {
		Map<Item, Integer> book = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L));
		Map<Item, Integer> frame = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of(Items.STICK, 8L));
		helper.assertTrue(book.equals(Map.of(Items.LEATHER, 1)), "a book from 3 paper lacks " + book + ", expected {leather=1}");
		helper.assertTrue(frame.equals(Map.of(Items.LEATHER, 1)), "an item frame from 8 sticks lacks " + frame + ", expected {leather=1}");
		helper.succeed();
	}

	/** Two rabbit hides there: going down uses something that's there, so a book lacks the 2 more hides (B409 still holds). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withSomeRabbitHideABookLacksTheRestOfTheHide(GameTestHelper helper) {
		Map<Item, Integer> book = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 2L));
		helper.assertTrue(book.equals(Map.of(Items.RABBIT_HIDE, 2)), "a book from 3 paper and 2 rabbit hides lacks " + book + ", expected {rabbit_hide=2}");
		helper.succeed();
	}

	/** Leather is still the Leatherworker's own thing to make from hide: a leather asked for itself lacks the 4 Rabbit Hides. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leatherAskedForItselfStillLacksRabbitHide(GameTestHelper helper) {
		Map<Item, Integer> leather = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.LEATHER, 1, Map.of());
		helper.assertTrue(leather.equals(Map.of(Items.RABBIT_HIDE, 4)), "a leather from nothing lacks " + leather + ", expected {rabbit_hide=4}");
		helper.succeed();
	}

	/** A piston (and a sticky piston) at the forge from nothing names Redstone Dust and no ore at all. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPistonFromNothingNamesRedstoneNotItsOre(GameTestHelper helper) {
		for (Item item : List.of(Items.PISTON, Items.STICKY_PISTON)) {
			Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.WORKSHOP, item, 1, Map.of());
			helper.assertTrue(lack.containsKey(Items.REDSTONE), item + " from nothing lacks " + lack + ", expected Redstone Dust named");
			helper.assertTrue(lack.keySet().stream().noneMatch(i -> i.builtInRegistryHolder().is(ORES)), item + " from nothing names an ore: " + lack);
		}
		helper.succeed();
	}

	/** Through the player's entry point: a Librarian, three bookshelves, the cane and planks and no leather: "9 Leather". */
	//$ gametest_ticks_batch AREA '1600' '"shortLineGatheredShelves"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "shortLineGatheredShelves")
	public void aLibrarianWithNoLeatherSaysNineLeather(GameTestHelper helper) {
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.OAK_PLANKS, 18)};
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, items);
		QaJobChainsGameTests.librarian(helper);
		helper.succeedWhen(() -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String got = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 9 Leather to make 3 Bookshelves".equals(got), "the builder's shortage line: " + got
				+ " (want 'Can't be made yet: short of 9 Leather to make 3 Bookshelves')");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 27 && chest.countItem(Items.OAK_PLANKS) == 18, "the chest was touched");
		});
	}
}
