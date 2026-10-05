package io.github.jcondedata.aliveworkplace.legend;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Gated;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * A Legend on strike (ROADMAP 29.5) pickets: by day their WORK activity is standing by the Village Hall, looking at it,
 * and the rest of their trade's work waits ({@link #work}). A Legend with no workstation (the {@code aliveworkplace:legend}
 * trade) has no WORK activity, so they picket in their IDLE one ({@link #idle}). A villager taken by a strange mood
 * (29.10) stands at the workstation they claimed in the same way, and their trade's work waits too.
 */
public final class Picket extends Behavior<Villager> {
	/** Behaviours at this priority or later (vanilla: the schedule update) always run. */
	private static final int ALWAYS = 99;
	/** How near the hall a picket stands. */
	public static final int NEAR = 4;
	private static final float SPEED = 0.5f;

	private final boolean idle;
	private int retryWait;

	private Picket(boolean idle) {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 1200);
		this.idle = idle;
	}

	/** The WORK package of every trade: the picket first, everything else only while the villager isn't on strike. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> pkg) {
		return wrap(pkg, false);
	}

	/** The IDLE package: the same for a striking Legend with no workstation. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> idle(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> pkg) {
		return wrap(pkg, true);
	}

	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> wrap(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> pkg, boolean idle) {
		if (!pkg.isEmpty() && pkg.get(0).getSecond() instanceof Picket) {
			return pkg; // already wrapped
		}
		Picket picket = new Picket(idle);
		Predicate<Villager> mayRun = v -> !picket.applies(v);
		ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
		out.add(Pair.of(0, picket));
		// A Legend's research tree at a lectern by their home (29.11); it starts only for a settled Legend with a topic chosen.
		out.add(Pair.of(0, new Gated<Villager>(mayRun, new io.github.jcondedata.aliveworkplace.research.TreeWork())));
		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : pkg) {
			out.add(entry.getFirst() >= ALWAYS ? entry : Pair.of(entry.getFirst(), new Gated<Villager>(mayRun, entry.getSecond())));
		}
		return out.build();
	}

	/** Whether {@code villager} pickets in this package: on strike, and (in IDLE) with no workstation; or (in WORK) in a strange mood. */
	boolean applies(Villager villager) {
		return LegendNeeds.striking(villager) && (!idle || villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty())
			|| !idle && StrangeMoods.claiming(villager);
	}

	/** Where {@code villager} stands: the workstation a strange mood claimed, else the hall they picket. */
	@Nullable
	static BlockPos target(ServerLevel level, Villager villager) {
		BlockPos station = StrangeMoods.claiming(villager) ? StrangeMoods.station(villager) : null;
		return station != null ? station : hall(level, villager);
	}

	/** The hall a Legend pickets: their own, else the nearest. */
	@Nullable
	public static BlockPos hall(ServerLevel level, Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data != null && data.hall().isPresent() && level.isLoaded(data.hall().get())
			&& level.getBlockEntity(data.hall().get()) instanceof VillageHallBlockEntity) {
			return data.hall().get();
		}
		return VillageHalls.nearest(level, villager.blockPosition()).orElse(null);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return applies(villager) && level.isDay() && !villager.isSleeping() && target(level, villager) != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		retryWait = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if ((gameTime + villager.getId()) % 10 != 0) {
			return;
		}
		BlockPos station = StrangeMoods.claiming(villager) ? StrangeMoods.station(villager) : null;
		if (station != null) {
			// a strange mood: at the claimed workstation, looking at it
			if (villager.blockPosition().closerThan(station, 2.5)) {
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
				return;
			}
			retryWait = Walker.requestWalk(villager, station, SPEED, 1, retryWait);
			return;
		}
		BlockPos hall = hall(level, villager);
		if (hall == null) {
			return;
		}
		if (villager.blockPosition().closerThan(hall, NEAR * 1.5 + 1)) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(hall.above()));
			return;
		}
		BlockPos spot = VillageHalls.besideHall(level, hall, Math.floorMod(villager.getId(), 8));
		retryWait = Walker.requestWalk(villager, spot != null ? spot : hall, SPEED, 1, retryWait);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
