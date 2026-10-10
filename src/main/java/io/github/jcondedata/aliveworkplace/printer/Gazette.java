package io.github.jcondedata.aliveworkplace.printer;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;

/**
 * What the Printer's papers say (ROADMAP 34.11). Both are our own items carrying vanilla's written-book content, filled
 * in from the village's hall the day they're printed:
 * <ul>
 * <li>the <b>Village Gazette</b>: the front page (the newest chronicle entries), the open quests with their rewards, the
 * next festival and market day, and the week's births, weddings and households that rose;</li>
 * <li>the <b>Illuminated Book</b>: the village's whole chronicle.</li>
 * </ul>
 * A Gazette also notes the hall it was printed at and the day (in its custom data), which is how the hall quest
 * <i>Spread the news</i> knows this week's paper from old news, and whose news it is.
 */
public final class Gazette {
	/** Custom-data keys: the hall the paper was printed at (a packed position) and the day ({@link Chronicle#day}). */
	static final String HALL = "gazette_hall";
	static final String DAY = "gazette_day";
	static final String VILLAGE = "gazette_village";
	/** A Gazette is "this week's" for this many days, the day it was printed included. */
	public static final int WEEK = 7;
	/** Chronicle entries on the front page. */
	public static final int FRONT_PAGE = 3;
	/** Chronicle entries to a page of the Illuminated Book and of the Gazette's week. */
	public static final int PER_PAGE = 3;

	/** Today, as the chronicle counts days. */
	public static long today(ServerLevel level) {
		return Chronicle.day(level);
	}

	/** Whether {@code stack} has been printed (blank ones come from the creative tab or a trade, and are written when first held to read). */
	public static boolean written(ItemStack stack) {
		return stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
	}

	/** The day {@code stack} was printed, or -1. */
	public static long day(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(DAY) ? tag.getLong(DAY) : -1;
	}

	/** The hall {@code stack} was printed at. */
	public static Optional<BlockPos> home(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(HALL) ? Optional.of(BlockPos.of(tag.getLong(HALL))) : Optional.empty();
	}

	/** The name of the village {@code stack} was printed in ("" for a blank one). */
	public static String village(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(VILLAGE);
	}

	/** Whether {@code stack} was printed within the last {@link #WEEK} days. */
	public static boolean thisWeek(ItemStack stack, long today) {
		long day = day(stack);
		return day >= 0 && today - day < WEEK && today >= day;
	}

	/**
	 * Prints {@code stack} (a Gazette or an Illuminated Book) from the hall of the village {@code near} is in, as of today.
	 * Without a hall the paper says so, and is still a paper.
	 */
	public static void write(ServerLevel level, BlockPos near, ItemStack stack) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, near);
		long today = today(level);
		boolean illuminated = stack.is(ModItems.ILLUMINATED_BOOK);
		List<Component> pages;
		Component village;
		if (hall.isPresent() && level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity) {
			village = VillageHalls.name(level, hall.get());
			pages = illuminated ? chroniclePages(village, entity.chronicle()) : gazettePages(level, hall.get(), entity);
		} else {
			village = Component.translatable("book.aliveworkplace.gazette.nowhere");
			pages = List.of(Component.translatable(illuminated ? "book.aliveworkplace.illuminated.no_hall" : "book.aliveworkplace.gazette.no_hall", today));
		}
		String title = Component.translatable(illuminated ? "book.aliveworkplace.illuminated.title" : "book.aliveworkplace.gazette.title", village).getString();
		if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
			title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		}
		String author = Component.translatable("book.aliveworkplace.gazette.author", village).getString();
		List<Filterable<Component>> filtered = new ArrayList<>();
		for (Component page : pages) {
			filtered.add(Filterable.passThrough(page));
		}
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, filtered, true));
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putLong(DAY, today);
		tag.putString(VILLAGE, village.getString());
		hall.ifPresent(h -> tag.putLong(HALL, h.asLong()));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/**
	 * Today's Gazette for the village round {@code hall}: the front page, the quests, the calendar, and the week's
	 * births, weddings and risen households (a page per {@link #PER_PAGE} of them; one page saying it was a quiet week
	 * when there were none).
	 */
	public static List<Component> gazettePages(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long today = today(level);
		Component village = VillageHalls.name(level, hall);
		List<Chronicle.Entry> chronicle = entity.chronicle();
		List<Component> pages = new ArrayList<>();
		// The front page: the newest entries first.
		MutableComponent front = Component.translatable("book.aliveworkplace.gazette.front", village, today);
		int shown = 0;
		for (int i = chronicle.size() - 1; i >= 0 && shown < FRONT_PAGE; i--, shown++) {
			front.append("\n\n").append(entry(chronicle.get(i)));
		}
		if (shown == 0) {
			front.append("\n\n").append(Component.translatable("book.aliveworkplace.gazette.no_news"));
		}
		pages.add(front);
		// The open quests, each with what it pays.
		MutableComponent quests = Component.translatable("book.aliveworkplace.gazette.quests");
		List<Quest> open = Stories.open(level, hall).stream().filter(q -> q.giver.equals("hall") && !q.done()).toList();
		for (Quest q : open) {
			int emeralds = q.emeralds();
			quests.append("\n\n").append(emeralds > 0
				? Component.translatable("book.aliveworkplace.gazette.quest", q.title(), Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds))
				: Component.translatable("book.aliveworkplace.gazette.quest_plain", q.title()));
		}
		if (open.isEmpty()) {
			quests.append("\n\n").append(Component.translatable("book.aliveworkplace.gazette.no_quests"));
		}
		pages.add(quests);
		// The calendar.
		long festival = Festivals.nextDay(level, hall, entity);
		long market = MarketDays.nextDay(level, hall, entity.lastMarketDay());
		pages.add(Component.translatable("book.aliveworkplace.gazette.calendar", when(festival, today), when(market, today)));
		// The week: births, weddings and the households that rose.
		List<Component> week = new ArrayList<>();
		for (Chronicle.Entry e : chronicle) {
			if (today - e.day() < WEEK && e.day() <= today && ofTheWeek(e)) {
				week.add(entry(e));
			}
		}
		if (week.isEmpty()) {
			pages.add(Component.translatable("book.aliveworkplace.gazette.week").append("\n\n").append(Component.translatable("book.aliveworkplace.gazette.quiet_week")));
		}
		for (int i = 0; i < week.size(); i += PER_PAGE) {
			MutableComponent page = Component.translatable("book.aliveworkplace.gazette.week");
			for (int j = i; j < Math.min(week.size(), i + PER_PAGE); j++) {
				page.append("\n\n").append(week.get(j));
			}
			pages.add(page);
		}
		return pages;
	}

	/** Whether a chronicle entry belongs on the week's page: a birth, a wedding, or a household that rose (not one that fell). */
	static boolean ofTheWeek(Chronicle.Entry e) {
		if (e.kind() == Chronicle.Kind.BIRTH || e.kind() == Chronicle.Kind.WEDDING) {
			return true;
		}
		return e.kind() == Chronicle.Kind.CLASS && e.text().getContents() instanceof TranslatableContents t
			&& t.getKey().equals("chronicle.aliveworkplace.class.rose");
	}

	/** The Illuminated Book: a cover, then the whole chronicle from the first day, {@link #PER_PAGE} entries to a page. */
	public static List<Component> chroniclePages(Component village, List<Chronicle.Entry> chronicle) {
		List<Component> pages = new ArrayList<>();
		pages.add(Component.translatable("book.aliveworkplace.illuminated.cover", village).withStyle(ChatFormatting.GOLD));
		if (chronicle.isEmpty()) {
			pages.add(Component.translatable("book.aliveworkplace.illuminated.empty"));
		}
		for (int i = 0; i < chronicle.size(); i += PER_PAGE) {
			MutableComponent page = Component.empty();
			for (int j = i; j < Math.min(chronicle.size(), i + PER_PAGE); j++) {
				if (j > i) {
					page.append("\n\n");
				}
				page.append(entry(chronicle.get(j)));
			}
			pages.add(page);
		}
		return pages;
	}

	private static Component entry(Chronicle.Entry e) {
		return Component.translatable("book.aliveworkplace.gazette.entry", e.day(), e.text());
	}

	/** "today", "tomorrow" or "in 3 days (day 12)". */
	private static Component when(long day, long today) {
		long in = day - today;
		if (in <= 0) {
			return Component.translatable("book.aliveworkplace.gazette.today");
		}
		if (in == 1) {
			return Component.translatable("book.aliveworkplace.gazette.tomorrow");
		}
		return Component.translatable("book.aliveworkplace.gazette.in_days", in, day);
	}

	/**
	 * {@code player} brings the Gazette {@code stack} to the hall at {@code hall}: when the village it was printed in has a
	 * <i>Spread the news</i> quest asking for it here and it's this week's, the copy goes into this village's store (or
	 * is left at the hall), the quest counts it and pays. Returns whether the Gazette was wanted here (old news is told
	 * so and kept); false leaves the click to the hall.
	 */
	public static boolean deliver(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos hall) {
		Optional<BlockPos> home = home(stack);
		if (home.isEmpty() || home.get().equals(hall) || !Stories.wantsGazette(level, home.get(), hall)) {
			return false;
		}
		if (!thisWeek(stack, today(level))) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.gazette.old_news", day(stack)).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		ItemStack copy = stack.split(1);
		if (!Stories.onGazette(level, home.get(), hall, player)) {
			stack.grow(1); // the quest came down between the look and the hand-over
			return false;
		}
		List<BlockPos> store = VillageNeeds.store(level, hall);
		ItemStack rest = store.isEmpty() ? copy : SupplyContainers.insert(level, store, copy);
		if (!rest.isEmpty()) {
			Block.popResource(level, hall.above(), rest);
		}
		level.playSound(null, hall, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
		Chronicle.atHall(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.gazette",
			player.getDisplayName(), VillageHalls.name(level, home.get())));
		return true;
	}

	private Gazette() {
	}
}
