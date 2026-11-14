package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-1114b), B1151 from its spec: a crafter may use what a builder's chests can spare, and what the build's
 * crew already carries is not held back in the chests a second time ({@code Builders.needFromChests} = the build's
 * remaining need less what its crew carries). The existing tests cover a helper's bag in the other build's hold
 * (B1187) and the Market Stall with the builder carrying all its logs; these cover the builder's OWN bag in his own
 * build's hold, from both sides of each boundary.
 *
 * <p>First, the hold itself, on an idle builder with a row of 5 spruce logs: carrying all 5, 4 (one fewer) and 6; a bag
 * of things the row doesn't need; the bag emptied mid-job and the builder killed (the hold rises again); and the builder
 * saved and loaded. Then in play, with a Carpenter and a Storehouse: the builder's row is 4 spruce planks then 4 spruce
 * logs and the Storehouse holds 1 spruce log. Carrying all 4 of the row's logs, that 1 is spare and the Carpenter saws it
 * into the 4 planks, and the row is built; carrying 3, it is the row's own and he never saws it; carrying 4 oak logs
 * (nothing the row needs), likewise. Spruce wood is counted every tick in each: never more, never less.
 */
public class QaCarpenterSpare1114bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final String BIG = "aliveworkplace_test:big_area";
	static final Item LOG = Items.SPRUCE_LOG;

	record Hold(ServerLevel level, BuildSite site, AtomicReference<Villager> builder) {
		int logs() {
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live == null) {
				live = site; // (the builder killed: his site may go with him; the record still answers)
			}
			Map<Item, Integer> need = Builders.needFromChests(level, live, live.plan(level));
			need.forEach((item, n) -> {
				if (n <= 0) {
					throw new GameTestAssertException("needFromChests holds " + n + " " + item + " back (never zero or negative): " + need);
				}
			});
			int n = 0;
			for (Map.Entry<Item, Integer> e : need.entrySet()) {
				n += MaterialFamilies.key(e.getKey()) == MaterialFamilies.key(LOG) ? e.getValue() : 0;
			}
			return n;
		}

		void carry(ItemStack... stacks) {
			ModAttachments.BUILDER_BAG.set(builder.get(), BuilderBag.of(List.of(stacks)));
		}
	}

	/** An idle builder (no AI) at 2,2,2 with a row of {@code length} spruce logs to build, nothing carried. */
	static Hold hold(GameTestHelper helper, String name, int length) {
		ServerLevel level = QaSharedStock1111bGameTests.prepare(helper);
		ResourceLocation id = QaSharedStock1111bGameTests.row(helper, "q1114b_" + name, new BlockPos(4, 2, 12), length, Blocks.SPRUCE_LOG);
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(2, 2, 2));
		BuildSite site = QaSharedStock1111bGameTests.start(helper, b, id, new BlockPos(4, 2, 12));
		Hold h = new Hold(level, site, new AtomicReference<>(b));
		expect(h, length, "nothing carried yet");
		return h;
	}

	static void expect(Hold h, int expected, String when) {
		int got = h.logs();
		if (got != expected) {
			throw new GameTestAssertException(when + ": the chests are to give the row " + got + " spruce logs, expected " + expected
				+ " (builder carries " + QaSharedStock1111bGameTests.bag(h.builder().get()).count(LOG) + ")");
		}
	}

	// --------------------------------------------------------------------------------------------- the hold itself

	/** Row of 5: the builder carrying 5 leaves nothing for the chests to give, 4 (one fewer) exactly 1, 6 nothing, none all 5. */
	//$ gametest_ticks_batch HUGE '40' '"qaSpare1114bEdges"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSpare1114bEdges")
	public void theBuildersOwnBagCountsAtBothSidesOfTheNeed(GameTestHelper helper) {
		Hold h = hold(helper, "edges", 5);
		h.carry(new ItemStack(LOG, 5));
		expect(h, 0, "the builder carries all 5");
		h.carry(new ItemStack(LOG, 4));
		expect(h, 1, "the builder carries 4 of 5");
		h.carry(new ItemStack(LOG, 6));
		expect(h, 0, "the builder carries 6 for a need of 5");
		h.carry();
		expect(h, 5, "the builder carries nothing again");
		helper.succeed();
	}

	/** A bag of oak logs, spruce planks and cobblestone (none of it the row's) leaves the row's 5 spruce logs to the chests. */
	//$ gametest_ticks_batch HUGE '40' '"qaSpare1114bOther"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSpare1114bOther")
	public void aBagOfThingsTheBuildDoesNotNeedFreesNoneOfItsLogs(GameTestHelper helper) {
		Hold h = hold(helper, "other", 5);
		h.carry(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.SPRUCE_PLANKS, 16), new ItemStack(Items.COBBLESTONE, 64));
		expect(h, 5, "the builder carries oak logs, spruce planks and cobblestone");
		helper.succeed();
	}

	/** The builder carries 3 of 5, then his bag is emptied mid-job: the chests are to give all 5 again at once. */
	//$ gametest_ticks_batch HUGE '40' '"qaSpare1114bEmptied"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSpare1114bEmptied")
	public void theBagEmptiedMidJobRaisesTheHoldAgain(GameTestHelper helper) {
		Hold h = hold(helper, "emptied", 5);
		h.carry(new ItemStack(LOG, 3));
		expect(h, 2, "the builder carries 3 of 5");
		BuilderBag bag = QaSharedStock1111bGameTests.bag(h.builder().get());
		bag.remove(LOG, bag.count(LOG));
		helper.assertTrue(bag.count(LOG) == 0, "the bag still holds " + bag.count(LOG) + " logs");
		expect(h, 5, "the builder's bag emptied");
		helper.succeed();
	}

	/** The builder carrying 4 of 5 is killed: once he's gone his bag no longer counts and the chests are to give all 5. */
	//$ gametest_ticks_batch HUGE '200' '"qaSpare1114bKilled"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaSpare1114bKilled")
	public void theBuilderKilledNoLongerLowersTheHold(GameTestHelper helper) {
		Hold h = hold(helper, "killed", 5);
		h.carry(new ItemStack(LOG, 4));
		expect(h, 1, "the builder carries 4 of 5");
		Villager b = h.builder().get();
		b.kill();
		helper.succeedWhen(() -> {
			helper.assertTrue(b.isRemoved(), "the builder's body is still there");
			expect(h, 5, "the builder is dead");
		});
	}

	/** The builder carrying 4 of 5, saved and loaded: his bag still counts, 1 left for the chests to give. */
	//$ gametest_ticks_batch HUGE '40' '"qaSpare1114bReload"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSpare1114bReload")
	public void aReloadedBuildersBagStillCounts(GameTestHelper helper) {
		Hold h = hold(helper, "reload", 5);
		h.carry(new ItemStack(LOG, 4));
		h.builder().set(QaCarpenterStorehouse1101GameTests.reload(h.level(), h.builder().get()));
		helper.assertTrue(QaSharedStock1111bGameTests.bag(h.builder().get()).count(LOG) == 4, "the reloaded builder carries "
			+ QaSharedStock1111bGameTests.bag(h.builder().get()).count(LOG) + " logs, not 4");
		expect(h, 1, "the builder saved and loaded, carrying 4 of 5");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------------- in play

	/**
	 * The boundary from the top: the row's 4 logs all carried, so the Storehouse's 1 log is spare. The Carpenter saws it
	 * into the row's 4 planks (and no more), the row is built, and spruce wood adds up to 20 planks' worth every tick.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaSpare1114bPlayAll"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaSpare1114bPlayAll")
	public void carryingAllTheRowsLogsTheCarpenterSawsTheOneSpare(GameTestHelper helper) {
		Play p = play(helper, "pall", LOG, 4);
		helper.onEachTick(() -> {
			p.conserve(helper, 20);
			p.atMost(4);
		});
		helper.succeedWhen(() -> {
			if (QaSharedStock1111bGameTests.open(helper, p.site)) {
				throw new GameTestAssertException("the row isn't built" + p.state(helper));
			}
			List<BlockPos> wrong = p.site.plan(p.level).unfinished(p.level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " blocks of the row wrong" + p.state(helper));
			helper.assertTrue(p.made() == 4, "the row is built, but the Carpenter made " + p.made() + " planks, not 4" + p.state(helper));
		});
	}

	/**
	 * The top boundary again, the Carpenter saved and loaded the first time he carries the planks to the Storehouse:
	 * they still arrive, he makes no more than the row's 4, the row is built and the wood adds up every tick.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaSpare1114bPlayReload"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaSpare1114bPlayReload")
	public void theCarpenterReloadedWhileCarryingTheSparesPlanksStillDeliversOnce(GameTestHelper helper) {
		Play p = play(helper, "preload", LOG, 4);
		java.util.concurrent.atomic.AtomicBoolean reloaded = new java.util.concurrent.atomic.AtomicBoolean();
		helper.onEachTick(() -> {
			io.github.jcondedata.aliveworkplace.craft.CrafterJob job = ModAttachments.CRAFTER_JOB.get(p.carpenter.get());
			if (!reloaded.get() && job != null && job.phase() == io.github.jcondedata.aliveworkplace.craft.CrafterJob.Phase.DELIVERING) {
				reloaded.set(true);
				p.carpenter.set(QaCarpenterStorehouse1101GameTests.reload(p.level, p.carpenter.get()));
			}
			p.conserve(helper, 20);
			p.atMost(4);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "the Carpenter was never caught carrying, so the reload showed nothing");
			if (QaSharedStock1111bGameTests.open(helper, p.site)) {
				throw new GameTestAssertException("the row isn't built after the Carpenter's reload" + p.state(helper));
			}
			List<BlockPos> wrong = p.site.plan(p.level).unfinished(p.level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " blocks of the row wrong" + p.state(helper));
		});
	}

	/**
	 * One below: the builder carries 3 of the row's 4 logs, so the Storehouse's 1 is the row's own. The Carpenter never
	 * saws it; the row's 4 logs (laid, carried or in the Storehouse) stay 4 and no plank exists, every tick.
	 */
	//$ gametest_ticks_batch BIG '1800' '"qaSpare1114bPlayShort"'
	@GameTest(template = BIG, timeoutTicks = 1800, batch = "qaSpare1114bPlayShort")
	public void carryingOneFewerTheCarpenterLeavesTheRowsLog(GameTestHelper helper) {
		Play p = play(helper, "pshort", LOG, 3);
		neverSawn(helper, p);
	}

	/**
	 * The builder's bag holds 4 oak logs, nothing the row needs: the Storehouse's 1 spruce log is still the row's (it
	 * needs 4, carries none of them) and the Carpenter never saws it.
	 */
	//$ gametest_ticks_batch BIG '1800' '"qaSpare1114bPlayOther"'
	@GameTest(template = BIG, timeoutTicks = 1800, batch = "qaSpare1114bPlayOther")
	public void carryingOtherLogsTheCarpenterLeavesTheRowsLog(GameTestHelper helper) {
		Play p = play(helper, "pother", Items.OAK_LOG, 4);
		neverSawn(helper, p);
	}

	static void neverSawn(GameTestHelper helper, Play p) {
		int expected = p.carriedItem == LOG ? 16 : 4;
		helper.onEachTick(() -> {
			p.conserve(helper, expected);
			p.atMost(0);
			int planks = QaSharedStock1111bGameTests.anywhere(helper, Items.SPRUCE_PLANKS);
			if (planks > 0) {
				throw new GameTestAssertException(planks + " spruce planks exist: the row's own log was sawn" + p.state(helper));
			}
		});
		helper.runAfterDelay(1400, () -> helper.succeedWhen(() -> {
			helper.assertTrue(QaSharedStock1111bGameTests.open(helper, p.site), "the row was finished without its planks" + p.state(helper));
			helper.assertTrue(p.made() == 0, "the Carpenter made " + p.made() + p.state(helper));
			int logs = QaSharedStock1111bGameTests.placed(helper, QaSharedStock1111bGameTests.ROW_A, 8, Blocks.SPRUCE_LOG)
				+ QaSharedStock1111bGameTests.anywhere(helper, LOG);
			helper.assertTrue(logs == expected / 4, logs + " spruce logs left, not " + (expected / 4) + p.state(helper));
			int shortPlanks = p.live().missing().getOrDefault(MaterialFamilies.key(Items.SPRUCE_PLANKS), 0);
			helper.assertTrue(shortPlanks > 0, "the row waits naming " + p.live().missing() + ", not the spruce planks" + p.state(helper));
		}));
	}

	static final class Play {
		ServerLevel level;
		BuildSite site;
		Villager builder;
		final AtomicReference<Villager> carpenter = new AtomicReference<>();
		Item carriedItem;
		long start;

		BuildSite live() {
			return BuildSiteManager.get(level).get(site.id());
		}

		int made() {
			return ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0);
		}

		void atMost(int most) {
			if (made() > most) {
				throw new GameTestAssertException("the Carpenter made " + made() + " planks, at most " + most + " expected" + state(null));
			}
		}

		/** Spruce wood in planks' worth (a log is 4): laid, in the Storehouse, any bag or inventory, or on the ground. */
		void conserve(GameTestHelper helper, int expected) {
			int logs = QaSharedStock1111bGameTests.placed(helper, QaSharedStock1111bGameTests.ROW_A, 8, Blocks.SPRUCE_LOG)
				+ QaSharedStock1111bGameTests.anywhere(helper, LOG);
			int planks = QaSharedStock1111bGameTests.placed(helper, QaSharedStock1111bGameTests.ROW_A, 8, Blocks.SPRUCE_PLANKS)
				+ QaSharedStock1111bGameTests.anywhere(helper, Items.SPRUCE_PLANKS);
			if (4 * logs + planks != expected) {
				throw new GameTestAssertException("at tick " + (level.getGameTime() - start) + " spruce wood adds up to " + (4 * logs + planks) + " planks' worth, not "
					+ expected + " (" + logs + " logs, " + planks + " planks)" + state(helper));
			}
		}

		String state(GameTestHelper helper) {
			BuildSite l = live();
			return " [row: " + (l == null ? "done" : l.stage() + " " + l.status() + " missing=" + l.missing()) + "; builder bag "
				+ QaSharedStock1111bGameTests.bag(builder).stacks() + "; carpenter made " + made() + ", job " + ModAttachments.CRAFTER_JOB.get(carpenter.get())
				+ (helper == null ? "" : "; store " + QaCarpenterStorehouse1101GameTests.inChest(helper, QaSharedStock1111bGameTests.STORE_CHEST, LOG) + " logs")
				+ "; tick " + (level.getGameTime() - start) + "]";
		}
	}

	/** One builder at A's table, a row of 4 spruce planks then 4 spruce logs, {@code n} {@code carried} in his bag, 1 spruce log in the Storehouse, a Carpenter. */
	static Play play(GameTestHelper helper, String name, Item carried, int n) {
		Play p = new Play();
		p.level = QaSharedStock1111bGameTests.prepare(helper);
		BlockPos row = QaSharedStock1111bGameTests.ROW_A;
		for (int x = 0; x < 8; x++) {
			helper.setBlock(row.offset(x, 0, 0), x < 4 ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_LOG);
		}
		ResourceLocation id = QaSharedStock1111bGameTests.save(helper, "q1114b_" + name, row, 8);
		QaSharedStock1111bGameTests.storehouse(helper, 1);
		p.builder = QaSharedStock1111bGameTests.builder(helper, QaSharedStock1111bGameTests.BENCH_A);
		QaSharedStock1111bGameTests.carry(p.builder, carried, n);
		p.carriedItem = carried;
		p.site = QaSharedStock1111bGameTests.start(helper, p.builder, id, row);
		p.carpenter.set(QaSharedStock1111bGameTests.carpenter(helper));
		p.start = p.level.getGameTime();
		return p;
	}
}
