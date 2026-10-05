package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * The Bard Laureate (ROADMAP 29.19), a Rare Bard Legend {@code legends/bard_laureate.json}: a guest at a festival 30
 * villagers come to (29.8), or born to a Bard (29.7). Likes books. Their powers:
 * <ul>
 *   <li>{@code anthem}: when they settle, the village's anthem is composed ({@code hall.Anthems});</li>
 *   <li>{@code work_songs}: twice a day (mid-morning and mid-afternoon) they walk to the busiest spot (the worker with the
 *   most workers within {@link WorkSongsPower#radius}) and sing for {@link WorkSongsPower#seconds} seconds with notes
 *   rising round them. Workers within the radius work {@link WorkSongsPower#factor} times as fast while they sing (the
 *   shared Legend pace and its cap), and everyone who hears is {@link WorkSongsPower#mood} happier for the day.</li>
 * </ul>
 * The song is saved on the Laureate ({@link ModAttachments#WORK_SONG}); who heard it on each hearer ({@link ModAttachments#SONG_HEARD}).
 */
public final class BardLaureate {
	public static final ResourceLocation ID = AliveWorkplace.id("bard_laureate");
	/** The day times the two songs begin from: mid-morning and mid-afternoon (each may start within {@link #WINDOW}). */
	public static final long[] SONG_TIMES = {3000, 8000};
	public static final long WINDOW = 1500;

	/** {@code anthem}: the village's anthem, composed from its name when they settle. */
	public record AnthemPower() implements Power {
		static AnthemPower read(JsonObject json) {
			return new AnthemPower();
		}

		@Override
		public String type() {
			return "anthem";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.anthem");
		}
	}

	/** {@code work_songs}: twice a day a song of {@code seconds}; workers within {@code radius} {@code factor}× as fast, hearers +{@code mood}. */
	public record WorkSongsPower(int radius, float factor, int seconds, int mood) implements Power {
		static WorkSongsPower read(JsonObject json) {
			int radius = json.has("radius") ? json.get("radius").getAsInt() : 16;
			float factor = json.has("factor") ? json.get("factor").getAsFloat() : 1.25f;
			int seconds = json.has("seconds") ? json.get("seconds").getAsInt() : 120;
			int mood = json.has("mood") ? json.get("mood").getAsInt() : 5;
			if (radius < 1 || seconds < 1 || factor < 1f) {
				throw new IllegalArgumentException("'radius' or 'seconds' below 1, or 'factor' below 1");
			}
			return new WorkSongsPower(radius, factor, seconds, mood);
		}

		public static final WorkSongsPower DEFAULT = new WorkSongsPower(16, 1.25f, 120, 5);

		@Override
		public String type() {
			return "work_songs";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.work_songs", seconds / 60, radius, LegendText.number(factor), mood);
		}
	}

	/** A work song: the day and which of the day's two ({@code slot}), the game time it ends, where it's sung. */
	public record Song(long day, int slot, long until, BlockPos spot) {
		public static final Codec<Song> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Song::day),
			Codec.INT.optionalFieldOf("slot", 0).forGetter(Song::slot),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(Song::until),
			BlockPos.CODEC.optionalFieldOf("spot", BlockPos.ZERO).forGetter(Song::spot)
		).apply(i, Song::new));
	}

	static void register() {
		Powers.register("anthem", AnthemPower::read);
		Powers.register("work_songs", WorkSongsPower::read);
	}

	private static boolean inVillage(ServerLevel level, LegendPowers.Active a, BlockPos hall) {
		return a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false));
	}

	/** The settled Legend with {@code anthem} in the village round {@code hall}, if any. */
	public static Optional<Villager> composer(ServerLevel level, BlockPos hall) {
		return LegendPowers.settled(level).stream()
			.filter(a -> !a.legend().powers(AnthemPower.class).isEmpty() && a.villager().isAlive() && inVillage(level, a, hall))
			.map(LegendPowers.Active::villager).findFirst();
	}

	// Work songs.

	private static boolean worker(Villager v) {
		VillagerProfession p = v.getVillagerData().getProfession();
		return v.isAlive() && !v.isBaby() && p != VillagerProfession.NONE && p != VillagerProfession.NITWIT;
	}

	/** The busiest spot round {@code singer}'s village: where the worker with the most workers within {@code radius} stands. */
	public static Optional<BlockPos> busiest(ServerLevel level, Villager singer, int radius) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, singer.blockPosition());
		if (hall.isEmpty()) {
			return Optional.empty();
		}
		List<Villager> workers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall.get()), v -> v != singer && worker(v));
		BlockPos best = null;
		int most = -1;
		double r = (double) radius * radius;
		for (Villager w : workers) {
			int near = 0;
			for (Villager o : workers) {
				if (o.distanceToSqr(w) <= r) {
					near++;
				}
			}
			if (near > most) {
				most = near;
				best = w.blockPosition();
			}
		}
		return Optional.ofNullable(best);
	}

	/** {@code singer} begins a work song now (slot {@code slot} of today) at the busiest spot. Returns the song. */
	public static Song sing(ServerLevel level, Villager singer, WorkSongsPower power, int slot) {
		BlockPos spot = busiest(level, singer, power.radius()).orElse(singer.blockPosition());
		Song song = new Song(Chronicle.day(level), slot, level.getGameTime() + power.seconds() * 20L, spot);
		ModAttachments.WORK_SONG.set(singer, song);
		singer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.55f, 2));
		LegendPowers.forget();
		return song;
	}

	/** Whether {@code villager} is singing a work song now. */
	public static boolean singing(Villager villager) {
		Song song = ModAttachments.WORK_SONG.get(villager);
		return song != null && villager.level().getGameTime() < song.until();
	}

	/** The pace a singing Laureate gives {@code worker} (1 when none sings near): the factor, within the radius. */
	static float songPace(List<LegendPowers.Active> legends, Villager worker) {
		float factor = 1f;
		for (LegendPowers.Active a : legends) {
			for (WorkSongsPower p : a.legend().powers(WorkSongsPower.class)) {
				if (a.villager() != worker && singing(a.villager()) && a.villager().distanceToSqr(worker) <= (double) p.radius() * p.radius()) {
					factor *= p.factor();
				}
			}
		}
		return factor;
	}

	/** The mood of having heard a work song today. */
	public static LegendPowers.MoodReason heardMood(ServerLevel level, Villager villager) {
		Long day = ModAttachments.SONG_HEARD.get(villager);
		if (day == null || day != Chronicle.day(level)) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.work_song"), WorkSongsPower.DEFAULT.mood());
	}

	/**
	 * Every second of a Laureate's life (from {@link Legends#tick}): in the song windows a settled Laureate begins the
	 * day's song once; while singing, notes rise round them and everyone within the radius hears it.
	 */
	public static void tick(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !(villager.level() instanceof ServerLevel level) || !data.settled() || data.onStrike()) {
			return;
		}
		Optional<Legend> legend = Legends.get(data.id());
		if (legend.isEmpty() || legend.get().powers(WorkSongsPower.class).isEmpty()) {
			return;
		}
		WorkSongsPower power = legend.get().powers(WorkSongsPower.class).get(0);
		long time = level.getDayTime() % VillageNeeds.DAY;
		long today = Chronicle.day(level);
		Song song = ModAttachments.WORK_SONG.get(villager);
		if (!singing(villager) && !villager.isSleeping()) {
			for (int slot = 0; slot < SONG_TIMES.length; slot++) {
				boolean sung = song != null && song.day() == today && song.slot() >= slot;
				if (!sung && time >= SONG_TIMES[slot] && time < SONG_TIMES[slot] + WINDOW) {
					song = sing(level, villager, power, slot);
					break;
				}
			}
		}
		if (singing(villager)) {
			hear(level, villager, power);
			if (villager.blockPosition().distSqr(song.spot()) > 9) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(song.spot(), 0.55f, 2));
			}
		}
	}

	/** One second of singing: notes round the singer, a note sound, and everyone within the radius heard it today. */
	public static void hear(ServerLevel level, Villager singer, WorkSongsPower power) {
		for (int i = 0; i < 3; i++) {
			level.sendParticles(ParticleTypes.NOTE, singer.getX() + (level.random.nextDouble() - 0.5) * 1.6, singer.getY() + 2.1 + level.random.nextDouble() * 0.6,
				singer.getZ() + (level.random.nextDouble() - 0.5) * 1.6, 0, level.random.nextDouble(), 0, 0, 1);
		}
		level.playSound(null, singer.blockPosition(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.NEUTRAL, 0.8f,
			io.github.jcondedata.aliveworkplace.hall.Anthems.pitch(6 + level.random.nextInt(12)));
		long today = Chronicle.day(level);
		for (Villager v : level.getEntitiesOfClass(Villager.class, singer.getBoundingBox().inflate(power.radius()), Villager::isAlive)) {
			if (v.distanceToSqr(singer) <= (double) power.radius() * power.radius()) {
				Long day = ModAttachments.SONG_HEARD.get(v);
				if (day == null || day != today) {
					ModAttachments.SONG_HEARD.set(v, today);
					io.github.jcondedata.aliveworkplace.people.Moods.forget(v);
				}
			}
		}
	}

	private BardLaureate() {
	}
}
