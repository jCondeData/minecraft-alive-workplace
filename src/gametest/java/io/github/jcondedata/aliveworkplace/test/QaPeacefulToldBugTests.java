package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-31), from B208's fix as written in the ROADMAP ("Peaceful is told to players near") and the
 * wiki's lairs.md: on Peaceful, a player near a village whose foes can't come is told why, once a day. A refusal far
 * from every player earlier the same day (another village, nobody there) must not use up that day's telling.
 */
public class QaPeacefulToldBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '100' '"qaPeacefulToldFarFirst"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPeacefulToldFarFirst")
	public void aRefusalFarFromEveryoneDoesntUseUpTheDaysWord(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		HostileSpawns.forget();
		Leftovers.after(helper, HostileSpawns::forget);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, seen, "qa-peaceful");
		BlockPos stand = helper.absolutePos(new BlockPos(4, 2, 4));
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		helper.runAfterDelay(2, () -> {
			// a camp's foe refused at another village 500 blocks off, with nobody there
			Pillager far = EntityType.PILLAGER.create(level);
			far.moveTo(helper.absoluteVec(new Vec3(500, 2, 6)));
			helper.assertTrue(!HostileSpawns.add(level, far, "test camp far off", Difficulty.PEACEFUL), "setup: the far pillager spawned on Peaceful");
			// then, the same day, one by this player's village
			Pillager near = EntityType.PILLAGER.create(level);
			near.moveTo(helper.absoluteVec(new Vec3(6, 2, 6)));
			helper.assertTrue(!HostileSpawns.add(level, near, "test camp", Difficulty.PEACEFUL), "setup: the near pillager spawned on Peaceful");
			long told = seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.threat.peaceful")).count();
			helper.assertTrue(told == 1, "the player by the second village was told " + told + " times (expected once: the first refusal of the day was 500 blocks"
				+ " from every player, and used up the day's word): " + seen);
			helper.succeed();
		});
	}
}
