package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=leaderboard (ROADMAP 33.19): the player owns Ashford, a Town in a plains village; Rowan owns Thornholm (the
 * capital of a realm with Farholt) and Farholt. A week of numbers is in; the clock reaches Sunday 19:00 and the chat
 * reads out the week's leaderboard (Biggest, Richest, Happiest, Most Legends, Greatest realm). Ashford won the Happiest:
 * its hall's name icon wears the ribbon, and the Trade page's toggle can leave it off the leaderboard.
 */
final class LeaderboardScene {
	private static final UUID ROWAN = UUID.nameUUIDFromBytes("showcase:rowan".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	private int tick;
	private volatile BlockPos ashford;

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
				level.setDayTime(13000); // a Sunday evening
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// Sunday 19:00: the chat reads the week out.
		if (tick == 420) {
			server.execute(() -> {
				Leaderboard.CLOCK = Clock.fixed(Instant.parse("2026-10-04T19:00:05Z"), ZoneOffset.UTC);
				Leaderboard.settle(server, false);
				RealmData data = RealmData.get(server);
				Showcase.check(data.weekStarted() == Instant.parse("2026-10-04T19:00:00Z").toEpochMilli(), "Sunday 19:00 came and the week was read out");
				Showcase.check(data.heldLines().isEmpty() && !server.getPlayerList().getPlayers().isEmpty(), "the lines went out to everyone online, none held back");
				Showcase.check(Leaderboard.ribbons(server.overworld(), ashford).stream().anyMatch(r -> r.board().equals("happiest")),
					"Ashford won the Happiest and wears its ribbon");
			});
		}
		if (tick == 440) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 470) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_the_leaderboard");
			mc.setScreen(null);
		}
		// Ashford's hall: the name icon wears the ribbon.
		if (tick == 480) {
			server.execute(() -> VillageHallScreen.open(player(server), ashford));
		}
		if (tick == 500) {
			ScreenshotHarness.pointAt(mc, VillageHallScreen.NAME);
		}
		if (tick == 520) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_the_ribbon");
			server.execute(() -> {
				ServerPlayer player = player(server);
				Showcase.check(player.containerMenu instanceof ChoiceMenu menu && TradePage.marks(menu.icon(VillageHallScreen.NAME)).contains(Leaderboard.RIBBON),
					"the hall's name icon wears the ribbon");
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(VillageHallScreen.ROUTES, player); // the Trade page
					menu.broadcastChanges();
				}
			});
		}
		if (tick == 540) {
			ScreenshotHarness.pointAt(mc, Leaderboard.TOGGLE);
		}
		if (tick == 560) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_the_toggle");
			server.execute(() -> {
				ServerPlayer player = player(server);
				Showcase.check(player.containerMenu instanceof ChoiceMenu menu && menu.icon(Leaderboard.TOGGLE).getHoverName().getString().equals("On the weekly leaderboard"),
					"the Trade page's toggle: on the weekly leaderboard");
			});
		}
		if (tick == 580) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Ashford (the player's), Thornholm and Farholt (Rowan's, a realm), a week of numbers, the clock just before 19:00. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "place structure minecraft:village_plains 0 -60 0");
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
		ashford = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		int z = ashford.getZ() + 3;
		player.teleportTo(level, ashford.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ashford.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(ashford);
		entity.setCustomName(Component.literal("Ashford"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		Realms.round(level, ashford, entity, 18);
		BlockPos thornholm = village(level, ashford.offset(80, 0, -40), "Thornholm", 24);
		BlockPos farholt = village(level, ashford.offset(-70, 0, 50), "Farholt", 11);
		RealmData data = RealmData.get(server);
		data.clearLeaderboard();
		Leaderboard.CLOCK = Clock.fixed(Instant.parse("2026-10-04T18:59:00Z"), ZoneOffset.UTC);
		Leaderboard.settle(server, false); // the week began last Sunday
		GlobalPos capital = GlobalPos.of(level.dimension(), thornholm);
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), capital,
			List.of(new RealmData.Member(capital, 0), new RealmData.Member(GlobalPos.of(level.dimension(), farholt), 0)), Optional.empty(), "barony", 0));
		// the week: what each earned, its rounds' wellbeing, its Legends
		week(level, ashford, 1840, new int[]{88, 92, 90}, 2);
		week(level, thornholm, 2650, new int[]{70, 74, 72}, 1);
		week(level, farholt, 960, new int[]{61, 65, 63}, 0);
	}

	private static void week(ServerLevel level, BlockPos hall, int cents, int[] rounds, int legends) {
		Leaderboard.earned(level, hall, cents);
		for (int w : rounds) {
			Leaderboard.round(level, hall, w, legends, legends);
		}
	}

	/** One of Rowan's villages: its hall, named, a Town, seen by a round with {@code villagers}. */
	private static BlockPos village(ServerLevel level, BlockPos at, String name, int villagers) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
			entity.setOwner(ROWAN, "Rowan");
			entity.setRank(VillageRanks.Rank.TOWN);
			Realms.round(level, pos, entity, villagers);
		}
		return pos;
	}
}
