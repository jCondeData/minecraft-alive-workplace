package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The disaster engine (ROADMAP 32.15), with the test datapack's {@code aliveworkplace_test:test_blight}, which has every
 * effect of the toolbox: crops don't grow, farmland dries by water, cauldrons lose a level a day, the villagers fall
 * ill and pass it on, are unhappy, a fire starts and farmers work at half pace. It ends after its two days or with rain,
 * survives a save and load of the hall (and a hall saved before it loads with none), is foretold a day ahead by a
 * librarian, and switched off it never starts (one under way ends). And every new sentence a player reads.
 */
public class DisasterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation BLIGHT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_blight");
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** Day 21 begins here: far from the other tests' days. */
	private static final long BASE = 20 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Disaster blight) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 10;
		level.setDayTime(BASE + 1000);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			level.setWeatherParameters(6000, 0, false, false);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		Disaster blight = Disasters.get(BLIGHT).orElse(null);
		helper.assertTrue(blight != null, "the test datapack's disaster isn't loaded: " + Disasters.all());
		return new Village(level, hall, entity, blight);
	}

	private static void start(Village v) {
		Disasters.start(v.level(), v.hall(), v.entity(), v.blight(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(1L));
		if (v.entity().disaster() == null) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("the disaster didn't start");
		}
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	//$ gametest_ticks_batch AREA '100' '"disasterCrops"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterCrops")
	public void cropsDontGrowInTheDisaster(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.WHEAT);
		BlockPos crop = helper.absolutePos(new BlockPos(4, 2, 4));
		RandomSource random = RandomSource.create(7L);
		start(v);
		helper.assertFalse(Disasters.cropGrows(v.level(), crop, random), "a crop in the village may grow with crop_growth 0");
		for (int i = 0; i < 300; i++) {
			BlockState state = v.level().getBlockState(crop);
			state.randomTick(v.level(), crop, random);
		}
		helper.assertTrue(((CropBlock) Blocks.WHEAT).getAge(v.level().getBlockState(crop)) == 0, "the wheat grew in the disaster");
		BlockPos outside = v.hall().offset(40, 0, 0);
		helper.assertTrue(Disasters.cropGrows(v.level(), outside, random), "a crop outside the village is held back");
		Disasters.end(v.level(), v.hall(), v.entity(), v.blight(), Chronicle.day(v.level()));
		for (int i = 0; i < 300; i++) {
			v.level().getBlockState(crop).randomTick(v.level(), crop, random);
		}
		helper.assertTrue(((CropBlock) Blocks.WHEAT).getAge(v.level().getBlockState(crop)) > 0, "the wheat didn't grow after the disaster");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterFarmland"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterFarmland")
	public void farmlandDriesByWater(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(5, 1, 4), Blocks.WATER);
		BlockPos soil = helper.absolutePos(new BlockPos(4, 1, 4));
		RandomSource random = RandomSource.create(3L);
		start(v);
		v.level().getBlockState(soil).randomTick(v.level(), soil, random);
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 6, "wet farmland by water didn't dry a step");
		Disasters.end(v.level(), v.hall(), v.entity(), v.blight(), Chronicle.day(v.level()));
		v.level().getBlockState(soil).randomTick(v.level(), soil, random);
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7, "the farmland by water didn't get wet again after");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterCauldrons"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterCauldrons")
	public void cauldronsLoseALevelADay(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		BlockPos cauldron = helper.absolutePos(new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> { // (the cauldron's POI is added on the next tick)
			start(v);
			VillageHalls.Census census = VillageHalls.census(v.level(), v.hall());
			Disasters.round(v.level(), v.hall(), v.entity(), census);
			helper.assertTrue(v.level().getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2, "the cauldron didn't lose a level");
			Disasters.round(v.level(), v.hall(), v.entity(), census);
			helper.assertTrue(v.level().getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2, "the cauldron lost two levels in one day");
			v.level().setDayTime(BASE + VillageNeeds.DAY + 1000);
			Disasters.round(v.level(), v.hall(), v.entity(), census);
			helper.assertTrue(v.level().getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 1, "the cauldron didn't lose a level the next day");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '100' '"disasterIllness"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterIllness")
	public void villagersFallIll(GameTestHelper helper) {
		Village v = village(helper);
		List<Villager> villagers = List.of(villager(helper, 4, 4), villager(helper, 16, 4), villager(helper, 4, 16));
		start(v);
		Disasters.daily(v.level(), v.hall(), v.blight(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(5L));
		helper.assertTrue(villagers.stream().filter(Sickness::isIll).count() >= 2, "the illness chance made no one ill");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterContagion"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterContagion")
	public void illnessPassesOn(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 4, 4);
		Villager near = villager(helper, 6, 4);
		Villager far = villager(helper, 16, 16);
		start(v);
		Sickness.fallIll(v.level(), sick);
		Disasters.contagion(v.level(), v.hall(), v.blight(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(5L));
		helper.assertTrue(Sickness.isIll(near), "the villager 2 blocks from the ill one didn't catch it");
		helper.assertFalse(Sickness.isIll(far), "a villager far from the ill one caught it");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterMood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterMood")
	public void villagersAreUnhappy(GameTestHelper helper) {
		Village v = village(helper);
		Villager villager = villager(helper, 6, 6);
		helper.assertTrue(Disasters.mood(v.level(), villager) == null, "a mood reason before the disaster");
		start(v);
		var mood = Disasters.mood(v.level(), villager);
		helper.assertTrue(mood != null && mood.points() == -10, "no -10 mood reason in the disaster: " + mood);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Moods.work(v.level(), villager).bad().contains(mood.reason()),
			"the villager's mood doesn't list the disaster");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterFire"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterFire")
	public void aFireStarts(GameTestHelper helper) {
		Village v = village(helper);
		start(v);
		helper.assertTrue(Disasters.startFire(v.level(), v.hall(), RandomSource.create(9L)), "no fire took");
		long fires = BlockPos.betweenClosedStream(helper.getBounds().inflate(1)).filter(p -> v.level().getBlockState(p).is(Blocks.FIRE)).count();
		helper.assertTrue(fires >= 1, "no fire in the village");
		BlockPos.betweenClosedStream(helper.getBounds().inflate(1)).filter(p -> v.level().getBlockState(p).is(Blocks.FIRE)).toList()
			.forEach(p -> v.level().removeBlock(p, false));
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterPace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterPace")
	public void farmersWorkAtHalfPace(GameTestHelper helper) {
		Village v = village(helper);
		Villager farmer = villager(helper, 6, 6);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		Villager mason = villager(helper, 8, 6);
		mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON));
		helper.assertTrue(Disasters.pace(farmer) == 1f, "a farmer slowed before the disaster");
		start(v);
		helper.assertTrue(Disasters.pace(farmer) == 2f, "the farmer's work doesn't take twice as long: " + Disasters.pace(farmer));
		helper.assertTrue(Disasters.pace(mason) == 1f, "the mason is slowed too");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterEnds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterEnds")
	public void endsAfterItsDaysOrWithRain(GameTestHelper helper) {
		Village v = village(helper);
		start(v);
		long day = Chronicle.day(v.level());
		helper.assertTrue(v.entity().disasterSince() == day && v.entity().disasterEnds() == day + 1, "not a two-day disaster from today: "
			+ v.entity().disasterSince() + " to " + v.entity().disasterEnds());
		v.level().setDayTime(BASE + VillageNeeds.DAY + 1000);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() != null, "over before its second day was out");
		v.level().setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() == null, "not over after its two days");
		helper.assertTrue(v.entity().lastDisasterDay() == day + 2, "the day it ended isn't kept");
		helper.assertTrue(Disasters.mood(v.level(), villager(helper, 6, 6)) == null, "the village is still afflicted after it ended");
		// Seven days' rest: none may start, though its chance is 1.
		helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(1L)).isEmpty(),
			"a disaster started in the days of rest");
		// With rain.
		v.entity().setDisaster(null, -1, -1, -100);
		start(v);
		v.level().setWeatherParameters(0, 6000, true, false);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() == null, "the rain didn't end it");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterSaved")
	public void survivesASaveAndLoad(GameTestHelper helper) {
		Village v = village(helper);
		start(v);
		long since = v.entity().disasterSince();
		long ends = v.entity().disasterEnds();
		CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
		v.entity().setDisaster(null, -1, -1, 5);
		v.entity().loadWithComponents(tag, v.level().registryAccess());
		helper.assertTrue(BLIGHT.equals(v.entity().disaster()) && v.entity().disasterSince() == since && v.entity().disasterEnds() == ends
			&& v.entity().lastDisasterDay() == -100, "the disaster wasn't saved and loaded: " + v.entity().disaster());
		// A hall saved before 32.15: none, and never had one.
		tag.remove("disaster");
		tag.remove("disasterSince");
		tag.remove("disasterEnds");
		tag.remove("lastDisasterDay");
		v.entity().loadWithComponents(tag, v.level().registryAccess());
		helper.assertTrue(v.entity().disaster() == null && v.entity().disasterSince() == -1 && v.entity().disasterEnds() == -1
			&& v.entity().lastDisasterDay() == -100, "an old hall didn't load with no disaster");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterOff")
	public void switchedOffItNeverStarts(GameTestHelper helper) {
		Village v = village(helper);
		VillageHalls.Census census = VillageHalls.census(v.level(), v.hall());
		Disasters.DISABLED = Set.of(Disasters.key(BLIGHT));
		for (int i = 0; i < 20; i++) {
			helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), census, RandomSource.create(i)).isEmpty(), "a switched-off disaster started");
		}
		helper.assertTrue(v.entity().disaster() == null, "the hall has a disaster");
		Disasters.DISABLED = Set.of();
		helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), census, RandomSource.create(1L)).isPresent(), "switched on, a chance of 1 didn't start it");
		// One under way ends at once when switched off.
		Disasters.DISABLED = Set.of(Disasters.key(BLIGHT));
		Disasters.round(v.level(), v.hall(), v.entity(), census);
		helper.assertTrue(v.entity().disaster() == null, "the disaster under way went on, switched off");
		Disasters.DISABLED = Set.of();
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"disasterForetold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "disasterForetold")
	public void aLibrarianForetellsItADayAhead(GameTestHelper helper) {
		Village v = village(helper);
		Villager librarian = villager(helper, 6, 6);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		VillageHalls.Census census = VillageHalls.census(v.level(), v.hall());
		helper.assertTrue(Disasters.foreteller(v.level(), v.hall(), v.blight(), census).isPresent(), "the librarian doesn't foretell it");
		start(v);
		long day = Chronicle.day(v.level());
		helper.assertTrue(v.entity().disasterSince() == day + 1, "not foretold a day ahead: begins on " + v.entity().disasterSince() + ", today " + day);
		Disasters.round(v.level(), v.hall(), v.entity(), census);
		helper.assertTrue(Disasters.mood(v.level(), librarian) == null, "the village is afflicted before it comes");
		helper.assertTrue(Disasters.line(v.entity(), day) != null, "the wellbeing icon doesn't show it foretold");
		helper.assertFalse(Disasters.tips(v.level(), v.hall(), census).isEmpty(), "What next? doesn't show it");
		v.level().setDayTime(BASE + VillageNeeds.DAY + 1000);
		Disasters.round(v.level(), v.hall(), v.entity(), census);
		helper.assertTrue(Disasters.mood(v.level(), librarian) != null, "the village isn't afflicted the day it comes");
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("disaster.aliveworkplace.start", "disaster.aliveworkplace.foretold", "disaster.aliveworkplace.end",
			"chronicle.aliveworkplace.disaster.start", "chronicle.aliveworkplace.disaster.foretold", "chronicle.aliveworkplace.disaster.end",
			"mood.aliveworkplace.reason.disaster", "pace.aliveworkplace.source.disaster", "screen.aliveworkplace.hall.disaster",
			"screen.aliveworkplace.hall.disaster_rain", "screen.aliveworkplace.hall.disaster_foretold", "advice.aliveworkplace.disaster",
			"advice.aliveworkplace.disaster.how", "advice.aliveworkplace.disaster_answer", "advice.aliveworkplace.disaster_answer.how")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
