package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-01), adversarial tests from the specs of three recent fixes, not from their code:
 * <ol>
 * <li>B738: a quiet village (threat level 0, At peace, Peaceful) gets no Bandit King's storm raid, and "the night isn't
 * spent, so the raids come again once the village stops being quiet": peace lifted on the same storm night brings the
 * raid that same night; Peaceful brings none, and back on a hostile difficulty the same night it comes.</li>
 * <li>B720: {@code /workplace threat start bandits} by a village whose camp has its whole band out or lost answers
 * {@code command.aliveworkplace.threat.lair_empty}; with one bandit home the order brings a raid of at most him; once
 * the band is back (the raid called off with {@code /workplace threat end}) an order works again.</li>
 * <li>{@code Arcs.onEvent}: an enabled event arc (the Lost Caravan's {@code caravan_lost}) starts on its event, one in
 * {@code disabledArcs} never does, and a threat event arc waits while the village is quiet (kills the surviving
 * mutant {@code !disabled} to {@code disabled}).</li>
 * </ol>
 */
public class QaThreatFixes1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = VillageNeeds.DAY;

	// --- 1. B738: the Bandit King's storm and quiet villages -------------------------------------------------------

	private static void open(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** A hall with 8 villagers and the Bandit King's arc at its storm (chapter 3) since today (as QaQuietVillageSweep1031GameTests). */
	private static VillageHallBlockEntity storm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, hall));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i % 4, 2, 9 + i / 4));
			v.setNoAi(true);
		}
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * DAY + 1000);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "setup: expected the Bandit King's arc to be loaded, found none");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0, "setup: expected the Bandit King's arc to start at chapter 0, got " + (s == null ? "null" : "chapter " + s.chapter));
		s.mobs.forEach(m -> m.dead = true);
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.equals(BanditKingStory.FIRST_CAMP) || p.key.startsWith(BanditKingStory.CAMP)) {
				p.spot = helper.absolutePos(new BlockPos(26, 2, 26));
				p.placed = 1;
			}
		}
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 1 && s.started, "setup: expected chapter 2 (index 1) started, got index " + s.chapter + ", started " + s.started);
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started && s.chapterDay == day,
			"setup: expected the storm (index 2) begun on day " + day + ", got index " + s.chapter + ", started " + s.started + ", day " + s.chapterDay);
		s.place(BanditKingStory.FORT).spot = helper.absolutePos(new BlockPos(22, 2, 22));
		return entity;
	}

	/** Dusk-past on the storm's {@code night}th night (1 = its first), the storm having begun on {@code day}. */
	private static void night(ServerLevel level, long day, int night) {
		level.setDayTime((day - 2 + night) * DAY + 13000);
	}

	/**
	 * B738's "the night isn't spent": the storm's first night, At peace, several ticks pass with no raid; peace is lifted
	 * on that same night and the next tick brings the King's raid that night (not only two nights later).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidPeaceLiftedSameNight1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidPeaceLiftedSameNight1101")
	public void kingsRaidComesTheSameNightPeaceIsLifted(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		int threat = Threats.LEVEL;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			open(helper);
			VillageHallBlockEntity entity = storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			entity.setAtPeace(true, Chronicle.day(level));
			helper.assertTrue(Threats.quiet(level, hall), "setup: expected the village At peace to be quiet, it isn't");
			long day = Chronicle.day(level);
			night(level, day, 1);
			for (int tick = 0; tick < 3; tick++) {
				level.setDayTime(level.getDayTime() + 40); // (the story ticks every 40 ticks; the same night still)
				BanditKingStory.tick(level);
				helper.assertTrue(VillageRaids.active(hall).isEmpty(),
					"expected no King's raid on a village At peace (storm night 1, tick " + tick + "), saw a raid with "
						+ VillageRaids.raiders(level, hall).size() + " raiders");
			}
			long sameDay = Chronicle.day(level);
			entity.setAtPeace(false, Chronicle.day(level));
			helper.assertTrue(!Threats.quiet(level, hall), "setup: expected the village no longer quiet with peace lifted");
			level.setDayTime(level.getDayTime() + 40);
			helper.assertTrue(Chronicle.day(level) == sameDay && level.getDayTime() % 24000 >= VillageRaids.DUSK,
				"setup: expected still the storm's first night (day " + sameDay + ", past dusk), got day " + Chronicle.day(level) + " time " + level.getDayTime() % 24000);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"B738 'the night isn't spent': expected the King's raid the same storm night peace was lifted, saw no raid (raiders "
					+ VillageRaids.raiders(level, hall).size() + ")");
			VillageRaids.callOff(level, hall);
			helper.succeed();
		} finally {
			VillageRaids.callOff(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
			VillageRaids.ENABLED = raids;
			Threats.LEVEL = threat;
		}
	}

	/**
	 * B738 on Peaceful difficulty (one of the three quiet cases): the storm's raid night brings no raid; switched back to a
	 * hostile difficulty the same night, the King's raid comes (the control, and the night isn't spent).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidPeaceful1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidPeaceful1101")
	public void kingsRaidNeverComesOnPeacefulAndComesAfter(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		int threat = Threats.LEVEL;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Difficulty before = level.getDifficulty();
		Difficulty hostile = before == Difficulty.PEACEFUL ? Difficulty.NORMAL : before;
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			level.getServer().setDifficulty(hostile, true);
			open(helper);
			storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.assertTrue(Threats.quiet(level, hall), "setup: expected the village quiet on Peaceful, it isn't");
			long day = Chronicle.day(level);
			night(level, day, 1);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(),
				"expected no King's raid on Peaceful (storm night 1), saw a raid with " + VillageRaids.raiders(level, hall).size() + " raiders");
			// The control, the same night: back on a hostile difficulty the raid comes.
			level.getServer().setDifficulty(hostile, true);
			helper.assertTrue(!Threats.quiet(level, hall), "setup: expected the village not quiet on " + hostile);
			level.setDayTime(level.getDayTime() + 40);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"expected the King's raid the same storm night once back on " + hostile + " (the night isn't spent), saw no raid");
			VillageRaids.callOff(level, hall);
			helper.succeed();
		} finally {
			VillageRaids.callOff(level, hall);
			level.getServer().setDifficulty(before, true);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
			VillageRaids.ENABLED = raids;
			Threats.LEVEL = threat;
		}
	}

	// --- 2. B720: an ordered raid by a camp with nobody (or one) at home --------------------------------------------

	private static final String LAIR_EMPTY = "nobody left at their camp";

	/** The bandits' camp by the village round {@code hall}, found at CAMP; fails the test if there's none. */
	private static Lairs.Lair camp(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null && Lairs.of(level, hall, BanditCamps.CULTURE).isPresent(),
			"setup: expected a bandit camp by the village, none was founded");
		return Lairs.of(level, hall, BanditCamps.CULTURE).orElseThrow();
	}

	private static int home(ServerLevel level, BlockPos hall) {
		return Lairs.of(level, hall, BanditCamps.CULTURE).map(Lairs.Lair::home).orElse(-1);
	}

	/** The raid's own band in the world: its raiders less the culture's extras and a captain leading it himself. */
	private static int band(ServerLevel level, BlockPos hall) {
		return (int) VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG) && !Lairs.leading(m)).count();
	}

	private static void lairSettings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean lairs = Lairs.ENABLED;
		Lairs.forget(level);
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			BanditCamps.forget(level);
			Lairs.ENABLED = lairs;
		});
	}

	/** The boundary: one bandit left at home. The order brings a raid (not lair_empty), and its band is that one. */
	//$ gametest_ticks_batch BIG '60' '"qaThreatOrderOneHome1101"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaThreatOrderOneHome1101")
	public void anOrderByACampWithOneHomeBringsARaidOfHim(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		lairSettings(helper);
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 97, 15000);
			new WorkplaceConfig().apply();
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			Lairs.Lair lair = camp(helper, hall);
			helper.assertTrue(lair.strength() >= 2, "setup: expected a camp of 2 or more, got strength " + lair.strength());
			Lairs.sent(level, hall, lair.strength() - 1);
			helper.assertTrue(home(level, hall) == 1, "setup: expected exactly 1 bandit home, got " + home(level, hall));
			said.clear();
			int sent = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			boolean raid = ThreatData.get(level).raid(hall).isPresent();
			helper.assertTrue(said.stream().noneMatch(s -> s.contains(LAIR_EMPTY)),
				"one bandit home: expected the order not to be answered 'nobody left at their camp', got " + said);
			helper.assertTrue(raid && sent > 0, "one bandit home: expected the order to bring a raid, got raid " + raid + ", returned " + sent + ": " + said);
			int band = band(level, hall);
			helper.assertTrue(band == 1,
				"one bandit home: expected a raid band of exactly that 1 bandit, saw " + band + " (raid size " + ThreatData.get(level).raid(hall).map(ThreatData.Under::raiders).orElse(-1) + ")");
			helper.assertTrue(home(level, hall) == 0, "one bandit home: expected nobody home once he left on the raid, got " + home(level, hall));
			helper.succeed();
		});
	}

	/** The boundary from the other side: the last one goes out, and the order says so in the lang line's own words. */
	//$ gametest_ticks_batch BIG '60' '"qaThreatOrderLastOut1101"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaThreatOrderLastOut1101")
	public void anOrderAfterTheLastBanditLeftSaysTheCampIsEmpty(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		lairSettings(helper);
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 98, 15000);
			new WorkplaceConfig().apply();
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			Lairs.Lair lair = camp(helper, hall);
			Lairs.sent(level, hall, lair.strength() - 1);
			helper.assertTrue(home(level, hall) == 1, "setup: expected exactly 1 bandit home, got " + home(level, hall));
			Lairs.sent(level, hall, 1);
			helper.assertTrue(home(level, hall) == 0, "setup: expected nobody home after the last left, got " + home(level, hall));
			said.clear();
			int sent = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			boolean raid = ThreatData.get(level).raid(hall).isPresent();
			String village = VillageHalls.name(level, hall).getString();
			String expected = "Bandits have nobody left at their camp by " + village + " to send: the whole band is out or lost.";
			helper.assertTrue(!raid && sent == 0, "the last bandit out: expected no raid and 0 returned, got raid " + raid + ", returned " + sent + ": " + said);
			helper.assertTrue(said.contains(expected), "the last bandit out: expected the answer '" + expected + "', got " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("found nowhere to gather")), "the last bandit out: expected no 'found nowhere to gather', got " + said);
			helper.succeed();
		});
	}

	/**
	 * Once the band is back the order works again: the last bandit leaves on an ordered raid, a second order while it's
	 * on starts nothing more, the raid is called off ({@code /workplace threat end}, the survivors come home), and the
	 * next order brings a raid, never 'nobody left at their camp'.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaThreatOrderBandBack1101"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaThreatOrderBandBack1101")
	public void anOrderWorksAgainOnceTheBandIsBack(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		lairSettings(helper);
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 99, 15000);
			new WorkplaceConfig().apply();
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			Lairs.Lair lair = camp(helper, hall);
			Lairs.sent(level, hall, lair.strength() - 1);
			helper.assertTrue(home(level, hall) == 1, "setup: expected exactly 1 bandit home, got " + home(level, hall));
			said.clear();
			int first = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			helper.assertTrue(first > 0 && ThreatData.get(level).raid(hall).isPresent(), "expected the first order to bring a raid, returned " + first + ": " + said);
			helper.assertTrue(home(level, hall) == 0, "expected nobody home with the last bandit out raiding, got " + home(level, hall));
			int out = band(level, hall);

			said.clear();
			QaThreatOrders1031GameTests.run(level, op, "workplace threat end");
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "expected '/workplace threat end' to end the raid, it's still on: " + said);
			int back = home(level, hall);
			helper.assertTrue(back >= 1 && back >= out,
				"expected the " + out + " raider(s) called off to be home again, got " + back + " home (lair " + Lairs.of(level, hall, BanditCamps.CULTURE).orElse(null) + ")");

			said.clear();
			int again = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains(LAIR_EMPTY)),
				"the band back home: expected no 'nobody left at their camp', got " + said);
			helper.assertTrue(again > 0 && ThreatData.get(level).raid(hall).isPresent() && band(level, hall) >= 1,
				"the band back home: expected the order to bring a raid again, returned " + again + ", band " + band(level, hall) + ": " + said);
			helper.succeed();
		});
	}

	// --- 3. Arcs.onEvent: enabled starts, disabled never, a threat arc waits while quiet -----------------------------

	private static final String CARAVAN_LOST = "caravan_lost";
	private static final String THREAT_EVENT = "qa_threat_event_1101";

	/**
	 * The Lost Caravan's event arc starts on {@code caravan_lost} while enabled (not quiet), never while its id (by path
	 * or full id) is in {@code disabledArcs}, and again once it's taken off; a threat event arc waits while the village
	 * is At peace or at threat level 0.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaEventArcCaravanLost1101"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaEventArcCaravanLost1101")
	public void theLostCaravanStartsOnItsEventUnlessDisabled(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		int threat = Threats.LEVEL;
		Difficulty before = level.getDifficulty();
		VillageHallBlockEntity entity = null;
		try {
			VillageHalls.RADIUS = 8;
			Arcs.ENABLED = true;
			Arcs.DISABLED = Set.of();
			Threats.LEVEL = 2;
			if (before == Difficulty.PEACEFUL) {
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
			}
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setLastQuestDay(Long.MAX_VALUE / 2);
			entity.setAtPeace(false, -1);
			Arcs.stopAll(level, hall);
			Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
			Arcs.Arc caravan = Arcs.find("lost_caravan").orElse(null);
			helper.assertTrue(caravan != null && caravan.event().equals(CARAVAN_LOST),
				"setup: expected the Lost Caravan's arc loaded on event '" + CARAVAN_LOST + "', got " + (caravan == null ? "none" : "event '" + caravan.event() + "'"));
			helper.assertTrue(!Threats.quiet(level, hall), "setup: expected the village not quiet (threat level 2, not At peace, " + level.getDifficulty() + ")");
			String id = caravan.id().toString();

			ArcState s = Arcs.onEvent(level, hall, CARAVAN_LOST);
			helper.assertTrue(s != null && s.id.equals(id) && Arcs.running(level, hall) == s,
				"enabled, not quiet: expected '" + CARAVAN_LOST + "' to start " + id + ", got " + (s == null ? "null" : s.id) + ", running " + Arcs.running(level, hall));
			Arcs.stopAll(level, hall);
			helper.assertTrue(Arcs.running(level, hall) == null, "setup: expected the Lost Caravan stopped, it still runs");

			for (Set<String> off : List.of(Set.of(caravan.id().getPath()), Set.of(id))) {
				Arcs.DISABLED = off;
				ArcState d = Arcs.onEvent(level, hall, CARAVAN_LOST);
				helper.assertTrue(d == null && Arcs.running(level, hall) == null,
					"disabledArcs " + off + ": expected '" + CARAVAN_LOST + "' to start nothing, got " + (d == null ? Arcs.running(level, hall) : d.id));
			}
			Arcs.DISABLED = Set.of();
			s = Arcs.onEvent(level, hall, CARAVAN_LOST);
			helper.assertTrue(s != null && s.id.equals(id), "taken off disabledArcs: expected " + id + " to start again, got " + (s == null ? "null" : s.id));
			Arcs.stopAll(level, hall);

			// A threat event arc: the Lost Caravan's own file is checked first; if it isn't a threat arc, a test one is.
			Arcs.Arc threatArc = caravan;
			String event = CARAVAN_LOST;
			if (!caravan.threat()) {
				threatArc = Arcs.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/threat_event_1101"), JsonParser.parseString(
					"{\"name\": \"QA threat event arc\", \"threat\": true, \"trigger\": {\"event\": \"" + THREAT_EVENT
						+ "\"}, \"chapters\": [{\"name\": \"Waiting\", \"needs_flags\": [\"never_set\"]}]}").getAsJsonObject());
				Arcs.add(threatArc);
				event = THREAT_EVENT;
			}
			String threatId = threatArc.id().toString();
			helper.assertTrue(threatArc.threat(), "setup: expected " + threatId + " marked threat");
			entity.setAtPeace(true, Chronicle.day(level));
			ArcState q = Arcs.onEvent(level, hall, event);
			helper.assertTrue(q == null && Arcs.running(level, hall) == null,
				"At peace: expected the threat arc " + threatId + " not to start on '" + event + "', got " + (q == null ? Arcs.running(level, hall) : q.id));
			entity.setAtPeace(false, -1);
			Threats.LEVEL = 0;
			q = Arcs.onEvent(level, hall, event);
			helper.assertTrue(q == null && Arcs.running(level, hall) == null,
				"threat level 0: expected the threat arc " + threatId + " not to start on '" + event + "', got " + (q == null ? Arcs.running(level, hall) : q.id));
			Threats.LEVEL = 2;
			q = Arcs.onEvent(level, hall, event);
			helper.assertTrue(q != null && q.id.equals(threatId),
				"not quiet: expected the threat arc " + threatId + " to start on '" + event + "', got " + (q == null ? "null" : q.id));
			Arcs.stopAll(level, hall);
			helper.succeed();
		} finally {
			Arcs.stopAll(level, hall);
			if (entity != null) {
				entity.setAtPeace(false, -1);
			}
			level.getServer().setDifficulty(before, true);
			VillageHalls.RADIUS = radius;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
			Threats.LEVEL = threat;
		}
	}
}
