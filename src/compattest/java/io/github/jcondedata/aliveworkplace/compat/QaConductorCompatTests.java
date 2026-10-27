package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA for ROADMAP 39.7d (the Conductor), written from the player guide's page (docs/guide/conductor.md), with the real
 * Create: one station takes one Conductor; a Conductor hired before the Village trains setting goes off keeps the job; a
 * Conductor saved and loaded again is still the Conductor with the same trades; the trades by rank as the guide lists
 * them (Novices buy coal, train controls only for a Master); and the stop is called once while the train stands at the
 * station, with the chronicle naming the Conductor who took the seat.
 */
public class QaConductorCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
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

	/** "One station, one Conductor": two jobless villagers by one station, and only one of them takes it. */
	//$ gametest_ticks_batch AREA '2400' '"qa_conductor_one_station"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_conductor_one_station")
	public void oneStationTakesOneConductor(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STATION, create("track_station"));
		Villager first = jobless(helper, new BlockPos(5, 2, 5));
		Villager second = jobless(helper, new BlockPos(5, 2, 3));
		helper.setDayTime(2000);
		int[] since = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			int hired = (ModVillagers.isConductor(first.getVillagerData().getProfession()) ? 1 : 0)
				+ (ModVillagers.isConductor(second.getVillagerData().getProfession()) ? 1 : 0);
			if (hired > 1) {
				helper.fail("one station hired two Conductors");
			}
			if (hired == 1 && since[0] < 0) {
				since[0] = tick[0];
			}
			if (since[0] >= 0 && tick[0] - since[0] >= 600) {
				helper.succeed(); // one Conductor, and still one 30 seconds later
			}
			if (tick[0] == 2300 && since[0] < 0) {
				helper.fail("neither jobless villager took the station in 2300 ticks");
			}
		});
	}

	/** "With the Village trains setting off ... a Conductor already hired keeps the job", working with his AI on. */
	//$ gametest_ticks_batch AREA '1600' '"qa_conductor_keeps_job"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_conductor_keeps_job")
	public void aHiredConductorKeepsTheJobWithTheSwitchOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, create("track_station"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Stations.assign(level, villager, helper.absolutePos(STATION), ModVillagers.CONDUCTOR);
		helper.assertTrue(CaravanTrains.isConductor(villager), "no Conductor at the station");
		boolean before = CaravanTrains.ENABLED;
		CaravanTrains.ENABLED = false;
		helper.setDayTime(2000);
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (!CaravanTrains.isConductor(villager)) {
				CaravanTrains.ENABLED = before;
				helper.fail("with Village trains off the hired Conductor lost the job at tick " + tick[0] + ": "
					+ villager.getVillagerData().getProfession());
			}
			if (tick[0] == 1200) {
				CaravanTrains.ENABLED = before;
				helper.succeed();
			}
		});
	}

	/** A Conductor saved and loaded again (a server restart, with Create) is the Conductor with the same trades. */
	//$ gametest_batch AREA '"qa_conductor_reload"'
	@GameTest(template = AREA, batch = "qa_conductor_reload")
	public void aConductorSavedAndLoadedIsStillTheConductorWithHisTrades(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, create("track_station"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		villager.setNoAi(true);
		Stations.assign(level, villager, helper.absolutePos(STATION), ModVillagers.CONDUCTOR);
		List<String> offers = new ArrayList<>();
		for (MerchantOffer offer : villager.getOffers()) {
			offers.add(offer.getCostA() + " -> " + offer.getResult());
		}
		helper.assertTrue(!offers.isEmpty(), "a Novice Conductor offers nothing");
		CompoundTag saved = villager.saveWithoutId(new CompoundTag());
		Villager loaded = EntityType.VILLAGER.create(level);
		helper.assertTrue(loaded != null, "no villager to load into");
		loaded.load(saved);
		helper.assertTrue(ModVillagers.isConductor(loaded.getVillagerData().getProfession()),
			"a saved Conductor loads as " + loaded.getVillagerData().getProfession());
		List<String> after = new ArrayList<>();
		for (MerchantOffer offer : loaded.getOffers()) {
			after.add(offer.getCostA() + " -> " + offer.getResult());
		}
		helper.assertTrue(after.equals(offers), "the Conductor's trades changed over a save: " + offers + " then " + after);
		villager.discard();
		helper.succeed();
	}

	/**
	 * The trades by rank as the guide gives them: Novices buy coal and sell rails; schedules, signals and casing come as they
	 * grow in rank (never at Novice); train controls only as a Master; powered rails and railway casing are sold somewhere.
	 */
	//$ gametest_batch AREA '"qa_conductor_ranks"'
	@GameTest(template = AREA, batch = "qa_conductor_ranks")
	public void theConductorsTradesFollowHisRank(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		var levels = VillagerTrades.TRADES.get(ModVillagers.CONDUCTOR);
		helper.assertTrue(levels != null, "the Conductor has no trades");
		helper.setBlock(STATION, create("track_station"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		villager.setNoAi(true);
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), ModVillagers.CONDUCTOR);
		List<List<MerchantOffer>> byRank = new ArrayList<>();
		List<Item> sold = new ArrayList<>();
		for (int lvl = 1; lvl <= 5; lvl++) {
			List<MerchantOffer> rank = new ArrayList<>();
			for (VillagerTrades.ItemListing listing : levels.get(lvl)) {
				// every listing with a few seeds, so a listing that picks among items shows each of them
				for (long seed = 0; seed < 8; seed++) {
					MerchantOffer offer = listing.getOffer(villager, RandomSource.create(seed));
					if (offer != null) {
						rank.add(offer);
						sold.add(offer.getResult().getItem());
					}
				}
			}
			byRank.add(rank);
		}
		List<MerchantOffer> novice = byRank.get(0);
		helper.assertTrue(novice.stream().anyMatch(o -> o.getCostA().is(Items.COAL) && o.getResult().is(Items.EMERALD)),
			"a Novice Conductor doesn't buy coal: " + novice);
		helper.assertTrue(novice.stream().anyMatch(o -> o.getResult().is(Items.RAIL)), "a Novice Conductor doesn't sell rails: " + novice);
		for (String later : List.of("schedule", "track_signal", "railway_casing", "controls")) {
			Item item = createItem(later);
			helper.assertTrue(item != Items.AIR, "no create:" + later);
			helper.assertTrue(novice.stream().noneMatch(o -> o.getResult().is(item)), "a Novice already sells create:" + later);
		}
		Item controls = createItem("controls");
		for (int lvl = 1; lvl <= 4; lvl++) {
			int rank = lvl;
			helper.assertTrue(byRank.get(lvl - 1).stream().noneMatch(o -> o.getResult().is(controls)),
				"train controls are sold before Master, at level " + rank);
		}
		helper.assertTrue(byRank.get(4).stream().anyMatch(o -> o.getResult().is(controls)), "a Master Conductor doesn't sell train controls: " + byRank.get(4));
		for (Item wanted : List.of(Items.POWERED_RAIL, createItem("railway_casing"), createItem("track"))) {
			helper.assertTrue(sold.contains(wanted), "the Conductor never sells " + wanted);
		}
		villager.discard();
		helper.succeed();
	}

	/**
	 * The train comes in at the other village with the Conductor in the seat: he calls the stop once, not again while the
	 * train stands there, and the first village's chronicle says he took the seat (by name) and where the train runs.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa_conductor_calls_once"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa_conductor_calls_once")
	public void theStopIsCalledOnceWhileTheTrainStands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		boolean west = CaravanTrains.first(v.a(), v.b());
		BlockPos home = west ? STATION_A : STATION_B;
		BlockPos firstHall = west ? v.a() : v.b();
		BlockPos b = helper.absolutePos(west ? STATION_B : STATION_A);
		Villager conductor = EntityType.VILLAGER.spawn(level, helper.absolutePos(home.offset(west ? 4 : -4, 0, 5)), MobSpawnType.COMMAND);
		conductor.setNoAi(true);
		Stations.assign(level, conductor, helper.absolutePos(home), ModVillagers.CONDUCTOR);
		String name = conductor.getDisplayName().getString();
		CaravanTrains.lastCall = null;
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] calledAt = {-1};
		int[] standingLooks = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			buildTrain(level, v);
			CaravanTrains.serve(level, v.a(), hall(level, v.a()));
			CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			boolean atB = CaravanTrains.RAILWAY.call(r -> r.at(level, owner, b), false);
			if (calledAt[0] < 0) {
				if (atB && CaravanTrains.lastCall != null) {
					calledAt[0] = tick[0];
					CaravanTrains.lastCall = null; // any call from now on, while it stands there, is a second one
					boolean chronicled = hall(level, firstHall).chronicle().stream().anyMatch(e -> e.text().getContents() instanceof TranslatableContents t
						&& t.getKey().equals("chronicle.aliveworkplace.train_assembled") && t.getArgs().length == 2
						&& t.getArgs()[0] instanceof Component who && who.getString().equals(name));
					helper.assertTrue(chronicled, "the chronicle doesn't say " + name + " took the seat of the village's train");
				} else if (tick[0] >= 2300) {
					helper.fail("the train didn't come in at the other station with a call: " + CaravanTrains.RAILWAY.call(r -> r.status(level, owner), "-"));
				}
				return;
			}
			helper.assertTrue(CaravanTrains.lastCall == null, "the stop was called again while the train stood there, at tick " + tick[0]);
			if (atB) {
				standingLooks[0]++;
			}
			if (!atB || standingLooks[0] >= 20 || tick[0] >= 2300) {
				CaravanTrains.RAILWAY.call(r -> {
					r.scrap(level, owner);
					return true;
				}, false);
				tidy(level, v);
				helper.succeed();
			}
		});
	}

	/**
	 * QA lane E, 2026-10-27, from the wiki's Create page: the Conductor calls the stop "in the chat of players within 24
	 * blocks of the station": a player 24 blocks from the arrival station reads "<name>: Now arriving at <village>! All
	 * change, mind the gap.", word for word; one 26.5 blocks off reads nothing of it.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa_conductor_call_reach"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa_conductor_call_reach")
	public void theStopIsCalledToPlayersWithinTwentyFourBlocksOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		boolean west = CaravanTrains.first(v.a(), v.b());
		BlockPos home = west ? STATION_A : STATION_B;
		BlockPos b = helper.absolutePos(west ? STATION_B : STATION_A);
		Villager conductor = EntityType.VILLAGER.spawn(level, helper.absolutePos(home.offset(west ? 4 : -4, 0, 5)), MobSpawnType.COMMAND);
		conductor.setNoAi(true);
		Stations.assign(level, conductor, helper.absolutePos(home), ModVillagers.CONDUCTOR);
		String name = conductor.getDisplayName().getString();
		List<String> nearHeard = new java.util.concurrent.CopyOnWriteArrayList<>();
		List<String> farHeard = new java.util.concurrent.CopyOnWriteArrayList<>();
		net.minecraft.server.level.ServerPlayer near = listener(helper, nearHeard, b.offset(0, 0, 24));
		net.minecraft.server.level.ServerPlayer far = listener(helper, farHeard, b.offset(0, 0, -27));
		CaravanTrains.lastCall = null;
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		java.util.regex.Pattern expected = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(name + ": Now arriving at ")
			+ "[^!]+" + java.util.regex.Pattern.quote("! All change, mind the gap."));
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			near.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 24.5);
			far.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() - 26.5);
			buildTrain(level, v);
			CaravanTrains.serve(level, v.a(), hall(level, v.a()));
			CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			boolean atB = CaravanTrains.RAILWAY.call(r -> r.at(level, owner, b), false);
			if (!(atB && CaravanTrains.lastCall != null) && tick[0] < 2300) {
				return;
			}
			try {
				helper.assertTrue(atB && CaravanTrains.lastCall != null,
					"the train didn't come in at the other station with a call: " + CaravanTrains.RAILWAY.call(r -> r.status(level, owner), "-"));
				helper.assertTrue(nearHeard.stream().anyMatch(s -> expected.matcher(s).matches()), "a player 24 blocks from the station didn't read the call word for word ("
					+ expected + "): " + nearHeard);
				helper.assertTrue(farHeard.stream().noneMatch(s -> s.contains("Now arriving")), "a player 26.5 blocks off read the call: " + farHeard);
			} finally {
				CaravanTrains.RAILWAY.call(r -> {
					r.scrap(level, owner);
					return true;
				}, false);
				tidy(level, v);
				level.getServer().getPlayerList().remove(near);
				level.getServer().getPlayerList().remove(far);
			}
			helper.succeed();
		});
	}

	/** A player who writes down every line shown to them, standing (flying, so nothing moves them) at {@code at}. */
	private static net.minecraft.server.level.ServerPlayer listener(GameTestHelper helper, List<String> seen, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-ear-" + seen.hashCode() % 1000), false);
		net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(),
			cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		player.getAbilities().flying = true;
		player.setNoGravity(true);
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		return player;
	}

	// ---- the two villages (as ConductorCompatTests sets them up) -----------------------------------------------------

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
