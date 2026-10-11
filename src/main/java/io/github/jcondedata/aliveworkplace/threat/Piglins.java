package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Piglin incursions (ROADMAP 32.10): what piglins and hoglins of a raid or a lair do in the Overworld.
 * <ul>
 * <li><b>They never zombify</b> while they raid or keep a lair ({@link #keep}: their own immunity, saved with them).</li>
 * <li><b>They go for the villagers and the guards</b>: every {@link #EVERY} ticks a raiding piglin or hoglin with nothing
 * to fight is angered at the nearest villager within {@link #SIGHT} blocks (a piglin's {@code angry_at}, a hoglin's
 * attack target, as their own brains do it when struck); with none in sight it walks on toward the nearest villager of
 * the village, or the hall, {@link #STRIDE} blocks at a time. Their brains, not goals, steer them, so this sets the
 * memories their brains read.</li>
 * <li><b>The lair's portal</b> (a lair whose file says {@code portal}): the raiders step out of it ({@link #portal}),
 * and when the lair is broken its portal goes out ({@link #putOut}).</li>
 * <li><b>The Warlord's fall</b> (a captain whose file says {@code zombifies_band}): his band left in the Overworld, at
 * the lair and out raiding, turns zombified at once ({@link #zombify}): piglins and brutes into zombified piglins,
 * hoglins into zoglins. The raiders among them stay in the raid (they keep its tags) until it ends.</li>
 * </ul>
 */
public final class Piglins {
	/** How often (ticks) raiding piglins are steered. */
	public static final int EVERY = 20;
	/** How far a raiding piglin looks for a villager to go for (their brains attack within 16). */
	public static final int SIGHT = 16;
	/** How far ahead a raiding piglin is sent at a time when no villager is in sight. */
	public static final int STRIDE = 12;
	/** How far from a lair's middle its portal is looked for. */
	private static final int PORTAL_REACH = 16;

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == 0) {
				tick(level);
			}
		});
	}

	/** Whether {@code mob} is of the piglins' kind: a piglin, a brute or a hoglin. */
	public static boolean piglinKind(LivingEntity mob) {
		return mob instanceof AbstractPiglin || mob instanceof Hoglin;
	}

	/** Keeps {@code mob} from turning zombified in the Overworld (a raider, one of a lair's band); anything else is left be. */
	public static void keep(Mob mob) {
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		} else if (mob instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
	}

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Under raid : data.raids()) {
			List<Mob> raiders = Sieges.raiders(level, raid.hall());
			for (Mob mob : raiders) {
				if (piglinKind(mob) && mob.isAlive()) {
					keep(mob);
					steer(level, raid.hall(), mob);
				}
			}
		}
	}

	/** The raider's foe now: what its brain is set on attacking. */
	@Nullable
	static LivingEntity fighting(Mob mob) {
		Optional<LivingEntity> target = brain(mob).getMemory(MemoryModuleType.ATTACK_TARGET);
		return target != null && target.isPresent() && target.get().isAlive() ? target.get() : null;
	}

	@SuppressWarnings("unchecked")
	private static Brain<LivingEntity> brain(Mob mob) {
		return (Brain<LivingEntity>) mob.getBrain();
	}

	/** One raiding piglin's round: on the nearest villager in sight, else on toward the village's villagers. */
	static void steer(ServerLevel level, BlockPos hall, Mob mob) {
		if (fighting(mob) != null) {
			return;
		}
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(VillageHalls.RADIUS, 16, VillageHalls.RADIUS), LivingEntity::isAlive);
		Villager nearest = villagers.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
		if (nearest != null && mob.distanceToSqr(nearest) <= SIGHT * SIGHT) {
			anger(mob, nearest);
			return;
		}
		Vec3 goal = nearest != null ? nearest.position() : Vec3.atBottomCenterOf(hall);
		Vec3 way = goal.subtract(mob.position());
		double far = Math.sqrt(way.x * way.x + way.z * way.z);
		if (far < 2) {
			return;
		}
		Vec3 step = far <= STRIDE ? goal : mob.position().add(way.x / far * STRIDE, 0, way.z / far * STRIDE);
		BlockPos column = BlockPos.containing(step.x, mob.getY(), step.z);
		BlockPos to = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		brain(mob).eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		brain(mob).setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 1.0f, 1));
	}

	/**
	 * Sets {@code mob}'s anger on {@code target}, as its brain does when struck: a piglin or brute is angry at it (and
	 * attacks it, for its brain then finds it the nearest foe), a hoglin attacks it.
	 */
	public static void anger(Mob mob, LivingEntity target) {
		Brain<LivingEntity> brain = brain(mob);
		brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
		if (mob instanceof AbstractPiglin) {
			brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
		}
		if (mob instanceof AbstractPiglin || mob instanceof Hoglin) {
			brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, 200L);
		} else {
			mob.setTarget(target);
		}
	}

	// The lair's portal.

	/** The portal blocks of {@code lair} (a lair with a {@code portal}, its ground loaded), bottom row first; empty if none. */
	public static List<BlockPos> portalBlocks(ServerLevel level, Lairs.Lair lair) {
		List<BlockPos> out = new ArrayList<>();
		boolean has = Threats.get(lair.culture()).flatMap(Culture::lair).map(Culture.Lair::portal).orElse(false);
		if (!has || !level.isLoaded(lair.pos())) {
			return out;
		}
		BlockPos c = lair.pos();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-PORTAL_REACH, -6, -PORTAL_REACH), c.offset(PORTAL_REACH, 12, PORTAL_REACH))) {
			if (level.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
				out.add(p.immutable());
			}
		}
		out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(lair.hall())));
		return out;
	}

	/** Where the raiders of {@code lair} step out: its lowest portal block nearest the village, or null if it has none lit. */
	@Nullable
	public static BlockPos portal(ServerLevel level, Lairs.Lair lair) {
		List<BlockPos> blocks = portalBlocks(level, lair);
		return blocks.isEmpty() ? null : blocks.get(0);
	}

	/** The raiders stepping out of the portal at {@code at}: its light and sound. */
	public static void stepOut(ServerLevel level, BlockPos at) {
		level.sendParticles(ParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 60, 0.6, 1.0, 0.6, 0.4);
		level.playSound(null, at, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.6f, 1.4f);
	}

	/** Puts out the portal of {@code lair}: every portal block of it goes, with a hiss. Returns how many went. */
	public static int putOut(ServerLevel level, Lairs.Lair lair) {
		List<BlockPos> blocks = portalBlocks(level, lair);
		for (BlockPos p : blocks) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
		}
		if (!blocks.isEmpty()) {
			level.playSound(null, blocks.get(0), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5f, 0.6f);
			level.playSound(null, blocks.get(0), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.2f, 0.8f);
		}
		return blocks.size();
	}

	// The Warlord's fall.

	/**
	 * The captain of {@code lair} has fallen and it is broken up: its portal goes out, and if his culture says
	 * {@code zombifies_band} the band left in the Overworld (at the lair and out raiding) turns zombified. Called before
	 * the band is sent away, so the zombified stay. Returns how many turned.
	 */
	public static int fallen(ServerLevel level, Lairs.Lair lair) {
		Optional<Culture> culture = Threats.get(lair.culture());
		putOut(level, lair);
		if (!culture.flatMap(Culture::captain).map(Culture.Captain::zombifiesBand).orElse(false)) {
			return 0;
		}
		List<Mob> band = new ArrayList<>(Lairs.band(level, lair));
		for (Mob raider : Sieges.raiders(level, lair.hall())) {
			if (raider.getTags().contains(Lairs.TAG) && !band.contains(raider)) {
				band.add(raider);
			}
		}
		int turned = 0;
		for (Mob mob : band) {
			if (!mob.getUUID().equals(lair.captain()) && zombify(level, mob) != null) {
				turned++;
			}
		}
		return turned;
	}

	/**
	 * Turns {@code mob} zombified now, as the Overworld does in the end: a piglin or brute into a zombified piglin, a
	 * hoglin into a zoglin, dazed for 10 s. A raider stays one of its raid (its raid and role tags go with it); it is no
	 * more one of a lair's band. Null for anything else.
	 */
	@Nullable
	public static Mob zombify(ServerLevel level, Mob mob) {
		EntityType<? extends Mob> into = mob instanceof AbstractPiglin ? EntityType.ZOMBIFIED_PIGLIN : mob instanceof Hoglin ? EntityType.ZOGLIN : null;
		if (into == null || !mob.isAlive()) {
			return null;
		}
		List<String> tags = new ArrayList<>();
		for (String tag : mob.getTags()) {
			if (tag.equals(Threats.RAIDER_TAG) || tag.equals(Threats.EXTRA_TAG) || tag.startsWith("aliveworkplace_role_")) {
				tags.add(tag);
			}
		}
		Mob turned = mob.convertTo(into, true);
		if (turned == null) {
			return null;
		}
		tags.forEach(turned::addTag);
		turned.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
		level.sendParticles(ParticleTypes.SMOKE, turned.getX(), turned.getY() + 1, turned.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, turned.blockPosition(), mob instanceof Hoglin ? SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED : SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED,
			SoundSource.HOSTILE, 1f, 1f);
		return turned;
	}

	private Piglins() {
	}
}
