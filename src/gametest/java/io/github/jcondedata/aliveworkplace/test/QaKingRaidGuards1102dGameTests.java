package io.github.jcondedata.aliveworkplace.test;

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
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-02), from the Bandit King's storm spec (32.x, "every second night of the storm the King's raiders
 * come: the village's bandit raid, half again as big") and the raid switch ({@code villageRaids}: "raids off, no raid
 * comes"). Kills the nightly survivor {@code BanditKingStory.java:503} ({@code !ENABLED || active} to
 * {@code !ENABLED && active}), which no test reached: no storm night was ever tried with raids switched off or with a
 * raid already on the village.
 * <ul>
 * <li>Raids switched off: the storm's raid night brings no King's raid; switched back on that same night, it comes
 * (the night isn't spent while raids are off).</li>
 * <li>A raid already on the village on the storm's raid night: the King's raiders don't come on top of it (the raid
 * under way stays as it was); once it is over, the King's raid comes that same night.</li>
 * <li>From dusk, once a night, every second night: none a tick before dusk, one at dusk exactly, no second that night,
 * none on the storm's second night (the same gate's line-502 survivors in the local mutation run).</li>
 * </ul>
 */
public class QaKingRaidGuards1102dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final long DAY = VillageNeeds.DAY;

	private static void open(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** A hall with 8 villagers and the Bandit King's arc at its storm (chapter 3) since today (as QaThreatFixes1101GameTests). */
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

	/** Dusk-past on the storm's first night, the storm having begun today. */
	private static void firstNight(ServerLevel level) {
		level.setDayTime((Chronicle.day(level) - 1) * DAY + 13000);
	}

	private static void stillTheNight(GameTestHelper helper, ServerLevel level, long day) {
		helper.assertTrue(Chronicle.day(level) == day && level.getDayTime() % 24000 >= VillageRaids.DUSK,
			"setup: expected still the storm's first night (day " + day + ", past dusk), got day " + Chronicle.day(level) + " time " + level.getDayTime() % 24000);
	}

	/**
	 * Raids switched off ({@code villageRaids: false}): the storm's raid night, ticked three times, brings no King's raid
	 * and no raider; switched back on the same night, the next story tick brings the King's raid.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidRaidsOff1102d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidRaidsOff1102d")
	public void kingsRaidNeverComesWithRaidsOffAndComesWhenSwitchedOn(GameTestHelper helper) {
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
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			if (before == Difficulty.PEACEFUL) {
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
			}
			open(helper);
			storm(helper);
			VillageRaids.callOff(level, hall);
			VillageRaids.ENABLED = false;
			helper.assertTrue(!Threats.quiet(level, hall), "setup: expected the village not quiet (threat level 2, not At peace, not Peaceful)");
			firstNight(level);
			long day = Chronicle.day(level);
			for (int tick = 0; tick < 3; tick++) {
				level.setDayTime(level.getDayTime() + 40); // (the story ticks every 40 ticks; the same night still)
				BanditKingStory.tick(level);
				helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(),
					"expected no King's raid with village raids switched off (storm night 1, tick " + tick + "), saw "
						+ (VillageRaids.active(hall).isPresent() ? "a raid" : "no raid") + " and " + VillageRaids.raiders(level, hall).size() + " raiders");
			}
			VillageRaids.ENABLED = true;
			level.setDayTime(level.getDayTime() + 40);
			stillTheNight(helper, level, day);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"expected the King's raid the same storm night once raids were switched back on (the night isn't spent while they're off), saw no raid");
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

	/**
	 * "Every second night of the storm (its first, third...)", from dusk (18:00, day time 12000): on the storm's first day
	 * one tick before dusk no raid; at dusk exactly the King's raid; once it is over, no second raid that same night; and
	 * none on the storm's second night (kills the line-502 survivors of the same gate).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidFromDusk1102d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidFromDusk1102d")
	public void kingsRaidComesFromDuskOnceAndOnlyOnTheStormsRaidNights(GameTestHelper helper) {
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
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			if (before == Difficulty.PEACEFUL) {
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
			}
			open(helper);
			storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			long day = Chronicle.day(level);
			long start = (day - 1) * DAY;
			level.setDayTime(start + VillageRaids.DUSK - 1);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(),
				"expected no King's raid one tick before dusk on the storm's first day (day time " + (VillageRaids.DUSK - 1) + "), saw one");
			level.setDayTime(start + VillageRaids.DUSK);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"expected the King's raid at dusk exactly (day time " + VillageRaids.DUSK + ") on the storm's first night, saw none");
			VillageRaids.callOff(level, hall);
			for (int tick = 0; tick < 3; tick++) {
				level.setDayTime(level.getDayTime() + 40);
				stillTheNight(helper, level, day);
				BanditKingStory.tick(level);
				helper.assertTrue(VillageRaids.active(hall).isEmpty(),
					"expected one King's raid a night: after it was over a second came the same night (tick " + tick + ")");
			}
			level.setDayTime(day * DAY + 13000);
			helper.assertTrue(Chronicle.day(level) == day + 1, "setup: expected the storm's second day, got day " + Chronicle.day(level) + " (storm began " + day + ")");
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(),
				"expected no King's raid on the storm's second night (they come every second night: its first, third...), saw one");
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

	/**
	 * A raid of three monsters already on the village on the storm's raid night: the story tick leaves it as it was (the
	 * same three raiders, the same start, nobody added); once it is called off, the next story tick that same night
	 * brings the King's raid.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidOnARaid1102d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidOnARaid1102d")
	public void kingsRaidWaitsForARaidUnderWayAndComesAfterIt(GameTestHelper helper) {
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
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			if (before == Difficulty.PEACEFUL) {
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
			}
			open(helper);
			storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			firstNight(level);
			long day = Chronicle.day(level);
			for (int i = 0; i < 3; i++) {
				Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(12 + i, 2, 3));
				z.setNoAi(true);
				z.setPersistenceRequired();
				z.addTag(VillageRaids.TAG);
			}
			VillageRaids.Raid under = VillageRaids.track(level, hall, 3);
			helper.assertTrue(under != null && VillageRaids.active(hall).isPresent(), "setup: expected a raid of 3 under way on the village");
			for (int tick = 0; tick < 2; tick++) {
				level.setDayTime(level.getDayTime() + 40);
				BanditKingStory.tick(level);
				VillageRaids.Raid now = VillageRaids.active(hall).orElse(null);
				helper.assertTrue(now != null && now.raiders() == 3 && now.began() == under.began(),
					"expected the raid under way left as it was on the storm night (3 raiders, began " + under.began() + "), got " + now);
				int about = VillageRaids.raiders(level, hall).size();
				helper.assertTrue(about == 3, "expected no King's raiders on top of a raid under way (tick " + tick + "): 3 raiders about, saw " + about);
			}
			VillageRaids.callOff(level, hall);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "setup: expected the raid called off");
			level.setDayTime(level.getDayTime() + 40);
			stillTheNight(helper, level, day);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"expected the King's raid the same storm night once the raid under way was over (the night isn't spent waiting for it), saw no raid");
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
}
