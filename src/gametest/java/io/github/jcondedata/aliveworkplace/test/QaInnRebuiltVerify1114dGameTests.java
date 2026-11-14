package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-14), B1213/B1214/B1215 verified from the spec: "a broken counter is forgotten (or a counter worked
 * by someone other than the remembered Innkeeper is no inn)", and "a Shop Counter inside the village that an Innkeeper
 * works is its inn wherever he stands". The fix's tests cover a counter rebuilt for a Shopkeeper while the old Innkeeper
 * is unloaded, and an Innkeeper never seen inside the village. These cover the other side of each boundary: the counter
 * rebuilt for the Innkeeper again is the inn again, a counter only turned (not broken) stays the inn, a broken counter
 * stays forgotten through a save, and the level-wide lookup tells an Innkeeper's counter from a Shopkeeper's when both
 * workers stand past the edge.
 */
public class QaInnRebuiltVerify1114dGameTests implements FabricGameTest {
	static final String AREA = QaInnCounters1111dGameTests.AREA;
	private static final BlockPos HALL = QaInnCounters1111dGameTests.HALL; // (15, 2, 2)
	private static final BlockPos INN = QaInnCounters1111dGameTests.INN; // (25, 2, 3)
	private static final BlockPos SHOP = new BlockPos(18, 2, 8); // 6.7 blocks from the Hall: inside the edge (12)
	private static final BlockPos AWAY = new BlockPos(15, 2, 27); // 25 blocks from the Hall: past the edge

	private static List<BlockPos> counters(GameTestHelper helper) {
		return Innkeepers.counters(helper.getLevel(), helper.absolutePos(HALL), VillageHalls.RADIUS);
	}

	private static boolean innRolls(GameTestHelper helper) {
		return LegendGuests.rollsOn(helper.getLevel(), helper.absolutePos(HALL), null, "inn", 1);
	}

	private static void start(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaInnCounters1111dGameTests.radius(helper, 12);
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		BlockPos shop = helper.absolutePos(SHOP);
		Leftovers.after(helper, () -> {
			InnCounters.get(level).forget(inn);
			InnCounters.get(level).forget(shop);
		});
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
	}

	/**
	 * The Innkeeper unloads, the player breaks his counter and puts it back, and the counter stands free: no inn. Then
	 * the Innkeeper (loaded again, still out past the edge) takes the new counter: it is the inn again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInnRebuiltVerify1114dBack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInnRebuiltVerify1114dBack")
	public void aCounterRebuiltForTheSameInnkeeperIsTheInnAgain(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		CompoundTag[] saved = new CompoundTag[1];
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, helper.absolutePos(HALL));
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, INN.south());
			helper.assertTrue(counters(helper).contains(inn), "setup: the Innkeeper's counter isn't the inn with him by it");
			QaMoveInnVerify1111cGameTests.sendAway(helper, ina);
			saved[0] = QaMoveInnVerify1111cGameTests.unload(ina);
			helper.assertTrue(counters(helper).contains(inn), "setup: the counter of an unloaded Innkeeper stopped being the inn");
			helper.destroyBlock(INN);
		});
		helper.runAfterDelay(5, () -> helper.setBlock(INN, ModBlocks.SHOP_COUNTER));
		helper.runAfterDelay(9, () -> {
			helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "a broken and rebuilt Shop Counter still remembers its old Innkeeper");
			helper.assertFalse(counters(helper).contains(inn), "a rebuilt Shop Counter nobody works is the inn: " + counters(helper));
			helper.assertFalse(innRolls(helper), "a legend rolls at a rebuilt Shop Counter nobody works");
			Villager ina = QaMoveInnVerify1111cGameTests.back(level, saved[0]);
			Jobs.employ(level, ina, inn, ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
			helper.assertTrue(counters(helper).contains(inn),
				"the Innkeeper took his rebuilt counter again (standing past the edge) and it is no inn: " + counters(helper));
			helper.assertTrue(innRolls(helper), "no legend rolls at the rebuilt inn its Innkeeper works again");
			ina.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}

	/** Turning the counter (the same block, another facing) is no break: the unloaded Innkeeper's counter stays the inn. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuiltVerify1114dTurned"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuiltVerify1114dTurned")
	public void aCounterTurnedButNotBrokenStaysTheInn(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, helper.absolutePos(HALL));
			UUID unloaded = UUID.randomUUID();
			InnCounters.get(level).remember(inn, unloaded);
			level.getPoiManager().take(h -> h.is(ModVillagers.SHOP_COUNTER_POI), (h, p) -> p.equals(inn), inn, 1);
			helper.assertTrue(counters(helper).contains(inn), "setup: the held counter of an unloaded Innkeeper isn't the inn");
			BlockState state = level.getBlockState(inn);
			Direction facing = state.getValue(ShopCounterBlock.FACING);
			level.setBlockAndUpdate(inn, state.setValue(ShopCounterBlock.FACING, facing.getClockWise()));
			helper.assertTrue(level.getBlockState(inn).is(ModBlocks.SHOP_COUNTER), "setup: the counter wasn't turned in place");
			helper.assertTrue(unloaded.equals(InnCounters.get(level).keeper(inn)),
				"turning a Shop Counter (not breaking it) forgot its Innkeeper");
			helper.assertTrue(counters(helper).contains(inn), "a turned Shop Counter stopped being the unloaded Innkeeper's inn: " + counters(helper));
			level.getPoiManager().release(inn);
			helper.succeed();
		});
	}

	/** A counter broken after it was remembered is still forgotten after the world is saved and loaded. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuiltVerify1114dSaved"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuiltVerify1114dSaved")
	public void aBrokenCounterStaysForgottenThroughASave(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		helper.runAfterDelay(2, () -> {
			InnCounters.get(level).remember(inn, UUID.randomUUID());
			CompoundTag before = InnCounters.get(level).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(InnCounters.load(before, level.registryAccess()).keeper(inn) != null, "setup: a remembered counter isn't saved");
			helper.setBlock(INN, Blocks.AIR);
			CompoundTag after = InnCounters.get(level).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(InnCounters.load(after, level.registryAccess()).keeper(inn) == null,
				"a broken Shop Counter comes back remembered as an Innkeeper's after a save");
			helper.assertTrue(InnCounters.get(level).isDirty(), "forgetting a broken counter doesn't mark the inns' list to be saved");
			helper.succeed();
		});
	}

	/**
	 * Two counters in the village, both workers past the edge and never seen inside it: the Innkeeper's counter is the
	 * inn, the Shopkeeper's is not (and is never remembered as an Innkeeper's).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuiltVerify1114dTwo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuiltVerify1114dTwo")
	public void ofTwoCountersWorkedFromPastTheEdgeOnlyTheInnkeepersIsTheInn(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		BlockPos shop = helper.absolutePos(SHOP);
		helper.setBlock(SHOP, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, helper.absolutePos(HALL));
			Villager sam = QaMoverResidents1110cGameTests.villager(helper, AWAY.east(), "Sam");
			Jobs.employ(level, sam, shop, ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
			helper.assertFalse(counters(helper).contains(shop), "a Shopkeeper's counter (he stands past the edge) is the inn: " + counters(helper));
			helper.assertFalse(innRolls(helper), "a legend rolls at the inn of a village whose only worked counter is a Shopkeeper's");
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, AWAY.west());
			helper.assertTrue(counters(helper).equals(List.of(inn)),
				"with an Innkeeper and a Shopkeeper both past the edge, the inns are " + counters(helper) + ", not just the Innkeeper's counter");
			helper.assertTrue(InnCounters.get(level).keeper(shop) == null, "the Shopkeeper's counter is remembered as an Innkeeper's");
			helper.assertTrue(ina.getUUID().equals(InnCounters.get(level).keeper(inn)), "the Innkeeper's counter isn't remembered as his");
			ina.remove(Entity.RemovalReason.DISCARDED);
			sam.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
