package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-b-1111-0937) after B1151/B1171, from the player's side: two builders draw on one village Storehouse,
 * one of them with part of his build already in his bag. When the village holds enough for every build, every build
 * finishes; what a builder carries is never counted twice (neither by the other builder nor by the Carpenter who saws
 * spare logs); nothing is made twice or lost (counted every tick); and a save and reload, or the builders-help switch
 * thrown mid-job, changes none of that. One short, the build left waiting names what it's short of.
 *
 * <p>The village: two Blueprint Tables with no chests of their own (A at 2,2,2, B at 5,2,2), a Storehouse with one
 * chest 15 blocks away (out of either table's own reach), rows to build at z=8 (A) and z=11 (B), and in the Carpenter
 * tests a Carpenter's bench at 13,2,2. Each test has its own batch: build sites and claims are shared state.
 */
public class QaSharedStock1111bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	static final BlockPos BENCH_B = new BlockPos(5, 2, 2);
	static final BlockPos STATION = new BlockPos(13, 2, 2);
	static final BlockPos STOREHOUSE = new BlockPos(18, 2, 19);
	static final BlockPos STORE_CHEST = new BlockPos(18, 2, 17);
	static final BlockPos ROW_A = new BlockPos(6, 2, 8);
	static final BlockPos ROW_B = new BlockPos(6, 2, 11);

	record Row(BlockPos origin, int length, Block block) {
		int placed(GameTestHelper helper) {
			int n = 0;
			for (int x = 0; x < length; x++) {
				if (helper.getBlockState(origin.offset(x, 0, 0)).is(block)) {
					n++;
				}
			}
			return n;
		}
	}

	static final class Village {
		final ServerLevel level;
		final long start;
		final BuildSite a;
		final BuildSite b;
		final Row rowA;
		final Row rowB;
		final AtomicReference<Villager> builderA;
		final AtomicReference<Villager> builderB;
		final AtomicReference<Villager> carpenter = new AtomicReference<>();

		Village(ServerLevel level, BuildSite a, BuildSite b, Row rowA, Row rowB, Villager builderA, Villager builderB) {
			this.level = level;
			this.start = level.getGameTime();
			this.a = a;
			this.b = b;
			this.rowA = rowA;
			this.rowB = rowB;
			this.builderA = new AtomicReference<>(builderA);
			this.builderB = new AtomicReference<>(builderB);
		}

		long tick() {
			return level.getGameTime() - start;
		}
	}

	// ----------------------------------------------------------------------------------------------- builders only

	/**
	 * A's row takes 6 spruce logs and A already carries 4; B's takes 3 and B carries none. The Storehouse holds exactly
	 * the 2 + 3 the chests still owe the two builds. Both rows are built, and the 9 logs are all accounted for at every
	 * tick (placed, in the Storehouse, in a bag or on the ground): never more, never fewer.
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaShared1111bExact"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaShared1111bExact")
	public void theStorehouseHoldingExactlyWhatBothStillNeedFinishesBoth(GameTestHelper helper) {
		Village v = village(helper, "exact", 6, Blocks.SPRUCE_LOG, 4, 3, Blocks.SPRUCE_LOG, 0, 5, false);
		helper.onEachTick(() -> conserveLogs(helper, v, 9));
		helper.succeedWhen(() -> bothFinished(helper, v));
	}

	/**
	 * The same village one log short (4 in the Storehouse): 8 of the 9 logs are placed, the last build waits for exactly
	 * one spruce log and its line names it, and nothing is lost or doubled on the way.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaShared1111bShort"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaShared1111bShort")
	public void oneLogShortTheBuildLeftWaitingNamesTheLog(GameTestHelper helper) {
		Village v = village(helper, "short", 6, Blocks.SPRUCE_LOG, 4, 3, Blocks.SPRUCE_LOG, 0, 4, false);
		helper.onEachTick(() -> conserveLogs(helper, v, 8));
		helper.runAfterDelay(1500, () -> helper.succeedWhen(() -> {
			int placed = v.rowA.placed(helper) + v.rowB.placed(helper);
			if (placed != 8) {
				throw new GameTestAssertException(placed + " of the 8 logs there are placed" + state(helper, v));
			}
			BuildSite waiting = open(helper, v.a) ? v.a : open(helper, v.b) ? v.b : null;
			if (waiting == null) {
				throw new GameTestAssertException("both rows finished with one log too few" + state(helper, v));
			}
			if (open(helper, v.a) && open(helper, v.b)) {
				throw new GameTestAssertException("both builds still open with 8 of 9 logs placed" + state(helper, v));
			}
			int short_ = waiting.missing().getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
			if (short_ != 1) {
				throw new GameTestAssertException("the waiting build is short " + waiting.missing() + ", not 1 spruce log" + state(helper, v));
			}
			String text = Builders.statusText(v.level, waiting, waiting == v.a ? v.builderA.get() : v.builderB.get()).getString();
			if (!text.contains("Spruce Log")) {
				throw new GameTestAssertException("the waiting builder's line doesn't name the spruce log: \"" + text + "\"" + state(helper, v));
			}
		}));
	}

	/**
	 * The exact village, both builders saved and loaded the first time each carries logs for an unfinished row (A from the
	 * start, B once he has fetched). Both rows still finish and the 9 logs add up at every tick.
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaShared1111bReload"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaShared1111bReload")
	public void reloadedMidFetchWithLogsInTheirBagsBothStillFinish(GameTestHelper helper) {
		Village v = village(helper, "reload", 6, Blocks.SPRUCE_LOG, 4, 3, Blocks.SPRUCE_LOG, 0, 5, false);
		AtomicBoolean reloadedA = new AtomicBoolean();
		AtomicBoolean reloadedB = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (!reloadedA.get() && v.tick() >= 40 && open(helper, v.a) && bag(v.builderA.get()).count(Items.SPRUCE_LOG) > 0) {
				reloadedA.set(true);
				v.builderA.set(QaCarpenterStorehouse1101GameTests.reload(v.level, v.builderA.get()));
			}
			if (!reloadedB.get() && open(helper, v.b) && bag(v.builderB.get()).count(Items.SPRUCE_LOG) > 0) {
				reloadedB.set(true);
				v.builderB.set(QaCarpenterStorehouse1101GameTests.reload(v.level, v.builderB.get()));
			}
			conserveLogs(helper, v, 9);
		});
		helper.succeedWhen(() -> {
			bothFinished(helper, v);
			helper.assertTrue(reloadedA.get() && reloadedB.get(), "a builder was never caught carrying logs (A " + reloadedA.get() + ", B " + reloadedB.get() + ")");
		});
	}

	/**
	 * Builders-help off at the start, switched on mid-job: A's short row is done early and he may join B's long one, his
	 * bag in B's crew. B's row (10 logs, all in the Storehouse) and A's (2 logs, carried) are both built, and the 12 logs
	 * add up at every tick: a helper's bag isn't counted on top of the chest or emptied into nowhere.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaShared1111bHelp"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaShared1111bHelp")
	public void buildersHelpSwitchedOnMidJobLosesAndDoublesNothing(GameTestHelper helper) {
		Village v = village(helper, "help", 2, Blocks.SPRUCE_LOG, 2, 10, Blocks.SPRUCE_LOG, 0, 10, false);
		helper.runAfterDelay(60, () -> v.level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, v.level.getServer()));
		helper.onEachTick(() -> conserveLogs(helper, v, 12));
		helper.succeedWhen(() -> bothFinished(helper, v));
	}

	// ---------------------------------------------------------------------------------------------- with a Carpenter

	/**
	 * A's row is 8 spruce planks (none carried); B's is 4 spruce logs and B carries 2. The Storehouse holds 4 logs: B's
	 * 2 still owed and 2 for A's planks. The Carpenter saws the 2 that are spare, not B's (counting B's bag once), and
	 * makes A's 8 planks; both rows are built. Spruce wood adds up to 24 planks' worth at every tick.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaShared1111bSaw"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaShared1111bSaw")
	public void theCarpenterSawsOnlyTheSpareLogsAndBothRowsAreBuilt(GameTestHelper helper) {
		Village v = village(helper, "saw", 8, Blocks.SPRUCE_PLANKS, 0, 4, Blocks.SPRUCE_LOG, 2, 4, true);
		helper.onEachTick(() -> {
			conserveWood(helper, v, 24);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(v.carpenter.get(), 0);
			if (made > 8) {
				throw new GameTestAssertException("the Carpenter made " + made + " planks for a row of 8" + state(helper, v));
			}
		});
		helper.succeedWhen(() -> bothFinished(helper, v));
	}

	/**
	 * One log short for A's planks (3 in the Storehouse: B's 2 and 1 spare): the Carpenter saws the 1 spare log and never
	 * B's 2, so B's row is built; A's waits for spruce planks. Wood adds up to 20 planks' worth at every tick.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaShared1111bSawShort"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaShared1111bSawShort")
	public void oneLogShortTheCarpenterLeavesBsLogsAndAWaitsForPlanks(GameTestHelper helper) {
		Village v = village(helper, "sawshort", 8, Blocks.SPRUCE_PLANKS, 0, 4, Blocks.SPRUCE_LOG, 2, 3, true);
		helper.onEachTick(() -> {
			conserveWood(helper, v, 20);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(v.carpenter.get(), 0);
			if (made > 4) {
				throw new GameTestAssertException("the Carpenter made " + made + " planks from one spare log: he sawed B's" + state(helper, v));
			}
		});
		helper.runAfterDelay(1500, () -> helper.succeedWhen(() -> {
			helper.assertTrue(!open(helper, v.b) && v.rowB.placed(helper) == 4, "B's row isn't built: " + v.rowB.placed(helper) + "/4" + state(helper, v));
			helper.assertTrue(open(helper, v.a), "A's row of 8 planks finished from one log" + state(helper, v));
			helper.assertTrue(v.rowA.placed(helper) == 4, "A's row has " + v.rowA.placed(helper) + " planks, the 4 one log makes expected" + state(helper, v));
			String text = Builders.statusText(v.level, v.a, v.builderA.get()).getString();
			helper.assertTrue(text.contains("Spruce Plank"), "A's line doesn't name what's short: \"" + text + "\"" + state(helper, v));
		}));
	}

	/**
	 * Both builders carry part of their rows' logs and the Carpenter makes A's planks too: A's row is 4 spruce planks then 4
	 * spruce logs (he holds his logs while he waits for the planks), with 3 logs in A's bag; B's 4 logs with 2 in B's. The Storehouse holds 1 (A's) + 2 (B's) + 1 (A's
	 * planks) = 4 logs. Neither bag is counted twice by the Carpenter (his own build's crew, B1151; the other build's, 23.5):
	 * both rows are built, 36 planks' worth of wood at every tick.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaShared1111bBothCarry"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaShared1111bBothCarry")
	public void bothBuildersCarryingTheCarpenterStillMakesAsPlanks(GameTestHelper helper) {
		Village v = mixedVillage(helper, "both", 3, 2, 4);
		helper.onEachTick(() -> {
			conserveWood(helper, v, 36);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(v.carpenter.get(), 0);
			if (made > 4) {
				throw new GameTestAssertException("the Carpenter made " + made + " planks for A's 4" + state(helper, v));
			}
		});
		helper.succeedWhen(() -> bothFinished(helper, v));
	}

	// -------------------------------------------------------------------------------------------- what's kept back

	/**
	 * The control for QaSharedStock1111bBugTests: A's row takes 2 spruce logs and A has no chest; B's chest holds 3 and B
	 * himself carries 2 for his row of 3, his crew idle. 2 of the chest's logs are spare: A takes them and builds his row,
	 * and the 1 log B's row still owes stays in the chest.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaShared1111bBuilderPlay"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaShared1111bBuilderPlay")
	public void theOtherBuilderTakesTheSpareWhenTheBuilderHimselfCarries(GameTestHelper helper) {
		aBuildsFromTheSpare(helper, spareRun(helper, "bp", false));
	}

	record SpareRun(ServerLevel level, BuildSite siteA, BuildSite siteB, Villager b, Villager mate, BlockPos chest) {
	}

	/** A (working) at 2,2,2 with no chest, row of 2 logs; B (idle) at 20,2,2, chest at 21,2,4 with 3 logs, row of 3; B's 2 carried logs on B or on his idle helper. */
	static SpareRun spareRun(GameTestHelper helper, String name, boolean byHelper) {
		ServerLevel level = prepare(helper);
		BlockPos chest = new BlockPos(21, 2, 4);
		ResourceLocation rowA = row(helper, "sa_" + name, new BlockPos(4, 2, 20), 2, Blocks.SPRUCE_LOG);
		ResourceLocation rowB = row(helper, "sb_" + name, new BlockPos(14, 2, 24), 3, Blocks.SPRUCE_LOG);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(0, new ItemStack(Items.SPRUCE_LOG, 3));
		Villager a = builder(helper, new BlockPos(2, 2, 2));
		Villager b = idleBuilder(helper, new BlockPos(20, 2, 2));
		Villager mate = idleBuilder(helper, new BlockPos(26, 2, 16));
		BuildSite siteA = start(helper, a, rowA, new BlockPos(4, 2, 20));
		BuildSite siteB = start(helper, b, rowB, new BlockPos(14, 2, 24));
		carry(byHelper ? mate : b, Items.SPRUCE_LOG, 2);
		if (byHelper) {
			siteB.seen(mate.getUUID(), level.getGameTime());
		}
		return new SpareRun(level, siteA, siteB, b, mate, chest);
	}

	static void aBuildsFromTheSpare(GameTestHelper helper, SpareRun run) {
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level()).get(run.siteA().id());
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, run.chest(), Items.SPRUCE_LOG);
			if (live != null) {
				throw new GameTestAssertException("A still waits (" + live.status() + ", missing " + live.missing() + ") beside B's chest of " + left
					+ " logs, B's row owing it only 1 (bags: B " + bag(run.b()).count(Items.SPRUCE_LOG) + ", helper " + bag(run.mate()).count(Items.SPRUCE_LOG) + ")");
			}
			helper.assertTrue(left == 1, "A's row is built, but B's chest holds " + left + " logs, not the 1 B's row still owes");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/**
	 * The two-builder village: A's row of {@code lenA} {@code blockA} with {@code carryA} of it (as items) in A's bag,
	 * B's likewise, {@code logs} spruce logs in the Storehouse chest, and a Carpenter when {@code carpenter}.
	 */
	static Village village(GameTestHelper helper, String name, int lenA, Block blockA, int carryA, int lenB, Block blockB, int carryB, int logs,
						   boolean carpenter) {
		ServerLevel level = prepare(helper);
		Row rowA = new Row(ROW_A, lenA, blockA);
		Row rowB = new Row(ROW_B, lenB, blockB);
		ResourceLocation idA = row(helper, "a_" + name, ROW_A, lenA, blockA);
		ResourceLocation idB = row(helper, "b_" + name, ROW_B, lenB, blockB);
		storehouse(helper, logs);
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		carry(a, blockA.asItem(), carryA);
		carry(b, blockB.asItem(), carryB);
		Village v = new Village(level, start(helper, a, idA, ROW_A), start(helper, b, idB, ROW_B), rowA, rowB, a, b);
		if (carpenter) {
			v.carpenter.set(carpenter(helper));
		}
		return v;
	}

	/**
	 * A's row is 4 spruce planks then 4 spruce logs (8 long, read as two rows for counting), with {@code carryA} logs in
	 * A's bag; B's 4 spruce logs with {@code carryB}; {@code logs} in the Storehouse; a Carpenter.
	 */
	static Village mixedVillage(GameTestHelper helper, String name, int carryA, int carryB, int logs) {
		ServerLevel level = prepare(helper);
		for (int x = 0; x < 8; x++) {
			helper.setBlock(ROW_A.offset(x, 0, 0), x < 4 ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_LOG);
		}
		ResourceLocation idA = save(helper, "a_" + name, ROW_A, 8);
		ResourceLocation idB = row(helper, "b_" + name, ROW_B, 4, Blocks.SPRUCE_LOG);
		storehouse(helper, logs);
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		carry(a, Items.SPRUCE_LOG, carryA);
		carry(b, Items.SPRUCE_LOG, carryB);
		// counted as A's logs + B's logs (rowA) and A's planks (rowB) by the wood count
		Village v = new Village(level, start(helper, a, idA, ROW_A), start(helper, b, idB, ROW_B), new Row(ROW_A, 8, Blocks.SPRUCE_LOG),
			new Row(ROW_B, 4, Blocks.SPRUCE_LOG), a, b);
		v.carpenter.set(carpenter(helper));
		return v;
	}

	static ServerLevel prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		return level;
	}

	static void storehouse(GameTestHelper helper, int logs) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0, left = logs; left > 0; i++, left -= 64) {
			chest.setItem(i, new ItemStack(Items.SPRUCE_LOG, Math.min(64, left)));
		}
	}

	static Villager carpenter(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	static void carry(Villager v, Item item, int n) {
		if (n > 0 && bag(v).addAll(item, n) != 0) {
			throw new GameTestAssertException("the bag doesn't take " + n + " " + item);
		}
	}

	static Villager builder(GameTestHelper helper, BlockPos bench) {
		return QaCarpenterStorehouse1101GameTests.builder(helper, bench);
	}

	static Villager idleBuilder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench.below(), Blocks.STONE);
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		helper.setBlock(bench.north().below(), Blocks.STONE);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.north());
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no build of " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A row of {@code length} {@code block}s saved as a blueprint, the spot cleared again. */
	static ResourceLocation row(GameTestHelper helper, String name, BlockPos origin, int length, Block block) {
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		return save(helper, name, origin, length);
	}

	static ResourceLocation save(GameTestHelper helper, String name, BlockPos origin, int length) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1111b_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	static BuilderBag bag(Villager villager) {
		return ModAttachments.BUILDER_BAG.getOrCreate(villager);
	}

	static boolean open(GameTestHelper helper, BuildSite site) {
		return BuildSiteManager.get(helper.getLevel()).get(site.id()) != null;
	}

	static void bothFinished(GameTestHelper helper, Village v) {
		if (open(helper, v.a) || open(helper, v.b)) {
			throw new GameTestAssertException("still building" + state(helper, v));
		}
		List<BlockPos> wrongA = v.a.plan(v.level).unfinished(v.level);
		List<BlockPos> wrongB = v.b.plan(v.level).unfinished(v.level);
		if (!wrongA.isEmpty() || !wrongB.isEmpty()) {
			throw new GameTestAssertException("rows finished with blocks wrong: A " + wrongA.size() + ", B " + wrongB.size() + state(helper, v));
		}
	}

	static int placed(GameTestHelper helper, BlockPos from, int length, Block block) {
		return new Row(from, length, block).placed(helper);
	}

	/** Spruce logs placed in either row, in the Storehouse, in any bag or inventory, or on the ground. */
	static void conserveLogs(GameTestHelper helper, Village v, int expected) {
		int logs = placed(helper, ROW_A, 16, Blocks.SPRUCE_LOG) + placed(helper, ROW_B, 16, Blocks.SPRUCE_LOG) + anywhere(helper, Items.SPRUCE_LOG);
		if (logs != expected) {
			throw new GameTestAssertException("at tick " + v.tick() + " there are " + logs + " spruce logs, not " + expected + state(helper, v));
		}
	}

	/** Spruce wood in planks' worth (a log is 4): placed, in the Storehouse, the bags, inventories and on the ground. */
	static void conserveWood(GameTestHelper helper, Village v, int expected) {
		int logs = placed(helper, ROW_A, 16, Blocks.SPRUCE_LOG) + placed(helper, ROW_B, 16, Blocks.SPRUCE_LOG) + anywhere(helper, Items.SPRUCE_LOG);
		int planks = placed(helper, ROW_A, 16, Blocks.SPRUCE_PLANKS) + placed(helper, ROW_B, 16, Blocks.SPRUCE_PLANKS) + anywhere(helper, Items.SPRUCE_PLANKS);
		if (4 * logs + planks != expected) {
			throw new GameTestAssertException("at tick " + v.tick() + " the spruce wood adds up to " + (4 * logs + planks) + " planks' worth, not " + expected
				+ " (" + logs + " logs, " + planks + " planks)" + state(helper, v));
		}
	}

	static int anywhere(GameTestHelper helper, Item item) {
		return QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, item) + QaCarpenterStorehouse1101GameTests.inBags(helper, item)
			+ QaCarpenterStorehouse1101GameTests.onGround(helper, item);
	}

	static String state(GameTestHelper helper, Village v) {
		List<String> parts = new ArrayList<>();
		for (BuildSite s : List.of(v.a, v.b)) {
			BuildSite live = BuildSiteManager.get(v.level).get(s.id());
			parts.add((s == v.a ? "A" : "B") + ": " + (live == null ? "done" : live.stage() + " " + live.status() + " missing=" + live.missing()));
		}
		parts.add("bags A " + bag(v.builderA.get()).count(Items.SPRUCE_LOG) + " logs/" + bag(v.builderA.get()).count(Items.SPRUCE_PLANKS) + " planks, B "
			+ bag(v.builderB.get()).count(Items.SPRUCE_LOG) + " logs/" + bag(v.builderB.get()).count(Items.SPRUCE_PLANKS) + " planks");
		parts.add("store " + QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) + " logs/"
			+ QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) + " planks");
		parts.add("placed A " + v.rowA.placed(helper) + ", B " + v.rowB.placed(helper));
		if (v.carpenter.get() != null) {
			parts.add("carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(v.carpenter.get(), 0) + ", job " + ModAttachments.CRAFTER_JOB.get(v.carpenter.get()));
		}
		return " [" + String.join("; ", parts) + ", tick " + v.tick() + "]";
	}
}
