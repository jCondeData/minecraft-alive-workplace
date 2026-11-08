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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-08) for the Wall Sentry's leash (ROADMAP 29.15, {@link WallSentries}: "it fights what comes within
 * 6 blocks of the post, never steps more than 3 blocks off it", and walks back "whenever it is more than 3 blocks off it,
 * or more than a step off it with no foe in range"). A local mutation run of WallSentries (lane-d-1108-0108, 12 mutants
 * against the 8 sentry and golem classes) let 6 survive, 4 of them in the hold goal's rules: {@code canUse}'s
 * {@code ||}/{@code &&} swapped either way, its leash check at exactly 3 blocks, and {@code canContinueToUse}'s 2-block
 * hand-back moved to sqrt(8). These check each rule from the spec's side, on a posted sentry whose own goals are taken
 * away (a fresh HoldGoal is asked at set positions, in the same tick, so nothing moves the golem in between). Each test has a batch of its own: Leftovers.clear in a neighbour
 * would discard this one's golem.
 */
public class QaSentryLeash1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BATCH = "qaSentryLeash1108d";
	private static final BlockPos POST = new BlockPos(15, 2, 15);

	private static ItemStack map(ServerLevel level, List<BlockPos> points) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), points));
		return map;
	}

	/** A sentry posted at POST by a player's sneak-click with a map, with every goal but its HoldGoal taken away. */
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
		player.setItemInHand(InteractionHand.MAIN_HAND, map(level, List.of(helper.absolutePos(POST))));
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, golem, null);
		helper.assertTrue(helper.absolutePos(POST).equals(WallSentries.post(golem)), "setup: not posted: " + WallSentries.post(golem));
		return golem;
	}

	/** A still husk {@code dx} blocks east of the post's center (well inside the 6-block range for dx up to 5). */
	private static Husk foe(GameTestHelper helper, double dx) {
		Husk husk = EntityType.HUSK.create(helper.getLevel());
		helper.assertTrue(husk != null, "setup: no husk");
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		husk.moveTo(at.x + dx, at.y, at.z, 0f, 0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		helper.getLevel().addFreshEntity(husk);
		Leftovers.after(helper, husk::discard);
		return husk;
	}

	/** Sets the golem {@code dz} blocks south of its post's bottom center, on the floor, and still. */
	private static void standOff(GameTestHelper helper, IronGolem golem, double dz) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		golem.setPos(at.x, at.y, at.z + dz);
		golem.setDeltaMovement(Vec3.ZERO);
	}

	private static double off(GameTestHelper helper, IronGolem golem) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		double dx = golem.getX() - at.x;
		double dz = golem.getZ() - at.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * More than a step off its post (2.5 blocks, inside the 3-block leash) with no foe anywhere, the sentry walks back:
	 * within 200 ticks it stands within a block of the post. (Kills canUse's first {@code ||} turned into {@code &&}.)
	 */
	//$ gametest_ticks_batch AREA '300' 'BATCH + "Walk"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = BATCH + "Walk")
	public void aSentryTwoAndAHalfOffWithNoFoeWalksBackToItsPost(GameTestHelper helper) {
		IronGolem golem = sentry(helper);
		standOff(helper, golem, 2.5);
		helper.assertTrue(new WallSentries.HoldGoal(golem).canUse(), "a sentry 2.5 blocks off its post with no foe doesn't want to walk back");
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (off(helper, golem) <= 1.0) {
				helper.succeed();
			} else if (ticks[0] >= 200) {
				helper.fail(String.format("a sentry 2.5 blocks off its post with no foe is still %.2f blocks off it after 200 ticks", off(helper, golem)));
			}
		});
	}

	/**
	 * Inside its leash (2.5 blocks off) with a foe 4 blocks from the post (inside the 6-block range), the sentry stays to
	 * fight: the hold goal doesn't take its legs and the foe stays its target. (Kills canUse's {@code &&} turned into
	 * {@code ||}.) Without the foe the same spot sends it back.
	 */
	//$ gametest_batch AREA 'BATCH + "Fight"'
	@GameTest(template = AREA, batch = BATCH + "Fight")
	public void aSentryInsideItsLeashWithAFoeInRangeStaysToFight(GameTestHelper helper) {
		IronGolem golem = sentry(helper);
		Husk husk = foe(helper, 4.0);
		standOff(helper, golem, 2.5);
		golem.setTarget(husk);
		WallSentries.HoldGoal goal = new WallSentries.HoldGoal(golem);
		helper.assertFalse(goal.canUse(), "a sentry 2.5 blocks off its post walks back though a foe stands 4 blocks from the post");
		helper.assertTrue(golem.getTarget() == husk, "a foe 4 blocks from the post was let go: " + golem.getTarget());
		golem.setTarget(null);
		helper.assertTrue(goal.canUse(), "control: with no foe, 2.5 blocks off the post doesn't send the sentry back");
		helper.succeed();
	}

	/**
	 * The leash is "more than 3 blocks": exactly 3 blocks off with a foe in range is still inside it (the sentry fights
	 * on), 3.1 blocks off is past it (it walks back, foe or not). (Kills {@code away > LEASH²} turned into {@code >=},
	 * and the first {@code ||} turned into {@code &&}.)
	 */
	//$ gametest_batch AREA 'BATCH + "Three"'
	@GameTest(template = AREA, batch = BATCH + "Three")
	public void exactlyThreeBlocksOffIsInsideTheLeashAndOnePointOneMoreIsPast(GameTestHelper helper) {
		IronGolem golem = sentry(helper);
		Husk husk = foe(helper, 4.0);
		WallSentries.HoldGoal goal = new WallSentries.HoldGoal(golem);
		standOff(helper, golem, 3.0);
		golem.setTarget(husk);
		helper.assertFalse(goal.canUse(), "exactly 3 blocks off its post (the leash, not past it) the sentry walks back from a foe in range");
		standOff(helper, golem, 3.1);
		golem.setTarget(husk);
		helper.assertTrue(goal.canUse(), "3.1 blocks off its post, past the 3-block leash, the sentry stays out to fight");
		helper.succeed();
	}

	/**
	 * Once walking back, the sentry keeps walking until it is within 2 blocks (a block inside the leash) and only then
	 * gives its legs back to the fight: 2.5 blocks off with a foe in range it walks on, 1.9 off with the foe it stops,
	 * 1.9 off with no foe it walks on to the post, and within a block of it it is home. (Kills canContinueToUse's
	 * {@code (LEASH - 1)²} moved to {@code (LEASH + 1) * (LEASH - 1)}.)
	 */
	//$ gametest_batch AREA 'BATCH + "Legs"'
	@GameTest(template = AREA, batch = BATCH + "Legs")
	public void walkingBackItGivesTheFightItsLegsOnlyWithinTwoBlocks(GameTestHelper helper) {
		IronGolem golem = sentry(helper);
		Husk husk = foe(helper, 4.0);
		WallSentries.HoldGoal goal = new WallSentries.HoldGoal(golem);
		golem.setTarget(husk);
		standOff(helper, golem, 2.5);
		helper.assertTrue(goal.canContinueToUse(), "walking back 2.5 blocks off its post (past the 2-block mark) the sentry stopped for a foe in range");
		standOff(helper, golem, 1.9);
		helper.assertFalse(goal.canContinueToUse(), "1.9 blocks off its post with a foe in range, the sentry keeps walking instead of fighting");
		golem.setTarget(null);
		helper.assertTrue(goal.canContinueToUse(), "1.9 blocks off its post with no foe, the sentry stopped short of it");
		standOff(helper, golem, 0.9);
		helper.assertFalse(goal.canContinueToUse(), "0.9 blocks off its post the sentry still walks back");
		helper.succeed();
	}
}
