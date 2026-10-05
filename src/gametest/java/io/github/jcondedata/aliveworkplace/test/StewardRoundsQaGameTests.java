package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** QA (B62, ROADMAP 27.5 from its spec): a Steward walks his morning rounds, one stop per zone, and comes back to the hall. */
public class StewardRoundsQaGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A morning with a two-zone plan: he holds the plan on his rounds, finishes them, and is back at the hall without it. */
	//$ gametest_ticks_batch AREA '1800' '"stewardRoundsQa"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "stewardRoundsQa")
	public void theStewardWalksHisRoundsAndComesBack(GameTestHelper helper) {
		rounds(helper, false);
	}

	/** The same with a fence post standing on each zone's spot, as a player marks a zone (the showcase's staging). */
	//$ gametest_ticks_batch AREA '1800' '"stewardRoundsPostsQa"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "stewardRoundsPostsQa")
	public void theStewardWalksRoundsPastFencePosts(GameTestHelper helper) {
		rounds(helper, true);
	}

	private static void rounds(GameTestHelper helper, boolean posts) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 2500L); // a working morning
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		CityPlan plan = CityPlan.EMPTY;
		BlockPos[] spots = {hall.offset(9, 0, -6), hall.offset(-9, 0, -6)};
		for (int i = 0; i < spots.length; i++) {
			BitSet cells = new BitSet();
			cells.set(CityPlan.cellAt(hall, spots[i]));
			plan = plan.addZone("homes", "Zone " + i, "").paint(i, cells);
			if (posts) {
				level.setBlockAndUpdate(spots[i], net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());
			}
		}
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setPlan(plan);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, cityPlan, hall);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		helper.assertTrue(Stewards.appoint(player, steward, cityPlan) == InteractionResult.SUCCESS, "not appointed");
		helper.assertTrue(StewardWork.stops(level, steward, hall).size() == 2, "stops: " + StewardWork.stops(level, steward, hall).size());
		boolean[] heldPlan = {false};
		helper.onEachTick(() -> heldPlan[0] |= StewardWork.holdsPlan(steward));
		helper.succeedWhen(() -> {
			helper.assertTrue(steward.isAlive(), "the steward died");
			helper.assertTrue(StewardWork.roundDone(level, steward), "round not done; at " + steward.blockPosition().subtract(hall)
				+ ", held the plan: " + heldPlan[0]);
			helper.assertTrue(heldPlan[0], "never held the City Plan on his rounds");
			helper.assertTrue(steward.distanceToSqr(hall.getCenter()) < 16, "not back at the hall: " + steward.blockPosition().subtract(hall));
			helper.assertTrue(!StewardWork.holdsPlan(steward), "still holds the plan at the hall");
		});
	}
}
