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
 * QA (lane-d-1107-1008), from B208/B708's spec: on Peaceful "each player within 96 blocks is told once a day". 95 and 97
 * blocks are tested in QaThreatFixes1031GameTests; here the edge itself: a player exactly 96 blocks from the refused foe
 * is within 96 and is told, one a hair farther is not (kills the survivor HostileSpawns:176 '>' to '>=' of a mutation
 * spot check).
 */
public class QaPeacefulRadius1107dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String PEACEFUL = "message.aliveworkplace.threat.peaceful";

	/** A refusal on Peaceful: the player exactly 96 blocks east is told once, the one 96.05 blocks west is not. */
	//$ gametest_ticks_batch AREA '60' '"qaPeacefulRadius1107d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPeacefulRadius1107d")
	public void aPlayerExactly96BlocksOffIsToldAndOneFartherIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		HostileSpawns.forget();
		Leftovers.after(helper, HostileSpawns::forget);
		List<Component> seenEdge = new ArrayList<>();
		List<Component> seenPast = new ArrayList<>();
		ServerPlayer edge = RealmGameTests.listener(helper, seenEdge, "qa-edge-96");
		ServerPlayer past = RealmGameTests.listener(helper, seenPast, "qa-past-96");
		Vec3 foe = helper.absoluteVec(new Vec3(8.5, 2, 8.5));
		Vec3 atEdge = foe.add(96, 0, 0);
		Vec3 atPast = foe.add(-96.05, 0, 0);
		edge.teleportTo(atEdge.x, atEdge.y, atEdge.z);
		past.teleportTo(atPast.x, atPast.y, atPast.z);
		helper.runAfterDelay(2, () -> {
			edge.teleportTo(atEdge.x, atEdge.y, atEdge.z);
			past.teleportTo(atPast.x, atPast.y, atPast.z);
			Pillager p = EntityType.PILLAGER.create(level);
			p.moveTo(foe.x, foe.y, foe.z);
			helper.assertTrue(edge.distanceToSqr(p) == 96.0 * 96.0, "setup: the edge player is " + Math.sqrt(edge.distanceToSqr(p)) + " blocks off, not 96");
			helper.assertTrue(past.distanceToSqr(p) > 96.0 * 96.0, "setup: the far player is " + Math.sqrt(past.distanceToSqr(p)) + " blocks off");
			helper.assertTrue(!HostileSpawns.add(level, p, "qa test camp", Difficulty.PEACEFUL), "a pillager was put in the world on Peaceful");
			helper.assertTrue(told(seenEdge) == 1, "the player exactly 96 blocks off was told " + told(seenEdge) + " times (expected once: he is within 96)");
			helper.assertTrue(told(seenPast) == 0, "the player 96.05 blocks off was told " + told(seenPast) + " times (expected never)");
			helper.succeed();
		});
	}

	private static long told(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(PEACEFUL)).count();
	}
}
