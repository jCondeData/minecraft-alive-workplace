package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1102-1626), from the Storehouse's spec ("a builder counts AND takes from the village Storehouse"): a
 * Storehouse with two chests that each hold enough cobblestone for the hut; after the builder counted them a player
 * empties the first chest. He must not wait or ask for cobblestone the second chest still holds: he finishes the hut
 * from the second, every block right, and takes exactly what the hut needs from it (none appears from nowhere, none
 * is left over in the chest he drew from beyond what wasn't needed).
 */
public class QaStoreTwoChests1102bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos FIRST = new BlockPos(23, 2, 26);
	private static final BlockPos SECOND = new BlockPos(26, 2, 23);
	/** QaBuilderStockGameTests.HUGE; a swap line takes a plain name (Stonecutter rejects a dotted one). */
	private static final String HUGE = QaBuilderStockGameTests.HUGE;

	//$ gametest_ticks_batch HUGE '3000' '"qaStoreTwoChests"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaStoreTwoChests")
	public void aStorehouseChestEmptiedMidJobLetsTheBuilderFinishFromTheOther(GameTestHelper helper) {
		ServerLevel level = QaBuilderStockGameTests.setup(helper, STOREHOUSE, 25, 1);
		Container second = (Container) helper.getBlockEntity(SECOND);
		second.setItem(1, new ItemStack(Items.COBBLESTONE, 25));
		second.setChanged();
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "with 50 cobblestone in the Storehouse the status needs " + b.site().missing());
		Container first = (Container) helper.getBlockEntity(FIRST);
		first.setItem(0, ItemStack.EMPTY); // a player takes the first chest's cobblestone
		first.setChanged();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: stage=" + b.site().stage()
				+ " status=" + b.site().status() + " missing=" + b.site().missing());
			List<BlockPos> unfinished = b.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the hut was built from the second chest");
			int inFirst = first.countItem(Items.COBBLESTONE);
			int inSecond = second.countItem(Items.COBBLESTONE);
			helper.assertTrue(inFirst == 0, "cobblestone appeared in the emptied chest: " + inFirst);
			helper.assertTrue(inSecond == 0, "the hut needs 25 cobblestone and only the second chest had any, yet it still holds " + inSecond);
			helper.assertTrue(second.countItem(Items.OAK_DOOR) + first.countItem(Items.OAK_DOOR) <= 1, "a door appeared from nowhere");
			LOGGER.info("qa store two chests: first emptied mid-job, hut finished from the second ({} left)", inSecond);
		});
	}
}
