package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1103-1554), bug tests for the People table's No job on a villager with a role (not registered: they fail
 * until the mod is fixed; the builder registers each with its fix).
 */
public class QaHallRolesDuty1103BugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);

	/**
	 * A hired mercenary band (the hall's Mercenaries, 12 emeralds: three Guards with swords and armor, no guard post, who
	 * leave at dawn) is shown in the People table as jobless ("No job"), and his row offers the jobless villager's free
	 * first job. Expected: a mercenary is a guard, not jobless (the header's jobless count isn't raised by a hired band),
	 * and a hired sword's job isn't the village's to hand out: no change on his row, nothing given for free.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdMercenary"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdMercenary")
	public void aHiredMercenaryIsNotListedAsJobless(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos stonecutter = new BlockPos(22, 2, 22);
		helper.setBlock(stonecutter, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 30);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(3, () -> {
			io.github.jcondedata.aliveworkplace.guard.Mercenaries.hire(level, hall, player);
			List<Villager> band = io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(level, hall);
			helper.assertTrue(band.size() == 3 && player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 18, "setup: band " + band.size());
			Villager merc = band.get(0);
			String was = merc.getVillagerData().getProfession().toString();
			Leftovers.after(helper, () -> band.forEach(Villager::discard));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			net.minecraft.nbt.CompoundTag row = io.github.jcondedata.aliveworkplace.hall.HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, merc)));
			if (!row.getString("kind").equals("jobless") && row.getString("job_action").isEmpty()) {
				helper.succeed();
				return;
			}
			// What a player can do with that row: Job ▾, then the Mason line.
			menu.clickMenuButton(player, io.github.jcondedata.aliveworkplace.hall.HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, merc));
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.fail("a hired Mercenary (" + was + " before the click) is listed as kind '" + row.getString("kind")
				+ "', job '" + row.getString("job") + "', action '" + row.getString("job_action") + "'; one click on Mason made him "
				+ merc.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(merc) + " for "
				+ (18 - player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)) + " emeralds, still hired: "
				+ io.github.jcondedata.aliveworkplace.guard.Mercenaries.isMercenary(merc) + "; told " + seen);
		});
	}

	/**
	 * A friend with an open "Next level before the festival" request (personal-requests.md: "a worker below Master ... they
	 * reach their next level") is given No job from the People table. Jobless, she can never reach a level (the request's
	 * own condition, `level_below`: "they have a job"), so the request should be called off (or otherwise stay
	 * finishable); left open, it can only run out, and then the player who took it on is punished for it: 30 friendship
	 * off, "Let down: nobody helped in time", and the "missed" line in chat.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdRequestNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdRequestNoJob")
	public void aJoblessFriendsLevelRequestIsNotLeftToFail(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		boolean requests = PersonalRequests.ENABLED;
		boolean friendship = Friendship.ENABLED;
		PersonalRequests.ENABLED = true;
		Friendship.ENABLED = true;
		PersonalRequests.forget();
		Stories.Data.get(level).remove(hall);
		Leftovers.after(helper, () -> {
			PersonalRequests.ENABLED = requests;
			Friendship.ENABLED = friendship;
			PersonalRequests.forget();
			Stories.Data.get(level).remove(hall);
		});
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", LECTERN.offset(1, 0, 1));
		Jobs.employ(level, dara, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setVillagerXp(5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Quest quest = QaHallRolesDuty1103GameTests.levelUpAccepted(helper, dara, player);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: No job didn't take: " + dara.getVillagerData());
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			VillageQuests.tick(level, hall, entity);
			if (PersonalRequests.of(dara) == quest) {
				int before = Friendship.points(dara, player.getUUID());
				int missed = PersonalRequests.overdue(level, hall, quest.due);
				helper.fail("Dara, jobless, still has her open \"" + quest.title().getString() + "\" request, which no jobless villager can finish; at its"
					+ " deadline it was missed (" + missed + ") and the player lost " + (before - Friendship.points(dara, player.getUUID()))
					+ " friendship; told " + seen.get(seen.size() - 1));
			}
			helper.succeed();
		});
	}
}
