package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, 2026-11-11, verifying B1177 across saves): "these should look for an innkeeper's counter the same way (a
 * Shop Counter worked by an Innkeeper)", and Innkeepers.counters' contract: the inns' counters within the radius, each
 * once. On top of InnShopCounterB1177GameTests: the Innkeeper read back from his save (a server restart) still makes his
 * Shop Counter an inn; asleep in his bed he still does; his counter broken, he doesn't; an old save's Innkeeper at a retired Inn Counter is counted once; and the
 * reach's edge from both sides. Not covered: the Innkeeper out past the reach while his counter is inside it (the
 * lookup goes by where he stands, so his inn isn't found then; noted, not filed). Hall (15, 2, 2), counter (25, 2, 3): about 10.05 blocks apart.
 */
public class QaInnShopCounter1111eGameTests implements FabricGameTest {
	private static final String AREA = QaMoverResidents1110cGameTests.AREA;
	private static final BlockPos HALL = QaMoverResidents1110cGameTests.HALL;
	private static final BlockPos INN = QaMoverResidents1110cGameTests.INN;

	private interface Check {
		void run(ServerLevel level, BlockPos hall, BlockPos counter);
	}

	/** A Shop Counter (or the old Inn Counter, {@code old}) at the inn spot, every other inn round cleared, then {@code check}. */
	private static void stage(GameTestHelper helper, boolean old, Check check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(INN, old ? ModBlocks.INN_COUNTER : ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			helper.setBlock(INN, old ? ModBlocks.INN_COUNTER : ModBlocks.SHOP_COUNTER); // noOtherInns takes Inn Counters down
		});
		helper.runAfterDelay(4, () -> {
			check.run(level, hall, helper.absolutePos(INN));
			helper.succeed();
		});
	}

	private static Villager innkeeper(GameTestHelper helper, BlockPos counter, boolean old) {
		Villager keeper = QaMoverResidents1110cGameTests.villager(helper, INN.south(), "Ina");
		Jobs.employ(helper.getLevel(), keeper, counter, old ? ModVillagers.INN_COUNTER_POI : ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
		return keeper;
	}

	/** Saves the villager as the world does and loads him back in his place (a server restart). */
	private static Villager readBack(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helperAssert(villager.save(tag), "the Innkeeper wasn't saved");
		villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helperAssert(back instanceof Villager, "the Innkeeper didn't load back: " + back);
		helperAssert(level.addFreshEntity(back), "the Innkeeper read back couldn't be added");
		return (Villager) back;
	}

	private static void helperAssert(boolean ok, String what) {
		if (!ok) {
			throw new net.minecraft.gametest.framework.GameTestAssertException(what);
		}
	}

	private static String found(ServerLevel level, BlockPos hall, int radius) {
		return Innkeepers.counters(level, hall, radius).toString();
	}

	/** The Innkeeper read back from his save keeps his Shop Counter an inn: the legend rolls there and it's an inn counter. */
	//$ gametest_batch AREA '"qaInnShop1111eReload"'
	@GameTest(template = AREA, batch = "qaInnShop1111eReload")
	public void anInnkeeperReadBackFromHisSaveStillMakesHisShopCounterAnInn(GameTestHelper helper) {
		stage(helper, false, (level, hall, counter) -> {
			Villager back = readBack(level, innkeeper(helper, counter, false));
			helper.assertTrue(Innkeepers.isInnkeeper(back), "read back he is no Innkeeper: " + back.getVillagerData().getProfession());
			helper.assertTrue(Innkeepers.counters(level, hall, 64).equals(List.of(counter)), "after a reload the inn counters are " + found(level, hall, 64) + ", not his counter " + counter);
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "after a reload his Shop Counter inn doesn't roll for a legend");
		});
	}

	/** At night, asleep in his bed, the Innkeeper's counter is still the inn. */
	//$ gametest_batch AREA '"qaInnShop1111eAsleep"'
	@GameTest(template = AREA, batch = "qaInnShop1111eAsleep")
	public void anInnkeeperAsleepInHisBedStillKeepsHisInn(GameTestHelper helper) {
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_1);
		stage(helper, false, (level, hall, counter) -> {
			Villager keeper = innkeeper(helper, counter, false);
			keeper.startSleeping(helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_1));
			helper.assertTrue(keeper.isSleeping(), "setup: the Innkeeper isn't asleep");
			helper.assertTrue(Innkeepers.counters(level, hall, 64).equals(List.of(counter)), "with the Innkeeper asleep the inn counters are " + found(level, hall, 64));
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "with the Innkeeper asleep his inn doesn't roll for a legend");
		});
	}

	/** The reach's edge from both sides, with him at his counter: 10.05 blocks off, out of a reach of 10, in at 11. */
	//$ gametest_batch AREA '"qaInnShop1111eEdge"'
	@GameTest(template = AREA, batch = "qaInnShop1111eEdge")
	public void theReachsEdgeFromBothSides(GameTestHelper helper) {
		stage(helper, false, (level, hall, counter) -> {
			innkeeper(helper, counter, false);
			helper.assertTrue(Innkeepers.counters(level, hall, 10).isEmpty(), "a counter 10.05 blocks off is inside a reach of 10: " + found(level, hall, 10));
			helper.assertTrue(Innkeepers.counters(level, hall, 11).equals(List.of(counter)), "a counter 10.05 blocks off is outside a reach of 11: " + found(level, hall, 11));
		});
	}

	/** His Shop Counter broken (his memory of it not yet dropped): no inn, no legend at the inn. */
	//$ gametest_batch AREA '"qaInnShop1111eBroken"'
	@GameTest(template = AREA, batch = "qaInnShop1111eBroken")
	public void anInnkeepersBrokenCounterIsNoInn(GameTestHelper helper) {
		stage(helper, false, (level, hall, counter) -> {
			innkeeper(helper, counter, false);
			helper.assertTrue(!Innkeepers.counters(level, hall, 64).isEmpty(), "setup: no inn before the break");
			level.destroyBlock(counter, false);
			helper.assertTrue(Innkeepers.counters(level, hall, 64).isEmpty(), "his Shop Counter broken, the inn counters are still " + found(level, hall, 64));
			helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1), "his Shop Counter broken, the inn still rolls for a legend");
		});
	}

	/** An old save: the Innkeeper at a retired Inn Counter, read back. One inn, counted once (both lookups find it). */
	//$ gametest_batch AREA '"qaInnShop1111eOld"'
	@GameTest(template = AREA, batch = "qaInnShop1111eOld")
	public void anOldSavesInnCounterAndItsInnkeeperAreOneInn(GameTestHelper helper) {
		stage(helper, true, (level, hall, counter) -> {
			readBack(level, innkeeper(helper, counter, true));
			helper.assertTrue(Innkeepers.counters(level, hall, 64).equals(List.of(counter)), "an old Inn Counter worked by its Innkeeper: " + found(level, hall, 64));
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "an old Inn Counter inn doesn't roll for a legend");
		});
	}
}
