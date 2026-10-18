package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=modded_storehouse, filmed with -Pfurniture=true (the pack's furniture and storage mods: Sophisticated Storage and
 * Tom's Simple Storage among them; with -Pcobblemon=true, as CobbleFurnies in that set needs it).
 *
 * <p>Part one (ROADMAP 37.9): a Storehouse with a Sophisticated Storage controller on it and three Sophisticated barrels
 * and two chests linked to it, goods in each. With {@code moddedStorageNetworks} on, the Storehouse reads them as one
 * store through the controller; its board says "Linked through a storage controller: 5 blocks". Checks the store is the
 * controller alone, its count is the blocks' total (not twice), and the board line.
 *
 * <p>Part two (37.10), 20 blocks south: a Storehouse with a Tom's inventory connector, a chest touching it, a cable to
 * a second chest and a filing cabinet, and a storage terminal on the connector. The Storehouse's store is the connector
 * and each item counts once; its board says "Linked through an inventory connector: 3 blocks"; a player opens the
 * terminal and sees the village's goods in Tom's own screen.
 */
final class ModdedStorehouseScene {
	private static final BlockPos ROOM = new BlockPos(0, -60, 0);
	private static final Vec3 EYE = new Vec3(3.5, 3.2, -4.5);
	private static final Vec3 AT = new Vec3(3.5, 0.8, 2.5);

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile boolean modsHere;
	private volatile boolean oneStore;
	private volatile int linked;

	private static final BlockPos O = new BlockPos(0, -60, 20);
	private static final BlockPos STOREHOUSE = O.offset(4, 0, 4);
	private static final BlockPos CONNECTOR = O.offset(1, 0, 2);
	// Against the connector's south side. Tom's terminal "faces" the block it reads (its screen on the other side),
	// so facing north it reads the connector and shows its screen to the camera, as when a player places it there.
	private static final BlockPos TERMINAL = O.offset(1, 0, 3);
	private static final BlockPos CHEST_1 = O.offset(0, 0, 2); // touching the connector
	private static final List<BlockPos> CABLES = List.of(O.offset(2, 0, 2), O.offset(3, 0, 2));
	private static final BlockPos CABLE_CONNECTOR_1 = O.offset(4, 0, 2); // facing east, at the second chest
	private static final BlockPos CHEST_2 = O.offset(5, 0, 2);
	private static final BlockPos CABLE_CONNECTOR_2 = O.offset(3, 0, 1); // facing north, at the filing cabinet
	private static final BlockPos CABINET = O.offset(3, 0, 0);

	private volatile boolean tomsHere = true;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick > 180) {
			tickToms(mc, server, tick - 180);
			return;
		}
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(3000);
				StorageNetworks.ENABLED = true; // M37's switch (off on main until the milestone ships)
				place(level);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), Vec3.atLowerCornerOf(ROOM).add(EYE),
					Vec3.atLowerCornerOf(ROOM).add(AT));
			});
		}
		if (tick == 80) {
			ScreenshotHarness.shot(mc, "01_storehouse");
		}
		if (tick == 100) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (modsHere) {
					StorehouseBoard.open(player, ROOM.offset(3, 0, 2));
				}
			});
		}
		if (tick == 140) {
			mc.options.hideGui = false;
		}
		if (tick == 160) {
			ScreenshotHarness.shot(mc, "02_board");
		}
		if (tick == 180) {
			if (!modsHere) {
				wrong.add("Sophisticated Storage isn't loaded: film this scene with -Pfurniture=true");
			}
			if (!oneStore) {
				wrong.add("the Storehouse didn't read the controller's five blocks as one store, counted once");
			}
			if (linked != 5) {
				wrong.add("the board's linked line counted " + linked + " blocks, not 5");
			}
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the Storehouse reads a Sophisticated controller and its five linked blocks as one store, counted once, and its board says so"
				: String.join("; ", wrong));
		}
	}

	/** Part two, {@code tick} counted from the end of part one. */
	private void tickToms(Minecraft mc, MinecraftServer server, int tick) {
		if (tick == 1) {
			server.execute(() -> player(server).closeContainer());
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				placeToms(server.overworld());
			});
		}
		if (tick == 30) {
			server.execute(() -> ScreenshotHarness.hoverLookingAt(player(server), Vec3.atLowerCornerOf(O).add(9, 4.5, 8.5),
				Vec3.atLowerCornerOf(O).add(2.5, 0.5, 2)));
		}
		if (tick == 80) {
			ScreenshotHarness.shot(mc, "03_network");
			server.execute(() -> checkToms(server));
		}
		if (tick == 90 && tomsHere) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				StorehouseBoard.open(player, STOREHOUSE);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					String lore = String.valueOf(menu.icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE));
					Showcase.check(lore.contains("storehouse.connector") && lore.contains("3"),
						"the Storehouse board says the connector links 3 blocks (" + lore + ")");
				} else {
					Showcase.check(false, "the Storehouse board didn't open");
				}
			});
		}
		if (tick == 110) {
			mc.options.hideGui = false;
			ScreenshotHarness.pointAt(mc, StorehouseBoard.INFO_SLOT);
		}
		if (tick == 130) {
			ScreenshotHarness.shot(mc, "04_board");
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				if (tomsHere) {
					// The player walks up to the terminal (Tom's terminal opens only within 6 blocks: from the
					// overview camera it says "Terminal out of range") and right-clicks its screen with an empty hand.
					Direction face = facing(server.overworld().getBlockState(TERMINAL)).getOpposite();
					ScreenshotHarness.hoverLookingAt(player, Vec3.atBottomCenterOf(TERMINAL).add(0, 0, 2.5), Vec3.atCenterOf(TERMINAL));
					player.gameMode.useItemOn(player, server.overworld(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(TERMINAL).relative(face, 0.5), face, TERMINAL, false));
				}
			});
		}
		if (tick == 170) {
			String screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
			Showcase.check(!tomsHere || screen.toLowerCase(java.util.Locale.ROOT).contains("terminal"),
				"right-clicking the storage terminal opens Tom's terminal screen (" + screen + ")");
			ScreenshotHarness.shot(mc, "05_terminal");
		}
		if (tick == 190) {
			mc.stop();
		}
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private void place(ServerLevel level) {
		modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("sophisticatedstorage:controller"));
		if (!modsHere) {
			return;
		}
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 6; z++) {
				boolean wall = x == -1 || x == 8 || z == 6;
				level.setBlock(ROOM.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
				for (int y = 0; y < 4; y++) {
					level.setBlock(ROOM.offset(x, y, z), wall && y < 3 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.setBlockAndUpdate(ROOM.offset(3, 0, 2), ModBlocks.STOREHOUSE.defaultBlockState());
		List<BlockPos> stores = List.of(ROOM.offset(2, 0, 3), ROOM.offset(3, 0, 3), ROOM.offset(4, 0, 3), ROOM.offset(2, 1, 3), ROOM.offset(4, 1, 3));
		String[] kinds = {"sophisticatedstorage:barrel", "sophisticatedstorage:iron_barrel", "sophisticatedstorage:gold_barrel",
			"sophisticatedstorage:chest", "sophisticatedstorage:chest"};
		ItemStack[] goods = {new ItemStack(Items.WHEAT, 40), new ItemStack(Items.RAW_IRON, 24), new ItemStack(Items.OAK_LOG, 48),
			new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.BREAD, 20)};
		for (int i = 0; i < stores.size(); i++) {
			level.setBlockAndUpdate(stores.get(i), block(kinds[i]).defaultBlockState());
			SupplyContainers.insert(level, List.of(stores.get(i)), goods[i]);
		}
		BlockPos controller = ROOM.offset(3, 1, 3);
		Block c = block("sophisticatedstorage:controller");
		level.setBlockAndUpdate(controller, c.defaultBlockState());
		BlockState state = level.getBlockState(controller);
		c.setPlacedBy(level, controller, state, null, new ItemStack(c.asItem())); // linked as a player's placing links it
		level.setBlock(ROOM.offset(0, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);
		level.setBlock(ROOM.offset(7, 0, 0), Blocks.LANTERN.defaultBlockState(), 2);

		StorageNetworks.Folded folded = SupplyContainers.networks(level, ROOM.offset(3, 0, 2));
		long counted = SupplyContainers.contents(level, folded.stores()).values().stream().mapToLong(Long::longValue).sum();
		oneStore = folded.stores().equals(List.of(controller)) && counted == 40 + 24 + 48 + 64 + 20;
		linked = folded.linked();
		if (!oneStore) {
			wrong.add("stores " + folded.stores() + ", " + counted + " items counted");
		}
	}
	private void placeToms(ServerLevel level) {
		tomsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("toms_storage", "inventory_connector"));
		if (!tomsHere) {
			Showcase.check(false, "Tom's Simple Storage isn't loaded: film this scene with -Pfurniture=true");
			return;
		}
		for (int x = -2; x <= 8; x++) {
			for (int z = -2; z <= 7; z++) {
				level.setBlock(O.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
			}
		}
		level.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE.defaultBlockState(), 3);
		level.setBlock(CONNECTOR, toms("inventory_connector").defaultBlockState(), 3);
		level.setBlock(CHEST_1, Blocks.CHEST.defaultBlockState(), 3);
		for (BlockPos cable : CABLES) {
			level.setBlock(cable, toms("inventory_cable").defaultBlockState(), 3);
		}
		level.setBlock(CABLE_CONNECTOR_1, turned(toms("inventory_cable_connector").defaultBlockState(), Direction.EAST), 3);
		level.setBlock(CHEST_2, Blocks.CHEST.defaultBlockState(), 3);
		level.setBlock(CABLE_CONNECTOR_2, turned(toms("inventory_cable_connector").defaultBlockState(), Direction.NORTH), 3);
		level.setBlock(CABINET, toms("filing_cabinet").defaultBlockState(), 3);
		level.setBlock(TERMINAL, turned(toms("storage_terminal").defaultBlockState(), Direction.NORTH), 3);
		for (BlockPos p : List.of(CABLES.get(0), CABLES.get(1), CABLE_CONNECTOR_1, CABLE_CONNECTOR_2, CONNECTOR, TERMINAL)) {
			level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
		}
		SupplyContainers.insert(level, List.of(CHEST_1), new ItemStack(Items.COBBLESTONE, 64));
		SupplyContainers.insert(level, List.of(CHEST_1), new ItemStack(Items.OAK_LOG, 32));
		SupplyContainers.insert(level, List.of(CHEST_2), new ItemStack(Items.OAK_PLANKS, 48));
		SupplyContainers.insert(level, List.of(CHEST_2), new ItemStack(Items.BREAD, 20));
		for (ItemStack tool : List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL),
				new ItemStack(Items.SHEARS), new ItemStack(Items.WRITABLE_BOOK))) {
			SupplyContainers.insert(level, List.of(CABINET), tool); // a filing cabinet holds things that don't stack
		}
	}

	private void checkToms(MinecraftServer server) {
		if (!tomsHere) {
			return;
		}
		ServerLevel level = server.overworld();
		List<BlockPos> store = SupplyContainers.find(level, STOREHOUSE, null);
		Showcase.check(store.equals(List.of(CONNECTOR)), "the Storehouse's store is the connector alone (" + store + ")");
		long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
		long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
		long picks = SupplyContainers.count(level, store, Items.IRON_PICKAXE);
		Showcase.check(cobble == 64 && planks == 48 && picks == 1, "each item counts once: " + cobble + " cobblestone, " + planks
			+ " planks (on the cable), " + picks + " pickaxe (in the filing cabinet)");
		Showcase.check(StorageNetworks.members(level, CONNECTOR).size() == 3, "the connector joins two chests and the filing cabinet");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static Block toms(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("toms_storage", id));
	}

	private static BlockState turned(BlockState state, Direction side) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("facing") && property.getValueClass() == Direction.class) {
				@SuppressWarnings("unchecked")
				Property<Direction> p = (Property<Direction>) property;
				if (p.getPossibleValues().contains(side)) {
					return state.setValue(p, side);
				}
			}
		}
		return state;
	}

	private static Direction facing(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("facing") && state.getValue(property) instanceof Direction d) {
				return d;
			}
		}
		return Direction.SOUTH;
	}
}
