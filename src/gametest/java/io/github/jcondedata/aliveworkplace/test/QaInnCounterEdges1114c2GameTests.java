package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AWAY;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.INN;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.SHOP;

import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-c-1114-1252), B1213 and B1228 from their expected behavior: "a broken counter is forgotten, and a counter
 * worked by someone other than the remembered Innkeeper is no inn". The fixes' own tests cover a counter rebuilt for a
 * Shopkeeper while the old Innkeeper is unloaded, loads past the edge or comes back by the Hall; these cover the other
 * side: the fix must not over-forget (the same Innkeeper, or a new one, taking the rebuilt counter makes it the inn
 * again, and it stays the inn while they are away), a counter broken and not put back is listed nowhere, of two inns only
 * the rebuilt one stops being one, a shop rebuilt stays a shop across a save with the old Innkeeper standing at it, and a
 * rebuilt counter nobody works is no inn.
 */
public class QaInnCounterEdges1114c2GameTests implements FabricGameTest {
	private static boolean rollsAnyDay(GameTestHelper helper) {
		for (long day = 1; day <= 8; day++) {
			if (LegendGuests.rollsOn(helper.getLevel(), QaInnRebuild1114cGameTests.hall(helper), null, "inn", day)) {
				return true;
			}
		}
		return false;
	}

	private static void standAt(GameTestHelper helper, Villager v, BlockPos at) {
		BlockPos abs = helper.absolutePos(at);
		v.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
	}

	/**
	 * Ina is unloaded; her counter is broken and put back; she loads again and takes the new counter herself. It is the inn
	 * again, remembered as hers, and stays the inn when she goes off into an unloaded chunk once more.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnEdges1114c2Same"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnEdges1114c2Same")
	public void theSameInnkeeperTakingTheRebuiltCounterBackMakesItTheInnAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "a broken Shop Counter still remembers its Innkeeper");
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager ina = QaMoveInnVerify1111cGameTests.back(level, tag);
					Jobs.employ(level, ina, inn, ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Ina didn't take the rebuilt counter");
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"Ina the Innkeeper took the rebuilt counter back (she is out past the edge): it is no inn: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertTrue(ina.getUUID().equals(InnCounters.get(level).keeper(inn)), "Ina isn't remembered at the counter she took back: " + InnCounters.get(level).keeper(inn));
					helper.assertTrue(QaInnRebuild1114cGameTests.innRolls(helper), "no legend can come to the inn Ina took back");
					CompoundTag again = QaMoveInnVerify1111cGameTests.unload(ina);
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"the counter Ina took back is no inn once she is in an unloaded chunk again: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertTrue(rollsAnyDay(helper), "no legend can come to the inn Ina took back while she is away");
					Villager home = QaMoveInnVerify1111cGameTests.back(level, again);
					standAt(helper, home, INN.south());
					helper.runAfterDelay(10, () -> {
						helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
							"the counter Ina took back is no inn with her standing at it: counters " + QaInnRebuild1114cGameTests.counters(helper));
						home.discard();
						helper.succeed();
					});
				});
			});
		});
	}

	/** Ina is unloaded and her counter is broken and never put back: no inn anywhere, before or after she loads again by the spot. */
	//$ gametest_ticks_batch AREA '80' '"qaInnEdges1114c2Gone"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnEdges1114c2Gone")
	public void aCounterBrokenAndNotRebuiltIsListedNowhere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "the broken counter's Innkeeper is still remembered");
			helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).isEmpty(),
				"a Shop Counter broken while its Innkeeper is unloaded is still listed: counters " + QaInnRebuild1114cGameTests.counters(helper));
			helper.assertFalse(rollsAnyDay(helper), "a legend can come to an inn whose counter was broken");
			Villager ina = QaMoveInnVerify1111cGameTests.back(level, tag);
			standAt(helper, ina, INN.south());
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).isEmpty(),
					"the broken counter's Innkeeper came back and stands by the empty spot: counters " + QaInnRebuild1114cGameTests.counters(helper));
				helper.assertFalse(rollsAnyDay(helper), "a legend can come to a broken inn once its Innkeeper is back");
				helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "the empty spot remembers Ina again");
				ina.discard();
				helper.succeed();
			});
		});
	}

	/**
	 * Two inns: Ina's at INN and Ivo's at SHOP, both Innkeepers then unloaded. Ina's counter is broken and rebuilt for Sam the
	 * Shopkeeper (then unloaded too): Ivo's stays the inn, Ina's is a shop.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnEdges1114c2Two"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnEdges1114c2Two")
	public void ofTwoInnsOnlyTheOneRebuiltForAShopkeeperStopsBeingAnInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		BlockPos other = helper.absolutePos(SHOP);
		QaInnRebuild1114cGameTests.village(helper, INN, SHOP);
		helper.runAfterDelay(2, () -> {
			QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper); // (clears every other Innkeeper about first)
			Villager ivo = QaInnRebuild1114cGameTests.worker(helper, SHOP, SHOP.south(), "Ivo", ModVillagers.INNKEEPER);
			helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(other), "setup: Ivo's counter isn't an inn: counters " + QaInnRebuild1114cGameTests.counters(helper)
				+ ", Ivo " + ivo.getVillagerData().getProfession() + " at " + ivo.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE) + ", counter " + other.toShortString());
			helper.assertTrue(ivo.getUUID().equals(InnCounters.get(level).keeper(other)), "setup: Ivo isn't remembered at his counter");
			BlockPos away = helper.absolutePos(AWAY.west(2));
			ivo.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			QaMoveInnVerify1111cGameTests.unload(ivo);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					QaMoveInnVerify1111cGameTests.unload(sam);
					List<BlockPos> found = QaInnRebuild1114cGameTests.counters(helper);
					helper.assertTrue(found.equals(List.of(other)), "of two inns (Ivo's at " + other.toShortString() + ", Ina's at " + inn.toShortString()
						+ ", rebuilt for Sam the Shopkeeper), with all three unloaded, the inns are " + found);
					helper.assertTrue(ivo.getUUID().equals(InnCounters.get(level).keeper(other)), "Ivo's untouched inn forgot him when Ina's counter was broken");
					helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "Sam's rebuilt shop remembers an Innkeeper");
					helper.assertTrue(QaInnRebuild1114cGameTests.innRolls(helper), "a village with Ivo's inn standing gets no legend at the inn");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The counter rebuilt for Sam (who goes off into an unloaded chunk) stays a shop across a save of the remembered
	 * counters, with Ina back and standing right at it for a while.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInnEdges1114c2Save"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInnEdges1114c2Save")
	public void aCounterRebuiltForAShopkeeperStaysAShopAcrossASaveWithTheOldInnkeeperAtIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					QaMoveInnVerify1111cGameTests.unload(sam);
					CompoundTag saved = InnCounters.get(level).save(new CompoundTag(), level.registryAccess());
					level.getDataStorage().set(InnCounters.NAME, InnCounters.load(saved, level.registryAccess()));
					// Ina's own save goes through a round trip too (her chunk saved and loaded again).
					Villager ina = QaMoveInnVerify1111cGameTests.back(level, tag);
					CompoundTag twice = QaMoveInnVerify1111cGameTests.unload(ina);
					Villager back = QaMoveInnVerify1111cGameTests.back(level, twice);
					standAt(helper, back, INN.south());
					helper.runAfterDelay(20, () -> {
						helper.assertFalse(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
							"after a save, Sam's rebuilt shop is the inn again with Ina, its old Innkeeper, standing at it for a second: counters "
								+ QaInnRebuild1114cGameTests.counters(helper) + ", remembered keeper " + InnCounters.get(level).keeper(inn));
						helper.assertFalse(rollsAnyDay(helper), "after a save, a legend can come to Sam's rebuilt shop as an inn");
						back.discard();
						helper.succeed();
					});
				});
			});
		});
	}

	/**
	 * The counter is rebuilt and nobody takes it (an unemployed villager stands by it): no inn. A second Innkeeper, Ivo, takes
	 * it: his inn, remembered as his, and it stays his when Ina loads again by the Hall and when Ivo goes off unloaded.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnEdges1114c2Ivo"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnEdges1114c2Ivo")
	public void aRebuiltCounterNobodyWorksIsNoInnUntilASecondInnkeeperTakesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager una = QaMoverResidents1110cGameTests.villager(helper, INN.west(), "Una");
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 1, "setup: someone took the rebuilt counter");
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).isEmpty(),
						"a rebuilt Shop Counter nobody works (an unemployed villager by it, its old Innkeeper unloaded) is an inn: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertFalse(rollsAnyDay(helper), "a legend can come to a rebuilt counter nobody works");
					Villager ivo = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Ivo", ModVillagers.INNKEEPER);
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).equals(List.of(inn)),
						"Ivo, a new Innkeeper (out past the edge), took the rebuilt counter: no inn: counters " + QaInnRebuild1114cGameTests.counters(helper));
					UUID keeper = InnCounters.get(level).keeper(inn);
					helper.assertTrue(ivo.getUUID().equals(keeper), "the rebuilt counter isn't remembered as Ivo's: " + keeper);
					Villager ina = QaMoveInnVerify1111cGameTests.back(level, tag);
					standAt(helper, ina, HALL.south());
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"Ivo's inn stopped being the inn once Ina, the old Innkeeper, loaded by the Hall: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertTrue(ivo.getUUID().equals(InnCounters.get(level).keeper(inn)), "Ina's stale memory took Ivo's inn: " + InnCounters.get(level).keeper(inn));
					QaMoveInnVerify1111cGameTests.unload(ivo);
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"Ivo's inn is no inn while he is in an unloaded chunk and Ina stands by the Hall: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertTrue(rollsAnyDay(helper), "no legend can come to Ivo's inn while he is away");
					ina.discard();
					una.discard();
					helper.succeed();
				});
			});
		});
	}
}
