package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.EnumSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Wall Sentries (ROADMAP 29.15): a Golem Smith's golem with the role {@code sentry}. Sneak-right-click it with a Patrol
 * Map and it goes to the map's first point ({@link ModAttachments#GOLEM_POST}) and never leaves it: it fights what comes
 * within {@link #HOLD_RANGE} blocks of the post, never steps more than {@link #LEASH} blocks off it, and its blows throw
 * attackers {@link #KNOCKBACK} back off the wall. It has twice a golem's health ({@link GolemSmith#SENTRY_HEALTH}). A
 * blank map takes the post away. In a protected village only its people may post a sentry.
 */
public final class WallSentries {
	/** How far from its post a sentry may step while fighting. */
	public static final double LEASH = 3.0;
	/** Foes closer than this to the post are fought; farther ones are left alone. */
	public static final double HOLD_RANGE = 6.0;
	/** How hard a sentry's blow throws its foe back (a guard's shove is 0.5). */
	public static final double KNOCKBACK = 1.6;
	private static final double SPEED = 1.0;

	/** The post, or null for a sentry that has none yet (or isn't a sentry). */
	@Nullable
	public static BlockPos post(IronGolem golem) {
		return GolemSmith.role(golem) == GolemSmith.Role.SENTRY ? ModAttachments.GOLEM_POST.get(golem) : null;
	}

	/** Hands {@code golem} the map's first point as its post (or, with a blank map, takes its post away). */
	public static InteractionResult giveMap(ServerPlayer player, IronGolem golem, ItemStack map) {
		if (!VillageProtection.mayChange(player.level(), player, golem.blockPosition())) {
			return InteractionResult.FAIL;
		}
		PatrolMapItem.Route route = PatrolMapItem.route(map);
		if (route.points().isEmpty()) {
			ModAttachments.GOLEM_POST.remove(golem);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.sentry.taken", golem.getDisplayName()));
			return InteractionResult.SUCCESS;
		}
		if (!route.dimension().map(Ids.of(player.level().dimension())::equals).orElse(false)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.patrol.elsewhere").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.FAIL;
		}
		BlockPos post = route.points().get(0);
		ModAttachments.GOLEM_POST.set(golem, post);
		golem.setTarget(null);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.sentry.posted", golem.getDisplayName(), post.getX(), post.getY(), post.getZ())
			.withStyle(ChatFormatting.GREEN));
		golem.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.8f);
		return InteractionResult.SUCCESS;
	}

	/** A sentry's blow landed: the foe flies back, away from the post. */
	public static void struck(IronGolem golem, Entity target) {
		if (post(golem) != null && target instanceof LivingEntity foe) {
			foe.knockback(KNOCKBACK, golem.getX() - foe.getX(), golem.getZ() - foe.getZ());
			foe.hurtMarked = true;
		}
	}

	/** Whether {@code foe} is near enough the post to be fought. */
	static boolean inRange(BlockPos post, @Nullable LivingEntity foe) {
		return foe != null && foe.isAlive() && foe.position().distanceToSqr(post.getCenter()) <= HOLD_RANGE * HOLD_RANGE;
	}

	/**
	 * The sentry's post, as its first goal: it walks back whenever it is more than {@link #LEASH} blocks off it, or more
	 * than a step off it with no foe in range, and nothing else moves it meanwhile (the golem's attack included).
	 */
	public static class HoldGoal extends Goal {
		private final IronGolem golem;

		public HoldGoal(IronGolem golem) {
			this.golem = golem;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!(golem.level() instanceof ServerLevel) || golem.isPassenger()) {
				return false;
			}
			BlockPos post = post(golem);
			if (post == null) {
				return false;
			}
			if (golem.getTarget() != null && !inRange(post, golem.getTarget())) {
				golem.setTarget(null); // a foe beyond the wall: let it go
			}
			double away = golem.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(post));
			return away > LEASH * LEASH || away > 1.5 * 1.5 && !inRange(post, golem.getTarget());
		}

		@Override
		public boolean canContinueToUse() {
			BlockPos post = post(golem);
			if (post == null) {
				return false;
			}
			double away = golem.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(post));
			// Back at the post, or near enough with a foe to fight: the attack may have the legs again.
			return away > 1.0 && (away > (LEASH - 1) * (LEASH - 1) || !inRange(post, golem.getTarget()));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BlockPos post = post(golem);
			if (post != null && (golem.getNavigation().isDone() || !post.equals(golem.getNavigation().getTargetPos()))) {
				golem.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, SPEED);
			}
			if (golem.getTarget() != null && !inRange(post, golem.getTarget())) {
				golem.setTarget(null); // a foe beyond the wall: let it go
			}
		}

		@Override
		public void stop() {
			golem.getNavigation().stop();
		}
	}

	private WallSentries() {
	}
}
