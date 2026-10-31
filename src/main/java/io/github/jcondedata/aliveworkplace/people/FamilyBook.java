package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * Family trees (ROADMAP 34.21). Every Village Hall keeps the village's <b>Book of Families</b>: everyone who has lived
 * there, with their id, name, family, the day they were born or grew up, the day they died, their parents, partner and
 * trade; up to {@link #MAX} people, the longest dead forgotten first. The hall's round writes the living into it, a death
 * dates their entry, and someone an Undertaker brings back is alive in it again.
 *
 * <p><b>Family names.</b> A villager whose parents aren't known founds a family, named from a list of {@link #NAMES_COUNT}
 * ({@code family_name.aliveworkplace.<n>}) by their UUID; a child takes a parent's (the first parent's, else the
 * other's). The name is kept as a number on the villager ({@code family_name}) and only ever shown: the hall says
 * "Dara (Ashby)", the name over their head (or from a Name Tag) never changes. {@code familyNames} off hides every family
 * name; the book, the Family page and the printed tree then go by first names.
 *
 * <p>Births in the chronicle name the grandparents when the book knows them, and the first of a family's third
 * generation is noted once. Part of 1.8 ({@link #ENABLED} is gated with the expansion).
 */
public final class FamilyBook {
	/** The book, family names given, the Family page and the printed tree: on with 1.8 (Classes and luxuries). */
	public static boolean ENABLED = true;
	/** {@code familyNames} in the config: off, no family name is shown anywhere (they are still kept). */
	public static boolean NAMES = true;
	/** People the book keeps at most. */
	public static final int MAX = 400;
	/** Family names in the language file ({@code family_name.aliveworkplace.1} to {@code .60}). */
	public static final int NAMES_COUNT = 60;
	/** What the Printer asks for a family's tree, in emeralds (and CobbleDollars at the usual rate). */
	public static final int TREE_EMERALDS = 2;
	/** The generation the chronicle notes, once a family. */
	public static final int NOTED_GENERATION = 3;

	/**
	 * One person in the book: {@code born} is the day they were born here ({@code bornHere}) or the day they grew up (or
	 * were first written in); {@code died} is -1 for the living. Parents and partner are ids; {@code family} is 0 for none.
	 */
	public record Person(UUID id, Component name, int family, long born, boolean bornHere, long died, @Nullable UUID mother,
						 @Nullable UUID father, @Nullable UUID partner, String trade) {
		public static final Codec<Person> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Person::id),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Person::name),
			Codec.INT.optionalFieldOf("family", 0).forGetter(Person::family),
			Codec.LONG.optionalFieldOf("born", 0L).forGetter(Person::born),
			Codec.BOOL.optionalFieldOf("born_here", false).forGetter(Person::bornHere),
			Codec.LONG.optionalFieldOf("died").forGetter(p -> p.died() >= 0 ? Optional.of(p.died()) : Optional.empty()),
			UUIDUtil.CODEC.optionalFieldOf("mother").forGetter(p -> Optional.ofNullable(p.mother())),
			UUIDUtil.CODEC.optionalFieldOf("father").forGetter(p -> Optional.ofNullable(p.father())),
			UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(p -> Optional.ofNullable(p.partner())),
			Codec.STRING.optionalFieldOf("trade", "").forGetter(Person::trade)
		).apply(i, (id, name, family, born, bornHere, died, mother, father, partner, trade) -> new Person(id, name, family, born, bornHere,
			died.orElse(-1L), mother.orElse(null), father.orElse(null), partner.orElse(null), trade)));

		public boolean dead() {
			return died >= 0;
		}

		/** The parents on record, first parent first. */
		public List<UUID> parents() {
			List<UUID> out = new ArrayList<>(2);
			if (mother != null) {
				out.add(mother);
			}
			if (father != null) {
				out.add(father);
			}
			return out;
		}

		Person diedOn(long day) {
			return new Person(id, name, family, born, bornHere, day, mother, father, partner, trade);
		}
	}

	/** A hall's Book of Families, saved with the hall ({@code families}, and {@code familiesNoted}: the families whose third generation is noted). */
	public static final class Book {
		private final Map<UUID, Person> people = new LinkedHashMap<>();
		private final Set<Integer> noted = new TreeSet<>();

		@Nullable
		public Person get(@Nullable UUID id) {
			return id == null ? null : people.get(id);
		}

		/** Everyone in the book, in the order they were first written in. */
		public Collection<Person> people() {
			return java.util.Collections.unmodifiableCollection(people.values());
		}

		public int size() {
			return people.size();
		}

		/** Writes {@code person} in (in place of their old entry); whether anything changed. Keeps the book to {@link #MAX}. */
		public boolean put(Person person) {
			Person old = people.put(person.id(), person);
			trim();
			return !person.equals(old);
		}

		/** Whether the chronicle has noted {@code family}'s third generation. */
		public boolean noted(int family) {
			return noted.contains(family);
		}

		void note(int family) {
			noted.add(family);
		}

		/** Over {@link #MAX}: the longest dead are forgotten first (and if nobody is dead, the first written in). */
		void trim() {
			while (people.size() > MAX) {
				Person forget = people.values().stream().filter(Person::dead)
					.min(Comparator.comparingLong(Person::died).thenComparingLong(Person::born)).orElse(people.values().iterator().next());
				people.remove(forget.id());
			}
		}

		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			people.clear();
			noted.clear();
			if (Nbt.has(tag, "families", Tag.TAG_LIST)) {
				Person.CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("families")).result()
					.ifPresent(list -> list.forEach(p -> people.put(p.id(), p)));
			}
			for (int family : Nbt.getIntArray(tag, "familiesNoted")) {
				noted.add(family);
			}
		}

		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			if (!people.isEmpty()) {
				Person.CODEC.listOf().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), List.copyOf(people.values())).result()
					.ifPresent(t -> tag.put("families", t));
			}
			if (!noted.isEmpty()) {
				tag.putIntArray("familiesNoted", noted.stream().mapToInt(Integer::intValue).toArray());
			}
		}
	}

	// --- Family names ----------------------------------------------------------------------------------------------

	/** {@code villager}'s family (1 to {@link #NAMES_COUNT}), or 0 for none yet. */
	public static int family(Villager villager) {
		Integer n = ModAttachments.FAMILY_NAME.get(villager);
		return n == null ? 0 : n;
	}

	/** The family {@code id} founds: one of the {@link #NAMES_COUNT} names, by the UUID. */
	public static int founded(UUID id) {
		return Math.floorMod(id.hashCode(), NAMES_COUNT) + 1;
	}

	/** Family {@code n}'s name ("Ashby"). */
	public static Component familyName(int n) {
		return Component.translatable("family_name.aliveworkplace." + n);
	}

	/** A baby is born to {@code mother} and {@code father}: it takes the first parent's family (who founds one if they have none), else the other's. */
	static void born(Villager baby, Villager mother, Villager father) {
		if (!ENABLED) {
			return;
		}
		int f = family(mother) > 0 ? family(mother) : family(father);
		if (f == 0) {
			f = founded(mother.getUUID());
			ModAttachments.FAMILY_NAME.set(mother, f);
		}
		ModAttachments.FAMILY_NAME.set(baby, f);
	}

	/**
	 * Gives {@code villager} a family if they have none: a parent's (by id, from the living or the book; or, for a record
	 * from before the ids, a villager in {@code village} with the parent's name), else one they found.
	 */
	static void giveFamily(ServerLevel level, Book book, Villager villager, List<Villager> village) {
		if (family(villager) > 0) {
			return;
		}
		int f = 0;
		Families.Parents parents = Families.parents(villager);
		if (parents != null) {
			for (UUID id : new UUID[] {parents.motherId(), parents.fatherId()}) {
				if (f == 0 && id != null) {
					if (level.getEntity(id) instanceof Villager parent && family(parent) > 0) {
						f = family(parent);
					} else if (book.get(id) != null) {
						f = book.get(id).family();
					}
				}
			}
			if (f == 0 && parents.motherId() == null && parents.fatherId() == null) {
				for (Component name : List.of(parents.mother(), parents.father())) {
					for (Villager other : village) {
						if (f == 0 && other != villager && family(other) > 0 && other.getName().getString().equals(name.getString())) {
							f = family(other);
						}
					}
				}
			}
		}
		ModAttachments.FAMILY_NAME.set(villager, f > 0 ? f : founded(villager.getUUID()));
	}

	/** "Dara (Ashby)": a villager as the hall's list names them (the name alone with {@code familyNames} off, or with no family yet). */
	public static Component hallName(Villager villager) {
		return withFamily(villager.getDisplayName(), family(villager));
	}

	/** "Dara (Ashby)": someone in the book. */
	public static Component hallName(Person person) {
		return withFamily(person.name(), person.family());
	}

	private static Component withFamily(Component name, int family) {
		return ENABLED && NAMES && family > 0 ? Component.translatable("screen.aliveworkplace.hall.family_name", name, familyName(family)) : name;
	}

	/** "Bram Ashby": someone in the book with their family name after (the name alone with {@code familyNames} off). */
	public static Component fullName(Person person) {
		return NAMES && person.family() > 0 ? Component.translatable("family.aliveworkplace.full_name", person.name(), familyName(person.family())) : person.name();
	}

	// --- The book ----------------------------------------------------------------------------------------------------

	/** The book of the hall at {@code hall}, or null. */
	@Nullable
	public static Book book(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.families() : null;
	}

	/** Living here (not a mercenary, an inn's guest or a caravan's carter passing through). */
	static boolean staying(Villager villager) {
		return villager.isAlive() && !passing(villager);
	}

	private static boolean passing(Villager villager) {
		return io.github.jcondedata.aliveworkplace.guard.Mercenaries.isMercenary(villager)
			|| io.github.jcondedata.aliveworkplace.inn.Innkeepers.isTraveller(villager)
			|| io.github.jcondedata.aliveworkplace.hall.CaravanSights.isParty(villager);
	}

	/**
	 * The hall's round: everyone living in the village gets a family name if they have none and is written into the book
	 * (a baby new to it may be their family's third generation).
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED) {
			return;
		}
		List<Villager> village = VillageHalls.villagers(level, hall, FamilyBook::staying);
		Book book = entity.families();
		long today = Chronicle.day(level);
		boolean changed = false;
		// Founders first, then grown children, then babies, so a child with a parent here can take the parent's.
		List<Villager> order = new ArrayList<>(village);
		order.sort(Comparator.comparingInt((Villager v) -> Families.parents(v) == null ? 0 : v.isBaby() ? 2 : 1));
		for (Villager villager : order) {
			giveFamily(level, book, villager, village);
		}
		List<Villager> newBabies = new ArrayList<>();
		for (Villager villager : village) {
			if (villager.isBaby() && book.get(villager.getUUID()) == null) {
				newBabies.add(villager);
			}
			changed |= book.put(entry(book, villager, today));
		}
		for (Villager baby : newBabies) {
			noteGeneration(level, hall, book, baby); // once everyone's entry is up to date
		}
		if (changed) {
			entity.setChanged();
		}
	}

	/** {@code villager} as the book should have them today (alive). */
	static Person entry(Book book, Villager villager, long today) {
		Person old = book.get(villager.getUUID());
		Families.Parents parents = Families.parents(villager);
		UUID partner = Couples.partner(villager) != null ? Couples.partner(villager).id()
			: ModAttachments.LATE_PARTNER.get(villager) != null ? ModAttachments.LATE_PARTNER.get(villager).id()
			: old != null ? old.partner() : null;
		long born;
		boolean bornHere;
		if (old != null) {
			born = old.born();
			bornHere = old.bornHere();
		} else {
			Long grown = LifeStages.adultSince(villager);
			bornHere = villager.isBaby();
			born = bornHere || grown == null ? today : grown;
		}
		String trade = job(villager);
		if (trade.isEmpty() && Retirement.isRetired(villager) && ModAttachments.RETIRED.get(villager) != null) {
			trade = ModAttachments.RETIRED.get(villager).trade();
		}
		if (trade.isEmpty() && old != null) {
			trade = old.trade();
		}
		return new Person(villager.getUUID(), villager.getName().copy(), family(villager), born, bornHere, -1,
			parents != null ? parents.motherId() : old != null ? old.mother() : null,
			parents != null ? parents.fatherId() : old != null ? old.father() : null, partner, trade);
	}

	private static String job(Villager villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		return p == VillagerProfession.NONE || p == VillagerProfession.NITWIT ? "" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString();
	}

	/** Writes the living {@code villager} into {@code book} now (a page opened before the round reached them). */
	public static void write(ServerLevel level, BlockPos hall, Villager villager) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !staying(villager)) {
			return;
		}
		giveFamily(level, entity.families(), villager, List.of());
		if (entity.families().put(entry(entity.families(), villager, Chronicle.day(level)))) {
			entity.setChanged();
		}
	}

	/** A villager died: their entry in the book of the village they lived in is dated (they're written in first if they weren't). */
	public static void onDeath(ServerLevel level, Villager villager) {
		if (!ENABLED || passing(villager)) {
			return;
		}
		VillageHalls.containing(level, villager.blockPosition()).ifPresent(hall -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				long today = Chronicle.day(level);
				Book book = entity.families();
				giveFamily(level, book, villager, List.of());
				book.put(entry(book, villager, today).diedOn(today));
				entity.setChanged();
			}
		});
	}

	// --- Relations ---------------------------------------------------------------------------------------------------

	/** Everyone in {@code book} with {@code id} as a parent, oldest first. */
	public static List<Person> children(Book book, UUID id) {
		return book.people().stream().filter(p -> id.equals(p.mother()) || id.equals(p.father()))
			.sorted(Comparator.comparingLong(Person::born)).toList();
	}

	/** Everyone who shares a parent with {@code person}, oldest first. */
	public static List<Person> siblings(Book book, Person person) {
		List<UUID> parents = person.parents();
		return book.people().stream().filter(p -> !p.id().equals(person.id()) && p.parents().stream().anyMatch(parents::contains))
			.sorted(Comparator.comparingLong(Person::born)).toList();
	}

	/** {@code person}'s parents in the book. */
	public static List<Person> parents(Book book, Person person) {
		return person.parents().stream().map(book::get).filter(Objects::nonNull).toList();
	}

	/** {@code person}'s grandparents in the book, the first parent's first. */
	public static List<Person> grandparents(Book book, Person person) {
		List<Person> out = new ArrayList<>();
		for (Person parent : parents(book, person)) {
			out.addAll(parents(book, parent));
		}
		return out;
	}

	/** {@code person}'s grandchildren in the book, oldest first. */
	public static List<Person> grandchildren(Book book, Person person) {
		List<Person> out = new ArrayList<>();
		for (Person child : children(book, person.id())) {
			out.addAll(children(book, child.id()));
		}
		out.sort(Comparator.comparingLong(Person::born));
		return out.stream().distinct().toList();
	}

	/** Which generation of their family {@code person} is: 1 for a founder, one more than their parent of the same family. */
	public static int generation(Book book, Person person) {
		return generation(book, person, 0);
	}

	private static int generation(Book book, Person person, int depth) {
		int best = 1;
		if (depth < 64) {
			for (Person parent : parents(book, person)) {
				if (parent.family() == person.family()) {
					best = Math.max(best, generation(book, parent, depth + 1) + 1);
				}
			}
		}
		return best;
	}

	/** The founder of {@code person}'s line: up through the parents of the same family to the first generation. */
	public static Person founder(Book book, Person person) {
		Person at = person;
		for (int i = 0; i < 64; i++) {
			Person up = parents(book, at).stream().filter(p -> p.family() == person.family())
				.max(Comparator.comparingInt(p -> generation(book, p))).orElse(null);
			if (up == null) {
				return at;
			}
			at = up;
		}
		return at;
	}

	/** Everyone of family {@code family} in the book, by generation then age. */
	public static List<Person> members(Book book, int family) {
		return book.people().stream().filter(p -> p.family() == family)
			.sorted(Comparator.comparingInt((Person p) -> generation(book, p)).thenComparingLong(Person::born)).toList();
	}

	/** "the Ashby family"; with {@code familyNames} off, its founder's ("Bram's family"). */
	public static Component familyTitle(Book book, Person person) {
		return NAMES && person.family() > 0
			? Component.translatable("family.aliveworkplace.the_family", familyName(person.family()))
			: Component.translatable("family.aliveworkplace.founders_family", founder(book, person).name());
	}

	/** "The Ashby Family" on a book's cover; with {@code familyNames} off, "Bram's Family". */
	public static Component bookTitle(Book book, Person person) {
		return NAMES && person.family() > 0
			? Component.translatable("family.aliveworkplace.book_title", familyName(person.family()))
			: Component.translatable("family.aliveworkplace.book_title_founder", founder(book, person).name());
	}

	// --- The chronicle -----------------------------------------------------------------------------------------------

	/**
	 * The chronicle's line for {@code baby}, just born to {@code mother} and {@code father} in the village of {@code hall}:
	 * with the grandparents the book knows, "Mira was born to Dara and Tomas, grandchild of Bram Ashby and Ilse Ashby";
	 * else the old "A baby was born to Dara and Tomas". The three are written into the book.
	 */
	public static Component birthLine(ServerLevel level, BlockPos hall, Villager baby, Villager mother, Villager father) {
		Component plain = Component.translatable("chronicle.aliveworkplace.birth", mother.getDisplayName(), father.getDisplayName());
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return plain;
		}
		Book book = entity.families();
		long today = Chronicle.day(level);
		for (Villager v : List.of(mother, father, baby)) {
			giveFamily(level, book, v, List.of());
			book.put(entry(book, v, today));
		}
		entity.setChanged();
		List<Component> grand = new ArrayList<>();
		for (Person g : grandparents(book, book.get(baby.getUUID()))) {
			grand.add(fullName(g));
		}
		if (grand.isEmpty()) {
			return plain;
		}
		return baby.hasCustomName()
			? Component.translatable("chronicle.aliveworkplace.birth_grandchild", baby.getName(), mother.getDisplayName(), father.getDisplayName(), list(grand))
			: Component.translatable("chronicle.aliveworkplace.birth_grandchild_unnamed", mother.getDisplayName(), father.getDisplayName(), list(grand));
	}

	/** A baby who is the first of their family's third generation: the chronicle notes it, once a family. Whether it did. */
	public static boolean noteGeneration(ServerLevel level, BlockPos hall, Book book, Villager baby) {
		Person me = book.get(baby.getUUID());
		if (!ENABLED || me == null || me.family() <= 0 || book.noted(me.family()) || generation(book, me) < NOTED_GENERATION) {
			return false;
		}
		book.note(me.family());
		Component who = baby.hasCustomName() ? baby.getName() : Component.translatable("chronicle.aliveworkplace.family.a_baby");
		Chronicle.atHall(level, hall, Chronicle.Kind.LIFE, NAMES
			? Component.translatable("chronicle.aliveworkplace.family.third_generation", familyName(me.family()), who)
			: Component.translatable("chronicle.aliveworkplace.family.third_generation_founder", founder(book, me).name(), who));
		return true;
	}

	/** "Bram", "Bram and Ilse", "Bram, Ilse and Tomas". */
	public static Component list(List<Component> names) {
		if (names.isEmpty()) {
			return Component.empty();
		}
		if (names.size() == 1) {
			return names.get(0);
		}
		MutableComponent head = Component.empty();
		for (int i = 0; i < names.size() - 1; i++) {
			if (i > 0) {
				head.append(Component.literal(", "));
			}
			head.append(names.get(i));
		}
		return Component.translatable("heart_event.aliveworkplace.and", head, names.get(names.size() - 1));
	}

	private FamilyBook() {
	}
}
