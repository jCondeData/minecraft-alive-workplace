package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Reputation and titles (ROADMAP 31.11). Each player has a standing in each village: points kept by hall in
 * {@code aliveworkplace_stories} ({@link Stories.Entry#standings}).
 *
 * <p><b>Earned:</b> a daily quest {@value #DAILY_QUEST}; a bounty {@value #BOUNTY}; a villager's own request
 * {@value #PERSONAL} to every helper; a story chapter {@value #CHAPTER} to everyone who helped in it; an arc's ending
 * what its file says (the {@code reputation} reward); breaking up a bandit camp or a lair {@value #LAIR} (whoever
 * brought its chief down); fighting in a raid {@value #RAID} (once {@value #RAID_KILLS} raiders are killed, once a
 * raid); coming to a festival {@value #FESTIVAL}; a gift a villager doesn't dislike {@value #GIFT} (at most
 * {@value #GIFT_DAY_CAP} a day per village). <b>Lost:</b> hitting a villager {@value #HIT}, killing one
 * {@value #KILL_VILLAGER}, killing an iron golem {@value #KILL_GOLEM}, killing a guard {@value #KILL_GUARD}.
 *
 * <p><b>Titles:</b> Stranger, Friend (50), Hero (300), Lord (1000, and the village at least a Town; until then a
 * player with 1000 stays a Hero). Anyone may become Lord by deeds alone, the hall's owner or not (the owner's call is
 * open, ROADMAP Notes). A title a player reaches for the first time is told to the whole server with a sound and a
 * TITLE line in the chronicle; reaching it again after losing it, and losing one, are told only to that player.
 *
 * <p><b>Honours</b> are names an arc gives ({@code honour} reward): Kingslayer, Healer of the village, Wayfinder,
 * Co-author, or any id a pack's lang file names ({@code honour.aliveworkplace.<id>}). They are listed with the standing.
 *
 * <p><b>In chat</b> ({@link #decorate}, through {@code Platform.onChatDecorate}) a player's title in the village they
 * stand in, else their best anywhere, goes before what they say. Config {@code reputation} off: nothing is earned,
 * lost, told or shown (saved standings stay); {@code titlesInChat} off: only the chat lines go undecorated.
 */
public final class Reputation {
	/** Config {@code reputation}. */
	public static boolean ENABLED = true;
	/** Config {@code titlesInChat}. */
	public static boolean CHAT = true;

	public static final int DAILY_QUEST = 10;
	public static final int PERSONAL = 25;
	public static final int CHAPTER = 50;
	public static final int BOUNTY = 40;
	public static final int LAIR = 40;
	public static final int RAID = 20;
	public static final int RAID_KILLS = 3;
	public static final int FESTIVAL = 5;
	public static final int GIFT = 2;
	public static final int GIFT_DAY_CAP = 10;
	public static final int HIT = -10;
	public static final int KILL_VILLAGER = -150;
	public static final int KILL_GOLEM = -100;
	public static final int KILL_GUARD = -200;
	/** Standing never leaves this range either way. */
	static final int LIMIT = 1_000_000;

	/** The honours our own stories give (a pack may give others). */
	public static final List<String> HONOURS = List.of("kingslayer", "healer", "wayfinder", "co_author");

	/** How many titles were told to the whole server since the game started (tests count it). */
	public static final AtomicInteger ANNOUNCED = new AtomicInteger();

	/** Something told about a title or an honour: to one player, or ({@code to} null) to the whole server. */
	public record Told(@Nullable UUID to, Component text) {
	}

	/** The last few things told, newest last (tests read what was said, and to whom). */
	public static final Deque<Told> TOLD = new ConcurrentLinkedDeque<>();

	private static void told(@Nullable UUID to, Component text) {
		TOLD.addLast(new Told(to, text));
		while (TOLD.size() > 32) {
			TOLD.pollFirst();
		}
	}

	/** The titles, lowest first, with the standing each needs. */
	public enum Title {
		STRANGER(Integer.MIN_VALUE), FRIEND(50), HERO(300), LORD(1000);

		public final int points;

		Title(int points) {
			this.points = points;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** "Hero". */
		public MutableComponent label() {
			return Component.translatable("title.aliveworkplace." + id());
		}

		/** "Hero of Thornholm". */
		public MutableComponent of(Component village) {
			return Component.translatable("title.aliveworkplace.of", label(), village);
		}

		public boolean atLeast(Title other) {
			return ordinal() >= other.ordinal();
		}

		static Title parse(String id) {
			for (Title t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			return STRANGER;
		}
	}

	/** One player's standing in one village. */
	static final class Standing {
		String name = "";
		int points;
		/** The title last told (what they hold). */
		Title title = Title.STRANGER;
		/** The titles already told to the whole server (a bit per {@link Title#ordinal()}). */
		int announced;
		final Set<String> honours = new LinkedHashSet<>();
		/** The +{@value #GIFT_DAY_CAP} a day from gifts: the day, and what gifts gave that day. */
		long giftDay = -1;
		int giftPoints;
		/** The last festival day they came to. */
		long festivalDay = -1;
		/** The raid they last fought in (its stamp) and the raiders they killed in it. */
		long raid = Long.MIN_VALUE;
		int raidKills;

		CompoundTag save(UUID player) {
			CompoundTag t = new CompoundTag();
			Nbt.putUuid(t, "player", player);
			t.putString("name", name);
			t.putInt("points", points);
			t.putString("title", title.id());
			t.putInt("announced", announced);
			if (!honours.isEmpty()) {
				ListTag l = new ListTag();
				honours.forEach(h -> l.add(StringTag.valueOf(h)));
				t.put("honours", l);
			}
			if (giftDay >= 0) {
				t.putLong("gift_day", giftDay);
				t.putInt("gift_points", giftPoints);
			}
			if (festivalDay >= 0) {
				t.putLong("festival_day", festivalDay);
			}
			if (raid != Long.MIN_VALUE) {
				t.putLong("raid", raid);
				t.putInt("raid_kills", raidKills);
			}
			return t;
		}

		static Standing load(CompoundTag t) {
			Standing s = new Standing();
			s.name = Nbt.getString(t, "name");
			s.points = Nbt.getInt(t, "points");
			s.title = Title.parse(Nbt.getString(t, "title"));
			s.announced = Nbt.getInt(t, "announced");
			ListTag l = Nbt.getList(t, "honours", Tag.TAG_STRING);
			for (int i = 0; i < l.size(); i++) {
				s.honours.add(Nbt.stringAt(l, i));
			}
			if (t.contains("gift_day")) {
				s.giftDay = Nbt.getLong(t, "gift_day");
				s.giftPoints = Nbt.getInt(t, "gift_points");
			}
			if (t.contains("festival_day")) {
				s.festivalDay = Nbt.getLong(t, "festival_day");
			}
			if (t.contains("raid")) {
				s.raid = Nbt.getLong(t, "raid");
				s.raidKills = Nbt.getInt(t, "raid_kills");
			}
			return s;
		}
	}

	/** What an {@link #add} did: the points and titles before and after, and whether the whole server was told. */
	public record Change(int before, int after, Title was, Title now, boolean announced) {
		static final Change NONE = new Change(0, 0, Title.STRANGER, Title.STRANGER, false);

		public int by() {
			return after - before;
		}
	}

	/** A village's standing as lists show it. */
	public record Line(UUID player, String name, int points, Title title, List<String> honours) {
	}

	/** A player's standing in a village somewhere. */
	public record Held(ServerLevel level, BlockPos hall, Component village, int points, Title title, List<String> honours) {
	}

	public static void init() {
		Platform.get().afterDamage((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level && !blocked && damage > 0 && entity.isAlive()) {
				onHurt(level, villager, source);
			}
		});
		Platform.get().afterDeath((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				onDeath(level, entity, source);
			}
		});
		Lairs.onBroken(Reputation::onLairBroken);
		Platform.get().onChatDecorate(Reputation::decorate);
	}

	// --- Reading --------------------------------------------------------------------------------------------------

	/** The title {@code points} earn; a Lord only in a village that is at least a Town. */
	public static Title title(int points, boolean town) {
		Title best = Title.STRANGER;
		for (Title t : Title.values()) {
			if (points >= t.points && (t != Title.LORD || town)) {
				best = t;
			}
		}
		return best;
	}

	@Nullable
	private static Standing standing(ServerLevel level, BlockPos hall, UUID player) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		return e == null ? null : e.standings.get(player);
	}

	/** {@code player}'s standing in the village round {@code hall} as {@code data} holds it (a saved copy read back). */
	public static int points(Stories.Data data, BlockPos hall, UUID player) {
		Stories.Entry e = data.halls.get(hall);
		Standing s = e == null ? null : e.standings.get(player);
		return s == null ? 0 : s.points;
	}

	/** The title and honours {@code player} holds in the village round {@code hall} as {@code data} holds them: "hero [kingslayer]". */
	public static String held(Stories.Data data, BlockPos hall, UUID player) {
		Stories.Entry e = data.halls.get(hall);
		Standing s = e == null ? null : e.standings.get(player);
		return s == null ? "" : s.title.id() + " " + s.honours;
	}

	/** {@code player}'s standing in the village round {@code hall} (0 when none). */
	public static int points(ServerLevel level, BlockPos hall, UUID player) {
		Standing s = standing(level, hall, player);
		return s == null ? 0 : s.points;
	}

	/** The title {@code player} holds in the village round {@code hall}; a Stranger with {@code reputation} off. */
	public static Title title(ServerLevel level, BlockPos hall, UUID player) {
		Standing s = standing(level, hall, player);
		return s == null || !ENABLED ? Title.STRANGER : s.title;
	}

	/** The honours {@code player} was given in the village round {@code hall}. */
	public static List<String> honours(ServerLevel level, BlockPos hall, UUID player) {
		Standing s = standing(level, hall, player);
		return s == null ? List.of() : List.copyOf(s.honours);
	}

	/** "Kingslayer", "Healer of Thornholm": the honour {@code id} as {@code village} gives it. */
	public static MutableComponent honourName(String id, Component village) {
		String plain = id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
		return Component.translatableWithFallback("honour.aliveworkplace." + id, plain, village);
	}

	/** The players with the most standing in the village round {@code hall}, best first (only those above 0), at most {@code n}. */
	public static List<Line> top(ServerLevel level, BlockPos hall, int n) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e == null) {
			return List.of();
		}
		return e.standings.entrySet().stream().filter(en -> en.getValue().points > 0)
			.map(en -> new Line(en.getKey(), en.getValue().name, en.getValue().points, en.getValue().title, List.copyOf(en.getValue().honours)))
			.sorted(Comparator.comparingInt(Line::points).reversed().thenComparing(Line::name)).limit(n).toList();
	}

	/** Every village (in every dimension) where {@code player} has a standing or an honour, best first. */
	public static List<Held> everywhere(MinecraftServer server, UUID player) {
		List<Held> out = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			Stories.Data data = Stories.Data.get(level);
			for (Map.Entry<BlockPos, Stories.Entry> en : data.halls.entrySet()) {
				Standing s = en.getValue().standings.get(player);
				if (s != null && (s.points != 0 || !s.honours.isEmpty())) {
					out.add(new Held(level, en.getKey(), villageName(level, en.getKey(), en.getValue()), s.points, s.title, List.copyOf(s.honours)));
				}
			}
		}
		out.sort(Comparator.comparingInt(Held::points).reversed());
		return out;
	}

	/**
	 * The village's name without loading its chunk: the hall's when it is loaded (and remembered then), else the name
	 * last seen, else the one made up from where it stands.
	 */
	static Component villageName(ServerLevel level, BlockPos hall, Stories.Entry e) {
		if (level.isLoaded(hall)) {
			Component name = VillageHalls.name(level, hall);
			if (e.villageName == null || !e.villageName.getString().equals(name.getString())) {
				e.villageName = name;
				Stories.Data.get(level).setDirty();
			}
			return name;
		}
		return e.villageName != null ? e.villageName : VillageHalls.madeUpName(hall);
	}

	private static boolean town(ServerLevel level, BlockPos hall) {
		return VillageRanks.of(level, hall).ordinal() >= VillageRanks.Rank.TOWN.ordinal();
	}

	// --- Earning and losing ---------------------------------------------------------------------------------------

	/** {@code player} earns (or, below 0, loses) {@code points} of standing in the village round {@code hall}. */
	public static Change add(ServerLevel level, BlockPos hall, ServerPlayer player, int points) {
		return add(level, hall, player.getUUID(), player.getGameProfile().getName(), points);
	}

	/** As {@link #add(ServerLevel, BlockPos, ServerPlayer, int)}, for a player who may be away ({@code name}: theirs if known, else empty). */
	public static Change add(ServerLevel level, BlockPos hall, UUID player, String name, int points) {
		if (!ENABLED || points == 0) {
			return Change.NONE;
		}
		Standing s = open(level, hall, player, name);
		if (s == null) {
			return Change.NONE;
		}
		Stories.Data data = Stories.Data.get(level);
		Stories.Entry e = data.entry(hall);
		int before = s.points;
		Title was = s.title;
		s.points = (int) Math.max(-LIMIT, Math.min(LIMIT, (long) before + points));
		data.setDirty();
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		Component village = villageName(level, hall, e);
		if (online != null && s.points != before) {
			int by = s.points - before;
			Chat.actionBar(online, Component.translatable(by > 0 ? "message.aliveworkplace.standing.gain" : "message.aliveworkplace.standing.loss",
				Math.abs(by), village, s.points).withStyle(by > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		boolean announced = retitle(level, hall, e, player, s);
		return new Change(before, s.points, was, s.title, announced);
	}

	/** The standing to change: null when the hall is gone (its chunk loaded and no hall there). */
	@Nullable
	private static Standing open(ServerLevel level, BlockPos hall, UUID player, String name) {
		if (level.isLoaded(hall) && !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return null;
		}
		Standing s = Stories.Data.get(level).entry(hall).standings.computeIfAbsent(player, k -> new Standing());
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			s.name = online.getGameProfile().getName(); // (helpers come by id only: a title must never be told of "Someone" who is right here)
		} else if (!name.isBlank()) {
			s.name = name;
		} else if (s.name.isBlank() && level.getServer().getProfileCache() != null) {
			s.name = level.getServer().getProfileCache().get(player).map(p -> p.getName()).orElse("");
		}
		return s;
	}

	/**
	 * Looks at the title {@code s} holds against its points and the village's rank and tells the change: a title reached
	 * for the first time to the whole server (returns true), one won back or lost only to the player. While the hall's
	 * chunk isn't loaded the Lord's Town rule can't be read, so a Lord stays one and a Hero waits.
	 */
	private static boolean retitle(ServerLevel level, BlockPos hall, Stories.Entry e, UUID player, Standing s) {
		boolean town = level.isLoaded(hall) ? town(level, hall) : s.title == Title.LORD;
		Title now = title(s.points, town);
		Title was = s.title;
		if (now == was) {
			return false;
		}
		s.title = now;
		Stories.Data.get(level).setDirty();
		MinecraftServer server = level.getServer();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		Component village = villageName(level, hall, e);
		Component who = s.name.isBlank() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(s.name);
		if (now.ordinal() > was.ordinal()) {
			int bit = 1 << now.ordinal();
			if ((s.announced & bit) == 0) {
				s.announced |= bit;
				Component news = Component.translatable("message.aliveworkplace.title.new", who, now.of(village)).withStyle(ChatFormatting.GOLD);
				server.getPlayerList().broadcastSystemMessage(news, false);
				told(null, news);
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1f);
				}
				if (level.isLoaded(hall)) {
					Chronicle.atHall(level, hall, Chronicle.Kind.TITLE, Component.translatable("chronicle.aliveworkplace.title", who, now.label()));
				}
				ANNOUNCED.incrementAndGet();
				return true;
			}
			if (online != null) {
				Component again = Component.translatable("message.aliveworkplace.title.again", now.of(village)).withStyle(ChatFormatting.GOLD);
				Chat.chat(online, again);
				told(player, again);
			}
		} else if (online != null) {
			Component lost = (now == Title.STRANGER
				? Component.translatable("message.aliveworkplace.title.lost_all", was.of(village))
				: Component.translatable("message.aliveworkplace.title.lost", was.of(village), now.label())).withStyle(ChatFormatting.RED);
			Chat.chat(online, lost);
			told(player, lost);
			online.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 0.8f);
		}
		return false;
	}

	/** The hall's round: titles follow the village's rank (a Hero with 1000 becomes Lord the day it is a Town). */
	static void round(ServerLevel level, BlockPos hall, Stories.Entry e) {
		if (!ENABLED || e.standings.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Standing> en : List.copyOf(e.standings.entrySet())) {
			retitle(level, hall, e, en.getKey(), en.getValue());
		}
	}

	/** The village round {@code hall} names {@code player} {@code honour} (once); true when it is new to them. */
	public static boolean honour(ServerLevel level, BlockPos hall, UUID player, String name, String honour) {
		if (!ENABLED || honour.isBlank()) {
			return false;
		}
		Standing s = open(level, hall, player, name);
		if (s == null || !s.honours.add(honour)) {
			return false;
		}
		Stories.Data data = Stories.Data.get(level);
		data.setDirty();
		Component village = villageName(level, hall, data.entry(hall));
		Component what = honourName(honour, village).withStyle(ChatFormatting.GOLD);
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			Component named = Component.translatable("message.aliveworkplace.honour.new", village, what).withStyle(ChatFormatting.YELLOW);
			Chat.chat(online, named);
			told(player, named);
			online.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.9f);
		}
		if (level.isLoaded(hall)) {
			Chronicle.atHall(level, hall, Chronicle.Kind.TITLE, Component.translatable("chronicle.aliveworkplace.honour",
				s.name.isBlank() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(s.name), honourName(honour, village)));
		}
		return true;
	}

	// --- The sources ----------------------------------------------------------------------------------------------

	/** A quest of the board was finished: {@value #DAILY_QUEST} to who finished it, {@value #BOUNTY} for a bounty. An arc's quests count by chapter. */
	static void onQuestDone(ServerLevel level, BlockPos hall, Quest quest, @Nullable ServerPlayer player) {
		if (player != null && quest.arc == null) {
			add(level, hall, player, "bounty".equals(quest.giver) ? BOUNTY : DAILY_QUEST);
		}
	}

	/** A villager's own request was done: {@value #PERSONAL} to every helper, here or away. */
	static void onRequestDone(ServerLevel level, BlockPos hall, Quest quest) {
		for (UUID helper : List.copyOf(quest.helpers.keySet())) {
			add(level, hall, helper, "", PERSONAL);
		}
	}

	/** A story chapter was finished: {@value #CHAPTER} to everyone who helped in it. */
	static void onChapterDone(ServerLevel level, BlockPos hall, ArcState s) {
		for (UUID helper : List.copyOf(s.chapterHelpers.keySet())) {
			add(level, hall, helper, "", CHAPTER);
		}
	}

	/** A lair (or a bandit camp) was broken up: {@value #LAIR} to the player who brought its chief down. */
	static void onLairBroken(ServerLevel level, Lairs.Lair lair, @Nullable Entity by) {
		if (by instanceof ServerPlayer player) {
			add(level, lair.hall(), player, LAIR);
		}
	}

	/** {@code player} gave {@code villager} a gift: {@value #GIFT} unless they disliked it, at most {@value #GIFT_DAY_CAP} a day in their village. */
	static void onGift(ServerLevel level, Villager villager, ServerPlayer player, Tastes.Band band) {
		VillageHallBlockEntity entity = CivicEffects.hallOf(villager);
		if (!ENABLED || band.points <= 0 || entity == null) {
			return;
		}
		BlockPos hall = entity.getBlockPos();
		Standing s = open(level, hall, player.getUUID(), player.getGameProfile().getName());
		if (s == null) {
			return;
		}
		long today = Chronicle.day(level);
		if (s.giftDay != today) {
			s.giftDay = today;
			s.giftPoints = 0;
		}
		int grant = Math.min(GIFT, GIFT_DAY_CAP - s.giftPoints);
		if (grant > 0) {
			s.giftPoints += grant;
			add(level, hall, player, grant);
		}
	}

	/** A festival is on in the village round {@code hall}: every player there gets {@value #FESTIVAL}, once a festival day. */
	public static void onFestival(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return;
		}
		long today = Chronicle.day(level);
		AABB area = VillageHalls.area(hall);
		for (ServerPlayer player : level.getPlayers(p -> !p.isSpectator() && area.contains(p.position()))) {
			Standing s = open(level, hall, player.getUUID(), player.getGameProfile().getName());
			if (s != null && s.festivalDay != today) {
				s.festivalDay = today;
				add(level, hall, player, FESTIVAL);
			}
		}
	}

	/** A player hit a villager: {@value #HIT} in the villager's village. */
	static void onHurt(ServerLevel level, Villager villager, DamageSource source) {
		if (ENABLED && source.getEntity() instanceof ServerPlayer player) {
			hallOf(level, villager).ifPresent(hall -> add(level, hall, player, HIT));
		}
	}

	/** Something died: a villager, a guard or an iron golem by a player's hand costs standing; a raider counts towards the raid. */
	static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (!ENABLED || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (entity instanceof Villager villager) {
			hallOf(level, villager).ifPresent(hall -> add(level, hall, player, Guards.isGuard(villager) ? KILL_GUARD : KILL_VILLAGER));
		} else if (entity instanceof IronGolem) {
			VillageHalls.nearest(level, entity.blockPosition()).ifPresent(hall -> add(level, hall, player, KILL_GOLEM));
		} else if (Guards.isRaider(entity) || entity instanceof Raider raider && raider.hasActiveRaid()) {
			onRaiderKilled(level, entity, player);
		}
	}

	private static Optional<BlockPos> hallOf(ServerLevel level, Villager villager) {
		VillageHallBlockEntity entity = CivicEffects.hallOf(villager);
		return entity != null ? Optional.of(entity.getBlockPos()) : VillageHalls.nearest(level, villager.blockPosition());
	}

	/** One of a raid's raiders fell to {@code player}: the third in the same raid earns {@value #RAID}. */
	private static void onRaiderKilled(ServerLevel level, LivingEntity raider, ServerPlayer player) {
		Optional<BlockPos> near = VillageHalls.nearest(level, raider.blockPosition());
		if (near.isEmpty()) {
			near = VillageHalls.nearest(level, player.blockPosition());
		}
		if (near.isEmpty()) {
			return;
		}
		BlockPos hall = near.get();
		long stamp;
		Optional<VillageRaids.Raid> ours = VillageRaids.active(hall);
		net.minecraft.world.entity.raid.Raid vanilla = level.getRaidAt(raider.blockPosition());
		if (ours.isPresent()) {
			stamp = ours.get().began();
		} else if (vanilla != null && vanilla.isActive()) {
			stamp = -1L - vanilla.getId();
		} else {
			return; // a straggler after the raid: no fight to have fought in
		}
		Standing s = open(level, hall, player.getUUID(), player.getGameProfile().getName());
		if (s == null) {
			return;
		}
		if (s.raid != stamp) {
			s.raid = stamp;
			s.raidKills = 0;
		}
		s.raidKills++;
		Stories.Data.get(level).setDirty();
		if (s.raidKills == RAID_KILLS) {
			add(level, hall, player, RAID);
		}
	}

	// --- Rewards and arc effects ----------------------------------------------------------------------------------

	/** Who a {@code reputation} or {@code honour} reward goes to. */
	static final Set<String> WHO = Set.of("finisher", "helpers", "chapter_helpers");

	static String who(JsonObject json) {
		String who = GsonHelper.getAsString(json, "who", "finisher");
		if (!WHO.contains(who)) {
			throw new IllegalArgumentException("'who' must be finisher, helpers or chapter_helpers, not '" + who + "'");
		}
		return who;
	}

	/**
	 * The players a quest's reward names: the finisher, or everyone credited with progress. A villager's own request
	 * pays each helper in turn (as its finisher), so there it is always the one being paid.
	 */
	static Collection<UUID> paid(String who, @Nullable ServerPlayer finisher, @Nullable Quest quest) {
		Set<UUID> out = new LinkedHashSet<>();
		if (!who.equals("finisher") && quest != null && !quest.personal()) {
			out.addAll(quest.helpers.keySet());
		}
		if (finisher != null) {
			out.add(finisher.getUUID());
		}
		return out;
	}

	/** The players an arc's effect names: who finished its last quest, everyone who helped in the arc, or in its last chapter. */
	static Collection<UUID> paid(String who, ArcState s) {
		Set<UUID> out = new LinkedHashSet<>();
		switch (who) {
			case "helpers" -> out.addAll(s.helpers.keySet());
			case "chapter_helpers" -> out.addAll(s.chapterHelpers.keySet());
			default -> {
				if (s.last != null) {
					out.add(s.last);
				}
			}
		}
		return out;
	}

	/** An arc's {@code reputation} or {@code honour} effect (a chapter's or the ending's). */
	static void arcEffect(ServerLevel level, BlockPos hall, ArcState s, JsonObject json) {
		Rewards.Reward reward = Rewards.parse(json);
		if (reward instanceof Rewards.ReputationReward r) {
			paid(r.who(), s).forEach(p -> add(level, hall, p, "", r.points()));
		} else if (reward instanceof Rewards.Honour h) {
			paid(h.who(), s).forEach(p -> honour(level, hall, p, "", h.id()));
		}
	}

	// --- Shown ----------------------------------------------------------------------------------------------------

	/**
	 * The title {@code player} shows: the one they hold in the village they stand in, else their best anywhere (the
	 * higher title, then the more standing); empty for a Stranger everywhere.
	 */
	public static Optional<Component> shown(ServerPlayer player) {
		if (!ENABLED) {
			return Optional.empty();
		}
		ServerLevel level = player.serverLevel();
		Optional<BlockPos> here = VillageHalls.nearest(level, player.blockPosition());
		if (here.isPresent()) {
			Stories.Entry e = Stories.Data.get(level).halls.get(here.get());
			Standing s = e == null ? null : e.standings.get(player.getUUID());
			if (s != null && s.title != Title.STRANGER) {
				return Optional.of(s.title.of(villageName(level, here.get(), e)));
			}
		}
		return everywhere(level.getServer(), player.getUUID()).stream().filter(h -> h.title() != Title.STRANGER)
			.max(Comparator.<Held>comparingInt(h -> h.title().ordinal()).thenComparingInt(Held::points))
			.map(h -> (Component) h.title().of(h.village()));
	}

	/** A chat line as the server sends it on: "[Hero of Thornholm] " before what {@code sender} said, when they hold a title. */
	public static Component decorate(@Nullable ServerPlayer sender, Component message) {
		if (!ENABLED || !CHAT || sender == null) {
			return message;
		}
		Optional<Component> title = shown(sender);
		if (title.isEmpty()) {
			return message;
		}
		return Component.translatable("chat.aliveworkplace.title", title.get().copy().withStyle(ChatFormatting.GOLD), message);
	}

	/** The hall's name tag tooltip: the viewer's standing and honours, and the three players the village thinks most of. */
	public static List<Component> hallLines(ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer) {
		List<Component> out = new ArrayList<>();
		if (!ENABLED) {
			return out;
		}
		Component village = VillageHalls.name(level, hall);
		if (viewer != null) {
			int points = points(level, hall, viewer.getUUID());
			Title title = title(level, hall, viewer.getUUID());
			out.add(Component.translatable("screen.aliveworkplace.hall.standing", points, title.label())
				.withStyle(title == Title.STRANGER ? ChatFormatting.GRAY : ChatFormatting.GOLD));
			List<String> honours = honours(level, hall, viewer.getUUID());
			if (!honours.isEmpty()) {
				out.add(Component.translatable("screen.aliveworkplace.hall.honours", honourList(honours, village)).withStyle(ChatFormatting.YELLOW));
			}
		}
		List<Line> top = top(level, hall, 3);
		if (top.isEmpty()) {
			out.add(Component.translatable("screen.aliveworkplace.hall.standing_none").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			out.add(Component.translatable("screen.aliveworkplace.hall.standing_top").withStyle(ChatFormatting.GRAY));
			for (int i = 0; i < top.size(); i++) {
				Line l = top.get(i);
				out.add(Component.translatable("screen.aliveworkplace.hall.standing_line", i + 1, name(l.name()), l.title().label(), l.points())
					.withStyle(ChatFormatting.WHITE));
			}
		}
		return out;
	}

	private static Component name(String name) {
		return name.isBlank() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(name);
	}

	/** "Kingslayer, Healer of Thornholm". */
	static Component honourList(List<String> honours, Component village) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < honours.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(honourName(honours.get(i), village));
		}
		return out;
	}

	/** {@code /workplace standing}: {@code player}'s standing, title and honours in every village. */
	public static List<Component> standingLines(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		if (!ENABLED) {
			out.add(Component.translatable("message.aliveworkplace.standing.off").withStyle(ChatFormatting.GRAY));
			return out;
		}
		List<Held> held = everywhere(player.serverLevel().getServer(), player.getUUID());
		if (held.isEmpty()) {
			out.add(Component.translatable("message.aliveworkplace.standing.none").withStyle(ChatFormatting.GRAY));
			return out;
		}
		out.add(Component.translatable("message.aliveworkplace.standing.header").withStyle(ChatFormatting.GOLD));
		for (Held h : held) {
			out.add(Component.translatable("message.aliveworkplace.standing.line", h.village().copy().withStyle(ChatFormatting.WHITE),
				h.title().label().withStyle(h.title() == Title.STRANGER ? ChatFormatting.GRAY : ChatFormatting.GOLD), h.points()).withStyle(ChatFormatting.GRAY));
			if (!h.honours().isEmpty()) {
				out.add(Component.translatable("message.aliveworkplace.standing.honours", honourList(h.honours(), h.village())).withStyle(ChatFormatting.YELLOW));
			}
		}
		return out;
	}

	private Reputation() {
	}
}
