package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Trainer screen (ROADMAP 43.1, {@code docs/design/M42/trainer_a.png}), in the Village Hall's look ({@link TableStyle}):
 * on the left the trainer's name, title and rank, their portrait, the player's record against them, today's line and the
 * rules; on the right the reward, the team (the real Pokémon models; the ones not seen yet as "???"), the player's party
 * with a line each, and Challenge, Trade and Close. Challenge is grayed out with the reason under it when the trainer
 * can't battle now. Everything shown comes from the server ({@link TrainerView}); the buttons are menu-button clicks.
 */
public class TrainerScreen extends AbstractContainerScreen<TrainerMenu> {
	private static final int SIDE_W = 112;
	private static final int CELL_H = 34;
	private static final int PARTY_ROW = 11;
	private static final int BUTTON_H = 16;
	private static final int MAX_H = 262;

	private int panelW;
	private int panelH;
	/** Each button's box this frame: id, x, y, w. */
	private final List<int[]> buttons = new ArrayList<>();
	/** Each team cell's box this frame: index, x, y, w. */
	private final List<int[]> cells = new ArrayList<>();

	public TrainerScreen(TrainerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	private TrainerView view() {
		return menu.view();
	}

	@Override
	protected void init() {
		panelW = TableStyle.panelWidth(width);
		panelH = Math.min(height - 16, MAX_H);
		imageWidth = panelW;
		imageHeight = panelH;
		super.init();
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
		int left = leftPos;
		int top = topPos;
		TableStyle.panel(g, left, top, panelW, panelH);
		renderSide(g, left + 6, top + 6, SIDE_W, panelH - 12 - 12, mouseX, mouseY);
		Component tooltip = renderMain(g, left + 6 + SIDE_W + 10, top + 8, panelW - SIDE_W - 6 - 10 - 8, mouseX, mouseY);
		TableStyle.footerHint(g, font, Component.translatable("screen.aliveworkplace.trainer.hint"), left + 8, top + panelH - 12, panelW - 16);
		if (tooltip != null) {
			g.renderTooltip(font, font.split(tooltip, 200), mouseX, mouseY);
		} else {
			int cell = cellAt(mouseX, mouseY);
			if (cell >= 0) {
				TrainerView.TeamSlot slot = view().team().get(cell);
				List<Component> lines = new ArrayList<>();
				if (slot.hidden()) {
					lines.add(Component.translatable("screen.aliveworkplace.trainer.team.unseen"));
				} else {
					lines.add(slot.icon().getHoverName());
					lines.add(Component.translatable("screen.aliveworkplace.trainer.level", slot.level()).withColor(TableStyle.SUBLINE));
					if (slot.scaled()) {
						lines.add(Component.translatable("screen.aliveworkplace.trainer.team.scaled").withColor(TrainerView.ORANGE));
					}
				}
				g.renderComponentTooltip(font, lines, mouseX, mouseY);
			}
		}
	}

	/** The left column: who the trainer is, the portrait, the record, today and the rules. */
	private void renderSide(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		TableStyle.listBackground(g, x, y, w, h);
		int tx = x + 6;
		int tw = w - 12;
		int ty = y + 6;
		g.drawString(font, TableStyle.trim(font, view().name(), tw), tx, ty, TableStyle.TITLE, false);
		ty += 11;
		g.drawString(font, TableStyle.trim(font, view().title(), tw), tx, ty, TableStyle.NAME, false);
		ty += 11;
		g.drawString(font, TableStyle.trim(font, view().rank().getString(), tw), tx, ty, TableStyle.SUBTITLE, false);
		ty += 14;
		int pw = 50;
		int ph = Math.max(40, Math.min(66, h - 140));
		int px = x + (w - pw) / 2;
		g.fill(px - 1, ty - 1, px + pw + 1, ty + ph + 1, TableStyle.PANEL_EDGE);
		g.fill(px, ty, px + pw, ty + ph, TableStyle.CELL_BG);
		Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(view().entityId());
		if (entity instanceof LivingEntity living) {
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, ty, px + pw, ty + ph, ph * 4 / 9, 0.0625f, mouseX, mouseY, living);
		}
		ty += ph + 8;
		ty = field(g, Component.translatable("screen.aliveworkplace.trainer.record"), view().record(), TableStyle.NAME, tx, ty, tw);
		ty = field(g, Component.translatable("screen.aliveworkplace.trainer.today"), view().today(), view().todayColor(), tx, ty, tw);
		field(g, Component.translatable("screen.aliveworkplace.trainer.rules_heading"), view().rules(), TableStyle.NAME, tx, ty, tw);
	}

	/** A heading in grey-blue and its value (wrapped) under it; returns where the next one goes. */
	private int field(GuiGraphics g, Component heading, Component value, int color, int x, int y, int w) {
		g.drawString(font, heading, x, y, TableStyle.SUBTITLE, false);
		y += 10;
		for (FormattedCharSequence line : font.split(value, w)) {
			g.drawString(font, line, x, y, color, false);
			y += 10;
		}
		return y + 5;
	}

	/** The right side: the reward, the team, the party and the buttons; returns the tooltip of what's under the mouse, if any. */
	private Component renderMain(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
		TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.trainer.challenge_heading", view().name()), x, y);
		y += 11;
		for (FormattedCharSequence line : font.split(view().reward(), w)) {
			g.drawString(font, line, x, y, TableStyle.BODY, false);
			y += 10;
		}
		y += 6;
		cells.clear();
		if (!view().team().isEmpty()) {
			TableStyle.heading(g, font, view().teamHeading(), x, y);
			y += 12;
			int n = view().team().size();
			int gap = 4;
			int cw = Math.min(48, (w - gap * (Math.max(6, n) - 1)) / Math.max(6, n));
			int hovered = cellAt(mouseX, mouseY);
			for (int i = 0; i < n; i++) {
				int cx = x + i * (cw + gap);
				cells.add(new int[]{i, cx, y, cw});
				teamCell(g, view().team().get(i), cx, y, cw, i == hovered);
			}
			y += CELL_H + 4;
			if (!view().teamHint().getString().isEmpty()) {
				g.drawString(font, TableStyle.trim(font, view().teamHint().getString(), w), x, y, TableStyle.SUBLINE, false);
				y += 10;
			}
			y += 6;
		}
		if (!view().party().isEmpty() || !view().partyNote().getString().isEmpty()) {
			TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.trainer.party"), x, y);
			y += 11;
			int rows = Math.max(1, view().party().size());
			TableStyle.listBackground(g, x, y, w, rows * PARTY_ROW + 4);
			int levelX = x + 6 + w * 40 / 100;
			int stateX = x + 6 + w * 56 / 100;
			if (view().party().isEmpty()) {
				g.drawString(font, TableStyle.trim(font, view().partyNote().getString(), w - 12), x + 6, y + 3, TableStyle.LOADING, false);
			}
			for (int i = 0; i < view().party().size(); i++) {
				TrainerView.PartyLine line = view().party().get(i);
				int ry = y + 3 + i * PARTY_ROW;
				g.drawString(font, TableStyle.trim(font, line.name(), levelX - x - 12), x + 6, ry, TableStyle.NAME, false);
				g.drawString(font, Component.translatable("screen.aliveworkplace.trainer.level", line.level()), levelX, ry, TableStyle.BODY, false);
				g.drawString(font, TableStyle.trim(font, line.state().getString(), x + w - stateX - 4), stateX, ry, line.color(), false);
			}
			y += rows * PARTY_ROW + 4 + 10;
		}
		buttons.clear();
		Component tooltip = null;
		boolean refused = !view().canChallenge();
		int[] ids = {TrainerMenu.CHALLENGE, TrainerMenu.TRADE, TrainerMenu.CLOSE};
		boolean[] offs = {refused, !view().canTrade(), false};
		Component[] labels = new Component[TrainerScreens.BUTTON_KEYS.length];
		int[] need = new int[labels.length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = Component.translatable(TrainerScreens.BUTTON_KEYS[i]);
			need[i] = TableStyle.buttonWidth(font, labels[i]);
		}
		// fitted inside the right side's w pixels, wrapped onto more rows if they must be (B1131, as the status card's, B1103)
		int[][] boxes = StatusCard.buttonRow(need, w);
		for (int i = 0; i < boxes.length; i++) {
			int id = ids[i];
			boolean off = offs[i];
			int bx = x + boxes[i][0];
			int by = y + boxes[i][1] * (BUTTON_H + StatusCard.BUTTON_ROW_GAP);
			int bw = boxes[i][2];
			boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BUTTON_H;
			TableStyle.button(g, font, labels[i], bx, by, bw, BUTTON_H, id == TrainerMenu.CHALLENGE && !off, over, off);
			buttons.add(new int[]{id, bx, by, bw, off ? 1 : 0});
			if (over && off && id == TrainerMenu.CHALLENGE) {
				tooltip = view().refusal();
			}
		}
		int rows = StatusCard.buttonRows(boxes);
		y += rows * BUTTON_H + (rows - 1) * StatusCard.BUTTON_ROW_GAP + 6;
		if (refused) {
			for (FormattedCharSequence line : font.split(view().refusal(), w)) {
				g.drawString(font, line, x, y, TableStyle.BODY, false);
				y += 10;
			}
		}
		return tooltip;
	}

	/** One of the trainer's Pokémon: a framed cell with its model over its level, or "???" for one not seen yet. */
	private void teamCell(GuiGraphics g, TrainerView.TeamSlot slot, int x, int y, int w, boolean over) {
		g.fill(x, y, x + w, y + CELL_H, over ? TableStyle.CELL_HOVER : TableStyle.PANEL_EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + CELL_H - 1, TableStyle.CELL_BG);
		String label;
		int color;
		if (slot.hidden()) {
			int cx = x + w / 2;
			int cy = y + 12;
			g.fill(cx - 7, cy - 7, cx + 7, cy + 7, TableStyle.PANEL_EDGE);
			g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0xFF0C1016);
			label = "???";
			color = TableStyle.FAINT;
		} else {
			g.pose().pushPose();
			g.pose().translate(x + w / 2f - 10, y + 2, 0);
			g.pose().scale(1.25f, 1.25f, 1f);
			g.renderItem(slot.icon(), 0, 0);
			g.pose().popPose();
			label = Component.translatable("screen.aliveworkplace.trainer.level", slot.level()).getString();
			color = slot.scaled() ? TrainerView.ORANGE : TableStyle.NAME;
		}
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + CELL_H - 10, color, false);
	}

	private int cellAt(double mx, double my) {
		for (int[] c : cells) {
			if (mx >= c[1] && mx < c[1] + c[3] && my >= c[2] && my < c[2] + CELL_H) {
				return c[0];
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			for (int[] b : buttons) {
				if (mx >= b[1] && mx < b[1] + b[3] && my >= b[2] && my < b[2] + BUTTON_H) {
					if (b[4] != 0) {
						return true; // grayed out: the reason is under it
					}
					press(b[0]);
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	/** Presses button {@code id} ({@link TrainerMenu#CHALLENGE}, {@code TRADE} or {@code CLOSE}), as a click on it does. */
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
		return new int[]{leftPos + panelW / 2, topPos + panelH / 2};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
