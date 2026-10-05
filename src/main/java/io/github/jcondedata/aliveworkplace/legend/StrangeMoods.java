package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Strange moods and Masterworks, the fourth way Legends come (ROADMAP 29.10, after Dwarf Fortress). Once a day, in the
 * hall's round, a happy village ({@link LegendNeeds#happy}) with no mood already on, no raid and no festival, rolls: a
 * Master whose trade a Legend's {@code inspired} way names (the village meeting that Legend's conditions, its slot free)
 * is seized by a strange mood, by default one time in {@link #ONE_IN} (the way's {@code chance}). The villager
 * ({@code STRANGE_MOOD}):
 * <ul>
 *   <li>claims their workstation: their WORK activity is standing at it ({@link Picket}), and a purple line over their
 *       head says "Taken by a strange mood";</li>
 *   <li>asks for three materials from the Legend's {@code masterwork.materials}, one of each, in a chest by the
 *       workstation within {@link #DAYS} days; the chronicle, the requests board ({@link Requests}) and the village's
 *       players are told. Each one that turns up is put to the player who handed it over at a Storehouse board, else the
 *       nearest player by the chest;</li>
 *   <li><b>success</b>: they make the Masterwork ({@link #masterwork}): the file's item with a made-up name, lore naming
 *       the maker, the village, the day and the materials, and a glint. It goes to the player who brought the most
 *       (else into the chest), and the villager becomes the Legend ({@link Legends#make}, way {@code inspired}). A
 *       Masterwork in an item frame in the village is worth {@link #BEAUTY} beauty;</li>
 *   <li><b>failure</b>: {@link #SULK_DAYS} days of sulking ({@link #SULK_MOOD} mood, half pace, "Sulking" over their
 *       head), and no strange mood in that village for {@link #NO_MOOD_DAYS} days.</li>
 * </ul>
 * The Founder's mood ({@code "founder": true}, 29.23) never comes from the daily roll. {@code strangeMoods} in the config
 * switches it off: no mood starts, and a mood already on is called off at its next check.
 */
public final class StrangeMoods {
	public static boolean ENABLED = true;
	/** One time in this many a day, when a way names no {@code chance}. */
	public static final int ONE_IN = 8;
	/** Materials asked for, one of each. */
	public static final int MATERIALS = 3;
	/** Days the materials may take. */
	public static final int DAYS = 3;
	/** Days a failed mood sulks, and the mood (lost) and pace (times as slow) meanwhile. */
	public static final int SULK_DAYS = 7;
	public static final int SULK_MOOD = 30;
	public static final float SULK_PACE = 2f;
	/** Days without a strange mood in the village after one fails. */
	public static final int NO_MOOD_DAYS = 10;
	/** Beauty a Masterwork in an item frame in the village is worth. */
	public static final int BEAUTY = 3;
	/** The made-up words a Masterwork's name may take ({@code masterwork.aliveworkplace.word.<n>}). */
	public static final int WORDS = 16;
	/** The custom data flag that marks a Masterwork. */
	public static final String MARK = "aliveworkplace_masterwork";
	/** How near the chest a player is credited with what turns up in it. */
	public static final int CREDIT_RANGE = 8;

	/** A sulking villager works at half pace (30.2's pace rule, after the cap). */
	public static final Pace.Source SULKING = Pace.register(new Pace.Source("sulking", Pace.Kind.PENALTY, v -> sulking(v) ? SULK_PACE : 1f));

	/** A material handed over at a Storehouse board for a villager in a mood, waiting to be seen in their chest. */
	private record Credit(UUID player, Item item, int count) {
	}

	private static final Map<Villager, List<Credit>> HANDED = new WeakHashMap<>();

	static void init() {
		Requests.onGiven(StrangeMoods::handed);
	}

	/** A Legend's way to come by a strange mood, and the Masters of its trades in the village. */
	public record Candidate(Legend legend, float chance, List<Villager> masters) {
	}

	// --- Starting a mood -------------------------------------------------------------------------------------------

	/** The hall's round: once a Chronicle day, the roll. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED || !ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		long today = Chronicle.day(level);
		if (entity.moodRolledDay() >= today) {
			return;
		}
		entity.setMoodRolledDay(today);
		roll(level, hall, today, level.random);
	}

	/** Why the village round {@code hall} can have no strange mood on {@code today}, or null when it can roll. */
	@Nullable
	public static String blocked(ServerLevel level, BlockPos hall, long today) {
		if (!Legends.ENABLED || !ENABLED) {
			return "off";
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return "no hall";
		}
		if (entity.noMoodUntil() > today) {
			return "no mood till day " + entity.noMoodUntil();
		}
		if (VillageRaids.raided(level, hall, hall)) {
			return "a raid";
		}
		if (Festivals.isOn(level, hall)) {
			return "a festival";
		}
		if (moodIn(level, hall) != null) {
			return "a mood already on";
		}
		if (!LegendNeeds.happy(level, hall)) {
			return "not a happy village";
		}
		return null;
	}

	/**
	 * The day's roll for the village round {@code hall}: when nothing blocks it and some Legend may come, one of them is
	 * picked, then its chance thrown, then one of its Masters, all from {@code random} in that order (so a test can fix
	 * them). Returns the villager seized, if any.
	 */
	public static Optional<Villager> roll(ServerLevel level, BlockPos hall, long today, RandomSource random) {
		if (blocked(level, hall, today) != null) {
			return Optional.empty();
		}
		List<Candidate> open = candidates(level, hall);
		if (open.isEmpty()) {
			return Optional.empty();
		}
		Candidate pick = open.size() == 1 ? open.get(0) : open.get(random.nextInt(open.size()));
		if (random.nextFloat() >= pick.chance()) {
			return Optional.empty();
		}
		Villager who = pick.masters().size() == 1 ? pick.masters().get(0) : pick.masters().get(random.nextInt(pick.masters().size()));
		start(level, hall, who, pick.legend(), today, random);
		return Optional.of(who);
	}

	/**
	 * The Legends who may come to the village round {@code hall} by a strange mood: a Masterwork with an item and at least
	 * three materials, an {@code inspired} way naming trades (not the Founder's), every condition met, their slot free, and
	 * at least one Master of those trades in the village (grown up, at a workstation, not a Legend, not sulking).
	 */
	public static List<Candidate> candidates(ServerLevel level, BlockPos hall) {
		List<Candidate> out = new ArrayList<>();
		for (Legend legend : Legends.all()) {
			if (masterworkItem(legend) == Items.AIR || pool(legend).size() < MATERIALS) {
				continue;
			}
			for (JsonObject way : legend.ways("inspired")) {
				if (way.has("founder") && way.get("founder").getAsBoolean() || !way.has("trades")) {
					continue;
				}
				Set<ResourceLocation> trades = new HashSet<>();
				for (JsonElement e : way.getAsJsonArray("trades")) {
					ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
					if (id != null) {
						trades.add(id);
					}
				}
				boolean met = true;
				for (Condition c : legend.conditions()) {
					met &= c.met(level, hall);
				}
				if (!met || LegendSlots.whyNot(level, hall, legend, null).isPresent()) {
					break;
				}
				List<Villager> masters = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby()
					&& v.getVillagerData().getLevel() >= VillagerData.MAX_VILLAGER_LEVEL
					&& trades.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()))
					&& !ModAttachments.LEGEND.has(v) && !ModAttachments.STRANGE_MOOD.has(v) && Builders.benchPos(v).isPresent()
					&& VillageHalls.nearest(level, v.blockPosition()).equals(Optional.of(hall)));
				if (!masters.isEmpty()) {
					float chance = way.has("chance") ? way.get("chance").getAsFloat() : 1f / ONE_IN;
					masters.sort(java.util.Comparator.comparing(Villager::getUUID)); // the same order every time, for the dice
					out.add(new Candidate(legend, chance, List.copyOf(masters)));
					break;
				}
			}
		}
		return out;
	}

	/**
	 * {@code villager} is seized by a strange mood that leads to {@code legend}: three materials are picked from its pool
	 * with {@code random}, the workstation claimed, and the village told. Also how the Founder's mood (29.23) begins.
	 */
	public static StrangeMood start(ServerLevel level, BlockPos hall, Villager villager, Legend legend, long today, RandomSource random) {
		List<ResourceLocation> pool = new ArrayList<>(pool(legend));
		List<ResourceLocation> asked = new ArrayList<>();
		for (int i = 0; i < MATERIALS && !pool.isEmpty(); i++) {
			asked.add(pool.remove(random.nextInt(pool.size())));
		}
		BlockPos station = Builders.benchPos(villager).orElse(villager.blockPosition());
		StrangeMood mood = StrangeMood.begin(legend.id(), station, Optional.of(hall), asked, today);
		ModAttachments.STRANGE_MOOD.set(villager, mood);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Component wants = materials(mood);
		for (ServerPlayer player : LegendSlots.audience(level, hall, villager.blockPosition(), Rarity.RARE)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.strange_mood.start", villager.getDisplayName(), VillageHalls.name(level, hall),
				wants, DAYS).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.strange_mood.start",
			villager.getDisplayName(), wants));
		level.sendParticles(ParticleTypes.WITCH, villager.getX(), villager.getY() + 1.2, villager.getZ(), 20, 0.4, 0.6, 0.4, 0);
		level.playSound(null, villager.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 0.8f);
		return mood;
	}

	// --- While it lasts --------------------------------------------------------------------------------------------

	/** Every second of a villager's life: a mood's or a sulk's check (on the Chronicle day), and the line over their head. */
	static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || !ModAttachments.STRANGE_MOOD.has(villager)) {
			return;
		}
		check(level, villager, Chronicle.day(level));
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		if (mood != null && !villager.isSleeping()) {
			WorkerStatus.set(villager, overhead(mood), mood.inMood() ? found(level, mood) / (float) mood.materials().size() : -1f, line(level, mood, Chronicle.day(level)));
		}
	}

	/**
	 * Checks {@code villager}'s mood on {@code today}: a sulk over is forgotten; a mood whose Legend or switch is gone is
	 * called off; who brought what is counted; every material there makes the Masterwork; past the last day the mood
	 * fails. Returns the Masterwork when it was made now (else empty).
	 */
	public static ItemStack check(ServerLevel level, Villager villager, long today) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		if (mood == null) {
			return ItemStack.EMPTY;
		}
		if (!mood.inMood()) {
			if (today >= mood.sulkUntil()) {
				ModAttachments.STRANGE_MOOD.remove(villager);
				Moods.forget(villager);
			}
			return ItemStack.EMPTY;
		}
		Legend legend = Legends.get(mood.legend()).orElse(null);
		if (!ENABLED || legend == null) {
			ModAttachments.STRANGE_MOOD.remove(villager); // switched off, or its Legend's file is gone: called off quietly
			return ItemStack.EMPTY;
		}
		mood = count(level, villager, mood);
		if (found(level, mood) >= mood.materials().size()) {
			return succeed(level, villager, mood, legend, today);
		}
		if (today > mood.lastDay()) {
			fail(level, villager, mood, legend, today);
		}
		return ItemStack.EMPTY;
	}

	/** Counts what the chest holds now, putting each new material to whoever brought it. */
	static StrangeMood count(ServerLevel level, Villager villager, StrangeMood mood) {
		List<BlockPos> chests = SupplyContainers.find(level, mood.station(), null);
		Map<String, Integer> seen = new LinkedHashMap<>(mood.seen());
		Map<String, Integer> givers = new LinkedHashMap<>(mood.givers());
		for (ResourceLocation id : mood.materials()) {
			Item item = Lookup.value(BuiltInRegistries.ITEM, id);
			int have = (int) Math.min(64, SupplyContainers.count(level, chests, item));
			int before = seen.getOrDefault(id.toString(), 0);
			if (have > before) {
				UUID who = creditFor(level, villager, mood, item, have - before);
				if (who != null) {
					givers.merge(who.toString(), have - before, Integer::sum);
				}
			}
			seen.put(id.toString(), have);
		}
		StrangeMood now = mood.counted(seen, givers);
		if (!now.equals(mood)) {
			ModAttachments.STRANGE_MOOD.set(villager, now);
		}
		return now;
	}

	/** Who brought {@code count} of {@code item}: a hand-over at a Storehouse board for this villager, else the nearest player by the chest. */
	@Nullable
	private static UUID creditFor(ServerLevel level, Villager villager, StrangeMood mood, Item item, int count) {
		synchronized (HANDED) {
			List<Credit> credits = HANDED.get(villager);
			if (credits != null) {
				for (int i = 0; i < credits.size(); i++) {
					if (credits.get(i).item() == item) {
						return credits.remove(i).player();
					}
				}
			}
		}
		Player near = level.getNearestPlayer(mood.station().getX() + 0.5, mood.station().getY() + 0.5, mood.station().getZ() + 0.5, CREDIT_RANGE,
			p -> !p.isSpectator());
		return near == null ? null : near.getUUID();
	}

	/** A player handed something over at a Storehouse board: if it's for a villager in a mood, it's theirs when it turns up. */
	static void handed(ServerPlayer player, Requests.Request request, int count) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(request.worker());
		Item item = request.item();
		if (mood == null || !mood.inMood() || item == null) {
			return;
		}
		synchronized (HANDED) {
			HANDED.computeIfAbsent(request.worker(), v -> new ArrayList<>()).add(new Credit(player.getUUID(), item, count));
		}
	}

	/** How many of the asked-for materials the chest holds (each counted once). */
	public static int found(ServerLevel level, StrangeMood mood) {
		int n = 0;
		for (ResourceLocation id : mood.materials()) {
			n += mood.seen().getOrDefault(id.toString(), 0) > 0 ? 1 : 0;
		}
		return n;
	}

	/** What {@code worker}'s mood still wants, for the requests board and the hall: one of each material not in the chest. */
	public static List<Requests.Request> requests(ServerLevel level, Villager worker) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(worker);
		if (mood == null || !mood.inMood() || !ENABLED || !Legends.ENABLED) {
			return List.of();
		}
		List<Requests.Request> out = new ArrayList<>();
		List<BlockPos> chests = SupplyContainers.find(level, mood.station(), null);
		for (ResourceLocation id : mood.materials()) {
			Item item = Lookup.value(BuiltInRegistries.ITEM, id);
			if (item != Items.AIR && SupplyContainers.count(level, chests, item) <= 0) {
				out.add(Requests.forItem(worker, mood.station(), item, 1));
			}
		}
		return out;
	}

	// --- How it ends -----------------------------------------------------------------------------------------------

	/**
	 * Every material is there: they're taken from the chest, the Masterwork made and given to the player who brought the
	 * most (else put in the chest), and the villager becomes the Legend if its slot is still free.
	 */
	static ItemStack succeed(ServerLevel level, Villager villager, StrangeMood mood, Legend legend, long today) {
		List<BlockPos> chests = SupplyContainers.find(level, mood.station(), null);
		for (ResourceLocation id : mood.materials()) {
			Item item = Lookup.value(BuiltInRegistries.ITEM, id);
			SupplyContainers.takeOne(level, chests, s -> s.is(item));
		}
		BlockPos hall = mood.hall().filter(h -> level.getBlockEntity(h) instanceof VillageHallBlockEntity)
			.or(() -> VillageHalls.nearest(level, villager.blockPosition())).orElse(null);
		ItemStack work = masterwork(level, hall, villager, legend, mood, today);
		ItemStack record = work.copy();
		ServerPlayer to = topGiver(level, mood);
		boolean given = to != null && to.getInventory().add(work);
		if (!given) {
			to = null;
			ItemStack rest = SupplyContainers.insert(level, chests, work);
			if (!rest.isEmpty()) {
				BlockPos at = mood.station().above();
				level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, rest));
			}
		}
		ModAttachments.STRANGE_MOOD.remove(villager);
		Component name = record.getHoverName();
		Component told = to != null
			? Component.translatable("message.aliveworkplace.strange_mood.masterwork.to", villager.getDisplayName(), name, to.getDisplayName())
			: Component.translatable("message.aliveworkplace.strange_mood.masterwork.chest", villager.getDisplayName(), name);
		for (ServerPlayer player : LegendSlots.audience(level, hall, villager.blockPosition(), Rarity.RARE)) {
			Chat.chat(player, told.copy().withStyle(ChatFormatting.GOLD));
		}
		if (to != null && !LegendSlots.audience(level, hall, villager.blockPosition(), Rarity.RARE).contains(to)) {
			Chat.chat(to, told.copy().withStyle(ChatFormatting.GOLD));
		}
		if (hall != null) {
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.strange_mood.masterwork",
				villager.getDisplayName(), name));
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.2, villager.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
		level.playSound(null, villager.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1f);
		if (LegendSlots.whyNot(level, hall, legend, villager.getUUID()).isEmpty()) {
			Legends.make(level, villager, legend, "inspired");
			if (Founder.isFounder(legend)) {
				Founder.made(level, hall); // the Founder's mood never comes again (29.23)
			}
		}
		return record;
	}

	/** The online player who brought the most of the materials (the first on a tie), or null. */
	@Nullable
	static ServerPlayer topGiver(ServerLevel level, StrangeMood mood) {
		String best = null;
		int most = 0;
		for (Map.Entry<String, Integer> e : mood.givers().entrySet()) {
			if (e.getValue() > most) {
				best = e.getKey();
				most = e.getValue();
			}
		}
		if (best == null) {
			return null;
		}
		try {
			return level.getServer().getPlayerList().getPlayer(UUID.fromString(best));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The last day passed without every material: a week of sulking, and no strange mood in the village for ten days. */
	static void fail(ServerLevel level, Villager villager, StrangeMood mood, Legend legend, long today) {
		ModAttachments.STRANGE_MOOD.set(villager, mood.sulkingUntil(today + SULK_DAYS));
		Moods.forget(villager);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		BlockPos hall = mood.hall().filter(h -> level.getBlockEntity(h) instanceof VillageHallBlockEntity)
			.or(() -> VillageHalls.nearest(level, villager.blockPosition())).orElse(null);
		if (hall != null && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setNoMoodUntil(today + NO_MOOD_DAYS);
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.strange_mood.failed",
				villager.getDisplayName()));
		}
		for (ServerPlayer player : LegendSlots.audience(level, hall, villager.blockPosition(), Rarity.RARE)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.strange_mood.failed", villager.getDisplayName(), SULK_DAYS)
				.withStyle(ChatFormatting.GRAY));
		}
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.4, villager.getZ(), 6, 0.3, 0.3, 0.3, 0);
	}

	// --- The Masterwork --------------------------------------------------------------------------------------------

	/** The Masterwork's item from {@code legend}'s file (air when none or unknown). */
	public static Item masterworkItem(Legend legend) {
		JsonObject mw = legend.masterwork();
		if (mw == null || !mw.has("item")) {
			return Items.AIR;
		}
		ResourceLocation id = ResourceLocation.tryParse(mw.get("item").getAsString());
		return id == null ? Items.AIR : Lookup.value(BuiltInRegistries.ITEM, id);
	}

	/** The materials pool of {@code legend}'s Masterwork: known items only, each once. */
	public static List<ResourceLocation> pool(Legend legend) {
		JsonObject mw = legend.masterwork();
		List<ResourceLocation> out = new ArrayList<>();
		if (mw == null || !mw.has("materials")) {
			return out;
		}
		for (JsonElement e : mw.getAsJsonArray("materials")) {
			ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
			if (id != null && !out.contains(id) && Lookup.value(BuiltInRegistries.ITEM, id) != Items.AIR) {
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * The Masterwork {@code maker} made for {@code legend}: the file's item, named by the file's {@code name} key (or
	 * "The &lt;word&gt; &lt;item&gt;") with the village, the maker, a made-up word and the item's name as arguments; lore
	 * naming the maker and the village, the day and the materials; a glint and the {@link #MARK}. The word is picked from
	 * the maker and the day, so the same mood always makes the same name.
	 */
	public static ItemStack masterwork(ServerLevel level, @Nullable BlockPos hall, Villager maker, Legend legend, StrangeMood mood, long day) {
		if (Founder.isFounder(legend)) {
			return Founder.charter(level, hall, maker, legend, day); // the Charter of the village (29.23)
		}
		Item item = masterworkItem(legend);
		ItemStack stack = new ItemStack(item == Items.AIR ? Items.NETHER_STAR : item);
		JsonObject mw = legend.masterwork();
		String key = mw != null && mw.has("name") ? mw.get("name").getAsString() : "masterwork.aliveworkplace.name";
		Component village = Component.literal(hall != null ? VillageHalls.name(level, hall).getString()
			: Component.translatable("message.aliveworkplace.legend.the_wilds").getString());
		Component who = Component.literal(maker.getName().getString());
		RandomSource words = RandomSource.create(maker.getUUID().getLeastSignificantBits() ^ day * 31L);
		Component word = Component.translatable("masterwork.aliveworkplace.word." + words.nextInt(WORDS));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key, village, who, word, stack.getItem().getDescription())
			.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		List<Component> mats = new ArrayList<>();
		for (ResourceLocation id : mood.materials()) {
			mats.add(Lookup.value(BuiltInRegistries.ITEM, id).getDescription());
		}
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			lore(Component.translatable("masterwork.aliveworkplace.lore.maker", who, village)),
			lore(Component.translatable("masterwork.aliveworkplace.lore.day", day)),
			lore(Component.translatable("masterwork.aliveworkplace.lore.materials", join(mats))))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		tag.putString("legend", legend.id().toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static Component lore(MutableComponent line) {
		return line.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY));
	}

	/** Whether {@code stack} is a Masterwork. */
	public static boolean isMasterwork(ItemStack stack) {
		return !stack.isEmpty() && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(MARK);
	}

	/** The beauty of the Masterworks in item frames in the village round {@code hall}. */
	public static int beauty(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return 0;
		}
		return BEAUTY * level.getEntitiesOfClass(ItemFrame.class, VillageHalls.area(hall), f -> isMasterwork(f.getItem())).size();
	}

	// --- Sulking, the line over their head, the words ---------------------------------------------------------------

	/** Whether {@code villager} is sulking after a failed strange mood (today). */
	public static boolean sulking(Villager villager) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		return mood != null && villager.level() instanceof ServerLevel level && mood.sulking(Chronicle.day(level));
	}

	/** Whether {@code villager} is in a strange mood (their workstation claimed). */
	public static boolean claiming(Villager villager) {
		if (!ENABLED || !Legends.ENABLED) {
			return false;
		}
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		return mood != null && mood.inMood();
	}

	/** The workstation a villager in a strange mood has claimed, or null. */
	@Nullable
	public static BlockPos station(Villager villager) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		return mood != null && mood.inMood() ? mood.station() : null;
	}

	/** The mood a sulk costs while it lasts, as a mood reason (null when not sulking). */
	@Nullable
	public static LegendPowers.MoodReason sulkMood(ServerLevel level, Villager villager) {
		StrangeMood mood = ModAttachments.STRANGE_MOOD.get(villager);
		if (mood == null || !mood.sulking(Chronicle.day(level))) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.sulking"), -SULK_MOOD);
	}

	/** "Taken by a strange mood" in purple, or "Sulking" in grey: the line over their head. */
	public static Component overhead(StrangeMood mood) {
		return mood.inMood() ? Component.translatable("legend.aliveworkplace.overhead.strange_mood").withStyle(ChatFormatting.LIGHT_PURPLE)
			: Component.translatable("legend.aliveworkplace.overhead.sulking").withStyle(ChatFormatting.GRAY);
	}

	/** The second line: what the mood still wants and how many days are left, or the days of sulking left. */
	public static Component line(ServerLevel level, StrangeMood mood, long today) {
		if (!mood.inMood()) {
			return Component.translatable("legend.aliveworkplace.overhead.sulk_days", Math.max(1, mood.sulkUntil() - today)).withStyle(ChatFormatting.DARK_GRAY);
		}
		return Component.translatable("legend.aliveworkplace.overhead.mood_wants", materials(mood), Math.max(1, mood.lastDay() - today + 1))
			.withStyle(ChatFormatting.GRAY);
	}

	/** "a Diamond, a Blaze Rod and an Echo Shard": the materials, by their names. */
	public static Component materials(StrangeMood mood) {
		List<Component> names = new ArrayList<>();
		for (ResourceLocation id : mood.materials()) {
			names.add(Lookup.value(BuiltInRegistries.ITEM, id).getDescription());
		}
		return join(names);
	}

	static Component join(List<Component> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(Component.translatable(i == parts.size() - 1 ? "legend.aliveworkplace.and" : "legend.aliveworkplace.comma"));
			}
			out.append(parts.get(i));
		}
		return out;
	}

	/** The villager in a strange mood in the village round {@code hall}, or null. */
	@Nullable
	public static Villager moodIn(ServerLevel level, BlockPos hall) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && ModAttachments.STRANGE_MOOD.has(v))) {
			StrangeMood mood = ModAttachments.STRANGE_MOOD.get(v);
			if (mood != null && mood.inMood() && (mood.hall().isEmpty() || mood.hall().get().equals(hall))) {
				return v;
			}
		}
		return null;
	}

	private StrangeMoods() {
	}
}
