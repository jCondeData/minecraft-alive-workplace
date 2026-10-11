package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WellKeeperWork;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * The drought and the Well Keeper (ROADMAP 32.17), with the mod's own {@code drought.json} on the disaster engine: in
 * a drought wheat grows on at most a quarter of its random ticks (a fixed {@link RandomSource}, 400 ticks called) and
 * watered wheat at the usual rate; sweet berry bushes are held back too; the Well Keeper (a shovel at a cauldron) carries
 * water from a pond and wets dry farmland within 600 ticks, and between droughts fills an empty cauldron; rain ends the
 * drought; a finished Well takes the mood reason away; a Well Keeper foretells it a day ahead; the bucket's water
 * survives a save and load; config {@code wellKeepers} off, there's no job and a hired one does nothing; the chance
 * follows the dry days; and every new sentence has its text.
 */
public class DroughtGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);
	/** The pond: a water source in the floor. */
	private static final BlockPos POND = new BlockPos(2, 1, 8);
	/** The field: a 3 x 3 square of farmland in the floor, wheat on it; this is its middle. */
	private static final BlockPos FIELD = new BlockPos(10, 1, 6);
	/** Day 31 begins here: far from the other tests' days. */
	private static final long BASE = 30 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Disaster drought) {
	}

	private static Village village(GameTestHelper helper, long time) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		VillageHalls.RADIUS = 20;
		level.setDayTime(BASE + time);
		level.setWeatherParameters(6000, 0, false, false);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(was);
			level.setWeatherParameters(6000, 0, false, false);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		Disaster drought = Drought.get().orElse(null);
		helper.assertTrue(drought != null, "drought.json isn't loaded: " + Disasters.all());
		return new Village(level, hall, entity, drought);
	}

	private static void start(Village v) {
		v.entity().setDisaster(null, -1, -1, -100);
		Disasters.start(v.level(), v.hall(), v.entity(), v.drought(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(1L));
		if (!Drought.ID.equals(v.entity().disaster()) || !Drought.in(v.level(), v.hall())) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("the drought didn't start");
		}
	}

	/** The 3 x 3 field of farmland of {@code moisture}, wheat of age 0 on it. */
	private static void field(GameTestHelper helper, int moisture) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(FIELD.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
				helper.setBlock(FIELD.offset(dx, 1, dz), Blocks.WHEAT);
			}
		}
	}

	/** A Well Keeper at the cauldron, with a pond to fill the bucket at. */
	private static Villager keeper(GameTestHelper helper) {
		helper.setBlock(CAULDRON, Blocks.CAULDRON);
		helper.setBlock(POND, Blocks.WATER);
		Villager v = helper.spawn(EntityType.VILLAGER, CAULDRON.south());
		Stations.assign(helper.getLevel(), v, helper.absolutePos(CAULDRON), ModVillagers.WELL_KEEPER);
		return v;
	}

	/** How many of 400 random ticks grow the wheat on {@code soil} by a stage (reset to age 0 after each), with seed 42. */
	private static int growths(ServerLevel level, BlockPos crop) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), 2);
			level.getBlockState(crop).randomTick(level, crop, random);
			if (((CropBlock) Blocks.WHEAT).getAge(level.getBlockState(crop)) > 0) {
				grew++;
			}
		}
		return grew;
	}

	/** In a drought wheat grows on at most a quarter of its random ticks; on farmland a Well Keeper watered, at the usual rate. */
	//$ gametest_ticks_batch AREA '100' '"droughtWheat"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtWheat")
	public void wheatGrowsAtAQuarterPace(GameTestHelper helper) {
		Village v = village(helper, 1000);
		field(helper, 7);
		BlockPos crop = helper.absolutePos(FIELD.above());
		int usual = growths(v.level(), crop);
		start(v);
		int passed = 0;
		RandomSource gate = RandomSource.create(42L);
		for (int i = 0; i < 400; i++) {
			if (Disasters.cropGrows(v.level(), crop, gate)) {
				passed++;
			}
		}
		helper.assertTrue(passed <= 115, "in the drought " + passed + " of 400 random ticks may grow the wheat, more than a quarter");
		int dry = growths(v.level(), crop);
		helper.assertTrue(usual >= 20, "the wheat hardly grows without a drought: " + usual + " of 400");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the wheat grew " + dry + " times, against " + usual + " without: not a quarter pace");
		// The Well Keeper's bucket: the same random ticks grow it as often as without the drought.
		helper.assertTrue(Drought.wet(v.level(), helper.absolutePos(FIELD)) == 9, "a bucket didn't wet the 9 farmland");
		int watered = growths(v.level(), crop);
		helper.assertTrue(watered == usual, "watered wheat grew " + watered + " times in the drought, " + usual + " without");
		// Watered farmland keeps its moisture that day; the drought dries the rest.
		BlockPos soil = helper.absolutePos(FIELD);
		v.level().getBlockState(soil).randomTick(v.level(), soil, RandomSource.create(3L));
		helper.assertTrue(v.level().getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7, "the watered farmland dried the same day");
		helper.setBlock(FIELD.offset(4, 0, 0), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		BlockPos other = helper.absolutePos(FIELD.offset(4, 0, 0));
		v.level().getBlockState(other).randomTick(v.level(), other, RandomSource.create(3L));
		helper.assertTrue(v.level().getBlockState(other).getValue(FarmBlock.MOISTURE) == 6, "unwatered farmland didn't dry in the drought");
		helper.succeed();
	}

	/** Sweet berry bushes are held back in a drought too. */
	//$ gametest_ticks_batch AREA '100' '"droughtBerries"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtBerries")
	public void berryBushesGrowSlower(GameTestHelper helper) {
		Village v = village(helper, 1000);
		helper.setBlock(new BlockPos(6, 2, 10), Blocks.SWEET_BERRY_BUSH);
		BlockPos bush = helper.absolutePos(new BlockPos(6, 2, 10));
		int usual = bushGrowths(v.level(), bush);
		start(v);
		int dry = bushGrowths(v.level(), bush);
		helper.assertTrue(usual >= 20 && dry * 2 < usual, "berry bushes grew " + dry + " times in the drought, " + usual + " without");
		helper.succeed();
	}

	private static int bushGrowths(ServerLevel level, BlockPos bush) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
			level.getBlockState(bush).randomTick(level, bush, random);
			if (level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) > 0) {
				grew++;
			}
		}
		return grew;
	}

	/** In a drought the Well Keeper fetches water from the pond and wets the dry field within 600 ticks. */
	//$ gametest_ticks_batch AREA '600' '"droughtKeeper"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "droughtKeeper")
	public void theKeeperWetsDryFarmland(GameTestHelper helper) {
		Village v = village(helper, 2000);
		field(helper, 0);
		start(v);
		Villager keeper = keeper(helper);
		int xp = keeper.getVillagerXp();
		BlockPos middle = helper.absolutePos(FIELD);
		helper.succeedWhen(() -> {
			helper.assertTrue(v.level().getBlockState(middle).getValue(FarmBlock.MOISTURE) == 7 && Drought.watered(v.level(), middle),
				"the field isn't watered yet: moisture " + v.level().getBlockState(middle).getValue(FarmBlock.MOISTURE));
			helper.assertTrue(keeper.getVillagerXp() > xp, "no experience for the bucket");
			helper.assertTrue(v.level().getBlockState(helper.absolutePos(POND)).is(Blocks.WATER), "the pond is gone");
		});
	}

	/** Between droughts the Well Keeper fills an empty cauldron of the village and wets farmland that dried right out. */
	//$ gametest_ticks_batch AREA '1200' '"droughtBetween"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "droughtBetween")
	public void betweenDroughtsCauldronsAndFieldsAreKept(GameTestHelper helper) {
		Village v = village(helper, 2000);
		field(helper, 0);
		helper.setBlock(new BlockPos(8, 2, 12), Blocks.CAULDRON);
		keeper(helper);
		BlockPos cauldron = helper.absolutePos(new BlockPos(8, 2, 12));
		BlockPos middle = helper.absolutePos(FIELD);
		helper.succeedWhen(() -> {
			helper.assertTrue(v.level().getBlockState(cauldron).is(Blocks.WATER_CAULDRON)
				&& v.level().getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 3, "the empty cauldron isn't filled");
			helper.assertTrue(v.level().getBlockState(middle).getValue(FarmBlock.MOISTURE) > 0, "the dried-out farmland isn't wet");
		});
	}

	/** Rain ends the drought. */
	//$ gametest_ticks_batch AREA '100' '"droughtRain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtRain")
	public void rainEndsTheDrought(GameTestHelper helper) {
		Village v = village(helper, 1000);
		start(v);
		long day = Chronicle.day(v.level());
		helper.assertTrue(v.entity().disasterEnds() - day + 1 >= 3 && v.entity().disasterEnds() - day + 1 <= 5,
			"not 3 to 5 days: " + (v.entity().disasterEnds() - day + 1));
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() != null, "over without rain");
		v.level().setWeatherParameters(0, 6000, true, false);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertTrue(v.entity().disaster() == null && !Drought.in(v.level(), v.hall()), "the rain didn't end the drought");
		helper.succeed();
	}

	/** "The wells are low" (-10), until the village has a finished Well. */
	//$ gametest_ticks_batch AREA '100' '"droughtWell"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtWell")
	public void aFinishedWellTakesTheMoodAway(GameTestHelper helper) {
		Village v = village(helper, 1000);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		villager.setNoAi(true);
		start(v);
		var mood = Disasters.mood(v.level(), villager);
		helper.assertTrue(mood != null && mood.points() == -10 && mood.reason().getString().equals(Language.getInstance().getOrDefault("mood.aliveworkplace.reason.drought")),
			"not -10 for the wells being low: " + mood);
		BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(), helper.absolutePos(new BlockPos(12, 2, 4)),
			Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(v.level()).recordFinished(StarterBlueprints.WELL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(v.level()).forgetFinished(placement));
		helper.assertTrue(Disasters.mood(v.level(), villager) == null, "a finished Well didn't take the mood reason away");
		helper.succeed();
	}

	/** A Well Keeper in the village foretells the drought a day ahead. */
	//$ gametest_ticks_batch AREA '100' '"droughtForetold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtForetold")
	public void theKeeperForetellsItADayAhead(GameTestHelper helper) {
		Village v = village(helper, 1000);
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		keeper.setNoAi(true);
		keeper.setVillagerData(keeper.getVillagerData().setProfession(ModVillagers.WELL_KEEPER));
		helper.assertTrue(Disasters.foreteller(v.level(), v.hall(), v.drought(), VillageHalls.census(v.level(), v.hall())).isPresent(),
			"the Well Keeper doesn't foretell the drought");
		Disasters.start(v.level(), v.hall(), v.entity(), v.drought(), VillageHalls.census(v.level(), v.hall()), RandomSource.create(1L));
		long day = Chronicle.day(v.level());
		helper.assertTrue(v.entity().disasterSince() == day + 1, "not foretold a day ahead: begins on " + v.entity().disasterSince() + ", today " + day);
		Disasters.round(v.level(), v.hall(), v.entity(), VillageHalls.census(v.level(), v.hall()));
		helper.assertFalse(Drought.in(v.level(), v.hall()), "the village is in the drought before it comes");
		helper.succeed();
	}

	/** Five dry days (or a dry biome) give the drought its chance; a rainy day none. */
	//$ gametest_ticks_batch AREA '100' '"droughtChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtChance")
	public void theChanceFollowsTheDryDays(GameTestHelper helper) {
		Village v = village(helper, 1000);
		VillageHalls.Census census = VillageHalls.census(v.level(), v.hall());
		long day = Chronicle.day(v.level());
		Disasters.rainedOn(v.level(), day - 6);
		double dry = Disasters.chance(v.level(), v.hall(), v.drought(), census);
		helper.assertTrue(dry == 0.05 || dry == 0.15, "after 6 dry days the chance is " + dry);
		v.level().setWeatherParameters(0, 6000, true, false);
		helper.assertTrue(Disasters.chance(v.level(), v.hall(), v.drought(), census) == 0, "a drought may start in the rain");
		helper.succeed();
	}

	/** A shovel at a cauldron makes a Well Keeper and the cauldron's tooltip lists the job; config wellKeepers off, neither, and a hired one stands idle. */
	//$ gametest_ticks_batch AREA '300' '"droughtJob"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "droughtJob")
	public void theJobStartsFromACauldronAndAShovel(GameTestHelper helper) {
		Village v = village(helper, 2000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(CAULDRON, Blocks.CAULDRON);
		Villager villager = helper.spawn(EntityType.VILLAGER, CAULDRON.south());
		helper.runAfterDelay(2, () -> {
			Stations.Station station = Stations.at(Blocks.CAULDRON).orElseThrow();
			helper.assertTrue(station.jobs().stream().filter(Stations::available).anyMatch(j -> j.profession().get() == ModVillagers.WELL_KEEPER),
				"the cauldron's tooltip doesn't list the Well Keeper");
			helper.assertTrue(Language.getInstance().has(Stations.itemKey(station.jobFor(new ItemStack(Items.IRON_SHOVEL)).orElseThrow())),
				"the tooltip doesn't say a shovel picks it");
			WellKeeperWork.ENABLED = false;
			helper.assertTrue(station.jobs().stream().filter(Stations::available).noneMatch(j -> j.profession().get() == ModVillagers.WELL_KEEPER),
				"wellKeepers off: the tooltip still lists the Well Keeper");
			helper.assertTrue(Stations.choose(player, villager, new ItemStack(Items.WOODEN_SHOVEL)) == InteractionResult.PASS
				&& villager.getVillagerData().getProfession() != ModVillagers.WELL_KEEPER, "wellKeepers off: a shovel still made a Well Keeper");
			WellKeeperWork.ENABLED = true;
			InteractionResult result = Stations.choose(player, villager, new ItemStack(Items.WOODEN_SHOVEL));
			helper.assertTrue(result.consumesAction() && villager.getVillagerData().getProfession() == ModVillagers.WELL_KEEPER,
				"a shovel at the cauldron gave " + villager.getVillagerData().getProfession() + " (" + result + ")");
			helper.assertTrue(Stations.choose(player, villager, new ItemStack(Items.LEATHER)).consumesAction()
				&& villager.getVillagerData().getProfession() == VillagerProfession.LEATHERWORKER, "leather no longer makes a Leatherworker there");
			villager.discard();
			// Switched off, a hired Well Keeper leaves a drought's dry field alone.
			field(helper, 0);
			start(v);
			WellKeeperWork.ENABLED = false;
			keeper(helper);
			helper.runAfterDelay(250, () -> {
				helper.assertTrue(v.level().getBlockState(helper.absolutePos(FIELD)).getValue(FarmBlock.MOISTURE) == 0,
					"wellKeepers off, the field was watered");
				helper.succeed();
			});
		});
	}

	/** The water in the bucket survives a save and load. */
	//$ gametest_ticks_batch AREA '100' '"droughtSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "droughtSaved")
	public void theBucketSurvivesASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));
		keeper.setVillagerData(keeper.getVillagerData().setProfession(ModVillagers.WELL_KEEPER));
		helper.assertFalse(WellKeeperWork.hasWater(keeper), "a new Well Keeper's bucket is full");
		WellKeeperWork.setWater(keeper, true);
		CompoundTag tag = keeper.saveWithoutId(new CompoundTag());
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(tag);
		helper.assertTrue(WellKeeperWork.hasWater(loaded) && ModAttachments.WELL_KEEPER_WATER.has(loaded), "the bucket was emptied by a save and load");
		loaded.discard();
		keeper.discard();
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("entity.minecraft.villager.well_keeper", "entity.minecraft.zombie_villager.well_keeper",
			"station.aliveworkplace.item.aliveworkplace.well_keeper", "aliveworkplace.config.wellKeepers", "aliveworkplace.config.wellKeepers.tooltip",
			"message.aliveworkplace.well_keeper.title_full", "message.aliveworkplace.well_keeper.title_empty", "message.aliveworkplace.well_keeper.state.water",
			"message.aliveworkplace.well_keeper.state.drought", "message.aliveworkplace.well_keeper.state.field", "message.aliveworkplace.well_keeper.state.cauldron",
			"disaster.aliveworkplace.drought.name", "disaster.aliveworkplace.drought.start", "disaster.aliveworkplace.drought.foretold",
			"disaster.aliveworkplace.drought.end", "chronicle.aliveworkplace.drought.start", "chronicle.aliveworkplace.drought.foretold",
			"chronicle.aliveworkplace.drought.end", "mood.aliveworkplace.reason.drought")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
