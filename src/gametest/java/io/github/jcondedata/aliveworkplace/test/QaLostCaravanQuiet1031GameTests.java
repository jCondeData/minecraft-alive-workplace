package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1031-1613, config extremes): the Lost Caravan (31.20) in a quiet village. The Lost Caravan's arc lays a
 * raiders' stockade and spawns a raider captain and four bandits there (arcs/lost_caravan.json, chapter 2). The wiki
 * (peace.md) and 32.21 promise that at threat level 0 "nothing comes: no raids, no raiders' camps", and that a village
 * set At peace gets "no raiders, camps or disasters". So a caravan of such a village never goes missing to bandits.
 * Each test first proves the setup: the same caravan does go missing in a village that isn't quiet.
 */
public class QaLostCaravanQuiet1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static VillageHallBlockEntity setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		int chance = LostCaravanStory.CHANCE;
		int threat = Threats.LEVEL;
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, abs);
			VillageHalls.RADIUS = radius;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
			LostCaravanStory.CHANCE = chance;
			Threats.LEVEL = threat;
		});
		VillageHalls.RADIUS = 8;
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = 20;
		Arcs.DISABLED = Set.of();
		LostCaravanStory.CHANCE = 1; // every caravan on a long road would go missing
		Threats.LEVEL = 2;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static boolean lose(GameTestHelper helper, int seed) {
		BlockPos a = helper.absolutePos(HALL);
		return LostCaravanStory.lose(helper.getLevel(), a, a.offset(400, 0, 0), List.of(new ItemStack(Items.BREAD, 4)), RandomSource.create(seed));
	}

	/** The control: a village that isn't quiet loses the caravan, so the quiet case below tests the quiet rule only. */
	private static void controlLoses(GameTestHelper helper) {
		BlockPos a = helper.absolutePos(HALL);
		helper.assertTrue(lose(helper, 0) && Arcs.running(helper.getLevel(), a) != null, "setup: at threat level 2 the caravan didn't go missing");
		Arcs.stopAll(helper.getLevel(), a);
		helper.assertTrue(Arcs.running(helper.getLevel(), a) == null, "setup: the Lost Caravan didn't stop");
	}

	/** Threat level 0 ("nothing comes"): no caravan goes missing to the bandits' stockade. */
	//$ gametest_ticks_batch AREA '40' '"qaLostCaravanQuietZero"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLostCaravanQuietZero")
	public void atThreatLevelZeroNoCaravanGoesMissingToBandits(GameTestHelper helper) {
		setUp(helper);
		controlLoses(helper);
		Threats.LEVEL = 0;
		BlockPos a = helper.absolutePos(HALL);
		for (int seed = 0; seed < 5; seed++) {
			helper.assertTrue(!lose(helper, seed) && Arcs.running(helper.getLevel(), a) == null,
				"at threat level 0 a caravan went missing and the Lost Caravan's raiders' stockade began (seed " + seed + ")");
		}
		helper.succeed();
	}

	/** A village set At peace on its Defense page: none of its caravans goes missing to bandits. */
	//$ gametest_ticks_batch AREA '40' '"qaLostCaravanQuietPeace"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLostCaravanQuietPeace")
	public void anAtPeaceVillagesCaravanNeverGoesMissingToBandits(GameTestHelper helper) {
		VillageHallBlockEntity hall = setUp(helper);
		controlLoses(helper);
		hall.setAtPeace(true, 0);
		BlockPos a = helper.absolutePos(HALL);
		helper.assertTrue(Threats.quiet(helper.getLevel(), a), "setup: the At peace village isn't quiet");
		for (int seed = 0; seed < 5; seed++) {
			helper.assertTrue(!lose(helper, seed) && Arcs.running(helper.getLevel(), a) == null,
				"an At peace village's caravan went missing and the Lost Caravan's raiders' stockade began (seed " + seed + ")");
		}
		helper.succeed();
	}
}
