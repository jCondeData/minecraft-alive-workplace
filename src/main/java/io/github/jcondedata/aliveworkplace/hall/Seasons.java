package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The village calendar (ROADMAP 22.6): four seasons of {@link #DAYS} days each (config {@code seasonDays}, default 16),
 * the same for the whole world, counted from the overworld's day ({@link Chronicle#day}). Each season has one festival,
 * on its middle day. Nothing in the mod changes with the seasons yet: expansions listen with {@link #onNewDay},
 * {@link #onNewSeason} and {@link #onFestival}, and the hall shows the date on its calendar page.
 *
 * <p>The last day the events fired for is saved with the overworld, so a reload doesn't fire a day twice, and days
 * skipped while the server was off (or by {@code /time set}) fire once for the day it is now.
 */
public final class Seasons {
	/** Days in a season (config {@code seasonDays}). */
	public static volatile int DAYS = 16;
	/** How often the clock is looked at, in ticks. */
	static final int CHECK_EVERY = 20;

	public enum Season {
		SPRING(Items.PINK_PETALS), SUMMER(Items.SUNFLOWER), AUTUMN(Items.PUMPKIN), WINTER(Items.SNOWBALL);

		public final Item icon;

		Season(Item icon) {
			this.icon = icon;
		}

		public Component title() {
			return Component.translatable("season.aliveworkplace." + name().toLowerCase());
		}

		/** The name of this season's festival ("the Harvest Fair"). */
		public Component festival() {
			return Component.translatable("season.aliveworkplace." + name().toLowerCase() + ".festival");
		}

		public Season next() {
			return values()[(ordinal() + 1) % 4];
		}
	}

	/**
	 * A day of the calendar: {@code day} as {@link Chronicle#day} counts (1 for the world's first), its season, the day of
	 * the season (1 to {@code length}), the season's length and the year (1 for the first).
	 */
	public record Date(long day, Season season, int dayOfSeason, int length, long year) {
		/** The day of the season its festival falls on. */
		public int festivalDay() {
			return festivalDayOf(length);
		}

		public boolean isFestival() {
			return dayOfSeason == festivalDay();
		}

		/** Days until this season's festival (0 today), or -1 once it is past. */
		public int daysToFestival() {
			return dayOfSeason <= festivalDay() ? festivalDay() - dayOfSeason : -1;
		}

		/** Days until the next season begins (1 on its last day). */
		public int daysLeft() {
			return length - dayOfSeason + 1;
		}
	}

	private static final List<BiConsumer<ServerLevel, Date>> NEW_DAY = new CopyOnWriteArrayList<>();
	private static final List<BiConsumer<ServerLevel, Date>> NEW_SEASON = new CopyOnWriteArrayList<>();
	private static final List<BiConsumer<ServerLevel, Date>> FESTIVAL = new CopyOnWriteArrayList<>();

	/** Called with the overworld once at the start of every day (and once when a world first starts). */
	public static void onNewDay(BiConsumer<ServerLevel, Date> listener) {
		NEW_DAY.add(listener);
	}

	/** Called on the first day the calendar sees of every season (not when a world first starts mid-season). */
	public static void onNewSeason(BiConsumer<ServerLevel, Date> listener) {
		NEW_SEASON.add(listener);
	}

	/** Called at the start of each season's festival day. */
	public static void onFestival(BiConsumer<ServerLevel, Date> listener) {
		FESTIVAL.add(listener);
	}

	/** The hall's calendar page (the first page in the hall's page row). */
	public static final String PAGE = "calendar";
	/** Where the four seasons stand on the calendar page. */
	public static final int[] SEASON_SLOTS = {VillageHallScreen.FIRST_ROW + 1, VillageHallScreen.FIRST_ROW + 3, VillageHallScreen.FIRST_ROW + 5,
		VillageHallScreen.FIRST_ROW + 7};

	public static void init() {
		Platform.get().onServerTick(server -> {
			ServerLevel overworld = server.overworld();
			if (overworld.getGameTime() % CHECK_EVERY == 0) {
				tick(overworld);
			}
		});
		HallPages.register(PAGE, (level, hall) -> tab(today(level)), (level, hall) -> header(level, hall),
			(menu, level, hall, viewer) -> fill(menu, today(level)));
	}

	/** The page row's tab: today's date, the festival and the next season. */
	static ItemStack tab(Date today) {
		return VillageHallScreen.icon(Items.CLOCK, Component.translatable("screen.aliveworkplace.calendar.today", today.season().title(),
				today.dayOfSeason(), today.length()), ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.calendar.year", today.year()), ChatFormatting.GRAY),
			festivalLine(today),
			VillageHallScreen.line(today.daysLeft() == 1
				? Component.translatable("screen.aliveworkplace.calendar.next_tomorrow", today.season().next().title())
				: Component.translatable("screen.aliveworkplace.calendar.next_in", today.season().next().title(), today.daysLeft()), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.calendar.hint", ChatFormatting.DARK_GRAY));
	}

	private static Component festivalLine(Date today) {
		int to = today.daysToFestival();
		return VillageHallScreen.line(to == 0 ? Component.translatable("screen.aliveworkplace.calendar.festival_today", today.season().festival())
			: to == 1 ? Component.translatable("screen.aliveworkplace.calendar.festival_tomorrow", today.season().festival())
			: to > 0 ? Component.translatable("screen.aliveworkplace.calendar.festival_in", today.season().festival(), to)
			: Component.translatable("screen.aliveworkplace.calendar.festival_past", today.season().festival(), today.festivalDay()),
			to == 0 ? ChatFormatting.LIGHT_PURPLE : to > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.CLOCK, Component.translatable("screen.aliveworkplace.calendar.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.calendar.about", DAYS), ChatFormatting.GRAY));
	}

	/** The calendar page: the four seasons, today's highlighted, each with its days and festival. */
	static void fill(ChoiceMenu menu, Date today) {
		for (Season season : Season.values()) {
			boolean now = season == today.season();
			int first = season.ordinal() * today.length() + 1;
			int festival = first + today.festivalDay() - 1;
			Component when;
			if (now) {
				when = Component.translatable("screen.aliveworkplace.calendar.now", today.dayOfSeason(), today.length());
			} else {
				int ahead = Math.floorMod(season.ordinal() - today.season().ordinal(), 4);
				long days = (long) (ahead - 1) * today.length() + today.daysLeft();
				when = days == 1 ? Component.translatable("screen.aliveworkplace.calendar.starts_tomorrow")
					: Component.translatable("screen.aliveworkplace.calendar.starts_in", days);
			}
			ItemStack icon = VillageHallScreen.icon(season.icon, season.title().copy(), now ? ChatFormatting.GREEN : ChatFormatting.WHITE,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.calendar.days", first, first + today.length() - 1), ChatFormatting.GRAY),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.calendar.festival", season.festival(), festival), ChatFormatting.LIGHT_PURPLE),
				VillageHallScreen.line(when, now ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			if (now) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(SEASON_SLOTS[season.ordinal()], icon, null);
		}
	}

	/** The festival day of a season {@code length} days long: its middle day. */
	public static int festivalDayOf(int length) {
		return length / 2 + 1;
	}

	/** The date of {@code day} (as {@link Chronicle#day} counts) with seasons {@code length} days long. */
	public static Date date(long day, int length) {
		int days = Math.max(1, length);
		long index = Math.max(0, day - 1);
		long seasonsGone = index / days;
		return new Date(day, Season.values()[(int) (seasonsGone % 4)], (int) (index % days) + 1, days, seasonsGone / 4 + 1);
	}

	/** Today's date in the world {@code level} is in (the overworld's clock). */
	public static Date today(ServerLevel level) {
		MinecraftServer server = level.getServer();
		return date(Chronicle.day(server == null ? level : server.overworld()), DAYS);
	}

	/** Which events a day fired (all false when the calendar had already seen it). */
	public record Fired(boolean newDay, boolean newSeason, boolean festival) {
		static final Fired NONE = new Fired(false, false, false);
	}

	/** Looks at the overworld's clock and fires the events for a day the calendar hasn't seen yet. */
	public static void tick(ServerLevel overworld) {
		fire(overworld, Data.get(overworld), Chronicle.day(overworld), DAYS);
	}

	/**
	 * Moves {@code data} on to {@code day} (seasons {@code length} days long) and calls the listeners: a new day each time
	 * the day changes, a new season when the season (or the year) differs from the last day seen or it's a season's first
	 * day, and the festival on its day. The very first day a world's calendar sees fires only the new day.
	 */
	public static Fired fire(ServerLevel level, Data data, long day, int length) {
		if (data.lastDay == day) {
			return Fired.NONE;
		}
		Date today = date(day, length);
		boolean first = data.lastDay == 0;
		Date was = first ? null : date(data.lastDay, length);
		data.lastDay = day;
		data.setDirty();
		boolean season = !first && (was.season() != today.season() || was.year() != today.year() || today.dayOfSeason() == 1);
		boolean festival = today.isFestival();
		for (BiConsumer<ServerLevel, Date> listener : NEW_DAY) {
			listener.accept(level, today);
		}
		if (season) {
			for (BiConsumer<ServerLevel, Date> listener : NEW_SEASON) {
				listener.accept(level, today);
			}
		}
		if (festival) {
			for (BiConsumer<ServerLevel, Date> listener : FESTIVAL) {
				listener.accept(level, today);
			}
		}
		return new Fired(true, season, festival);
	}

	/** The last day the calendar fired its events for, saved with the overworld (0: never). */
	public static long lastDay(ServerLevel overworld) {
		return Data.get(overworld).lastDay;
	}

	/** The calendar's saved state: the last day it fired its events for (0: never). */
	public static final class Data extends SavedData {
		static final String NAME = "aliveworkplace_calendar";
		long lastDay;

		public Data() {
		}

		public Data(long lastDay) {
			this.lastDay = lastDay;
		}

		public long lastDay() {
			return lastDay;
		}

		static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			data.lastDay = io.github.jcondedata.aliveworkplace.mc.Nbt.getLong(tag, "last_day");
			return data;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putLong("last_day", lastDay);
			return tag;
		}
	}

	private Seasons() {
	}
}
