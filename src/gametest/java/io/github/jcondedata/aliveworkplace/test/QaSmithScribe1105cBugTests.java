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

/**
 * QA (2026-11-05) bug tests for the Weaponsmith's mending (README: "They mend what's worn in the chests by their
 * grindstone (drop your own worn gear there), by the guards' grindstones and at the other workers ... and put each piece
 * back where it was"). B970: a piece the smith is carrying was kept in their (invisible) bag when the grindstone went.
 * (B971's test, a reload mid-job putting the piece in the smith's own chest, stays on tests/smith-1105c till it's fixed.)
 */
public class QaSmithScribe1105cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The grindstone broken while the smith has the worn sword (taken from the drop-off chest): the sword and the iron are
	 * not lost: once the smith has had 200 ticks to react they are back in a chest or on the ground where a player can
	 * pick them up (not left in the smith's bag, out of a player's reach, for the rest of the test).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cMendGrindstoneGone"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cMendGrindstoneGone")
	public void qaTheGrindstoneBrokenMidJobLosesNothing(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && damageIn(drop, Items.IRON_SWORD) == -1) {
				helper.destroyBlock(GRINDSTONE);
				brokenAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the smith never took the sword");
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 200, "letting the smith react");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD);
			int iron = drop.countItem(Items.IRON_INGOT) + stock.countItem(Items.IRON_INGOT) + onGround(helper, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, "grindstone broken mid-job: swords in chests or on the ground " + swords
				+ ", iron " + iron + " of 10; the smith's bag holds " + bag(smith).stacks() + " (out of a player's reach)");
		});
	}

	/**
	 * B970 with a save and reload: the smith is saved and reloaded with the worn sword in his bag, then the grindstone is
	 * broken. The sword and the iron still end up in a chest or on the ground, not kept in the bag.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cMendReloadGrindstoneGone"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cMendReloadGrindstoneGone")
	public void qaTheGrindstoneBrokenAfterAReloadLosesNothing(GameTestHelper helper) {
		Villager first = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && bag(smith[0]).count(Items.IRON_SWORD) == 1) {
				smith[0] = reload(helper, smith[0]);
				helper.destroyBlock(GRINDSTONE);
				brokenAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the smith never took the sword");
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 200, "letting the smith react");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD);
			int iron = drop.countItem(Items.IRON_INGOT) + stock.countItem(Items.IRON_INGOT) + onGround(helper, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, "reloaded, then the grindstone broken: swords in chests or on the ground " + swords
				+ ", iron " + iron + " of 10; the smith's bag holds " + bag(smith[0]).stacks());
		});
	}
}
