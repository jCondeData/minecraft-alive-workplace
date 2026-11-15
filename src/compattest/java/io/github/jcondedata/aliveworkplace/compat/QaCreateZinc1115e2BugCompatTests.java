package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15, second run), failing with Create loaded: B1267's fix covers Minecraft's metals, but Create's own
 * zinc still names the Crushed Raw Zinc, which only a crushing wheel makes. Both of Create's ways to a zinc ingot (blasting
 * raw zinc, blasting crushed raw zinc) are Create's, so "the one from the thing's own mod comes first" can't choose, and
 * the crushed one wins. The spec (crafter.md, B408/B489/B1267): the line names the raw metal a player mines. Moves to main
 * with the fix.
 */
public class QaCreateZinc1115e2BugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Read off the forge's recipes: zinc from nothing lacks Raw Zinc, never Crushed Raw Zinc. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zincFromNothingNamesRawZinc(GameTestHelper helper) {
		Map<Item, Integer> ingot = Crafting.lacking(helper.getLevel(), Crafting.Kind.WORKSHOP, create("zinc_ingot"), 3, Map.of());
		Map<Item, Integer> block = Crafting.lacking(helper.getLevel(), Crafting.Kind.WORKSHOP, create("zinc_block"), 1, Map.of());
		helper.assertTrue(ingot.equals(Map.of(create("raw_zinc"), 3)) && block.equals(Map.of(create("raw_zinc"), 9)),
			"3 zinc ingots from nothing lack " + ingot + ", a zinc block " + block + "; expected {raw_zinc=3} and {raw_zinc=9}");
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 2 Blocks of Zinc (the Tinkerer's, by its ingots), a coal and no
	 * ore in his chest, a Tinkerer at his bench. The line names the Raw Zinc; nothing taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateZinc1115e2Line"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateZinc1115e2Line")
	public void aBuilderShortOfZincBlocksReadsRawZinc(GameTestHelper helper) {
		Block zincBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "zinc_block"));
		helper.assertTrue(zincBlock != Blocks.AIR, "create:zinc_block isn't registered");
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), zincBlock.asItem()) == CraftJobs.Job.TINKERER,
			"setup: a Block of Zinc's maker is " + CraftJobs.maker(helper.getLevel(), zincBlock.asItem()) + ", expected the Tinkerer");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, zincBlock, 2, 1, "zincblocks"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), tinkerer, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Zinc") && !text.contains("Crushed"),
				"the builder's line: '" + text + "', expected it to name the Raw Zinc (never the Crushed Raw Zinc)");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the Tinkerer took something");
			helper.succeed();
		});
	}

	private static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("create:" + path + " isn't registered");
		}
		return item;
	}
}
