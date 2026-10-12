package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=realm_edicts (ROADMAP 33.22): Thornholm, a Town, is the capital of the Barony of Thornholm with Ashford a member.
 * Its Book of Edicts (the hall's lectern button) has the realm's row: one free realm slot and three locked ones, and the
 * six realm edicts after the village's. The player points at the Royal Highway (its boost, its upkeep, its reform and
 * the count across the realm), clicks it twice, and it fills the realm slot; last, Ashford's own book shows the same
 * edict in force from the capital.
 */
final class RealmEdictsScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos ashford;
	private volatile int highway = -1;

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
				level.setDayTime(6000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> {
				stage(server);
				found(server);
				openBook(server, hall);
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					highway = find(menu, "Royal Highway");
				}
			});
		}
		// The capital's book: a free realm slot and three locked ones.
		if (tick == 420) {
			ScreenshotHarness.pointAt(mc, RealmEdicts.SLOTS[1]);
		}
		if (tick == 440) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_realm_slots");
			server.execute(() -> {
				boolean slots = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmEdicts.SLOTS[0]).is(Items.MAP)
					&& menu.icon(RealmEdicts.SLOTS[1]).is(Items.GRAY_DYE);
				Showcase.check(slots && highway >= 0, "the capital's book shows a Barony's free realm slot, the locked ones and the realm edicts");
			});
		}
		// The Royal Highway in the list: boost, upkeep, reform and the count.
		if (tick == 460 && highway >= 0) {
			ScreenshotHarness.pointAt(mc, highway);
		}
		if (tick == 480) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_royal_highway");
		}
		// Two clicks proclaim it for the whole realm.
		if (tick == 500) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu && highway >= 0) {
					menu.clicked(highway, 0, ClickType.PICKUP, player);
					menu.clicked(highway, 0, ClickType.PICKUP, player);
					menu.broadcastChanges();
				}
				RealmData.Realm realm = RealmData.get(server).realmOf(GlobalPos.of(server.overworld().dimension(), hall));
				Showcase.check(realm != null && RealmEdicts.inForce(server, realm).size() == 1, "two clicks proclaimed the Royal Highway for the realm");
			});
		}
		if (tick == 520) {
			ScreenshotHarness.pointAt(mc, RealmEdicts.SLOTS[0]);
		}
		if (tick == 540) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_in_force");
			server.execute(() -> openBook(server, ashford));
		}
		// Ashford's own book: the realm's edict, in force from the capital.
		if (tick == 560) {
			ScreenshotHarness.pointAt(mc, RealmEdicts.SLOTS[0]);
		}
		if (tick == 580) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_a_members_book");
			server.execute(() -> {
				boolean shown = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmEdicts.SLOTS[0]).is(Items.STONE_BRICKS);
				Showcase.check(shown, "Ashford's book shows the Royal Highway in force from the capital");
			});
		}
		if (tick == 600) {
			mc.stop();
		}
	}

	private static int find(ChoiceMenu menu, String name) {
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		return -1;
	}

	/** The realm founded on the capital's Realm tab, Ashford added with a click: the Barony of Thornholm. */
	private void found(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, hall);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.REALM.slot(), player);
			menu.press(Realms.FOUND, player);
			menu.press(Realms.MEMBERS + 1, player);
		}
		player.closeContainer();
	}

	/** The hall's screen at {@code at}, then its Book of Edicts: the way a player opens it. */
	private static void openBook(MinecraftServer server, BlockPos at) {
		ServerPlayer player = player(server);
		player.closeContainer();
		VillageHallScreen.open(player, at);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.BOOK, player);
			menu.broadcastChanges();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Thornholm's hall in a plains village, a Town owned by the player; Ashford, the player's too, a little way off. */
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
		int z = hall.getZ() + 3;
		player.teleportTo(level, hall.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		Realms.round(level, hall, entity, 24);
		BlockPos at = hall.offset(80, 0, -40);
		ashford = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(ashford) instanceof VillageHallBlockEntity other) {
			other.setCustomName(Component.literal("Ashford"));
			other.setOwner(player.getUUID(), player.getGameProfile().getName());
			other.setRank(VillageRanks.Rank.VILLAGE);
			Realms.round(level, ashford, other, 12);
		}
	}
}
