package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.cup.Cups;
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
import io.github.jcondedata.aliveworkplace.wonder.powers.TournamentHostPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import io.github.jcondedata.aliveworkplace.work.Bank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Stadium's powers (ROADMAP 35.24), with Cobblemon (the Stadium's file loads only with it): a Stadium Day brings
 * two to four visiting trainers into the boxes in the afternoon of every second day and sends them off at dusk, once a
 * day; a bout between the village's trainer and a visitor goes by rank and team strength on a fixed random, both gain
 * battle XP and the result is in the chronicle; a player's win over a visitor pays once a day; villagers in the stands
 * are 5 happier; the Stadium is its village's Arena for the Festival Cup; a damaged Stadium (or Wonders off) holds no
 * Stadium Days and hosts nothing. The Stadium is 53 across, wider than a test area, so its spots are given in the area
 * (two Training Posts 20 apart, a row of seats, the gate) with {@code StadiumDays.setSpotsForTest}; that the drawing's
 * own spots are found is checked against the real file.
 */
public class StadiumPowerCompatTests {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 3);
	private static final BlockPos WEST_POST = new BlockPos(5, 2, 15);
	private static final BlockPos EAST_POST = new BlockPos(25, 2, 15);

	/** A finished Stadium in the village round the test's hall, its spots in the area; undone when the test ends. */
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
		List<BlockPos> seats = new java.util.ArrayList<>();
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

	/** An even day (every second day is a Stadium Day) from today on. */
	private static long stadiumDay(ServerLevel level) {
		long today = Chronicle.day(level);
		return today % 2 == 0 ? today : today + 1;
	}

	private static StadiumDays.Spots spots(GameTestHelper helper, WonderSite site) {
		return StadiumDays.spots(helper.getLevel(), site).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no spots"));
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
	 * A Stadium Day: at the afternoon of an even day two to four visitors (the day's fixed random) stand in the boxes, all
	 * trainers of Journeyman to Master rank (no linked village: travellers), facing the field; another round brings no
	 * more; at dusk they're sent off (no player near: gone), and later that day nobody comes back. The power reads its
	 * numbers and its page line.
	 */
	//$ gametest_ticks_batch HUGE '100' '"stadiumDay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "stadiumDay")
	public void aStadiumDayBringsVisitorsAndSendsThemOffAtDusk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		StadiumDaysPower power = StadiumDays.power(level, site);
		helper.assertTrue(power != null && power.everyDays() == 2 && power.minVisitors() == 2 && power.maxVisitors() == 4, "the power: " + power);
		helper.assertTrue(power.describe().getString().equals("Stadium Days every second afternoon: 2 to 4 visiting trainers in its boxes"),
			power.describe().getString());
		helper.assertTrue(!WonderPowers.DECLARED.contains(StadiumDaysPower.TYPE) && !WonderPowers.DECLARED.contains(TournamentHostPower.TYPE),
			"still declared");
		// the morning, and an odd day's afternoon: nobody
		StadiumDays.tick(level, day, 3000);
		StadiumDays.tick(level, day + 1, 7000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "visitors outside a Stadium Day's afternoon");
		// the afternoon: two to four, as the day's random says
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		RandomSource random = StadiumDays.random(site.id(), day);
		int expected = 2 + random.nextInt(3);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(visitors.size() == expected && expected >= 2 && expected <= 4, "visitors: " + visitors.size() + ", expected " + expected);
		for (Villager v : visitors) {
			StadiumDays.Visitor record = StadiumDays.visitor(v);
			BlockPos box = spots.boxes().get(record.spot());
			helper.assertTrue(v.blockPosition().equals(box), v.getDisplayName().getString() + " at " + v.blockPosition() + ", not in its box " + box);
			helper.assertTrue(Trainers.isTrainer(v) && Trainers.tier(v) >= 3 && Trainers.tier(v) <= 5 && record.home().isEmpty() && record.day() == day,
				"a traveller of Journeyman to Master: " + Trainers.title(v).getString() + " " + record);
			helper.assertTrue(Trainers.title(v).getString().endsWith("Trainer the Traveler"), Trainers.title(v).getString());
		}
		helper.assertTrue(visitors.stream().map(v -> StadiumDays.visitor(v).spot()).distinct().count() == visitors.size(), "two in one spot");
		helper.assertTrue(StadiumDays.on(level, site) == (Chronicle.day(level) % 2 == 0 && level.getDayTime() % 24000 >= 6000
			&& level.getDayTime() % 24000 < 12000), "on() doesn't follow the clock");
		StadiumDays.tick(level, day, 9000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).size() == expected, "more came: " + StadiumDays.visitors(level, site, spots).size());
		// dusk: off they go, and none come back that day
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "still there at dusk: " + StadiumDays.visitors(level, site, spots).size());
		StadiumDays.tick(level, day, 8000);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "they came back the same day");
		helper.succeed();
	}

	/**
	 * A bout goes by rank and team strength (the day's seed fixed): a Master of the village beats a visiting Novice, and a
	 * visiting Master beats the village's Novice; both gain battle XP (8 for the win, 5 for the loss) and a battle each,
	 * and the result is in the chronicle and the bouts come one at a time from the afternoon.
	 */
	//$ gametest_ticks_batch HUGE '100' '"stadiumBout"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "stadiumBout")
	public void aBoutsResultAndXpFollowTheRanks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		BlockPos hall = site.hall();
		Villager ash = trainer(helper, new BlockPos(15, 2, 8), "Ash", 5);
		Villager novice = trainer(helper, new BlockPos(6, 2, 15), "Nell", 1);
		ModAttachments.STADIUM_VISITOR.set(novice, new StadiumDays.Visitor(hall, site.id(), Optional.empty(), new UUID(0, 0), day, false, 0));
		int ashXp = ash.getVillagerXp();
		int nellXp = novice.getVillagerXp();
		StadiumDays.Bout bout = StadiumDays.bout(level, hall, spots, ash, novice, day);
		helper.assertTrue(bout.winner() == ash && bout.loser() == novice, "the Master lost to a Novice: " + bout.winner().getDisplayName().getString());
		helper.assertTrue(bout.winnerXp() == Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN && bout.loserXp() == Trainers.XP_PER_BATTLE,
			"XP " + bout.winnerXp() + " / " + bout.loserXp());
		helper.assertTrue(ash.getVillagerXp() > ashXp && novice.getVillagerXp() > nellXp, "no XP: " + ash.getVillagerXp() + ", " + novice.getVillagerXp());
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0) == 1 && ModAttachments.TRAINER_BATTLES.getOrElse(novice, 0) == 1, "battles");
		helper.assertTrue(StadiumDays.visitor(novice).fought(), "the visitor's bout not marked");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		List<String> lines = entity.chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("At the Stadium, Master Trainer Ash beat ") && l.contains("Nell")), "chronicle: " + lines);
		// the other way round: the visitor the Master
		Villager visitingMaster = trainer(helper, new BlockPos(24, 2, 15), "Vera", 5);
		Villager homeNovice = trainer(helper, new BlockPos(16, 2, 8), "Ned", 1);
		ModAttachments.STADIUM_VISITOR.set(visitingMaster, new StadiumDays.Visitor(hall, site.id(), Optional.empty(), new UUID(0, 0), day, false, 1));
		StadiumDays.Bout back = StadiumDays.bout(level, hall, spots, homeNovice, visitingMaster, day);
		helper.assertTrue(back.winner() == visitingMaster && back.winnerXp() == 8 && back.loserXp() == 5, "the visiting Master lost to a Novice");
		// on the clock: one bout at 7000 with the village's best (Ash), the next at 8000
		visitingMaster.discard();
		novice.discard();
		homeNovice.discard();
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		StadiumDays.tick(level, day, 6900);
		helper.assertTrue(visitors.stream().noneMatch(v -> StadiumDays.visitor(v).fought()), "a bout before 7000");
		StadiumDays.tick(level, day, 7000);
		helper.assertTrue(visitors.stream().filter(v -> StadiumDays.visitor(v).fought()).count() == 1, "not one bout at 7000");
		StadiumDays.tick(level, day, 7500);
		helper.assertTrue(visitors.stream().filter(v -> StadiumDays.visitor(v).fought()).count() == 1, "two bouts before 8000");
		StadiumDays.tick(level, day, 8000);
		helper.assertTrue(visitors.stream().filter(v -> StadiumDays.visitor(v).fought()).count() == 2, "no second bout at 8000");
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0) == 3, "Ash fought " + ModAttachments.TRAINER_BATTLES.getOrElse(ash, 0));
		helper.succeed();
	}

	private static long wealth(ServerPlayer player) {
		Long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), null);
		return dollars != null ? dollars : player.getInventory().countItem(Items.EMERALD);
	}

	/** A player's win over a visitor pays, as over the village's own trainers, once a day; a loss pays nothing. */
	//$ gametest_ticks_batch HUGE '100' '"stadiumPrize"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "stadiumPrize")
	public void aPlayersWinOverAVisitorPaysOnceADay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		StadiumDays.tick(level, stadiumDay(level), StadiumDays.AFTERNOON);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		helper.assertTrue(!visitors.isEmpty(), "no visitors");
		Villager visitor = visitors.get(0);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos by = helper.absolutePos(new BlockPos(15, 2, 12));
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		long before = wealth(player);
		Trainers.battleOver(level.getServer(), visitor.getUUID(), player.getUUID(), false);
		helper.assertTrue(wealth(player) == before, "a loss paid");
		Trainers.battleOver(level.getServer(), visitor.getUUID(), player.getUUID(), true);
		long once = wealth(player);
		helper.assertTrue(once > before, "a win paid nothing: " + before + " → " + once);
		Trainers.battleOver(level.getServer(), visitor.getUUID(), player.getUUID(), true);
		helper.assertTrue(wealth(player) == once, "a second win the same day paid again: " + once + " → " + wealth(player));
		helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(visitor, 0) == 3, "the visitor's battles: " + ModAttachments.TRAINER_BATTLES.getOrElse(visitor, 0));
		helper.succeed();
	}

	/**
	 * The stands and the Cup: a villager off work by a seat sits down and is 5 happier ("watched a Stadium Day"); the
	 * Stadium is the village's Arena (its field the ring, its boxes, its seats) and its village has a Stadium for the Cup.
	 * Damaged: no Stadium Day, no Arena; with Wonders off, none either; repaired, the visitors come.
	 */
	//$ gametest_ticks_batch HUGE '100' '"stadiumDamaged"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "stadiumDamaged")
	public void aDamagedStadiumHoldsNoStadiumDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WonderSite site = stadium(helper);
		StadiumDays.Spots spots = spots(helper, site);
		long day = stadiumDay(level);
		BlockPos hall = site.hall();
		Villager fan = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 23));
		fan.setNoAi(true);
		fan.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE); // off work, whatever the test world's hour
		Moods.forget();
		int before = Moods.work(level, fan).score();
		// the Cup's Arena
		Optional<Arenas.Arena> arena = Arenas.find(level, hall);
		helper.assertTrue(arena.isPresent() && arena.get().ring().equals(spots.ring()) && arena.get().boxes().equals(spots.boxes().subList(0, 2))
			&& arena.get().seats().equals(spots.seats()), "the Stadium isn't the village's Arena: " + arena);
		helper.assertTrue(Cups.hasStadium(level, hall), "no Stadium for the Cup");
		// damaged: nothing
		damage(level, site, true);
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty(), "a damaged Stadium held a Stadium Day");
		helper.assertTrue(!fan.isPassenger(), "the stands opened at a damaged Stadium");
		helper.assertTrue(Arenas.find(level, hall).isEmpty() && !Cups.hasStadium(level, hall), "a damaged Stadium hosts the Cup");
		// Wonders off: nothing
		damage(level, site, false);
		boolean on = Wonders.ENABLED;
		Wonders.ENABLED = false;
		try {
			StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
			helper.assertTrue(StadiumDays.visitors(level, site, spots).isEmpty() && Arenas.find(level, hall).isEmpty(), "Stadium Days with Wonders off");
		} finally {
			Wonders.ENABLED = on;
		}
		// repaired: the visitors come, and the fan takes a seat and is happier
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		helper.assertTrue(!StadiumDays.visitors(level, site, spots).isEmpty(), "no visitors once repaired");
		helper.assertTrue(fan.isPassenger() && ModAttachments.STADIUM_WATCHED.get(fan) != null && ModAttachments.STADIUM_WATCHED.get(fan) == day,
			"the fan didn't sit in the stands (off work " + io.github.jcondedata.aliveworkplace.cup.CupDays.offWork(fan) + ", village "
				+ VillageHalls.nearest(level, fan.blockPosition()) + ", hall " + hall + ")");
		Moods.forget();
		Moods.Mood mood = Moods.work(level, fan);
		List<String> reasons = mood.good().stream().map(Component::getString).toList();
		ModAttachments.STADIUM_WATCHED.remove(fan);
		Moods.forget();
		int unwatched = Moods.work(level, fan).score();
		helper.assertTrue(reasons.contains("watched a Stadium Day") && mood.score() == Math.min(100, unwatched + StadiumDays.WATCHED_MOOD),
			"the watcher's mood: " + unwatched + " → " + mood.score() + " " + reasons + " (alone, before: " + before + ")");
		helper.succeed();
	}

	/**
	 * The drawing's own spots, as a finished Stadium stands: its two Training Posts in the trainer boxes at the field's
	 * ends, the field's centre spot between them, a spot before each post that's clear in the drawing, its hundreds of
	 * seats (the carpets on the tiers) and the gate out on the road in front of the gateway.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStadiumsDrawingHasItsBoxesAndSeats(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hallRel = new BlockPos(0, 1, 0);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(StadiumDays.STADIUM, helper.absolutePos(hallRel), UUID.randomUUID(), "Jesse", 1);
		BlockPos far = new BlockPos(7100, 70, 6000);
		manager.place(site.id(), new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
			io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()), far, net.minecraft.world.level.block.Rotation.NONE,
			net.minecraft.world.level.block.Mirror.NONE), far);
		manager.finish(site.id(), 2);
		try {
			WonderSite placed = manager.site(site.id()).orElseThrow();
			StadiumDays.Spots spots = StadiumDays.spots(level, placed).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
				"no spots in the drawing"));
			helper.assertTrue(spots.posts().equals(List.of(far.offset(8, 1, 21), far.offset(44, 1, 21))), "the posts: " + spots.posts());
			helper.assertTrue(spots.ring().equals(far.offset(26, 1, 21)), "the field's centre: " + spots.ring());
			helper.assertTrue(spots.boxes().equals(List.of(far.offset(9, 1, 21), far.offset(43, 1, 21), far.offset(9, 1, 22), far.offset(43, 1, 20))),
				"the box spots: " + spots.boxes());
			java.util.Set<BlockPos> solid = new java.util.HashSet<>();
			for (io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.Block b : io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.blocks(level, placed)) {
				if (!b.state().getCollisionShape(level, b.pos()).isEmpty()) {
					solid.add(b.pos());
				}
			}
			for (BlockPos spot : spots.boxes()) {
				helper.assertTrue(!solid.contains(spot) && !solid.contains(spot.above()) && solid.contains(spot.below()),
					"a box spot isn't room to stand: " + spot);
			}
			helper.assertTrue(spots.seats().size() > 300, "seats: " + spots.seats().size());
			helper.assertTrue(spots.gate().equals(far.offset(26, 1, -4)), "the gate: " + spots.gate());
		} finally {
			StadiumDays.forget();
			manager.remove(site.id());
			Wonders.forgetPowers();
		}
		helper.succeed();
	}
}
