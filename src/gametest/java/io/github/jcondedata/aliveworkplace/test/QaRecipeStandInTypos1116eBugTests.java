package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.RecipeStandIns;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * QA (lane E, 2026-11-16): a datapack's recipe stand-in with a typo in one of its {@code from} or {@code except}
 * entries (an id with a capital letter or a space, as a person writing JSON by hand would) is a broken file: it must be
 * skipped (the loader's 'Skipping recipe stand-in' warning) or the entry left out, and every crafting job must keep
 * planning. Crafting.warm indexes the stand-ins on server start and after every /reload, so a throw there is a throw at
 * boot or reload.
 */
public class QaRecipeStandInTypos1116eBugTests {
	private static final String BATCH = "qaStandInTypos1116e";
	private static final String LOADED = "minecraft:crafting_special_repairitem";

	private static void check(GameTestHelper helper, String name, String from, String except) {
		try {
			QaRecipeStandInFiles1116eGameTests.reloadWith(helper, Map.of("typo", "{\"serializer\": \"" + LOADED + "\", \"from\": [" + from + "]"
				+ (except == null ? "" : ", \"except\": [" + except + "]") + ", \"result\": \"minecraft:bell\"}"));
			List<RecipeHolder<?>> chests;
			try {
				chests = QaRecipeStandInFiles1116eGameTests.freshRecipesFor(helper, Items.CHEST);
			} catch (RuntimeException e) {
				throw new AssertionError("a stand-in file with " + name + " breaks every crafting job's recipe index: " + e, e);
			}
			helper.assertTrue(!chests.isEmpty(), "with " + name + " crafting lost the chest");
		} finally {
			QaRecipeStandInFiles1116eGameTests.restore(helper);
		}
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aCapitalLetterInAFromTagBreaksNoCrafting(GameTestHelper helper) {
		check(helper, "a capital in a from tag", "\"#c:Chests/Wooden\"", null);
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aSpaceInAFromItemBreaksNoCrafting(GameTestHelper helper) {
		check(helper, "a space in a from item", "\"minecraft:gold block\"", null);
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aCapitalLetterInAnExceptItemBreaksNoCrafting(GameTestHelper helper) {
		check(helper, "a capital in an except item", "\"minecraft:gold_block\"", "\"minecraft:Iron_Block\"");
		helper.succeed();
	}
}
