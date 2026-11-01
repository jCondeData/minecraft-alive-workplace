package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (2026-11-01, lane-c-1101-0052) of B730/B732 on a server that already has a wagon-bed list: the fix marks a list
 * "complete" only in a world started with it, so a server whose list was saved before (the owner's live server) still
 * judges every new brown or green bed by the old camp look, and a cabin's bed by the village builder's own Blueprint
 * Table is taken for a wagon bed.
 */
public class QaWagonBedsOldList1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B730/B732 on a server whose wagon-bed list was saved before the fix (as the owner's live server's is: a current list
	 * with no "complete" mark): a cabin bed the player places now, by a campfire, hay and the village builder's own
	 * Blueprint Table, was placed by nobody's camp, after the village was founded. Expected (B732: "a bed placed after the
	 * village was founded is a home"; B730: the look judges only beds from lists saved before the fix): it stays a home.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVf1101CabinOldList"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVf1101CabinOldList")
	public void qaANewCabinBedOnAServerWithAnOlderListStaysAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		// the list as a server saved it before B730: this version's list, with no "complete" mark, and no beds near here
		CompoundTag saved = CampBeds.Data.load(new CompoundTag(), level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.contains("version") && !saved.contains("complete"), "set-up: not an older list: " + saved);
		CampBeds.Data older = CampBeds.Data.load(saved, level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, older);
		helper.assertTrue(!CampBeds.Data.get(level).complete(), "set-up: the older list counts as complete");
		QaVillageFixes1101GameTests.cabinRound(helper, (beds, residents) -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(!CampBeds.isWagonBed(level, beds.get(i)) && beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))),
					"on a server with a list saved before the fix, a cabin bed placed now by the builder's Blueprint Table is a wagon bed: bed " + i
						+ " wagon " + CampBeds.isWagonBed(level, beds.get(i)) + ", home " + QaVillageGuards1031GameTests.home(residents.get(i)));
			}
			helper.succeed();
		});
	}

}
