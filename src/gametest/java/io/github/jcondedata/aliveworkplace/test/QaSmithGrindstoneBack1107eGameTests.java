package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.GRINDSTONE;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, saves, 2026-11-07; B970's verification). B970: a Weaponsmith who lost his grindstone while carrying a
 * worn sword to mend drops what he carries after a while (time to put a grindstone back), so nothing stays in his bag
 * out of a player's reach. From the other sides of that wait:
 * <ul>
 * <li>the server restarts (the smith saved and reloaded) in the middle of the wait: the countdown isn't saved, but the
 * sword and the iron must still come out of his bag;</li>
 * <li>a grindstone put back at once: nothing is lost either way (he mends it, or drops it), and the sword ends up in a
 * chest or on the ground, never kept in the bag.</li>
 * </ul>
 * The novice smith's case (he loses the job with the grindstone) is on tests/smith-bag-1107e (B1008).
 */
public class QaSmithGrindstoneBack1107eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The grindstone broken while he carries the sword, and the smith saved and reloaded 50 ticks later. */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eSmithReloadMidWait"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eSmithReloadMidWait")
	public void qaASmithReloadedWhileWaitingForAGrindstoneLosesNothing1107e(GameTestHelper helper) {
		run(helper, "reloaded 50 ticks after the grindstone broke", (h, smith, since) -> since == 50 ? reload(h, smith) : smith);
	}

	/** The grindstone broken while he carries the sword, and put back at the same spot 20 ticks later. */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eSmithGrindstoneBack"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eSmithGrindstoneBack")
	public void qaAGrindstonePutBackAtOnceLosesNothing1107e(GameTestHelper helper) {
		run(helper, "the grindstone put back 20 ticks later", (h, smith, since) -> {
			if (since == 20) {
				h.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
			}
			return smith;
		});
	}

	interface Step {
		Villager after(GameTestHelper helper, Villager smith, long since);
	}

	private static void run(GameTestHelper helper, String what, Step step) {
		Villager first = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0) {
				if (bag(smith[0]).count(Items.IRON_SWORD) == 1 && damageIn(drop, Items.IRON_SWORD) == -1) {
					helper.destroyBlock(GRINDSTONE);
					brokenAt[0] = helper.getTick();
				}
				return;
			}
			smith[0] = step.after(helper, smith[0], helper.getTick() - brokenAt[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the smith never took the sword (bag " + bag(smith[0]).stacks() + ")");
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 600, "letting the smith react");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD);
			int iron = drop.countItem(Items.IRON_INGOT) + stock.countItem(Items.IRON_INGOT) + onGround(helper, Items.IRON_INGOT);
			boolean mended = damageIn(drop, Items.IRON_SWORD) == 0 || damageIn(stock, Items.IRON_SWORD) == 0;
			// mended, some iron went into it; otherwise all 10 are back
			helper.assertTrue(swords == 1 && (mended ? iron < 10 : iron == 10), what + ": swords in chests or on the ground " + swords + ", iron " + iron + " of 10, "
				+ (helper.getTick() - brokenAt[0]) + " ticks after the grindstone broke (mended: " + mended + "); the smith's bag holds " + bag(smith[0]).stacks());
		});
	}
}
