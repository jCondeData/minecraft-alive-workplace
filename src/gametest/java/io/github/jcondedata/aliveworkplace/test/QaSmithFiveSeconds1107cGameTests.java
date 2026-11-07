package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import io.github.jcondedata.aliveworkplace.mend.MendingWork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1107-1252), B970's "a few seconds": the nightly mutation run (2026-11-07) found that the moment a smith
 * without a grindstone lets go of what he carries could move a tick later unnoticed ({@code MendingWork} "now - since <
 * STATIONLESS_TICKS" turned into "<="). The fix's words: after 100 ticks (5 seconds) without a station, a Weaponsmith drops
 * your worn sword and the iron at his feet. These drive the smith's own per-tick check ({@link MendingWork#stationlessTick},
 * what every villager's AI step calls) with the smith standing still, so the clock is the test's: still carried at 99
 * ticks, on the ground at 100; and never dropped while the grindstone stands.
 */
public class QaSmithFiveSeconds1107cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A Weaponsmith who has traded (so keeps his profession) with no grindstone, carrying a worn sword and iron. */
	private static Villager stationless(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		smith.setVillagerData(smith.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
		smith.setVillagerXp(10);
		smith.setNoAi(true); // the test calls the per-tick check itself, once a tick
		bag(smith).add(worn(Items.IRON_SWORD, 200));
		bag(smith).add(new ItemStack(Items.IRON_INGOT, 3));
		return smith;
	}

	/** Still carried after 99 ticks without a grindstone; the sword and the iron are on the ground at 100. */
	//$ gametest_ticks_batch AREA '300' '"qaSmithFiveSeconds1107c"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaSmithFiveSeconds1107c")
	public void aSmithWithoutAGrindstoneDropsTheSwordAtExactlyFiveSeconds(GameTestHelper helper) {
		Villager smith = stationless(helper);
		long[] first = {-1};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (first[0] < 0) {
				first[0] = now;
			}
			MendingWork.stationlessTick(smith);
			long waited = now - first[0];
			int sword = onGround(helper, Items.IRON_SWORD);
			if (waited < 100) {
				helper.assertTrue(sword == 0 && bag(smith).count(Items.IRON_SWORD) == 1, "the smith let go of the sword after only "
					+ waited + " ticks without a grindstone (expected 100): ground " + sword + ", bag " + bag(smith).stacks());
			} else if (waited == 100) {
				helper.assertTrue(sword == 1 && onGround(helper, Items.IRON_INGOT) == 3 && bag(smith).isEmpty(), "100 ticks (5 s) "
					+ "without a grindstone and the smith still carries " + bag(smith).stacks() + " (ground: sword " + sword + ", iron "
					+ onGround(helper, Items.IRON_INGOT) + ")");
				helper.succeed();
			}
		});
	}

	/** With his grindstone standing, a smith carrying a sword and iron never drops them, however long. */
	//$ gametest_ticks_batch AREA '400' '"qaSmithFiveSecondsBench1107c"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSmithFiveSecondsBench1107c")
	public void aSmithWithHisGrindstoneKeepsWhatHeCarries(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		smith.setVillagerXp(10);
		smith.setNoAi(true);
		bag(smith).add(worn(Items.IRON_SWORD, 200));
		bag(smith).add(new ItemStack(Items.IRON_INGOT, 3));
		helper.onEachTick(() -> MendingWork.stationlessTick(smith));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(onGround(helper, Items.IRON_SWORD) == 0 && onGround(helper, Items.IRON_INGOT) == 0
				&& bag(smith).count(Items.IRON_SWORD) == 1 && bag(smith).count(Items.IRON_INGOT) == 3,
				"a smith by his grindstone dropped what he carried within 300 ticks: bag " + bag(smith).stacks());
			helper.succeed();
		});
	}
}
