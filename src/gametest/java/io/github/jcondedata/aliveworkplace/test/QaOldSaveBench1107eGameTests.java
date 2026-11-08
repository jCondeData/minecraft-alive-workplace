package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBagNewJob1107cGameTests.reachable;
import static io.github.jcondedata.aliveworkplace.test.QaBagNewJob1107cGameTests.reload;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1107-2212) for B1018's fix and B353, from their words: a crafting job saved before B1009 (no `profession`)
 * is "stamped with his profession when loaded", and "a bench put back (or moved) within that time carries on with the
 * job", old save or not. These load a novice Carpenter carrying the builder's six logs from an old save (and, as the
 * control, from a new one) and check the builder gets his three barrels exactly once, with no log left over:
 * <ul>
 * <li>his bench never goes: the old job is stamped Carpenter and he finishes it himself;</li>
 * <li>the bench was broken and he was jobless when the world was saved; a player puts it back 560 ticks after the load
 * (inside B353's 600 ticks from the bench going) and he takes it again: he carries on with the job, it isn't let go.</li>
 * </ul>
 */
public class QaOldSaveBench1107eGameTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final String CARPENTER_ID = "aliveworkplace:carpenter";

	/** An old save's job, his bench kept: stamped as a Carpenter's at the first look and finished by him, barrels made once. */
	//$ gametest_ticks_batch AREA '6000' '"qa1107eOldSaveKeepsBench"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107eOldSaveKeepsBench")
	public void qaAnOldSavesCarpenterWhoKeepsHisBenchFinishesTheJob1107e(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL,
			new ItemStack(Items.OAK_LOG, 6));
		Villager[] c = {QaCrafterFurnitureGameTests.carpenter(helper, STATION)};
		c[0].setVillagerXp(0);
		long[] loadedAt = {-1};
		String[] stamp = {null};
		boolean[] letGo = {false};
		helper.onEachTick(() -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(c[0]);
			if (loadedAt[0] < 0 && bag != null && bag.count(Items.OAK_LOG) > 0) {
				c[0] = reload(helper, c[0], true);
				loadedAt[0] = helper.getTick();
				return;
			}
			if (loadedAt[0] >= 0) {
				CrafterJob job = ModAttachments.CRAFTER_JOB.get(c[0]);
				if (job != null && stamp[0] == null && !job.profession().isEmpty()) {
					stamp[0] = job.profession();
				}
				if (job == null && ModAttachments.ITEMS_CRAFTED.getOrElse(c[0], 0) < 3) {
					letGo[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(loadedAt[0] >= 0, "the novice carpenter never carried the logs");
			helper.assertTrue(CARPENTER_ID.equals(stamp[0]), "the old save's crafting job should be stamped " + CARPENTER_ID
				+ " once he works again, was " + stamp[0]);
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(c[0], 0);
			helper.assertTrue(made == 3 && !letGo[0], "loaded from an old save with his bench standing, the carpenter made "
				+ made + " barrels (3 expected)" + (letGo[0] ? " and let his job go before it was done" : ""));
			int logs = reachable(helper, Items.OAK_LOG);
			int spare = reachable(helper, Items.BARREL);
			helper.assertTrue(logs == 0 && spare == 0 && ModAttachments.BUILDER_BAG.getOrCreate(c[0]).isEmpty(), "after the build, logs left "
				+ logs + ", spare barrels " + spare + ", his bag " + ModAttachments.BUILDER_BAG.getOrCreate(c[0]).stacks());
		});
	}

	/** Old save, jobless at the save, bench put back after the load: he carries on with the job (B353), as from a new save. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107eOldSaveBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107eOldSaveBenchBack")
	public void qaAnOldSavesJoblessCarpenterGivenHisBenchBackCarriesOn1107e(GameTestHelper helper) {
		benchBack(helper, true);
	}

	/** The control: the same from a save made after B1009. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107eNewSaveBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107eNewSaveBenchBack")
	public void qaAJoblessCarpenterReloadedAndGivenHisBenchBackCarriesOn1107e(GameTestHelper helper) {
		benchBack(helper, false);
	}

	static void benchBack(GameTestHelper helper, boolean oldSave) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL,
			new ItemStack(Items.OAK_LOG, 6));
		Villager[] c = {QaCrafterFurnitureGameTests.carpenter(helper, STATION)};
		c[0].setVillagerXp(0); // never traded: vanilla takes his profession away with the bench
		long[] brokenAt = {-1};
		long[] loadedAt = {-1};
		long[] backAt = {-1};
		long[] letGoAt = {-1};
		int[] logsAtLoad = {-1};
		String[] stamp = {null};
		helper.onEachTick(() -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(c[0]);
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(c[0]);
			if (loadedAt[0] >= 0 && stamp[0] == null && job != null && !job.profession().isEmpty()) {
				stamp[0] = job.profession();
			}
			if (brokenAt[0] < 0 && bag != null && bag.count(Items.OAK_LOG) > 0) {
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
				brokenAt[0] = helper.getTick();
				return;
			}
			if (brokenAt[0] >= 0 && loadedAt[0] < 0 && c[0].getVillagerData().getProfession() == VillagerProfession.NONE) {
				helper.assertTrue(ModAttachments.CRAFTER_JOB.get(c[0]) != null, "the job went before the save, "
					+ (helper.getTick() - brokenAt[0]) + " ticks after the bench (B353 waits 600)");
				c[0] = reload(helper, c[0], oldSave); // the world is saved and opened again while he's jobless
				loadedAt[0] = helper.getTick();
				logsAtLoad[0] = reachable(helper, Items.OAK_LOG);
				return;
			}
			if (loadedAt[0] >= 0 && backAt[0] < 0 && helper.getTick() >= loadedAt[0] + 560) {
				helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH); // the player puts it back
				Jobs.employ(helper.getLevel(), c[0], helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
				backAt[0] = helper.getTick();
			}
			// let go: the job is gone before the barrels are made, or the logs he carried are back in the chests (he may take
			// the freed request again in the same tick, so the job alone can't tell)
			if (loadedAt[0] >= 0 && letGoAt[0] < 0 && ModAttachments.ITEMS_CRAFTED.getOrElse(c[0], 0) < 3
				&& (ModAttachments.CRAFTER_JOB.get(c[0]) == null || reachable(helper, Items.OAK_LOG) > logsAtLoad[0])) {
				letGoAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			String who = (oldSave ? "loaded from an old save" : "reloaded") + " while jobless, his bench put back 560 ticks later";
			helper.assertTrue(brokenAt[0] >= 0, "the novice carpenter never carried the logs");
			helper.assertTrue(loadedAt[0] >= 0, "vanilla never made the novice carpenter jobless after the bench went (job "
				+ c[0].getVillagerData().getProfession() + ")");
			helper.assertTrue(backAt[0] >= 0, "the bench isn't back yet");
			helper.assertTrue(letGoAt[0] < 0, who + " (" + (backAt[0] - brokenAt[0]) + " ticks after it went), the carpenter let the"
				+ " builder's job go " + (letGoAt[0] - backAt[0]) + " ticks after he had it back (the job was stamped '" + stamp[0]
				+ "' after the load; he is a " + c[0].getVillagerData().getProfession() + " now)");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(c[0], 0);
			helper.assertTrue(made == 3, who + ": he made " + made + " barrels, not 3");
			int logs = reachable(helper, Items.OAK_LOG);
			int spare = reachable(helper, Items.BARREL);
			helper.assertTrue(logs == 0 && spare == 0 && ModAttachments.BUILDER_BAG.getOrCreate(c[0]).isEmpty(), who + ": after the build, logs left "
				+ logs + ", spare barrels " + spare + ", his bag " + ModAttachments.BUILDER_BAG.getOrCreate(c[0]).stacks());
		});
	}
}
