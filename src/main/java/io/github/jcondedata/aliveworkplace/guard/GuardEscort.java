package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/** A guard following the player whose Rally Banner is raised (see {@link Escorts}); in CORE, any time of day. */
public class GuardEscort extends Behavior<Villager> {
	private static final float SPEED = 0.7f;
	private static final float HURRY = 0.9f;

	public GuardEscort() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return lagging(villager, 3.5);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return lagging(villager, 2.5);
	}

	private static boolean lagging(Villager villager, double distance) {
		if (GuardCombat.isFighting(villager)) {
			return false;
		}
		return Escorts.leader(villager).map(player -> villager.distanceToSqr(player) > distance * distance).orElse(false);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		ServerPlayer player = Escorts.leader(villager).orElse(null);
		if (player == null) {
			return;
		}
		double distance = villager.distanceTo(player);
		if (distance > Escorts.CATCH_UP) {
			Escorts.moveNear(level, villager, player.blockPosition());
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), distance > 10 ? HURRY : SPEED, 2));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		status(villager, player);
	}

	static void status(Villager villager, ServerPlayer player) {
		WorkerStatus.set(villager, GuardCombat.title(villager), -1f,
			Component.translatable("message.aliveworkplace.guard.state.following", player.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
