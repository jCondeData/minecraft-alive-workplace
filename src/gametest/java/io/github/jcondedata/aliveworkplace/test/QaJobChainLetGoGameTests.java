package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-0953) on B353's "a job outlives its bench for half a minute, then is let go" (37.14a's chains), at the
 * one moment the earlier tests don't reach: the bench broken while the Carpenter is still walking to the chest, before
 * he has carried anything (his bag never filled). The nightly mutation run of 2026-10-25 left `bag == null ||
 * bag.isEmpty()` to `&&` alive in {@code CrafterWork.benchlessTick}: with it, letting such a job go throws in the
 * villager's tick. Helpers are QaCrafterFurnitureGameTests's.
 */
public class QaJobChainLetGoGameTests {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);

	/**
	 * The bench broken the moment the Carpenter takes the builder's barrels (still fetching, empty-handed): half a minute
	 * later the job is let go with nothing taken (the six logs still in the chest, nothing on the ground, nothing in his
	 * bag) and he is alive and well; the bench put back, he takes the request up again and the three barrels are built.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaLetGoEmptyHanded"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaLetGoEmptyHanded")
	public void aBenchBrokenBeforeAnythingWasCarriedLetsTheJobGoCleanly(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		AtomicInteger stage = new AtomicInteger(); // 0 waiting for the job, 1 bench broken, 2 job let go and bench back
		AtomicInteger brokenFor = new AtomicInteger();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (stage.get() == 0 && job != null) {
				helper.assertTrue(job.phase() == CrafterJob.Phase.FETCHING, "setup: the job started at " + job.phase());
				BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
				helper.assertTrue(bag == null || bag.isEmpty(), "setup: the carpenter already carries " + held(bag));
				LOG.info("let go empty-handed: bag attachment " + (bag == null ? "absent" : "present and empty"));
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench
				stage.set(1);
			} else if (stage.get() == 1) {
				brokenFor.incrementAndGet();
				if (job == null) {
					Container chest = helper.getBlockEntity(a.chest());
					helper.assertTrue(brokenFor.get() >= 500, "the job was let go after " + brokenFor.get() + " ticks, not half a minute");
					helper.assertTrue(chest.countItem(Items.OAK_LOG) == 6, "the chest holds " + chest.countItem(Items.OAK_LOG) + " of its 6 logs");
					helper.assertTrue(QaCrafterFurnitureGameTests.loose(helper, Items.OAK_LOG) == 0
						&& QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS) == 0, "wood on the ground after a job nothing was taken for");
					BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
					helper.assertTrue(bag == null || bag.isEmpty(), "the carpenter kept " + held(bag));
					helper.assertTrue(carpenter.isAlive(), "the carpenter is gone");
					helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH); // and puts it back
					stage.set(2);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage.get() >= 1, "the carpenter never took the builder's barrels");
			helper.assertTrue(stage.get() == 2, "the job was never let go after the bench was broken: " + ModAttachments.CRAFTER_JOB.get(carpenter));
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			helper.assertTrue(carpenter.isAlive(), "the carpenter is gone");
		});
	}

	private static String held(BuilderBag bag) {
		return bag == null ? "nothing" : String.valueOf(bag.stacks());
	}
}
