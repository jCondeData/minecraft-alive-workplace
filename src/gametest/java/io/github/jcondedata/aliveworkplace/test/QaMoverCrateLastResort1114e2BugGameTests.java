package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/**
 * QA (lane e, 2026-11-14 evening), B1249's fix in MoveSite.dropPacked's last resort: a beehive with 2 bees whose new spot
 * is the Mover's Crate in a storage room (a barrel on every spot within 2, stone up the column) is dropped as an empty
 * beehive and both bees are gone (0 of 2 anywhere). With room round the crate the same hive keeps its bees in its item
 * (QaMoverCrateBorrow1114c3BugGameTests). The last resort's {@code BlockEntity.loadStatic(at, state, data, ...)} returns
 * null for every block: the packed data has no {@code id}, so the log says "Skipping BlockEntity with id " and the drops
 * are worked out with no block entity (no bees, no copied components).
 */
public class QaMoverCrateLastResort1114e2BugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A beehive with 2 bees, the crate in a storage room: one beehive, and exactly its 2 bees (none lost, none doubled). */
	//$ gametest_batch AREA '"qaE1114e2StoreHive"'
	@GameTest(template = AREA, batch = "qaE1114e2StoreHive")
	public void aBeehiveOnACrateAmongContainersKeepsExactlyItsBees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = QaMoverCrateHolds1114cGameTests.crateInside(helper, "qae2storehive");
		QaMoverCrateHolds1114cGameTests.replaceChest(helper, Blocks.BEEHIVE.defaultBlockState(), be -> {
			for (int i = 0; i < 2; i++) {
				Bee bee = EntityType.BEE.create(level);
				bee.moveTo(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1, be.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(bee);
				((BeehiveBlockEntity) be).addOccupant(bee);
			}
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateLastResort1114e2GameTests.bees(helper) == 2, "setup: " + QaMoverCrateLastResort1114e2GameTests.bees(helper) + " bees, not the hive's 2");
			QaMoverCrateLastResort1114e2GameTests.dropInTheStoreRoom(helper, s);
			QaMoverCrateEdges1114bGameTests.assertExactly(helper, Items.BEEHIVE, 1, "the beehive (storage room)");
			int now = QaMoverCrateLastResort1114e2GameTests.bees(helper);
			helper.assertTrue(now == 2, "the hive's bees (storage room): " + now + " of 2 anywhere (" + (now < 2 ? "lost" : "doubled") + ")");
			helper.succeed();
		});
	}

}
