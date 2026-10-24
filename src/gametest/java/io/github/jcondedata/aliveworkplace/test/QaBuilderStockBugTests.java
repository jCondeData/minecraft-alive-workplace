package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane b, 2026-10-24): the bug test for B355. The builder's status names a Storehouse he doesn't count because it is
 * past the village's reach, but rounds the distance, so a Storehouse 20.4 blocks off in a 20-block village reads "it is 20
 * blocks from his table, and a village reaches 20". Fails on main until the fix; the builder who fixes it merges this.
 */
public class QaBuilderStockBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaBuilderStockGameTests.HUGE;

	/**
	 * Just past the reach (20.4 blocks, a 20-block village) it isn't counted, and the sentence says why with numbers that
	 * agree: the distance it gives is more than the reach it gives (not "it is 20 blocks from his table, and a village
	 * reaches 20").
	 */
	//$ gametest_batch HUGE '"qaStockEdgeOut"'
	@GameTest(template = HUGE, batch = "qaStockEdgeOut")
	public void aStorehouseJustPastTheReachIsTooFarAndTheNumbersSaySo(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(22, 2, 6); // sqrt(20² + 4²) = 20.4 blocks from the table
		ServerLevel level = QaBuilderStockGameTests.setup(helper, storehouse, 25, 1);
		Leftovers.village(helper, 20);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		Component status = Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(!b.site().missing().isEmpty(), "a Storehouse 20.4 blocks off was counted in a 20-block village");
		TranslatableContents far = QaBuilderStockGameTests.find(status, "message.aliveworkplace.status.storehouse_far");
		helper.assertTrue(far != null, "the status doesn't say the Storehouse is too far: " + status.getString());
		int distance = Integer.parseInt(String.valueOf(QaBuilderStockGameTests.arg(far.getArgs()[1])));
		int reach = Integer.parseInt(String.valueOf(QaBuilderStockGameTests.arg(far.getArgs()[2])));
		helper.assertTrue(distance > reach, "the status says the Storehouse is too far, yet the distance it gives is not past the reach: "
			+ Component.translatable(far.getKey(), far.getArgs()).getString());
		helper.succeed();
	}

}
