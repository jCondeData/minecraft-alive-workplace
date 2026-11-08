package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** QA bug test split out of QaSiegeDripstoneEdges1108dGameTests (lane-d-1108-1608): fails until the bug is fixed. */
public class QaSpikeGoneThrower1108dBugTests {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A raider's trident still in flight when its thrower has gone (left at dawn, or called off: discarded): it is still
	 * "a projectile one shot", so the builder's spike stands.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSpikeEdgeGoneThrower1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSpikeEdgeGoneThrower1108d")
	public void aRaidersTridentWhoseThrowerLeftStillLeavesTheSpike(GameTestHelper helper) {
		ServerLevel level = QaSiegeDripstoneEdges1108dGameTests.spike(helper, true);
		Drowned raider = QaSiegeDripstoneEdges1108dGameTests.drowned(helper, true);
		raider.moveTo(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
		helper.assertTrue(level.addFreshEntity(raider), "setup: the raider wasn't added");
		ThrownTrident flying = QaSiegeDripstoneEdges1108dGameTests.trident(helper, raider, QaSiegeDripstoneEdges1108dGameTests.SPIKE);
		raider.discard(); // left with the raid at dawn while the trident flew
		QaSiegeDripstoneEdges1108dGameTests.hit(helper, flying, QaSiegeDripstoneEdges1108dGameTests.SPIKE);
		helper.assertTrue(helper.getBlockState(QaSiegeDripstoneEdges1108dGameTests.SPIKE).is(Blocks.POINTED_DRIPSTONE),
			"a raider's trident, thrown before its thrower left at dawn, broke the builders' spike: " + helper.getBlockState(QaSiegeDripstoneEdges1108dGameTests.SPIKE));
		helper.succeed();
	}
}
