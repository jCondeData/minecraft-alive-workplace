package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Witch covens (ROADMAP 32.11): what the witches of a culture whose {@code tactics} name {@link #TACTIC} do, and their
 * lair, the Coven Hut.
 * <ul>
 * <li><b>The hex tactic.</b> No rams, no ladders: in a raid every witch keeps {@link #NEAREST} to {@link #FARTHEST}
 * blocks outside the village's finished walls (it stands {@link #STAND} beyond the outermost wall on its side) and
 * throws splash potions over them every {@link #THROW_EVERY} ticks at whoever is within {@link #REACH} blocks: slowness,
 * weakness or poison at villagers, harming at guards; a witch of the raid that is hurt gets a potion of healing from
 * another instead. A village without walls: they come within {@link #UNWALLED} blocks of a villager and throw from there.
 * A goal holds them to it ({@link HoldGoal}): their own would walk them to the walls.</li>
 * <li><b>The nightly hex.</b> Every night a coven's lair stands (its culture switched on), it hexes one to three of the
 * village's grown villagers who aren't yet ({@link Sickness#hex}): a swirl of witch particles, an illness the nurse's
 * remedies don't touch and that doesn't pass by itself. The night is remembered ({@link Data}, saved), so a restart
 * doesn't hex twice.</li>
 * <li><b>Breaking the coven.</b> The Coven Mother killed (any lair's captain), or her cauldron broken: the one cauldron of
 * the Coven Hut, by a player or anything else. Either way the lair is broken up and every hexed villager of the village
 * is cured at once; one who was out of loaded ground is cured on the hall's next round ({@link #cursed}).</li>
 * <li><b>The Coven Mother blinks</b> (a captain whose file says {@code blinks}): after that many hits in a row (none more
 * than {@link #IN_A_ROW} ticks after the last) she is gone, in ender particles, to somewhere 8 to 12 blocks off.</li>
 * </ul>
 */
public final class Covens {
	/** The tactic a culture names for this. */
	public static final String TACTIC = "hex";
	/** The nearest a raiding witch stands to a wall, and the farthest. */
	public static final int NEAREST = 12;
	public static final int FARTHEST = 20;
	/** Where she stands: this far beyond the outermost wall on her side. */
	public static final int STAND = 16;
	/** How far she throws. */
	public static final int REACH = 28;
	/** Without walls: how near a villager she comes. */
	public static final int UNWALLED = 10;
	/** How often (ticks) a witch throws. */
	public static final int THROW_EVERY = 60;
	/** How often (ticks) the witches are steered. */
	public static final int EVERY = 10;
	/** How often (ticks) the covens are looked at: their cauldron, tonight's hex. */
	public static final int CHECK_EVERY = 40;
	/** Hits on the Coven Mother no more than this many ticks apart are in a row. */
	public static final int IN_A_ROW = 100;
	/** How far round a lair's middle its cauldron is looked for. */
	private static final int CAULDRON_REACH = 10;

	private static final Map<UUID, Long> THROWN = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HELD = new ConcurrentHashMap<>();
	private static final Map<UUID, Hits> HITS = new ConcurrentHashMap<>();
	private static final Map<BlockPos, BlockPos> CAULDRONS = new ConcurrentHashMap<>();
	private static final Set<Mob> GOALED = Collections.newSetFromMap(new WeakHashMap<>());

	private record Hits(int count, long last) {
	}

	public static void init() {
		Threats.registerTactic(TACTIC, new Threats.Tactic() {
		});
		Platform.get().onLevelTick(level -> {
			long now = level.getGameTime();
			if (now % EVERY == 0) {
				steer(level);
			}
			if (now % CHECK_EVERY == 0) {
				covens(level);
			}
		});
		Platform.get().afterDamage((entity, source, base, taken, blocked) -> {
			if (!blocked && entity.level() instanceof ServerLevel level && entity instanceof Mob mob && mob.isAlive()
				&& mob.getTags().contains(Lairs.CAPTAIN_TAG)) {
				hit(level, mob);
			}
		});
		Platform.get().allowBreakBlock((level, player, pos, state) -> {
			if (level instanceof ServerLevel server && state.getBlock() instanceof AbstractCauldronBlock) {
				cauldronBroken(server, pos, player);
			}
			return true;
		});
		Lairs.onBroken((level, lair, by) -> {
			CAULDRONS.remove(lair.pos());
			if (hexes(lair.culture())) {
				lift(level, lair.hall());
			}
		});
	}

	/** Forgets what it holds in memory (tests). */
	public static void forget() {
		THROWN.clear();
		HELD.clear();
		HITS.clear();
		CAULDRONS.clear();
	}

	/** Whether the culture {@code id} hexes: its file names {@link #TACTIC}. */
	public static boolean hexes(ResourceLocation id) {
		return Threats.get(id).map(c -> c.tactics().contains(TACTIC)).orElse(false);
	}

	// The hex.

	/**
	 * Whether {@code villager} is still under a coven's hex: a lair of a culture that hexes, switched on, stands by a
	 * village whose ground they are in or near. Once there is none their hex lifts ({@link Sickness}).
	 */
	public static boolean cursed(ServerLevel level, Villager villager) {
		if (Threats.gentle()) {
			return false; // a gentle threat level has no hexes (32.21)
		}
		double reach = VillageHalls.RADIUS + 16.0;
		for (Lairs.Lair lair : Lairs.all(level)) {
			if (hexes(lair.culture()) && Threats.enabled(lair.culture())) {
				double dx = lair.hall().getX() + 0.5 - villager.getX();
				double dz = lair.hall().getZ() + 0.5 - villager.getZ();
				if (dx * dx + dz * dz <= reach * reach) {
					return true;
				}
			}
		}
		return false;
	}

	/** The coven by the village round {@code hall} is broken: every hexed villager there is cured at once. Returns how many. */
	public static int lift(ServerLevel level, BlockPos hall) {
		int cured = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall).inflate(16), Villager::isAlive)) {
			if (Sickness.isHexed(v) && !cursed(level, v)) {
				Sickness.recover(level, v);
				cured++;
			}
		}
		return cured;
	}

	/**
	 * Tonight's hex by {@code lair}: one to three of the village's grown villagers who aren't hexed yet (rolled with
	 * {@code random}). Returns those hexed; the night is remembered.
	 */
	public static List<Villager> hexNight(ServerLevel level, Lairs.Lair lair, RandomSource random) {
		Data.get(level).hexed(lair.hall(), Chronicle.day(level));
		List<Villager> open = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(lair.hall()),
			v -> v.isAlive() && !v.isBaby() && !Sickness.isHexed(v)));
		Collections.shuffle(open, new java.util.Random(random.nextLong()));
		int count = Math.min(open.size(), 1 + random.nextInt(3));
		List<Villager> hexed = new ArrayList<>(open.subList(0, count));
		hexed.forEach(v -> Sickness.hex(level, v));
		for (Villager v : hexed) {
			if (Sickness.isHexed(v) && Plague.carried(level, v, random)) {
				break; // a hexed villager may bring the plague (32.18)
			}
		}
		if (!hexed.isEmpty()) {
			Culture culture = Threats.get(lair.culture()).orElse(null);
			Component name = VillageHalls.name(level, lair.hall());
			double r = VillageHalls.RADIUS + 32.0;
			for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(lair.hall()) <= r * r)) {
				Chat.chat(player, Words.countedIfAny(culture == null ? "message.aliveworkplace.coven.hex"
					: culture.messages().key("hex", "message.aliveworkplace.coven.hex"), hexed.size(), name, hexed.size()).withStyle(ChatFormatting.DARK_PURPLE));
			}
			Chronicle.atHall(level, lair.hall(), Chronicle.Kind.RAID, Words.countedIfAny(culture == null ? "chronicle.aliveworkplace.coven.hex"
				: culture.chronicle().key("hex", "chronicle.aliveworkplace.coven.hex"), hexed.size(), hexed.size()));
		}
		return hexed;
	}

	private static boolean night(ServerLevel level) {
		long t = level.getDayTime() % 24000;
		return t >= 13000 && t < 23000;
	}

	/** Every coven: its cauldron still stands (else it is broken), and tonight's hex if it hasn't been laid. */
	private static void covens(ServerLevel level) {
		for (Lairs.Lair lair : Lairs.all(level)) {
			if (!hexes(lair.culture())) {
				continue;
			}
			if (level.isPositionEntityTicking(lair.pos()) && cauldron(level, lair) == null) {
				Lairs.breakUp(level, lair, null); // her cauldron is gone: by a creeper, by fire, by a player we didn't see
				continue;
			}
			if (Threats.enabled(lair.culture()) && night(level) && level.isLoaded(lair.hall()) && !Threats.gentle() && !Threats.quiet(level, lair.hall()) // (no hexes at a gentle threat level or in a quiet village, 32.21)
				&& Data.get(level).lastHexed(lair.hall()) < Chronicle.day(level)) {
				hexNight(level, lair, level.random);
			}
		}
	}

	/** The coven's cauldron by {@code lair}, the one nearest its middle; null when none is left. */
	@Nullable
	public static BlockPos cauldron(ServerLevel level, Lairs.Lair lair) {
		BlockPos known = CAULDRONS.get(lair.pos());
		if (known != null && level.getBlockState(known).getBlock() instanceof AbstractCauldronBlock) {
			return known;
		}
		BlockPos best = null;
		BlockPos c = lair.pos();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-CAULDRON_REACH, -6, -CAULDRON_REACH), c.offset(CAULDRON_REACH, 4, CAULDRON_REACH))) {
			if (level.getBlockState(p).getBlock() instanceof AbstractCauldronBlock && (best == null || p.distSqr(c) < best.distSqr(c))) {
				best = p.immutable();
			}
		}
		if (best != null) {
			CAULDRONS.put(lair.pos(), best);
		} else {
			CAULDRONS.remove(lair.pos());
		}
		return best;
	}

	/** A player is breaking the cauldron at {@code pos}: if it is a coven's, the coven is broken, by them. */
	private static void cauldronBroken(ServerLevel level, BlockPos pos, @Nullable Entity by) {
		for (Lairs.Lair lair : Lairs.all(level)) {
			if (hexes(lair.culture()) && pos.equals(cauldron(level, lair))) {
				level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.5, 0.6, 0.5, 0.1);
				level.playSound(null, pos, SoundEvents.WITCH_DEATH, SoundSource.HOSTILE, 1.2f, 0.8f);
				Lairs.breakUp(level, lair, by);
				return;
			}
		}
	}

	// The Coven Mother.

	/** A lair's captain was hit: a captain who blinks counts the hits in a row, and blinks away at the last. */
	private static void hit(ServerLevel level, Mob mob) {
		Optional<Culture.Blink> blink = Optional.empty();
		for (Lairs.Lair lair : Lairs.all(level)) {
			if (lair.captain().equals(mob.getUUID())) {
				blink = Threats.get(lair.culture()).flatMap(Culture::captain).flatMap(Culture.Captain::blinks);
				break;
			}
		}
		if (blink.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Hits last = HITS.get(mob.getUUID());
		int count = last == null || now - last.last() > IN_A_ROW ? 1 : last.count() + 1;
		if (count >= blink.get().after()) {
			HITS.remove(mob.getUUID());
			blink(level, mob, blink.get(), level.random);
		} else {
			HITS.put(mob.getUUID(), new Hits(count, now));
		}
	}

	/** {@code mob} blinks away to somewhere {@code near} to {@code far} blocks off, in ender particles; true if she found somewhere. */
	public static boolean blink(ServerLevel level, Mob mob, Culture.Blink blink, RandomSource random) {
		Vec3 from = mob.position();
		for (int tries = 0; tries < 24; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = blink.near() + random.nextDouble() * (blink.far() - blink.near());
			double x = from.x + Math.cos(angle) * distance;
			double z = from.z + Math.sin(angle) * distance;
			BlockPos column = BlockPos.containing(x, from.y, z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			double y = Math.min(from.y + 4, Math.max(from.y - 4, top));
			if (mob.randomTeleport(x, y + 1, z, false)) {
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 48, 0.4, 0.8, 0.4, 0.4);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 32, 0.4, 0.8, 0.4, 0.05);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.2f);
				level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.2f);
				mob.getNavigation().stop();
				return true;
			}
		}
		return false;
	}

	// The raid.

	/** Every raid by a culture that hexes: its witches are held off the walls and throw. */
	private static void steer(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Under raid : data.raids()) {
			if (!hexes(raid.culture())) {
				continue;
			}
			BlockPos hall = raid.hall();
			List<Mob> raiders = Sieges.raiders(level, hall);
			if (raiders.isEmpty()) {
				continue;
			}
			List<BlockPos> walls = Sieges.wallBlocks(level, hall);
			for (Mob mob : raiders) {
				if (mob instanceof Witch witch) {
					steer(level, hall, witch, walls, raiders);
				}
			}
		}
	}

	/**
	 * Where a witch of the raid on the village round {@code hall} stands: {@link #STAND} beyond the outermost of
	 * {@code walls} on her side (the line from the hall through her), and at least {@link #NEAREST} + 1 from any of
	 * them. Null when the village has no walls.
	 */
	@Nullable
	public static Vec3 standing(BlockPos hall, Vec3 at, List<BlockPos> walls) {
		if (walls.isEmpty()) {
			return null;
		}
		double hx = hall.getX() + 0.5;
		double hz = hall.getZ() + 0.5;
		double dx = at.x - hx;
		double dz = at.z - hz;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.5) {
			dx = 1;
			dz = 0;
		} else {
			dx /= length;
			dz /= length;
		}
		double outer = -1;
		double any = 0;
		for (BlockPos w : walls) {
			double wx = w.getX() + 0.5 - hx;
			double wz = w.getZ() + 0.5 - hz;
			double along = wx * dx + wz * dz;
			double across = Math.abs(wx * dz - wz * dx);
			any = Math.max(any, Math.sqrt(wx * wx + wz * wz));
			if (across <= 12 && along > outer) {
				outer = along;
			}
		}
		double r = (outer < 0 ? any : outer) + STAND;
		for (int i = 0; i < 40 && wallDistance(hx + dx * r, hz + dz * r, walls) < NEAREST + 1; i++) {
			r += 1;
		}
		return new Vec3(hx + dx * r, at.y, hz + dz * r);
	}

	/** The distance (across the ground) from (x, z) to the nearest of {@code walls}. */
	public static double wallDistance(double x, double z, List<BlockPos> walls) {
		double best = Double.MAX_VALUE;
		for (BlockPos w : walls) {
			double dx = w.getX() + 0.5 - x;
			double dz = w.getZ() + 0.5 - z;
			best = Math.min(best, dx * dx + dz * dz);
		}
		return Math.sqrt(best);
	}

	private static void steer(ServerLevel level, BlockPos hall, Witch witch, List<BlockPos> walls, List<Mob> raiders) {
		long now = level.getGameTime();
		HELD.put(witch.getUUID(), now);
		if (GOALED.add(witch)) {
			((MobAccessor) witch).aliveworkplace$goals().addGoal(0, new HoldGoal(witch));
		}
		Vec3 stand = standing(hall, witch.position(), walls);
		Villager target = null;
		if (stand == null) {
			// No walls: up to within UNWALLED blocks of the nearest villager of the village.
			target = nearestVillager(level, hall, witch);
			if (target == null) {
				walk(level, witch, Vec3.atBottomCenterOf(hall), 1);
			} else if (witch.distanceTo(target) > UNWALLED) {
				walk(level, witch, target.position(), UNWALLED - 2);
			} else {
				witch.getNavigation().stop();
			}
		} else {
			double from = wallDistance(witch.getX(), witch.getZ(), walls);
			double off = Math.hypot(stand.x - witch.getX(), stand.z - witch.getZ());
			if (from < NEAREST + 0.5 || from > FARTHEST || off > 3) {
				walk(level, witch, stand, 0);
			} else {
				witch.getNavigation().stop();
			}
		}
		Long last = THROWN.get(witch.getUUID());
		if (last != null && now - last < THROW_EVERY) {
			return;
		}
		// A sister of the raid who is hurt gets healing first.
		for (Mob other : raiders) {
			if (other != witch && other instanceof Witch && other.isAlive() && other.getHealth() < other.getMaxHealth() * 0.7f && witch.distanceTo(other) <= 16) {
				throwAt(level, witch, other, Potions.HEALING);
				THROWN.put(witch.getUUID(), now);
				return;
			}
		}
		List<Villager> within = level.getEntitiesOfClass(Villager.class, witch.getBoundingBox().inflate(REACH, 12, REACH),
			v -> v.isAlive() && Math.hypot(v.getX() - witch.getX(), v.getZ() - witch.getZ()) <= REACH);
		if (within.isEmpty()) {
			return;
		}
		Villager at = within.get(level.random.nextInt(within.size()));
		Holder<Potion> potion = Guards.isGuard(at) ? Potions.HARMING
			: List.of(Potions.SLOWNESS, Potions.WEAKNESS, Potions.POISON).get(level.random.nextInt(3));
		throwAt(level, witch, at, potion);
		THROWN.put(witch.getUUID(), now);
	}

	@Nullable
	private static Villager nearestVillager(ServerLevel level, BlockPos hall, Mob mob) {
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			if (best == null || v.distanceToSqr(mob) < best.distanceToSqr(mob)) {
				best = v;
			}
		}
		return best;
	}

	/** Sends {@code mob} to {@code to} (the ground there), unless she is already going there. */
	private static void walk(ServerLevel level, Mob mob, Vec3 to, int within) {
		BlockPos column = BlockPos.containing(to);
		int y = level.isLoaded(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) : column.getY();
		var path = mob.getNavigation().getPath();
		if (path != null && !path.isDone() && path.getTarget().closerThan(new BlockPos(column.getX(), y, column.getZ()), 2.5)) {
			return;
		}
		mob.getNavigation().moveTo(mob.getNavigation().createPath(column.getX(), y, column.getZ(), within), 1.0);
	}

	/**
	 * {@code witch} throws a splash potion of {@code potion} at {@code target}, high, over whatever wall is between:
	 * the speed of the throw found by flying it as the game does (gravity 0.05, drag 0.99), at 45 degrees up.
	 */
	public static void throwAt(ServerLevel level, Mob witch, LivingEntity target, Holder<Potion> potion) {
		Vec3 aim = target.position().add(target.getDeltaMovement().scale(10));
		double dx = aim.x - witch.getX();
		double dz = aim.z - witch.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double rise = aim.y + 0.5 - witch.getEyeY();
		double speed = speedFor(flat, rise);
		ThrownPotion thrown = new ThrownPotion(level, witch);
		thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion));
		thrown.shoot(dx, flat, dz, (float) speed, 2f);
		level.addFreshEntity(thrown);
		witch.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, witch.getSoundSource(), 1f, 0.8f + level.random.nextFloat() * 0.4f);
	}

	/** The speed that carries a potion thrown at 45 degrees {@code flat} blocks out where it falls {@code rise} above (or below) the hand. */
	static double speedFor(double flat, double rise) {
		double best = 0.6;
		double miss = Double.MAX_VALUE;
		for (double v = 0.3; v <= 2.4; v += 0.02) {
			double vx = v / Math.sqrt(2);
			double vy = vx;
			double x = 0;
			double y = 0;
			for (int t = 0; t < 200; t++) {
				x += vx;
				y += vy;
				vx *= 0.99;
				vy = vy * 0.99 - 0.05;
				if (vy < 0 && y <= rise) {
					break;
				}
			}
			if (Math.abs(x - flat) < miss) {
				miss = Math.abs(x - flat);
				best = v;
			}
		}
		return best;
	}

	/**
	 * Holds a raiding witch to where {@link #steer} sends her (and nothing else walks her): her own ranged attack
	 * would take her up to the walls after villagers she can't reach.
	 */
	private static final class HoldGoal extends Goal {
		private final Mob mob;

		HoldGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Long held = HELD.get(mob.getUUID());
			return held != null && mob.getTags().contains(Threats.RAIDER_TAG) && mob.level().getGameTime() - held <= EVERY * 3;
		}

		@Override
		public void tick() {
			if (mob.getTarget() != null) {
				mob.getLookControl().setLookAt(mob.getTarget());
			}
		}
	}

	/** The night each village was last hexed, saved so a restart doesn't hex it twice in a night. */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_covens";
		private final Map<BlockPos, Long> hexed = new HashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		/** The day the village round {@code hall} was last hexed; -1: never. */
		public long lastHexed(BlockPos hall) {
			return hexed.getOrDefault(hall, -1L);
		}

		void hexed(BlockPos hall, long day) {
			hexed.put(hall.immutable(), day);
			setDirty();
		}

		/** Forgets the village round {@code hall} (tests). */
		public void forget(BlockPos hall) {
			hexed.remove(hall);
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			hexed.forEach((hall, day) -> {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", hall.asLong());
				c.putLong("day", day);
				list.add(c);
			});
			tag.put("hexed", list);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "hexed", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				data.hexed.put(BlockPos.of(Nbt.getLong(c, "hall")), Nbt.getLong(c, "day"));
			}
			return data;
		}
	}

	private Covens() {
	}
}
