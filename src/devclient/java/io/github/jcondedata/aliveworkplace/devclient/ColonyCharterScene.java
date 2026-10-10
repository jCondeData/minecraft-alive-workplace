package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.client.ColonyCharterScreen;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=colony_charter (ROADMAP 33.8): Thornholm, a City with a real plains village round its hall, and two other
 * villages with halls on its map (Ashford to the north-east, Farholt to the south-west). The owner buys a Colony
 * Charter on the hall's Colonies tab (the treasury pays 20 emeralds, the player 12), right-clicks the air for the map
 * (the land the server has loaded drawn like a vanilla map, parchment round it, the three banners, the ring and the
 * circles round the other halls), clicks a spot 612 blocks north-east, and reads the charter's tooltip.
 */
final class ColonyCharterScene {
	private static final int DX = 433;
	private static final int DZ = -433;
	private int tick;
	private volatile BlockPos hall;
	private volatile String bought = "";
	private volatile BlockPos kept;

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
				level.setDayTime(3000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// The Colonies tab: the charter for sale, who pays what.
		if (tick == 420) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				city(server); // a hall's round since the staging counts a village of two farmers a Hamlet again
				VillageHallScreen.open(player, hall);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(VillageHallScreen.ROUTES, player);
					menu.press(TradePage.Tab.COLONIES.slot(), player);
					menu.broadcastChanges();
				}
			});
		}
		if (tick == 440) {
			ScreenshotHarness.pointAt(mc, Colonies.BUY);
		}
		if (tick == 460) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_colonies_tab");
			server.execute(() -> {
				ServerPlayer player = player(server);
				VillageHallBlockEntity entity = (VillageHallBlockEntity) server.overworld().getBlockEntity(hall);
				boolean tab = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Colonies.BUY).is(ModItems.COLONY_CHARTER);
				Showcase.check(tab, "the hall's Colonies tab offers a Colony Charter");
				city(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(Colonies.BUY, player); // the real button
				}
				int emeralds = player.getInventory().countItem(Items.EMERALD);
				bought = "treasury " + entity.treasury() + ", emeralds " + emeralds;
				Showcase.check(entity.treasury() == 50 && emeralds == 20, "the treasury paid 20 emeralds and the player 12 (" + bought + ")");
				player.closeContainer();
				// the charter into the hand
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ItemStack stack = player.getInventory().getItem(i);
					if (stack.is(ModItems.COLONY_CHARTER)) {
						player.getInventory().setItem(i, ItemStack.EMPTY);
						player.getInventory().setItem(0, stack);
					}
				}
				player.getInventory().selected = 0;
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
			});
		}
		// Right-click the air: the map.
		if (tick == 490) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.gameMode.useItem(player, server.overworld(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
		}
		if (tick == 520) {
			Showcase.check(mc.screen instanceof ColonyCharterScreen, "right-clicking the air with the charter opened its map");
			if (mc.screen instanceof ColonyCharterScreen screen) {
				int[] box = screen.mapBox();
				// pointing at a place in the ring says how far and which way it is
				setMouse(mc, ColonyMap.toScreen(hall.getX(), hall.getX() - 300, box[0], box[2]), ColonyMap.toScreen(hall.getZ(), hall.getZ() + 420, box[1], box[2]));
			}
		}
		if (tick == 540) {
			ScreenshotHarness.shot(mc, "02_colony_map");
			if (mc.screen instanceof ColonyCharterScreen screen) {
				int[] box = screen.mapBox();
				screen.mouseClicked(ColonyMap.toScreen(hall.getX(), hall.getX() + DX, box[0], box[2]), ColonyMap.toScreen(hall.getZ(), hall.getZ() + DZ, box[1], box[2]), 0);
				setMouse(mc, 4, 4); // off the map, so no pointer tooltip covers the cross
			}
		}
		if (tick == 570) {
			if (mc.screen instanceof ColonyCharterScreen screen) {
				BlockPos spot = screen.spot().orElse(null);
				Showcase.check(spot != null && Math.abs(spot.getX() - hall.getX() - DX) <= 16 && Math.abs(spot.getZ() - hall.getZ() - DZ) <= 16,
					"the click put the red cross within 16 blocks of the place clicked (" + spot + ")");
			}
			ScreenshotHarness.shot(mc, "03_colony_spot");
			server.execute(() -> {
				var charter = player(server).getMainHandItem().get(ModComponents.COLONY_CHARTER);
				kept = charter == null ? null : charter.spot().orElse(null);
			});
		}
		// The tooltip.
		if (tick == 585) {
			mc.setScreen(new InventoryScreen(mc.player));
		}
		if (tick == 595) {
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			setMouse(mc, left + 8 + 8, top + 142 + 8); // hotbar slot 0
		}
		if (tick == 615) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_colony_tooltip");
			List<String> tip = Screen.getTooltipFromItem(mc, mc.player.getInventory().getItem(0)).stream().map(Component::getString).toList();
			Showcase.check(kept != null && mc.screen instanceof InventoryScreen && tip.contains("Of Thornholm: founds its colony")
				&& tip.stream().anyMatch(l -> l.startsWith("The spot: 61") && l.endsWith("blocks north-east of Thornholm")),
				"the charter keeps the spot and its tooltip names it: " + tip);
		}
		if (tick == 630) {
			mc.stop();
		}
	}

	/** Thornholm as the scene needs it: a City (staged by fiat: a real one needs 35 villagers and 25 buildings). */
	private void city(MinecraftServer server) {
		if (server.overworld().getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setRank(VillageRanks.Rank.CITY);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Thornholm's hall in a plains village, a City with 20.5 emeralds in its treasury; Ashford and Farholt on its map; the owner with 32 emeralds. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		BlockPos spot = null;
		for (int r = 2; r <= 16 && spot == null; r++) {
			for (int dx = -r; dx <= r && spot == null; dx++) {
				for (int dz = -r; dz <= r && spot == null; dz++) {
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
					BlockPos below = new BlockPos(dx, top - 1, dz);
					if (top <= -59 && (level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
						|| level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.DIRT_PATH))) {
						spot = below.above();
					}
				}
			}
		}
		hall = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.EMERALD, 32));
		// on the ground by the hall, in survival: the price is really paid, and the inventory is the survival one
		int z = hall.getZ() + 3;
		player.teleportTo(level, hall.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.CITY);
		entity.setTreasury(2050);
		// Two more villages with halls, as their own rounds would have told the caravans.
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(hall, Component.literal("Thornholm"), List.of());
		village(level, data, hall.offset(640, 0, -112), "Ashford");
		village(level, data, hall.offset(-496, 0, 384), "Farholt");
	}

	private static void village(ServerLevel level, Caravans.Data data, BlockPos at, String name) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
		}
		data.setWants(pos, Component.literal(name), List.of());
	}

	/** Moves the mouse to a place given in GUI pixels. */
	private static void setMouse(Minecraft mc, double guiX, double guiY) {
		double scale = mc.getWindow().getGuiScale();
		try {
			var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
			var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
			xpos.setAccessible(true);
			ypos.setAccessible(true);
			xpos.setDouble(mc.mouseHandler, guiX * scale);
			ypos.setDouble(mc.mouseHandler, guiY * scale);
		} catch (ReflectiveOperationException e) {
			Showcase.check(false, "couldn't move the mouse: " + e);
		}
	}
}
