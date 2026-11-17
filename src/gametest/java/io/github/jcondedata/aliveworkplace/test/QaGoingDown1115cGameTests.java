package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-15), the plain suite (no Create) for B1278: crafter.md (B409) "The line names what the chest is
 * short of, not some other way to make it ... It goes further down only when that uses something that's there (planks
 * still to be sawn from the logs at hand read 'short of 1 Oak Log')". Read for vanilla chains whose ingredient has a
 * vanilla recipe of its own from something else: leather (from rabbit hide), white wool (from string), iron ingots (from
 * nuggets or an iron block), slimeballs (from a slime block) and wheat (from a hay bale). With none of either there, the
 * ingredient itself is named; with the other input there, the chain goes down and uses it.
 */
public class QaGoingDown1115cGameTests implements FreshPlanning {
	static final String AREA ="aliveworkplace_test:build_area";

	/** What {@code kind} lacks for {@code count} of {@code target} from {@code usable} (pairs of item and count), the zeros left out. */
	static Map<Item, Integer> lack(GameTestHelper helper, Crafting.Kind kind, Item target, int count, Object... itemsAndCounts) {
		Map<Item, Long> usable = new LinkedHashMap<>();
		for (int i = 0; i < itemsAndCounts.length; i += 2) {
			usable.put((Item) itemsAndCounts[i], ((Number) itemsAndCounts[i + 1]).longValue());
		}
		Map<Item, Integer> out = new LinkedHashMap<>();
		Crafting.lacking(helper.getLevel(), kind, target, count, usable).forEach((k, v) -> {
			if (v > 0) {
				out.put(k, v);
			}
		});
		return out;
	}

	/** Adds a line to {@code wrong} unless {@code got} is exactly {@code want}. */
	static void check(List<String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> want) {
		if (!got.equals(want)) {
			wrong.add(what + ": lacks " + names(got) + ", want " + names(want));
		}
	}

	private static String names(Map<Item, Integer> lack) {
		Map<String, Integer> out = new LinkedHashMap<>();
		lack.forEach((k, v) -> out.put(BuiltInRegistries.ITEM.getKey(k).getPath(), v));
		return out.toString();
	}

	// ---- 1. leather, read off the recipes (with no leather and no hide at all: QaGoingDown1115cBugGameTests, B1278) ----

	/**
	 * Rabbit hide there and no leather: the chain goes down and uses it. A book from 3 paper and 4 hide, an item frame from
	 * 8 sticks and 4 hide, and 3 bookshelves from 36 hide lack nothing; with too little hide (2 of a book's 4, 4 of three
	 * shelves' 36) the rest is named in hide, as the logs at hand are for planks still to be sawn (B409).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void rabbitHideThereIsUsedForTheLeather(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a book from 3 paper and 4 rabbit hide", lack(helper, Crafting.Kind.CRAFTING, Items.BOOK, 1, Items.PAPER, 3, Items.RABBIT_HIDE, 4), Map.of());
		check(wrong, "an item frame from 8 sticks and 4 rabbit hide", lack(helper, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Items.STICK, 8, Items.RABBIT_HIDE, 4), Map.of());
		check(wrong, "3 bookshelves from 27 cane, 18 planks and 36 rabbit hide", lack(helper, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3,
			Items.SUGAR_CANE, 27, Items.OAK_PLANKS, 18, Items.RABBIT_HIDE, 36), Map.of());
		check(wrong, "a book from 3 paper and 2 rabbit hide", lack(helper, Crafting.Kind.CRAFTING, Items.BOOK, 1, Items.PAPER, 3, Items.RABBIT_HIDE, 2),
			Map.of(Items.RABBIT_HIDE, 2));
		check(wrong, "3 bookshelves from 27 cane, 18 planks and 4 rabbit hide", lack(helper, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3,
			Items.SUGAR_CANE, 27, Items.OAK_PLANKS, 18, Items.RABBIT_HIDE, 4), Map.of(Items.RABBIT_HIDE, 32));
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 4L));
		if (plan == null) {
			wrong.add("no plan for a book from 3 paper and 4 rabbit hide");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- 2. other vanilla chains of the same shape ----

	/**
	 * White wool (a white banner's six, with its stick there): 24 string there are spun into the wool (nothing lacking); 12
	 * string go down and the rest is named in string. (From no wool and no string: QaGoingDown1115cBugGameTests.)
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void aBannersStringThereIsSpunIntoTheWool(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a white banner from 1 stick and 24 string", lack(helper, Crafting.Kind.CRAFTING, Items.WHITE_BANNER, 1, Items.STICK, 1, Items.STRING, 24), Map.of());
		check(wrong, "a white banner from 1 stick and 12 string", lack(helper, Crafting.Kind.CRAFTING, Items.WHITE_BANNER, 1, Items.STICK, 1, Items.STRING, 12),
			Map.of(Items.STRING, 12));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Iron ingots at the crafting table (a bucket's three): from nothing it lacks 3 Iron Ingots, never 27 Iron Nuggets; 27
	 * nuggets there, or an iron block, are made into the ingots (nothing lacking).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void aBucketsIngotsAreNamedUnlessNuggetsOrABlockAreThere(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a bucket from nothing", lack(helper, Crafting.Kind.CRAFTING, Items.BUCKET, 1), Map.of(Items.IRON_INGOT, 3));
		check(wrong, "a bucket from 27 nuggets", lack(helper, Crafting.Kind.CRAFTING, Items.BUCKET, 1, Items.IRON_NUGGET, 27), Map.of());
		check(wrong, "a bucket from an iron block", lack(helper, Crafting.Kind.CRAFTING, Items.BUCKET, 1, Items.IRON_BLOCK, 1), Map.of());
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The Tinkerer's forge: 16 iron bars from 54 iron nuggets, or from an iron block, lack nothing (the forge goes down to
	 * what's there); from nothing at all, 6 Raw Iron (crafter.md, B489), never nuggets.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void theForgeUsesNuggetsOrABlockThatAreThere(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "16 iron bars from 54 nuggets", lack(helper, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Items.IRON_NUGGET, 54), Map.of());
		check(wrong, "16 iron bars from an iron block", lack(helper, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Items.IRON_BLOCK, 1), Map.of());
		check(wrong, "16 iron bars from a coal", lack(helper, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Items.COAL, 1), Map.of(Items.RAW_IRON, 6));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * A sticky piston's slimeball (its piston there): from nothing it lacks 1 Slimeball, never a Slime Block; a slime block
	 * there is unpacked for it (nothing lacking).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void aStickyPistonsSlimeballIsNamedUnlessASlimeBlockIsThere(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "a sticky piston from a piston", lack(helper, Crafting.Kind.CRAFTING, Items.STICKY_PISTON, 1, Items.PISTON, 1), Map.of(Items.SLIME_BALL, 1));
		check(wrong, "a sticky piston from a piston and a slime block", lack(helper, Crafting.Kind.CRAFTING, Items.STICKY_PISTON, 1, Items.PISTON, 1, Items.SLIME_BLOCK, 1),
			Map.of());
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A cook's bread: from nothing it lacks 3 Wheat, never a Hay Bale; a hay bale there is unpacked for it (nothing lacking). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGoingDown1115cRecipes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGoingDown1115cRecipes")
	public void breadsWheatIsNamedUnlessAHayBaleIsThere(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, "bread from nothing", lack(helper, Crafting.Kind.KITCHEN, Items.BREAD, 1), Map.of(Items.WHEAT, 3));
		check(wrong, "bread from a hay bale", lack(helper, Crafting.Kind.KITCHEN, Items.BREAD, 1, Items.HAY_BLOCK, 1), Map.of());
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- 3. through the player's entry point: a builder, a Librarian at his lectern (no leather: the bug class) ----

	/**
	 * The same Librarian with 36 rabbit hide in place of leather: the chain goes down (hide into leather, cane into paper
	 * into books, then the shelves), the three bookshelves are built and the hide is used up.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaGoingDown1115cShelvesFromHide"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGoingDown1115cShelvesFromHide")
	public void bookshelvesFromRabbitHideAreBuilt(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF,
			new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.RABBIT_HIDE, 36), new ItemStack(Items.OAK_PLANKS, 18));
		QaJobChainsGameTests.librarian(helper);
		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
				"still building: status=" + a.site().status() + " missing=" + a.site().missing() + " line='" + (line == null ? null : line.getString()) + "'");
			List<BlockPos> unfinished = a.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RABBIT_HIDE) == 0 && chest.countItem(Items.LEATHER) == 0,
				"the hide should be used up: " + chest.countItem(Items.RABBIT_HIDE) + " rabbit hide, " + chest.countItem(Items.LEATHER) + " leather left");
		});
	}

	static void assertUntouched(GameTestHelper helper, BlockPos chestPos, ItemStack... items) {
		Container chest = helper.getBlockEntity(chestPos);
		for (ItemStack s : items) {
			helper.assertTrue(chest.countItem(s.getItem()) == s.getCount(), "the chest's " + s.getHoverName().getString() + " was touched: "
				+ chest.countItem(s.getItem()) + " of " + s.getCount());
		}
	}
}
