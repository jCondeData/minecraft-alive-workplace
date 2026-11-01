package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * QA (2026-11-01), written from the bugs' text, not the fixes:
 * <ul>
 *   <li>B760: with the village Storehouse full, "what [the Carpenter] makes reaches the builder who asked, or he doesn't
 *   craft for a builder he can't deliver to, and only the log the planks need is used". Exact room (one free slot) and
 *   no room at all (the log's own slot is the only one that would free up), two builders sharing one freed slot, a save
 *   and reload while he waits at the full Storehouse, and a Storehouse that stays full after he set off. Uses
 *   {@link QaCarpenterStorehouse1101GameTests}'s scene (chestless builders waiting for 3 spruce planks each).</li>
 *   <li>B762: "the set [of villagers who drilled today] is saved with its hall's entry (default empty, so old saves
 *   load) and the count is the same with or without a restart". Through the drill's own tick ({@link
 *   ReformStories#tick}) and the chronicle's end-of-drill line, with the restart written to the world's data folder and
 *   read back by the game's loader.</li>
 * </ul>
 */
public class QaVillageEconomySaves1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final String REFORM_FILE = "aliveworkplace_reform_stories";
	static final BlockPos HALL = new BlockPos(6, 2, 6);
	static final BlockPos DUMMY = new BlockPos(14, 2, 14);

	// ------------------------------------------------------------------------------------------------------ B760

	/**
	 * The Storehouse is full but one slot: exactly room for the one stack of planks a log makes. He crafts (the boundary
	 * from the "room" side), the planks go into that slot, the row is built, exactly one of the 64 logs is used and no
	 * plank ever lies on the ground.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaEcon1101OneSlot"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaEcon1101OneSlot")
	public void aFullStorehouseWithOneFreeSlotGetsThePlanksFromOneLog(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "econone", 64, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		for (int i = 1; i < store.getContainerSize() - 1; i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			noPlanksOnTheGround(helper, run);
			int logs = QaCarpenterStorehouse1101GameTests.logsAnywhere(helper);
			if (logs < 63) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + (64 - logs) + " logs were used for 3 planks (one is enough)");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()), "with one free slot in the Storehouse, A's row isn't finished: "
				+ run.a().status() + " missing " + run.a().missing() + "; store planks " + store.countItem(Items.SPRUCE_PLANKS));
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3, "A's row isn't 3 planks");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) == 63, "logs left: " + QaCarpenterStorehouse1101GameTests.logsAnywhere(helper)
				+ " of 64 (exactly one should be used)");
		});
	}

	/**
	 * The other side of the boundary: every slot is full and the only spare log sits alone in a slot (so room would
	 * appear only once he took it). Either he makes the planks and they reach the builder, or he leaves the log alone;
	 * never a plank on the ground, never more than the one log.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaEcon1101NoSlot"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaEcon1101NoSlot")
	public void aFullStorehouseWhoseOnlyLogFillsTheLastSlotLeavesNothingLying(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "econnone", 1, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4);
			noPlanksOnTheGround(helper, run);
		});
		helper.succeedWhen(() -> {
			boolean built = QaCarpenterStorehouse1101GameTests.done(helper, run.a()) && QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3;
			boolean leftAlone = run.tick() >= 3000 && store.countItem(Items.SPRUCE_LOG) == 1 && QaCarpenterStorehouse1101GameTests.planksAnywhere(helper) == 0;
			helper.assertTrue(built || leftAlone, "A neither built nor the log left alone: " + run.a().status() + " missing " + run.a().missing() + ", store "
				+ store.countItem(Items.SPRUCE_LOG) + " logs " + store.countItem(Items.SPRUCE_PLANKS) + " planks, bags "
				+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + " planks");
		});
	}

	/**
	 * Two chestless builders wait at a full Storehouse; at tick 800 just one slot is emptied. Nothing is crafted before,
	 * then both rows are built through that one slot, exactly two logs are used (6 planks need two) and the two planks
	 * left over are in the Storehouse or a bag, never on the ground.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaEcon1101TwoOneSlot"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaEcon1101TwoOneSlot")
	public void twoBuildersBothGetTheirPlanksThroughOneFreedSlot(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "econtwo", 64, false, true, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		int last = store.getContainerSize() - 1;
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4 * 64);
			noPlanksOnTheGround(helper, run);
			int logs = QaCarpenterStorehouse1101GameTests.logsAnywhere(helper);
			if (run.tick() < 800 && logs < 64) {
				throw new GameTestAssertException("at tick " + run.tick() + " a log was used while the Storehouse was full");
			}
			if (logs < 62) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + (64 - logs) + " logs were used for two rows of 3 planks (two are enough)");
			}
			if (run.tick() == 800) {
				store.setItem(last, ItemStack.EMPTY);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()) && QaCarpenterStorehouse1101GameTests.done(helper, run.b()),
				"after one slot was freed: A " + run.a().status() + " missing " + run.a().missing() + ", B " + run.b().status() + " missing " + run.b().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3
				&& QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_B) == 3, "the rows aren't 3 planks each");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) == 62, "logs left: " + QaCarpenterStorehouse1101GameTests.logsAnywhere(helper)
				+ " of 64 (exactly two should be used)");
		});
	}

	/**
	 * The Storehouse fills up while the Carpenter carries the planks to it; while he waits by it, he is saved and loaded
	 * (a restart). The planks are neither lost nor made again nor dropped; 300 ticks after the reload a slot is freed and
	 * the builder gets them, and the delivered job isn't left saved on him.
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaEcon1101ReloadWait"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaEcon1101ReloadWait")
	public void reloadedWhileWaitingAtAFullStorehouseStillDeliversOnce(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "econreload", 1, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicReference<Villager> carpenter = new AtomicReference<>(run.carpenter());
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4);
			noPlanksOnTheGround(helper, run);
			int tick = (int) run.tick();
			if (filledAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) > 0) {
				fillEmpty(store);
				filledAt.set(tick);
			}
			Villager c = carpenter.get();
			if (filledAt.get() >= 0 && reloadedAt.get() < 0 && tick >= filledAt.get() + 60
				&& c.blockPosition().closerThan(helper.absolutePos(QaCarpenterStorehouse1101GameTests.STORE_CHEST), 4)) {
				CrafterJob job = ModAttachments.CRAFTER_JOB.get(c);
				helper.assertTrue(job != null && job.phase() == CrafterJob.Phase.DELIVERING, "waiting at the full Storehouse, his job is " + job);
				Villager copy = QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), c);
				carpenter.set(copy);
				reloadedAt.set(tick);
			}
			if (reloadedAt.get() >= 0 && tick == reloadedAt.get() + 300) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "the Carpenter never waited by the full Storehouse (filled at " + filledAt.get() + ")");
			helper.assertTrue(run.tick() > reloadedAt.get() + 300, "the Storehouse is still full");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.a()), "after the reload and the freed slot, A's row isn't finished: "
				+ run.a().status() + " missing " + run.a().missing() + "; bags " + QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS));
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3, "A's row isn't 3 planks");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter.get()) == null, "the delivered job is still saved: " + ModAttachments.CRAFTER_JOB.get(carpenter.get()));
		});
	}

	/** The bug itself: a spruce plank lying on the ground anywhere near the test, at any tick. */
	static void noPlanksOnTheGround(GameTestHelper helper, QaCarpenterStorehouse1101GameTests.Run run) {
		int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
		if (lying > 0) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground (A waiting: "
				+ !QaCarpenterStorehouse1101GameTests.done(helper, run.a()) + ", missing " + run.a().missing() + ")");
		}
	}

	/** Cobblestone in every empty slot. */
	static void fillEmpty(Container store) {
		for (int i = 0; i < store.getContainerSize(); i++) {
			if (store.getItem(i).isEmpty()) {
				store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------ B762

	/**
	 * The same drill on two days in one village: on the first nothing happens in between, on the second the server
	 * restarts after two villagers have drilled and one of them is gone afterwards. The chronicle's end-of-drill line
	 * counts 2 villagers both days.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEcon1101DrillSame"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEcon1101DrillSame")
	public void theDrillsCountIsTheSameWithOrWithoutARestart(GameTestHelper helper) {
		drillScene(helper, (level, hall) -> {
			Villager a = driller(helper, 0);
			Villager b = driller(helper, 1);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();

			// day 1: no restart
			drillToday(level, hall, today);
			beat(level, today, ReformStories.DRILL_START + 20);
			beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = drillLines(level, hall, village);
			helper.assertTrue(lines.size() == 1, "the first drill's lines: " + lines);
			String without = lines.get(0);

			// day 2: a restart after both drilled; b is gone after it, a drills again
			drillToday(level, hall, today + 1);
			beat(level, today + 1, ReformStories.DRILL_START + 20);
			restart(level, true);
			helper.assertTrue(ReformStories.Data.get(level) != null, "no data after the restart");
			b.discard();
			beat(level, today + 1, ReformStories.DRILL_START + 400);
			beat(level, today + 1, ReformStories.DRILL_END + 20);
			lines = drillLines(level, hall, village);
			helper.assertTrue(lines.size() == 2, "the second drill's lines: " + lines);
			String expected = "Drill day in " + village + ": 2 villagers sparred at the dummies.";
			helper.assertTrue(without.equals(expected), "without a restart the drill reads '" + without + "', not '" + expected + "'");
			helper.assertTrue(lines.get(1).equals(without), "with a restart the drill reads '" + lines.get(1) + "', without one '" + without + "'");
			helper.assertTrue(a.isAlive(), "a is gone");
		});
	}

	/** One villager drills before and after a restart: counted once ("1 villager", the one-form line), not twice. */
	//$ gametest_ticks_batch HUGE '100' '"qaEcon1101DrillTwice"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEcon1101DrillTwice")
	public void aVillagerWhoDrillsBeforeAndAfterARestartCountsOnce(GameTestHelper helper) {
		drillScene(helper, (level, hall) -> {
			driller(helper, 0);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			drillToday(level, hall, today);
			beat(level, today, ReformStories.DRILL_START + 20);
			restart(level, true);
			beat(level, today, ReformStories.DRILL_START + 400);
			restart(level, true);
			beat(level, today, ReformStories.DRILL_START + 800);
			beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 1 villager sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "one villager drilling across two restarts: " + lines + ", expected " + expected);
		});
	}

	/**
	 * A save from before B762, written in the middle of a drill day (drill due today, not done, no "drillers"), is loaded
	 * by the game's own loader without an error; the drill carries on and counts only those who drill after it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEcon1101DrillOld"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEcon1101DrillOld")
	public void anOldSaveMidDrillLoadsAndCountsOnlyTheDrillersAfterIt(GameTestHelper helper) {
		drillScene(helper, (level, hall) -> {
			driller(helper, 0);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			ReformStories.Data before = ReformStories.Data.get(level);
			CompoundTag old = new CompoundTag();
			ListTag halls = new ListTag();
			CompoundTag entry = new CompoundTag();
			entry.putLong("hall", hall.asLong());
			entry.putLong("drillDay", today);
			entry.putBoolean("drillDone", false);
			halls.add(entry);
			old.put("halls", halls);
			ReformStories.Data loaded = load(old, level.registryAccess());
			level.getDataStorage().set(REFORM_FILE, loaded);
			Leftovers.after(helper, () -> level.getDataStorage().set(REFORM_FILE, before));
			helper.assertTrue(drillers(loaded.entry(hall)).isEmpty(), "an old save loaded with drillers " + drillers(loaded.entry(hall)));
			beat(level, today, ReformStories.DRILL_START + 20);
			beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 1 villager sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "after an old save mid-drill: " + lines + ", expected " + expected);
		});
	}

	/**
	 * Two halls' entries in one file, each with its own drillers (one with ids at the ends of the long range, one with
	 * nobody): after a save and load each hall has exactly its own, none swapped, merged or lost.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void twoHallsKeepTheirOwnDrillersAcrossASave(GameTestHelper helper) {
		BlockPos first = new BlockPos(100, 64, 100);
		BlockPos second = new BlockPos(-300, 70, 900);
		BlockPos third = new BlockPos(5000, 64, -5000);
		ReformStories.Data data = new ReformStories.Data();
		Set<UUID> one = Set.of(new UUID(1, 2), new UUID(Long.MIN_VALUE, Long.MAX_VALUE), new UUID(-1, 0));
		Set<UUID> two = Set.of(new UUID(7, 8), new UUID(0, 0));
		for (Map.Entry<BlockPos, Set<UUID>> en : Map.of(first, one, second, two, third, Set.<UUID>of()).entrySet()) {
			ReformStories.Entry e = data.entry(en.getKey());
			e.drillDay = 40;
			drillers(e).addAll(en.getValue());
		}
		ReformStories.Data read = load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
		helper.assertTrue(drillers(read.entry(first)).equals(one), "the first hall's drillers " + one + " came back as " + drillers(read.entry(first)));
		helper.assertTrue(drillers(read.entry(second)).equals(two), "the second hall's drillers " + two + " came back as " + drillers(read.entry(second)));
		helper.assertTrue(drillers(read.entry(third)).isEmpty(), "the third hall had nobody drilled, read " + drillers(read.entry(third)));
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------------ drill helpers

	interface DrillBody {
		void run(ServerLevel level, BlockPos hall);
	}

	/** A Village Hall and one Training Dummy, an empty area, and {@code body} two ticks later; restores the time of day. */
	static void drillScene(GameTestHelper helper, DrillBody body) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DUMMY, ModBlocks.TRAINING_DUMMY);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			// Training Dummies an earlier batch left within reach (outside this area, so never cleared) would take
			// villagers away from this one.
			for (BlockPos stray : ReformStories.dummies(level, hall)) {
				if (!stray.equals(helper.absolutePos(DUMMY))) {
					level.removeBlock(stray, false);
				}
			}
			ReformStories.forget(level, hall);
			helper.assertTrue(ReformStories.dummies(level, hall).equals(List.of(helper.absolutePos(DUMMY))), "the dummies about: " + ReformStories.dummies(level, hall));
			body.run(level, hall);
			ReformStories.Data.get(level).entry(hall).drillDone = true;
			ReformStories.Data.get(level).setDirty();
			helper.succeed();
		});
	}

	/** A grown villager standing still by the dummy. */
	static Villager driller(GameTestHelper helper, int i) {
		Villager v = helper.spawn(EntityType.VILLAGER, DUMMY.offset(1, 0, i == 0 ? 0 : 1));
		v.setNoAi(true);
		v.setAge(0);
		return v;
	}

	/** Drill day is {@code day} at this hall, not yet done, nobody drilled. */
	static void drillToday(ServerLevel level, BlockPos hall, long day) {
		ReformStories.Data data = ReformStories.Data.get(level);
		ReformStories.Entry e = data.entry(hall);
		e.drillDay = day;
		e.drillDone = false;
		drillers(e).clear();
		data.setDirty();
	}

	/** One run of the reform stories' own tick at {@code time} of day {@code day} (Chronicle counts days from 1). */
	static void beat(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + time);
		ReformStories.tick(level);
	}

	static List<String> drillLines(ServerLevel level, BlockPos hall, String village) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(c -> c.text().getString()).filter(l -> l.startsWith("Drill day in " + village)).toList();
	}

	/**
	 * A restart: every changed file of saved data is written to the world's data folder (as the game does when it saves
	 * and stops) and the reform stories are dropped from memory, so the next use reads them from the disk. With {@code
	 * forceDirty}, the reform stories are written even if nothing marked them changed.
	 */
	static void restart(ServerLevel level, boolean forceDirty) {
		if (forceDirty) {
			ReformStories.Data.get(level).setDirty();
		}
		DimensionDataStorage storage = level.getDataStorage();
		storage.save();
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove(REFORM_FILE);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static ReformStories.Data load(CompoundTag tag, HolderLookup.Provider registries) {
		try {
			Method load = ReformStories.Data.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			load.setAccessible(true);
			return (ReformStories.Data) load.invoke(null, tag, registries);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@SuppressWarnings("unchecked")
	static Set<UUID> drillers(ReformStories.Entry e) {
		try {
			Field f = ReformStories.Entry.class.getDeclaredField("drillers");
			f.setAccessible(true);
			return (Set<UUID>) f.get(e);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
