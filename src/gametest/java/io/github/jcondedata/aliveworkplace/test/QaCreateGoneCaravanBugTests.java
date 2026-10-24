package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-24), a bug test: a world saved while a caravan waited at its station for the village's Create train
 * ({@code BOARDING}) or rode it with its goods as its record ({@code ABOARD}), opened again with trains no longer running
 * (Create taken out of the pack, as in this suite, or config {@code villageTrains} switched off). The spec (39.7, and
 * {@code CaravanTrains.serve}): "Without the train, caravans waiting for it take the timed trip". Both stations are still on
 * the saved list (nothing notes them again without trains), so no hall ever retimes the two caravans and their goods
 * never arrive.
 */
public class QaCreateGoneCaravanBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(5, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos STORE_B = new BlockPos(16, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	//$ gametest_ticks_batch AREA '2400' '"qa_create_gone_caravan"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_create_gone_caravan")
	public void aCaravanSavedAtTheStationArrivesWhenTrainsNoLongerRun(GameTestHelper helper) throws ReflectiveOperationException {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.assertTrue(!CaravanTrains.live(), "setup: trains run in the plain suite");
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		int every = VillageNeeds.CHECK_EVERY;
		boolean sights = CaravanSights.ENABLED;
		VillageHalls.RADIUS = 8;
		Caravans.MIN_TRAVEL = 0;
		VillageNeeds.CHECK_EVERY = Integer.MAX_VALUE; // only the rounds this test runs
		CaravanSights.ENABLED = false;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			VillageNeeds.CHECK_EVERY = every;
			CaravanSights.ENABLED = sights;
			data.remove(a);
			data.remove(b);
			data.clearRoad(a);
			data.clearRoad(b);
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_B, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		Container theirs = helper.getBlockEntity(CHEST_B);
		// The save: both villages on the list with the stations noted while Create ran, and two caravans for Ashford by train
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 32)));
		data.setStations(a, List.of(a.east(4)));
		data.setStations(b, List.of(b.west(4)));
		java.lang.reflect.Method ship = Caravans.Data.class.getDeclaredMethod("ship", Caravans.Shipment.class);
		ship.setAccessible(true);
		ship.invoke(data, new Caravans.Shipment(a, b, List.of(new ItemStack(Items.BREAD, 16)), CaravanTrains.BOARDING, List.of(), false, true));
		ship.invoke(data, new Caravans.Shipment(a, b, List.of(new ItemStack(Items.BREAD, 16)), CaravanTrains.ABOARD, List.of(), false, true));
		// Both halls do their rounds as the days go by (every 100 ticks here)
		for (int t = 100; t < 2300; t += 100) {
			helper.runAfterDelay(t, () -> {
				Caravans.round(level, a, null);
				Caravans.round(level, b, null);
			});
		}
		helper.succeedWhen(() -> {
			List<Caravans.Shipment> left = data.onTheRoad().stream().filter(s -> s.to().equals(b)).toList();
			helper.assertTrue(theirs.countItem(Items.BREAD) == 32, "Ashford got " + theirs.countItem(Items.BREAD)
				+ " of the 32 bread; still on the road: " + left.stream().map(s -> s.goods() + " arrives "
				+ (s.arrives() == CaravanTrains.BOARDING ? "BOARDING" : s.arrives() == CaravanTrains.ABOARD ? "ABOARD" : String.valueOf(s.arrives()))).toList());
		});
	}
}
