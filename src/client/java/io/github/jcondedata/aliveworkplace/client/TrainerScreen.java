package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.TrainerLayout;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Trainer screen (ROADMAP 43.1) in Copperline (48.2, {@code docs/design/copperline/boards/Trainer.png}): the plaque
 * with the trainer's title, the lintel with their rank and the close X; on the rail their portrait (the real model) with
 * their name and title, the player's record against them and today's line (a glyph, a word and a color), and Challenge
 * (the one copper button), Trade and Close at its foot; on the stage the reward, the team as cards (the real Pokémon
 * models; the ones not seen yet as "???"), the player's party with a status each and the rules, scrolled when they don't
 * fit; the whisper line says what the thing under the mouse is or does. Challenge is grayed out with the reason in a
 * refusal box at the stage's top when the trainer can't battle now. Everything shown comes from the server
 * ({@link TrainerView}); the buttons are menu-button clicks. The boxes come from {@link TrainerLayout}.
 */
public class TrainerScreen extends AbstractContainerScreen<TrainerMenu> {
	private static final int[] IDS = {TrainerMenu.CHALLENGE, TrainerMenu.TRADE, TrainerMenu.CLOSE};
	private static final int PARTY_ROW = 12;

	private TrainerLayout.Boxes boxes;
	private Component[] labels;
	/** Each team card's box this frame (in screen pixels, after scrolling), by the team's order. */
	private final List<int[]> cards = new ArrayList<>();
	private int scroll;
	private int contentH;
	private Component whisper = Component.empty();

	public TrainerScreen(TrainerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	private TrainerView view() {
		return menu.view();
	}

	@Override
	protected void init() {
		labels = new Component[TrainerScreens.BUTTON_KEYS.length];
		int[] need = new int[labels.length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = Component.translatable(TrainerScreens.BUTTON_KEYS[i]);
			need[i] = TableStyle.buttonWidth(font, labels[i]);
		}
		boxes = TrainerLayout.of(width, height, font.width(title), need);
		imageWidth = boxes.win()[2];
		imageHeight = boxes.win()[3];
		super.init();
	}

	/** The boxes the screen draws in and takes its clicks with (the screenshot scene checks them). */
	public TrainerLayout.Boxes boxes() {
		return boxes;
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
		whisper = Component.empty();
		int[] win = boxes.win();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, win);
		CopperStyle.lintel(g, font, win, plaque, view().rank(), overClose);
		if (overClose) {
			whisper = Component.translatable("screen.aliveworkplace.trainer.whisper.close");
		} else if (CopperLayout.inside(boxes.lintelText(), mouseX, mouseY)) {
			whisper = view().rank();
		}
		renderRail(g, mouseX, mouseY);
		renderStage(g, mouseX, mouseY);
		renderButtons(g, mouseX, mouseY);
		int[] w = boxes.whisper();
		Component text = whisper.getString().isEmpty() ? Component.translatable("screen.aliveworkplace.trainer.hint") : whisper;
		CopperStyle.whisper(g, font, text, w[0] + 2, w[1] + 3, w[2] - 4, boxes.compact());
	}

	/** The rail: the portrait with the name and title beside it, the record and today under them. */
	private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] r = boxes.rail();
		CopperStyle.panel(g, r[0], r[1], r[2], r[3]);
		int[] p = boxes.portrait();
		CopperStyle.well(g, p[0], p[1], p[2], p[3]);
		Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(view().entityId());
		if (entity instanceof LivingEntity living) {
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, p[0] + 1, p[1] + 1, p[0] + p[2] - 1, p[1] + p[3] - 1, p[3] * 4 / 9,
				0.0625f, mouseX, mouseY, living);
		}
		int[] n = boxes.nameBox();
		CopperStyle.label(g, font, view().name().getString(), n[0], n[1] + 2, n[2], CopperStyle.QUARTZ);
		g.drawString(font, CopperStyle.trim(font, view().title().getString(), n[2]), n[0], n[1] + 13, CopperStyle.TUFF, false);
		if (CopperLayout.inside(p, mouseX, mouseY) || CopperLayout.inside(n, mouseX, mouseY)) {
			whisper = Component.translatable("screen.aliveworkplace.trainer.whisper.trainer", view().name(), view().title());
		}

		int[] t = boxes.railText();
		if (t[3] <= 0) {
			return;
		}
		g.enableScissor(t[0], t[1], t[0] + t[2], t[1] + t[3]);
		int y = t[1];
		int lines = boxes.compact() ? 1 : 2;
		int top = y;
		y = field(g, Component.translatable("screen.aliveworkplace.trainer.record"), view().record(), CopperStyle.QUARTZ, null, t[0], y, t[2], lines);
		if (mouseY >= top && mouseY < y && CopperLayout.inside(t, mouseX, mouseY)) {
			whisper = Component.translatable("screen.aliveworkplace.trainer.whisper.record", view().name(), view().record());
		}
		top = y;
		CopperStyle.Status today = status(view().todayColor());
		y = field(g, Component.translatable("screen.aliveworkplace.trainer.today"), view().today(), today.color, today, t[0], y, t[2], lines);
		if (mouseY >= top && mouseY < y && CopperLayout.inside(t, mouseX, mouseY)) {
			whisper = view().today();
		}
		g.disableScissor();
	}

	/** A Tuff heading and its value under it (a status's glyph before it), at most {@code lines} lines, cut with "..."; returns where the next goes. */
	private int field(GuiGraphics g, Component heading, Component value, int color, CopperStyle.Status status, int x, int y, int w,
		int lines) {
		g.drawString(font, CopperStyle.trim(font, heading.getString(), w), x, y, CopperStyle.TUFF, false);
		y += 10;
		int vx = x;
		if (status != null) {
			g.blitSprite(status.glyph, x, y, 8, 8);
			vx += 10;
		}
		y = wrapped(g, value, color, vx, y, x + w - vx, lines);
		return y + 4;
	}

	/** {@code text} wrapped to {@code w}, at most {@code lines} lines, the last cut with "..." if more was left; returns where the next goes. */
	private int wrapped(GuiGraphics g, Component text, int color, int x, int y, int w, int lines) {
		List<FormattedCharSequence> split = font.split(text, w);
		if (split.size() <= lines) {
			for (FormattedCharSequence line : split) {
				g.drawString(font, line, x, y, color, false);
				y += 10;
			}
			return y;
		}
		// Cut: the lines that fit whole, then the rest of the text on the last line ending with "...".
		String all = text.getString();
		int used = 0;
		for (int i = 0; i < lines - 1; i++) {
			String line = font.plainSubstrByWidth(all.substring(used), w);
			int cut = line.lastIndexOf(' ');
			line = cut > 0 && used + line.length() < all.length() ? line.substring(0, cut + 1) : line;
			g.drawString(font, line.stripTrailing(), x, y, color, false);
			used += line.length();
			y += 10;
		}
		g.drawString(font, CopperStyle.trim(font, all.substring(Math.min(used, all.length())), w), x, y, color, false);
		return y + 10;
	}

	/** The stage: the refusal (when Challenge is off), the reward, the team, the party and the rules, scrolled to {@link #scroll}. */
	private void renderStage(GuiGraphics g, int mouseX, int mouseY) {
		int[] s = boxes.stage();
		int[] c = boxes.content();
		g.enableScissor(s[0], s[1], s[0] + s[2], s[1] + s[3]);
		int x = c[0];
		int w = c[2] - 2;
		int y0 = c[1] - scroll;
		int y = y0;
		boolean inStage = CopperLayout.inside(c, mouseX, mouseY);
		if (!view().canChallenge()) {
			int top = y;
			y += CopperStyle.refusal(g, font, Component.translatable("screen.aliveworkplace.trainer.cant_challenge"), view().refusal(), x, y, w) + 6;
			if (inStage && mouseY >= top && mouseY < y) {
				whisper = view().refusal();
			}
		}
		CopperStyle.label(g, font, Component.translatable("screen.aliveworkplace.trainer.challenge_heading", view().name()).getString(), x, y,
			w, CopperStyle.QUARTZ);
		y += 11;
		int top = y;
		g.blitSprite(CopperStyle.glyph("coin"), x, y, 8, 8);
		y = wrapped(g, view().reward(), CopperStyle.QUARTZ, x + 10, y, w - 10, 3) + 6;
		if (inStage && mouseY >= top - 11 && mouseY < y) {
			whisper = view().reward();
		}

		cards.clear();
		List<TrainerView.TeamSlot> team = view().team();
		if (!team.isEmpty()) {
			g.drawString(font, CopperStyle.trim(font, view().teamHeading().getString().toUpperCase(java.util.Locale.ROOT), w), x, y, CopperStyle.TUFF, false);
			CopperStyle.headerRow(g, x, y - 1, w);
			y += 13;
			int[][] boxesOf = TrainerLayout.teamCards(x, y, w, team.size());
			int hovered = -1;
			for (int i = 0; i < team.size(); i++) {
				cards.add(boxesOf[i]);
				if (inStage && CopperLayout.inside(boxesOf[i], mouseX, mouseY)) {
					hovered = i;
				}
			}
			for (int i = 0; i < team.size(); i++) {
				card(g, team.get(i), boxesOf[i], i == hovered);
			}
			if (hovered >= 0) {
				TrainerView.TeamSlot slot = team.get(hovered);
				whisper = slot.hidden() ? Component.translatable("screen.aliveworkplace.trainer.whisper.unseen", view().name())
					: Component.translatable(slot.scaled() ? "screen.aliveworkplace.trainer.whisper.card_scaled" : "screen.aliveworkplace.trainer.whisper.card",
						slot.icon().getHoverName(), slot.level());
			}
			y += TrainerLayout.teamHeight(w, team.size()) + 4;
			if (!view().teamHint().getString().isEmpty()) {
				CopperStyle.Status info = CopperStyle.Status.INFO;
				g.blitSprite(info.glyph, x, y, 8, 8);
				y = wrapped(g, view().teamHint(), CopperStyle.TUFF, x + 10, y, w - 10, 2);
			}
			y += 6;
		}

		if (!view().party().isEmpty() || !view().partyNote().getString().isEmpty()) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.trainer.party").getString().toUpperCase(java.util.Locale.ROOT), w),
				x, y, CopperStyle.TUFF, false);
			CopperStyle.headerRow(g, x, y - 1, w);
			y += 13;
			if (view().party().isEmpty()) {
				g.blitSprite(CopperStyle.Status.INFO.glyph, x, y, 8, 8);
				y = wrapped(g, view().partyNote(), CopperStyle.TUFF, x + 10, y, w - 10, 2) + 2;
			}
			int[] cols = TrainerLayout.partyColumns(w);
			int levelX = x + 4 + cols[0];
			int stateX = x + 4 + cols[1];
			for (int i = 0; i < view().party().size(); i++) {
				TrainerView.PartyLine line = view().party().get(i);
				boolean over = inStage && mouseY >= y && mouseY < y + PARTY_ROW && mouseX >= x && mouseX < x + w;
				CopperStyle.rowBand(g, x, y, w, PARTY_ROW, i % 2 == 1, over, false);
				int ty = y + 2;
				g.drawString(font, CopperStyle.trim(font, line.name().getString(), levelX - x - 10), x + 5, ty, CopperStyle.QUARTZ, false);
				g.drawString(font, Component.translatable("screen.aliveworkplace.trainer.level", line.level()), levelX, ty, CopperStyle.TUFF, false);
				CopperStyle.Status st = status(line.color());
				g.blitSprite(st.glyph, stateX, ty, 8, 8);
				g.drawString(font, CopperStyle.trim(font, line.state().getString(), x + w - stateX - 12), stateX + 10, ty, st.color, false);
				if (over) {
					whisper = Component.translatable("screen.aliveworkplace.trainer.whisper.party", line.name(), line.level(), line.state());
				}
				y += PARTY_ROW;
			}
			y += 6;
		}

		top = y;
		g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.trainer.rules_heading").getString(), w), x, y,
			CopperStyle.TUFF, false);
		y += 10;
		y = wrapped(g, view().rules(), CopperStyle.QUARTZ, x, y, w, 2);
		if (inStage && mouseY >= top && mouseY < y) {
			whisper = Component.translatable("screen.aliveworkplace.trainer.whisper.rules", view().rules());
		}
		g.disableScissor();
		contentH = y - y0;
		int max = Math.max(0, contentH - c[3]);
		if (scroll > max) {
			scroll = max;
		}
		CopperStyle.scrollbar(g, s[0] + s[2] - 4, s[1], s[3], scroll, max, contentH, inStage && max > 0);
	}

	/** One of the trainer's Pokémon: a card with its model, its name and level, or "???" for one not seen yet. */
	private void card(GuiGraphics g, TrainerView.TeamSlot slot, int[] b, boolean over) {
		CopperStyle.panel(g, b[0], b[1], b[2], b[3]);
		if (over) {
			g.renderOutline(b[0], b[1], b[2], b[3], CopperStyle.LANTERN);
		}
		// The Pokémon is the card's subject (the board draws it big): a sunken well as tall as the card allows, the same
		// well a Pokémon not seen yet gets, so a seen one never reads weaker than its "???" (48.2 critique).
		int[] well = TrainerLayout.cardWell(b);
		int ws = well[2];
		int ix = well[0];
		int iy = well[1];
		int[] text = TrainerLayout.cardText(b);
		int tx = text[0];
		int tw = text[2];
		CopperStyle.well(g, ix, iy, ws, ws);
		if (slot.hidden()) {
			g.drawString(font, "?", ix + (ws - font.width("?")) / 2, iy + (ws - 8) / 2, CopperStyle.SLATE, false);
			g.drawString(font, CopperStyle.trim(font, "???", tw), tx, b[1] + 6, CopperStyle.OFF_TEXT, false);
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.trainer.team.unseen").getString(), tw), tx,
				b[1] + 17, CopperStyle.OFF_TEXT, false);
			return;
		}
		g.pose().pushPose();
		g.pose().translate(ix + 1, iy + 1, 0);
		g.pose().scale((ws - 2) / 16f, (ws - 2) / 16f, 1f);
		g.renderItem(slot.icon(), 0, 0);
		g.pose().popPose();
		CopperStyle.label(g, font, slot.icon().getHoverName().getString(), tx, b[1] + 6, tw, CopperStyle.QUARTZ);
		String level = Component.translatable("screen.aliveworkplace.trainer.level", slot.level()).getString();
		if (slot.scaled()) {
			g.blitSprite(CopperStyle.Status.WARNING.glyph, tx, b[1] + 17, 8, 8);
			g.drawString(font, CopperStyle.trim(font, level, tw - TrainerLayout.LEVEL_GLYPH), tx + TrainerLayout.LEVEL_GLYPH, b[1] + 17, CopperStyle.LANTERN, false);
		} else {
			g.drawString(font, CopperStyle.trim(font, level, tw), tx, b[1] + 17, CopperStyle.TUFF, false);
		}
	}

	/** Challenge (copper, or off with its reason), Trade and Close along the rail's foot. */
	private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
		int[][] b = boxes.buttons();
		for (int i = 0; i < b.length; i++) {
			boolean off = off(i);
			boolean over = CopperLayout.inside(b[i], mouseX, mouseY);
			CopperStyle.Kind kind = TrainerLayout.copper(i) ? CopperStyle.Kind.COPPER : CopperStyle.Kind.NEUTRAL;
			CopperStyle.button(g, font, labels[i], null, b[i][0], b[i][1], b[i][2], b[i][3], kind, over, false, off);
			if (over) {
				whisper = switch (IDS[i]) {
					case TrainerMenu.CHALLENGE -> off ? view().refusal() : Component.translatable("screen.aliveworkplace.trainer.whisper.challenge", view().name());
					case TrainerMenu.TRADE -> Component.translatable(off ? "screen.aliveworkplace.trainer.whisper.trade_off"
						: "screen.aliveworkplace.trainer.whisper.trade", view().name());
					default -> Component.translatable("screen.aliveworkplace.trainer.whisper.close");
				};
			}
		}
	}

	private boolean off(int i) {
		return switch (IDS[i]) {
			case TrainerMenu.CHALLENGE -> !view().canChallenge();
			case TrainerMenu.TRADE -> !view().canTrade();
			default -> false;
		};
	}

	/** A server color as a Copperline status (its glyph and color): green good, red bad, orange or gold a warning, else info. */
	static CopperStyle.Status status(int color) {
		if (color == TrainerView.GREEN) {
			return CopperStyle.Status.GOOD;
		}
		if (color == TrainerView.RED) {
			return CopperStyle.Status.BAD;
		}
		if (color == TrainerView.ORANGE) {
			return CopperStyle.Status.WARNING;
		}
		return CopperStyle.Status.INFO;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (CopperLayout.inside(boxes.stage(), mx, my)) {
			scroll = Mth.clamp(scroll - (int) (scrollY * 12), 0, Math.max(0, contentH - boxes.content()[3]));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			if (CopperLayout.inside(boxes.close(), mx, my)) {
				press(TrainerMenu.CLOSE);
				return true;
			}
			int i = CopperLayout.at(boxes.buttons(), mx, my);
			if (i >= 0) {
				if (!off(i)) {
					press(IDS[i]);
				}
				return true; // grayed out: the reason is in the refusal box and the whisper line
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
		int[][] b = boxes.buttons();
		for (int i = 0; i < IDS.length && i < b.length; i++) {
			if (IDS[i] == id) {
				return new int[]{b[i][0] + b[i][2] / 2, b[i][1] + b[i][3] / 2};
			}
		}
		int[] win = boxes.win();
		return new int[]{win[0] + win[2] / 2, win[1] + win[3] / 2};
	}

	/** How many team cards were drawn last frame (the scene checks the team arrived). */
	public int cardCount() {
		return cards.size();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
