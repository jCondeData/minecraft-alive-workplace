package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1111-2137), verifying B1187 from its spec: a helper's bag counts like the builder's, so a chest shared
 * between two builds keeps back from the other build only what the crew still needs from it. Boundaries from both sides
 * (the helper carrying all, one fewer, more), two helpers splitting the need, a helper carrying something else, the
 * helper leaving the crew, dying or dropping off, a save and reload of the helper, and the builder's and the helper's
 * bags together beyond the need (nothing negative held back). The last three tests are the same in play: builder A,
 * with no chest of his own, takes from B's chest exactly what B's crew doesn't still need. Each test has its own batch:
 * build sites are shared state.
 */
public class QaSharedStockVerify1111dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final Item LOG = Items.SPRUCE_LOG;

	/** Builder A (idle, a row of planks) and builder B (idle, a row of {@code rowB} spruce logs), B's chest, and B's would-be helpers. */
	record Pair(ServerLevel level, BuildSite siteA, BuildSite siteB, Villager b, List<Villager> mates, List<BlockPos> chests) {
		int hold() {
			return held().getOrDefault(MaterialFamilies.key(LOG), 0);
		}

		Map<Item, Integer> held() {
			return Builders.reservedIn(level, chests, siteA);
		}

		/** Puts {@code mate} on B's crew now, carrying exactly {@code stacks} (its bag replaced). */
		void onCrew(Villager mate, ItemStack... stacks) {
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.set(mate,
				io.github.jcondedata.aliveworkplace.build.BuilderBag.of(List.of(stacks)));
			siteB.seen(mate.getUUID(), level.getGameTime());
		}
	}

	static Pair pair(GameTestHelper helper, String name, int rowB, int mates) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chest = new BlockPos(3, 2, 6);
		ResourceLocation planks = QaSharedStock1111bGameTests.row(helper, "va_" + name, new BlockPos(4, 2, 24), 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logs = QaSharedStock1111bGameTests.row(helper, "vb_" + name, new BlockPos(4, 2, 27), rowB, Blocks.SPRUCE_LOG);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(0, new ItemStack(LOG, rowB));
		Villager a = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(2, 2, 2));
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, chest.offset(4, 0, 0));
		List<Villager> crew = new ArrayList<>();
		for (int i = 0; i < mates; i++) {
			crew.add(QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(12 + 4 * i, 2, 2)));
		}
		BuildSite siteA = QaSharedStock1111bGameTests.start(helper, a, planks, new BlockPos(4, 2, 24));
		BuildSite siteB = QaSharedStock1111bGameTests.start(helper, b, logs, new BlockPos(4, 2, 27));
		Pair p = new Pair(level, siteA, siteB, b, crew, List.of(helper.absolutePos(chest)));
		int before = p.hold();
		helper.assertTrue(before == rowB, "B's row of " + rowB + " logs keeps " + before + " back before anyone carries any");
		return p;
	}

	static void expectHold(GameTestHelper helper, Pair p, int expected, String when) {
		int got = p.hold();
		if (got != expected) {
			throw new GameTestAssertException(when + ": B's chest keeps " + got + " spruce logs back from A, expected " + expected + " (held " + p.held() + ")");
		}
		p.held().forEach((item, n) -> {
			if (n <= 0) {
				throw new GameTestAssertException(when + ": " + n + " " + item + " held back (a hold is never zero or negative): " + p.held());
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- boundaries

	/** B's row needs 3; his helper carries all 3: nothing is held back. One over (4) changes nothing. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dAll"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dAll")
	public void aHelperCarryingTheWholeNeedLeavesNothingHeldBack(GameTestHelper helper) {
		Pair p = pair(helper, "all", 3, 1);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 3));
		expectHold(helper, p, 0, "the helper carries all 3");
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 4));
		expectHold(helper, p, 0, "the helper carries 4 for a need of 3");
		helper.succeed();
	}

	/** B's row needs 5; his helper carries 4 (one fewer): exactly 1 is held back; then 5: none; then 0 again: all 5. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dOneShort"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dOneShort")
	public void aHelperOneShortLeavesExactlyOneHeldBack(GameTestHelper helper) {
		Pair p = pair(helper, "short", 5, 1);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 4));
		expectHold(helper, p, 1, "the helper carries 4 of 5");
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 5));
		expectHold(helper, p, 0, "the helper carries 5 of 5");
		p.onCrew(p.mates().get(0));
		expectHold(helper, p, 5, "the helper carries nothing");
		helper.succeed();
	}

	/** Two helpers split B's need of 5: 2 + 2 leaves 1 held back, 3 + 2 none. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dTwo"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dTwo")
	public void twoHelpersSplittingTheNeedBothCount(GameTestHelper helper) {
		Pair p = pair(helper, "two", 5, 2);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 2));
		p.onCrew(p.mates().get(1), new ItemStack(LOG, 2));
		helper.assertTrue(p.siteB().helpers(p.level().getGameTime()).size() == 2, "B's crew has " + p.siteB().helpers(p.level().getGameTime()) + ", not both helpers");
		expectHold(helper, p, 1, "the helpers carry 2 and 2 of 5");
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 3));
		expectHold(helper, p, 0, "the helpers carry 3 and 2 of 5");
		helper.succeed();
	}

	/** The helper's bag full of other things (oak logs, spruce planks, cobblestone) doesn't lower the hold of spruce logs. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dOther"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dOther")
	public void aHelperCarryingOtherMaterialsDoesNotLowerTheHold(GameTestHelper helper) {
		Pair p = pair(helper, "other", 3, 1);
		p.onCrew(p.mates().get(0), new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.SPRUCE_PLANKS, 10), new ItemStack(Items.COBBLESTONE, 64));
		expectHold(helper, p, 3, "the helper carries oak logs, spruce planks and cobblestone");
		helper.assertTrue(!p.held().containsKey(Items.OAK_LOG) && !p.held().containsKey(Items.COBBLESTONE),
			"what the helper carries for nothing B needs is held back: " + p.held());
		helper.succeed();
	}

	/** B carries 2 and his helper 2 for a need of 3: nothing held back, and nothing negative. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dOver"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dOver")
	public void theBuilderAndHelperTogetherCarryingMoreThanTheNeedHoldNothingBack(GameTestHelper helper) {
		Pair p = pair(helper, "over", 3, 1);
		QaSharedStock1111bGameTests.carry(p.b(), LOG, 2);
		expectHold(helper, p, 1, "B himself carries 2 of 3");
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 2));
		expectHold(helper, p, 0, "B carries 2 and the helper 2 of 3");
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------- the helper goes away

	/** The helper leaves B's crew (taken for another job): what he carries no longer counts, all 3 are held back again. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dLeave"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dLeave")
	public void aHelperWhoLeavesTheCrewNoLongerLowersTheHold(GameTestHelper helper) {
		Pair p = pair(helper, "leave", 3, 1);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 2));
		expectHold(helper, p, 1, "the helper carries 2 of 3");
		p.siteB().leaveCrew(p.mates().get(0).getUUID());
		expectHold(helper, p, 3, "the helper has left B's crew");
		helper.succeed();
	}

	/** The helper is killed: once he's gone, his bag no longer counts and the hold is back to 3. */
	//$ gametest_ticks_batch HUGE '200' '"qaVerify1111dKilled"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVerify1111dKilled")
	public void aHelperKilledMidJobNoLongerLowersTheHold(GameTestHelper helper) {
		Pair p = pair(helper, "killed", 3, 1);
		Villager mate = p.mates().get(0);
		p.onCrew(mate, new ItemStack(LOG, 2));
		expectHold(helper, p, 1, "the helper carries 2 of 3");
		mate.kill();
		helper.succeedWhen(() -> {
			p.siteB().seen(mate.getUUID(), p.level().getGameTime()); // still named on the crew: only his body is gone
			helper.assertTrue(mate.isRemoved(), "the helper's body is still there");
			expectHold(helper, p, 3, "the helper is dead");
		});
	}

	/** The helper stops working on B's build: after the crew forgets him (10 seconds), his bag no longer counts. */
	//$ gametest_ticks_batch HUGE '400' '"qaVerify1111dIdle"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaVerify1111dIdle")
	public void aHelperWhoStopsHelpingDropsOutOfTheCount(GameTestHelper helper) {
		Pair p = pair(helper, "idle", 3, 1);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 2));
		long seenAt = p.level().getGameTime();
		helper.runAfterDelay(100, () -> expectHold(helper, p, 1, "5 seconds after the helper was last on the build"));
		helper.succeedWhen(() -> {
			helper.assertTrue(p.level().getGameTime() - seenAt > 200, "not yet 10 seconds");
			expectHold(helper, p, 3, "the helper hasn't been on the build for over 10 seconds");
		});
	}

	/** The helper (and B) saved and loaded mid-job, bags and all: the helper's 2 logs still count, 1 held back. */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1111dReload"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1111dReload")
	public void aReloadedHelpersBagStillCounts(GameTestHelper helper) {
		Pair p = pair(helper, "reload", 3, 1);
		p.onCrew(p.mates().get(0), new ItemStack(LOG, 2));
		Villager copy = QaCarpenterStorehouse1101GameTests.reload(p.level(), p.mates().get(0));
		QaCarpenterStorehouse1101GameTests.reload(p.level(), p.b());
		helper.assertTrue(QaSharedStock1111bGameTests.bag(copy).count(LOG) == 2, "the reloaded helper carries " + QaSharedStock1111bGameTests.bag(copy).count(LOG) + " logs, not 2");
		expectHold(helper, p, 1, "the helper and B saved and loaded, the helper carrying 2 of 3");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------------- in play

	/**
	 * In play, the boundary from the top: A (working, no chest) needs 3 spruce logs, B's chest holds 3 and B's helper
	 * carries all 3 of B's row. None are B's to keep: A takes all 3, builds his row, and the chest is empty.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1111dPlayAll"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1111dPlayAll")
	public void inPlayTheOtherBuilderTakesEveryLogTheHelperMakesSpare(GameTestHelper helper) {
		Play run = play(helper, "pall", 3, 3);
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level).get(run.siteA.id());
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, run.chest, LOG);
			if (live != null) {
				throw new GameTestAssertException("A still waits (" + live.status() + ", missing " + live.missing() + ") beside B's chest of " + left
					+ " logs, B's helper carrying all of B's row (" + QaSharedStock1111bGameTests.bag(run.mate.get()).count(LOG) + ")");
			}
			helper.assertTrue(left == 0, "A's row of 3 is built, but B's chest still holds " + left + " logs");
		});
	}

	/**
	 * In play, the boundary from below: as above but the helper carries 2 of B's 3, so B's chest keeps 1 back. A, needing
	 * 3, takes the 2 spare, lays them, and waits for the third, naming 1 spruce log; the chest keeps its 1.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1111dPlayShort"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1111dPlayShort")
	public void inPlayOneShortTheChestKeepsExactlyOneBack(GameTestHelper helper) {
		Play run = play(helper, "pshort", 3, 2);
		BlockPos rowA = new BlockPos(4, 2, 20);
		helper.onEachTick(() -> {
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, run.chest, LOG);
			if (left < 1) {
				throw new GameTestAssertException("B's chest went down to " + left + " logs, though B's row still owes it 1");
			}
		});
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level).get(run.siteA.id());
			int placed = QaSharedStock1111bGameTests.placed(helper, rowA, 3, Blocks.SPRUCE_LOG);
			helper.assertTrue(live != null, "A's row of 3 was finished, though only 2 logs were spare");
			helper.assertTrue(placed + QaSharedStock1111bGameTests.bag(run.a).count(LOG) == 2,
				"A has laid " + placed + " and carries " + QaSharedStock1111bGameTests.bag(run.a).count(LOG) + " logs, not the 2 spare");
			helper.assertTrue(live.missing().getOrDefault(LOG, 0) == 1, "A waits naming " + live.missing() + ", not 1 spruce log");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.inChest(helper, run.chest, LOG) == 1, "B's chest doesn't keep its 1 log");
		});
	}

	/**
	 * In play, the helper saved and loaded while A works: his 2 of B's 3 logs still count, A takes the 2 spare and
	 * builds his row of 2, and the chest keeps the 1 B's row still owes.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1111dPlayReload"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1111dPlayReload")
	public void inPlayAHelperReloadedMidJobStillCounts(GameTestHelper helper) {
		Play run = play(helper, "preload", 2, 2);
		helper.runAfterDelay(5, () -> run.mate.set(QaCarpenterStorehouse1101GameTests.reload(run.level, run.mate.get())));
		helper.succeedWhen(() -> {
			helper.assertTrue(QaSharedStock1111bGameTests.bag(run.mate.get()).count(LOG) == 2, "the reloaded helper carries "
				+ QaSharedStock1111bGameTests.bag(run.mate.get()).count(LOG) + " logs, not 2");
			BuildSite live = BuildSiteManager.get(run.level).get(run.siteA.id());
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, run.chest, LOG);
			if (live != null) {
				throw new GameTestAssertException("A still waits (" + live.status() + ", missing " + live.missing() + ") beside B's chest of " + left
					+ " logs after the helper was reloaded, B's row owing it only 1");
			}
			helper.assertTrue(left == 1, "A's row is built, but B's chest holds " + left + " logs, not the 1 B's row still owes");
		});
	}

	static final class Play {
		ServerLevel level;
		BuildSite siteA;
		BuildSite siteB;
		Villager a;
		final AtomicReference<Villager> mate = new AtomicReference<>();
		BlockPos chest;
	}

	/**
	 * A (working) at 2,2,2 with no chest, a row of {@code rowA} spruce logs; B (idle) at 20,2,2, his chest at 21,2,4 with 3
	 * logs, a row of 3; B's idle helper carrying {@code carried} of B's logs, kept on B's crew every tick.
	 */
	static Play play(GameTestHelper helper, String name, int rowA, int carried) {
		Play run = new Play();
		run.level = QaSharedStock1111bGameTests.prepare(helper);
		run.chest = new BlockPos(21, 2, 4);
		ResourceLocation idA = QaSharedStock1111bGameTests.row(helper, "pa_" + name, new BlockPos(4, 2, 20), rowA, Blocks.SPRUCE_LOG);
		ResourceLocation idB = QaSharedStock1111bGameTests.row(helper, "pb_" + name, new BlockPos(14, 2, 24), 3, Blocks.SPRUCE_LOG);
		helper.setBlock(run.chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(run.chest);
		c.setItem(0, new ItemStack(LOG, 3));
		run.a = QaSharedStock1111bGameTests.builder(helper, new BlockPos(2, 2, 2));
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(20, 2, 2));
		run.mate.set(QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(26, 2, 16)));
		run.siteA = QaSharedStock1111bGameTests.start(helper, run.a, idA, new BlockPos(4, 2, 20));
		run.siteB = QaSharedStock1111bGameTests.start(helper, b, idB, new BlockPos(14, 2, 24));
		QaSharedStock1111bGameTests.carry(run.mate.get(), LOG, carried);
		helper.onEachTick(() -> run.siteB.seen(run.mate.get().getUUID(), run.level.getGameTime()));
		return run;
	}
}
