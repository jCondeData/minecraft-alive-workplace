package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create, 2026-11-04; 39.7a/b, B900's fix): partner villages are up to 2048 blocks apart (Caravans.RANGE),
 * so on a real server the two ends of a village train are seldom loaded at once. Here the far village (its hall,
 * Storehouse chest and station) stands 110 blocks from the near one on one straight line, held loaded during setup and
 * then left to unload as it does when the player walks home; the near village's hall keeps looking at its train.
 * <ul>
 * <li>B912: a player renames the far village's station with Create's screen, then goes home: the wiki (create.md) says
 * the hall's next look points the train's stop at the new name; with the far station unloaded it never does
 * (CreateTrains.follow needs both stations' block entities). Control: the far village kept loaded.</li>
 * <li>B913: the train standing at the far village's station when that village unloads stays there, its conductors
 * false/false, until the far village is loaded again. Controls: kept loaded; loaded again 400 ticks later; and a train
 * that runs from the near village to the unloaded far one and back (it does).</li>
 * </ul>
 * The line is laid west of the area or, mirrored, east, so the train is put together where each case wants
 * (CaravanTrains.first goes by the halls' coordinates).
 */
public class QaTrainRenamedFarAway1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final int TRACK_Z = 12;
	/** How far west (relative x) the far village's end of the line is. */
	private static final int FAR = -110;
	private static final BlockPos NEAR_STATION = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos NEAR_HALL = new BlockPos(6, 2, 22);
	private static final BlockPos FAR_STATION = new BlockPos(FAR + 6, 2, TRACK_Z + 1);
	private static final BlockPos FAR_HALL = new BlockPos(FAR + 6, 2, 22);

	// Timeouts: the 6000 ticks a case needs plus ChunkWaits.TIMEOUT_TICKS for the wait, on the clock, until the force-loaded
	// far village is running (B1125, B1150).
	//$ gametest_ticks_batch AREA '26000' '"qaTrainRenamedFarAwayUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainRenamedFarAwayUnloaded")
	public void aStationRenamedInAFarVillageThatThenUnloadsIsFollowedByTheTrain(GameTestHelper helper) {
		run(helper, Case.RENAMED_UNLOADED);
	}

	/** Control: the same with the far village loaded all along; the hall's look renames the stop. */
	//$ gametest_ticks_batch AREA '26000' '"qaTrainRenamedFarAwayLoaded"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainRenamedFarAwayLoaded")
	public void aStationRenamedInAFarVillageKeptLoadedIsFollowedByTheTrain(GameTestHelper helper) {
		run(helper, Case.RENAMED_LOADED);
	}

	/**
	 * No rename: the train put together at the far village's station and standing there when the player walks away (the
	 * far village unloads with the train in it) still comes to the near village, where he is.
	 */
	//$ gametest_ticks_batch AREA '26000' '"qaTrainLeftStandingFarAway"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainLeftStandingFarAway")
	public void aTrainStandingAtAFarStationThatUnloadsStillComesToTheNearVillage(GameTestHelper helper) {
		run(helper, Case.STANDING_UNLOADED);
	}

	/** Control: the same with the far village loaded all along. */
	//$ gametest_ticks_batch AREA '26000' '"qaTrainLeftStandingFarAwayLoaded"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainLeftStandingFarAwayLoaded")
	public void aTrainStandingAtAFarStationKeptLoadedComesToTheNearVillage(GameTestHelper helper) {
		run(helper, Case.STANDING_LOADED);
	}

	/** The same, and the far village loaded again 400 ticks later (the player goes back): the train comes on then. */
	//$ gametest_ticks_batch AREA '26000' '"qaTrainLeftStandingFarAwayVisited"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainLeftStandingFarAwayVisited")
	public void aTrainStandingAtAFarStationThatUnloadsComesOnOnceItsVillageLoadsAgain(GameTestHelper helper) {
		run(helper, Case.STANDING_VISITED);
	}

	/** No rename: the train put together at the near village runs to the far one, unloaded, and comes back. */
	//$ gametest_ticks_batch AREA '26000' '"qaTrainRoundTripFarAway"'
	@GameTest(template = AREA, timeoutTicks = 26000, batch = "qaTrainRoundTripFarAway")
	public void aTrainToAFarVillageThatIsUnloadedComesBack(GameTestHelper helper) {
		run(helper, Case.ROUND_TRIP_UNLOADED);
	}

	private enum Case {
		RENAMED_UNLOADED(true, false, true), RENAMED_LOADED(true, true, true), STANDING_UNLOADED(false, false, false),
		STANDING_LOADED(false, true, false), STANDING_VISITED(false, false, false), ROUND_TRIP_UNLOADED(false, false, true);

		final boolean rename;
		final boolean keepLoaded;
		/** Whether the train is put together at the near village's station (else at the far one's). */
		final boolean nearBuilds;

		Case(boolean rename, boolean keepLoaded, boolean nearBuilds) {
			this.rename = rename;
			this.keepLoaded = keepLoaded;
			this.nearBuilds = nearBuilds;
		}
	}

	private static void run(GameTestHelper helper, Case c) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		// the train is put together at the first village's station (CaravanTrains.first, by the halls' coordinates): the
		// line is laid west or, mirrored, east of this area, whichever puts it together where the case wants
		boolean westNearFirst = CaravanTrains.first(helper.absolutePos(NEAR_HALL), helper.absolutePos(FAR_HALL));
		boolean east = westNearFirst != c.nearBuilds;
		Layout l = new Layout(helper, east);
		helper.assertTrue(CaravanTrains.first(l.near, l.far) == c.nearBuilds, "setup: neither layout puts the train together at the "
			+ (c.nearBuilds ? "near" : "far") + " village");
		List<long[]> forced = new ArrayList<>();
		BlockPos farMin = l.abs(new BlockPos(FAR - 4, 0, 0));
		BlockPos farMax = l.abs(new BlockPos(FAR + 30, 0, 29));
		for (int cx = Math.min(farMin.getX(), farMax.getX()) >> 4; cx <= Math.max(farMin.getX(), farMax.getX()) >> 4; cx++) {
			for (int cz = Math.min(farMin.getZ(), farMax.getZ()) >> 4; cz <= Math.max(farMin.getZ(), farMax.getZ()) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				forced.add(new long[] {cx, cz});
			}
		}
		PartnerShowsCompatTests.clearLeftovers(helper);
		line(l);
		BlockPos near = l.near;
		BlockPos far = l.far;
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos hall : List.of(near, far)) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		((Container) level.getBlockEntity(near.east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		data.setWants(near, Component.literal("Ashford"), List.of());
		data.setWants(far, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		data.toggleRoute(near, far);
		BlockPos meet = near.offset((far.getX() - near.getX()) / 2, 0, 0);
		data.setHalf(near, far, new Caravans.Half(meet, true, false, true));
		data.setHalf(far, near, new Caravans.Half(meet, true, false, true));
		data.setTracked(near, far, true);
		data.setTracked(far, near, true);
		CaravanTrains.noteStations(level, near, data);
		CaravanTrains.noteStations(level, far, data);
		helper.assertTrue(CaravanTrains.rides(data, near, far), "setup: the two villages aren't on a railway: stations "
			+ data.stations(near) + " / " + data.stations(far));
		CreateTrainCompatTests.Villages v = new CreateTrainCompatTests.Villages(near, far, data);
		BlockPos builds = c.nearBuilds ? l.nearStation : l.farStation;
		Villager driver = EntityType.VILLAGER.spawn(level, builds.south(3), MobSpawnType.COMMAND);
		driver.setNoAi(true);
		UUID owner = CaravanTrains.railwayId(near, far);
		int[] tick = {0};
		int[] startedAt = {-1};
		int[] unloadedAt = {-1};
		boolean[] left = {false};
		// B1125, B1150 (QaTrainFarAwayEdges1104e2CompatTests has the story): the forced far village only starts running
		// once its neighbours and entities have loaded on other threads, in real time, while the server ticks flat out; a
		// train put together there before entities move never has its conductor counted. The case's ticks count only while
		// the far village, when the player is there, is running.
		boolean[] wantFar = {true};
		ChunkWaits.Clock[] loading = {ChunkWaits.start(level)};
		helper.onEachTick(() -> {
			if (wantFar[0] && !(ChunkWaits.running(level, forced, far.getY()) && ChunkWaits.running(level, l.nearStation))) {
				if (loading[0] == null) {
					loading[0] = ChunkWaits.start(level); // a chunk stopped running while he's there: waited for again
				}
				if (loading[0].overdue()) {
					fail(helper, level, l, v, forced, "setup: the far village's chunks never started running: " + ChunkWaits.state(level, forced, far.getY())
						+ ", near station " + ChunkWaits.state(level, l.nearStation) + " (waited " + loading[0] + ")");
				}
				return;
			}
			if (wantFar[0] && loading[0] != null) {
				if (loading[0].ticks() > 0) {
					LOGGER.info("QA far-away train (B1125, B1150): the far village at {} was running after {}", far.toShortString(), loading[0]);
				}
				loading[0] = null; // running: said once for each time it is loaded
			}
			tick[0]++;
			if (startedAt[0] < 0) {
				if (tick[0] % 10 == 0) {
					CreateTrainCompatTests.buildTrain(level, v);
					CaravanTrains.serve(level, near, CreateTrainCompatTests.hall(level, near));
					CaravanTrains.serve(level, far, CreateTrainCompatTests.hall(level, far));
				}
				if (CaravanTrains.realTrain(level, near, far)) {
					if (c.rename) {
						StationBlockEntity be = (StationBlockEntity) level.getBlockEntity(l.farStation);
						helper.assertTrue(be != null && be.getStation() != null, "setup: the far station isn't a station Create knows");
						be.updateName("Harbor"); // the player at the far village, with Create's station screen
					}
					CreateTrainCompatTests.sendAgain(level, v);
					if (!c.keepLoaded) {
						for (long[] ch : forced) {
							level.setChunkForced((int) ch[0], (int) ch[1], false); // he walks home: the far village unloads
						}
						wantFar[0] = false;
					}
					startedAt[0] = tick[0];
				} else if (tick[0] > 1200) {
					fail(helper, level, l, v, forced, "setup: no village train was put together in 1200 ticks");
				}
				return;
			}
			if (!c.keepLoaded && unloadedAt[0] < 0) {
				if (!level.isLoaded(l.farStation)) {
					unloadedAt[0] = tick[0];
				} else if (tick[0] - startedAt[0] > 600) {
					fail(helper, level, l, v, forced, "setup: the far village never unloaded");
				}
				return; // the near hall looks again only once the far village is gone, as on a server with one player at home
			}
			if (c == Case.STANDING_VISITED && tick[0] - unloadedAt[0] == 400) {
				for (long[] ch : forced) {
					level.setChunkForced((int) ch[0], (int) ch[1], true); // he goes back to the far village
				}
				wantFar[0] = true;
				loading[0] = ChunkWaits.start(level);
			}
			if (tick[0] % 10 == 0) {
				// every hall that is loaded looks at its train, as its own tick does
				for (BlockPos hall : List.of(near, far)) {
					if (level.isLoaded(hall)) {
						CaravanTrains.serve(level, hall, CreateTrainCompatTests.hall(level, hall));
					}
				}
			}
			boolean atNear = CreateTrainCompatTests.railway().at(level, owner, l.nearStation);
			left[0] |= !atNear;
			boolean ok = c.rename ? stops(owner).contains("Harbor") : c.nearBuilds ? left[0] && atNear : atNear;
			if (ok) {
				CreateTrainCompatTests.railway().scrap(level, owner);
				tidy(level, l, v, forced);
				helper.succeed();
			} else if (tick[0] - startedAt[0] >= 4000) {
				String what = c.rename ? "a player renamed the far village's station 'Harbor' and the far village "
					+ (c.keepLoaded ? "stayed loaded" : "then unloaded") + "; the village train's schedule still goes to " + stops(owner)
					+ " (no station of that name is left)"
					: c.nearBuilds ? "the train put together at the near village never came back from the far one, unloaded (it left: " + left[0] + ")"
					: "the train standing at the far village's station " + (c.keepLoaded ? "(kept loaded)" : c == Case.STANDING_VISITED
					? "when the far village unloaded, loaded again 400 ticks later," : "when the far village unloaded")
					+ " never came to the near village";
				fail(helper, level, l, v, forced, what + "; " + (tick[0] - startedAt[0]) + " ticks on: " + CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	/** Where everything is: the line west of the area, or mirrored east of it. */
	private static final class Layout {
		final GameTestHelper helper;
		final boolean east;
		final BlockPos near;
		final BlockPos far;
		final BlockPos nearStation;
		final BlockPos farStation;

		Layout(GameTestHelper helper, boolean east) {
			this.helper = helper;
			this.east = east;
			near = abs(NEAR_HALL);
			far = abs(FAR_HALL);
			nearStation = abs(NEAR_STATION);
			farStation = abs(FAR_STATION);
		}

		BlockPos abs(BlockPos rel) {
			return helper.absolutePos(east ? new BlockPos(29 - rel.getX(), rel.getY(), rel.getZ()) : rel);
		}
	}

	/** The station names on the village train's Create schedule, in order (empty without the train). */
	private static List<String> stops(UUID owner) {
		List<String> out = new ArrayList<>();
		for (Train train : Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				Schedule schedule = train.runtime.getSchedule();
				if (schedule != null) {
					for (ScheduleEntry entry : schedule.entries) {
						if (entry.instruction instanceof DestinationInstruction to) {
							out.add(to.getData().getString("Text"));
						}
					}
				}
			}
		}
		return out;
	}

	/** A flat stone floor from the far village to the near one, one straight track along it and a station by each village. */
	private static void line(Layout l) {
		ServerLevel level = l.helper.getLevel();
		for (int x = FAR - 4; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				level.setBlockAndUpdate(l.abs(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
				for (int y = 2; y < 8; y++) {
					level.setBlockAndUpdate(l.abs(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
				}
			}
		}
		BlockState track = state("track");
		track = track.setValue(prop(track, "shape"), value(track, "shape", "xo"));
		for (int x = FAR + 1; x <= 28; x++) {
			level.setBlockAndUpdate(l.abs(new BlockPos(x, 2, TRACK_Z)), track);
		}
		level.setBlockAndUpdate(l.nearStation, state("track_station"));
		level.setBlockAndUpdate(l.farStation, state("track_station"));
	}

	/** Takes the track and stations outside the area up again (Create's railway would keep them for the next batch here). */
	private static void untrack(Layout l) {
		ServerLevel level = l.helper.getLevel();
		for (int x = FAR - 4; x <= 0; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < 8; y++) {
					BlockPos p = l.abs(new BlockPos(x, y, z));
					if (!level.getBlockState(p).isAir()) {
						level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void fail(GameTestHelper helper, ServerLevel level, Layout l, CreateTrainCompatTests.Villages v, List<long[]> forced, String why) {
		CreateTrainCompatTests.railway().scrap(level, CaravanTrains.railwayId(v.a(), v.b()));
		tidy(level, l, v, forced);
		helper.fail(why);
	}

	private static void tidy(ServerLevel level, Layout l, CreateTrainCompatTests.Villages v, List<long[]> forced) {
		CreateTrainCompatTests.tidy(level, v);
		untrack(l);
		for (long[] c : forced) {
			level.setChunkForced((int) c[0], (int) c[1], false);
		}
	}

	private static BlockState state(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.world.level.block.state.properties.Property prop(BlockState state, String name) {
		return state.getBlock().getStateDefinition().getProperty(name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Comparable value(BlockState state, String name, String value) {
		return (Comparable) prop(state, name).getValue(value).orElseThrow();
	}
}
