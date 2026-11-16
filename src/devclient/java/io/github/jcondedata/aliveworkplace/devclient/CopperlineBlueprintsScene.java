package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=copperline_blueprints (ROADMAP 48.1): the Blueprint Table in Copperline, like the Blueprint Book board. At GUI
 * scale 2: the library (the book) with the Starter Cottage picked, its 3D preview, size and materials (the planks carried,
 * the rest framed in Redstone with the word under them), then in a style from the rail, then the Stored tab with two
 * blueprints on the shelf; at 320x180 (the GUI scale 3 rule) the same book with the rail as an icon grid, and the Stored tab.
 */
final class CopperlineBlueprintsScene {
	private static final BlockPos TABLE = new BlockPos(0, -60, -3);
	private int tick;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
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
				player.getInventory().setItem(0, BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE.id(), null));
				player.getInventory().setItem(1, BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), null));
				player.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 64));
				player.getInventory().setItem(3, new ItemStack(Items.OAK_PLANKS, 64));
				player.getInventory().setItem(4, new ItemStack(Items.COBBLESTONE, 64));
				player.teleportTo(level, 0.5, -60, 0.5, 180, 30);
			});
		}
		if (tick == 60) {
			server.execute(() -> TableServer.open(player(server), TABLE));
		}
		if (tick == 80 && mc.screen instanceof BlueprintTableScreen screen) {
			mc.getToasts().clear(); // the world's first recipe and advancement toasts, not the table's
			screen.select(StarterBlueprints.STARTER_COTTAGE.id());
		}
		if (tick == 130) {
			Showcase.check(mc.screen instanceof BlueprintTableScreen, "the Blueprint Table opened");
			if (mc.screen instanceof BlueprintTableScreen screen) {
				checkLayout(screen, "the book at GUI scale 2");
				Showcase.check(screen.materialCount() > 0 && screen.previewCells() > 0, "the Starter Cottage's preview and materials arrived ("
					+ screen.materialCount() + " materials)");
			}
			ScreenshotHarness.shot(mc, "01_book");
		}
		if (tick == 135 && mc.screen instanceof BlueprintTableScreen screen) {
			screen.chooseStyle("sandstone");
		}
		if (tick == 175 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.style().equals("sandstone"), "the style rail chose Sandstone (" + screen.style() + ")");
			ScreenshotHarness.shot(mc, "02_book_style");
			screen.store(true); // Store All: the two blueprints carried go on the shelf
		}
		if (tick == 215 && mc.screen instanceof BlueprintTableScreen screen) {
			screen.showStored();
		}
		if (tick == 235 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.storedOpen() && screen.storedCount() == 2, "Store All put the two blueprints on the shelf (" + screen.storedCount() + ")");
			checkLayout(screen, "the Stored tab at GUI scale 2");
			ScreenshotHarness.shot(mc, "03_stored");
			// 320x180, the GUI scale 3 rule's size (a 960x540 window stops at GUI scale 2, so the screen is laid out at
			// 320x180 and drawn at scale 2 in the frame's top-left two-thirds).
			screen.resize(mc, 320, 180);
		}
		if (tick == 255 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.boxes().compact() && screen.width == 320 && screen.height == 180,
				"at 320x180 (" + screen.width + "x" + screen.height + ") the screen takes the compact layout");
			checkLayout(screen, "the Stored tab at 320x180");
			ScreenshotHarness.shot(mc, "04_stored_compact");
			screen.showLibrary();
		}
		if (tick == 275 && mc.screen instanceof BlueprintTableScreen screen) {
			checkLayout(screen, "the book at 320x180");
			ScreenshotHarness.shot(mc, "05_book_compact");
			screen.search("zzz");
		}
		if (tick == 290 && mc.screen instanceof BlueprintTableScreen screen) {
			Showcase.check(screen.shownCount() == 0, "a search for nothing lists nothing and says so");
			ScreenshotHarness.shot(mc, "06_no_match_compact");
		}
		if (tick == 300) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	/** The view's buttons inside the window and apart, the copper one first, the list and the detail beside each other. */
	private static void checkLayout(BlueprintTableScreen screen, String what) {
		BlueprintBookLayout.Boxes b = screen.boxes();
		int[] win = b.win();
		List<Button> buttons = screen.viewButtons();
		boolean ok = !CopperLayout.overlap(b.list(), b.detail()) && !CopperLayout.overlap(b.rail(), b.list());
		for (int i = 0; i < buttons.size(); i++) {
			Button x = buttons.get(i);
			int[] box = {x.getX(), x.getY(), x.getWidth(), x.getHeight()};
			ok &= box[0] >= win[0] && box[0] + box[2] <= win[0] + win[2] && box[1] + box[3] <= win[1] + win[3];
			for (int j = 0; j < i; j++) {
				Button y = buttons.get(j);
				ok &= !CopperLayout.overlap(box, new int[]{y.getX(), y.getY(), y.getWidth(), y.getHeight()});
			}
		}
		Showcase.check(ok, what + ": the rail, list, detail and " + buttons.size() + " buttons (the first copper) inside the window, apart");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
