package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-22), B1463's fix against the player's view: the village must still get the fisherman the cap
 * allows when the "free workstation of another job" beside the barrel is one no jobless villager takes (a crafting
 * table: only by an item; a Shop Counter nobody can reach) or is the Fisher's Hut II's own smoker. These failed on main
 * 576f701a6 (B1476, B1477; fixed by lane-a-1122-0623 in WorkerLimits.storeOfAFreeWorkstation). Helpers in
 * {@link QaJobBarrels1122cGameTests}.
 */
public class QaJobBarrels1122cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BARREL = QaJobBarrels1122cGameTests.BARREL;
	private static final BlockPos BESIDE = QaJobBarrels1122cGameTests.BESIDE;

	/**
	 * A crafting table two blocks from the barrel (a player's workbench by his fishing hut; Starter Cottages, Artisan's
	 * Houses and Warehouses draw one beside their barrels). No job is taken at a crafting table by itself (Stations: only
	 * by an item), so it is no free workstation a jobless villager could take instead: the village still gets its
	 * fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cTable"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cTable")
	public void qaACraftingTableBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), Blocks.CRAFTING_TABLE);
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a crafting table 2 blocks from the barrel");
	}

	/**
	 * A Shop Counter 4 blocks from the barrel, walled in with glass on every side and on top: free, but no villager can
	 * ever reach it to take it. The barrel isn't any free workshop's stock a villager could take instead; the village
	 * still gets its fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cSealed"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cSealed")
	public void qaASealedOffShopCounterNearbyDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos counter = BARREL.offset(4, 0, 0);
		helper.setBlock(counter, ModBlocks.SHOP_COUNTER);
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
			helper.setBlock(counter.relative(d), Blocks.GLASS);
		}
		Villager v = QaJobBarrels1122cGameTests.jobless(helper, BESIDE);
		QaJobBarrels1122cGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a sealed Shop Counter 4 blocks away");
	}

	/**
	 * fishers_hut.md: Level II adds exactly one smoker within supply reach of the barrel, to smoke the catch; "a jobless
	 * villager by it [the barrel] becomes a Fisherman". A jobless villager beside the Level II barrel, the village with no
	 * fisherman yet: he becomes the hut's Fisherman (not the smoker's Butcher).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrels1122cHut2"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrels1122cHut2")
	public void qaAJoblessVillagerAtTheFishersHutIIBecomesItsFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.fishersHut(helper, StarterBlueprints.FISHERS_HUT_2);
	}

}
