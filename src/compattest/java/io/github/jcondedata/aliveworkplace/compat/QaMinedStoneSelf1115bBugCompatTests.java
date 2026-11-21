package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), with the compat pack: a sweep of every job-made thing's shortage line from nothing (the
 * QaShortLineSweep1115e3 way) finds the Mason's stonecutter line for stones a player mines naming the stone itself:
 * andesite, diorite, granite, tuff, calcite, deepslate and a dripstone block each lack {itself=1}. crafter.md: the line
 * names what the chest is short of to make it, so a builder short of andesite never reads "short of 3 Andesite to make 3
 * Andesite" (a Mason can't make andesite out of andesite).
 */
public class QaMinedStoneSelf1115bBugCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** From nothing, by the stonecutter's plan: none of the mined stones names itself. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaMinedStone1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaMinedStone1115bLogic")
	public void minedStonesNeverNameThemselves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (Item stone : List.of(Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.DRIPSTONE_BLOCK)) {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.STONECUTTING, stone, 3, Map.of());
			if (lack.containsKey(stone)) {
				List<String> ways = new ArrayList<>();
				for (RecipeHolder<?> h : Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.STONECUTTING, stone)) {
					ways.add(h.id().toString());
				}
				wrong.add(BuiltInRegistries.ITEM.getKey(stone).getPath() + " (maker " + CraftJobs.maker(level, stone) + ", made by " + ways + ") lacks " + lack);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "mined stones naming themselves: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of 3 Andesite, an empty chest, a Mason at his stonecutter. */
	//$ gametest_ticks_batch AREA '1400' '"qaMinedStone1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaMinedStone1115bLine")
	public void aBuilderShortOfAndesiteIsNotToldAndesiteMakesIt(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.ANDESITE, 3, 1,
			"qa1115bandesite"), Blocks.CHEST.defaultBlockState(), new ItemStack[0]);
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.STONECUTTER);
		helper.setBlock(station.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text == null || !text.contains("Andesite to make"), "the builder's line: '" + text
				+ "', a Mason can't make andesite out of andesite; site " + QaCreateRivals1115cCompatTests.site(helper, a));
			helper.succeed();
		});
	}
}
