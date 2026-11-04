package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA, lane D, 2026-11-04: the tests of {@link QaLaneD1104GameTests} that fail because the mod is wrong against the spec.
 * Not registered (the suite stays green); register it to see them fail, and move each one back once fixed.
 * <ul>
 *   <li>B133 kin: with only a Seer watching, the Defense page's idle line reads "&lt;Seer&gt; keeps watch from a tower: a
 *       raid is seen a day ahead" (the Seer has no tower and sees two days ahead).</li>
 *   <li>B426 kin: jungle and cherry logs and crimson and warped stems have no plural keys, so the shortage line reads
 *       "short of 2 Jungle Log to make 8 Jungle Planks".</li>
 * </ul>
 */
public class QaLaneD1104BugTests implements FabricGameTest {
	private static final String BIG = QaLaneD1104GameTests.BIG;

	/**
	 * The Defense page while no attack is coming names who watches: with only a Seer, it must not say they keep watch from
	 * a tower and see a day ahead (the Seer has no tower and sees two days ahead, as 32.14 and the wiki say).
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104SeerPage"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104SeerPage")
	public void qaB133TheDefensePageDoesNotPutTheSeerOnATower(GameTestHelper helper) {
		QaLaneD1104GameTests.SeerVillage v = QaLaneD1104GameTests.seerVillage(helper);
		ItemStack next = DefencePage.next(v.level(), v.hall());
		List<String> lines = QaLaneD1104GameTests.lore(next);
		String name = v.seer().getName().getString();
		helper.assertTrue(next.getHoverName().getString().equals("No attack seen coming"), "the page: " + next.getHoverName().getString());
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains(name)), "the page doesn't name the Seer: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("from a tower") || l.contains("a day ahead")),
			"the page says the Seer keeps watch from a tower, a day ahead: " + lines);
		helper.succeed();
	}

	/**
	 * Other woods than oak: a crafter short of two logs of jungle, cherry, crimson or warped wood names them in the plural,
	 * as B426 expects ("plural item names after counts above one"), the same as spruce, birch and the rest.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB426EveryWoodsLogsAreNamedInThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, String> planks = new LinkedHashMap<>();
		planks.put(Items.SPRUCE_PLANKS, "short of 2 Spruce Logs to make 8 Spruce Planks");
		planks.put(Items.BIRCH_PLANKS, "short of 2 Birch Logs to make 8 Birch Planks");
		planks.put(Items.JUNGLE_PLANKS, "short of 2 Jungle Logs to make 8 Jungle Planks");
		planks.put(Items.ACACIA_PLANKS, "short of 2 Acacia Logs to make 8 Acacia Planks");
		planks.put(Items.DARK_OAK_PLANKS, "short of 2 Dark Oak Logs to make 8 Dark Oak Planks");
		planks.put(Items.MANGROVE_PLANKS, "short of 2 Mangrove Logs to make 8 Mangrove Planks");
		planks.put(Items.CHERRY_PLANKS, "short of 2 Cherry Logs to make 8 Cherry Planks");
		planks.put(Items.CRIMSON_PLANKS, "short of 2 Crimson Stems to make 8 Crimson Planks");
		planks.put(Items.WARPED_PLANKS, "short of 2 Warped Stems to make 8 Warped Planks");
		planks.forEach((item, want) -> {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, item, 8, Map.of());
			QaLaneD1104GameTests.check(wrong, JobShortages.lackLine(lack, 8, item).getString(), want);
		});
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

}
