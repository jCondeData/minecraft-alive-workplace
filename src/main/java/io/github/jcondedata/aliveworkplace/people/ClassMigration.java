package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Switching classes on for every village at once (ROADMAP 34.22, docs/design/M34.md "The migration"). A hall saved before
 * 1.8 has {@code classesVersion} 0: while {@code villageClasses} is on, each of its rounds seeds the next {@link #SLICE}
 * villagers without a class. A household gets the highest class, at most the third (Burgher: Nobles are earned), whose
 * needs other than luxuries hold now ({@code fed_days} reads "fed now", since nobody has counted fed dawns yet); children
 * their parents' or their building's; grown-ups an age ({@code adult_since}) spread by UUID over the last
 * {@link #AGE_SPREAD} days and a family name. Mercenaries, inn guests and caravan parties are skipped.
 *
 * <p>Nothing is kept about how far it got: a villager with {@code social_class} is seeded, so a crash or a save part-way
 * re-seeds only the rest, and nobody is seeded twice. When nobody is left the hall is at {@link #VERSION}, settles in for
 * {@link #GRACE_DAYS} days ({@code classGraceUntil}: nobody falls a class, luxuries are taken but not needed, nobody
 * retires), and the chronicle and the players in the village are told once. Afterwards a villager who arrives without a
 * class is seeded the same way at the next round, quietly. Jobs, ranks, the treasury, build sites, quarries, daycare
 * Pokémon, fossils and parcels are never touched. With {@code villageClasses} off nothing happens and nothing is lost.
 */
public final class ClassMigration {
	/** Villagers seeded a hall round. */
	public static final int SLICE = 16;
	/** The rules seeding uses now ({@code classesVersion}). */
	public static final int VERSION = 1;
	/** Ages are spread over this many days before the switch. */
	public static final int AGE_SPREAD = 1000; // 36.16: ten times the first 100, as lives are ten times longer
	/** {@code classGraceDays}: days of settling in after a hall is first seeded. */
	public static int GRACE_DAYS = 7;
	/**
	 * Whether the hall's own round seeds. Off in GameTests (a hall's round would seed a test's villagers part-way through
	 * it); the migration tests switch it on, or call {@link #round(ServerLevel, BlockPos, VillageHallBlockEntity, long)}.
	 */
	public static boolean AUTO = true;
	/** The highest class seeding gives: the third of the ladder (Burgher). */
	public static final int HIGHEST_STEP = 2;

	/** What a round did: how many it seeded, and whether the hall has just finished its first seeding. */
	public record Round(int seeded, boolean finished) {
	}

	/** The hall's round. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (AUTO) {
			round(level, hall, entity, Chronicle.day(level));
		}
	}

	/** {@link #round} on {@code today}; null with classes off (or no class files), or when nobody needed seeding. */
	@Nullable
	public static Round round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today) {
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			return null;
		}
		List<Villager> village = residents(level, hall);
		boolean first = entity.classesVersion() < VERSION;
		int seeded = seed(level, hall, entity, village, today, SLICE);
		if (seeded > 0) {
			entity.setChanged(); // the Book of Families' ages
		}
		if (!first) {
			return seeded == 0 ? null : new Round(seeded, false);
		}
		if (village.stream().anyMatch(v -> !ModAttachments.SOCIAL_CLASS.has(v))) {
			return new Round(seeded, false);
		}
		finish(level, hall, entity, village, today);
		return new Round(seeded, true);
	}

	/**
	 * The {@link #resident} villagers of the village round {@code hall}: inside its circle ({@link VillageHalls#contains}),
	 * as the census counts them, not the square box round it (B548: a villager in the box's corner is in no village).
	 */
	static List<Villager> residents(ServerLevel level, BlockPos hall) {
		return VillageHalls.villagers(level, hall, ClassMigration::resident);
	}

	/** Lives here: not a mercenary, an inn's guest or a caravan's party passing through. */
	static boolean resident(Villager villager) {
		return villager.isAlive() && FamilyBook.staying(villager);
	}

	/**
	 * Seeds up to {@code budget} of {@code village} without a class (a couple is never split, so a round may go one over):
	 * the grown households first, in a fixed order by UUID, then the children. Returns how many were seeded.
	 */
	static int seed(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, List<Villager> village, long today, int budget) {
		ClassNeeds.Village context = new ClassNeeds.Village(level, hall, today);
		int seeded = 0;
		for (Households.Household household : Households.of(village)) {
			List<Villager> fresh = household.members().stream().filter(v -> !ModAttachments.SOCIAL_CLASS.has(v)).toList();
			if (fresh.isEmpty()) {
				continue;
			}
			if (seeded >= budget) {
				return seeded;
			}
			SocialClass c = qualifies(level, household.members(), context);
			for (Villager v : household.members()) {
				SocialClass had = SocialClasses.of(v);
				if (had != null && had.tier() > c.tier()) {
					c = had; // a newcomer married to someone already seeded lives as the higher, as the dawn check has it
				}
			}
			for (Villager v : fresh) {
				grownUp(level, entity, v, today, village);
				SocialClasses.seed(v, c);
				seeded++;
			}
		}
		List<Villager> children = new ArrayList<>(village.stream().filter(v -> v.isBaby() && !ModAttachments.SOCIAL_CLASS.has(v)).toList());
		children.sort(Comparator.comparing(Villager::getUUID));
		List<Villager> grown = village.stream().filter(Households::grownResident).toList();
		for (Villager child : children) {
			if (seeded >= budget) {
				return seeded;
			}
			FamilyBook.giveFamily(level, entity.families(), child, village);
			SocialClasses.seed(child, childClass(level, child, grown, context));
			seeded++;
		}
		return seeded;
	}

	/** The highest class up to the third whose needs, luxuries aside, hold for {@code members} now; the floor when none above does. */
	static SocialClass qualifies(ServerLevel level, List<Villager> members, ClassNeeds.Village context) {
		List<SocialClass> ladder = SocialClasses.ladder();
		for (int i = Math.min(HIGHEST_STEP, ladder.size() - 1); i > 0; i--) {
			SocialClass c = ladder.get(i);
			boolean all = true;
			for (ClassNeeds.Need need : c.needs()) {
				if (!holdsNow(level, need, members, context)) {
					all = false;
					break;
				}
			}
			if (all) {
				return c;
			}
		}
		return ladder.get(0);
	}

	/** A need as seeding reads it: luxuries don't count, {@code fed_days} is "fed now"; the rest as the dawn check reads them. */
	static boolean holdsNow(ServerLevel level, ClassNeeds.Need need, List<Villager> members, ClassNeeds.Village context) {
		return switch (need) {
			case ClassNeeds.Luxury l -> true;
			case ClassNeeds.FedDays f -> members.stream().allMatch(v -> ModAttachments.LAST_MEAL.has(v) && !VillageNeeds.isHungry(v, level.getGameTime()));
			default -> ClassNeeds.holds(need, members, context);
		};
	}

	/** A child's class: their parents' (the higher, if they're here and seeded), else that of the grown-ups of their building, else the floor. */
	static SocialClass childClass(ServerLevel level, Villager child, List<Villager> grown, ClassNeeds.Village context) {
		SocialClass best = null;
		Families.Parents parents = Families.parents(child);
		if (parents != null) {
			for (UUID id : new UUID[] {parents.motherId(), parents.fatherId()}) {
				if (id != null && level.getEntity(id) instanceof Villager parent) {
					SocialClass c = SocialClasses.of(parent);
					if (c != null && (best == null || c.tier() > best.tier())) {
						best = c;
					}
				}
			}
		}
		if (best == null) {
			BlockPos bed = VillageNeeds.bed(level, child);
			Homes.Building building = context.buildingAt(bed).orElse(null);
			if (building != null) {
				for (Villager v : grown) {
					BlockPos theirs = VillageNeeds.bed(level, v);
					SocialClass c = theirs != null && building.box().isInside(theirs) ? SocialClasses.of(v) : null;
					if (c != null && (best == null || c.tier() > best.tier())) {
						best = c;
					}
				}
			}
		}
		return best != null ? best : SocialClasses.floor();
	}

	/** A grown-up's age, if nobody knows it yet ({@link #ageDays}), and a family name; the book's entry dated to match. */
	static void grownUp(ServerLevel level, VillageHallBlockEntity entity, Villager villager, long today, List<Villager> village) {
		if (LifeStages.adultSince(villager) == null) {
			long since = today - ageDays(villager.getUUID());
			LifeStages.setAdultSince(villager, since);
			FamilyBook.Book book = entity.families();
			FamilyBook.Person p = book.get(villager.getUUID());
			if (p != null && !p.bornHere() && !p.dead()) {
				// Written in by an earlier round with today as the day they grew up: now it's known.
				book.put(new FamilyBook.Person(p.id(), p.name(), p.family(), since, false, p.died(), p.mother(), p.father(), p.partner(), p.trade()));
			}
		}
		FamilyBook.giveFamily(level, entity.families(), villager, village);
	}

	/** Days grown at the switch, 0 to {@link #AGE_SPREAD} - 1, from the UUID: the same villager always gets the same. */
	public static int ageDays(UUID id) {
		long mixed = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits();
		return (int) Math.floorMod(mixed ^ (mixed >>> 29), (long) AGE_SPREAD);
	}

	/** Everyone's seeded: the version, the grace, and (if anyone lives here) the chronicle's line and one chat line to the players in the village. */
	static void finish(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, List<Villager> village, long today) {
		entity.setClasses(VERSION, today + Math.max(0, GRACE_DAYS));
		if (village.isEmpty()) {
			return;
		}
		Component name = VillageHalls.name(level, hall);
		Component counts = counts(village);
		Chronicle.atHall(level, hall, Chronicle.Kind.CLASS, Component.translatable("chronicle.aliveworkplace.class.seeded", name, counts));
		String key = GRACE_DAYS <= 0 ? "message.aliveworkplace.class.seeded_no_grace" : GRACE_DAYS == 1 ? "message.aliveworkplace.class.seeded_one" : "message.aliveworkplace.class.seeded";
		Component line = Component.translatable(key, name, counts, GRACE_DAYS).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && VillageHalls.contains(hall, player.blockPosition())) {
				io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, line);
			}
		}
	}

	/** How many of {@code village} are of each class, lowest first, classes with nobody left out: "14 peasants, 6 artisans and 2 burghers". */
	public static Component counts(List<Villager> village) {
		Map<SocialClass, Integer> by = new LinkedHashMap<>();
		for (SocialClass c : SocialClasses.ladder()) {
			by.put(c, 0);
		}
		for (Villager v : village) {
			SocialClass c = SocialClasses.of(v);
			if (c != null) {
				by.merge(c, 1, Integer::sum);
			}
		}
		List<Component> parts = new ArrayList<>();
		for (Map.Entry<SocialClass, Integer> e : by.entrySet()) {
			if (e.getValue() > 0) {
				parts.add(count(e.getKey(), e.getValue()));
			}
		}
		return FamilyBook.list(parts);
	}

	/** "14 peasants", "1 burgher" (a data pack's class without words of its own: "3 Guildmaster"). */
	public static Component count(SocialClass c, int n) {
		String key = "class." + c.id().getNamespace() + "." + c.id().getPath() + ".count" + (n == 1 ? ".one" : "");
		return Component.translatableWithFallback(key, "%s %s", n, c.name());
	}

	// --- Settling in -------------------------------------------------------------------------------------------------

	/** Days of settling in left at {@code hall} on {@code today} (7 the day it's seeded, 0 once it's over), or 0 with classes off. */
	public static long daysLeft(ServerLevel level, BlockPos hall, long today) {
		if (!SocialClasses.ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.classesVersion() < VERSION) {
			return 0;
		}
		return Math.max(0, entity.classGraceUntil() - today);
	}

	/** Whether the village at {@code hall} is settling in on {@code today}. */
	public static boolean settling(ServerLevel level, BlockPos hall, long today) {
		return daysLeft(level, hall, today) > 0;
	}

	/** Whether the village is still being seeded (classes on, the hall not yet at {@link #VERSION}). */
	public static boolean seeding(ServerLevel level, BlockPos hall) {
		return SocialClasses.ENABLED && !SocialClasses.ladder().isEmpty() && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			&& entity.classesVersion() < VERSION;
	}

	/**
	 * Nobody falls a class and nobody starts retiring while a village settles in. (Not while it is being seeded: that takes
	 * a few rounds, and nobody falls in less than {@code classFallDays} dawns.)
	 */
	public static boolean quiet(ServerLevel level, BlockPos hall, long today) {
		return settling(level, hall, today);
	}

	/**
	 * The hall's settling-in lines (unstyled): "Settling in: 5 days left", what the week means, then each class's luxuries,
	 * which it will need from then on ("Artisan households will need Work Clothes"). Empty when the village isn't settling in.
	 */
	public static List<Component> settlingLines(ServerLevel level, BlockPos hall, long today) {
		List<Component> out = new ArrayList<>();
		long left = daysLeft(level, hall, today);
		if (left <= 0) {
			return out;
		}
		out.add(Component.translatable(left == 1 ? "screen.aliveworkplace.classes.settling_one" : "screen.aliveworkplace.classes.settling", left));
		out.add(Component.translatable("screen.aliveworkplace.classes.settling_about"));
		for (SocialClass c : SocialClasses.ladder()) {
			List<Component> luxuries = new ArrayList<>();
			for (ClassNeeds.Need need : c.needs()) {
				if (need instanceof ClassNeeds.Luxury l) {
					luxuries.add(io.github.jcondedata.aliveworkplace.hall.ClassesPage.luxuryName(l.id()));
				}
			}
			if (!luxuries.isEmpty()) {
				out.add(Component.translatable("screen.aliveworkplace.classes.will_need", c.name(), FamilyBook.list(luxuries)));
			}
		}
		return out;
	}

	// --- The operators' command ----------------------------------------------------------------------------------------

	/** {@code /workplace classes status}: the village's counts, and whether it is seeding, settling in or settled. */
	public static List<Component> status(ServerLevel level, BlockPos hall) {
		List<Component> out = new ArrayList<>();
		Component name = VillageHalls.name(level, hall);
		if (!SocialClasses.ENABLED || SocialClasses.ladder().isEmpty()) {
			out.add(Component.translatable("command.aliveworkplace.classes.off", name));
			return out;
		}
		List<Villager> village = residents(level, hall);
		long unseeded = village.stream().filter(v -> !ModAttachments.SOCIAL_CLASS.has(v)).count();
		long today = Chronicle.day(level);
		if (seeding(level, hall)) {
			out.add(Component.translatable("command.aliveworkplace.classes.seeding", name, village.size() - unseeded, village.size()));
		} else if (settling(level, hall, today)) {
			out.add(io.github.jcondedata.aliveworkplace.work.Words.counted("command.aliveworkplace.classes.settling", daysLeft(level, hall, today), name, daysLeft(level, hall, today)));
		} else {
			out.add(Component.translatable("command.aliveworkplace.classes.settled", name));
		}
		out.add(Component.translatable("command.aliveworkplace.classes.counts", village.size() - unseeded == 0
			? Component.translatable("command.aliveworkplace.classes.nobody") : counts(village)));
		if (!seeding(level, hall) && unseeded > 0) {
			out.add(Component.translatable("command.aliveworkplace.classes.newcomers", unseeded));
		}
		return out;
	}

	/**
	 * {@code /workplace classes reseed}: the hall goes back to {@code classesVersion} 0 and its villagers lose their class
	 * and progress, so the next rounds seed them again (ages and family names are kept). Returns how many were cleared.
	 */
	public static int reseed(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		int cleared = 0;
		for (Villager v : residents(level, hall)) {
			if (ModAttachments.SOCIAL_CLASS.has(v)) {
				cleared++;
			}
			ModAttachments.SOCIAL_CLASS.remove(v);
			ModAttachments.CLASS_PROGRESS.remove(v);
			ClassLook.update(v);
		}
		entity.setClasses(0, 0);
		SocialClasses.forget();
		return cleared;
	}

	private ClassMigration() {
	}
}
