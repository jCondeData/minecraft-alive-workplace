package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * The Village Hall's sidebar (ROADMAP 40.1, {@code docs/design/M40.md}): ten sections, each a labeled button on the
 * left of the hall's screen. The server lays the hall's main page out as it always has (the same slots and clicks);
 * this marks each of its buttons with the sections it belongs to and keeps, in the menu's numbers ({@link ChoiceMenu#state}),
 * the open section, which sections are empty and today's date. The client shows only the open section's buttons.
 *
 * <p>A sidebar click is the menu's button click ({@link ChoiceMenu#onMenuButton}) with the section's number; it opens the
 * main page on that section. The section a player last opened is remembered while the server runs.
 */
public final class HallSections {
	/** The menu's numbers: the open section, a bit for each empty section, the season, its day and the year. */
	public static final int SECTION = 0;
	public static final int EMPTY = 1;
	public static final int SEASON = 2;
	public static final int DAY = 3;
	public static final int YEAR = 4;
	/** The People table's numbers (40.2, {@link HallPeople}): everyone listed, working, jobless, children, the first person shown. */
	public static final int PEOPLE_TOTAL = 5;
	public static final int PEOPLE_WORKING = 6;
	public static final int PEOPLE_JOBLESS = 7;
	public static final int PEOPLE_CHILDREN = 8;
	public static final int PEOPLE_FIRST = 9;
	/**
	 * The rail's counts (Copperline, 47.2), set as the main page is laid out: the people waiting for a job, whether an
	 * attack is coming (1) and the open quests. Not saved; 0 shows nothing.
	 */
	public static final int RAIL_WAITING = 10;
	public static final int RAIL_DANGER = 11;
	public static final int RAIL_QUESTS = 12;
	/**
	 * The Overview's header (Copperline, 47.2, Main.png): the village's rank ({@link VillageRanks.Rank}'s number) and how far
	 * it is toward the next one, 0 to 100 (100 at the top rank). Not saved.
	 */
	public static final int RANK = 13;
	public static final int RANK_PROGRESS = 14;
	/** The mark on a main-page button: a bit for each section it sits in. */
	public static final String MARK = "aliveworkplace:hall_sections";

	public enum Section {
		OVERVIEW(Items.BELL), PEOPLE(Items.EMERALD), HOMES(Items.RED_BED), BUILDINGS(Items.BRICKS), DEFENSE(Items.IRON_SWORD),
		QUESTS(Items.MAP), TRADE(Items.CHEST_MINECART), CALENDAR(Items.CLOCK), LAWS(Items.LECTERN), MAP(Items.FILLED_MAP);

		/** The sidebar button's icon (a vanilla item). */
		public final Item icon;

		Section(Item icon) {
			this.icon = icon;
		}

		public String id() {
			return name().toLowerCase();
		}

		public int bit() {
			return 1 << ordinal();
		}

		/** The sidebar button's word ("Homes & Food"). */
		public Component title() {
			return Component.translatable("screen.aliveworkplace.hall.section." + id());
		}

		/** What the section is for, in one line (its pane with nothing chosen). */
		public Component about() {
			return Component.translatable("screen.aliveworkplace.hall.section." + id() + ".about");
		}

		/** The plain sentence an empty section shows at the top of its list. */
		public Component empty() {
			return Component.translatable("screen.aliveworkplace.hall.section." + id() + ".empty");
		}

		/** Whether this section can be empty (Overview, Calendar and Map always have something). */
		public boolean canBeEmpty() {
			return this != OVERVIEW && this != CALENDAR && this != MAP;
		}

		/** The section numbered {@code index} (Overview for a number that's none). */
		public static Section of(int index) {
			Section[] all = values();
			return index >= 0 && index < all.length ? all[index] : OVERVIEW;
		}
	}

	private static int mask(Section... sections) {
		int out = 0;
		for (Section s : sections) {
			out |= s.bit();
		}
		return out;
	}

	/** Where the hall's own pages go (the rest: {@link #place}, else Overview). */
	private static final Map<String, Integer> PAGES = new ConcurrentHashMap<>(Map.of(
		Seasons.PAGE, mask(Section.CALENDAR),
		io.github.jcondedata.aliveworkplace.cup.CupPage.PAGE, mask(Section.CALENDAR),
		io.github.jcondedata.aliveworkplace.legend.LegendsPage.PAGE, mask(Section.PEOPLE),
		ClassesPage.PAGE, mask(Section.PEOPLE),
		FreeJobsPage.PAGE, mask(Section.PEOPLE),
		io.github.jcondedata.aliveworkplace.wonder.WondersPage.PAGE, mask(Section.BUILDINGS),
		RepairsPage.PAGE, mask(Section.BUILDINGS),
		UpgradesPage.PAGE, mask(Section.BUILDINGS)));

	/** The section each player last opened, while the server runs (40.1: not saved). */
	private static final Map<UUID, Section> LAST = new ConcurrentHashMap<>();

	/** Puts the hall page {@code pageId}'s tab under {@code sections} (a page placed nowhere goes under Overview). */
	public static void place(String pageId, Section... sections) {
		PAGES.put(pageId, mask(sections));
	}

	/** The sections a hall page's tab goes under. */
	public static int sectionsOf(String pageId) {
		return PAGES.getOrDefault(pageId, Section.OVERVIEW.bit());
	}

	/** The sections of a fixed button of the main page (0 for none: the page row and the people list are marked apart). */
	static int fixed(int slot) {
		return switch (slot) {
			case VillageHallScreen.NAME -> mask(Section.OVERVIEW, Section.LAWS);
			case VillageHallScreen.PEOPLE, VillageHallScreen.WELLBEING, VillageHallScreen.REQUESTS -> mask(Section.OVERVIEW, Section.PEOPLE);
			case VillageHallScreen.BEDS, VillageHallScreen.FOOD -> mask(Section.OVERVIEW, Section.HOMES);
			case VillageHallScreen.GUARDS -> mask(Section.OVERVIEW, Section.DEFENSE);
			case VillageHallScreen.BUILDS -> mask(Section.OVERVIEW, Section.BUILDINGS);
			case VillageHallScreen.QUESTS -> mask(Section.OVERVIEW, Section.QUESTS);
			case VillageHallScreen.BOOK -> mask(Section.LAWS);
			case VillageHallScreen.MAP -> mask(Section.MAP);
			case VillageHallScreen.RECALL -> mask(Section.PEOPLE, Section.DEFENSE);
			case VillageHallScreen.MERCENARIES -> mask(Section.DEFENSE);
			case VillageHallScreen.CHRONICLE, VillageHallScreen.FESTIVAL -> mask(Section.CALENDAR);
			case VillageHallScreen.ROUTES, PlayerBank.BUTTON -> mask(Section.TRADE);
			case VillageHallScreen.ADVICE -> mask(Section.OVERVIEW, Section.BUILDINGS);
			default -> 0;
		};
	}

	/** Marks {@code icon} as a button of {@code sections}. */
	public static void mark(ItemStack icon, int sections) {
		if (icon.isEmpty()) {
			return;
		}
		CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(MARK, sections);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** The sections {@code icon} is a button of (0: not a main-page button, as on any other page). */
	public static int marks(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(MARK) ? 0 : data.copyTag().getInt(MARK);
	}

	/** What a main page needs to know to say which sections are empty. */
	record Counts(int villagers, int beds, long food, int builds, int guards, int quests, boolean neighbours, boolean laws) {
	}

	/**
	 * After the main page is laid out: marks every button with its sections (the page tabs by their page, the people
	 * list and its arrows under People) and sets the menu's numbers.
	 */
	static void apply(ChoiceMenu menu, ServerLevel level, java.util.List<HallPages.Page> tabs, Counts counts) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty() || icon.has(DataComponents.HIDE_TOOLTIP)) {
				continue;
			}
			int sections;
			if (slot >= VillageHallScreen.PAGE_ROW && slot < VillageHallScreen.PAGE_ROW + 9) {
				int i = slot - VillageHallScreen.PAGE_ROW;
				sections = i < tabs.size() ? sectionsOf(tabs.get(i).id()) : 0;
			} else if (slot >= VillageHallScreen.FIRST_PERSON) {
				sections = Section.PEOPLE.bit();
			} else {
				sections = fixed(slot);
			}
			if (sections == 0) {
				sections = Section.OVERVIEW.bit(); // a button placed nowhere is never dropped
			}
			mark(icon, sections);
		}
		int empty = 0;
		if (counts.villagers() == 0) {
			empty |= Section.PEOPLE.bit();
		}
		if (counts.beds() == 0 && counts.food() == 0) {
			empty |= Section.HOMES.bit();
		}
		if (counts.builds() == 0) {
			empty |= Section.BUILDINGS.bit();
		}
		if (counts.guards() == 0) {
			empty |= Section.DEFENSE.bit();
		}
		if (counts.quests() == 0) {
			empty |= Section.QUESTS.bit();
		}
		if (!counts.neighbours()) {
			empty |= Section.TRADE.bit();
		}
		if (!counts.laws()) {
			empty |= Section.LAWS.bit();
		}
		menu.setState(EMPTY, empty);
	}

	/**
	 * The badge on {@code section}'s rail entry (Copperline, 47.2) from the menu's numbers: People the people waiting for a
	 * job, Defense "!" when an attack is coming, Quests the open quests; "" for none. A compact rail shows "9+" past nine.
	 */
	public static String railBadge(Section section, int waiting, int danger, int quests, boolean compact) {
		int n = switch (section) {
			case PEOPLE -> waiting;
			case QUESTS -> quests;
			default -> 0;
		};
		if (section == Section.DEFENSE) {
			return danger > 0 ? "!" : "";
		}
		if (n <= 0) {
			return "";
		}
		return compact && n > 9 ? "9+" : String.valueOf(n);
	}

	/**
	 * How far {@code score} is from {@code rank} toward the next rank, 0 to 100: the average of the villagers', buildings'
	 * and research's way from this rank's need to the next's, each capped at its full share (100 at the top rank).
	 */
	public static int rankProgress(VillageRanks.Score score, VillageRanks.Rank rank) {
		VillageRanks.Rank next = rank.next();
		if (next == null) {
			return 100;
		}
		double sum = share(score.villagers(), rank.villagers, next.villagers) + share(score.buildings(), rank.buildings, next.buildings)
			+ share(score.research(), rank.research, next.research);
		return (int) Math.max(0, Math.min(100, Math.floor(sum / 3 * 100)));
	}

	private static double share(int have, int from, int to) {
		if (to <= from) {
			return 1;
		}
		return Math.max(0, Math.min(1, (have - from) / (double) (to - from)));
	}

	/** The mark on a People-table button whose villager is on a job (47.2, "At work now"): its progress and its task's lore line. */
	public static final String WORK = "aliveworkplace:work";
	public static final String WORK_LINE = "aliveworkplace:work_line";
	/** {@link #markWork}'s progress for a villager on no job (no mark). */
	public static final int NOT_AT_WORK = Integer.MIN_VALUE;

	/** A status's progress (0 to 1, below 0 for none) as 0 to 100, or -1 for none. */
	public static int percent(float progress) {
		return progress < 0 || Float.isNaN(progress) ? -1 : Math.round(Math.min(1f, progress) * 100);
	}

	/** Marks a person button as at work: {@code progress} 0 to 100 (-1: busy, no bar), the task on lore line {@code line}. */
	public static void markWork(ItemStack icon, int line, int progress) {
		if (icon.isEmpty() || progress == NOT_AT_WORK) {
			return;
		}
		CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(WORK, progress);
		tag.putInt(WORK_LINE, line);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** One "At work now" line: who, what they're doing and how far (-1: no bar). */
	public record AtWork(Component who, Component task, int progress) {
	}

	/** The first {@code max} people on the main page who are at work, in the table's order (47.2). */
	public static List<AtWork> atWork(ChoiceMenu menu, int max) {
		return atWork(menu::icon, max);
	}

	/** The same from the main page's buttons by slot (the client's copy of the menu). */
	public static List<AtWork> atWork(java.util.function.IntFunction<ItemStack> icons, int max) {
		List<AtWork> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE && out.size() < max; slot++) {
			ItemStack icon = icons.apply(slot);
			CustomData data = icon.get(DataComponents.CUSTOM_DATA);
			if (data == null || !data.contains(WORK)) {
				continue;
			}
			CompoundTag tag = data.copyTag();
			net.minecraft.world.item.component.ItemLore lore = icon.get(DataComponents.LORE);
			int line = tag.getInt(WORK_LINE);
			Component task = lore != null && line >= 0 && line < lore.lines().size() ? lore.lines().get(line) : Component.empty();
			out.add(new AtWork(icon.getHoverName(), task, tag.getInt(WORK)));
		}
		return out;
	}

	/** The Overview's stat cards' buttons (47.2), in order: People, Mood, Food, Defense. */
	public static final int[] CARD_SLOTS = {VillageHallScreen.PEOPLE, VillageHallScreen.WELLBEING, VillageHallScreen.FOOD, VillageHallScreen.GUARDS};

	/** A stat card: its number, its status line and the line's tone (0 info, 1 good, 2 warning, 3 bad). */
	public record Card(String value, Component line, int tone) {
	}

	/**
	 * Stat card {@code i}'s contents from its main-page button (the number in the button's name, the first line that isn't
	 * gray as the status line, else the first line), so the card says what the button's tooltip says.
	 */
	public static Card card(int i, ItemStack icon) {
		Component name = icon.getHoverName();
		String value = name.getString();
		if (name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getArgs().length > 0) {
			Object arg = t.getArgs()[0];
			value = (arg instanceof Component c ? c.getString() : String.valueOf(arg)) + (i == 1 ? "%" : "");
		}
		net.minecraft.world.item.component.ItemLore lore = icon.get(DataComponents.LORE);
		java.util.List<Component> lines = lore == null ? java.util.List.of() : lore.lines();
		Component line = Component.empty();
		int tone = 0;
		for (Component l : lines) {
			int t = toneOf(l);
			if (t != 0) {
				line = l;
				tone = t;
				break;
			}
		}
		if (tone == 0 && !lines.isEmpty()) {
			line = lines.get(i == 0 && lines.size() > 1 ? 1 : 0); // People: who's without a job
		}
		return new Card(value, Component.literal(line.getString()), tone);
	}

	private static int toneOf(Component line) {
		net.minecraft.network.chat.TextColor c = line.getStyle().getColor();
		if (c == null && !line.getSiblings().isEmpty()) {
			c = line.getSiblings().get(0).getStyle().getColor();
		}
		if (c == null) {
			return 0;
		}
		int rgb = c.getValue();
		if (rgb == net.minecraft.ChatFormatting.GREEN.getColor()) {
			return 1;
		}
		if (rgb == net.minecraft.ChatFormatting.YELLOW.getColor() || rgb == net.minecraft.ChatFormatting.GOLD.getColor()) {
			return 2;
		}
		if (rgb == net.minecraft.ChatFormatting.RED.getColor()) {
			return 3;
		}
		return 0;
	}

	/** The section open on {@code menu}. */
	public static Section current(ChoiceMenu menu) {
		return Section.of(menu.state(SECTION));
	}

	/** Whether {@code section} is empty on {@code menu}'s main page (it says so at the top of its list). */
	public static boolean isEmpty(ChoiceMenu menu, Section section) {
		return (menu.state(EMPTY) & section.bit()) != 0;
	}

	/** The main page's buttons in {@code section}, in the order the screen lists them. */
	public static List<Integer> slots(ChoiceMenu menu, Section section) {
		List<Integer> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			if ((marks(menu.icon(slot)) & section.bit()) != 0) {
				out.add(slot);
			}
		}
		return out;
	}

	/** The date the sidebar shows: "Spring, day 1 · Year 1". */
	public static Component date(int season, int day, int year) {
		return Component.translatable("screen.aliveworkplace.hall.date", Seasons.Season.values()[Math.floorMod(season, 4)].title(), day, year);
	}

	/** The section {@code player} opened last (Overview at first). */
	public static Section last(ServerPlayer player) {
		return LAST.getOrDefault(player.getUUID(), Section.OVERVIEW);
	}

	/**
	 * Gets {@code menu} ready for the sidebar as the main page is laid out: its section is {@code player}'s last (the one
	 * a sidebar click just chose), today's date is set (the screen draws the sidebar once it has a year), and a sidebar
	 * click opens a section.
	 */
	static void install(ChoiceMenu menu, ServerPlayer player, ServerLevel level, BlockPos hall) {
		menu.setState(SECTION, last(player).ordinal());
		Seasons.Date today = Seasons.today(level);
		menu.setState(SEASON, today.season().ordinal());
		menu.setState(DAY, today.dayOfSeason());
		menu.setState(YEAR, (int) Math.max(1, Math.min(Short.MAX_VALUE, today.year())));
		menu.onMenuButton((p, id) -> {
			if (MovePage.handles(id)) {
				return MovePage.click(menu, level, p, id); // a click on the Move page's village map (B1163)
			}
			if (HallJobs.handles(id)) {
				return HallJobs.click(menu, level, hall, p, id); // Job ▾'s list (40.3)
			}
			if (HallBeds.handles(id)) {
				return HallBeds.click(menu, level, hall, p, id); // Bed ▾'s list (40.4)
			}
			if (id >= HallPeople.SCROLL) {
				HallPeople.scrollTo(menu, level, hall, id - HallPeople.SCROLL); // the People table scrolled (40.2)
				return true;
			}
			if (id < 0 || id >= Section.values().length) {
				return false;
			}
			choose(menu, level, hall, p, Section.of(id));
			return true;
		});
	}

	/** Opens {@code section} on {@code menu} (the main page, from any page) and remembers it for {@code player}. */
	public static void choose(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player, Section section) {
		LAST.put(player.getUUID(), section);
		menu.setState(SECTION, section.ordinal());
		VillageHallScreen.showList(menu, level, hall, 0);
	}

	/** Forgets what every player opened last (tests). */
	public static void forget() {
		LAST.clear();
	}

	private HallSections() {
	}
}
