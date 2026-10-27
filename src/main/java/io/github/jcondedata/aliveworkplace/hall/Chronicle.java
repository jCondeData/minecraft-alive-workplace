package io.github.jcondedata.aliveworkplace.hall;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The village chronicle: what happened in a village with a Village Hall, day by day — the hall founded, babies born,
 * villagers who died or came back, travellers who arrived and joined, buildings finished, quests done, research, new
 * masters. Kept in the hall ({@link #MAX} entries, the oldest forgotten) and shown on its Chronicle page.
 */
public final class Chronicle {
	/** Most entries a hall keeps. */
	public static final int MAX = 100;

	public enum Kind {
		FOUNDED(Items.BELL), BIRTH(Items.EGG), DEATH(Items.BONE), REVIVED(Items.TOTEM_OF_UNDYING), ARRIVED(Items.LEATHER_BOOTS),
		JOINED(Items.NAME_TAG), BUILT(Items.BRICKS), QUEST(Items.MAP), RESEARCH(Items.ENCHANTED_BOOK), MASTER(Items.EXPERIENCE_BOTTLE),
		MARKET(Items.EMERALD), CARAVAN(Items.CHEST_MINECART), RAID(Items.ZOMBIE_HEAD), RANK(Items.FIREWORK_ROCKET),
		FESTIVAL(Items.CAKE), WEDDING(Items.POPPY), PROTECTION(Items.SHIELD), EDICT(Items.LECTERN), LEGEND(Items.NETHER_STAR), PLANS(Items.MAP),
		SIGHTING(Items.SPYGLASS), BANNER(Items.WHITE_BANNER),
		REFORM(Items.WRITABLE_BOOK), GUILD(Items.PAPER), CUP(Items.GOLD_INGOT),
		/** A household rose or came down in the world (34.6). */
		CLASS(Items.GOLD_INGOT),
		/** A villager's life stages (34.19, 34.19a): an elder made ageless. */
		LIFE(Items.CLOCK),
		/** A story arc's chapters and endings (31.4). */
		STORY(Items.BOOK),
		/** A villager told a player something of their life (a heart event, 31.7). */
		FRIEND(Items.PINK_TULIP),
		/** A player's new title in the village, or an honour it gave them (31.11). */
		TITLE(Items.GOLDEN_HELMET),
		/** The morning's report of a siege (32.6): who came, how many fell and to whom, whether the gate held, the hero. */
		SIEGE(Items.IRON_BARS),
		/** A realm founded, joined, left, renamed, risen in rank or ended (33.11). */
		REALM(Items.GOLDEN_HELMET),
		/** A bounty posted, its captain fallen, its insignia handed in (and by whom), or the bounty come down (31.13). */
		BOUNTY(Items.CROSSBOW),
		/** A fire in the village, begun and put out (32.15, 32.16). */
		FIRE(Items.FLINT_AND_STEEL),
		/** A drought, begun, foretold and ended (32.15, 32.17). */
		DROUGHT(Items.DEAD_BUSH),
		/** A plague, begun, foretold and ended (32.15, 32.18). */
		PLAGUE(Items.FERMENTED_SPIDER_EYE),
		/** A feud declared, peace offered or made, a war raised and ended with the guards each side lost (33.20). */
		FEUD(Items.IRON_SWORD),
		/** A Wonder begun, a stage of it done, finished or abandoned (M35). */
		WONDER(Items.BEACON),
		/** A funeral the Undertaker led at a grave (36.16). */
		FUNERAL(Items.WITHER_ROSE);

		public final Item icon;

		Kind(Item icon) {
			this.icon = icon;
		}

		static Kind parse(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return FOUNDED;
			}
		}
	}

	/** One line of the chronicle: the day (1 for the world's first), what kind of thing, and what happened. */
	public record Entry(long day, Kind kind, Component text) {
	}

	/** The day number of {@code level}'s clock (the first day is day 1). */
	public static long day(ServerLevel level) {
		return level.getDayTime() / VillageNeeds.DAY + 1;
	}

	/**
	 * Who did it, for the first word of a chronicle line (B611): {@code who}'s name, or "A villager" when they aren't
	 * loaded ({@code steward.aliveworkplace.jobs.someone}, "a villager", is for mid-sentence use).
	 */
	public static Component lineStarter(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity who) {
		return who != null ? who.getDisplayName() : Component.translatable("chronicle.aliveworkplace.a_villager");
	}

	/** The other village's name mid-sentence ("A caravan came from Ashdale"), or "another village" when it's unknown (B611). */
	public static Component otherVillage(@org.jetbrains.annotations.Nullable Component name) {
		return name != null ? name : Component.translatable("chronicle.aliveworkplace.another_village");
	}

	/** Writes {@code text} into the chronicle of the village {@code where} is in (if it has a hall). */
	public static void record(ServerLevel level, BlockPos where, Kind kind, Component text) {
		VillageHalls.nearest(level, where).ifPresent(hall -> record(level, hall, kind, text, true));
	}

	/** Writes {@code text} into the chronicle of the hall at {@code hall}. */
	static void record(ServerLevel level, BlockPos hall, Kind kind, Component text, boolean atHall) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.addToChronicle(new Entry(day(level), kind, text));
		}
	}

	/** Writes {@code text} into the chronicle of the hall at {@code hall} (the quest engine, 31.2). */
	public static void atHall(ServerLevel level, BlockPos hall, Kind kind, Component text) {
		record(level, hall, kind, text, true);
	}

	/** Writes {@code text} into the chronicle of the hall at {@code hall}, dated {@code day} (a Cup's entry written when the village next loads, 28.19). */
	public static void atHall(ServerLevel level, BlockPos hall, Kind kind, Component text, long day) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.addToChronicle(new Entry(day, kind, text));
		}
	}

	private Chronicle() {
	}
}
