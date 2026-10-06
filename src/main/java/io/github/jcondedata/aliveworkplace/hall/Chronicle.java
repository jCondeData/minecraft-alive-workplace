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
		REFORM(Items.WRITABLE_BOOK), GUILD(Items.PAPER), CUP(Items.GOLD_INGOT);

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
