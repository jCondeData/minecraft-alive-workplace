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
 * QA (lane D, 2026-11-02), bug tests for the Pharaoh's curse (32.9) at a raid's end: a husk that fell beside the living
 * Pharaoh in the last three seconds before the raid was called off ("its raiders go at once", 32.21) or before the
 * desert raiders left at noon, rises after they've gone, a raider (aliveworkplace_raider) in a village with no raid on.
 * Also when the raid is fought off (the Pharaoh, its last, killed a second after the husk). All three fail on main
 * 9e0535007 (B828).
 */
public class QaRisings1102dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "setup: expected desert_raiders.json loaded, found none");
		return culture.get();
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m instanceof Husk || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, box.inflate(4), e -> true).forEach(ItemEntity::discard);
			Risings.forget();
		});
	}

	private static Husk pharaoh(GameTestHelper helper, BlockPos at) {
		Husk pharaoh = helper.spawn(EntityType.HUSK, at);
		pharaoh.setNoAi(true);
		pharaoh.addTag(VillageRaids.TAG);
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "setup: expected the Pharaoh marked as a raiser");
		return pharaoh;
	}

	private static Husk husk(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	private static List<Husk> risen(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
	}

	/** A hall in the middle and a desert raid on it, begun {@code ago} ticks back. */
	private static BlockPos raid(GameTestHelper helper, long ago) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		Leftovers.halls(helper);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageRaids.forget(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		VillageHalls.RADIUS = 12;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ThreatData.get(level).begin(new ThreatData.Under(hall, DESERT, 3, level.getGameTime() - ago));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a desert raid under way");
		return hall;
	}

	private static List<Mob> raidersLeft(GameTestHelper helper, BlockPos hall) {
		return VillageRaids.raiders(helper.getLevel(), hall);
	}

	/**
	 * Called off ({@code /workplace threat end}) a second after a husk fell beside the Pharaoh: "its raiders go at once".
	 * Two seconds later nobody of that raid is about: no husk rises in the village as a raider of a raid that is over.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsCalledOff1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsCalledOff1102d")
	public void aHuskThatFellJustBeforeTheRaidWasCalledOffNeverRisesAfterIt(GameTestHelper helper) {
		setUp(helper);
		BlockPos hall = raid(helper, 100);
		helper.getLevel().setDayTime((helper.getLevel().getDayTime() / 24000) * 24000 + 1000); // morning: the raid isn't over by itself
		pharaoh(helper, new BlockPos(8, 2, 8));
		Husk husk = husk(helper, new BlockPos(20, 2, 20));
		BlockPos fell = husk.blockPosition();
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the husk waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(VillageRaids.callOff(helper.getLevel(), hall), "setup: expected the raid called off");
			helper.assertTrue(raidersLeft(helper, hall).isEmpty(), "raiders still about right after the call-off: " + raidersLeft(helper, hall).size());
		});
		helper.runAfterDelay(100, () -> {
			List<Mob> left = raidersLeft(helper, hall);
			helper.assertTrue(left.isEmpty(), "a raider rose in the village after the raid was called off (no raid under way: "
				+ ThreatData.get(helper.getLevel()).raid(hall).isEmpty() + "): " + left.stream().map(m -> m.getType().toShortString() + " at "
				+ m.blockPosition().toShortString()).toList() + ", fell at " + fell.toShortString());
			helper.succeed();
		});
	}

	/**
	 * At noon the desert raiders leave (their hours are until_noon). A husk that fell beside the Pharaoh a second before
	 * they left doesn't rise afterwards, alone in the village with nobody's raid on.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsNoon1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsNoon1102d")
	public void aHuskThatFellJustBeforeTheRaidersLeftAtNoonNeverRisesAfterThem(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = raid(helper, 3000);
		helper.assertTrue(desert(helper).hours() == Culture.Hours.UNTIL_NOON, "setup: expected the desert raiders' hours until noon");
		pharaoh(helper, new BlockPos(8, 2, 8));
		Husk husk = husk(helper, new BlockPos(20, 2, 20));
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 5990);
		husk.kill();
		helper.runAfterDelay(20, () -> {
			level.setDayTime((level.getDayTime() / 24000) * 24000 + 6100);
			VillageRaids.tick(level, hall, 0, 0, Long.MIN_VALUE / 2, d -> { });
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "setup: expected the desert raid over after noon");
			helper.assertTrue(raidersLeft(helper, hall).isEmpty(), "raiders still about right after they left at noon: " + raidersLeft(helper, hall).size());
		});
		helper.runAfterDelay(100, () -> {
			List<Mob> left = raidersLeft(helper, hall);
			helper.assertTrue(left.isEmpty(), "a raider rose in the village after the desert raiders left at noon: "
				+ left.stream().map(m -> m.getType().toShortString() + " at " + m.blockPosition().toShortString()).toList());
			helper.succeed();
		});
	}

	/**
	 * Fought off: a husk falls beside the Pharaoh, and a second later the Pharaoh, the raid's last, falls too. The raid
	 * is over, won; two seconds on, no raider of it gets up in the village.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsFoughtOff1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsFoughtOff1102d")
	public void aHuskThatFellJustBeforeTheRaidWasFoughtOffNeverRisesAfterIt(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = raid(helper, 100);
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 1000); // morning: the desert raiders' hours (until noon)
		Husk pharaoh = pharaoh(helper, new BlockPos(8, 2, 8));
		Husk husk = husk(helper, new BlockPos(20, 2, 20));
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: expected the husk waiting to rise, waiting: " + Risings.pending());
		helper.runAfterDelay(20, () -> {
			pharaoh.kill();
			VillageRaids.tick(level, hall, 0, 0, Long.MIN_VALUE / 2, d -> { });
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "setup: expected the raid over once its last raider fell");
		});
		helper.runAfterDelay(100, () -> {
			List<Mob> left = raidersLeft(helper, hall);
			helper.assertTrue(left.isEmpty(), "a raider rose in the village after the raid was fought off: "
				+ left.stream().map(m -> m.getType().toShortString() + " at " + m.blockPosition().toShortString()).toList());
			helper.succeed();
		});
	}
}
