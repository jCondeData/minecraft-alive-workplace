package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-09), written from the specs of B1055, B1063, B1066 and B1079 (wiki raids.md: "Out there, a raider
 * only counts for the raid it came with: each raider is stamped with its raid (the hall and the moment it began) when it
 * spawns and whenever that raid holds it in its reach, so a leftover of an older raid ... never keeps a later raid going,
 * however near or far it is. A raider with no stamp ... counts only within 64 blocks beyond the reach of the nearest
 * raided hall"; sieges.md: a raider's shot stays a raider's until it lands). The edges not yet covered: two raids at
 * once with a stamped stray nearer the other hall; a leftover of the same hall's raid one tick older; the unstamped
 * bound from both sides, across and up; a stamped stray saved and loaded; a leftover the new raid holds (restamped); dawn;
 * and a raider's arrow handed to a real owner, to nobody, and saved.
 * <p>
 * The hall radius is 0 here, so a reach is 32 blocks across and 48 up or down; an unstamped stray counts within 96
 * across and 112 up or down.
 */
public class QaRaidStampEdges1109dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");
	private static final BlockPos HALL = QaRaidStrays1108dGameTests.HALL;

	private static void tick(ServerLevel level, BlockPos hall) {
		QaRaidOwnership1104cGameTests.tick(level, hall);
	}

	private static void at(Husk husk, BlockPos abs) {
		husk.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
	}

	private static ThreatData.Under raidOn(GameTestHelper helper, BlockPos hall) {
		ThreatData.Under raid = ThreatData.get(helper.getLevel()).raid(hall).orElse(null);
		helper.assertTrue(raid != null, "setup: no raid under way on " + hall.toShortString());
		return raid;
	}

	/**
	 * 1. Two raids under way at once (halls 150 apart). Raid A's raider, held by A in its reach (so stamped A's), is led
	 * out of both reaches to a spot 100 from A's hall and 50 from B's. B's own raider is killed: B is fought off (A's
	 * raider is no raider of B's, however near). A's raider in its reach is killed: A goes on while its stray lives; the
	 * stray killed, A is fought off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampTwoRaids1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampTwoRaids1109d")
	public void aStampedStrayNearerAnotherRaidedHallCountsOnlyForItsOwnRaid(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hallA = helper.absolutePos(HALL);
		BlockPos hallB = hallA.offset(150, 0, 0);
		BlockPos out = hallA.offset(100, 0, 0);
		Husk stray = QaRaidStrays1108dGameTests.raiderAt(helper, out);
		Husk ofB = QaRaidStrays1108dGameTests.raiderAt(helper, hallB.offset(3, 0, 0));
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(stray, ofB), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, stray, "raid A's raider");
			QaRaidStrays1108dGameTests.assertHeld(helper, ofB, "raid B's raider");
			QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
			QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
			at(stray, hallA.offset(3, 0, 0)); // it came with A's raid
			Husk ofA = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(-3, 0, 2));
			tick(level, hallA);
			tick(level, hallB);
			helper.assertValueEqual(VillageRaids.stampOn(stray), VillageRaids.stampOf(raidOn(helper, hallA)), "the stamp of a raider raid A held in its reach");
			at(stray, out); // led away, towards village B
			helper.assertFalse(VillageRaids.reaches(hallA, stray) || VillageRaids.reaches(hallB, stray), "setup: the stray stands within a reach");
			helper.assertTrue(hallB.distSqr(stray.blockPosition()) < hallA.distSqr(stray.blockPosition()), "setup: the stray isn't nearer B's hall");
			ofB.kill();
			tick(level, hallB);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hallB,
				"village B's own raider was killed; the only other raider out there came with village A's raid (stamped A's), 50 blocks from B's hall");
			helper.assertTrue(stray.isAlive() && !stray.isRemoved(), "village B's round sent away village A's raider");
			ofA.kill();
			tick(level, hallA);
			helper.assertTrue(ThreatData.get(level).raid(hallA).isPresent(),
				"village A's raid was ended while its own raider, led nearer village B, still lives: " + ThreatData.get(level).past(hallA));
			stray.kill();
			tick(level, hallA);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hallA, "village A's last raider was killed out there");
			helper.succeed();
		});
	}

	/**
	 * 2. The same hall's older raid, which began one tick before the new one, left a raider stamped with it 40 blocks off
	 * (where an unstamped stray would count); a second leftover carries the stamp of a raid that began at the very same
	 * tick on a hall one block over. Neither is the new raid's: its raider killed, it is fought off, and both are left be.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampOneTick1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampOneTick1109d")
	public void aLeftoverOfTheSameHallsRaidOneTickOlderDoesNotKeepTheNewRaidGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		Husk older = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(-40, 0, 0));
		Husk nextDoor = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(0, 0, -40));
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(older, nextDoor), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, older, "the older raid's leftover");
			QaRaidStrays1108dGameTests.assertHeld(helper, nextDoor, "the other hall's leftover");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			ThreatData.Under raid = raidOn(helper, hall);
			VillageRaids.stamp(older, new ThreatData.Under(hall, BANDITS, 3, raid.began() - 1));
			VillageRaids.stamp(nextDoor, new ThreatData.Under(hall.offset(1, 0, 0), BANDITS, 3, raid.began()));
			helper.assertFalse(VillageRaids.stampOf(raid).equals(VillageRaids.stampOn(older)) || VillageRaids.stampOf(raid).equals(VillageRaids.stampOn(nextDoor)),
				"setup: a leftover carries the new raid's stamp: " + older.getTags() + " " + nextDoor.getTags());
			Husk raider = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(3, 0, 0));
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended while its raider stood by the hall");
			raider.kill();
			tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall,
				"the new raid's raider was killed; the leftovers out there carry the stamp of a raid one tick older and of a hall one block over");
			helper.assertTrue(older.isAlive() && !older.isRemoved() && nextDoor.isAlive() && !nextDoor.isRemoved(), "the new raid's round sent away a leftover");
			helper.succeed();
		});
	}

	/**
	 * 3. An unstamped stray (an old save's raider) at exactly 64 blocks beyond the reach (96 east) keeps the raid going;
	 * one block further (97) it doesn't, and the raid, its raiders in the reach dead, is fought off. Then a second raid,
	 * the same up the hall's column: 112 above (48 + 64) keeps it going, 113 doesn't.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampUnstampedEdge1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampUnstampedEdge1109d")
	public void anUnstampedStrayCountsUpTo64BlocksBeyondTheReachAndNotOneMore(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		Husk stray = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(96, 0, 0));
		Husk edge = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(97, 0, 0)); // forces its chunk loaded; discarded once held
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(stray, edge), () -> {
			edge.discard();
			QaRaidStrays1108dGameTests.assertHeld(helper, stray, "the unstamped stray");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			Husk raider = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(3, 0, 0));
			tick(level, hall);
			helper.assertTrue(VillageRaids.stampOn(stray) == null, "setup: the stray out of reach got a stamp: " + stray.getTags());
			raider.kill();
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(),
				"the raid ended while an unstamped stray stands exactly 64 blocks beyond its reach (96 east of the hall): " + ThreatData.get(level).past(hall));
			at(stray, hall.offset(97, 0, 0));
			QaRaidStrays1108dGameTests.assertHeld(helper, stray, "the unstamped stray at 97");
			tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the raid's raiders are dead; an unstamped stray stands 65 blocks beyond the reach (97 east)");
			helper.assertTrue(stray.isAlive() && !stray.isRemoved(), "the round sent away an unstamped stray 65 blocks beyond the reach");

			// Up the hall's column.
			at(stray, hall.offset(0, 112, 0));
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			Husk second = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(3, 0, 0));
			tick(level, hall);
			second.kill();
			helper.assertFalse(VillageRaids.reaches(hall, stray), "setup: the stray 112 above is within the reach");
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(),
				"the raid ended while an unstamped stray stands exactly 64 blocks above the reach (112 above the hall): " + ThreatData.get(level).past(hall));
			at(stray, hall.offset(0, 113, 0));
			tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the raid's raiders are dead; an unstamped stray stands 65 blocks above the reach (113 above)");
			helper.succeed();
		});
	}

	/**
	 * 4. A raid's own stray, stamped in the reach and led 100 blocks off (beyond where an unstamped one counts), is saved
	 * and loaded with the threats' data, as a restart does: the loaded raider still keeps the raid going, and killed, the
	 * raid is fought off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampReload1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampReload1109d")
	public void aRaidsStampedStrayStillKeepsItGoingAfterASaveAndLoad(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos out = hall.offset(100, 0, 0);
		Husk stray = QaRaidStrays1108dGameTests.raiderAt(helper, out);
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(stray), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, stray, "the raider");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			at(stray, hall.offset(3, 0, 0));
			tick(level, hall);
			String stamp = VillageRaids.stampOn(stray);
			helper.assertValueEqual(stamp, VillageRaids.stampOf(raidOn(helper, hall)), "the stamp of a raider the raid held");
			at(stray, out);
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended while its stray lives");

			CompoundTag nbt = new CompoundTag();
			helper.assertTrue(stray.save(nbt), "setup: the stray didn't save");
			stray.discard();
			QaRaidCallOff1103cGameTests.reloadThreats(level);
			Husk loaded = (Husk) EntityType.create(nbt, level).orElse(null);
			helper.assertTrue(loaded != null, "setup: the saved stray didn't load");
			helper.assertTrue(level.addFreshEntity(loaded), "setup: the loaded stray wasn't added to the world");
			Leftovers.after(helper, loaded::discard);
			QaRaidStrays1108dGameTests.assertHeld(helper, loaded, "the loaded stray");
			helper.assertValueEqual(VillageRaids.stampOn(loaded), stamp, "the stamp of the stray after a save and load");
			helper.assertTrue(raidOn(helper, hall) != null && VillageRaids.stampOf(raidOn(helper, hall)).equals(stamp),
				"the loaded raid's stamp isn't the one its raider carries: " + VillageRaids.stampOf(raidOn(helper, hall)) + " vs " + stamp);
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(),
				"after a save and load the raid ended while its own stamped raider, 100 blocks off, still lives: " + ThreatData.get(level).past(hall));
			loaded.kill();
			tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the loaded stray, the raid's last raider, was killed");
			helper.succeed();
		});
	}

	/**
	 * 5. A leftover stamped by an older raid stands 100 blocks off and doesn't count; it walks into the new raid's reach,
	 * and the raid holds it (restamps it its own). Led out again, it keeps that raid going after the raid's other raider
	 * is killed; killed itself, the raid is fought off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampRestamp1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampRestamp1109d")
	public void aLeftoverTheNewRaidHoldsBecomesItsRaiderAndCountsWhenLedOut(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos out = hall.offset(-100, 0, 0);
		Husk leftover = QaRaidStrays1108dGameTests.raiderAt(helper, out);
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(leftover), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, leftover, "the leftover");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			ThreatData.Under raid = raidOn(helper, hall);
			VillageRaids.stamp(leftover, new ThreatData.Under(hall, BANDITS, 3, raid.began() - 24000)); // last night's raid
			Husk raider = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(3, 0, 0));
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended while its raider stood by the hall");
			helper.assertFalse(VillageRaids.stampOf(raid).equals(VillageRaids.stampOn(leftover)), "the new raid stamped a leftover it never held: " + leftover.getTags());
			at(leftover, hall.offset(-5, 0, 0)); // walks into the village
			tick(level, hall);
			helper.assertValueEqual(VillageRaids.stampOn(leftover), VillageRaids.stampOf(raid), "the stamp of a leftover the new raid holds in its reach");
			helper.assertTrue(leftover.getTags().stream().filter(t -> t.startsWith(VillageRaids.RAID_STAMP)).count() == 1,
				"the restamped leftover carries more than one raid stamp: " + leftover.getTags());
			at(leftover, out); // led out again
			raider.kill();
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(),
				"the raid ended while a raider it held in its reach, led 100 blocks off, still lives: " + ThreatData.get(level).past(hall));
			leftover.kill();
			tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the raid's last raider (a leftover it held) was killed out there");
			helper.succeed();
		});
	}

	/**
	 * 6. At dawn the raid's own stray, stamped in its reach and led 100 blocks off, leaves with it (B1055: "at the raid's
	 * hour it leaves with the others, wherever it is"), and the raid ends fled. (What becomes of an older raid's leftover
	 * standing out there is only logged: the wiki doesn't say.)
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampDawn1109d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampDawn1109d")
	public void atDawnTheRaidsOwnFarStrayLeavesWithIt(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos out = hall.offset(100, 0, 0);
		Husk stray = QaRaidStrays1108dGameTests.raiderAt(helper, out);
		Husk leftover = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(-100, 0, 0));
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(stray, leftover), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, stray, "the raid's raider");
			QaRaidStrays1108dGameTests.assertHeld(helper, leftover, "the leftover");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			ThreatData.Under raid = raidOn(helper, hall);
			VillageRaids.stamp(leftover, new ThreatData.Under(hall, BANDITS, 3, raid.began() - 24000));
			at(stray, hall.offset(3, 0, 0));
			tick(level, hall);
			helper.assertValueEqual(VillageRaids.stampOn(stray), VillageRaids.stampOf(raid), "the stamp of a raider the raid held");
			at(stray, out);
			tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended at night while its stray lives");
			QaRaidStrays1108dGameTests.morning(level);
			tick(level, hall);
			helper.assertTrue(stray.isRemoved() || !stray.isAlive(),
				"morning came and the raid's own raider, 100 blocks off, is still in the world: " + QaRaidStrays1108dGameTests.taggedMobs(level));
			List<ThreatData.Past> past = ThreatData.get(level).past(hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && past.size() == 1 && past.get(0).fled(),
				"expected the raid to end at dawn with its raider fleeing: raid under way " + ThreatData.get(level).raid(hall).isPresent() + ", past " + past);
			AliveWorkplace.LOG.info("[test] qa raid stamp dawn: an older raid's leftover out of reach at dawn: alive {}, removed {}", leftover.isAlive(), leftover.isRemoved());
			helper.succeed();
		});
	}

	/**
	 * 7. A raider's arrow: its shooter discarded, then bounced (owner set to null) and saved and loaded, it is still a
	 * threat's, and another bounce leaves it so; handed to a real non-raider owner (a plain skeleton) it is not, a bounce
	 * after that doesn't give the mark back, and a save and load keeps it gone. A player's arrow taken over by a raider
	 * becomes a raider's shot; a raider's arrow taken over by a player is no longer one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRaidersArrowLosesItsMarkOnlyToARealNewOwner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pillager raider = EntityType.PILLAGER.create(level);
		Pillager other = EntityType.PILLAGER.create(level);
		Skeleton plain = EntityType.SKELETON.create(level);
		Arrow shot = EntityType.ARROW.create(level);
		Arrow players = EntityType.ARROW.create(level);
		Arrow taken = EntityType.ARROW.create(level);
		helper.assertTrue(raider != null && other != null && plain != null && shot != null && players != null && taken != null, "setup: no entities");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		raider.addTag(Threats.RAIDER_TAG);
		other.addTag(Threats.RAIDER_TAG);

		shot.setOwner(raider);
		helper.assertTrue(ThreatDamage.isThreat(shot), "a raider's arrow isn't a threat's");
		raider.discard();
		helper.assertTrue(shot.getOwner() == null, "setup: the discarded raider still reads as the arrow's owner");
		shot.setOwner(null); // it bounces
		helper.assertTrue(ThreatDamage.isThreat(shot), "a gone raider's arrow, bounced (owner null), isn't a threat's: tags " + shot.getTags());
		CompoundTag nbt = new CompoundTag();
		helper.assertTrue(shot.save(nbt), "setup: the arrow didn't save");
		Arrow loaded = (Arrow) EntityType.create(nbt, level).orElse(null);
		helper.assertTrue(loaded != null && loaded.getOwner() == null, "setup: the arrow didn't load, or still finds its gone raider");
		helper.assertTrue(ThreatDamage.isThreat(loaded), "a gone raider's bounced arrow, saved and loaded, isn't a threat's: tags " + loaded.getTags());
		loaded.setOwner(null); // bounces again
		helper.assertTrue(ThreatDamage.isThreat(loaded), "a gone raider's arrow, loaded and bounced again, isn't a threat's: tags " + loaded.getTags());

		loaded.setOwner(plain); // a plain skeleton's now
		helper.assertFalse(ThreatDamage.isThreat(loaded), "a raider's arrow taken over by a plain skeleton is still a threat's: tags " + loaded.getTags());
		loaded.setOwner(null);
		helper.assertFalse(ThreatDamage.isThreat(loaded), "a bounce gave the raider's mark back to an arrow a plain skeleton took over: tags " + loaded.getTags());
		CompoundTag again = new CompoundTag();
		helper.assertTrue(loaded.save(again), "setup: the arrow didn't save");
		Arrow reloaded = (Arrow) EntityType.create(again, level).orElse(null);
		helper.assertTrue(reloaded != null, "setup: the arrow didn't load");
		helper.assertFalse(ThreatDamage.isThreat(reloaded), "an arrow a plain skeleton took over is a threat's after a save and load: tags " + reloaded.getTags());

		players.setOwner(player);
		helper.assertFalse(ThreatDamage.isThreat(players), "a player's arrow is a threat's");
		players.setOwner(other); // a raider takes it over
		other.discard();
		helper.assertTrue(ThreatDamage.isThreat(players), "a player's arrow a raider took over isn't a threat's once that raider is gone: tags " + players.getTags());

		Pillager third = EntityType.PILLAGER.create(level);
		helper.assertTrue(third != null, "setup: no pillager");
		third.addTag(Threats.RAIDER_TAG);
		taken.setOwner(third);
		taken.setOwner(player); // the player knocks it back
		third.discard();
		helper.assertFalse(ThreatDamage.isThreat(taken), "a raider's arrow a player took over is still a threat's: tags " + taken.getTags());
		helper.succeed();
	}
}
