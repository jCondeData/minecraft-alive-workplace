package io.github.jcondedata.aliveworkplace.registry;

import com.google.common.collect.ImmutableSet;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

public final class ModVillagers {
	public static final ResourceLocation BENCH_ID = AliveWorkplace.id("builders_bench");
	public static final ResourceKey<PoiType> BUILDERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BENCH_ID);
	public static final PoiType BUILDERS_BENCH_POI_TYPE = PointOfInterestHelper.register(BENCH_ID, 1, 1, ModBlocks.BUILDERS_BENCH);

	public static final VillagerProfession BUILDER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("builder"),
		new VillagerProfession(
			"builder",
			holder -> holder.is(BUILDERS_BENCH_POI),
			holder -> holder.is(BUILDERS_BENCH_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_MASON
		)
	);

	public static final ResourceLocation MINERS_BENCH_ID = AliveWorkplace.id("miners_bench");
	public static final ResourceKey<PoiType> MINERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MINERS_BENCH_ID);
	public static final PoiType MINERS_BENCH_POI_TYPE = PointOfInterestHelper.register(MINERS_BENCH_ID, 1, 1, ModBlocks.MINERS_BENCH);

	public static final VillagerProfession MINER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("miner"),
		new VillagerProfession(
			"miner",
			holder -> holder.is(MINERS_BENCH_POI),
			holder -> holder.is(MINERS_BENCH_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH
		)
	);

	public static final ResourceLocation CHOPPING_BLOCK_ID = AliveWorkplace.id("chopping_block");
	public static final ResourceKey<PoiType> CHOPPING_BLOCK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CHOPPING_BLOCK_ID);
	public static final PoiType CHOPPING_BLOCK_POI_TYPE = PointOfInterestHelper.register(CHOPPING_BLOCK_ID, 1, 1, ModBlocks.CHOPPING_BLOCK);

	public static final VillagerProfession LUMBERJACK = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("lumberjack"),
		new VillagerProfession(
			"lumberjack",
			holder -> holder.is(CHOPPING_BLOCK_POI),
			holder -> holder.is(CHOPPING_BLOCK_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.AXE_STRIP
		)
	);

	public static final ResourceLocation POSTAL_DESK_ID = AliveWorkplace.id("postal_desk");
	public static final ResourceKey<PoiType> POSTAL_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, POSTAL_DESK_ID);
	public static final PoiType POSTAL_DESK_POI_TYPE = PointOfInterestHelper.register(POSTAL_DESK_ID, 1, 1, ModBlocks.POSTAL_DESK);

	public static final VillagerProfession POSTMAN = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("postman"),
		new VillagerProfession(
			"postman",
			holder -> holder.is(POSTAL_DESK_POI),
			holder -> holder.is(POSTAL_DESK_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER
		)
	);

	public static final ResourceLocation GUARD_POST_ID = AliveWorkplace.id("guard_post");
	public static final ResourceKey<PoiType> GUARD_POST_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, GUARD_POST_ID);
	public static final PoiType GUARD_POST_POI_TYPE = PointOfInterestHelper.register(GUARD_POST_ID, 1, 1, ModBlocks.GUARD_POST);

	public static final VillagerProfession GUARD = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("guard"),
		new VillagerProfession(
			"guard",
			holder -> holder.is(GUARD_POST_POI),
			holder -> holder.is(GUARD_POST_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_WEAPONSMITH
		)
	);

	public static final ResourceLocation NURSE_STATION_ID = AliveWorkplace.id("nurse_station");
	public static final ResourceKey<PoiType> NURSE_STATION_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, NURSE_STATION_ID);
	public static final PoiType NURSE_STATION_POI_TYPE = PointOfInterestHelper.register(NURSE_STATION_ID, 1, 1, ModBlocks.NURSE_STATION);

	public static final VillagerProfession NURSE = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("nurse"),
		new VillagerProfession(
			"nurse",
			holder -> holder.is(NURSE_STATION_POI),
			holder -> holder.is(NURSE_STATION_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CLERIC
		)
	);

	public static final ResourceLocation SHOP_COUNTER_ID = AliveWorkplace.id("shop_counter");
	public static final ResourceKey<PoiType> SHOP_COUNTER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, SHOP_COUNTER_ID);
	public static final PoiType SHOP_COUNTER_POI_TYPE = PointOfInterestHelper.register(SHOP_COUNTER_ID, 1, 1, ModBlocks.SHOP_COUNTER);

	public static final VillagerProfession SHOPKEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("shopkeeper"),
		new VillagerProfession(
			"shopkeeper",
			holder -> holder.is(SHOP_COUNTER_POI),
			holder -> holder.is(SHOP_COUNTER_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_LIBRARIAN
		)
	);

	public static final ResourceLocation TRAVEL_POST_ID = AliveWorkplace.id("travel_post");
	public static final ResourceKey<PoiType> TRAVEL_POST_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TRAVEL_POST_ID);
	public static final PoiType TRAVEL_POST_POI_TYPE = PointOfInterestHelper.register(TRAVEL_POST_ID, 1, 1, ModBlocks.TRAVEL_POST);

	public static final VillagerProfession FERRYMAN = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("ferryman"),
		new VillagerProfession(
			"ferryman",
			holder -> holder.is(TRAVEL_POST_POI),
			holder -> holder.is(TRAVEL_POST_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_FISHERMAN
		)
	);

	public static final ResourceLocation MUSIC_STAND_ID = AliveWorkplace.id("music_stand");
	public static final ResourceKey<PoiType> MUSIC_STAND_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MUSIC_STAND_ID);
	public static final PoiType MUSIC_STAND_POI_TYPE = PointOfInterestHelper.register(MUSIC_STAND_ID, 1, 1, ModBlocks.MUSIC_STAND);

	public static final VillagerProfession BARD = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("bard"),
		new VillagerProfession(
			"bard",
			holder -> holder.is(MUSIC_STAND_POI),
			holder -> holder.is(MUSIC_STAND_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.NOTE_BLOCK_HARP.value()
		)
	);

	/** Bards play a morning set at the market and an evening set while the village gathers, then sleep. */
	public static final Schedule BARD_SCHEDULE = Registry.register(
		BuiltInRegistries.SCHEDULE,
		AliveWorkplace.id("bard"),
		new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.IDLE)
			.changeActivityAt(1000, Activity.WORK)
			.changeActivityAt(3500, Activity.IDLE)
			.changeActivityAt(9000, Activity.WORK)
			.changeActivityAt(12500, Activity.REST)
			.build()
	);

	public static final ResourceLocation TRAINING_POST_ID = AliveWorkplace.id("training_post");
	public static final ResourceKey<PoiType> TRAINING_POST_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TRAINING_POST_ID);
	public static final PoiType TRAINING_POST_POI_TYPE = PointOfInterestHelper.register(TRAINING_POST_ID, 1, 1, ModBlocks.TRAINING_POST);

	public static final VillagerProfession TRAINER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("trainer"),
		new VillagerProfession(
			"trainer",
			holder -> holder.is(TRAINING_POST_POI),
			holder -> holder.is(TRAINING_POST_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_ARMORER
		)
	);

	public static final ResourceLocation LEADERS_PODIUM_ID = AliveWorkplace.id("leaders_podium");
	public static final ResourceKey<PoiType> LEADERS_PODIUM_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, LEADERS_PODIUM_ID);
	public static final PoiType LEADERS_PODIUM_POI_TYPE = PointOfInterestHelper.register(LEADERS_PODIUM_ID, 1, 1, ModBlocks.LEADERS_PODIUM);

	public static final VillagerProfession TRAINER_LEADER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("trainer_leader"),
		new VillagerProfession(
			"trainer_leader",
			holder -> holder.is(LEADERS_PODIUM_POI),
			holder -> holder.is(LEADERS_PODIUM_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_ARMORER
		)
	);

	public static final ResourceLocation TUTORS_DESK_ID = AliveWorkplace.id("tutors_desk");
	public static final ResourceKey<PoiType> TUTORS_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TUTORS_DESK_ID);
	public static final PoiType TUTORS_DESK_POI_TYPE = PointOfInterestHelper.register(TUTORS_DESK_ID, 1, 1, ModBlocks.TUTORS_DESK);

	public static final VillagerProfession TUTOR = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("tutor"),
		new VillagerProfession(
			"tutor",
			holder -> holder.is(TUTORS_DESK_POI),
			holder -> holder.is(TUTORS_DESK_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_LIBRARIAN
		)
	);

	public static final ResourceLocation TRADE_BOARD_ID = AliveWorkplace.id("trade_board");
	public static final ResourceKey<PoiType> TRADE_BOARD_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TRADE_BOARD_ID);
	public static final PoiType TRADE_BOARD_POI_TYPE = PointOfInterestHelper.register(TRADE_BOARD_ID, 1, 1, ModBlocks.TRADE_BOARD);

	public static final VillagerProfession POKEMON_TRADER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("pokemon_trader"),
		new VillagerProfession(
			"pokemon_trader",
			holder -> holder.is(TRADE_BOARD_POI),
			holder -> holder.is(TRADE_BOARD_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER
		)
	);

	/**
	 * Guards keep the night watch: on patrol from evening to mid-morning, asleep until early afternoon,
	 * then out with the village. They fight whenever a monster shows up, whatever they are doing.
	 */
	public static final Schedule GUARD_SCHEDULE = Registry.register(
		BuiltInRegistries.SCHEDULE,
		AliveWorkplace.id("guard"),
		new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.WORK)
			.changeActivityAt(3000, Activity.REST)
			.changeActivityAt(8000, Activity.IDLE)
			.changeActivityAt(10500, Activity.WORK)
			.build()
	);

	/** Villagers whose work this mod runs: they get the long shift and our WORK package. */
	public static boolean isWorker(VillagerProfession profession) {
		return profession == BUILDER || profession == MINER || profession == LUMBERJACK || profession == POSTMAN
			|| profession == NURSE || profession == SHOPKEEPER || profession == FERRYMAN
			|| profession == TRAINER || profession == TRAINER_LEADER || profession == TUTOR || profession == POKEMON_TRADER;
	}

	/**
	 * Builders put in a longer shift than vanilla villagers (who only WORK 2000-9000 and then
	 * gossip): they work from early morning to dusk, then go to bed like everyone else.
	 */
	public static final Schedule BUILDER_SCHEDULE = Registry.register(
		BuiltInRegistries.SCHEDULE,
		AliveWorkplace.id("builder"),
		new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.IDLE)
			.changeActivityAt(1000, Activity.WORK)
			.changeActivityAt(11000, Activity.IDLE)
			.changeActivityAt(12000, Activity.REST)
			.build()
	);

	public static void init() {
	}

	private ModVillagers() {
	}
}
