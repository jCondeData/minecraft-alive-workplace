package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.GRINDSTONE;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.POST_CHEST;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.guard;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (2026-11-05) bug tests for the Weaponsmith's mending (README: "They mend what's worn in the chests by their
 * grindstone (drop your own worn gear there), by the guards' grindstones and at the other workers ... and put each piece
 * back where it was"). Both fail on main: a piece the smith is carrying is kept in their (invisible) bag when the
 * grindstone goes, and after a save and reload mid-job it is put in the smith's own chest, not back where it came from.
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
	 * A worn helmet in a guard's chest, fetched by the smith, who is then saved and reloaded (as when the chunk unloads or
	 * the server restarts) with the helmet in hand: it still ends up back in the guard's chest, mended, not in the
	 * smith's own chests.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1105cMendReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1105cMendReload")
	public void qaASmithReloadedMidJobStillPutsTheGuardsHelmetBack(GameTestHelper helper) {
		Leftovers.village(helper, 48);
		Villager first = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Container guards = chest(helper, POST_CHEST);
		guards.setItem(0, worn(Items.IRON_HELMET, 120)); // of 165: a quarter is 41, three ingots
		Villager[] smith = {first};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && bag(smith[0]).count(Items.IRON_HELMET) == 1) {
				smith[0] = reload(helper, smith[0]);
				reloaded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the smith never took the helmet");
			helper.assertTrue(stock.countItem(Items.IRON_HELMET) == 0 && drop.countItem(Items.IRON_HELMET) == 0,
				"the guard's helmet ended in the smith's chests: damage in the stock chest " + damageIn(stock, Items.IRON_HELMET)
					+ ", in the drop-off " + damageIn(drop, Items.IRON_HELMET) + " (-1: not there); in the guard's chest " + damageIn(guards, Items.IRON_HELMET));
			int damage = damageIn(guards, Items.IRON_HELMET);
			helper.assertTrue(damage == 0, "the helmet in the guard's chest has damage " + damage + " (-1: not there); the smith carries " + bag(smith[0]).stacks());
			helper.assertTrue(stock.countItem(Items.IRON_INGOT) == 0, "iron left " + stock.countItem(Items.IRON_INGOT));
		});
	}
}
