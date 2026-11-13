package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-13), B1121: a Wall Sentry off its post with no foe walks back to it (ROADMAP 29.15: "it walks back
 * whenever it is more than a step off it with no foe in range"), whatever way it faced when it was built. GolemSmith gives
 * a new golem a random facing; facing 43.5 degrees, a sentry set 2.5 blocks south of its post stops for good about 1.39
 * blocks off it, in the block next to the post, because its path there (accuracy 1) counts as already finished. The
 * same walk from a few other facings, as controls, ends within a block. Each test has a batch of its own.
 */
public class QaSentryYaw1113dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BATCH = "qaSentryYaw1113d";
	private static final BlockPos POST = new BlockPos(15, 2, 15);

	private static IronGolem sentry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(POST), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "setup: no sentry was built");
		Leftovers.after(helper, golem::discard);
		((MobAccessor) golem).aliveworkplace$goals().removeAllGoals(g -> !(g instanceof WallSentries.HoldGoal));
		((MobAccessor) golem).aliveworkplace$targets().removeAllGoals(g -> true);
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), List.of(helper.absolutePos(POST))));
		player.setItemInHand(InteractionHand.MAIN_HAND, map);
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, golem, null);
		helper.assertTrue(helper.absolutePos(POST).equals(WallSentries.post(golem)), "setup: not posted: " + WallSentries.post(golem));
		return golem;
	}

	private static double off(GameTestHelper helper, IronGolem golem) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		return Math.hypot(golem.getX() - at.x, golem.getZ() - at.z);
	}

	/** The sentry 2.5 blocks south of its post, facing {@code yaw}, no foe: within 300 ticks it is within a block of it. */
	private static void walksBack(GameTestHelper helper, float yaw) {
		IronGolem golem = sentry(helper);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		golem.setPos(at.x, at.y, at.z + 2.5);
		golem.setDeltaMovement(Vec3.ZERO);
		golem.setYRot(yaw);
		golem.yBodyRot = yaw;
		golem.yHeadRot = yaw;
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (off(helper, golem) <= 1.0) {
				helper.succeed();
			} else if (ticks[0] >= 300) {
				helper.fail(String.format("a sentry 2.5 blocks off its post with no foe, built facing %.1f degrees, stopped %.2f blocks off it"
					+ " (at %.3f, %.3f from the post's corner; navigation done %s)", yaw, off(helper, golem),
					golem.getX() - helper.absolutePos(POST).getX(), golem.getZ() - helper.absolutePos(POST).getZ(), golem.getNavigation().isDone()));
			}
		});
	}

	//$ gametest_ticks_batch AREA '400' 'BATCH + "43"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = BATCH + "43")
	public void aSentryFacing43DegreesWalksBackToItsPost(GameTestHelper helper) {
		walksBack(helper, 43.5f);
	}

	//$ gametest_ticks_batch AREA '400' 'BATCH + "44"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = BATCH + "44")
	public void aSentryFacing44DegreesWalksBackToItsPost(GameTestHelper helper) {
		walksBack(helper, 44.0f);
	}

	//$ gametest_ticks_batch AREA '400' 'BATCH + "0"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = BATCH + "0")
	public void controlASentryFacingSouthWalksBackToItsPost(GameTestHelper helper) {
		walksBack(helper, 0f);
	}

	//$ gametest_ticks_batch AREA '400' 'BATCH + "180"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = BATCH + "180")
	public void controlASentryFacingNorthWalksBackToItsPost(GameTestHelper helper) {
		walksBack(helper, 180f);
	}
}
