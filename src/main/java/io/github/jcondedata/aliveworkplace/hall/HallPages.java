package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Village Hall's page row (ROADMAP 22.5): the third row of the hall's screen holds {@link #ROW} tabs, one for each page
 * added here. Each expansion that gives the hall a page (the Festival Cup, harvest season, ...) registers it once
 * at start-up with {@link #register}; the hall draws the tab, opens the page with a back button and the page's header
 * above a divider, and the page fills the rows below ({@link VillageHallScreen#FIRST_ROW} onwards). The pages that
 * were there before (quests, chronicle, trade routes, advice) keep their buttons in the first two rows.
 *
 * <p>Every page from {@link #register} is sure of a tab: the row takes {@link #ROW} of them, which leaves six for other
 * features and add-ons besides the mod's first three (the calendar, Legends, the Cup). The mod's later pages (the
 * Classes page, 34.6) come in through {@link #registerSpare}: their tab sits in the row while it has room and gives way,
 * newest first, to pages from {@link #register} when it hasn't, so the six tabs kept for others are never taken.
 */
public final class HallPages {
	/** Tabs in the page row, and the pages {@link #register} takes at most. */
	public static final int ROW = 9;
	/** Pages {@link #registerSpare} takes at most. */
	public static final int SPARES = 1;
	/** Pages the hall takes at most: a tab each for {@link #ROW}, and the spare pages that give way when the row is full. */
	public static final int MAX = ROW + SPARES;

	/** What a page's tab and header look like in the village round {@code hall} (an icon with a name and lore). */
	public interface Icon {
		ItemStack icon(ServerLevel level, BlockPos hall);
	}

	/** Fills the page's rows (slots {@link VillageHallScreen#FIRST_ROW} to {@link ChoiceMenu#SIZE}). */
	public interface Content {
		void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer);
	}

	/**
	 * A page: {@code id} names it (for tests and logs), {@code tab} is its button in the page row, {@code header} its
	 * title at the top of the page, {@code content} the rest.
	 */
	public record Page(String id, Icon tab, Icon header, Content content) {
	}

	/** A registered page, and whether its tab gives way when the row is full. */
	private record Entry(Page page, boolean spare) {
	}

	private static final List<Entry> PAGES = new CopyOnWriteArrayList<>();

	/** Adds a page to every Village Hall, after the ones already there, with a tab of its own. Call it once, at start-up. */
	public static synchronized Page register(String id, Icon tab, Icon header, Content content) {
		return add(id, tab, header, content, false);
	}

	/**
	 * Adds one of the mod's own pages whose tab gives way to the pages from {@link #register} when the row is full (then
	 * it has no tab: {@link #slot} is -1). Call it once, at start-up.
	 */
	public static synchronized Page registerSpare(String id, Icon tab, Icon header, Content content) {
		return add(id, tab, header, content, true);
	}

	private static Page add(String id, Icon tab, Icon header, Content content, boolean spare) {
		int sure = 0;
		int spares = 0;
		for (Entry entry : PAGES) {
			if (entry.page().id().equals(id)) {
				throw new IllegalStateException("A hall page called " + id + " is already registered");
			}
			if (entry.spare()) {
				spares++;
			} else {
				sure++;
			}
		}
		if (spare ? spares >= SPARES : sure >= ROW) {
			throw new IllegalStateException("The hall's page row is full (" + ROW + " pages): can't add " + id);
		}
		Page page = new Page(id, tab, header, content);
		PAGES.add(new Entry(page, spare));
		return page;
	}

	/** Takes a page out again (tests that add pages of their own). */
	public static synchronized void unregister(String id) {
		PAGES.removeIf(entry -> entry.page().id().equals(id));
	}

	/** Every page, in tab order, with a tab or not. */
	public static List<Page> all() {
		List<Page> out = new ArrayList<>();
		for (Entry entry : PAGES) {
			out.add(entry.page());
		}
		return out;
	}

	/** The pages with a tab in the row, in tab order: every page, less the newest spare pages while there are more than {@link #ROW}. */
	public static List<Page> shown() {
		List<Entry> entries = new ArrayList<>(PAGES);
		for (int i = entries.size() - 1; i >= 0 && entries.size() > ROW; i--) {
			if (entries.get(i).spare()) {
				entries.remove(i);
			}
		}
		List<Page> out = new ArrayList<>();
		for (Entry entry : entries) {
			out.add(entry.page());
		}
		return out;
	}

	/** The slot of {@code page}'s tab, or -1 if it isn't registered or has given its tab up. */
	public static int slot(String id) {
		List<Page> shown = shown();
		for (int i = 0; i < shown.size(); i++) {
			if (shown.get(i).id().equals(id)) {
				return VillageHallScreen.PAGE_ROW + i;
			}
		}
		return -1;
	}

	private HallPages() {
	}
}
