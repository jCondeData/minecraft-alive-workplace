package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.WallKits;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sieges (ROADMAP 32.4): a raid by a culture whose {@code tactics} name {@link #TACTIC} on a village with at least one
 * finished wall or gate build ({@link StarterBlueprints#DEFENCES}, their upgrades and styles, and the wall kits'
 * pieces) is a siege.
 * <ul>
 * <li><b>When it begins</b> the village's gates shut and the portcullis of every finished gate build drops: iron bars
 * across the gateway under the drawn-up ones, whatever the hour and with or without guards. Both open again at the
 * first dawn after ({@link ThreatData.Siege#dawn}), once the raid is over.</li>
 * <li><b>The breach</b> is the gate build nearest the side the raiders came from; its gate blocks (fence gates, doors,
 * iron bars; the dropped bars too) are broken in order: the middle way through first, from the outside in, then the
 * ways beside it, and what hangs above head height last (the ram needs that gone to get through itself).</li>
 * <li><b>The rams</b> (role {@code ram}; without one, the raiders with axes) walk to the breach and strike: a ram's
 * blow does {@link #RAM_BLOW} every {@link #RAM_EVERY} ticks, an axe {@link #AXE_BLOW} every {@link #AXE_EVERY}. A
 * fence gate has {@link #FENCE_GATE_HP} hit points, a door {@link #DOOR_HP}, iron bars {@link #IRON_BARS_HP}. Cracks
 * show (the block's destroy progress) and every blow is heard; at 0 the block goes, nothing drops, and it waits for a
 * builder ({@link ThreatData#brokenGates}). The other raiders wait behind the rams and go through once a way is open.</li>
 * <li><b>Only gate blocks of finished builds ever break</b>: the list comes from the builds' blueprints, and a block
 * counts only while the world still holds the blueprint's block there. A player's own gate is never in it. With
 * {@code mobGriefing} off or {@code siegeDamage} false ({@link #DAMAGE}) the rams pound and the gates hold.
 * {@code sieges} false ({@link #ENABLED}) makes every raid a plain one.</li>
 * <li><b>One director a siege</b>, every {@link #EVERY} ticks, asking for at most {@link #MAX_PATHS} paths a tick. Hit
 * points live in the siege's saved data, not on the blocks, so a restart mid-siege carries on where it was.</li>
 * </ul>
 */
public final class Sieges {
	/** Config {@code sieges}: off, every raid is a plain one. */
	public static boolean ENABLED = true;
	/** Config {@code siegeDamage}: off, gates hold however hard they are hit. */
	public static boolean DAMAGE = true;
	/** The tactic a culture names to lay sieges. */
	public static final String TACTIC = "ram_gates";
	/** The director's pace (ticks) and the most paths it asks for in one of its ticks. */
	public static final int EVERY = 20;
	public static final int MAX_PATHS = 4;
	public static final int FENCE_GATE_HP = 60;
	public static final int DOOR_HP = 80;
	public static final int IRON_BARS_HP = 150;
	public static final int RAM_BLOW = 12;
	public static final int RAM_EVERY = 40;
	public static final int AXE_BLOW = 4;
	public static final int AXE_EVERY = 20;
	/** How often (director ticks) the raiders are counted again and the cracks shown again (a client forgets them after 20 s). */
	private static final int REFRESH = 10;

	/** A finished defence build and its blueprint. */
	private record Defence(BuildSiteManager.Finished finished, Blueprint blueprint) {
		BlockPos world(BlockPos local) {
			return finished.placement().origin().offset(StructureTemplate.transform(local, finished.placement().mirror(), finished.placement().rotation(), BlockPos.ZERO));
		}
	}

	/** A gate block in the world: where, where in its blueprint, and whether the siege dropped it (a portcullis bar). */
	private record Piece(BlockPos pos, BlockPos local, BlockState state, boolean dropped) {
	}

	/** How the village's gates stand (the Defence page). */
	public record Status(int gates, int open, int broken, boolean portcullis, boolean down, boolean siege) {
	}

	private record Key(ResourceKey<Level> dimension, BlockPos hall) {
	}

	/** What a siege's director keeps in memory (rebuilt after a restart). */
	private static final class Director {
		List<Mob> raiders = List.of();
		int steps;
		int cursor;
		int most;
		long total;
		final Map<UUID, Long> lastBlow = new HashMap<>();
		/** The raiders under the director's orders now (rams at the gate, the rest waiting or going through). */
		final Set<UUID> ordered = new HashSet<>();
		/** The raiders who went in through the breach (the director leaves them be). */
		final Set<UUID> through = new HashSet<>();
	}

	private static final Map<Key, Director> DIRECTORS = new ConcurrentHashMap<>();
	/** What a raider is to do: a ram breaks the gate block {@code gate}; the others ({@code gate} null) wait or go through. */
	private record Order(@Nullable BlockPos gate) {
		static final Order FOLLOW = new Order(null);
	}

	/** The director's order for each raider (its goal holds it to it). */
	private static final Map<UUID, Order> ORDERS = new ConcurrentHashMap<>();
	/** The mobs that already carry the siege's goal. */
	private static final Set<Mob> GOALED = Collections.newSetFromMap(new WeakHashMap<>());

	public static void init() {
		Threats.registerTactic(TACTIC, new Threats.Tactic() {
			@Override
			public void begin(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
				Sieges.begin(level, hall, culture, raiders);
			}

			@Override
			public void end(ServerLevel level, BlockPos hall, Culture culture, boolean fled) {
				raidOver(level, hall);
			}
		});
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == 0) {
				tick(level);
			}
		});
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets what the directors keep in memory, as a restart does (tests). The sieges themselves are saved. */
	public static void forget() {
		DIRECTORS.clear();
		ORDERS.clear();
	}

	// What can be besieged.

	/** Whether {@code structure} is a wall or gate build: one of {@link StarterBlueprints#DEFENCES} or a wall kit's piece, in any style or tier. */
	public static boolean isDefence(ResourceLocation structure) {
		ResourceLocation base = BlueprintStyles.base(structure);
		for (int i = 0; i < 4; i++) {
			if (defenceIds().contains(base)) {
				return true;
			}
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				return false;
			}
			base = lower.get();
		}
		return false;
	}

	private static Set<ResourceLocation> defenceIds() {
		Set<ResourceLocation> ids = new HashSet<>();
		StarterBlueprints.DEFENCES.forEach(e -> ids.add(e.id()));
		ids.addAll(WallKits.allBlueprints());
		return ids;
	}

	/**
	 * The blueprints whose fence gates are the village's gates (shut at night by {@code guard/Gates}, and in a siege): the
	 * two we ship, and every wall kit's gate (27.18).
	 */
	public static Set<ResourceLocation> gateBuilds() {
		Set<ResourceLocation> out = new java.util.LinkedHashSet<>(Set.of(StarterBlueprints.GATEHOUSE.id(), StarterBlueprints.PALISADE_GATE.id()));
		out.addAll(WallKits.gates());
		return out;
	}

	/** Whether {@code structure} is a gate build (its gates shut, its portcullis drops): one of {@link #gateBuilds}, in any style or tier. */
	private static boolean isGated(ResourceLocation structure) {
		Set<ResourceLocation> gated = gateBuilds();
		ResourceLocation base = BlueprintStyles.base(structure);
		for (int i = 0; i < 4; i++) {
			if (gated.contains(base)) {
				return true;
			}
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				return false;
			}
			base = lower.get();
		}
		return false;
	}

	private static List<Defence> defences(ServerLevel level, BlockPos hall) {
		List<Defence> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (isDefence(f.structure())) {
				BlueprintLibrary.get(level, f.structure()).ifPresent(b -> out.add(new Defence(f, b)));
			}
		}
		return out;
	}

	/** Whether the village round {@code hall} has a finished wall or gate build: a raid on it by a besieging culture is a siege. */
	public static boolean walled(ServerLevel level, BlockPos hall) {
		return !defences(level, hall).isEmpty();
	}

	/** Whether rams can break {@code state} at all: a fence gate, a door, iron bars. */
	public static boolean isGate(BlockState state) {
		return hitPoints(state) > 0;
	}

	/** The hit points of a gate block: fence gate {@link #FENCE_GATE_HP}, door {@link #DOOR_HP}, iron bars {@link #IRON_BARS_HP}; 0 for anything else. */
	public static int hitPoints(BlockState state) {
		if (state.getBlock() instanceof FenceGateBlock) {
			return FENCE_GATE_HP;
		}
		if (state.getBlock() instanceof DoorBlock) {
			return DOOR_HP;
		}
		return state.is(Blocks.IRON_BARS) ? IRON_BARS_HP : 0;
	}

	/** The gate blocks of {@code build} that stand in the world as its blueprint has them (a door counts once, by its lower half). */
	private static List<Piece> pieces(ServerLevel level, Defence build) {
		List<Piece> out = new ArrayList<>();
		for (Blueprint.Entry e : build.blueprint().blocks()) {
			if (!isGate(e.state()) || e.state().getBlock() instanceof DoorBlock && e.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}
			BlockPos pos = build.world(e.pos());
			BlockState there = level.getBlockState(pos);
			if (there.getBlock() == e.state().getBlock()) {
				out.add(new Piece(pos, e.pos(), there, false));
			}
		}
		return out;
	}

	// The siege.

	/** The siege laid to the village round {@code hall}, from when it begins until the first dawn after. */
	public static Optional<ThreatData.Siege> siege(ServerLevel level, BlockPos hall) {
		return ThreatData.get(level).siege(hall);
	}

	/** Whether the gates of the village round {@code hall} are held shut by a siege. */
	public static boolean shut(ServerLevel level, BlockPos hall) {
		return siege(level, hall).isPresent();
	}

	/** The day time of the first dawn (the hour the gates open, 23500) after {@code dayTime}. */
	public static long nextDawn(long dayTime) {
		long day = Math.floorDiv(dayTime, VillageNeeds.DAY);
		long dawn = day * VillageNeeds.DAY + 23500;
		return dawn > dayTime ? dawn : dawn + VillageNeeds.DAY;
	}

	/**
	 * A raid by {@code culture} has begun on the village round {@code hall}: if sieges are on and the village has a
	 * finished wall or gate build it is a siege. The gates shut, the portcullis drops, the breach is picked on the side
	 * {@code raiders} stand, and the director takes over. Empty when it stays a plain raid.
	 */
	public static Optional<ThreatData.Siege> begin(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
		if (!ENABLED) {
			return Optional.empty();
		}
		List<Defence> builds = defences(level, hall);
		if (builds.isEmpty()) {
			return Optional.empty();
		}
		ThreatData data = ThreatData.get(level);
		data.siege(hall).ifPresent(old -> lift(level, old)); // (the last one's bars go up before this one's come down)
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), nextDawn(level.getDayTime()));
		Vec3 from = Vec3.atCenterOf(hall);
		if (!raiders.isEmpty()) {
			from = Vec3.ZERO;
			for (Mob mob : raiders) {
				from = from.add(mob.position());
			}
			from = from.scale(1.0 / raiders.size());
		}
		setGates(level, builds, true);
		Map<Defence, List<Piece>> bars = new HashMap<>();
		for (Defence build : builds) {
			if (isGated(build.finished().structure())) {
				bars.put(build, drop(level, build, siege));
			}
		}
		pickBreach(level, siege, builds, bars, from);
		data.beginSiege(siege);
		prune(level, data, hall);
		DIRECTORS.remove(new Key(level.dimension(), hall));
		Component name = VillageHalls.name(level, hall);
		String fallback = siege.portcullis.isEmpty() ? "message.aliveworkplace.siege.begins" : "message.aliveworkplace.siege.begins_portcullis";
		tell(level, hall, Component.translatable(culture.messages().key("siege", fallback), name).withStyle(ChatFormatting.RED));
		return Optional.of(siege);
	}

	/**
	 * Drops the portcullis of {@code build}: under every iron bar its blueprint draws up, bars down through the gateway
	 * (where the blueprint has air) to the floor. Returns the bars dropped, which the siege remembers to draw up again.
	 */
	private static List<Piece> drop(ServerLevel level, Defence build, ThreatData.Siege siege) {
		Map<BlockPos, BlockState> plan = new HashMap<>();
		for (Blueprint.Entry e : build.blueprint().blocks()) {
			plan.put(e.pos(), e.state());
		}
		List<Piece> dropped = new ArrayList<>();
		for (Blueprint.Entry e : build.blueprint().blocks()) {
			if (!e.state().is(Blocks.IRON_BARS) || !level.getBlockState(build.world(e.pos())).is(Blocks.IRON_BARS)) {
				continue;
			}
			for (BlockPos local = e.pos().below(); local.getY() >= 0; local = local.below()) {
				BlockState wanted = plan.get(local);
				if (wanted != null && !wanted.isAir()) {
					break;
				}
				BlockPos pos = build.world(local);
				BlockState there = level.getBlockState(pos);
				if (!there.isAir() && !there.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, Block.updateFromNeighbourShapes(Blocks.IRON_BARS.defaultBlockState(), level, pos), 3);
				siege.portcullis.add(pos.immutable());
				dropped.add(new Piece(pos.immutable(), local, level.getBlockState(pos), true));
			}
		}
		if (!dropped.isEmpty()) {
			level.playSound(null, dropped.get(0).pos(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7f, 0.6f);
		}
		return dropped;
	}

	/**
	 * Picks the breach: the gate build with the gate block nearest {@code from} (the raiders' side), and the order its
	 * gate blocks are broken in (see the class comment).
	 */
	private static void pickBreach(ServerLevel level, ThreatData.Siege siege, List<Defence> builds, Map<Defence, List<Piece>> bars, Vec3 from) {
		Defence breach = null;
		List<Piece> its = List.of();
		double best = Double.MAX_VALUE;
		for (Defence build : builds) {
			List<Piece> pieces = new ArrayList<>(pieces(level, build));
			pieces.addAll(bars.getOrDefault(build, List.of()));
			for (Piece piece : pieces) {
				double d = Vec3.atCenterOf(piece.pos()).distanceToSqr(from);
				if (d < best) {
					best = d;
					breach = build;
					its = pieces;
				}
			}
		}
		if (breach == null) {
			return; // walls without a gate: nothing for a ram to do
		}
		int floor = its.stream().mapToInt(p -> p.local().getY()).min().orElse(0);
		List<Integer> ways = new ArrayList<>(new TreeSet<>(its.stream().filter(p -> p.local().getY() - floor < 2).map(p -> p.local().getX()).toList()));
		int middle = ways.get(ways.size() / 2);
		ways.sort(Comparator.<Integer>comparingInt(x -> Math.abs(x - middle)).thenComparingInt(x -> x));
		Comparator<Piece> order = Comparator.<Piece>comparingInt(p -> p.local().getY() - floor < 2 ? 0 : 1)
			.thenComparingInt(p -> lane(ways, middle, p.local().getX()))
			.thenComparingDouble(p -> Vec3.atCenterOf(p.pos()).distanceToSqr(from));
		List<Piece> sorted = new ArrayList<>(its);
		sorted.sort(order);
		for (Piece piece : sorted) {
			siege.gates.add(new ThreatData.Gate(piece.pos(), hitPoints(piece.state()), lane(ways, middle, piece.local().getX()),
				piece.local().getY() - floor >= 2, piece.dropped()));
		}
		siege.breach = sorted.get(0).pos();
		// The outside is the back of the blueprint (its front, z = 0, faces the village), unless the raiders stand the other side.
		Direction out = breach.finished().placement().rotation().rotate(breach.finished().placement().mirror().mirror(Direction.SOUTH));
		Vec3 toward = from.subtract(Vec3.atCenterOf(siege.breach));
		if (toward.x * out.getStepX() + toward.z * out.getStepZ() < 0) {
			out = out.getOpposite();
		}
		siege.out = out;
	}

	/** Which way through a gate block at the blueprint's {@code x} belongs to: 0 for the middle one, then outward. */
	private static int lane(List<Integer> ways, int middle, int x) {
		int i = ways.indexOf(x);
		return i >= 0 ? i : ways.size() + Math.abs(x - middle);
	}

	/** The raid on the village round {@code hall} is over: the rams let go; the gates stay shut till dawn. */
	public static void raidOver(ServerLevel level, BlockPos hall) {
		ThreatData data = ThreatData.get(level);
		data.siege(hall).ifPresent(siege -> {
			if (!siege.over) {
				siege.over = true;
				data.setDirty();
			}
			release(DIRECTORS.get(new Key(level.dimension(), hall)));
		});
	}

	private static void release(@Nullable Director director) {
		if (director != null) {
			director.ordered.forEach(ORDERS::remove);
			director.ordered.clear();
		}
	}

	/**
	 * The siege is lifted: the bars it dropped are drawn up, the cracks go, the gates open if it's day, and it is
	 * forgotten. The gate blocks the rams broke stay on the list for a builder.
	 */
	public static void lift(ServerLevel level, ThreatData.Siege siege) {
		ThreatData data = ThreatData.get(level);
		boolean raised = false;
		for (BlockPos pos : siege.portcullis) {
			if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				raised = true;
			}
		}
		if (raised) {
			level.playSound(null, siege.portcullis.get(0), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
		}
		for (ThreatData.Gate gate : siege.gates) {
			level.destroyBlockProgress(crackId(gate.pos), gate.pos, -1);
		}
		release(DIRECTORS.remove(new Key(level.dimension(), siege.hall)));
		data.endSiege(siege.hall);
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time < 12500 || time >= 23500) { // (by night the gates stay as the guards keep them)
			setGates(level, defences(level, siege.hall), false);
		}
		prune(level, data, siege.hall);
	}

	/** Shuts ({@code shut}) or opens the fence gates of the gate builds among {@code builds}. */
	private static void setGates(ServerLevel level, List<Defence> builds, boolean shut) {
		for (Defence build : builds) {
			if (!isGated(build.finished().structure())) {
				continue;
			}
			BlockPos first = null;
			for (Piece piece : pieces(level, build)) {
				if (piece.state().getBlock() instanceof FenceGateBlock && piece.state().getValue(FenceGateBlock.OPEN) == shut) {
					level.setBlock(piece.pos(), piece.state().setValue(FenceGateBlock.OPEN, !shut), 10);
					first = first == null ? piece.pos() : first;
				}
			}
			if (first != null) {
				level.playSound(null, first, shut ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1f, 0.9f);
			}
		}
	}

	/** Takes the gate blocks that are back off the hall's list of broken ones. */
	private static void prune(ServerLevel level, ThreatData data, BlockPos hall) {
		for (BlockPos pos : data.brokenGates(hall)) {
			if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
				data.gateMended(hall, pos);
			}
		}
	}

	// The director.

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Siege siege : data.sieges()) {
			step(level, data, siege);
		}
	}

	/** One tick of a siege's director. */
	private static void step(ServerLevel level, ThreatData data, ThreatData.Siege siege) {
		Director director = DIRECTORS.computeIfAbsent(new Key(level.dimension(), siege.hall), k -> new Director());
		if (!siege.over && data.raid(siege.hall).isEmpty()) {
			siege.over = true;
			data.setDirty();
		}
		if (siege.over) {
			release(director);
			long time = level.getDayTime();
			if (time >= siege.dawn || time < siege.dawn - 2 * VillageNeeds.DAY) { // (or the clock was set back: don't wait days)
				lift(level, siege);
			}
			return;
		}
		if (director.steps % REFRESH == 0) {
			director.raiders = raiders(level, siege.hall);
			showCracks(level, siege);
		}
		director.steps++;
		List<Mob> raiders = director.raiders.stream().filter(m -> m.isAlive() && !m.isRemoved()).toList();
		ThreatData.Gate target = siege.breach == null ? null : next(level, data, siege);
		long now = level.getGameTime();
		int paths = 0;
		Set<UUID> ramming = new HashSet<>();
		Set<UUID> ordered = new HashSet<>();
		if (target != null) {
			BlockPos stand = new BlockPos(target.pos.getX() + siege.out.getStepX(), siege.breach.getY(), target.pos.getZ() + siege.out.getStepZ());
			for (Mob ram : rams(raiders)) {
				ramming.add(ram.getUUID());
				order(ram, new Order(target.pos), ordered);
				if (inReach(ram, target.pos)) {
					ram.getNavigation().stop();
					boolean heavy = Threats.role(ram) == Culture.Role.RAM;
					// (once it went, the others' blows wait for the next tick, which picks the next block)
					if (target.state == ThreatData.GateState.STANDING
						&& now - director.lastBlow.getOrDefault(ram.getUUID(), Long.MIN_VALUE / 2) >= (heavy ? RAM_EVERY : AXE_EVERY)) {
						director.lastBlow.put(ram.getUUID(), now);
						blow(level, data, siege, target, ram, heavy ? RAM_BLOW : AXE_BLOW);
					}
				} else if (ram.getNavigation().isDone() && paths < MAX_PATHS) {
					ram.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0);
					paths++;
				}
			}
		}
		// The rest: behind the rams while the way is shut, through it once it's open. A raider with a foe in sight fights
		// on (its goal lets go); one that is through is left to its own fight.
		if (siege.breach != null && !raiders.isEmpty()) {
			boolean open = open(siege);
			Vec3 gate = Vec3.atBottomCenterOf(siege.breach);
			Vec3 within = gate.add(siege.out.getStepX() * -6, 0, siege.out.getStepZ() * -6);
			int n = raiders.size();
			for (int i = 0; i < n; i++) {
				int index = (director.cursor + i) % n;
				Mob mob = raiders.get(index);
				if (ramming.contains(mob.getUUID())) {
					continue;
				}
				if (open && !director.through.contains(mob.getUUID()) && mob.position().distanceToSqr(within) < 16) {
					director.through.add(mob.getUUID());
				}
				if (director.through.contains(mob.getUUID())) {
					continue;
				}
				order(mob, Order.FOLLOW, ordered);
				if (paths >= MAX_PATHS || !mob.getNavigation().isDone() || busy(mob)) {
					continue;
				}
				if (open) {
					mob.getNavigation().moveTo(within.x, within.y, within.z, 1.0);
					paths++;
				} else {
					// Five blocks out from the gate, spread along the wall so they don't stand in one heap.
					double along = (index % 5 - 2) * 1.5;
					Vec3 behind = gate.add(siege.out.getStepX() * 5 + siege.out.getStepZ() * along, 0, siege.out.getStepZ() * 5 + siege.out.getStepX() * along);
					if (mob.position().distanceToSqr(behind) > 16) {
						mob.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.0);
						paths++;
					}
				}
			}
			director.cursor = (director.cursor + MAX_PATHS) % n;
		}
		director.ordered.removeIf(id -> {
			if (!ordered.contains(id)) {
				ORDERS.remove(id);
				return true;
			}
			return false;
		});
		director.ordered.addAll(ordered);
		director.most = Math.max(director.most, paths);
		director.total += paths;
	}

	/** The raiders still about in the village round {@code hall} (as {@code VillageRaids.raiders} finds them). */
	private static List<Mob> raiders(ServerLevel level, BlockPos hall) {
		int r = VillageHalls.RADIUS + 32;
		return level.getEntitiesOfClass(Mob.class, new AABB(hall).inflate(r, 48, r), m -> m.isAlive() && m.getTags().contains(Threats.RAIDER_TAG));
	}

	/** The next gate block to break: the first in the siege's order that still stands in the world. */
	@Nullable
	private static ThreatData.Gate next(ServerLevel level, ThreatData data, ThreatData.Siege siege) {
		for (ThreatData.Gate gate : siege.gates) {
			if (gate.state != ThreatData.GateState.STANDING) {
				continue;
			}
			if (isGate(level.getBlockState(gate.pos))) {
				return gate;
			}
			gate.state = ThreatData.GateState.GONE; // someone else took it (a player, most likely): the way is open there
			level.destroyBlockProgress(crackId(gate.pos), gate.pos, -1);
			data.setDirty();
		}
		return null;
	}

	/** Whether the first way through the breach is open: none of its blocks at walking height still stands. */
	public static boolean open(ThreatData.Siege siege) {
		for (ThreatData.Gate gate : siege.gates) {
			if (gate.lane == 0 && !gate.high && gate.state == ThreatData.GateState.STANDING) {
				return false;
			}
		}
		return true;
	}

	/** The raiders who break gates: those with the role {@code ram}; without one, those with an axe in hand. */
	private static List<Mob> rams(List<Mob> raiders) {
		List<Mob> rams = raiders.stream().filter(m -> Threats.role(m) == Culture.Role.RAM).toList();
		return rams.isEmpty() ? raiders.stream().filter(m -> m.getMainHandItem().getItem() instanceof AxeItem).toList() : rams;
	}

	private static boolean inReach(Mob mob, BlockPos pos) {
		double dx = mob.getX() - (pos.getX() + 0.5);
		double dz = mob.getZ() - (pos.getZ() + 0.5);
		double reach = mob.getBbWidth() / 2 + 1.75;
		return dx * dx + dz * dz <= reach * reach && Math.abs(mob.getY() - pos.getY()) <= 4;
	}

	/** One blow of {@code ram} on {@code gate}: seen and heard always; it costs hit points unless the gates hold. */
	private static void blow(ServerLevel level, ThreatData data, ThreatData.Siege siege, ThreatData.Gate gate, Mob ram, int damage) {
		BlockState state = level.getBlockState(gate.pos);
		boolean iron = state.is(Blocks.IRON_BARS);
		if (ram instanceof Ravager) {
			level.broadcastEntityEvent(ram, (byte) 4); // its head butt
		} else {
			ram.swing(InteractionHand.MAIN_HAND);
		}
		ram.getLookControl().setLookAt(Vec3.atCenterOf(gate.pos));
		level.playSound(null, gate.pos, iron ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2f,
			0.8f + level.random.nextFloat() * 0.2f);
		if (!damaging(level)) {
			return;
		}
		gate.hp -= damage;
		data.setDirty();
		if (gate.hp > 0) {
			level.destroyBlockProgress(crackId(gate.pos), gate.pos, cracks(gate.hp, hitPoints(state)));
			return;
		}
		gate.hp = 0;
		gate.state = ThreatData.GateState.BROKEN;
		level.destroyBlockProgress(crackId(gate.pos), gate.pos, -1);
		level.destroyBlock(gate.pos, false, ram); // nothing drops; a door's other half goes with it
		if (!iron) {
			level.playSound(null, gate.pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2f, 1f);
		}
		if (!gate.dropped) {
			data.gateBroken(siege.hall, gate.pos);
		}
		if (!siege.breached) {
			siege.breached = true;
			tell(level, siege.hall, Component.translatable("message.aliveworkplace.siege.breached", VillageHalls.name(level, siege.hall)).withStyle(ChatFormatting.RED));
		}
	}

	/** Whether rams' blows cost gates hit points here: {@code siegeDamage} on and the {@code mobGriefing} rule on. */
	public static boolean damaging(ServerLevel level) {
		return DAMAGE && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** The destroy stage (0 to 9) of a gate block with {@code hp} of {@code max} hit points left. */
	public static int cracks(int hp, int max) {
		return max <= 0 ? 0 : Math.max(0, Math.min(9, (max - hp) * 10 / max));
	}

	/** The id the block's cracks are shown under (never an entity's: those are positive). */
	private static int crackId(BlockPos pos) {
		return -1 - (pos.hashCode() & 0x3fffffff);
	}

	private static void showCracks(ServerLevel level, ThreatData.Siege siege) {
		for (ThreatData.Gate gate : siege.gates) {
			if (gate.state == ThreatData.GateState.STANDING) {
				int max = hitPoints(level.getBlockState(gate.pos));
				if (max > 0 && gate.hp < max) {
					level.destroyBlockProgress(crackId(gate.pos), gate.pos, cracks(gate.hp, max));
				}
			}
		}
	}

	private static void tell(ServerLevel level, BlockPos hall, Component message) {
		double reach = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= reach * reach)) {
			Chat.chat(player, message);
		}
	}

	// What others read.

	/** The hit points left of the gate block at {@code pos} in the siege of the village round {@code hall}; -1 if the siege has no such block. */
	public static int hitPointsLeft(ServerLevel level, BlockPos hall, BlockPos pos) {
		return siege(level, hall).flatMap(s -> s.gates.stream().filter(g -> g.pos.equals(pos)).findFirst()).map(g -> g.hp).orElse(-1);
	}

	/** The most paths the director of the siege of the village round {@code hall} asked for in one of its ticks (-1: no director yet). */
	public static int mostPaths(ServerLevel level, BlockPos hall) {
		Director director = DIRECTORS.get(new Key(level.dimension(), hall));
		return director == null ? -1 : director.most;
	}

	/** All the paths that director asked for so far. */
	public static long pathsAsked(ServerLevel level, BlockPos hall) {
		Director director = DIRECTORS.get(new Key(level.dimension(), hall));
		return director == null ? 0 : director.total;
	}

	/** Whether a portcullis of the village round {@code hall} is down. */
	public static boolean portcullisDown(ServerLevel level, BlockPos hall) {
		return siege(level, hall).map(s -> s.portcullis.stream().anyMatch(p -> level.getBlockState(p).is(Blocks.IRON_BARS))).orElse(false);
	}

	/** How the gates of the village round {@code hall} stand, for the Defence page. */
	public static Status status(ServerLevel level, BlockPos hall) {
		ThreatData data = ThreatData.get(level);
		prune(level, data, hall);
		int gates = 0;
		int open = 0;
		boolean portcullis = false;
		for (Defence build : defences(level, hall)) {
			if (!isGated(build.finished().structure())) {
				continue;
			}
			for (Piece piece : pieces(level, build)) {
				if (piece.state().getBlock() instanceof FenceGateBlock) {
					gates++;
					open += piece.state().getValue(FenceGateBlock.OPEN) ? 1 : 0;
				} else if (piece.state().is(Blocks.IRON_BARS)) {
					portcullis = true;
				}
			}
		}
		return new Status(gates, open, data.brokenGates(hall).size(), portcullis, portcullisDown(level, hall), data.siege(hall).isPresent());
	}

	/** Gives {@code mob} its order, and the goal that holds it to it if it hasn't got one. */
	private static void order(Mob mob, Order order, Set<UUID> ordered) {
		ordered.add(mob.getUUID());
		ORDERS.put(mob.getUUID(), order);
		if (GOALED.add(mob)) {
			((MobAccessor) mob).aliveworkplace$goals().addGoal(1, new SiegeGoal(mob));
		}
	}

	/** Whether {@code mob} has a foe in sight: then it fights, and the director doesn't send it anywhere. */
	private static boolean busy(Mob mob) {
		return mob.getTarget() != null && mob.getTarget().isAlive() && mob.getSensing().hasLineOfSight(mob.getTarget());
	}

	/**
	 * Holds a raider to the director's order: a ram at the gate, whatever else it would rather do; the others where the
	 * director walks them (behind the rams, then through the breach) for as long as they have no foe in sight. Without
	 * it their own fighting would walk them round the walls after villagers they can't reach.
	 */
	private static final class SiegeGoal extends Goal {
		private final Mob mob;

		SiegeGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Order order = ORDERS.get(mob.getUUID());
			return order != null && (order.gate() != null || !busy(mob));
		}

		@Override
		public void tick() {
			Order order = ORDERS.get(mob.getUUID());
			if (order != null && order.gate() != null) {
				mob.getLookControl().setLookAt(Vec3.atCenterOf(order.gate()));
			}
		}
	}

	private Sieges() {
	}
}
