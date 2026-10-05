package io.github.jcondedata.aliveworkplace.daycare;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Daycare Keeper's shift (ROADMAP 28.12): she keeps to her pasture, looks in on the pairs (the eggs of the dawns
 * passed, {@link DaycareKeepers#dawn}) and keeps them company. Everything is in her saved pairs, so she can restart at
 * any tick.
 */
public class DaycareKeeperWork extends Behavior<Villager> {
	private static final int LOOK_EVERY = 100;
	/** How often a partner keeps the pairs company (a show). */
	private static final int COMPANY_EVERY = 1200;
	private static final float SPEED = 0.5f;
	private static final double REACH = 3.0;

	private final Walker walker = new Walker(SPEED);
	private int lookTimer;
	private long nextCompany;

	public DaycareKeeperWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Daycare Keeper's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new DaycareKeeperWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && DaycareKeepers.isKeeper(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos pasture = Builders.benchPos(villager).orElse(null);
		if (pasture == null) {
			return;
		}
		if (!DaycareKeepers.ENABLED) {
			status(villager, "off");
			return;
		}
		if (!walker.reach(level, villager, pasture, REACH)) {
			if (walker.noSpot()) {
				walker.reset();
			}
			return;
		}
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		DaycareKeepers.dawn(level, villager, villager.getRandom());
		var pairs = DaycareKeepers.pairs(villager);
		if (pairs.isEmpty()) {
			status(villager, "empty");
			return;
		}
		int eggs = pairs.stream().mapToInt(DaycareKeepers.Pair::eggs).sum();
		status(villager, eggs > 0 ? "eggs" : "watching", Component.literal(String.valueOf(pairs.size())), Component.literal(String.valueOf(eggs)));
		if (gameTime >= nextCompany) {
			nextCompany = gameTime + COMPANY_EVERY;
			PartnerShows.cue(villager, "keep_company", pasture);
		}
	}

	private static void status(Villager villager, String state, Component... args) {
		Component title = Component.translatable("message.aliveworkplace.daycare_keeper.title", DaycareKeepers.pairs(villager).size());
		boolean warn = state.equals("off");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.daycare_keeper.state." + state, (Object[]) args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
