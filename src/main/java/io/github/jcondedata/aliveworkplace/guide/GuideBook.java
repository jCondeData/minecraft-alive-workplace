package io.github.jcondedata.aliveworkplace.guide;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;

/**
 * The Guide Book's pages and chapters in reading order (ROADMAP 26.2a), and where a reader is in it (44.4). Each page is
 * an in-game screenshot ({@code textures/gui/guide/<id>.png}, made by tools/guide/build.py from the screenshot scenes)
 * with a title and a few short steps from the language file ({@code guide.aliveworkplace.page.<id>.title} and
 * {@code .text}). The Pokémon chapter only shows with Cobblemon installed, the City Plan's page once 1.1's Steward is on.
 * Pure data and arithmetic, so the GameTests open every page the way the book's screen does.
 */
public final class GuideBook {
	/** A page: its id (picture and words) and its chapter. */
	public record Entry(String id, String chapter) {
	}

	/** Every page, chapter by chapter. */
	public static final List<Entry> ALL = List.of(
		new Entry("welcome", "start"), new Entry("camp", "start"), new Entry("jobs", "start"), new Entry("status", "start"),
		new Entry("table", "builders"), new Entry("place", "builders"), new Entry("stock", "builders"), new Entry("build", "builders"),
		new Entry("styles", "builders"), new Entry("library", "builders"), new Entry("shapes", "builders"),
		new Entry("hall", "village"), new Entry("people", "village"), new Entry("requests", "village"), new Entry("storehouse", "village"),
		new Entry("villages", "village"), new Entry("city_plan", "village"),
		new Entry("miner", "gathering"), new Entry("lumberjack", "gathering"), new Entry("farmer", "gathering"),
		new Entry("orchard", "gathering"), new Entry("fisher", "gathering"), new Entry("beekeeper", "gathering"),
		new Entry("carpenter", "making"), new Entry("chef", "making"), new Entry("mail", "making"), new Entry("shop", "making"),
		new Entry("ferry", "making"),
		new Entry("guard", "safety"), new Entry("training", "safety"), new Entry("nurse", "safety"),
		new Entry("trainer", "pokemon"), new Entry("tutor", "pokemon"), new Entry("ball_smith", "pokemon"), new Entry("trader", "pokemon"),
		new Entry("fossil", "pokemon"));

	/** The chapters, in order. */
	public static final List<String> CHAPTERS = List.of("start", "builders", "village", "gathering", "making", "safety", "pokemon");

	/** The pages this game shows: the Pokémon chapter only with Cobblemon, the City Plan's page once its expansion (1.1) is on. */
	public static List<Entry> shown() {
		return shown(Platform.get().isModLoaded("cobblemon"), Expansions.on(Expansions.M27));
	}

	public static List<Entry> shown(boolean cobblemon, boolean cities) {
		return ALL.stream().filter(p -> cobblemon || !p.chapter().equals("pokemon"))
			.filter(p -> cities || !p.id().equals("city_plan")).toList();
	}

	/**
	 * Where a reader is: book page 0 is the contents, 1 and up the pages. Turning past either end stays there; a chapter
	 * opens at its first page.
	 */
	public static final class Reader {
		private final List<Entry> pages;
		private int page;

		public Reader(List<Entry> pages, int start) {
			this.pages = List.copyOf(pages);
			this.page = Math.max(0, Math.min(start, pages.size()));
		}

		public int page() {
			return page;
		}

		/** Pages counting the contents page. */
		public int pageCount() {
			return pages.size() + 1;
		}

		public List<Entry> pages() {
			return pages;
		}

		/** Goes to {@code to} (clamped); whether the page changed. */
		public boolean go(int to) {
			int clamped = Math.max(0, Math.min(to, pages.size()));
			if (clamped == page) {
				return false;
			}
			page = clamped;
			return true;
		}

		public boolean turn(int by) {
			return go(page + by);
		}

		/** The page shown on {@code bookPage} (1 and up). */
		public Entry at(int bookPage) {
			return pages.get(bookPage - 1);
		}

		/** The book page (1-based, after the contents) a chapter starts on, or 0 if it has none here. */
		public int firstPageOf(String chapter) {
			for (int i = 0; i < pages.size(); i++) {
				if (pages.get(i).chapter().equals(chapter)) {
					return i + 1;
				}
			}
			return 0;
		}

		/** How many pages a chapter has here. */
		public int pagesIn(String chapter) {
			return (int) pages.stream().filter(p -> p.chapter().equals(chapter)).count();
		}

		/** The chapters with a page here, in order. */
		public List<String> chapters() {
			return CHAPTERS.stream().filter(c -> firstPageOf(c) > 0).toList();
		}

		/** The open page's chapter, or null on the contents. */
		public String chapter() {
			return page == 0 ? null : pages.get(page - 1).chapter();
		}

		/** Opens a chapter at its first page; whether it has one here. */
		public boolean openChapter(String chapter) {
			int first = firstPageOf(chapter);
			if (first == 0) {
				return false;
			}
			go(first);
			return true;
		}
	}

	private GuideBook() {
	}
}
