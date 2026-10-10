package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

/**
 * Answering the village bell. When it rings, other villagers run home and hide; a guard gets up, heads for
 * the bell and fights anything near it (see {@link GuardCombat}) for a minute and a half. In the guard's CORE
 * activity, with its {@link #listener()} first of all.
 */
public class GuardRally extends Behavior<Villager> {
	/** How long a guard stays at the bell after it rings. */
	public static final int RALLY_TICKS = 1800;
	/** Bells a guard hears: vanilla tells villagers within 32 blocks, so look a bit further for the bell itself. */
	private static final int BELL_SEARCH = 40;
	private static final float SPEED = 0.75f;

	private record Rally(BlockPos bell, long until) {
	}

	private static final Map<Villager, Rally> RALLIES = new WeakHashMap<>();

	public GuardRally() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), RALLY_TICKS);
	}

	/** Where this guard is rallying to, while they are. */
	public static Optional<BlockPos> rallyPoint(Villager villager) {
		Rally rally = RALLIES.get(villager);
		if (rally == null || villager.level().getGameTime() > rally.until()) {
			return Optional.empty();
		}
		return Optional.of(rally.bell());
	}

	public static boolean isRallying(Villager villager) {
		return rallyPoint(villager).isPresent();
	}

	/**
	 * Listens for the bell: takes the "heard the bell" memory (so the guard never goes to hide like everyone
	 * else) and starts a rally. Never runs itself, so it gets a look every tick; goes before vanilla's CORE.
	 */
	public static net.minecraft.world.entity.ai.behavior.BehaviorControl<Villager> listener() {
		return net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder.triggerIf(villager -> {
			if (Guards.isGuard(villager) && villager.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)
				&& villager.level() instanceof ServerLevel level) {
				villager.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
				level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), villager.blockPosition(), BELL_SEARCH, PoiManager.Occupancy.ANY)
					.ifPresent(bell -> {
						RALLIES.put(villager, new Rally(bell, level.getGameTime() + RALLY_TICKS));
						if (villager.isSleeping()) {
							villager.stopSleeping();
						}
					});
			}
			return false;
		});
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Guards.isGuard(villager) && needsToWalk(villager);
	}

	private static boolean needsToWalk(Villager villager) {
		Optional<BlockPos> bell = rallyPoint(villager);
		return bell.isPresent() && !BattleStations.stationed(villager) && !GuardCombat.isFighting(villager) && !Escorts.isEscorting(villager) && !bell.get().closerToCenterThan(villager.position(), 4);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return needsToWalk(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		rallyPoint(villager).ifPresent(bell -> {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, SPEED, 2));
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(bell));
			WorkerStatus.set(villager, GuardCombat.title(villager), -1f,
				Component.translatable("message.aliveworkplace.guard.state.rally").withStyle(ChatFormatting.GOLD));
		});
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
