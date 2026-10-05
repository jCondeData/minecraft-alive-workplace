package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.legend.BardLaureate;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * The Bard Laureate's anthem (29.19): when a Legend with the {@code anthem} power settles, the village's anthem is
 * composed from the village's name ({@link #compose}: 16 notes on one instrument, the same name always giving the same
 * tune), kept on the hall and never composed again. It is played with note-block sounds over the hall at every
 * festival, rank-up, wedding and Legend arrival, and from the hall's Anthem page; the hall's owner gets it as a written
 * book, "The Anthem of Thornholm", with the notes written out.
 */
public final class Anthems {
	/** The instruments an anthem may be for, as note blocks name them. */
	public static final List<String> INSTRUMENTS = List.of("harp", "flute", "bell", "chime", "guitar", "xylophone");
	public static final int NOTES = 16;
	/** Ticks between two notes. */
	public static final int BEAT = 5;
	/** The anthem's button: top right on the hall's Legends page. */
	public static final int PLAY = 8;
	/** A major pentatonic scale over two octaves, as note-block notes (0 to 24). */
	private static final int[] SCALE = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};
	private static final String[] NAMES = {"F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"};

	/** An anthem: the instrument and its 16 notes (note-block notes, 0 to 24). */
	public record Anthem(String instrument, int[] notes) {
		public boolean sameAs(Anthem other) {
			return other != null && instrument.equals(other.instrument) && java.util.Arrays.equals(notes, other.notes);
		}

		/** "harp: G A B D …", how the book and the page write it. */
		public String written() {
			StringBuilder out = new StringBuilder();
			for (int n : notes) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(noteName(n));
			}
			return out.toString();
		}
	}

	/** A note's name as a note block's: F# at 0, rising, with its octave (1 to 3). */
	public static String noteName(int note) {
		return NAMES[note % 12] + (note / 12 + 1);
	}

	/** The anthem of a village called {@code name}: the same name (case and spaces aside) always gives the same. */
	public static Anthem compose(String name) {
		String key = name.trim().toLowerCase(java.util.Locale.ROOT);
		long seed = 0xcbf29ce484222325L;
		for (int i = 0; i < key.length(); i++) {
			seed ^= key.charAt(i);
			seed *= 0x100000001b3L;
		}
		SplittableRandom random = new SplittableRandom(seed);
		String instrument = INSTRUMENTS.get(random.nextInt(INSTRUMENTS.size()));
		int[] notes = new int[NOTES];
		int step = 3 + random.nextInt(4);
		for (int i = 0; i < NOTES; i++) {
			if (i == NOTES - 1) {
				step = step >= 5 ? 5 : 0; // home, on the key note
			} else if (i > 0) {
				int move = random.nextInt(5) - 2;
				if (i % 4 == 3) {
					move = random.nextBoolean() ? 0 : move; // the end of a phrase lingers
				}
				step = Math.max(0, Math.min(SCALE.length - 1, step + move));
			}
			notes[i] = SCALE[step];
		}
		return new Anthem(instrument, notes);
	}

	public static SoundEvent sound(String instrument) {
		return switch (instrument) {
			case "flute" -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			case "bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
			case "chime" -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case "guitar" -> SoundEvents.NOTE_BLOCK_GUITAR.value();
			case "xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
			default -> SoundEvents.NOTE_BLOCK_HARP.value();
		};
	}

	public static float pitch(int note) {
		return (float) Math.pow(2.0, (note - 12) / 12.0);
	}

	// Playing.

	/** An anthem being played over a hall: the next note. */
	private static final class Playing {
		final BlockPos hall;
		final Anthem anthem;
		int next;

		Playing(BlockPos hall, Anthem anthem) {
			this.hall = hall;
			this.anthem = anthem;
		}
	}

	private static final Map<ResourceKey<Level>, List<Playing>> PLAYING = new HashMap<>();
	/** Notes played over each hall since the last {@link #forget} (tests count them). */
	private static final Map<BlockPos, Integer> NOTES_PLAYED = new HashMap<>();
	/** Why each anthem was started, over each hall, since the last {@link #forget}. */
	private static final Map<BlockPos, List<String>> OCCASIONS = new HashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % BEAT == 0) {
				tick(level);
			}
		});
	}

	/** Plays one note of every anthem playing in {@code level}. */
	public static void tick(ServerLevel level) {
		List<Playing> list = PLAYING.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return;
		}
		for (Playing p : List.copyOf(list)) {
			int note = p.anthem.notes()[p.next];
			double x = p.hall.getX() + 0.5;
			double y = p.hall.getY() + 1.6;
			double z = p.hall.getZ() + 0.5;
			level.playSound(null, x, y, z, sound(p.anthem.instrument()), SoundSource.RECORDS, 3f, pitch(note));
			level.sendParticles(ParticleTypes.NOTE, x + (level.random.nextDouble() - 0.5), y + 0.4, z + (level.random.nextDouble() - 0.5), 0,
				note / 24.0, 0, 0, 1);
			NOTES_PLAYED.merge(p.hall, 1, Integer::sum);
			if (++p.next >= p.anthem.notes().length) {
				list.remove(p);
			}
		}
	}

	/**
	 * Plays the village's anthem over its hall for {@code occasion} ({@code festival}, {@code rank}, {@code wedding},
	 * {@code legend}, {@code hall}), if it has one and it isn't playing already. Returns whether it began.
	 */
	public static boolean play(ServerLevel level, BlockPos hall, String occasion) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		Optional<Anthem> anthem = entity.anthem();
		if (anthem.isEmpty()) {
			return false;
		}
		List<Playing> list = PLAYING.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
		if (list.stream().anyMatch(p -> p.hall.equals(hall))) {
			return false;
		}
		list.add(new Playing(hall.immutable(), anthem.get()));
		OCCASIONS.computeIfAbsent(hall.immutable(), k -> new ArrayList<>()).add(occasion);
		return true;
	}

	public static boolean isPlaying(ServerLevel level, BlockPos hall) {
		return PLAYING.getOrDefault(level.dimension(), List.of()).stream().anyMatch(p -> p.hall.equals(hall));
	}

	public static int notesPlayed(BlockPos hall) {
		return NOTES_PLAYED.getOrDefault(hall, 0);
	}

	public static List<String> occasions(BlockPos hall) {
		return List.copyOf(OCCASIONS.getOrDefault(hall, List.of()));
	}

	/** Forgets what's playing and the counts (tests). */
	public static void forget() {
		PLAYING.clear();
		NOTES_PLAYED.clear();
		OCCASIONS.clear();
	}

	// Composing.

	/**
	 * The hall's round: a village with a settled anthem-maker and no anthem gets one, composed from its name, and plays
	 * it; the owner gets the book once (when online).
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (entity.anthem().isEmpty()) {
			Optional<net.minecraft.world.entity.npc.Villager> bard = BardLaureate.composer(level, hall);
			if (bard.isEmpty()) {
				return;
			}
			Component name = VillageHalls.name(level, hall);
			entity.setAnthem(compose(name.getString()));
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.anthem", bard.get().getDisplayName(), name));
			double r = VillageHalls.RADIUS + 32;
			for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.anthem.composed", bard.get().getDisplayName(), name)
					.withStyle(ChatFormatting.GOLD));
			}
			play(level, hall, "legend");
		}
		if (!entity.anthemBookGiven() && entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null) {
				ItemStack book = book(level, hall, entity.anthem().get());
				if (!owner.getInventory().add(book)) {
					owner.drop(book, false);
				}
				entity.setAnthemBookGiven(true);
				Chat.chat(owner, Component.translatable("message.aliveworkplace.anthem.book", VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** The written book: "The Anthem of Thornholm", the instrument, then the notes four to a line. */
	public static ItemStack book(ServerLevel level, BlockPos hall, Anthem anthem) {
		Component village = VillageHalls.name(level, hall);
		String title = Component.translatable("item.aliveworkplace.anthem.title", village).getString();
		StringBuilder notes = new StringBuilder();
		for (int i = 0; i < anthem.notes().length; i++) {
			notes.append(noteName(anthem.notes()[i])).append(i % 4 == 3 ? "\n" : "  ");
		}
		Component page = Component.translatable("item.aliveworkplace.anthem.page", village, Component.translatable("anthem.aliveworkplace.instrument." + anthem.instrument()),
			notes.toString().trim());
		String author = Component.translatable("item.aliveworkplace.anthem.author").getString();
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title.length() > 32 ? title.substring(0, 32) : title),
			author, 0, List.of(Filterable.passThrough(page)), true));
		return book;
	}

	// The hall's button, top right on the Legends page.

	/** Shows the anthem's button on the Legends page: play it, or (none yet) who would compose one. */
	public static void button(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		Optional<Anthem> anthem = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.anthem() : Optional.empty();
		if (anthem.isEmpty()) {
			menu.button(PLAY, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.anthem.none"), ChatFormatting.GRAY,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.anthem.none_hint"), ChatFormatting.GRAY)), p ->
				Chat.actionBar(p, Component.translatable("screen.aliveworkplace.anthem.none").withStyle(ChatFormatting.GRAY)));
			return;
		}
		Anthem a = anthem.get();
		menu.button(PLAY, VillageHallScreen.icon(Items.JUKEBOX, Component.translatable("screen.aliveworkplace.anthem.play"), ChatFormatting.GREEN,
			VillageHallScreen.line(Component.translatable("item.aliveworkplace.anthem.title", VillageHalls.name(level, hall)), ChatFormatting.GOLD),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.anthem.has", Component.translatable("anthem.aliveworkplace.instrument." + a.instrument())),
				ChatFormatting.YELLOW),
			VillageHallScreen.line(Component.literal(a.written()), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.anthem.about"), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.anthem.play_hint"), ChatFormatting.GRAY)), p -> {
			boolean began = play(level, hall, "hall");
			Chat.actionBar(p, Component.translatable(began ? "message.aliveworkplace.anthem.playing" : "message.aliveworkplace.anthem.already",
				VillageHalls.name(level, hall)).withStyle(began ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		});
	}

	private Anthems() {
	}
}
