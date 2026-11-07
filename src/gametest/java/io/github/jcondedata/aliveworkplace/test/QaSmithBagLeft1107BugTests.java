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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1107-0952) bug test for B970's fix: a brand-new Weaponsmith (0 XP) who loses his grindstone while carrying
 * your worn sword, and takes a free lectern before the few seconds are up, keeps the sword in his bag for good (seen 5 of
 * 10 repeats: he became a Teacher or a Scholar). Its bug: see ROADMAP (QaSmithBagLeft1107BugTests).
 */
public class QaSmithBagLeft1107BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static int reachable(GameTestHelper helper, Container drop, Container stock, net.minecraft.world.item.Item item) {
		return drop.countItem(item) + stock.countItem(item) + onGround(helper, item);
	}

	/**
	 * The same new smith, with a free lectern a few blocks off: having lost his job with the grindstone he may take the
	 * lectern and become a Librarian. Whatever job he ends up in, your sword and the iron come out of his bag (a Librarian
	 * has no use for them and nothing would ever give them back).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1107SmithNewJob"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1107SmithNewJob")
	public void qaANewSmithWhoTakesAnotherJobDoesntKeepTheSword(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		smith.setVillagerXp(0);
		BlockPos lectern = new BlockPos(7, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
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
			helper.assertTrue(helper.getTick() >= brokenAt[0] + 600, "letting the smith react");
			int swords = reachable(helper, drop, stock, Items.IRON_SWORD);
			int iron = reachable(helper, drop, stock, Items.IRON_INGOT);
			VillagerProfession now = smith.getVillagerData().getProfession();
			helper.assertTrue(swords == 1 && iron == 10, "a 0-XP smith lost his grindstone mid-job and is now " + now
				+ ": swords a player can reach " + swords + ", iron " + iron + " of 10; his bag holds " + bag(smith).stacks());
		});
	}
}
