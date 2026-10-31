package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * QA (lane E, Create, 2026-10-27), from the wiki's Create page (39.7a): the village train's owner is
 * {@code CaravanTrains.railwayId}, "which is how the village finds its own train and never touches a player's". A
 * player's own Create train (a train whose owner is the player) parked at the village's station, with a caravan waiting
 * to go: the village never loads the caravan's goods into it, never empties it, never scraps it or gives it a schedule,
 * and the caravan, with no village train, takes the timed trip.
 */
public class QaPlayersTrainCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '1600' '"qa_players_train"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_players_train")
	public void aPlayersTrainParkedAtTheVillageStationIsNeverTouched(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		UUID player = UUID.randomUUID();
		UUID village = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = first.equals(v.a()) ? v.b() : v.a();
		boolean[] parked = {false};
		boolean[] sent = {false};
		int[] tick = {0};
		int[] after = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
			CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			List<BlockPos> ours = v.data().stations(first);
			List<BlockPos> theirs = v.data().stations(other);
			if (!parked[0]) {
				if (ours.isEmpty() || theirs.isEmpty() || !r.linked(level, ours.get(0), theirs.get(0))) {
					if (tick[0] > 400) {
						helper.fail("the stations never joined one network");
					}
					return;
				}
				// the player builds a train of their own at the village's station and parks it there (no schedule)
				java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> parts = r.train(level, ours.get(0), theirs.get(0));
				helper.assertTrue(parts != null, "setup: no room for a train at the station");
				parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
				Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
				helper.assertTrue(r.assemble(level, ours.get(0), theirs.get(0), player, driver) != null, "setup: the player's train didn't assemble");
				Train train = train(player);
				helper.assertTrue(train != null, "setup: no train owned by the player");
				train.runtime.discardSchedule();
				helper.assertTrue(r.load(level, player, List.of(new ItemStack(Items.DIAMOND, 3))), "setup: the player's diamonds didn't go into their train's chest");
				parked[0] = true;
				CreateTrainCompatTests.sendAgain(level, v);
				return;
			}
			Train train = train(player);
			helper.assertTrue(train != null && !train.invalid, "the player's train is gone (scrapped by the village?) at tick " + tick[0]);
			helper.assertTrue(train.runtime.getSchedule() == null, "the village gave the player's train a schedule: " + train.runtime.getSchedule());
			List<ItemStack> cargo = r.cargo(level, player);
			helper.assertTrue(cargo.stream().noneMatch(s -> s.is(Items.OAK_LOG)), "the caravan's logs went into the player's train: " + cargo);
			helper.assertTrue(cargo.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 3,
				"the player's diamonds were taken out of their train: " + cargo);
			helper.assertTrue(!CaravanTrains.realTrain(level, v.a(), v.b()), "a village train was put together at a station the player's train stands in");
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (!sent[0]) {
				if (s != null && !CaravanTrains.waiting(s.arrives()) && !CaravanTrains.aboard(s.arrives())) {
					helper.assertTrue(s.arrives() > level.getGameTime() && s.goods().stream().filter(g -> g.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum() == 32,
						"the caravan didn't take the timed trip with its 32 logs: " + s);
					sent[0] = true;
				} else if (tick[0] > 1200) {
					helper.fail("the caravan still waits for a train that can't come: " + s);
				}
				return;
			}
			if (++after[0] >= 20) { // 200 more ticks of the halls' looks: still untouched
				r.scrap(level, player);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			}
		});
	}

	@Nullable
	private static Train train(UUID owner) {
		for (Train train : Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				return train;
			}
		}
		return null;
	}
}
