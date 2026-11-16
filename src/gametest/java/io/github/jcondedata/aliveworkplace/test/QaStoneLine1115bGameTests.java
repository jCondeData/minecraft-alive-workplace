package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), written from crafter.md ("The line names what the chest is short of", what a player gathers):
 * andesite, diorite and granite are stones a player mines. Their crafting recipes (cobblestone and Nether Quartz) are
 * the long way round, so a line for polished andesite from nothing should name the Andesite, never Nether Quartz from
 * another dimension.
 */
public class QaStoneLine1115bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/**
	 * The three stones' polished forms, slabs, stairs and walls from nothing, the way a Mason's stonecutter plans them: the
	 * mined stone named, never Nether Quartz. (The crafting table's and the kiln's plans go down to cobblestone and quartz;
	 * a Mason's line for these uses the stonecutter's, below.)
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaStone1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaStone1115bLogic")
	public void minedStonesNameTheStoneNotQuartz(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, Item> stones = Map.of(Items.POLISHED_ANDESITE, Items.ANDESITE, Items.POLISHED_DIORITE, Items.DIORITE, Items.POLISHED_GRANITE,
			Items.GRANITE, Items.ANDESITE_SLAB, Items.ANDESITE, Items.POLISHED_ANDESITE_STAIRS, Items.ANDESITE, Items.DIORITE_WALL, Items.DIORITE);
		for (Crafting.Kind kind : List.of(Crafting.Kind.STONECUTTING)) {
			for (Map.Entry<Item, Item> e : stones.entrySet()) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, e.getKey(), 4, Map.of());
				if (lack.containsKey(Items.QUARTZ) || (!lack.isEmpty() && !lack.containsKey(e.getValue()))) {
					wrong.add(kind + " 4 " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath() + " from nothing lack " + names(lack)
						+ ", expected the " + BuiltInRegistries.ITEM.getKey(e.getValue()).getPath());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "mined stones' lines (maker of polished andesite: " + CraftJobs.maker(level, Items.POLISHED_ANDESITE) + "): " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 3 polished andesite with an empty chest and a Mason at his
	 * stonecutter reads a line naming Andesite, never Nether Quartz or Cobblestone; nothing is made.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaStone1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaStone1115bLine")
	public void aBuilderShortOfPolishedAndesiteReadsAndesite(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.POLISHED_ANDESITE);
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no stone anywhere");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 3 Polished Andesite")
				&& text.contains("Andesite to make") && !text.contains("Quartz") && !text.contains("Cobblestone"),
				"the builder's line: '" + text + "', expected it to name the Andesite");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 0, "the Mason made something from nothing");
			helper.succeed();
		});
	}

	private static String names(Map<Item, Integer> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}
}
