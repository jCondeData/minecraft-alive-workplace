package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=colony_departure (ROADMAP 33.9): Thornholm, a City with a real plains village round its hall and a Storehouse
 * holding the settlers' supplies. The owner right-clicks the hall with the Colony Charter (its spot chosen 612 blocks
 * north-east, named Newbrook): Dara and Tomas volunteer and the supplies are packed (the Colonies tab shows the order
 * and its call-off). The settlers gather at the hall, the bell rings, they walk out toward the spot, and once out of
 * sight the tab reads "On the road to Newbrook". The scene's one shortcut: the morning the settlers wait for comes
 * five seconds after the order ({@link Settlers#MUSTER_DELAY}), not the next day.
 */
final class ColonyDepartureScene {
	private static final int DX = 433;
	private static final int DZ = -433;
	private int tick;
	private volatile BlockPos hall;
	private final List<Villager> settlers = new ArrayList<>();

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
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -80 -80 80 80");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// The owner right-clicks the hall with the charter: the order.
		if (tick == 430) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				city(server);
				Settlers.MUSTER_DELAY = 100;
				player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(hall), Direction.UP, hall, false));
				RealmData.Order order = order(server);
				Showcase.check(order != null && player.getMainHandItem().isEmpty(), "right-clicking the hall with the charter gave the order and used the charter up");
				if (order != null) {
					settlers.addAll(Settlers.here(level, order));
					Showcase.check(settlers.size() >= 2, "two settlers volunteered (" + settlers.stream().map(v -> v.getDisplayName().getString()).toList() + ")");
					Showcase.check(Settlers.missing(order).isEmpty(), "the Storehouse gave every supply (missing: " + Settlers.missing(order) + ")");
				}
			});
		}
		if (tick == 450) {
			server.execute(() -> openTab(server));
		}
		if (tick == 470) {
			ScreenshotHarness.pointAt(mc, Colonies.ON_ROAD);
		}
		if (tick == 490) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_colony_order");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Colonies.ON_ROAD).is(Items.LEATHER_BOOTS)
					&& menu.icon(Settlers.CALL_OFF).is(Items.BARRIER);
				Showcase.check(shown, "the Colonies tab shows the order getting ready, with its call-off");
				player.closeContainer();
			});
		}
		// The settlers at the hall, just before the bell.
		if (tick == 525) {
			ScreenshotHarness.shot(mc, "02_settlers_gathered");
			server.execute(() -> Showcase.check(!settlers.isEmpty() && settlers.stream().allMatch(v -> v.isAlive() && v.distanceToSqr(Vec3.atCenterOf(hall)) <= 8 * 8),
				"the settlers are at the hall"));
		}
		// The bell has rung: they walk out toward the spot.
		if (tick == 640) {
			ScreenshotHarness.shot(mc, "03_walking_out");
			server.execute(() -> {
				RealmData.Order order = order(server);
				Showcase.check(order != null && order.gone(), "the bell rang and the settlers set out (" + (order == null ? null : order.state()) + ")");
			});
		}
		// Out of sight (or half a minute on): on the road, saved in the order.
		if (tick == 1230) {
			server.execute(() -> {
				RealmData.Order order = order(server);
				Showcase.check(order != null && order.state().equals(RealmData.ON_ROAD) && order.settlers().size() == settlers.size()
					&& settlers.stream().allMatch(Villager::isRemoved), "the settlers are on the road, kept in the order (" + (order == null ? null : order.state()) + ")");
				openTab(server);
			});
		}
		if (tick == 1250) {
			ScreenshotHarness.pointAt(mc, Colonies.ON_ROAD);
		}
		if (tick == 1270) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_on_the_road");
			server.execute(() -> {
				ServerPlayer player = player(server);
				List<String> lore = new ArrayList<>();
				if (player.containerMenu instanceof ChoiceMenu menu && menu.icon(Colonies.ON_ROAD).get(DataComponents.LORE) != null) {
					menu.icon(Colonies.ON_ROAD).get(DataComponents.LORE).lines().forEach(l -> lore.add(l.getString()));
				}
				Showcase.check(lore.stream().anyMatch(l -> l.startsWith("On the road to Newbrook, there in ")), "the tab says how far they are: " + lore);
			});
		}
		if (tick == 1290) {
			Settlers.MUSTER_DELAY = -1;
			mc.stop();
		}
	}

	private RealmData.Order order(MinecraftServer server) {
		return RealmData.get(server).orderOf(GlobalPos.of(server.overworld().dimension(), hall));
	}

	private void openTab(MinecraftServer server) {
		ServerPlayer player = player(server);
		city(server);
		VillageHallScreen.open(player, hall);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.COLONIES.slot(), player);
			menu.broadcastChanges();
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

	/** Thornholm's hall in a plains village, a Storehouse with the supplies beside it, Dara and Tomas by the hall, the owner holding the charter. */
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
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.CITY);
		// The Storehouse and its chest, with everything the settlers take.
		BlockPos store = ground(level, hall.getX() - 3, hall.getZ());
		level.setBlockAndUpdate(store, ModBlocks.STOREHOUSE.defaultBlockState());
		BlockPos chestAt = ground(level, hall.getX() - 4, hall.getZ());
		level.setBlockAndUpdate(chestAt, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest) {
			ItemStack[] stacks = {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
				new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
				new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED)};
			for (int i = 0; i < stacks.length; i++) {
				chest.setItem(i, stacks[i]);
			}
		}
		// Dara and Tomas, jobless grown-ups by the hall: the nearest to it, so they are the ones who volunteer.
		settler(level, ground(level, hall.getX() + 1, hall.getZ() + 1), "Dara");
		settler(level, ground(level, hall.getX() - 1, hall.getZ() + 1), "Tomas");
		// The charter: Thornholm's, the spot 612 blocks north-east, named Newbrook in an anvil.
		ItemStack charter = ColonyCharterItem.of(level, hall);
		charter.set(ModComponents.COLONY_CHARTER, charter.get(ModComponents.COLONY_CHARTER).withSpot(hall.offset(DX, 0, DZ)));
		charter.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		player.getInventory().setItem(0, charter);
		player.getInventory().selected = 0;
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
		// south-west of the hall, looking north-east past it: the way the settlers go
		BlockPos stand = ground(level, hall.getX() - 5, hall.getZ() + 6);
		player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, -140, 8);
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static void settler(ServerLevel level, BlockPos at, String name) {
		Villager villager = EntityType.VILLAGER.create(level);
		if (villager == null) {
			return;
		}
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		villager.setCustomName(Component.literal(name));
		villager.setPersistenceRequired();
		level.addFreshEntity(villager);
	}
}
