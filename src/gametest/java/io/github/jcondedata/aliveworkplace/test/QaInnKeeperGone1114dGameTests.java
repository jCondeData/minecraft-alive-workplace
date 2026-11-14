package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-14), B1203/B1205 verified from the spec: "a Shop Counter inside the village that an Innkeeper works
 * is its inn wherever that Innkeeper is standing". The fix's tests cover the Innkeeper away, a save and a discarded
 * Innkeeper; these cover the other ways the inn should end or stay while he is away: he is killed out there, his counter
 * is broken, he takes another trade, and he is in an unloaded chunk (remembered, counter still held) and then lets go.
 */
public class QaInnKeeperGone1114dGameTests implements FabricGameTest {
	static final String AREA = QaInnCounters1111dGameTests.AREA;
	private static final BlockPos HALL = QaInnCounters1111dGameTests.HALL;
	private static final BlockPos INN = QaInnCounters1111dGameTests.INN;
	private static final BlockPos AWAY = new BlockPos(15, 2, 27); // 25 blocks from the Hall: past the edge (12)

	/** The inn with its Innkeeper sent out past the edge (checked: still the inn), for {@code then}. */
	private static void awayInn(GameTestHelper helper, java.util.function.BiConsumer<Villager, BlockPos> then) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QaInnCounters1111dGameTests.radius(helper, 12);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			Villager keeper = QaInnCounters1111dGameTests.innkeeper(helper, INN, INN.south());
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(INN)), "setup: no inn");
			BlockPos away = helper.absolutePos(AWAY);
			keeper.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(INN)),
				"setup: the inn stopped being one while its Innkeeper is away");
			then.accept(keeper, hall);
		});
	}

	/** Killed out past the edge: the counter nobody works is no inn any more (no legend comes to a keeperless inn). */
	//$ gametest_ticks_batch AREA '80' '"qaInnKeeperGone1114dKilled"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnKeeperGone1114dKilled")
	public void anInnkeeperKilledWhileAwayLeavesNoInn(GameTestHelper helper) {
		awayInn(helper, (keeper, hall) -> {
			ServerLevel level = helper.getLevel();
			keeper.kill();
			helper.runAfterDelay(2, () -> {
				helper.assertFalse(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(INN)),
					"the counter of an Innkeeper killed out past the edge is still the village's inn");
				helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1), "a legend can still come to the inn of a dead Innkeeper");
				keeper.discard();
				helper.succeed();
			});
		});
	}

	/** His counter broken while he is away: there is no inn (and no counter) left. */
	//$ gametest_ticks_batch AREA '80' '"qaInnKeeperGone1114dBroken"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnKeeperGone1114dBroken")
	public void aCounterBrokenWhileItsInnkeeperIsAwayIsNoInn(GameTestHelper helper) {
		awayInn(helper, (keeper, hall) -> {
			ServerLevel level = helper.getLevel();
			helper.setBlock(INN, Blocks.AIR);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).isEmpty(),
					"a broken Shop Counter is still an inn counter while its Innkeeper is away: " + Innkeepers.counters(level, hall, VillageHalls.RADIUS));
				keeper.discard();
				helper.succeed();
			});
		});
	}

	/** He takes another trade while away (loaded, out past the edge): his old counter is a shop's now, not the inn. */
	//$ gametest_ticks_batch AREA '80' '"qaInnKeeperGone1114dTrade"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnKeeperGone1114dTrade")
	public void anInnkeeperWhoTakesAnotherTradeWhileAwayLeavesNoInn(GameTestHelper helper) {
		awayInn(helper, (keeper, hall) -> {
			ServerLevel level = helper.getLevel();
			keeper.setVillagerData(keeper.getVillagerData().setProfession(VillagerProfession.FARMER));
			helper.assertFalse(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(INN)),
				"the counter of a former Innkeeper (a Farmer now, out past the edge) is still the inn");
			helper.assertTrue(InnCounters.get(level).keeper(helper.absolutePos(INN)) == null,
				"the counter is still remembered as the former Innkeeper's");
			keeper.discard();
			helper.succeed();
		});
	}

	/**
	 * In an unloaded chunk (simulated: a remembered keeper the level doesn't have, the counter's ticket still held) the
	 * counter is taken at its word; once the ticket is let go (the block's job site freed), it is no inn.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaInnKeeperGone1114dUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnKeeperGone1114dUnloaded")
	public void anUnloadedInnkeepersHeldCounterIsTheInnUntilItIsFreed(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos inn = helper.absolutePos(INN);
		QaInnCounters1111dGameTests.radius(helper, 12);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).isEmpty(), "setup: an inn with nobody at it");
			UUID unloaded = UUID.randomUUID();
			InnCounters.get(level).remember(inn, unloaded);
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).isEmpty(),
				"a remembered counter whose ticket nobody holds counts as the inn");
			level.getPoiManager().take(h -> h.is(ModVillagers.SHOP_COUNTER_POI), (h, p) -> p.equals(inn), inn, 1);
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(inn),
				"the held counter of an Innkeeper in an unloaded chunk isn't the inn");
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "no legend can come to the inn of an Innkeeper in an unloaded chunk");
			level.getPoiManager().release(inn);
			helper.assertFalse(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(inn),
				"a freed counter is still the inn of an Innkeeper who isn't loaded");
			InnCounters.get(level).forget(inn);
			helper.succeed();
		});
	}
}
