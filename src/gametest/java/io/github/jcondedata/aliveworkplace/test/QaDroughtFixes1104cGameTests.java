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
import io.github.jcondedata.aliveworkplace.threat.RainLedger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (lane C, 2026-11-04): the drought fixes B946 (farmland by water stays dry), B947 (pitcher crops held back) and
 * B948 (the days without rain survive a restart), tested from ROADMAP 32.17 around their edges: outside the hall's area
 * nothing changes, the Well Keeper's water still counts, the drought's end lets the pond wet the field again, and the
 * saved count reads back from what a real save writes.
 */
public class QaDroughtFixes1104cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	/** In the hall's area (radius 8 in these tests). */
	private static final BlockPos NEAR = new BlockPos(14, 1, 12);
	/** Outside it: 14 blocks off on both axes. */
	private static final BlockPos FAR = new BlockPos(2, 1, 2);
	private static final long BASE = 130 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		Disaster drought() {
			return Drought.get().orElseThrow(() -> new GameTestAssertException("drought.json isn't loaded"));
		}

		void start() {
			entity.setDisaster(null, -1, -1, -100);
			Disasters.start(level, hall, entity, drought(), VillageHalls.census(level, hall), RandomSource.create(1L));
			if (!Drought.in(level, hall)) {
				throw new GameTestAssertException("the drought didn't start");
			}
		}

		void end() {
			entity.setDisaster(null, -1, -1, -100);
			Disasters.forget();
			if (Drought.in(level, hall)) {
				throw new GameTestAssertException("test setup: the drought didn't end");
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
			Drought.forget();
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
		Drought.forget();
		return new Village(level, hall, entity);
	}

	/** The highest moisture the farmland at {@code rel} reaches in {@code ticks} random ticks (it must stay farmland). */
	private static int highestMoisture(GameTestHelper helper, BlockPos rel, int ticks, long seed) {
		ServerLevel level = helper.getLevel();
		BlockPos soil = helper.absolutePos(rel);
		RandomSource random = RandomSource.create(seed);
		int highest = 0;
		for (int i = 0; i < ticks; i++) {
			level.getBlockState(soil).randomTick(level, soil, random);
			BlockState state = level.getBlockState(soil);
			helper.assertTrue(state.is(Blocks.FARMLAND), "the farmland at " + rel + " turned to " + state + " after " + (i + 1) + " random ticks");
			highest = Math.max(highest, state.getValue(FarmBlock.MOISTURE));
		}
		return highest;
	}

	private static void pondField(GameTestHelper helper, BlockPos rel, int moisture) {
		helper.setBlock(rel.east(), Blocks.WATER);
		helper.setBlock(rel, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
	}

	/** B946's edge: the drought keeps only the hall's area dry; a field by a pond outside it is wet by the pond as usual. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPondOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPondOutside")
	public void aPondOutsideTheVillageStillWetsItsFieldInADrought(GameTestHelper helper) {
		Village v = village(helper);
		pondField(helper, FAR, 0);
		pondField(helper, NEAR, 0);
		v.start();
		helper.assertFalse(Drought.in(v.level(), helper.absolutePos(FAR)), "test setup: the far field lies in the drought's area");
		int far = highestMoisture(helper, FAR, 20, 5L);
		int near = highestMoisture(helper, NEAR, 20, 5L);
		helper.assertTrue(far == FarmBlock.MAX_MOISTURE, "outside the hall's area the pond wet its field only to " + far + " in a drought");
		helper.assertTrue(near == 0, "in the hall's area the pond wet its field to " + near + " in a drought");
		helper.succeed();
	}

	/**
	 * B946 with a crop on it: dry farmland by a pond, planted with wheat, stays farmland (never dirt) and dry for 200
	 * random ticks of the drought; the wheat on it is still there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPondPlanted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPondPlanted")
	public void plantedFarmlandByAPondStaysTilledAndDry(GameTestHelper helper) {
		Village v = village(helper);
		pondField(helper, NEAR, 0);
		helper.setBlock(NEAR.above(), Blocks.WHEAT);
		v.start();
		int highest = highestMoisture(helper, NEAR, 200, 11L);
		helper.assertTrue(highest == 0, "dry planted farmland by the pond was wet again to " + highest + " in a drought");
		helper.assertTrue(helper.getBlockState(NEAR.above()).is(Blocks.WHEAT), "the wheat on the dry field is gone: " + helper.getBlockState(NEAR.above()));
		helper.succeed();
	}

	/** 32.17: "a bucket wets 9 farmland (moisture 7)": a Well Keeper's bucket on a dry field by a pond wets it, and it stays wet that day. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPondBucket"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPondBucket")
	public void theWellKeepersBucketStillWetsAFieldByAPond(GameTestHelper helper) {
		Village v = village(helper);
		pondField(helper, NEAR, 0);
		v.start();
		helper.assertTrue(highestMoisture(helper, NEAR, 10, 2L) == 0, "test setup: the field didn't stay dry");
		int wet = Drought.wet(v.level(), helper.absolutePos(NEAR));
		helper.assertTrue(wet >= 1, "the bucket wet " + wet + " farmland");
		BlockPos soil = helper.absolutePos(NEAR);
		RandomSource random = RandomSource.create(9L);
		for (int i = 0; i < 40; i++) {
			v.level().getBlockState(soil).randomTick(v.level(), soil, random);
		}
		BlockState state = v.level().getBlockState(soil);
		helper.assertTrue(state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE,
			"a field the Well Keeper watered today dried in the drought: " + state);
		helper.succeed();
	}

	/** When the drought ends the pond reaches its field again: dry farmland by water is wet to 7 within 20 random ticks. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPondAfter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPondAfter")
	public void afterTheDroughtThePondWetsTheFieldAgain(GameTestHelper helper) {
		Village v = village(helper);
		pondField(helper, NEAR, 0);
		v.start();
		helper.assertTrue(highestMoisture(helper, NEAR, 20, 4L) == 0, "test setup: the field didn't stay dry in the drought");
		v.end();
		int highest = highestMoisture(helper, NEAR, 20, 4L);
		helper.assertTrue(highest == FarmBlock.MAX_MOISTURE, "after the drought the pond wet its field only to " + highest);
		helper.succeed();
	}

	/** Farmland far from any water still dries step by step in a drought and ends at 0 (B946's change only touches fields by water). */
	//$ gametest_ticks_batch AREA '100' '"qa1104cDryNoWater"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cDryNoWater")
	public void farmlandAwayFromWaterStillDriesInADrought(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(NEAR, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(NEAR.above(), Blocks.WHEAT);
		v.start();
		BlockPos soil = helper.absolutePos(NEAR);
		RandomSource random = RandomSource.create(8L);
		for (int i = 0; i < 60; i++) {
			v.level().getBlockState(soil).randomTick(v.level(), soil, random);
		}
		BlockState state = v.level().getBlockState(soil);
		helper.assertTrue(state.is(Blocks.FARMLAND), "planted farmland turned to " + state);
		helper.assertTrue(state.getValue(FarmBlock.MOISTURE) == 0, "after 60 random ticks of drought the field is still at moisture " + state.getValue(FarmBlock.MOISTURE));
		helper.succeed();
	}

	private static int growths(ServerLevel level, BlockPos crop, BlockState young, net.minecraft.world.level.block.state.properties.IntegerProperty age) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(crop.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(crop, young, Block.UPDATE_CLIENTS);
			level.getBlockState(crop).randomTick(level, crop, random);
			BlockState state = level.getBlockState(crop);
			if (state.is(young.getBlock()) && state.getValue(age) > young.getValue(age)) {
				grew++;
			}
		}
		level.setBlock(crop.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		return grew;
	}

	/** B947's edge: a pitcher crop outside the hall's area grows as often in a drought as with none. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cPitcherOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cPitcherOutside")
	public void aPitcherCropOutsideTheVillageGrowsOnInADrought(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(FAR, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		BlockPos crop = helper.absolutePos(FAR.above());
		BlockState young = Blocks.PITCHER_CROP.defaultBlockState();
		int usual = growths(v.level(), crop, young, BlockStateProperties.AGE_4);
		v.start();
		int dry = growths(v.level(), crop, young, BlockStateProperties.AGE_4);
		helper.assertTrue(usual >= 20, "test setup: the pitcher crop hardly grows: " + usual + " of 400");
		helper.assertTrue(dry == usual, "outside the drought's area the pitcher crop grew " + dry + " times, against " + usual + " with no drought");
		helper.succeed();
	}

	/** 32.17 "crops ... grow at a quarter pace": the torchflower crop (the other new crop, like the pitcher) is held back too. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cTorchflower"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cTorchflower")
	public void torchflowerCropsAreHeldBackInADrought(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(NEAR, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		BlockPos crop = helper.absolutePos(NEAR.above());
		BlockState young = Blocks.TORCHFLOWER_CROP.defaultBlockState();
		int usual = growths(v.level(), crop, young, BlockStateProperties.AGE_1);
		v.start();
		int dry = growths(v.level(), crop, young, BlockStateProperties.AGE_1);
		helper.assertTrue(usual >= 20, "test setup: the torchflower crop hardly grows: " + usual + " of 400");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the torchflower crop grew " + dry + " times, against " + usual + " without: not a quarter pace");
		helper.succeed();
	}

	/**
	 * B948 through the world's own saved data: the rain day the hall's round records is what the save writes, a ledger
	 * read back from that file counts the same dry days, and a numeric value written as an int (an edited file) still reads.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cRainSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cRainSaved")
	public void theRainDayIsWrittenToTheWorldAndReadBack(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		var registries = level.registryAccess();
		long day = Chronicle.day(level);
		RainLedger ledger = RainLedger.get(level);
		Disasters.rainedOn(level, day - 8);
		ledger.setDirty(false); // as after an autosave (the ledger is the world's one, shared with every other test)
		Disasters.rainedOn(level, day - 7);
		helper.assertTrue(ledger.isDirty(), "a new rain day isn't marked for the world's save");
		CompoundTag saved = ledger.save(new CompoundTag(), registries);
		helper.assertTrue(saved.getLong("last_rain_day") == day - 7, "the save wrote " + saved + ", not the day it last rained (" + (day - 7) + ")");
		Disasters.forget(); // the restart
		helper.assertTrue(Disasters.dryDays(level) == 7, "after a restart the days without rain read " + Disasters.dryDays(level) + ", not 7");
		RainLedger reloaded = RainLedger.load(saved, registries);
		helper.assertTrue(reloaded.dryDays(day) == 7, "a ledger read back from the save counts " + reloaded.dryDays(day) + " dry days, not 7");
		CompoundTag edited = new CompoundTag();
		edited.putInt("last_rain_day", (int) (day - 3));
		helper.assertTrue(RainLedger.load(edited, registries).dryDays(day) == 3, "a rain day saved as an int doesn't read back");
		CompoundTag junk = new CompoundTag();
		junk.putString("last_rain_day", "yesterday");
		helper.assertTrue(RainLedger.load(junk, registries).dryDays(day) == 0, "a broken rain day isn't read as unknown (counted from today)");
		helper.succeed();
	}

	/** Rain resets the count: while it rains the dry days are 0, and the day after the rain stops they count from the rain day. */
	//$ gametest_ticks_batch AREA '100' '"qa1104cRainResets"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cRainResets")
	public void rainSetsTheDryDaysBackToZeroAndIsSaved(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		long day = Chronicle.day(level);
		Disasters.rainedOn(level, day - 9);
		helper.assertTrue(Disasters.dryDays(level) == 9, "test setup: " + Disasters.dryDays(level) + " dry days, not 9");
		level.setWeatherParameters(0, 6000, true, false);
		helper.assertTrue(Disasters.dryDays(level) == 0, "while it rains the dry days read " + Disasters.dryDays(level));
		level.setWeatherParameters(6000, 0, false, false);
		level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
		Disasters.forget(); // a restart overnight
		long dry = Disasters.dryDays(level);
		helper.assertTrue(dry == 1, "the day after a rain (and a restart) the dry days read " + dry + ", not 1");
		helper.succeed();
	}
}
