package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-d-1102-1651) for lane A's fixes of B823 and B824, written from the bugs' words and 32.8, for what
 * PirateLandingGameTests and RaidOfOneGameTests leave out.
 * <ul>
 * <li>B823 / 32.8 ("the crew wade ashore at the beach nearest the ship, not from the land side"): a raid the Seer
 * foretold (29.16, the Seer branch of the hall's round) comes ashore at that beach too, also when the side told is the
 * land side and when the foretelling is an old one with no attack behind it; with two beaches the one nearest the ship
 * wins, not the one nearest the hall.</li>
 * <li>B824 ("Expected 'wades'/'is attacking'/'marches on' for one"): the red line a player near the village actually
 * gets in chat for a real raid of one pirate, and of two; every culture's counted raid line (named, unnamed, led;
 * chat and chronicle) reads with singular agreement for one and plural for two.</li>
 * </ul>
 * The sea is QaPiratesThreatLevelGameTests' coast: a beach along the north, the hall at its west end, a pool to the south.
 */
public class QaRaidFixes1102dGameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final Component TOWN = Component.literal("Birchgate");
	private static final Component CAPTAIN = Component.literal("Captain Brine");
	/**
	 * A little sand island in the pool between the north beach and the ship (which lies broadside, x 2 to 28, z 10 to
	 * 18): nearer the ship than any of the north beach, further from the hall than the north beach by the hall.
	 */
	private static final BlockPos ISLAND_MIN = new BlockPos(11, 2, 7);
	private static final BlockPos ISLAND_MAX = new BlockPos(12, 7, 8);

	static VillageHallBlockEntity hallEntity(ServerLevel level, BlockPos hall) {
		return (VillageHallBlockEntity) level.getBlockEntity(hall);
	}

	/** Every pirate of the raid within 5 blocks of {@code beach} and on its sea side (south of it); else where they stand. */
	private static List<String> awayFrom(BlockPos beach, BlockPos hall, List<Mob> raiders) {
		List<String> away = new ArrayList<>();
		for (Mob m : raiders) {
			if (QaPirates1102dGameTests.horizontal(beach, m.getX(), m.getZ()) > 5) {
				away.add(m.blockPosition().subtract(hall).toShortString());
			}
		}
		return away;
	}

	// ---------------------------------------------------------------- B823: the Seer's raids and two beaches

	/**
	 * A Seer foretold tonight's raid at dawn (the hall keeps it), from the ship's side; the clock's attack is the
	 * pirates'. At the hour, the 8 of the crew wade ashore at the beach nearest the ship, not at the village's edge.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaRaidFixes1102dSeer"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaRaidFixes1102dSeer")
	public void qaASeerForetoldPirateRaidWadesAshoreAtTheBeachNearestTheShip(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(QaPirates1102dGameTests.BEACH).equals(beach), "the beach nearest the ship: " + beach);
			QaPirates1102dGameTests.setTonight(level, hall, lair, 70, 13500);
			hallEntity(level, hall).setSeer(new Seer.State(70, true, QaPirates1102dGameTests.side(hall, lair), 13500, false, -1, -1, Map.of()));
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			helper.assertTrue(Seer.tonight(level, hall).isPresent(), "the Seer's foretelling isn't tonight's");
			List<Long> raided = new ArrayList<>();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raided.equals(List.of(70L)) && raiders.size() == 8, "the foretold raid: " + raided + ", " + raiders.size() + " raiders");
			helper.assertTrue(Seer.tonight(level, hall).map(Seer.State::raidStarted).orElse(false), "the Seer's raid isn't marked begun");
			List<String> away = awayFrom(beach, hall, raiders);
			helper.assertTrue(away.isEmpty(), away.size() + " of 8 pirates of the Seer's raid came ashore away from the beach nearest the ship (at "
				+ beach.subtract(hall).toShortString() + " from the hall; theirs): " + away);
			helper.succeed();
		});
	}

	/**
	 * The Seer told the land side (west, away from the sea) and the foretelling is an old one with no clock attack
	 * behind it (VillageRaids: "a foretelling saved before it has none"): the raid still comes from the ship at anchor,
	 * and its crew still wade ashore at the beach nearest it, not on the land side told.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaRaidFixes1102dSeerLand"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaRaidFixes1102dSeerLand")
	public void qaASeerToldLandSideStillWadesAshoreFromTheShip(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(ThreatData.get(level).attacks(hall).stream().noneMatch(a -> a.day() == 70), "an attack set for tonight before the test");
			hallEntity(level, hall).setSeer(new Seer.State(70, true, Math.PI, 13500, false, -1, -1, Map.of())); // the west: land
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			List<Long> raided = new ArrayList<>();
			VillageRaids.lastGathering = null;
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raided.equals(List.of(70L)) && !raiders.isEmpty(), "the foretold raid: " + raided + ", " + raiders.size() + " raiders");
			helper.assertTrue(ThreatData.get(level).raid(hall).map(r -> r.culture().equals(QaPirates1102dGameTests.PIRATES)).orElse(false),
				"the raid from the village with a ship at anchor isn't the pirates': " + ThreatData.get(level).raid(hall));
			List<String> away = awayFrom(beach, hall, raiders);
			helper.assertTrue(away.isEmpty(), away.size() + " of " + raiders.size() + " pirates came from the land side told, not the beach nearest the ship (at "
				+ beach.subtract(hall).toShortString() + "; gathered at " + (VillageRaids.lastGathering == null ? null : VillageRaids.lastGathering.subtract(hall).toShortString())
				+ "): " + away);
			helper.succeed();
		});
	}

	/**
	 * Two beaches: the north shore runs right up to the hall, and a sand island in the pool stands nearer the ship but
	 * further from the hall. The clock's raid gathers wading in to the island (the beach nearest the ship), and every
	 * pirate comes ashore there.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaRaidFixes1102dTwoBeaches"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaRaidFixes1102dTwoBeaches")
	public void qaWithTwoBeachesTheOneNearestTheShipWinsNotTheOneNearestTheHall(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			List<BlockPos> ship = Ships.of(level, hall).map(p -> List.copyOf(Ships.blocks(level, p).keySet())).orElse(List.of());
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(ISLAND_MIN), helper.absolutePos(ISLAND_MAX.above(3)))) {
				helper.assertTrue(!ship.contains(p), "the ship stands where the island goes: " + p.subtract(hall).toShortString() + "; ship x "
					+ ship.stream().mapToInt(BlockPos::getX).min().getAsInt() + ".." + ship.stream().mapToInt(BlockPos::getX).max().getAsInt() + " z "
					+ ship.stream().mapToInt(BlockPos::getZ).min().getAsInt() + ".." + ship.stream().mapToInt(BlockPos::getZ).max().getAsInt() + " (area origin "
					+ helper.absolutePos(BlockPos.ZERO).toShortString() + ")");
			}
			for (BlockPos p : BlockPos.betweenClosed(ISLAND_MIN, ISLAND_MAX)) {
				helper.setBlock(p, p.getY() == ISLAND_MAX.getY() ? Blocks.SAND : Blocks.STONE);
			}
			BlockPos north = helper.absolutePos(QaPirates1102dGameTests.BEACH);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(beach != null && beach.getX() >= helper.absolutePos(ISLAND_MIN).getX() && beach.getX() <= helper.absolutePos(ISLAND_MAX).getX()
				&& beach.getZ() >= helper.absolutePos(ISLAND_MIN).getZ() && beach.getZ() <= helper.absolutePos(ISLAND_MAX).getZ(),
				"the beach nearest the ship should be the island, is " + (beach == null ? null : beach.subtract(hall).toShortString()));
			BlockPos byHall = Ships.beach(level, hall, hall);
			helper.assertTrue(byHall != null && byHall.getZ() < helper.absolutePos(new BlockPos(0, 0, 4)).getZ() && beach.distSqr(hall) > byHall.distSqr(hall)
				&& beach.distSqr(lair.pos()) < north.distSqr(lair.pos()), "the island should be further from the hall than the north beach by the hall ("
				+ (byHall == null ? null : byHall.subtract(hall).toShortString()) + ") and nearer the ship than the north beach");
			QaPirates1102dGameTests.setTonight(level, hall, lair, 70, 13500);
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			VillageRaids.lastGathering = null;
			List<Long> raided = new ArrayList<>();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raided.equals(List.of(70L)) && raiders.size() == 8, "tonight's raid: " + raided + ", " + raiders.size() + " raiders");
			BlockPos wade = Ships.wading(level, beach, lair.pos());
			helper.assertTrue(wade.equals(VillageRaids.lastGathering), "they gathered at " + (VillageRaids.lastGathering == null ? null
				: VillageRaids.lastGathering.subtract(hall).toShortString()) + ", not wading in to the island at " + wade.subtract(hall).toShortString());
			List<String> away = awayFrom(beach, hall, raiders);
			helper.assertTrue(away.isEmpty(), away.size() + " of 8 pirates came ashore away from the island nearest the ship: " + away);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- B824: what a player reads

	/** Starts the pirates' raid from the ship at anchor with {@code home} of the crew left aboard; returns the chat seen. */
	private static List<String> pirateRaidOf(GameTestHelper helper, BlockPos hall, int home, List<Component> heard) {
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 86);
		Lairs.sent(level, hall, lair.strength() - home);
		ServerPlayer player = QaServiceJobs1031GameTests.listener(helper, heard);
		player.teleportTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5);
		heard.clear();
		QaPiratesThreatLevelGameTests.at(level, 86, 15000);
		VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(5L));
		helper.assertTrue(raid != null && VillageRaids.raiders(level, hall).size() == home,
			"a raid of " + home + ": " + VillageRaids.raiders(level, hall).size() + " raiders (strength " + lair.strength() + ")");
		return heard.stream().map(Component::getString).toList();
	}

	/** A raid of one pirate: the red line in the player's chat is "1 of <captain>'s drowned pirates wades ashore at <village>!". */
	//$ gametest_ticks_batch TALL '100' '"qaRaidFixes1102dChatOne"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaRaidFixes1102dChatOne")
	public void qaARaidOfOnePirateWadesInThePlayersChat(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			List<Component> heard = new ArrayList<>();
			List<String> said = pirateRaidOf(helper, hall, 1, heard);
			Lairs.Lair now = Lairs.near(level, hall).orElseThrow();
			String expected = "1 of " + Lairs.captainName(now).getString() + "'s drowned pirates wades ashore at " + VillageHalls.name(level, hall).getString() + "!";
			helper.assertTrue(said.contains(expected), "the player's chat for a raid of one: " + said + ", expected \"" + expected + "\"");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains(" wade ashore") || s.contains("%")), "a plural or broken line in chat: " + said);
			String told = QaPirates1102dGameTests.lastChronicle(level, hall);
			helper.assertTrue(told.startsWith("1 of ") && told.contains("drowned pirates came ashore") && !told.contains("%"), "the chronicle's raid line: " + told);
			helper.succeed();
		});
	}

	/** A raid of two pirates still reads in the plural: "2 of <captain>'s drowned pirates wade ashore at <village>!". */
	//$ gametest_ticks_batch TALL '100' '"qaRaidFixes1102dChatTwo"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaRaidFixes1102dChatTwo")
	public void qaARaidOfTwoPiratesWadeInThePlayersChat(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			List<Component> heard = new ArrayList<>();
			List<String> said = pirateRaidOf(helper, hall, 2, heard);
			Lairs.Lair now = Lairs.near(level, hall).orElseThrow();
			String expected = "2 of " + Lairs.captainName(now).getString() + "'s drowned pirates wade ashore at " + VillageHalls.name(level, hall).getString() + "!";
			helper.assertTrue(said.contains(expected), "the player's chat for a raid of two: " + said + ", expected \"" + expected + "\"");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains(" wades ashore")), "a singular line for two in chat: " + said);
			helper.succeed();
		});
	}

	private static Lairs.Lair lair(ResourceLocation culture) {
		return new Lairs.Lair(BlockPos.ZERO, BlockPos.ZERO, null, 0, culture, 8, 0, 0, 0, 0);
	}

	/** Every counted raid line of a culture, for {@code n} raiders: what VillageRaids and Lairs put in chat and the chronicle. */
	private static Map<String, String> lines(Culture culture, int n) {
		Lairs.Lair lair = lair(culture.id());
		Map<String, String> out = new LinkedHashMap<>();
		out.put("chat, unnamed", Words.countedFirst(culture.messages().key("raid", "message.aliveworkplace.raid.begins"), n, TOWN).getString());
		out.put("chat, named", Lairs.message(lair, "raid", true, n, TOWN, CAPTAIN).getString());
		out.put("chat, led", Lairs.message(lair, "raid_led", false, n, TOWN, CAPTAIN).getString());
		out.put("chronicle, unnamed", Words.countedFirst(culture.chronicle().key("raid", "chronicle.aliveworkplace.raid"), n).getString());
		out.put("chronicle, named", Lairs.chronicle(lair, "raid", true, n, CAPTAIN).getString());
		out.put("chronicle, led", Lairs.chronicle(lair, "raid_led", false, n, CAPTAIN).getString());
		return out;
	}

	/** Verbs and pronouns that agree with a plural subject; and the singular ones. */
	private static final Pattern PLURAL = Pattern.compile("(?i)\\b(are|wade|march|step|creep|their|them)\\b");
	private static final Pattern SINGULAR = Pattern.compile("(?i)\\b(is|wades|marches|steps|creeps|her|its)\\b");
	/** "1 monsters", "1 bandits", "1 drowned pirates", "1 desert raiders", "1 piglins" at the start (not "1 of ..."). */
	private static final Pattern ONE_PLURAL_NOUN = Pattern.compile("^1 (monsters|bandits|drowned pirates|desert raiders|piglins|raiders|witches)\\b");
	private static final Pattern TWO_SINGULAR_NOUN = Pattern.compile("^2 (monster|bandit|drowned pirate|desert raider|piglin|raider)\\b(?!s)");

	/**
	 * Every loaded culture (and its lair's lines, named and led), for a raid of one and of two: the line is filled in
	 * (the count, the village, no placeholder or bare key), reads with singular agreement for one ("is", "wades",
	 * "marches", "her") and plural for two.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryCulturesRaidLineAgreesWithItsCount(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Culture culture : Threats.all()) {
			if (!culture.id().getNamespace().equals("aliveworkplace")) {
				continue; // the test datapack's own cultures (aliveworkplace_test) carry no lines of their own
			}
			Map<String, String> one = lines(culture, 1);
			Map<String, String> two = lines(culture, 2);
			for (String which : one.keySet()) {
				String a = one.get(which);
				String b = two.get(which);
				String where = culture.id() + " " + which;
				checked++;
				if (a.contains("%") || a.startsWith("message.") || a.startsWith("chronicle.") || !a.contains("1")) {
					wrong.add(where + ", one, not filled in: \"" + a + "\"");
				} else if (PLURAL.matcher(a).find() || ONE_PLURAL_NOUN.matcher(a).find()) {
					wrong.add(where + ", one, reads plural: \"" + a + "\"");
				}
				if (b.contains("%") || !b.contains("2")) {
					wrong.add(where + ", two, not filled in: \"" + b + "\"");
				} else if (SINGULAR.matcher(b).find() || TWO_SINGULAR_NOUN.matcher(b).find()) {
					wrong.add(where + ", two, reads singular: \"" + b + "\"");
				}
				if (which.startsWith("chat") && !a.contains("Birchgate")) {
					wrong.add(where + ", one, doesn't name the village: \"" + a + "\"");
				}
			}
		}
		helper.assertTrue(checked >= 7 * 6, "only " + checked + " lines checked: cultures " + Threats.all().stream().map(Culture::id).toList());
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " raid lines disagree with their count: " + wrong);
		helper.succeed();
	}
}
