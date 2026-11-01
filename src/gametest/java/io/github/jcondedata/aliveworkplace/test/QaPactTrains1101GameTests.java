package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1101-1913), B768 on the Create-train and waystone legs of a caravan's trip: "a caravan that set out under a
 * pact is still paid at the pact's terms when it arrives". A caravan by the village's train is rebuilt at every stage
 * (retimed at the station, boarding, its goods out of the carriage's chest, recovered from a train that's gone, put back
 * on the road when trains stop, walking the rest when the station is broken, off the waystones when a stone is gone, and
 * going on when its home village's hall is taken away). Each rebuild is the same trip, so a caravan marked when the pact
 * ended must still be marked after it; a dropped mark hands its cargo over free on arrival.
 */
public class QaPactTrains1101GameTests implements FabricGameTest {
	private static final BlockPos A = new BlockPos(10_000, 64, 10_000);
	private static final BlockPos B = new BlockPos(10_400, 64, 10_000);
	private static final long NOW = 1_000;

	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aMarkedCaravanKeepsItsMarkThroughEveryLegOfATrainOrWaystoneTrip(GameTestHelper helper) {
		List<String> lost = new ArrayList<>();
		// 39.7a: retimed at the station
		leg(helper, lost, "retimed at the station", train(CaravanTrains.BOARDING), d -> d.retime(A, B, CaravanTrains.BOARDING, NOW + 500));
		// 39.7b: boards with its goods in the carriage's chest, and as the caravan's record when there's no room
		leg(helper, lost, "boarding into the chest", train(CaravanTrains.BOARDING), d -> d.board(A, B, goods -> true));
		leg(helper, lost, "boarding as a record", train(CaravanTrains.BOARDING), d -> d.board(A, B, goods -> false));
		// 39.7b: the train comes in and its chest is unloaded
		leg(helper, lost, "unloaded at the station", train(CaravanTrains.IN_CHEST), d -> d.unload(A, B, NOW, goods -> goods));
		// 39.7b: the train is gone or derailed; what its chest still holds goes on with the caravan
		leg(helper, lost, "recovered from a lost train", train(CaravanTrains.IN_CHEST), d -> d.recover(A, B, NOW + 300, goods -> goods));
		// B359: trains stop (Create removed, villageTrains off): the timed trip instead, from either end
		leg(helper, lost, "off the trains (sender's side)", train(CaravanTrains.BOARDING), d -> d.offTheTrains(A, s -> NOW + 300, Caravans.Shipment::goods));
		leg(helper, lost, "off the trains (receiver's side)", train(CaravanTrains.IN_CHEST), d -> d.offTheTrains(B, s -> NOW + 300, Caravans.Shipment::goods));
		// 39.7: the receiver's station broken while the train is on its way: the caravan walks the rest
		leg(helper, lost, "walking the rest (aboard)", train(CaravanTrains.ABOARD),
			d -> call(d, "walkTheRest", new Class<?>[]{BlockPos.class, long.class, ToLongFunction.class}, B, NOW, (ToLongFunction<Caravans.Shipment>) s -> 200L));
		leg(helper, lost, "walking the rest (never boarded)", train(CaravanTrains.BOARDING),
			d -> call(d, "walkTheRest", new Class<?>[]{BlockPos.class, long.class, ToLongFunction.class}, B, NOW, (ToLongFunction<Caravans.Shipment>) s -> 200L));
		// 41.3: a waystone at either end isn't standing: it walks the whole road
		leg(helper, lost, "off the waystones",
			new Caravans.Shipment(A, B, cargo(), NOW - 1, List.of(), false, false, true),
			d -> call(d, "offTheWaystones", new Class<?>[]{BlockPos.class, long.class, Predicate.class, ToLongFunction.class},
				B, NOW, (Predicate<Caravans.Shipment>) s -> false, (ToLongFunction<Caravans.Shipment>) s -> 200L));
		// B214: the sender's hall taken away while the caravan waits at its station: it gets off and walks on to the receiver
		leg(helper, lost, "its home hall taken away at the station", train(CaravanTrains.BOARDING),
			d -> call(d, "remove", new Class<?>[]{BlockPos.class, long.class, Function.class, ToLongFunction.class},
				A, NOW, (Function<Caravans.Shipment, List<ItemStack>>) Caravans.Shipment::goods, (ToLongFunction<Caravans.Shipment>) s -> 200L));
		helper.assertTrue(lost.isEmpty(), "a caravan that set out under the ended pact lost its mark (its cargo would be handed over free): " + String.join("; ", lost));
		helper.succeed();
	}

	/** One leg: a fresh road with {@code shipment} from A to B, the pact between them ends (A's gifts off), then {@code step}. */
	private static void leg(GameTestHelper helper, List<String> lost, String what, Caravans.Shipment shipment, Consumer<Caravans.Data> step) {
		Caravans.Data data = Caravans.Data.load(new CompoundTag(), helper.getLevel().registryAccess());
		ship(data, shipment);
		helper.assertTrue(data.pactEnded(A, B) == 1, "setup (" + what + "): the caravan wasn't marked when the pact ended");
		step.accept(data);
		List<Caravans.Shipment> road = data.onTheRoad().stream().filter(s -> s.from().equals(A) && s.to().equals(B)).toList();
		helper.assertTrue(road.size() == 1, "setup (" + what + "): the leg left " + road.size() + " caravans from A to B: " + data.onTheRoad());
		Caravans.Shipment after = road.get(0);
		if (!after.pact()) {
			lost.add(what + " (" + after + ")");
		}
		// and a save and load after the leg
		Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
		if (after.pact() && loaded.onTheRoad().stream().noneMatch(s -> s.from().equals(A) && s.to().equals(B) && s.pact())) {
			lost.add(what + ", then a save and load");
		}
	}

	private static Caravans.Shipment train(long arrives) {
		return new Caravans.Shipment(A, B, cargo(), arrives, List.of(), false, true, false);
	}

	private static List<ItemStack> cargo() {
		return List.of(new ItemStack(Items.WHEAT, 20));
	}

	private static void call(Caravans.Data data, String name, Class<?>[] types, Object... args) {
		try {
			Method m = Caravans.Data.class.getDeclaredMethod(name, types);
			m.setAccessible(true);
			m.invoke(data, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(name + ": " + e, e);
		}
	}

	private static void ship(Caravans.Data data, Caravans.Shipment shipment) {
		call(data, "ship", new Class<?>[]{Caravans.Shipment.class}, shipment);
	}
}
