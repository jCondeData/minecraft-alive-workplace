package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-16, qa-lane-b-1116-0038) past B1302 (1092ec099), from its Expected and docs/wiki/crafter.md ("Special
 * recipes as data"): with Carved Wood's wooden_chests on (the owner's pack) a builder whose blueprint asks for a plain
 * chest gets it as a player in the pack would make it: 8 planks make that wood's carved chest and any carved chest (not a
 * trapped one) makes the plain chest; a chest from nothing names the logs. QaCarvedChest1115eBugCompatTests and
 * RecipeStandInsCompatTests cover 8 oak planks, a carved oak chest given, and the planner's single steps; these go to
 * logs (three crafts deep), two chests at once, a carved chest and planks of another wood together, a carved trapped
 * chest (never used, the line still names logs), and the other way round: a blueprint asking for a carved chest is never
 * built with a plain one. Nothing here is random.
 */
public class QaCarvedChest1116bCompatTests implements FreshPlanning {
	private static final String AREA = QaCarvedChest1115eCompatTests.AREA;
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private static Item item(String id) {
		return QaCarvedChest1115eCompatTests.block(id).asItem();
	}

	/** Two oak logs and a Carpenter: logs to planks, planks to Carved Wood's Oak Chest, that to the chest, and it's built. */
	//$ gametest_ticks_batch AREA '3600' '"qaCarvedChest1116bLogs"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaCarvedChest1116bLogs")
	public void aPlainChestAskedWithTwoOakLogsAndACarpenterIsBuilt(GameTestHelper helper) {
		QaCarvedChest1115eCompatTests.run(helper, Blocks.CHEST, QaCarvedChest1115eCompatTests.block("carved_wood:oak_chest"), "1116blogs",
			new ItemStack(Items.OAK_LOG, 2));
	}

	/**
	 * The planner, both ways from the boundary: 2 oak logs are a chest in three steps (planks, Oak Chest, chest), 1 is
	 * none; 16 oak planks are two chests (takes all 16), 15 are one; a carved oak chest and 8 spruce planks are two chests,
	 * the carved one used as it is.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void plainChestsFromLogsPlanksAndACarvedChestTogether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item carvedOak = item("carved_wood:oak_chest");
		Crafting.Plan logs = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_LOG, 2L));
		helper.assertTrue(logs != null && logs.takes().equals(Map.of(Items.OAK_LOG, 2)), "a chest from 2 oak logs: " + logs);
		List<Item> outs = logs.steps().stream().map(s -> s.out().getItem()).toList();
		helper.assertTrue(outs.equals(List.of(Items.OAK_PLANKS, carvedOak, Items.CHEST)), "the steps should be planks, the Oak Chest, the chest: " + outs);
		helper.assertTrue(Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_LOG, 1L)) == null, "a chest from 1 oak log");

		Crafting.Plan two = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, Map.of(Items.OAK_PLANKS, 16L));
		helper.assertTrue(two != null && two.takes().equals(Map.of(Items.OAK_PLANKS, 16)), "two chests from 16 oak planks: " + two);
		helper.assertTrue(Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, Map.of(Items.OAK_PLANKS, 15L)) == null, "two chests from 15 oak planks");
		helper.assertTrue(Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_PLANKS, 15L)) != null, "no chest from 15 oak planks");

		Map<Item, Long> mixed = Map.of(carvedOak, 1L, Items.SPRUCE_PLANKS, 8L);
		Crafting.Plan both = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, mixed);
		helper.assertTrue(both != null && both.takes().equals(Map.of(carvedOak, 1, Items.SPRUCE_PLANKS, 8)),
			"two chests from a carved oak chest and 8 spruce planks: " + both);
		helper.assertTrue(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, mixed).isEmpty(), "two chests from a carved oak chest and 8 spruce planks lack something");
		helper.succeed();
	}

	/**
	 * A carved trapped oak chest is all the builder has, with a Carpenter hired: it's never turned into the plain chest
	 * (Carved Wood's recipe refuses trapped chests), nothing is built, and the builder's line names logs a player can go
	 * and cut, never "Nobody here makes".
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaCarvedChest1116bTrapped"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaCarvedChest1116bTrapped")
	public void aCarvedTrappedChestIsNeverUsedAndTheLineNamesLogs(GameTestHelper helper) {
		ServerLevel level = QaCarvedChest1115eCompatTests.prepare(helper);
		Block trapped = QaCarvedChest1115eCompatTests.block("carved_wood:trapped_oak_chest");
		helper.assertTrue(trapped != Blocks.AIR, "Carved Wood's trapped oak chest isn't loaded");
		Container chest = QaCarvedChest1115eCompatTests.builderChest(helper, new ItemStack(trapped.asItem(), 1));
		BuildSite site = QaCarvedChest1115eCompatTests.startBuilder(helper, QaCarvedChest1115eCompatTests.spaced(helper, Blocks.CHEST, "1116btrapped"));
		Villager carpenter = QaCarvedChest1115eCompatTests.hire(helper);
		helper.runAfterDelay(1400, () -> {
			String report = QaCarvedChest1115eCompatTests.report(helper, site, chest, carpenter);
			System.out.println("QaCarvedChest1116b trapped: " + report);
			Component line = JobShortages.line(level, site.id(), site.missing(), helper.absolutePos(QaCarvedChest1115eCompatTests.BENCH));
			String text = line == null ? "" : line.getString();
			helper.assertTrue(text.matches("Can't be made yet: short of \\d+ .*Logs? to make 1 .*Chest"), "the builder's line should name logs: " + report);
			helper.assertTrue(chest.countItem(trapped.asItem()) == 1, "the carved trapped chest was taken: " + report);
			helper.assertTrue(!helper.getBlockState(ROW).is(Blocks.CHEST) && !helper.getBlockState(ROW).is(trapped), "something was built: " + report);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the carpenter made something: " + report);
			helper.succeed();
		});
	}

	/**
	 * The other way round (the fix's decision: no two-way equivalence): a blueprint asking for Carved Wood's Oak Chest,
	 * with only a plain chest in the builder's chest and no Carpenter, is never built with the plain chest; the plain chest
	 * stays where it is.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCarvedChest1116bReverse"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCarvedChest1116bReverse")
	public void aCarvedChestBlueprintIsNeverBuiltWithAPlainChest(GameTestHelper helper) {
		QaCarvedChest1115eCompatTests.prepare(helper);
		Block carved = QaCarvedChest1115eCompatTests.block("carved_wood:oak_chest");
		helper.assertTrue(carved != Blocks.AIR, "Carved Wood's oak chest isn't loaded");
		Container chest = QaCarvedChest1115eCompatTests.builderChest(helper, new ItemStack(Items.CHEST, 1));
		BuildSite site = QaCarvedChest1115eCompatTests.startBuilder(helper, QaCarvedChest1115eCompatTests.spaced(helper, carved, "1116breverse"));
		helper.runAfterDelay(1200, () -> {
			String spot = String.valueOf(helper.getBlockState(ROW));
			helper.assertTrue(!helper.getBlockState(ROW).is(Blocks.CHEST), "a plain chest was built for the carved one: " + spot + ", missing " + site.missing());
			helper.assertTrue(chest.countItem(Items.CHEST) == 1, "the plain chest was taken: status " + site.status() + ", missing " + site.missing());
			helper.assertTrue(site.missing().containsKey(carved.asItem()), "the builder doesn't say he's short of the Oak Chest: " + site.missing());
			helper.succeed();
		});
	}
}
