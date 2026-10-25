package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, the sentences players read; kin of B413): the line above a worker's head counts what they've done, and
 * after the first one it must read "1 expedition", not "1 expeditions" (ROADMAP 24.5: "1 parcel", "2 parcels"). The
 * Porter's title already has its singular ({@code porter.title.one}); the other counting titles don't.
 */
public class QaWorkerTitlesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The counting titles above workers' heads (each filled with one count that starts at 0 and goes up by one). */
	private static final List<String> COUNTING_TITLES = List.of(
		"message.aliveworkplace.netherworker.title", "message.aliveworkplace.explorer.title",
		"message.aliveworkplace.teacher.title", "message.aliveworkplace.beekeeper.title",
		"message.aliveworkplace.lumberjack.title", "message.aliveworkplace.innkeeper.title",
		"message.aliveworkplace.daycare_keeper.title", "message.aliveworkplace.camp_cook.title",
		"message.aliveworkplace.alchemist.title", "message.aliveworkplace.herder.title",
		"message.aliveworkplace.florist.title", "message.aliveworkplace.berry_breeder.title",
		"message.aliveworkplace.gem_grower.title", "message.aliveworkplace.smelter.title");

	/** A Netherworker back from their first trip, with no portal for another: the line above their head. */
	//$ gametest_ticks_batch AREA '200' '"qaWorkerTitleOne"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWorkerTitleOne")
	public void aNetherworkerAfterOneTripReadsOneExpedition(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos brazier = new BlockPos(2, 2, 2);
		helper.setBlock(brazier, ModBlocks.NETHER_BRAZIER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		((net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 4))).setItem(0, new ItemStack(Items.BREAD, 3));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(brazier), ModVillagers.NETHER_BRAZIER_POI, ModVillagers.NETHERWORKER);
		ModAttachments.NETHER_TRIPS.set(villager, 1);
		helper.succeedWhen(() -> {
			WorkerStatus.Entry status = WorkerStatus.get(villager, helper.getLevel().getGameTime());
			helper.assertTrue(status != null, "no line above the netherworker's head yet");
			String title = status.title().getString();
			helper.assertTrue(title.equals("Netherworker · 1 expedition"), "the line above the head after one trip: '" + title + "'");
		});
	}

	/** Every counting title has its singular form, as every other counted sentence does. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCountingTitleHasItsOneForm(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (String key : COUNTING_TITLES) {
			helper.assertTrue(lang.has(key), "no such title: " + key);
			if (!lang.has(key + ".one")) {
				missing.add(key + " ('" + lang.getOrDefault(key).replace("%s", "1") + "')");
			}
		}
		helper.assertTrue(missing.isEmpty(), missing.size() + " of " + COUNTING_TITLES.size() + " titles read wrong at 1: " + missing);
		helper.succeed();
	}
}
