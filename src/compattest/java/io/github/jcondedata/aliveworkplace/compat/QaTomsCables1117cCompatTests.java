package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.assertCount;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.joinUp;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.put;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.rel;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.toms;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-17) for ROADMAP 37.10, Tom's Simple Storage as the Storehouse's source, written from the spec: the
 * connector is "one store for everything it connects (the inventories touching it and those on its inventory_cable,
 * inventory_cable_connector and framed cables)", "read and filled through the connector's item storage", "the connected
 * inventories drop out of the list"; "a chest unplugged from the network becomes its own store again"; a builder gets
 * what's asked (and never more than there is); filing cabinets stay stores of their own. The oracle is what a player
 * would count by opening every chest: each item exactly once, nothing lost, nothing made.
 */
public class QaTomsCables1117cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	private static BlockState tomsBlock(String id) {
		return TomsStorageCompatTests.block(id).defaultBlockState();
	}

	private static BlockState facing(String id, Direction side) {
		return TomsStorageCompatTests.facing(tomsBlock(id), side);
	}

	/**
	 * A long run of cables, part plain and part framed, carries the network 25 blocks out from the Storehouse, far past
	 * its reach, to a framed cable connector facing a chest; a plain cable connector on the way faces a chest in reach.
	 * The Storehouse lists only the connector (both chests on the cables and the one touching it drop out); every item
	 * in all three counts once, the far chest's too; a chest far away on no network counts nothing; and the builder's
	 * withdrawal of the far chest's planks takes exactly them through the connector.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_toms_long_1117c"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_toms_long_1117c")
	public void aLongFramedCableRunJoinsAFarChestOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		BlockPos connector = new BlockPos(4, 2, 2);
		BlockPos touching = new BlockPos(4, 2, 1);
		BlockPos branch = new BlockPos(8, 2, 3); // a cable connector off the run, facing south at a chest in reach
		BlockPos nearChest = new BlockPos(8, 2, 4);
		BlockPos end = new BlockPos(27, 2, 2); // a framed cable connector facing east at the far chest
		BlockPos farChest = new BlockPos(28, 2, 2);
		BlockPos stray = new BlockPos(28, 2, 8); // on no network, far from the Storehouse
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, tomsBlock("inventory_connector"));
		helper.setBlock(touching, Blocks.CHEST);
		List<BlockPos> run = new ArrayList<>();
		for (int x = 5; x <= 26; x++) {
			BlockPos p = new BlockPos(x, 2, 2);
			helper.setBlock(p, tomsBlock(x >= 12 && x <= 20 ? "inventory_cable_framed" : "inventory_cable"));
			run.add(p);
		}
		helper.setBlock(branch, facing("inventory_cable_connector", Direction.SOUTH));
		helper.setBlock(nearChest, Blocks.CHEST);
		helper.setBlock(end, facing("inventory_cable_connector_framed", Direction.EAST));
		helper.setBlock(farChest, Blocks.CHEST);
		helper.setBlock(stray, Blocks.CHEST);
		List<BlockPos> joins = new ArrayList<>(run);
		joins.add(branch);
		joins.add(end);
		joins.add(connector);
		joinUp(helper, joins.toArray(BlockPos[]::new));
		put(helper, touching, new ItemStack(Items.SAND, 5));
		put(helper, nearChest, new ItemStack(Items.COBBLESTONE, 7));
		put(helper, farChest, new ItemStack(Items.OAK_PLANKS, 40));
		put(helper, stray, new ItemStack(Items.OAK_PLANKS, 9));
		BlockPos at = helper.absolutePos(storehouse);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))),
				"the store: " + rel(helper, store) + ", wanted only the connector");
			Set<BlockPos> members = StorageNetworks.members(level, helper.absolutePos(connector));
			helper.assertTrue(members.contains(helper.absolutePos(farChest)) && members.contains(helper.absolutePos(nearChest))
				&& members.contains(helper.absolutePos(touching)), "the connector joins " + rel(helper, List.copyOf(members))
				+ ", wanted the touching chest, the chest on the plain cable connector and the far chest on the framed one");
			assertCount(helper, store, Items.SAND, 5);
			assertCount(helper, store, Items.COBBLESTONE, 7);
			assertCount(helper, store, Items.OAK_PLANKS, 40); // the far chest's, once; never the stray chest's
			helper.assertTrue(toms(helper, connector, Items.OAK_PLANKS) == 40, "Tom's connector shows "
				+ toms(helper, connector, Items.OAK_PLANKS) + " planks, wanted the far chest's 40");
			int taken = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 40);
			helper.assertTrue(taken == 40, "the builder took " + taken + " planks through the connector, wanted 40");
			long left = ((Container) level.getBlockEntity(helper.absolutePos(farChest))).countItem(Items.OAK_PLANKS);
			helper.assertTrue(left == 0, "the far chest still holds " + left + " planks");
			long strayLeft = ((Container) level.getBlockEntity(helper.absolutePos(stray))).countItem(Items.OAK_PLANKS);
			helper.assertTrue(strayLeft == 9, "the stray chest on no network lost planks: " + strayLeft);
			helper.succeed();
		});
	}

	/**
	 * A player breaks a cable in the middle of the run: the chest beyond the break (in the Storehouse's reach) is
	 * unplugged and becomes its own store, the chest touching the connector stays joined, and each item still counts
	 * once at the very next look and after Tom's connector has rescanned.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_cut_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_cut_1117c")
	public void aCableCutMidwayUnplugsTheFarChest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos connector = new BlockPos(3, 2, 8);
		BlockPos touching = new BlockPos(3, 2, 9);
		List<BlockPos> cables = List.of(new BlockPos(3, 2, 7), new BlockPos(3, 2, 6), new BlockPos(3, 2, 5));
		BlockPos cableConnector = new BlockPos(3, 2, 4);
		BlockPos far = new BlockPos(3, 2, 3);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, tomsBlock("inventory_connector"));
		helper.setBlock(touching, Blocks.CHEST);
		cables.forEach(c -> helper.setBlock(c, tomsBlock("inventory_cable")));
		helper.setBlock(cableConnector, facing("inventory_cable_connector", Direction.NORTH));
		helper.setBlock(far, Blocks.CHEST);
		joinUp(helper, cables.get(0), cables.get(1), cables.get(2), cableConnector, connector);
		put(helper, touching, new ItemStack(Items.COBBLESTONE, 11));
		put(helper, far, new ItemStack(Items.OAK_PLANKS, 23));
		BlockPos at = helper.absolutePos(storehouse);
		BlockPos cut = helper.absolutePos(cables.get(1));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> before = SupplyContainers.find(level, at, null);
			helper.assertTrue(before.equals(List.of(helper.absolutePos(connector))), "before the cut, the store: " + rel(helper, before));
			assertCount(helper, before, Items.OAK_PLANKS, 23);
			assertCount(helper, before, Items.COBBLESTONE, 11);

			level.destroyBlock(cut, true);
			level.getEntitiesOfClass(ItemEntity.class, new AABB(cut).inflate(3)).forEach(Entity::discard);
			// The very next look: the unplugged chest is its own store (the double count until Tom's connector rescans
			// is QaTomsCables1117cBugCompatTests's).
			List<BlockPos> after = SupplyContainers.find(level, at, null);
			helper.assertTrue(after.size() == 2 && after.contains(helper.absolutePos(connector)) && after.contains(helper.absolutePos(far)),
				"right after the cut, the store: " + rel(helper, after) + ", wanted the connector and the unplugged chest");
			helper.succeedWhen(() -> {
				List<BlockPos> later = SupplyContainers.find(level, at, null);
				helper.assertTrue(later.size() == 2 && later.contains(helper.absolutePos(far)), "after the cut, the store: " + rel(helper, later));
				assertCount(helper, later, Items.OAK_PLANKS, 23);
				assertCount(helper, later, Items.COBBLESTONE, 11);
				helper.assertTrue(toms(helper, connector, Items.OAK_PLANKS) == 0, "Tom's connector still shows the unplugged chest's planks: "
					+ toms(helper, connector, Items.OAK_PLANKS));
				// A delivery of planks into the store is counted once.
				helper.assertTrue(SupplyContainers.insert(level, later, new ItemStack(Items.OAK_PLANKS, 5)).isEmpty(), "the store took no planks");
				assertCount(helper, SupplyContainers.find(level, at, null), Items.OAK_PLANKS, 28);
			});
		});
	}

	/**
	 * The builder asks for 50 planks and the network holds 30, split over two chests: it gets the 30, no more, both
	 * chests are left empty of planks, and asking again gets nothing. Nothing drops on the ground.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_short_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_short_1117c")
	public void askingForMoreThanTheNetworkHoldsGivesOnlyWhatIsThere(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos connector = new BlockPos(8, 2, 11);
		BlockPos a = new BlockPos(8, 2, 12);
		BlockPos cable = new BlockPos(9, 2, 11);
		BlockPos cableConnector = new BlockPos(10, 2, 11);
		BlockPos b = new BlockPos(11, 2, 11);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, tomsBlock("inventory_connector"));
		helper.setBlock(a, Blocks.CHEST);
		helper.setBlock(cable, tomsBlock("inventory_cable"));
		helper.setBlock(cableConnector, facing("inventory_cable_connector", Direction.EAST));
		helper.setBlock(b, Blocks.CHEST);
		joinUp(helper, cable, cableConnector, connector);
		put(helper, a, new ItemStack(Items.OAK_PLANKS, 20));
		put(helper, b, new ItemStack(Items.OAK_PLANKS, 10));
		put(helper, b, new ItemStack(Items.COBBLESTONE, 4));
		BlockPos at = helper.absolutePos(storehouse);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + rel(helper, store));
			int taken = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 50);
			helper.assertTrue(taken == 30, "asked for 50 planks of 30, got " + taken);
			List<BlockPos> chests = List.of(helper.absolutePos(a), helper.absolutePos(b));
			assertCount(helper, chests, Items.OAK_PLANKS, 0);
			assertCount(helper, chests, Items.COBBLESTONE, 4); // the other goods untouched
			int again = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 50);
			helper.assertTrue(again == 0, "asked again for planks of none, got " + again);
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "items were dropped on the ground");
			helper.succeed();
		});
	}

	/**
	 * A porter's delivery of 100 cobblestone into a network with room for 40, and no other store: 40 go in, the other 60
	 * come back to the porter, none lost and none made; the network's count rises by exactly 40.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_room_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_room_1117c")
	public void aNearlyFullNetworkTakesWhatFitsAndGivesTheRestBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos connector = new BlockPos(8, 2, 11);
		BlockPos chest = new BlockPos(8, 2, 12);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, tomsBlock("inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		joinUp(helper, connector);
		Container box = (Container) level.getBlockEntity(helper.absolutePos(chest));
		for (int slot = 0; slot < 26; slot++) {
			box.setItem(slot, new ItemStack(Items.DIRT, 64));
		}
		box.setItem(26, new ItemStack(Items.COBBLESTONE, 24)); // room for 40 cobblestone, none for anything else
		BlockPos at = helper.absolutePos(storehouse);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + rel(helper, store));
			ItemStack rest1 = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 64));
			ItemStack rest2 = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 36));
			int back = rest1.getCount() + rest2.getCount();
			helper.assertTrue(back == 60, back + " cobblestone came back of 100, wanted the 60 there was no room for");
			helper.assertTrue(box.countItem(Items.COBBLESTONE) == 64, "the chest holds " + box.countItem(Items.COBBLESTONE) + " cobblestone, wanted 64");
			assertCount(helper, store, Items.COBBLESTONE, 64);
			assertCount(helper, store, Items.DIRT, 26 * 64);
			ItemStack sand = SupplyContainers.insert(level, store, new ItemStack(Items.SAND, 10));
			helper.assertTrue(sand.getCount() == 10, "a full network took " + (10 - sand.getCount()) + " sand");
			helper.succeed();
		});
	}

	/**
	 * A filing cabinet by the Storehouse on no network stays a store of its own: listed, its goods counted, a delivery
	 * of one more sword going into it; beside it a connector with a chest is one more store, nothing counted twice.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_cabinet_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_cabinet_1117c")
	public void aLoneFilingCabinetIsAStoreOfItsOwn(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos cabinet = new BlockPos(11, 2, 8);
		BlockPos connector = new BlockPos(5, 2, 8);
		BlockPos chest = new BlockPos(4, 2, 8);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(cabinet, tomsBlock("filing_cabinet"));
		helper.setBlock(connector, tomsBlock("inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		joinUp(helper, connector);
		put(helper, cabinet, new ItemStack(Items.IRON_SWORD));
		put(helper, cabinet, new ItemStack(Items.IRON_SWORD));
		put(helper, chest, new ItemStack(Items.IRON_SWORD));
		BlockPos at = helper.absolutePos(storehouse);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.size() == 2 && store.contains(helper.absolutePos(cabinet)) && store.contains(helper.absolutePos(connector)),
				"the store: " + rel(helper, store) + ", wanted the filing cabinet and the connector");
			assertCount(helper, store, Items.IRON_SWORD, 3);
			long tomsTakes;
			try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction t = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
				var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, helper.absolutePos(cabinet), null);
				tomsTakes = storage == null ? -1 : storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.IRON_SWORD), 1, t);
			}
			ItemStack rest = SupplyContainers.insert(level, List.of(helper.absolutePos(cabinet)), new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(rest.isEmpty(), "the filing cabinet took no sword (Tom's own storage would take " + tomsTakes + ", block entity "
				+ level.getBlockEntity(helper.absolutePos(cabinet)) + ")");
			assertCount(helper, SupplyContainers.find(level, at, null), Items.IRON_SWORD, 4);
			helper.succeed();
		});
	}

	/**
	 * Two separate networks by one Storehouse, each a connector with its own chests (one through a framed cable and a
	 * framed cable connector): the store lists both connectors and no chest of theirs; each network's goods count once;
	 * a withdrawal larger than either network holds takes from both, and exactly what was there.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_two_nets_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_two_nets_1117c")
	public void twoSeparateNetworksAreTwoStoresEachCountedOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos west = new BlockPos(4, 2, 8);
		BlockPos westChest = new BlockPos(3, 2, 8);
		BlockPos east = new BlockPos(12, 2, 8);
		BlockPos framed = new BlockPos(13, 2, 8);
		BlockPos framedConnector = new BlockPos(14, 2, 8);
		BlockPos eastChest = new BlockPos(15, 2, 8);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(west, tomsBlock("inventory_connector"));
		helper.setBlock(westChest, Blocks.CHEST);
		helper.setBlock(east, tomsBlock("inventory_connector"));
		helper.setBlock(framed, tomsBlock("inventory_cable_framed"));
		helper.setBlock(framedConnector, facing("inventory_cable_connector_framed", Direction.EAST));
		helper.setBlock(eastChest, Blocks.CHEST);
		joinUp(helper, framed, framedConnector, west, east);
		put(helper, westChest, new ItemStack(Items.OAK_PLANKS, 25));
		put(helper, eastChest, new ItemStack(Items.OAK_PLANKS, 30));
		BlockPos at = helper.absolutePos(storehouse);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.size() == 2 && store.contains(helper.absolutePos(west)) && store.contains(helper.absolutePos(east)),
				"the store: " + rel(helper, store) + ", wanted the two connectors");
			assertCount(helper, store, Items.OAK_PLANKS, 55);
			helper.assertTrue(toms(helper, east, Items.OAK_PLANKS) == 30, "the east connector shows " + toms(helper, east, Items.OAK_PLANKS)
				+ " planks through its framed cable, wanted 30");
			int taken = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 40);
			helper.assertTrue(taken == 40, "took " + taken + " planks of 55, asked for 40");
			assertCount(helper, List.of(helper.absolutePos(westChest), helper.absolutePos(eastChest)), Items.OAK_PLANKS, 15);
			int rest = SupplyContainers.extract(level, store, Items.OAK_PLANKS, 40);
			helper.assertTrue(rest == 15, "took " + rest + " planks of the 15 left, asked for 40");
			helper.succeed();
		});
	}
}
