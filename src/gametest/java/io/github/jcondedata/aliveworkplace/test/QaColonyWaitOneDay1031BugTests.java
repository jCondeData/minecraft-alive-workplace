package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane-e-1031-1913, config extremes and the sentences a player reads): {@code colonyCooldownDays} goes down to 0
 * (WorkplaceConfig.RANGES: 0 to 60), and the Founder (33.23) and the Royal Charter (33.22) halve it, floored at one day,
 * so a village can wait exactly one day between colonies. The hall's Colonies tab (Trade page) then reads, on its count
 * button, "A village waits 1 days between colonies" ({@code screen.aliveworkplace.colonies.wait_about} has no
 * {@code .one} form and is passed through a plain translatable), while the line above it, the countdown, already says
 * "in 1 day". Expected: "A village waits 1 day between colonies"; 2 days stays plural (the control).
 */
public class QaColonyWaitOneDay1031BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	//$ gametest_ticks_batch AREA '100' '"qaColonyWaitOneDay1031"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColonyWaitOneDay1031")
	public void aOneDayWaitReadsInTheSingularOnTheColoniesTab(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int cap = Colonies.PER_VILLAGE;
		int every = VillageNeeds.CHECK_EVERY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.PER_VILLAGE = 3;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			Colonies.PER_VILLAGE = cap;
			VillageNeeds.CHECK_EVERY = every;
			RealmData.get(level.getServer()).clearColonies();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		String name = VillageHalls.name(level, hall).getString();
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		GlobalPos colony = GlobalPos.of(level.dimension(), hall.offset(500, 0, 0));

		// the control: two days, a colony founded yesterday, one day left
		Colonies.COOLDOWN_DAYS = 2;
		RealmData.get(level.getServer()).recordFounded(mother, colony, Chronicle.day(level) - 1);
		List<String> two = BoardTradeGameTests.lore(tab(player, hall).icon(Colonies.COUNT));
		helper.assertTrue(Colonies.cooldownDays(level, hall) == 2 && two.contains("A village waits 2 days between colonies")
			&& two.contains("The next colony can set out in 1 day"), "at 2 days the tab reads " + two);

		// the config's one day: founded today
		Colonies.COOLDOWN_DAYS = 1;
		RealmData.get(level.getServer()).clearColonies();
		RealmData.get(level.getServer()).recordFounded(mother, colony, Chronicle.day(level));
		entity.setRank(VillageRanks.Rank.CITY);
		List<String> one = BoardTradeGameTests.lore(tab(player, hall).icon(Colonies.COUNT));
		helper.assertTrue(Colonies.cooldownDays(level, hall) == 1, "colonyCooldownDays 1 is a wait of " + Colonies.cooldownDays(level, hall));
		helper.assertTrue(one.contains("The next colony can set out in 1 day"), name + "'s countdown at 1 day: " + one);
		helper.assertTrue(one.contains("A village waits 1 day between colonies"), "at 1 day the tab reads " + one);
		helper.succeed();
	}

	private static ChoiceMenu tab(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.COLONIES.slot(), player);
		return menu;
	}
}
