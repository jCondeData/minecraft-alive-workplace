package io.github.jcondedata.aliveworkplace.bard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import org.jetbrains.annotations.Nullable;

/**
 * A bard's set: by the Music Stand, play the music discs from the chests nearby one after another (the
 * discs stay in the chest), or, with no discs, make up a little tune on the harp.
 */
public class BardWork extends Behavior<Villager> {
	private static final float SPEED = 0.5f;
	/** A pentatonic scale (note-block pitches): whatever the bard picks sounds fine. */
	private static final float[] SCALE = {0.5f, 0.561f, 0.63f, 0.749f, 0.841f, 1.0f, 1.122f, 1.26f, 1.498f, 1.682f, 2.0f};
	private static final int NOTE_EVERY = 5;
	private static final int PHRASE = 16;
	/** Who's playing what right now (tests and the status line). */
	private static final Map<Villager, Holder<JukeboxSong>> PLAYING = Collections.synchronizedMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Holder<JukeboxSong> song;
	private int songTicks;
	private int nextDisc;
	private int note;
	private int step;
	private int rest;

	public BardWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static Optional<Holder<JukeboxSong>> playing(Villager villager) {
		return Optional.ofNullable(PLAYING.get(villager));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		note = SCALE.length / 2;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		stopSong(level, villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos stand = Builders.benchPos(villager).orElse(null);
		if (stand == null) {
			return;
		}
		if (!walker.walkTo(level, villager, stand, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stand.above()));
		if (song != null) {
			if (++songTicks < song.value().lengthInTicks() + 60) {
				if (songTicks % 20 == 0) {
					level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getY() + 2.2, villager.getZ(), 1, 0.3, 0.1, 0.3, 1);
				}
				return;
			}
			stopSong(level, villager);
		}
		Holder<JukeboxSong> next = pickDisc(level, stand);
		if (next != null) {
			song = next;
			songTicks = 0;
			PLAYING.put(villager, next);
			int id = level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(next.value());
			level.levelEvent(null, 1010, stand, id);
			villager.swing(InteractionHand.MAIN_HAND);
			WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.bard"), -1f,
				Component.translatable("message.aliveworkplace.bard.playing", next.value().description()).withStyle(ChatFormatting.GRAY));
			return;
		}
		tune(level, villager);
	}

	/** The next music disc from the chests near the stand (going round them in order). */
	@Nullable
	private Holder<JukeboxSong> pickDisc(ServerLevel level, BlockPos stand) {
		List<ItemStack> discs = new java.util.ArrayList<>();
		for (BlockPos chest : SupplyContainers.find(level, stand, null)) {
			discs.addAll(SupplyContainers.peekMatching(level, chest, s -> JukeboxSong.fromStack(level.registryAccess(), s).isPresent()));
		}
		if (discs.isEmpty()) {
			return null;
		}
		ItemStack disc = discs.get(nextDisc++ % discs.size());
		return JukeboxSong.fromStack(level.registryAccess(), disc).orElse(null);
	}

	/** No discs: a made-up tune on the harp, a phrase at a time, with a bass note on the beat. */
	private void tune(ServerLevel level, Villager villager) {
		WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.bard"), -1f,
			Component.translatable("message.aliveworkplace.bard.tune").withStyle(ChatFormatting.GRAY));
		if (rest > 0) {
			rest--;
			return;
		}
		if (++step % NOTE_EVERY != 0) {
			return;
		}
		int beat = step / NOTE_EVERY;
		if (beat % PHRASE == 0 && beat > 0) {
			rest = 30 + level.random.nextInt(40);
		}
		note = Math.max(0, Math.min(SCALE.length - 1, note + level.random.nextInt(5) - 2));
		play(level, villager, SoundEvents.NOTE_BLOCK_HARP.value(), SCALE[note], 0.9f);
		if (beat % 4 == 0) {
			play(level, villager, SoundEvents.NOTE_BLOCK_BASS.value(), SCALE[Math.max(0, note - 5)], 0.7f);
			villager.swing(InteractionHand.MAIN_HAND);
		}
		level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getY() + 2.2, villager.getZ(), 0, note / 24.0, 0, 0, 1);
	}

	private static void play(ServerLevel level, Villager villager, SoundEvent sound, float pitch, float volume) {
		level.playSound(null, villager.getX(), villager.getY() + 1, villager.getZ(), sound, SoundSource.RECORDS, volume, pitch);
	}

	private void stopSong(ServerLevel level, Villager villager) {
		if (song != null) {
			Builders.benchPos(villager).ifPresent(stand -> level.levelEvent(1011, stand, 0));
			song = null;
		}
		PLAYING.remove(villager);
	}
}
