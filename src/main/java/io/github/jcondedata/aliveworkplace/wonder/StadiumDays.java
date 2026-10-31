package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.cup.StandSeat;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.github.jcondedata.aliveworkplace.wonder.powers.StadiumDaysPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.TournamentHostPower;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Stadium's Stadium Days (ROADMAP 35.24, its {@code stadium_days} power). On every {@code every_days}th day, from
 * {@link #AFTERNOON} until {@link #DUSK}, while the Wonder is finished and standing: {@code min_visitors} to
 * {@code max_visitors} visiting trainers (the trainers the villages of its realm, or its caravan partners, would send to
 * a Cup, {@code Cups.delegate}; travelling trainers of Journeyman to Master rank when those have none to send) stand in
 * its two trainer boxes. Players challenge them as they do the village's own ({@code trainer/Trainers}: CobbleDollars,
 * or emeralds without them, once a day each; money only, no badges). From {@link #FIRST_BOUT}, one an
 * {@link #BOUT_EVERY} ticks, the village's own trainers take them on in bouts settled by rank and team strength (the
 * Cup's exhibition, {@link CupBout}, played out at once with each side's own team: the server can't play a real battle
 * with no player in it); both gain battle XP (a visitor's own trainer at home too, or banked on its village's caravans'
 * entry), and the result goes to the players there and into the chronicle. The villagers off work fill the stands (on
 * a {@link StandSeat}, as at a Cup) and those who watched are {@link #WATCHED_MOOD} happier for the days until the
 * next. At dusk the visitors leave: gone where nobody sees, else walking out of the gate.
 *
 * <p>The boxes, the field and the seats are found in the Wonder's own drawing (its two Training Posts, its carpets), so a
 * server owner's Wonder with the power works the same. Saved: the day each site last held one ({@link Data}) and each
 * visitor's {@link Visitor} record, so a restart mid-afternoon picks up where it was.
 */
public final class StadiumDays {
	public static final ResourceLocation STADIUM = AliveWorkplace.id("stadium");
	/** When the visitors come (time of day): the afternoon off, as the Cup's stands open. */
	public static final long AFTERNOON = CupDays.STANDS;
	/** When they leave. */
	public static final long DUSK = 12000;
	/** The first bout, and the time between bouts. */
	public static final long FIRST_BOUT = 7000;
	public static final long BOUT_EVERY = 1000;
	/** How much happier the villagers who watched are. */
	public static final int WATCHED_MOOD = 5;
	/** Journeyman and Master: a travelling trainer's ranks. */
	public static final int TRAVELLER_LOWEST = 3;
	public static final int TRAVELLER_HIGHEST = 5;
	/** Players this close to the field read the bouts. */
	public static final double AT_THE_STADIUM = 64;
	/** A visitor leaves only with no player this close. */
	static final double SIGHT = 32;
	static final double SIT_REACH = 2.5;
	static final int TICK_EVERY = 20;
	/** The gate's spot in the Stadium's drawing: out in front of the gateway, on the road. */
	static final BlockPos GATE = new BlockPos(26, 1, -4);

	/** A visitor: the Stadium's village and site, where it came from (none for a traveller), the trainer it stands for, the day, whether it has had its bout, its spot. */
	public record Visitor(BlockPos hall, UUID site, Optional<BlockPos> home, UUID trainer, long day, boolean fought, int spot) {
		public static final Codec<Visitor> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.optionalFieldOf("hall", BlockPos.ZERO).forGetter(Visitor::hall),
			UUIDUtil.CODEC.optionalFieldOf("site", new UUID(0, 0)).forGetter(Visitor::site),
			BlockPos.CODEC.optionalFieldOf("home").forGetter(Visitor::home),
			UUIDUtil.CODEC.optionalFieldOf("trainer", new UUID(0, 0)).forGetter(Visitor::trainer),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Visitor::day),
			Codec.BOOL.optionalFieldOf("fought", false).forGetter(Visitor::fought),
			Codec.INT.optionalFieldOf("spot", 0).forGetter(Visitor::spot)
		).apply(i, Visitor::new));

		Visitor withFought() {
			return new Visitor(hall, site, home, trainer, day, true, spot);
		}
	}

	/**
	 * Where things are at a Stadium: the field's centre, the visitors' spots in the boxes (the first in each box, then the
	 * second), the Training Posts, the seats (each a carpet on a tier) and the gate.
	 */
	public record Spots(BlockPos ring, List<BlockPos> boxes, List<BlockPos> posts, List<BlockPos> seats, BlockPos gate) {
	}

	/** The result of a bout: the winner and the loser, and the XP each gained. */
	public record Bout(Villager winner, Villager loser, int winnerXp, int loserXp) {
	}

	/** One who's coming: their village (none for a traveller), the trainer they stand for, name, rank and experience. */
	private record Comer(Optional<BlockPos> home, UUID trainer, String name, int tier, int xp) {
	}

	/** Each finished site's spots, from its drawing (empty: it has no two Training Posts). */
	private static final Map<UUID, Optional<Spots>> SPOTS = new ConcurrentHashMap<>();

	private StadiumDays() {
	}

	public static void init() {
		Platform.get().onLevelTick(StadiumDays::tick);
	}

	/** Forgets the spots found in drawings (the files were read again). */
	public static void forget() {
		SPOTS.clear();
	}

	/** Uses {@code spots} for {@code site} instead of its drawing's (a test's small Stadium; null: its drawing's again). */
	public static void setSpotsForTest(UUID site, @Nullable Spots spots) {
		if (spots == null) {
			SPOTS.remove(site);
		} else {
			SPOTS.put(site, Optional.of(spots));
		}
	}

	// ---------------------------------------------------------------- where things are

	/** The site's spots: worked out once from its drawing, placed where it stands; empty without a placement or two posts. */
	public static Optional<Spots> spots(ServerLevel level, WonderSite site) {
		Optional<Spots> known = SPOTS.get(site.id());
		if (known != null) {
			return known;
		}
		if (site.placement().isEmpty()) {
			return Optional.empty();
		}
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, site);
		if (blocks.isEmpty()) {
			return Optional.empty(); // its drawing isn't loaded: looked for again next round
		}
		Optional<Spots> found = spots(blocks, site.placement().get());
		SPOTS.put(site.id(), found);
		return found;
	}

	/** The spots in a drawing's blocks as placed: two Training Posts, the field between them, a spot or two before each. */
	static Optional<Spots> spots(List<WonderUpkeep.Block> blocks, BlueprintData.Placement placement) {
		List<BlockPos> posts = new ArrayList<>();
		List<BlockPos> seats = new ArrayList<>();
		for (WonderUpkeep.Block b : blocks) {
			if (b.state().is(ModBlocks.TRAINING_POST)) {
				posts.add(b.pos().immutable());
			} else if (b.state().getBlock() instanceof CarpetBlock) {
				seats.add(b.pos().immutable());
			}
		}
		if (posts.size() < 2) {
			return Optional.empty();
		}
		posts.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		BlockPos a = posts.get(0);
		BlockPos b = posts.get(posts.size() - 1);
		BlockPos ring = new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
		List<BlockPos> first = new ArrayList<>();
		List<BlockPos> second = new ArrayList<>();
		for (BlockPos post : List.of(a, b)) {
			Direction toward = Direction.getNearest(ring.getX() - post.getX(), 0, ring.getZ() - post.getZ());
			BlockPos spot = post.relative(toward);
			first.add(spot);
			second.add(spot.relative(toward.getClockWise()));
		}
		List<BlockPos> boxes = new ArrayList<>(first);
		boxes.addAll(second);
		BlockPos ringPos = ring;
		seats.sort(Comparator.comparingDouble(s -> s.distSqr(ringPos)));
		return Optional.of(new Spots(ring, List.copyOf(boxes), List.of(a, b), List.copyOf(seats), Arenas.world(placement, GATE)));
	}

	/**
	 * The village round {@code hall}'s Stadium as the Festival Cup's Arena ({@code tournament_host}: {@code Arenas.find}
	 * takes it first): its field the ring, its boxes, seats and the gate's road for the fair and the notice board.
	 */
	public static Optional<Arenas.Arena> arena(ServerLevel level, BlockPos hall) {
		if (!Wonders.ENABLED) {
			return Optional.empty();
		}
		Optional<io.github.jcondedata.aliveworkplace.wonder.Wonders.Granted> host = TournamentHostPower.at(level, hall);
		if (host.isEmpty()) {
			return Optional.empty();
		}
		WonderSite site = host.get().site();
		Optional<Spots> spots = spots(level, site);
		if (spots.isEmpty()) {
			return Optional.empty();
		}
		Spots s = spots.get();
		BlueprintData.Placement at = site.placement().orElse(new BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()), s.ring(), Rotation.NONE, Mirror.NONE));
		return Optional.of(new Arenas.Arena(site.wonder(), 3, at, s.ring(), List.of(s.boxes().get(0), s.boxes().get(1)), s.seats(),
			Optional.of(s.gate()), Optional.empty(), s.gate()));
	}

	// ---------------------------------------------------------------- the power

	/** The {@code stadium_days} power {@code site} grants now (finished, standing, Wonders on), or null. */
	@Nullable
	public static StadiumDaysPower power(ServerLevel level, WonderSite site) {
		for (Wonders.Granted g : Wonders.powers(level, site.hall(), StadiumDaysPower.TYPE)) {
			if (g.home() && g.site().id().equals(site.id()) && g.power() instanceof StadiumDaysPower p) {
				return p;
			}
		}
		return null;
	}

	/** Whether the Wonder {@code site} is of a kind whose file names {@code stadium_days}. */
	static boolean hasStadiumDays(WonderSite site) {
		Wonder wonder = Wonders.loaded().get(site.wonder());
		return wonder != null && wonder.powers().stream().anyMatch(p -> p.power() instanceof StadiumDaysPower);
	}

	/** Whether a Stadium Day is on now at {@code site}: its power granted, today one, between the afternoon and dusk. */
	public static boolean on(ServerLevel level, WonderSite site) {
		StadiumDaysPower p = power(level, site);
		long t = level.getDayTime() % 24000L;
		return p != null && p.on(Chronicle.day(level)) && t >= AFTERNOON && t < DUSK;
	}

	/** Whether the stands of a Stadium Day near {@code at} are open (a {@link StandSeat} there keeps its villager). */
	public static boolean standsOpen(ServerLevel level, BlockPos at) {
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (site.done() && hasStadiumDays(site) && on(level, site)) {
				Optional<Spots> spots = spots(level, site);
				if (spots.isPresent() && spots.get().ring().closerThan(at, AT_THE_STADIUM)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the day

	static void tick(ServerLevel level) {
		if (level.getGameTime() % TICK_EVERY != 0) {
			return;
		}
		tick(level, Chronicle.day(level), level.getDayTime() % 24000L);
	}

	/** Every Stadium of {@code level}, one round on {@code day} (as the chronicle counts them) at time of day {@code t}. */
	public static void tick(ServerLevel level, long day, long t) {
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (!site.done() || !hasStadiumDays(site) || !level.isLoaded(site.hall())) {
				continue;
			}
			Optional<Spots> found = spots(level, site);
			if (found.isEmpty() || !level.isLoaded(found.get().ring())) {
				continue;
			}
			Spots spots = found.get();
			StadiumDaysPower p = power(level, site);
			boolean on = p != null && p.on(day) && t >= AFTERNOON && t < DUSK;
			leave(level, site, spots, on, day);
			if (!on) {
				continue;
			}
			Data data = Data.get(level);
			if (data.held(site.id()) != day) {
				arrive(level, site, spots, p, day);
			}
			keep(level, site, spots);
			bouts(level, site, spots, day, t);
			stands(level, site, spots, day);
		}
	}

	/** Who stands in the boxes: the trainers the realm's villages (or the caravan partners) would send, then travellers. */
	static List<Comer> comers(ServerLevel level, WonderSite site, int count, RandomSource random) {
		List<Comer> out = new ArrayList<>();
		for (GlobalPos other : Wonders.linked(level, site.hall())) {
			if (out.size() >= count) {
				break;
			}
			if (!other.dimension().equals(level.dimension())) {
				continue;
			}
			Caravans.Leader l = Cups.delegate(level, other.pos());
			if (l != null) {
				out.add(new Comer(Optional.of(other.pos()), l.id(), l.name(), Math.max(1, Math.min(5, l.tier())), Math.max(1, l.xp())));
			}
		}
		while (out.size() < count) {
			int tier = TRAVELLER_LOWEST + random.nextInt(TRAVELLER_HIGHEST - TRAVELLER_LOWEST + 1);
			out.add(new Comer(Optional.empty(), new UUID(0, 0), "", tier, Math.max(1, VillagerData.getMinXpPerLevel(tier))));
		}
		return out;
	}

	/** The day's random: the same for the same site and day. */
	public static RandomSource random(UUID site, long day) {
		return RandomSource.create(site.getMostSignificantBits() ^ site.getLeastSignificantBits() * 31 ^ day * 0x9E3779B97F4A7C15L);
	}

	/** The visitors come: {@code min_visitors} to {@code max_visitors} (the day's random), each in a box, facing the field. */
	public static List<Villager> arrive(ServerLevel level, WonderSite site, Spots spots, StadiumDaysPower p, long day) {
		Data.get(level).setHeld(site.id(), day);
		RandomSource random = random(site.id(), day);
		int count = Math.min(spots.boxes().size(), p.minVisitors() + random.nextInt(p.maxVisitors() - p.minVisitors() + 1));
		List<Villager> out = new ArrayList<>();
		List<Comer> comers = comers(level, site, count, random);
		for (int i = 0; i < comers.size(); i++) {
			Comer c = comers.get(i);
			Villager v = EntityType.VILLAGER.create(level);
			if (v == null) {
				continue;
			}
			BlockPos spot = spots.boxes().get(i);
			v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw(spot, spots.ring()), 0);
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(c.tier()));
			v.setVillagerXp(c.xp());
			if (c.home().isPresent()) {
				Component village = VillageHalls.name(level, c.home().get());
				v.setCustomName(c.name().isEmpty() ? Component.translatable("entity.aliveworkplace.stadium_visitor", village)
					: Component.translatable("entity.aliveworkplace.cup_delegate", c.name(), village));
			} else {
				v.setCustomName(Component.translatable("entity.aliveworkplace.stadium_traveller"));
			}
			v.setPersistenceRequired();
			ModAttachments.STADIUM_VISITOR.set(v, new Visitor(site.hall().immutable(), site.id(), c.home(), c.trainer(), day, false, i));
			level.addFreshEntity(v);
			out.add(v);
		}
		tell(level, site.hall(), spots, Words.counted("message.aliveworkplace.stadium.day", out.size(), VillageHalls.name(level, site.hall()), out.size())
			.withStyle(ChatFormatting.GOLD));
		return out;
	}

	private static float yaw(BlockPos from, BlockPos to) {
		return (float) (Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX())) - 90);
	}

	public static boolean isVisitor(Villager v) {
		return ModAttachments.STADIUM_VISITOR.has(v);
	}

	@Nullable
	public static Visitor visitor(Villager v) {
		return ModAttachments.STADIUM_VISITOR.get(v);
	}

	/** {@code site}'s visitors, by their spot. */
	public static List<Villager> visitors(ServerLevel level, WonderSite site, Spots spots) {
		List<Villager> out = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(spots.ring()).inflate(AT_THE_STADIUM, 32, AT_THE_STADIUM),
			v -> v.isAlive() && isVisitor(v) && visitor(v).site().equals(site.id())));
		out.sort(Comparator.comparingInt(v -> visitor(v).spot()));
		return out;
	}

	/** The visitors in their boxes: no job, no bed, back to their spot when they stray, looking at the field. */
	static void keep(ServerLevel level, WonderSite site, Spots spots) {
		for (Villager v : visitors(level, site, spots)) {
			CupDays.noJobNoBed(v);
			BlockPos spot = spots.boxes().get(Math.floorMod(visitor(v).spot(), spots.boxes().size()));
			Vec3 at = Vec3.atBottomCenterOf(spot);
			if (v.isPassenger()) {
				continue;
			}
			if (!v.position().closerThan(at, 10)) {
				v.moveTo(at.x, at.y, at.z, yaw(spot, spots.ring()), 0);
			} else if (!v.position().closerThan(at, 1.2)) {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.5f, 0));
			} else {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(spots.ring().above()));
			}
		}
	}

	/** After dusk (or with the power off), the visitors go: gone where nobody sees, else out of the gate. */
	static void leave(ServerLevel level, WonderSite site, Spots spots, boolean on, long day) {
		for (Villager v : visitors(level, site, spots)) {
			if (on && visitor(v).day() == day) {
				continue;
			}
			if (level.getNearestPlayer(v, SIGHT) == null || v.position().closerThan(Vec3.atBottomCenterOf(spots.gate()), 3)) {
				if (v.isPassenger()) {
					v.stopRiding();
				}
				v.discard();
			} else if (!v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spots.gate(), 0.6f, 1));
			}
		}
	}

	// ---------------------------------------------------------------- the bouts

	/** The village's own trainers who take the visitors on: the highest rank first. */
	public static List<Villager> homeTrainers(ServerLevel level, BlockPos hall) {
		List<Villager> out = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && !v.isSleeping() && Trainers.isTrainer(v) && !isVisitor(v) && !CupDays.isDelegate(v)));
		out.sort(Comparator.<Villager>comparingInt(v -> -Trainers.tier(v)).thenComparingInt(v -> -v.getVillagerXp()).thenComparing(Villager::getUUID));
		return out;
	}

	/** The bouts due by {@code t}: one at {@link #FIRST_BOUT}, then one each {@link #BOUT_EVERY}, each visitor's once. */
	static void bouts(ServerLevel level, WonderSite site, Spots spots, long day, long t) {
		if (t < FIRST_BOUT) {
			return;
		}
		int due = (int) ((t - FIRST_BOUT) / BOUT_EVERY) + 1;
		List<Villager> visitors = visitors(level, site, spots);
		int fought = (int) visitors.stream().filter(v -> visitor(v).fought()).count();
		if (fought >= due) {
			return;
		}
		Villager next = visitors.stream().filter(v -> !visitor(v).fought()).findFirst().orElse(null);
		if (next == null) {
			return;
		}
		List<Villager> home = homeTrainers(level, site.hall());
		if (home.isEmpty()) {
			ModAttachments.STADIUM_VISITOR.set(next, visitor(next).withFought()); // nobody to take them on: they wait for players
			return;
		}
		bout(level, site.hall(), spots, home.get(fought % home.size()), next, day);
	}

	/**
	 * A bout between one of the village's trainers and a visitor, settled at once by rank and team strength: the Cup's
	 * exhibition played out with each side's own team ({@code CupBattlers.homeTeam}), its random fixed by the village, the
	 * day and the two (without Pokémon on either side, the higher rank wins, the home side on a tie). Both get battle
	 * XP ({@code Trainers.XP_PER_BATTLE}, the winner {@code XP_FOR_WIN} more), the visitor's own trainer too; the result
	 * goes to the players there and into the village's chronicle.
	 */
	public static Bout bout(ServerLevel level, BlockPos hall, Spots spots, Villager home, Villager visiting, long day) {
		boolean homeFirst = Trainers.tier(home) >= Trainers.tier(visiting);
		Villager a = homeFirst ? home : visiting;
		Villager b = homeFirst ? visiting : home;
		List<CupBout.Fighter> teamA = CupBattlers.EXTENSION.call(x -> x.homeTeam(a.getUUID(), Trainers.tier(a)), List.of());
		List<CupBout.Fighter> teamB = CupBattlers.EXTENSION.call(x -> x.homeTeam(b.getUUID(), Trainers.tier(b)), List.of());
		CupBout bout = new CupBout(a.getUUID(), b.getUUID(), teamA, teamB, 0, CupBout.seed(hall, day, 0, a.getUUID(), b.getUUID()), spots.ring(),
			spots.boxes().get(0), spots.boxes().get(1));
		if (bout.winner < 0) {
			bout.playOut();
		}
		Villager winner = bout.winner == 1 ? b : a;
		Villager loser = winner == a ? b : a;
		int winXp = award(level, winner, true);
		int loseXp = award(level, loser, false);
		Visitor v = visitor(visiting);
		if (v != null) {
			ModAttachments.STADIUM_VISITOR.set(visiting, v.withFought());
		}
		Component line = Component.translatable("message.aliveworkplace.stadium.bout", Trainers.title(winner), Trainers.title(loser));
		tell(level, hall, spots, line.copy().withStyle(ChatFormatting.GOLD));
		Chronicle.atHall(level, hall, Chronicle.Kind.CUP, Component.translatable("chronicle.aliveworkplace.stadium.bout", Trainers.title(winner),
			Trainers.title(loser)), Chronicle.day(level));
		return new Bout(winner, loser, winXp, loseXp);
	}

	/** A bout's battle XP for {@code trainer} (and, for a visitor, the trainer at home they stand for); returns what it gave. */
	static int award(ServerLevel level, Villager trainer, boolean won) {
		int xp = Guilds.trainerXp(trainer, Trainers.XP_PER_BATTLE + (won ? Trainers.XP_FOR_WIN : 0));
		ModAttachments.TRAINER_BATTLES.set(trainer, ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0) + 1);
		BuilderLevels.addXp(level, trainer, xp, null);
		Visitor v = visitor(trainer);
		if (v != null && v.home().isPresent()) {
			if (level.getEntity(v.trainer()) instanceof Villager own && own.isAlive()) {
				ModAttachments.TRAINER_BATTLES.set(own, ModAttachments.TRAINER_BATTLES.getOrElse(own, 0) + 1);
				BuilderLevels.addXp(level, own, Guilds.trainerXp(own, xp), null);
			} else {
				Caravans.Data.get(level).bankXp(v.home().get(), v.trainer(), xp); // paid when that village next loads
			}
		}
		return xp;
	}

	/** Tells the players at the Stadium and in its village. */
	static void tell(ServerLevel level, BlockPos hall, Spots spots, Component message) {
		AABB village = VillageHalls.area(hall);
		for (ServerPlayer player : level.players()) {
			if (player.position().closerThan(Vec3.atCenterOf(spots.ring()), AT_THE_STADIUM) || village.contains(player.position())) {
				player.sendSystemMessage(message);
			}
		}
	}

	// ---------------------------------------------------------------- the stands

	/** The villagers off work go to the stands and sit on a free seat; those seated have watched today. */
	static void stands(ServerLevel level, WonderSite site, Spots spots, long day) {
		AABB around = new AABB(spots.ring()).inflate(AT_THE_STADIUM, 24, AT_THE_STADIUM);
		Set<BlockPos> taken = new HashSet<>();
		for (StandSeat seat : level.getEntitiesOfClass(StandSeat.class, around, Entity::isAlive)) {
			taken.add(seat.blockPosition());
			for (Entity p : seat.getPassengers()) {
				if (p instanceof Villager v) {
					ModAttachments.STADIUM_WATCHED.set(v, day);
				}
			}
		}
		List<BlockPos> free = new ArrayList<>(spots.seats().stream().filter(s -> !taken.contains(s)).toList());
		if (free.isEmpty()) {
			return;
		}
		Map<BlockPos, Boolean> claimed = new HashMap<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(site.hall()).minmax(around), v -> v.isAlive() && !v.isSleeping()
			&& !v.isPassenger() && !v.isBaby() && !isVisitor(v) && !CupDays.isDelegate(v) && !Trainers.isTrainer(v) && CupDays.offWork(v)
			&& VillageHalls.nearest(level, v.blockPosition()).map(site.hall()::equals).orElse(false))) {
			if (free.isEmpty()) {
				return;
			}
			BlockPos seatAt = free.stream().filter(s -> !claimed.containsKey(s))
				.min(Comparator.comparingDouble(s -> v.distanceToSqr(Vec3.atBottomCenterOf(s)))).orElse(null);
			if (seatAt == null) {
				return;
			}
			if (v.position().closerThan(Vec3.atBottomCenterOf(seatAt), SIT_REACH)) {
				StandSeat seat = StandSeat.at(level, seatAt);
				level.addFreshEntity(seat);
				if (v.startRiding(seat, true)) {
					free.remove(seatAt);
					ModAttachments.STADIUM_WATCHED.set(v, day);
					v.getNavigation().stop();
					v.getLookControl().setLookAt(Vec3.atCenterOf(spots.ring()));
				} else {
					seat.discard();
				}
			} else {
				claimed.put(seatAt, true);
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(seatAt, 0.6f, 1));
			}
		}
	}

	/** "watched a Stadium Day" ({@link #WATCHED_MOOD}): from the day they watched until the next Stadium Day's. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		Long watched = ModAttachments.STADIUM_WATCHED.get(villager);
		if (watched == null || Chronicle.day(level) - watched >= StadiumDaysPower.MAX_EVERY) {
			return null;
		}
		int every = 2;
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isPresent()) {
			every = Wonders.powers(level, hall.get(), StadiumDaysPower.TYPE).stream().map(g -> g.power()).filter(p -> p instanceof StadiumDaysPower)
				.mapToInt(p -> ((StadiumDaysPower) p).everyDays()).findFirst().orElse(every);
		}
		if (Chronicle.day(level) - watched >= every) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.stadium"), WATCHED_MOOD);
	}

	// ---------------------------------------------------------------- saved

	/** The day each Stadium last held a Stadium Day (so the visitors come once a day, a restart in the afternoon too). */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_stadium_days";
		private final Map<UUID, Long> held = new HashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		/** The day {@code site} last held one; -1: never. */
		public long held(UUID site) {
			return held.getOrDefault(site, -1L);
		}

		public void setHeld(UUID site, long day) {
			held.put(site, day);
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			CompoundTag sites = new CompoundTag();
			held.forEach((id, day) -> sites.putLong(id.toString(), day));
			tag.put("held", sites);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			CompoundTag sites = io.github.jcondedata.aliveworkplace.mc.Nbt.getCompound(tag, "held");
			for (String key : io.github.jcondedata.aliveworkplace.mc.Nbt.keys(sites)) {
				try {
					data.held.put(UUID.fromString(key), io.github.jcondedata.aliveworkplace.mc.Nbt.getLong(sites, key));
				} catch (IllegalArgumentException e) {
					// a broken key: forgotten
				}
			}
			return data;
		}
	}
}
