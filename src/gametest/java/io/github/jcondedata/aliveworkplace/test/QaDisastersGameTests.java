package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StemBlock;

/**
 * QA (2026-10-24, lane-d-1024-1908), written from ROADMAP 32.15 (the disaster engine), 32.17 (Drought and the Well
 * Keeper) and 32.18 (Plague and the Physician), covering what DisasterGameTests, DroughtGameTests and PlagueGameTests
 * leave out: the drought's chance from both sides of its "5 days without rain" in the plains, 15% in the desert, savanna
 * and badlands, and doubled by a standing desert raiders' lair; the plague's "Town and up" from both sides and doubled
 * with more villagers than beds; one disaster at a time and the 7 days' rest from both sides; a drought in a saved hall
 * coming back after a restart (memory forgotten, the hall loaded, one round); water cauldrons emptied a level a day and
 * only in the hall's area; pumpkin stems at a quarter pace; a Well Keeper's water good for that day only; a Fountain or
 * a Well II taking "the wells are low" away, a Well outside the village not; the plague's ill not getting well by
 * themselves, bedridden from two days and held in bed until a Remedy; Medicine making the plague less catching; the
 * config {@code disasters} switch and the threat levels 0 and 1; and the sentences a player reads, rendered.
 */
public class QaDisastersGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	private static final ResourceLocation BLIGHT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_blight");
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	/** Day 81 begins here: far from the other disaster tests' days. */
	private static final long BASE = 80 * VillageNeeds.DAY;
	private static final double EPS = 1e-6;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		VillageHalls.Census census() {
			return VillageHalls.census(level, hall);
		}

		long day() {
			return Chronicle.day(level);
		}

		void round() {
			Disasters.round(level, hall, entity, census());
		}

		Disaster drought() {
			return Drought.get().orElseThrow(() -> new GameTestAssertException("drought.json isn't loaded: " + Disasters.all()));
		}

		Disaster plague() {
			return Disasters.get(Plague.ID).orElseThrow(() -> new GameTestAssertException("plague.json isn't loaded: " + Disasters.all()));
		}

		void start(Disaster d) {
			Disasters.start(level, hall, entity, d, census(), RandomSource.create(1L));
			if (!d.id().equals(entity.disaster())) {
				throw new GameTestAssertException("the " + d.id() + " didn't start");
			}
		}
	}

	private static Village village(GameTestHelper helper, BlockPos at, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int wasRadius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		int threat = Threats.LEVEL;
		VillageHalls.RADIUS = radius;
		level.setDayTime(BASE + 1000);
		level.setWeatherParameters(6000, 0, false, false);
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			Research.forget();
			VillageHalls.RADIUS = wasRadius;
			level.setDayTime(was);
			level.setWeatherParameters(6000, 0, false, false);
			new WorkplaceConfig().apply();
			Threats.LEVEL = threat;
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		entity.setDisaster(null, -1, -1, -100);
		Disasters.forget();
		return new Village(level, hall, entity);
	}

	private static Village village(GameTestHelper helper) {
		return village(helper, HALL, 20);
	}

	private static Villager villager(GameTestHelper helper, double x, double z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new net.minecraft.world.phys.Vec3(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	/** Sets the biome round {@code at} (and puts the old one back after the test). */
	private static void biome(GameTestHelper helper, BlockPos at, String biome) {
		ServerLevel level = helper.getLevel();
		String old = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, at, biome);
		Leftovers.after(helper, () -> fill(level, at, old));
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	private static void assertChance(GameTestHelper helper, double got, double want, String what) {
		helper.assertTrue(Math.abs(got - want) < EPS, what + ": the chance is " + got + ", not " + want);
	}

	private static void finished(GameTestHelper helper, ServerLevel level, ResourceLocation blueprint, BlockPos absolute) {
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), absolute, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(blueprint, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
	}

	private static void forget(GameTestHelper helper, ServerLevel level, ResourceLocation blueprint, BlockPos absolute) {
		BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), absolute, Rotation.NONE, Mirror.NONE));
	}

	// --- 32.17: the drought's chance.

	/**
	 * In the plains the drought has no chance on the 4th day without rain and 5% on the 5th (and on); in the desert,
	 * savanna and badlands 15% from the first dry day; never on a rainy day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtChance")
	public void theDroughtsChanceByBiomeAndDryDays(GameTestHelper helper) {
		Village v = village(helper);
		biome(helper, v.hall(), "minecraft:plains");
		VillageHalls.Census census = v.census();
		long day = v.day();
		Disasters.rainedOn(v.level(), day - 4);
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0, "plains, 4 days without rain");
		Disasters.rainedOn(v.level(), day - 5);
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0.05, "plains, 5 days without rain");
		Disasters.rainedOn(v.level(), day - 12);
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0.05, "plains, 12 days without rain");
		for (String dry : List.of("minecraft:desert", "minecraft:savanna", "minecraft:badlands")) {
			fill(v.level(), v.hall(), dry);
			Disasters.rainedOn(v.level(), day - 1);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0.15, dry + ", 1 day without rain");
			Disasters.rainedOn(v.level(), day - 6);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0.15, dry + ", 6 days without rain");
		}
		v.level().setWeatherParameters(0, 6000, true, false);
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), census), 0, "the desert in the rain");
		helper.succeed();
	}

	/** A standing desert raiders' lair makes the drought twice as likely: 30% in the desert, 10% in the plains after 5 dry days. */
	//$ gametest_ticks_batch HUGE '200' '"qaDroughtLair"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaDroughtLair")
	public void aDesertLairDoublesTheDrought(GameTestHelper helper) {
		Village v = village(helper, new BlockPos(3, 2, 3), 16);
		ServerLevel level = v.level();
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))
				.forEach(Mob::discard);
			Lairs.forget(level);
			ThreatData.get(level).forgetPast(v.hall());
			Lairs.ENABLED = lairs;
		});
		biome(helper, v.hall(), "minecraft:desert");
		Disasters.rainedOn(level, v.day() - 6);
		assertChance(helper, Disasters.chance(level, v.hall(), v.drought(), v.census()), 0.15, "the desert, no lair");
		Lairs.Lair lair = Lairs.found(level, v.hall(), helper.absolutePos(new BlockPos(18, 1, 18)), DESERT);
		helper.assertTrue(lair != null && Lairs.of(level, v.hall(), DESERT).isPresent(), "no desert raiders' lair stands by the village (test setup): " + lair);
		assertChance(helper, Disasters.chance(level, v.hall(), v.drought(), v.census()), 0.30, "the desert, a desert raiders' lair standing");
		fill(level, v.hall(), "minecraft:plains");
		assertChance(helper, Disasters.chance(level, v.hall(), v.drought(), v.census()), 0.10, "the plains after 6 dry days, a desert raiders' lair standing");
		helper.succeed();
	}

	// --- 32.18: the plague's chance.

	/** The plague: no chance in a Hamlet or a Village, 3% in a Town or a City, 6% with more villagers than beds. */
	//$ gametest_ticks_batch AREA '100' '"qaPlagueChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPlagueChance")
	public void thePlaguesChanceIsTownAndUpAndDoubledWhenCrowded(GameTestHelper helper) {
		Village v = village(helper);
		for (int i = 0; i < 3; i++) {
			Villager villager = villager(helper, 4.5 + 3 * i, 4.5);
			Beds.home(villager, helper.absolutePos(new BlockPos(4 + 3 * i, 2, 8)));
		}
		helper.runAfterDelay(5, () -> {
			VillageHalls.Census census = v.census();
			helper.assertTrue(census.villagers() == 3 && census.beds() == 3, "not 3 villagers and 3 beds (test setup): " + census.villagers() + ", " + census.beds());
			v.entity().setRank(VillageRanks.Rank.HAMLET);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), census), 0, "a Hamlet");
			v.entity().setRank(VillageRanks.Rank.VILLAGE);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), census), 0, "a Village");
			v.entity().setRank(VillageRanks.Rank.TOWN);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), census), 0.03, "a Town with a bed for everyone");
			v.entity().setRank(VillageRanks.Rank.CITY);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), census), 0.03, "a City with a bed for everyone");
			villager(helper, 13.5, 4.5);
			VillageHalls.Census crowded = v.census();
			helper.assertTrue(crowded.villagers() == 4 && crowded.beds() == 3, "not 4 villagers and 3 beds (test setup)");
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), crowded), 0.06, "a City with more villagers than beds");
			v.entity().setRank(VillageRanks.Rank.VILLAGE);
			assertChance(helper, Disasters.chance(v.level(), v.hall(), v.plague(), crowded), 0, "a crowded Village");
			helper.succeed();
		});
	}

	// --- 32.15: one at a time, seven days' rest.

	/**
	 * While a drought is under way (or only foretold) no other disaster starts, though the test blight's chance is 1; after
	 * one ends, none starts on the 6th day after and one does on the 7th.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDisasterRest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDisasterRest")
	public void oneAtATimeAndSevenDaysRestFromBothSides(GameTestHelper helper) {
		Village v = village(helper);
		helper.assertTrue(Disasters.get(BLIGHT).isPresent(), "the test blight isn't loaded");
		v.start(v.drought());
		for (int i = 0; i < 10; i++) {
			helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), v.census(), RandomSource.create(i)).isEmpty(),
				"a second disaster started with the drought under way");
		}
		helper.assertTrue(Drought.ID.equals(v.entity().disaster()), "the drought gave way to another: " + v.entity().disaster());
		// Only foretold: still one at a time.
		v.entity().setDisaster(Drought.ID, v.day() + 1, v.day() + 3, -100);
		helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), v.census(), RandomSource.create(1L)).isEmpty(),
			"a disaster started with a drought foretold");
		// Ended today: six days' rest isn't enough, seven is.
		long ended = v.day();
		v.entity().setDisaster(null, -1, -1, ended);
		v.level().setDayTime(BASE + 6 * VillageNeeds.DAY + 1000);
		helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), v.census(), RandomSource.create(1L)).isEmpty(),
			"a disaster started on the 6th day after the last one ended");
		helper.assertTrue(v.entity().disaster() == null, "the hall has a disaster in its days of rest");
		v.level().setDayTime(BASE + 7 * VillageNeeds.DAY + 1000);
		helper.assertTrue(Disasters.roll(v.level(), v.hall(), v.entity(), v.census(), RandomSource.create(1L)).isPresent() && v.entity().disaster() != null,
			"no disaster could start 7 days after the last, with the blight's chance of 1");
		helper.succeed();
	}

	// --- 32.15 / 32.17: save and a restart.

	/**
	 * A drought in a hall saved to disk comes back after a restart: the memory of afflicted villages forgotten and the hall
	 * loaded, one hall round holds the wheat back again, with its days left as they were.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtRestart")
	public void aDroughtComesBackAfterARestart(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.WHEAT);
		BlockPos crop = helper.absolutePos(new BlockPos(6, 2, 6));
		v.start(v.drought());
		long since = v.entity().disasterSince();
		long ends = v.entity().disasterEnds();
		CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
		// The restart: nothing kept in memory, the hall read back from its tag.
		Disasters.forget();
		v.entity().setDisaster(null, -1, -1, -100);
		v.entity().loadWithComponents(tag, v.level().registryAccess());
		helper.assertTrue(Drought.ID.equals(v.entity().disaster()) && v.entity().disasterSince() == since && v.entity().disasterEnds() == ends,
			"the drought wasn't read back: " + v.entity().disaster() + " " + v.entity().disasterSince() + "-" + v.entity().disasterEnds());
		v.round();
		helper.assertTrue(Drought.in(v.level(), crop), "after a restart and a hall round the village isn't in the drought");
		RandomSource gate = RandomSource.create(42L);
		int passed = 0;
		for (int i = 0; i < 400; i++) {
			if (Disasters.cropGrows(v.level(), crop, gate)) {
				passed++;
			}
		}
		helper.assertTrue(passed <= 115, "after the restart " + passed + " of 400 random ticks may grow the wheat: not a quarter pace");
		helper.assertTrue(v.entity().disasterEnds() == ends, "the restart changed the drought's last day");
		// A foretold drought saved and read back stays foretold: the village isn't afflicted before it comes.
		v.entity().setDisaster(Drought.ID, v.day() + 1, v.day() + 3, -100);
		CompoundTag foretold = v.entity().saveWithoutMetadata(v.level().registryAccess());
		Disasters.forget();
		v.entity().loadWithComponents(foretold, v.level().registryAccess());
		v.round();
		helper.assertFalse(Drought.in(v.level(), crop), "a foretold drought read back after a restart came at once");
		helper.assertTrue(Drought.ID.equals(v.entity().disaster()), "the foretold drought was lost");
		helper.succeed();
	}

	// --- 32.17: the drought's effects.

	/** Day by day the drought takes a level from a full water cauldron in the village until it's empty; a cauldron outside the village keeps its water. */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtCauldrons"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtCauldrons")
	public void cauldronsRunDryOneLevelADayOnlyInTheVillage(GameTestHelper helper) {
		Village v = village(helper, HALL, 8);
		BlockPos in = new BlockPos(12, 2, 16);
		BlockPos out = new BlockPos(2, 2, 2);
		helper.setBlock(in, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		helper.setBlock(out, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		helper.runAfterDelay(5, () -> { // (a cauldron's point of interest is added on the next tick)
			v.start(v.drought());
			helper.assertTrue(v.entity().disasterEnds() >= v.day() + 2, "a drought shorter than 3 days: ends " + v.entity().disasterEnds());
			int[] want = {2, 1, 0};
			for (int d = 0; d < 3; d++) {
				v.level().setDayTime(BASE + d * VillageNeeds.DAY + 1000);
				v.round();
				v.round(); // (twice the same day: one level)
				var state = helper.getBlockState(in);
				int got = state.is(Blocks.WATER_CAULDRON) ? state.getValue(LayeredCauldronBlock.LEVEL) : state.is(Blocks.CAULDRON) ? 0 : -1;
				helper.assertTrue(got == want[d], "on day " + (d + 1) + " of the drought the village's cauldron holds " + got + ", not " + want[d]);
			}
			helper.assertTrue(helper.getBlockState(in).is(Blocks.CAULDRON), "the emptied cauldron isn't an empty cauldron: " + helper.getBlockState(in));
			helper.assertTrue(helper.getBlockState(out).is(Blocks.WATER_CAULDRON) && helper.getBlockState(out).getValue(LayeredCauldronBlock.LEVEL) == 3,
				"a cauldron outside the village lost water: " + helper.getBlockState(out));
			helper.succeed();
		});
	}

	/** Pumpkin stems grow at a quarter pace in the drought too (400 random ticks, a fixed seed). */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtStems"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtStems")
	public void pumpkinStemsAreHeldBack(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(6, 1, 10), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(6, 2, 10), Blocks.PUMPKIN_STEM);
		BlockPos stem = helper.absolutePos(new BlockPos(6, 2, 10));
		int usual = stemGrowths(v.level(), stem);
		v.start(v.drought());
		int dry = stemGrowths(v.level(), stem);
		helper.assertTrue(usual >= 20, "the stem hardly grows without a drought: " + usual + " of 400 (test setup)");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the stem grew " + dry + " times, against " + usual + " without: not a quarter pace");
		helper.succeed();
	}

	private static int stemGrowths(ServerLevel level, BlockPos stem) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(stem, Blocks.PUMPKIN_STEM.defaultBlockState(), 2);
			level.getBlockState(stem).randomTick(level, stem, random);
			if (level.getBlockState(stem).getValue(StemBlock.AGE) > 0) {
				grew++;
			}
		}
		return grew;
	}

	/** A Well Keeper's bucket is good for the day it's poured: the next day of the drought the wheat on it is back to a quarter pace and the farmland dries. */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtWateredDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtWateredDay")
	public void wateredFarmlandIsGoodForThatDayOnly(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos field = new BlockPos(8, 1, 6);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(field.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 2));
				helper.setBlock(field.offset(dx, 1, dz), Blocks.WHEAT);
			}
		}
		BlockPos soil = helper.absolutePos(field);
		BlockPos crop = soil.above();
		v.start(v.drought());
		helper.assertTrue(Drought.wet(v.level(), soil) == 9, "a bucket didn't wet 9 farmland");
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7, "the bucket didn't wet the farmland through (moisture 7)");
		RandomSource gate = RandomSource.create(42L);
		for (int i = 0; i < 400; i++) {
			helper.assertTrue(Disasters.cropGrows(v.level(), crop, gate), "wheat on farmland watered today was held back on random tick " + i);
		}
		// The next day: the drought goes on, the water is yesterday's.
		v.level().setDayTime(BASE + VillageNeeds.DAY + 1000);
		v.round();
		helper.assertTrue(Drought.in(v.level(), soil) && !Drought.watered(v.level(), soil), "the farmland still counts as watered the next day");
		int passed = 0;
		for (int i = 0; i < 400; i++) {
			if (Disasters.cropGrows(v.level(), crop, gate)) {
				passed++;
			}
		}
		helper.assertTrue(passed <= 115, "the day after the bucket " + passed + " of 400 random ticks may grow the wheat: not back to a quarter pace");
		v.level().getBlockState(soil).randomTick(v.level(), soil, RandomSource.create(3L));
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 6, "yesterday's watered farmland didn't dry in the drought");
		helper.succeed();
	}

	/** "The wells are low" goes with a finished Fountain or Well II as with a Well; a Well finished outside the village doesn't take it away, and a villager outside the village has no such reason. */
	//$ gametest_ticks_batch AREA '100' '"qaDroughtFountain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDroughtFountain")
	public void aFountainOrWellIITakesTheMoodAwayAFarWellDoesNot(GameTestHelper helper) {
		Village v = village(helper);
		Villager villager = villager(helper, 6.5, 6.5);
		v.start(v.drought());
		String low = Language.getInstance().getOrDefault("mood.aliveworkplace.reason.drought");
		var mood = Disasters.mood(v.level(), villager);
		helper.assertTrue(mood != null && mood.points() == -10 && mood.reason().getString().equals(low), "not -10 for the wells being low: " + mood);
		BlockPos far = v.hall().offset(60, 0, 0);
		finished(helper, v.level(), StarterBlueprints.WELL.id(), far);
		helper.assertTrue(Disasters.mood(v.level(), villager) != null, "a Well finished 60 blocks off, outside the village, took the mood reason away");
		BlockPos fountain = helper.absolutePos(new BlockPos(2, 2, 24));
		finished(helper, v.level(), StarterBlueprints.FOUNTAIN.id(), fountain);
		helper.assertTrue(Disasters.mood(v.level(), villager) == null, "a finished Fountain didn't take the mood reason away");
		forget(helper, v.level(), StarterBlueprints.FOUNTAIN.id(), fountain);
		helper.assertTrue(Disasters.mood(v.level(), villager) != null, "the mood reason didn't come back without the Fountain (test setup)");
		finished(helper, v.level(), StarterBlueprints.WELL_2.id(), fountain);
		helper.assertTrue(Disasters.mood(v.level(), villager) == null, "a finished Well II didn't take the mood reason away");
		helper.succeed();
	}

	// --- 32.18: the plague's ill.

	/**
	 * In a plague the ill don't get well by themselves (five days on, still ill); a day and a half ill they're up, from two
	 * days they go to their bed and stay in it (a villager getting up is held back), until a Remedy cures them by day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPlagueBed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPlagueBed")
	public void thePlaguesIllStayIllAndKeepToTheirBed(GameTestHelper helper) {
		Village v = village(helper);
		Villager ill = villager(helper, 6.5, 7.5);
		BlockPos bed = helper.absolutePos(new BlockPos(6, 2, 8));
		Beds.home(ill, bed);
		// "by day": level.isDay() reads the sky's darkness, worked out once a tick, so it's the clock village() set only
		// after a tick, and rain or thunder left by an earlier batch darkens it (lane E, 2026-10-31: failed after the threat
		// tests with the clock at night): clear skies, then the test a tick later.
		v.level().setRainLevel(0);
		v.level().setThunderLevel(0);
		helper.runAfterDelay(1, () -> plagueBed(helper, v, ill, bed));
	}

	private static void plagueBed(GameTestHelper helper, Village v, Villager ill, BlockPos bed) {
		helper.assertTrue(v.level().isDay(), "test setup: not day a tick after the clock was set to morning");
		v.start(v.plague());
		helper.assertTrue(Sickness.isPlagueIll(ill) && Plague.underway(v.level(), v.hall()), "the only villager isn't the plague's first case (test setup)");
		long now = v.level().getGameTime();
		ModAttachments.ILL_SINCE.set(ill, now - (Plague.BEDRIDDEN_DAYS * VillageNeeds.DAY - VillageNeeds.DAY / 2));
		Sickness.round(v.level(), ill, 1);
		helper.assertFalse(ill.isSleeping() || Plague.bedridden(v.level(), ill), "a villager a day and a half ill keeps to bed");
		ModAttachments.ILL_SINCE.set(ill, now - 5 * VillageNeeds.DAY);
		Sickness.round(v.level(), ill, 1);
		helper.assertTrue(Sickness.isPlagueIll(ill), "five days ill with the plague, the villager got well by themselves");
		helper.assertTrue(ill.isSleeping() && ill.getSleepingPos().map(bed::equals).orElse(false), "two days and more ill, the villager isn't in their bed: "
			+ ill.getSleepingPos());
		ill.stopSleeping();
		helper.assertTrue(ill.isSleeping(), "the bedridden villager got up");
		helper.assertTrue(Plague.cure(v.level(), ill), "the Remedy found no illness to cure");
		helper.assertFalse(Sickness.isIll(ill) || ill.isSleeping(), "cured by day, the villager stays ill or in bed");
		helper.succeed();
	}

	/** Medicine makes the plague less catching: 400 rounds beside the ill, fewer catch it with two levels of Medicine (fixed seed). */
	//$ gametest_ticks_batch AREA '100' '"qaPlagueMedicine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPlagueMedicine")
	public void medicineMakesThePlagueLessCatching(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 6.5, 6.5);
		Villager near = villager(helper, 8.5, 6.5);
		Disaster.Effect contagion = v.plague().effect(Disaster.Effect.Type.CONTAGION).orElseThrow();
		Sickness.catchPlague(v.level(), sick);
		int without = catches(v.level(), contagion, sick, near);
		v.entity().setResearch(new Research.State(Map.of(Research.Topic.MEDICINE.key(), 2), Optional.empty(), 0, false));
		Research.forget();
		helper.assertTrue(Research.level(near, Research.Topic.MEDICINE) == 2, "the village doesn't know Medicine 2 (test setup)");
		int with = catches(v.level(), contagion, sick, near);
		helper.assertTrue(without >= 50 && without <= 110, "without Medicine " + without + " of 400 rounds passed it on, not about 1 in 5");
		helper.assertTrue(with < without * 0.8, "with Medicine " + with + " of 400 rounds passed it on, against " + without + " without: not less");
		helper.succeed();
	}

	private static int catches(ServerLevel level, Disaster.Effect contagion, Villager sick, Villager near) {
		RandomSource random = RandomSource.create(13L);
		int caught = 0;
		for (int i = 0; i < 400; i++) {
			Sickness.recover(level, near);
			if (!Plague.spread(level, contagion, List.of(sick, near), random).isEmpty()) {
				caught++;
			}
		}
		Sickness.recover(level, near);
		return caught;
	}

	// --- 32.15: switched off.

	/** Config {@code disasters} with drought false (the real config, applied): it can't be had, and one under way ends at the next hall round. */
	//$ gametest_ticks_batch AREA '100' '"qaDisasterConfig"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDisasterConfig")
	public void theConfigSwitchEndsADroughtUnderWay(GameTestHelper helper) {
		Village v = village(helper);
		helper.assertTrue(new WorkplaceConfig().disastersOff().isEmpty(), "a disaster is off by default: " + new WorkplaceConfig().disastersOff());
		v.start(v.drought());
		WorkplaceConfig config = new WorkplaceConfig();
		config.disasters.put("drought", false);
		config.apply();
		helper.assertFalse(Disasters.enabled(Drought.ID) || Drought.get().isPresent(), "config drought false, the drought is still on");
		helper.assertTrue(Disasters.enabled(Plague.ID), "config drought false switched the plague off too");
		v.round();
		helper.assertTrue(v.entity().disaster() == null && !Drought.in(v.level(), v.hall()), "the drought under way went on, switched off in the config");
		new WorkplaceConfig().apply();
		helper.assertTrue(Drought.get().isPresent(), "switched back on, the drought isn't there");
		helper.succeed();
	}

	/** Threat level 0: no drought has a chance, and one under way ends; threat level 1 (gentle): half the drought's chance, no plague, and one under way ends. */
	//$ gametest_ticks_batch AREA '100' '"qaDisasterThreatLevel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDisasterThreatLevel")
	public void theThreatLevelQuietsDisasters(GameTestHelper helper) {
		Village v = village(helper);
		biome(helper, v.hall(), "minecraft:plains");
		Disasters.rainedOn(v.level(), v.day() - 6);
		Threats.LEVEL = 2;
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), v.census()), 0.05, "threat level 2");
		Threats.LEVEL = 0;
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), v.census()), 0, "threat level 0");
		Threats.LEVEL = 2;
		v.start(v.drought());
		Threats.LEVEL = 0;
		v.round();
		helper.assertTrue(v.entity().disaster() == null, "threat level 0 and the drought under way went on");
		Threats.LEVEL = 1;
		assertChance(helper, Disasters.chance(v.level(), v.hall(), v.drought(), v.census()), 0.025, "threat level 1 (gentle)");
		v.entity().setDisaster(null, -1, -1, -100);
		v.entity().setRank(VillageRanks.Rank.CITY);
		villager(helper, 6.5, 6.5);
		Map<ResourceLocation, Boolean> tried = new java.util.HashMap<>();
		for (int i = 0; i < 200; i++) {
			v.entity().setDisaster(null, -1, -1, -100);
			Disasters.roll(v.level(), v.hall(), v.entity(), v.census(), RandomSource.create(i)).ifPresent(d -> tried.put(d.id(), true));
		}
		helper.assertFalse(tried.containsKey(Plague.ID), "a plague started at threat level 1 (gentle): " + tried.keySet());
		v.entity().setDisaster(null, -1, -1, -100);
		Threats.LEVEL = 2;
		v.start(v.plague());
		Threats.LEVEL = 1;
		v.round();
		helper.assertTrue(v.entity().disaster() == null, "threat level 1 and the plague under way went on");
		helper.succeed();
	}

	// --- The sentences a player reads.

	/**
	 * The drought and the plague as a player reads them: the start and end messages with the village's name, the hall's
	 * chronicle lines (kinds DROUGHT and PLAGUE), the wellbeing line with its days, and the mood reason.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDisasterText"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDisasterText")
	public void theDroughtAndPlagueReadRight(GameTestHelper helper) {
		Village v = village(helper);
		Villager villager = villager(helper, 6.5, 6.5);
		String village = v.entity().getName().getString();
		helper.assertTrue(!village.isBlank() && !village.contains("%") && !village.contains("village_name."), "the village's name: " + village);
		v.start(v.drought());
		long day = v.day();
		int days = (int) (v.entity().disasterEnds() - day + 1);
		Chronicle.Entry came = last(v.entity());
		helper.assertTrue(came.kind() == Chronicle.Kind.DROUGHT && came.text().getString().equals("A drought came to the village."),
			"the chronicle's drought line: " + came.kind() + " " + came.text().getString());
		String line = Disasters.line(v.entity(), day).getString();
		helper.assertTrue(line.equals("Drought (days left: " + days + ")"), "the wellbeing line: " + line);
		helper.assertTrue(Disasters.mood(v.level(), villager).reason().getString().equals("The wells are low"), "the mood reason");
		Disaster drought = v.drought();
		String start = net.minecraft.network.chat.Component.translatable("disaster.aliveworkplace.drought.start", Disasters.name(drought), v.entity().getName())
			.getString();
		helper.assertTrue(start.equals("A drought has come to " + village + ": the crops wilt and the wells run low."), "the start message: " + start);
		v.level().setWeatherParameters(0, 6000, true, false);
		v.round();
		Chronicle.Entry over = last(v.entity());
		helper.assertTrue(v.entity().disaster() == null && over.kind() == Chronicle.Kind.DROUGHT && over.text().getString().equals("The drought is over."),
			"the chronicle's end line: " + over.kind() + " " + over.text().getString());
		String end = net.minecraft.network.chat.Component.translatable("disaster.aliveworkplace.drought.end", Disasters.name(drought), v.entity().getName()).getString();
		helper.assertTrue(end.equals("The drought in " + village + " is over: the wells are filling again."), "the end message: " + end);
		v.level().setWeatherParameters(6000, 0, false, false);
		// Foretold by a Well Keeper.
		Villager keeper = villager(helper, 10.5, 6.5);
		keeper.setVillagerData(keeper.getVillagerData().setProfession(ModVillagers.WELL_KEEPER));
		var seer = Disasters.foreteller(v.level(), v.hall(), drought, v.census()).orElseThrow();
		String foretold = net.minecraft.network.chat.Component.translatable("disaster.aliveworkplace.drought.foretold", seer, Disasters.name(drought),
			v.entity().getName(), 1L).getString();
		helper.assertTrue(foretold.endsWith(": a drought will come to " + village + ". Days until it strikes: 1.") && !foretold.contains("%"),
			"the foretold message: " + foretold);
		keeper.discard();
		// The plague.
		v.entity().setDisaster(null, -1, -1, -100);
		v.start(v.plague());
		Chronicle.Entry broke = last(v.entity());
		helper.assertTrue(broke.kind() == Chronicle.Kind.PLAGUE && broke.text().getString().equals("A plague broke out in the village."),
			"the chronicle's plague line: " + broke.kind() + " " + broke.text().getString());
		String plague = Disasters.line(v.entity(), v.day()).getString();
		helper.assertTrue(plague.equals("Plague, until no one is ill"), "the plague's wellbeing line: " + plague);
		String plagueEnd = net.minecraft.network.chat.Component.translatable("disaster.aliveworkplace.plague.end", Disasters.name(v.plague()), v.entity().getName())
			.getString();
		helper.assertTrue(plagueEnd.equals("The plague in " + village + " is over: no one has been ill for a day."), "the plague's end message: " + plagueEnd);
		helper.succeed();
	}

	private static Chronicle.Entry last(VillageHallBlockEntity hall) {
		List<Chronicle.Entry> all = hall.chronicle();
		if (all.isEmpty()) {
			throw new GameTestAssertException("the hall's chronicle is empty");
		}
		return all.get(all.size() - 1); // (the newest is written last)
	}
}
