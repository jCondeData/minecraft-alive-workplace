package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1026-1614): a world saved while a caravan was stepping through the waystones (41.3) is opened again with
 * Waystones taken off the server (as in this suite). 41.3: "if either stone is missing or broken, they walk as today".
 * The caravan's "by waystone" mark survives the save; the receiving village's round finds no link, the caravan walks the
 * road instead (its chronicle says so), and all its goods arrive, once.
 */
public class QaWaystonesGoneGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(5, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos STORE_B = new BlockPos(16, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	//$ gametest_ticks_batch AREA '2400' '"qa_waystones_gone_caravan"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_waystones_gone_caravan")
	public void aCaravanSavedMidWaystoneTripWalksAndArrivesWhenWaystonesIsGone(GameTestHelper helper) throws ReflectiveOperationException {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.assertTrue(!VillageWaystones.EXTENSION.present(), "setup: Waystones is in the plain suite");
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
			VillageWaystones.Data.get(level).forget(a);
			VillageWaystones.Data.get(level).forget(b);
			// Leave no village behind: tests that look 64-80 blocks around them count halls and stores (and this bread)
			for (BlockPos at : List.of(CHEST_A, CHEST_B, STORE_A, STORE_B, HALL_A, HALL_B)) {
				if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof Container c) {
					c.clearContent();
				}
				helper.setBlock(at, Blocks.AIR);
			}
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_B, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		Container theirs = helper.getBlockEntity(CHEST_B);

		// The save: both villages on the list and a caravan of 24 bread to Ashford by waystone, due a little later
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 24)));
		long due = level.getGameTime() + 150;
		java.lang.reflect.Method ship = Caravans.Data.class.getDeclaredMethod("ship", Caravans.Shipment.class);
		ship.setAccessible(true);
		ship.invoke(data, new Caravans.Shipment(a, b, List.of(new ItemStack(Items.BREAD, 24)), due, List.of(), false, false, true));

		// The save and reload keep it a waystone trip, with its goods and time
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		List<Caravans.Shipment> reloaded = Caravans.Data.load(tag, level.registryAccess()).onTheRoad().stream().filter(s -> s.to().equals(b)).toList();
		helper.assertTrue(reloaded.size() == 1 && reloaded.get(0).byWaystone() && !reloaded.get(0).byTrain()
				&& reloaded.get(0).arrives() == due && reloaded.get(0).goods().get(0).getCount() == 24,
			"the save changed the waystone caravan: " + reloaded);

		for (int t = 100; t < 2300; t += 100) {
			helper.runAfterDelay(t, () -> {
				Caravans.round(level, a, null);
				Caravans.round(level, b, null);
			});
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(data.onTheRoad().stream().noneMatch(s -> s.to().equals(b)), "still on the road: "
				+ data.onTheRoad().stream().filter(s -> s.to().equals(b)).map(s -> s.goods() + "@" + s.arrives() + (s.byWaystone() ? " by waystone" : "")).toList());
			helper.assertTrue(theirs.countItem(Items.BREAD) == 24, "Ashford got " + theirs.countItem(Items.BREAD) + " of the 24 bread");
			VillageHallBlockEntity ashford = helper.getBlockEntity(HALL_B);
			List<String> walked = ashford.chronicle().stream().map(Chronicle.Entry::text)
				.filter(c -> c.getContents() instanceof TranslatableContents k && k.getKey().equals("chronicle.aliveworkplace.waystone_walked"))
				.map(Component::getString).toList();
			helper.assertTrue(walked.size() == 1 && walked.get(0).equals("The caravan from " + VillageHalls.name(level, a).getString() + " found a waystone broken and is walking the road instead"),
				"Ashford's chronicle on the walk: " + walked);
		});
	}
}
