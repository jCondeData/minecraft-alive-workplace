package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (2026-11-04): drought tests from ROADMAP 32.17 that FAIL against the mod as it is (not registered on main):
 * farmland by water bounces back to wet in a drought; pitcher crops aren't held back; the dry days are forgotten at
 * every server restart.
 */
public class QaDroughtPlague1104dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	private static final long BASE = 120 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		VillageHalls.Census census() {
			return VillageHalls.census(level, hall);
		}

		Disaster drought() {
			return Drought.get().orElseThrow(() -> new GameTestAssertException("drought.json isn't loaded"));
		}

		void start() {
			entity.setDisaster(null, -1, -1, -100);
			Disasters.start(level, hall, entity, drought(), census(), RandomSource.create(1L));
			if (!Drought.in(level, hall)) {
				throw new GameTestAssertException("the drought didn't start");
			}
		}
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int wasRadius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		VillageHalls.RADIUS = 8;
		level.setDayTime(BASE + 1000);
		level.setWeatherParameters(6000, 0, false, false);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			VillageHalls.RADIUS = wasRadius;
			level.setDayTime(was);
			level.setWeatherParameters(6000, 0, false, false);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		entity.setDisaster(null, -1, -1, -100);
		Disasters.forget();
		return new Village(level, hall, entity);
	}

	/**
	 * "Farmland dries" in a drought, water or not: farmland a block from a pond, dried to 1, must not be wet through
	 * again by its random ticks (no Well Keeper watered it). Fails: at moisture 0 vanilla's tick finds the water and sets 7.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dBugPondFarmland"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dBugPondFarmland")
	public void farmlandByWaterStaysDryInADrought(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soilRel = new BlockPos(14, 1, 12);
		helper.setBlock(soilRel.east(), Blocks.WATER);
		helper.setBlock(soilRel, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 1));
		v.start();
		BlockPos soil = helper.absolutePos(soilRel);
		RandomSource random = RandomSource.create(3L);
		int highest = 0;
		for (int i = 0; i < 20; i++) {
			v.level().getBlockState(soil).randomTick(v.level(), soil, random);
			var state = v.level().getBlockState(soil);
			helper.assertTrue(state.is(Blocks.FARMLAND), "test setup: the farmland turned to " + state);
			highest = Math.max(highest, state.getValue(FarmBlock.MOISTURE));
		}
		helper.assertTrue(highest <= 1, "in a drought the farmland by the pond dried to 0 and was wet again to " + highest + " within 20 random ticks");
		helper.succeed();
	}

	/** Pitcher crops (planted on farmland like any crop) grow at a quarter pace in a drought. Fails: they grow as often as without it. */
	//$ gametest_ticks_batch AREA '100' '"qa1104dBugPitcher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dBugPitcher")
	public void pitcherCropsAreHeldBack(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(14, 1, 12), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(14, 2, 12), Blocks.PITCHER_CROP);
		BlockPos crop = helper.absolutePos(new BlockPos(14, 2, 12));
		int usual = pitcherGrowths(v.level(), crop);
		v.start();
		int dry = pitcherGrowths(v.level(), crop);
		helper.assertTrue(usual >= 20, "test setup: the pitcher crop hardly grows without a drought: " + usual + " of 400");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the pitcher crop grew " + dry + " times, against " + usual + " without: not a quarter pace");
		helper.succeed();
	}

	private static int pitcherGrowths(ServerLevel level, BlockPos crop) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(crop.above(), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(crop, Blocks.PITCHER_CROP.defaultBlockState(), 2);
			level.getBlockState(crop).randomTick(level, crop, random);
			var state = level.getBlockState(crop);
			if (state.is(Blocks.PITCHER_CROP) && state.getValue(BlockStateProperties.AGE_4) > 0) {
				grew++;
			}
		}
		level.setBlock(crop.above(), Blocks.AIR.defaultBlockState(), 2);
		return grew;
	}

	/**
	 * "5 days without rain" counts the days it didn't rain: a server restart (what server start does: the memory
	 * forgotten) on the 6th dry day leaves the plains' drought at 5%. Fails: the restart counts from that day, 0%.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dBugDryDaysRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dBugDryDaysRestart")
	public void aRestartDoesNotMakeItRain(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		String old = level.getBiome(v.hall()).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		biome(level, v.hall(), "minecraft:plains");
		Leftovers.after(helper, () -> biome(level, v.hall(), old));
		long day = Chronicle.day(level);
		Disasters.rainedOn(level, day - 6);
		double before = Disasters.chance(level, v.hall(), v.drought(), v.census());
		helper.assertTrue(Math.abs(before - 0.05) < 1e-6, "test setup: after 6 dry days in the plains the chance is " + before);
		Disasters.forget(); // the restart (Disasters.init: onServerStarting -> forget)
		double after = Disasters.chance(level, v.hall(), v.drought(), v.census());
		helper.assertTrue(Math.abs(after - 0.05) < 1e-6, "after a restart on the 6th dry day the plains' drought chance is " + after + ", not 0.05: no rain fell");
		helper.succeed();
	}

	private static void biome(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}
}
