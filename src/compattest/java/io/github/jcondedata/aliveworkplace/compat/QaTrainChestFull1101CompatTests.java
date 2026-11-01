package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, Create, 2026-11-01), 39.7b: "the goods into its chest, or as the caravan's record when they don't fit".
 * The village train's chest is already full (a player filled it, or it holds what a broken trip left) when a caravan
 * boards: the caravan's goods can't go in, so they ride as its record, and they arrive all the same, once: the two
 * villages' chests hold the 64 logs they started with, never more, and what was in the train's chest is still there,
 * never taken for the caravan's goods. The logs in the full chest are the same item as the caravan's, so a train that
 * paid the caravan out of the chest as well as from its record would show 32 logs too many, and one that paid it out
 * of the chest alone would leave the chest short.
 */
public class QaTrainChestFull1101CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The train's chest filled with whole stacks of logs before the caravan boards: it rides as its record and arrives. */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainChestFull1101"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainChestFull1101")
	public void aCaravanWhoseGoodsDontFitTheFullTrainChestStillArrivesOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		Container chestA = (Container) level.getBlockEntity(v.a().east(3).north(2));
		Container chestB = (Container) level.getBlockEntity(v.b().east(3).north(2));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		boolean[] aboard = {false};
		int[] filled = {0};
		List<Long> seen = new ArrayList<>();
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			Caravans.Data data = v.data();
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					// Fill the train's chest to the brim: whole stacks of logs until no more go in.
					for (int i = 0; i < 200 && CreateTrainCompatTests.railway().load(level, owner, List.of(new ItemStack(Items.OAK_LOG, 64))); i++) {
						filled[0] += 64;
					}
					while (filled[0] < 64 * 200 && CreateTrainCompatTests.railway().load(level, owner, List.of(new ItemStack(Items.OAK_LOG, 1)))) {
						filled[0]++;
					}
					helper.assertTrue(filled[0] >= 64, "the train's chest took no logs at all: " + CreateTrainCompatTests.railway().cargo(level, owner));
					helper.assertTrue(!CreateTrainCompatTests.railway().load(level, owner, List.of(new ItemStack(Items.OAK_LOG, 32))),
						"the train's chest still takes 32 logs after it was filled with " + filled[0]);
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain() && CaravanTrains.waiting(s.arrives()), "the caravan doesn't wait for the village train: " + s);
					sent[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && (seen.isEmpty() || seen.get(seen.size() - 1) != s.arrives())) {
				seen.add(s.arrives());
			}
			if (s != null) {
				helper.assertTrue(s.arrives() != CaravanTrains.IN_CHEST,
					"the caravan's goods are marked as in the train's chest, which was full: " + s + ", chest " + CreateTrainCompatTests.railway().cargo(level, owner));
				if (s.arrives() == CaravanTrains.ABOARD) {
					aboard[0] = true;
					helper.assertTrue(s.goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "the caravan aboard doesn't carry its 32 logs: " + s.goods());
				}
			}
			if (tick[0] == 2300) {
				helper.fail("the goods didn't arrive: caravan " + s + ", arrivals " + seen + ", train: " + CreateTrainCompatTests.railway().status(level, owner));
			}
			if (s == null) {
				int logs = chestA.countItem(Items.OAK_LOG) + chestB.countItem(Items.OAK_LOG);
				int inTrain = CreateTrainCompatTests.railway().cargo(level, owner).stream().filter(st -> st.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
				helper.assertTrue(aboard[0], "the caravan never rode the train: arrivals " + seen);
				helper.assertTrue(logs == 64, "the two villages' chests hold " + logs + " logs, not the 64 they started with (" + chestB.countItem(Items.OAK_LOG)
					+ " at the receiving end); the train's chest holds " + inTrain + " of the " + filled[0] + " put in");
				helper.assertTrue(inTrain == filled[0], "the full train's chest holds " + inTrain + " logs after the trip, not the " + filled[0] + " it was filled with");
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			}
		});
	}
}
