package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (B1018's fix, CrafterWork.benchlessTick) from the words of B353, B1009 and B1018 and docs/wiki/crafter.md: a crafting
 * job saved before B1009 (no `profession`) is stamped with his profession at the first look after loading, a job that
 * outlives its bench under another job is let go, and "a bench put back or moved within that time carries the job on".
 * Each test: a builder short of 3 Barrels with 6 Oak Logs in his chest, a Carpenter fetching the logs; things happen to
 * him (old-save reload, the bench broken, put back or moved); the test checks the job was never let go before he made
 * the barrels himself, the build is finished and no log, plank or barrel is left over. Setup from
 * {@link QaBagNewJob1107cGameTests} and {@link QaCrafterFurnitureGameTests}.
 */
public class QaOldSaveCrafter1107cGameTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos MOVED = new BlockPos(14, 2, 12);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final String CARPENTER_ID = String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.CARPENTER));

	/** One thing that happens once {@code when} (ticks since the step before) holds. */
	private record Step(String name, LongPredicate when, Runnable run) {
	}

	/** What a test records as it runs. */
	private static final class Run {
		final Villager[] v = new Villager[1];
		final List<Step> steps = new ArrayList<>();
		QaCrafterFurnitureGameTests.Asker asker;
		int at;
		long lastAt;
		long brokenAt = -1;
		long endAt = -1;
		int madeAtEnd = -1;
		int woodAtEnd = -1;
		String stamp = "(not looked at)";
		String stamp2 = "(not looked at)";

		Run step(String name, LongPredicate when, Runnable run) {
			steps.add(new Step(name, when, run));
			return this;
		}

		BuilderBag bag() {
			return ModAttachments.BUILDER_BAG.getOrCreate(v[0]);
		}

		String job() {
			CrafterJob j = ModAttachments.CRAFTER_JOB.get(v[0]);
			return j == null ? "none" : "'" + j.profession() + "' " + j.phase();
		}
	}

	// --- The tests ---------------------------------------------------------------------------------------------------

	/**
	 * The suspicion: an old-save job loaded on a novice Carpenter who's already jobless (his bench broken before the
	 * restart), then the bench put back within B353's wait so he's a Carpenter again. The wiki: "a bench put back ...
	 * within that time carries the job on". He should finish the barrels himself, the job never let go.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveJoblessBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveJoblessBenchBack")
	public void qaAnOldSaveJobOnAJoblessCarpenterCarriesOnWhenTheBenchIsPutBack1107c(GameTestHelper helper) {
		joblessThenBenchBack(helper, true, 100);
	}

	/** The control: the same with a job saved after B1009 (stamped Carpenter). */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cNewSaveJoblessBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cNewSaveJoblessBenchBack")
	public void qaANewSaveJobOnAJoblessCarpenterCarriesOnWhenTheBenchIsPutBack1107c(GameTestHelper helper) {
		joblessThenBenchBack(helper, false, 100);
	}

	/**
	 * The same old-save case with the bench put back late, 560 ticks after loading: still within B353's 600, so the wiki's
	 * "carries the job on" holds as it does for a new save (the next test), although the first look after loading stamps
	 * this job "minecraft:none" (he's jobless then).
	 */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveJoblessBenchBackLate"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveJoblessBenchBackLate")
	public void qaAnOldSaveJobOnAJoblessCarpenterCarriesOnWhenTheBenchIsPutBackLate1107c(GameTestHelper helper) {
		joblessThenBenchBack(helper, true, 560);
	}

	/** The control: a new-save job, the bench put back 560 ticks after loading. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cNewSaveJoblessBenchBackLate"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cNewSaveJoblessBenchBackLate")
	public void qaANewSaveJobOnAJoblessCarpenterCarriesOnWhenTheBenchIsPutBackLate1107c(GameTestHelper helper) {
		joblessThenBenchBack(helper, false, 560);
	}

	/** An old-save job on a novice Carpenter who keeps his bench: stamped Carpenter, never let go, finished by him. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveBenchKept"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveBenchKept")
	public void qaAnOldSaveJobOnACarpenterWithHisBenchIsFinished1107c(GameTestHelper helper) {
		Run r = start(helper, 0);
		r.step("he carries the logs, then the old save is loaded", t -> r.bag().count(Items.OAK_LOG) > 0, () -> reload(helper, r, true))
			.step("the first look stamps the job", t -> t >= 20, () -> r.stamp = stamp(r))
			.step("well past B353's wait with his bench", t -> t >= 700, () -> { });
		finish(helper, r, CARPENTER_ID, "an old-save carpenter who kept his bench");
	}

	/** An old-save job on a Carpenter with XP (keeps his profession benchless): the bench broken, put back after 200 ticks. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveXpBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveXpBenchBack")
	public void qaAnOldSaveJobOnAnExperiencedCarpenterCarriesOnWhenTheBenchIsPutBack1107c(GameTestHelper helper) {
		experienced(helper, false, STATION, "an old-save carpenter with XP whose bench was put back");
	}

	/** The same, the bench moved (put down elsewhere) instead of put back. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveXpBenchMoved"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveXpBenchMoved")
	public void qaAnOldSaveJobOnAnExperiencedCarpenterCarriesOnWhenTheBenchIsMoved1107c(GameTestHelper helper) {
		experienced(helper, false, MOVED, "an old-save carpenter with XP whose bench was moved");
	}

	/** The stamp is saved: reloaded again after it, the job still names the Carpenter, and bench broken and put back carries on. */
	//$ gametest_ticks_batch AREA '7000' '"qa1107cOldSaveStampReload"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qa1107cOldSaveStampReload")
	public void qaTheOldSaveStampIsSavedAndTheJobCarriesOnAfterAnotherReload1107c(GameTestHelper helper) {
		experienced(helper, true, STATION, "an old-save carpenter with XP, reloaded again after the stamp, whose bench was put back");
	}

	// --- Scenarios ---------------------------------------------------------------------------------------------------

	/** {@code backAfter}: ticks after loading the bench is put back (B353's wait is 600). */
	static void joblessThenBenchBack(GameTestHelper helper, boolean oldSave, int backAfter) {
		Run r = start(helper, 0); // never traded: vanilla takes his profession away with the bench
		r.step("he carries the logs, then the bench is broken", t -> r.bag().count(Items.OAK_LOG) > 0, () -> breakBench(helper, r))
			.step("vanilla makes him jobless, then the server restarts" + (oldSave ? " (old save)" : ""),
				t -> r.v[0].getVillagerData().getProfession() == VillagerProfession.NONE, () -> reload(helper, r, oldSave))
			.step("the first look after loading", t -> t >= 20, () -> r.stamp = stamp(r))
			.step("the bench put back and taken: a Carpenter again (" + backAfter + " ticks after loading)", t -> t >= backAfter - 20, () -> {
				benchBack(helper, r, STATION);
			})
			.step("well past B353's wait after loading", t -> t >= 700, () -> { });
		finish(helper, r, null, (oldSave ? "an old-save job" : "a new-save job") + " on a novice carpenter jobless at loading, bench put back " + backAfter + " ticks after loading");
	}

	static void experienced(GameTestHelper helper, boolean reloadAfterStamp, BlockPos benchTo, String who) {
		Run r = start(helper, 10);
		r.step("he carries the logs, then the old save is loaded", t -> r.bag().count(Items.OAK_LOG) > 0, () -> reload(helper, r, true))
			.step("the first look stamps the job", t -> t >= 20, () -> {
				r.stamp = stamp(r);
				if (reloadAfterStamp) {
					reload(helper, r, false);
					r.stamp2 = stamp(r); // as loaded, before any look
				}
				breakBench(helper, r);
			})
			.step("200 ticks benchless, then the bench put down", t -> t >= 200, () -> {
				helper.assertTrue(r.v[0].getVillagerData().getProfession() == ModVillagers.CARPENTER, who + ": a carpenter with XP lost his"
					+ " profession with the bench (" + r.v[0].getVillagerData().getProfession() + ")");
				benchBack(helper, r, benchTo);
			})
			.step("well past B353's wait after the break", t -> t >= 700, () -> { });
		if (reloadAfterStamp) {
			helper.onEachTick(() -> helper.assertTrue(r.at < 2 || r.stamp2.equals(CARPENTER_ID), who + ": the stamp after another reload is '"
				+ r.stamp2 + "', not '" + CARPENTER_ID + "'"));
		}
		finish(helper, r, CARPENTER_ID, who);
	}

	// --- Helpers -----------------------------------------------------------------------------------------------------

	static Run start(GameTestHelper helper, int xp) {
		QaCrafterFurnitureGameTests.prepare(helper);
		Run r = new Run();
		r.asker = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		r.v[0] = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		r.v[0].setVillagerXp(xp);
		return r;
	}

	private static void reload(GameTestHelper helper, Run r, boolean oldSave) {
		r.v[0] = QaBagNewJob1107cGameTests.reload(helper, r.v[0], oldSave);
		CrafterJob loaded = ModAttachments.CRAFTER_JOB.get(r.v[0]);
		helper.assertTrue(loaded != null && loaded.profession().isEmpty() == oldSave, "the crafting job as loaded: " + loaded);
	}

	private static String stamp(Run r) {
		CrafterJob j = ModAttachments.CRAFTER_JOB.get(r.v[0]);
		return j == null ? "(no job)" : j.profession();
	}

	private static void breakBench(GameTestHelper helper, Run r) {
		helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
		r.brokenAt = helper.getTick();
	}

	/** The bench put down at {@code at} and taken by him, as vanilla does when he walks up to it. */
	private static void benchBack(GameTestHelper helper, Run r, BlockPos at) {
		helper.setBlock(at, ModBlocks.CARPENTERS_BENCH);
		Jobs.employ(helper.getLevel(), r.v[0], helper.absolutePos(at), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
	}

	/**
	 * Runs {@code r}'s steps and checks the end: the job is gone only once he has made the 3 barrels himself (not let go
	 * with the wood sent back), {@code stamp} if not null was the first look's stamp, the build is finished, and no log,
	 * plank or barrel is left over.
	 */
	private static void finish(GameTestHelper helper, Run r, String stamp, String who) {
		helper.onEachTick(() -> {
			if (r.at < r.steps.size()) {
				Step s = r.steps.get(r.at);
				if (s.when().test(helper.getTick() - r.lastAt)) {
					s.run().run();
					r.lastAt = helper.getTick();
					r.at++;
				}
			}
			if (r.at > 0 && r.endAt < 0 && ModAttachments.CRAFTER_JOB.get(r.v[0]) == null) {
				r.endAt = helper.getTick();
				r.madeAtEnd = ModAttachments.ITEMS_CRAFTED.getOrElse(r.v[0], 0);
				r.woodAtEnd = QaBagNewJob1107cGameTests.reachable(helper, Items.OAK_LOG) + QaBagNewJob1107cGameTests.reachable(helper, Items.OAK_PLANKS);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.at == r.steps.size(), who + ": stuck before step '" + r.steps.get(Math.min(r.at, r.steps.size() - 1)).name()
				+ "' (job " + r.job() + ", job " + r.v[0].getVillagerData().getProfession() + ", bag " + r.bag().stacks() + ")");
			helper.assertTrue(r.endAt >= 0, who + ": his job is still held (" + r.job() + "), made " + ModAttachments.ITEMS_CRAFTED.getOrElse(r.v[0], 0));
			String base = r.brokenAt >= 0 ? " " + (r.endAt - r.brokenAt) + " ticks after the break" : "";
			helper.assertTrue(r.madeAtEnd == 3, who + ": his crafting job was let go" + base + " with " + r.madeAtEnd + " barrels made"
				+ " (logs and planks a player can reach then " + r.woodAtEnd + "; stamped '" + r.stamp + "' at the first look after loading; now "
				+ r.v[0].getVillagerData().getProfession() + ")");
			if (stamp != null) {
				helper.assertTrue(stamp.equals(r.stamp), who + ": the first look stamped '" + r.stamp + "', not '" + stamp + "'");
			}
			QaCrafterFurnitureGameTests.assertBuilt(helper, r.asker);
			int logs = QaBagNewJob1107cGameTests.reachable(helper, Items.OAK_LOG);
			int planks = QaBagNewJob1107cGameTests.reachable(helper, Items.OAK_PLANKS);
			int spare = QaBagNewJob1107cGameTests.reachable(helper, Items.BARREL);
			// 6 logs saw into 24 planks; 3 barrels take 18 planks and 6 slabs (3 planks): 3 planks are left over, in the chests
			helper.assertTrue(logs == 0 && planks == 3 && spare == 0 && r.bag().isEmpty(), who + ": after the build, logs " + logs
				+ ", planks " + planks + " (3 left over), spare barrels " + spare + ", his bag " + r.bag().stacks());
		});
	}
}
