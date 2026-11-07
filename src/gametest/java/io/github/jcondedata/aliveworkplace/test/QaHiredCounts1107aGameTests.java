package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B999, B1000 (lane-a-1107-1222): the hall's census counts a hired band, relief riders and a story's visitors in its
 * lists, but not among the village's own villagers, so the realm's count, the festival's cost and the Steward's checks
 * stay as they were while the help is there. The People title and the rank are QaHiredHelpCounts1107cBugTests' and
 * QaHiredRank1107dBugTests'.
 */
public class QaHiredCounts1107aGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The census lists the band (the People table shows them) but counts two villagers; the Steward's checks too. */
	//$ gametest_ticks_batch AREA '200' '"qaHiredCounts1107aCensus"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredCounts1107aCensus")
	public void theCensusListsTheBandButCountsOnlyTheVillagesOwn(GameTestHelper helper) {
		BlockPos hall = QaHiredHelpCounts1107cGameTests.village(helper);
		QaHiredHelpCounts1107cGameTests.ownHoused(helper, 2);
		ServerPlayer player = QaHiredHelpCounts1107cGameTests.player(helper, hall);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			QaHiredHelpCounts1107cGameTests.hire(helper, player, hall, true);
			VillageHalls.Census census = VillageHalls.census(level, hall);
			helper.assertTrue(census.villagers() == 2, "with a band of " + Mercenaries.BAND + " hired the census counts " + census.villagers() + " villagers, not 2");
			helper.assertTrue(census.everyone() == 2 + Mercenaries.BAND, "the census lists " + census.everyone() + " people, not the 2 and the band");
			helper.assertTrue(VillageAdvice.bedsShort(census) == 0, "the band leaves the village " + VillageAdvice.bedsShort(census) + " beds short");
			StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
			helper.assertFalse(new StewardConditions.VillagersAtLeast(3).test(facts).held(), "the Steward counts the band toward 3 villagers");
			helper.succeed();
		});
	}

	/** The hall's own round keeps what the realm's tab shows of the village: its own two, not the band. */
	//$ gametest_ticks_batch AREA '200' '"qaHiredCounts1107aRealm"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredCounts1107aRealm")
	public void theHallsRoundTellsTheRealmTheVillagesOwnCount(GameTestHelper helper) {
		BlockPos hall = QaHiredHelpCounts1107cGameTests.village(helper);
		QaHiredHelpCounts1107cGameTests.ownHoused(helper, 2);
		ServerPlayer player = QaHiredHelpCounts1107cGameTests.player(helper, hall);
		ServerLevel level = helper.getLevel();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData realms = RealmData.get(level.getServer());
		Leftovers.after(helper, () -> realms.forgetKnown(at));
		helper.runAfterDelay(3, () -> {
			QaHiredHelpCounts1107cGameTests.hire(helper, player, hall, true);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setNeeds(null);
			VillageHallBlockEntity.serverTick(level, hall, level.getBlockState(hall), entity); // the hall's round
			RealmData.Known known = realms.known(at);
			helper.assertTrue(known != null, "the hall's round left the realm nothing of the village");
			helper.assertTrue(known.villagers() == 2, "with a band of " + Mercenaries.BAND + " hired the realm sees " + known.villagers() + " villagers, not 2");
			helper.succeed();
		});
	}

	/**
	 * Festival Season: "3 emeralds and 1 more for every 4 villagers". A village of two pays 3; with a band of three (5
	 * people) it would pay 4. The Festival button reads 3.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredCounts1107aFestival"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredCounts1107aFestival")
	public void aHiredBandLeavesTheFestivalsCostAsItWas(GameTestHelper helper) {
		BlockPos hall = QaHiredHelpCounts1107cGameTests.village(helper);
		QaHiredHelpCounts1107cGameTests.ownHoused(helper, 2);
		ServerPlayer player = QaHiredHelpCounts1107cGameTests.player(helper, hall);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			CivicEffects.forget();
			Festivals.forget();
		});
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setTreasury(0);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("festival_season").toString(), 1)));
			CivicEffects.forget();
			helper.assertTrue(Festivals.cost(entity, 2) == 3 && Festivals.cost(entity, 2 + Mercenaries.BAND) == 4,
				"setup: Festival Season costs " + Festivals.cost(entity, 2) + " for 2 and " + Festivals.cost(entity, 2 + Mercenaries.BAND) + " for 5");
			String three = Words.counted("screen.aliveworkplace.hall.festival_cost", 3, 3, 0).getString();
			helper.assertTrue(festivalLines(player, hall).contains(three), "setup: no '" + three + "' on the Festival button: " + festivalLines(player, hall));
			QaHiredHelpCounts1107cGameTests.hire(helper, player, hall, true);
			List<String> now = festivalLines(player, hall);
			helper.assertTrue(now.contains(three), "with a band of " + Mercenaries.BAND + " hired the Festival button reads " + now + ", not '" + three + "'");
			helper.succeed();
		});
	}

	private static List<String> festivalLines(ServerPlayer player, BlockPos hall) {
		ItemStack rocket = VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.FESTIVAL);
		if (!rocket.is(Items.FIREWORK_ROCKET)) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("no Festival button on the hall screen: " + rocket);
		}
		return QaHiredHelpCounts1107cGameTests.lore(rocket);
	}
}
