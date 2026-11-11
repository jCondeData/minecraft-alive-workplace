package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Candidate bug test (QA lane c, 2026-11-11, B1177's kin; NOT registered): B1177's spec says the inn is "a Shop Counter
 * worked by an Innkeeper", the counter the innkeeper's job finds by his job site. Innkeepers.counters looks for the
 * Innkeeper himself within the hall's reach, so an inn whose counter stands in the village but whose Innkeeper has
 * stepped out of it (asleep in a bed past the village's edge, gone strolling) is no inn for that moment: no legend
 * rolls there (the Seer foretells nobody) and a move's residents stay by the Hall. The hall's reach is set small here
 * (12; the counter is 10 from the hall), standing in for the real 64 with an inn near the village's edge.
 */
public class QaInnShopCounter1111cBugTests implements FabricGameTest {
	static final String AREA = QaMoverResidents1110cGameTests.AREA;

	//$ gametest_batch AREA '"qaInn1111cAwayBug"'
	@GameTest(template = AREA, batch = "qaInn1111cAwayBug")
	public void anInnkeeperStepsOutOfTheVillageButHisCounterIsStillTheInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		QaMoverResidents1110cGameTests.Setup s = QaInnShopCounter1111cGameTests.shopInn(helper, "qa1111caway");
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			VillageHalls.RADIUS = 12;
			try {
				helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "setup: the inn doesn't roll with the Innkeeper by his counter");
				BlockPos away = helper.absolutePos(new BlockPos(29, 2, 12)); // 14 blocks east of the hall: outside its reach
				keeper.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
				helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper),
					"the Innkeeper stepped out of the village while his Shop Counter stands in it, and the inn is no inn: counters "
						+ Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
				Optional<BlockPos> bed = QaInnShopCounter1111cGameTests.stayBed(helper, s);
				helper.assertTrue(QaInnShopCounter1111cGameTests.atTheInn(helper, bed), "residents should stay at the inn, got " + bed);
			} finally {
				VillageHalls.RADIUS = radius;
			}
			helper.succeed();
		});
	}
}
