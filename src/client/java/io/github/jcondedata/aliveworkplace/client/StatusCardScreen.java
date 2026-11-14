package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * A small status card in the Village Hall's look (ROADMAP 43.3: the Nurse's and the Fossil Scientist's; 42.5's job cards
 * reuse it), sized as {@code docs/design/M42/trainer_c.png}: the villager's name in gold with their job on the right, a
 * grey-blue line, a gold heading over labeled lines (icon, label, value, bar), the buttons, the reason a grayed-out one
 * can't be used, and a footer hint. Everything comes from the server ({@link StatusCard}); the buttons are menu-button
 * clicks. The card is as tall as what it shows.
 */
public class StatusCardScreen extends AbstractContainerScreen<StatusCardMenu> {
	private static final int BUTTON_H = 16;

	private int cardW;
	private int cardH;
	/** Each button's box this frame: id, x, y, w, grayed. */
	private final List<int[]> buttons = new ArrayList<>();

	public StatusCardScreen(StatusCardMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	private StatusCard card() {
		return menu.card();
	}

	@Override
	protected void init() {
		cardW = Math.min(TableStyle.CARD_W, width - 16);
		cardH = Math.min(height - 16, layoutHeight());
		imageWidth = cardW;
		imageHeight = cardH;
		super.init();
	}

	/** The card's height for what it shows: the same steps {@link #render} takes. */
	private int layoutHeight() {
		int inner = cardW - TableStyle.CARD_PAD * 2;
		int h = TableStyle.CARD_PAD + 11;
		h += font.split(card().subtitle(), inner).size() * 10 + 6;
		if (!card().heading().getString().isEmpty()) {
			h += 12;
		}
		h += card().lines().isEmpty() ? font.split(card().empty(), inner).size() * 10 + 4 : card().lines().size() * TableStyle.CARD_LINE + 2;
		int rows = StatusCard.buttonRows(buttonBoxes(inner));
		h += 6 + rows * BUTTON_H + (rows - 1) * StatusCard.BUTTON_ROW_GAP + 4;
		StatusCard.Button off = firstOff();
		if (off != null) {
			h += font.split(off.refusal(), inner).size() * 10 + 2;
		}
		h += card().hint().getString().isEmpty() ? 4 : 14;
		return h;
	}

	/** The buttons' boxes inside {@code inner} pixels ({@link StatusCard#buttonRow}): x, row, width. */
	private int[][] buttonBoxes(int inner) {
		List<StatusCard.Button> list = card().buttons();
		int[] need = new int[list.size()];
		for (int i = 0; i < need.length; i++) {
			need[i] = TableStyle.buttonWidth(font, list.get(i).label());
		}
		return StatusCard.buttonRow(need, inner);
	}

	/** The first grayed-out button (its reason shows under the row), or null. */
	private StatusCard.Button firstOff() {
		return card().buttons().stream().filter(b -> !b.usable()).findFirst().orElse(null);
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
		int pad = TableStyle.CARD_PAD;
		int x = leftPos + pad;
		int w = cardW - pad * 2;
		int y = topPos + pad;
		TableStyle.panel(g, leftPos, topPos, cardW, cardH);
		TableStyle.cardHeader(g, font, card().title(), card().tag(), x, y, w);
		y += 11;
		for (FormattedCharSequence line : font.split(card().subtitle(), w)) {
			g.drawString(font, line, x, y, TableStyle.SUBLINE, false);
			y += 10;
		}
		y += 6;
		if (!card().heading().getString().isEmpty()) {
			TableStyle.heading(g, font, card().heading(), x, y);
			y += 12;
		}
		if (card().lines().isEmpty()) {
			for (FormattedCharSequence line : font.split(card().empty(), w)) {
				g.drawString(font, line, x, y, TableStyle.BODY, false);
				y += 10;
			}
			y += 4;
		} else {
			for (StatusCard.Line line : card().lines()) {
				TableStyle.cardLine(g, font, line.icon(), line.label(), line.value(), line.color(), line.bar(), x, y, w);
				y += TableStyle.CARD_LINE;
			}
			y += 2;
		}
		y += 6;
		buttons.clear();
		int[][] boxes = buttonBoxes(w);
		boolean gold = true;
		Component tooltip = null;
		for (int i = 0; i < boxes.length; i++) {
			StatusCard.Button button = card().buttons().get(i);
			boolean off = !button.usable();
			int bx = x + boxes[i][0];
			int by = y + boxes[i][1] * (BUTTON_H + StatusCard.BUTTON_ROW_GAP);
			int bw = boxes[i][2];
			boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_H;
			boolean on = gold && !off && button.id() != StatusCardMenu.CLOSE;
			TableStyle.button(g, font, button.label(), bx, by, bw, BUTTON_H, on, over, off);
			gold &= !on;
			buttons.add(new int[]{button.id(), bx, by, bw, off ? 1 : 0});
			if (over && off) {
				tooltip = button.refusal();
			}
		}
		int rows = StatusCard.buttonRows(boxes);
		y += rows * BUTTON_H + (rows - 1) * StatusCard.BUTTON_ROW_GAP + 4;
		StatusCard.Button off = firstOff();
		if (off != null) {
			for (FormattedCharSequence line : font.split(off.refusal(), w)) {
				g.drawString(font, line, x, y, TableStyle.BODY, false);
				y += 10;
			}
			y += 2;
		}
		if (!card().hint().getString().isEmpty()) {
			TableStyle.footerHint(g, font, card().hint(), x, topPos + cardH - 12, w);
		}
		if (tooltip != null) {
			g.renderTooltip(font, font.split(tooltip, 200), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			for (int[] b : buttons) {
				if (mx >= b[1] && mx < b[1] + b[3] && my >= b[2] && my < b[2] + BUTTON_H) {
					if (b[4] == 0) {
						press(b[0]);
					}
					return true; // a grayed-out one: its reason is under the row
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

	/** Where button {@code id}'s middle is on the screen, in GUI pixels (the screenshot harness points at it). */
	public int[] centre(int id) {
		for (int[] b : buttons) {
			if (b[0] == id) {
				return new int[]{b[1] + b[3] / 2, b[2] + BUTTON_H / 2};
			}
		}
		return new int[]{leftPos + cardW / 2, topPos + cardH / 2};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
