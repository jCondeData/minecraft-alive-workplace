package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.joinUp;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.put;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.rel;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA of B1358's fix (lane-c-1121-1555), from the spec: with Tom's Simple Storage as the Storehouse's source, "each item
 * counts once at every tick", also in the second or so after a cable is cut while Tom's connector still shows the cut-off
 * chest. QaTomsCables1117cBugCompatTests checks the count once, right after the cut; these check every tick until well
 * after Tom's rescan, every read the Hall and a builder use (count, contents, the count of a stack, slots, free slots,
 * room), a layout where the cut-off chest is nearer the Storehouse than the connector (it comes first in the store) (the chest plugged back in: QaTomsCableCut1121cBugCompatTests).
 */
public class QaTomsCableCut1121cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	static final int WATCH = 45; // ticks: over two of Tom's 20-tick rescans

	/** The store's readings at one tick, or what was off. */
	static void check(GameTestHelper helper, BlockPos storehouse, int slots, int free, int tick, List<String> off) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
		long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
		long matching = SupplyContainers.countMatching(level, store, new ItemStack(Items.OAK_PLANKS));
		Map<net.minecraft.world.item.Item, Long> contents = SupplyContainers.contents(level, store);
		int s = SupplyContainers.slots(level, store);
		int f = SupplyContainers.freeSlots(level, store);
		ItemStores.Room room = SupplyContainers.room(level, store);
		if (planks != 23 || cobble != 11 || matching != 23 || contents.getOrDefault(Items.OAK_PLANKS, 0L) != 23
			|| contents.getOrDefault(Items.COBBLESTONE, 0L) != 11 || s != slots || f != free || room.freeSlots() != free) {
			off.add("tick " + tick + " store " + rel(helper, store) + ": planks " + planks + "/matching " + matching + "/contents "
				+ contents.get(Items.OAK_PLANKS) + ", cobblestone " + cobble + "/contents " + contents.get(Items.COBBLESTONE) + ", slots " + s
				+ ", free " + f + ", room's free " + room.freeSlots() + " (wanted 23, 11, slots " + slots + ", free " + free + ")");
		}
	}

	/**
	 * Schedules a check at every tick from {@code start + 1} to {@code start + WATCH} (from the test's body: a delay added
	 * from inside another one would change the test's schedule while it is read), then a success if nothing was off.
	 * {@code setup} holds the slots and free slots read before the change; {@code off} the check at {@code start} too.
	 */
	static void watch(GameTestHelper helper, BlockPos storehouse, int start, int[] setup, List<String> off, String when) {
		for (int t = 1; t <= WATCH; t++) {
			int tick = t;
			helper.runAfterDelay(start + t, () -> check(helper, storehouse, setup[0], setup[1], tick, off));
		}
		helper.runAfterDelay(start + WATCH + 1, () -> {
			helper.assertTrue(off.isEmpty(), when + ": " + off.size() + " of " + (WATCH + 1) + " ticks counted wrong, first " + (off.isEmpty() ? "" : off.get(0)));
			helper.succeed();
		});
	}

	static void cut(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos cut = helper.absolutePos(rel);
		level.destroyBlock(cut, true); // as a player's breaking it
		level.getEntitiesOfClass(ItemEntity.class, new AABB(cut).inflate(3)).forEach(Entity::discard);
	}

	/**
	 * The bug's layout (a chest at the connector, a cable run to a cable connector at a far chest), the middle cable cut:
	 * at every tick for over two seconds (Tom's rescan inside it) every count reads each item once, and the slots and free
	 * slots are those of the two chests, once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_cut_1121c_ticks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_cut_1121c_ticks")
	public void atEveryTickAfterACableIsCutEveryReadCountsEachItemOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 8);
		BlockPos connector = new BlockPos(3, 2, 8);
		BlockPos touching = new BlockPos(3, 2, 9);
		List<BlockPos> cables = List.of(new BlockPos(3, 2, 7), new BlockPos(3, 2, 6), new BlockPos(3, 2, 5));
		BlockPos cableConnector = new BlockPos(3, 2, 4);
		BlockPos far = new BlockPos(3, 2, 3);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(touching, Blocks.CHEST);
		cables.forEach(c -> helper.setBlock(c, TomsStorageCompatTests.block("inventory_cable")));
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		helper.setBlock(far, Blocks.CHEST);
		joinUp(helper, cables.get(0), cables.get(1), cables.get(2), cableConnector, connector);
		put(helper, touching, new ItemStack(Items.COBBLESTONE, 11));
		put(helper, far, new ItemStack(Items.OAK_PLANKS, 23));
		BlockPos at = helper.absolutePos(storehouse);
		int[] setup = new int[2];
		List<String> off = new ArrayList<>();
		helper.runAfterDelay(40, () -> {
			List<BlockPos> before = SupplyContainers.find(level, at, null);
			setup[0] = SupplyContainers.slots(level, before);
			setup[1] = SupplyContainers.freeSlots(level, before);
			helper.assertTrue(setup[0] == 54 && setup[1] == 52, "(setup) before the cut the store " + rel(helper, before) + " has " + setup[0]
				+ " slots, " + setup[1] + " free; wanted the two chests' 54, 52 free");
			cut(helper, cables.get(1));
			check(helper, storehouse, setup[0], setup[1], 0, off);
		});
		watch(helper, storehouse, 40, setup, off, "after the cable was cut");
	}

	/**
	 * The cut-off chest nearer the Storehouse than the connector (it may come first in the store's list, the connector
	 * after it still showing it): each item still counts once at every tick.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_cut_1121c_near"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_cut_1121c_near")
	public void theCutOffChestNearerTheStorehouseThanTheConnectorCountsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(4, 2, 4);
		BlockPos near = new BlockPos(5, 2, 5); // next to the Storehouse
		BlockPos cableConnector = new BlockPos(5, 2, 6); // faces the near chest (north)
		List<BlockPos> cables = List.of(new BlockPos(5, 2, 7), new BlockPos(5, 2, 8), new BlockPos(5, 2, 9));
		BlockPos connector = new BlockPos(5, 2, 10);
		BlockPos touching = new BlockPos(5, 2, 11);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(near, Blocks.CHEST);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		cables.forEach(c -> helper.setBlock(c, TomsStorageCompatTests.block("inventory_cable")));
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(touching, Blocks.CHEST);
		joinUp(helper, cableConnector, cables.get(0), cables.get(1), cables.get(2), connector);
		put(helper, near, new ItemStack(Items.OAK_PLANKS, 23));
		put(helper, touching, new ItemStack(Items.COBBLESTONE, 11));
		BlockPos at = helper.absolutePos(storehouse);
		int[] setup = new int[2];
		List<String> off = new ArrayList<>();
		helper.runAfterDelay(40, () -> {
			List<BlockPos> before = SupplyContainers.find(level, at, null);
			setup[0] = SupplyContainers.slots(level, before);
			setup[1] = SupplyContainers.freeSlots(level, before);
			helper.assertTrue(SupplyContainers.count(level, before, Items.OAK_PLANKS) == 23 && setup[0] == 54 && setup[1] == 52,
				"(setup) before the cut the store " + rel(helper, before) + " counts " + SupplyContainers.count(level, before, Items.OAK_PLANKS)
					+ " planks, " + setup[0] + " slots, " + setup[1] + " free; wanted 23, 54, 52");
			cut(helper, cables.get(1));
			check(helper, storehouse, setup[0], setup[1], 0, off);
		});
		watch(helper, storehouse, 40, setup, off, "after the cable was cut, the chest nearer the Storehouse");
	}
}
