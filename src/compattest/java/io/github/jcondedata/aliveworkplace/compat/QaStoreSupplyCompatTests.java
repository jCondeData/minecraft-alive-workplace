package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (tester, 2026-10-24), written from the specs of B371 and B305: the adversarial cases their fixes' own tests leave
 * out. B371: the room behind a Sophisticated controller is the real room with two kinds of goods stored, agrees with the
 * barrels read one by one, and is exactly what goes in. B305: with two Tom's connectors on one network, either one
 * broken mid-way leaves every chest counted once (never twice, never lost); a passed-over connector's other inventory
 * stays a store of its own.
 */
public class QaStoreSupplyCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * B371: a controller links a barrel with a stack upgrade (tier 2) and a plain barrel; 100 cobblestone and 300 oak planks
	 * go in through it. The Storehouse's room (and the board's line) is the empty room less 400, the same as the two
	 * barrels read on their own; filling with cobblestone then planks puts exactly that much in, after which the room is
	 * 0. With the controller broken, the barrels on their own report no room and hold everything.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_store_controller_mixed"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_store_controller_mixed")
	public void theRoomBehindAControllerWithTwoKindsOfGoodsIsTheRealRoom(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos upgraded = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos plain = helper.absolutePos(new BlockPos(5, 2, 4));
		List<BlockPos> barrels = List.of(upgraded, plain);
		level.setBlockAndUpdate(upgraded, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		level.setBlockAndUpdate(plain, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		player.teleportTo(upgraded.getX() + 0.5, upgraded.getY(), upgraded.getZ() + 2.5);
		ItemStack upgrade = new ItemStack(SophisticatedStorageCompatTests.item("stack_upgrade_tier_2"));
		player.setItemInHand(InteractionHand.MAIN_HAND, upgrade);
		player.setShiftKeyDown(true);
		Direction front = SophisticatedStorageCompatTests.front(level.getBlockState(upgraded));
		player.gameMode.useItemOn(player, level, upgrade, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(upgraded).relative(front, 0.5), front, upgraded, false));
		player.setShiftKeyDown(false);
		helper.assertTrue(SupplyContainers.room(level, List.of(upgraded)).freeItems() == 27L * 256,
			"the stack upgrade isn't in the barrel: " + SupplyContainers.room(level, List.of(upgraded)));
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, SophisticatedControllerCompatTests.block("controller"), player);
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		helper.assertTrue(store.equals(List.of(controller)), "the Storehouse's stores with a controller: " + store);

		long empty = 27L * 256 + 27L * 64;
		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 100)).isEmpty(), "the controller took no cobblestone");
		for (int i = 0; i < 5; i++) {
			helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.OAK_PLANKS, 60)).isEmpty(), "the controller took no planks");
		}
		long wanted = empty - 400;
		ItemStores.Room room = SupplyContainers.room(level, store);
		helper.assertTrue(room.freeItems() == wanted, "with 100 cobblestone and 300 planks the room is " + room + ", wanted " + wanted);
		ItemStores.Room alone = SupplyContainers.room(level, barrels);
		helper.assertTrue(alone.freeItems() == room.freeItems(), "through the controller " + room.freeItems() + ", the barrels alone " + alone.freeItems());
		Component line = SophisticatedStorageCompatTests.roomLine(SophisticatedStorageCompatTests.info(player, storehouse, helper));
		helper.assertTrue(line != null && arg(line).equals(String.valueOf(wanted)), "the board's room line: " + line + ", wanted " + wanted);

		long in = fill(level, store, Items.COBBLESTONE, (int) wanted) + fill(level, store, Items.OAK_PLANKS, (int) wanted);
		helper.assertTrue(in == wanted, in + " went in after the room said " + wanted);
		helper.assertTrue(SupplyContainers.room(level, store).freeItems() == 0, "full, the room is " + SupplyContainers.room(level, store));
		long held = SupplyContainers.count(level, barrels, Items.COBBLESTONE) + SupplyContainers.count(level, barrels, Items.OAK_PLANKS);
		helper.assertTrue(held == empty, "the barrels hold " + held + " of " + empty + ": a put was reported but went nowhere");

		level.destroyBlock(controller, false);
		helper.assertTrue(SupplyContainers.room(level, barrels).freeItems() == 0, "without the controller the barrels say " + SupplyContainers.room(level, barrels));
		helper.succeed();
	}

	/**
	 * B305: two connectors on one network, the one the Storehouse reads it through broken: the other one now stands for
	 * what it still joins, the chest that touched only the broken one becomes a store of its own, and every item still
	 * counts once. The second connector broken too: the chests are stores of their own, each counted once.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_store_toms_broken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_store_toms_broken")
	public void aConnectorBrokenOffATwoConnectorNetworkLosesAndDoublesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		TomsStorageCompatTests.network(helper, false);
		BlockPos second = new BlockPos(14, 2, 14);
		helper.setBlock(second, TomsStorageCompatTests.block("inventory_connector"));
		BlockPos at = helper.absolutePos(second);
		level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);
		put(helper, TomsStorageCompatTests.CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, TomsStorageCompatTests.CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(TomsStorageCompatTests.STOREHOUSE);
		BlockPos first = helper.absolutePos(TomsStorageCompatTests.CONNECTOR);
		BlockPos[] broken = new BlockPos[1];
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.size() == 1 && (store.contains(at) || store.contains(first)), "before: the store is " + rel(helper, store));
			countsOnce(helper, store, "two connectors");
			broken[0] = store.get(0);
			level.destroyBlock(broken[0], false);
		});
		helper.runAfterDelay(80, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertFalse(store.contains(broken[0]), "the broken connector is still a store");
			helper.assertTrue(store.contains(broken[0].equals(at) ? first : at), "the remaining connector isn't a store: " + rel(helper, store));
			countsOnce(helper, store, "with " + helper.relativePos(broken[0]) + " broken, stores " + rel(helper, store));
			level.destroyBlock(broken[0].equals(at) ? first : at, false);
		});
		helper.runAfterDelay(120, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.contains(helper.absolutePos(TomsStorageCompatTests.CHEST_1)) && store.contains(helper.absolutePos(TomsStorageCompatTests.CHEST_2)),
				"with both connectors broken the chests aren't stores: " + rel(helper, store));
			countsOnce(helper, store, "both connectors broken, stores " + rel(helper, store));
			helper.succeed();
		});
	}

	/**
	 * B305, the other side of the rule: a second connector off the cables touches the network's first chest and a barrel of
	 * its own. It is passed over (the shared chest is read through the network's connector, once), but its barrel stays a
	 * store of its own: what the barrel holds is counted once and can be taken.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_store_toms_own_barrel"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_store_toms_own_barrel")
	public void aPassedOverConnectorsOwnBarrelIsStillCounted(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper);
		ServerLevel level = helper.getLevel();
		TomsStorageCompatTests.network(helper, false);
		BlockPos second = TomsStorageCompatTests.CHEST_1.south();
		BlockPos barrel = second.west();
		helper.setBlock(second, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(barrel, Blocks.BARREL);
		put(helper, TomsStorageCompatTests.CHEST_1, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, TomsStorageCompatTests.CHEST_2, new ItemStack(Items.OAK_PLANKS, 20));
		put(helper, barrel, new ItemStack(Items.DIAMOND, 5));
		BlockPos storehouse = helper.absolutePos(TomsStorageCompatTests.STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.contains(helper.absolutePos(TomsStorageCompatTests.CONNECTOR)), "the network's connector isn't a store: " + rel(helper, store));
			assertCount(helper, store, Items.COBBLESTONE, 10);
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			assertCount(helper, store, Items.DIAMOND, 5);
			int taken = SupplyContainers.extract(level, store, Items.DIAMOND, 5);
			helper.assertTrue(taken == 5, "took " + taken + " of the barrel's 5 diamonds");
			assertCount(helper, List.of(helper.absolutePos(barrel)), Items.DIAMOND, 0);
			helper.setBlock(second, Blocks.AIR);
			helper.succeed();
		});
	}

	private static void countsOnce(GameTestHelper helper, List<BlockPos> store, String when) {
		helper.assertTrue(new HashSet<>(store).size() == store.size(), when + ": a store listed twice");
		long cobble = SupplyContainers.count(helper.getLevel(), store, Items.COBBLESTONE);
		long planks = SupplyContainers.count(helper.getLevel(), store, Items.OAK_PLANKS);
		helper.assertTrue(cobble == 10 && planks == 20, when + ": counts " + cobble + " cobblestone and " + planks + " planks, wanted 10 and 20");
	}

	/** Puts {@code max} of {@code item} into the store a stack at a time, as a porter does, and returns what went in. */
	private static long fill(ServerLevel level, List<BlockPos> store, Item item, int max) {
		long in = 0;
		while (in < max) {
			int ask = (int) Math.min(64, max - in);
			ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(item, ask));
			in += ask - rest.getCount();
			if (!rest.isEmpty()) {
				break;
			}
		}
		return in;
	}

	private static void networks(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
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

	private static String arg(Component line) {
		return String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]);
	}
}
