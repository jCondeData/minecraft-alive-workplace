package io.github.jcondedata.aliveworkplace.cup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cup's day at the host (ROADMAP 28.19), from morning to the final. From {@link #ARRIVE} the far villages'
 * entrants come as delegates (visiting villagers with their Leader's name and a Trainer Leader's outfit, "of" their
 * village, {@link ModAttachments#CUP_DELEGATE}), walking in from the village edge on their home's side; they take no
 * job or bed, and leave at dawn out of sight. A real Leader whose village is loaded is away at the Cup that day. The
 * market's travelling traders hold a fair at the Arena's fair lane, {@link #FAIR_EXTRA} more than a market day, each
 * also selling the theme's wares. From {@link #STANDS} the villagers off work, and for the final every villager, sit in
 * the stands (on a {@link StandSeat}), cheer when their village's entrant wins, and eat the feast (the theme's dish
 * first); the bard plays the theme's disc at the ring, and the festival's square is the ring all day. After the final:
 * fireworks in the theme's colours, Hero of the Village for players at the Arena, the festival's mood for everyone who
 * came, and the Cup in the chronicle of every circuit village (kept for one that isn't loaded until it is). At the
 * theme's end any bout left is settled as an exhibition ({@link #settle}). A host not loaded on its Cup morning puts it
 * off a day at a time, {@link #MAX_POSTPONED} days at most; a host whose Arena is gone, or with {@code festivalCup}
 * off, holds a plain festival.
 */
public final class CupDays {
	/** When the delegates arrive and the fair opens (time of day). */
	public static final long ARRIVE = 1000;
	/** When the stands open: the afternoon off. */
	public static final long STANDS = 6000;
	/** Traders at the fair beyond a market day's. */
	public static final int FAIR_EXTRA = 2;
	/** Days a Cup may be put off before it's called off. */
	public static final int MAX_POSTPONED = 8;
	/** How long (ticks of the day) the stands stay full after the final, for the fireworks. */
	public static final long AFTER_FINAL = 1200;
	/** A delegate leaves only with no player this close. */
	public static final double SIGHT = 32;
	/** How near a bench a villager sits down. */
	static final double SIT_REACH = 2.5;
	static final int TICK_EVERY = 20;

	/** A delegate: the hall of the village it stands for, the entrant's UUID, tier, the host, and the Cup's day. */
	public record Delegate(BlockPos home, UUID leader, int tier, BlockPos host, long day) {
		public static final Codec<Delegate> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.optionalFieldOf("home", BlockPos.ZERO).forGetter(Delegate::home),
			UUIDUtil.CODEC.optionalFieldOf("leader", new UUID(0, 0)).forGetter(Delegate::leader),
			Codec.INT.optionalFieldOf("tier", 1).forGetter(Delegate::tier),
			BlockPos.CODEC.optionalFieldOf("host", BlockPos.ZERO).forGetter(Delegate::host),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Delegate::day)
		).apply(i, Delegate::new));
	}

	/** By host: until when (game time) fireworks go up after the final. Not saved: a restart ends the show. */
	private static final Map<BlockPos, Long> FIREWORKS = new HashMap<>();
	/** By host: how many villagers cheered the last win (tests). */
	private static final Map<BlockPos, Integer> CHEERS = new HashMap<>();

	private CupDays() {
	}

	public static void init() {
		Platform.get().onServerTick(CupDays::tick);
	}

	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getGameTime() % TICK_EVERY == 0) {
				tick(level);
			}
		}
	}

	// ---------------------------------------------------------------- queries

	public static boolean isDelegate(Villager v) {
		return ModAttachments.CUP_DELEGATE.has(v);
	}

	@Nullable
	public static Delegate delegate(Villager v) {
		return ModAttachments.CUP_DELEGATE.get(v);
	}

	/** Whether {@code cup} is today's and on: closed with a bracket, on its day, the Cup on. */
	static boolean today(ServerLevel level, CupData.Cup cup) {
		return Cups.ENABLED && cup.closed && cup.noCup.isEmpty() && !cup.bracket.isEmpty() && cup.day == Chronicle.day(level);
	}

	/** The ring of the Arena round {@code hall} while its Cup's day is on (the festival's square then), else empty. */
	public static Optional<BlockPos> ring(ServerLevel level, BlockPos hall) {
		CupData.Cup cup = CupData.get(level).existing(hall);
		if (cup == null || !today(level, cup)) {
			return Optional.empty();
		}
		return Arenas.find(level, hall).map(Arenas.Arena::ring);
	}

	/** The theme's dish while the Cup's day of the village round {@code hall} is on, else null. */
	@Nullable
	public static Item dish(ServerLevel level, BlockPos hall) {
		CupData.Cup cup = CupData.get(level).existing(hall);
		CupThemes.Theme theme = cup == null ? null : Cups.theme(cup);
		if (theme == null || !today(level, cup)) {
			return null;
		}
		return item(theme.dish());
	}

	@Nullable
	static Item item(net.minecraft.resources.ResourceLocation id) {
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
		return item == Items.AIR ? null : item;
	}

	/** Whether the stands of the Arena at {@code at} are open now: a Cup's afternoon, until a while after the final. */
	public static boolean standsOpen(ServerLevel level, BlockPos at) {
		long t = level.getDayTime() % Cups.DAY;
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			CupData.Cup cup = e.getValue();
			CupThemes.Theme theme = Cups.theme(cup);
			if (theme == null || !today(level, cup) || t < STANDS || t >= theme.end() || !e.getKey().closerThan(at, VillageHalls.RADIUS + 32)) {
				continue;
			}
			if (cup.finaleDay != cup.day || t < cup.finaleTime + AFTER_FINAL) {
				return true;
			}
		}
		return false;
	}

	/** Why {@code trainer} takes no challenge today (28.19): a Leader away at the Cup, or a delegate there for it; null if they do. */
	@Nullable
	public static Component noChallenge(ServerLevel level, Villager trainer) {
		if (isDelegate(trainer)) {
			return Component.translatable("message.aliveworkplace.cup.delegate_busy", trainer.getDisplayName());
		}
		for (Map.Entry<BlockPos, CupData.Cup> e : CupData.get(level).all().entrySet()) {
			CupData.Cup cup = e.getValue();
			if (today(level, cup) && cup.entrants.stream().anyMatch(en -> en.id().equals(trainer.getUUID()) && !en.village().equals(e.getKey()))) {
				return Component.translatable("message.aliveworkplace.cup.away", trainer.getDisplayName(), CupBouts.villageName(level, e.getKey()));
			}
		}
		return null;
	}

	/** Who fights for entrant {@code id} at the ring at {@code ring}: their delegate there, else the entrant themself. */
	@Nullable
	public static Entity standIn(ServerLevel level, UUID id, BlockPos ring) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(ring).inflate(CupBouts.AT_THE_ARENA), CupDays::isDelegate)) {
			if (id.equals(delegate(v).leader())) {
				return v;
			}
		}
		return level.getEntity(id);
	}

	/** The delegates of the Cup at {@code host} near it now. */
	public static List<Villager> delegates(ServerLevel level, BlockPos host) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(host).inflate(32), v -> v.isAlive() && isDelegate(v) && delegate(v).host().equals(host));
	}

	/** How many villagers cheered the last win at {@code host}'s ring (tests). */
	public static int cheers(BlockPos host) {
		return CHEERS.getOrDefault(host, 0);
	}

	// ---------------------------------------------------------------- the day, tick by tick

	/** Every host in {@code level}, one step (every {@link #TICK_EVERY} ticks). */
	public static void tick(ServerLevel level) {
		CupData data = CupData.get(level);
		long now = level.getDayTime();
		long t = now % Cups.DAY;
		for (Map.Entry<BlockPos, CupData.Cup> e : data.all().entrySet()) {
			BlockPos host = e.getKey();
			CupData.Cup cup = e.getValue();
			if (!level.isLoaded(host)) {
				postpone(level, host, cup);
				continue;
			}
			delegatesLeave(level, host, cup);
			if (!today(level, cup)) {
				continue;
			}
			Optional<Arenas.Arena> found = Arenas.find(level, host);
			if (found.isEmpty()) { // the Arena is gone: a plain festival
				cup.noCup = "screen.aliveworkplace.cup.why.arena";
				if (cup.bout != null || cup.call != null) {
					CupBouts.stop(level, host, cup);
				}
				data.setDirty();
				Cups.tell(level, host, Component.translatable("message.aliveworkplace.cup.no_arena", VillageHalls.name(level, host)));
				continue;
			}
			Arenas.Arena arena = found.get();
			CupThemes.Theme theme = Cups.theme(cup);
			if (theme == null) {
				continue;
			}
			if (t >= ARRIVE && cup.delegatesDay != cup.day) {
				cup.delegatesDay = cup.day;
				arrive(level, host, cup, arena);
				data.setDirty();
			}
			if (t >= ARRIVE && cup.fairDay != cup.day) {
				cup.fairDay = cup.day;
				fair(level, host, arena, theme);
				data.setDirty();
			}
			keepDelegates(level, host, arena);
			toArena(level, host, cup, arena, t >= STANDS);
			if (t >= STANDS && t < theme.end()) {
				if (cup.feastDay != cup.day) {
					cup.feastDay = cup.day;
					feast(level, host, arena, theme);
					data.setDirty();
				}
				if (standsOpen(level, host)) {
					stands(level, host, cup, arena, finalNext(cup));
				}
			}
			while (cup.cheered < cup.results.size()) {
				cheer(level, host, cup, cup.results.get(cup.cheered));
				cup.cheered++;
				data.setDirty();
			}
			if (cup.finaleDay != cup.day && CupBouts.champion(cup) != null) {
				finale(level, host, cup, arena, theme);
			}
			Long until = FIREWORKS.get(host);
			if (until != null) {
				if (level.getGameTime() > until) {
					FIREWORKS.remove(host);
				} else {
					rocket(level, arena.ring(), theme);
				}
			}
		}
	}

	// ---------------------------------------------------------------- put off, or called off

	/** A host not loaded on its Cup's morning puts it off a day; after {@link #MAX_POSTPONED} days it's called off. */
	static void postpone(ServerLevel level, BlockPos host, CupData.Cup cup) {
		long today = Chronicle.day(level);
		if (!Cups.ENABLED || cup.closed || cup.day < 1 || today < cup.day) {
			return;
		}
		CupThemes.Theme theme = Cups.theme(cup);
		Component name = theme == null ? Component.translatable("screen.aliveworkplace.cup.tab") : Component.translatable(theme.name());
		if (cup.postponed >= MAX_POSTPONED) {
			Cups.over(cup);
			cup.day = -1;
			Cups.tell(level, host, Component.translatable("message.aliveworkplace.cup.called_off", name, CupBouts.villageName(level, host), MAX_POSTPONED));
		} else {
			cup.postponed++;
			cup.day = today + 1;
			Cups.tell(level, host, Component.translatable("message.aliveworkplace.cup.put_off", name, CupBouts.villageName(level, host), cup.day));
		}
		CupData.get(level).setDirty();
	}

	// ---------------------------------------------------------------- the delegates

	/** The far villages' entrants arrive as delegates from the village edge on their home's side. */
	static void arrive(ServerLevel level, BlockPos host, CupData.Cup cup, Arenas.Arena arena) {
		Set<UUID> here = new HashSet<>();
		delegates(level, host).forEach(v -> here.add(delegate(v).leader()));
		for (CupData.Entrant e : cup.entrants) {
			if (e.kind() == CupData.Kind.PLAYER || e.kind() == CupData.Kind.HOST_TRAINER || e.village().equals(host) || here.contains(e.id())) {
				continue;
			}
			BlockPos edge = edge(level, host, e.village());
			Villager v = EntityType.VILLAGER.create(level);
			if (v == null) {
				continue;
			}
			v.moveTo(edge.getX() + 0.5, edge.getY(), edge.getZ() + 0.5, level.random.nextFloat() * 360, 0);
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(Math.max(1, Math.min(5, e.tier()))));
			v.setVillagerXp(Math.max(1, e.xp()));
			v.setCustomName(Component.translatable("entity.aliveworkplace.cup_delegate", e.display(), CupBouts.villageName(level, e.village())));
			v.setPersistenceRequired();
			ModAttachments.CUP_DELEGATE.set(v, new Delegate(e.village().immutable(), e.id(), e.tier(), host.immutable(), cup.day));
			level.addFreshEntity(v);
			walk(v, arena.ring(), level);
		}
	}

	/** Where a delegate from {@code home} comes in: the village edge towards it (as near as is loaded), on the ground. */
	public static BlockPos edge(ServerLevel level, BlockPos host, BlockPos home) {
		double dx = home.getX() - host.getX();
		double dz = home.getZ() - host.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1) {
			dx = 1;
			dz = 0;
			len = 1;
		}
		for (int d = VillageHalls.RADIUS; d >= 8; d -= 4) {
			BlockPos column = BlockPos.containing(host.getX() + dx / len * d, host.getY(), host.getZ() + dz / len * d);
			if (level.isLoaded(column)) {
				return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			}
		}
		return host.above();
	}

	/** Delegates near the ring, never with a job site or a bed. */
	static void keepDelegates(ServerLevel level, BlockPos host, Arenas.Arena arena) {
		for (Villager v : delegates(level, host)) {
			noJobNoBed(v);
			if (!v.position().closerThan(Vec3.atBottomCenterOf(arena.ring()), 12) && !v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
				walk(v, arena.ring(), level);
			}
		}
	}

	/** The host's own entrants walk to the Arena; from noon the village's bard too, to play at the ring. */
	static void toArena(ServerLevel level, BlockPos host, CupData.Cup cup, Arenas.Arena arena, boolean afternoon) {
		List<Villager> going = new ArrayList<>();
		for (CupData.Entrant e : cup.entrants) {
			if (e.kind() != CupData.Kind.PLAYER && e.village().equals(host) && level.getEntity(e.id()) instanceof Villager v) {
				going.add(v);
			}
		}
		Villager bard = afternoon ? bard(level, host) : null;
		if (bard != null) {
			going.add(bard);
		}
		Vec3 ring = Vec3.atBottomCenterOf(arena.ring());
		for (Villager v : going) {
			if (v.isAlive() && !v.isSleeping() && !v.isPassenger() && !v.position().closerThan(ring, 10)
				&& !v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
				walk(v, arena.ring(), level);
			}
		}
	}

	/** The village's bard (the one nearest the hall), or null. */
	@Nullable
	static Villager bard(ServerLevel level, BlockPos host) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(host), v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.BARD)
			.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(host)))).orElse(null);
	}

	static void noJobNoBed(Villager v) {
		for (MemoryModuleType<GlobalPos> poi : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.HOME)) {
			if (v.getBrain().hasMemoryValue(poi)) {
				v.releasePoi(poi);
				v.getBrain().eraseMemory(poi);
			}
		}
	}

	private static void walk(Villager v, BlockPos to, ServerLevel level) {
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3), 0.6f, 2));
	}

	/** From dawn after their Cup (or once it's off), delegates leave: gone where nobody sees, else walking to the edge. */
	static void delegatesLeave(ServerLevel level, BlockPos host, CupData.Cup cup) {
		long now = level.getDayTime();
		for (Villager v : delegates(level, host)) {
			Delegate d = delegate(v);
			boolean off = !Cups.ENABLED || cup.day == d.day() && cup.closed && !cup.noCup.isEmpty();
			if (now < d.day() * Cups.DAY && !off) {
				continue;
			}
			if (level.getNearestPlayer(v, SIGHT) == null) {
				if (v.isPassenger()) {
					v.stopRiding();
				}
				v.discard();
			} else if (!v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(edge(level, host, d.home()), 0.6f, 2));
			}
		}
	}

	// ---------------------------------------------------------------- the fair, the feast and the stands

	/** The fair at the Arena's fair lane (by the notice board in an Arena I): the market's traders and two more, selling the theme's wares too. */
	static List<WanderingTrader> fair(ServerLevel level, BlockPos host, Arenas.Arena arena, CupThemes.Theme theme) {
		BlockPos lane = arena.fairLane().orElse(arena.noticeBoard());
		return MarketDays.hold(level, host, lane, FAIR_EXTRA, trader -> trader.getOffers().addAll(offers(theme)));
	}

	/** What each trader at the fair sells besides their own: the theme's wares (any whose item this game lacks left out), for emeralds. */
	public static List<MerchantOffer> offers(CupThemes.Theme theme) {
		List<MerchantOffer> out = new ArrayList<>();
		for (CupThemes.Ware w : theme.wares()) {
			Item item = item(w.item());
			if (item != null) {
				out.add(new MerchantOffer(new ItemCost(Items.EMERALD, w.price()), new ItemStack(item), 4, 1, 0.05f));
			}
		}
		return out;
	}

	/** The feast (the theme's dish first) and the bard's disc at the ring. */
	static void feast(ServerLevel level, BlockPos host, Arenas.Arena arena, CupThemes.Theme theme) {
		if (level.getBlockEntity(host) instanceof VillageHallBlockEntity entity && entity.feastDay() < Chronicle.day(level)) {
			entity.setFeastDay(Chronicle.day(level));
			Festivals.feast(level, host);
		}
		Villager bard = bard(level, host);
		BlockPos at = bard != null && bard.position().closerThan(Vec3.atBottomCenterOf(arena.ring()), 16) ? bard.blockPosition() : arena.ring();
		Item disc = item(theme.disc());
		JukeboxPlayable playable = disc == null ? null : new ItemStack(disc).get(DataComponents.JUKEBOX_PLAYABLE);
		if (playable != null) {
			playable.song().unwrap(level.registryAccess()).ifPresent(song ->
				level.playSound(null, at, song.value().soundEvent().value(), SoundSource.RECORDS, 4f, 1f));
		}
		Vec3 ring = Vec3.atCenterOf(arena.ring());
		level.sendParticles(ParticleTypes.NOTE, ring.x, ring.y + 1.5, ring.z, 6, 1.5, 0.5, 1.5, 1);
	}

	/** Whether the next bout is the final (one pair left). */
	public static boolean finalNext(CupData.Cup cup) {
		List<CupData.Entrant> slots = new ArrayList<>(cup.bracket);
		int round = 1;
		while (slots.size() > 2) {
			List<CupData.Entrant> winners = new ArrayList<>();
			for (int i = 0; i + 1 < slots.size(); i += 2) {
				CupData.Entrant a = slots.get(i);
				CupData.Entrant b = slots.get(i + 1);
				if (a == null || b == null) {
					winners.add(a == null ? b : a);
					continue;
				}
				CupData.Result r = CupBouts.result(cup, round, a.id(), b.id());
				if (r == null) {
					return false;
				}
				winners.add(r.winner().equals(a.id()) ? a : b);
			}
			slots = winners;
			round++;
		}
		return slots.size() == 2 && CupBouts.champion(cup) == null;
	}

	/** The villagers off work (for the final every villager) go to the stands and sit on a free bench. */
	static void stands(ServerLevel level, BlockPos host, CupData.Cup cup, Arenas.Arena arena, boolean everyone) {
		Set<UUID> entrants = new HashSet<>();
		cup.entrants.forEach(e -> entrants.add(e.id()));
		Set<BlockPos> taken = new HashSet<>();
		AABB stands = new AABB(arena.ring()).inflate(40, 16, 40);
		for (StandSeat seat : level.getEntitiesOfClass(StandSeat.class, stands, Entity::isAlive)) {
			taken.add(seat.blockPosition());
		}
		List<BlockPos> free = new ArrayList<>(arena.seats().stream().filter(s -> !taken.contains(s)).toList());
		if (free.isEmpty()) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(host), v -> v.isAlive() && !v.isSleeping() && !v.isPassenger()
			&& !isDelegate(v) && !entrants.contains(v.getUUID()) && v.getVillagerData().getProfession() != ModVillagers.BARD)) {
			if (free.isEmpty()) {
				return;
			}
			if (!everyone && (v.isBaby() || !offWork(v))) {
				continue;
			}
			BlockPos bench = free.stream().min(java.util.Comparator.comparingDouble(s -> v.distanceToSqr(Vec3.atBottomCenterOf(s)))).get();
			if (v.position().closerThan(Vec3.atBottomCenterOf(bench), SIT_REACH)) {
				StandSeat seat = StandSeat.at(level, bench);
				level.addFreshEntity(seat);
				if (v.startRiding(seat, true)) {
					free.remove(bench);
					ModAttachments.FESTIVAL_DAY.set(v, Chronicle.day(level));
					v.getNavigation().stop();
					v.getLookControl().setLookAt(Vec3.atCenterOf(arena.ring()));
				} else {
					seat.discard();
				}
			} else {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bench, 0.6f, 1));
			}
		}
	}

	/** Off work: idle, meeting, playing or at work (the afternoon is a holiday), not resting or hiding. */
	static boolean offWork(Villager v) {
		Activity a = v.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
		return a == Activity.IDLE || a == Activity.MEET || a == Activity.PLAY || a == Activity.WORK;
	}

	/** Villagers seated in the stands round {@code ring}. */
	public static List<Villager> seated(ServerLevel level, BlockPos ring) {
		List<Villager> out = new ArrayList<>();
		for (StandSeat seat : level.getEntitiesOfClass(StandSeat.class, new AABB(ring).inflate(40, 16, 40), Entity::isAlive)) {
			for (Entity p : seat.getPassengers()) {
				if (p instanceof Villager v) {
					out.add(v);
				}
			}
		}
		return out;
	}

	/** A win: the stands cheer if it's their village's entrant (the host's for the host's villagers, a delegate's own). */
	static void cheer(ServerLevel level, BlockPos host, CupData.Cup cup, CupData.Result r) {
		CupData.Entrant winner = CupBouts.entrant(cup, r.winner(), host);
		List<Villager> cheering = new ArrayList<>();
		Optional<Arenas.Arena> arena = Arenas.find(level, host);
		if (winner.village().equals(host) && arena.isPresent()) {
			cheering.addAll(seated(level, arena.get().ring()));
		}
		for (Villager d : delegates(level, host)) {
			if (delegate(d).home().equals(winner.village())) {
				cheering.add(d);
			}
		}
		for (Villager v : cheering) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 0.9f + level.random.nextFloat() * 0.3f);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.8, v.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
		}
		CHEERS.put(host.immutable(), cheering.size());
	}

	// ---------------------------------------------------------------- the champion

	/** After the final: the champion cheered, fireworks, heroes, the festival's mood, the roll and every circuit chronicle. */
	static void finale(ServerLevel level, BlockPos host, CupData.Cup cup, Arenas.Arena arena, CupThemes.Theme theme) {
		CupData.Entrant champ = CupBouts.champion(cup);
		cup.finaleDay = cup.day;
		cup.finaleTime = level.getDayTime() % Cups.DAY;
		CupData.get(level).setDirty();
		CupData.Result last = cup.results.stream().max(java.util.Comparator.comparingInt(CupData.Result::round)).orElse(null);
		CupData.Entrant runnerUp = last == null ? null : CupBouts.entrant(cup, last.loser(), host);
		CupData.Champion before = CupChampions.holder(cup);
		cup.champions.add(new CupData.Champion(cup.day, theme.id(), champ.display().getString(), champ.village().immutable(), champ.id()));
		CupChampions.crowned(level, host, champ, before == null ? null : before.village()); // 28.21: the title, the banner, the bonus
		Component themeName = Component.translatable(theme.name());
		Component hostName = CupBouts.villageName(level, host);
		Component champVillage = CupBouts.villageName(level, champ.village());
		// fireworks in the theme's colours, for a while
		for (int i = 0; i < 4; i++) {
			rocket(level, arena.ring(), theme);
		}
		FIREWORKS.put(host.immutable(), level.getGameTime() + 200);
		Vec3 ring = Vec3.atCenterOf(arena.ring());
		Component line = Component.translatable("message.aliveworkplace.cup.champion", champ.display(), champVillage, themeName).withStyle(ChatFormatting.GOLD);
		long left = Math.max(1200, theme.end() - cup.finaleTime);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(ring) <= CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA) {
				Chat.chat(player, line);
				player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, (int) left, 0, true, true));
			}
		}
		// the festival's mood for everyone who came
		long today = Chronicle.day(level);
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(arena.ring()).inflate(CupBouts.AT_THE_ARENA), Villager::isAlive)) {
			ModAttachments.FESTIVAL_DAY.set(v, today);
		}
		level.playSound(null, arena.ring(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 2f, 1f);
		// the chronicle of every circuit village
		Component entry = runnerUp == null
			? Component.translatable("chronicle.aliveworkplace.cup_walkover", themeName, hostName, champ.display(), champVillage)
			: Component.translatable("chronicle.aliveworkplace.cup", themeName, hostName, champ.display(), champVillage, runnerUp.display(),
				CupBouts.villageName(level, runnerUp.village()));
		CupData data = CupData.get(level);
		Set<BlockPos> villages = new java.util.LinkedHashSet<>(Cups.circuit(level, host));
		cup.entrants.forEach(e -> villages.add(e.village()));
		for (BlockPos hall : villages) {
			if (level.isLoaded(hall) && level.getBlockEntity(hall) instanceof VillageHallBlockEntity) {
				Chronicle.atHall(level, hall, Chronicle.Kind.CUP, entry, cup.day);
			} else {
				data.note(hall, new CupData.Note(cup.day, Component.Serializer.toJson(entry, level.registryAccess())));
			}
		}
	}

	private static void rocket(ServerLevel level, BlockPos ring, CupThemes.Theme theme) {
		BlockPos column = ring.offset(level.random.nextInt(13) - 6, 0, level.random.nextInt(13) - 6);
		Festivals.launch(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column), theme.fireworks());
	}

	/** The Cup entries kept for the village round {@code hall} while it wasn't loaded, into its chronicle now (its round). */
	public static void writeNotes(ServerLevel level, BlockPos hall) {
		CupData data = CupData.get(level);
		if (data.notes(hall).isEmpty()) {
			return;
		}
		for (CupData.Note n : data.takeNotes(hall)) {
			Component text = Component.Serializer.fromJson(n.json(), level.registryAccess());
			if (text != null) {
				Chronicle.atHall(level, hall, Chronicle.Kind.CUP, text, n.day());
			}
		}
	}

	// ---------------------------------------------------------------- the end of the day

	/**
	 * The theme's end: the bout in the ring stopped and every bout left settled as an exhibition (two villagers: played out
	 * at once by the bout's seed; a player not at the ring loses by walkover), then the champion cheered.
	 */
	public static void settle(ServerLevel level, BlockPos host, CupData.Cup cup) {
		if (cup.bout != null || cup.call != null) {
			CupBouts.stop(level, host, cup);
		}
		if (!cup.closed || !cup.noCup.isEmpty() || cup.bracket.isEmpty() || !Cups.ENABLED) {
			return;
		}
		CupThemes.Theme theme = Cups.theme(cup);
		Optional<Arenas.Arena> arena = Arenas.find(level, host);
		BlockPos ring = arena.map(Arenas.Arena::ring).orElse(host);
		for (int guard = 0; guard < 64 && CupBouts.champion(cup) == null; guard++) {
			CupBouts.Pairing p = undecided(cup);
			if (p == null) {
				break;
			}
			long seed = CupBout.seed(host, cup.day, p.round(), p.a().id(), p.b().id());
			boolean aOut = p.a().kind() == CupData.Kind.PLAYER && !here(level, p.a(), ring);
			boolean bOut = p.b().kind() == CupData.Kind.PLAYER && !here(level, p.b(), ring);
			CupData.Entrant win;
			boolean walkover = false;
			if (aOut != bOut) { // a player not at the ring loses by walkover
				win = aOut ? p.b() : p.a();
				walkover = true;
			} else if (p.a().kind() != CupData.Kind.PLAYER && p.b().kind() != CupData.Kind.PLAYER && theme != null) {
				CupBout bout = new CupBout(p.a().id(), p.b().id(), CupBouts.team(p.a(), theme), CupBouts.team(p.b(), theme), p.round(), seed, ring, ring, ring);
				bout.playOut();
				win = bout.winner == 0 ? p.a() : p.b();
			} else {
				win = (seed & 1) == 0 ? p.a() : p.b();
			}
			CupData.Entrant lose = win == p.a() ? p.b() : p.a();
			cup.results.add(new CupData.Result(p.round(), win.id(), lose.id()));
			CupBouts.award(level, win, true);
			CupBouts.award(level, lose, false);
			Component line = walkover
				? Component.translatable("message.aliveworkplace.cup.settled_walkover", win.display(), CupBouts.villageName(level, win.village()), lose.display())
				: Component.translatable("message.aliveworkplace.cup.settled", win.display(), CupBouts.villageName(level, win.village()), lose.display(),
					CupBouts.villageName(level, lose.village()));
			tellAt(level, ring, line.copy().withStyle(ChatFormatting.GOLD));
		}
		CupData.get(level).setDirty();
		if (theme != null && arena.isPresent() && cup.finaleDay != cup.day && CupBouts.champion(cup) != null) {
			finale(level, host, cup, arena.get(), theme);
		}
	}

	/** The first undecided bout of the earliest round still going, players' too; null once a champion is decided. */
	@Nullable
	static CupBouts.Pairing undecided(CupData.Cup cup) {
		List<CupData.Entrant> slots = new ArrayList<>(cup.bracket);
		int round = 1;
		while (slots.size() >= 2) {
			List<CupData.Entrant> winners = new ArrayList<>();
			for (int i = 0; i + 1 < slots.size(); i += 2) {
				CupData.Entrant a = slots.get(i);
				CupData.Entrant b = slots.get(i + 1);
				if (a == null || b == null) {
					winners.add(a == null ? b : a);
					continue;
				}
				CupData.Result r = CupBouts.result(cup, round, a.id(), b.id());
				if (r == null) {
					return new CupBouts.Pairing(round, a, b);
				}
				winners.add(r.winner().equals(a.id()) ? a : b);
			}
			slots = winners;
			round++;
		}
		return null;
	}

	/** Whether a player entrant is online at the ring. */
	static boolean here(ServerLevel level, CupData.Entrant e, BlockPos ring) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.id());
		return p != null && p.level() == level && p.position().distanceToSqr(Vec3.atCenterOf(ring)) <= CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA;
	}

	private static void tellAt(ServerLevel level, BlockPos ring, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(Vec3.atCenterOf(ring)) <= CupBouts.AT_THE_ARENA * CupBouts.AT_THE_ARENA) {
				Chat.chat(player, message);
			}
		}
	}

	/** Forgets the fireworks and cheers (tests). */
	public static void forget() {
		FIREWORKS.clear();
		CHEERS.clear();
	}
}
