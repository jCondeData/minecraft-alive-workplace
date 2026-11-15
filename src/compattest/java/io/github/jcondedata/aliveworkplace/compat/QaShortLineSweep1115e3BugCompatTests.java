package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15, third run), failing with the compat suite's mods (Create, Cobblemon, Chipped, Supplementaries...):
 * B1273/B1277/B1279's fix read past its own examples. Every item a job makes (CraftJobs.maker), read from an empty chest the
 * way that job reads a builder's shortage line, against crafter.md: "Of two ways that fit as well, the one from the thing's
 * own mod comes first"; "Of an ingredient's options the base game's come first ... never Chipped's decorative planks that no
 * crafting recipe makes" (B1273). Found: a smoker (and Handcrafted's oven, Create's chassis...) names Cobblemon's Apricorn
 * Logs for its logs; Tom's Storage terminals, Sophisticated Storage upgrades, Supplementaries' sconce lever and cannon still
 * name Chipped's Acacia Planks Mosaic; Minecraft's chest, hopper and TNT minecarts name Create's Minecart Contraption; a slime
 * ball itself names Create's Wheat Flour (B1277 fixed it only as a sticky piston's ingredient).
 * Moves to main with the fix.
 */
public class QaShortLineSweep1115e3BugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * Every job-made item from nothing (one tick's planning at a time, so a spent budget never reads as "nothing short"): no
	 * line names Chipped's decorative planks for another mod's thing; no line for a thing outside Cobblemon names a modded log
	 * of another mod (the logs tag holds oak); a Minecraft thing with a Minecraft recipe names only Minecraft's items.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '20000' '"qaShortLineSweep1115e3"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "qaShortLineSweep1115e3")
	public void everyJobsLineFromNothingNamesTheBaseGamesOwnThings(GameTestHelper helper) {
		QaShortLineSweep1115e3CompatTests.sweep(helper, QaShortLineSweep1115e3BugCompatTests::judge);
	}

	/** The rules above, and B1277's: Wheat Flour only a millstone makes is named only for Create's own things. */
	static String judge(ServerLevel level, QaShortLineSweep1115e3CompatTests.Case c, Map<Item, Integer> lack) {
		String target = QaShortLineSweep1115e3CompatTests.ns(c.item());
		boolean vanillaWay = Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), c.kind(), c.item()).stream()
			.anyMatch(h -> Recipes.id(h).getNamespace().equals("minecraft"));
		for (Item k : lack.keySet()) {
			String n = QaShortLineSweep1115e3CompatTests.ns(k);
			String id = BuiltInRegistries.ITEM.getKey(k).toString();
			if (n.equals("chipped") && !target.equals("chipped")
					|| k.builtInRegistryHolder().is(ItemTags.LOGS) && !n.equals("minecraft") && !n.equals(target)
					|| target.equals("minecraft") && vanillaWay && !n.equals("minecraft")
					|| id.equals("create:wheat_flour") && !target.equals("create")) {
				return "names " + id;
			}
		}
		return null;
	}

	/**
	 * Through the player's entry point: a builder short of 1 Smoker with the furnace's 8 cobblestone in his chest, a Carpenter
	 * at his bench. The line names Minecraft's logs (oak), never Cobblemon's Apricorn Logs; nothing taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLineSweep1115e3Smoker"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLineSweep1115e3Smoker")
	public void aBuilderShortOfASmokerReadsOakLogsNotApricornLogs(GameTestHelper helper) {
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), Items.SMOKER) == CraftJobs.Job.CARPENTER,
			"setup: a smoker's maker is " + CraftJobs.maker(helper.getLevel(), Items.SMOKER) + ", expected the Carpenter");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 8)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.SMOKER, 1, 1, "sweepsmoker"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Oak Log") && !text.contains("Apricorn"),
				"the builder's line: '" + text + "', expected Oak Logs named (never Apricorn Logs); site " + QaCreateRivals1115cCompatTests.site(helper, a));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the Carpenter took something");
			helper.succeed();
		});
	}
}
