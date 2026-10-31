package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * B725 (QA 2026-10-31, lane-d-1031-1908): a quiet village's long-road caravan must not go missing to bandits (32.21:
 * threat level 0 "no raids, lairs, sieges or disasters at all"; At peace "no raids, lairs or disasters there", the
 * sending village or the receiving one; Peaceful "nothing comes"). Each sends three caravans with the chance forced to
 * 1 in 1 and checks none is lost, then lifts the setting and checks one is (the setup proves something). Helpers are
 * {@link QaLostCaravanThreats1031GameTests}'s.
 */
public class QaLostCaravanQuietBugTests implements FabricGameTest {
	static final String AREA = QaLostCaravanGameTests.AREA;
	private static final BlockPos STOREHOUSE = new BlockPos(5, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(5, 2, 4);

	private static BlockPos[] road(GameTestHelper helper) {
		return QaLostCaravanThreats1031GameTests.road(helper);
	}

	private static Caravans.Data route(GameTestHelper helper, BlockPos a, BlockPos b) {
		return QaLostCaravanThreats1031GameTests.route(helper, a, b);
	}

	private static boolean send(GameTestHelper helper, Caravans.Data data, BlockPos a, BlockPos b) {
		return QaLostCaravanThreats1031GameTests.send(helper, data, a, b);
	}

	/** No arc of either village, and no arc mob (a bandit or a captain) anywhere in the test area. */
	private static void nothingCame(GameTestHelper helper, BlockPos a, BlockPos b, String why) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Arcs.running(level, a) == null && Arcs.running(level, b) == null,
			why + ": a story started (" + Arcs.running(level, a) + ", " + Arcs.running(level, b) + ")");
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
		helper.assertTrue(level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(Arcs.MOB_TAG)).isEmpty(), why + ": a story mob is about");
	}

	// ---- the threat level --------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanThreatLevel0"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanThreatLevel0")
	public void atThreatLevelZeroNoCaravanGoesMissing(GameTestHelper helper) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = route(helper, a, b);
			Threats.LEVEL = 0;
			for (int day = 0; day < 3; day++) {
				helper.assertFalse(send(helper, data, a, b), "at threat level 0 (\"no raids, lairs, sieges or disasters at all\") the caravan went missing to bandits (send " + day + ")");
				nothingCame(helper, a, b, "threat level 0");
			}
			Threats.LEVEL = 2;
			helper.assertTrue(send(helper, data, a, b), "at threat level 2 the forced caravan didn't go missing (the setup proves nothing)");
			AliveWorkplace.LOG.info("QA Lost Caravan threats: level 0 kept 3 caravans on the road; level 2 lost one");
			helper.succeed();
		});
	}

	// ---- At peace ----------------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanSenderAtPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanSenderAtPeace")
	public void aSendingVillageAtPeaceLosesNoCaravan(GameTestHelper helper) {
		atPeace(helper, true);
	}

	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanReceiverAtPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanReceiverAtPeace")
	public void aReceivingVillageAtPeaceLosesNoCaravan(GameTestHelper helper) {
		atPeace(helper, false);
	}

	private static void atPeace(GameTestHelper helper, boolean sender) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity peaceful = (VillageHallBlockEntity) level.getBlockEntity(sender ? a : b);
			Caravans.Data data = route(helper, a, b);
			peaceful.setAtPeace(true, Chronicle.day(level));
			helper.assertTrue(Threats.atPeace(level, sender ? a : b), "setup: the village isn't At peace");
			String who = sender ? "the sending village" : "the receiving village";
			for (int day = 0; day < 3; day++) {
				helper.assertFalse(send(helper, data, a, b), "with " + who + " At peace (\"no raids, lairs or disasters there\") the caravan went missing to bandits (send " + day + ")");
				nothingCame(helper, a, b, who + " At peace");
			}
			peaceful.setAtPeace(false, Chronicle.day(level));
			helper.assertTrue(send(helper, data, a, b), "with peace lifted the forced caravan didn't go missing (the setup proves nothing)");
			helper.succeed();
		});
	}

	// ---- Peaceful difficulty -----------------------------------------------------------------------------------------

	/**
	 * "On Peaceful difficulty nothing comes": the bandits who take the caravan are hostile mobs, which can't exist on
	 * Peaceful, so the caravan arrives; on Normal it goes missing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanPeaceful"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanPeaceful")
	public void onPeacefulNoCaravanGoesMissing(GameTestHelper helper) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = route(helper, a, b);
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			for (int day = 0; day < 3; day++) {
				helper.assertFalse(send(helper, data, a, b), "on Peaceful difficulty (\"nothing comes\") the caravan went missing to bandits (send " + day + ")");
				nothingCame(helper, a, b, "Peaceful");
			}
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			helper.assertTrue(send(helper, data, a, b), "on Normal the forced caravan didn't go missing (the setup proves nothing)");
			helper.succeed();
		});
	}

	/**
	 * Whatever the first test finds: a Lost Caravan running on Peaceful must still be finishable. The bandits can't be
	 * there, but the cage opens, Tobin follows and walks home, and the story ends with him alive and the village's.
	 */
}
