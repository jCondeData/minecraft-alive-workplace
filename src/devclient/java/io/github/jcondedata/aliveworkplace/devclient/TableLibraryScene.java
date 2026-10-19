package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=table_library (ROADMAP 36.13): the Blueprint Table as a library. The player carries four bought blueprints (two
 * Starter Cottages, a Market Stall and an Inn); the table's Stored tab starts empty, Store All puts them on its shelf,
 * listed with how many of each, and Take One Out hands a Starter Cottage back, which ends up in the player's hand.
 */
final class TableLibraryScene {
	private static final BlockPos TABLE = new BlockPos(0, -60, -3);
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();
	private int tick;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.setDayTime(6000);
				level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				player.getInventory().clearContent();
				player.getInventory().selected = 0;
				player.getInventory().setItem(0, BlueprintItem.create(COTTAGE, null));
				player.getInventory().setItem(1, BlueprintItem.create(COTTAGE, null));
				player.getInventory().setItem(2, BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), null));
				player.getInventory().setItem(3, BlueprintItem.create(AliveWorkplace.id("inn"), null));
				player.teleportTo(level, 0.5, -60, 0.5, 180, 30);
			});
		}
		if (tick == 80) {
			server.execute(() -> TableServer.open(player(server), TABLE));
		}
		if (tick == 110 && mc.screen instanceof BlueprintTableScreen screen) {
			screen.showStored();
		}
		if (tick == 130) {
			boolean open = mc.screen instanceof BlueprintTableScreen s && s.storedOpen();
			Showcase.check(open, "the Blueprint Table's Stored tab opened");
			Showcase.check(mc.screen instanceof BlueprintTableScreen s && s.storedCount() == 0, "a new table has nothing stored");
			ScreenshotHarness.shot(mc, "01_stored_empty");
			if (mc.screen instanceof BlueprintTableScreen screen) {
				screen.store(true); // the Store All button
			}
		}
		if (tick == 190 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.storedCount() == 4 && screen.storedKinds() == 3,
				"Store All put the four blueprints carried in the table, listed as " + screen.storedKinds() + " kinds (" + screen.storedCount() + " in all)");
			Showcase.check(screen.selectedStoredName().contains("Starter Cottage") && screen.selectedStoredCount() == 2,
				"the Starter Cottage is listed with its count (" + screen.selectedStoredName() + " ×" + screen.selectedStoredCount() + ")");
			ScreenshotHarness.shot(mc, "02_stored");
			screen.takeOut(); // the Take One Out button, on the picked Starter Cottage
		}
		if (tick == 240 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.storedCount() == 3 && screen.selectedStoredCount() == 1,
				"Take One Out counted the cottage down to 1 (" + screen.storedCount() + " stored in all)");
			mc.options.hideGui = false;
			ScreenshotHarness.shot(mc, "03_taken_out");
		}
		if (tick == 250) {
			mc.setScreen(null);
		}
		if (tick == 280) {
			boolean inHand = BlueprintItem.data(mc.player.getMainHandItem()).map(d -> d.structure().equals(COTTAGE)).orElse(false);
			Showcase.check(inHand, "the Starter Cottage taken out is back in the player's hand");
			ScreenshotHarness.shot(mc, "04_in_hand");
		}
		if (tick == 300) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
