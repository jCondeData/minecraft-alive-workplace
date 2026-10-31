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
import io.github.jcondedata.aliveworkplace.work.Bank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-d-1028-0109) for ROADMAP 35.24, written from its text: "every second day in the afternoon: two to four
 * visiting trainers ... stand in the trainer boxes until dusk"; "Players challenge them as they do the village's own
 * (CobbleDollars, once a day each, money only)"; bouts "with the results in chat and the chronicle"; "Villagers off work
 * fill the stands, and those who watched get +5 mood"; "a damaged Stadium holds no Stadium Days". These add what the
 * power's own tests leave out: each clock boundary from both sides and the next Stadium Day, the chat lines a player
 * reads with their real numbers, the Stadium damaged in the middle of a Stadium Day, a visitor gone mid-day, a visitor
 * and the day's record through a save, two visitors beaten on one day, and who sits in the stands and for how long the
 * +5 lasts (the 35.24 decisions: until the next Stadium Day).
 */
public class QaStadium1028CompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 3);
	private static final BlockPos WEST_POST = new BlockPos(5, 2, 15);
	private static final BlockPos EAST_POST = new BlockPos(25, 2, 15);

	/** A finished Stadium round the test's hall, its spots in the area (as StadiumPowerCompatTests stages it). */
	private static WonderSite stadium(GameTestHelper helper) {
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
		for (int x = 9; x <= 21; x++) {
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

	private static int expected(WonderSite site, long day) {
		return 2 + StadiumDays.random(site.id(), day).nextInt(3);
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name, int tier) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(tier));
		v.setVillagerXp(Math.max(1, net.minecraft.world.entity.npc.VillagerData.getMinXpPerLevel(tier)));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void damage(ServerLevel level, WonderSite site, boolean damaged) {
		WonderManager.get(level).setKept(site.id(), new WonderSite.Kept(damaged ? 70 : 100, 10, damaged, 9));
	}

	/**
	 * The clock, each edge from both sides: 5999 nobody, 6000 the visitors; 11999 still there, 12000 gone; the odd day
	 * after nobody at any hour of its afternoon; the next even day a new set (that day's own count) in the boxes.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumClock"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumClock")
	public void theStadiumDaysEdgesFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		helper.assertTrue(StadiumDays.AFTERNOON == 6000 && StadiumDays.DUSK == 12000, "afternoon " + StadiumDays.AFTERNOON + ", dusk " + StadiumDays.DUSK);
		StadiumDays.tick(level, day, 5999);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "visitors at 5999, before the afternoon");
		StadiumDays.tick(level, day, 6000);
		int n = StadiumDays.visitors(level, site, spots).size();
		helper.assertTrue(n == expected(site, day), "at 6000: " + n + " visitors, the day's random says " + expected(site, day));
		StadiumDays.tick(level, day, 11999);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).size() == n, "gone before dusk, at 11999: " + StadiumDays.visitors(level, site, spots).size());
		StadiumDays.tick(level, day, 12000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "still in the boxes at 12000 (dusk)");
		for (long t : new long[] {6000, 9000, 11999}) {
			StadiumDays.tick(level, day + 1, t);
			helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "visitors on the odd day " + (day + 1) + " at " + t);
		}
		StadiumDays.tick(level, day + 2, 6000);
		List<Villager> next = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(next.size() == expected(site, day + 2), "the next Stadium Day (day " + (day + 2) + "): " + next.size()
			+ " visitors, its random says " + expected(site, day + 2));
		helper.assertTrue(next.stream().allMatch(v -> StadiumDays.visitor(v).day() == day + 2), "the next day's visitors carry the wrong day");
		StadiumDays.tick(level, day + 2, 12000);
		helper.succeed();
	}

	/**
	 * The chat a player at the Stadium reads, with the real numbers: "Stadium Day at <village>: <n> visiting trainers stand
	 * in the boxes until dusk. Challenge them!" with n the visitors in the boxes, then at 7000 the bout, "At the Stadium:
	 * <winner> beats <loser>", naming the village's Master and a visitor by their titles; the chronicle has the same bout.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumChat"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumChat")
	public void thePlayerReadsTheStadiumDayAndTheBout(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(15, 2, 12)), 0);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(talker.player()));
		Villager ash = trainer(helper, new BlockPos(15, 2, 8), "Ash", 5);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		String village = VillageHalls.name(level, site.hall()).getString();
		String day1 = "Stadium Day at " + village + ": " + visitors.size() + " visiting trainers stand in the boxes until dusk. Challenge them!";
		helper.assertTrue(talker.said().contains(day1), "the player read " + talker.said() + ", not '" + day1 + "'");
		StadiumDays.tick(level, day, StadiumDays.FIRST_BOUT);
		Villager fought = visitors.stream().filter(v -> StadiumDays.visitor(v).fought()).findFirst()
			.orElseThrow(() -> new GameTestAssertException("no bout at 7000"));
		String a = Trainers.title(ash).getString();
		String b = Trainers.title(fought).getString();
		helper.assertTrue(a.equals("Master Trainer Ash"), "the village's Master is titled " + a);
		List<String> bouts = talker.said().stream().filter(s -> s.startsWith("At the Stadium: ")).toList();
		helper.assertTrue(bouts.size() == 1 && (bouts.get(0).equals("At the Stadium: " + a + " beats " + b) || bouts.get(0).equals("At the Stadium: " + b + " beats " + a)),
			"the bout in chat: " + bouts + " (between " + a + " and " + b + ")");
		helper.assertTrue(!bouts.get(0).contains("%s") && !bouts.get(0).contains("message.aliveworkplace"), "an unrendered line: " + bouts.get(0));
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.succeed();
	}

	/**
	 * The Stadium damaged in the middle of a Stadium Day: the visitors go (nobody watches, so they vanish) and no bout is
	 * fought while it's damaged; repaired the same afternoon, no second set comes (one Stadium Day a day).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumDamagedMidDay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumDamagedMidDay")
	public void aStadiumDamagedMidDaySendsTheVisitorsOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		Villager ash = trainer(helper, new BlockPos(15, 2, 8), "Ash", 5);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(visitors.size() >= 2, "setup: visitors " + visitors.size());
		damage(level, site, true);
		StadiumDays.tick(level, day, 9000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "visitors still in the boxes of a damaged Stadium: "
			+ StadiumDays.visitors(level, site, spots).size());
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0) == 0, "a bout at a damaged Stadium: " + ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0));
		damage(level, site, false);
		StadiumDays.tick(level, day, 10000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "a second set came the same day once repaired: "
			+ StadiumDays.visitors(level, site, spots).size());
		helper.succeed();
	}

	/**
	 * A visitor gone in the middle of the day (killed): nobody takes their box (the day's visitors came once), and the
	 * bouts go on with the rest, one an hour from 7000, until each remaining visitor has fought.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumVisitorGone"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumVisitorGone")
	public void aVisitorGoneMidDayLeavesTheRestToFight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		Villager ash = trainer(helper, new BlockPos(15, 2, 8), "Ash", 5);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		int n = visitors.size();
		helper.assertTrue(n >= 2, "setup: visitors " + n);
		visitors.get(0).kill();
		StadiumDays.tick(level, day, 6500);
		List<Villager> left = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(left.size() == n - 1, "after one was killed: " + left.size() + " of " + n + " (a replacement came, or more left)");
		for (int i = 0; i < n; i++) {
			StadiumDays.tick(level, day, StadiumDays.FIRST_BOUT + i * StadiumDays.BOUT_EVERY);
		}
		left = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(left.size() == n - 1 && left.stream().allMatch(v -> StadiumDays.visitor(v).fought()),
			"not every remaining visitor fought: " + left.stream().map(v -> StadiumDays.visitor(v).fought()).toList());
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0) == n - 1, "Ash fought " + ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0)
			+ " bouts, not " + (n - 1));
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "still there at dusk");
		helper.succeed();
	}

	/**
	 * Save and reload: a visitor saved and loaded back (its chunk reloaded mid-day) is still that day's visitor of this
	 * Stadium in its box, and is sent off at dusk; the day's record (aliveworkplace_stadium_days) saved and loaded keeps
	 * the day, so a restart in the afternoon brings no second set.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumReload"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumReload")
	public void aVisitorAndTheDaySurviveASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		int n = visitors.size();
		Villager first = visitors.get(0);
		StadiumDays.Visitor record = StadiumDays.visitor(first);
		CompoundTag tag = new CompoundTag();
		first.save(tag);
		first.discard();
		Villager back = EntityType.VILLAGER.create(level);
		back.load(tag);
		level.addFreshEntity(back);
		helper.assertTrue(StadiumDays.isVisitor(back) && record.equals(StadiumDays.visitor(back)), "the visitor after a reload: " + StadiumDays.visitor(back)
			+ ", before " + record);
		helper.assertTrue(back.getDisplayName().getString().equals(first.getDisplayName().getString()), "the name after a reload: " + back.getDisplayName().getString());
		helper.assertTrue(Trainers.tier(back) == Trainers.tier(first), "the rank after a reload: " + Trainers.tier(back));
		StadiumDays.tick(level, day, 9000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).size() == n, "after the reload: " + StadiumDays.visitors(level, site, spots).size() + " of " + n);
		CompoundTag saved = StadiumDays.Data.get(level).save(new CompoundTag(), level.registryAccess());
		StadiumDays.Data loaded = StadiumDays.Data.load(saved, level.registryAccess());
		helper.assertTrue(loaded.held(site.id()) == day, "the day's record after a save: " + loaded.held(site.id()) + ", not " + day);
		helper.assertTrue(StadiumDays.Data.load(new CompoundTag(), level.registryAccess()).held(site.id()) == -1, "an old save without the record");
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty() && back.isRemoved(), "the reloaded visitor wasn't sent off at dusk");
		helper.succeed();
	}

	private static long dollars(ServerPlayer player) {
		Long d = Bank.EXTENSION.call(bank -> bank.balance(player), null);
		return d == null ? -1 : d;
	}

	/**
	 * "Once a day each": a player who beats two visitors the same day is paid for each, once; a second win over either
	 * pays nothing; money only, with CobbleDollars (nothing lands in the inventory).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumTwoWins"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumTwoWins")
	public void twoVisitorsBeatenTheSameDayPayOnceEach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		StadiumDays.tick(level, stadiumDay(level), StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(visitors.size() >= 2, "setup: visitors " + visitors.size());
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(15, 2, 12)), 0);
		ServerPlayer player = talker.player();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.assertTrue(dollars(player) == 0, "setup: CobbleDollars balance " + dollars(player));
		Villager a = visitors.get(0);
		Villager b = visitors.get(1);
		Trainers.battleOver(level.getServer(), a.getUUID(), player.getUUID(), true);
		long afterA = dollars(player);
		Trainers.battleOver(level.getServer(), b.getUUID(), player.getUUID(), true);
		long afterB = dollars(player);
		helper.assertTrue(afterA > 0 && afterB > afterA, "two visitors beaten: " + 0 + " → " + afterA + " → " + afterB);
		Trainers.battleOver(level.getServer(), a.getUUID(), player.getUUID(), true);
		Trainers.battleOver(level.getServer(), b.getUUID(), player.getUUID(), true);
		helper.assertTrue(dollars(player) == afterB, "a second win over the same visitors paid again: " + afterB + " → " + dollars(player));
		helper.assertTrue(player.getInventory().isEmpty(), "money only, but the inventory holds " + player.getInventory().getItem(0));
		helper.assertTrue(talker.said().stream().noneMatch(s -> s.contains("%s") || s.contains("message.aliveworkplace")), "an unrendered line: " + talker.said());
		StadiumDays.tick(level, stadiumDay(level), StadiumDays.DUSK);
		helper.succeed();
	}

	/**
	 * The stands: a villager resting (asleep in the schedule) by a seat doesn't sit, an idle one does; the one who didn't
	 * watch has no "watched a Stadium Day"; the watcher's +5 is there the next day and gone on the next Stadium Day.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStadiumStands"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStadiumStands")
	public void whoSitsInTheStandsAndHowLongTheMoodLasts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		long day = stadiumDay(level);
		Villager fan = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 23));
		fan.setNoAi(true);
		fan.getBrain().setActiveActivityIfPossible(Activity.IDLE);
		Villager resting = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 23));
		resting.setNoAi(true);
		resting.getBrain().setActiveActivityIfPossible(Activity.REST);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		helper.assertTrue(fan.isPassenger(), "the idle villager by a seat didn't sit");
		helper.assertTrue(!resting.isPassenger() && ModAttachments.STADIUM_WATCHED.get(resting) == null, "a resting villager went to the stands");
		helper.assertTrue(StadiumDays.mood(level, resting) == null, "the one who didn't watch has a Stadium Day mood");
		helper.assertTrue(StadiumDays.mood(level, fan) != null && StadiumDays.mood(level, fan).points() == 5, "the watcher's mood: " + StadiumDays.mood(level, fan));
		// the +5 lasts until the next Stadium Day: there on the day after, gone two days on
		ModAttachments.STADIUM_WATCHED.set(fan, Chronicle.day(level) - 1);
		helper.assertTrue(StadiumDays.mood(level, fan) != null, "the +5 was gone the day after the Stadium Day");
		ModAttachments.STADIUM_WATCHED.set(fan, Chronicle.day(level) - 2);
		helper.assertTrue(StadiumDays.mood(level, fan) == null, "the +5 outlived the next Stadium Day");
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.succeed();
	}

	/** The power's page line in each form the file can give, never an unrendered key: every afternoon, every 3 days, 3 visitors. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePowersLineInEachForm(GameTestHelper helper) {
		String daily = new StadiumDaysPower(1, 3, 3).describe().getString();
		helper.assertTrue(daily.equals("Stadium Days every afternoon: 3 visiting trainers in its boxes"), daily);
		String every = new StadiumDaysPower(3, 2, 4).describe().getString();
		helper.assertTrue(every.equals("Stadium Days every 3 days, in the afternoon: 2 to 4 visiting trainers in its boxes"), every);
		helper.assertTrue(new StadiumDaysPower(3, 2, 4).on(0) && !new StadiumDaysPower(3, 2, 4).on(2) && new StadiumDaysPower(3, 2, 4).on(3),
			"every 3 days isn't days 0, 3, 6");
		helper.succeed();
	}
}
