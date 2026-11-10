package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
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
import net.minecraft.nbt.CompoundTag;
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
 * QA (lane E, Create, 2026-11-04, second pass; B912 and B913's fixes, c56f01f78 and 3ff1e5a63): the edges around a village
 * train whose far village (110 blocks off, on one straight line) is unloaded, as on a real server where partners are up
 * to 2048 blocks apart. Written from the bugs' text (the stop follows a renamed station with either end unloaded; a train
 * standing at a station keeps running when that village unloads; caravans waiting where the new train is put together
 * board its first departure), not from the fix. QaTrainRenamedFarAway1104eCompatTests covers the bugs' own cases.
 * <ul>
 * <li>The near (loaded) village's station renamed while the far one is unloaded: the far stop is read from the railway,
 * and the train still comes back to the renamed near station.</li>
 * <li>The far station renamed twice before it unloads: only the last name counts, and the train gets there and back.</li>
 * <li>The far station renamed, followed, then renamed back on a later visit and unloaded again: the stop goes back.</li>
 * <li>The train standing at the unloaded far station saved and read back (a server restart, as far as the harness can):
 * it keeps its conductors and comes to the near village.</li>
 * <li>A caravan sent from the far village as the train is put together there boards that first departure, and its goods
 * reach the near village's chest with the far village unloaded behind it.</li>
 * </ul>
 */
public class QaTrainFarAwayEdges1104e2CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRACK_Z = 12;
	private static final int FAR = -110;
	private static final BlockPos NEAR_STATION = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos NEAR_HALL = new BlockPos(6, 2, 22);
	private static final BlockPos FAR_STATION = new BlockPos(FAR + 6, 2, TRACK_Z + 1);
	private static final BlockPos FAR_HALL = new BlockPos(FAR + 6, 2, 22);
	/** How long a case waits for what the player should see once the far village has unloaded. */
	private static final int WAIT = 4000;

	/** B912 reversed: the player renames his own (near) village's station while the far one is unloaded. */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainEdgesNearRenamed"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainEdgesNearRenamed")
	public void theNearStationRenamedWithTheFarVillageUnloadedIsFollowedAndTheTrainComesBackToIt(GameTestHelper helper) {
		Run r = new Run(helper, true, false);
		boolean[] renamed = {false};
		boolean[] leftAfter = {false};
		r.go(() -> r.sendNearToFar(), () -> {
			if (!renamed[0]) {
				StationBlockEntity be = (StationBlockEntity) r.level.getBlockEntity(r.l.nearStation);
				helper.assertTrue(be != null && be.getStation() != null, "the near station isn't a station Create knows");
				be.updateName("Harbor"); // the player at home, with Create's station screen
				renamed[0] = true;
				return null;
			}
			r.serveLoaded();
			boolean atNear = r.at(r.l.nearStation);
			leftAfter[0] |= !atNear;
			List<String> stops = r.stops();
			if (stops.size() == 2 && stops.contains("Harbor") && stops.contains(r.farName) && leftAfter[0] && atNear) {
				return "";
			}
			return r.elapsed() >= WAIT ? "the near station was renamed 'Harbor' with the far village unloaded; the train's stops are " + stops
				+ " (wanted 'Harbor' and the far station's '" + r.farName + "'), it left the near station: " + leftAfter[0] + ", at it now: " + atNear : null;
		});
	}

	/** The far station renamed twice before the player walks home: the train goes by the last name, there and back. */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainEdgesFarRenamedTwice"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainEdgesFarRenamedTwice")
	public void theFarStationRenamedTwiceBeforeItUnloadsIsFollowedUnderItsLastName(GameTestHelper helper) {
		Run r = new Run(helper, true, false);
		boolean[] left = {false};
		r.go(() -> {
			StationBlockEntity be = (StationBlockEntity) r.level.getBlockEntity(r.l.farStation);
			helper.assertTrue(be != null && be.getStation() != null, "setup: the far station isn't a station Create knows");
			be.updateName("Harbor");
			be.updateName("Quayside");
			r.expect = "Quayside";
			r.sendNearToFar();
		}, () -> {
			r.serveLoaded();
			boolean atNear = r.at(r.l.nearStation);
			left[0] |= !atNear;
			List<String> stops = r.stops();
			if (stops.size() == 2 && stops.contains("Quayside") && !stops.contains("Harbor") && left[0] && atNear) {
				return "";
			}
			return r.elapsed() >= WAIT ? "the far station was renamed 'Harbor', then 'Quayside', then unloaded; the train's stops are " + stops
				+ ", it left the near station: " + left[0] + ", back at it: " + atNear : null;
		});
	}

	/**
	 * The far station renamed 'Harbor' and the stop followed with the far village unloaded; on a later visit the player
	 * names it back as it was and walks home again: the stop goes back to the old name.
	 */
	//$ gametest_ticks_batch AREA '8000' '"qaTrainEdgesFarRenamedBack"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qaTrainEdgesFarRenamedBack")
	public void theFarStationRenamedBackOnALaterVisitIsFollowedBackOnceItUnloadsAgain(GameTestHelper helper) {
		Run r = new Run(helper, true, false);
		// 0: waiting for 'Harbor' to be followed; 1: visiting (far loaded again); 2: named back, waiting for it to unload; 3: checking
		int[] phase = {0};
		int[] at = {0};
		boolean[] left = {false};
		r.go(() -> {
			StationBlockEntity be = (StationBlockEntity) r.level.getBlockEntity(r.l.farStation);
			helper.assertTrue(be != null && be.getStation() != null, "setup: the far station isn't a station Create knows");
			be.updateName("Harbor");
			r.expect = "Harbor";
			r.sendNearToFar();
		}, () -> {
			switch (phase[0]) {
				case 0 -> {
					r.serveLoaded();
					if (r.stops().contains("Harbor")) {
						r.force(true); // he goes back to the far village
						phase[0] = 1;
						at[0] = r.tick;
					} else if (r.elapsed() >= WAIT) {
						return "the far station renamed 'Harbor' (then unloaded) was never followed: stops " + r.stops();
					}
				}
				case 1 -> {
					if (r.level.isLoaded(r.l.farStation) && r.level.getBlockEntity(r.l.farStation) instanceof StationBlockEntity be && be.getStation() != null
							&& r.quiet()) {
						be.updateName(r.farName); // and names it back
						r.force(false); // then walks home
						phase[0] = 2;
						at[0] = r.tick;
					} else if (r.tick - at[0] > 600) {
						return "setup: the far village didn't load again in 600 ticks";
					}
				}
				case 2 -> {
					if (!r.level.isLoaded(r.l.farStation)) {
						if (r.stops().contains(r.farName)) {
							return "setup: the stop followed the name back before the far village unloaded (a hall looked in between): " + r.stops();
						}
						phase[0] = 3;
						at[0] = r.tick;
					} else if (r.tick - at[0] > 600) {
						return "setup: the far village didn't unload again in 600 ticks";
					}
				}
				default -> {
					r.serveLoaded();
					boolean atNear = r.at(r.l.nearStation);
					left[0] |= !atNear;
					List<String> stops = r.stops();
					if (stops.size() == 2 && stops.contains(r.farName) && !stops.contains("Harbor") && left[0] && atNear) {
						return "";
					}
					if (r.tick - at[0] >= WAIT) {
						return "the far station, followed as 'Harbor', was named back '" + r.farName + "' and unloaded again; the train's stops are "
							+ stops + ", it left the near station since: " + left[0] + ", back at it: " + atNear;
					}
				}
			}
			return null;
		});
	}

	/**
	 * B913 across a restart: the train standing at the far station when that village unloads is saved and read back
	 * (Create's own write and read of the train, its carriages put away as the unloaded village keeps them); it still
	 * has its conductors and comes to the near village.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainEdgesStandingReloaded"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainEdgesStandingReloaded")
	public void aTrainStandingAtTheUnloadedFarStationSavedAndReadBackStillComesToTheNearVillage(GameTestHelper helper) {
		Run r = new Run(helper, false, false);
		boolean[] reloaded = {false};
		String[] before = {""};
		r.go(() -> r.sendNearToFar(), () -> {
			if (!reloaded[0]) {
				if (r.elapsed() < 20) {
					return null;
				}
				Train train = Create.RAILWAYS.trains.get(r.trainId());
				helper.assertTrue(train != null, "the village train is gone once the far village unloaded: " + r.status());
				before[0] = r.status();
				DimensionPalette palette = new DimensionPalette();
				CompoundTag tag = train.write(palette, r.level.registryAccess());
				Create.RAILWAYS.removeTrain(train.id);
				Train back = Train.read(tag, r.level.registryAccess(), Create.RAILWAYS.trackNetworks, palette);
				Create.RAILWAYS.addTrain(back);
				helper.assertTrue(back.hasForwardConductor() || back.hasBackwardConductor(), "the village train read back has no conductor (before: "
					+ before[0] + ", after: " + r.status() + ")");
				reloaded[0] = true;
				return null;
			}
			r.serveLoaded();
			if (r.at(r.l.nearStation)) {
				return "";
			}
			return r.elapsed() >= WAIT ? "the train standing at the unloaded far station, saved and read back, never came to the near village (before: "
				+ before[0] + "), now: " + r.status() : null;
		});
	}

	/**
	 * 3ff1e5a63 with B913: a caravan sent from the far village just as the train is put together at its station boards that
	 * first departure (not a round trip later); once the train has left, the far village unloads, and the 32 logs reach
	 * the near village's chest.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainEdgesFirstDeparture"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainEdgesFirstDeparture")
	public void aCaravanSentAsTheFarTrainIsPutTogetherBoardsItsFirstDepartureAndArrives(GameTestHelper helper) {
		Run r = new Run(helper, false, true);
		int[] departed = {-1};
		int[] boarded = {-1};
		r.go(() -> r.sendFarToNear(), () -> {
			Caravans.Shipment s = r.data.onTheRoad().stream().filter(x -> x.from().equals(r.l.far) && x.to().equals(r.l.near)).findFirst().orElse(null);
			if (boarded[0] < 0 && s != null && CaravanTrains.aboard(s.arrives())) {
				boarded[0] = r.tick;
			}
			if (departed[0] < 0) {
				if (r.tick % 10 == 0) {
					r.serveAll();
				}
				boolean atFar = r.at(r.l.farStation);
				if (!atFar) { // gone from the station where it was put together (at once, too: then it didn't wait at all)
					departed[0] = r.tick;
					if (boarded[0] < 0) {
						return "the caravan sent from the far village as its train was put together missed the train's first departure ("
							+ (departed[0] - r.startedAt) + " ticks after it was put together); caravan " + s + ", train " + r.status();
					}
					r.force(false); // the train is off: the player walks home
				} else if (r.elapsed() > 1200) {
					return "the new train never left the far station in 1200 ticks: caravan " + s + ", train " + r.status();
				}
				return null;
			}
			r.serveLoaded();
			int logs = ((Container) r.level.getBlockEntity(r.l.near.east(3).north(2))).countItem(Items.OAK_LOG);
			if (s == null && logs == 32 && CreateTrainCompatTests.said(r.level, r.l.near, "chronicle.aliveworkplace.train_came")) {
				return "";
			}
			return r.elapsed() >= WAIT ? "the caravan aboard the first departure never reached the near village: " + logs + " of 32 logs in its chest, caravan "
				+ s + ", train " + r.status() + ", the near hall's storehouse chests " + Caravans.storehouse(r.level, r.l.near).stream()
				.map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)) + "=" + ((Container) r.level.getBlockEntity(p)).countItem(Items.OAK_LOG)).toList()
				+ ", train's chest " + CreateTrainCompatTests.railway().cargo(r.level, r.owner) : null;
		});
	}

	/** One case: the line, the two villages, the train put together, the far village left to unload. */
	private static final class Run {
		final GameTestHelper helper;
		final ServerLevel level;
		final Layout l;
		final List<long[]> forced = new ArrayList<>();
		final Caravans.Data data;
		final CreateTrainCompatTests.Villages v;
		final UUID owner;
		/** Whether the far village stays loaded until the case lets it go itself. */
		final boolean holdFar;
		String farName = "";
		int tick;
		int startedAt = -1;
		int unloadedAt = -1;

		Run(GameTestHelper helper, boolean nearBuilds, boolean holdFar) {
			this.helper = helper;
			this.holdFar = holdFar;
			level = helper.getLevel();
			helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
			boolean westNearFirst = CaravanTrains.first(helper.absolutePos(NEAR_HALL), helper.absolutePos(FAR_HALL));
			l = new Layout(helper, westNearFirst != nearBuilds);
			helper.assertTrue(CaravanTrains.first(l.near, l.far) == nearBuilds, "setup: neither layout puts the train together where the case wants");
			BlockPos farMin = l.abs(new BlockPos(FAR - 4, 0, 0));
			BlockPos farMax = l.abs(new BlockPos(FAR + 30, 0, 29));
			for (int cx = Math.min(farMin.getX(), farMax.getX()) >> 4; cx <= Math.max(farMin.getX(), farMax.getX()) >> 4; cx++) {
				for (int cz = Math.min(farMin.getZ(), farMax.getZ()) >> 4; cz <= Math.max(farMin.getZ(), farMax.getZ()) >> 4; cz++) {
					forced.add(new long[] {cx, cz});
				}
			}
			force(true);
			PartnerShowsCompatTests.clearLeftovers(helper);
			line(l);
			data = Caravans.Data.get(level);
			// arriving goods go into the chests by every Storehouse within the hall's reach (Caravans.storehouse): one an
			// earlier batch left near here would take the train's logs (as CreateTrainCompatTests.villages clears them)
			for (BlockPos hall : List.of(l.near, l.far)) {
				for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
						p -> true, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
						net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
					level.removeBlock(store, false);
				}
			}
			for (BlockPos hall : List.of(l.near, l.far)) {
				data.remove(hall);
				level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
				level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
				level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
			}
			data.setWants(l.near, Component.literal("Ashford"), List.of());
			data.setWants(l.far, Component.literal("Bramble"), List.of());
			data.toggleRoute(l.near, l.far);
			if (holdFar) {
				data.toggleRoute(l.far, l.near); // the case where the far village sends
			}
			BlockPos meet = l.near.offset((l.far.getX() - l.near.getX()) / 2, 0, 0);
			data.setHalf(l.near, l.far, new Caravans.Half(meet, true, false, true));
			data.setHalf(l.far, l.near, new Caravans.Half(meet, true, false, true));
			data.setTracked(l.near, l.far, true);
			data.setTracked(l.far, l.near, true);
			CaravanTrains.noteStations(level, l.near, data);
			CaravanTrains.noteStations(level, l.far, data);
			helper.assertTrue(CaravanTrains.rides(data, l.near, l.far), "setup: the two villages aren't on a railway: stations "
				+ data.stations(l.near) + " / " + data.stations(l.far));
			v = new CreateTrainCompatTests.Villages(l.near, l.far, data);
			Villager driver = EntityType.VILLAGER.spawn(level, (nearBuilds ? l.nearStation : l.farStation).south(3), MobSpawnType.COMMAND);
			driver.setNoAi(true);
			owner = CaravanTrains.railwayId(l.near, l.far);
		}

		/**
		 * Runs the case: {@code atStart} the tick the train is put together (the far village loaded), then, once the far
		 * village has unloaded (or at once when the case holds it), {@code check} each tick: null to go on, "" for a pass,
		 * else why it failed.
		 */
		void go(Runnable atStart, java.util.function.Supplier<String> check) {
			helper.onEachTick(() -> {
				tick++;
				if (startedAt < 0) {
					if (tick % 10 == 0) {
						CreateTrainCompatTests.buildTrain(level, v);
						serveAll();
					}
					if (CaravanTrains.realTrain(level, l.near, l.far) && quiet()) {
						StationBlockEntity far = (StationBlockEntity) level.getBlockEntity(l.farStation);
						helper.assertTrue(far != null && far.getStation() != null, "setup: the far station isn't a station Create knows");
						farName = far.getStation().name;
						atStart.run();
						if (!holdFar) {
							force(false); // he walks home: the far village unloads
						}
						startedAt = tick;
					} else if (tick > 1200) {
						end("setup: no village train was put together in 1200 ticks: " + why());
					}
					return;
				}
				if (!holdFar && unloadedAt < 0) {
					if (!level.isLoaded(l.farStation)) {
						unloadedAt = tick;
						if (!expect.isEmpty() && stops().contains(expect)) {
							end("setup: the stop followed '" + expect + "' before the far village unloaded (a hall looked in between): " + stops());
						}
					} else if (tick - startedAt > 600) {
						end("setup: the far village never unloaded");
					}
					return; // the near hall looks again only once the far village is gone
				}
				String result = check.get();
				if (result != null) {
					end(result);
				}
			});
		}

		/**
		 * B1125: why no train was put together, for the setup's failure: each station as Create knows it, whether they're
		 * on one network, whether the train's blocks stand (or have room), every train with the pair's owner, who would
		 * drive it, and any open site of the hall's.
		 */
		String why() {
			StringBuilder out = new StringBuilder();
			for (BlockPos hall : List.of(l.near, l.far)) {
				out.append(hall.equals(l.near) ? "near" : "far").append(" hall ").append(hall.toShortString()).append(" loaded ").append(level.isLoaded(hall))
					.append(", stations ").append(data.stations(hall)).append("; ");
			}
			for (BlockPos pos : List.of(l.nearStation, l.farStation)) {
				out.append(pos.equals(l.nearStation) ? "near" : "far").append(" station ").append(pos.toShortString()).append(": ");
				if (!(level.getBlockEntity(pos) instanceof StationBlockEntity be)) {
					out.append("no station block (").append(level.getBlockState(pos)).append("); ");
					continue;
				}
				var station = be.getStation();
				out.append("track ").append(be.edgePoint.hasValidTrack()).append(", ")
					.append(station == null ? "unknown to Create" : "'" + station.name + "', present train "
						+ (station.getPresentTrain() == null ? "-" : station.getPresentTrain().id + " owner " + station.getPresentTrain().owner))
					.append("; ");
			}
			BlockPos builds = CaravanTrains.first(l.near, l.far) ? l.nearStation : l.farStation;
			BlockPos other = builds.equals(l.nearStation) ? l.farStation : l.nearStation;
			CaravanTrains.Railway rw = CreateTrainCompatTests.railway();
			out.append("linked ").append(rw.linked(level, builds, other)).append(", built ").append(rw.built(level, builds, other))
				.append(", room ").append(rw.train(level, builds, other) != null).append(", owner's trains [");
			for (Train train : Create.RAILWAYS.trains.values()) {
				if (owner.equals(train.owner)) {
					out.append(train.id).append(" invalid ").append(train.invalid).append(" derailed ").append(train.derailed).append(' ');
				}
			}
			Villager driver = CaravanTrains.conductorFor(level, builds);
			out.append("], driver ").append(driver == null ? "none" : driver.blockPosition().toShortString())
				.append(", open sites ").append(io.github.jcondedata.aliveworkplace.city.Roads.openSegments(level, l.near).stream()
					.map(s -> s.structure().toString()).toList())
				.append(", trains on the railway ").append(Create.RAILWAYS.trains.size());
			return out.toString();
		}

		/** A name the stop must not follow before the far village has unloaded (else the case tests nothing far away). */
		String expect = "";

		/**
		 * Whether neither hall's own tick looks at the train (CaravanTrains.tick, every 10 ticks by its position) in the next
		 * few ticks, the time the far village takes to unload: a rename done now is first seen with the far village gone.
		 */
		boolean quiet() {
			for (BlockPos hall : List.of(l.near, l.far)) {
				int phase = Math.floorMod(level.getGameTime() + hall.hashCode(), 10);
				if (phase == 0 || phase >= 6) {
					return false;
				}
			}
			return true;
		}

		/** Ticks since the far village unloaded (or, held, since the train was put together). */
		int elapsed() {
			return tick - (unloadedAt >= 0 ? unloadedAt : startedAt);
		}

		void serveAll() {
			CaravanTrains.serve(level, l.near, CreateTrainCompatTests.hall(level, l.near));
			CaravanTrains.serve(level, l.far, CreateTrainCompatTests.hall(level, l.far));
		}

		/** Every 10 ticks, every hall that is loaded looks at its train, as its own tick does. */
		void serveLoaded() {
			if (tick % 10 == 0) {
				for (BlockPos hall : List.of(l.near, l.far)) {
					if (level.isLoaded(hall)) {
						CaravanTrains.serve(level, hall, CreateTrainCompatTests.hall(level, hall));
					}
				}
			}
		}

		void force(boolean on) {
			for (long[] ch : forced) {
				level.setChunkForced((int) ch[0], (int) ch[1], on);
			}
		}

		boolean at(BlockPos station) {
			return CreateTrainCompatTests.railway().at(level, owner, station);
		}

		String status() {
			return CreateTrainCompatTests.railway().status(level, owner);
		}

		UUID trainId() {
			for (Train train : Create.RAILWAYS.trains.values()) {
				if (owner.equals(train.owner) && !train.invalid) {
					return train.id;
				}
			}
			return null;
		}

		/** The station names on the village train's Create schedule, in order (empty without the train). */
		List<String> stops() {
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

		/** Today's caravan from the near village (64 logs) to the far one (wants 32), sent now that the train runs. */
		void sendNearToFar() {
			send(l.near, "Ashford", l.far, "Bramble");
		}

		/** Today's caravan from the far village (64 logs) to the near one (wants 32). */
		void sendFarToNear() {
			send(l.far, "Bramble", l.near, "Ashford");
		}

		private void send(BlockPos from, String fromName, BlockPos to, String toName) {
			data.clearRoad(l.near);
			data.clearRoad(l.far);
			((Container) level.getBlockEntity(from.east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			((Container) level.getBlockEntity(to.east(3).north(2))).clearContent();
			data.setWants(from, Component.literal(fromName), List.of());
			data.setWants(to, Component.literal(toName), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			data.sent(from, -1);
			Caravans.round(level, from, null);
			helper.assertTrue(data.onTheRoad().stream().anyMatch(x -> x.from().equals(from) && x.to().equals(to) && x.byTrain()),
				"setup: no caravan by train went out: " + data.onTheRoad());
		}

		void end(String result) {
			CreateTrainCompatTests.railway().scrap(level, owner);
			CreateTrainCompatTests.tidy(level, v);
			untrack(l);
			force(false);
			if (result.isEmpty()) {
				helper.succeed();
			} else {
				helper.fail(result);
			}
		}
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
