package io.github.jcondedata.aliveworkplace.inn;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

/**
 * An Innkeeper's shift at the Inn Counter: in the morning, a traveller comes to stay while there's a free bed nearby and
 * fewer than {@link Innkeepers#MAX_GUESTS} guests; guests nobody hired move on when their stay is over (out of sight of
 * players).
 */
public class InnkeeperWork extends Behavior<Villager> {
	static final int EVERY = 200;
	/** Travellers arrive in the morning (day time below this). */
	static final long MORNING = 6000;

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private String state = "none";

	public InnkeeperWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Innkeepers.isInnkeeper(villager) && Builders.benchPos(villager).isPresent();
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
		BlockPos counter = Builders.benchPos(villager).orElse(null);
		if (counter == null) {
			return;
		}
		walker.walkTo(level, villager, counter, 2.5);
		if (--timer > 0) {
			return;
		}
		timer = EVERY;
		tend(level, villager, counter);
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.innkeeper.title",
				villager.getAttachedOrElse(ModAttachments.GUESTS_HOSTED, 0)), -1f,
			Component.translatable("message.aliveworkplace.innkeeper.state." + state, Innkeepers.guests(level, counter).size())
				.withStyle(state.equals("no_bed") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}

	/** The inn's round: guests whose stay is over leave, and in the morning a new one may arrive. */
	void tend(ServerLevel level, Villager villager, BlockPos counter) {
		List<Villager> guests = Innkeepers.guests(level, counter);
		for (Villager guest : guests) {
			if (Innkeepers.stayOver(level, guest) && level.getNearestPlayer(guest, 24) == null) {
				Innkeepers.leave(level, guest);
			}
		}
		guests = Innkeepers.guests(level, counter);
		if (guests.size() >= Innkeepers.MAX_GUESTS) {
			state = "full";
			return;
		}
		if (!Innkeepers.hasFreeBed(level, counter)) {
			state = "no_bed";
			return;
		}
		long day = level.getDayTime() / 24000;
		if (level.getDayTime() % 24000 < MORNING && villager.getAttachedOrElse(ModAttachments.LAST_GUEST_DAY, -1L) < day) {
			if (Innkeepers.arrive(level, villager, counter) != null) {
				villager.setAttached(ModAttachments.LAST_GUEST_DAY, day);
				villager.setAttached(ModAttachments.GUESTS_HOSTED, villager.getAttachedOrElse(ModAttachments.GUESTS_HOSTED, 0) + 1);
				BuilderLevels.addXp(level, villager, 3, null);
			}
		}
		state = guests.isEmpty() ? "waiting" : "hosting";
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
