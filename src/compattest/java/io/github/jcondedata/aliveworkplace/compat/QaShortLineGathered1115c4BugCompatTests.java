package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1115-1252), B1290: with Chipped loaded, Chipped's planks (in the planks tag) the chest does hold weren't used
 * for a crafting table; the line named an Oak Log nobody has. crafter.md: the line names what the chest is short of, and
 * goes further down only when that uses something that's there. In the pack, Carved Wood's mixed crafting table recipe (a
 * special, code recipe, on by default) makes Minecraft's table from any 2x2 of planks no other recipe takes, so four of
 * Chipped's mosaic planks make one for a player; the planner reads it through a stand-in
 * ({@code recipe_stand_ins/carved_wood_crafting_table.json}). The bookshelf half of the report stays a question for the owner:
 * Carved Wood's mixed bookshelf recipe is off by default, so in the pack a player can't make Minecraft's bookshelf from
 * acacia mosaic planks either (pinned by ChippedShortLineCompatTests#withCarvedWoodABookshelfTakesOakPlanksNotChippedOnes).
 */
public class QaShortLineGathered1115c4BugCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/** Read off the table's recipes: a crafting table from 4 Chipped planks lacks nothing; from 4 acacia planks (Carved Wood's own table) it does. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chippedPlanksThereMakeACraftingTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item chipped = chippedPlanks();
		Map<String, String> wrong = new LinkedHashMap<>();
		for (Crafting.Kind k : java.util.List.of(Crafting.Kind.CRAFTING, Crafting.Kind.CRAFTER)) {
			QaCreateRivals1115cCompatTests.check(wrong, "a crafting table from 4 " + chipped + " (" + k + ")",
				Crafting.lacking(level, k, Items.CRAFTING_TABLE, 1, Map.of(chipped, 4L)), Map.of());
		}
		Map<Item, Integer> three = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, Map.of(chipped, 3L));
		helper.assertTrue(!three.isEmpty(), "a crafting table from 3 " + chipped + " lacks nothing");
		// four acacia planks make Carved Wood's Acacia Crafting Table for a player, not Minecraft's: the stand-in leaves them out
		Map<Item, Integer> acacia = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, Map.of(Items.ACACIA_PLANKS, 4L));
		helper.assertTrue(!acacia.isEmpty(), "a crafting table from 4 acacia planks lacks nothing, though they make Carved Wood's acacia table");
		helper.assertTrue(wrong.isEmpty(), "Chipped planks there not used: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of 1 crafting table, 4 Chipped planks there, a Carpenter: she makes it. */
	//$ gametest_ticks_batch AREA '1600' '"qaShortLineChippedTable1115c4"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortLineChippedTable1115c4")
	public void aCarpenterMakesTheCraftingTableFromChippedPlanks(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item chipped = chippedPlanks();
		ItemStack[] items = {new ItemStack(chipped, 4)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.CRAFTING_TABLE, 1, 1, "qachiptable4"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(QaCreateRivals1115cCompatTests.CHEST);
			helper.assertTrue(chest.countItem(chipped) == 0,
				"the Carpenter never used the 4 Chipped planks: the builder's line reads '" + QaCrafterOwnModdedStore1111eCompatTests.line(helper, a)
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
