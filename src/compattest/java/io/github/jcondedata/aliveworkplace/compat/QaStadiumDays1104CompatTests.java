package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.StadiumDaysPower;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-04) for ROADMAP 35.24, written from its text: "Stadium Days, every second day in the afternoon: two to four
 * visiting trainers (from the villages of its realm or its caravan partners; travelling trainers from Journeyman to Master
 * when those have none) stand in the trainer boxes until dusk. Players challenge them as they do the village's own ...
 * The village's own trainers take on the visitors in bouts ... both gaining battle XP ... Villagers off work fill the
 * stands, and those who watched get +5 mood." These add what QaStadium1028CompatTests, QaStadiumVisitorsCompatTests and
 * the power's own tests leave out: the count over a month of Stadium Days (never fewer than two or more than four, and
 * every count seen), the travellers' ranks and name over many days, the Stadium Day on the server's own clock (an even
 * afternoon brings them, the odd day's sends them off), stands with fewer seats than fans, a village with no trainer of
 * its own, and the visitor's own trainer at home sharing the bout's XP.
 */
public class QaStadiumDays1104CompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 3);
	private static final BlockPos WEST_POST = new BlockPos(5, 2, 15);
	private static final BlockPos EAST_POST = new BlockPos(25, 2, 15);

	/** A finished Stadium round the test's hall with {@code seats} carpets (from x = 12) for seats, as the other Stadium tests stage it. */
	private static WonderSite stadium(GameTestHelper helper, int seatCount) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.assertTrue(Wonders.loaded().containsKey(StadiumDays.STADIUM), "the Stadium's file didn't load with Cobblemon");
		helper.assertTrue(Wonders.ENABLED, "Wonders are off");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 24;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(WEST_POST, ModBlocks.TRAINING_POST);
		helper.setBlock(EAST_POST, ModBlocks.TRAINING_POST);
		BlockPos hall = helper.absolutePos(HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(StadiumDays.STADIUM, hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 9);
		List<BlockPos> seats = new ArrayList<>();
		for (int x = 12; x < 12 + seatCount; x++) {
			helper.setBlock(new BlockPos(x, 2, 24), Blocks.RED_CARPET);
			seats.add(helper.absolutePos(new BlockPos(x, 2, 24)));
		}
		StadiumDays.setSpotsForTest(site.id(), new StadiumDays.Spots(helper.absolutePos(new BlockPos(15, 2, 15)),
			List.of(helper.absolutePos(new BlockPos(6, 2, 15)), helper.absolutePos(new BlockPos(24, 2, 15)),
				helper.absolutePos(new BlockPos(6, 2, 16)), helper.absolutePos(new BlockPos(24, 2, 14))),
			List.of(helper.absolutePos(WEST_POST), helper.absolutePos(EAST_POST)), List.copyOf(seats), helper.absolutePos(new BlockPos(15, 2, 28))));
		Wonders.forgetPowers();
		PartnerShowsCompatTests.after(helper, () -> {
			StadiumDays.setSpotsForTest(site.id(), null);
			manager.remove(site.id());
			Wonders.forgetPowers();
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		return manager.site(site.id()).orElseThrow();
	}

	private static long stadiumDay(ServerLevel level) {
		long today = Chronicle.day(level);
		return today % 2 == 0 ? today : today + 1;
	}

	private static StadiumDays.Spots spots(GameTestHelper helper, WonderSite site) {
		return StadiumDays.spots(helper.getLevel(), site).orElseThrow(() -> new GameTestAssertException("no spots"));
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name, int tier) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(tier));
		v.setVillagerXp(Math.max(1, VillagerData.getMinXpPerLevel(tier)));
		v.setCustomName(Component.literal(name));
		return v;
	}

	/**
	 * "Two to four visiting trainers", the Stadium's own file read as every second day with 2 to 4: thirty Stadium Days in
	 * a row each bring at least two and at most four (never more than the four box spots), every one of 2, 3 and 4 is seen,
	 * and each set leaves at dusk.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaStadiumDaysCount"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaStadiumDaysCount")
	public void twoToFourVisitorsOnEveryStadiumDayForAMonth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		StadiumDaysPower power = StadiumDays.power(level, site);
		helper.assertTrue(power != null, "a finished, standing Stadium grants no stadium_days power");
		helper.assertTrue(power.everyDays() == 2 && power.minVisitors() == 2 && power.maxVisitors() == 4,
			"the Stadium's file: every " + power.everyDays() + " days, " + power.minVisitors() + " to " + power.maxVisitors() + " visitors");
		long first = stadiumDay(level);
		Set<Integer> seen = new TreeSet<>();
		List<Integer> counts = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			long day = first + 2L * i;
			StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
			int n = StadiumDays.visitors(level, site, spots).size();
			counts.add(n);
			seen.add(n);
			helper.assertTrue(n >= 2 && n <= 4, "Stadium Day " + day + " brought " + n + " visitors (all so far: " + counts + ")");
			StadiumDays.tick(level, day, StadiumDays.DUSK);
			helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "Stadium Day " + day + "'s visitors still there at dusk");
		}
		helper.assertTrue(seen.equals(Set.of(2, 3, 4)), "over 30 Stadium Days the counts were only " + seen + ": " + counts);
		helper.succeed();
	}

	/**
	 * "Travelling trainers from Journeyman to Master when those have none": a Stadium with no realm and no caravan partner
	 * gets only travellers, over twenty Stadium Days never a Novice or an Apprentice (ranks 3 to 5, each seen), none from a
	 * village, each called "the Traveler" (en_us), each a Trainer a player can challenge.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaStadiumDaysTravellers"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaStadiumDaysTravellers")
	public void travellersAreJourneymanToMasterAndCalledTheTraveler(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		helper.assertTrue(Wonders.linked(level, site.hall()).isEmpty(), "setup: the test's village has a realm or a partner: " + Wonders.linked(level, site.hall()));
		long first = stadiumDay(level);
		Set<Integer> ranks = new TreeSet<>();
		for (int i = 0; i < 20; i++) {
			long day = first + 2L * i;
			StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
			for (Villager v : StadiumDays.visitors(level, site, spots)) {
				int tier = Trainers.tier(v);
				ranks.add(tier);
				String name = v.getDisplayName().getString();
				helper.assertTrue(tier >= 3 && tier <= 5, "day " + day + ": a traveller of rank " + tier + " (" + Trainers.title(v).getString() + ")");
				helper.assertTrue(StadiumDays.visitor(v).home().isEmpty(), "day " + day + ": a traveller from a village " + StadiumDays.visitor(v).home());
				helper.assertTrue(name.equals("the Traveler"), "day " + day + ": a traveller called '" + name + "', not 'the Traveler'");
				helper.assertTrue(Trainers.isTrainer(v), "day " + day + ": a visitor a player can't challenge (not a Trainer): " + v.getVillagerData().getProfession());
			}
			StadiumDays.tick(level, day, StadiumDays.DUSK);
		}
		helper.assertTrue(ranks.equals(Set.of(3, 4, 5)), "over 20 Stadium Days the travellers' ranks were only " + ranks);
		helper.succeed();
	}

	/**
	 * The Stadium Day on the server's own clock, not a test's call: the level's time set to an even day's afternoon brings
	 * the visitors within a second; set to the next (odd) day's afternoon, with no player near, they're gone and nobody
	 * comes.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaStadiumDaysClock"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaStadiumDaysClock")
	public void theServersOwnClockHoldsTheStadiumDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		long before = level.getDayTime();
		PartnerShowsCompatTests.after(helper, () -> level.setDayTime(before));
		long day = stadiumDay(level);
		level.setDayTime((day - 1) * VillageNeeds.DAY + 6500);
		helper.assertTrue(Chronicle.day(level) == day, "setup: the chronicle's day " + Chronicle.day(level) + ", not " + day);
		helper.runAfterDelay(40, () -> {
			int n = StadiumDays.visitors(level, site, spots).size();
			helper.assertTrue(n >= 2 && n <= 4, "on the even day " + day + " at 6500 the server's own tick brought " + n + " visitors");
			level.setDayTime(day * VillageNeeds.DAY + 6500);
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(Chronicle.day(level) == day + 1, "setup: the next day is " + Chronicle.day(level));
				helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "on the odd day " + (day + 1) + " at 6500: "
					+ StadiumDays.visitors(level, site, spots).size() + " visitors in the boxes");
				helper.succeed();
			});
		});
	}

	/**
	 * "Villagers off work fill the stands": five idle villagers by three seats, three sit (one to a seat) and the other two
	 * stay standing; the three who sat have "watched a Stadium Day" (+5), the two who couldn't, nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumDaysFullStands"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumDaysFullStands")
	public void fiveFansAndThreeSeatsSeatThree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		List<Villager> fans = new ArrayList<>();
		for (int x = 11; x <= 15; x++) {
			Villager fan = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 23));
			fan.setNoAi(true);
			fan.getBrain().setActiveActivityIfPossible(Activity.IDLE);
			fans.add(fan);
		}
		for (int i = 0; i < 6; i++) {
			StadiumDays.tick(level, day, StadiumDays.AFTERNOON + i * 20L);
		}
		List<Villager> seated = fans.stream().filter(Villager::isPassenger).toList();
		helper.assertTrue(seated.size() == 3, seated.size() + " of 5 fans sat on the 3 seats");
		helper.assertTrue(seated.stream().map(v -> v.getVehicle().blockPosition()).distinct().count() == 3, "two fans on one seat");
		for (Villager fan : fans) {
			var mood = StadiumDays.mood(level, fan);
			if (fan.isPassenger()) {
				helper.assertTrue(mood != null && mood.points() == 5 && mood.reason().getString().equals("watched a Stadium Day"),
					"a seated fan's mood: " + (mood == null ? "none" : mood.points() + " '" + mood.reason().getString() + "'"));
			} else {
				helper.assertTrue(mood == null, "a fan who found no seat has the Stadium Day mood");
			}
		}
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		fans.forEach(Villager::stopRiding);
		helper.succeed();
	}

	/**
	 * A village with no trainer of its own: the visitors stand in the boxes all afternoon with nobody to fight (no bout in
	 * chat, at 7000 or later), still there at 11999, gone at dusk.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumDaysNoHome"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumDaysNoHome")
	public void noTrainerAtHomeMeansNoBoutButTheVisitorsStay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(15, 2, 12)), 0);
		PartnerShowsCompatTests.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(talker.player().getUUID()) != null) {
				level.getServer().getPlayerList().remove(talker.player());
			}
		});
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		int n = StadiumDays.visitors(level, site, spots).size();
		helper.assertTrue(n >= 2, "setup: visitors " + n);
		for (long t = StadiumDays.FIRST_BOUT; t < StadiumDays.DUSK; t += StadiumDays.BOUT_EVERY) {
			StadiumDays.tick(level, day, t);
		}
		StadiumDays.tick(level, day, StadiumDays.DUSK - 1);
		List<String> bouts = talker.said().stream().filter(s -> s.startsWith("At the Stadium")).toList();
		helper.assertTrue(bouts.isEmpty(), "a bout with no trainer at home: " + bouts);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).size() == n, "at 11999 " + StadiumDays.visitors(level, site, spots).size() + " of " + n + " visitors");
		// the player stands 3 blocks from the field: out of the boxes the visitors walk to the gate; the player leaves first
		level.getServer().getPlayerList().remove(talker.player());
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "visitors still there at dusk");
		helper.succeed();
	}

	/**
	 * "Both gaining battle XP", the visiting side whole: a visitor who stands for a trainer of a partner village (loaded) is
	 * beaten by the village's Master; the Master, the visitor and the trainer at home the visitor stands for each count one
	 * battle more and gain XP.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumDaysXpAtHome"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumDaysXpAtHome")
	public void theVisitorsOwnTrainerAtHomeGainsTheBoutsXp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper, 3);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		BlockPos hall = site.hall();
		Villager ash = trainer(helper, new BlockPos(15, 2, 8), "Ash", 5);
		Villager mara = trainer(helper, new BlockPos(28, 2, 28), "Mara", 2);
		Villager visiting = trainer(helper, new BlockPos(6, 2, 15), "Mara of Riverton", 2);
		BlockPos riverton = hall.offset(400, 0, 0);
		ModAttachments.STADIUM_VISITOR.set(visiting, new StadiumDays.Visitor(hall, site.id(), Optional.of(riverton), mara.getUUID(), day, false, 0));
		int ashXp = ash.getVillagerXp();
		int visitingXp = visiting.getVillagerXp();
		int maraXp = mara.getVillagerXp();
		StadiumDays.Bout bout = StadiumDays.bout(level, hall, spots, ash, visiting, day);
		helper.assertTrue(bout.winner() == ash, "the Master lost to an Apprentice");
		helper.assertTrue(ash.getVillagerXp() > ashXp && visiting.getVillagerXp() > visitingXp,
			"the two sides' XP: Ash " + ashXp + " → " + ash.getVillagerXp() + ", the visitor " + visitingXp + " → " + visiting.getVillagerXp());
		helper.assertTrue(mara.getVillagerXp() > maraXp, "the trainer at home the visitor stands for gained no XP: " + maraXp + " → " + mara.getVillagerXp());
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0) == 1 && ModAttachments.TRAINER_BATTLES.getOrElse(visiting, 0) == 1
			&& ModAttachments.TRAINER_BATTLES.getOrElse(mara, 0) == 1, "battles: Ash " + ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0)
			+ ", visitor " + ModAttachments.TRAINER_BATTLES.getOrElse(visiting, 0) + ", Mara at home " + ModAttachments.TRAINER_BATTLES.getOrElse(mara, 0));
		helper.succeed();
	}
}
