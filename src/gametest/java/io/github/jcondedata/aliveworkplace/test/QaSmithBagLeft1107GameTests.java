package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.GRINDSTONE;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1107-0952) for B970's fix, from its words: a Weaponsmith whose grindstone is broken while he carries your
 * worn sword (or the iron to mend it) drops what he carries at his feet after a few seconds, so nothing is lost; "a stale
 * job whose piece left the bag is let go". These go round the corners the fix's own tests don't: a brand-new smith (no
 * trades yet, so vanilla takes his profession away with the grindstone), and what happens once the grindstone is back.
 * (The new smith who takes another job instead, QaSmithBagLeft1107BugTests, waits on tests/smith-1107 for its fix.)
 */
public class QaSmithBagLeft1107GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Swords and iron anywhere a player can get them: the two chests or the ground. */
	private static int reachable(GameTestHelper helper, Container drop, Container stock, net.minecraft.world.item.Item item) {
		return drop.countItem(item) + stock.countItem(item) + onGround(helper, item);
	}

	/**
	 * A smith who has never traded (0 XP, as one who just took a grindstone in play is) loses his profession with the
	 * grindstone. He was carrying the worn sword: it and the iron still come out of his bag, into a chest or onto the
	 * ground, within 400 ticks.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1107SmithNovice"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1107SmithNovice")
	public void qaANewSmithWhoLosesHisJobWithTheGrindstoneDropsTheSword(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		smith.setVillagerXp(0); // never traded: vanilla resets his profession once the grindstone is gone
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
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 400, "letting the smith react");
			int swords = reachable(helper, drop, stock, Items.IRON_SWORD);
			int iron = reachable(helper, drop, stock, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, "a 0-XP smith (now " + smith.getVillagerData().getProfession()
				+ ") lost his grindstone mid-job: swords a player can reach " + swords + ", iron " + iron + " of 10; his bag holds "
				+ bag(smith).stacks());
		});
	}

	/**
	 * After the grindstone went and the smith dropped the sword, the grindstone is put back and a player puts the sword
	 * back in the drop-off chest: the stale job is let go, the smith takes it up again, mends it and returns it to that
	 * chest. Never two swords, and none left on the ground at the end.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa1107SmithBack"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1107SmithBack")
	public void qaTheGrindstoneBackTheDroppedSwordIsMendedNextTime(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		long[] brokenAt = {-1};
		long[] backAt = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			int swords = reachable(helper, drop, stock, Items.IRON_SWORD) + bag(smith).count(Items.IRON_SWORD);
			most[0] = Math.max(most[0], swords);
			if (brokenAt[0] < 0 && damageIn(drop, Items.IRON_SWORD) == -1) {
				helper.destroyBlock(GRINDSTONE);
				brokenAt[0] = helper.getTick();
			} else if (brokenAt[0] >= 0 && backAt[0] < 0 && onGround(helper, Items.IRON_SWORD) == 1) {
				// the sword is on the ground: a player picks it up, puts the grindstone back and the sword in the chest
				for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
					e -> e.getItem().is(Items.IRON_SWORD))) {
					drop.setItem(0, e.getItem().copy());
					e.discard();
				}
				helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
				backAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the smith never took the sword");
			helper.assertTrue(backAt[0] >= 0, "the sword never came out of the smith's bag after the grindstone went (bag "
				+ bag(smith).stacks() + ")");
			helper.assertTrue(most[0] == 1, "there were " + most[0] + " swords at once");
			int damage = damageIn(drop, Items.IRON_SWORD);
			helper.assertTrue(damage >= 0 && damage < 200, "the grindstone back, the sword put back in the drop-off chest: "
				+ (damage < 0 ? "not in the chest (bag " + bag(smith).stacks() + ", ground " + onGround(helper, Items.IRON_SWORD) + ")"
				: "still worn (" + damage + ")") + "; the smith is " + smith.getVillagerData().getProfession());
			helper.assertTrue(onGround(helper, Items.IRON_SWORD) == 0, "a sword left on the ground");
		});
	}
}
