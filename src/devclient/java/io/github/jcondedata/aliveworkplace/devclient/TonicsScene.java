package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * SCENE=tonics (ROADMAP 30.15): a miner, Dara, at her blast furnace and the player holding Miner's Brew; its tooltip in
 * the inventory; the player right-clicks her and she drinks it ("Dara drinks the Miner's Brew: 25% faster for 20
 * minutes."); then her status line: "25% faster (Miner's Brew, 19 min left)". Its checks: the tooltip names the jobs,
 * she drank it, and her pace and status line say so.
 */
final class TonicsScene {
	private static final BlockPos FURNACE = new BlockPos(0, -60, 0);
	private int tick;
	private volatile Villager miner;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(1000);
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_tonics_offer");
		}
		if (tick == 110) {
			mc.setScreen(new InventoryScreen(mc.player));
		}
		if (tick == 120) {
			// Over hotbar slot 0, where the brew is.
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			setMouse(mc, (left + 8 + 8) * scale, (top + 142 + 8) * scale);
		}
		if (tick == 140) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_tonics_tooltip");
			boolean says = Screen.getTooltipFromItem(mc, mc.player.getInventory().getItem(0)).stream()
				.map(Component::getString).anyMatch(line -> line.equals("For Miners, Sifters and Netherworkers"));
			Showcase.check(mc.screen instanceof InventoryScreen && says, "the brew's tooltip says what it does and for whom");
		}
		if (tick == 150) {
			mc.setScreen(null);
		}
		if (tick == 170) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Tonics.Offer offer = Tonics.offer(player, miner, player.getMainHandItem()); // the plain right-click's own path
				Showcase.check(offer.outcome() == Tonics.Outcome.DRUNK && player.getMainHandItem().getCount() == 1,
					"Dara drank the brew: " + (offer.message() == null ? offer.outcome() : offer.message().getString()));
			});
		}
		if (tick == 180) {
			ScreenshotHarness.shot(mc, "03_tonics_drunk");
		}
		if (tick == 240) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Miners.sendStatus(player, miner);
				Component pace = Pace.describe(miner);
				Showcase.check(pace != null && pace.getString().contains("25% faster") && pace.getString().contains("Miner's Brew, 19 min left"),
					"her status line: " + (pace == null ? null : pace.getString()));
			});
		}
		if (tick == 255) {
			ScreenshotHarness.shot(mc, "04_tonics_status");
		}
		if (tick == 280) {
			mc.stop();
		}
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		Villager v = JobScenes.worker(level, FURNACE, Blocks.BLAST_FURNACE, PoiTypes.ARMORER, ModVillagers.MINER);
		v.setVillagerData(v.getVillagerData().setLevel(2));
		v.setVillagerXp(10);
		v.setCustomName(Component.literal("Dara"));
		v.setNoAi(true); // she stays in the picture
		v.moveTo(FURNACE.getX() + 0.5, FURNACE.getY(), FURNACE.getZ() + 1.5, 180f, 0f);
		v.setYHeadRot(180f);
		miner = v;
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(ModItems.MINERS_BREW, 2));
		player.getInventory().selected = 0;
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, -60, 4.2, 180f, 12f);
		Showcase.check(miner != null && miner.getVillagerData().getProfession() == ModVillagers.MINER, "a miner was staged");
	}

	/** Puts the mouse at window pixel ({@code x}, {@code y}); the harness has no real mouse. */
	private static void setMouse(Minecraft mc, double x, double y) {
		try {
			var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
			var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
			xpos.setAccessible(true);
			ypos.setAccessible(true);
			xpos.setDouble(mc.mouseHandler, x);
			ypos.setDouble(mc.mouseHandler, y);
		} catch (ReflectiveOperationException e) {
			Showcase.check(false, "couldn't move the mouse: " + e);
		}
	}
}
