package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.assertCount;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.joinUp;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.put;
import static io.github.jcondedata.aliveworkplace.compat.QaTomsNetworks1108cCompatTests.rel;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
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
 * QA (2026-11-17), a failing test for ROADMAP 37.10 (not registered: it fails on today's code). A player breaks a cable
 * in the middle of a Tom's network: the Storehouse at once lists the unplugged chest as its own store (its own walk of
 * the cables), but still reads the connector's item storage, which Tom's connector only rescans 5 to 20 ticks later. For
 * that window the unplugged chest's items count twice (46 planks of 23), against "each item counted once". The connector
 * broken outright is fine at once ({@code QaTomsNetworks1108cCompatTests#theConnectorBrokenByAPlayerFreesItsChests}).
 */
public class QaTomsCables1117cBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '200' '"qa_toms_cut_bug_1117c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_cut_bug_1117c")
	public void rightAfterACableIsCutEachItemCountsOnce(GameTestHelper helper) {
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
		BlockPos cut = helper.absolutePos(cables.get(1));
		helper.runAfterDelay(40, () -> {
			assertCount(helper, SupplyContainers.find(level, at, null), Items.OAK_PLANKS, 23);
			level.destroyBlock(cut, true); // as a player's breaking it
			level.getEntitiesOfClass(ItemEntity.class, new AABB(cut).inflate(3)).forEach(Entity::discard);
			List<BlockPos> after = SupplyContainers.find(level, at, null);
			helper.assertTrue(after.size() == 2 && after.contains(helper.absolutePos(far)), "right after the cut, the store: " + rel(helper, after));
			assertCount(helper, after, Items.OAK_PLANKS, 23);
			assertCount(helper, after, Items.COBBLESTONE, 11);
			helper.succeed();
		});
	}
}
