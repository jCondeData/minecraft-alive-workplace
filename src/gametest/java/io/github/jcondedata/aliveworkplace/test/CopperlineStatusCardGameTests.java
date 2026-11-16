package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.CardToasts;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout.Boxes;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * The villager status card, its toast and its tooltips in Copperline (ROADMAP 48.3, the VillagerCard board) through
 * {@link StatusCardLayout}, the boxes the card draws in and takes its clicks with, at GUI scale 2 (427x240, 480x270,
 * 640x360) and 3 (320x180), for a builder's card in English and with its labels three and five times as long, a card with
 * no lines, and one with far more lines than fit: the card inside the screen; the portrait, name, job and close X at its
 * top and apart; the buttons inside the card, apart, in order, under the reason box and the divider and above the hint,
 * even when the body has to give up room (B1103); the first usable button the one copper button; and the toast's packet
 * and new sentences.
 */
public class CopperlineStatusCardGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {640, 360}, {320, 180}};
	private static final String[] BUTTON_KEYS = {"screen.aliveworkplace.card.job.hall", "screen.aliveworkplace.card.job.chat"};

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static int[] need(int times) {
		int[] need = new int[BUTTON_KEYS.length];
		for (int i = 0; i < need.length; i++) {
			need[i] = Math.max(44, Component.translatable(BUTTON_KEYS[i]).getString().repeat(times).length() * 6 + 12);
		}
		return need;
	}

	private static void check(String at, int sw, int sh, Boxes b, int buttons, boolean hint, boolean refusal, List<String> problems) {
		int[] card = b.card();
		if (!within(card, new int[]{0, 0, sw, sh}) || card[2] < 150) {
			problems.add(at + "the card " + box(card) + " leaves the screen or is too narrow");
		}
		int[] inner = {card[0] + StatusCardLayout.PAD, card[1], card[2] - 2 * StatusCardLayout.PAD, card[3]};
		List<int[]> top = List.of(b.portrait(), b.name(), b.close());
		for (int[] p : top) {
			if (!within(p, card)) {
				problems.add(at + box(p) + " at the card's top leaves the card " + box(card));
			}
		}
		if (CopperLayout.overlap(b.portrait(), b.name()) || CopperLayout.overlap(b.name(), b.close()) || CopperLayout.overlap(b.tag(), b.portrait())
			|| b.name()[2] < 60) {
			problems.add(at + "the portrait, the name " + box(b.name()) + " and the close X overlap, or the name has no room");
		}
		if (b.subtitle()[1] < b.portrait()[1] + b.portrait()[3]) {
			problems.add(at + "the status line " + box(b.subtitle()) + " runs into the portrait");
		}
		int[][] bs = b.buttons();
		if (bs.length != buttons) {
			problems.add(at + bs.length + " button boxes for " + buttons + " buttons");
		}
		for (int i = 0; i < bs.length; i++) {
			if (!within(bs[i], inner) || bs[i][3] != StatusCardLayout.BUTTON_H) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " leaves the card " + box(inner));
			}
			if (bs[i][1] <= b.divider()[1] || (refusal && bs[i][1] < b.refusal()[1] + b.refusal()[3])) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " isn't under the divider and the reason box");
			}
			if (hint && bs[i][1] + bs[i][3] > b.hint()[1]) {
				problems.add(at + "button " + i + " runs into the hint " + box(b.hint()));
			}
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(bs[i], bs[j])) {
					problems.add(at + "buttons " + j + " and " + i + " overlap");
				}
				if (bs[j][1] == bs[i][1] && bs[j][0] > bs[i][0]) {
					problems.add(at + "buttons " + j + " and " + i + " are out of order");
				}
			}
		}
		if (refusal && (b.refusal()[3] <= 0 || !within(b.refusal(), inner) || b.refusal()[1] <= b.divider()[1])) {
			problems.add(at + "the reason box " + box(b.refusal()) + " is missing, leaves the card or sits over the divider");
		}
		if (hint && !within(b.hint(), inner)) {
			problems.add(at + "the hint " + box(b.hint()) + " leaves the card");
		}
		int[] body = b.body();
		if (body[3] > 0 && (body[1] + body[3] > b.divider()[1] || body[1] < b.subtitle()[1] + b.subtitle()[3])) {
			problems.add(at + "the body " + box(body) + " runs past the divider or into the status line");
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void statusCardFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3, 5}) {
				String at = s[0] + "x" + s[1] + " x" + times + ": ";
				// A builder's card: two status lines, a heading over five lines, a two-line reason, a hint.
				Boxes job = StatusCardLayout.of(s[0], s[1], 2, 12 + 5 * 18, 2, true, need(times));
				check(at, s[0], s[1], job, 2, true, true, problems);
				if (job.cut() && s[1] >= 240 && times == 1) { // (longer labels wrap the buttons onto more rows)
					problems.add(at + "a builder's card cut its lines on a screen 240 tall");
				}
				// No lines, no reason, no hint.
				check(at + "(empty) ", s[0], s[1], StatusCardLayout.of(s[0], s[1], 1, 10, 0, false, need(times)), 2, false, false, problems);
			}
			// Far more lines than fit: the body gives up its room, the buttons stay in the card.
			Boxes tall = StatusCardLayout.of(s[0], s[1], 3, 12 + 30 * 18, 3, true, need(1));
			check(s[0] + "x" + s[1] + " (30 lines) ", s[0], s[1], tall, 2, true, true, problems);
			if (!tall.cut()) {
				problems.add(s[0] + "x" + s[1] + ": thirty lines fit without being cut");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void statusCardHasOneCopperButton(GameTestHelper helper) {
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{true, true}, new boolean[]{false, false}) == 0, "the first usable button isn't copper");
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{false, true}, new boolean[]{false, false}) == 1,
			"a grayed-out first button took the copper");
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{true, true}, new boolean[]{true, false}) == 1, "Close took the copper");
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{false, false}, new boolean[]{false, false}) == -1,
			"a card with every button grayed out has a copper one");
		helper.assertTrue(StatusCardLayout.copper(new boolean[0], new boolean[0]) == -1, "a card with no buttons has a copper one");
		// No buttons at all: no boxes, and the card still lays out.
		Boxes none = StatusCardLayout.of(427, 240, 1, 40, 0, true, new int[0]);
		helper.assertTrue(none.buttons().length == 0 && none.card()[3] > 0, "a card without buttons didn't lay out");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void toastSaysWhoFinishedWhat(GameTestHelper helper) {
		CardToasts.Show built = CardToasts.built(Component.literal("Mira"), Component.literal("Stone House"), false);
		helper.assertTrue(built.title().getString().equals("Stone House") && built.line().getString().equals("Mira finished it."),
			"the toast reads \"" + built.title().getString() + " / " + built.line().getString() + "\"");
		CardToasts.Show down = CardToasts.built(Component.literal("Mira"), Component.literal("Stone House"), true);
		helper.assertTrue(down.line().getString().equals("Mira took it down."), "taken down reads \"" + down.line().getString() + "\"");
		helper.assertTrue(Component.translatable("screen.aliveworkplace.card.off").getString().equals("Not yet"),
			"the grayed-out button's tooltip word is missing from the lang file");
		// The packet goes and comes back whole.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		CardToasts.Show.CODEC.encode(buf, built);
		CardToasts.Show back = CardToasts.Show.CODEC.decode(buf);
		helper.assertTrue(back.icon().equals(CardToasts.BUILT_ICON) && back.title().getString().equals("Stone House")
			&& back.line().getString().equals("Mira finished it."), "the toast's packet lost something: " + back);
		buf.release();
		helper.succeed();
	}
}
