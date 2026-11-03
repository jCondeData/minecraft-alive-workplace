package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-02), verifying B828 (85ed8afb8: a raider who fell beside the Pharaoh stays down once its raid is
 * over) from 32.9's text ("rises once more after the delay, where it fell ... the same mob as it was (its raid)") and
 * 32.21's ("its raiders go at once"), at the boundaries the bug tests don't reach:
 * <ul>
 * <li>called off and a new raid of the same culture begun on the same village before the rise: the husk was the old
 *     raid's, so it stays down;</li>
 * <li>a neighbour village's raid called off: the husk was of the raid still on at its own, nearer, village, so it
 *     rises, a raider of it;</li>
 * <li>a husk that was no raider (the Pharaoh's curse outside any raid) rises as before, not made a raider.</li>
 * </ul>
 */
public class QaRisingsVerify1102dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "setup: expected desert_raiders.json loaded, found none");
		return culture.get();
	}

	/** Clears leftovers and the curse's memory; puts back the day, the radius and the halls' raids afterwards. */
	private static void setUp(GameTestHelper helper, BlockPos... halls) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m instanceof Husk || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, box.inflate(4), e -> true).forEach(ItemEntity::discard);
			Risings.forget();
			for (BlockPos rel : halls) {
				BlockPos hall = helper.absolutePos(rel);
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
			}
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		VillageHalls.RADIUS = 12;
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 1000); // morning: the desert raiders' hours (until noon)
		for (BlockPos rel : halls) {
			helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		}
	}

	private static BlockPos raid(GameTestHelper helper, BlockPos rel, long ago) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(rel);
		ThreatData.get(level).begin(new ThreatData.Under(hall, DESERT, 3, level.getGameTime() - ago));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a desert raid under way at " + rel.toShortString());
		return hall;
	}

	private static Husk pharaoh(GameTestHelper helper, BlockPos at, boolean raider) {
		Husk pharaoh = helper.spawn(EntityType.HUSK, at);
		pharaoh.setNoAi(true);
		if (raider) {
			pharaoh.addTag(VillageRaids.TAG);
		}
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "setup: expected the Pharaoh marked as a raiser");
		return pharaoh;
	}

	private static Husk husk(GameTestHelper helper, BlockPos at, boolean raider) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		if (raider) {
			husk.addTag(VillageRaids.TAG);
		}
		return husk;
	}

	private static List<Husk> risen(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
	}

	/**
	 * A husk falls beside the Pharaoh; a second later the raid is called off and, half a second after, the operator
	 * starts a new desert raid on the same village. The fallen husk was the old raid's: it doesn't rise into the new one.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsNewRaid1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsNewRaid1102d")
	public void aHuskOfACalledOffRaidStaysDownWhenANewRaidBeginsBeforeItWouldRise(GameTestHelper helper) {
		BlockPos rel = new BlockPos(15, 2, 15);
		setUp(helper, rel);
		ServerLevel level = helper.getLevel();
		BlockPos hall = raid(helper, rel, 100);
		pharaoh(helper, new BlockPos(8, 2, 8), true);
		Husk husk = husk(helper, new BlockPos(20, 2, 20), true);
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the husk waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(20, () -> helper.assertTrue(VillageRaids.callOff(level, hall), "setup: expected the raid called off"));
		helper.runAfterDelay(30, () -> {
			ThreatData.get(level).begin(new ThreatData.Under(hall, DESERT, 3, level.getGameTime()));
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected the new desert raid under way");
		});
		helper.runAfterDelay(100, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.isEmpty(), "a husk of the raid that was called off rose into the new raid begun after it: "
				+ up.stream().map(h -> h.blockPosition().toShortString()).toList());
			helper.succeed();
		});
	}

	/**
	 * Two villages side by side, each with a desert raid. A husk falls beside the Pharaoh in the second village (its
	 * own hall 4 blocks off, the first's 31); then the first village's raid is called off. The husk's raid is still on,
	 * so it rises, a raider of it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsNeighbour1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsNeighbour1102d")
	public void aNeighbourVillagesCallOffDoesNotKeepDownAHuskOfARaidStillOn(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		// The first village's own raider, so a round of its hall before the call-off doesn't end its raid as fought off
		// (B856: the Pharaoh and the husk, nearer the second village, are that raid's, not the first's).
		husk(helper, new BlockPos(6, 2, 6), true);
		pharaoh(helper, new BlockPos(22, 2, 22), true);
		Husk husk = husk(helper, new BlockPos(24, 2, 24), true);
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the husk waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
			helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "setup: expected the second village's raid still on");
		});
		helper.runAfterDelay(100, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "the husk of the raid still on in its own village didn't rise after the neighbour village's raid was called off, risen: "
				+ up.size());
			helper.assertTrue(up.get(0).getTags().contains(VillageRaids.TAG), "expected the risen husk still a raider (" + VillageRaids.TAG + ")");
			helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false), "expected the second village's raid still the same one");
			helper.succeed();
		});
	}

	/**
	 * B849: two villages side by side, each with a desert raid. Calling off the first's sends its own raider away, but
	 * not the second's, though it stands within the first's reach (nearer its own hall); the second village's round then
	 * finds its raider still about, so its raid goes on, not ended as fought off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRisingsNeighbourRaiders1103"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRisingsNeighbourRaiders1103")
	public void aNeighbourVillagesCallOffLeavesTheOtherRaidsRaidersAndItGoesOn(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = husk(helper, new BlockPos(6, 2, 6), true);
		Husk ours = husk(helper, new BlockPos(22, 2, 22), true);
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
		helper.assertTrue(theirs.isRemoved(), "the first village's own raider stayed after its raid was called off");
		helper.assertTrue(!ours.isRemoved() && ours.isAlive(), "the second village's raider (4 blocks from its hall, 28 from the first's) was sent away when the first village's raid was called off");
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's raid ended in its round after the neighbour's was called off: " + ThreatData.get(level).raid(hallB));
		helper.succeed();
	}

	/**
	 * B856: two raided villages side by side. The first's raiders are all killed; only the second's raider is about,
	 * within the first's reach but nearer its own hall. The first village's round counts only its own raiders, so its
	 * raid ends (fought off), and the second's goes on with its raider still there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidRoundOwn1103"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidRoundOwn1103")
	public void aVillagesRoundEndsItsRaidOnceItsOwnRaidersAreGoneThoughANeighboursAreNear(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = husk(helper, new BlockPos(6, 2, 6), true);
		Husk ours = husk(helper, new BlockPos(22, 2, 22), true);
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		theirs.kill();
		helper.assertTrue(!theirs.isAlive(), "setup: expected the first village's raider dead");
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(),
			"the first village's raid went on in its round with all its raiders dead, kept up by the second village's raider (4 blocks from its own hall, 28 from the first's): "
				+ ThreatData.get(level).raid(hallA));
		helper.assertTrue(!ours.isRemoved() && ours.isAlive(), "the second village's raider was sent away by the first village's round");
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's raid ended in its round though its raider is still about: " + ThreatData.get(level).raid(hallB));
		helper.succeed();
	}

	/**
	 * B856: the first village's raiders leave when its hours are over (desert raiders: at noon), and only they: the
	 * second village's raider, within the first's reach but nearer its own hall, stays, and its raid goes on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidRoundOwnLeave1103"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidRoundOwnLeave1103")
	public void aVillagesRaidersLeavingAtTheirHourLeaveANeighboursRaiderBehind(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 7000); // afternoon: the desert raiders' hours are over
		BlockPos hallA = raid(helper, relA, 2000);
		BlockPos hallB = raid(helper, relB, 2000);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = husk(helper, new BlockPos(6, 2, 6), true);
		Husk ours = husk(helper, new BlockPos(22, 2, 22), true);
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(), "setup: expected the first village's raid over at its hour: " + ThreatData.get(level).raid(hallA));
		helper.assertTrue(theirs.isRemoved(), "the first village's own raider stayed after its raid left");
		helper.assertTrue(!ours.isRemoved() && ours.isAlive(), "the second village's raider (4 blocks from its hall, 28 from the first's) left with the first village's raiders");
		helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's raid is no longer under way: " + ThreatData.get(level).raid(hallB));
		helper.succeed();
	}

	/** No raid anywhere: a husk that was no raider falls beside a Pharaoh and rises as before, not made a raider. */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsNoRaid1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsNoRaid1102d")
	public void aHuskThatWasNoRaiderStillRisesBesideThePharaoh(GameTestHelper helper) {
		setUp(helper);
		pharaoh(helper, new BlockPos(8, 2, 8), false);
		Husk husk = husk(helper, new BlockPos(12, 2, 12), false);
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the husk waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(100, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "expected the husk that was no raider risen beside the Pharaoh, risen: " + up.size());
			helper.assertTrue(!up.get(0).getTags().contains(VillageRaids.TAG), "the husk that was no raider rose as a raider");
			helper.succeed();
		});
	}
}
