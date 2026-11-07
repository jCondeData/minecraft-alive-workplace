package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, Create, 2026-11-07; B933's verification, 39.7a). B933's fix sends a village train standing at a namesake
 * of its stop on to the stop's own station. Its own tests cover two stations named alike with the train standing at one
 * of them. These go at it from the spec (B900: "whatever a player calls them", the 32 logs ride to the other village and
 * arrive, every run) from other sides:
 * <ul>
 * <li>a player adds a halt halfway along the line and names all three stations "Station": the nearest namesake on the
 * way out is the halt, not either village's station, so the train stops at a station that is neither of its stops;</li>
 * <li>the halfway halt given the other village's own station name, the villages' stations left as the villages named
 * them;</li>
 * <li>only the sending village's station renamed to the other's name (a one-sided namesake), with Create's outcome of
 * taking the station it stands at as the stop forced, so the bad case happens every run.</li>
 * </ul>
 * The line and villages are {@link CreateTrainCompatTests}'s; the halt sits where {@link QaTrainSameNames1103eCompatTests}
 * puts its middle station.
 */
public class QaTrainNamesakes1107eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_MID = new BlockPos(15, 2, 13);
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);

	/** A halt halfway along the line, and all three stations named "Station". */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainNamesakeHalt1107e"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainNamesakeHalt1107e")
	public void aHaltHalfwayAndEveryStationNamedStationStillCarryTheCaravan1107e(GameTestHelper helper) {
		run(helper, true, "all three (with a halt halfway) named 'Station'", (h, t) -> {
			rename(h, STATION_A, "Station");
			rename(h, STATION_MID, "Station");
			rename(h, STATION_B, "Station");
		});
	}

	/** A halt halfway along the line given the other village's own station name. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainNamesakeHaltCopy1107e"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainNamesakeHaltCopy1107e")
	public void aHaltHalfwayNamedLikeTheOtherVillagesStationStillCarriesTheCaravan1107e(GameTestHelper helper) {
		run(helper, true, "the halt named like the other village's station", (h, t) -> {
			String b = QaTrainStationNames1102CompatTests.name(h.getLevel(), h.absolutePos(STATION_B));
			h.assertTrue(b != null && !b.startsWith("("), "setup: the other village's station has no name: " + b);
			rename(h, STATION_MID, b);
		});
	}

	/**
	 * Only the sending village's station renamed to the other's name, while the train stands there, and Create's outcome
	 * forced: the stop for the other station counted as reached where it stands.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainNamesakeOneSided1107e"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainNamesakeOneSided1107e")
	public void onlyTheHomeStationGivenTheOthersNameStillCarriesTheCaravan1107e(GameTestHelper helper) {
		run(helper, false, "the sending village's station named like the other's", (h, t) -> {
			String b = QaTrainStationNames1102CompatTests.name(h.getLevel(), h.absolutePos(STATION_B));
			h.assertTrue(b != null && !b.startsWith("("), "setup: the other village's station has no name: " + b);
			if (t != null) {
				arrivedAtTheNamesake(h, t, b);
			}
			rename(h, STATION_A, b);
		});
	}

	private static void rename(GameTestHelper helper, BlockPos which, String name) {
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(which);
		helper.assertTrue(be.getStation() != null, "setup: the station at " + which + " isn't a station Create knows");
		be.updateName(name); // what Create's station screen does when a player types a name
	}

	/**
	 * The halt aimed at the line as a player's placing aims it (Create's station item sets its target track): the track
	 * beside it, facing the way the other village's station does (both stand beside the line on the same side).
	 */
	private static void aimLikeTheOther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StationBlockEntity other = (StationBlockEntity) helper.getBlockEntity(STATION_B);
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(STATION_MID);
		net.minecraft.nbt.CompoundTag from = other.saveWithoutMetadata(level.registryAccess());
		net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.put("TargetTrack", from.get("TargetTrack").copy());
		tag.putBoolean("TargetDirection", from.getBoolean("TargetDirection"));
		be.loadWithComponents(tag, level.registryAccess());
		be.setChanged();
		be.notifyUpdate();
	}

	private static boolean known(GameTestHelper helper, BlockPos which) {
		return helper.getBlockEntity(which) instanceof StationBlockEntity be && be.getStation() != null;
	}

	@org.jetbrains.annotations.Nullable
	private static Train train(UUID owner) {
		for (Train t : Create.RAILWAYS.trains.values()) {
			if (owner.equals(t.owner) && !t.invalid) {
				return t;
			}
		}
		return null;
	}

	/** Create's outcome when the nearest station of the stop's name is the one the train stands at. */
	private static void arrivedAtTheNamesake(GameTestHelper helper, Train t, String away) {
		int entry = -1;
		for (int i = 0; i < t.runtime.getSchedule().entries.size(); i++) {
			if (t.runtime.getSchedule().entries.get(i).instruction instanceof DestinationInstruction to && to.getData().getString("Text").equals(away)) {
				entry = i;
			}
		}
		helper.assertTrue(entry >= 0, "setup: no stop of the schedule goes to '" + away + "'");
		t.runtime.currentEntry = entry;
		t.runtime.state = ScheduleRuntime.State.IN_TRANSIT;
		t.runtime.destinationReached();
	}

	/**
	 * {@code halt}: a station is added halfway along the line before the villages note theirs. {@code renames} runs once:
	 * with a halt, once the train is put together; without, in the tick the train is seen standing at the sending
	 * village's station (it gets the train then).
	 */
	private static void run(GameTestHelper helper, boolean halt, String what, BiConsumer<GameTestHelper, Train> renames) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		if (halt) {
			helper.setBlock(STATION_MID, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track_station")).defaultBlockState());
		}
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID train = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos home = helper.absolutePos(STATION_A);
		int[] tick = {0};
		int[] sentAt = {-1};
		boolean[] aboard = {false};
		boolean[] aimed = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (sentAt[0] < 0) {
				Train t = train(train);
				if (halt && !aimed[0] && known(helper, STATION_B)) {
					aimLikeTheOther(helper);
					aimed[0] = true;
				}
				boolean ready = CaravanTrains.realTrain(level, v.a(), v.b()) && t != null && (halt
					? known(helper, STATION_MID) : (t.runtime.state != ScheduleRuntime.State.IN_TRANSIT && CreateTrainCompatTests.railway().at(level, train, home)));
				if (ready) {
					renames.accept(helper, halt ? null : t);
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
					helper.assertFalse(CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came"),
						"setup: the other village's chronicle already says a train came before the caravan was sent");
					sentAt[0] = tick[0];
				} else if (tick[0] > 1600) {
					helper.fail("setup: no village train " + (halt ? "was put together" : "stood at the sending village's station")
						+ " in 1600 ticks: " + CreateTrainCompatTests.railway().status(level, train) + "; halt: " + helper.getBlockState(STATION_MID)
						+ " '" + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_MID)) + "'");
				}
				return;
			}
			int since = tick[0] - sentAt[0];
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				int there = ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, what + ": logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], what + ": the goods arrived without riding the train (" + since + " ticks after it was sent; "
					+ there + " logs at the other village)");
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3500) {
				helper.fail("with " + what + ", the caravan sent " + since + " ticks ago never arrived (aboard: " + aboard[0]
					+ "): " + s + "; train: " + CreateTrainCompatTests.railway().status(level, train) + "; stations '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_A)) + "', '"
					+ (halt ? QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_MID)) : "-") + "', '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_B)) + "'");
			}
		});
	}
}
