package io.github.jcondedata.aliveworkplace.build;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A builder with no building to work on lays the path they planned when they finished the last one (see {@link Paths}):
 * along it, ground block by ground block, into dirt path — the grass and flowers on it cleared away.
 */
public class PathWork extends Behavior<Villager> {
	private static final double REACH = 3.5;
	private static final int EVERY = 6;

	private final Walker walker = new Walker(0.55f);
	private int timer;

	public PathWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	static boolean hasPath(Villager villager) {
		return !villager.getAttachedOrElse(ModAttachments.PATH, List.of()).isEmpty();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.isBuilder(villager) && !villager.hasAttached(ModAttachments.BUILDER_JOB) && hasPath(villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		List<BlockPos> path = villager.getAttachedOrElse(ModAttachments.PATH, List.of());
		if (path.isEmpty()) {
			return;
		}
		BlockPos next = path.get(0);
		BlockState ground = level.getBlockState(next);
		if (!Paths.convertible(ground)) {
			pop(villager, path); // someone built there, or it's already path
			return;
		}
		if (!walker.reach(level, villager, next, REACH) && !walker.noSpot()) {
			return;
		}
		if (--timer > 0) {
			return;
		}
		timer = BuilderLevels.delay(EVERY, villager);
		BlockState above = level.getBlockState(next.above());
		if (!above.isAir() && above.canBeReplaced()) {
			level.destroyBlock(next.above(), false);
		}
		level.setBlockAndUpdate(next, Blocks.DIRT_PATH.defaultBlockState());
		level.playSound(null, next, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 0.8f, 1f);
		villager.swing(InteractionHand.MAIN_HAND);
		walker.reset();
		pop(villager, path);
	}

	private static void pop(Villager villager, List<BlockPos> path) {
		List<BlockPos> rest = new ArrayList<>(path.subList(1, path.size()));
		if (rest.isEmpty()) {
			villager.removeAttached(ModAttachments.PATH);
		} else {
			villager.setAttached(ModAttachments.PATH, List.copyOf(rest));
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
