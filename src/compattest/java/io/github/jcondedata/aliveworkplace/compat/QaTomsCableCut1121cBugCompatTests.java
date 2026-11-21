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
 * A bug QA found checking B1358's fix (lane-c-1121-1555), the fix's own "not covered" note: a chest plugged back into a
 * Tom's network (the cut cable placed again) counts ZERO in the Storehouse's store until Tom's connector looks again (13
 * ticks in the first run): our cable walk already folds the chest into the connector, which doesn't show it yet. Spec
 * (ROADMAP 37.10, B1358): "each item counts once at every tick"; a builder's shortage check and the Hall read none.
 * Fails on the code it was written against; not registered until the fix.
 */
public class QaTomsCableCut1121cBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * The fix's "not covered" note: the cut cable placed again (the chest plugged back into Tom's network) while Tom's
	 * connector hasn't looked again yet. The planks are in storage the whole time, so at every tick they count 23: never 0
	 * (a builder would see a shortage) and never 46.
	 */
	//$ gametest_ticks_batch AREA '240' '"qa_toms_cut_1121c_plug"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qa_toms_cut_1121c_plug")
	public void aChestPluggedBackInCountsOnceAtEveryTick(GameTestHelper helper) {
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
			QaTomsCableCut1121cCompatTests.cut(helper, cables.get(1));
		});
		helper.runAfterDelay(90, () -> {
			// long after Tom's rescan: the far chest is a store of its own; now the cable goes back in
			List<BlockPos> apart = SupplyContainers.find(level, at, null);
			helper.assertTrue(apart.contains(helper.absolutePos(far)) && SupplyContainers.count(level, apart, Items.OAK_PLANKS) == 23,
				"(setup) after the cut the store " + rel(helper, apart) + " counts " + SupplyContainers.count(level, apart, Items.OAK_PLANKS) + " planks");
			helper.setBlock(cables.get(1), TomsStorageCompatTests.block("inventory_cable"));
			joinUp(helper, cables.get(0), cables.get(1), cables.get(2), cableConnector, connector);
			QaTomsCableCut1121cCompatTests.check(helper, storehouse, setup[0], setup[1], 0, off);
		});
		QaTomsCableCut1121cCompatTests.watch(helper, storehouse, 90, setup, off, "after the cable was placed again");
	}
}
