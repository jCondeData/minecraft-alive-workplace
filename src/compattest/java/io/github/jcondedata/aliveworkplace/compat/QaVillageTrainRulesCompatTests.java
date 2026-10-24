package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * QA for ROADMAP 39.7 from the player guide (docs/guide/create-trains.md), with the real Create: "It's quicker": a third
 * of the walk, never less than about twenty seconds (and never slower than walking), at each boundary from both sides;
 * "a road that ends at a milestone: caravans walk", with the two halves of the road meeting at the very reach that
 * counts as joined and one block beyond it; the railway kept over a save, and a save of the list with a station and
 * track at one village only loads as no railway.
 */
public class QaVillageTrainRulesCompatTests implements FabricGameTest {
	private static final BlockPos A = new BlockPos(1000, 70, 1000);
	private static final BlockPos B = new BlockPos(1100, 70, 1000);

	/** A fresh caravans' list (not the level's) with Ashford and Bramble on a route, stations at both and track laid. */
	private static Caravans.Data railway(GameTestHelper helper, BlockPos endA, BlockPos endB, boolean milestone) {
		Caravans.Data data = Caravans.Data.load(new CompoundTag(), helper.getLevel().registryAccess());
		data.setWants(A, Component.literal("Ashford"), List.of());
		data.setWants(B, Component.literal("Bramble"), List.of());
		data.toggleRoute(A, B);
		data.setHalf(A, B, new Caravans.Half(endA, true, milestone, true));
		data.setHalf(B, A, new Caravans.Half(endB, true, false, true));
		data.setStations(A, List.of(A.offset(-5, 0, 2)));
		data.setStations(B, List.of(B.offset(-5, 0, 2)));
		data.setTracked(A, B, true);
		data.setTracked(B, A, true);
		return data;
	}

	/** A third of the walk; never under the twenty-second floor; never longer than the walk itself. Both sides of each. */
	//$ gametest_batch EMPTY_STRUCTURE '"qa_train_trip"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qa_train_trip")
	public void theTrainIsAThirdOfTheWalkNeverUnderTwentySecondsNorSlower(GameTestHelper helper) {
		long floor = CaravanTrains.TRAIN_MIN;
		helper.assertTrue(floor >= 380 && floor <= 420, "the floor isn't about twenty seconds: " + floor + " ticks");
		long[][] cases = {
			{9000, 3000}, // a long road: a third
			{floor * 3 + 3, floor + 1}, // just over the floor's walk: a third
			{floor * 3, floor}, // the floor's walk, exactly
			{floor * 3 - 3, floor}, // just under: held at the floor
			{floor + 1, floor}, // a walk barely longer than the floor: the floor
			{floor, floor}, // a walk as long as the floor: the same
			{floor - 1, floor - 1}, // a walk shorter than the floor: never slower than walking
			{60, 60},
		};
		for (long[] c : cases) {
			long trip = CaravanTrains.trainTrip(c[0]);
			helper.assertTrue(trip == c[1], "a walk of " + c[0] + " ticks goes by train in " + trip + ", not " + c[1]);
		}
		helper.succeed();
	}

	/** The halves meeting within the reach that joins them: a railway; one block further apart (a milestone's gap): walk. */
	//$ gametest_batch EMPTY_STRUCTURE '"qa_train_milestone"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qa_train_milestone")
	public void aRoadThatStopsAtAMilestoneIsNoRailway(GameTestHelper helper) {
		BlockPos meet = A.offset(50, 0, 0);
		Caravans.Data joined = railway(helper, meet, meet, false);
		helper.assertTrue(CaravanTrains.rides(joined, A, B) && CaravanTrains.rides(joined, B, A), "a finished, tracked road with stations is no railway");
		Caravans.Data reach = railway(helper, meet, meet.offset(Caravans.HALVES_MEET, 0, 0), false);
		helper.assertTrue(CaravanTrains.rides(reach, A, B), "halves " + Caravans.HALVES_MEET + " blocks apart (the reach that joins them) are no railway");
		Caravans.Data gap = railway(helper, meet, meet.offset(Caravans.HALVES_MEET + 1, 0, 0), false);
		helper.assertTrue(!CaravanTrains.rides(gap, A, B) && !CaravanTrains.rides(gap, B, A), "halves one block beyond the reach make a railway");
		Caravans.Data milestone = railway(helper, meet.offset(-20, 0, 0), meet, true);
		helper.assertTrue(!CaravanTrains.rides(milestone, A, B) && !CaravanTrains.rides(milestone, B, A),
			"a road whose half stops short at a milestone makes a railway");
		helper.succeed();
	}

	/** The railway comes back from a save; a list saved with the station and track at Ashford only loads as no railway. */
	//$ gametest_batch EMPTY_STRUCTURE '"qa_train_save"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qa_train_save")
	public void theRailwayIsKeptOverASaveAndHalfARailwayLoadsAsNone(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		BlockPos meet = A.offset(50, 0, 0);
		Caravans.Data data = railway(helper, meet, meet, false);
		Caravans.Data back = Caravans.Data.load(data.save(new CompoundTag(), registries), registries);
		helper.assertTrue(CaravanTrains.rides(back, A, B) && back.stations(A).equals(List.of(A.offset(-5, 0, 2)))
			&& back.stations(B).equals(List.of(B.offset(-5, 0, 2))) && back.tracked(A, B) && back.tracked(B, A),
			"the railway didn't come back from a save: stations " + back.stations(A) + " / " + back.stations(B));
		data.setStations(B, List.of());
		data.setTracked(B, A, false);
		Caravans.Data half = Caravans.Data.load(data.save(new CompoundTag(), registries), registries);
		helper.assertTrue(half.stations(B).isEmpty() && !half.tracked(B, A) && half.tracked(A, B) && !CaravanTrains.rides(half, A, B),
			"a railway with Bramble's station and track gone came back from a save as a railway");
		helper.succeed();
	}
}
