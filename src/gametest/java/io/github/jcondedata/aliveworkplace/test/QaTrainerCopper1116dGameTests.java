package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.TrainerLayout;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-11-16, lane d), written from the spec of 48.2 (the Trainer screen in Copperline, like the Trainer board) and
 * Milestone 48's rule that nothing the screen did before may be lost, only how it looks: the player's own entry point
 * (an empty-hand click on a Trainer, without Cobblemon) and every button from it, including Close, an unknown button and
 * Trade grayed out for a trainer with nothing to sell; the layout on every common GUI scale 2 and 3 screen and at the
 * compact boundary (399x219 / 400x220) and on huge screens where the window stops growing; and every sentence the screen
 * reads out, through the lang file, in American English, without "(s)" plurals and with every argument filled.
 */
public class QaTrainerCopper1116dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** GUI scale 2 and 3 on common windows (1280x720, 1366x768, 1600x900, 1920x1080, 2560x1440, 960x540), and the compact edge. */
	private static final int[][] SCREENS = {{640, 360}, {683, 384}, {455, 256}, {533, 300}, {800, 450}, {640, 360}, {427, 240},
		{480, 270}, {320, 180}, {399, 219}, {400, 220}, {399, 240}, {427, 219}, {960, 540}, {1280, 720}};

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private Villager trainer(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		return trainer;
	}

	private static TrainerMenu open(GameTestHelper helper, ServerPlayer player, Villager trainer) {
		var result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, trainer, null);
		helper.assertTrue(result.consumesAction(), "the empty-hand click on the trainer did nothing: " + result);
		helper.assertTrue(player.containerMenu instanceof TrainerMenu, "no Trainer screen: " + player.containerMenu);
		return (TrainerMenu) player.containerMenu;
	}

	/**
	 * Close (the button and the lintel's X both send CLOSE) closes the screen and starts nothing; a button id the screen
	 * doesn't have is refused and leaves it open; the client's copy of the menu (no trainer) never acts on a button.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void closeClosesAndUnknownButtonsAreRefused(GameTestHelper helper) {
		Villager trainer = trainer(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(trainer.getX() + 1, trainer.getY(), trainer.getZ());
		TrainerMenu menu = open(helper, player, trainer);
		helper.assertFalse(menu.clickMenuButton(player, 3), "a fourth button was taken");
		helper.assertFalse(menu.clickMenuButton(player, -1), "button -1 was taken");
		helper.assertTrue(player.containerMenu == menu, "an unknown button closed the screen");
		TrainerMenu clientCopy = new TrainerMenu(menu.containerId, player.getInventory(), menu.view());
		for (int id : new int[]{TrainerMenu.CHALLENGE, TrainerMenu.TRADE, TrainerMenu.CLOSE}) {
			helper.assertFalse(clientCopy.clickMenuButton(player, id), "the client's copy acted on button " + id);
		}
		helper.assertTrue(player.containerMenu == menu, "the client's copy closed the screen");
		helper.assertTrue(menu.clickMenuButton(player, TrainerMenu.CLOSE), "Close wasn't taken");
		helper.assertFalse(player.containerMenu instanceof TrainerMenu, "Close left the screen open");
		helper.assertTrue(trainer.getTradingPlayer() == null, "Close opened the trades");
		helper.assertValueEqual(TrainerScreens.BUTTON_KEYS.length, 3, "the screen's buttons (Challenge, Trade, Close)");
		helper.succeed();
	}

	/**
	 * A trainer with nothing to sell: Trade is grayed out (the whisper says why), and a Trade click that reaches the server
	 * anyway opens no trades; a baby trainer's Trade is grayed out too. Challenge stays refused without Cobblemon, with a
	 * reason to show in the "Can't challenge now" box.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tradeIsGrayedOutWithNothingToSell(GameTestHelper helper) {
		Villager trainer = trainer(helper);
		trainer.getOffers().clear();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(trainer.getX() + 1, trainer.getY(), trainer.getZ());
		TrainerMenu menu = open(helper, player, trainer);
		TrainerView view = menu.view();
		helper.assertTrue(trainer.getOffers().isEmpty(), "setup: opening the screen stocked the trainer's trades");
		helper.assertFalse(view.canTrade(), "Trade isn't grayed out for a trainer with nothing to sell");
		helper.assertTrue(menu.clickMenuButton(player, TrainerMenu.TRADE), "Trade wasn't taken");
		helper.assertTrue(trainer.getTradingPlayer() == null, "Trade opened an empty trade screen");
		helper.assertFalse(view.canChallenge(), "Challenge is on without Cobblemon");
		helper.assertFalse(view.refusal().getString().isBlank(), "Challenge is off with no reason to show");
		player.closeContainer();
		trainer.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 4, 1, 0.05f));
		helper.assertTrue(TrainerScreens.view(player, trainer).canTrade(), "Trade is grayed out for a grown trainer with a trade");
		trainer.setAge(-24000);
		helper.assertFalse(TrainerScreens.view(player, trainer).canTrade(), "Trade isn't grayed out for a baby trainer");
		helper.succeed();
	}

	/**
	 * GUI scale 2 and 3 on every common window, at the compact boundary and on screens where the window stops growing: the
	 * window on screen, the rail, the stage and the three buttons inside it and apart, clear of the whisper line and the close
	 * X, each button clickable at its middle and nowhere else's; a team of one to six cards inside the stage's content, each
	 * hit by the mouse at its middle alone.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theLayoutHoldsOnEveryCommonScreen(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] need = new int[TrainerScreens.BUTTON_KEYS.length];
		for (int i = 0; i < need.length; i++) {
			need[i] = Math.max(44, Component.translatable(TrainerScreens.BUTTON_KEYS[i]).getString().length() * 6 + 12);
		}
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			TrainerLayout.Boxes b = TrainerLayout.of(s[0], s[1], "Trainer Leader Bartholomew".length() * 6, need);
			int[] win = b.win();
			if (!within(win, new int[]{0, 0, s[0], s[1]})) {
				problems.add(at + "the window " + box(win) + " leaves the screen");
			}
			if (b.compact() != (s[0] < 400 || s[1] < 220)) {
				problems.add(at + "compact is " + b.compact());
			}
			if (b.plaque()[1] < 0 || b.plaque()[2] > win[2] / 2) {
				problems.add(at + "the plaque " + box(b.plaque()) + " leaves the screen or is over half the window");
			}
			for (int[] p : List.of(b.rail(), b.stage(), b.content(), b.whisper())) {
				if (!within(p, win)) {
					problems.add(at + box(p) + " leaves the window " + box(win));
				}
			}
			int[][] buttons = b.buttons();
			for (int i = 0; i < buttons.length; i++) {
				int[] x = buttons[i];
				if (!within(x, b.rail()) || CopperLayout.overlap(x, b.whisper()) || CopperLayout.overlap(x, b.close()) || CopperLayout.overlap(x, b.stage())) {
					problems.add(at + "button " + i + " " + box(x) + " leaves the rail or runs under the whisper, the X or the stage");
				}
				if (x[2] < need[i]) {
					problems.add(at + "button " + i + " is cut to " + x[2] + " of " + need[i]);
				}
				if (CopperLayout.at(buttons, x[0] + x[2] / 2.0, x[1] + x[3] / 2.0) != i) {
					problems.add(at + "button " + i + "'s middle isn't button " + i);
				}
				if (CopperLayout.inside(b.close(), x[0] + x[2] / 2.0, x[1] + x[3] / 2.0)) {
					problems.add(at + "button " + i + "'s middle is the close X");
				}
			}
			int[] c = b.content();
			for (int n = 1; n <= 6; n++) {
				int[][] cards = TrainerLayout.teamCards(c[0], c[1], c[2] - 2, n);
				for (int i = 0; i < n; i++) {
					int[] k = cards[i];
					if (k[0] < c[0] || k[0] + k[2] > c[0] + c[2] - 2 || k[2] < TrainerLayout.CARD_MIN_W) {
						problems.add(at + n + " cards: card " + i + " " + box(k) + " leaves the content " + box(c) + " or is under " + TrainerLayout.CARD_MIN_W);
					}
					if (CopperLayout.at(cards, k[0] + k[2] / 2.0, k[1] + k[3] / 2.0) != i) {
						problems.add(at + n + " cards: card " + i + "'s middle isn't card " + i);
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every sentence the Trainer screen reads out (its screen keys, the refusal without Cobblemon, the whisper line's key
	 * hint): in the lang file, American English, no "(s)" plurals, every %s filled when rendered with real arguments, the
	 * whispers that are sentences ending with a period, and the old screen's sentences (the scaled card, the hidden team,
	 * the empty party) still there.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everySentenceIsInTheLangFileInAmericanEnglish(GameTestHelper helper) {
		JsonObject lang;
		try (var in = QaTrainerCopper1116dGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		Pattern british = Pattern.compile("(?i)\\b(colour|centre|grey|cancelled|cancelling|defence|armour|favourite|honour|behaviour|realis|organis|travelled|levelled|jeweller|metre)\\w*");
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
			String key = e.getKey();
			if (!key.startsWith("screen.aliveworkplace.trainer.") && !key.startsWith("screen.aliveworkplace.copperline.key.")
				&& !key.startsWith("message.aliveworkplace.trainer.no_cobblemon")) {
				continue;
			}
			checked++;
			String raw = e.getValue().getAsString();
			Matcher m = british.matcher(raw);
			while (m.find()) {
				problems.add(key + " says \"" + m.group() + "\" (British)");
			}
			if (raw.contains("(s)")) {
				problems.add(key + " has a \"(s)\" plural");
			}
			if (raw.isBlank() || !raw.equals(raw.strip()) || raw.contains("  ")) {
				problems.add(key + " is blank or has stray spaces: \"" + raw + "\"");
			}
			String shown = Component.translatable(key, "Hale", "42", "Ready").getString();
			if (shown.contains("%")) {
				problems.add(key + " shows a raw %: " + shown);
			}
		}
		helper.assertTrue(checked >= 40, "only " + checked + " Trainer screen sentences found");
		for (String key : new String[]{"whisper.challenge", "whisper.trade", "whisper.trade_off", "whisper.close", "whisper.unseen",
			"team.hidden", "reward", "reward.paid", "party.none"}) {
			String full = "screen.aliveworkplace.trainer." + key;
			String text = Component.translatable(full, "Hale").getString();
			if (text.equals(full) || !text.endsWith(".")) {
				problems.add(full + " isn't a sentence ending with a period: " + text);
			}
		}
		// What the old screen said must still be said: the card brought down to the level cap, the hidden team, the empty party.
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.whisper.card_scaled", "Pikachu", 40).getString(),
			"Pikachu · Lv 40 · brought down to your level cap", "a capped card's whisper");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.whisper.trade_off", "Hale").getString(),
			"Hale can't trade right now.", "Trade's grayed-out whisper");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.whisper.close").getString(),
			"Close this screen. Esc does the same.", "Close's whisper");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.copperline.key.esc").getString(), "Esc", "the whisper line's key");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
