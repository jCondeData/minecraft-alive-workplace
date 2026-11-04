package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import io.github.jcondedata.aliveworkplace.threat.RainLedger;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-04, third pass): the fixes for B946 (farmland by water dries in a drought), B947 (pitcher crops
 * held back), B948 (the days without rain survive a restart) and B950 (a guard's horse within 24 blocks), tested from
 * docs/guide/drought.md, docs/wiki/drought.md and docs/guide/guard.md around their edges: outside the village or the
 * drought a pond still wets a field, the drought's end or its switch lets it, a Well Keeper's bucket beats it; the
 * pitcher crop away from the village, on watered farmland, and torchflowers; the rain day in the world file; and the
 * horse's 24 blocks measured on every side, on the diagonal and for a wide camel.
 */
public class QaDroughtGuard1104d3GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	/** Day 131 begins here: far from the other disaster tests' days. */
	private static final long BASE = 130 * VillageNeeds.DAY;
	/** Inside the hall's area (radius 8): a field with a pond a block east of it. */
	private static final BlockPos FIELD = new BlockPos(14, 1, 12);
	/** Outside the hall's area: 14 blocks from the hall on both axes. */
	private static final BlockPos FAR_FIELD = new BlockPos(2, 1, 2);

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

		void round() {
			Disasters.round(level, hall, entity, census());
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

	/** Dry farmland at {@code rel} with water a block east of it; returns its absolute position. */
	private static BlockPos pondField(GameTestHelper helper, BlockPos rel, int moisture) {
		helper.setBlock(rel.east(), Blocks.WATER);
		helper.setBlock(rel, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
		return helper.absolutePos(rel);
	}

	/** One random tick on the farmland at {@code soil}; its moisture after (fails if it isn't farmland any more). */
	private static int tick(GameTestHelper helper, BlockPos soil, RandomSource random) {
		ServerLevel level = helper.getLevel();
		level.getBlockState(soil).randomTick(level, soil, random);
		BlockState state = level.getBlockState(soil);
		helper.assertTrue(state.is(Blocks.FARMLAND), "the field by the pond turned to " + state + " instead of staying tilled");
		return state.getValue(FarmBlock.MOISTURE);
	}

	// --- B946: farmland by water ------------------------------------------------------------------------------------

	/** Control: with no drought, dry farmland a block from a pond is wet through (moisture 7) by its first random tick, as in vanilla. */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondNoDrought"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondNoDrought")
	public void aPondWetsAFieldWithoutADrought(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FIELD, 0);
		helper.assertFalse(Drought.in(v.level(), soil), "test setup: a drought is on");
		int m = tick(helper, soil, RandomSource.create(5L));
		helper.assertTrue(m == 7, "with no drought, a dry field beside a pond read moisture " + m + " after a random tick, not 7");
		helper.succeed();
	}

	/** In a drought, a dry field by a pond outside the hall's area (14 blocks off, radius 8) is still wet by the pond. */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondOutside")
	public void aPondOutsideTheVillageStillWetsItsField(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FAR_FIELD, 0);
		v.start();
		helper.assertFalse(Drought.in(v.level(), soil), "test setup: the far field is inside the drought's area");
		int m = tick(helper, soil, RandomSource.create(5L));
		helper.assertTrue(m == 7, "in a drought, a dry field by a pond outside the village read moisture " + m + ", not 7: the drought reached past the village");
		helper.succeed();
	}

	/**
	 * In the drought a wet field by a pond (moisture 7) dries a step each random tick, to 0 in 7, and stays tilled and
	 * at 0 through 40 more: it never climbs back up.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondDries"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondDries")
	public void aWetFieldByAPondDriesStepByStepInADrought(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FIELD, 7);
		v.start();
		RandomSource random = RandomSource.create(7L);
		int last = 7;
		for (int i = 1; i <= 47; i++) {
			int m = tick(helper, soil, random);
			helper.assertTrue(m <= last, "in a drought the field by the pond went from moisture " + last + " back up to " + m + " on random tick " + i);
			if (i <= 7) {
				helper.assertTrue(m == 7 - i, "in a drought the field by the pond read moisture " + m + " after " + i + " random ticks, not " + (7 - i));
			}
			last = m;
		}
		helper.assertTrue(last == 0, "after 47 random ticks of drought the field by the pond is at moisture " + last);
		helper.succeed();
	}

	/** The rain ends the drought (guide: "a pond won't wet it again until the drought is over"): then the pond wets the dried field. */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondAfterRain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondAfterRain")
	public void onceRainEndsTheDroughtThePondWetsTheFieldAgain(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FIELD, 0);
		v.start();
		RandomSource random = RandomSource.create(9L);
		for (int i = 0; i < 10; i++) {
			int m = tick(helper, soil, random);
			helper.assertTrue(m == 0, "test setup: in the drought the field by the pond was wet to " + m);
		}
		v.level().setWeatherParameters(0, 6000, true, false);
		v.round();
		helper.assertTrue(v.entity().disaster() == null && !Drought.in(v.level(), soil), "test setup: the rain didn't end the drought");
		v.level().setWeatherParameters(6000, 0, false, false);
		int m = tick(helper, soil, random);
		helper.assertTrue(m == 7, "after the rain ended the drought, the dry field by the pond read moisture " + m + " after a random tick, not 7");
		helper.succeed();
	}

	/** Config {@code disasters.drought} false mid-drought: the drought ends at the next round, and the pond wets its field again. */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondSwitchedOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondSwitchedOff")
	public void switchingDroughtsOffLetsThePondWetTheField(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FIELD, 0);
		v.start();
		WorkplaceConfig config = new WorkplaceConfig();
		config.disasters.put("drought", false);
		config.apply();
		v.round();
		helper.assertTrue(v.entity().disaster() == null && !Drought.in(v.level(), soil), "with droughts switched off, the drought under way went on");
		int m = tick(helper, soil, RandomSource.create(11L));
		helper.assertTrue(m == 7, "with droughts switched off, the dry field by the pond read moisture " + m + " after a random tick, not 7");
		helper.succeed();
	}

	/**
	 * The guide: the dry field by water stays dry "until a Well Keeper waters it". A bucket on it wets it to 7, and it
	 * keeps that all day through 20 random ticks; the next day it dries a step a tick again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PondBucket"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PondBucket")
	public void aKeepersBucketWetsAFieldByAPondForTheDay(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos soil = pondField(helper, FIELD, 0);
		v.start();
		int wet = Drought.wet(v.level(), soil);
		helper.assertTrue(wet >= 1, "test setup: the bucket wet " + wet + " farmland");
		RandomSource random = RandomSource.create(13L);
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7, "the Well Keeper's bucket didn't wet the field by the pond");
		for (int i = 1; i <= 20; i++) {
			int m = tick(helper, soil, random);
			helper.assertTrue(m == 7, "the field by the pond, watered by the Well Keeper today, dried to " + m + " on random tick " + i);
		}
		v.level().setDayTime(v.level().getDayTime() + VillageNeeds.DAY);
		int m = tick(helper, soil, random);
		helper.assertTrue(m == 6, "the day after the bucket, the field by the pond in the drought read " + m + " after a random tick, not 6");
		helper.succeed();
	}

	// --- B947: pitcher crops and the other modern crops -------------------------------------------------------------

	/** How many of 400 random ticks (fixed seed) grow a fresh {@code crop} at {@code at}. */
	private static int growths(ServerLevel level, BlockPos at, Block crop) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(at, crop.defaultBlockState(), 2);
			level.getBlockState(at).randomTick(level, at, random);
			BlockState state = level.getBlockState(at);
			boolean grown = !state.is(crop) || (state.hasProperty(BlockStateProperties.AGE_4) ? state.getValue(BlockStateProperties.AGE_4) > 0
				: state.getBlock() instanceof CropBlock c && c.getAge(state) > 0);
			if (grown) {
				grew++;
			}
		}
		level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
		return grew;
	}

	private static BlockPos plot(GameTestHelper helper, BlockPos soil) {
		helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		return helper.absolutePos(soil.above());
	}

	/** In a drought a pitcher crop outside the hall's area grows exactly as often as with no drought (same seed). */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PitcherOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PitcherOutside")
	public void aPitcherCropOutsideTheVillageGrowsAtFullPace(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos crop = plot(helper, FAR_FIELD);
		int usual = growths(v.level(), crop, Blocks.PITCHER_CROP);
		v.start();
		int dry = growths(v.level(), crop, Blocks.PITCHER_CROP);
		helper.assertTrue(usual >= 20, "test setup: the pitcher crop hardly grows: " + usual + " of 400");
		helper.assertTrue(dry == usual, "in a drought a pitcher crop outside the village grew " + dry + " times in 400 random ticks, against " + usual + " without");
		helper.succeed();
	}

	/** Pitcher crops on farmland a Well Keeper watered today grow at their full pace in the drought (guide: "the crops on them"). */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3PitcherWatered"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3PitcherWatered")
	public void aPitcherCropOnWateredFarmlandGrowsAtFullPace(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos crop = plot(helper, FIELD);
		int usual = growths(v.level(), crop, Blocks.PITCHER_CROP);
		v.start();
		int dry = growths(v.level(), crop, Blocks.PITCHER_CROP);
		Drought.wet(v.level(), crop.below());
		int watered = growths(v.level(), crop, Blocks.PITCHER_CROP);
		helper.assertTrue(dry * 4 <= usual * 1.5, "test setup: in the drought the pitcher crop grew " + dry + " of " + usual);
		helper.assertTrue(watered == usual, "on farmland the Well Keeper watered today the pitcher crop grew " + watered + " times, against " + usual + " with no drought");
		helper.succeed();
	}

	/** Torchflower crops (another farm crop of the newer versions) grow at about a quarter pace in the drought too. */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3Torchflower"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3Torchflower")
	public void torchflowerCropsAreHeldBackToo(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos crop = plot(helper, FIELD);
		int usual = growths(v.level(), crop, Blocks.TORCHFLOWER_CROP);
		v.start();
		int dry = growths(v.level(), crop, Blocks.TORCHFLOWER_CROP);
		helper.assertTrue(usual >= 20, "test setup: the torchflower crop hardly grows: " + usual + " of 400");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the torchflower crop grew " + dry + " times, against " + usual + " without: not a quarter pace");
		helper.assertTrue(dry > 0, "in the drought the torchflower crop never grew at all in 400 random ticks");
		helper.succeed();
	}

	// --- B948: the days without rain --------------------------------------------------------------------------------

	/**
	 * The last rain day survives a save and load at every count (0 up to a world 2^40 days old): k days later reads k.
	 * A day saved as a plain number of another width loads too; a garbled one counts from the day it's read, never
	 * from day 0 (which would mean thousands of dry days and a drought at once).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3Ledger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3Ledger")
	public void theRainDaySurvivesASaveAtEveryCount(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		for (long rain : new long[] {0, 1, 5, 6, 1000, 1L << 40}) {
			RainLedger ledger = new RainLedger();
			ledger.rainedOn(rain);
			CompoundTag saved = ledger.save(new CompoundTag(), registries);
			for (long k : new long[] {0, 4, 5, 6, 30}) {
				RainLedger loaded = RainLedger.load(saved.copy(), registries);
				long read = loaded.dryDays(rain + k);
				helper.assertTrue(read == k, "it last rained on day " + rain + "; after a restart, " + k + " days later read " + read + " days without rain");
			}
		}
		CompoundTag asInt = new CompoundTag();
		asInt.putInt("last_rain_day", 50);
		long fromInt = RainLedger.load(asInt, registries).dryDays(56);
		helper.assertTrue(fromInt == 6, "a rain day saved as a smaller number (50) read " + fromInt + " dry days on day 56, not 6");
		CompoundTag garbled = new CompoundTag();
		garbled.putString("last_rain_day", "yesterday");
		RainLedger odd = RainLedger.load(garbled, registries);
		long first = odd.dryDays(300);
		long later = odd.dryDays(302);
		helper.assertTrue(first == 0 && later == 2, "a garbled rain day read " + first + " then " + later + " dry days on days 300 and 302, not 0 and 2");
		helper.succeed();
	}

	/**
	 * The rain day goes into the world's own file, data/aliveworkplace_rain.dat (what a real restart reads): saving the
	 * world writes it, and the ledger read back from that file counts the same 6 dry days.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3LedgerFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3LedgerFile")
	public void theRainDayIsWrittenToTheWorldsFile(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		helper.assertTrue(level.dimension() == net.minecraft.world.level.Level.OVERWORLD, "test setup: the test level isn't the overworld");
		long day = Chronicle.day(level);
		Disasters.rainedOn(level, day - 7);
		Disasters.rainedOn(level, day - 6);
		level.getDataStorage().save();
		java.nio.file.Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(RainLedger.NAME + ".dat");
		helper.assertTrue(java.nio.file.Files.isRegularFile(file), "saving the world wrote no " + file.getFileName());
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("the rain file can't be read back: " + e);
		}
		CompoundTag data = root.getCompound("data");
		helper.assertTrue(data.contains("last_rain_day") && data.getLong("last_rain_day") == day - 6,
			"the world's rain file holds " + data + ", not the last rain on day " + (day - 6));
		long read = RainLedger.load(data, level.registryAccess()).dryDays(day);
		helper.assertTrue(read == 6, "read back from the world's file, 6 dry days are " + read);
		helper.succeed();
	}

	/**
	 * Rain still counts after a restart: in the plains, 6 dry days give the drought its 5%; a restart then rain makes
	 * it 0; another restart doesn't undo the rain (still 0 three dry days on); 6 dry days after that rain it's 5% again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104d3RainAfterRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104d3RainAfterRestart")
	public void rainAfterARestartStartsTheCountAgain(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		String old = level.getBiome(v.hall()).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		biome(level, v.hall(), "minecraft:plains");
		Leftovers.after(helper, () -> biome(level, v.hall(), old));
		long day = Chronicle.day(level);
		Disasters.rainedOn(level, day - 6);
		double six = Disasters.chance(level, v.hall(), v.drought(), v.census());
		helper.assertTrue(Math.abs(six - 0.05) < 1e-6, "test setup: after 6 dry days in the plains the chance is " + six);
		Disasters.forget();
		level.setWeatherParameters(0, 6000, true, false);
		helper.assertTrue(Disasters.dryDays(level) == 0, "while it rains after a restart, the days without rain read " + Disasters.dryDays(level));
		level.setWeatherParameters(6000, 0, false, false);
		Disasters.forget();
		level.setDayTime(level.getDayTime() + 3 * VillageNeeds.DAY);
		long three = Disasters.dryDays(level);
		double chance3 = Disasters.chance(level, v.hall(), v.drought(), v.census());
		helper.assertTrue(three == 3 && chance3 == 0, "3 days after the rain and a restart: " + three + " dry days, a " + chance3 + " drought chance in the plains (expected 3 and 0)");
		level.setDayTime(level.getDayTime() + 3 * VillageNeeds.DAY);
		double chance6 = Disasters.chance(level, v.hall(), v.drought(), v.census());
		helper.assertTrue(Math.abs(chance6 - 0.05) < 1e-6, "6 dry days after the rain the plains' drought chance is " + chance6 + ", not 0.05");
		helper.succeed();
	}

	private static void biome(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	// --- B950: the guard's horse within 24 blocks -------------------------------------------------------------------

	private static Villager guard(GameTestHelper helper, BlockPos post) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.north());
		guard.setNoAi(true);
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return guard;
	}

	private static <T extends AbstractHorse> T mount(GameTestHelper helper, EntityType<T> type, BlockPos at) {
		T horse = helper.spawn(type, at);
		horse.setTamed(true);
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		horse.setNoAi(true);
		horse.setNoGravity(true);
		return horse;
	}

	private static AbstractHorse found(GameTestHelper helper, Villager guard, BlockPos post) {
		return Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post));
	}

	/** On the diagonal: a horse 16 and 17 blocks off (23.3) is the guard's, one 17 and 17 off (24.04) isn't, though inside a 24-block square. */
	//$ gametest_ticks_batch HUGE '60' '"qa1104d3HorseDiagonal"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1104d3HorseDiagonal")
	public void aHorseJustPastTwentyFourOnTheDiagonalIsNotTheGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(2, 2, 2);
		Villager guard = guard(helper, post);
		AbstractHorse far = mount(helper, EntityType.HORSE, new BlockPos(19, 2, 19));
		helper.assertTrue(found(helper, guard, post) == null, "a horse 17 and 17 blocks off (24.04 from the post) is the guard's");
		far.discard();
		AbstractHorse near = mount(helper, EntityType.HORSE, new BlockPos(18, 2, 19));
		helper.assertTrue(found(helper, guard, post) == near, "a horse 16 and 17 blocks off (23.35 from the post) isn't the guard's");
		near.discard();
		helper.succeed();
	}

	/** East, west, south and north of the post's middle: a horse 24.0 blocks off is the guard's, 24.1 isn't, on every side. */
	//$ gametest_ticks_batch HUGE '60' '"qa1104d3HorseSides"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1104d3HorseSides")
	public void theReachIsTwentyFourOnEverySide(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(15, 2, 15);
		Villager guard = guard(helper, post);
		Vec3 middle = Vec3.atBottomCenterOf(helper.absolutePos(post));
		AbstractHorse horse = mount(helper, EntityType.HORSE, new BlockPos(20, 2, 15));
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		String[] names = {"east", "west", "south", "north"};
		for (int s = 0; s < 4; s++) {
			for (double d : new double[] {24.0, 24.1}) {
				horse.moveTo(middle.x + sides[s][0] * d, middle.y, middle.z + sides[s][1] * d, 0f, 0f);
				AbstractHorse got = found(helper, guard, post);
				if (d <= 24.0) {
					helper.assertTrue(got == horse, "a horse " + d + " blocks " + names[s] + " of the post isn't the guard's");
				} else {
					helper.assertTrue(got == null, "a horse " + d + " blocks " + names[s] + " of the post is the guard's");
				}
			}
		}
		horse.discard();
		helper.succeed();
	}

	/** A camel is wider than a horse, which must not stretch the reach: saddled 24.6 blocks off it isn't the guard's; 23.9 off it is. */
	//$ gametest_ticks_batch HUGE '60' '"qa1104d3CamelReach"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1104d3CamelReach")
	public void aWideCamelJustPastTwentyFourIsNotTheGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(2, 2, 14);
		Villager guard = guard(helper, post);
		Vec3 middle = Vec3.atBottomCenterOf(helper.absolutePos(post));
		var camel = mount(helper, EntityType.CAMEL, new BlockPos(20, 2, 14));
		helper.assertTrue(Cavalry.usable(camel), "test setup: the saddled camel can't carry a guard");
		camel.moveTo(middle.x + 24.6, middle.y, middle.z, 0f, 0f);
		helper.assertTrue(found(helper, guard, post) == null, "a saddled camel 24.6 blocks from the post is the guard's");
		camel.moveTo(middle.x + 23.9, middle.y, middle.z, 0f, 0f);
		helper.assertTrue(found(helper, guard, post) == camel, "a saddled camel 23.9 blocks from the post isn't the guard's");
		camel.discard();
		helper.succeed();
	}
}
