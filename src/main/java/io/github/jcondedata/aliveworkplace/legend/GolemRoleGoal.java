package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.farm.FarmhandGolems;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * The work of a Golem Smith's golem (29.15), as one of the iron golem's own goals (added to every iron golem by
 * {@code IronGolemRolesMixin}; it does nothing for a plain golem). It holds the golem's legs while a Hauler carries
 * goods or a Farmhand tends the fields, so the golem's wandering doesn't pull it off the job; a fight comes first (the
 * golem's attack goal ranks above this one and takes over whenever it has a target). The Wall Sentry's post is held by
 * {@link WallSentries.HoldGoal}, which ranks above the attack.
 */
public class GolemRoleGoal extends Goal {
	/** How often (ticks) the golem decides its next step; walking happens in between. */
	public static final int EVERY = 5;
	private static final double SPEED = 1.0;
	/**
	 * How long (ticks) a golem may walk without getting a block farther before it squeezes through: its path runs
	 * between mobs that stand still and push it back (villagers with no AI, 2 blocks apart, are a golem's width), and
	 * until it arrives nothing pushes it (B329).
	 */
	public static final int STUCK_TICKS = 40;
	/** Where each walking golem last made headway, {x, z, game time}. */
	private static final Map<IronGolem, double[]> HEADWAY = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<IronGolem> SQUEEZING = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
	private final IronGolem golem;

	public GolemRoleGoal(IronGolem golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(golem.level() instanceof ServerLevel) || golem.getTarget() != null || golem.isPassenger()) {
			return false;
		}
		GolemSmith.Role role = GolemSmith.role(golem);
		return role == GolemSmith.Role.HAULER || role == GolemSmith.Role.FARMHAND;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		golem.getNavigation().stop();
		arrived(golem);
	}

	@Override
	public void tick() {
		if ((golem.tickCount + golem.getId()) % EVERY != 0 || !(golem.level() instanceof ServerLevel level)) {
			return;
		}
		switch (GolemSmith.role(golem)) {
			case HAULER -> HaulerGolems.work(level, golem);
			case FARMHAND -> FarmhandGolems.work(level, golem);
			case null, default -> {
			}
		}
	}

	/** Walks {@code golem} toward {@code pos}; true once within {@code reach} blocks of its middle. */
	public static boolean walkTo(IronGolem golem, BlockPos pos, double reach) {
		if (golem.position().distanceToSqr(pos.getCenter()) <= reach * reach) {
			golem.getNavigation().stop();
			arrived(golem);
			return true;
		}
		if (golem.getNavigation().isDone() || !pos.equals(golem.getNavigation().getTargetPos())) {
			golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED);
		}
		headway(golem);
		return false;
	}

	/** Notes the golem's headway on a walk; {@link #STUCK_TICKS} without a block of it and it squeezes through. */
	private static void headway(IronGolem golem) {
		long now = golem.level().getGameTime();
		double[] last = HEADWAY.get(golem);
		if (last == null || Math.abs(golem.getX() - last[0]) + Math.abs(golem.getZ() - last[1]) >= 1.0) {
			HEADWAY.put(golem, new double[]{golem.getX(), golem.getZ(), now});
		} else if (now - (long) last[2] >= STUCK_TICKS) {
			SQUEEZING.add(golem);
		}
	}

	private static void arrived(IronGolem golem) {
		HEADWAY.remove(golem);
		SQUEEZING.remove(golem);
	}

	/** Whether {@code golem} is squeezing past mobs that hold it up on its way: nothing pushes it until it arrives. */
	public static boolean squeezing(IronGolem golem) {
		return SQUEEZING.contains(golem);
	}
}
