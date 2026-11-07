package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
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
 * QA (lane-b-1107-0937) for scaffolding (ROADMAP 35.6) and the Carpenter (B721), written from docs/wiki/scaffolding.md
 * ("Asking for it", and its Known limit "Not tested: a carpenter taking the bamboo and string from the Storehouse rather
 * than the builder's chest") and docs/wiki/crafter.md ("What a job makes for a builder goes where that builder looks
 * for materials: the chests by his Blueprint Table, or, when there are none, the village Storehouse").
 *
 * <p>The 14-high test pillar of {@link QaScaffoldingGameTests}, with no scaffolding anywhere: its top is skipped and
 * scaffolding is asked for while the build carries on. The village has a Storehouse whose chest is out of reach of both
 * the builder's bench and the Carpenter's (more than 8 blocks from each), so whatever the Carpenter uses from it he had
 * to fetch from the Storehouse. Bamboo, string and scaffolding are counted everywhere (the builder's chest, the
 * Storehouse, every bag, the ground, standing) and must add up at every tick: 6 bamboo and a string make 6. Each test
 * has its own batch (the crafters' claims and the village are shared state).
 */
public class QaScaffoldStorehouse1107GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	/** The Storehouse block and its chest: 16 blocks from the builder's bench, 18 from the Carpenter's, clear of the pillar. */
	static final BlockPos STOREHOUSE = new BlockPos(19, 2, 2);
	static final BlockPos STORE_CHEST = new BlockPos(19, 2, 4);
	static final int BAMBOO = 36;
	static final int STRING = 6;

	record Run(QaScaffoldingGameTests.Site s, Villager carpenter, long start) {
		long tick() {
			return s.level().getGameTime() - start;
		}
	}

	/**
	 * All 36 bamboo and 6 string are in the Storehouse, none in the builder's chest. The pillar skips its top and asks
	 * for scaffolding; the Carpenter fetches the Storehouse's bamboo and string and makes it by the recipe. At every tick
	 * the bamboo, string and scaffolding add up; in the end what was made is what the bamboo and string used make, it is
	 * all in the builder's chest or the Storehouse (where he fetches from), the bamboo used came out of the Storehouse,
	 * and the request is gone.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScafStore1107All"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScafStore1107All")
	public void aCarpenterMakesTheScaffoldingFromTheStorehousesBambooAndString(GameTestHelper helper) {
		Run run = setUp(helper, 0, BAMBOO, STRING);
		Tracker t = track(helper, run);
		helper.succeedWhen(() -> {
			helper.assertTrue(t.askedAt >= 0, "scaffolding was never asked for: missing " + run.s().site().missing());
			helper.assertTrue(t.madeAt >= 0, "the Carpenter never made scaffolding from the Storehouse's bamboo and string (asked " + t.asked
				+ " at tick " + t.askedAt + "; Storehouse bamboo " + inStore(helper, Items.BAMBOO) + ", string " + inStore(helper, Items.STRING)
				+ "; build " + (QaCarpenterStorehouse1101GameTests.done(helper, run.s().site()) ? "ended at tick " + t.endedAt : "still going")
				+ "; job " + ModAttachments.CRAFTER_JOB.get(run.carpenter()) + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.s().site()), "still building: stage " + run.s().site().stage()
				+ " status " + run.s().site().status() + " missing " + run.s().site().missing());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "the Carpenter is still on a job");
			int made = assertMadeAndKept(helper, run, BAMBOO, STRING);
			helper.assertTrue(made >= t.asked, "the Carpenter made " + made + " scaffolding, fewer than the " + t.asked + " asked for");
			helper.assertTrue(inStore(helper, Items.BAMBOO) == BAMBOO - made, "the Storehouse holds " + inStore(helper, Items.BAMBOO)
				+ " bamboo, not the " + (BAMBOO - made) + " left after making " + made + " (the bamboo used didn't come from it)");
			helper.assertTrue(t.clearedAt >= 0 || t.endedAt >= 0 && t.endedAt <= t.storedAt, "scaffolding was stored at tick " + t.storedAt
				+ " but the build still asked for it");
			LOG.info("[QA scafStore1107 all] asked {} at {} (skipped {}), made {} at {}, stored at {} (builder's chest {}, Storehouse {}), request gone at {},"
				+ " build ended at {}", t.asked, t.askedAt, t.skippedWhenAsked, made, t.madeAt, t.storedAt,
				QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING), inStore(helper, Items.SCAFFOLDING), t.clearedAt, t.endedAt);
		});
	}

	/**
	 * Split: 18 bamboo in the builder's chest, the other 18 and all 6 string in the Storehouse. The Carpenter still makes
	 * the scaffolding (taking from both), and at every tick and in the end nothing is lost or made twice.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScafStore1107Split"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScafStore1107Split")
	public void bambooSplitBetweenTheBuildersChestAndTheStorehouseIsAllAccountedFor(GameTestHelper helper) {
		Run run = setUp(helper, BAMBOO / 2, BAMBOO / 2, STRING);
		Tracker t = track(helper, run);
		helper.succeedWhen(() -> {
			helper.assertTrue(t.askedAt >= 0, "scaffolding was never asked for: missing " + run.s().site().missing());
			helper.assertTrue(t.madeAt >= 0, "the Carpenter never made scaffolding with the bamboo split (asked " + t.asked + " at tick " + t.askedAt
				+ "; builder's chest bamboo " + QaScaffoldingGameTests.chestCount(helper, Items.BAMBOO) + ", Storehouse bamboo "
				+ inStore(helper, Items.BAMBOO) + ", string " + inStore(helper, Items.STRING) + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.s().site()), "still building: stage " + run.s().site().stage()
				+ " status " + run.s().site().status() + " missing " + run.s().site().missing());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "the Carpenter is still on a job");
			int made = assertMadeAndKept(helper, run, BAMBOO, STRING);
			helper.assertTrue(made >= t.asked, "the Carpenter made " + made + " scaffolding, fewer than the " + t.asked + " asked for");
			LOG.info("[QA scafStore1107 split] asked {} at {}, made {} at {}; bamboo left: builder's chest {}, Storehouse {}; string left {}", t.asked,
				t.askedAt, made, t.madeAt, QaScaffoldingGameTests.chestCount(helper, Items.BAMBOO), inStore(helper, Items.BAMBOO),
				count(helper, Items.STRING));
		});
	}

	/**
	 * The Storehouse holds the 36 bamboo but there is no string anywhere. The build still finishes without waiting (the
	 * top skipped and counted, scaffolding asked for), nothing is made, and every bamboo stays in the Storehouse, watched
	 * for 3,000 ticks after the build ended.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScafStore1107NoString"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScafStore1107NoString")
	public void noStringAnywhereMakesNothingAndTheBuildStillFinishes(GameTestHelper helper) {
		Run run = setUp(helper, 0, BAMBOO, 0);
		Tracker t = track(helper, run);
		helper.onEachTick(() -> {
			int made = made(helper);
			int bamboo = count(helper, Items.BAMBOO);
			if (made > 0 || bamboo != BAMBOO) {
				throw new GameTestAssertException("with no string anywhere, at tick " + run.tick() + " " + made + " scaffolding exist and the bamboo adds"
					+ " up to " + bamboo + ", not " + BAMBOO + " (Storehouse " + inStore(helper, Items.BAMBOO) + ", bags "
					+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.BAMBOO) + ", ground " + QaScaffoldingGameTests.loose(helper, Items.BAMBOO) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t.askedAt >= 0, "scaffolding was never asked for: missing " + run.s().site().missing());
			helper.assertTrue(t.endedAt >= 0, "the build is still going without scaffolding (it must never wait for it): stage "
				+ run.s().site().stage() + " status " + run.s().site().status() + " missing " + run.s().site().missing());
			helper.assertTrue(run.s().site().skipped() > 0, "the build ended with nothing skipped, though no scaffolding could be had");
			helper.assertTrue(run.tick() >= t.endedAt + 3000, "watching for 3,000 ticks after the build ended");
			helper.assertTrue(inStore(helper, Items.BAMBOO) == BAMBOO, "the Storehouse holds " + inStore(helper, Items.BAMBOO) + " bamboo, not "
				+ BAMBOO);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "the Carpenter is on a job with no string: "
				+ ModAttachments.CRAFTER_JOB.get(run.carpenter()));
			LOG.info("[QA scafStore1107 noString] asked {} at {}, build ended at {} with {} skipped; Storehouse bamboo {}, scaffolding made 0", t.asked,
				t.askedAt, t.endedAt, run.s().site().skipped(), inStore(helper, Items.BAMBOO));
		});
	}

	/**
	 * The player's loop with the Storehouse: the pillar skips its top, the Carpenter makes the scaffolding from the
	 * Storehouse's bamboo and string, and the player starts the same build again. The builder now scaffolds up to the
	 * missing top: the pillar and cap are finished with nothing skipped, none stands, and every scaffold made is back in
	 * the builder's chest or the Storehouse.
	 */
	//$ gametest_ticks_batch BIG '20000' '"qaScafStore1107Again"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaScafStore1107Again")
	public void theStorehouseMadeScaffoldingBuildsTheSkippedTopOnTheNextGo(GameTestHelper helper) {
		Run run = setUp(helper, 0, BAMBOO, STRING);
		Tracker t = track(helper, run);
		BuildSite[] again = {null};
		int[] most = {0};
		helper.onEachTick(() -> {
			if (again[0] == null && t.madeAt >= 0 && QaCarpenterStorehouse1101GameTests.done(helper, run.s().site())
				&& ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null && stored(helper) > 0) {
				ServerLevel level = helper.getLevel();
				again[0] = Builders.start(level, run.s().builder(), null, QaScaffoldingGameTests.PILLAR, new BlueprintData.Placement(
					level.dimension().location(), helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT), Rotation.NONE, Mirror.NONE));
				helper.assertTrue(again[0] != null, "the pillar couldn't be started again");
				LOG.info("[QA scafStore1107 again] started again at tick {} with {} scaffolding in the builder's chest and {} in the Storehouse",
					run.tick(), QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING), inStore(helper, Items.SCAFFOLDING));
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
			helper.assertTrue(unfinished.isEmpty(), "the pillar is still unfinished after the second go with the Storehouse-made scaffolding: "
				+ unfinished.size() + " block(s), e.g. " + unfinished.stream().limit(4).map(helper::relativePos).toList()
				+ " (second go skipped " + again[0].skipped() + ", scaffolding put up at most " + most[0] + ")");
			helper.assertTrue(most[0] > 0, "the second go put up no scaffolding");
			helper.assertTrue(again[0].skipped() == 0, "the second go skipped " + again[0].skipped() + " step(s)");
			int made = assertMadeAndKept(helper, run, BAMBOO, STRING);
			LOG.info("[QA scafStore1107 again] made {} at {}, second go put up {} at most, pillar finished at tick {}; builder's chest {}, Storehouse {}",
				made, t.madeAt, most[0], run.tick(), QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING), inStore(helper, Items.SCAFFOLDING));
		});
	}

	// --- helpers -----------------------------------------------------------------------------

	/** What happens to the request and the scaffolding made, tick by tick, with the counts checked every tick. */
	static final class Tracker {
		long askedAt = -1;
		int asked;
		int skippedWhenAsked;
		long madeAt = -1;
		long storedAt = -1;
		long clearedAt = -1;
		long endedAt = -1;
	}

	static Tracker track(GameTestHelper helper, Run run) {
		Tracker t = new Tracker();
		BuildSite site = run.s().site();
		int bamboo = count(helper, Items.BAMBOO);
		int string = count(helper, Items.STRING);
		helper.onEachTick(() -> {
			long tick = run.tick();
			conserve(helper, run, bamboo, string);
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
			if (t.storedAt < 0 && stored(helper) > 0) {
				t.storedAt = tick;
			}
			if (going && t.askedAt >= 0 && t.storedAt >= 0 && t.clearedAt < 0 && want == 0) {
				t.clearedAt = tick;
			}
			if (!going && t.endedAt < 0) {
				t.endedAt = tick;
			}
		});
		return t;
	}

	/**
	 * The pillar with no scaffolding anywhere; {@code ownBamboo} bamboo in the builder's chest; a Storehouse (out of reach of
	 * both benches) with {@code storeBamboo} bamboo and {@code storeString} string; a Carpenter at his bench in the village.
	 */
	static Run setUp(GameTestHelper helper, int ownBamboo, int storeBamboo, int storeString) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		if (ownBamboo > 0) {
			Container own = helper.getBlockEntity(QaScaffoldingGameTests.CHEST);
			own.setItem(3, new ItemStack(Items.BAMBOO, ownBamboo));
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		if (storeBamboo > 0) {
			store.setItem(0, new ItemStack(Items.BAMBOO, storeBamboo));
		}
		if (storeString > 0) {
			store.setItem(1, new ItemStack(Items.STRING, storeString));
		}
		helper.setBlock(QaScaffolding1107GameTests.STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, QaScaffolding1107GameTests.STATION.east());
		Jobs.employ(level, carpenter, helper.absolutePos(QaScaffolding1107GameTests.STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.onEachTick(() -> helper.setDayTime(3000));
		return new Run(s, carpenter, level.getGameTime());
	}

	/** At every tick: 6 bamboo and a string make 6, so bamboo + scaffolding and 6 x string + scaffolding never change. */
	static void conserve(GameTestHelper helper, Run run, int bamboo, int string) {
		int made = made(helper);
		int b = count(helper, Items.BAMBOO);
		int s = count(helper, Items.STRING);
		if (b + made != bamboo || 6 * s + made != 6 * string) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + made + " scaffolding exist (builder's chest "
				+ QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + ", Storehouse " + inStore(helper, Items.SCAFFOLDING) + ", bags "
				+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SCAFFOLDING) + ", ground " + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING)
				+ ", standing " + QaScaffolding1107GameTests.standing(helper) + ") with " + b + " of " + bamboo + " bamboo and " + s + " of " + string
				+ " string left (6 bamboo and a string make 6): bamboo builder's chest " + QaScaffoldingGameTests.chestCount(helper, Items.BAMBOO)
				+ ", Storehouse " + inStore(helper, Items.BAMBOO) + ", bags " + QaCarpenterStorehouse1101GameTests.inBags(helper, Items.BAMBOO)
				+ ", ground " + QaScaffoldingGameTests.loose(helper, Items.BAMBOO));
		}
	}

	/**
	 * Once the Carpenter is done: something was made, exactly what the bamboo and string used make, none stands, and all
	 * of it is in the builder's chest or the Storehouse (none in a bag or on the ground). Returns how many were made.
	 */
	static int assertMadeAndKept(GameTestHelper helper, Run run, int bamboo, int string) {
		QaScaffoldingGameTests.assertNoScaffolding(helper);
		int made = made(helper);
		int bambooUsed = bamboo - count(helper, Items.BAMBOO);
		int stringUsed = string - count(helper, Items.STRING);
		helper.assertTrue(made > 0, "no scaffolding was made");
		helper.assertTrue(made == bambooUsed && made == 6 * stringUsed, made + " scaffolding exist, but " + bambooUsed + " bamboo and " + stringUsed
			+ " string were used (6 bamboo and a string make 6)");
		helper.assertTrue(stored(helper) == made, "of the " + made + " scaffolding made, the builder's chest holds "
			+ QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + " and the Storehouse " + inStore(helper, Items.SCAFFOLDING) + " (bags "
			+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SCAFFOLDING) + ", ground " + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + ")");
		return made;
	}

	/** Scaffolding where the builder fetches it from: his chest and the Storehouse. */
	static int stored(GameTestHelper helper) {
		return QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + inStore(helper, Items.SCAFFOLDING);
	}

	static int inStore(GameTestHelper helper, Item item) {
		return QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, item);
	}

	/** Scaffolding as items anywhere (both chests, any bag, the ground, the Carpenter's own chest) plus what stands. */
	static int made(GameTestHelper helper) {
		return count(helper, Items.SCAFFOLDING) + QaScaffolding1107GameTests.standing(helper);
	}

	static int count(GameTestHelper helper, Item item) {
		return QaScaffolding1107GameTests.count(helper, item) + inStore(helper, item);
	}
}
