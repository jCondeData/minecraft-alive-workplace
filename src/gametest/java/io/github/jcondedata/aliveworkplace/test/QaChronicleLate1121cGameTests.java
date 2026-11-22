package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

/**
 * QA of B1353's fix (lane-c-1121-1555), from the spec: the Chronicle page lists entries newest date first with one day
 * header per day, whatever order they were written in (an entry written late, dated an earlier day: a Cup's entry when its
 * village next loads). The page is read the way the Copperline screen reads it (ChronicleView: the entry slots in order,
 * a header wherever the day changes). Beyond QaChronicleCopper1117dBugGameTests' one Cup entry: several late entries over
 * several days, late entries on the same day as others, the page after a save and reload of the hall, a Show filter,
 * and the "since day N" of the lintel when a late entry is the oldest of all.
 */
public class QaChronicleLate1121cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	/** Days far from any other test's entries, so only ours are read. */
	private static final long BASE = 9000;

	private static CompoundTag mark(ItemStack icon, String key) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(key) ? null : data.copyTag().getCompound(key);
	}

	/** The page's entries as listed, top to bottom: their days and texts (only ours, from day {@link #BASE}). */
	private record Page(List<Long> days, List<String> texts, int headers) {
	}

	private static Page read(ChoiceMenu menu) {
		List<Long> days = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		int headers = 0;
		long last = Long.MIN_VALUE;
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag e = mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY);
			if (e == null || e.getLong("day") < BASE) {
				continue;
			}
			long day = e.getLong("day");
			if (day != last) {
				headers++; // as ChronicleView: a header wherever the day changes
				last = day;
			}
			days.add(day);
			texts.add(menu.icon(slot).getHoverName().getString());
		}
		return new Page(days, texts, headers);
	}

	/** Newest date first, one header per day, and exactly {@code wanted} (days in any order within the check). */
	private static void assertInOrder(GameTestHelper helper, Page page, List<Long> wanted, String when) {
		Set<Long> distinct = new HashSet<>(page.days());
		boolean descending = true;
		for (int i = 1; i < page.days().size(); i++) {
			descending &= page.days().get(i) <= page.days().get(i - 1);
		}
		helper.assertTrue(descending && page.headers() == distinct.size() && page.days().equals(wanted),
			when + ": the page lists days " + page.days() + " (" + page.texts() + ") under " + page.headers() + " day headers; wanted " + wanted
				+ " under " + new HashSet<>(wanted).size());
	}

	private static BlockPos setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	private static void write(ServerLevel level, BlockPos hall, Chronicle.Kind kind, String text, long day) {
		Chronicle.atHall(level, hall, kind, Component.literal(text), day);
	}

	/**
	 * Written in the order 2, 4, then late 1, late 3, then 5, then late 2: the page reads 5, 4, 3, 2, 2, 1 (the two entries
	 * of day 2 under one header), six entries under five headers.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChronicleLate1121cMany"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicleLate1121cMany")
	public void severalLateEntriesOverSeveralDaysListNewestFirstOneHeaderEach(GameTestHelper helper) {
		BlockPos hall = setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			write(level, hall, Chronicle.Kind.BUILT, "A built on day 2", BASE + 2);
			write(level, hall, Chronicle.Kind.RAID, "B raid on day 4", BASE + 4);
			write(level, hall, Chronicle.Kind.FESTIVAL, "C late festival of day 1", BASE + 1);
			write(level, hall, Chronicle.Kind.BIRTH, "D late birth of day 3", BASE + 3);
			write(level, hall, Chronicle.Kind.MARKET, "E market on day 5", BASE + 5);
			// a Cup that ended on day 2 while the village was away, written as it loads (the player's real path, 28.19)
			CupData.get(level).note(hall, new CupData.Note(BASE + 2, Component.Serializer.toJson(Component.literal("F late Cup of day 2"), level.registryAccess())));
			CupDays.writeNotes(level, hall);
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			Page page = read(menu);
			assertInOrder(helper, page, List.of(BASE + 5, BASE + 4, BASE + 3, BASE + 2, BASE + 2, BASE + 1), "late entries over several days");
			helper.assertTrue(page.texts().stream().anyMatch(t -> t.contains("F late Cup")), "the late Cup entry isn't on the page: " + page.texts());
			helper.succeed();
		});
	}

	/**
	 * Same day: three entries of day 7 written around a late one of day 6 and one of day 8: day 7's three sit together under
	 * one header, between day 8 and day 6.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChronicleLate1121cSameDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicleLate1121cSameDay")
	public void entriesOfOneDayWrittenAroundALateOneShareOneHeader(GameTestHelper helper) {
		BlockPos hall = setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			write(level, hall, Chronicle.Kind.BUILT, "day 7 first", BASE + 7);
			write(level, hall, Chronicle.Kind.CUP, "day 6 late", BASE + 6);
			write(level, hall, Chronicle.Kind.BIRTH, "day 7 second", BASE + 7);
			write(level, hall, Chronicle.Kind.RAID, "day 8", BASE + 8);
			write(level, hall, Chronicle.Kind.MARKET, "day 7 third", BASE + 7);
			Page page = read(ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall)));
			assertInOrder(helper, page, List.of(BASE + 8, BASE + 7, BASE + 7, BASE + 7, BASE + 6), "three entries of one day around a late one");
			helper.succeed();
		});
	}

	/**
	 * Saved and loaded again (the hall's block entity, as a chunk reload does): every entry is still there, and the page
	 * still reads newest date first with one header per day, the late entries included.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChronicleLate1121cReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicleLate1121cReload")
	public void afterASaveAndReloadTheLateEntriesStillListInDateOrder(GameTestHelper helper) {
		BlockPos hall = setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			write(level, hall, Chronicle.Kind.RAID, "day 12", BASE + 12);
			write(level, hall, Chronicle.Kind.BUILT, "day 13", BASE + 13);
			write(level, hall, Chronicle.Kind.CUP, "day 11 late", BASE + 11);
			write(level, hall, Chronicle.Kind.CUP, "day 12 late", BASE + 12);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			int before = entity.chronicle().size();
			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity reloaded = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(reloaded != entity && reloaded.chronicle().isEmpty(), "(setup) the hall wasn't placed afresh");
			reloaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(reloaded.chronicle().size() == before, "the reload kept " + reloaded.chronicle().size() + " of " + before + " entries");
			Page page = read(ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall)));
			assertInOrder(helper, page, List.of(BASE + 13, BASE + 12, BASE + 12, BASE + 11), "after a save and reload");
			// and an entry written after the reload, late again, still goes in its place
			write(level, hall, Chronicle.Kind.FESTIVAL, "day 12 later still", BASE + 12);
			page = read(ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall)));
			assertInOrder(helper, page, List.of(BASE + 13, BASE + 12, BASE + 12, BASE + 12, BASE + 11), "a late entry after the reload");
			helper.succeed();
		});
	}

	/**
	 * A Show filter (Festivals: festivals and Cups) on a page with late Cup entries: only that group's entries, still
	 * newest date first, one header per day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChronicleLate1121cFilter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicleLate1121cFilter")
	public void aShowFilterKeepsLateEntriesInDateOrder(GameTestHelper helper) {
		BlockPos hall = setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			write(level, hall, Chronicle.Kind.FESTIVAL, "festival day 21", BASE + 21);
			write(level, hall, Chronicle.Kind.RAID, "raid day 22", BASE + 22);
			write(level, hall, Chronicle.Kind.FESTIVAL, "festival day 23", BASE + 23);
			write(level, hall, Chronicle.Kind.CUP, "cup day 20 late", BASE + 20);
			write(level, hall, Chronicle.Kind.CUP, "cup day 22 late", BASE + 22);
			Page page = read(ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall, Chronicle.Group.FESTIVALS)));
			assertInOrder(helper, page, List.of(BASE + 23, BASE + 22, BASE + 21, BASE + 20), "the Festivals filter");
			helper.assertTrue(page.texts().stream().noneMatch(t -> t.contains("raid")), "a raid under the Festivals filter: " + page.texts());
			page = read(ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall, Chronicle.Group.DEFENSE)));
			assertInOrder(helper, page, List.of(BASE + 22), "the Defense filter");
			helper.succeed();
		});
	}

	/**
	 * The lintel's "N entries since day D" (the page's head): when a late entry is dated before every other one, D is that
	 * entry's day, the chronicle's first, not the day of the first entry written.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaChronicleLate1121cFirst"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicleLate1121cFirst")
	public void aLateEntryOlderThanAllIsTheChroniclesFirstDay(GameTestHelper helper) {
		BlockPos hall = setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			write(level, hall, Chronicle.Kind.BUILT, "day 31", BASE + 31);
			write(level, hall, Chronicle.Kind.RAID, "day 32", BASE + 32);
			write(level, hall, Chronicle.Kind.CUP, "day 30 late", BASE + 30);
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			CompoundTag head = mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD);
			helper.assertTrue(head != null, "(setup) no chronicle head");
			List<Chronicle.Entry> all = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle();
			long oldest = all.stream().mapToLong(Chronicle.Entry::day).min().orElse(-1);
			helper.assertTrue(head.getInt("total") == all.size() && head.getLong("first") == oldest && oldest <= BASE + 30,
				"the head says " + head.getInt("total") + " entries since day " + head.getLong("first") + "; wanted " + all.size() + " since day " + oldest);
			Page page = read(menu);
			assertInOrder(helper, page, List.of(BASE + 32, BASE + 31, BASE + 30), "a late entry older than all");
			// the bottom of the page is the late entry: the oldest
			helper.assertTrue(page.texts().get(page.texts().size() - 1).contains("day 30 late"), "the oldest isn't at the bottom: " + page.texts());
			helper.succeed();
		});
	}
}
