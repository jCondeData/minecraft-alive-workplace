package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1107-0338) for scaffolding (ROADMAP 35.6, 35.6a), written from docs/wiki/scaffolding.md, not the code, for
 * what its "Known limits" call untested and what no earlier class checks:
 * <ul>
 * <li>a Carpenter in the village making the scaffolding a build asked for, from bamboo and string, after the build
 * already skipped its step without it ("Whether it makes scaffolding for a build that already skipped its step without
 * it isn't tested"; "Not tested here: a carpenter crafting it end to end");</li>
 * <li>the player's way to get the skipped part built: the scaffolding the Carpenter made, then the build started again
 * ("put scaffolding in the chests to have such steps built");</li>
 * <li>"only the lead builder does it (helpers leave such blocks to the lead)": the helper never carries or climbs any;</li>
 * <li>free materials with a player's own scaffolding in the chest: "none taken from or added to the chests";</li>
 * <li>a player breaking the column under the builder while he is high up it: none lost, the build finishes;</li>
 * <li>what the builder puts up is recorded, fetched "up to 16 at a time", never put up in the landscaping stage, and
 * taken down "from the top, a block at a time".</li>
 * </ul>
 * Same 14-high test pillar with its 5 x 5 cap as {@link QaScaffoldingGameTests}. Each test has its own batch (the
 * crafters' claims and the village are shared state).
 */
public class QaScaffolding1107GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	/** The Carpenter's bench: in the village, well clear of the pillar and the ground its landscaping levels. */
	static final BlockPos STATION = new BlockPos(1, 2, 18);
	static final int BAMBOO = 36;
	static final int STRING = 6;

	record Run(QaScaffoldingGameTests.Site s, Villager carpenter, long start) {
		long tick() {
			return s.level().getGameTime() - start;
		}
	}

	/**
	 * The pillar with no scaffolding anywhere, but 36 bamboo and 6 string in the builder's chest and a Carpenter at his
	 * bench in the village. The pillar's top is skipped and scaffolding is asked for (the spec: the build never waits);
	 * then the Carpenter makes it by the vanilla recipe (6 bamboo and a string make 6) into the builder's chest, and once
	 * it is there the request goes. Nothing is lost: what was made is what the bamboo and string used make, all of it
	 * ends in the chest, and none stands.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaf1107Carpenter"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaf1107Carpenter")
	public void aCarpenterMakesTheScaffoldingABuildThatSkippedItsStepAskedFor(GameTestHelper helper) {
		Run run = carpenterRun(helper);
		Tracker t = track(helper, run);
		helper.succeedWhen(() -> {
			helper.assertTrue(t.askedAt >= 0, "scaffolding was never asked for: missing " + run.s().site().missing());
			helper.assertTrue(t.skippedWhenAsked > 0 || run.s().site().skipped() > 0, "nothing was skipped without scaffolding");
			helper.assertTrue(t.madeAt >= 0, "the Carpenter never made scaffolding for the build that asked for " + t.asked
				+ " at tick " + t.askedAt + " (bamboo " + count(helper, Items.BAMBOO) + ", string " + count(helper, Items.STRING)
				+ ", build " + (QaCarpenterStorehouse1101GameTests.done(helper, run.s().site()) ? "ended at tick " + t.endedAt : "still going")
				+ ", job " + ModAttachments.CRAFTER_JOB.get(run.carpenter()) + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.s().site()), "still building: stage " + run.s().site().stage()
				+ " status " + run.s().site().status() + " missing " + run.s().site().missing());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "the Carpenter is still on a job");
			assertMadeAndKept(helper, run);
			helper.assertTrue(t.clearedAt >= 0 || t.endedAt >= 0 && t.endedAt <= t.inChestAt, "scaffolding reached the chest at tick " + t.inChestAt
				+ " but the build still asked for it");
			LOG.info("[QA scaf1107 carpenter] asked {} at {} (skipped {}), made at {}, in chest at {}, request gone at {}, build ended at {}",
				t.asked, t.askedAt, t.skippedWhenAsked, t.madeAt, t.inChestAt, t.clearedAt, t.endedAt);
		});
	}

	/**
	 * The whole loop the wiki gives a player: the pillar is built without scaffolding (its top skipped, scaffolding asked
	 * for), the Carpenter makes it from the bamboo and string, and the player starts the same build again. This time the
	 * builder scaffolds up to the missing top: the pillar and cap are finished with nothing skipped, no scaffolding is
	 * left standing and every scaffold the Carpenter made is back in the chest.
	 */
	//$ gametest_ticks_batch BIG '20000' '"qaScaf1107Again"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaScaf1107Again")
	public void theScaffoldingTheCarpenterMadeBuildsTheSkippedTopOnTheNextGo(GameTestHelper helper) {
		Run run = carpenterRun(helper);
		Tracker t = track(helper, run);
		BuildSite[] again = {null};
		int[] most = {0};
		helper.onEachTick(() -> {
			if (again[0] == null && t.madeAt >= 0 && QaCarpenterStorehouse1101GameTests.done(helper, run.s().site())
				&& ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null && QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) > 0) {
				ServerLevel level = helper.getLevel();
				again[0] = Builders.start(level, run.s().builder(), null, QaScaffoldingGameTests.PILLAR, new BlueprintData.Placement(
					level.dimension().location(), helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT), Rotation.NONE, Mirror.NONE));
				helper.assertTrue(again[0] != null, "the pillar couldn't be started again");
				LOG.info("[QA scaf1107 again] started again at tick {} with {} scaffolding in the chest", run.tick(),
					QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING));
			}
			if (again[0] != null) {
				most[0] = Math.max(most[0], again[0].scaffolds().size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t.madeAt >= 0, "the Carpenter never made scaffolding (asked " + t.asked + " at tick " + t.askedAt + ", build ended at "
				+ t.endedAt + ")");
			helper.assertTrue(again[0] != null, "the second go never started");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, again[0]), "the second go is still building: stage " + again[0].stage()
				+ " status " + again[0].status() + " missing " + again[0].missing() + " scaffolds " + again[0].scaffolds().size());
			List<BlockPos> unfinished = run.s().plan().unfinished(helper.getLevel());
			helper.assertTrue(unfinished.isEmpty(), "the pillar is still unfinished after the second go with the Carpenter's scaffolding: "
				+ unfinished.size() + " block(s), e.g. " + unfinished.stream().limit(4).map(helper::relativePos).toList()
				+ " (second go skipped " + again[0].skipped() + ", scaffolding put up at most " + most[0] + ")");
			helper.assertTrue(most[0] > 0, "the second go put up no scaffolding");
			helper.assertTrue(again[0].skipped() == 0, "the second go skipped " + again[0].skipped() + " step(s)");
			assertMadeAndKept(helper, run);
			LOG.info("[QA scaf1107 again] made at {}, second go put up {} at most, pillar finished at tick {}", t.madeAt, most[0], run.tick());
		});
	}

	/**
	 * A crew of two on the pillar (workplaceBuildersHelp on). Wiki: "only the lead builder does it (helpers leave such
	 * blocks to the lead)". At no tick does the helper carry scaffolding in his bag or stand up a column; the lead puts
	 * scaffolding up; the pillar is finished with nothing skipped and all 64 scaffolding end back in the chest.
	 */
	//$ gametest_ticks_batch BIG '10000' '"qaScaf1107Crew"'
	@GameTest(template = BIG, timeoutTicks = 10000, batch = "qaScaf1107Crew")
	public void theHelperNeverCarriesNorClimbsScaffolding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.start(helper, QaScaffoldingGameTests.PILLAR, QaScaffoldingGameTests.PILLAR_AT, null);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer()));
		QaScaffoldingGameTests.fill(helper, QaScaffoldingGameTests.SCAFFOLDING);
		helper.setBlock(QaScaffoldingGameTests.BENCH2, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, QaScaffoldingGameTests.HELPER);
		Builders.employ(level, mate, helper.absolutePos(QaScaffoldingGameTests.BENCH2));
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		boolean[] helped = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			helped[0] |= Builders.isHelping(mate) || s.site().helpers(level.getGameTime()).contains(mate.getUUID());
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			int carried = QaScaffoldingGameTests.bagCount(mate);
			if (carried > 0) {
				throw new GameTestAssertException("the helper carries " + carried + " scaffolding at tick " + helper.getTick() + " (lead carries "
					+ QaScaffoldingGameTests.bagCount(s.builder()) + ", " + s.site().scaffolds().size() + " up)");
			}
			if (mate.getY() > ground + 1.5 && onScaffolding(level, mate)) {
				throw new GameTestAssertException("the helper is up a scaffolding column at " + helper.relativePos(mate.blockPosition()) + ", tick "
					+ helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(helped[0], "the second builder never helped");
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped with a crew");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			helper.assertTrue(total == QaScaffoldingGameTests.SCAFFOLDING, "the chest holds " + total + " scaffolding, not "
				+ QaScaffoldingGameTests.SCAFFOLDING + " (ground " + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + ", lead's bag "
				+ QaScaffoldingGameTests.bagCount(s.builder()) + ", helper's " + QaScaffoldingGameTests.bagCount(mate) + ")");
		});
	}

	/**
	 * Free materials on, and the player's own 64 scaffolding in the chest. Wiki: "scaffolding is put up and taken down
	 * for nothing (none taken from or added to the chests)". At every tick the chest holds exactly its 64; the builder
	 * still scaffolds and finishes with nothing skipped, and no scaffolding item appears on the ground or in his bag.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaScaf1107Free"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaScaf1107Free")
	public void freeMaterialsLeaveThePlayersScaffoldingInTheChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(was, level.getServer()));
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			int inChest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			if (inChest != QaScaffoldingGameTests.SCAFFOLDING) {
				throw new GameTestAssertException("with free materials the chest holds " + inChest + " scaffolding at tick " + helper.getTick()
					+ ", not the player's " + QaScaffoldingGameTests.SCAFFOLDING + " (" + s.site().scaffolds().size() + " up, stage " + s.site().stage() + ")");
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up with free materials");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped with free materials");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int extra = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(extra == 0, extra + " scaffolding appeared outside the chest with free materials (ground "
				+ QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + ", bag " + QaScaffoldingGameTests.bagCount(s.builder()) + ")");
		});
	}

	/**
	 * A player breaks the foot of the column while the builder stands at least 5 blocks up it: the column comes down (as
	 * vanilla scaffolding does) and drops. Wiki: "Scaffolding someone else broke is simply struck off the list." The
	 * builder survives, the build finishes with nothing skipped (the chest still holds plenty), none stands, the site lists
	 * none, and every one of the 64 is in the chest, the bag or on the ground.
	 */
	//$ gametest_ticks_batch BIG '10000' '"qaScaf1107BrokenHigh"'
	@GameTest(template = BIG, timeoutTicks = 10000, batch = "qaScaf1107BrokenHigh")
	public void theColumnBrokenUnderTheBuilderHighUpLosesNone(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		ServerLevel level = helper.getLevel();
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		int[] brokenWith = {0};
		double[] brokenHeight = {0};
		helper.onEachTick(() -> {
			Villager v = s.builder();
			if (brokenWith[0] == 0 && v.getY() >= ground + 5 && s.site().scaffolds().size() >= 5 && onScaffolding(level, v)) {
				brokenWith[0] = s.site().scaffolds().size();
				brokenHeight[0] = v.getY() - ground;
				BlockPos foot = s.site().scaffolds().stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
				helper.assertTrue(level.destroyBlock(foot, true), "the column's foot couldn't be broken");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenWith[0] > 0, "the builder was never 5 up a column");
			QaScaffoldingGameTests.assertFinished(helper, s);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			helper.assertTrue(s.site().scaffolds().isEmpty(), "the site still lists scaffolds: " + s.site().scaffolds());
			helper.assertTrue(s.builder().isAlive(), "the builder died when the column was broken under him");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped after the column was broken, with the chest still full");
			int chest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			int ground2 = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(chest + ground2 + bag == QaScaffoldingGameTests.SCAFFOLDING, "after the column was broken (" + brokenWith[0] + " up, builder "
				+ brokenHeight[0] + " high): chest " + chest + ", ground " + ground2 + ", bag " + bag + ", not " + QaScaffoldingGameTests.SCAFFOLDING);
			LOG.info("[QA scaf1107 brokenHigh] broken with {} up, builder {} high; chest {}, ground {}, bag {}", brokenWith[0], brokenHeight[0], chest, ground2, bag);
		});
	}

	/**
	 * The pillar with 64 scaffolding, watched every tick against the wiki: "Every scaffold is recorded in the build site"
	 * (no scaffolding block stands in the area that the site doesn't list); "up to 16 at a time" (before the first
	 * take-down his bag never holds more than 16); "Never in the landscaping stage" (none added while landscaping); and
	 * "takes the scaffolding down from the top, a block at a time" (at most one comes off a tick, and never one with a
	 * listed scaffold still above it).
	 */
	//$ gametest_ticks_batch BIG '8000' '"qaScaf1107Watch"'
	@GameTest(template = BIG, timeoutTicks = 8000, batch = "qaScaf1107Watch")
	public void scaffoldingIsRecordedFetchedSixteenAtATimeAndTakenDownFromTheTop(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		ServerLevel level = helper.getLevel();
		Set<BlockPos> last = new HashSet<>();
		boolean[] takingDown = {false};
		int[] mostBag = {0};
		int[] most = {0};
		int[] removed = {0};
		helper.onEachTick(() -> {
			List<BlockPos> now = s.site().scaffolds();
			most[0] = Math.max(most[0], now.size());
			Set<BlockPos> listed = new HashSet<>(now);
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 17, 21)))) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING) && !listed.contains(p)) {
					throw new GameTestAssertException("scaffolding stands at " + helper.relativePos(p) + " that the site doesn't list (tick "
						+ helper.getTick() + ", listed " + now.stream().map(helper::relativePos).toList() + ")");
				}
			}
			List<BlockPos> added = now.stream().filter(p -> !last.contains(p)).toList();
			List<BlockPos> gone = last.stream().filter(p -> !listed.contains(p)).toList();
			if (!added.isEmpty() && s.site().stage() == BuildPlan.Stage.LANDSCAPE) {
				throw new GameTestAssertException("scaffolding put up in the landscaping stage at " + added.stream().map(helper::relativePos).toList());
			}
			if (!gone.isEmpty()) {
				takingDown[0] = true;
				removed[0] += gone.size();
				if (gone.size() > 1) {
					throw new GameTestAssertException(gone.size() + " scaffolds came down in one tick: " + gone.stream().map(helper::relativePos).toList());
				}
				BlockPos p = gone.get(0);
				for (BlockPos q : now) {
					if (q.getX() == p.getX() && q.getZ() == p.getZ() && q.getY() > p.getY()) {
						throw new GameTestAssertException("the scaffold at " + helper.relativePos(p) + " came down with one still up above it at "
							+ helper.relativePos(q));
					}
				}
			}
			if (!takingDown[0]) {
				int bag = QaScaffoldingGameTests.bagCount(s.builder());
				mostBag[0] = Math.max(mostBag[0], bag);
				if (bag > 16) {
					throw new GameTestAssertException("the builder carries " + bag + " scaffolding before the first take-down (tick " + helper.getTick()
						+ ", " + now.size() + " up): more than the 16 a trip");
				}
			}
			last.clear();
			last.addAll(now);
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			helper.assertTrue(removed[0] > 0, "no scaffold was ever seen coming down");
			helper.assertTrue(mostBag[0] > 0, "the builder never carried scaffolding before the take-down");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int chest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			helper.assertTrue(chest == QaScaffoldingGameTests.SCAFFOLDING, "the chest holds " + chest + " scaffolding, not " + QaScaffoldingGameTests.SCAFFOLDING);
			LOG.info("[QA scaf1107 watch] most up {}, most carried before take-down {}, {} came down", most[0], mostBag[0], removed[0]);
		});
	}

	// --- helpers -----------------------------------------------------------------------------

	/** What happens to the request and the scaffolding made, tick by tick. */
	static final class Tracker {
		long askedAt = -1;
		int asked;
		int skippedWhenAsked;
		long madeAt = -1;
		long inChestAt = -1;
		long clearedAt = -1;
		long endedAt = -1;
	}

	static Tracker track(GameTestHelper helper, Run run) {
		Tracker t = new Tracker();
		BuildSite site = run.s().site();
		helper.onEachTick(() -> {
			long tick = run.tick();
			boolean going = !QaCarpenterStorehouse1101GameTests.done(helper, site);
			int want = site.missing().getOrDefault(Items.SCAFFOLDING, 0);
			if (going && t.askedAt < 0 && want > 0) {
				t.askedAt = tick;
				t.asked = want;
				t.skippedWhenAsked = site.skipped();
			}
			if (t.madeAt < 0 && made(helper) > 0) {
				t.madeAt = tick;
			}
			if (t.inChestAt < 0 && QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) > 0) {
				t.inChestAt = tick;
			}
			if (going && t.askedAt >= 0 && t.inChestAt >= 0 && t.clearedAt < 0 && want == 0) {
				t.clearedAt = tick;
			}
			if (!going && t.endedAt < 0) {
				t.endedAt = tick;
			}
		});
		return t;
	}

	/** The pillar with no scaffolding, bamboo and string in the builder's chest, and a Carpenter at his bench nearby. */
	static Run carpenterRun(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		Container chest = helper.getBlockEntity(QaScaffoldingGameTests.CHEST);
		chest.setItem(3, new ItemStack(Items.BAMBOO, BAMBOO));
		chest.setItem(4, new ItemStack(Items.STRING, STRING));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.east());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.onEachTick(() -> helper.setDayTime(3000));
		return new Run(s, carpenter, level.getGameTime());
	}

	/** Scaffolding as items anywhere (the chest, any villager's bag or inventory, the ground) plus what stands. */
	static int made(GameTestHelper helper) {
		return count(helper, Items.SCAFFOLDING) + standing(helper);
	}

	static int count(GameTestHelper helper, Item item) {
		return QaScaffoldingGameTests.chestCount(helper, item) + QaCarpenterStorehouse1101GameTests.inBags(helper, item)
			+ QaScaffoldingGameTests.loose(helper, item) + QaCarpenterStorehouse1101GameTests.inChest(helper, STATION.north(), item);
	}

	static int standing(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 29, 21)))) {
			n += helper.getLevel().getBlockState(p).is(Blocks.SCAFFOLDING) ? 1 : 0;
		}
		return n;
	}

	/**
	 * Once the Carpenter is done: the scaffolding made is exactly what the bamboo and string used make (6 bamboo and a
	 * string for 6), all of it is in the builder's chest, and none stands.
	 */
	static void assertMadeAndKept(GameTestHelper helper, Run run) {
		QaScaffoldingGameTests.assertNoScaffolding(helper);
		int made = made(helper);
		int bambooUsed = BAMBOO - count(helper, Items.BAMBOO);
		int stringUsed = STRING - count(helper, Items.STRING);
		helper.assertTrue(made > 0, "no scaffolding was made");
		helper.assertTrue(made == bambooUsed && made == 6 * stringUsed, made + " scaffolding exist, but " + bambooUsed + " bamboo and " + stringUsed
			+ " string were used (6 bamboo and a string make 6)");
		int inChest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
		helper.assertTrue(inChest == made, "of the " + made + " scaffolding made, the builder's chest holds " + inChest + " (bags "
			+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SCAFFOLDING) + ", ground " + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + ")");
	}

	/** Whether {@code v} stands in or on a scaffolding block. */
	static boolean onScaffolding(ServerLevel level, Villager v) {
		BlockPos feet = v.blockPosition();
		return level.getBlockState(feet).is(Blocks.SCAFFOLDING) || level.getBlockState(feet.below()).is(Blocks.SCAFFOLDING);
	}
}
