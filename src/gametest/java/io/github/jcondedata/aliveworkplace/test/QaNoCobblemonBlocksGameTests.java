package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;

/**
 * QA (lane E, optional-mod absence; B688's other door): without Cobblemon (this run), the Pokémon jobs "need Cobblemon"
 * (training-post.md: "Needs: Cobblemon, for its jobs to work"; villager-jobs.md: a job that needs Cobblemon, without it,
 * isn't listed and can't be picked). B688 closed the item door (sneak-right-click). These check the other door a player
 * meets: a jobless villager standing by one of our Pokémon workstations doesn't take its Pokémon job by itself. The
 * Training Post is the one a player meets: it crafts from vanilla items and is in the creative tab without Cobblemon.
 * A Miner's Bench in the same set-up makes a Miner, so the wait is long enough for a villager to take a block.
 */
public class QaNoCobblemonBlocksGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);
	private static final int WAIT = 600;

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	private static Villager jobless(GameTestHelper helper, Block block) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, block);
		return helper.spawn(EntityType.VILLAGER, STANDING);
	}

	/** For {@link #WAIT} ticks the jobless villager by {@code block} never holds a job that can't be had in this game. */
	private static void takesNoPokemonJob(GameTestHelper helper, Block block) {
		Villager v = jobless(helper, block);
		helper.onEachTick(() -> {
			VillagerProfession p = v.getVillagerData().getProfession();
			if (p != VillagerProfession.NONE && !Stations.available(p)) {
				helper.fail("without Cobblemon a jobless villager by the " + BuiltInRegistries.BLOCK.getKey(block)
					+ " took " + job(v) + " by itself (a job that needs Cobblemon) at tick " + helper.getTick());
			}
		});
		helper.runAtTickTime(WAIT, helper::succeed);
	}

	/** The control: a Miner's Bench in the same set-up makes a Miner within the wait. */
	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonControl"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonControl")
	public void controlAMinersBenchMakesAMinerInTheWait(GameTestHelper helper) {
		Villager v = jobless(helper, ModBlocks.MINERS_BENCH);
		helper.runAtTickTime(WAIT, () -> {
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.MINER, "a jobless villager by a Miner's Bench is still " + job(v));
			helper.succeed();
		});
	}

	/** A Shop Counter (a shared block, ours): the Shopkeeper or nothing, never the Pokémon Trader. */
	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonCounter"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonCounter")
	public void aJoblessVillagerByAShopCounterTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.SHOP_COUNTER);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonPost"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonPost")
	public void aJoblessVillagerByATrainingPostTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.TRAINING_POST);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonPodium"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonPodium")
	public void aJoblessVillagerByALeadersPodiumTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.LEADERS_PODIUM);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonDesk"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonDesk")
	public void aJoblessVillagerByATutorsDeskTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.TUTORS_DESK);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonBoard"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonBoard")
	public void aJoblessVillagerByATradeBoardTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.TRADE_BOARD);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonBall"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonBall")
	public void aJoblessVillagerByABallWorkbenchTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.BALL_WORKBENCH);
	}

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonFossil"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonFossil")
	public void aJoblessVillagerByAFossilLabTakesNoPokemonJob(GameTestHelper helper) {
		takesNoPokemonJob(helper, ModBlocks.FOSSIL_LAB);
	}
}
