package io.github.jcondedata.aliveworkplace.daycare;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Daycare Keeper (ROADMAP 28.12): a villager at a Pasture Block, picked with an egg, who looks after one pair of
 * Pokémon per player ({@link #MAX_PAIRS} pairs at most). Each dawn she may find an egg with a pair ({@link #chance}),
 * by how well the two get along; she keeps up to {@link #MAX_EGGS} a pair for their trainer, who collects them for
 * {@link #EGG_PRICE} emeralds (or their worth in CobbleDollars) each. With Cobbreeding an egg is a real Cobbreeding egg;
 * without it, the hatchling itself. If she dies, the pairs go to their trainers' PCs. The Pokémon, the screen and the
 * eggs live in {@code compat/cobblemon/CobblemonDaycareKeeper}; this keeps the pairs and the dawns. Config
 * {@code daycareKeepers}.
 */
public final class DaycareKeepers {
	/** Config switch {@code daycareKeepers}: off, an egg picks no job at a pasture and keepers already hired find no eggs. */
	public static boolean ENABLED = true;
	/** Most pairs one keeper looks after (one per player). */
	public static final int MAX_PAIRS = 3;
	/** Most eggs she keeps for a pair. */
	public static final int MAX_EGGS = 3;
	/** What collecting one egg costs, in emeralds. */
	public static final int EGG_PRICE = 4;
	/** How well a pair gets along: not at all, so-so, well, very well. */
	public static final int NOT_AT_ALL = 0, SO_SO = 1, WELL = 2, VERY_WELL = 3;
	public static final String[] GET_ALONG = {"not_at_all", "so_so", "well", "very_well"};

	/** A pair in a keeper's care: whose, the two Pokémon as they were left, the eggs found and waiting, and the last day she looked. */
	public record Pair(UUID owner, String ownerName, CompoundTag first, CompoundTag second, int eggs, long day) {
		public static final Codec<Pair> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Pair::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Pair::ownerName),
			CompoundTag.CODEC.fieldOf("first").forGetter(Pair::first),
			CompoundTag.CODEC.fieldOf("second").forGetter(Pair::second),
			Codec.INT.optionalFieldOf("eggs", 0).forGetter(Pair::eggs),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Pair::day)
		).apply(i, Pair::new));

		public Pair withEggs(int eggs, long day) {
			return new Pair(owner, ownerName, first, second, eggs, day);
		}
	}

	/** The Pokémon side, filled by the compat layer. */
	public interface Breeding {
		Extension<Breeding> EXTENSION = new Extension<>("the daycare keeper's pairs");

		/** Opens the daycare screen for {@code player} at {@code keeper}. */
		void open(ServerPlayer player, Villager keeper);

		/** How well the pair gets along ({@link #NOT_AT_ALL} to {@link #VERY_WELL}). */
		int getAlong(ServerLevel level, Pair pair);

		/** Sends every pair in {@code keeper}'s care back to its trainer's PC. */
		void returnAll(ServerLevel level, Villager keeper);
	}

	public static boolean isKeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.DAYCARE_KEEPER;
	}

	/** Whether the job can be had: Cobblemon is there with its Pasture Block, and the config switch is on. */
	public static boolean available() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && BuiltInRegistries.BLOCK.containsKey(ModVillagers.PASTURE_BLOCK);
	}

	/** An egg: what picks the job at a Pasture Block. */
	public static boolean isEgg(ItemStack stack) {
		return available() && stack.is(Items.EGG);
	}

	public static List<Pair> pairs(Villager keeper) {
		return ModAttachments.DAYCARE_PAIRS.getOrElse(keeper, List.of());
	}

	public static void setPairs(Villager keeper, List<Pair> pairs) {
		if (pairs.isEmpty()) {
			ModAttachments.DAYCARE_PAIRS.remove(keeper);
		} else {
			ModAttachments.DAYCARE_PAIRS.set(keeper, List.copyOf(pairs));
		}
	}

	/** The chance (percent) of an egg at dawn for a pair that gets along {@code getAlong}, with a keeper at {@code level}. */
	public static int chance(int getAlong, int level) {
		int base = switch (getAlong) {
			case VERY_WELL -> 70;
			case WELL -> 50;
			case SO_SO -> 20;
			default -> 0;
		};
		return base == 0 ? 0 : base + (level >= 4 ? 10 : 0);
	}

	/** {@code keeper}'s chance (percent) of an egg at dawn for a pair that gets along {@code getAlong}: her level, and the village's Breeding Records (29.21). */
	public static int chance(Villager keeper, int getAlong) {
		return io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.eggOdds(keeper, chance(getAlong, keeper.getVillagerData().getLevel()));
	}

	/** The day number (a new one starts at dawn). */
	public static long day(ServerLevel level) {
		return Math.floorDiv(level.getDayTime(), 24000L);
	}

	/**
	 * The dawns since she last looked: for each pair, one roll per dawn passed ({@link #MAX_EGGS} at most), an egg on a
	 * hit. Returns the eggs found. A pair left today first counts from today.
	 */
	public static int dawn(ServerLevel level, Villager keeper, RandomSource random) {
		List<Pair> pairs = pairs(keeper);
		if (pairs.isEmpty()) {
			return 0;
		}
		long today = day(level);
		Breeding breeding = Breeding.EXTENSION.call(b -> b, null);
		List<Pair> out = new ArrayList<>();
		int found = 0;
		boolean changed = false;
		for (Pair pair : pairs) {
			if (pair.day() >= today) {
				out.add(pair);
				continue;
			}
			int eggs = pair.eggs();
			if (ENABLED && breeding != null) {
				int odds = chance(keeper, breeding.getAlong(level, pair));
				for (long d = Math.max(pair.day(), today - MAX_EGGS); d < today && eggs < MAX_EGGS; d++) {
					if (odds > 0 && random.nextInt(100) < odds) {
						eggs++;
						found++;
					}
				}
			}
			out.add(pair.withEggs(eggs, today));
			changed = true;
		}
		if (changed) {
			setPairs(keeper, out);
		}
		return found;
	}

	/** {@code keeper} died: every pair goes to its trainer's PC. */
	public static void onDeath(ServerLevel level, Villager keeper) {
		if (!pairs(keeper).isEmpty()) {
			Breeding.EXTENSION.run(b -> b.returnAll(level, keeper));
		}
	}

	public static void open(ServerPlayer player, Villager keeper) {
		Breeding.EXTENSION.run(b -> b.open(player, keeper));
	}

	private DaycareKeepers() {
	}
}
