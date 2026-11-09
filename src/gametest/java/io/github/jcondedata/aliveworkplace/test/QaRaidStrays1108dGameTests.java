package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * QA (lane D, 2026-11-08) for B1055's fix (wiki raids.md: "A raider led out of every raided village's reach ... still
 * belongs to the raid whose hall is nearest: that raid isn't fought off while it lives, and at the raid's hour it leaves
 * with the others, wherever it is"; the bug's spec: "a raid isn't fought off while its raider lives, and by morning the
 * raider has left"). The edges: a stray killed out there lets the raid be fought off; with two raids a stray counts for
 * exactly one (the nearest hall's, {@code VillageRaids.withStrays}' javadoc); a mob carrying the raider tag that belongs
 * to no raid under way (left over from an older raid) is not the new raid's raider; a call-off sends its strays away and
 * leaves the other raid's; the reach boundary (RADIUS + 32) from both sides.
 * <p>
 * The hall radius is 0 here, so the reach is 32 blocks from the hall. Raiders outside the test area stand in chunks
 * this test forces loaded; each test checks the game really holds them (its entity lookup finds them) before it counts,
 * and starts its raids only then: the hall block's own round would otherwise book a raid fought off in the ticks before
 * a forced chunk's raider is held (that made the tie test flaky).
 */
public class QaRaidStrays1108dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(15, 2, 15);
	/** Ticks to wait at most for a forced chunk's raiders to be held by the game (its entity lookup finds them). */
	static final int WAIT = 300;
	/** A real server's tick (ms): the wait below is held to this pace, so {@link #WAIT} ticks are at least 15 s of real time. */
	private static final long TICK_MS = 50;

	/**
	 * A raider standing at {@code abs} (any distance from the test), its chunk forced loaded while the test runs; it
	 * neither moves nor falls. Discarded and the chunk let go afterwards.
	 */
	static Husk raiderAt(GameTestHelper helper, BlockPos abs) {
		ServerLevel level = helper.getLevel();
		ChunkPos chunk = new ChunkPos(abs);
		boolean forced = level.getForcedChunks().contains(chunk.toLong());
		if (!forced) {
			level.setChunkForced(chunk.x, chunk.z, true);
		}
		level.getChunk(chunk.x, chunk.z);
		Husk husk = EntityType.HUSK.create(level);
		helper.assertTrue(husk != null, "setup: no husk");
		husk.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		husk.setNoAi(true);
		husk.setNoGravity(true);
		husk.setPersistenceRequired(); // as every raider is (Threats.outfit)
		husk.addTag(VillageRaids.TAG);
		helper.assertTrue(level.addFreshEntity(husk), "setup: the husk at " + abs.toShortString() + " wasn't added to the world");
		Leftovers.after(helper, () -> {
			husk.discard();
			if (!forced) {
				level.setChunkForced(chunk.x, chunk.z, false);
			}
		});
		return husk;
	}

	/**
	 * Runs {@code body} once the game holds every one of {@code raiders} in loaded ground (a forced chunk's entities take
	 * some ticks to count); fails loudly, naming the first not held, after {@link #WAIT} ticks.
	 * <p>
	 * A freshly forced chunk's entities come in from the storage thread, in real time, not in ticks. The game test server
	 * runs its ticks back to back (a few hundred in a tenth of a second when little else runs), so under a full suite's
	 * load, with the storage thread busy saving, 300 ticks once went by before a raider 200 blocks off was held (B1086).
	 * While it waits, this keeps the server to a real server's pace (it sleeps whatever the ticks ran ahead of
	 * {@link #TICK_MS} each), so the wait is the same 15 s a real server would give.
	 */
	static void whenHeld(GameTestHelper helper, List<Husk> raiders, Runnable body) {
		whenHeld(helper, raiders, body, 0, System.nanoTime());
	}

	private static void whenHeld(GameTestHelper helper, List<Husk> raiders, Runnable body, int waited, long since) {
		ServerLevel level = helper.getLevel();
		boolean held = raiders.stream().allMatch(h -> level.getEntity(h.getUUID()) == h && level.isPositionEntityTicking(h.blockPosition()));
		if (held || waited >= WAIT) {
			body.run();
			return;
		}
		long ahead = waited * TICK_MS - (System.nanoTime() - since) / 1_000_000L;
		if (ahead > 0) {
			try {
				Thread.sleep(ahead);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		helper.runAfterDelay(5, () -> whenHeld(helper, raiders, body, waited + 5, since));
	}

	/** Fails loudly unless the game holds {@code husk} in loaded ground where the raids' counts can see it. */
	static void assertHeld(GameTestHelper helper, Husk husk, String what) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(husk.isAlive() && level.isLoaded(husk.blockPosition()) && level.getEntity(husk.getUUID()) == husk,
			"setup: " + what + " at " + husk.blockPosition().toShortString() + " isn't in loaded ground the game counts (loaded "
				+ level.isLoaded(husk.blockPosition()) + ", found " + (level.getEntity(husk.getUUID()) != null) + ", its chunk's entities loaded "
				+ level.areEntitiesLoaded(new ChunkPos(husk.blockPosition()).toLong()) + ", entity ticking " + level.isPositionEntityTicking(husk.blockPosition())
				+ ", alive " + husk.isAlive() + ", removed " + husk.getRemovalReason() + ")");
	}

	/** Every living mob in the world carrying the raider tag, with where it stands (for the failure messages). */
	static String taggedMobs(ServerLevel level) {
		List<String> all = new ArrayList<>();
		for (Mob m : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && m.getTags().contains(VillageRaids.TAG))) {
			all.add(m.getType().toShortString() + "@" + m.blockPosition().toShortString());
		}
		return all.toString();
	}

	/** The raid on {@code hall} is over, fought off: no raid under way, and its past attack fled == false with every raider fallen. */
	static void assertFoughtOff(GameTestHelper helper, BlockPos hall, String why) {
		ServerLevel level = helper.getLevel();
		List<ThreatData.Past> past = ThreatData.get(level).past(hall);
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && past.size() == 1 && !past.get(0).fled() && past.get(0).fell() >= past.get(0).came(),
			why + ": expected the village's round to book its raid fought off, but raid under way: " + ThreatData.get(level).raid(hall).isPresent()
				+ ", past attacks: " + past + ", raider-tagged mobs in the world: " + taggedMobs(level));
	}

	static void morning(ServerLevel level) {
		level.setDayTime((level.getDayTime() / 24000) * 24000 + QaRaidOwnership1104cGameTests.MORNING + 24000);
	}

	/** a. A raid's last raider led out of the reach and killed out there: the next round books the raid fought off. */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStrayKilled1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStrayKilled1108d")
	public void aStrayKilledOutOfReachLetsTheRaidBeFoughtOff(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		Husk stray = raiderAt(helper, hall.offset(-40, 0, 0));
		whenHeld(helper, List.of(stray), () -> {
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			assertHeld(helper, stray, "the led-away raider");
			helper.assertFalse(VillageRaids.reaches(hall, stray), "setup: the stray is within the reach");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid ended while its led-away raider still lives out of reach: "
				+ ThreatData.get(level).past(hall));
			stray.kill();
			helper.assertFalse(stray.isAlive(), "setup: the stray didn't die");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			assertFoughtOff(helper, hall, "the raid's last raider was killed out of the reach");
			helper.succeed();
		});
	}

	/**
	 * b. Two raids under way; a raider led out of both reaches, nearer hall B's: it is B's raid's alone (withStrays: "each
	 * given to the raid under way whose hall is nearest"; "every raider still counts for exactly one raid"). A's round
	 * books A's raid fought off and leaves the raider be; B's raid goes on, and at dawn the raider leaves with B's.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStrayTwoRaids1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStrayTwoRaids1108d")
	public void aStrayBetweenTwoRaidsCountsOnlyForTheNearestHallsRaid(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hallA = helper.absolutePos(HALL);
		BlockPos hallB = hallA.offset(100, 0, 0);
		Husk stray = raiderAt(helper, hallA.offset(60, 0, 0)); // 60 from A, 40 from B: out of both reaches (32)
		whenHeld(helper, List.of(stray), () -> {
			QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
			QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
			assertHeld(helper, stray, "the stray between the villages");
			helper.assertFalse(VillageRaids.reaches(hallA, stray) || VillageRaids.reaches(hallB, stray), "setup: the stray is within a reach");
			QaRaidOwnership1104cGameTests.tick(level, hallA);
			assertFoughtOff(helper, hallA, "village A's raiders are all gone and the only one alive is nearer village B (B's stray)");
			helper.assertTrue(stray.isAlive() && !stray.isRemoved(), "village A's round sent away a stray that is village B's raid's");
			QaRaidOwnership1104cGameTests.tick(level, hallB);
			helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(),
				"village B's raid was ended while its stray (nearer B's hall than any other) still lives: " + ThreatData.get(level).past(hallB));
			morning(level);
			QaRaidOwnership1104cGameTests.tick(level, hallB);
			helper.assertTrue(stray.isRemoved() || !stray.isAlive(), "morning came and village B's stray is still in the world: " + taggedMobs(level));
			List<ThreatData.Past> past = ThreatData.get(level).past(hallB);
			helper.assertTrue(ThreatData.get(level).raid(hallB).isEmpty() && past.size() == 1 && past.get(0).fled(),
				"expected village B's raid to end at dawn with its raiders fleeing: " + past);
			helper.succeed();
		});
	}

	/**
	 * b2. A stray exactly as far from two raided halls, out of both reaches: it is the first raided village's (wiki
	 * raids.md: "the nearest hall's (the first raided, on a tie)"; withStrays: "the first listed on a tie"). The second
	 * village's round books its raid fought off; the first's goes on. (Kills the mutant {@code <} to {@code <=} in
	 * {@code nearest}, which gave the stray to the last listed.)
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStrayTie1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStrayTie1108d")
	public void aStrayAsFarFromTwoRaidedHallsIsTheFirstRaidedVillages(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos first = helper.absolutePos(HALL);
		BlockPos second = first.offset(100, 0, 0);
		Husk stray = raiderAt(helper, first.offset(50, 0, 0)); // 50 from each hall
		whenHeld(helper, List.of(stray), () -> {
			QaRaidOwnership1104cGameTests.raid(helper, first, halls);
			QaRaidOwnership1104cGameTests.raid(helper, second, halls);
			assertHeld(helper, stray, "the stray halfway between the villages");
			helper.assertTrue(first.distSqr(stray.blockPosition()) == second.distSqr(stray.blockPosition()), "setup: the stray isn't as far from both halls");
			QaRaidOwnership1104cGameTests.tick(level, second);
			assertFoughtOff(helper, second, "the second raided village has no raider left; the stray as far from both halls is the first raided village's");
			QaRaidOwnership1104cGameTests.tick(level, first);
			helper.assertTrue(ThreatData.get(level).raid(first).isPresent(),
				"the first raided village's raid ended while its stray (as far from both halls; the first raided wins a tie) still lives: "
					+ ThreatData.get(level).past(first));
			helper.succeed();
		});
	}

	/**
	 * d. An operator's call-off ({@code /workplace threat end}) sends the raid's strays away too, and leaves another
	 * raid's stray (nearer that raid's hall) standing; calling that raid off sends it away.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStrayCallOff1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStrayCallOff1108d")
	public void callingOffARaidSendsItsStraysAwayAndLeavesTheOtherRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hallA = helper.absolutePos(HALL);
		BlockPos hallB = hallA.offset(100, 0, 0);
		Husk strayA = raiderAt(helper, hallA.offset(-40, 0, 0));
		Husk strayB = raiderAt(helper, hallA.offset(60, 0, 0));
		whenHeld(helper, List.of(strayA, strayB), () -> {
			QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
			QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
			assertHeld(helper, strayA, "village A's stray");
			assertHeld(helper, strayB, "village B's stray");
			helper.assertTrue(VillageRaids.callOff(level, hallA), "calling off village A's raid said no raid was under way");
			helper.assertTrue(strayA.isRemoved(), "village A's raid was called off and its stray, led out of the reach, is still in the world");
			helper.assertTrue(strayB.isAlive() && !strayB.isRemoved(), "calling off village A's raid sent away village B's stray (nearer B's hall)");
			helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "calling off village A's raid ended village B's");
			helper.assertTrue(VillageRaids.callOff(level, hallB), "calling off village B's raid said no raid was under way");
			helper.assertTrue(strayB.isRemoved(), "village B's raid was called off and its stray is still in the world");
			helper.succeed();
		});
	}

	/**
	 * e. The reach's edge: a raider exactly RADIUS + 32 from the hall (east) is within the reach, the village's own; one
	 * block further it is a stray. Both keep the raid going; killed, the raid is fought off. The same on the west side.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStrayEdge1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStrayEdge1108d")
	public void aRaiderAtTheReachsEdgeIsOwnAndOneBlockBeyondIsAStrayBothKeepTheRaidGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		Husk east = raiderAt(helper, hall.offset(32, 0, 0));
		Husk west = raiderAt(helper, hall.offset(-33, 0, 0)); // forces the west chunk loaded; discarded once held
		whenHeld(helper, List.of(east, west), () -> {
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			west.discard();
			assertHeld(helper, east, "the raider at the reach's edge");
			helper.assertTrue(VillageRaids.reaches(hall, east) && VillageRaids.raiders(level, hall).contains(east),
				"a raider exactly 32 blocks east of the hall (radius 0 + 32) isn't counted within the village's reach");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid ended while its raider stands at the very edge of the reach");
			east.teleportTo(hall.getX() + 33.5, hall.getY(), hall.getZ() + 0.5);
			helper.assertTrue(!VillageRaids.reaches(hall, east) && !VillageRaids.raiders(level, hall).contains(east),
				"a raider 33 blocks east of the hall is still counted within the reach");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid ended while its raider stands one block beyond the reach (east)");
			east.teleportTo(hall.getX() - 32 + 0.5, hall.getY(), hall.getZ() + 0.5);
			helper.assertTrue(VillageRaids.reaches(hall, east), "a raider exactly 32 blocks west of the hall isn't within the reach");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid ended while its raider stands at the reach's west edge");
			east.teleportTo(hall.getX() - 33 + 0.5, hall.getY(), hall.getZ() + 0.5);
			helper.assertFalse(VillageRaids.reaches(hall, east), "a raider 33 blocks west of the hall is still within the reach");
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid ended while its raider stands one block beyond the reach (west)");
			east.kill();
			QaRaidOwnership1104cGameTests.tick(level, hall);
			assertFoughtOff(helper, hall, "the raid's only raider was killed one block beyond the reach");
			helper.succeed();
		});
	}
}
