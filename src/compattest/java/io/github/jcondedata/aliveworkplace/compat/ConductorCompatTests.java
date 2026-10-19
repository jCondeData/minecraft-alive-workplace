package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 39.7d with the real Create: the Conductor. A jobless villager by a Create Train Station takes it as their job
 * by themselves; with {@code villageTrains} off the station is no job; the Conductor's trades are there, Create's track and
 * schedule among them; the village train is assembled with the village's Conductor in the seat rather than the jobless
 * villager standing nearer, and he calls the stop when it comes in at the other village; without a Conductor a jobless
 * villager drives it as before and nobody calls.
 */
public class ConductorCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	/** The track runs along x at this z, y = 2, from x = 1 to x = 28 (as in {@code CreateTrainCompatTests}). */
	private static final int TRACK_Z = 12;
	private static final BlockPos STATION_A = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos STATION_B = new BlockPos(23, 2, TRACK_Z + 1);
	private static final BlockPos HALL_A = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_B = new BlockPos(23, 2, 22);

	private static BlockState create(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	private static Item createItem(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		return villager;
	}

	/** Create's Train Station is a job site of ours, and a jobless villager by it becomes the Conductor by themselves. */
	//$ gametest_ticks_batch AREA '1800' '"conductor_takes_station"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "conductor_takes_station")
	public void aJoblessVillagerTakesTheStation(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(ModVillagers.CONDUCTOR != null, "no Conductor profession with Create installed");
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.CONDUCTOR).toString().equals("aliveworkplace:conductor"),
			"the Conductor's id is " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.CONDUCTOR));
		PoiType poi = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).get(ModVillagers.TRACK_STATION_ID);
		helper.assertTrue(poi != null && poi.is(create("track_station")), "Create's station isn't a Conductor's job block");
		helper.setBlock(STATION, create("track_station"));
		Villager villager = jobless(helper, new BlockPos(5, 2, 5));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> helper.assertTrue(ModVillagers.isConductor(villager.getVillagerData().getProfession()),
			"villager is still " + villager.getVillagerData().getProfession() + " at " + helper.relativePos(villager.blockPosition())));
	}

	/** Config {@code villageTrains} off: a jobless villager doesn't see the station as a workstation and stays jobless. */
	//$ gametest_ticks_batch AREA '1200' '"conductor_switched_off"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "conductor_switched_off")
	public void withVillageTrainsOffTheStationIsNoJob(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, create("track_station"));
		Villager villager = jobless(helper, new BlockPos(5, 2, 5));
		boolean before = CaravanTrains.ENABLED;
		CaravanTrains.ENABLED = false;
		var station = level.getPoiManager().getType(helper.absolutePos(STATION)).orElseThrow();
		WorkerLimits.thinking(villager);
		long seen;
		try {
			seen = WorkerLimits.hideFull(java.util.stream.Stream.of(com.mojang.datafixers.util.Pair.of(station, helper.absolutePos(STATION)))).count();
		} finally {
			WorkerLimits.thinking(null);
		}
		if (seen != 0) {
			CaravanTrains.ENABLED = before;
			helper.fail("villageTrains off: a jobless villager still sees the station as a workstation");
		}
		helper.setDayTime(2000);
		helper.runAfterDelay(600, () -> {
			CaravanTrains.ENABLED = before;
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"villageTrains off, and the villager took a job: " + villager.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/** The Conductor trades at every level: vanilla rails and coal, Create's track, signals, schedules, casing and controls. */
	//$ gametest_batch AREA '"conductor_trades"'
	@GameTest(template = AREA, batch = "conductor_trades")
	public void theConductorTrades(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		var levels = VillagerTrades.TRADES.get(ModVillagers.CONDUCTOR);
		helper.assertTrue(levels != null, "the Conductor has no trades");
		helper.setBlock(STATION, create("track_station"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		villager.setNoAi(true);
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), ModVillagers.CONDUCTOR);
		helper.assertTrue(CaravanTrains.isConductor(villager), "no Conductor at the station");
		List<Item> sold = new java.util.ArrayList<>();
		RandomSource random = RandomSource.create(7L);
		for (int lvl = 1; lvl <= 5; lvl++) {
			VillagerTrades.ItemListing[] listings = levels.get(lvl);
			helper.assertTrue(listings != null && listings.length > 0, "no Conductor trades at level " + lvl);
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(villager, random);
				helper.assertTrue(offer != null && !offer.getResult().isEmpty() && !offer.getCostA().isEmpty(), "a level " + lvl + " trade is empty: " + offer);
				sold.add(offer.getResult().getItem());
			}
		}
		for (Item wanted : List.of(Items.RAIL, createItem("track"), createItem("schedule"), createItem("track_signal"), createItem("controls"))) {
			helper.assertTrue(wanted != Items.AIR && sold.contains(wanted), "the Conductor doesn't sell " + wanted + ": " + sold);
		}
		helper.assertTrue(!villager.getOffers().isEmpty(), "a Novice Conductor offers nothing");
		villager.discard();
		helper.succeed();
	}

	/**
	 * Two villages joined by track: the train is assembled with the village's Conductor in the seat (the jobless villager
	 * standing nearer the station stays on the platform), and he calls the stop as it comes in at the other village.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"conductor_drives"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "conductor_drives")
	public void theConductorTakesTheSeatAndCallsTheStop(GameTestHelper helper) {
		trainRun(helper, true);
	}

	/** No Conductor in the village: a jobless villager drives the train as before, and nobody calls the stops. */
	//$ gametest_ticks_batch HUGE '2400' '"conductor_fallback"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "conductor_fallback")
	public void withoutAConductorAJoblessVillagerDrives(GameTestHelper helper) {
		trainRun(helper, false);
	}

	private static void trainRun(GameTestHelper helper, boolean withConductor) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		boolean west = CaravanTrains.first(v.a(), v.b()); // the village that puts the train together, at its station
		BlockPos home = west ? STATION_A : STATION_B;
		BlockPos b = helper.absolutePos(west ? STATION_B : STATION_A);
		BlockPos thereHall = west ? v.b() : v.a(); // the village the train comes in at
		Villager jobless = EntityType.VILLAGER.spawn(level, helper.absolutePos(home.offset(0, 0, 2)), MobSpawnType.COMMAND); // nearest the station
		jobless.setNoAi(true);
		Villager librarian = EntityType.VILLAGER.spawn(level, helper.absolutePos(home.offset(1, 0, 2)), MobSpawnType.COMMAND);
		librarian.setNoAi(true);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		Villager conductor = null;
		if (withConductor) {
			conductor = EntityType.VILLAGER.spawn(level, helper.absolutePos(home.offset(west ? 4 : -4, 0, 5)), MobSpawnType.COMMAND); // further off
			conductor.setNoAi(true);
			Stations.assign(level, conductor, helper.absolutePos(home), ModVillagers.CONDUCTOR);
			helper.assertTrue(CaravanTrains.isConductor(conductor), "no Conductor at the station");
		}
		Villager driver = conductor;
		CaravanTrains.lastCall = null;
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] seated = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (!seated[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					helper.assertTrue(CaravanTrains.lastCall == null, "a stop was called as the train was put together: " + CaravanTrains.lastCall);
					seated[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			if (tick[0] == 2300) {
				helper.fail("the train didn't come in at the other station: " + CaravanTrains.RAILWAY.call(r -> r.status(level, owner), "-"));
			}
			// the train standing at the other station when that village's hall has just looked (serve, above, this tick)
			if (tick[0] % 10 == 0 && CaravanTrains.RAILWAY.call(r -> r.at(level, owner, b), false)) {
				// who rides it, by id: Create may save a carriage's riders and put them back as new entities on the way
				if (driver != null) {
					helper.assertTrue(riding(level, driver), "the village's Conductor isn't riding the train at tick " + tick[0] + " (west " + west + "): "
						+ CaravanTrains.RAILWAY.call(r -> r.status(level, owner), "-"));
					helper.assertTrue(!riding(level, jobless), "the jobless villager took the seat over the Conductor");
				} else {
					helper.assertTrue(riding(level, jobless), "without a Conductor the jobless villager isn't riding the train");
				}
				helper.assertTrue(!riding(level, librarian), "the Librarian was taken off her job to drive the train");
				if (driver != null) {
					Component call = CaravanTrains.lastCall;
					String there = v.data().village(thereHall).name().getString(); // its name as the caravans' list has it now
					helper.assertTrue(call != null, "the Conductor didn't call the stop at the other village");
					helper.assertTrue(call.getContents() instanceof TranslatableContents t && t.getArgs().length == 2
						&& t.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents l
						&& l.getKey().equals("message.aliveworkplace.conductor.arriving") && l.getArgs().length == 1
						&& l.getArgs()[0] instanceof Component name && name.getString().equals(there),
						"the call doesn't name the village it came in at: " + call.getString());
				} else {
					helper.assertTrue(CaravanTrains.lastCall == null, "a stop was called without a Conductor: " + CaravanTrains.lastCall);
				}
				CaravanTrains.RAILWAY.call(r -> {
					r.scrap(level, owner);
					return true;
				}, false);
				tidy(level, v);
				helper.succeed();
			}
		});
	}

	/** Whether the villager with {@code villager}'s id (whichever entity has it now) is riding something. */
	private static boolean riding(ServerLevel level, Villager villager) {
		net.minecraft.world.entity.Entity now = level.getEntity(villager.getUUID());
		return now != null && now.isPassenger();
	}

	// ---- the two villages (as CreateTrainCompatTests sets them up) ----------------------------------------------------

	/** A flat stone floor, the track and the two stations. */
	private static void line(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y < 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockState track = MechanicCompatTests.with(create("track"), "shape", "xo");
		for (int x = 1; x <= 28; x++) {
			helper.setBlock(new BlockPos(x, 2, TRACK_Z), track);
		}
		helper.setBlock(STATION_A, create("track_station"));
		helper.setBlock(STATION_B, create("track_station"));
	}

	private record Villages(BlockPos a, BlockPos b, Caravans.Data data) {
	}

	/** Two halls with a storehouse chest each, a caravan route, the road between them built and tracked, the stations noted. */
	private static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos hall : List.of(a, b)) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of());
		data.toggleRoute(a, b);
		BlockPos meet = a.offset(8, 0, 0);
		data.setHalf(a, b, new Caravans.Half(meet, true, false, true));
		data.setHalf(b, a, new Caravans.Half(meet, true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
		CaravanTrains.noteStations(level, a, data);
		CaravanTrains.noteStations(level, b, data);
		helper.assertTrue(CaravanTrains.rides(data, a, b), "the two villages aren't on a railway: stations " + data.stations(a) + " / " + data.stations(b));
		return new Villages(a, b, data);
	}

	/** The train's blocks put up at the first village's station as its builder would, once Create knows both stations. */
	private static void buildTrain(ServerLevel level, Villages v) {
		BlockPos hall = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		List<BlockPos> ours = v.data().stations(hall);
		List<BlockPos> theirs = v.data().stations(hall.equals(v.a()) ? v.b() : v.a());
		CaravanTrains.RAILWAY.call(r -> {
			if (ours.isEmpty() || theirs.isEmpty() || CaravanTrains.realTrain(level, v.a(), v.b()) || !r.linked(level, ours.get(0), theirs.get(0))
				|| r.built(level, ours.get(0), theirs.get(0))) {
				return false;
			}
			java.util.Map<BlockPos, BlockState> parts = r.train(level, ours.get(0), theirs.get(0));
			if (parts != null) {
				parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
			}
			return true;
		}, false);
	}

	/** Takes the two villages away again (the next batch's areas stand within a hall's reach of this one). */
	private static void tidy(ServerLevel level, Villages v) {
		for (BlockPos hall : List.of(v.a(), v.b())) {
			level.removeBlock(hall.east(3).north(2), false);
			level.removeBlock(hall.east(3), false);
			level.removeBlock(hall, false);
			v.data().clearRoad(hall);
			v.data().remove(hall);
		}
	}

	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}
}
