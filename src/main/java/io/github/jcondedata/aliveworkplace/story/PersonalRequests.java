package io.github.jcondedata.aliveworkplace.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Personal requests (ROADMAP 31.9): a villager with {@link #MIN_HEARTS} hearts or more with a player nearby, and no
 * request open, may ask that player for help. Each morning the village rolls once ({@link #CHANCE}); on a hit, one such
 * villager is picked the first time that day one of them has a friend within {@link #NEAR} blocks. They walk up, ask
 * over their head, and the player's chat gets the request with [I'll help] and [Not now]
 * ({@code /workplace quest accept|decline <id>}). An offer nobody answers lapses with the day and costs nothing.
 *
 * <p>Accepted, it is an open quest of the village with the giver {@code villager} ({@link Quest#villager}): it shows in
 * the journal's Personal tab, on the villager's life-story page and on the hall's tooltip for them. Whoever accepted is
 * a helper; other players with {@link #MIN_HEARTS} hearts join from the Personal tab, by [I'll help] or by moving it on.
 * Done, every helper gets the reward and {@link Friendship.Favour#REQUEST} friendship, and the chronicle a line. A
 * deadline is a number of days or "before the next festival" ({@link Festivals#nextDay}); a missed one costs every
 * helper {@link #MISSED_COST} friendship, leaves the villager glum for a day ({@link #glum}) and they don't ask again
 * for {@link #QUIET_DAYS} days.
 *
 * <p>The requests are quest files with the giver {@code villager} ({@code data/<ns>/quests/personal/}), offered when
 * their conditions hold for the villager and the player ({@link io.github.jcondedata.aliveworkplace.rules.GiverConditions})
 * and their objectives can be asked of that villager. Objectives that are about the villager
 * ({@link Objectives.Looked}) are looked at in the hall's round. Config {@code personalRequests} off: nobody asks and
 * offers lapse; requests already accepted stay and can be finished (or missed).
 */
public final class PersonalRequests {
	/** Config {@code personalRequests}. */
	public static boolean ENABLED = true;
	/** Off in gametests (a villager near a test's player would walk up); the request tests tick it themselves. */
	public static boolean AUTO = System.getProperty("fabric-api.gametest") == null;
	public static final int MIN_HEARTS = 3;
	/** The chance, each morning, that someone in the village asks. */
	public static final float CHANCE = 0.25f;
	/** How near (blocks) a friend has to be for a villager to be picked. */
	public static final double NEAR = 24;
	/** How near the villager walks before asking, and how long (ticks) they try before asking from where they stand. */
	public static final double TALK_RANGE = 3.5;
	public static final int WALK_UP = 200;
	public static final int CHECK_EVERY = 10;
	/** What a missed deadline costs each helper, and how many days the villager then asks nobody. */
	public static final int MISSED_COST = 30;
	public static final int QUIET_DAYS = 3;
	/** How much a missed request weighs on their mood, for a day. */
	public static final int GLUM = 10;
	/** "Before the next festival" means at least this many days away (else the one after). */
	public static final int MIN_FESTIVAL_DAYS = 2;

	/** What a missed request left on a villager: no asking up to and including {@code quietDay}, glum until the game time {@code glumUntil}. */
	public record LetDown(long quietDay, long glumUntil) {
		public static final Codec<LetDown> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("quiet_day", -1L).forGetter(LetDown::quietDay),
			Codec.LONG.optionalFieldOf("glum_until", -1L).forGetter(LetDown::glumUntil)
		).apply(i, LetDown::new));
	}

	/** A request asked and not answered yet (not saved: it lapses with the day, or with a restart). */
	public static final class Offer {
		public final Quest quest;
		public final ServerLevel level;
		public final BlockPos hall;
		public final UUID villager;
		public final UUID player;
		public final long day;
		public final long started;
		/** Whether they've said it (they walk up first). */
		public boolean asked;

		Offer(Quest quest, ServerLevel level, BlockPos hall, UUID villager, UUID player, long day, long started) {
			this.quest = quest;
			this.level = level;
			this.hall = hall;
			this.villager = villager;
			this.player = player;
			this.day = day;
			this.started = started;
		}
	}

	/** Quest id → its offer. */
	private static final Map<UUID, Offer> OFFERS = new LinkedHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (AUTO && level.getGameTime() % CHECK_EVERY == 0) {
				tick(level, level.getGameTime());
			}
		});
		LifeStory.section(PersonalRequests::lifeStorySection);
	}

	/** Whether villagers ask: the switch, friendship (requests go by hearts) and milestone 31's gate. */
	public static boolean active() {
		return ENABLED && Friendship.ENABLED && Expansions.on(Expansions.M31);
	}

	// --- Reading --------------------------------------------------------------------------------------------------

	/** The open (accepted) request of {@code villager} in the village round {@code hall}, or null. */
	@Nullable
	public static Quest of(ServerLevel level, BlockPos hall, Villager villager) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e != null) {
			for (Quest q : e.quests) {
				if (q.personal() && villager.getUUID().equals(q.villager)) {
					return q;
				}
			}
		}
		return null;
	}

	/** The open request of {@code villager} whichever village holds it, or null. */
	@Nullable
	public static Quest of(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		for (BlockPos hall : Stories.halls(level)) {
			Quest q = of(level, hall, villager);
			if (q != null) {
				return q;
			}
		}
		return null;
	}

	/** The open personal requests of the village round {@code hall}. */
	public static List<Quest> open(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.personal() && !q.done()).toList();
	}

	/** The request {@code villager} has asked and nobody has answered yet, or null. */
	@Nullable
	public static Offer offer(Villager villager) {
		synchronized (OFFERS) {
			return OFFERS.values().stream().filter(o -> o.villager.equals(villager.getUUID())).findFirst().orElse(null);
		}
	}

	/** The offer of the quest {@code id}, or null. */
	@Nullable
	public static Offer offer(UUID id) {
		synchronized (OFFERS) {
			return OFFERS.get(id);
		}
	}

	/** Forgets every unanswered offer (tests; a restart does the same). */
	public static void forget() {
		synchronized (OFFERS) {
			OFFERS.clear();
		}
	}

	/** The player {@code id} if they're in the game (the server's list, else the level's own: a test's mock player). */
	@Nullable
	private static ServerPlayer online(ServerLevel level, UUID id) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
		return player != null ? player : level.players().stream().filter(p -> p.getUUID().equals(id)).findFirst().orElse(null);
	}

	/** The villager who asked for {@code quest}, if they're loaded. */
	@Nullable
	public static Villager giver(ServerLevel level, Quest quest) {
		return quest.villager != null && level.getEntity(quest.villager) instanceof Villager v && v.isAlive() ? v : null;
	}

	/** Whether {@code player} helps with {@code quest} (accepted it, joined it or moved it on). */
	public static boolean helps(Quest quest, UUID player) {
		return quest.helpers.containsKey(player);
	}

	/** Whether {@code player} may help with {@code quest}: a helper already, or {@link #MIN_HEARTS} hearts with the one who asked. */
	public static boolean mayHelp(ServerLevel level, Quest quest, ServerPlayer player) {
		if (helps(quest, player.getUUID())) {
			return true;
		}
		Villager giver = giver(level, quest);
		return giver != null && Friendship.hearts(Friendship.points(giver, player.getUUID())) >= MIN_HEARTS;
	}

	/** Whether {@code villager} asks nobody today after a missed request. */
	public static boolean quiet(Villager villager, long today) {
		LetDown state = ModAttachments.REQUEST_LET_DOWN.get(villager);
		return state != null && today <= state.quietDay();
	}

	/** Whether {@code villager} is glum over a request nobody helped with in time (a mood reason, for a day). */
	public static boolean glum(ServerLevel level, Villager villager) {
		LetDown state = ModAttachments.REQUEST_LET_DOWN.get(villager);
		return state != null && level.getGameTime() < state.glumUntil();
	}

	// --- The texts ------------------------------------------------------------------------------------------------

	/** What the villager's lines get: {@code %1$s} their name, {@code %2$s} the player, {@code %3$s} what they ask for. */
	private static Object[] args(Quest quest, Component player) {
		return new Object[] {Component.literal(quest.poster), player, quest.objectives.get(0).what()};
	}

	private static Component text(Quest quest, String part, Component player) {
		return Component.translatable((quest.text == null ? "request.aliveworkplace.unknown" : quest.text) + "." + part, args(quest, player));
	}

	/** How many days {@code quest} has left (at least 1). */
	public static long daysLeft(ServerLevel level, Quest quest) {
		return Math.max(1, (quest.due - level.getGameTime() + VillageNeeds.DAY - 1) / VillageNeeds.DAY);
	}

	/** "Dara wants to make Journeyman before the next festival", "Odo wants a taste of home: 1 Pumpkin Pie within 3 days". */
	public static Component wants(ServerLevel level, Quest quest) {
		Component wants = text(quest, "wants", Component.empty());
		if (quest.due < 0) {
			return wants;
		}
		long days = daysLeft(level, quest);
		return quest.festival ? Component.translatable("request.aliveworkplace.before_festival", wants)
			: days <= 1 ? Component.translatable("request.aliveworkplace.within_day", wants)
			: Component.translatable("request.aliveworkplace.within_days", wants, days);
	}

	/** The hall tooltip's line for {@code villager}: what they want, while a request of theirs is open. */
	@Nullable
	public static Component hallLine(ServerLevel level, BlockPos hall, Villager villager) {
		Quest quest = of(level, hall, villager);
		return quest == null ? null : wants(level, quest);
	}

	/** The names of {@code quest}'s helpers, as the villager knows them. */
	public static List<String> helperNames(ServerLevel level, Quest quest) {
		Villager giver = giver(level, quest);
		List<String> names = new ArrayList<>();
		for (UUID id : quest.helpers.keySet()) {
			ServerPlayer online = online(level, id);
			String name = online != null ? online.getGameProfile().getName() : giver == null ? "" : Friendship.of(giver).bond(id).name();
			if (!name.isEmpty() && !names.contains(name)) {
				names.add(name);
			}
		}
		return names;
	}

	/** The life-story page's section: what they want, how far it has got and who helps. */
	@Nullable
	private static net.minecraft.world.item.ItemStack lifeStorySection(ServerLevel level, BlockPos hall, Villager villager, ServerPlayer viewer) {
		Quest quest = of(level, hall, villager);
		if (quest == null) {
			return null;
		}
		List<Component> lore = new ArrayList<>();
		int i = Math.max(0, quest.current());
		Objectives.Objective o = quest.objectives.get(i);
		lore.add(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.line(
			Component.translatable("screen.aliveworkplace.journal.objective", o.line(), quest.progress[i], o.need()), ChatFormatting.WHITE));
		List<String> names = helperNames(level, quest);
		if (!names.isEmpty()) {
			lore.add(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.line(
				Component.translatable("screen.aliveworkplace.request.helpers", String.join(", ", names)), ChatFormatting.GRAY));
		}
		lore.add(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.line(helps(quest, viewer.getUUID()) ? "screen.aliveworkplace.request.helping"
			: mayHelp(level, quest, viewer) ? "screen.aliveworkplace.request.can_help" : "screen.aliveworkplace.request.need_hearts", ChatFormatting.DARK_GRAY));
		return io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.icon(net.minecraft.world.item.Items.WRITABLE_BOOK, wants(level, quest).copy(),
			ChatFormatting.YELLOW, lore.toArray(Component[]::new));
	}

	// --- The morning ----------------------------------------------------------------------------------------------

	/**
	 * The hall's round: overdue requests are missed, the objectives about a villager are looked at, and the morning's
	 * roll is made, once a day; on a hit, someone asks the first time today a villager has a friend near.
	 */
	static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Stories.Entry e) {
		long today = Chronicle.day(level);
		boolean dawn = e.requestDawn < today;
		if (dawn) {
			e.requestDawn = today;
			Stories.Data.get(level).setDirty();
		}
		look(level, hall, entity, e, dawn);
		synchronized (OFFERS) {
			OFFERS.values().removeIf(o -> o.level == level && o.hall.equals(hall) && (o.day < today || !active()));
		}
		if (!active()) {
			return;
		}
		if (e.requestDay < today) {
			e.requestDay = today;
			e.requestPending = rolls(level.random);
			Stories.Data.get(level).setDirty();
		}
		if (e.requestPending && pick(level, hall, entity, level.random, today) != null) {
			e.requestPending = false;
			Stories.Data.get(level).setDirty();
		}
	}

	/** The morning's roll: whether someone in the village asks today. */
	public static boolean rolls(RandomSource random) {
		return random.nextFloat() < CHANCE;
	}

	/** Looks at the open requests' objectives that are about their villager ({@link Objectives.Looked}). */
	public static void look(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, boolean dawn) {
		look(level, hall, entity, Stories.Data.get(level).entry(hall), dawn);
	}

	private static void look(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Stories.Entry e, boolean dawn) {
		for (Quest q : List.copyOf(e.quests)) {
			int i = q.current();
			Villager giver = q.personal() ? giver(level, q) : null;
			if (giver != null && i >= 0 && q.objectives.get(i) instanceof Objectives.Looked looked) {
				int now = Math.min(looked.need(), looked.look(level, hall, giver, q, dawn));
				if (now > q.progress[i]) {
					Stories.progress(level, hall, entity, q, i, now - q.progress[i], null);
				}
			}
		}
	}

	/** A villager's friend near enough to be asked: the player within {@link #NEAR} blocks with the most hearts ({@link #MIN_HEARTS} or more), or null. */
	@Nullable
	static ServerPlayer friendNear(ServerLevel level, Villager villager) {
		ServerPlayer best = null;
		int bestPoints = MIN_HEARTS * Friendship.PER_HEART - 1;
		for (ServerPlayer p : level.players()) {
			int points = Friendship.points(villager, p.getUUID());
			if (p.isAlive() && !p.isSpectator() && points > bestPoints && p.distanceToSqr(villager) <= NEAR * NEAR) {
				best = p;
				bestPoints = points;
			}
		}
		return best;
	}

	/** Whether {@code villager} could ask someone today: a named adult of a village, awake, with no request open or asked, and not let down lately. */
	public static boolean mayAsk(ServerLevel level, BlockPos hall, Villager villager, long today) {
		return Friendship.eligible(villager) && !villager.isBaby() && !villager.isSleeping() && of(villager) == null && offer(villager) == null
			&& !quiet(villager, today) && HeartEvents.telling(villager) == null;
	}

	/**
	 * Picks who asks: of the village's villagers who may ask and have a friend near, one at random that has something to
	 * ask for, and makes their offer (they then walk up, {@link #tick}). Null when nobody can.
	 */
	@Nullable
	public static Offer pick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, RandomSource random, long today) {
		if (!active()) {
			return null;
		}
		List<Villager> askers = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> mayAsk(level, hall, v, today) && friendNear(level, v) != null));
		askers.sort(Comparator.comparing(Villager::getUUID)); // the same order every time, so a fixed random picks the same villager
		while (!askers.isEmpty()) {
			Villager villager = askers.remove(random.nextInt(askers.size()));
			ServerPlayer player = friendNear(level, villager);
			Quest quest = player == null ? null : make(level, hall, entity, villager, player, random);
			if (quest != null) {
				return propose(level, hall, villager, player, quest, today);
			}
		}
		return null;
	}

	/** {@code villager} is to ask {@code player} for {@code quest} (from {@link #make} or {@link #resolve}): they walk up and ask ({@link #tick}). */
	public static Offer propose(ServerLevel level, BlockPos hall, Villager villager, ServerPlayer player, Quest quest, long today) {
		Offer offer = new Offer(quest, level, hall.immutable(), villager.getUUID(), player.getUUID(), today, level.getGameTime());
		synchronized (OFFERS) {
			OFFERS.values().removeIf(o -> o.villager.equals(villager.getUUID())); // one request at a time
			OFFERS.put(quest.id, offer);
		}
		return offer;
	}

	/**
	 * What {@code villager} would ask {@code player} for now (not offered): of the {@code villager} quest files whose
	 * conditions hold for the two of them and whose objectives can be asked of this villager, the highest priority, then
	 * one by weight. Null when there's none.
	 */
	@Nullable
	public static Quest make(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Villager villager, ServerPlayer player, RandomSource random) {
		TreeMap<Integer, List<QuestFiles.QuestFile>> byPriority = new TreeMap<>(Comparator.reverseOrder());
		for (QuestFiles.QuestFile f : QuestFiles.all()) {
			if (f.giver().equals("villager") && f.weight() > 0) {
				byPriority.computeIfAbsent(f.priority(), k -> new ArrayList<>()).add(f);
			}
		}
		VillageHalls.Census census = VillageHalls.census(level, hall);
		for (List<QuestFiles.QuestFile> group : byPriority.values()) {
			List<Quest> can = new ArrayList<>();
			List<Integer> weights = new ArrayList<>();
			int total = 0;
			for (QuestFiles.QuestFile f : group) {
				Quest q = resolve(level, hall, entity, census, f, villager, player, random);
				if (q != null) {
					can.add(q);
					weights.add(f.weight());
					total += f.weight();
				}
			}
			if (total > 0) {
				int roll = random.nextInt(total);
				for (int i = 0; i < can.size(); i++) {
					roll -= weights.get(i);
					if (roll < 0) {
						return can.get(i);
					}
				}
			}
		}
		return null;
	}

	/** {@code file} as {@code villager}'s request to {@code player} now, or null when it isn't theirs to ask. */
	@Nullable
	public static Quest resolve(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageHalls.Census census, QuestFiles.QuestFile file,
								Villager villager, ServerPlayer player, RandomSource random) {
		if (!file.giver().equals("villager")) {
			return null;
		}
		for (Condition c : file.conditions()) {
			if (!c.met(level, hall, villager, player)) {
				return null;
			}
		}
		Objectives.Context context = new Objectives.Context(level, hall, census, random, villager.getDisplayName().getString());
		context.giver = villager;
		List<Objectives.Objective> objectives = new ArrayList<>();
		for (Objectives.Objective o : file.objectives()) {
			Objectives.Objective r = o.resolve(context);
			if (r == null) {
				return null;
			}
			objectives.add(r);
		}
		List<Rewards.Reward> rewards = file.rewards().stream().map(r -> r.resolve(objectives, 1f)).toList();
		long now = level.getGameTime();
		long due = now + file.days() * VillageNeeds.DAY;
		boolean festival = false;
		if (file.festival() && Festivals.ENABLED) {
			long today = Chronicle.day(level);
			long day = Festivals.nextDay(level, hall, entity);
			if (day - today < MIN_FESTIVAL_DAYS) {
				day += Festivals.every(entity);
			}
			due = now + (day - today) * VillageNeeds.DAY - level.getDayTime() % VillageNeeds.DAY; // the festival's morning
			festival = true;
		}
		Quest quest = new Quest(UUID.randomUUID(), file.id(), file.giver(), file.name(), villager.getDisplayName().getString(), now, due, objectives,
			new int[objectives.size()], rewards);
		Quest located = Stories.locate(level, hall, quest);
		if (located == null) {
			return null;
		}
		located.villager = villager.getUUID();
		located.text = file.text();
		located.festival = festival;
		return located;
	}

	// --- The asking -----------------------------------------------------------------------------------------------

	/** One look at {@code level}'s offers at game time {@code now}: whoever is to ask walks up and, once near, asks. */
	public static void tick(ServerLevel level, long now) {
		List<Offer> offers;
		synchronized (OFFERS) {
			offers = OFFERS.values().stream().filter(o -> o.level == level).toList();
		}
		for (Offer offer : offers) {
			ServerPlayer player = online(level, offer.player);
			if (!(level.getEntity(offer.villager) instanceof Villager villager) || !villager.isAlive() || !active() || player == null || !player.isAlive()
				|| player.level() != level) {
				withdraw(offer);
				continue;
			}
			if (offer.asked) {
				continue;
			}
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
			boolean far = villager.distanceToSqr(player) > TALK_RANGE * TALK_RANGE;
			if (far && now - offer.started < WALK_UP) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6f, 2));
				continue;
			}
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			ask(level, villager, player, offer);
		}
	}

	private static void withdraw(Offer offer) {
		synchronized (OFFERS) {
			OFFERS.remove(offer.quest.id);
		}
	}

	/** They ask: the line over their head and in the player's chat, then what they want with [I'll help] and [Not now]. */
	private static void ask(ServerLevel level, Villager villager, ServerPlayer player, Offer offer) {
		offer.asked = true;
		Component line = text(offer.quest, "ask", Component.literal(player.getGameProfile().getName()));
		say(villager, line);
		Chat.chat(player, HeartEvents.said(villager.getDisplayName(), line));
		Chat.chat(player, offerLine(level, offer.quest));
	}

	/** A line over {@code villager}'s head, in italics, with their voice. */
	private static void say(Villager villager, Component line) {
		WorkerStatus.set(villager, villager.getDisplayName().copy().withStyle(ChatFormatting.GRAY), -1f, line.copy().withStyle(ChatFormatting.ITALIC));
		villager.playSound(SoundEvents.VILLAGER_AMBIENT, 0.6f, villager.getVoicePitch());
	}

	/** "Dara wants to make Journeyman before the next festival. [I'll help] [Not now]". */
	public static Component offerLine(ServerLevel level, Quest quest) {
		return Component.translatable("message.aliveworkplace.request.offer", wants(level, quest), reward(quest),
			button("accept", quest.id, ChatFormatting.GREEN), button("decline", quest.id, ChatFormatting.GRAY)).withStyle(ChatFormatting.YELLOW);
	}

	/** What a request pays, in a few words: "6 emeralds and their friendship", or "their friendship". */
	public static Component reward(Quest quest) {
		int emeralds = quest.emeralds();
		return emeralds > 0 ? Component.translatable("message.aliveworkplace.request.reward", emeralds)
			: Component.translatable("message.aliveworkplace.request.reward_friendship");
	}

	private static MutableComponent button(String action, UUID id, ChatFormatting colour) {
		return Component.translatable("message.aliveworkplace.request." + action).withStyle(s -> s.withColor(colour)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/workplace quest " + action + " " + id))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.request." + action + "_hover"))));
	}

	// --- Answering ------------------------------------------------------------------------------------------------

	/**
	 * {@code /workplace quest accept <id>}, [I'll help]: {@code player} takes the request they were asked (it opens), or
	 * joins one already open if they have the hearts. Returns whether they help now.
	 */
	public static boolean accept(ServerPlayer player, UUID id) {
		ServerLevel level = Players.level(player);
		Offer offer = offer(id);
		if (offer != null) {
			if (!offer.player.equals(player.getUUID()) || offer.level != level) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.request.not_yours").withStyle(ChatFormatting.GRAY));
				return false;
			}
			withdraw(offer);
			if (!(level.getEntity(offer.villager) instanceof Villager villager) || !villager.isAlive() || of(villager) != null
				|| !(level.getBlockEntity(offer.hall) instanceof VillageHallBlockEntity)) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.request.gone").withStyle(ChatFormatting.GRAY));
				return false;
			}
			Quest quest = offer.quest;
			quest.helpers.put(player.getUUID(), 0);
			Stories.post(level, offer.hall, quest);
			Component glad = Component.translatable("message.aliveworkplace.request.glad", player.getGameProfile().getName());
			say(villager, glad);
			Chat.chat(player, HeartEvents.said(villager.getDisplayName(), glad));
			Chat.chat(player, Component.translatable("message.aliveworkplace.request.accepted", quest.title(), VillageHalls.name(level, offer.hall))
				.withStyle(ChatFormatting.GREEN));
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1f);
			return true;
		}
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest q : open(level, hall)) {
				if (q.id.equals(id)) {
					return join(player, level, q);
				}
			}
		}
		Chat.chat(player, Component.translatable("message.aliveworkplace.request.gone").withStyle(ChatFormatting.GRAY));
		return false;
	}

	/** {@code player} joins the open request {@code quest} as a helper, if they have {@link #MIN_HEARTS} hearts with who asked. */
	public static boolean join(ServerPlayer player, ServerLevel level, Quest quest) {
		if (helps(quest, player.getUUID())) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.request.already", quest.poster).withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!mayHelp(level, quest, player)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.request.need_hearts", MIN_HEARTS, quest.poster).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		quest.helpers.put(player.getUUID(), 0);
		Stories.Data.get(level).setDirty();
		Chat.chat(player, Component.translatable("message.aliveworkplace.request.joined", quest.poster, quest.title()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** {@code /workplace quest decline <id>}, [Not now]: the offer is withdrawn, at no cost. Returns whether there was one to decline. */
	public static boolean decline(ServerPlayer player, UUID id) {
		Offer offer = offer(id);
		if (offer == null || !offer.player.equals(player.getUUID())) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.request.gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		withdraw(offer);
		if (offer.level.getEntity(offer.villager) instanceof Villager villager && villager.isAlive()) {
			Component line = Component.translatable("message.aliveworkplace.request.another_time", player.getGameProfile().getName());
			say(villager, line);
			Chat.chat(player, HeartEvents.said(villager.getDisplayName(), line));
		}
		return true;
	}

	// --- Ending ---------------------------------------------------------------------------------------------------

	/**
	 * {@code quest} is done (it has come down already): every helper gets what it pays and the friendship, the villager
	 * thanks them over their head, and the chronicle gets a line.
	 */
	static void done(ServerLevel level, BlockPos hall, Quest quest) {
		Villager giver = giver(level, quest);
		List<String> names = helperNames(level, quest);
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID id : quest.helpers.keySet()) {
			ServerPlayer helper = online(level, id);
			if (helper != null) {
				online.add(helper);
			} else if (giver != null) {
				Friendship.add(giver, id, "", Friendship.Favour.REQUEST.points); // away: the friendship waits for them
			}
		}
		for (ServerPlayer helper : online) {
			for (Rewards.Reward r : quest.rewards) {
				r.give(level, hall, helper, quest);
			}
			if (giver != null) {
				Friendship.favour(giver, helper, Friendship.Favour.REQUEST);
			}
			Component thanks = text(quest, "thanks", Component.literal(helper.getGameProfile().getName()));
			Chat.chat(helper, HeartEvents.said(Component.literal(quest.poster), thanks));
			Chat.chat(helper, Component.translatable("message.aliveworkplace.request.done", quest.poster, quest.title(), reward(quest)).withStyle(ChatFormatting.GREEN));
			level.playSound(null, helper.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
		}
		if (giver != null) {
			say(giver, text(quest, "thanks", online.isEmpty() ? Component.translatable("chronicle.aliveworkplace.someone")
				: Component.literal(online.get(0).getGameProfile().getName())));
		}
		Chronicle.atHall(level, hall, Chronicle.Kind.FRIEND, Component.translatable("chronicle.aliveworkplace.request_done",
			names.isEmpty() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(String.join(", ", names)),
			Component.literal(quest.poster), quest.title()));
	}

	/**
	 * Takes down the requests of the village round {@code hall} that are overdue at game time {@code now}, each missed:
	 * every helper loses {@link #MISSED_COST} friendship with who asked, they're glum for a day and they ask nobody for
	 * {@link #QUIET_DAYS} days. Returns how many were missed.
	 */
	public static int overdue(ServerLevel level, BlockPos hall, long now) {
		Stories.Data data = Stories.Data.get(level);
		Stories.Entry e = data.halls.get(hall);
		int missed = 0;
		for (Quest quest : e == null ? List.<Quest>of() : List.copyOf(e.quests)) {
			if (!quest.personal() || quest.due < 0 || now < quest.due) {
				continue;
			}
			e.quests.remove(quest);
			data.setDirty();
			QuestTracker.changed(level.getServer(), quest.id);
			missed++;
			Villager giver = giver(level, quest);
			if (giver != null) {
				ModAttachments.REQUEST_LET_DOWN.set(giver, new LetDown(Chronicle.day(level) + QUIET_DAYS, level.getGameTime() + VillageNeeds.DAY));
				io.github.jcondedata.aliveworkplace.people.Moods.forget(giver);
			}
			for (UUID id : quest.helpers.keySet()) {
				if (giver != null) {
					Friendship.add(giver, id, "", -MISSED_COST);
				}
				ServerPlayer helper = online(level, id);
				if (helper != null) {
					Chat.chat(helper, Component.translatable("message.aliveworkplace.request.missed", quest.poster, quest.title()).withStyle(ChatFormatting.RED));
				}
			}
		}
		return missed;
	}

	// --- Events ---------------------------------------------------------------------------------------------------

	/** {@code player} beat the Trainer {@code trainer} in battle: a day towards their "Help me train", once a day. */
	public static void onBattleWon(Villager trainer, ServerPlayer player) {
		if (trainer.level() instanceof ServerLevel level) {
			onBattleWon(level, trainer, player, Chronicle.day(level));
		}
	}

	/** The same, on the day {@code today}. Returns whether it counted. */
	public static boolean onBattleWon(ServerLevel level, Villager trainer, ServerPlayer player, long today) {
		VillageHallBlockEntity entity = CivicEffects.hallOf(trainer);
		for (BlockPos hall : Stories.halls(level)) {
			Quest quest = of(level, hall, trainer);
			int i = quest == null ? -1 : quest.current();
			if (i >= 0 && quest.objectives.get(i) instanceof Objectives.BeatGiver && quest.mark != today && mayHelp(level, quest, player)
				&& level.getBlockEntity(hall) instanceof VillageHallBlockEntity at) {
				quest.mark = today;
				Stories.progress(level, hall, entity != null && entity.getBlockPos().equals(hall) ? entity : at, quest, i, 1, player);
				return true;
			}
		}
		return false;
	}

	private PersonalRequests() {
	}
}
