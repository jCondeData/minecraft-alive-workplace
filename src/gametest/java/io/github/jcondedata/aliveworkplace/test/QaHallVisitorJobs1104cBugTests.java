package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-a, 2026-11-04): failures found while checking B906 that are the mod's, not the test's. Each test states what
 * the player should see; it fails until the mod is fixed.
 */
public class QaHallVisitorJobs1104cBugTests {
	private static final String ITEM_AREA = "aliveworkplace_test:huge_area";
	/** The village hall of QaHallScreenGameTests.village. */
	private static final BlockPos ITEM_HALL = new BlockPos(15, 2, 15);
	/** A spot in that village with no Stonecutter (nor any other job block) near. */
	private static final BlockPos NO_BLOCK = new BlockPos(13, 2, 14);

	/** One sneak-right-click with clay (the Mason's item) through the game's interact packet, as the player's own. */
	private static void clay(ServerPlayer player, Villager v) {
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.CLAY_BALL), true);
	}

	private static List<Villager> hireBand(GameTestHelper helper, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(ITEM_HALL);
		Mercenaries.hire(level, hall, player);
		List<Villager> band = Mercenaries.near(level, hall);
		Leftovers.after(helper, () -> band.forEach(Villager::discard));
		helper.assertTrue(band.size() == Mercenaries.BAND, "setup: band " + band.size());
		return band;
	}

	private static void standAway(GameTestHelper helper, Villager v) {
		v.setNoAi(true);
		BlockPos at = helper.absolutePos(NO_BLOCK);
		v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

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
			Villager merc = hireBand(helper, player).get(0);
			standAway(helper, merc);
			seen.clear();
			clay(player, merc);
			helper.assertTrue(merc.getVillagerData().getProfession() == ModVillagers.GUARD, "the mercenary's job changed: " + merc.getVillagerData());
			helper.assertTrue(seen.contains("Mercenary's job can't be changed here."),
				"with no Stonecutter near, a mercenary handed clay was told: " + seen);
			helper.succeed();
		});
	}
}
