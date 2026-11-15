package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-15, third run): a slot that takes any log (the smoker's four), read the way the Carpenter's
 * shortage line reads it, in the base game alone. crafter.md (B409): "The line names what the chest is short of, not some
 * other way to make it ... It goes further down only when that uses something that's there". With the pack's mods the
 * same smoker reads 8 Apricorn Logs (B1289); these pin what it reads without them: 4 logs (the logs tag's first, dark oak
 * here), never 8 through wood. Mixed woods are B1293's (QaLogsSlot1115e3BugGameTests).
 */
public class QaLogsSlot1115e3GameTests implements FabricGameTest {

	/** One entry: 4 of a base-game log. */
	static boolean fourLogsOfOneKind(Map<Item, Integer> lack) {
		return lack.size() == 1 && lack.values().iterator().next() == 4 && lack.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.LOGS)
			&& BuiltInRegistries.ITEM.getKey(lack.keySet().iterator().next()).getNamespace().equals("minecraft");
	}

	static Map<Item, Integer> smoker(GameTestHelper helper, Map<Item, Long> chest) {
		return Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SMOKER, 1, chest);
	}

	/** The furnace's 8 cobblestone there, no log: exactly 4 logs of one of the base game's woods short (not 8 through wood). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSmokerFromCobblestoneLacksFourLogsOfOneWood(GameTestHelper helper) {
		Map<Item, Integer> lack = smoker(helper, Map.of(Items.COBBLESTONE, 8L));
		helper.assertTrue(fourLogsOfOneKind(lack), "a smoker from 8 cobblestone lacks " + lack + ", expected 4 logs of one kind");
		helper.succeed();
	}

	/** Two oak logs there: the other 2 are short; one birch log there: 3 more of it (what's there leads). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void someLogsThereLeaveOnlyTheRestShort(GameTestHelper helper) {
		Map<Item, Integer> oak = smoker(helper, Map.of(Items.COBBLESTONE, 8L, Items.OAK_LOG, 2L));
		helper.assertTrue(oak.equals(Map.of(Items.OAK_LOG, 2)), "with 2 oak logs a smoker lacks " + oak + ", expected {oak_log=2}");
		Map<Item, Integer> birch = smoker(helper, Map.of(Items.COBBLESTONE, 8L, Items.BIRCH_LOG, 1L));
		helper.assertTrue(birch.values().stream().mapToInt(Integer::intValue).sum() == 3 && !birch.containsKey(Items.COBBLESTONE),
			"with 1 birch log a smoker lacks " + birch + ", expected 3 logs and nothing else");
		helper.succeed();
	}

	/** A ready furnace there: only the 4 logs, no cobblestone, nothing else. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFurnaceThereLeavesOnlyTheLogs(GameTestHelper helper) {
		Map<Item, Integer> lack = smoker(helper, Map.of(Items.FURNACE, 1L));
		helper.assertTrue(fourLogsOfOneKind(lack), "a smoker with a furnace there lacks " + lack + ", expected 4 logs of one kind");
		helper.succeed();
	}
}
