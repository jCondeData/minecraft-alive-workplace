package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1107-0952) on B353's "a job outlives its bench for half a minute, then is let go and what he carries goes
 * back", for a brand-new Carpenter (0 XP, as one who just took his bench in play is): vanilla makes him jobless with the
 * bench, and a free lectern stands nearby for him to take. Whatever job he ends up in, the request he was crafting is
 * let go and the wood he carried leaves his bag (B970's smith is the same case, B1007). Helpers are
 * QaCrafterFurnitureGameTests's.
 */
public class QaCrafterNewJob1107BugTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final BlockPos LECTERN = new BlockPos(11, 2, 4);

	/**
	 * The bench broken once the new Carpenter carries the builder's logs: within 1,500 ticks his bag is empty and the
	 * crafting job gone, whether he took the lectern or stayed jobless.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1107CarpenterNewJob"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107CarpenterNewJob")
	public void qaANewCarpenterWhoTakesAnotherJobLetsTheRequestAndTheWoodGo(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		carpenter.setVillagerXp(0); // never traded: vanilla takes his profession away with the bench
		helper.setBlock(LECTERN, Blocks.LECTERN);
		AtomicInteger brokenAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
			if (brokenAt.get() < 0 && bag != null && !bag.isEmpty()) {
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
				brokenAt.set((int) helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() >= 0, "the carpenter never carried anything");
			helper.assertTrue(helper.getTick() >= brokenAt.get() + 1500, "letting him react");
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
			boolean job = ModAttachments.CRAFTER_JOB.get(carpenter) != null;
			helper.assertTrue(!job && (bag == null || bag.isEmpty()), "a 0-XP carpenter lost his bench while carrying wood and is now "
				+ carpenter.getVillagerData().getProfession() + ": crafting job " + (job ? "still held" : "let go") + ", bag "
				+ (bag == null ? "none" : bag.stacks()) + " (logs on the ground " + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_LOG)
				+ ", planks " + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS) + ")");
		});
	}
}
