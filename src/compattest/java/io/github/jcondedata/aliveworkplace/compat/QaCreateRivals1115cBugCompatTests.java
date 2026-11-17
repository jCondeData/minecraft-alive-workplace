package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-15), failing with Create loaded: the bugs QaCreateRivals1115cCompatTests found beyond B1267/B1268,
 * written from crafter.md (B409: "The line names what the chest is short of, not some other way to make it ... It goes
 * further down only when that uses something that's there"; B1267: never an item only Create's machines make).
 * (1) A sticky piston's slimeball has only Create's crafting recipe (Dough and Lime Dye), so the line names Create's Wheat
 * Flour (and Redstone Ore and Chipped planks). (2) With no leather at all, a book, an item frame or bookshelves name Rabbit
 * Hide, not Leather (probably not Create-specific). Moves to main with the fix.
 */
public class QaCreateRivals1115cBugCompatTests implements FreshPlanning {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/**
	 * Every item the Tinkerer forges (the aliveworkplace:tinkering tag), from nothing: something is named short, and never
	 * an item of Create's or Cobblemon's (a crushing wheel's ore, Create's zinc, a Cobblemon plaque). Failing when written: a
	 * sticky piston lacks create:wheat_flour (its slimeball by Create's dough recipe; see the sticky piston test).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTinkeringItemFromNothingNamesNoCreateOrCobblemonItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TagKey<Item> tinkering = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "tinkering"));
		Map<String, String> wrong = new LinkedHashMap<>();
		int checked = 0;
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tinkering)) {
			Item item = holder.value();
			if (Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.WORKSHOP, item).isEmpty()) {
				continue;
			}
			checked++;
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.WORKSHOP, item, 1, Map.of());
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			if (lack.isEmpty()) {
				wrong.put(id, "nothing named short from an empty chest");
			}
			for (Item k : lack.keySet()) {
				String ns = BuiltInRegistries.ITEM.getKey(k).getNamespace();
				if (ns.equals("create") || ns.equals("cobblemon")) {
					wrong.put(id, "lacks " + lack);
				}
			}
		}
		helper.assertTrue(checked >= 30, "setup: only " + checked + " tinkering items have a forge recipe");
		helper.assertTrue(wrong.isEmpty(), "the forge names another mod's item (or nothing) short: " + wrong);
		helper.succeed();
	}

	/**
	 * Found by this class (not Create-specific): with paper (or sticks) there and no leather at all, a book or item frame
	 * lacks {rabbit_hide=4}, not 1 Leather. crafter.md (B409): "The line names what the chest is short of, not some other way
	 * to make it ... It goes further down only when that uses something that's there"; no rabbit hide is there.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withNoLeatherABookOrFrameNamesLeatherNotRabbitHide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		QaCreateRivals1115cCompatTests.check(wrong, "a book from 3 paper", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L)), Map.of(Items.LEATHER, 1));
		QaCreateRivals1115cCompatTests.check(wrong, "an item frame from 8 sticks", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 1, Map.of(Items.STICK, 8L)),
			Map.of(Items.LEATHER, 1));
		helper.assertTrue(wrong.isEmpty(), "the wrong thing is named short: " + wrong);
		helper.succeed();
	}

	/**
	 * Found by this class: a sticky piston's slimeball has only one crafting recipe with Create, Create's (Dough and Lime
	 * Dye), so the forge read from nothing names {create:wheat_flour=1, water_bucket=1, green_dye=1, bone_meal=1, ...}: Wheat
	 * Flour only a millstone makes. Through the player's entry point: a builder short of 1 sticky piston with a coal, a
	 * Tinkerer at his bench: a line is said, it names the Slimeball, and never Wheat Flour or Dough.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateRivals1115cSticky"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateRivals1115cSticky")
	public void aStickyPistonShortNamesTheSlimeballNotWheatFlour(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.STICKY_PISTON, 1, 1, "rivsticky"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager t = QaCreateRivals1115cCompatTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Slimeball") && !text.contains("Wheat Flour") && !text.contains("Dough"),
				"the builder's line: '" + text + "', expected the Slimeball named (never Create's Wheat Flour or Dough); site " + QaCreateRivals1115cCompatTests.site(helper, a));
			QaCreateRivals1115cCompatTests.tookNothing(helper, t, items);
			helper.succeed();
		});
	}

	/**
	 * A Librarian and three bookshelves with the cane and planks but no leather at all: the line names Leather, never Rabbit
	 * Hide (no hide is there, B409) or Create's Cardboard; nothing is taken.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaCreateRivals1115cShelvesNoLeather"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaCreateRivals1115cShelvesNoLeather")
	public void bookshelvesWithNoLeatherNameLeather(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.OAK_PLANKS, 18)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.BOOKSHELF, 3, 1, "rivnoleather"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager librarian = QaCreateRivals1115cCompatTests.librarian(helper);
		helper.runAfterDelay(1400, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Leather") && !text.contains("Rabbit Hide") && !text.contains("Cardboard"),
				"the builder's line: '" + text + "', expected Leather named (never Rabbit Hide or Cardboard); site " + QaCreateRivals1115cCompatTests.site(helper, a));
			QaCreateRivals1115cCompatTests.tookNothing(helper, librarian, items);
			helper.succeed();
		});
	}
}
