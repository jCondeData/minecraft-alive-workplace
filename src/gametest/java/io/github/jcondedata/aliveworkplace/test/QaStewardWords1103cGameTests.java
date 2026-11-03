package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B469, B471 and B475 (2026-11-03): every sentence the three bugs name, at 0, 1 and 2, in the exact English of
 * en_us.json, reached the way a player meets it where that can be done (the City Plan's action-bar lines, the Steward's
 * wishes, the desk's Decline). One reads singular, 0 and 2 read plural, and no line shows a raw translation key.
 */
public class QaStewardWords1103cGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static void check(List<String> wrong, String got, String want) {
		if (got.contains("aliveworkplace.")) {
			wrong.add("raw key in \"" + got + "\"");
		} else if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
	}

	private static List<String> overlay(List<QaStewardDeskJobsGameTests.Seen> heard, String key) {
		return QaStewardDeskJobsGameTests.lines(heard, key, true).stream().map(s -> s.text().getString()).toList();
	}

	/** B469: painting cells nearer another village tells the player "1 cell is", "2 cells are"; 0 reads plural (and is never said). */
	//$ gametest_ticks_batch HUGE '80' '"qaWords1103cOtherVillage"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaWords1103cOtherVillage")
	public void cellsNearerAnotherVillageCountOneInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 128;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(4, 2, 27), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(4, 2, 3));
		BlockPos other = helper.absolutePos(new BlockPos(4, 2, 27));
		List<QaStewardDeskJobsGameTests.Seen> heard = new ArrayList<>();
		ServerPlayer player = QaStewardDeskJobsGameTests.listener(helper, "qa-words-plan", heard);
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "setup: no zone");
		int a = CityPlan.cellAt(hall, hall.offset(1, 0, 17));
		int b = CityPlan.cellAt(hall, hall.offset(9, 0, 17));
		int c = CityPlan.cellAt(hall, hall.offset(-7, 0, 17));
		int mine = CityPlan.cellAt(hall, hall.offset(1, 0, 1));
		for (int cell : new int[] {a, b, c}) {
			BlockPos centre = CityPlan.cellCentre(hall, cell);
			helper.assertTrue(centre.distSqr(other) < centre.distSqr(hall), "setup: cell " + cell + " isn't nearer the other hall");
		}
		helper.assertTrue(a != b && b != c && a != c, "setup: the cells aren't distinct");
		String key = "message.aliveworkplace.city_plan.other_village";
		helper.runAfterDelay(5, () -> {
			List<String> wrong = new ArrayList<>();
			CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, QaCityPlanGameTests.cells(mine))));
			helper.assertTrue(overlay(heard, key).isEmpty() && overlay(heard, key + ".one").isEmpty(),
				"painting no refused cell still said " + overlay(heard, key) + overlay(heard, key + ".one"));
			CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, QaCityPlanGameTests.cells(mine, a))));
			List<String> one = overlay(heard, key + ".one");
			helper.assertTrue(one.size() == 1 && overlay(heard, key).isEmpty(), "one refused cell said " + one + " / " + overlay(heard, key));
			check(wrong, one.get(0), "1 cell is nearer another village: left out");
			CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, QaCityPlanGameTests.cells(b, c))));
			List<String> two = overlay(heard, key);
			helper.assertTrue(two.size() == 1, "two refused cells said " + two);
			check(wrong, two.get(0), "2 cells are nearer another village: left out");
			check(wrong, Words.counted(key, 0, 0).getString(), "0 cells are nearer another village: left out");
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	/** B469: the plan right-clicked outside every zone says "(0 zones)", "(1 zone)", "(2 zones)", with the village's name. */
	//$ gametest_ticks_batch BIG '40' '"qaWords1103cNoZone"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaWords1103cNoZone")
	public void planOutsideEveryZoneCountsItsZones(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(8, 2, 8), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(8, 2, 8));
		List<QaStewardDeskJobsGameTests.Seen> heard = new ArrayList<>();
		ServerPlayer player = QaStewardDeskJobsGameTests.listener(helper, "qa-words-zone", heard);
		BlockPos stand = helper.absolutePos(new BlockPos(3, 2, 3));
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, hall);
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		String village = VillageHalls.name(level, hall).getString();
		String key = "message.aliveworkplace.city_plan.no_zone";
		List<String> wrong = new ArrayList<>();
		String[] want = {"No zone here on " + village + "'s plan (0 zones)", "No zone here on " + village + "'s plan (1 zone)",
			"No zone here on " + village + "'s plan (2 zones)"};
		for (int zones = 0; zones <= 2; zones++) {
			if (zones > 0) {
				helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Z" + zones, ""))),
					"setup: zone " + zones + " refused");
			}
			helper.assertTrue(entity.plan().zones().size() == zones && entity.plan().zoneAt(hall, player.blockPosition()).isEmpty(),
				"setup: " + entity.plan().zones().size() + " zones, standing in one: " + entity.plan().zoneAt(hall, player.blockPosition()).isPresent());
			heard.clear();
			ModItems.CITY_PLAN.use(level, player, InteractionHand.MAIN_HAND);
			List<String> said = new ArrayList<>(overlay(heard, key));
			said.addAll(overlay(heard, key + ".one"));
			helper.assertTrue(said.size() == 1, "at " + zones + " zones the plan said " + said + " (keys " + QaStewardDeskJobsGameTests.keys(heard) + ")");
			helper.assertTrue(overlay(heard, key + ".one").size() == (zones == 1 ? 1 : 0), "at " + zones + " zones the wrong form was picked: " + said);
			check(wrong, said.get(0), want[zones]);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B471: every Steward reason the bug names, as his wish reads it (and as the desk's proposal would), at 0, 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stewardWishesCountOneInTheSingular(GameTestHelper helper) {
		Map<String, String[]> lines = new LinkedHashMap<>(); // why -> {at 0, at 1, at 2}
		lines.put("food", new String[] {"The store is 0 meals short", "The store is 1 meal short", "The store is 2 meals short"});
		lines.put("better_homes", new String[] {"0 grown-ups live in plain homes", "1 grown-up lives in a plain home", "2 grown-ups live in plain homes"});
		lines.put("homes_class", new String[] {"0 households want a grander home", "1 household wants a grander home", "2 households want a grander home"});
		lines.put("research", new String[] {"Nothing is being researched, and 0 topics are open", "Nothing is being researched, and 1 topic is open",
			"Nothing is being researched, and 2 topics are open"});
		lines.put("street_lamps", new String[] {"0 beds are in the dark", "1 bed is in the dark", "2 beds are in the dark"});
		lines.put("chapel", new String[] {"0 couples are courting and there's no chapel to marry in", "1 couple is courting and there's no chapel to marry in",
			"2 couples are courting and there's no chapel to marry in"});
		lines.put("lookout_tower", new String[] {"The village is 0 guards short", "The village is 1 guard short", "The village is 2 guards short"});
		lines.put("barracks", new String[] {"The town is 0 guards short", "The town is 1 guard short", "The town is 2 guards short"});
		List<String> wrong = new ArrayList<>();
		StewardRules.Effect effect = new StewardRules.Effect(StewardRules.Kind.ASSIGN_JOBS, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		lines.forEach((why, want) -> {
			for (int n = 0; n <= 2; n++) {
				StewardWishes.Wish wish = new StewardWishes.Wish(AliveWorkplace.id("qa_words"), effect, 50, "steward.aliveworkplace.why." + why, List.of((long) n, 7L));
				check(wrong, wish.reason().getString(), want[n]);
			}
		});
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B475: declining the jobs proposal on the desk quotes its name: "Give 1 villager a job", "Give 2 villagers jobs". */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cDecline"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cDecline")
	public void decliningTheJobsProposalQuotesItsCountedName(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<QaStewardDeskJobsGameTests.Seen> heard = new ArrayList<>();
		QaStewardDeskJobsGameTests.Village v = QaStewardDeskJobsGameTests.village(helper, heard, QaStewardDeskJobsGameTests.jobsWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(11, 2, 13), Blocks.FLETCHING_TABLE);
		QaStewardDeskJobsGameTests.jobless(helper, new BlockPos(12, 2, 15));
		QaStewardDeskJobsGameTests.jobless(helper, new BlockPos(10, 2, 15));
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		List<StewardDesk.Proposal> proposals = StewardDesk.of(v.level(), v.hall()).proposals();
		helper.assertTrue(proposals.size() == 1 && proposals.get(0).isJobs() && proposals.get(0).jobs().size() == 2, "setup: proposals " + proposals);
		ChoiceMenu menu = QaStewardDeskJobsGameTests.desk(v.owner(), v.hall());
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		menu.press(StewardDeskPage.DECLINE, v.owner());
		List<String> declined = overlay(heard, "message.aliveworkplace.steward.desk.declined");
		helper.assertTrue(declined.size() == 1, "declining said " + declined + " (keys " + QaStewardDeskJobsGameTests.keys(heard) + ")");
		check(wrong, declined.get(0), "Declined: \"Give 2 villagers jobs\" won't be proposed again for 3 days");
		// the same line as the desk builds it for one job and for none (the Steward never proposes none, but the title still counts)
		check(wrong, Component.translatable("message.aliveworkplace.steward.desk.declined", StewardJobs.title(1), StewardDesk.DECLINE_DAYS).getString(),
			"Declined: \"Give 1 villager a job\" won't be proposed again for 3 days");
		check(wrong, Component.translatable("message.aliveworkplace.steward.desk.declined", StewardJobs.title(0), StewardDesk.DECLINE_DAYS).getString(),
			"Declined: \"Give 0 villagers jobs\" won't be proposed again for 3 days");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B475: declining a one-villager jobs proposal through the desk reads "Give 1 villager a job", quoted. */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cDeclineOne"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cDeclineOne")
	public void decliningAOneJobProposalReadsInTheSingular(GameTestHelper helper) {
		List<QaStewardDeskJobsGameTests.Seen> heard = new ArrayList<>();
		QaStewardDeskJobsGameTests.Village v = QaStewardDeskJobsGameTests.village(helper, heard, QaStewardDeskJobsGameTests.jobsWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		QaStewardDeskJobsGameTests.jobless(helper, new BlockPos(12, 2, 15));
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		List<StewardDesk.Proposal> proposals = StewardDesk.of(v.level(), v.hall()).proposals();
		helper.assertTrue(proposals.size() == 1 && proposals.get(0).isJobs() && proposals.get(0).jobs().size() == 1, "setup: proposals " + proposals);
		ChoiceMenu menu = QaStewardDeskJobsGameTests.desk(v.owner(), v.hall());
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		menu.press(StewardDeskPage.DECLINE, v.owner());
		List<String> declined = overlay(heard, "message.aliveworkplace.steward.desk.declined");
		helper.assertTrue(declined.size() == 1, "declining said " + declined + " (keys " + QaStewardDeskJobsGameTests.keys(heard) + ")");
		List<String> wrong = new ArrayList<>();
		check(wrong, declined.get(0), "Declined: \"Give 1 villager a job\" won't be proposed again for 3 days");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
