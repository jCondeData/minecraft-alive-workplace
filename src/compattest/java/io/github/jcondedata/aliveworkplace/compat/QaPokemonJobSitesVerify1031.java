package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;

/**
 * QA verification of B697 (lane-a's fix) with Cobblemon installed: the fix keeps a jobless villager off the Pokémon
 * blocks only when their job can't be had. With Cobblemon each of them must still take a jobless villager by itself
 * (StationsCompatTests checks the Training Post; these check the other five), its own job at that block.
 */
public class QaPokemonJobSitesVerify1031 implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	private static String name(VillagerProfession p) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString();
	}

	private static void takesItsJob(GameTestHelper helper, Block block, VillagerProfession expected) {
		helper.assertTrue(Stations.available(expected), "setup: " + name(expected) + " isn't available with Cobblemon");
		helper.setDayTime(2000);
		for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			e.discard();
		}
		helper.setBlock(STATION, block);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		BlockPos at = helper.absolutePos(STATION);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == expected,
				"with Cobblemon a jobless villager by the " + BuiltInRegistries.BLOCK.getKey(block) + " is " + name(villager.getVillagerData().getProfession()));
			Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
			helper.assertTrue(site.equals(Optional.of(at)), "the " + name(expected) + " works at " + site);
		});
	}

	//$ gametest_ticks_batch AREA '1200' '"qaVerifyCompatPodium"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerifyCompatPodium")
	public void aLeadersPodiumTakesATrainerLeaderByItself(GameTestHelper helper) {
		takesItsJob(helper, ModBlocks.LEADERS_PODIUM, ModVillagers.TRAINER_LEADER);
	}

	//$ gametest_ticks_batch AREA '1200' '"qaVerifyCompatDesk"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerifyCompatDesk")
	public void aTutorsDeskTakesATutorByItself(GameTestHelper helper) {
		takesItsJob(helper, ModBlocks.TUTORS_DESK, ModVillagers.TUTOR);
	}

	//$ gametest_ticks_batch AREA '1200' '"qaVerifyCompatBoard"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerifyCompatBoard")
	public void aTradeBoardTakesAPokemonTraderByItself(GameTestHelper helper) {
		takesItsJob(helper, ModBlocks.TRADE_BOARD, ModVillagers.POKEMON_TRADER);
	}

	//$ gametest_ticks_batch AREA '1200' '"qaVerifyCompatBall"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerifyCompatBall")
	public void aBallWorkbenchTakesABallSmithByItself(GameTestHelper helper) {
		takesItsJob(helper, ModBlocks.BALL_WORKBENCH, ModVillagers.BALL_SMITH);
	}

	//$ gametest_ticks_batch AREA '1200' '"qaVerifyCompatFossil"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerifyCompatFossil")
	public void aFossilLabTakesAFossilScientistByItself(GameTestHelper helper) {
		takesItsJob(helper, ModBlocks.FOSSIL_LAB, ModVillagers.FOSSIL_SCIENTIST);
	}
}
