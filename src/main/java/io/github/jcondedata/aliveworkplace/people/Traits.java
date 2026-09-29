package io.github.jcondedata.aliveworkplace.people;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/**
 * Every villager is a little different: one trait, sometimes two, that change how they work and live — {@link Trait}.
 * A villager's traits come from who they are (their UUID), so they never change and cost nothing to keep; the Village
 * Hall lists them. {@code villagerTraits} in the config turns them off.
 */
public final class Traits {
	/** Whether villagers have traits at all. */
	public static boolean ENABLED = true;
	/** A village is this much happier for each cheerful grown-up in it, up to {@link #MAX_CHEER}. */
	public static final float CHEER = 0.01f;
	public static final float MAX_CHEER = 0.05f;

	public enum Trait {
		/** Works 10% faster. */
		DILIGENT,
		/** Works 10% slower. */
		LAZY,
		/** Walks 15% faster. */
		NIMBLE,
		/** Learns a quarter faster (more XP for the same work). */
		CLEVER,
		/** Hits 15% harder (guards). */
		STRONG,
		/** Makes the village happier (1% wellbeing each, up to 5%). */
		CHEERFUL,
		/** Eats twice a day. */
		GLUTTON,
		/** Eats every other day. */
		FRUGAL;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Component title() {
			return Component.translatable("trait.aliveworkplace." + key());
		}

		public Component description() {
			return Component.translatable("trait.aliveworkplace." + key() + ".desc");
		}

		boolean clashesWith(Trait other) {
			return this == other || pair(DILIGENT, LAZY, other) || pair(GLUTTON, FRUGAL, other);
		}

		private boolean pair(Trait a, Trait b, Trait other) {
			return this == a && other == b || this == b && other == a;
		}
	}

	private static final Map<UUID, List<Trait>> CACHE = new ConcurrentHashMap<>();

	/** {@code villager}'s traits (none when traits are off). */
	public static List<Trait> of(Villager villager) {
		return ENABLED ? of(villager.getUUID()) : List.of();
	}

	/** The traits someone with this UUID has: one, and half the time a second that doesn't clash with it. */
	public static List<Trait> of(UUID id) {
		return CACHE.computeIfAbsent(id, u -> {
			RandomSource random = RandomSource.create(u.getMostSignificantBits() ^ Long.rotateLeft(u.getLeastSignificantBits(), 17));
			Trait[] all = Trait.values();
			List<Trait> out = new ArrayList<>();
			out.add(all[random.nextInt(all.length)]);
			if (random.nextBoolean()) {
				Trait second = all[random.nextInt(all.length)];
				if (!second.clashesWith(out.get(0))) {
					out.add(second);
				}
			}
			return List.copyOf(out);
		});
	}

	public static boolean has(Villager villager, Trait trait) {
		return of(villager).contains(trait);
	}

	/** Work delay multiplier: diligent villagers work 10% faster, lazy ones 10% slower. */
	public static float pace(Villager villager) {
		List<Trait> traits = of(villager);
		return traits.contains(Trait.DILIGENT) ? 1f / 1.1f : traits.contains(Trait.LAZY) ? 1.1f : 1f;
	}

	/** Walking speed multiplier: nimble villagers walk 15% faster. */
	public static float speed(Villager villager) {
		return has(villager, Trait.NIMBLE) ? 1.15f : 1f;
	}

	/** Damage multiplier: strong villagers hit 15% harder. */
	public static float strength(Villager villager) {
		return has(villager, Trait.STRONG) ? 1.15f : 1f;
	}

	/** The XP {@code xp} is worth to {@code villager}: a quarter more for clever ones (the fraction by chance). */
	public static int xp(Villager villager, int xp, RandomSource random) {
		if (xp <= 0 || !has(villager, Trait.CLEVER)) {
			return xp;
		}
		float more = xp * 0.25f;
		int whole = (int) more;
		return xp + whole + (random.nextFloat() < more - whole ? 1 : 0);
	}

	/** How often {@code villager} eats, in ticks: gluttons twice a day, the frugal every other day. */
	public static long mealEvery(Villager villager, long day) {
		List<Trait> traits = of(villager);
		return traits.contains(Trait.GLUTTON) ? day / 2 : traits.contains(Trait.FRUGAL) ? day * 2 : day;
	}

	/** Forget cached traits (tests). */
	public static void forget() {
		CACHE.clear();
	}

	private Traits() {
	}
}
