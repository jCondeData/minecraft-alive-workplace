package io.github.jcondedata.aliveworkplace.client.guide;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The Guide Book's screen (ROADMAP 26.2a): one page at a time, like a book: the chapter and page number on top, the page's
 * title, its in-game screenshot and a few short steps under it, and the page arrows at the bottom. Page 0 lists the
 * chapters; click one to jump there. Drawn with plain fills in book colours (no texture), like the mod's other screens.
 * The book opens where the player left it.
 */
public class GuideScreen extends Screen {
	static final int PANEL_W = 260;
	/** At most 236 tall: the smallest screen the Auto GUI scale gives is 240 (854 x 480, 1280 x 720, 2560 x 1440...). */
	static final int PANEL_H = 236;
	static final int IMAGE_W = 224;
	static final int IMAGE_H = 126;
	/** The text is a little wider than the picture. */
	static final int TEXT_W = 240;
	private static final int MARGIN = 10;
	public static final int TEXT_LINES = 6;

	private static final int LEATHER_DARK = 0xFF3B2414;
	private static final int LEATHER = 0xFF6B4423;
	private static final int PAPER = 0xFFF4EBD2;
	private static final int PAPER_EDGE = 0xFFD9CBA6;
	private static final int INK = 0xFF2B2117;
	private static final int FAINT = 0xFF8A7656;
	private static final int LINK = 0xFF1F4E8C;
	private static final int LINK_HOVER = 0xFF2F72C8;

	/** Where the book was left open (this game session). */
	private static int lastPage;

	private final List<GuidePages.Page> pages = GuidePages.shown();
	private int page;
	private int left;
	private int top;
	private PageButton back;
	private PageButton forward;
	private Button contents;

	public GuideScreen() {
		super(Component.translatable("item.aliveworkplace.guide_book"));
		page = Math.min(lastPage, pages.size());
	}

	/** Opens the book at {@code page} (0 = the contents); for the screenshot scenes. */
	public GuideScreen(int page) {
		this();
		this.page = Math.max(0, Math.min(page, pages.size()));
	}

	/** Pages counting the contents page. */
	public int pageCount() {
		return pages.size() + 1;
	}

	public int page() {
		return page;
	}

	@Override
	protected void init() {
		left = (width - PANEL_W) / 2;
		top = Math.max(2, (height - PANEL_H) / 2);
		int navY = top + PANEL_H - 17;
		back = addRenderableWidget(new PageButton(left + 8, navY, false, b -> turn(-1), true));
		forward = addRenderableWidget(new PageButton(left + PANEL_W - 8 - 23, navY, true, b -> turn(1), true));
		contents = addRenderableWidget(Button.builder(Component.translatable("guide.aliveworkplace.contents"), b -> go(0))
			.bounds(left + PANEL_W / 2 - 32, navY - 1, 64, 14).build());
		update();
	}

	private void update() {
		back.visible = page > 0;
		forward.visible = page < pages.size();
		contents.visible = page > 0;
		lastPage = page;
	}

	private void turn(int by) {
		go(page + by);
	}

	private void go(int to) {
		int clamped = Math.max(0, Math.min(to, pages.size()));
		if (clamped != page) {
			page = clamped;
			update();
		}
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
		if (page == 0 && button == 0) {
			int chapter = chapterAt(mouseX, mouseY);
			if (chapter >= 0) {
				go(firstPageOf(GuidePages.CHAPTERS.get(chapter)));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** The book page (1-based, after the contents) a chapter starts on, or 0 if it has none here. */
	private int firstPageOf(String chapter) {
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).chapter().equals(chapter)) {
				return i + 1;
			}
		}
		return 0;
	}

	private List<String> shownChapters() {
		return GuidePages.CHAPTERS.stream().filter(c -> firstPageOf(c) > 0).toList();
	}

	private int chapterRowY(int i) {
		return top + 96 + i * 15;
	}

	/** The contents row under the mouse (an index into {@link GuidePages#CHAPTERS}), or -1. */
	private int chapterAt(double mouseX, double mouseY) {
		List<String> shown = shownChapters();
		for (int i = 0; i < shown.size(); i++) {
			int y = chapterRowY(i);
			if (mouseX >= left + MARGIN && mouseX < left + PANEL_W - MARGIN && mouseY >= y - 2 && mouseY < y + 11) {
				return GuidePages.CHAPTERS.indexOf(shown.get(i));
			}
		}
		return -1;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int x0 = left, y0 = top, x1 = left + PANEL_W, y1 = top + PANEL_H;
		g.fill(x0, y0, x1, y1, LEATHER_DARK);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, LEATHER);
		g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, PAPER_EDGE);
		g.fill(x0 + 5, y0 + 5, x1 - 5, y1 - 5, PAPER);
		// Leather tabs under the page arrows, so the light arrows stand out on the paper.
		for (PageButton arrow : new PageButton[] {back, forward}) {
			if (arrow != null && arrow.visible) {
				g.fill(arrow.getX() - 2, arrow.getY() - 2, arrow.getX() + arrow.getWidth() + 2, arrow.getY() + arrow.getHeight() + 2, LEATHER);
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if (page == 0) {
			renderContents(g, mouseX, mouseY);
		} else {
			renderPage(g, pages.get(page - 1));
		}
	}

	private void renderContents(GuiGraphics g, int mouseX, int mouseY) {
		int cx = left + PANEL_W / 2;
		g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), cx - font.width(title.copy().withStyle(ChatFormatting.BOLD)) / 2,
			top + 14, INK, false);
		int y = top + 32;
		for (FormattedCharSequence line : font.split(Component.translatable("guide.aliveworkplace.intro"), PANEL_W - 2 * MARGIN - 4)) {
			g.drawString(font, line, left + MARGIN + 2, y, INK, false);
			y += 10;
		}
		g.drawString(font, Component.translatable("guide.aliveworkplace.chapters"), left + MARGIN + 2, top + 82, FAINT, false);
		List<String> shown = shownChapters();
		int hovered = chapterAt(mouseX, mouseY);
		for (int i = 0; i < shown.size(); i++) {
			String chapter = shown.get(i);
			boolean over = hovered == GuidePages.CHAPTERS.indexOf(chapter);
			Component name = Component.literal((i + 1) + ". ").append(GuidePages.chapterName(chapter));
			if (over) {
				name = name.copy().withStyle(ChatFormatting.UNDERLINE);
			}
			int row = chapterRowY(i);
			g.drawString(font, name, left + MARGIN + 6, row, over ? LINK_HOVER : LINK, false);
			String number = String.valueOf(firstPageOf(chapter));
			g.drawString(font, number, left + PANEL_W - MARGIN - 4 - font.width(number), row, FAINT, false);
		}
	}

	private void renderPage(GuiGraphics g, GuidePages.Page p) {
		int x = left + MARGIN;
		int ix = left + (PANEL_W - IMAGE_W) / 2;
		g.drawString(font, p.chapterName(), x, top + 8, FAINT, false);
		String number = page + " / " + pages.size();
		g.drawString(font, number, left + PANEL_W - MARGIN - font.width(number), top + 8, FAINT, false);
		Component title = p.title().copy().withStyle(ChatFormatting.BOLD);
		g.drawString(font, title, left + (PANEL_W - font.width(title)) / 2, top + 19, INK, false);
		int iy = top + 31;
		g.fill(ix - 1, iy - 1, ix + IMAGE_W + 1, iy + IMAGE_H + 1, LEATHER_DARK);
		g.blit(p.image(), ix, iy, IMAGE_W, IMAGE_H, 0, 0, GuidePages.TEXTURE_W, GuidePages.TEXTURE_H, GuidePages.TEXTURE_W, GuidePages.TEXTURE_H);
		int y = iy + IMAGE_H + 5;
		List<FormattedCharSequence> lines = font.split(p.text(), TEXT_W);
		for (int i = 0; i < Math.min(lines.size(), TEXT_LINES); i++) {
			g.drawString(font, lines.get(i), x, y, INK, false);
			y += 9;
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
		return bookPage <= 0 ? 0 : font.split(pages.get(bookPage - 1).text(), TEXT_W).size();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
