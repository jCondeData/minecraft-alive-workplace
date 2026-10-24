package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, saves): what the reform stories leave in a village (ROADMAP 31.22, saved in
 * {@code aliveworkplace_reform_stories}) through a real restart: written to the world's data folder and read back by
 * the game's own loader, not a copy in memory. The Shift Bell keeps ringing, tomorrow's harvest supper and drill day and
 * the waiting grand festival still come, the first lamplit night and the drill's line are written once only, the Fair
 * Ledger keeps its takings, and a village entry saved before a field existed loads with that field unset.
 */
public class QaReformSavesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String NAME = "aliveworkplace_reform_stories";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos BELL = new BlockPos(10, 2, 6);

	//$ gametest_ticks_batch AREA '100' '"qaReformSavesRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformSavesRestart")
	public void everythingAReformStoryLeftComesBackAfterARestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BELL, Blocks.BELL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos bell = helper.absolutePos(BELL);
			long today = Chronicle.day(level);
			long dayStart = (today - 1) * VillageNeeds.DAY; // (Chronicle.day counts from 1)
			level.setDayTime(dayStart + 1000);
			ReformStories.Data data = ReformStories.Data.get(level);
			ReformStories.Entry e = data.entry(hall);
			e.bell = bell.asLong();
			e.supperDay = today + 1;
			e.grandPending = true;
			e.lamplit = true;
			e.drillDay = today + 1;
			ReformStories.takings(level, hall, today - 1, 250);
			ReformStories.takings(level, hall, today, 1234);
			data.setDirty();
			Leftovers.after(helper, () -> {
				ReformStories.Data.get(level).entry(hall).bell = Long.MIN_VALUE;
				ReformStories.Data.get(level).setDirty();
			});

			restart(level);
			helper.assertTrue(ReformStories.Data.get(level) != data, "the data wasn't read back from the disk");
			helper.assertTrue(bell.equals(ReformStories.bell(level, hall)), "the Shift Bell after a restart: " + ReformStories.bell(level, hall));
			int rings = ReformStories.RINGS.get();
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.RING_START);
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings + 1, "the bell didn't ring the work day in after a restart");

			// tomorrow: the supper, the grand festival, the drill's line once
			helper.assertTrue(ReformStories.supper(level, hall), "tomorrow's harvest supper was lost in the restart");
			Festivals.feast(level, hall);
			helper.assertTrue(ReformStories.grand(level, hall), "the grand festival waiting was lost in the restart");
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.DRILL_END + 20);
			ReformStories.tick(level);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(told(level, hall).stream().filter(l -> l.startsWith("Drill day in " + village)).count() == 1,
				"the drill day set before the restart: " + told(level, hall));
			restart(level);
			ReformStories.tick(level);
			helper.assertTrue(told(level, hall).stream().filter(l -> l.startsWith("Drill day in " + village)).count() == 1,
				"the drill's line was written again after a second restart");

			// the first lamplit night, written once across a restart
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.NIGHT + 100);
			ReformStories.tick(level);
			String lamplit = "The first lamplit night in " + village + ": the streets glowed, and nobody hurried home.";
			helper.assertTrue(told(level, hall).contains(lamplit), "the lamplit night after a restart: " + told(level, hall));
			restart(level);
			ReformStories.tick(level);
			helper.assertTrue(told(level, hall).stream().filter(lamplit::equals).count() == 1, "the lamplit night written again after a restart");

			// the Fair Ledger still has yesterday's and today's takings (now the day before and the day before that)
			ItemStack book = ReformStories.ledger(level, hall, ReformStories.Data.get(level).entry(hall));
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			String page = content == null ? "" : content.pages().get(0).raw().getString();
			helper.assertTrue(page.contains("2.50 emeralds") && page.contains("12.34 emeralds"), "the ledger after restarts: " + page);
			helper.succeed();
		});
	}

	/** A village entry saved before the later fields existed (only its hall), and one with a broken takings list. */
	//$ gametest_ticks_batch AREA '100' '"qaReformSavesOld"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformSavesOld")
	public void aVillageSavedBeforeTheFieldsExistedHasNothingSet(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ReformStories.Data before = ReformStories.Data.get(level);
			CompoundTag old = new CompoundTag();
			ListTag halls = new ListTag();
			CompoundTag entry = new CompoundTag();
			entry.putLong("hall", hall.asLong());
			entry.putLongArray("takings", new long[] {Chronicle.day(level), 500, 7}); // a dangling day with no amount
			halls.add(entry);
			old.put("halls", halls);
			ReformStories.Data loaded = load(old, level.registryAccess());
			level.getDataStorage().set(NAME, loaded);
			Leftovers.after(helper, () -> level.getDataStorage().set(NAME, before));

			long today = Chronicle.day(level);
			long dayStart = (today - 1) * VillageNeeds.DAY; // (Chronicle.day counts from 1)
			helper.assertTrue(ReformStories.bell(level, hall) == null, "an old save has a Shift Bell: " + ReformStories.bell(level, hall));
			for (long d = today - 1; d <= today + 2; d++) {
				level.setDayTime((d - 1) * VillageNeeds.DAY + 1000);
				helper.assertTrue(!ReformStories.supper(level, hall) && !ReformStories.grand(level, hall), "an old save holds a supper or grand festival on day " + d);
			}
			Festivals.feast(level, hall);
			helper.assertTrue(!ReformStories.grand(level, hall), "an old save's feast was grand");
			for (long t : new long[] {ReformStories.RING_START, ReformStories.DRILL_END + 20, ReformStories.NIGHT + 100}) {
				level.setDayTime(dayStart + t);
				ReformStories.tick(level);
			}
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(told(level, hall).stream().noneMatch(l -> l.contains(village) && (l.startsWith("Drill day") || l.startsWith("The first lamplit"))),
				"an old save wrote a story line: " + told(level, hall));
			level.setDayTime(dayStart + 1000);
			ItemStack book = ReformStories.ledger(level, hall, ReformStories.Data.get(level).entry(hall));
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			String page = content == null ? "" : content.pages().get(0).raw().getString();
			helper.assertTrue(page.contains("5.00 emeralds"), "the old save's takings: " + page);
			CompoundTag again = ReformStories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(again.getList("halls", 10).getCompound(0).getLongArray("takings").length == 2, "the dangling day was saved back: " + again);
			helper.succeed();
		});
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

	private static ReformStories.Data load(CompoundTag tag, HolderLookup.Provider registries) {
		try {
			Method load = ReformStories.Data.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			load.setAccessible(true);
			return (ReformStories.Data) load.invoke(null, tag, registries);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<String> told(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(c -> c.text().getString()).toList();
	}
}
