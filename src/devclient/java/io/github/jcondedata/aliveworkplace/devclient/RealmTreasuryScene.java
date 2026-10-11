package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=realm_treasury (ROADMAP 33.13): Thornholm, a Town, founds a realm on its Realm tab and adds Ashford and Farholt
 * (a County). Four mornings pass: each member pays the 10% levy on its takings into the realm treasury. The tab's
 * treasury icon then shows what it holds of the County's 512 and the movements by day; the player gives a stack of
 * twelve emeralds with a shift-click and the newest movement says so; last, the levy's icon.
 */
final class RealmTreasuryScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos ashford;
	private volatile BlockPos farholt;

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
				mornings(server);
				openTab(server);
			});
		}
		// The treasury's icon: what it holds, the levy and the movements by day.
		if (tick == 420) {
			ScreenshotHarness.pointAt(mc, RealmTreasury.TREASURY);
		}
		if (tick == 440) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_treasury_tooltip");
			server.execute(() -> {
				RealmData.Realm realm = RealmData.get(server).realmOf(at(server, hall));
				boolean shown = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmTreasury.TREASURY).is(Items.EMERALD_BLOCK);
				Showcase.check(shown && realm != null && realm.treasury() > 0 && realm.ledger().size() == 12,
					"four mornings of the levy from three members filled the treasury (" + (realm == null ? 0 : realm.treasury()) + " hundredths)");
			});
		}
		// A shift-click with a stack of emeralds gives them all.
		if (tick == 460) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					long before = RealmData.get(server).realmOf(at(server, hall)).treasury();
					menu.setCarried(new ItemStack(Items.EMERALD, 12));
					menu.clicked(RealmTreasury.TREASURY, 0, ClickType.QUICK_MOVE, player); // the real shift-click
					long after = RealmData.get(server).realmOf(at(server, hall)).treasury();
					Showcase.check(menu.getCarried().isEmpty() && after - before == 1200, "a shift-click gave the stack of twelve emeralds");
					menu.broadcastChanges();
				}
			});
		}
		if (tick == 480) {
			ScreenshotHarness.pointAt(mc, RealmTreasury.TREASURY);
		}
		if (tick == 500) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_after_a_gift");
		}
		if (tick == 520) {
			ScreenshotHarness.pointAt(mc, RealmTreasury.LEVY);
		}
		if (tick == 540) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_the_levy");
			server.execute(() -> {
				boolean levy = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmTreasury.LEVY).is(Items.HOPPER);
				Showcase.check(levy, "the levy's icon shows 10%");
			});
		}
		if (tick == 560) {
			mc.stop();
		}
	}

	/** The realm founded on the tab, Ashford and Farholt added with a click each: the County of Thornholm. */
	private void found(MinecraftServer server) {
		openTab(server);
		ServerPlayer player = player(server);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(Realms.FOUND, player);
			menu.press(Realms.MEMBERS + 1, player);
			menu.press(Realms.MEMBERS + 2, player);
		}
		player.closeContainer();
	}

	/** Four mornings: each member's takings (as its hall's round reckons them) pay the levy. */
	private void mornings(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int[] takings = {450, 300, 160};
		BlockPos[] halls = {hall, ashford, farholt};
		for (int day = 0; day < 4; day++) {
			level.setDayTime(24000L * (day + 3) + 1000);
			for (int i = 0; i < halls.length; i++) {
				RealmTreasury.levy(level, halls[i], takings[i] + 20L * day);
			}
		}
		level.setDayTime(24000L * 6 + 6000);
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
		ashford = village(level, player, hall.offset(80, 0, -40), "Ashford", VillageRanks.Rank.VILLAGE, 12);
		farholt = village(level, player, hall.offset(-90, 0, 60), "Farholt", VillageRanks.Rank.HAMLET, 6);
	}

	/** Another village of the player's: its hall, named, of {@code rank}, seen by a round with {@code villagers}. */
	private static BlockPos village(ServerLevel level, ServerPlayer player, BlockPos at, String name, VillageRanks.Rank rank, int villagers) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			entity.setRank(rank);
			Realms.round(level, pos, entity, villagers);
		}
		return pos;
	}
}
