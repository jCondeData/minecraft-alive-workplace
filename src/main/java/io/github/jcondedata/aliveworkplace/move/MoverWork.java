package io.github.jcondedata.aliveworkplace.move;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * The Mover's work (ROADMAP 45.3), in their WORK activity: the move waiting in their village ({@link MoveSites#work}),
 * one block a {@link #STEP_TICKS} ticks, taken down top-down at the old spot and put up again at the new one. They walk
 * to the side of the box being worked on and swing from within {@link #REACH} blocks of its middle. Two Movers of a
 * village work the same site, each with their own swings, so the move goes twice as fast. Nothing is kept on the Mover:
 * everything is on the site, so a Mover replaced, killed or reloaded mid-move leaves nothing behind.
 */
public class MoverWork extends Behavior<Villager> {
	/** Ticks between a Mover's steps. */
	public static final int STEP_TICKS = 4;
	/** How near the middle of the box worked on a Mover must stand to work. */
	public static final double REACH = 24;
	private static final float SPEED = 0.6f;

	private static final class State {
		final Walker walker = new Walker(SPEED);
		long nextStep;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public MoverWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new MoverWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	/** The village a Mover works for: the hall round their workstation (or round them, between workstations). */
	public static Optional<BlockPos> hall(ServerLevel level, Villager villager) {
		BlockPos at = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension())
			.map(GlobalPos::pos).orElse(villager.blockPosition());
		return VillageHalls.containing(level, at);
	}

	/** The site this Mover works on now, if any (started from a waiting move if none has). */
	public static Optional<MoveSite> site(ServerLevel level, Villager villager) {
		if (!Movers.isMover.test(villager) || villager.isBaby()) {
			return Optional.empty();
		}
		return hall(level, villager).flatMap(hall -> MoveSites.get(level).work(level, hall));
	}

	/** The middle of the box the site works on now: the old spot while it comes down (or back up), else the new one. */
	public static BlockPos workingAt(MoveSite site) {
		boolean old = site.stage() == MoveSite.Stage.DISMANTLE || site.stage() == MoveSite.Stage.RESTORE_UP;
		BlockPos min = old ? site.oldMin() : site.newMin();
		var size = site.plan().size(old ? net.minecraft.world.level.block.Rotation.NONE : site.rotation());
		return min.offset(size.getX() / 2, 0, size.getZ() / 2);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return site(level, villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
		return site(level, villager).isPresent();
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long time) {
		Optional<MoveSite> site = site(level, villager);
		if (site.isEmpty()) {
			return;
		}
		BlockPos middle = workingAt(site.get());
		State state = state(villager);
		if (villager.position().distanceTo(Vec3.atBottomCenterOf(middle)) > REACH - 4) {
			state.walker.walkTo(level, villager, middle, REACH - 4);
			return;
		}
		if (time >= state.nextStep) {
			state.nextStep = time + STEP_TICKS;
			work(level, villager, site.get());
		}
	}

	/** One swing of the Mover on {@code site}: one block taken down or put up. Whether anything was done. */
	public static boolean work(ServerLevel level, Villager villager, MoveSite site) {
		Optional<BlockPos> pos = MoveSites.get(level).step(level, site);
		status(villager, site);
		pos.ifPresent(p -> {
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(p));
			villager.swing(InteractionHand.MAIN_HAND);
		});
		return pos.isPresent();
	}

	/** The line over the Mover's head: what he's doing and how far along (WorkerStatus). */
	public static void status(Villager villager, MoveSite site) {
		int total = Math.max(1, site.total());
		int packed = site.packed();
		net.minecraft.network.chat.Component line;
		float progress;
		if (site.canceled()) {
			line = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.status.back");
			progress = 1f - (float) packed / total;
		} else if (site.stage() == MoveSite.Stage.DISMANTLE) {
			line = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.status.down", packed, site.total());
			progress = 0.5f * packed / total;
		} else {
			int up = site.total() - packed - site.skipped();
			line = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.status.up", up, site.total() - site.skipped());
			progress = 0.5f + 0.5f * up / Math.max(1, site.total() - site.skipped());
		}
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.set(villager, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.status"),
			progress, line);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long time) {
		state(villager).walker.reset();
	}
}
