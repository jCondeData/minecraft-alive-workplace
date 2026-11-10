package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (qa-lane-c-1110-0353) for ROADMAP 46.1: what a player reads wrong. The "Hire a ..." line names countable things
 * in the singular when the lang has no plural for them ("Hire a Tinkerer to make Minecart"), and the Storehouse says
 * "No job makes or gathers this" for stripped logs, which the Lumberjack strips for builders, and a builder short of
 * concrete (the Mason's, but it has no crafting recipe) reads no "Hire a Mason" line at all.
 */
public class QaJobCraftListsBugTests {
	/** Countable things of the table read in the plural on the hire line, as the ones with a plural key do. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobListsPlurals"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobListsPlurals")
	public void theHireLineSaysEveryCountableThingInThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, String> expected = new LinkedHashMap<>();
		expected.put(Items.MINECART, "Hire a Tinkerer to make Minecarts");
		expected.put(Items.REDSTONE_TORCH, "Hire a Tinkerer to make Redstone Torches");
		expected.put(Items.COPPER_BULB, "Hire a Tinkerer to make Copper Bulbs");
		expected.put(Items.ARMOR_STAND, "Hire a Carpenter to make Armor Stands");
		expected.put(Items.TRAPPED_CHEST, "Hire a Carpenter to make Trapped Chests");
		expected.put(Items.BLACK_STAINED_GLASS_PANE, "Hire a Mason to make Black Stained Glass Panes");
		expected.put(Items.BEACON, "Hire a Librarian to make Beacons");
		expected.put(Items.ENDER_CHEST, "Hire a Librarian to make Ender Chests");
		expected.put(Items.PAINTING, "Hire a Leatherworker to make Paintings");
		expected.put(Items.HONEYCOMB_BLOCK, "Hire a Leatherworker to make Honeycomb Blocks");
		expected.put(Items.LEAD, "Hire a Leatherworker to make Leads");
		expected.put(Items.GLOW_ITEM_FRAME, "Hire a Leatherworker to make Glow Item Frames");
		Map<Item, UUID> sites = new LinkedHashMap<>();
		expected.keySet().forEach(i -> sites.put(i, UUID.randomUUID()));
		sites.forEach((item, site) -> JobShortages.line(level, site, Map.of(item, 3)));
		helper.runAfterDelay(610, () -> {
			List<String> wrong = new ArrayList<>();
			sites.forEach((item, site) -> {
				Component line = JobShortages.line(level, site, Map.of(item, 3));
				String text = line == null ? null : line.getString();
				JobShortages.forget(site);
				if (!expected.get(item).equals(text)) {
					wrong.add("'" + text + "', expected '" + expected.get(item) + "'");
				}
			});
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/** Stripped logs: the Lumberjack strips them for builders, so the Storehouse names him, not "No job ...". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStorehouseNamesTheLumberjackForStrippedLogs(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> wrong = new ArrayList<>();
		for (Item item : List.of(Items.STRIPPED_OAK_LOG, Items.STRIPPED_SPRUCE_LOG)) {
			String got = CraftJobs.storeLines(helper.getLevel(), pos, 8, item).stream().map(Component::getString).collect(Collectors.joining(" | "));
			if (!got.contains("Lumberjack")) {
				wrong.add(item + ": '" + got + "'");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the Storehouse doesn't name the Lumberjack: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Concrete is the Mason's (the table): a builder short of it with no Mason in the village reads "Hire a Mason to make
	 * Red Concrete", as for every other thing of a job's list. (Concrete has no crafting recipe: it is set in water.)
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '700' '"qaJobListsConcreteHire"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 700, batch = "qaJobListsConcreteHire")
	public void theHireLineNamesTheMasonForConcrete(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID site = UUID.randomUUID();
		JobShortages.line(level, site, Map.of(Items.RED_CONCRETE, 3));
		helper.runAfterDelay(610, () -> {
			Component line = JobShortages.line(level, site, Map.of(Items.RED_CONCRETE, 3));
			String text = line == null ? null : line.getString();
			JobShortages.forget(site);
			helper.assertTrue("Hire a Mason to make Red Concrete".equals(text), "the line: '" + text + "', expected 'Hire a Mason to make Red Concrete'");
			helper.succeed();
		});
	}
}
