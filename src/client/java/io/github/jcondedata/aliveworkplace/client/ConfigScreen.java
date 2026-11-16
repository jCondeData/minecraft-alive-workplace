package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.SettingsEditor;
import io.github.jcondedata.aliveworkplace.ui.SettingsLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's settings ({@code config/aliveworkplace.json}) in Copperline (ROADMAP 44.4, boards/Settings.png): the plaque,
 * the lintel (the open section and how many settings it has), the rail of sections, and the open section's settings in a
 * scrolling list: a toggle for every switch, [-] and [+] or a slider for every distance and number, "Reset this page" and
 * Done. Pointing at a setting shows what it does in a Copperline tooltip and how to change it in the whisper line. Opened
 * from Mod Menu. Closing the screen (Done, the close X or Esc) saves the file and puts the values into effect in this game
 * (singleplayer and LAN worlds); a dedicated server keeps its own file, which the whisper line says. The clicks go through
 * {@link SettingsEditor}, the boxes come from {@link SettingsLayout}.
 */
public class ConfigScreen extends Screen {
	/** The language keys of an option's label and tooltip. */
	public static String labelKey(String option) {
		return "aliveworkplace.config." + option;
	}

	public static String tooltipKey(String option) {
		return "aliveworkplace.config." + option + ".tooltip";
	}

	private static final String KEY = "screen.aliveworkplace.settings.";

	private final Screen lastScreen;
	private final Path dir;
	private final WorkplaceConfig config;
	private final SettingsEditor editor;
	private SettingsLayout.Screen boxes;
	private Component whisper = Component.empty();
	private boolean pressed;

	public ConfigScreen(Screen lastScreen) {
		super(Component.translatable("aliveworkplace.config.title"));
		this.lastScreen = lastScreen;
		this.dir = Platform.get().configDir();
		this.config = WorkplaceConfig.load(dir);
		this.editor = new SettingsEditor(config);
	}

	/** A section's name in the rail and the lintel. */
	public static Component sectionName(String id) {
		return Component.translatable(KEY + "section." + id);
	}

	/** The rail's icon for a section: one of Copperline's 16 px icons, or an item. */
	private static String sectionIcon(String id) {
		return switch (id) {
			case "builder" -> "buildings";
			case "village" -> "homes";
			case "people" -> "people";
			case "defense" -> "defense";
			case "realms" -> "map";
			case "other" -> "laws";
			default -> null;
		};
	}

	private static ItemStack sectionItem(String id) {
		return switch (id) {
			case "work" -> new ItemStack(Items.IRON_PICKAXE);
			case "jobs" -> new ItemStack(Items.CRAFTING_TABLE);
			case "pokemon" -> new ItemStack(Items.EGG);
			default -> ItemStack.EMPTY;
		};
	}

	@Override
	protected void init() {
		int railW = 0;
		for (String id : editor.sections()) {
			railW = Math.max(railW, font.width(sectionName(id)));
		}
		railW += font.width("00") - 6; // the count's two digits need a few pixels more than the shell keeps for them
		boxes = SettingsLayout.screen(width, height, font.width(title), railW, editor.sections().size(),
			font.width(Component.translatable(KEY + "reset")) + 12, Math.max(44, font.width(Component.translatable(KEY + "done")) + 16));
		editor.scrollTo(boxes, editor.scroll());
	}

	/** The boxes as laid out for this screen size (for the screenshot scene's checks). */
	public SettingsLayout.Screen boxes() {
		return boxes;
	}

	public SettingsEditor editor() {
		return editor;
	}

	/** The settings being edited, saved when the screen closes. */
	public WorkplaceConfig config() {
		return config;
	}

	/** Opens the section a setting is in and scrolls it into sight (for the screenshot scene). */
	public void show(String option) {
		editor.open(editor.sections().indexOf(SettingsLayout.sectionOf(option)));
		List<SettingsLayout.Row> rows = editor.rows();
		for (int i = 0; i < rows.size(); i++) {
			if (option.equals(rows.get(i).option())) {
				editor.scrollTo(boxes, i - boxes.listRows() / 2);
			}
		}
	}

	public void scrollToEnd() {
		editor.scrollTo(boxes, Integer.MAX_VALUE / 2);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(lastScreen);
	}

	@Override
	public void removed() {
		super.removed();
		config.save(dir);
		config.apply();
	}

	// --- input ---------------------------------------------------------------------------------------------------------

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			if (CopperLayout.inside(boxes.close(), mx, my) || CopperLayout.inside(boxes.done(), mx, my)) {
				click();
				onClose();
				return true;
			}
			if (editor.click(boxes, mx, my)) {
				pressed = true;
				click();
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (button == 0 && editor.drag(boxes, mx)) {
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		editor.release();
		pressed = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (scrollY != 0) {
			editor.scrollBy(boxes, scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
			double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
			double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
			String name = editor.optionAt(boxes, mx, my);
			if (name != null && !WorkplaceConfig.isSwitch(name)) {
				int by = (key == GLFW.GLFW_KEY_LEFT ? -1 : 1) * (hasShiftDown() ? 10 : 1);
				editor.step(name, by);
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
			editor.scrollBy(boxes, (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * boxes.listRows());
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB) {
			int n = editor.sections().size();
			editor.open((editor.section() + (hasShiftDown() ? n - 1 : 1)) % n);
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
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
		whisper = Component.translatable("aliveworkplace.config.note");
		super.render(g, mouseX, mouseY, partialTick);
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, boxes.win());
		String id = editor.sectionId();
		CopperStyle.lintel(g, font, boxes.win(), plaque,
			Component.translatable(KEY + "lintel", sectionName(id), editor.options().size()), overClose);
		renderRail(g, mouseX, mouseY);
		String tip = renderList(g, mouseX, mouseY);
		boolean overReset = CopperLayout.inside(boxes.reset(), mouseX, mouseY);
		boolean overDone = CopperLayout.inside(boxes.done(), mouseX, mouseY);
		int[] r = boxes.reset();
		CopperStyle.button(g, font, Component.translatable(KEY + "reset"), null, r[0], r[1], r[2], r[3], CopperStyle.Kind.NEUTRAL, overReset,
			overReset && pressed, false);
		int[] d = boxes.done();
		CopperStyle.button(g, font, Component.translatable(KEY + "done"), null, d[0], d[1], d[2], d[3], CopperStyle.Kind.COPPER, overDone, false, false);
		if (overReset) {
			whisper = Component.translatable(KEY + "whisper.reset", sectionName(id));
		} else if (overDone || overClose) {
			whisper = Component.translatable(KEY + "whisper.done");
		}
		int[] w = boxes.whisper();
		CopperStyle.whisper(g, font, whisper, w[0] + 2, w[1] + 3, w[2] - 4, boxes.compact());
		if (tip != null && !editor.dragging()) {
			renderTip(g, tip, mouseX, mouseY);
		}
	}

	private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] rail = boxes.rail();
		g.fill(rail[0], rail[1], rail[0] + rail[2], rail[1] + rail[3], CopperStyle.DEEP_800);
		if (!boxes.compact()) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable(KEY + "sections").getString().toUpperCase(Locale.ROOT), rail[2] - 8),
				rail[0] + 4, rail[1] + 3, CopperStyle.TUFF, false);
		}
		int[][] entries = boxes.railEntries();
		List<String> sections = editor.sections();
		for (int i = 0; i < entries.length && i < sections.size(); i++) {
			String id = sections.get(i);
			boolean over = CopperLayout.inside(entries[i], mouseX, mouseY);
			CopperStyle.railEntry(g, font, entries[i], sectionIcon(id), sectionItem(id), sectionName(id),
				boxes.compact() ? 0 : editor.optionsIn(id).size(), i == editor.section(), over, boxes.compact());
			if (over) {
				whisper = Component.translatable(KEY + "whisper.section", sectionName(id), editor.optionsIn(id).size());
			}
		}
	}

	/** The open section's rows; returns the setting whose tooltip the mouse is on (its label), or null. */
	private String renderList(GuiGraphics g, int mouseX, int mouseY) {
		List<SettingsLayout.Row> rows = editor.rows();
		int shown = boxes.listRows();
		String tip = null;
		String on = Component.translatable(KEY + "on").getString();
		String off = Component.translatable(KEY + "off").getString();
		int wordW = Math.max(font.width(on), font.width(off));
		for (int v = 0; v < shown && editor.scroll() + v < rows.size(); v++) {
			int i = editor.scroll() + v;
			SettingsLayout.Row row = rows.get(i);
			int[] box = SettingsLayout.row(boxes, v);
			if (row.isHeading()) {
				heading(g, Component.translatable(KEY + row.heading()), box[0] + 1, box[1] + 4, box[2] - 2);
				continue;
			}
			String name = row.option();
			boolean isSwitch = WorkplaceConfig.isSwitch(name);
			WorkplaceConfig.Range range = isSwitch ? null : WorkplaceConfig.RANGES.get(name);
			boolean small = range != null && SettingsLayout.small(range);
			boolean over = CopperLayout.inside(box, mouseX, mouseY) && !editor.dragging();
			CopperStyle.rowBand(g, box[0], box[1], box[2], box[3] - 1, v % 2 == 1, over, false);
			int labelX = SettingsLayout.labelX(box);
			int room = SettingsLayout.labelRoom(box, isSwitch, small, boxes.compact(), wordW);
			Component label = Component.translatable(labelKey(name));
			g.drawString(font, CopperStyle.trim(font, label.getString(), room), labelX, box[1] + 3, CopperStyle.QUARTZ, false);
			if (isSwitch) {
				boolean value = config.getBoolean(name);
				int[] t = SettingsLayout.toggle(box);
				String word = value ? on : off;
				g.drawString(font, word, t[0] - 4 - font.width(word), box[1] + 3, value ? CopperStyle.VERDIGRIS : CopperStyle.TUFF, false);
				CopperStyle.toggle(g, t[0], t[1], value, false, over);
				if (over) {
					whisper = Component.translatable(KEY + "whisper.switch", label);
				}
			} else if (range != null) {
				int value = config.getInt(name);
				String shownValue = valueText(name, value);
				if (small) {
					int[][] n = SettingsLayout.number(box);
					int overPart = !over ? -1 : CopperLayout.at(n, mouseX, mouseY);
					CopperStyle.number(g, font, shownValue, n[0][0], n[0][1], SettingsLayout.FIELD_W, false, overPart);
					if (over) {
						whisper = Component.translatable(KEY + "whisper.number", range.min(), range.max());
					}
				} else {
					int[] s = SettingsLayout.slider(box, boxes.compact());
					int[] vbox = SettingsLayout.value(box);
					CopperStyle.slider(g, s[0], s[1], s[2], SettingsLayout.fraction(range, value), over);
					g.blitSprite(CopperStyle.WELL, vbox[0], vbox[1], vbox[2], vbox[3]);
					String t = CopperStyle.trim(font, shownValue, vbox[2] - 6);
					g.drawString(font, t, vbox[0] + vbox[2] - 3 - font.width(t), vbox[1] + 2, CopperStyle.QUARTZ, false);
					if (over || editor.dragging()) {
						whisper = Component.translatable(KEY + "whisper.slider", range.min(), range.max());
					}
				}
			}
			if (over && mouseX < labelX + Math.min(room, font.width(label)) + 2) {
				tip = name;
			}
		}
		int max = SettingsLayout.maxScroll(boxes, rows.size());
		int[] bar = SettingsLayout.scrollbar(boxes);
		CopperStyle.scrollbar(g, bar[0], bar[1], bar[3], editor.scroll(), max, rows.size(), CopperLayout.inside(bar, mouseX, mouseY));
		return tip;
	}

	/** A number as the screen shows it: its own word at 0 when it has one ("No Limit"). */
	private static String valueText(String name, int value) {
		String key = labelKey(name) + ".zero";
		if (value == 0 && Language.getInstance().has(key)) {
			return Component.translatable(key).getString();
		}
		return String.valueOf(value);
	}

	/** A heading over a group: Tuff capitals and a rule to its right. */
	private void heading(GuiGraphics g, Component text, int x, int y, int w) {
		String t = CopperStyle.trim(font, text.getString().toUpperCase(Locale.ROOT), w);
		g.drawString(font, t, x, y, CopperStyle.TUFF, false);
		int rx = x + font.width(t) + 4;
		if (rx < x + w) {
			g.fill(rx, y + 4, x + w, y + 5, CopperStyle.DEEP_600);
		}
	}

	/** What a setting does, in a Copperline tooltip by the mouse, kept on the screen. */
	private void renderTip(GuiGraphics g, String name, int mouseX, int mouseY) {
		Component label = Component.translatable(labelKey(name));
		int wrap = Math.min(220, width - 24);
		List<Component> lines = new ArrayList<>();
		for (FormattedText line : font.getSplitter().splitLines(Component.translatable(tooltipKey(name)), wrap, Style.EMPTY)) {
			lines.add(Component.literal(line.getString()));
		}
		int maxLines = Math.max(1, (height - 24 - 16) / 10);
		if (lines.size() > maxLines) {
			lines = new ArrayList<>(lines.subList(0, maxLines));
			lines.set(maxLines - 1, Component.literal(CopperStyle.trim(font, lines.get(maxLines - 1).getString() + "…", wrap)));
		}
		int w = font.width(label);
		for (Component line : lines) {
			w = Math.max(w, font.width(line));
		}
		w += 10;
		int h = 6 + 10 + lines.size() * 10 + 3;
		int x = Math.min(mouseX + 10, width - w - 4);
		int y = mouseY + 12 + h > height - 4 ? Math.max(4, mouseY - 6 - h) : mouseY + 12;
		CopperStyle.tooltip(g, font, label, lines, null, null, x, y);
	}
}
