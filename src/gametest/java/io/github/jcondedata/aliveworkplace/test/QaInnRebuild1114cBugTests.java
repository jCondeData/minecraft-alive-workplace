package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AWAY;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.INN;

import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Bug tests (QA, lane C, 2026-11-14), B1213/B1215's spec ("a counter worked by someone other than the remembered
 * Innkeeper is no inn"): the counter Ina the Innkeeper worked is broken while she is in an unloaded chunk, put back, and
 * Sam the Shopkeeper takes it (he holds its one ticket). When Ina loads again she still remembers the spot as her job
 * site (vanilla keeps that memory while a workstation of the kind stands there; the mod's own break count,
 * JobSiteTickets, knows it isn't hers since, B8/B25), and Innkeepers.counters takes her word for it: Sam's shop is the
 * village's inn again. Not registered: fails until the mod is fixed.
 */
public class QaInnRebuild1114cBugTests implements FabricGameTest {
	/** Broken while Ina was unloaded, rebuilt for Sam; then Ina loads again at {@code inaAt}, and Sam is unloaded or out past the edge. */
	private static void inaComesBack(GameTestHelper helper, BlockPos inaAt, boolean samUnloads, String where) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag ina = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					if (samUnloads) {
						QaMoveInnVerify1111cGameTests.unload(sam);
					}
					Villager back = QaMoveInnVerify1111cGameTests.back(level, ina);
					BlockPos at = helper.absolutePos(inaAt);
					back.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Sam doesn't hold the rebuilt counter");
					helper.assertTrue(InnCounters.get(level).keeper(inn) == null || !samUnloads, "setup: the broken counter wasn't forgotten");
					helper.assertFalse(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"the counter rebuilt for Sam the Shopkeeper" + (samUnloads ? " (now unloaded)" : " (out past the edge)")
							+ " is the inn again once Ina, its old Innkeeper, loads " + where + ": counters " + QaInnRebuild1114cGameTests.counters(helper)
							+ ", remembered keeper " + (ina.getUUID("UUID").equals(InnCounters.get(level).keeper(inn)) ? "Ina" : InnCounters.get(level).keeper(inn)));
					helper.assertFalse(QaInnRebuild1114cGameTests.innRolls(helper), "a legend can come to Sam's rebuilt shop as an inn because the old Innkeeper loaded " + where);
					back.discard();
					if (!samUnloads) {
						sam.discard();
					}
					helper.succeed();
				});
			});
		});
	}

	/** Ina loads again past the edge while Sam (the rebuilt counter's Shopkeeper) is in an unloaded chunk. */
	//$ gametest_ticks_batch AREA '80' '"qaInnRebuild1114cBackAway"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnRebuild1114cBackAway")
	public void theOldInnkeeperLoadingPastTheEdgeDoesNotMakeTheRebuiltShopAnInn(GameTestHelper helper) {
		inaComesBack(helper, AWAY.west(), true, "past the village's edge");
	}

	/**
	 * Ina loads again inside the village (by the Hall, 10 blocks from the counter: not standing at it) while Sam is out
	 * past the edge.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnRebuild1114cBackHome"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnRebuild1114cBackHome")
	public void theOldInnkeeperBackInTheVillageDoesNotMakeTheRebuiltShopAnInn(GameTestHelper helper) {
		inaComesBack(helper, HALL.south(), false, "in the village (by the Hall)");
	}
}
