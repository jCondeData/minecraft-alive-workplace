package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1025-1838), B408's boundaries that fail on main 1639a080d (not registered). The wiki (crafter.md): "The
 * line names what the chest is short of, not some other way to make it ... It goes further down only when that uses
 * something that's there", and the Tinkerer makes "raw iron fired into ingots, then bars". With five raw iron and a
 * coal the line reads "short of 6 Deepslate Iron Ore to make 3 Iron Bars": the wrong item (a block a player only gets
 * with Silk Touch) and the wrong count (five raw iron are right there; one more is what's short). With coal and no ore
 * it also names Deepslate Iron Ore rather than the raw iron.
 */
public class QaTinkererShortBugGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	/** Five raw iron and a coal: one ore short of the batch. Exactly "short of 1 Raw Iron to make 3 Iron Bars"; all stays. */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerOneShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerOneShort")
	public void oneRawIronShortNamesExactlyThatOneAndTakesNothing(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.RAW_IRON, 5), new ItemStack(Items.COAL, 1));
		Villager tinkerer = QaTinkererShortGameTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String expected = QaTinkererShortGameTests.cantMake("1", "Raw Iron", "3", "Iron Bars");
			String text = QaTinkererShortGameTests.line(helper, a);
			QaTinkererShortGameTests.assertTookNothing(helper, a, tinkerer, 5, 1);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'");
			LOG.info("[QA B408] one ore short: '{}'", text);
			helper.succeed();
		});
	}

	/** Coal and no ore: the line names the raw iron the Tinkerer fires, not Deepslate Iron Ore. */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerNoOreRaw"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerNoOreRaw")
	public void coalButNoOreNamesTheRawIron(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.COAL, 1));
		Villager tinkerer = QaTinkererShortGameTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String expected = QaTinkererShortGameTests.cantMake("6", "Raw Iron", "3", "Iron Bars");
			String text = QaTinkererShortGameTests.line(helper, a);
			QaTinkererShortGameTests.assertTookNothing(helper, a, tinkerer, 0, 1);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'");
			helper.succeed();
		});
	}

	/** Gold the same way (B489): one raw gold (a plate takes two) and a coal names the five raw gold short, takes nothing. */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerGoldShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerGoldShort")
	public void oneRawGoldNamesTheRawGoldShort(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, new ItemStack(Items.RAW_GOLD, 1),
			new ItemStack(Items.COAL, 1));
		Villager tinkerer = QaTinkererShortGameTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String expected = QaTinkererShortGameTests.cantMake("5", "Raw Gold", "3", "Light Weighted Pressure Plate");
			String text = QaTinkererShortGameTests.line(helper, a);
			net.minecraft.world.Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RAW_GOLD) == 1 && chest.countItem(Items.COAL) == 1, "the chest should hold 1 raw gold and 1 coal still: "
				+ chest.countItem(Items.RAW_GOLD) + " raw gold, " + chest.countItem(Items.GOLD_INGOT) + " ingots");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the tinkerer took something");
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'");
			helper.succeed();
		});
	}

	/**
	 * What's short, read straight off the forge's recipes (B489): copper names the raw copper short of the ones there;
	 * nuggets still go down through ingots to the raw iron (nuggets from an ingot aren't a block unpacked); and a raw iron
	 * block in the chest is unpacked rather than named.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theForgeNamesTheRawMetalShort(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		io.github.jcondedata.aliveworkplace.craft.Crafting.Kind kind = io.github.jcondedata.aliveworkplace.craft.Crafting.Kind.WORKSHOP;
		java.util.Map<net.minecraft.world.item.Item, Integer> copper = io.github.jcondedata.aliveworkplace.craft.Crafting.lacking(level, kind,
			Items.COPPER_BLOCK, 1, java.util.Map.of(Items.RAW_COPPER, 5L));
		helper.assertTrue(copper.equals(java.util.Map.of(Items.RAW_COPPER, 4)), "a copper block from 5 raw copper lacks " + copper);
		java.util.Map<net.minecraft.world.item.Item, Integer> nuggets = io.github.jcondedata.aliveworkplace.craft.Crafting.lacking(level, kind,
			Items.IRON_NUGGET, 9, java.util.Map.of());
		helper.assertTrue(nuggets.equals(java.util.Map.of(Items.RAW_IRON, 1)), "9 iron nuggets from nothing lack " + nuggets);
		java.util.Map<net.minecraft.world.item.Item, Integer> bars = io.github.jcondedata.aliveworkplace.craft.Crafting.lacking(level, kind,
			Items.IRON_BARS, 16, java.util.Map.of(Items.RAW_IRON_BLOCK, 1L));
		helper.assertTrue(bars.isEmpty(), "16 iron bars from a raw iron block lack " + bars);
		helper.succeed();
	}
}
