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
 * The Village Hall's page row (ROADMAP 22.5): the third row of the hall's screen holds a tab for every page added here,
 * up to {@link #MAX}. Each expansion that gives the hall a page (the Festival Cup, harvest season, ...) registers it once
 * at start-up with {@link #register}; the hall draws the tab, opens the page with a back button and the page's header
 * above a divider, and the page fills the rows below ({@link VillageHallScreen#FIRST_ROW} onwards). The pages that
 * were there before (quests, chronicle, trade routes, advice) keep their buttons in the first two rows.
 */
public final class HallPages {
	/** Tabs in the page row. */
	public static final int MAX = 9;

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

	private static final List<Page> PAGES = new CopyOnWriteArrayList<>();

	/** Adds a page to every Village Hall, after the ones already there. Call it once, at start-up. */
	public static synchronized Page register(String id, Icon tab, Icon header, Content content) {
		for (Page page : PAGES) {
			if (page.id().equals(id)) {
				throw new IllegalStateException("A hall page called " + id + " is already registered");
			}
		}
		if (PAGES.size() >= MAX) {
			throw new IllegalStateException("The hall's page row is full (" + MAX + " pages): can't add " + id);
		}
		Page page = new Page(id, tab, header, content);
		PAGES.add(page);
		return page;
	}

	/** Takes a page out again (tests that add pages of their own). */
	public static synchronized void unregister(String id) {
		PAGES.removeIf(page -> page.id().equals(id));
	}

	/** Every page, in tab order. */
	public static List<Page> all() {
		return new ArrayList<>(PAGES);
	}

	/** The slot of {@code page}'s tab, or -1 if it isn't registered. */
	public static int slot(String id) {
		for (int i = 0; i < PAGES.size(); i++) {
			if (PAGES.get(i).id().equals(id)) {
				return VillageHallScreen.PAGE_ROW + i;
			}
		}
		return -1;
	}

	private HallPages() {
	}
}
