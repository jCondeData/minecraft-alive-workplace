package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), written from crafter.md ("The line names what the chest is short of ... a raw option, logs, not
 * wood made from logs"): bamboo planks come from a Block of Bamboo or a Stripped Block of Bamboo, and a player gets either
 * only from 9 bamboo (the stripped one with an axe on the block). From nothing the line should name the bamboo (or the
 * block), never the stripped block, the way oak planks name Oak Logs and never Stripped Oak Logs.
 */
public class QaBambooLine1115bBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** Bamboo things from nothing, from 9 bamboo and from a block of bamboo. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBamboo1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBamboo1115bLogic")
	public void bambooPlanksNameTheBambooNotTheStrippedBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
			for (Item item : List.of(Items.BAMBOO_PLANKS, Items.BAMBOO_SLAB, Items.BAMBOO_STAIRS, Items.BAMBOO_MOSAIC, Items.BAMBOO_FENCE)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 2, Map.of());
				if (lack.isEmpty() || lack.containsKey(Items.STRIPPED_BAMBOO_BLOCK)
					|| !(lack.containsKey(Items.BAMBOO) || lack.containsKey(Items.BAMBOO_BLOCK))) {
					wrong.add(kind + " 2 " + BuiltInRegistries.ITEM.getKey(item).getPath() + " from nothing lack " + names(lack));
				}
			}
			Map<Item, Integer> fromBamboo = Crafting.lacking(level, kind, Items.BAMBOO_PLANKS, 2, Map.of(Items.BAMBOO, 9L));
			if (!fromBamboo.isEmpty()) {
				wrong.add(kind + " 2 bamboo planks from 9 bamboo lack " + names(fromBamboo) + ", expected nothing");
			}
			Map<Item, Integer> fromBlock = Crafting.lacking(level, kind, Items.BAMBOO_PLANKS, 2, Map.of(Items.BAMBOO_BLOCK, 1L));
			if (!fromBlock.isEmpty()) {
				wrong.add(kind + " 2 bamboo planks from a block of bamboo lack " + names(fromBlock) + ", expected nothing");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "bamboo lines: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 3 bamboo planks with an empty chest and a Carpenter at his
	 * bench reads a line naming Bamboo (or a Block of Bamboo), never "Stripped Block of Bamboo"; nothing is made or taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaBamboo1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaBamboo1115bLine")
	public void aBuilderShortOfBambooPlanksReadsTheBamboo(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BAMBOO_PLANKS);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no bamboo anywhere");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 3 Bamboo Planks")
				&& text.contains("Bamboo") && !text.contains("Stripped"), "the builder's line: '" + text + "', expected it to name the Bamboo (or a Block of Bamboo)");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made something from nothing");
			helper.succeed();
		});
	}

	private static String names(Map<Item, Integer> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}
}
