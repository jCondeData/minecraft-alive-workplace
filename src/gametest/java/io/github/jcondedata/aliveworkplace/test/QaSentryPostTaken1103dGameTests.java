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
 * QA (lane D, 2026-11-03) for the Wall Sentry (ROADMAP 29.15: "A blank map takes the post away"). A local mutation run
 * of WallSentries (lane-d-1103-1312) found that nothing tested what the sentry does once its post is taken while it is
 * walking back to it: deleting HoldGoal.stop()'s {@code getNavigation().stop()} survived. These check it: the post taken
 * mid-walk, the golem stops walking there, and given a map again it goes to the new post, not the old one.
 * <p>
 * The golem's other goals are taken away (only the sentry's HoldGoal is left), so a random stroll can't carry it toward
 * the old post by chance.
 */
public class QaSentryPostTaken1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos START = new BlockPos(3, 2, 3);
	private static final BlockPos OLD_POST = new BlockPos(26, 2, 26);
	private static final BlockPos NEW_POST = new BlockPos(3, 2, 26);

	private static ItemStack map(ServerLevel level, List<BlockPos> points) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), points));
		return map;
	}

	/** The player sneak-right-clicks the golem with {@code held}. */
	private static void give(ServerPlayer player, IronGolem golem, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, golem, null);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A posted sentry with only its HoldGoal left, walking from START toward OLD_POST. */
	private static IronGolem walkingSentry(GameTestHelper helper, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(START), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "setup: no sentry was built");
		Leftovers.after(helper, golem::discard);
		((MobAccessor) golem).aliveworkplace$goals().removeAllGoals(g -> !(g instanceof WallSentries.HoldGoal));
		((MobAccessor) golem).aliveworkplace$targets().removeAllGoals(g -> true);
		give(player, golem, map(level, List.of(helper.absolutePos(OLD_POST))));
		helper.assertTrue(helper.absolutePos(OLD_POST).equals(WallSentries.post(golem)), "setup: not posted: " + WallSentries.post(golem));
		return golem;
	}

	/**
	 * The post is taken with a blank map while the sentry is halfway there: it stops, and in the 80 ticks after it comes
	 * no nearer the old post than a step's drift.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaSentryPostTaken1103d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSentryPostTaken1103d")
	public void aSentryWhosePostIsTakenMidWalkStopsWalkingThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		IronGolem golem = walkingSentry(helper, player);
		Vec3 old = Vec3.atBottomCenterOf(helper.absolutePos(OLD_POST));
		double start = horizontal(golem.position(), old);
		double[] takenAt = {-1};
		long[] takenTick = {-1};
		helper.onEachTick(() -> {
			double d = horizontal(golem.position(), old);
			if (takenAt[0] < 0 && d < start - 8) {
				give(player, golem, map(level, List.of()));
				helper.assertTrue(WallSentries.post(golem) == null, "a blank map left the post: " + WallSentries.post(golem));
				takenAt[0] = d;
				takenTick[0] = helper.getTick();
			}
			if (takenAt[0] >= 0) {
				helper.assertTrue(d >= takenAt[0] - 1.5, "the sentry walked on to the post a blank map took away: " + String.format("%.1f", d)
					+ " blocks from it, " + String.format("%.1f", takenAt[0]) + " when the post was taken " + (helper.getTick() - takenTick[0]) + " ticks ago");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(takenAt[0] >= 0, "setup: the sentry never got 8 blocks nearer its post: " + String.format("%.1f", horizontal(golem.position(), old))
				+ " blocks off, " + String.format("%.1f", start) + " at the start");
			helper.assertTrue(helper.getTick() - takenTick[0] >= 80, "waiting 80 ticks after the post was taken");
		});
	}

	/**
	 * The post is taken mid-walk, then a map with another first point is given: the sentry goes to the new post and stays
	 * there, never on to the old one.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaSentryPostMoved1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSentryPostMoved1103d")
	public void aSentryGivenANewPostMidWalkGoesThereNotToTheOldOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		IronGolem golem = walkingSentry(helper, player);
		Vec3 old = Vec3.atBottomCenterOf(helper.absolutePos(OLD_POST));
		Vec3 now = Vec3.atBottomCenterOf(helper.absolutePos(NEW_POST));
		double start = horizontal(golem.position(), old);
		boolean[] moved = {false};
		int[] atNew = {0};
		helper.onEachTick(() -> {
			if (!moved[0] && horizontal(golem.position(), old) < start - 8) {
				give(player, golem, map(level, List.of()));
				give(player, golem, map(level, List.of(helper.absolutePos(NEW_POST))));
				helper.assertTrue(helper.absolutePos(NEW_POST).equals(WallSentries.post(golem)), "the new map didn't post it: " + WallSentries.post(golem));
				moved[0] = true;
			}
			if (moved[0]) {
				helper.assertTrue(horizontal(golem.position(), old) > 6, "the sentry reached its old post after it was given a new one");
				atNew[0] = horizontal(golem.position(), now) <= 2.0 ? atNew[0] + 1 : 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0], "setup: the sentry never got 8 blocks nearer its first post");
			helper.assertTrue(atNew[0] >= 40, "the sentry isn't holding its new post: " + String.format("%.1f", horizontal(golem.position(), now)) + " blocks off it");
		});
	}
}
