package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
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
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * SCENE=realm (ROADMAP 33.11): Thornholm, a Town in a plains village, and two more villages of its owner nearby
 * (Ashford, Farholt). On the hall's Trade page, Realm tab, the owner founds the Barony of Thornholm and adds Ashford
 * with one click; then, standing back to watch the sky over the hall, adds Farholt: three villages with a Town capital
 * make a County, and fireworks go up over the capital. Last, a banner clicked on the arms sets them, and the tab shows
 * the County of Thornholm with its members, the capital crowned.
 */
final class RealmScene {
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
				level.setDayTime(13500); // dusk: fireworks show
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// The Realm tab of a Town in no realm: "Found a realm".
		if (tick == 420) {
			server.execute(() -> openTab(server));
		}
		if (tick == 440) {
			ScreenshotHarness.pointAt(mc, Realms.FOUND);
		}
		if (tick == 460) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_found_a_realm");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean button = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Realms.FOUND).is(Items.GOLDEN_HELMET);
				Showcase.check(button, "the Town's Realm tab offers to found a realm");
				town(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(Realms.FOUND, player); // the real button
					menu.press(Realms.MEMBERS + 1, player); // Add Ashford, the nearest
					menu.broadcastChanges();
				}
				RealmData.Realm realm = RealmData.get(server).realmOf(at(server, hall));
				Showcase.check(realm != null && realm.has(at(server, ashford)), "the Barony of Thornholm was founded and Ashford added with one click");
			});
		}
		if (tick == 480) {
			ScreenshotHarness.pointAt(mc, Realms.MEMBERS + 1);
		}
		if (tick == 500) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_barony");
			// stand back and look at the sky over the hall
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				int z = hall.getZ() + 12;
				player.teleportTo(server.overworld(), hall.getX() + 0.5, server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z), z + 0.5, 180, -45);
			});
		}
		// Farholt joins: a County, fireworks over the capital.
		if (tick == 520) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				town(server);
				ChoiceMenu menu = ChoiceMenu.detached(player, m -> TradePage.render(m, server.overworld(), hall, TradePage.Tab.REALM, null, player));
				menu.press(Realms.MEMBERS + 2, player); // Add Farholt: what the tab's button does
				RealmData.Realm realm = RealmData.get(server).realmOf(at(server, hall));
				Showcase.check(realm != null && "county".equals(realm.rank()), "three villages with a Town capital made the County of Thornholm");
			});
		}
		if (tick == 530) {
			server.execute(() -> {
				List<FireworkRocketEntity> rockets = server.overworld().getEntitiesOfClass(FireworkRocketEntity.class, new AABB(hall).inflate(16, 64, 16));
				Showcase.check(!rockets.isEmpty(), "fireworks went up over the capital (" + rockets.size() + ")");
			});
		}
		if (tick == 548) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_fireworks");
		}
		// The arms: a banner clicked on them; the County's tab.
		if (tick == 580) {
			server.execute(() -> {
				openTab(server);
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.setCarried(arms());
					menu.press(Realms.ARMS, player);
					player.getInventory().add(menu.getCarried());
					menu.setCarried(ItemStack.EMPTY);
					menu.broadcastChanges();
				}
			});
		}
		if (tick == 600) {
			ScreenshotHarness.pointAt(mc, Realms.TITLE);
		}
		if (tick == 620) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_county_tab");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Realms.TITLE).getHoverName().getString().equals("the County of Thornholm")
					&& menu.icon(Realms.ARMS).is(Items.BLUE_BANNER) && menu.icon(Realms.MEMBERS).is(Items.GOLDEN_HELMET);
				Showcase.check(shown, "the tab shows the County of Thornholm, its blue arms and the crowned capital");
			});
		}
		if (tick == 640) {
			mc.stop();
		}
	}

	private void openTab(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.openRemote(player, hall); // at tick 580 the camera stands 12 blocks off (open needs 8): as from a Village Ledger (B128)
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.REALM.slot(), player);
			menu.broadcastChanges();
		}
	}

	/** A blue banner with a gold cross: the realm's arms. */
	private static ItemStack arms() {
		ItemStack banner = new ItemStack(Items.BLUE_BANNER);
		var lookup = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
		net.minecraft.core.Holder<BannerPattern> cross = lookup.getOrThrow(BannerPatterns.STRAIGHT_CROSS);
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(cross, DyeColor.YELLOW).build());
		return banner;
	}

	/** Thornholm a Town (staged by fiat: a hall's round counts a real village's rank again), its neighbours a Village and a Hamlet. */
	private void town(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setRank(VillageRanks.Rank.TOWN);
		}
		if (level.getBlockEntity(ashford) instanceof VillageHallBlockEntity entity) {
			entity.setRank(VillageRanks.Rank.VILLAGE);
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
