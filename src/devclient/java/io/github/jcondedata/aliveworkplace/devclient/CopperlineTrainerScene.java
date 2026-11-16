package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.client.TrainerScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.TrainerLayout;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=copperline_trainer (ROADMAP 48.2, with Cobblemon): the Trainer screen in Copperline, like the Trainer board. A
 * village Trainer the player hasn't beaten (so half the team shows as "???") and a party of three; at GUI scale 2 the
 * screen with the mouse on Challenge (the whisper line says what it does), then on Trade; at 320x180 (the GUI scale 3
 * rule) the compact layout.
 */
final class CopperlineTrainerScene {
	private static final BlockPos POST = new BlockPos(0, -60, 6);
	private int tick;
	private Villager trainer;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				level.setBlockAndUpdate(POST, ModBlocks.TRAINING_POST.defaultBlockState());
				Villager v = new Villager(EntityType.VILLAGER, level);
				v.setUUID(new java.util.UUID(0xc0ffe1L, 7));
				v.moveTo(0.5, -60, 5.5, 180, 0);
				level.addFreshEntity(v);
				Jobs.employ(level, v, POST, ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
				v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(4));
				trainer = v;
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				player.getInventory().clearContent();
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				for (String spec : new String[]{"pikachu level=34", "bulbasaur level=31", "charmander level=29"}) {
					party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(spec, " ", "=").create());
				}
				player.teleportTo(level, 0.5, -60, 1.5, 0, 10);
			});
		}
		if (tick == 70) {
			server.execute(() -> TrainerScreens.open(player(server), trainer));
		}
		if (tick == 90 && mc.screen instanceof TrainerScreen screen) {
			mc.getToasts().clear();
			point(mc, screen, TrainerMenu.CHALLENGE);
		}
		if (tick == 110) {
			Showcase.check(mc.screen instanceof TrainerScreen, "the Trainer screen opened");
			if (mc.screen instanceof TrainerScreen screen) {
				checkLayout(screen, "at GUI scale 2");
				Showcase.check(screen.cardCount() > 0, "the trainer's team arrived (" + screen.cardCount() + " cards)");
			}
			ScreenshotHarness.shot(mc, "01_trainer");
		}
		if (tick == 115 && mc.screen instanceof TrainerScreen screen) {
			point(mc, screen, TrainerMenu.TRADE);
		}
		if (tick == 130 && mc.screen instanceof TrainerScreen screen) {
			ScreenshotHarness.shot(mc, "02_trainer_trade");
			// 320x180, the GUI scale 3 rule's size (laid out at 320x180 and drawn at scale 2 in the frame's top-left).
			screen.resize(mc, 320, 180);
		}
		if (tick == 150 && mc.screen instanceof TrainerScreen screen) {
			Showcase.check(screen.boxes().compact() && screen.width == 320 && screen.height == 180,
				"at 320x180 (" + screen.width + "x" + screen.height + ") the screen takes the compact layout");
			checkLayout(screen, "at 320x180");
			ScreenshotHarness.shot(mc, "03_trainer_compact");
		}
		if (tick == 165) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	private static void point(Minecraft mc, TrainerScreen screen, int id) {
		int[] at = screen.centre(id);
		double scale = mc.getWindow().getGuiScale();
		ScreenshotHarness.setMouse(mc, at[0] * scale, at[1] * scale);
	}

	/** The rail, the stage and the three buttons inside the window and apart, Challenge the copper one. */
	private static void checkLayout(TrainerScreen screen, String what) {
		TrainerLayout.Boxes b = screen.boxes();
		int[] win = b.win();
		boolean ok = !CopperLayout.overlap(b.rail(), b.stage()) && b.buttons().length == 3 && TrainerLayout.copper(0);
		int[][] buttons = b.buttons();
		for (int i = 0; i < buttons.length; i++) {
			ok &= buttons[i][0] >= win[0] && buttons[i][0] + buttons[i][2] <= win[0] + win[2] && buttons[i][1] + buttons[i][3] <= win[1] + win[3];
			for (int j = 0; j < i; j++) {
				ok &= !CopperLayout.overlap(buttons[i], buttons[j]);
			}
		}
		Showcase.check(ok, what + ": the rail, the stage and Challenge, Trade and Close (Challenge copper) inside the window, apart");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
