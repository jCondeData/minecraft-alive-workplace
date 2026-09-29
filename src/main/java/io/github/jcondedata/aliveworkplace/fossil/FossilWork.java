package io.github.jcondedata.aliveworkplace.fossil;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Fossil Scientist's shift: at the Fossil Lab, work on the fossils handed over, one at a time; hand each finished one
 * to its owner as soon as they're online (they get it the next time they are, otherwise).
 */
public class FossilWork extends Behavior<Villager> {
	private static final float SPEED = 0.5f;
	/** Progress is saved this often (and the owners of finished ones looked for). */
	private static final int SAVE_EVERY = 20;

	private final Walker walker = new Walker(SPEED);
	private int saveTimer;
	private int worked;

	public FossilWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return FossilScientists.COBBLEMON && !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		worked = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		save(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos lab = Builders.benchPos(villager).orElse(null);
		if (lab == null) {
			return;
		}
		List<Revival> queue = FossilScientists.queue(villager);
		Revival current = queue.stream().filter(r -> !r.ready()).findFirst().orElse(null);
		if (--saveTimer <= 0) {
			saveTimer = SAVE_EVERY;
			save(villager);
			handOverFinished(level, villager);
			queue = FossilScientists.queue(villager);
			current = queue.stream().filter(r -> !r.ready()).findFirst().orElse(null);
		}
		if (current == null) {
			status(villager, null, queue.isEmpty() ? "waiting" : "done");
			return;
		}
		status(villager, current, "reviving");
		if (!walker.walkTo(level, villager, lab, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lab));
		worked++;
		if (worked % 20 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.sendParticles(ParticleTypes.GLOW, lab.getX() + 0.5, lab.getY() + 1.2, lab.getZ() + 0.5, 3, 0.25, 0.15, 0.25, 0.01);
			level.playSound(null, lab, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.3f, 1.4f);
		}
	}

	/** Writes the work done since the last save into the first unfinished revival. */
	private void save(Villager villager) {
		if (worked <= 0) {
			return;
		}
		List<Revival> queue = new ArrayList<>(FossilScientists.queue(villager));
		for (int i = 0; i < queue.size(); i++) {
			if (!queue.get(i).ready()) {
				queue.set(i, queue.get(i).worked(worked));
				break;
			}
		}
		worked = 0;
		FossilScientists.setQueue(villager, queue);
	}

	/** Finished fossils go to their owners who are online, wherever they are. */
	private static void handOverFinished(ServerLevel level, Villager villager) {
		for (Revival r : FossilScientists.queue(villager)) {
			if (r.ready()) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(r.owner());
				if (owner != null) {
					FossilScientists.deliver(level, villager, owner);
				}
			}
		}
	}

	private static void status(Villager villager, Revival current, String state) {
		Component title = Component.translatable("message.aliveworkplace.fossil.title", villager.getAttachedOrElse(ModAttachments.FOSSILS_REVIVED, 0));
		Component line = current != null
			? Component.translatable("message.aliveworkplace.fossil.state.reviving", FossilScientists.what(current.items()), current.ownerName())
				.withStyle(ChatFormatting.GRAY)
			: Component.translatable("message.aliveworkplace.fossil.state." + state).withStyle(ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, current != null ? current.progress() : -1f, line);
	}
}
