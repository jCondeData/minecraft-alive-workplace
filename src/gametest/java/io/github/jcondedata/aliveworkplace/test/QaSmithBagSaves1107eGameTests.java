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
 * QA (lane E, saves, 2026-11-07; B970's verification). B970: a Weaponsmith who took a worn sword to mend and then lost
 * his grindstone drops what he carries at his feet after a while, so nothing stays in his bag out of a player's reach.
 * {@link QaSmithScribe1105cBugTests} covers a smith with some experience, before and after a save. These take a novice
 * smith (no trades yet, as every smith is the day he takes the job): with his workstation gone a novice loses his
 * profession, so the piece he carries must still come out of his bag, with and without a server restart (a save and
 * reload of the smith) between taking the sword and the grindstone's breaking.
 */
public class QaSmithBagSaves1107eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A novice smith: the grindstone broken while he carries the sword. */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eNoviceSmithGrindstoneGone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eNoviceSmithGrindstoneGone")
	public void qaANoviceSmithsBagOutlivesNoGrindstone1107e(GameTestHelper helper) {
		run(helper, false);
	}

	/** A novice smith saved and reloaded with the sword in his bag, then the grindstone broken. */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eNoviceSmithReloadGrindstoneGone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eNoviceSmithReloadGrindstoneGone")
	public void qaANoviceSmithReloadedThenNoGrindstoneLosesNothing1107e(GameTestHelper helper) {
		run(helper, true);
	}

	private static void run(GameTestHelper helper, boolean restart) {
		Villager first = weaponsmith(helper);
		first.setVillagerXp(0); // a novice: Jobs.employ gives 1 so tests keep the job; a real new smith has none
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && bag(smith[0]).count(Items.IRON_SWORD) == 1 && damageIn(drop, Items.IRON_SWORD) == -1) {
				if (restart) {
					smith[0] = reload(helper, smith[0]);
				}
				helper.destroyBlock(GRINDSTONE);
				brokenAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the novice smith never took the sword (bag " + bag(smith[0]).stacks() + ", xp "
				+ smith[0].getVillagerXp() + ")");
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 600, "letting the smith react");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD);
			int iron = drop.countItem(Items.IRON_INGOT) + stock.countItem(Items.IRON_INGOT) + onGround(helper, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, (restart ? "reloaded, then " : "") + "the grindstone broken under a novice smith: "
				+ "swords in chests or on the ground " + swords + ", iron " + iron + " of 10, " + (helper.getTick() - brokenAt[0])
				+ " ticks later; his job now " + smith[0].getVillagerData().getProfession() + ", his bag holds " + bag(smith[0]).stacks()
				+ " (out of a player's reach)");
		});
	}
}
