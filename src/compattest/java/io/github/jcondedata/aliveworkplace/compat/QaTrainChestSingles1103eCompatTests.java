package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create, 2026-11-03), 39.7b: "the goods into its chest ... and gives them back". A caravan's goods are
 * often a single item (one emerald, one saddle): the village train's chest must give back a stack of one as surely as
 * a stack of 32, and leave nothing behind. Written from the spec against the local mutation run on
 * CreateTrains.unload (lines 355-400).
 */
public class QaTrainChestSingles1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);

	/** One emerald, one log and 63 wheat go into the train's chest, ride to the other station and come back out exactly. */
	//$ gametest_ticks_batch AREA '1200' '"qaTrainChestSingles1103e"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaTrainChestSingles1103e")
	public void singleItemsRideTheTrainsChestAndAllComeBackOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		UUID owner = UUID.randomUUID();
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		boolean[] built = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (!built[0]) {
				boolean aimed = r.aim(level, a, b, "Alpha") & r.aim(level, b, a, "Beta");
				if (aimed && r.linked(level, a, b)) {
					java.util.Map<BlockPos, BlockState> parts = r.train(level, a, b);
					helper.assertTrue(parts != null, "setup: no room for the train's blocks past the station");
					parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
					helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "setup: the station didn't assemble the village train");
					built[0] = true;
					helper.assertTrue(r.load(level, owner, List.of(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.OAK_LOG, 1),
						new ItemStack(Items.WHEAT, 63))), "one emerald, one log and 63 wheat didn't go into the empty train's chest");
					List<ItemStack> held = r.cargo(level, owner);
					helper.assertTrue(count(held, Items.EMERALD) == 1 && count(held, Items.OAK_LOG) == 1 && count(held, Items.WHEAT) == 63,
						"the train's chest doesn't hold one emerald, one log and 63 wheat: " + held);
				} else if (tick[0] > 200) {
					helper.fail("Create's railway doesn't know both stations on one network after 200 ticks (aimed " + aimed + ")");
				}
			} else if (tick[0] == 1100) {
				helper.fail("the train didn't reach the other station: " + r.status(level, owner));
			} else if (r.at(level, owner, b)) {
				List<ItemStack> out = r.unload(level, owner, List.of(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.OAK_LOG, 1),
					new ItemStack(Items.WHEAT, 63)));
				helper.assertTrue(count(out, Items.EMERALD) == 1, "the lone emerald didn't come out of the train's chest: " + out
					+ ", left in it " + r.cargo(level, owner));
				helper.assertTrue(count(out, Items.OAK_LOG) == 1, "the lone log didn't come out of the train's chest: " + out
					+ ", left in it " + r.cargo(level, owner));
				helper.assertTrue(count(out, Items.WHEAT) == 63, "63 wheat went in, " + count(out, Items.WHEAT) + " came out: " + out);
				helper.assertTrue(r.cargo(level, owner).isEmpty(), "goods stayed in the train's chest: " + r.cargo(level, owner));
				List<ItemStack> again = r.unload(level, owner, List.of(new ItemStack(Items.EMERALD, 1)));
				helper.assertTrue(again.isEmpty(), "the emptied chest gave out more: " + again);
				r.scrap(level, owner);
				helper.succeed();
			}
		});
	}

	private static int count(List<ItemStack> stacks, Item item) {
		return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
