package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.ui.ChronicleLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.ListMenuLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA for ROADMAP 48.4 (lane-d-1117-0107), written from the spec (boards/Chronicle.png, the Copperline README, the commit
 * message), not from the code:
 * <ul>
 * <li>the Chronicle opened as a player does (a right-click on the hall, the Calendar section, the Chronicle button) and
 * each Show filter clicked through the real click packet: every entry shown is in that group, the count on the filter is
 * how many it shows, only that filter is marked open, a group with no entries says so, All brings every entry back, and
 * Back returns to the Calendar its whisper line promises;</li>
 * <li>garbage from the client (slots out of range, a slot with no filter, sidebar ids out of range) changes nothing;</li>
 * <li>an empty chronicle: every count 0, the title's total 0, the empty line, and a filter clicked on it;</li>
 * <li>entries over several days: newest first, the day marks matching the Calendar's date (also with a 5-day season);</li>
 * <li>a very long entry: its whole text reaches the client (through the network codec) and an open entry can be scrolled
 * to its end in the list at GUI scale 2 and 3;</li>
 * <li>save and reload of the hall keeping every entry, its day, kind and group, and the counts;</li>
 * <li>the history cap: 100 kept, the 101st pushes out the oldest, the title's total and first day follow;</li>
 * <li>the list-menu rail at 320x180 and 427x240 with every tab count 1 to 12: each tab its own cell, clickable, on the
 * screen and clear of the list and the close X; the Pokemon Trader's window, close X, book and offers on the screen;</li>
 * <li>every sentence 48.4 added, rendered with real arguments: no stray %s, American English, the counted forms.</li>
 * </ul>
 */
public class QaChronicleCopper1117dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final int[][] SMALL = {{320, 180}, {427, 240}};

	// ---------------------------------------------------------------------------------------------- helpers

	static CompoundTag mark(ItemStack icon, String key) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(key) ? null : data.copyTag().getCompound(key);
	}

	/** The entry rows' slots, top to bottom. */
	static List<Integer> entrySlots(ChoiceMenu menu) {
		List<Integer> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY) != null) {
				out.add(slot);
			}
		}
		return out;
	}

	static List<String> entryTexts(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot : entrySlots(menu)) {
			out.add(menu.icon(slot).getHoverName().getString());
		}
		return out;
	}

	static CompoundTag filter(ChoiceMenu menu, Chronicle.Group group) {
		return mark(menu.icon(VillageHallScreen.CHRONICLE_FILTERS + group.ordinal()), VillageHallScreen.CHRONICLE_FILTER);
	}

	/** A left click on {@code slot}, as the client sends it. */
	static void click(ServerPlayer p, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(p.containerMenu.containerId, p.containerMenu.getStateId(), slot, 0,
			ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	/** A fresh hall at {@link #HALL} and a player beside it. */
	static ServerPlayer hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		HallSections.forget();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		return player;
	}

	/** Opens the hall as a player does, picks the Calendar section and clicks the Chronicle button. */
	static ChoiceMenu openChronicle(GameTestHelper helper, ServerPlayer player) {
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, HallSections.Section.CALENDAR.ordinal()), "the sidebar didn't take Calendar");
		click(player, VillageHallScreen.CHRONICLE);
		helper.assertTrue(mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD) != null,
			"the Chronicle button didn't open the chronicle: slot 4 is " + menu.icon(4).getHoverName().getString());
		return menu;
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	// ---------------------------------------------------------------------------------------------- the Chronicle

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dFilters"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dFilters")
	public void everyShowFilterClickedShowsOnlyItsGroupWithItsCount(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			// what the spec's words put in each group (Trade left with none on purpose)
			Map<Chronicle.Group, List<Chronicle.Kind>> added = new EnumMap<>(Chronicle.Group.class);
			added.put(Chronicle.Group.PEOPLE, List.of(Chronicle.Kind.BIRTH, Chronicle.Kind.WEDDING, Chronicle.Kind.DEATH));
			added.put(Chronicle.Group.BUILDING, List.of(Chronicle.Kind.BUILT, Chronicle.Kind.WONDER));
			added.put(Chronicle.Group.FESTIVALS, List.of(Chronicle.Kind.FESTIVAL));
			added.put(Chronicle.Group.DEFENSE, List.of(Chronicle.Kind.RAID, Chronicle.Kind.SIEGE, Chronicle.Kind.PROTECTION));
			added.put(Chronicle.Group.STORIES, List.of(Chronicle.Kind.STORY, Chronicle.Kind.QUEST));
			int[] expected = new int[Chronicle.Group.values().length];
			for (Chronicle.Entry e : entity.chronicle()) { // whatever placing the hall wrote
				expected[0]++;
				expected[Chronicle.Group.of(e.kind()).ordinal()]++;
			}
			long day = 10;
			for (Map.Entry<Chronicle.Group, List<Chronicle.Kind>> g : added.entrySet()) {
				for (Chronicle.Kind kind : g.getValue()) {
					Chronicle.atHall(level, hall, kind, Component.literal(g.getKey().name() + " " + kind.name()), day++);
					expected[0]++;
					expected[g.getKey().ordinal()]++;
				}
			}
			ChoiceMenu menu = openChronicle(helper, player);
			List<String> problems = new ArrayList<>();
			List<String> all = entryTexts(menu);
			if (all.size() != expected[0]) {
				problems.add("All shows " + all.size() + " entries, not " + expected[0]);
			}
			for (Chronicle.Group group : Chronicle.Group.values()) {
				click(player, VillageHallScreen.CHRONICLE_FILTERS + group.ordinal());
				List<Integer> shown = entrySlots(menu);
				CompoundTag f = filter(menu, group);
				if (f == null || f.getInt("group") != group.ordinal() || f.getInt("count") != expected[group.ordinal()] || shown.size() != expected[group.ordinal()]) {
					problems.add(group + ": filter " + f + ", shows " + shown.size() + ", expected " + expected[group.ordinal()]);
				}
				for (Chronicle.Group other : Chronicle.Group.values()) {
					CompoundTag o = filter(menu, other);
					if (o == null || o.getBoolean("open") != (other == group)) {
						problems.add("after " + group + "'s click, " + other + " is marked " + o);
					}
				}
				for (int slot : shown) {
					CompoundTag e = mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY);
					if (group != Chronicle.Group.ALL && e.getInt("group") != group.ordinal()) {
						problems.add(group + " shows '" + menu.icon(slot).getHoverName().getString() + "' of group " + e.getInt("group"));
					}
				}
				if (group != Chronicle.Group.ALL && added.containsKey(group)) {
					for (Chronicle.Kind kind : added.get(group)) {
						if (!entryTexts(menu).contains(group.name() + " " + kind.name())) {
							problems.add(group + " is missing its " + kind + " entry: " + entryTexts(menu));
						}
					}
				}
				if (expected[group.ordinal()] == 0) {
					String none = menu.icon(VillageHallScreen.FIRST_ROW + 4).getHoverName().getString();
					String want = "No " + group.title().getString() + " entries yet";
					if (!none.equals(want)) {
						problems.add("the empty " + group + " filter reads '" + none + "', not '" + want + "'");
					}
				}
			}
			click(player, VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.ALL.ordinal());
			if (!entryTexts(menu).equals(all)) {
				problems.add("All doesn't bring every entry back in order: " + entryTexts(menu));
			}
			// Back: "Back to the Calendar."
			click(player, 0);
			if (mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD) != null || HallSections.current(menu) != HallSections.Section.CALENDAR) {
				problems.add("Back left " + HallSections.current(menu) + " (chronicle still showing: " + (mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD) != null)
					+ "), not the Calendar its whisper line names");
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dGarbage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dGarbage")
	public void garbageClicksFromTheClientChangeNothing(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.RAID, Component.literal("Raid at the south gate"), 5);
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.BIRTH, Component.literal("Ada was born"), 6);
			ChoiceMenu menu = openChronicle(helper, player);
			click(player, VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.DEFENSE.ordinal());
			List<String> before = entryTexts(menu);
			List<String> problems = new ArrayList<>();
			// slots that are no filter: past the seven, outside the window, negative, past the menu, the player's inventory
			int[] garbage = {VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.values().length, -999, -1, -5, ChoiceMenu.SIZE + 3, 9999};
			for (int slot : garbage) {
				try {
					click(player, slot);
				} catch (RuntimeException e) {
					problems.add("slot " + slot + " threw " + e);
				}
				if (!(player.containerMenu == menu) || !entryTexts(menu).equals(before) || !filter(menu, Chronicle.Group.DEFENSE).getBoolean("open")) {
					problems.add("a click on slot " + slot + " changed the page: " + entryTexts(menu));
				}
			}
			for (int id : new int[]{-1, -100, HallSections.Section.values().length, 77}) {
				boolean taken;
				try {
					taken = menu.clickMenuButton(player, id);
				} catch (RuntimeException e) {
					problems.add("menu button " + id + " threw " + e);
					continue;
				}
				if (taken || !entryTexts(menu).equals(before)) {
					problems.add("menu button " + id + " was taken (" + taken + ") or changed the page: " + entryTexts(menu));
				}
			}
			if (!before.equals(List.of("Raid at the south gate"))) {
				problems.add("Defense showed " + before);
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dEmpty")
	public void anEmptyChronicleCountsZeroAndSaysSo(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			// a hall loaded with no chronicle at all, as an old save without the list
			CompoundTag saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
			saved.remove("chronicle");
			entity.loadWithComponents(saved, helper.getLevel().registryAccess());
			List<String> problems = new ArrayList<>();
			if (!entity.chronicle().isEmpty()) {
				problems.add("the hall still keeps " + entity.chronicle().size() + " entries");
			}
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, helper.getLevel(), hall));
			CompoundTag head = mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD);
			if (head == null || head.getInt("total") != 0 || head.getLong("first") < 1) {
				problems.add("the title's mark is " + head);
			}
			for (Chronicle.Group group : Chronicle.Group.values()) {
				CompoundTag f = filter(menu, group);
				if (f == null || f.getInt("count") != 0) {
					problems.add(group + " counts " + f);
				}
				String tip = menu.icon(VillageHallScreen.CHRONICLE_FILTERS + group.ordinal()).getOrDefault(DataComponents.LORE,
					net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream().map(Component::getString).toList().toString();
				if (!tip.contains("0 entries: click to show only these")) {
					problems.add(group + "'s tooltip doesn't say 0 entries: " + tip);
				}
			}
			for (Chronicle.Group group : new Chronicle.Group[]{Chronicle.Group.ALL, Chronicle.Group.STORIES}) {
				menu.press(VillageHallScreen.CHRONICLE_FILTERS + group.ordinal(), player);
				String line = menu.icon(VillageHallScreen.FIRST_ROW + 4).getHoverName().getString();
				if (!entrySlots(menu).isEmpty() || line.isEmpty() || line.contains("%") || line.startsWith("screen.")) {
					problems.add("an empty chronicle on " + group + " shows " + entryTexts(menu) + " and '" + line + "'");
				}
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dDays")
	public void entriesOverSeveralDaysAreNewestFirstWithTheCalendarsDates(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		int was = Seasons.DAYS;
		Leftovers.after(helper, () -> Seasons.DAYS = was);
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			for (int length : new int[]{was, 5}) {
				Seasons.DAYS = length;
				VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
				CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
				saved.remove("chronicle");
				entity.loadWithComponents(saved, level.registryAccess());
				long[] days = {1, 1, 4, 16, 17, 17, 17, 47, 64, 65};
				for (int i = 0; i < days.length; i++) {
					Chronicle.atHall(level, hall, Chronicle.Kind.BUILT, Component.literal("entry " + i), days[i]);
				}
				ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
				List<Integer> slots = entrySlots(menu);
				if (slots.size() != days.length) {
					problems.add(length + "-day seasons: " + slots.size() + " entries shown, not " + days.length);
					continue;
				}
				long last = Long.MAX_VALUE;
				int headers = 0;
				for (int i = 0; i < slots.size(); i++) {
					CompoundTag e = mark(menu.icon(slots.get(i)), VillageHallScreen.CHRONICLE_ENTRY);
					long day = e.getLong("day");
					if (day > last) {
						problems.add(length + "-day seasons: day " + day + " listed after day " + last);
					}
					headers += day != last ? 1 : 0;
					last = day;
					Seasons.Date date = Seasons.date(day, length);
					if (e.getInt("season") != date.season().ordinal() || e.getInt("of") != date.dayOfSeason() || e.getLong("year") != date.year()) {
						problems.add(length + "-day seasons: day " + day + " marked " + e + ", the calendar says " + date);
					}
				}
				long distinct = java.util.Arrays.stream(days).distinct().count();
				if (headers != distinct) {
					problems.add(length + "-day seasons: " + headers + " day headers for " + distinct + " days");
				}
				// an entry written today carries the Calendar's today
				CompoundTag clean = entity.saveWithoutMetadata(level.registryAccess());
				clean.remove("chronicle");
				entity.loadWithComponents(clean, level.registryAccess());
				Chronicle.record(level, hall, Chronicle.Kind.FESTIVAL, Component.literal("today"));
				ChoiceMenu todayMenu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
				List<Integer> todaySlots = entrySlots(todayMenu);
				Seasons.Date now = Seasons.today(level);
				CompoundTag today = todaySlots.size() == 1 ? mark(todayMenu.icon(todaySlots.get(0)), VillageHallScreen.CHRONICLE_ENTRY) : null;
				if (today == null || today.getLong("day") != Chronicle.day(level) || today.getInt("season") != now.season().ordinal()
					|| today.getInt("of") != now.dayOfSeason() || today.getLong("year") != now.year()) {
					problems.add(length + "-day seasons: today's entry is dated " + today + ", the Calendar's today is " + now);
				}
				// the header the player reads: "Day 17 · Summer 1" with 16-day seasons
				Seasons.Date d17 = Seasons.date(17, length);
				String header = Component.translatable("screen.aliveworkplace.hall.chronicle.day_header", 17L, d17.season().title(), d17.dayOfSeason()).getString();
				String want = "Day 17 · " + d17.season().title().getString() + " " + d17.dayOfSeason();
				if (!header.equals(want)) {
					problems.add("the day header reads '" + header + "', not '" + want + "'");
				}
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dLong"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dLong")
	public void aVeryLongEntryReachesTheClientWholeAndScrollsToItsEnd(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; sb.length() < 1500; i++) {
				sb.append("Nine zombies came up the south road and Garth held the wall ").append(i).append(". ");
			}
			String text = sb.toString().trim() + " THE END";
			Chronicle.atHall(level, hall, Chronicle.Kind.SIEGE, Component.literal(text), 46);
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			List<String> problems = new ArrayList<>();
			int slot = -1;
			for (int s : entrySlots(menu)) {
				if (menu.icon(s).getHoverName().getString().startsWith("Nine zombies")) {
					slot = s;
				}
			}
			if (slot < 0) {
				helper.fail("the long entry isn't shown: " + entryTexts(menu));
				return;
			}
			if (!menu.icon(slot).getHoverName().getString().equals(text)) {
				problems.add("the server cut the entry to " + menu.icon(slot).getHoverName().getString().length() + " of " + text.length() + " characters");
			}
			// what the client receives
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, menu.icon(slot));
			ItemStack received = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			CompoundTag e = mark(received, VillageHallScreen.CHRONICLE_ENTRY);
			if (!received.getHoverName().getString().equals(text) || e == null || e.getLong("day") != 46 || e.getInt("group") != Chronicle.Group.DEFENSE.ordinal()) {
				problems.add("the client's copy lost the text or the marks: " + received.getHoverName().getString().length() + " characters, " + e);
			}
			// open, wrapped at the list's width (6 pixels a character, as the game font's widest common letters), it can be
			// scrolled so its last line is inside the list at GUI scale 2 and 3
			for (int[] screen : new int[][]{{320, 180}, {427, 240}, {640, 360}}) {
				boolean compact = CopperLayout.compact(screen[0], screen[1]);
				int[] win = CopperLayout.window(screen[0], screen[1]);
				int[] body = CopperLayout.body(win, CopperLayout.railWidth(win, 60, true));
				int columnW = ChronicleLayout.columnWidth(body, 54, 18, compact);
				int[] list = ChronicleLayout.list(body, columnW);
				int lines = (int) Math.ceil(text.length() * 6.0 / ChronicleLayout.textWidth(list));
				boolean[] header = {true, false, true, false};
				int[] heights = {0, ChronicleLayout.entryHeight(1, false), 0, ChronicleLayout.entryHeight(lines, true)};
				int[] tops = ChronicleLayout.rows(list, header, heights, 0);
				int max = ChronicleLayout.maxScroll(list, tops[header.length]);
				int[] end = ChronicleLayout.rows(list, header, heights, max);
				int bottom = end[3] + heights[3];
				String at = screen[0] + "x" + screen[1] + ": ";
				if (heights[3] < 3 + lines * ChronicleLayout.LINE) {
					problems.add(at + "an open entry of " + lines + " lines is only " + heights[3] + " tall");
				}
				if (bottom > list[1] + list[3]) {
					problems.add(at + "scrolled to the end, the long entry's foot " + bottom + " is still under the list's " + (list[1] + list[3]));
				}
				if (end[3] + heights[3] < list[1]) {
					problems.add(at + "scrolled past the long entry's end");
				}
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dReload")
	public void saveAndReloadKeepEveryEntryItsDayAndGroup(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Chronicle.Kind[] kinds = {Chronicle.Kind.BIRTH, Chronicle.Kind.MARKET, Chronicle.Kind.CUP, Chronicle.Kind.FIRE, Chronicle.Kind.LEGEND,
				Chronicle.Kind.WONDER, Chronicle.Kind.FUNERAL, Chronicle.Kind.REALM};
			for (int i = 0; i < kinds.length; i++) {
				Chronicle.atHall(level, hall, kinds[i], Component.translatable("screen.aliveworkplace.hall.chronicle_day", 100 + i), 20 + i);
			}
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			List<Chronicle.Entry> before = entity.chronicle();
			ChoiceMenu first = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(hall, level.getBlockState(hall), saved, level.registryAccess());
			List<String> problems = new ArrayList<>();
			if (!(loaded instanceof VillageHallBlockEntity copy)) {
				helper.fail("the saved hall loaded as " + loaded);
				return;
			}
			List<Chronicle.Entry> after = copy.chronicle();
			if (after.size() != before.size()) {
				problems.add(after.size() + " entries after the reload, " + before.size() + " before");
			} else {
				for (int i = 0; i < before.size(); i++) {
					Chronicle.Entry a = before.get(i);
					Chronicle.Entry b = after.get(i);
					if (a.day() != b.day() || a.kind() != b.kind() || !a.text().getString().equals(b.text().getString())
						|| Chronicle.Group.of(a.kind()) != Chronicle.Group.of(b.kind())) {
						problems.add("entry " + i + " was " + a + ", is " + b);
					}
				}
			}
			// the page of the reloaded hall shows the same counts
			entity.loadWithComponents(saved, level.registryAccess());
			ChoiceMenu second = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			for (Chronicle.Group g : Chronicle.Group.values()) {
				if (filter(first, g).getInt("count") != filter(second, g).getInt("count")) {
					problems.add(g + " counted " + filter(first, g).getInt("count") + " before the reload and " + filter(second, g).getInt("count") + " after");
				}
			}
			if (!entryTexts(first).equals(entryTexts(second))) {
				problems.add("the entries changed with the reload: " + entryTexts(second));
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"qaChronicle1117dCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChronicle1117dCap")
	public void theHundredAndFirstEntryPushesOutTheOldest(GameTestHelper helper) {
		ServerPlayer player = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
			saved.remove("chronicle");
			entity.loadWithComponents(saved, level.registryAccess());
			List<String> problems = new ArrayList<>();
			for (int i = 1; i <= Chronicle.MAX; i++) {
				Chronicle.atHall(level, hall, i % 2 == 0 ? Chronicle.Kind.RAID : Chronicle.Kind.BIRTH, Component.literal("entry " + i), i);
			}
			ChoiceMenu full = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			CompoundTag head = mark(full.icon(4), VillageHallScreen.CHRONICLE_HEAD);
			if (entity.chronicle().size() != Chronicle.MAX || head.getInt("total") != Chronicle.MAX || head.getLong("first") != 1
				|| filter(full, Chronicle.Group.ALL).getInt("count") != Chronicle.MAX) {
				problems.add("at the cap: kept " + entity.chronicle().size() + ", title " + head + ", All " + filter(full, Chronicle.Group.ALL));
			}
			Chronicle.atHall(level, hall, Chronicle.Kind.FESTIVAL, Component.literal("entry 101"), 101);
			ChoiceMenu over = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, level, hall));
			head = mark(over.icon(4), VillageHallScreen.CHRONICLE_HEAD);
			List<Chronicle.Entry> kept = entity.chronicle();
			if (kept.size() != Chronicle.MAX || !kept.get(0).text().getString().equals("entry 2") || !kept.get(kept.size() - 1).text().getString().equals("entry 101")) {
				problems.add("past the cap the hall keeps " + kept.size() + " from '" + kept.get(0).text().getString() + "'");
			}
			if (head.getInt("total") != Chronicle.MAX || head.getLong("first") != 2) {
				problems.add("past the cap the title says " + head + " (100 entries since day 2 wanted)");
			}
			int people = filter(over, Chronicle.Group.PEOPLE).getInt("count");
			int defense = filter(over, Chronicle.Group.DEFENSE).getInt("count");
			int festivals = filter(over, Chronicle.Group.FESTIVALS).getInt("count");
			if (people != 49 || defense != 50 || festivals != 1 || filter(over, Chronicle.Group.ALL).getInt("count") != Chronicle.MAX) {
				problems.add("past the cap the counts are People " + people + ", Defense " + defense + ", Festivals " + festivals);
			}
			List<String> shown = entryTexts(over);
			int rows = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
			if (shown.size() != Math.min(rows, Chronicle.MAX) || !shown.get(0).equals("entry 101") || !shown.get(1).equals("entry 100")) {
				problems.add("All shows " + shown.size() + " entries starting " + (shown.isEmpty() ? "nowhere" : shown.get(0)));
			}
			over.press(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.FESTIVALS.ordinal(), player);
			if (!entryTexts(over).equals(List.of("entry 101"))) {
				problems.add("Festivals shows " + entryTexts(over));
			}
			over.press(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.DEFENSE.ordinal(), player);
			List<String> def = entryTexts(over);
			if (def.size() != Math.min(rows, defense) || !def.get(0).equals("entry 100") || !def.get(1).equals("entry 98")) {
				problems.add("Defense shows " + def.size() + " entries starting " + (def.isEmpty() ? "nowhere" : def.get(0)));
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------------- layouts

	//$ gametest_batch EMPTY_STRUCTURE '"qaChronicle1117dLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaChronicle1117dLayout")
	public void everyListMenuTabIsReachableOnSmallScreens(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : SMALL) {
			int[] scr = {0, 0, screen[0], screen[1]};
			boolean compact = CopperLayout.compact(screen[0], screen[1]);
			int[] win = CopperLayout.window(screen[0], screen[1]);
			int[] close = CopperLayout.close(win);
			for (int tabs = 1; tabs <= 12; tabs++) {
				ListMenuLayout.Boxes b = ListMenuLayout.layout(win, tabs, 90, new int[]{12, 12}, compact);
				String at = screen[0] + "x" + screen[1] + " " + tabs + " tabs: ";
				if (b.tabs().length != tabs) {
					problems.add(at + "only " + b.tabs().length + " laid out");
					continue;
				}
				for (int i = 0; i < tabs; i++) {
					int[] t = b.tabs()[i];
					if (!within(t, scr) || !within(t, win) || CopperLayout.overlap(t, b.list()) || CopperLayout.overlap(t, close) || t[2] < 12 || t[3] < 12) {
						problems.add(at + "tab " + i + " " + box(t) + " is off the screen, under the list or the X, or too small to click");
					}
					if (CopperLayout.at(b.tabs(), t[0] + t[2] / 2.0, t[1] + t[3] / 2.0) != i) {
						problems.add(at + "a click on tab " + i + "'s middle picks " + CopperLayout.at(b.tabs(), t[0] + t[2] / 2.0, t[1] + t[3] / 2.0));
					}
					for (int j = i + 1; j < tabs; j++) {
						if (CopperLayout.overlap(t, b.tabs()[j])) {
							problems.add(at + "tabs " + i + " and " + j + " overlap");
						}
					}
				}
				if (!within(b.list(), scr) || b.list()[2] < 150) {
					problems.add(at + "the list " + box(b.list()) + " is off the screen or too narrow");
				}
				for (int[] p : b.pages()) {
					if (!within(p, scr) || CopperLayout.overlap(p, close)) {
						problems.add(at + "a page arrow " + box(p) + " is off the screen or under the X");
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	//$ gametest_batch EMPTY_STRUCTURE '"qaChronicle1117dLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaChronicle1117dLayout")
	public void theShowColumnNeverPutsAFilterUnderBackAtAnyHeight(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int groups = Chronicle.Group.values().length;
		for (boolean compact : new boolean[]{false, true}) {
			for (int height = 60; height <= 260; height++) {
				int[] column = {10, 30, compact ? ChronicleLayout.COMPACT_W : 80, height};
				int[][] filters = ChronicleLayout.filters(column, groups, compact);
				int[] back = ChronicleLayout.back(column);
				for (int i = 0; i < filters.length; i++) {
					if (!within(filters[i], column) || CopperLayout.overlap(filters[i], back) || filters[i][1] + filters[i][3] > back[1]
						|| filters[i][3] < ChronicleLayout.FILTER_MIN) {
						problems.add((compact ? "compact " : "") + "height " + height + ": filter " + i + " " + box(filters[i]) + " runs into Back " + box(back));
					}
					if (i > 0 && filters[i][1] < filters[i - 1][1] + filters[i - 1][3]) {
						problems.add("height " + height + ": filters " + (i - 1) + " and " + i + " overlap");
					}
				}
				// tall enough for all seven at their smallest: none left off
				int room = height - (compact ? 0 : ChronicleLayout.HEADING_H) - ChronicleLayout.BACK_H - 2;
				if (room >= groups * ChronicleLayout.FILTER_MIN && filters.length != groups) {
					problems.add((compact ? "compact " : "") + "height " + height + ": only " + filters.length + " filters with room for " + groups);
				}
			}
		}
		// a row exactly at the list's top or foot is wholly inside it and takes clicks; one a pixel out doesn't
		int[] list = {50, 40, 200, 120};
		if (!ChronicleLayout.shown(list, 40, 14) || !ChronicleLayout.shown(list, 160 - 14, 14) || ChronicleLayout.shown(list, 39, 14)
			|| ChronicleLayout.shown(list, 160 - 13, 14)) {
			problems.add("rows at the list's edges are taken or refused wrongly");
		}
		if (ChronicleLayout.maxScroll(list, 120) != 0 || ChronicleLayout.maxScroll(list, 121) != 1) {
			problems.add("the list scrolls " + ChronicleLayout.maxScroll(list, 120) + " for content that fits");
		}
		helper.assertTrue(problems.size() <= 0, String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	//$ gametest_batch EMPTY_STRUCTURE '"qaChronicle1117dLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaChronicle1117dLayout")
	public void thePokemonTradersCloseBookAndOffersAreOnSmallScreens(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : SMALL) {
			int[] scr = {0, 0, screen[0], screen[1]};
			// centered as a container screen centers its image
			int[] win = {(screen[0] - PokemonTradeView.WIDTH) / 2, (screen[1] - PokemonTradeView.HEIGHT) / 2, PokemonTradeView.WIDTH, PokemonTradeView.HEIGHT};
			String at = screen[0] + "x" + screen[1] + ": ";
			int[] close = CopperLayout.close(win);
			int[] book = {win[0] + PokemonTradeView.INFO_X - 1, win[1] + PokemonTradeView.INFO_Y - 1, 18, 18};
			int[] list = {win[0] + PokemonTradeView.LIST_X, win[1] + PokemonTradeView.LIST_Y, PokemonTradeView.LIST_W, PokemonTradeView.LIST_H};
			if (!within(win, scr) || !within(close, scr) || !within(close, win)) {
				problems.add(at + "the window " + box(win) + " or its close X " + box(close) + " is off the screen");
			}
			if (!within(book, scr) || CopperLayout.overlap(book, close)) {
				problems.add(at + "the book " + box(book) + " is off the screen or under the X");
			}
			if (!within(list, scr) || CopperLayout.overlap(list, close) || CopperLayout.overlap(list, CopperLayout.whisper(win))) {
				problems.add(at + "the offers " + box(list) + " are off the screen or under the X or the whisper line");
			}
			for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
				int y = PokemonTradeView.cardY(i) + PokemonTradeView.ROW_H / 2;
				if (PokemonTradeView.offerAt(PokemonTradeView.LIST_X + PokemonTradeView.LIST_W / 2, y) != i || win[1] + PokemonTradeView.cardY(i) + PokemonTradeView.ROW_H > screen[1]) {
					problems.add(at + "offer " + i + " can't be picked at its middle (y " + y + ")");
				}
			}
			int[] whisper = CopperLayout.whisper(win);
			if (!within(whisper, scr)) {
				problems.add(at + "the whisper line " + box(whisper) + " is off the screen");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------- words

	private static final Pattern BRITISH = Pattern.compile("\\b(colou?rs?|defence|honour|centre|cancelled|grey|armour|favourite|neighbour|travell)",
		Pattern.CASE_INSENSITIVE);

	//$ gametest_batch EMPTY_STRUCTURE '"qaChronicle1117dLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaChronicle1117dLayout")
	public void everyNewSentenceReadsRightWithItsArguments(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Component village = Component.literal("Oakhaven");
		Map<String, String> want = new java.util.LinkedHashMap<>();
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.where", village, 128, 1).getString(), "Oakhaven · 128 entries since day 1");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.where.one", village, 1, 3).getString(), "Oakhaven · 1 entry since day 3");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.count", 128).getString(), "128 entries");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.count.one", 1).getString(), "1 entry");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.filter_count.one", 1).getString(), "1 entry: click to show only it");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.none_shown", Chronicle.Group.TRADE.title()).getString(), "No Trade entries yet");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.show_whisper", Chronicle.Group.PEOPLE.title(), 40).getString(),
			"Show only People entries (40).");
		want.put(Component.translatable("screen.aliveworkplace.hall.chronicle.show_all_whisper", Chronicle.Group.ALL.title(), 128).getString(),
			"Show every entry (128).");
		want.put(Component.translatable("screen.aliveworkplace.pokemon_trader.offers.one", 1).getString(), "1 offer today");
		want.put(Component.translatable("screen.aliveworkplace.pokemon_trader.offers", 3).getString(), "3 offers today");
		want.put(Component.translatable("screen.aliveworkplace.list.tab_whisper", "Pikachu", "Can learn 2 moves").getString(), "Pikachu: Can learn 2 moves");
		for (Map.Entry<String, String> e : want.entrySet()) {
			if (!e.getKey().equals(e.getValue())) {
				problems.add("'" + e.getKey() + "' should read '" + e.getValue() + "'");
			}
		}
		// the board's group words, in its order (Stories after Defense)
		String[] words = {"All", "People", "Building", "Trade", "Festivals", "Defense", "Stories"};
		for (Chronicle.Group g : Chronicle.Group.values()) {
			if (!g.title().getString().equals(words[g.ordinal()])) {
				problems.add(g + " reads '" + g.title().getString() + "', not '" + words[g.ordinal()] + "'");
			}
		}
		// every key 48.4 added: present, no stray format codes once filled, American English
		Language lang = Language.getInstance();
		String[] keys = {"screen.aliveworkplace.pokemon_trader.offers", "screen.aliveworkplace.pokemon_trader.offers.one",
			"screen.aliveworkplace.pokemon_trader.offers_none", "screen.aliveworkplace.pokemon_trader.offer_whisper",
			"screen.aliveworkplace.pokemon_trader.close_whisper", "screen.aliveworkplace.list.tab_whisper",
			"screen.aliveworkplace.hall.chronicle.filter_count", "screen.aliveworkplace.hall.chronicle.filter_count.one",
			"screen.aliveworkplace.hall.chronicle.none_shown", "screen.aliveworkplace.hall.chronicle.show", "screen.aliveworkplace.hall.chronicle.where",
			"screen.aliveworkplace.hall.chronicle.where.one", "screen.aliveworkplace.hall.chronicle.count", "screen.aliveworkplace.hall.chronicle.count.one",
			"screen.aliveworkplace.hall.chronicle.day_header", "screen.aliveworkplace.hall.chronicle.hint", "screen.aliveworkplace.hall.chronicle.show_whisper",
			"screen.aliveworkplace.hall.chronicle.show_all_whisper", "screen.aliveworkplace.hall.chronicle.back_whisper",
			"screen.aliveworkplace.hall.chronicle.entry_whisper", "screen.aliveworkplace.hall.chronicle.open_whisper",
			"screen.aliveworkplace.hall.chronicle.close_whisper"};
		for (String key : keys) {
			if (!lang.has(key)) {
				problems.add(key + " is missing");
				continue;
			}
			String filled = Component.translatable(key, "A", 2, 3).getString();
			if (filled.contains("%") || filled.equals(key)) {
				problems.add(key + " fills to '" + filled + "'");
			}
			if (BRITISH.matcher(lang.getOrDefault(key)).find()) {
				problems.add(key + " isn't American English: " + lang.getOrDefault(key));
			}
		}
		for (Chronicle.Group g : Chronicle.Group.values()) {
			if (BRITISH.matcher(g.title().getString()).find() || g.title().getString().startsWith("screen.")) {
				problems.add(g + "'s word is '" + g.title().getString() + "'");
			}
			if (!g.name().toLowerCase(Locale.ROOT).equals(g.name().toLowerCase(Locale.ROOT).trim())) {
				problems.add(g + " has a stray space");
			}
		}
		String written = Component.translatable("screen.aliveworkplace.hall.chronicle.open_whisper",
			Component.translatable("screen.aliveworkplace.hall.chronicle.day_header", 47L, Seasons.Season.AUTUMN.title(), 5)).getString();
		if (!written.equals("Written on Day 47 · " + Seasons.Season.AUTUMN.title().getString() + " 5. Click to read all of it.")) {
			problems.add("an entry's whisper reads '" + written + "'");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
