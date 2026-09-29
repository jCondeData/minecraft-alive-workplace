package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Cavalry: a guard with a tamed, saddled horse (or donkey or mule) left free — not on a lead — within
 * {@link #HORSE_RANGE} blocks of their Guard Post rides it on duty: they walk up to it and mount at the start of their
 * patrol, get down to spar at a Training Dummy (and back up after), and leave it where they are when their shift
 * ends. A villager who rides a mob steers it with that mob's own pathfinding (the game hands the rider the mount's
 * navigation), so the patrol and the fighting just go on from the saddle; a ridden horse is made faster
 * ({@link #PACE}), since a mob-steered horse otherwise plods at its wild pace.
 */
public final class Cavalry {
	/** How far from the post a guard looks for a horse. */
	public static int HORSE_RANGE = 24;
	/** How close the guard must be to swing up into the saddle. */
	static final double MOUNT_REACH = 2.5;
	/** A ridden horse's speed, times its own. */
	static final double PACE = 1.0; // (+100%)
	static final ResourceLocation PACE_ID = AliveWorkplace.id("cavalry_pace");

	/** Horses carrying a guard now (with the pace on), to take it off again however the guard gets down. */
	private static final Map<AbstractHorse, Villager> RIDDEN = new WeakHashMap<>();

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % 20 == 0 && !RIDDEN.isEmpty()) {
				RIDDEN.entrySet().removeIf(e -> {
					if (e.getKey().isRemoved() || e.getKey().getFirstPassenger() != e.getValue()) {
						slow(e.getKey());
						return true;
					}
					return false;
				});
			}
		});
	}

	/** The horse {@code villager} is riding, or null. */
	@Nullable
	public static AbstractHorse mount(Villager villager) {
		return villager.getVehicle() instanceof AbstractHorse horse ? horse : null;
	}

	/** Whether {@code horse} can carry a guard: tamed, saddled, grown, free (no lead, nobody on it). */
	public static boolean usable(AbstractHorse horse) {
		return horse.isAlive() && horse.isTamed() && horse.isSaddled() && !horse.isBaby() && !horse.isVehicle() && !horse.isLeashed()
			&& !(horse instanceof Llama);
	}

	/** The free horse nearest {@code villager} within {@link #HORSE_RANGE} of their post, or null. */
	@Nullable
	public static AbstractHorse freeHorse(ServerLevel level, Villager villager, BlockPos post) {
		return level.getEntitiesOfClass(AbstractHorse.class, new AABB(post).inflate(HORSE_RANGE, 8, HORSE_RANGE), Cavalry::usable).stream()
			.min(Comparator.comparingDouble(villager::distanceToSqr))
			.orElse(null);
	}

	/** Up into the saddle (true if they're riding it now). */
	public static boolean ride(Villager villager, AbstractHorse horse) {
		if (!usable(horse) || !villager.startRiding(horse, true)) {
			return false;
		}
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(PACE_ID, PACE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		RIDDEN.put(horse, villager);
		villager.level().playSound(null, horse.blockPosition(), SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.5f, 1f);
		return true;
	}

	/** Down from the saddle, the horse left where it stands. */
	public static void dismount(Villager villager) {
		AbstractHorse horse = mount(villager);
		if (horse != null) {
			villager.stopRiding();
			slow(horse);
			RIDDEN.remove(horse);
		}
	}

	/** The horse's own pace again. */
	private static void slow(AbstractHorse horse) {
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(PACE_ID);
		}
	}

	/** Whether {@code horse} has a guard's pace on (tests). */
	public static boolean paced(AbstractHorse horse) {
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(PACE_ID);
	}

	private Cavalry() {
	}
}
