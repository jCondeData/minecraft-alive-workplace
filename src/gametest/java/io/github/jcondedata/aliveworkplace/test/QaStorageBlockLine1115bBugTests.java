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
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), written from crafter.md: "When a job could make a part but a raw material is short, nothing is
 * taken and the builder's shortage line says so ... 'Can't be made yet: short of 4 Oak Logs to make 3 Barrels'". A hay
 * bale is 9 wheat at a crafting table, a slime block 9 slimeballs, a dried kelp block 9 dried kelp: a builder short of
 * hay bales with a Carpenter and not enough wheat should read the wheat he's short of. Today Crafting.lacking for these
 * blocks from nothing is {} (the block counts as raw: its one recipe packs an item whose one recipe unpacks it, so
 * Crafting.unpacks says yes both ways) and the builder has no line at all.
 */
public class QaStorageBlockLine1115bBugTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** Storage blocks from nothing or from too little name the item they're packed from. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaStorageLine1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaStorageLine1115bLogic")
	public void storageBlocksNameWhatTheyArePackedFrom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "3 hay bales from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HAY_BLOCK, 3, Map.of()), Map.of(Items.WHEAT, 27));
		expect(wrong, "3 hay bales from 20 wheat", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HAY_BLOCK, 3, Map.of(Items.WHEAT, 20L)),
			Map.of(Items.WHEAT, 7));
		expect(wrong, "a slime block from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.SLIME_BLOCK, 1, Map.of()), Map.of(Items.SLIME_BALL, 9));
		expect(wrong, "a dried kelp block from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.DRIED_KELP_BLOCK, 1, Map.of()),
			Map.of(Items.DRIED_KELP, 9));
		expect(wrong, "3 hay bales from 27 wheat", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HAY_BLOCK, 3, Map.of(Items.WHEAT, 27L)), Map.of());
		helper.assertTrue(wrong.isEmpty(), "storage blocks' lines: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 3 hay bales with 20 wheat in his chest and a Carpenter at his
	 * bench. Two bales are made (18 wheat); the third reads "Can't be made yet: short of 7 Wheat to make 1 Hay Bale".
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaStorageLine1115bHay"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaStorageLine1115bHay")
	public void aBuilderSevenWheatShortOfHisLastHayBaleIsToldSo(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.HAY_BLOCK, new ItemStack(Items.WHEAT, 20));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1400, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with 20 wheat for 3 hay bales");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 7 Wheat to make 1 Hay Bale"),
				"the builder's line: '" + text + "' (missing " + live.missing() + ", the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0)
					+ ", " + chest.countItem(Items.WHEAT) + " wheat left), expected 'Can't be made yet: short of 7 Wheat to make 1 Hay Bale'");
			helper.succeed();
		});
	}

	/** Through the player's entry point, from an empty chest: "short of 27 Wheat to make 3 Hay Bales". */
	//$ gametest_ticks_batch AREA '1400' '"qaStorageLine1115bHayNone"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaStorageLine1115bHayNone")
	public void aBuilderWithNoWheatIsToldTheWheat(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.HAY_BLOCK);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no wheat");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 27 Wheat to make 3 Hay Bales".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 27 Wheat to make 3 Hay Bales'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made something from nothing");
			helper.succeed();
		});
	}

	private static void expect(List<String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> want) {
		if (!got.equals(want)) {
			StringBuilder sb = new StringBuilder("{");
			got.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
			wrong.add(what + " lacks " + sb.append('}') + ", expected " + want.size() + " named");
		}
	}
}
