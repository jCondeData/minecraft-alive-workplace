package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-a-1103c) verifying three builder fixes from their spec (builder.md), past what their own tests cover:
 * <ul>
 * <li>B868 (a spot refilled after every dig): refilled exactly 16 times (the threshold) and 20 times, then left alone, it is
 * dug out on the retry with nothing skipped; given up, saved and loaded, with sand dropped elsewhere so a later CLEAR pass
 * runs, the given-up spot is never dug again and is counted once; two spots refilled for ever are both skipped and the hut
 * finishes.</li>
 * <li>B873 (powder snow gives its bucket back): a mixed water and powder snow build paid from a chest gives one bucket back
 * per pour; powder snow paid partly from the builder's bag too; a stone-only build leaves no bucket nor anything else.</li>
 * <li>B879 (a Yes after the hole was mended by hand): one of two holes mended is still agreed and repaired; mended, Yes
 * (gone), then broken again is asked about afresh; mended then Not now comes off the list at the builder's next look and the
 * next damage is asked afresh; mended then broken elsewhere before the Yes is agreed and repaired; the Repairs page's Yes on
 * a mended build answers like the chat's.</li>
 * </ul>
 */
public class QaBuilderFixes1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = QaRepairAsks1103bGameTests.AREA;
	private static final BlockPos SPOT_A = QaBuilderRestartClear1103bGameTests.SPOT_A;
	private static final BlockPos SPOT_B = QaBuilderRestartClear1103bGameTests.SPOT_B;
	/** builder.md: one spot dug this many times on a list is deferred. */
	private static final int MOST = 16;
	private static final String GONE = "That build is gone, or nothing is missing from it any more.";

	// ============================================================================================================ B868

	/** What one refill run saw. */
	private static final class Seen {
		final int[] digs = new int[2]; // per spot: times it was found dug (and put back) during CLEAR
		long leftAt = -1;
		int skippedAtExit = -1;
		int passesAtExit = -1;
		long gaveUpAt = -1;
		int digsAfterGiveUp;
		int skippedAtGiveUp = -1;
		boolean reloaded;
		boolean refilledAfterReload;
		int skippedAfterReload = -1;
		boolean bDropped;
	}

	/**
	 * The hut of QaBuilderTerrain1102GameTests on flat ground, sand standing on {@code spots}. While the site is in CLEAR,
	 * each spot found dug is given its sand back, up to {@code refills} times (-1: for ever). With {@code giveUpReload}
	 * the world is saved and loaded the first tick spot A reads as given up ({@code refilled}), and at A's 18th dig (on the
	 * retry, so the list's cursor is past B) sand is dropped once on B so a later pass over CLEAR has something to dig.
	 */
	private static QaBuilderTerrain1102GameTests.Run refill(GameTestHelper helper, List<BlockPos> spots, int refills, boolean giveUpReload, Seen seen) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		for (BlockPos p : spots) {
			helper.setBlock(p, Blocks.SAND);
		}
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BlockPos absA = helper.absolutePos(SPOT_A);
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s == null || seen.leftAt >= 0) {
				return;
			}
			if (giveUpReload && seen.gaveUpAt < 0 && s.refilled(absA)) {
				seen.gaveUpAt = helper.getTick();
				seen.skippedAtGiveUp = s.skipped();
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
				seen.reloaded = true;
				BuildSite back = r.site();
				seen.refilledAfterReload = back != null && back.refilled(absA);
				seen.skippedAfterReload = back == null ? -1 : back.skipped();
				LOGGER.info("qa 1103c b868: A given up at {} after {} digs (stage {}, skipped {}), reloaded: refilled {}, skipped {}", seen.gaveUpAt,
					seen.digs[0], s.stage(), seen.skippedAtGiveUp, seen.refilledAfterReload, seen.skippedAfterReload);
				s = back;
				if (s == null) {
					return;
				}
			}
			if (s.stage() != BuildPlan.Stage.CLEAR) {
				seen.leftAt = helper.getTick();
				seen.skippedAtExit = s.skipped();
				seen.passesAtExit = s.clearPasses();
				LOGGER.info("qa 1103c b868: CLEAR left at {} for {}, digs {}, skipped {}, passes {}, digs after give-up {}", seen.leftAt, s.stage(),
					java.util.Arrays.toString(seen.digs), seen.skippedAtExit, seen.passesAtExit, seen.digsAfterGiveUp);
				return;
			}
			for (int i = 0; i < spots.size(); i++) {
				BlockPos p = spots.get(i);
				if (helper.getBlockState(p).isAir() && (refills < 0 || seen.digs[i] < refills)) {
					if (i == 0 && seen.gaveUpAt >= 0) {
						seen.digsAfterGiveUp++;
					}
					helper.setBlock(p, Blocks.SAND);
					seen.digs[i]++;
				}
			}
			if (giveUpReload && !seen.bDropped && seen.digs[0] >= MOST + 2) {
				helper.setBlock(SPOT_B, Blocks.SAND);
				seen.bDropped = true;
			}
			helper.assertTrue(helper.getTick() < 9000, "expected CLEAR to end, but the builder is still in it at tick " + helper.getTick() + " after digs "
				+ java.util.Arrays.toString(seen.digs) + ": " + QaBuilderTerrain1102GameTests.state(helper, r));
		});
		return r;
	}

	/**
	 * Sand put back exactly 16 times (the threshold), then left alone: the spot is deferred with sand on it, the retry digs
	 * it once and it stays clear, so nothing is skipped and the hut finishes.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1103cRefill16"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1103cRefill16")
	public void aSpotRefilledSixteenTimesThenLeftIsDugOutOnTheRetry(GameTestHelper helper) {
		Seen seen = new Seen();
		QaBuilderTerrain1102GameTests.Run r = refill(helper, List.of(SPOT_A), MOST, false, seen);
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.leftAt >= 0, "expected CLEAR to be left, still in it after " + seen.digs[0] + " refills");
			helper.assertTrue(seen.digs[0] == MOST, "setup: expected the sand put back " + MOST + " times, it was " + seen.digs[0]);
			helper.assertTrue(seen.skippedAtExit == 0, "expected nothing skipped (the spot stopped being refilled before its retry), saw skipped "
				+ seen.skippedAtExit);
			helper.assertTrue(helper.getBlockState(SPOT_A).isAir() || !helper.getBlockState(SPOT_A).is(Blocks.SAND),
				"expected the sand at A dug out, saw " + helper.getBlockState(SPOT_A));
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/**
	 * Sand put back 20 times, then left alone: deferred after 16, the retry digs it the last 4 times and it stays clear, so
	 * nothing is skipped (it wasn't still being refilled when given its chance).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1103cRefill20"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1103cRefill20")
	public void aSpotRefilledTwentyTimesThenLeftIsClearedWithNothingSkipped(GameTestHelper helper) {
		Seen seen = new Seen();
		QaBuilderTerrain1102GameTests.Run r = refill(helper, List.of(SPOT_A), 20, false, seen);
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.leftAt >= 0, "expected CLEAR to be left, still in it after " + seen.digs[0] + " refills");
			helper.assertTrue(seen.digs[0] == 20, "setup: expected the sand put back 20 times, it was " + seen.digs[0]);
			helper.assertTrue(seen.skippedAtExit == 0, "expected nothing skipped (refills stopped on the retry and it was dug out), saw skipped "
				+ seen.skippedAtExit);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/**
	 * Sand put back for ever at A until it is given up; the world is saved and loaded at once, and sand dropped on B during
	 * A's retry makes CLEAR go back over its list. After the load A is still given up and counted once; the later pass digs
	 * B but never A again, skipped stays 1, and the hut finishes.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1103cGivenUpReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1103cGivenUpReload")
	public void aGivenUpSpotStaysGivenUpThroughAReloadAndALaterPass(GameTestHelper helper) {
		Seen seen = new Seen();
		QaBuilderTerrain1102GameTests.Run r = refill(helper, List.of(SPOT_A), -1, true, seen);
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.bDropped, "setup: sand was never dropped on B (A dug " + seen.digs[0] + " times)");
			helper.assertTrue(seen.reloaded, "expected A to be given up and the world reloaded, A dug " + seen.digs[0] + " times: "
				+ QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(seen.skippedAtGiveUp == 1, "expected A counted once in skipped when given up, saw " + seen.skippedAtGiveUp);
			helper.assertTrue(seen.refilledAfterReload, "expected A still saved as refilled after the reload, it isn't");
			helper.assertTrue(seen.skippedAfterReload == 1, "expected skipped 1 after the reload, saw " + seen.skippedAfterReload);
			helper.assertTrue(seen.leftAt >= 0, "expected CLEAR to be left, still in it");
			helper.assertTrue(seen.digsAfterGiveUp == 0, "expected the given-up spot A left be, but the builder dug it " + seen.digsAfterGiveUp
				+ " more time(s) after giving it up (passes " + seen.passesAtExit + ")");
			helper.assertTrue(seen.skippedAtExit == 1, "expected A counted once in skipped, saw " + seen.skippedAtExit + " at the end of CLEAR");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/** Two spots each refilled for ever: both deferred, both given up on the retry, skipped == 2, and the hut finishes. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1103cTwoRefilled"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1103cTwoRefilled")
	public void twoRefilledSpotsAreBothSkippedAndTheBuildFinishes(GameTestHelper helper) {
		Seen seen = new Seen();
		QaBuilderTerrain1102GameTests.Run r = refill(helper, List.of(SPOT_A, SPOT_B), -1, false, seen);
		helper.succeedWhen(() -> {
			helper.assertTrue(seen.leftAt >= 0, "expected CLEAR to be left, still in it after digs " + java.util.Arrays.toString(seen.digs));
			helper.assertTrue(seen.digs[0] <= 2 * MOST + 2 && seen.digs[1] <= 2 * MOST + 2, "expected each spot dug at most " + (2 * MOST + 2)
				+ " times, saw " + java.util.Arrays.toString(seen.digs));
			helper.assertTrue(seen.skippedAtExit == 2, "expected both refilled spots counted in skipped (2), saw " + seen.skippedAtExit);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	// ============================================================================================================ B873

	/** A stone basin: a water source in one cell, powder snow in the other and one more on the middle wall. */
	private static QaScanRebuild1102dGameTests.Scene waterAndSnow() {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				s.put(dx, 1, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(3, 1, 1, Blocks.POWDER_SNOW);
		s.put(2, 2, 1, Blocks.POWDER_SNOW);
		return s;
	}

	private static Villager builderOf(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
	}

	private static String contents(Container chest, Villager builder) {
		Map<Item, Integer> all = new HashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack st = chest.getItem(i);
			if (!st.isEmpty()) {
				all.merge(st.getItem(), st.getCount(), Integer::sum);
			}
		}
		if (builder != null) {
			for (ItemStack st : ModAttachments.BUILDER_BAG.getOrCreate(builder).stacks()) {
				if (!st.isEmpty()) {
					all.merge(st.getItem(), st.getCount(), Integer::sum);
				}
			}
		}
		all.remove(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT); // the blueprint handed back after the job
		return all.toString();
	}

	/** Water and two powder snow paid from a chest: three empty buckets back (one per pour), no full one left. */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103cWaterSnow"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103cWaterSnow")
	public void waterAndPowderSnowPaidFromAChestGiveABucketBackPerPour(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, waterAndSnow());
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.WATER_BUCKET, 0) == 1 && asked.getOrDefault(Items.POWDER_SNOW_BUCKET, 0) == 2,
			"setup: expected the list to ask 1 water and 2 powder snow buckets, it asks " + asked);
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = builderOf(helper);
			int buckets = QaScanItemless1103GameTests.held(chest, builder, Items.BUCKET);
			helper.assertTrue(buckets == 3, "expected 3 empty buckets back (1 water, 2 powder snow poured), saw " + buckets + "; chest and bag: "
				+ contents(chest, builder));
			int full = QaScanItemless1103GameTests.held(chest, builder, Items.POWDER_SNOW_BUCKET) + QaScanItemless1103GameTests.held(chest, builder, Items.WATER_BUCKET);
			helper.assertTrue(full == 0, "expected every full bucket poured, " + full + " left");
		});
	}

	/** Two powder snow, one bucket in the builder's bag and one in the chest: both come back as empty buckets. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103cSnowBag"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103cSnowBag")
	public void powderSnowPaidPartlyFromTheBagGivesEveryBucketBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POWDER_SNOW);
		s.put(1, 1, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = new HashMap<>(b.b().site().plan(level).materials());
		helper.assertTrue(asked.getOrDefault(Items.POWDER_SNOW_BUCKET, 0) == 2, "setup: expected the list to ask 2 powder snow buckets, it asks " + asked);
		asked.put(Items.POWDER_SNOW_BUCKET, 1);
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		Villager builder = builderOf(helper);
		helper.assertTrue(builder != null, "setup: no builder");
		ModAttachments.BUILDER_BAG.getOrCreate(builder).addAll(Items.POWDER_SNOW_BUCKET, 1);
		helper.assertTrue(QaScanItemless1103GameTests.held(chest, builder, Items.POWDER_SNOW_BUCKET) == 2, "setup: expected 2 powder snow buckets, one in the bag");
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager now = builderOf(helper);
			int buckets = QaScanItemless1103GameTests.held(chest, now, Items.BUCKET);
			helper.assertTrue(buckets == 2, "expected 2 empty buckets back (one bag-paid, one chest-paid), saw " + buckets + "; chest and bag: "
				+ contents(chest, now));
			int full = QaScanItemless1103GameTests.held(chest, now, Items.POWDER_SNOW_BUCKET);
			helper.assertTrue(full == 0, "expected both powder snow buckets poured, " + full + " left");
		});
	}

	/** A stone-only build paid from a chest leaves nothing behind: no bucket, no other item. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103cStoneOnly"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103cStoneOnly")
	public void anOrdinaryBlockLeavesNothingExtra(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.STONE);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Container chest = QaScanItemless1103GameTests.stock(helper, b.b().site().plan(level).materials());
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = builderOf(helper);
			int buckets = QaScanItemless1103GameTests.held(chest, builder, Items.BUCKET);
			helper.assertTrue(buckets == 0, "expected no bucket from a stone build, saw " + buckets);
			String left = contents(chest, builder);
			helper.assertTrue(left.equals("{}"), "expected the chest and bag empty (the blueprint aside) after a build paid exactly, saw " + left);
		});
	}

	// ============================================================================================================ B879

	private static long questions(QaRepairAsks1103bGameTests.Stage s) {
		return s.seen().stream().filter(c -> c.getString().contains("Repair it?")).count();
	}

	/** Since message {@code from}, the owner was told the agreed sentence and never the gone one. */
	private static void assertAgreed(GameTestHelper helper, QaRepairAsks1103bGameTests.Stage s, int from) {
		List<String> said = s.seen().subList(from, s.seen().size()).stream().map(c -> c.getString()).toList();
		String agreed = "Yes: a builder will repair the " + s.name() + ".";
		helper.assertTrue(said.contains(agreed) && !said.contains(GONE), "expected the agreed sentence (\"" + agreed + "\") and not the gone one, saw " + said);
	}

	/** Two holes, one mended by hand, then Yes: still agreed (one block is still missing), a repair starts and fills the other. */
	//$ gametest_ticks_batch AREA '800' '"qa1103cHalfMended"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qa1103cHalfMended")
	public void aYesAfterOneOfTwoHolesWasMendedIsStillAgreed(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1, 3);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			RepairAsks.Ask a = QaRepairAsks1103bGameTests.ask(helper, s);
			helper.assertTrue(a != null && a.state() == RepairAsks.State.ASKED, "expected a question, saw " + a);
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(1, 0, 0), Blocks.OAK_PLANKS); // one hole mended by hand
			int before = s.seen().size();
			helper.assertTrue(QaRepairAsks1103bGameTests.click(helper, s.owner(), "yes", s.at()) == 1, "expected the yes accepted, it was refused");
			assertAgreed(helper, s, before);
			a = QaRepairAsks1103bGameTests.ask(helper, s);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "expected a repair started with one hole still open, none; ask " + a);
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, QaRepairAsks1103bGameTests.WALL.offset(3, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "expected the repair over, still repairing");
			helper.assertTrue(QaRepairAsks1103bGameTests.ask(helper, s) == null && !QaRepairAsks1103bGameTests.onPage(helper, s),
				"expected the repaired wall off the list, saw " + QaRepairAsks1103bGameTests.ask(helper, s));
		});
	}

	/** Mended, Yes (gone, off the list), then broken again: the next look asks afresh and the wall is back on the page. */
	//$ gametest_ticks_batch AREA '200' '"qa1103cMendedBrokenAgain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103cMendedBrokenAgain")
	public void aWallMendedAndThenBrokenAgainIsAskedAboutAfresh(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			helper.assertTrue(QaRepairAsks1103bGameTests.ask(helper, s) != null, "expected a question, none");
			long asked = questions(s);
			helper.assertTrue(asked == 1, "setup: expected one question in chat, saw " + asked);
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(1, 0, 0), Blocks.OAK_PLANKS);
			QaRepairAsks1103bGameTests.click(helper, s.owner(), "yes", s.at());
			helper.assertTrue(QaRepairAsks1103bGameTests.last(s.seen()).equals(GONE), "expected the gone sentence, saw: " + QaRepairAsks1103bGameTests.last(s.seen()));
			helper.assertFalse(QaRepairAsks1103bGameTests.onPage(helper, s), "expected the mended wall off the page");
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(5, 0, 0), Blocks.AIR); // broken again later
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			RepairAsks.Ask a = QaRepairAsks1103bGameTests.ask(helper, s);
			helper.assertTrue(a != null && a.state() == RepairAsks.State.ASKED, "expected the new damage asked about, saw " + a);
			helper.assertTrue(QaRepairAsks1103bGameTests.onPage(helper, s), "expected the wall back on the Repairs page, it isn't");
			helper.assertTrue(questions(s) == asked + 1, "expected a second question in chat, saw " + questions(s) + " in all");
			helper.assertTrue(QaRepairAsks1103bGameTests.click(helper, s.owner(), "yes", s.at()) == 1, "expected the new yes accepted");
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "expected the new yes to start a repair, none");
			helper.succeed();
		});
	}

	/**
	 * Mended by hand, then Not now: no repair starts, and (builder.md: "put right by hand meanwhile, it comes off the list,
	 * and the next damage is asked about afresh") the builder's next look takes it off the list; new damage is asked afresh.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1103cMendedNotNow"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103cMendedNotNow")
	public void aMendedWallLeftForNowComesOffTheListAndNewDamageIsAskedAfresh(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			helper.assertTrue(QaRepairAsks1103bGameTests.ask(helper, s) != null, "expected a question, none");
			long asked = questions(s);
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(1, 0, 0), Blocks.OAK_PLANKS);
			QaRepairAsks1103bGameTests.click(helper, s.owner(), "notnow", s.at());
			String reply = QaRepairAsks1103bGameTests.last(s.seen());
			QaRepairAsks1103bGameTests.plain(helper, reply);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()),
				"expected no repair after Not now, one started");
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "expected no repair at the next look, one started");
			helper.assertFalse(QaRepairAsks1103bGameTests.onPage(helper, s), "expected the mended wall off the list after the builder's look, still there: "
				+ QaRepairAsks1103bGameTests.ask(helper, s));
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(5, 0, 0), Blocks.AIR);
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			RepairAsks.Ask a = QaRepairAsks1103bGameTests.ask(helper, s);
			helper.assertTrue(a != null && a.state() == RepairAsks.State.ASKED, "expected the next damage asked about afresh, saw " + a);
			helper.assertTrue(questions(s) == asked + 1, "expected a new question in chat, saw " + questions(s) + " (had " + asked + ")");
			helper.succeed();
		});
	}

	/** Mended by hand, then another block broken before the Yes: the Yes is agreed and the repair puts the new hole back. */
	//$ gametest_ticks_batch AREA '800' '"qa1103cMendedRebroken"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qa1103cMendedRebroken")
	public void aWallMendedThenBrokenBeforeTheYesIsStillRepaired(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			helper.assertTrue(QaRepairAsks1103bGameTests.ask(helper, s) != null, "expected a question, none");
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(1, 0, 0), Blocks.OAK_PLANKS); // mended
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(6, 0, 0), Blocks.AIR); // and broken elsewhere
			int before = s.seen().size();
			helper.assertTrue(QaRepairAsks1103bGameTests.click(helper, s.owner(), "yes", s.at()) == 1, "expected the yes accepted");
			assertAgreed(helper, s, before);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "expected a repair started, none; ask " + QaRepairAsks1103bGameTests.ask(helper, s));
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, QaRepairAsks1103bGameTests.WALL.offset(6, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "expected the repair over, still repairing");
			helper.assertFalse(QaRepairAsks1103bGameTests.onPage(helper, s), "expected the repaired wall off the list, saw " + QaRepairAsks1103bGameTests.ask(helper, s));
		});
	}

	/** The Repairs page's "Yes, repair it" on a wall mended by hand answers as the chat's: the gone sentence, no repair, off the list. */
	//$ gametest_ticks_batch AREA '200' '"qa1103cMendedPageYes"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103cMendedPageYes")
	public void thePagesYesOnAMendedWallPromisesNoRepair(GameTestHelper helper) {
		QaRepairAsks1103bGameTests.Stage s = QaRepairAsks1103bGameTests.stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.look(helper, s.builder());
			ChoiceMenu menu = QaRepairAsks1103bGameTests.openPage(helper, s.owner());
			menu.press(QaRepairAsks1103bGameTests.rowOf(helper, s), s.owner());
			helper.setBlock(QaRepairAsks1103bGameTests.WALL.offset(1, 0, 0), Blocks.OAK_PLANKS); // mended while the page is open
			menu.press(RepairsPage.YES, s.owner());
			String reply = QaRepairAsks1103bGameTests.last(s.seen());
			helper.assertTrue(reply.equals(GONE), "expected the gone sentence from the page's Yes, saw: " + reply);
			RepairAsks.Ask a = QaRepairAsks1103bGameTests.ask(helper, s);
			helper.assertTrue(a == null || a.state() != RepairAsks.State.YES, "expected no YES set, saw " + a);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "expected no repair, one started");
			helper.assertFalse(QaRepairAsks1103bGameTests.onPage(helper, s), "expected the mended wall off the Repairs page, still there: " + a);
			helper.succeed();
		});
	}
}
