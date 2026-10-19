package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Pharaoh's curse (ROADMAP 32.9): a captain whose culture gives him {@code raises} ({@link Culture.Raise}) carries
 * the tag {@link #RAISER_TAG}. While he lives, a mob of the raised kind that falls within the radius of him (not he
 * himself, nor one that rose already) rises once more after the delay, where it fell, at part of its health and in a
 * burst of sand: the same mob as it was (its gear, its raid, its role), with the tag {@link #RISEN_TAG}, so it never
 * rises twice. One whose Pharaoh is dead when it falls stays dead. Those waiting to rise are kept in memory only: a
 * restart within the three seconds loses them.
 */
public final class Risings {
	/** The tag of a captain who raises the fallen. */
	public static final String RAISER_TAG = "aliveworkplace_raiser";
	/** The tag of one who rose already. */
	public static final String RISEN_TAG = "aliveworkplace_risen";

	private record Pending(ResourceKey<Level> dimension, EntityType<?> type, CompoundTag data, Vec3 pos, long due, double health) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	public static void init() {
		Platform.get().afterDeath((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				onDeath(level, entity);
			}
		});
		Platform.get().onLevelTick(Risings::tick);
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets those waiting to rise, as a restart does (tests). */
	public static synchronized void forget() {
		PENDING.clear();
	}

	/** How many are waiting to rise. */
	public static synchronized int pending() {
		return PENDING.size();
	}

	/** Marks {@code captain} as one who raises the fallen (his culture's captain {@code raises} something). */
	public static void mark(Mob captain, Culture.Captain chief) {
		if (chief.raises().isPresent()) {
			captain.addTag(RAISER_TAG);
		}
	}

	/** What a living raiser near {@code entity} raises it with, if anything: his culture's {@code raises} for its kind. */
	static Optional<Culture.Raise> raiseFor(ServerLevel level, LivingEntity entity) {
		if (!(entity instanceof Mob) || entity.getTags().contains(RISEN_TAG) || entity.getTags().contains(RAISER_TAG)) {
			return Optional.empty();
		}
		ResourceLocation kind = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		for (Culture culture : Threats.all()) {
			Optional<Culture.Captain> captain = culture.captain();
			if (captain.isEmpty() || captain.get().raises().isEmpty() || !captain.get().raises().get().entity().equals(kind)) {
				continue;
			}
			Culture.Raise raise = captain.get().raises().get();
			ResourceLocation raiser = captain.get().entity();
			List<Mob> near = level.getEntitiesOfClass(Mob.class, new AABB(entity.blockPosition()).inflate(raise.radius()),
				m -> m != entity && m.isAlive() && m.getTags().contains(RAISER_TAG) && BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).equals(raiser)
					&& m.distanceToSqr(entity) <= raise.radius() * raise.radius());
			if (!near.isEmpty()) {
				return Optional.of(raise);
			}
		}
		return Optional.empty();
	}

	/** Every death: one that falls near a living raiser will rise again. */
	public static void onDeath(ServerLevel level, LivingEntity entity) {
		Optional<Culture.Raise> raise = raiseFor(level, entity);
		if (raise.isEmpty()) {
			return;
		}
		CompoundTag data = entity.saveWithoutId(new CompoundTag());
		for (String key : new String[] {"UUID", "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "Motion", "FallDistance", "Passengers",
			"Leash", "active_effects"}) {
			data.remove(key);
		}
		synchronized (Risings.class) {
			PENDING.add(new Pending(level.dimension(), entity.getType(), data, entity.position(), level.getGameTime() + raise.get().delay(),
				raise.get().health()));
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), entity.getX(), entity.getY() + 0.2,
			entity.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
	}

	private static void tick(ServerLevel level) {
		List<Pending> due = new ArrayList<>();
		synchronized (Risings.class) {
			if (PENDING.isEmpty()) {
				return;
			}
			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
				Pending p = it.next();
				if (p.dimension().equals(level.dimension()) && level.getGameTime() >= p.due()) {
					due.add(p);
					it.remove();
				}
			}
		}
		for (Pending p : due) {
			rise(level, p);
		}
	}

	private static void rise(ServerLevel level, Pending p) {
		if (!level.isLoaded(net.minecraft.core.BlockPos.containing(p.pos()))) {
			return;
		}
		Entity made = p.type().create(level);
		if (!(made instanceof Mob mob)) {
			return;
		}
		mob.load(p.data());
		mob.moveTo(p.pos().x, p.pos().y, p.pos().z, mob.getYRot(), 0f);
		mob.addTag(RISEN_TAG);
		mob.setHealth((float) Math.max(1, mob.getMaxHealth() * p.health()));
		if (!HostileSpawns.add(level, mob, "risen dead")) {
			return; // (said in the log: B208)
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), p.pos().x, p.pos().y + 0.6, p.pos().z,
			60, 0.5, 0.7, 0.5, 0.15);
		level.sendParticles(ParticleTypes.CLOUD, p.pos().x, p.pos().y + 0.3, p.pos().z, 8, 0.4, 0.2, 0.4, 0.02);
		level.playSound(null, mob.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
		level.playSound(null, mob.blockPosition(), SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.7f);
	}

	private Risings() {
	}
}
