package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * QA (qa-lane-b-1111-2137), B1187 from its spec on the Storehouse path: a helper's bag counts like the builder's, so
 * the village Storehouse keeps back for another build only what that build's crew still needs. The earlier tests
 * (QaSharedStockVerify1111dGameTests) covered chests by a builder's table; here neither table has a chest of its own and
 * the logs are in a Storehouse 15 blocks off (QaSharedStock1111bGameTests' village). Builder B's row of spruce logs is
 * the other build; builder A asks. The helper carrying all of B's need, one fewer, the helper leaving B's crew, the
 * builder's and helper's bags beyond the need (never a negative hold), and two in-play runs where the Carpenter saws
 * the Storehouse's logs for A. The Storehouse's hold is worked out once a second, so each change is read 25 ticks later. Each test has
 * its own batch: build sites are shared state.
 */
public class QaSharedStockStorehouse1111dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	static final Item LOG = Items.SPRUCE_LOG;
	static final int STEP = 25;

	/** Builder A and builder B (both idle, no chests), B's row of {@code rowB} logs, the Storehouse, B's would-be helper. */
	record Store(GameTestHelper helper, ServerLevel level, BuildSite siteA, BuildSite siteB, Villager b, Villager mate, AtomicBoolean onCrew) {
		Map<Item, Integer> held() {
			return Builders.reservedForOthers(level, helper.absolutePos(QaSharedStock1111bGameTests.STOREHOUSE), siteA);
		}

		int hold() {
			return held().getOrDefault(MaterialFamilies.key(LOG), 0);
		}

		void carries(Villager v, ItemStack... stacks) {
			ModAttachments.BUILDER_BAG.set(v, BuilderBag.of(List.of(stacks)));
		}
	}

	static Store store(GameTestHelper helper, String name, int rowB, int logs) {
		ServerLevel level = QaSharedStock1111bGameTests.prepare(helper);
		ResourceLocation planks = QaSharedStock1111bGameTests.row(helper, "sha_" + name, QaSharedStock1111bGameTests.ROW_A, 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logRow = QaSharedStock1111bGameTests.row(helper, "shb_" + name, QaSharedStock1111bGameTests.ROW_B, rowB, Blocks.SPRUCE_LOG);
		QaSharedStock1111bGameTests.storehouse(helper, logs);
		Villager a = QaSharedStock1111bGameTests.idleBuilder(helper, QaSharedStock1111bGameTests.BENCH_A);
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, QaSharedStock1111bGameTests.BENCH_B);
		Villager mate = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(28, 2, 2));
		BuildSite siteA = QaSharedStock1111bGameTests.start(helper, a, planks, QaSharedStock1111bGameTests.ROW_A);
		BuildSite siteB = QaSharedStock1111bGameTests.start(helper, b, logRow, QaSharedStock1111bGameTests.ROW_B);
		Store s = new Store(helper, level, siteA, siteB, b, mate, new AtomicBoolean(true));
		helper.onEachTick(() -> {
			if (s.onCrew().get()) {
				siteB.seen(mate.getUUID(), level.getGameTime());
			}
		});
		int before = s.hold();
		helper.assertTrue(before == rowB, "B's row of " + rowB + " logs keeps " + before + " back in the Storehouse before anyone carries any (held " + s.held() + ")");
		return s;
	}

	static void expectHold(Store s, int expected, String when) {
		Map<Item, Integer> held = s.held();
		int got = held.getOrDefault(MaterialFamilies.key(LOG), 0);
		if (got != expected) {
			throw new GameTestAssertException(when + ": the Storehouse keeps " + got + " spruce logs back from A, expected " + expected + " (held " + held + ")");
		}
		held.forEach((item, n) -> {
			if (n <= 0) {
				throw new GameTestAssertException(when + ": " + n + " " + item + " held back in the Storehouse (a hold is never zero or negative): " + held);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- the hold

	/** B's row needs 3, the helper carries all 3: the Storehouse keeps nothing back for B; 4 changes nothing. */
	//$ gametest_ticks_batch BIG '200' '"qaStore1111dAll"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaStore1111dAll")
	public void aHelperCarryingTheWholeNeedLeavesNothingHeldInTheStorehouse(GameTestHelper helper) {
		Store s = store(helper, "all", 3, 3);
		s.carries(s.mate(), new ItemStack(LOG, 3));
		helper.runAfterDelay(STEP, () -> {
			expectHold(s, 0, "the helper carries all 3 of B's row");
			s.carries(s.mate(), new ItemStack(LOG, 4));
		});
		helper.runAfterDelay(2 * STEP, () -> {
			expectHold(s, 0, "the helper carries 4 for B's need of 3");
			helper.succeed();
		});
	}

	/** B's row needs 5, the helper carries 4: exactly 1 kept back; then 5: none; then nothing: all 5 again. */
	//$ gametest_ticks_batch BIG '200' '"qaStore1111dOneShort"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaStore1111dOneShort")
	public void aHelperOneShortLeavesExactlyOneHeldInTheStorehouse(GameTestHelper helper) {
		Store s = store(helper, "short", 5, 5);
		s.carries(s.mate(), new ItemStack(LOG, 4));
		helper.runAfterDelay(STEP, () -> {
			expectHold(s, 1, "the helper carries 4 of B's 5");
			s.carries(s.mate(), new ItemStack(LOG, 5));
		});
		helper.runAfterDelay(2 * STEP, () -> {
			expectHold(s, 0, "the helper carries 5 of B's 5");
			s.carries(s.mate());
		});
		helper.runAfterDelay(3 * STEP, () -> {
			expectHold(s, 5, "the helper carries nothing");
			helper.succeed();
		});
	}

	/** The helper carries 2 of B's 3 (1 held), then leaves B's crew: his bag no longer counts, all 3 are held again. */
	//$ gametest_ticks_batch BIG '200' '"qaStore1111dLeave"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaStore1111dLeave")
	public void aHelperWhoLeavesTheCrewReturnsTheFullHoldInTheStorehouse(GameTestHelper helper) {
		Store s = store(helper, "leave", 3, 3);
		s.carries(s.mate(), new ItemStack(LOG, 2));
		helper.runAfterDelay(STEP, () -> {
			expectHold(s, 1, "the helper carries 2 of B's 3");
			s.onCrew().set(false);
			s.siteB().leaveCrew(s.mate().getUUID());
		});
		helper.runAfterDelay(2 * STEP, () -> {
			helper.assertFalse(s.siteB().helpers(s.level().getGameTime()).contains(s.mate().getUUID()), "the helper is still on B's crew");
			expectHold(s, 3, "the helper has left B's crew");
			helper.succeed();
		});
	}

	/**
	 * B carries 2 and his helper 2 (with oak logs and cobblestone besides) for B's need of 3: nothing held back, nothing
	 * negative, and nothing B doesn't need held at all; B alone with 2 holds exactly 1.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaStore1111dOver"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaStore1111dOver")
	public void theBuilderAndHelperBeyondTheNeedNeverHoldANegativeAmount(GameTestHelper helper) {
		Store s = store(helper, "over", 3, 3);
		s.carries(s.b(), new ItemStack(LOG, 2));
		helper.runAfterDelay(STEP, () -> {
			expectHold(s, 1, "B himself carries 2 of 3");
			s.carries(s.mate(), new ItemStack(LOG, 2), new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.COBBLESTONE, 64));
		});
		helper.runAfterDelay(2 * STEP, () -> {
			expectHold(s, 0, "B carries 2 and the helper 2 of 3");
			helper.assertTrue(!s.held().containsKey(Items.OAK_LOG) && !s.held().containsKey(Items.COBBLESTONE),
				"what the crew carries for nothing B needs is held in the Storehouse: " + s.held());
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------------------- in play
	// A builder takes the Storehouse's last logs anyway when nothing is spare anywhere (23.5, first come first served),
	// so in play the hold shows where the Carpenter saws the Storehouse's logs into another build's planks: he leaves
	// what the other builds keep back (B696). A (working, no chest) waits for 3 spruce planks, one log's worth.

	/**
	 * In play: 1 log in the Storehouse, B's row of 3 logs, B's helper carrying all 3. None is B's to keep: the Carpenter
	 * saws it into planks, A builds his row, and the Storehouse has no log left.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1111dPlayAll"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1111dPlayAll")
	public void inPlayTheCarpenterSawsTheLogTheHelperMakesSpare(GameTestHelper helper) {
		Play run = play(helper, "pall", 1, 3);
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level).get(run.siteA.id());
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, QaSharedStock1111bGameTests.STORE_CHEST, LOG);
			if (live != null) {
				throw new GameTestAssertException("A still waits (" + live.status() + ", missing " + live.missing() + ") with " + left
					+ " logs in the Storehouse, B's helper carrying all of B's row (" + QaSharedStock1111bGameTests.bag(run.mate).count(LOG) + ")");
			}
			helper.assertTrue(QaSharedStock1111bGameTests.placed(helper, QaSharedStock1111bGameTests.ROW_A, 3, Blocks.SPRUCE_PLANKS) == 3,
				"A's build closed without its 3 planks");
			helper.assertTrue(left == 0, "A's row is built, but the Storehouse still holds " + left + " logs");
		});
	}

	/**
	 * In play, one short: 2 logs in the Storehouse, B's helper carrying 2 of B's 3, so the Storehouse keeps exactly 1
	 * back. The Carpenter saws the 1 spare, A builds his row, and the Storehouse never goes below the 1 log B still owes.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1111dPlayShort"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1111dPlayShort")
	public void inPlayOneShortTheStorehouseKeepsExactlyOneBackFromTheCarpenter(GameTestHelper helper) {
		Play run = play(helper, "pshort", 2, 2);
		helper.onEachTick(() -> {
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, QaSharedStock1111bGameTests.STORE_CHEST, LOG);
			if (left < 1) {
				throw new GameTestAssertException("the Storehouse went down to " + left + " logs, though B's row still owes it 1 (helper carries "
					+ QaSharedStock1111bGameTests.bag(run.mate).count(LOG) + " of 3)");
			}
		});
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level).get(run.siteA.id());
			int left = QaCarpenterStorehouse1101GameTests.inChest(helper, QaSharedStock1111bGameTests.STORE_CHEST, LOG);
			if (live != null) {
				throw new GameTestAssertException("A still waits (" + live.status() + ", missing " + live.missing() + ") with " + left
					+ " logs in the Storehouse, 1 of them spare");
			}
			helper.assertTrue(QaSharedStock1111bGameTests.placed(helper, QaSharedStock1111bGameTests.ROW_A, 3, Blocks.SPRUCE_PLANKS) == 3,
				"A's build closed without its 3 planks");
			helper.assertTrue(left == 1, "A's row is built, but the Storehouse holds " + left + " logs, not the 1 B's row still owes");
		});
	}

	static final class Play {
		ServerLevel level;
		BuildSite siteA;
		BuildSite siteB;
		Villager mate;
	}

	/**
	 * A (working, no chest) with a row of 3 spruce planks; B (idle, no chest) with a row of 3 spruce logs; {@code logs}
	 * logs in the Storehouse; B's idle helper carrying {@code carried} logs, kept on B's crew every tick; a Carpenter.
	 */
	static Play play(GameTestHelper helper, String name, int logs, int carried) {
		Play run = new Play();
		run.level = QaSharedStock1111bGameTests.prepare(helper);
		ResourceLocation idA = QaSharedStock1111bGameTests.row(helper, "spa_" + name, QaSharedStock1111bGameTests.ROW_A, 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation idB = QaSharedStock1111bGameTests.row(helper, "spb_" + name, QaSharedStock1111bGameTests.ROW_B, 3, Blocks.SPRUCE_LOG);
		QaSharedStock1111bGameTests.storehouse(helper, logs);
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, QaSharedStock1111bGameTests.BENCH_B);
		run.mate = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(28, 2, 2));
		run.siteB = QaSharedStock1111bGameTests.start(helper, b, idB, QaSharedStock1111bGameTests.ROW_B);
		QaSharedStock1111bGameTests.carry(run.mate, LOG, carried);
		run.siteB.seen(run.mate.getUUID(), run.level.getGameTime());
		helper.onEachTick(() -> run.siteB.seen(run.mate.getUUID(), run.level.getGameTime()));
		Villager a = QaSharedStock1111bGameTests.builder(helper, QaSharedStock1111bGameTests.BENCH_A);
		run.siteA = QaSharedStock1111bGameTests.start(helper, a, idA, QaSharedStock1111bGameTests.ROW_A);
		QaSharedStock1111bGameTests.carpenter(helper);
		return run;
	}
}
