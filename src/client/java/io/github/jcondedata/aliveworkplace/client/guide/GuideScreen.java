package io.github.jcondedata.aliveworkplace.client.guide;

import io.github.jcondedata.aliveworkplace.client.CopperStyle;
import io.github.jcondedata.aliveworkplace.guide.GuideBook;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.GuideLayout;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The Guide Book's screen (ROADMAP 26.2a) in Copperline (44.4, nearest board Chronicle.png): the plaque names the book,
 * the lintel the chapter and the page number; the rail holds Contents and the chapters with their page counts (an icon
 * grid at 320x180); the stage shows one page at a time, its title, its in-game screenshot and a few short steps under it,
 * with Back and Next (the copper button). The contents page has the book's intro and the chapters with the page each
 * starts on; click one to go there. Arrow keys, Page Up and Down, A and D and the mouse wheel turn pages, Home and
 * Backspace go to the contents. The book opens where the player left it. Boxes from {@link GuideLayout}, the reading
 * position in {@link GuideBook.Reader}.
 */
public class GuideScreen extends Screen {
	/** The most lines a page's words may take (the scene checks every page). */
	public static final int TEXT_LINES = 6;
	private static final String KEY = "screen.aliveworkplace.guide.";

	/** Where the book was left open (this game session). */
	private static int lastPage;

	private final List<GuidePages.Page> pages = GuidePages.shown();
	private final GuideBook.Reader reader;
	private GuideLayout.Book boxes;
	private Component whisper = Component.empty();

	public GuideScreen() {
		super(Component.translatable("item.aliveworkplace.guide_book"));
		reader = new GuideBook.Reader(GuideBook.shown(), lastPage);
	}

	/** Opens the book at {@code page} (0 = the contents); for the screenshot scenes. */
	public GuideScreen(int page) {
		this();
		reader.go(page);
	}

	/** Pages counting the contents page. */
	public int pageCount() {
		return reader.pageCount();
	}

	public int page() {
		return reader.page();
	}

	/** A chapter's short name, for the rail. */
	public static Component shortName(String chapter) {
		return Component.translatable(KEY + "chapter." + chapter);
	}

	/** The rail's icon for an entry: one of Copperline's 16 px icons, or an item (null chapter: the contents). */
	private static String railIcon(String chapter) {
		if (chapter == null) {
			return null;
		}
		return switch (chapter) {
			case "start" -> "overview";
			case "builders" -> "buildings";
			case "village" -> "homes";
			case "safety" -> "defense";
			default -> null;
		};
	}

	private static ItemStack railItem(String chapter) {
		if (chapter == null) {
			return new ItemStack(ModItems.GUIDE_BOOK);
		}
		return switch (chapter) {
			case "gathering" -> new ItemStack(Items.WHEAT);
			case "making" -> new ItemStack(Items.CRAFTING_TABLE);
			case "pokemon" -> new ItemStack(Items.EGG);
			default -> ItemStack.EMPTY;
		};
	}

	private Component railLabel(int i) {
		return i == 0 ? Component.translatable("guide.aliveworkplace.contents") : shortName(reader.chapters().get(i - 1));
	}

	@Override
	protected void init() {
		int railW = 0;
		for (int i = 0; i <= reader.chapters().size(); i++) {
			railW = Math.max(railW, font.width(railLabel(i)));
		}
		railW += font.width("00") - 6; // the page count's two digits need a few pixels more than the shell keeps
		boxes = GuideLayout.book(width, height, font.width(title), railW, reader.chapters().size() + 1,
			font.width(backLabel()) + 12, Math.max(44, font.width(nextLabel()) + 16));
	}

	private Component backLabel() {
		return Component.literal("< ").append(Component.translatable(KEY + "back"));
	}

	private Component nextLabel() {
		return Component.translatable(KEY + "next").append(" >");
	}

	/** The boxes as laid out for this screen size (for the screenshot scene's checks). */
	public GuideLayout.Book boxes() {
		return boxes;
	}

	private void go(int to) {
		if (reader.go(to)) {
			lastPage = reader.page();
		}
	}

	private void turn(int by) {
		go(reader.page() + by);
	}

	private List<FormattedCharSequence> introLines() {
		return font.split(Component.translatable("guide.aliveworkplace.intro"), GuideLayout.textWidth(boxes));
	}

	private GuideLayout.Contents contents() {
		return GuideLayout.contents(boxes, introLines().size(), reader.chapters().size());
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch (key) {
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_D -> {
				turn(1);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_A -> {
				turn(-1);
				return true;
			}
			case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_BACKSPACE -> {
				go(0);
				return true;
			}
			default -> {
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			turn(scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (CopperLayout.inside(boxes.close(), mouseX, mouseY)) {
				click();
				onClose();
				return true;
			}
			int before = reader.page();
			if (GuideLayout.click(boxes, reader.page() == 0 ? contents() : null, reader, mouseX, mouseY)) {
				lastPage = reader.page();
				if (before != reader.page()) {
					click();
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	// --- drawing -------------------------------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int[] win = boxes.win();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		whisper = Component.translatable(KEY + "whisper.page");
		super.render(g, mouseX, mouseY, partialTick);
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, boxes.win());
		CopperStyle.lintel(g, font, boxes.win(), plaque, lintelText(), overClose);
		renderRail(g, mouseX, mouseY);
		if (reader.page() == 0) {
			renderContents(g, mouseX, mouseY);
		} else {
			renderPage(g, pages.get(reader.page() - 1));
		}
		renderArrows(g, mouseX, mouseY);
		if (overClose) {
			whisper = Component.translatable(KEY + "whisper.close");
		}
		int[] w = boxes.whisper();
		CopperStyle.whisper(g, font, whisper, w[0] + 2, w[1] + 3, w[2] - 4, boxes.compact());
	}

	private Component lintelText() {
		if (reader.page() == 0) {
			return Component.translatable(KEY + "lintel.contents", pages.size(), reader.chapters().size());
		}
		return Component.translatable(KEY + "lintel.page", GuidePages.chapterName(reader.chapter()), reader.page(), pages.size());
	}

	private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] rail = boxes.rail();
		g.fill(rail[0], rail[1], rail[0] + rail[2], rail[1] + rail[3], CopperStyle.DEEP_800);
		if (!boxes.compact()) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("guide.aliveworkplace.chapters").getString().toUpperCase(Locale.ROOT),
				rail[2] - 8), rail[0] + 4, rail[1] + 3, CopperStyle.TUFF, false);
		}
		int[][] entries = boxes.railEntries();
		List<String> chapters = reader.chapters();
		for (int i = 0; i < entries.length && i <= chapters.size(); i++) {
			String chapter = i == 0 ? null : chapters.get(i - 1);
			boolean current = chapter == null ? reader.page() == 0 : chapter.equals(reader.chapter());
			boolean over = CopperLayout.inside(entries[i], mouseX, mouseY);
			CopperStyle.railEntry(g, font, entries[i], railIcon(chapter), railItem(chapter), railLabel(i), chapter == null || boxes.compact() ? 0 : reader.pagesIn(chapter),
				current, over, boxes.compact());
			if (i == 0 && !boxes.compact()) { // a rule between the contents and the chapters
				int[] e = entries[i];
				g.fill(e[0] + 2, e[1] + e[3], e[0] + e[2] - 2, e[1] + e[3] + 1, CopperStyle.DEEP_600);
			}
			if (over) {
				whisper = chapter == null ? Component.translatable(KEY + "whisper.contents")
					: Component.translatable(KEY + "whisper.chapter", GuidePages.chapterName(chapter), reader.pagesIn(chapter));
			}
		}
	}

	/** The page's title in Quartz with a shadow, cut to its line. */
	private void title(GuiGraphics g, Component text) {
		int[] t = boxes.title();
		CopperStyle.label(g, font, text.getString(), t[0] + 2, t[1] + 2, t[2] - 2, CopperStyle.QUARTZ);
	}

	private void renderContents(GuiGraphics g, int mouseX, int mouseY) {
		title(g, Component.translatable("guide.aliveworkplace.contents"));
		GuideLayout.Contents c = contents();
		int y = c.intro()[1];
		for (FormattedCharSequence line : introLines()) {
			g.drawString(font, line, c.intro()[0], y, CopperStyle.TUFF, false);
			y += GuideLayout.LINE;
		}
		int[] h = c.head();
		heading(g, Component.translatable("guide.aliveworkplace.chapters"), h[0] + 1, h[1], h[2] - 2);
		List<String> chapters = reader.chapters();
		for (int i = 0; i < c.rows().length; i++) {
			int[] r = c.rows()[i];
			String chapter = chapters.get(i);
			boolean over = CopperLayout.inside(r, mouseX, mouseY);
			CopperStyle.rowBand(g, r[0], r[1], r[2], r[3], i % 2 == 1 && c.columns() == 1, over, false);
			String number = String.valueOf(reader.firstPageOf(chapter));
			int nx = r[0] + r[2] - 3 - font.width(number);
			Component name = c.columns() == 1 ? GuidePages.chapterName(chapter) : shortName(chapter);
			g.drawString(font, CopperStyle.trim(font, (i + 1) + ". " + name.getString(), nx - 4 - (r[0] + 5)), r[0] + 5, r[1] + 2,
				over ? CopperStyle.LANTERN : CopperStyle.QUARTZ, false);
			g.drawString(font, number, nx, r[1] + 2, CopperStyle.TUFF, false);
			if (over) {
				whisper = Component.translatable(KEY + "whisper.chapter", GuidePages.chapterName(chapter), reader.pagesIn(chapter));
			}
		}
	}

	private void renderPage(GuiGraphics g, GuidePages.Page p) {
		title(g, p.title());
		List<FormattedCharSequence> lines = font.split(p.text(), GuideLayout.textWidth(boxes));
		int shown = Math.min(lines.size(), TEXT_LINES);
		GuideLayout.Page layout = GuideLayout.page(boxes, shown);
		int[] pic = layout.picture();
		g.fill(pic[0] - 1, pic[1] - 1, pic[0] + pic[2] + 1, pic[1] + pic[3] + 1, CopperStyle.VOID);
		g.blit(p.image(), pic[0], pic[1], pic[2], pic[3], 0, 0, GuidePages.TEXTURE_W, GuidePages.TEXTURE_H, GuidePages.TEXTURE_W, GuidePages.TEXTURE_H);
		int y = layout.text()[1];
		for (int i = 0; i < shown; i++) {
			g.drawString(font, lines.get(i), layout.text()[0], y, CopperStyle.QUARTZ, false);
			y += GuideLayout.LINE;
		}
	}

	private void renderArrows(GuiGraphics g, int mouseX, int mouseY) {
		boolean hasBack = reader.page() > 0;
		boolean hasNext = reader.page() < reader.pageCount() - 1;
		int[] b = boxes.back();
		int[] n = boxes.next();
		boolean overBack = hasBack && CopperLayout.inside(b, mouseX, mouseY);
		boolean overNext = hasNext && CopperLayout.inside(n, mouseX, mouseY);
		Component back = boxes.compact() ? Component.literal("<") : backLabel();
		Component next = boxes.compact() ? Component.literal(">") : nextLabel();
		CopperStyle.button(g, font, back, null, b[0], b[1], b[2], b[3], CopperStyle.Kind.NEUTRAL, overBack, false, !hasBack);
		CopperStyle.button(g, font, next, null, n[0], n[1], n[2], n[3], CopperStyle.Kind.COPPER, overNext, false, !hasNext);
		int[] c = boxes.count();
		if (c[2] > 0 && reader.page() > 0) {
			String count = reader.page() + " / " + pages.size();
			g.drawString(font, count, c[0] + (c[2] - font.width(count)) / 2, c[1] + 3, CopperStyle.TUFF, false);
		}
		if (overBack) {
			whisper = Component.translatable(KEY + "whisper.back");
		} else if (overNext) {
			whisper = Component.translatable(KEY + "whisper.next");
		}
	}

	/** A heading: Tuff capitals and a rule to its right. */
	private void heading(GuiGraphics g, Component text, int x, int y, int w) {
		String t = CopperStyle.trim(font, text.getString().toUpperCase(Locale.ROOT), w);
		g.drawString(font, t, x, y, CopperStyle.TUFF, false);
		int rx = x + font.width(t) + 4;
		if (rx < x + w) {
			g.fill(rx, y + 4, x + w, y + 5, CopperStyle.DEEP_600);
		}
	}

	/** Turns to {@code bookPage} (0 = the contents). For the screenshot scenes. */
	public void show(int bookPage) {
		go(bookPage);
	}

	/** The page shown on {@code bookPage} (1 and up). For checks. */
	public GuidePages.Page pageAt(int bookPage) {
		return pages.get(bookPage - 1);
	}

	/** How many lines a page's text takes here (more than {@link #TEXT_LINES} would be cut off). For checks. */
	public int textLines(int bookPage) {
		return bookPage <= 0 ? 0 : font.split(pages.get(bookPage - 1).text(), GuideLayout.textWidth(boxes)).size();
	}

	/** The picture's box for {@code bookPage} on this screen. For checks. */
	public int[] pictureBox(int bookPage) {
		return GuideLayout.page(boxes, Math.min(TEXT_LINES, textLines(bookPage))).picture();
	}

	/** The contents page's boxes on this screen. For checks. */
	public GuideLayout.Contents contentsBoxes() {
		return contents();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
