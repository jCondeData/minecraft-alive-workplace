package io.github.jcondedata.aliveworkplace.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Friendship (ROADMAP 31.5): every named villager in a hall's village keeps a friendship with each player, 0 to
 * {@link #MAX} points, ten hearts of {@link #PER_HEART}. It's saved on the villager ({@code friendship} attachment,
 * absent until someone earns a point). Favours ({@link Favour}) count once a day per villager unless said; hitting a
 * villager costs {@link #HIT_COST} at most once a minute. Hearts never fade.
 *
 * <p>Other features add points through {@link #add} (gifts, heart events) or {@link #favour} (a favour of
 * {@link Favour}, once a day). Looking at a named villager within {@link #LOOK_RANGE} blocks shows your hearts in the
 * action bar; the hall's tooltip shows them with the villager's two best friends ({@link #hallLines}). Config
 * {@code friendship} off: no points move and no hearts are shown; what's saved stays.
 */
public final class Friendship {
	/** Config {@code friendship}. */
	public static boolean ENABLED = true;
	public static final int MAX = 1000;
	public static final int PER_HEART = 100;
	public static final int HEARTS = MAX / PER_HEART;
	/** How far a villager's hearts show when looked at. */
	public static final double LOOK_RANGE = 6;
	/** How often (ticks) each player's look is checked. */
	public static final int LOOK_EVERY = 10;
	/** What a hit costs, and how often at most (ticks). */
	public static final int HIT_COST = 50;
	public static final long HIT_EVERY = 1200;
	/** How long (ticks) after a monster hurt a villager killing it is a favour. */
	public static final long DEFEND_WINDOW = 200;

	/** The favours, with their points; {@code daily}: once a day per villager and player. */
	public enum Favour {
		TRADE(5, true),
		QUEST(40, true),
		HAND_IN(10, true),
		DEFENDED(15, false),
		WEDDING(30, true),
		FESTIVAL(10, true),
		/** A personal request of theirs done (31.9). */
		REQUEST(150, true);

		public final int points;
		public final boolean daily;

		Favour(int points, boolean daily) {
			this.points = points;
			this.daily = daily;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * One player's friendship with a villager: their last known name, points, the last gift's day, the week of the gifts
	 * counted and how many that week (31.6), each daily favour's last day, the game time of the last hit that cost points,
	 * and the heart events told (31.7).
	 */
	public record Bond(String name, int points, long giftDay, long giftWeek, int giftsWeek, Map<String, Long> favours, long hitTick,
					   List<String> told) {
		public static final Bond NONE = new Bond("", 0, -1, -1, 0, Map.of(), -1, List.of());
		public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("name", "").forGetter(Bond::name),
			Codec.INT.optionalFieldOf("points", 0).forGetter(Bond::points),
			Codec.LONG.optionalFieldOf("gift_day", -1L).forGetter(Bond::giftDay),
			Codec.LONG.optionalFieldOf("gift_week", -1L).forGetter(Bond::giftWeek),
			Codec.INT.optionalFieldOf("gifts_week", 0).forGetter(Bond::giftsWeek),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("favours", Map.of()).forGetter(Bond::favours),
			Codec.LONG.optionalFieldOf("hit_tick", -1L).forGetter(Bond::hitTick),
			Codec.STRING.listOf().optionalFieldOf("told", List.of()).forGetter(Bond::told)
		).apply(i, Bond::new));

		public Bond {
			favours = Map.copyOf(favours);
			told = List.copyOf(told);
		}

		public int hearts() {
			return Friendship.hearts(points);
		}

		public Bond withPoints(String name, int points) {
			return new Bond(name.isEmpty() ? this.name : name, points, giftDay, giftWeek, giftsWeek, favours, hitTick, told);
		}

		public Bond withFavour(String favour, long day) {
			Map<String, Long> map = new HashMap<>(favours);
			map.put(favour, day);
			return new Bond(name, points, giftDay, giftWeek, giftsWeek, map, hitTick, told);
		}

		public Bond withHit(long tick) {
			return new Bond(name, points, giftDay, giftWeek, giftsWeek, favours, tick, told);
		}
	}

	/** A villager's friendships: player → {@link Bond}. */
	public record Data(Map<UUID, Bond> players) {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bond.CODEC).optionalFieldOf("players", Map.of()).forGetter(Data::players)
		).apply(i, Data::new));

		public Data {
			players = Map.copyOf(players);
		}

		public Bond bond(UUID player) {
			return players.getOrDefault(player, Bond.NONE);
		}

		public Data with(UUID player, Bond bond) {
			Map<UUID, Bond> map = new LinkedHashMap<>(players);
			map.put(player, bond);
			return new Data(map);
		}
	}

	/** Monsters that hurt villagers lately: monster → villager → game time (not saved: it lasts ten seconds). */
	private static final Map<UUID, Map<UUID, Long>> HURT = new HashMap<>();

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (!ENABLED) {
				return;
			}
			int tick = server.getTickCount();
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				if ((tick + player.getId()) % LOOK_EVERY == 0) {
					Component line = lookLine(player);
					if (line != null) {
						Chat.actionBar(player, line);
					}
				}
			}
		});
		Platform.get().afterDamage((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level && !blocked) {
				onHurt(level, villager, source);
			}
		});
		Platform.get().afterDeath((entity, source) -> {
			if (!(entity instanceof Villager) && entity.level() instanceof ServerLevel level) {
				onKill(level, entity, source);
			}
		});
		io.github.jcondedata.aliveworkplace.work.Requests.onGiven((player, request, count) ->
			favour(request.worker(), player, Favour.HAND_IN));
	}

	// --- Reading --------------------------------------------------------------------------------------------------

	public static int hearts(int points) {
		return Math.max(0, Math.min(HEARTS, points / PER_HEART));
	}

	/** Whether {@code villager} keeps friendships: named, and living in a village with a hall. */
	public static boolean eligible(Villager villager) {
		return villager.isAlive() && villager.hasCustomName() && CivicEffects.hallOf(villager) != null;
	}

	public static Data of(Villager villager) {
		return ModAttachments.FRIENDSHIP.getOrElse(villager, new Data(Map.of()));
	}

	/** {@code player}'s points with {@code villager} (0 when none). */
	public static int points(Villager villager, UUID player) {
		return of(villager).bond(player).points();
	}

	/** The players with the most points with {@code villager}, best first (only those with any), at most {@code n}. */
	public static List<Bond> bestFriends(Villager villager, int n) {
		return of(villager).players().values().stream().filter(b -> b.points() > 0)
			.sorted(Comparator.comparingInt(Bond::points).reversed().thenComparing(Bond::name)).limit(n).toList();
	}

	/** "♥♥♥♡♡♡♡♡♡♡": filled hearts in red, the rest grey. */
	public static Component heartRow(int points) {
		int full = hearts(points);
		return Component.empty()
			.append(Component.literal("♥".repeat(full)).withStyle(ChatFormatting.RED))
			.append(Component.literal("♡".repeat(HEARTS - full)).withStyle(ChatFormatting.GRAY));
	}

	/** "Dara ♥♥♥♡♡♡♡♡♡♡". */
	public static Component heartsLine(Villager villager, int points) {
		return Component.translatable("message.aliveworkplace.friendship.hearts", villager.getDisplayName().copy().withStyle(ChatFormatting.WHITE),
			heartRow(points));
	}

	/**
	 * The named villager {@code player} looks at within {@link #LOOK_RANGE} blocks (one ray along their look), or null.
	 * Never loads a chunk (B93): the server tick asks for every player, and a ray through a chunk that isn't loaded read
	 * it from disk on the server thread each time, so a player standing where nothing is loaded sees no hearts.
	 */
	@Nullable
	public static Villager lookedAt(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1f).scale(LOOK_RANGE));
		// LOOK_RANGE is under a chunk, so the ray only crosses the chunks of its two ends and the two beside their corner
		int x0 = Mth.floor(eye.x) >> 4;
		int z0 = Mth.floor(eye.z) >> 4;
		int x1 = Mth.floor(end.x) >> 4;
		int z1 = Mth.floor(end.z) >> 4;
		if (!level.hasChunk(x0, z0) || !level.hasChunk(x1, z1) || !level.hasChunk(x0, z1) || !level.hasChunk(x1, z0)) {
			return null;
		}
		HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		Villager best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(eye, end).inflate(1), Villager::isAlive)) {
			AABB box = v.getBoundingBox().inflate(v.getPickRadius());
			var hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestDistance) {
				bestDistance = hit.get().distanceToSqr(eye);
				best = v;
			}
		}
		return best;
	}

	/** What the action bar shows {@code player} now: the hearts of the named villager they look at, or null. */
	@Nullable
	public static Component lookLine(ServerPlayer player) {
		if (!ENABLED) {
			return null;
		}
		Villager v = lookedAt(player);
		return v == null || !eligible(v) ? null : heartsLine(v, points(v, player.getUUID()));
	}

	/** The hall tooltip's lines for {@code villager}: {@code viewer}'s hearts, and the two best friends among the players. */
	public static List<Component> hallLines(Villager villager, @Nullable ServerPlayer viewer) {
		List<Component> lines = new ArrayList<>();
		if (!ENABLED || !villager.hasCustomName()) {
			return lines;
		}
		if (viewer != null) {
			lines.add(Component.translatable("screen.aliveworkplace.hall.your_hearts", heartRow(points(villager, viewer.getUUID()))));
		}
		List<Bond> best = bestFriends(villager, 2);
		if (!best.isEmpty()) {
			MutableComponent names = Component.empty();
			for (int i = 0; i < best.size(); i++) {
				if (i > 0) {
					names.append(Component.literal(", "));
				}
				names.append(Component.literal(best.get(i).name()));
			}
			lines.add(Component.translatable("screen.aliveworkplace.hall.best_friends", names));
		}
		return lines;
	}

	// --- Changing -------------------------------------------------------------------------------------------------

	/**
	 * Adds {@code points} (negative takes them off) to {@code player}'s friendship with {@code villager}, kept within 0 to
	 * {@link #MAX}; a rise puffs hearts over the villager. Returns the change made (0 when off or not eligible). The entry
	 * point for gifts (31.6) and heart events (31.7).
	 */
	public static int add(Villager villager, ServerPlayer player, int points) {
		return add(villager, player.getUUID(), player.getGameProfile().getName(), points);
	}

	/** The same for a player who may be offline ({@code name}: their name, or "" to keep the one known). */
	public static int add(Villager villager, UUID player, String name, int points) {
		if (!ENABLED || points == 0 || !(villager.level() instanceof ServerLevel level) || !eligible(villager)) {
			return 0;
		}
		Data data = of(villager);
		Bond bond = data.bond(player);
		int now = Math.max(0, Math.min(MAX, bond.points() + points));
		int change = now - bond.points();
		if (change == 0 && (name.isEmpty() || name.equals(bond.name()))) {
			return 0;
		}
		ModAttachments.FRIENDSHIP.set(villager, data.with(player, bond.withPoints(name, now)));
		if (change > 0) {
			level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getEyeY() + 0.5, villager.getZ(), 4, 0.35, 0.2, 0.35, 0.0);
		}
		return change;
	}

	/** {@code player} did {@code villager} a favour: its points, once a day if it's a daily one. Returns the change made. */
	public static int favour(Villager villager, ServerPlayer player, Favour favour) {
		if (!ENABLED || !(villager.level() instanceof ServerLevel level) || !eligible(villager)) {
			return 0;
		}
		long today = Chronicle.day(level);
		if (favour.daily) {
			Long last = of(villager).bond(player.getUUID()).favours().get(favour.id());
			if (last != null && last >= today) {
				return 0;
			}
		}
		int change = add(villager, player, favour.points);
		if (favour.daily) {
			Data data = of(villager);
			ModAttachments.FRIENDSHIP.set(villager, data.with(player.getUUID(), data.bond(player.getUUID()).withFavour(favour.id(), today)));
		}
		return change;
	}

	/** {@code player} hit {@code villager}: {@link #HIT_COST} off, at most once a minute. Returns the change made. */
	public static int hit(Villager villager, ServerPlayer player) {
		if (!ENABLED || !(villager.level() instanceof ServerLevel level) || !eligible(villager)) {
			return 0;
		}
		Bond bond = of(villager).bond(player.getUUID());
		long now = level.getGameTime();
		if (bond.points() <= 0 || bond.hitTick() >= 0 && now - bond.hitTick() < HIT_EVERY) {
			return 0;
		}
		int change = add(villager, player, -HIT_COST);
		Data data = of(villager);
		ModAttachments.FRIENDSHIP.set(villager, data.with(player.getUUID(), data.bond(player.getUUID()).withHit(now)));
		return change;
	}

	// --- Events ---------------------------------------------------------------------------------------------------

	/** A trade with {@code villager} (its trading player gets the favour). */
	public static void onTrade(Villager villager) {
		if (villager.getTradingPlayer() instanceof ServerPlayer player) {
			favour(villager, player, Favour.TRADE);
		}
	}

	/** The named villager called {@code name} in the village round {@code hall}, or null. */
	@Nullable
	public static Villager named(ServerLevel level, BlockPos hall, String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && v.hasCustomName()
			&& v.getDisplayName().getString().equals(name)).stream().findFirst().orElse(null);
	}

	/** {@code player} handed in something towards {@code quest}: a favour to whoever posted it. */
	static void onHandIn(ServerLevel level, BlockPos hall, Quest quest, ServerPlayer player) {
		Villager poster = named(level, hall, quest.poster);
		if (poster != null) {
			favour(poster, player, Favour.HAND_IN);
		}
	}

	/** {@code player} finished {@code quest}: a hall quest's poster counts it a favour. */
	static void onQuestDone(ServerLevel level, BlockPos hall, Quest quest, @Nullable ServerPlayer player) {
		if (player == null || !quest.giver.equals("hall")) {
			return;
		}
		Villager poster = named(level, hall, quest.poster);
		if (poster != null) {
			favour(poster, player, Favour.QUEST);
		}
	}

	/** A wedding at the village round {@code hall}: every player there is a guest of both. */
	public static void onWedding(ServerLevel level, BlockPos hall, Villager a, Villager b) {
		AABB area = VillageHalls.area(hall);
		for (ServerPlayer player : level.getPlayers(p -> area.contains(p.position()))) {
			favour(a, player, Favour.WEDDING);
			favour(b, player, Favour.WEDDING);
		}
	}

	/** While a festival is on: {@code player} is there with every villager who came to it today. */
	public static void onFestival(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!ENABLED) {
			return;
		}
		long today = Chronicle.day(level);
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && v.hasCustomName())) {
			Long day = ModAttachments.FESTIVAL_DAY.get(v);
			if (day != null && day == today) {
				favour(v, player, Favour.FESTIVAL);
			}
		}
	}

	static void onHurt(ServerLevel level, Villager villager, DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof ServerPlayer player) {
			hit(villager, player);
		} else if (by instanceof Enemy && by instanceof LivingEntity && ENABLED) {
			long now = level.getGameTime();
			synchronized (HURT) {
				if (HURT.size() > 256) {
					HURT.values().forEach(m -> m.values().removeIf(t -> now - t > DEFEND_WINDOW));
					HURT.values().removeIf(Map::isEmpty);
				}
				HURT.computeIfAbsent(by.getUUID(), k -> new HashMap<>()).put(villager.getUUID(), now);
			}
		}
	}

	/** {@code killed} died: if a player killed it within ten seconds of it hurting villagers, each counts it a favour. */
	static void onKill(ServerLevel level, LivingEntity killed, DamageSource source) {
		Map<UUID, Long> hurt;
		synchronized (HURT) {
			hurt = HURT.remove(killed.getUUID());
		}
		if (hurt == null || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		long now = level.getGameTime();
		hurt.forEach((id, when) -> {
			if (now - when <= DEFEND_WINDOW && level.getEntity(id) instanceof Villager villager) {
				favour(villager, player, Favour.DEFENDED);
			}
		});
	}

	private Friendship() {
	}
}
