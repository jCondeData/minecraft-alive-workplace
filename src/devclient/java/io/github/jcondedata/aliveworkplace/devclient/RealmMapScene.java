package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmMaps;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=realm_map (ROADMAP 33.21): the County of Thornholm (Thornholm, Ashford, Farholt; blue arms), allied with
 * Rowan's Brookmere, in a feud with Grimfell, a colony's settlers on the road, a caravan route to Ashford. The player
 * clicks the Realm tab's map button with an empty map: the button's tooltip, then the map in hand (blue banners, the
 * capital named, the light blue ally, the red rival, the dotted route, the red cross) and hung in an item frame.
 */
final class RealmMapScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos ashford;
	private volatile BlockPos farholt;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -160 -160 160 160");
			});
		}
		if (tick == 380) {
			server.execute(() -> {
				stage(server);
				realm(server);
				openTab(server);
			});
		}
		// The map button's tooltip on the Realm tab.
		if (tick == 420) {
			ScreenshotHarness.pointAt(mc, RealmMaps.BUTTON);
		}
		if (tick == 440) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_map_button");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(RealmMaps.BUTTON).is(Items.FILLED_MAP);
				Showcase.check(shown, "the Realm tab has the Draw the Realm Map button");
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(RealmMaps.BUTTON, player); // an empty map in the inventory
				}
				player.closeContainer();
				ItemStack map = ItemStack.EMPTY;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(Items.FILLED_MAP)) {
						map = player.getInventory().removeItemNoUpdate(i);
					}
				}
				MapItemSavedData data = map.isEmpty() ? null : server.overworld().getMapData(map.get(DataComponents.MAP_ID));
				boolean cross = map.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY).decorations().values().stream()
					.anyMatch(e -> e.type().equals(MapDecorationTypes.RED_X));
				Showcase.check(data != null && data.getBanners().size() == 5 && cross,
					"the map has five banners and the colony's cross (" + (data == null ? 0 : data.getBanners().size()) + " banners)");
				player.getInventory().setItem(player.getInventory().selected, map);
				int z = hall.getZ() + 4;
				player.teleportTo(server.overworld(), hall.getX() + 0.5, server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z),
					z + 0.5, 180, 70);
			});
		}
		// The map in hand, looking down at it.
		if (tick == 520) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_map_in_hand");
		}
		if (tick == 530) {
			server.execute(() -> hang(server));
		}
		if (tick == 600) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_map_in_frame");
		}
		if (tick == 620) {
			mc.stop();
		}
	}

	/** Ashford and Farholt added from the tab, blue arms; an ally, a rival, a route and a colony on the road. */
	private void realm(MinecraftServer server) {
		ServerLevel level = server.overworld();
		openTab(server);
		ServerPlayer player = player(server);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(Realms.FOUND, player);
			menu.press(Realms.MEMBERS + 1, player);
			menu.press(Realms.MEMBERS + 2, player);
		}
		player.closeContainer();
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(at(server, hall));
		if (realm != null) {
			data.putRealm(realm.withArms(Optional.of(new VillageBanners.Colours(DyeColor.BLUE, BannerPatternLayers.EMPTY))));
		}
		UUID rowan = UUID.randomUUID();
		BlockPos brookmere = village(level, rowan, "Rowan", hall.offset(150, 0, 90), "Brookmere", VillageRanks.Rank.VILLAGE);
		BlockPos grimfell = village(level, UUID.randomUUID(), "Morwen", hall.offset(-140, 0, -110), "Grimfell", VillageRanks.Rank.TOWN);
		Pacts.Data.get(server).put(new Pacts.Pact(at(server, hall), at(server, brookmere), true, 0, -1, false, false));
		Feuds.Data.get(server).put(new Feuds.Feud(at(server, grimfell), at(server, hall), level.getGameTime()));
		Caravans.Data.get(level).toggleRoute(hall, ashford);
		Caravans.Data.get(level).toggleRoute(farholt, brookmere);
		data.putOrder(new RealmData.Order(at(server, hall), hall.offset(60, 0, 150), Optional.of(Component.literal("Westmere")), RealmData.ON_ROAD,
			level.getGameTime(), level.getGameTime() + 24000, 0));
		player.getInventory().add(new ItemStack(Items.MAP));
	}

	/** The map in an item frame on a wall by the hall, and the camera in front of it. */
	private void hang(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		int held = player.getInventory().selected;
		ItemStack map = player.getInventory().getItem(held).copy();
		player.getInventory().setItem(held, ItemStack.EMPTY);
		BlockPos wall = hall.east(3);
		level.setBlockAndUpdate(wall, Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(wall.above(), Blocks.STONE_BRICKS.defaultBlockState());
		BlockPos frame = wall.above().south();
		level.setBlockAndUpdate(frame, Blocks.AIR.defaultBlockState());
		ItemFrame itemFrame = new ItemFrame(level, frame, Direction.SOUTH);
		itemFrame.setItem(map, false);
		level.addFreshEntity(itemFrame);
		Showcase.check(map.is(Items.FILLED_MAP), "the realm map hangs in the frame");
		Vec3 face = new Vec3(frame.getX() + 0.5, frame.getY() + 0.5, frame.getZ() + 0.1);
		ScreenshotHarness.hoverLookingAt(player, face.add(0.0, 0.1, 1.5), face);
	}

	private void openTab(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, hall);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.REALM.slot(), player);
			menu.broadcastChanges();
		}
	}

	private static GlobalPos at(MinecraftServer server, BlockPos pos) {
		return GlobalPos.of(server.overworld().dimension(), pos);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Thornholm's hall in a plains village, a Town owned by the player; Ashford and Farholt, the player's too, a little way off. */
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
					if (top <= -59 && (level.getBlockState(below).is(Blocks.GRASS_BLOCK) || level.getBlockState(below).is(Blocks.DIRT_PATH))) {
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
		int z = hall.getZ() + 3;
		player.teleportTo(level, hall.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		Realms.round(level, hall, entity, 24);
		ashford = village(level, player.getUUID(), player.getGameProfile().getName(), hall.offset(80, 0, -40), "Ashford", VillageRanks.Rank.VILLAGE);
		farholt = village(level, player.getUUID(), player.getGameProfile().getName(), hall.offset(-90, 0, 60), "Farholt", VillageRanks.Rank.HAMLET);
	}

	/** A village's hall at {@code at} on the ground, named, owned by {@code owner}, of {@code rank}, seen by a round. */
	private static BlockPos village(ServerLevel level, UUID owner, String ownerName, BlockPos at, String name, VillageRanks.Rank rank) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
			entity.setOwner(owner, ownerName);
			entity.setRank(rank);
			Realms.round(level, pos, entity, 10);
		}
		return pos;
	}
}
