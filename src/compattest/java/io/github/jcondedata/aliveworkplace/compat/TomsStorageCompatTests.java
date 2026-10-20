package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.HashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * ROADMAP 37.10: a Tom's Simple Storage inventory connector by the Storehouse is one store for every inventory it joins
 * (touching it, or faced by a cable connector on its cables), read and filled through its own item storage (Fabric's
 * transfer API, block ids only, never the mod's classes); the joined inventories drop out of the store list, so each
 * item counts once. A storage terminal on the connector shows it all with Tom's own screen.
 */
public class TomsStorageCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String MOD = "toms_storage";

	// The network, in the area's far corner (more than 8 blocks from the builder's bench at 2,2,2).
	static final BlockPos STOREHOUSE = new BlockPos(15, 2, 15);
	static final BlockPos CONNECTOR = new BlockPos(12, 2, 13);
	static final BlockPos TERMINAL = new BlockPos(11, 2, 13);
	static final BlockPos CHEST_1 = new BlockPos(12, 2, 14); // touching the connector
	static final List<BlockPos> CABLES = List.of(new BlockPos(13, 2, 13), new BlockPos(14, 2, 13));
	static final BlockPos CABLE_CONNECTOR_1 = new BlockPos(15, 2, 13); // facing east, at the second chest
	static final BlockPos CHEST_2 = new BlockPos(16, 2, 13);
	static final BlockPos CABLE_CONNECTOR_2 = new BlockPos(14, 2, 12); // facing north, at the filing cabinet
	static final BlockPos CABINET = new BlockPos(14, 2, 11);
	/** A chest by the Storehouse on no network: its own store. */
	static final BlockPos LONE_CHEST = new BlockPos(16, 2, 16);

	/** Builds the network (and the Storehouse); Tom's connector finds its inventories on its own next ticks. */
	static void network(GameTestHelper helper, boolean loneChest) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CONNECTOR, block("inventory_connector"));
		helper.setBlock(CHEST_1, Blocks.CHEST);
		for (BlockPos cable : CABLES) {
			helper.setBlock(cable, block("inventory_cable"));
		}
		helper.setBlock(CABLE_CONNECTOR_1, facing(block("inventory_cable_connector").defaultBlockState(), Direction.EAST));
		helper.setBlock(CHEST_2, Blocks.CHEST);
		helper.setBlock(CABLE_CONNECTOR_2, facing(block("inventory_cable_connector").defaultBlockState(), Direction.NORTH));
		helper.setBlock(CABINET, block("filing_cabinet"));
		// Tom's terminal faces the block it reads (its screen on the far side): east, at the connector.
		helper.setBlock(TERMINAL, facing(block("storage_terminal").defaultBlockState(), Direction.EAST));
		if (loneChest) {
			helper.setBlock(LONE_CHEST, Blocks.CHEST);
		}
		// The cables join up as when a player places them (their connection states from their neighbours).
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : List.of(CABLES.get(0), CABLES.get(1), CABLE_CONNECTOR_1, CABLE_CONNECTOR_2, CONNECTOR, TERMINAL)) {
			BlockPos at = helper.absolutePos(rel);
			level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);
		}
	}

	/**
	 * Two chests and a filing cabinet on the connector and its cable: the Storehouse lists the connector and the lone
	 * chest, not the three inventories it joins; each item counts once; the board says how many blocks the connector
	 * links; what goes into the store lands in the network and comes back out of it. With the switch off, every chest
	 * is its own store again and the connector is passed over.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_store"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_store")
	public void aConnectorIsOneStoreForItsChestsAndCabinet(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, true);
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		for (int i = 0; i < 3; i++) { // a filing cabinet holds things that don't stack, one a slot
			put(helper, CABINET, new ItemStack(Items.IRON_SWORD));
		}
		put(helper, LONE_CHEST, new ItemStack(Items.COBBLESTONE, 3));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.assertTrue(ItemStorage.SIDED.find(level, helper.absolutePos(CONNECTOR), null) != null, "Tom's connector has no item storage");

		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.size() == 2 && store.contains(helper.absolutePos(CONNECTOR)) && store.contains(helper.absolutePos(LONE_CHEST)),
				"the store: " + rel(helper, store) + ", wanted the connector and the lone chest");
			// Nearest first (a builder takes from the nearest store): the lone chest beside the Storehouse, then the connector.
			helper.assertTrue(store.equals(List.of(helper.absolutePos(LONE_CHEST), helper.absolutePos(CONNECTOR))),
				"the store " + rel(helper, store) + ", wanted the lone chest then the connector, nearest first");
			assertCount(helper, store, Items.COBBLESTONE, 13);
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			assertCount(helper, store, Items.IRON_SWORD, 3);
			helper.assertTrue(StorageNetworks.members(level, helper.absolutePos(CONNECTOR)).size() == 3,
				"the connector joins " + rel(helper, List.copyOf(StorageNetworks.members(level, helper.absolutePos(CONNECTOR)))));

			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
			player.teleportTo(storehouse.getX() + 0.5, storehouse.getY(), storehouse.getZ() - 1.5);
			Component line = connectorLine(info(player, helper));
			helper.assertTrue(line != null && String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]).equals("3"),
				"the board's connector line: " + line);
			helper.assertTrue(Component.translatable("screen.aliveworkplace.storehouse.connector", 3).getString()
				.equals("Linked through an inventory connector: 3 blocks"), "the line reads wrong");

			// Through the connector: in and out once.
			helper.assertTrue(SupplyContainers.insert(level, List.of(helper.absolutePos(CONNECTOR)), new ItemStack(Items.SAND, 30)).isEmpty(),
				"the connector took no sand");
			assertCount(helper, store, Items.SAND, 30);
			List<BlockPos> joined = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
			assertCount(helper, joined, Items.SAND, 30);
			helper.assertTrue(SupplyContainers.extract(level, store, Items.OAK_PLANKS, 12) == 12, "took no planks");
			assertCount(helper, joined, Items.OAK_PLANKS, 8);

			// The switch off: each chest its own store, the connector passed over.
			StorageNetworks.ENABLED = false;
			try {
				List<BlockPos> off = SupplyContainers.find(level, storehouse, null);
				helper.assertFalse(off.contains(helper.absolutePos(CONNECTOR)), "the switch is off, but the connector is a store");
				helper.assertTrue(off.containsAll(joined), "the switch is off: the store " + rel(helper, off));
				assertCount(helper, off, Items.COBBLESTONE, 13);
				helper.assertTrue(connectorLine(info(player, helper)) == null, "the switch is off, but the board has a connector line");
			} finally {
				StorageNetworks.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/**
	 * B192: with the Storehouse reading the connector, a player who right-clicks the storage terminal's screen gets Tom's
	 * own terminal menu (not "Terminal out of range"), and the terminal lists the network's goods, each once.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_terminal"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_terminal")
	public void aPlayerOpensTheTerminalBesideTheStorehouse(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		put(helper, CABINET, new ItemStack(Items.IRON_SWORD));
		BlockPos terminal = helper.absolutePos(TERMINAL);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(STOREHOUSE), null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))), "the store: " + rel(helper, store));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			// Standing two blocks in front of the screen (the terminal's west side), as a player would.
			player.teleportTo(terminal.getX() - 1.5, terminal.getY(), terminal.getZ() + 0.5);
			player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(terminal).relative(Direction.WEST, 0.5),
					Direction.WEST, terminal, false));
			String menu = player.containerMenu.getClass().getName();
			helper.assertTrue(menu.contains("StorageTerminalMenu"), "right-clicking the terminal opened " + menu + ", not Tom's terminal");
			stacks(helper, terminal); // asks the terminal to list its network on its next tick
			helper.runAfterDelay(20, () -> {
				Map<Item, Long> listed = stacks(helper, terminal);
				helper.assertTrue(listed.getOrDefault(Items.COBBLESTONE, 0L) == 10 && listed.getOrDefault(Items.OAK_PLANKS, 0L) == 20
					&& listed.getOrDefault(Items.IRON_SWORD, 0L) == 1, "the terminal lists " + listed + ", wanted 10 cobblestone, 20 planks, 1 sword");
				helper.succeed();
			});
		});
	}

	/** What Tom's terminal lists, by item (read by reflection: the test never compiles against Tom's classes). */
	private static Map<Item, Long> stacks(GameTestHelper helper, BlockPos terminal) {
		Map<Item, Long> out = new HashMap<>();
		try {
			Object be = helper.getLevel().getBlockEntity(terminal);
			helper.assertTrue(be != null, "the terminal has no block entity");
			Map<?, ?> stacks = (Map<?, ?>) be.getClass().getMethod("getStacks").invoke(be);
			for (Object stored : stacks.values()) {
				ItemStack stack = (ItemStack) stored.getClass().getMethod("getStack").invoke(stored);
				long n = (Long) stored.getClass().getMethod("getQuantity").invoke(stored);
				out.merge(stack.getItem(), n, Long::sum);
			}
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't read Tom's terminal: " + e);
		}
		return out;
	}

	/** A chest unplugged from the network (its cable connector broken) is its own store again; nothing counts twice. */
	//$ gametest_ticks_batch AREA '400' '"toms_unplug"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_unplug")
	public void aChestUnpluggedIsItsOwnStoreAgain(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))), "plugged in, the store: " + rel(helper, store));
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			helper.setBlock(CABLE_CONNECTOR_1, Blocks.AIR); // unplugged
			helper.succeedWhen(() -> {
				List<BlockPos> now = SupplyContainers.find(level, storehouse, null);
				helper.assertTrue(now.size() == 2 && now.contains(helper.absolutePos(CHEST_2)) && now.contains(helper.absolutePos(CONNECTOR)),
					"unplugged, the store: " + rel(helper, now));
				assertCount(helper, now, Items.OAK_PLANKS, 20);
				assertCount(helper, now, Items.COBBLESTONE, 10);
			});
		});
	}

	/**
	 * A double chest whose one half a cable connector faces is joined whole: its other half isn't also the Storehouse's
	 * own store, so the store is the connector alone and what lies in the far half counts once.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_double"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_double")
	public void aDoubleChestOnACableCountsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		// CHEST_2 (faced by the cable connector) and the half south of it: one double chest, its front east.
		BlockPos farHalf = CHEST_2.south();
		helper.setBlock(CHEST_2, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.EAST)
			.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
		helper.setBlock(farHalf, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.EAST)
			.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, farHalf, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))), "the store: " + rel(helper, store) + ", wanted the connector alone");
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			assertCount(helper, store, Items.COBBLESTONE, 10);
			helper.succeed();
		});
	}

	/**
	 * A connector standing inside the plot being built (the area a worker's store list leaves out) isn't read as one
	 * store: the chests it would join that stand outside the plot are each their own store again, and the connector,
	 * part of what's being built over, is passed over.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_in_plot"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_in_plot")
	public void aConnectorInsideThePlotIsPassedOver(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		BlockPos connector = helper.absolutePos(CONNECTOR);
		// The plot: the connector and the terminal beside it, nothing else of the network.
		net.minecraft.world.level.levelgen.structure.BoundingBox plot = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			helper.absolutePos(TERMINAL), connector);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> whole = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(whole.equals(List.of(connector)), "outside any plot, the store: " + rel(helper, whole));
			List<BlockPos> store = SupplyContainers.find(level, storehouse, plot);
			helper.assertFalse(store.contains(connector), "the connector inside the plot is a store: " + rel(helper, store));
			List<BlockPos> outside = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
			helper.assertTrue(store.size() == 3 && store.containsAll(outside), "with the connector in the plot, the store: " + rel(helper, store)
				+ ", wanted the two chests and the cabinet");
			assertCount(helper, store, Items.COBBLESTONE, 10);
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			helper.succeed();
		});
	}

	/** A porter carries a miner's raw iron into the Storehouse: through the connector, into the network's chests. */
	//$ gametest_ticks_batch AREA '1600' '"toms_porter"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "toms_porter")
	public void aPorterDeliversIntoTheNetwork(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000); // work hours
		ServerLevel level = helper.getLevel();
		BlockPos minersBench = new BlockPos(2, 2, 2);
		BlockPos minersChest = new BlockPos(2, 2, 4);
		helper.setBlock(minersBench, ModBlocks.MINERS_BENCH);
		helper.setBlock(minersChest, Blocks.CHEST);
		put(helper, minersChest, new ItemStack(Items.RAW_IRON, 40));
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, helper.absolutePos(minersBench));
		network(helper, false);
		put(helper, CHEST_1, new ItemStack(Items.RAW_IRON, 5));
		List<BlockPos> joined = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 15));
		Porters.employ(level, porter, helper.absolutePos(STOREHOUSE));
		helper.succeedWhen(() -> {
			long left = SupplyContainers.count(level, List.of(helper.absolutePos(minersChest)), Items.RAW_IRON);
			long stored = SupplyContainers.count(level, joined, Items.RAW_IRON);
			long seen = SupplyContainers.count(level, SupplyContainers.find(level, helper.absolutePos(STOREHOUSE), null), Items.RAW_IRON);
			helper.assertTrue(left == 0 && stored == 45 && seen == 45, "raw iron: " + left + " left with the miner, " + stored
				+ " in the network's chests, " + seen + " counted at the Storehouse");
			helper.assertTrue(miner.isAlive() && porter.isAlive(), "a worker is gone");
		});
	}

	/**
	 * A builder whose own chest is empty builds a plank wall with planks from the Storehouse's network (a chest on its
	 * cable), taking what it asks for through the connector.
	 */
	//$ gametest_ticks_batch AREA '2400' '"toms_builder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "toms_builder")
	public void theBuilderTakesWhatItNeedsThroughTheConnector(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (BlockPos p : List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 1, 0), new BlockPos(1, 1, 0))) {
			design.put(p, Blocks.OAK_PLANKS.defaultBlockState());
		}
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "toms_wall", design, Map.of());
		network(helper, false);
		put(helper, CHEST_2, new ItemStack(Items.OAK_PLANKS, 10));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 15));
		Porters.employ(level, porter, helper.absolutePos(STOREHOUSE));
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id);
		List<BlockPos> joined = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null,
				"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " missing=" + s.site().missing());
			for (BlockPos p : design.keySet()) {
				helper.assertTrue(level.getBlockState(s.at(p)).is(Blocks.OAK_PLANKS), "at " + p + ": " + level.getBlockState(s.at(p)));
			}
			long planks = SupplyContainers.count(level, joined, Items.OAK_PLANKS)
				+ SupplyContainers.count(level, List.of(helper.absolutePos(CompatGameTests.CHEST)), Items.OAK_PLANKS);
			helper.assertTrue(planks == 6, planks + " planks left of 10 after a wall of 4");
		});
	}

	/**
	 * A block that holds no items, though it has a block entity (an enchanting table on the connector), isn't one of
	 * the inventories the connector joins, and Tom's scan doesn't pass through it: the board still counts the three
	 * blocks, and a chest touching only the table is its own store, its items counted once.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_no_items"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_no_items")
	public void aBlockHoldingNoItemsIsntJoined(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		BlockPos table = CONNECTOR.above();
		BlockPos overTable = table.above();
		helper.setBlock(table, Blocks.ENCHANTING_TABLE);
		helper.setBlock(overTable, Blocks.CHEST);
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, overTable, new ItemStack(Items.OAK_PLANKS, 7));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> members = List.copyOf(StorageNetworks.members(level, helper.absolutePos(CONNECTOR)));
			List<BlockPos> joined = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
			helper.assertTrue(members.size() == 3 && members.containsAll(joined), "the connector joins " + rel(helper, members)
				+ ", wanted the two chests and the cabinet");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.contains(helper.absolutePos(overTable)), "the chest on the table isn't its own store: " + rel(helper, store));
			assertCount(helper, store, Items.OAK_PLANKS, 7);
			assertCount(helper, store, Items.COBBLESTONE, 10);
			helper.succeed();
		});
	}

	/**
	 * Tom's cables run on through a cable connector's other sides: a cable on top of the first cable connector leads to a
	 * third cable connector, whose chest the network joins too. The store is the connector alone, and what lies in that
	 * chest is counted once, through it.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_through"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_through")
	public void aCableRunsOnThroughACableConnector(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		// Above the first cable connector: a cable, then a cable connector facing west at a third chest.
		BlockPos cable = CABLE_CONNECTOR_1.above();
		BlockPos third = cable.above();
		BlockPos chest = third.west();
		helper.setBlock(cable, block("inventory_cable"));
		helper.setBlock(third, facing(block("inventory_cable_connector").defaultBlockState(), Direction.WEST));
		helper.setBlock(chest, Blocks.CHEST);
		for (BlockPos rel : List.of(CABLE_CONNECTOR_1, cable, third)) {
			BlockPos at = helper.absolutePos(rel);
			level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);
		}
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, chest, new ItemStack(Items.GLASS, 9));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> members = List.copyOf(StorageNetworks.members(level, helper.absolutePos(CONNECTOR)));
			helper.assertTrue(members.size() == 4 && members.contains(helper.absolutePos(chest)),
				"the connector joins " + rel(helper, members) + ", wanted the three chests and the cabinet");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))), "the store: " + rel(helper, store) + ", wanted the connector alone");
			assertCount(helper, store, Items.GLASS, 9);
			assertCount(helper, store, Items.COBBLESTONE, 10);
			// Nothing left for later batches' porters and builders to find.
			for (BlockPos p : List.of(chest, third, cable)) {
				helper.setBlock(p, Blocks.AIR);
			}
			helper.succeed();
		});
	}

	/**
	 * Tom's connector scans touching inventories only within its range (16 blocks, a block exactly 16 away is out of it):
	 * in a row of 16 barrels from the connector, it joins the first 15, which it exposes, and not the 16th, which stays a
	 * store of its own.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_range"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_range")
	public void theSixteenthBarrelIsOutOfTheConnectorsRange(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(0, 2, 8);
		helper.setBlock(connector, block("inventory_connector"));
		for (int x = 1; x <= 16; x++) {
			helper.setBlock(new BlockPos(x, 2, 8), Blocks.BARREL);
		}
		put(helper, new BlockPos(15, 2, 8), new ItemStack(Items.GLASS, 3));
		put(helper, new BlockPos(16, 2, 8), new ItemStack(Items.OAK_PLANKS, 5));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> members = List.copyOf(StorageNetworks.members(level, helper.absolutePos(connector)));
			helper.assertTrue(members.size() == 15 && !members.contains(helper.absolutePos(new BlockPos(16, 2, 8))),
				"the connector joins " + members.size() + " barrels: " + rel(helper, members) + ", wanted the 15 within its range");
			// Tom's own connector agrees: it exposes the 15th barrel's glass, not the 16th's planks.
			List<BlockPos> through = List.of(helper.absolutePos(connector));
			assertCount(helper, through, Items.GLASS, 3);
			assertCount(helper, through, Items.OAK_PLANKS, 0);
			// Nothing left for later batches' porters and builders to find.
			for (int x = 0; x <= 16; x++) {
				helper.setBlock(new BlockPos(x, 2, 8), Blocks.AIR);
			}
			helper.succeed();
		});
	}

	/**
	 * Another inventory connector touching one of the network's chests is a network's head, not an inventory: the
	 * connector doesn't join it (nor read on through it), so its members are the two chests and the cabinet, and what
	 * lies in the shared chest is counted once through the connector.
	 */
	//$ gametest_ticks_batch AREA '400' '"toms_own_blocks"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "toms_own_blocks")
	public void anotherConnectorIsntJoined(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		network(helper, false);
		BlockPos second = CHEST_1.south();
		helper.setBlock(second, block("inventory_connector"));
		put(helper, CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> members = List.copyOf(StorageNetworks.members(level, helper.absolutePos(CONNECTOR)));
			List<BlockPos> wanted = List.of(helper.absolutePos(CHEST_1), helper.absolutePos(CHEST_2), helper.absolutePos(CABINET));
			helper.assertTrue(members.size() == 3 && members.containsAll(wanted), "the connector joins " + rel(helper, members)
				+ ", wanted the two chests and the cabinet, not the other connector");
			// Tom's own connector agrees: the chest's cobblestone once.
			assertCount(helper, List.of(helper.absolutePos(CONNECTOR)), Items.COBBLESTONE, 10);
			// Nothing left for later batches' porters and builders to find.
			helper.setBlock(second, Blocks.AIR);
			helper.succeed();
		});
	}

	private static void put(GameTestHelper helper, BlockPos rel, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(helper.absolutePos(rel)), stack.copy());
		helper.assertTrue(rest.isEmpty(), "put " + stack + " in " + helper.getLevel().getBlockState(helper.absolutePos(rel)) + ": " + rest.getCount() + " left over");
	}

	private static void assertCount(GameTestHelper helper, List<BlockPos> store, Item item, long wanted) {
		long n = SupplyContainers.count(helper.getLevel(), store, item);
		helper.assertTrue(n == wanted, n + " " + BuiltInRegistries.ITEM.getKey(item).getPath() + " counted in " + rel(helper, store) + ", wanted " + wanted);
	}

	private static List<BlockPos> rel(GameTestHelper helper, List<BlockPos> list) {
		return list.stream().map(helper::relativePos).toList();
	}

	private static ItemLore info(ServerPlayer player, GameTestHelper helper) {
		ItemLore lore = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore;
	}

	private static Component connectorLine(ItemLore lore) {
		return lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("screen.aliveworkplace.storehouse.connector")).findFirst().orElse(null);
	}

	/** {@code state} turned to face {@code side} (its {@code facing} state, read by name). */
	static BlockState facing(BlockState state, Direction side) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("facing") && property.getValueClass() == Direction.class) {
				@SuppressWarnings("unchecked")
				Property<Direction> p = (Property<Direction>) property;
				if (p.getPossibleValues().contains(side)) {
					return state.setValue(p, side);
				}
			}
		}
		throw new GameTestAssertException("can't face " + side + ": " + state);
	}

	static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, id))
			.orElseThrow(() -> new GameTestAssertException("no block " + MOD + ":" + id));
	}
}
