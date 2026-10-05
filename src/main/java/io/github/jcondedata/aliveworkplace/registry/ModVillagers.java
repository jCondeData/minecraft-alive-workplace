package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import com.google.common.collect.ImmutableSet;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

public final class ModVillagers {
	public static final ResourceLocation BENCH_ID = AliveWorkplace.id("builders_bench");
	public static final ResourceKey<PoiType> BUILDERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BENCH_ID);
	public static final PoiType BUILDERS_BENCH_POI_TYPE = Platform.get().registerPoi(BENCH_ID, 1, 1, ModBlocks.BUILDERS_BENCH);

	/*
	 * Workstations since the owner's "fewer job blocks" (ROADMAP 21.1a): most of our jobs share a vanilla block with a
	 * vanilla job, picked with an item (work/Stations). These are the blocks that had no point of interest before. Only
	 * the Blueprint Table takes a jobless villager by itself (it's in the acquirable_job_site tag); the crafting table,
	 * the jukebox and the mailbox wait for the player's item. The old blocks (Builder's Bench...) keep working.
	 */
	public static final ResourceLocation BLUEPRINT_TABLE_ID = AliveWorkplace.id("blueprint_table");
	public static final ResourceKey<PoiType> BLUEPRINT_TABLE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BLUEPRINT_TABLE_ID);
	public static final PoiType BLUEPRINT_TABLE_POI_TYPE = Platform.get().registerPoi(BLUEPRINT_TABLE_ID, 1, 1, ModBlocks.BLUEPRINT_TABLE);
	public static final ResourceLocation CRAFTING_TABLE_ID = AliveWorkplace.id("crafting_table");
	public static final ResourceKey<PoiType> CRAFTING_TABLE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CRAFTING_TABLE_ID);
	public static final PoiType CRAFTING_TABLE_POI_TYPE = Platform.get().registerPoi(CRAFTING_TABLE_ID, 1, 1,
		net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
	public static final ResourceLocation JUKEBOX_ID = AliveWorkplace.id("jukebox");
	public static final ResourceKey<PoiType> JUKEBOX_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JUKEBOX_ID);
	public static final PoiType JUKEBOX_POI_TYPE = Platform.get().registerPoi(JUKEBOX_ID, 1, 1, net.minecraft.world.level.block.Blocks.JUKEBOX);
	public static final ResourceLocation MAILBOX_ID = AliveWorkplace.id("mailbox");
	public static final ResourceKey<PoiType> MAILBOX_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MAILBOX_ID);
	public static final PoiType MAILBOX_POI_TYPE = Platform.get().registerPoi(MAILBOX_ID, 1, 1, ModBlocks.MAILBOX);

	/**
	 * A Legend without a trade of their own (M29: the Old Sage, the Seer...): no workstation, and, always a Master, never
	 * reset to a jobless Novice.
	 */
	public static final VillagerProfession LEGEND = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("legend"),
		new VillagerProfession(
			"legend",
			holder -> false,
			holder -> false,
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.AMETHYST_BLOCK_CHIME
		)
	);

	public static final VillagerProfession BUILDER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("builder"),
		new VillagerProfession(
			"builder",
			holder -> holder.is(BUILDERS_BENCH_POI) || holder.is(BLUEPRINT_TABLE_POI),
			holder -> holder.is(BUILDERS_BENCH_POI) || holder.is(BLUEPRINT_TABLE_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_MASON
		)
	);

	public static final ResourceLocation MINERS_BENCH_ID = AliveWorkplace.id("miners_bench");
	public static final ResourceKey<PoiType> MINERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MINERS_BENCH_ID);
	public static final PoiType MINERS_BENCH_POI_TYPE = Platform.get().registerPoi(MINERS_BENCH_ID, 1, 1, ModBlocks.MINERS_BENCH);

	public static final VillagerProfession MINER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("miner"),
		new VillagerProfession(
			"miner",
			holder -> holder.is(MINERS_BENCH_POI) || holder.is(PoiTypes.ARMORER),
			holder -> holder.is(MINERS_BENCH_POI) || holder.is(PoiTypes.ARMORER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH
		)
	);

	public static final ResourceLocation CHOPPING_BLOCK_ID = AliveWorkplace.id("chopping_block");
	public static final ResourceKey<PoiType> CHOPPING_BLOCK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CHOPPING_BLOCK_ID);
	public static final PoiType CHOPPING_BLOCK_POI_TYPE = Platform.get().registerPoi(CHOPPING_BLOCK_ID, 1, 1, ModBlocks.CHOPPING_BLOCK);

	public static final VillagerProfession LUMBERJACK = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("lumberjack"),
		new VillagerProfession(
			"lumberjack",
			holder -> holder.is(CHOPPING_BLOCK_POI) || holder.is(PoiTypes.FLETCHER),
			holder -> holder.is(CHOPPING_BLOCK_POI) || holder.is(PoiTypes.FLETCHER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.AXE_STRIP
		)
	);

	public static final ResourceLocation POSTAL_DESK_ID = AliveWorkplace.id("postal_desk");
	public static final ResourceKey<PoiType> POSTAL_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, POSTAL_DESK_ID);
	public static final PoiType POSTAL_DESK_POI_TYPE = Platform.get().registerPoi(POSTAL_DESK_ID, 1, 1, ModBlocks.POSTAL_DESK);

	public static final VillagerProfession POSTMAN = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("postman"),
		new VillagerProfession(
			"postman",
			holder -> holder.is(POSTAL_DESK_POI) || holder.is(MAILBOX_POI),
			holder -> holder.is(POSTAL_DESK_POI) || holder.is(MAILBOX_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER
		)
	);

	public static final ResourceLocation GUARD_POST_ID = AliveWorkplace.id("guard_post");
	public static final ResourceKey<PoiType> GUARD_POST_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, GUARD_POST_ID);
	public static final PoiType GUARD_POST_POI_TYPE = Platform.get().registerPoi(GUARD_POST_ID, 1, 1, ModBlocks.GUARD_POST);

	public static final VillagerProfession GUARD = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("guard"),
		new VillagerProfession(
			"guard",
			holder -> holder.is(GUARD_POST_POI) || holder.is(PoiTypes.WEAPONSMITH),
			holder -> holder.is(GUARD_POST_POI) || holder.is(PoiTypes.WEAPONSMITH),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_WEAPONSMITH
		)
	);

	public static final ResourceLocation NURSE_STATION_ID = AliveWorkplace.id("nurse_station");
	public static final ResourceKey<PoiType> NURSE_STATION_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, NURSE_STATION_ID);
	public static final PoiType NURSE_STATION_POI_TYPE = Platform.get().registerPoi(NURSE_STATION_ID, 1, 1, ModBlocks.NURSE_STATION);

	public static final VillagerProfession NURSE = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("nurse"),
		new VillagerProfession(
			"nurse",
			// A Healing Machine (with Cobblemon, ROADMAP 28.7) is kept, but only ever taken with a honey bottle (work/Stations).
			holder -> holder.is(NURSE_STATION_POI) || holder.is(PoiTypes.CLERIC) || holder.is(ModVillagers.HEALING_MACHINE_POI),
			holder -> holder.is(NURSE_STATION_POI) || holder.is(PoiTypes.CLERIC),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CLERIC
		)
	);

	public static final ResourceLocation SHOP_COUNTER_ID = AliveWorkplace.id("shop_counter");
	public static final ResourceKey<PoiType> SHOP_COUNTER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, SHOP_COUNTER_ID);
	public static final PoiType SHOP_COUNTER_POI_TYPE = Platform.get().registerPoi(SHOP_COUNTER_ID, 1, 1, ModBlocks.SHOP_COUNTER);

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
	public static final PoiType TRAVEL_POST_POI_TYPE = Platform.get().registerPoi(TRAVEL_POST_ID, 1, 1, ModBlocks.TRAVEL_POST);

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
	public static final PoiType MUSIC_STAND_POI_TYPE = Platform.get().registerPoi(MUSIC_STAND_ID, 1, 1, ModBlocks.MUSIC_STAND);

	public static final VillagerProfession BARD = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("bard"),
		new VillagerProfession(
			"bard",
			holder -> holder.is(MUSIC_STAND_POI) || holder.is(JUKEBOX_POI),
			holder -> holder.is(MUSIC_STAND_POI) || holder.is(JUKEBOX_POI),
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
	public static final PoiType TRAINING_POST_POI_TYPE = Platform.get().registerPoi(TRAINING_POST_ID, 1, 1, ModBlocks.TRAINING_POST);

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
	public static final PoiType LEADERS_PODIUM_POI_TYPE = Platform.get().registerPoi(LEADERS_PODIUM_ID, 1, 1, ModBlocks.LEADERS_PODIUM);

	public static final VillagerProfession TRAINER_LEADER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("trainer_leader"),
		new VillagerProfession(
			"trainer_leader",
			holder -> holder.is(LEADERS_PODIUM_POI) || holder.is(TRAINING_POST_POI),
			holder -> holder.is(LEADERS_PODIUM_POI) || holder.is(TRAINING_POST_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_ARMORER
		)
	);

	public static final ResourceLocation TUTORS_DESK_ID = AliveWorkplace.id("tutors_desk");
	public static final ResourceKey<PoiType> TUTORS_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TUTORS_DESK_ID);
	public static final PoiType TUTORS_DESK_POI_TYPE = Platform.get().registerPoi(TUTORS_DESK_ID, 1, 1, ModBlocks.TUTORS_DESK);

	public static final VillagerProfession TUTOR = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("tutor"),
		new VillagerProfession(
			"tutor",
			holder -> holder.is(TUTORS_DESK_POI) || holder.is(TRAINING_POST_POI),
			holder -> holder.is(TUTORS_DESK_POI) || holder.is(TRAINING_POST_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_LIBRARIAN
		)
	);

	public static final ResourceLocation TRADE_BOARD_ID = AliveWorkplace.id("trade_board");
	public static final ResourceKey<PoiType> TRADE_BOARD_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TRADE_BOARD_ID);
	public static final PoiType TRADE_BOARD_POI_TYPE = Platform.get().registerPoi(TRADE_BOARD_ID, 1, 1, ModBlocks.TRADE_BOARD);

	public static final VillagerProfession POKEMON_TRADER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("pokemon_trader"),
		new VillagerProfession(
			"pokemon_trader",
			holder -> holder.is(TRADE_BOARD_POI) || holder.is(SHOP_COUNTER_POI),
			holder -> holder.is(TRADE_BOARD_POI) || holder.is(SHOP_COUNTER_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER
		)
	);

	public static final ResourceLocation FRUIT_BASKET_ID = AliveWorkplace.id("fruit_basket");
	public static final ResourceKey<PoiType> FRUIT_BASKET_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FRUIT_BASKET_ID);
	public static final PoiType FRUIT_BASKET_POI_TYPE = Platform.get().registerPoi(FRUIT_BASKET_ID, 1, 1, ModBlocks.FRUIT_BASKET);

	public static final VillagerProfession ORCHARD_KEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("orchard_keeper"),
		new VillagerProfession(
			"orchard_keeper",
			holder -> holder.is(FRUIT_BASKET_POI) || holder.is(PoiTypes.FARMER),
			holder -> holder.is(FRUIT_BASKET_POI) || holder.is(PoiTypes.FARMER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_FARMER
		)
	);

	public static final ResourceLocation APIARY_ID = AliveWorkplace.id("apiary");
	public static final ResourceKey<PoiType> APIARY_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, APIARY_ID);
	public static final PoiType APIARY_POI_TYPE = Platform.get().registerPoi(APIARY_ID, 1, 1, ModBlocks.APIARY);

	public static final VillagerProfession BEEKEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("beekeeper"),
		new VillagerProfession(
			"beekeeper",
			holder -> holder.is(APIARY_POI) || holder.is(PoiTypes.BEEHIVE) || holder.is(PoiTypes.BEE_NEST),
			holder -> holder.is(APIARY_POI) || holder.is(PoiTypes.BEEHIVE) || holder.is(PoiTypes.BEE_NEST),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.BEEHIVE_WORK
		)
	);

	public static final ResourceLocation FLOWER_STAND_ID = AliveWorkplace.id("flower_stand");
	public static final ResourceKey<PoiType> FLOWER_STAND_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FLOWER_STAND_ID);
	public static final PoiType FLOWER_STAND_POI_TYPE = Platform.get().registerPoi(FLOWER_STAND_ID, 1, 1, ModBlocks.FLOWER_STAND);

	public static final VillagerProfession FLORIST = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("florist"),
		new VillagerProfession(
			"florist",
			holder -> holder.is(FLOWER_STAND_POI) || holder.is(PoiTypes.FARMER),
			holder -> holder.is(FLOWER_STAND_POI) || holder.is(PoiTypes.FARMER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_FARMER
		)
	);

	/**
	 * The Village Hall: a point of interest, so the nearest hall is quick to find, with one place for its Steward (27.5).
	 * It isn't an acquirable job site, so only the City Plan gives it (see {@code city/Stewards}). Halls saved when it had
	 * no place are registered again when they load ({@code Stewards.fixTicket}).
	 */
	public static final ResourceLocation VILLAGE_HALL_ID = AliveWorkplace.id("village_hall");
	public static final ResourceKey<PoiType> VILLAGE_HALL_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, VILLAGE_HALL_ID);
	public static final PoiType VILLAGE_HALL_POI_TYPE = Platform.get().registerPoi(VILLAGE_HALL_ID, 1, 1, ModBlocks.VILLAGE_HALL);

	/** The Steward (27.5): plans the village from its hall, appointed with the City Plan. */
	public static final VillagerProfession STEWARD = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("steward"),
		new VillagerProfession(
			"steward",
			holder -> holder.is(VILLAGE_HALL_POI),
			holder -> holder.is(VILLAGE_HALL_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CARTOGRAPHER
		)
	);

	/** Drop Boxes: a point of interest nobody works at, so the porters find them quickly. */
	public static final ResourceLocation DROP_BOX_ID = AliveWorkplace.id("drop_box");
	public static final ResourceKey<PoiType> DROP_BOX_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, DROP_BOX_ID);
	public static final PoiType DROP_BOX_POI_TYPE = Platform.get().registerPoi(DROP_BOX_ID, 0, 1, ModBlocks.DROP_BOX);

	public static final ResourceLocation SIEVE_ID = AliveWorkplace.id("sieve");
	public static final ResourceKey<PoiType> SIEVE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, SIEVE_ID);
	public static final PoiType SIEVE_POI_TYPE = Platform.get().registerPoi(SIEVE_ID, 1, 1, ModBlocks.SIEVE);

	public static final VillagerProfession SIFTER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("sifter"),
		new VillagerProfession(
			"sifter",
			holder -> holder.is(SIEVE_POI) || holder.is(PoiTypes.LEATHERWORKER),
			holder -> holder.is(SIEVE_POI) || holder.is(PoiTypes.LEATHERWORKER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_MASON
		)
	);

	public static final ResourceLocation TINKERS_BENCH_ID = AliveWorkplace.id("tinkers_bench");
	public static final ResourceKey<PoiType> TINKERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TINKERS_BENCH_ID);
	public static final PoiType TINKERS_BENCH_POI_TYPE = Platform.get().registerPoi(TINKERS_BENCH_ID, 1, 1, ModBlocks.TINKERS_BENCH);

	public static final VillagerProfession TINKERER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("tinkerer"),
		new VillagerProfession(
			"tinkerer",
			holder -> holder.is(TINKERS_BENCH_POI) || holder.is(PoiTypes.TOOLSMITH),
			holder -> holder.is(TINKERS_BENCH_POI) || holder.is(PoiTypes.TOOLSMITH),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH
		)
	);

	public static final ResourceLocation NETHER_BRAZIER_ID = AliveWorkplace.id("nether_brazier");
	public static final ResourceKey<PoiType> NETHER_BRAZIER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, NETHER_BRAZIER_ID);
	public static final PoiType NETHER_BRAZIER_POI_TYPE = Platform.get().registerPoi(NETHER_BRAZIER_ID, 1, 1, ModBlocks.NETHER_BRAZIER);

	public static final VillagerProfession NETHERWORKER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("netherworker"),
		new VillagerProfession(
			"netherworker",
			holder -> holder.is(NETHER_BRAZIER_POI) || holder.is(PoiTypes.CARTOGRAPHER),
			holder -> holder.is(NETHER_BRAZIER_POI) || holder.is(PoiTypes.CARTOGRAPHER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_ARMORER
		)
	);

	public static final ResourceLocation COMPOST_BIN_ID = AliveWorkplace.id("compost_bin");
	public static final ResourceKey<PoiType> COMPOST_BIN_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, COMPOST_BIN_ID);
	public static final PoiType COMPOST_BIN_POI_TYPE = Platform.get().registerPoi(COMPOST_BIN_ID, 1, 1, ModBlocks.COMPOST_BIN);

	public static final VillagerProfession COMPOSTER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("composter"),
		new VillagerProfession(
			"composter",
			holder -> holder.is(COMPOST_BIN_POI) || holder.is(PoiTypes.FARMER),
			holder -> holder.is(COMPOST_BIN_POI) || holder.is(PoiTypes.FARMER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_FARMER
		)
	);

	/**
	 * Breeds Cobblemon berries at a composter (ROADMAP 28.9): only ever by a berry, with Cobblemon installed and config
	 * {@code berryBreeders} on.
	 */
	public static final VillagerProfession BERRY_BREEDER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("berry_breeder"),
		new VillagerProfession(
			"berry_breeder",
			holder -> holder.is(PoiTypes.FARMER),
			holder -> holder.is(PoiTypes.FARMER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_FARMER
		)
	);

	public static final ResourceLocation SCHOLARS_DESK_ID = AliveWorkplace.id("scholars_desk");
	public static final ResourceKey<PoiType> SCHOLARS_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, SCHOLARS_DESK_ID);
	public static final PoiType SCHOLARS_DESK_POI_TYPE = Platform.get().registerPoi(SCHOLARS_DESK_ID, 1, 1, ModBlocks.SCHOLARS_DESK);

	public static final VillagerProfession SCHOLAR = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("scholar"),
		new VillagerProfession(
			"scholar",
			holder -> holder.is(SCHOLARS_DESK_POI) || holder.is(PoiTypes.LIBRARIAN),
			holder -> holder.is(SCHOLARS_DESK_POI) || holder.is(PoiTypes.LIBRARIAN),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_LIBRARIAN
		)
	);

	/** Graves: points of interest nobody works at, so an undertaker finds the ones nearby quickly. */
	public static final ResourceLocation GRAVE_ID = AliveWorkplace.id("grave");
	public static final ResourceKey<PoiType> GRAVE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, GRAVE_ID);
	public static final PoiType GRAVE_POI_TYPE = Platform.get().registerPoi(GRAVE_ID, 0, 1, ModBlocks.GRAVE);

	public static final ResourceLocation UNDERTAKERS_TABLE_ID = AliveWorkplace.id("undertakers_table");
	public static final ResourceKey<PoiType> UNDERTAKERS_TABLE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, UNDERTAKERS_TABLE_ID);
	public static final PoiType UNDERTAKERS_TABLE_POI_TYPE = Platform.get().registerPoi(UNDERTAKERS_TABLE_ID, 1, 1, ModBlocks.UNDERTAKERS_TABLE);

	public static final VillagerProfession UNDERTAKER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("undertaker"),
		new VillagerProfession(
			"undertaker",
			holder -> holder.is(UNDERTAKERS_TABLE_POI) || holder.is(PoiTypes.CLERIC),
			holder -> holder.is(UNDERTAKERS_TABLE_POI) || holder.is(PoiTypes.CLERIC),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_CLERIC
		)
	);

	public static final ResourceLocation INN_COUNTER_ID = AliveWorkplace.id("inn_counter");
	public static final ResourceKey<PoiType> INN_COUNTER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, INN_COUNTER_ID);
	public static final PoiType INN_COUNTER_POI_TYPE = Platform.get().registerPoi(INN_COUNTER_ID, 1, 1, ModBlocks.INN_COUNTER);

	public static final VillagerProfession INNKEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("innkeeper"),
		new VillagerProfession(
			"innkeeper",
			holder -> holder.is(INN_COUNTER_POI) || holder.is(SHOP_COUNTER_POI),
			holder -> holder.is(INN_COUNTER_POI) || holder.is(SHOP_COUNTER_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_BUTCHER
		)
	);

	public static final ResourceLocation TEACHERS_DESK_ID = AliveWorkplace.id("teachers_desk");
	public static final ResourceKey<PoiType> TEACHERS_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TEACHERS_DESK_ID);
	public static final PoiType TEACHERS_DESK_POI_TYPE = Platform.get().registerPoi(TEACHERS_DESK_ID, 1, 1, ModBlocks.TEACHERS_DESK);

	public static final VillagerProfession TEACHER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("teacher"),
		new VillagerProfession(
			"teacher",
			holder -> holder.is(TEACHERS_DESK_POI) || holder.is(PoiTypes.LIBRARIAN),
			holder -> holder.is(TEACHERS_DESK_POI) || holder.is(PoiTypes.LIBRARIAN),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_LIBRARIAN
		)
	);

	public static final ResourceLocation FEED_TROUGH_ID = AliveWorkplace.id("feed_trough");
	public static final ResourceKey<PoiType> FEED_TROUGH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FEED_TROUGH_ID);
	public static final PoiType FEED_TROUGH_POI_TYPE = Platform.get().registerPoi(FEED_TROUGH_ID, 1, 1, ModBlocks.FEED_TROUGH);

	public static final VillagerProfession RANCHER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("rancher"),
		new VillagerProfession(
			"rancher",
			holder -> holder.is(FEED_TROUGH_POI) || holder.is(PoiTypes.BUTCHER),
			holder -> holder.is(FEED_TROUGH_POI) || holder.is(PoiTypes.BUTCHER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_BUTCHER
		)
	);

	public static final ResourceLocation BALL_WORKBENCH_ID = AliveWorkplace.id("ball_workbench");
	public static final ResourceKey<PoiType> BALL_WORKBENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BALL_WORKBENCH_ID);
	public static final PoiType BALL_WORKBENCH_POI_TYPE = Platform.get().registerPoi(BALL_WORKBENCH_ID, 1, 1, ModBlocks.BALL_WORKBENCH);

	public static final VillagerProfession BALL_SMITH = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("ball_smith"),
		new VillagerProfession(
			"ball_smith",
			holder -> holder.is(BALL_WORKBENCH_POI) || holder.is(PoiTypes.TOOLSMITH),
			holder -> holder.is(BALL_WORKBENCH_POI) || holder.is(PoiTypes.TOOLSMITH),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH
		)
	);

	public static final ResourceLocation STOREHOUSE_ID = AliveWorkplace.id("storehouse");
	public static final ResourceKey<PoiType> STOREHOUSE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, STOREHOUSE_ID);
	public static final PoiType STOREHOUSE_POI_TYPE = Platform.get().registerPoi(STOREHOUSE_ID, 1, 1, ModBlocks.STOREHOUSE);

	/** Keeps the village's storehouse: carries what the other workers make into the chests by the Storehouse. */
	public static final VillagerProfession PORTER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("porter"),
		new VillagerProfession(
			"porter",
			holder -> holder.is(STOREHOUSE_POI),
			holder -> holder.is(STOREHOUSE_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.BARREL_OPEN
		)
	);

	public static final ResourceLocation CARPENTERS_BENCH_ID = AliveWorkplace.id("carpenters_bench");
	public static final ResourceKey<PoiType> CARPENTERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CARPENTERS_BENCH_ID);
	public static final PoiType CARPENTERS_BENCH_POI_TYPE = Platform.get().registerPoi(CARPENTERS_BENCH_ID, 1, 1, ModBlocks.CARPENTERS_BENCH);

	/** Makes what the builders nearby are waiting for (stairs, doors, fences, planks...) with the crafting table's recipes. */
	public static final VillagerProfession CARPENTER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("carpenter"),
		new VillagerProfession(
			"carpenter",
			holder -> holder.is(CARPENTERS_BENCH_POI) || holder.is(CRAFTING_TABLE_POI),
			holder -> holder.is(CARPENTERS_BENCH_POI) || holder.is(CRAFTING_TABLE_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.WOOD_HIT
		)
	);

	public static final ResourceLocation KITCHEN_STOVE_ID = AliveWorkplace.id("kitchen_stove");
	public static final ResourceKey<PoiType> KITCHEN_STOVE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, KITCHEN_STOVE_ID);
	public static final PoiType KITCHEN_STOVE_POI_TYPE = Platform.get().registerPoi(KITCHEN_STOVE_ID, 1, 1, ModBlocks.KITCHEN_STOVE);

	/** Cooks for the village: bread, pies, cooked meat and fish, and with Cobblemon Poké Snacks, Poké Bait and Aprijuice. */
	public static final VillagerProfession CHEF = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("chef"),
		new VillagerProfession(
			"chef",
			holder -> holder.is(KITCHEN_STOVE_POI) || holder.is(PoiTypes.BUTCHER),
			holder -> holder.is(KITCHEN_STOVE_POI) || holder.is(PoiTypes.BUTCHER),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.SMOKER_SMOKE
		)
	);

	public static final ResourceLocation FOSSIL_LAB_ID = AliveWorkplace.id("fossil_lab");
	public static final ResourceKey<PoiType> FOSSIL_LAB_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FOSSIL_LAB_ID);
	public static final PoiType FOSSIL_LAB_POI_TYPE = Platform.get().registerPoi(FOSSIL_LAB_ID, 1, 1, ModBlocks.FOSSIL_LAB);
	/**
	 * Cobblemon's Fossil Analyzer, the Fossil Scientist's workstation (the owner, ROADMAP 21.1c): a job site of ours as soon
	 * as Cobblemon registers the block; never without Cobblemon. Not in the acquirable_job_site tag, so only a fossil gives
	 * a villager the job there (work/Stations).
	 */
	public static final ResourceLocation FOSSIL_ANALYZER_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "fossil_analyzer");
	public static final ResourceLocation FOSSIL_ANALYZER_ID = AliveWorkplace.id("fossil_analyzer");
	public static final ResourceKey<PoiType> FOSSIL_ANALYZER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FOSSIL_ANALYZER_ID);

	static {
		Platform.get().whenBlockRegistered(FOSSIL_ANALYZER_BLOCK, block -> Platform.get().registerPoi(FOSSIL_ANALYZER_ID, 1, 1, block));
	}

	/**
	 * Cobblemon's Healing Machine (ROADMAP 28.7) is a Nurse workstation too, by Cobblemon's own POI for it. A honey bottle picks the Nurse there; a jobless villager never takes a player's machine.
	 */
	public static final ResourceLocation HEALING_MACHINE_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine");
	/** Cobblemon registers the machine's POI itself, as {@code cobblemon:nurse} (a block may be in one POI type only). */
	public static final ResourceKey<PoiType> HEALING_MACHINE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
		ResourceLocation.fromNamespaceAndPath("cobblemon", "nurse"));

	/**
	 * Cobblemon's Campfire Pot (ROADMAP 28.8): its campfire with a pot on it ({@code cobblemon:campfire}) is the Camp Cook's
	 * workstation, by a POI of ours registered when Cobblemon registers the block. Only Hearty Grains give the job there;
	 * a jobless villager never takes a player's pot.
	 */
	public static final ResourceLocation CAMPFIRE_POT_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "campfire");
	public static final ResourceLocation CAMPFIRE_POT_ID = AliveWorkplace.id("campfire_pot");
	public static final ResourceKey<PoiType> CAMPFIRE_POT_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CAMPFIRE_POT_ID);

	static {
		Platform.get().whenBlockRegistered(CAMPFIRE_POT_BLOCK, block -> Platform.get().registerPoi(CAMPFIRE_POT_ID, 1, 1, block));
	}

	/** Cooks Cobblemon dishes in a Campfire Pot (ROADMAP 28.8): only ever by Hearty Grains, with Cobblemon and config {@code campCooks}. */
	public static final VillagerProfession CAMP_COOK = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("camp_cook"),
		new VillagerProfession(
			"camp_cook",
			holder -> holder.is(CAMPFIRE_POT_POI),
			holder -> holder.is(CAMPFIRE_POT_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.CAMPFIRE_CRACKLE
		)
	);

	/**
	 * Cobblemon's Pasture Block (ROADMAP 28.10): its lower half is the Habitat Keeper's workstation, by a POI of ours
	 * registered when Cobblemon registers {@code cobblemon:pasture}. Only a honey bottle gives the job there; a jobless
	 * villager never takes a player's pasture.
	 */
	public static final ResourceLocation PASTURE_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblemon", "pasture");
	public static final ResourceLocation PASTURE_ID = AliveWorkplace.id("pasture");
	public static final ResourceKey<PoiType> PASTURE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, PASTURE_ID);

	static {
		Platform.get().whenBlockRegistered(PASTURE_BLOCK, block -> Platform.get().registerPoi(PASTURE_ID, 1, 1,
			block.getStateDefinition().getPossibleStates().stream().filter(ModVillagers::lowerHalf).toList()));
	}

	/** Whether {@code state} is a two-block block's lower half (its {@code part} is {@code bottom}), or has no halves. */
	private static boolean lowerHalf(net.minecraft.world.level.block.state.BlockState state) {
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (property.getName().equals("part")) {
				return valueName(state, property).equals("bottom");
			}
		}
		return true;
	}

	/** The name a block state file uses for {@code property}'s value in {@code state} (an enum's own name may differ). */
	private static <T extends Comparable<T>> String valueName(net.minecraft.world.level.block.state.BlockState state,
			net.minecraft.world.level.block.state.properties.Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/** Keeps the wild Pokémon round a Pasture Block (ROADMAP 28.10): only ever by a honey bottle, with Cobblemon and config {@code habitatKeepers}. */
	public static final VillagerProfession HABITAT_KEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("habitat_keeper"),
		new VillagerProfession(
			"habitat_keeper",
			holder -> holder.is(PASTURE_POI),
			holder -> holder.is(PASTURE_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.BEEHIVE_DRIP
		)
	);

	/** Looks after pairs of Pokémon at a Pasture Block and finds their eggs (ROADMAP 28.12): only ever by an egg, with Cobblemon and config {@code daycareKeepers}. */
	public static final VillagerProfession DAYCARE_KEEPER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("daycare_keeper"),
		new VillagerProfession(
			"daycare_keeper",
			holder -> holder.is(PASTURE_POI),
			holder -> holder.is(PASTURE_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.CHICKEN_EGG
		)
	);

	/**
	 * Tends the gem beds round a stonecutter (ROADMAP 28.11): only ever by an amethyst shard, with config {@code gemGrowers}.
	 * A jobless villager by a stonecutter still becomes a Mason, which vanilla registers first.
	 */
	public static final VillagerProfession GEM_GROWER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("gem_grower"),
		new VillagerProfession(
			"gem_grower",
			holder -> holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MASON),
			holder -> holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MASON),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.AMETHYST_BLOCK_CHIME
		)
	);

	/** Revives fossils for players, for a price (only with Cobblemon installed). */
	public static final VillagerProfession FOSSIL_SCIENTIST = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("fossil_scientist"),
		new VillagerProfession(
			"fossil_scientist",
			// They keep a Fossil Lab or Training Post they worked at before 21.1c, but only ever take a new Fossil Analyzer.
			holder -> holder.is(FOSSIL_ANALYZER_POI) || holder.is(FOSSIL_LAB_POI) || holder.is(TRAINING_POST_POI),
			holder -> holder.is(FOSSIL_ANALYZER_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.BREWING_STAND_BREW
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

	/**
	 * Night Owls (a gift, 29.6) work from dusk to dawn: up at dawn for the village's morning, asleep from mid-morning
	 * to mid-afternoon, about the village until dusk, then at work all night. Builders and miners too.
	 */
	public static final Schedule NIGHT_OWL_SCHEDULE = Registry.register(
		BuiltInRegistries.SCHEDULE,
		AliveWorkplace.id("night_owl"),
		new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.IDLE)
			.changeActivityAt(3000, Activity.REST)
			.changeActivityAt(9000, Activity.IDLE)
			.changeActivityAt(12000, Activity.WORK)
			.build()
	);

	/** Villagers whose work this mod runs: they get the long shift and our WORK package. */
	public static boolean isWorker(VillagerProfession profession) {
		return profession == BUILDER || profession == MINER || profession == LUMBERJACK || profession == POSTMAN
			|| profession == NURSE || profession == SHOPKEEPER || profession == FERRYMAN
			|| profession == TRAINER || profession == TRAINER_LEADER || profession == TUTOR || profession == POKEMON_TRADER
			|| profession == ORCHARD_KEEPER || profession == BALL_SMITH || profession == PORTER || profession == CARPENTER || profession == CHEF || profession == FOSSIL_SCIENTIST
			|| profession == BEEKEEPER || profession == FLORIST || profession == RANCHER || profession == TEACHER || profession == INNKEEPER || profession == UNDERTAKER || profession == SCHOLAR
			|| profession == SIFTER || profession == TINKERER || profession == NETHERWORKER || profession == COMPOSTER
			|| profession == BERRY_BREEDER || profession == CAMP_COOK || profession == HABITAT_KEEPER || profession == GEM_GROWER
			|| profession == DAYCARE_KEEPER;
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
