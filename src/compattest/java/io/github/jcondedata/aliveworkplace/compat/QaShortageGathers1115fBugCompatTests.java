package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.BlockPos;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;

/**
 * QA (qa-lane-b-1115-1237), failing with the pack: a smoker from nothing names Cobblemon's Apricorn Logs (8 of them, for
 * a recipe that takes 4 logs). crafter.md (B1273): "Of an ingredient's options the base game's come first"; the line
 * names what a player gathers in Minecraft's own wood. Moves to main with the fix.
 */
public class QaShortageGathers1115fBugCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A smoker from nothing, and from a furnace: only Minecraft's things, and 4 logs (the recipe's four), not 8. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaShortGathers1115fSmoker"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaShortGathers1115fSmoker")
	public void aSmokerFromNothingNamesMinecraftLogs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StringBuilder wrong = new StringBuilder();
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
			for (Map<Item, Long> there : List.of(Map.<Item, Long>of(), Map.of(Items.FURNACE, 1L))) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, Items.SMOKER, 1, there);
				int logs = 0;
				boolean foreign = false;
				for (Map.Entry<Item, Integer> e : lack.entrySet()) {
					foreign |= !BuiltInRegistries.ITEM.getKey(e.getKey()).getNamespace().equals("minecraft");
					logs += e.getKey().builtInRegistryHolder().is(ItemTags.LOGS) ? e.getValue() : 0;
				}
				if (lack.isEmpty() || foreign || logs != 4) {
					wrong.append(kind).append(there.isEmpty() ? " from nothing" : " from a furnace").append(" lacks ").append(lack).append("; ");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "a smoker (maker " + CraftJobs.maker(level, Items.SMOKER) + "): " + wrong
			+ "expected 4 of Minecraft's logs (and the furnace's cobblestone), never another mod's wood");
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 1 Smoker with a furnace in his chest and a Carpenter at his
	 * bench. Expected "Can't be made yet: short of 4 Oak Logs to make 1 Smoker" (or another of Minecraft's logs: the
	 * recipe's four), never Apricorn Logs.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaShortGathers1115fSmokerLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortGathers1115fSmokerLine")
	public void aBuilderShortOfASmokerReadsMinecraftLogs(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.FURNACE, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.SMOKER, 1, 1,
			"qa1115fsmoker"), Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.matches("Can't be made yet: short of 4 [A-Za-z ]+ (Logs|Stems) to make 1 Smoker") && !text.contains("Apricorn"),
				"the builder's line: '" + text + "', expected 'Can't be made yet: short of 4 Oak Logs to make 1 Smoker' (or another of Minecraft's logs); site " + QaCreateRivals1115cCompatTests.site(helper, a));
			QaCreateRivals1115cCompatTests.tookNothing(helper, carpenter, items);
			helper.succeed();
		});
	}
}
