package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

/**
 * B832: a fisherman's bite is the delay times its steps, never shorter for a bigger workplaceBuildDelay (the real
 * fishermen at a huge rule are QaBuildDelayFisher1102BugTests). B833: the three village lines at a count of one (the
 * hall's buttons and the chronicle are QaOneCount1102cBugTests); here every new sentence word for word, and a raid of
 * one fought off still counts in the Founder's story of the village.
 */
public class SingularCountsAndBiteGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Every delay up to the rule's largest, at the fewest and most steps: never below the smaller delay's bite. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBiteNeverComesSoonerForABiggerDelay(GameTestHelper helper) {
		helper.assertTrue(FisherWork.biteTicks(8, 15) == 120 && FisherWork.biteTicks(8, 60) == 480, "the default bite: "
			+ FisherWork.biteTicks(8, 15) + " to " + FisherWork.biteTicks(8, 60));
		helper.assertTrue(FisherWork.biteTicks(0, 30) == 0, "no delay: " + FisherWork.biteTicks(0, 30));
		int[] delays = {1, 8, 1000, 35_791_394, 35_791_395, 71_582_788, 1 << 30, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
		for (int steps : new int[] {1, 15, 60}) {
			int last = 0;
			for (int delay : delays) {
				int bite = FisherWork.biteTicks(delay, steps);
				helper.assertTrue(bite >= last && bite >= Math.min(delay, Integer.MAX_VALUE / steps),
					"delay " + delay + " x " + steps + " steps bites after " + bite + " ticks, sooner than " + last);
				last = bite;
			}
			helper.assertTrue(last == Integer.MAX_VALUE, "the largest rule x " + steps + ": " + last);
		}
		helper.succeed();
	}

	/** Each new singular sentence, word for word, and its plural beside it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theVillageLinesReadRightForOne(GameTestHelper helper) {
		check(helper, Words.counted("screen.aliveworkplace.hall.meal_kinds", 1, 1, 3).getString(), "1 kind of meal in the store (3 or more for a varied diet)");
		check(helper, Words.counted("screen.aliveworkplace.hall.meal_kinds", 2, 2, 3).getString(), "2 kinds of meal in the store (3 or more for a varied diet)");
		check(helper, Words.counted("screen.aliveworkplace.hall.mercenaries_here", 1, 1).getString(), "1 mercenary is here until dawn");
		check(helper, Words.counted("screen.aliveworkplace.hall.mercenaries_here", 3, 3).getString(), "3 mercenaries are here until dawn");
		check(helper, Words.counted("chronicle.aliveworkplace.raid_won", 1, 1).getString(), "The village fought off the raid (1 monster)");
		check(helper, Words.counted("chronicle.aliveworkplace.raid_won", 0, 0).getString(), "The village fought off the raid (0 monsters)");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String got, String expected) {
		helper.assertTrue(got.equals(expected), "'" + got + "', expected '" + expected + "'");
	}

	/** A real raid of one zombie, slain: the chronicle says "(1 monster)" and the Founder's story still tells it. */
	//$ gametest_ticks_batch AREA '60' '"singularRaidOfOneStory"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "singularRaidOfOneStory")
	public void aRaidOfOneFoughtOffStaysInTheFoundersStory(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(11, 2, 11), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(11, 2, 11));
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> VillageRaids.forget(hall));
		helper.runAfterDelay(5, () -> {
			Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
			z.setNoAi(true);
			z.addTag(VillageRaids.TAG);
			VillageRaids.track(level, hall, 1);
			z.kill();
			helper.runAfterDelay(3, () -> {
				VillageRaids.tick(level, hall, 8, 0, Chronicle.day(level) - 10, d -> { });
				helper.assertTrue(VillageRaids.active(hall).isEmpty(), "setup: the raid didn't end");
				List<String> story = Founder.story(level, hall).stream().map(e -> e.text().getString()).toList();
				helper.assertTrue(story.contains("The village fought off the raid (1 monster)"), "the Founder's story: " + story);
				helper.succeed();
			});
		});
	}
}
