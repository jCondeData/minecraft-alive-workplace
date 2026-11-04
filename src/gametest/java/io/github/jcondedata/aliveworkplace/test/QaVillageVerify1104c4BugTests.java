package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1104-1254), B784/B814 follow-up: a loaded Hauler Golem that starts right beside a two-wide lava trench a
 * block below the ground (one block of ground between its own block and the lava) steps to the trench's edge, gives its
 * only Storehouse up and stands there with its whole load, though the way round the trench is open (a hauler starting
 * one block farther off goes round and stores it: QaVillageVerify1104c4GameTests). Expected (B784, B450): it keeps
 * clear of the lava, goes round and stores the load. Twice, so one run shows it isn't a fluke.
 */
public class QaVillageVerify1104c4BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int TOP = QaHaulerWaterVerify1102GameTests.TOP;

	/**
	 * A loaded hauler at {@code start} and a two-wide lava trench a block below the ground (x 12..13, z 7..17) between it
	 * and its only Storehouse at {@code store}: on no tick in lava, on fire or hurt, and all 64 logs stored.
	 */
	private static void trench(GameTestHelper helper, BlockPos start, BlockPos store, String where) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = 12; x <= 13; x++) {
			for (int z = 7; z <= 17; z++) {
				QaHaulerWaterVerify1102GameTests.liquid(helper, x, z, 1, 1, Blocks.LAVA);
			}
		}
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		BlockPos chest = store.east();
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, start);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		float health = golem.getHealth();
		helper.onEachTick(() -> {
			if (golem.isInLava() || golem.isOnFire() || golem.getHealth() < health) {
				helper.fail(where + ": the hauler went into the lava: in lava " + golem.isInLava() + ", on fire " + golem.isOnFire() + ", health "
					+ golem.getHealth() + " of " + health + " at " + helper.relativeVec(golem.position()) + " on tick " + helper.getTick());
			}
		});
		helper.succeedWhen(() -> QaHaulerWaterVerify1102GameTests.delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), where));
	}

	/** The hauler starts right beside the trench's edge (one block of ground between it and the lava). */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaBeside"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaBeside")
	public void aHaulerStartingBesideASunkenLavaTrenchStillStoresItsLoad(GameTestHelper helper) {
		trench(helper, new BlockPos(10, TOP - 1, 12), new BlockPos(20, TOP, 12), "starting beside a sunken two-wide lava trench");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaBesideB"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaBesideB")
	public void aHaulerStartingBesideASunkenLavaTrenchStillStoresItsLoadCopy1(GameTestHelper helper) {
		trench(helper, new BlockPos(10, TOP - 1, 12), new BlockPos(20, TOP, 12), "starting beside a sunken two-wide lava trench (copy 1)");
	}
}
