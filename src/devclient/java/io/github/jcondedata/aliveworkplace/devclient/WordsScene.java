package io.github.jcondedata.aliveworkplace.devclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * SCENE=words (ROADMAP 24.5): every message that was new or reworded for 1.0, shown in the game's own chat with
 * example values, a page at a time, so the owner reads each one as a player would. Counted messages show both their
 * one and many forms. A message whose key is missing, or that keeps a raw placeholder, fails the scene.
 */
final class WordsScene {
	/** A message and the example values that fill it. */
	private record Line(String key, Object... args) {
	}

	private static final List<List<Line>> PAGES = List.of(
		List.of(
			new Line("message.aliveworkplace.bench.info.one", 1, 6),
			new Line("message.aliveworkplace.bench.info", 3, 6),
			new Line("message.aliveworkplace.status.skipped.one", 1),
			new Line("message.aliveworkplace.status.skipped", 4),
			new Line("message.aliveworkplace.entities_left.one", 1),
			new Line("message.aliveworkplace.entities_left", 2),
			new Line("command.aliveworkplace.blueprints.one", 1),
			new Line("command.aliveworkplace.blueprints", 38)),
		List.of(
			new Line("message.aliveworkplace.import.done_with_unknown.one", "tavern", 11, 9, 13, 1, "create:shaft"),
			new Line("message.aliveworkplace.import.done_with_unknown", "tavern", 11, 9, 13, 5,
				"create:shaft, create:cogwheel, supplementaries:sconce and 2 more"),
			new Line("message.aliveworkplace.mail.collected.one", 1, "Bramble"),
			new Line("message.aliveworkplace.mail.collected", 3, "Bramble"),
			new Line("message.aliveworkplace.mail.waiting_at_desk.one", 1),
			new Line("message.aliveworkplace.mail.waiting_at_desk", 2)),
		List.of(
			new Line("message.aliveworkplace.festival.too_soon.one", 1),
			new Line("message.aliveworkplace.festival.too_soon", 4),
			new Line("screen.aliveworkplace.hall.festival_in.one", 1),
			new Line("screen.aliveworkplace.hall.festival_in", 5),
			new Line("message.aliveworkplace.rally.raised.one", 1),
			new Line("message.aliveworkplace.rally.raised", 3),
			new Line("screen.aliveworkplace.calendar.starts_tomorrow"),
			new Line("message.aliveworkplace.shop.items", 28, "Spruce Planks"),
			new Line("message.aliveworkplace.shop.owner_sale", "Steve", 28, "Spruce Planks", "4 Emeralds"),
			new Line("message.aliveworkplace.shop.log.line", 12, "Steve", 28, "Spruce Planks", "4 Emeralds")),
		List.of(
			new Line("message.aliveworkplace.shop.placed"),
			new Line("message.aliveworkplace.travel.placed", "Thornholm"),
			new Line("message.aliveworkplace.travel.found", "Ashford", 2),
			new Line("message.aliveworkplace.travel.known", "Ashford", 2),
			new Line("message.aliveworkplace.travel.nowhere"),
			new Line("message.aliveworkplace.travel.gone", "Ashford"),
			new Line("message.aliveworkplace.travel.not_at_post", 16),
			new Line("message.aliveworkplace.travel.menu_help"),
			new Line("message.aliveworkplace.travel.bought", "Ashford")),
		List.of(
			new Line("tooltip.aliveworkplace.ticket.where", 120, -340),
			new Line("tooltip.aliveworkplace.ticket.how"),
			new Line("message.aliveworkplace.quarry.reset"),
			new Line("message.aliveworkplace.field.reset"),
			new Line("message.aliveworkplace.route.reset"),
			new Line("message.aliveworkplace.route.incomplete"),
			new Line("message.aliveworkplace.route.full", "Bramble", 3),
			new Line("message.aliveworkplace.fisher.title", 12),
			new Line("message.aliveworkplace.guard.state.training"),
			new Line("aliveworkplace.config.fisherRadius"),
			new Line("aliveworkplace.config.fisherRadius.tooltip")));

	private int tick;
	private final List<String> problems = new ArrayList<>();

	void tick(Minecraft mc) {
		tick++;
		if (tick == 1) {
			mc.options.hideGui = false;
		}
		int start = 40;
		int page = (tick - start) / 30;
		int at = (tick - start) % 30;
		if (tick < start) {
			return;
		}
		if (page < PAGES.size()) {
			if (at == 0) {
				mc.setScreen(null);
				mc.gui.getChat().clearMessages(false);
				for (Line line : PAGES.get(page)) {
					if (!I18n.exists(line.key())) {
						problems.add("missing " + line.key());
					}
					Component text = Component.translatable(line.key(), line.args());
					String read = text.getString();
					if (read.contains("%") || read.contains("aliveworkplace.")) {
						problems.add(line.key() + " reads '" + read + "'");
					}
					mc.gui.getChat().addMessage(text);
				}
				mc.setScreen(new ChatScreen(""));
			}
			if (at == 20) {
				ScreenshotHarness.shot(mc, String.format("%02d_words", page + 1));
			}
			return;
		}
		if (at == 0) {
			int lines = PAGES.stream().mapToInt(List::size).sum();
			Showcase.check(problems.isEmpty(), "all " + lines + " new or reworded messages read without a raw key or placeholder"
				+ (problems.isEmpty() ? "" : ": " + String.join("; ", problems)));
			mc.setScreen(null);
			mc.stop();
		}
	}
}
