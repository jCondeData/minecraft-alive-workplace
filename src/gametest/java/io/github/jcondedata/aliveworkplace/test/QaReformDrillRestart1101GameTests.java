package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * QA (lane E, saves, 2026-11-01), verifying B762 through the drill itself: on drill day (ROADMAP 31.22, the reform
 * story's drill) the villagers spar at the Training Dummies and, when the drill ends, the chronicle says how many
 * sparred. B762's spec: the count is the same with or without a server restart in the middle of the drill. Each test
 * runs the real {@link ReformStories#tick} with real villagers at a real dummy, and restarts by writing the world's
 * data folder and dropping the reform stories from memory, so the game's own loader reads them back.
 */
public class QaReformDrillRestart1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String NAME = "aliveworkplace_reform_stories";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos DUMMY = new BlockPos(14, 2, 14);

	/**
	 * Two villagers spar, the server restarts, a third joins and one of the first spars again: the drill's line counts
	 * three (each villager once, the restart forgetting nobody), and a later restart neither writes it again nor carries
	 * the three into the next drill.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformDrillRestart"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformDrillRestart")
	public void aRestartMidDrillForgetsNobodyAndCountsEachVillagerOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DUMMY, ModBlocks.TRAINING_DUMMY);
		Villager a = villager(helper, DUMMY.west());
		Villager b = villager(helper, DUMMY.east());
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = Chronicle.day(level);
			long dayStart = (today - 1) * VillageNeeds.DAY;
			ReformStories.Data data = ReformStories.Data.get(level);
			ReformStories.Entry e = data.entry(hall);
			e.drillDay = today;
			e.drillDone = false;
			data.setDirty();
			Leftovers.after(helper, () -> {
				ReformStories.Entry left = ReformStories.Data.get(level).entry(hall);
				left.drillDay = -1;
				left.drillDone = true;
				ReformStories.Data.get(level).setDirty();
			});
			level.setDayTime(dayStart + ReformStories.DRILL_START + 20);
			pin(helper);
			ReformStories.tick(level);
			helper.assertTrue(drillers(level, hall).equals(Set.of(a.getUUID(), b.getUUID())),
				"the two villagers by the dummy didn't spar before the restart: " + drillers(level, hall));

			restart(level);
			helper.assertTrue(ReformStories.Data.get(level) != data, "the reform stories weren't read back from the disk");
			helper.assertTrue(drillers(level, hall).equals(Set.of(a.getUUID(), b.getUUID())),
				"the restart forgot who sparred: " + drillers(level, hall));
			Villager c = villager(helper, DUMMY.north());
			level.setDayTime(dayStart + ReformStories.DRILL_START + 400);
			pin(helper);
			ReformStories.tick(level);
			helper.assertTrue(drillers(level, hall).size() == 3, "after the restart the third villager and the first two count "
				+ drillers(level, hall).size() + ", not 3: " + drillers(level, hall) + " (c " + c.getUUID() + ")");

			level.setDayTime(dayStart + ReformStories.DRILL_END + 20);
			ReformStories.tick(level);
			String village = VillageHalls.name(level, hall).getString();
			String line = "Drill day in " + village + ": 3 villagers sparred at the dummies.";
			helper.assertTrue(told(level, hall).stream().filter(line::equals).count() == 1,
				"the drill's line after a restart mid-drill isn't '" + line + "' once: " + drillLines(level, hall));

			restart(level);
			ReformStories.tick(level);
			helper.assertTrue(drillLines(level, hall).size() == 1, "the drill's line was written again after a restart: " + drillLines(level, hall));
			helper.assertTrue(drillers(level, hall).isEmpty(), "the finished drill's villagers came back after a restart: " + drillers(level, hall));
			helper.succeed();
		});
	}

	/**
	 * The restart comes near the end: the one villager who sparred is gone afterwards, so nobody spars after it. The line
	 * still counts him, in the singular (before B762 it said 0 villagers).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformDrillRestartLate"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformDrillRestartLate")
	public void aRestartNearTheDrillsEndStillCountsWhoSparredBeforeIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DUMMY, ModBlocks.TRAINING_DUMMY);
		Villager a = villager(helper, DUMMY.west());
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = Chronicle.day(level);
			long dayStart = (today - 1) * VillageNeeds.DAY;
			ReformStories.Data data = ReformStories.Data.get(level);
			ReformStories.Entry e = data.entry(hall);
			e.drillDay = today;
			e.drillDone = false;
			data.setDirty();
			Leftovers.after(helper, () -> {
				ReformStories.Entry left = ReformStories.Data.get(level).entry(hall);
				left.drillDay = -1;
				left.drillDone = true;
				ReformStories.Data.get(level).setDirty();
			});
			level.setDayTime(dayStart + ReformStories.DRILL_END - 40);
			pin(helper);
			ReformStories.tick(level);
			helper.assertTrue(drillers(level, hall).equals(Set.of(a.getUUID())), "the villager by the dummy didn't spar: " + drillers(level, hall));

			restart(level);
			a.discard();
			level.setDayTime(dayStart + ReformStories.DRILL_END + 20);
			ReformStories.tick(level);
			String village = VillageHalls.name(level, hall).getString();
			String line = "Drill day in " + village + ": 1 villager sparred at the dummies.";
			helper.assertTrue(drillLines(level, hall).equals(List.of(line)),
				"a restart near the drill's end: the line isn't '" + line + "': " + drillLines(level, hall));
			helper.succeed();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setPersistenceRequired();
		return v;
	}

	/**
	 * The drill looks for Training Dummies 32 blocks round the hall, which reaches into the next test areas: a dummy left
	 * there by another test would take half the villagers away from this one. Pins the hall's dummies (the list the drill
	 * finds once and keeps in memory, so it is found again after a restart) to this test's own.
	 */
	private static void pin(GameTestHelper helper) {
		try {
			Field f = ReformStories.Entry.class.getDeclaredField("dummies");
			f.setAccessible(true);
			f.set(ReformStories.Data.get(helper.getLevel()).entry(helper.absolutePos(HALL)), new java.util.ArrayList<>(List.of(helper.absolutePos(DUMMY))));
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		helper.assertTrue(ReformStories.dummies(helper.getLevel(), helper.absolutePos(HALL)).contains(helper.absolutePos(DUMMY)),
			"the hall doesn't find this test's Training Dummy");
	}

	@SuppressWarnings("unchecked")
	private static Set<java.util.UUID> drillers(ServerLevel level, BlockPos hall) {
		try {
			Field f = ReformStories.Entry.class.getDeclaredField("drillers");
			f.setAccessible(true);
			return Set.copyOf((Set<java.util.UUID>) f.get(ReformStories.Data.get(level).entry(hall)));
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Writes every changed saved data to the world's data folder and drops the reform stories from memory. */
	private static void restart(ServerLevel level) {
		ReformStories.Data.get(level).setDirty();
		DimensionDataStorage storage = level.getDataStorage();
		storage.save();
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove(NAME);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<String> told(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(c -> c.text().getString()).toList();
	}

	private static List<String> drillLines(ServerLevel level, BlockPos hall) {
		return told(level, hall).stream().filter(l -> l.startsWith("Drill day in ") && l.contains("sparred")).toList();
	}
}
