package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-a, 2026-11-04): failures found while checking B906 that are the mod's, not the test's. Each test states what
 * the player should see; it fails until the mod is fixed.
 */
public class QaHallVisitorJobs1104cBugTests {
	private static final String ITEM_AREA = "aliveworkplace_test:huge_area";

	/**
	 * A mercenary with no Stonecutter anywhere near handed clay: the item can never give him the job, so he is told his
	 * job can't be changed here, not "The Mason job needs a Stonecutter: stand them next to one, then hand them this
	 * again." (doing that is only refused).
	 */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemMercNoBlock1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemMercNoBlock1104c")
	public void aMercenaryWithNoBlockNearIsToldHisJobCantChange(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Villager merc = QaHallVisitorJobs1104cGameTests.hireBand(helper, player).get(0);
			QaHallVisitorJobs1104cGameTests.standByCutter(helper, merc);
			seen.clear();
			QaHallVisitorJobs1104cGameTests.clay(player, merc);
			helper.assertTrue(merc.getVillagerData().getProfession() == ModVillagers.GUARD, "the mercenary's job changed: " + merc.getVillagerData());
			helper.assertTrue(seen.contains("Mercenary's job can't be changed here."),
				"with no Stonecutter near, a mercenary handed clay was told: " + seen);
			helper.succeed();
		});
	}
}
