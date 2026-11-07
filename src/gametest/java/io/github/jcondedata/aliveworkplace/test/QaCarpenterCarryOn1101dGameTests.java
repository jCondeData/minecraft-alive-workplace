package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b, 2026-11-01) for B773/B778, written from their spec, not the code: what a Carpenter makes for a chestless
 * builder reaches the builder who asked (or isn't made), planks never lie on the ground while the builder waits, and
 * when he took them home into a chest by his bench (the Storehouse stayed full past the wait) he carries them on once
 * the builder's chests or the Storehouse have room, and only as many as the builder asked.
 * <p>
 * Gaps left by {@link QaCarpenterFullStore1101cGameTests}, {@link QaCarpenterFullStore1101cBugTests} and
 * {@link QaCarpenterHomePlanks1101GameTests} (which cover a Storehouse slot freeing and a reload on his way): the builder
 * getting a chest of his own, a player's own planks in the chest by the bench, the build canceled or the builder dead
 * while the planks are home, and a save and reload before he sets off again. Every test checks at every tick that no
 * spruce plank lies on the ground and the spruce wood adds up (a log is 4 planks). Most tests stage the "taken home"
 * state directly (planks in the chest by his bench, the Storehouse full, no job) to skip the 2400-tick wait; the first
 * goes through it the player's way. No test draws on randomness of its own. Each test has its own batch: the crafters'
 * claims and the village are shared state.
 */
public class QaCarpenterCarryOn1101dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HOME = QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST;
	private static final BlockPos STORE_CHEST = QaCarpenterStorehouse1101GameTests.STORE_CHEST;
	private static final BlockPos A_CHEST = QaCarpenterStorehouse1101GameTests.A_CHEST;
	private static final BlockPos ROW = QaCarpenterStorehouse1101GameTests.ROW_A;

	/**
	 * The player's way: the Storehouse fills as he carries 4 planks (1 log) for a 3-plank row, stays full, and past the
	 * wait he takes them home. Then the builder gets a chest of his own (the Storehouse still full): within 1500 ticks the
	 * row is built, and only the 3 asked for left the chest by the bench (1 stays there).
	 */
	//$ gametest_ticks_batch BIG '7000' '"qaCarry1101dOwnChest"'
	@GameTest(template = BIG, timeoutTicks = 7000, batch = "qaCarry1101dOwnChest")
	public void takenHomeThenTheBuilderGetsAChestTheyAreCarriedThere(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = QaCarpenterHomePlanks1101GameTests.setUp(helper, "d_own", 1, ROW, 3, 1,
			QaCarpenterHomePlanks1101GameTests.Home.ROOM);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger homeAt = new AtomicInteger(-1);
		AtomicInteger chestAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (filledAt.get() < 0 && QaCarpenterHomePlanks1101GameTests.carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && store.countItem(Items.SPRUCE_PLANKS) > 0) {
				throw new GameTestAssertException("at tick " + tick + " planks went into the Storehouse that was kept full");
			}
			if (homeAt.get() < 0 && inChest(helper, HOME) > 0) {
				homeAt.set(tick);
			}
			if (homeAt.get() >= 0 && chestAt.get() < 0 && tick == homeAt.get() + 200) {
				helper.assertTrue(!QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "the builder was done before he had a chest");
				helper.setBlock(A_CHEST, Blocks.CHEST);
				chestAt.set(tick);
			}
			if (chestAt.get() >= 0 && tick > chestAt.get() + 1500 && !QaCarpenterStorehouse1101GameTests.done(helper, run.site())) {
				throw new GameTestAssertException("1500 ticks after the builder got a chest with room (Storehouse still full) he still waits for "
					+ run.site().missing() + " (" + run.site().status() + "); chest by the Carpenter's bench " + inChest(helper, HOME) + " planks, builder's chest "
					+ inChest(helper, A_CHEST) + ", Carpenter carries " + QaCarpenterHomePlanks1101GameTests.carrying(run.carpenter()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(chestAt.get() >= 0, "the planks never went home (filled at " + filledAt.get() + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "still waiting for " + run.site().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 3, "the row has " + QaCarpenterStorehouse1101GameTests.placed(helper, ROW) + " of 3");
			helper.assertTrue(inChest(helper, HOME) == 1, "the builder asked for 3 of the 4 planks home, but " + inChest(helper, HOME)
				+ " are left in the chest by the bench (builder's chest " + inChest(helper, A_CHEST) + ", bags "
				+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + ")");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "a job stays saved: " + ModAttachments.CRAFTER_JOB.get(run.carpenter()));
			say(helper, "[qaCarry1101dOwnChest] taken home " + (homeAt.get() - filledAt.get()) + " ticks after the Storehouse filled; built "
				+ (run.tick() - chestAt.get()) + " ticks after the builder got a chest; 1 plank left home");
		});
	}

	/**
	 * The chest by the Carpenter's bench holds 20 of the player's own planks (nothing taken home), the Storehouse 1 log
	 * and room. The builder asks for 3: at no tick may more than 3 of the player's planks have left that chest, and the
	 * row gets built.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaCarry1101dPlayers"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaCarry1101dPlayers")
	public void thePlayersOwnPlanksByTheBenchLeaveOnlyAsManyAsAsked(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = staged(helper, "d_players", 20, 1, false);
		AtomicInteger lowest = new AtomicInteger(20);
		helper.onEachTick(() -> {
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 24);
			int home = inChest(helper, HOME);
			lowest.set(Math.min(lowest.get(), home));
			if (home < 17) {
				throw new GameTestAssertException("at tick " + run.tick() + " only " + home + " of the player's 20 planks are left in the chest by the Carpenter's bench,"
					+ " though the builder asked for 3 (Storehouse " + inChest(helper, STORE_CHEST) + " planks, bags "
					+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + ", placed " + QaCarpenterStorehouse1101GameTests.placed(helper, ROW)
					+ ", builder done " + QaCarpenterStorehouse1101GameTests.done(helper, run.site()) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "still waiting for " + run.site().missing());
			helper.assertTrue(run.tick() >= 600, "watching a while longer after the build");
			say(helper, "[qaCarry1101dPlayers] built; the player's chest went no lower than " + lowest.get() + " of 20; Storehouse "
				+ inChest(helper, STORE_CHEST) + " planks, " + QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) + " log");
		});
	}

	/**
	 * The chest by the Carpenter's bench holds only other things (cobblestone, no planks), the Storehouse 1 log and room:
	 * nothing there is mistaken for the builder's planks to carry on, so he makes them from the log and the row is built
	 * (found by a planted bug in the carry-on's item check that no other test noticed).
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaCarry1101dOther"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaCarry1101dOther")
	public void otherThingsInTheChestByHisBenchArentCarriedOnAsPlanks(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = staged(helper, "d_other", 0, 1, false);
		Container home = helper.getBlockEntity(HOME);
		home.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		home.setItem(2, new ItemStack(Items.OAK_LOG, 8));
		helper.onEachTick(() -> {
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (run.tick() > 2000 && !QaCarpenterStorehouse1101GameTests.done(helper, run.site())) {
				throw new GameTestAssertException("at tick " + run.tick() + " the builder still waits for " + run.site().missing() + " (" + run.site().status()
					+ ") with a log in the Storehouse; the chest by the Carpenter's bench holds only cobblestone and oak logs (job "
					+ ModAttachments.CRAFTER_JOB.get(run.carpenter()) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "still waiting for " + run.site().missing());
			helper.assertTrue(home.countItem(Items.COBBLESTONE) == 64 && home.countItem(Items.OAK_LOG) == 8, "things in the chest by his bench were taken: cobblestone "
				+ home.countItem(Items.COBBLESTONE) + ", oak logs " + home.countItem(Items.OAK_LOG));
			say(helper, "[qaCarry1101dOther] built from the Storehouse log at tick " + run.tick() + "; his chest's cobblestone and oak logs untouched");
		});
	}

	/**
	 * 4 planks home, the Storehouse full, the build canceled; then a Storehouse slot frees. Nobody asks any more: no plank
	 * lost, made twice or dropped, and 1500 ticks on he holds no job and carries none (they are in a chest).
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaCarry1101dCancel"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaCarry1101dCancel")
	public void aBuildCanceledWhileThePlanksAreHomeLosesNone(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = staged(helper, "d_cancel", 4, 0, true);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger freedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (tick == 200) {
				Builders.cancel(helper.getLevel(), run.site());
			}
			if (tick == 400) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
		});
		helper.succeedWhen(() -> settledInChests(helper, run, freedAt, "canceled"));
	}

	/** As above, but the builder dies while the planks are home. */
	//$ gametest_ticks_batch BIG '3000' '"qaCarry1101dDeath"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaCarry1101dDeath")
	public void aBuilderWhoDiesWhileThePlanksAreHomeLosesNone(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = staged(helper, "d_death", 4, 0, true);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger freedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (tick == 200) {
				Villager builder = builder(helper);
				builder.kill();
			}
			if (tick == 400) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
		});
		helper.succeedWhen(() -> settledInChests(helper, run, freedAt, "dead builder"));
	}

	/**
	 * 4 planks home, the Storehouse full; the world is saved and loaded (the Carpenter and the builder both reloaded)
	 * before a slot frees. He still carries them on: within 1500 ticks of the freed slot the row is built, 1 plank left home.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaCarry1101dReload"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaCarry1101dReload")
	public void reloadedWhileThePlanksAreHomeStillCarriesThemOn(GameTestHelper helper) {
		QaCarpenterHomePlanks1101GameTests.Run run = staged(helper, "d_reload", 4, 0, true);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger freedAt = new AtomicInteger(-1);
		AtomicReference<String> before = new AtomicReference<>("");
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			QaCarpenterHomePlanks1101GameTests.everyTick(helper, run, 4);
			if (tick == 300) {
				helper.assertTrue(!QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "the builder was done with the Storehouse full");
				helper.assertTrue(inChest(helper, HOME) == 4, "with the Storehouse full the 4 planks left home anyway: " + inChest(helper, HOME) + " left");
				before.set(String.valueOf(run.site().missing()));
				run.carpenter(QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), run.carpenter()));
				QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), builder(helper));
			}
			if (tick == 400) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
			if (freedAt.get() >= 0 && tick > freedAt.get() + 1500 && !QaCarpenterStorehouse1101GameTests.done(helper, run.site())) {
				throw new GameTestAssertException("1500 ticks after a Storehouse slot freed (both reloaded) the builder still waits for " + run.site().missing()
					+ " (" + run.site().status() + "); chest by the Carpenter's bench " + inChest(helper, HOME) + ", Storehouse " + inChest(helper, STORE_CHEST));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "still waiting for " + run.site().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 3, "the row has " + QaCarpenterStorehouse1101GameTests.placed(helper, ROW) + " of 3");
			helper.assertTrue(inChest(helper, HOME) == 1, "the builder asked for 3 of the 4 planks home, but " + inChest(helper, HOME) + " are left there");
			say(helper, "[qaCarry1101dReload] reloaded while waiting for " + before.get() + "; built " + (run.tick() - freedAt.get())
				+ " ticks after a Storehouse slot freed; 1 plank left home");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/**
	 * The scene after B773 took a job home: {@code homePlanks} spruce planks in the chest by the Carpenter's bench,
	 * {@code logs} logs in the Storehouse chest, which is {@code full} or not, and the chestless builder's 3-plank row.
	 */
	private static QaCarpenterHomePlanks1101GameTests.Run staged(GameTestHelper helper, String name, int homePlanks, int logs, boolean full) {
		QaCarpenterHomePlanks1101GameTests.Run run = QaCarpenterHomePlanks1101GameTests.setUp(helper, name, logs, ROW, 3, 1,
			QaCarpenterHomePlanks1101GameTests.Home.ROOM);
		Container home = helper.getBlockEntity(HOME);
		home.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, homePlanks));
		if (full) {
			QaVillageEconomySaves1101GameTests.fillEmpty(helper.getBlockEntity(STORE_CHEST));
		}
		return run;
	}

	/** Nobody asks any more: 1500 ticks after the freed slot the 4 planks are all in chests and he holds no job. */
	private static void settledInChests(GameTestHelper helper, QaCarpenterHomePlanks1101GameTests.Run run, AtomicInteger freedAt, String what) {
		helper.assertTrue(freedAt.get() >= 0 && run.tick() >= freedAt.get() + 1500, "still watching");
		int home = inChest(helper, HOME);
		int stored = inChest(helper, STORE_CHEST);
		int bags = QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS);
		helper.assertTrue(home + stored == 4 && bags == 0, "(" + what + ") 1500 ticks after a Storehouse slot freed the 4 planks aren't settled in chests: home "
			+ home + ", Storehouse " + stored + ", bags " + bags + " (job " + ModAttachments.CRAFTER_JOB.get(run.carpenter()) + ")");
		helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "(" + what + ") a job stays saved on the Carpenter: "
			+ ModAttachments.CRAFTER_JOB.get(run.carpenter()));
		say(helper, "[qaCarry1101d " + what + "] planks settled: home " + home + ", Storehouse " + stored + ", none lost or dropped");
	}

	private static Villager builder(GameTestHelper helper) {
		List<Villager> builders = helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), Builders::isBuilder);
		if (builders.size() != 1) {
			throw new GameTestAssertException("expected one builder, found " + builders.size());
		}
		return builders.get(0);
	}

	private static int inChest(GameTestHelper helper, BlockPos pos) {
		return QaCarpenterStorehouse1101GameTests.inChest(helper, pos, Items.SPRUCE_PLANKS);
	}

	private static void say(GameTestHelper helper, String text) {
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
	}
}
