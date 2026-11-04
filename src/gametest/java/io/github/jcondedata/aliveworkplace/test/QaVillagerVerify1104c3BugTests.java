package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-04, round 3; B878 follow-up, Chef): B878 asks that every crafter-family line names many things
 * in the plural, the Chef's "cooking %s %s" included. A Chef cooks up to 8 of a dish at once ({@code Chefs.BATCH}), and
 * {@code Words.itemName} falls back to the one-form when the lang has no {@code plural.aliveworkplace.minecraft.<item>}.
 * The Smoker's dishes on his menu have none for the countable ones: a Chef smoking 4 porkchops reads "cooking 4 Cooked
 * Porkchop", and 8 bowls of soup "cooking 8 Beetroot Soup" (though the stews read "Bowls of Mushroom Stew"). Fixed by
 * B931 (the lang now has the plurals); registered.
 */
public class QaVillagerVerify1104c3BugTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/** A Chef smoking 4 raw porkchops: "cooking 4 Cooked Porkchops", never "4 Cooked Porkchop". */
	//$ gametest_ticks_batch BIG '2400' '"qaVv1104c3BugPork"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaVv1104c3BugPork")
	public void aChefCookingFourPorkchopsNamesThemInThePlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.PORKCHOP, 4));
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		Set<String> seen = QaVillagerVerify1104c3GameTests.lines(helper, chef);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.COOKED_PORKCHOP) == 4, "setup: " + chest.countItem(Items.COOKED_PORKCHOP) + " porkchops cooked, expected 4");
			helper.assertTrue(seen.contains("cooking 4 Cooked Porkchops"), "want 'cooking 4 Cooked Porkchops'; lines seen: " + seen);
		});
	}

	/**
	 * Every dish on a Chef's menu ({@code Chefs.menu}) that is a countable thing reads in the plural after 2. Mass nouns
	 * ("Bread", "Cooked Chicken", "Steak", "Dried Kelp" ...) may keep their one-form.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCountableDishOnTheChefsMenuHasAPlural(GameTestHelper helper) {
		Set<String> mass = Set.of("Bread", "Cooked Chicken", "Cooked Cod", "Cooked Salmon", "Cooked Mutton", "Cooked Rabbit", "Dried Kelp", "Steak");
		List<String> wrong = new ArrayList<>();
		List<Item> menu = Chefs.menu(helper.getLevel());
		for (Item dish : menu) {
			String one = dish.getDescription().getString();
			String many = Words.itemName(dish, 2).getString();
			if (many.equals(one) && !mass.contains(one) && BuiltInRegistries.ITEM.getKey(dish).getNamespace().equals("minecraft")) {
				wrong.add(BuiltInRegistries.ITEM.getKey(dish) + ": 'cooking 2 " + many + "'");
			}
		}
		helper.assertTrue(menu.contains(Items.COOKED_PORKCHOP), "setup: the menu " + menu);
		helper.assertTrue(wrong.isEmpty(), "dishes named as one after 2: " + wrong);
		helper.succeed();
	}
}
