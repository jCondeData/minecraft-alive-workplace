package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * The villager status card in Copperline (ROADMAP 48.3, docs/design/copperline/boards/VillagerCard.png, "On click,
 * pinned"; first 43.3, the Nurse's and the Fossil Scientist's, and 42.5's job cards): a card with a copper top edge, the
 * villager's portrait (the real model) in a well with the name beside it and the job and level under it, the close X, the
 * status line(s), a heading over labeled lines (icon, label, value in its status color, bar), a divider, the reason a
 * grayed-out button can't be used in a Redstone-edged box, the buttons (the first usable one is the card's one copper
 * button; Close is the X) and the hint at the foot. A line with an item shows a Copperline tooltip under the mouse (the
 * item, the line, its value with a status glyph), and so does a grayed-out button (its reason). Everything comes from the
 * server ({@link StatusCard}); where it goes is {@link StatusCardLayout}'s.
 */
public class StatusCardScreen extends AbstractContainerScreen<StatusCardMenu> {
	private StatusCardLayout.Boxes boxes;
	/** The buttons in the row (all but Close, which is the X), in the card's order. */
	private final List<StatusCard.Button> row = new ArrayList<>();
	/** What the tooltip under the mouse shows this frame (null: none). */
	private Runnable tooltip;

	public StatusCardScreen(StatusCardMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	private StatusCard card() {
		return menu.card();
	}

	@Override
	protected void init() {
		row.clear();
		for (StatusCard.Button b : card().buttons()) {
			if (b.id() != StatusCardMenu.CLOSE) {
				row.add(b);
			}
		}
		int inner = StatusCardLayout.inner(width);
		int[] need = new int[row.size()];
		for (int i = 0; i < need.length; i++) {
			need[i] = TableStyle.buttonWidth(font, row.get(i).label());
		}
		StatusCard.Button off = firstOff();
		int refusalLines = off == null ? 0 : font.split(off.refusal(), StatusCardLayout.refusalTextWidth(width)).size();
		boxes = StatusCardLayout.of(width, height, font.split(card().subtitle(), inner).size(), bodyHeight(inner), refusalLines,
			!card().hint().getString().isEmpty(), need);
		imageWidth = boxes.card()[2];
		imageHeight = boxes.card()[3];
		super.init();
		leftPos = boxes.card()[0];
		topPos = boxes.card()[1];
	}

	/** The body's height: the heading and the lines, or the line for when there are none. */
	private int bodyHeight(int inner) {
		int h = card().heading().getString().isEmpty() ? 0 : 12;
		return h + (card().lines().isEmpty() ? font.split(card().empty(), inner).size() * 10 : card().lines().size() * TableStyle.CARD_LINE);
	}

	/** The first grayed-out button in the row (its reason shows above the row), or null. */
	private StatusCard.Button firstOff() {
		return row.stream().filter(b -> !b.usable()).findFirst().orElse(null);
	}

	/** The layout this frame (the GameTests' and the screenshot scene's view of it). */
	public StatusCardLayout.Boxes boxes() {
		return boxes;
	}

	/** The index in the row of the copper button, or -1. */
	public int copperIndex() {
		boolean[] usable = new boolean[row.size()];
		boolean[] close = new boolean[row.size()];
		for (int i = 0; i < usable.length; i++) {
			usable[i] = row.get(i).usable();
		}
		return StatusCardLayout.copper(usable, close);
	}

	/** A server color as Copperline's: gold Lantern, green Verdigris, red Redstone, orange Copper, gray Tuff, white Quartz. */
	public static int copperline(int color) {
		return switch (color) {
			case StatusCard.GOLD -> CopperStyle.LANTERN;
			case StatusCard.GREEN -> CopperStyle.VERDIGRIS;
			case StatusCard.RED -> CopperStyle.REDSTONE;
			case StatusCard.ORANGE -> CopperStyle.COPPER;
			case StatusCard.GRAY -> CopperStyle.TUFF;
			case StatusCard.WHITE -> CopperStyle.QUARTZ;
			default -> color;
		};
	}

	/** The status a line's color says (glyph and word), or null for a plain value. */
	static CopperStyle.Status status(int color) {
		return switch (color) {
			case StatusCard.GREEN -> CopperStyle.Status.GOOD;
			case StatusCard.RED -> CopperStyle.Status.BAD;
			case StatusCard.GOLD, StatusCard.ORANGE -> CopperStyle.Status.WARNING;
			default -> null;
		};
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBlurredBackground(partialTick);
		renderMenuBackground(g);
		tooltip = null;
		int[] c = boxes.card();
		CopperStyle.panel(g, c[0], c[1], c[2], c[3]);
		g.renderOutline(c[0] - 1, c[1] - 1, c[2] + 2, c[3] + 2, CopperStyle.VOID);
		g.fill(c[0], c[1], c[0] + c[2], c[1] + StatusCardLayout.EDGE, CopperStyle.COPPER);

		int[] p = boxes.portrait();
		CopperStyle.well(g, p[0], p[1], p[2], p[3]);
		Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(card().entityId());
		if (entity instanceof LivingEntity living) {
			// The head and shoulders: the model drawn large and cut to the well.
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, p[0] + 1, p[1] + 1, p[0] + p[2] - 1, p[1] + p[3] - 1, 38, 0.55f,
				mouseX, mouseY, living);
		}
		int[] n = boxes.name();
		CopperStyle.label(g, font, card().title().getString(), n[0], n[1], n[2], CopperStyle.QUARTZ);
		int[] t = boxes.tag();
		g.drawString(font, CopperStyle.trim(font, card().tag().getString(), t[2]), t[0], t[1], CopperStyle.TUFF, false);
		int[] x = boxes.close();
		CopperStyle.close(g, x, CopperLayout.inside(x, mouseX, mouseY));

		int[] s = boxes.subtitle();
		int y = s[1];
		for (FormattedCharSequence line : font.split(card().subtitle(), s[2])) {
			g.drawString(font, line, s[0], y, CopperStyle.QUARTZ, false);
			y += 10;
		}

		renderBody(g, mouseX, mouseY);

		int[] d = boxes.divider();
		g.fill(d[0], d[1], d[0] + d[2], d[1] + 1, CopperStyle.DEEP_600);
		StatusCard.Button off = firstOff();
		int[] r = boxes.refusal();
		if (off != null && r[3] > 0) {
			g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], CopperStyle.DEEP_900);
			g.fill(r[0], r[1], r[0] + 2, r[1] + r[3], CopperStyle.REDSTONE);
			int ry = r[1] + 3;
			for (FormattedCharSequence line : font.split(off.refusal(), StatusCardLayout.refusalTextWidth(width))) {
				g.drawString(font, line, r[0] + 7, ry, CopperStyle.QUARTZ, false);
				ry += 10;
			}
		}

		int copper = copperIndex();
		int[][] b = boxes.buttons();
		for (int i = 0; i < b.length; i++) {
			StatusCard.Button button = row.get(i);
			boolean over = CopperLayout.inside(b[i], mouseX, mouseY);
			CopperStyle.button(g, font, button.label(), null, b[i][0], b[i][1], b[i][2], b[i][3],
				i == copper ? CopperStyle.Kind.COPPER : CopperStyle.Kind.NEUTRAL, over && button.usable(), false, !button.usable());
			if (over && !button.usable()) {
				List<Component> reason = wrap(button.refusal(), StatusCardLayout.tooltipWrap(width));
				Component word = Component.translatable("screen.aliveworkplace.card.off");
				int[] at = StatusCardLayout.buttonTooltip(width, height, mouseX, mouseY,
					CopperStyle.tooltipWidth(font, button.label(), reason, CopperStyle.Status.BAD, word),
					StatusCardLayout.tooltipHeight(reason.size(), true));
				tooltip = () -> CopperStyle.tooltip(g, font, button.label(), reason, CopperStyle.Status.BAD, word, at[0], at[1]);
			}
		}
		int[] h = boxes.hint();
		if (h[3] > 0) {
			TableStyle.footerHint(g, font, card().hint(), h[0], h[1], h[2]);
		}
		if (tooltip != null) {
			tooltip.run();
		}
	}

	/** The heading and the lines (or the line for none), cut to the body's room. */
	private void renderBody(GuiGraphics g, int mouseX, int mouseY) {
		int[] body = boxes.body();
		if (body[3] <= 0) {
			return;
		}
		g.enableScissor(body[0], body[1], body[0] + body[2], body[1] + body[3]);
		int y = body[1];
		if (!card().heading().getString().isEmpty()) {
			TableStyle.heading(g, font, card().heading(), body[0], y);
			y += 12;
		}
		if (card().lines().isEmpty()) {
			for (FormattedCharSequence line : font.split(card().empty(), body[2])) {
				g.drawString(font, line, body[0], y, CopperStyle.TUFF, false);
				y += 10;
			}
		} else {
			for (StatusCard.Line line : card().lines()) {
				if (y + TableStyle.CARD_LINE > body[1] + body[3] + 2) {
					break; // a short screen: whole rows only, never half of one
				}
				int color = copperline(line.color());
				TableStyle.cardLine(g, font, line.icon(), line.label(), line.value(), color, line.bar(), body[0], y, body[2]);
				int[] at = {body[0], y, body[2], TableStyle.CARD_LINE};
				if (!line.icon().isEmpty() && y + TableStyle.CARD_LINE <= body[1] + body[3] && CopperLayout.inside(at, mouseX, mouseY)) {
					int rowY = y;
					tooltip = () -> lineTooltip(g, line, rowY);
				}
				y += TableStyle.CARD_LINE;
			}
		}
		g.disableScissor();
	}

	/**
	 * A line's tooltip, as the board's: the item, the line's label when it says more, the value with its status glyph. It
	 * sits beside the card at the row's height (right of it, or left when the screen has no room; above or below the row when
	 * neither side has room), so it never covers the row it describes, and always on screen ({@link StatusCardLayout#lineTooltip},
	 * B1357).
	 */
	private void lineTooltip(GuiGraphics g, StatusCard.Line line, int rowY) {
		Component item = line.icon().getHoverName();
		List<Component> lines = new ArrayList<>();
		if (!line.label().getString().equals(item.getString())) {
			lines.add(line.label());
		}
		CopperStyle.Status status = status(line.color());
		if (status == null && !line.value().getString().isEmpty()) {
			lines.add(line.value());
		}
		int w = CopperStyle.tooltipWidth(font, item, lines, status, line.value());
		int h = StatusCardLayout.tooltipHeight(lines.size(), status != null);
		int[] row = {boxes.body()[0], rowY, boxes.body()[2], TableStyle.CARD_LINE};
		int[] at = StatusCardLayout.lineTooltip(width, height, boxes.card(), row, w, h);
		CopperStyle.tooltip(g, font, item, lines, status, line.value(), at[0], at[1]);
	}

	/** {@code text} wrapped at {@code w} pixels, one Component per line. */
	private List<Component> wrap(Component text, int w) {
		List<Component> out = new ArrayList<>();
		for (FormattedText part : font.getSplitter().splitLines(text, w, Style.EMPTY)) {
			out.add(Component.literal(part.getString()));
		}
		return out;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			if (CopperLayout.inside(boxes.close(), (int) mx, (int) my)) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
				onClose();
				return true;
			}
			int[][] b = boxes.buttons();
			for (int i = 0; i < b.length; i++) {
				if (CopperLayout.inside(b[i], (int) mx, (int) my)) {
					if (row.get(i).usable()) {
						press(row.get(i).id());
					}
					return true; // a grayed-out one: its reason is above the row
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	/** Presses button {@code id}, as a click on it does. */
	public void press(int id) {
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		if (mc.gameMode != null) {
			mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	/** Where button {@code id}'s middle is on the screen, in GUI pixels (the screenshot harness points at it); Close is the X. */
	public int[] centre(int id) {
		int[][] b = boxes.buttons();
		for (int i = 0; i < b.length; i++) {
			if (row.get(i).id() == id) {
				return new int[]{b[i][0] + b[i][2] / 2, b[i][1] + b[i][3] / 2};
			}
		}
		int[] x = boxes.close();
		if (id == StatusCardMenu.CLOSE) {
			return new int[]{x[0] + x[2] / 2, x[1] + x[3] / 2};
		}
		int[] c = boxes.card();
		return new int[]{c[0] + c[2] / 2, c[1] + c[3] / 2};
	}

	/** Where line {@code i}'s middle is (the scene points at it for the tooltip). */
	public int[] lineCentre(int i) {
		int[] body = boxes.body();
		int y = body[1] + (card().heading().getString().isEmpty() ? 0 : 12) + i * TableStyle.CARD_LINE;
		return new int[]{body[0] + 8, y + TableStyle.CARD_LINE / 2};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
