package io.github.jcondedata.aliveworkplace.furnish;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Villagers use the furniture (ROADMAP 37.6): the chairs, stools, benches, couches and sofas of the furniture mods (the
 * block tag {@code #aliveworkplace:villager_seats}, every entry {@code "required": false} so it loads without the mods)
 * are seats. At supper and in the evening ({@link #SUPPER} till bed at {@link #BEDTIME}) a villager who is idle or
 * meeting walks to the nearest free seat within {@link #REACH} blocks and sits on it ({@link FurnitureSeat}, as in the
 * Stadium's stands); they stand up for work, danger (hurt, a hostile near, panic or a raid) or bed, and when the switch
 * ({@link Furnishings#ENABLED}) is off. A seat with someone on it, or that another villager is walking to, isn't taken
 * twice, and a villager who can't reach their seat in {@link #GIVE_UP} ticks, or gets no closer to it for {@link #STUCK}
 * ticks (penned in, say), leaves it to the others for {@link #SKIP_FOR} ticks. A home with seats in it is furnished, and counts {@link #FURNISHED_BEAUTY} towards its people's
 * beauty need (M34's {@code beauty}, {@link io.github.jcondedata.aliveworkplace.people.ClassNeeds#beauty}).
 */
public final class FurnitureSeats {
	/** The furniture villagers sit on. */
	public static final TagKey<Block> SEATS = TagKey.create(Registries.BLOCK, AliveWorkplace.id("villager_seats"));
	/** When villagers sit down: supper (the hall's banquet hour). */
	public static final long SUPPER = io.github.jcondedata.aliveworkplace.hall.Banquets.SUPPER;
	/** When they get up for bed: the villagers' schedule turns to rest. */
	public static final long BEDTIME = 12000;
	/** How far a villager looks for a seat (blocks across; two below, one above). */
	public static final int REACH = 8;
	/** How close a villager must be to sit down. */
	static final double SIT_REACH = 2.0;
	/** How long a villager walks to a seat before giving up on it, in ticks. */
	static final int GIVE_UP = 600;
	/**
	 * How long a villager walking to a seat may get no closer to it before giving up early, in ticks (B967, B677): one penned
	 * in where they can't reach it lets it go in seconds, before the villagers who could sit on it have wandered off.
	 */
	static final int STUCK = 160;
	/** How much closer a villager must get to their seat (blocks) to count as getting there. */
	private static final double PROGRESS = 0.5;
	/** How long a villager leaves a seat they gave up on alone, in ticks, so others can have it (B945). */
	static final int SKIP_FOR = 1200;
	/** The beauty a furnished home is worth to the people who sleep in it. */
	public static final int FURNISHED_BEAUTY = 1;

	/** The seat each villager is walking to and when they set off (not saved: a reload starts over). */
	private static final Map<Villager, Target> TARGETS = new WeakHashMap<>();

	/** The seat each villager last gave up on and until when they leave it alone (not saved). */
	private static final Map<Villager, Skip> SKIPPED = new WeakHashMap<>();

	/** A seat walked to: since when, the closest the villager has come to it and when they last came closer. */
	private record Target(BlockPos pos, long since, double closest, long closerAt) {
	}

	private record Skip(BlockPos pos, long until) {
	}

	private FurnitureSeats() {
	}

	/** Each tick of a villager's brain (from the villager mixin): stand up when they should, sit down at supper. */
	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (villager.getVehicle() instanceof FurnitureSeat seat) {
			if (!seated(level, villager, seat.blockPosition())) {
				villager.stopRiding();
			}
			return;
		}
		if ((villager.tickCount + villager.getId()) % 20 != 0) {
			return;
		}
		if (!Furnishings.ENABLED || !wantsToSit(level, villager)) {
			TARGETS.remove(villager);
			return;
		}
		long now = level.getGameTime();
		Target target = TARGETS.get(villager);
		if (target != null) {
			double d = villager.position().distanceTo(Vec3.atBottomCenterOf(target.pos()));
			if (d < target.closest() - PROGRESS) {
				target = new Target(target.pos(), target.since(), d, now);
				TARGETS.put(villager, target);
			}
		}
		if (target != null && (now - target.since() > GIVE_UP || now - target.closerAt() > STUCK)) {
			// Couldn't reach it: leave it to the others for a while instead of picking it again at once (B945).
			SKIPPED.put(villager, new Skip(target.pos(), level.getGameTime() + SKIP_FOR));
			TARGETS.remove(villager);
			target = null;
		} else if (target != null && !free(level, target.pos(), villager)) {
			TARGETS.remove(villager);
			target = null;
		}
		if (target == null) {
			BlockPos found = nearestFree(level, villager);
			if (found == null) {
				return;
			}
			target = new Target(found, now, villager.position().distanceTo(Vec3.atBottomCenterOf(found)), now);
			TARGETS.put(villager, target);
		}
		if (villager.position().closerThan(Vec3.atBottomCenterOf(target.pos()), SIT_REACH)) {
			sit(level, villager, target.pos());
		} else {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target.pos(), 0.5f, 1));
		}
	}

	/** The seat {@code villager} is walking to, or null. */
	@Nullable
	public static BlockPos walkingTo(Villager villager) {
		Target target = TARGETS.get(villager);
		return target == null ? null : target.pos();
	}

	/** Seat {@code villager} on the furniture at {@code pos}; whether they sat. */
	public static boolean sit(ServerLevel level, Villager villager, BlockPos pos) {
		TARGETS.remove(villager);
		FurnitureSeat seat = FurnitureSeat.at(level, pos);
		level.addFreshEntity(seat);
		if (villager.startRiding(seat, true)) {
			villager.getNavigation().stop();
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return true;
		}
		seat.discard();
		return false;
	}

	/** Supper or the evening. */
	public static boolean sittingTime(ServerLevel level) {
		long t = level.getDayTime() % 24000;
		return t >= SUPPER && t < BEDTIME;
	}

	/** A villager who would sit down now: sitting time, idle or meeting, and nothing to fear. */
	static boolean wantsToSit(ServerLevel level, Villager villager) {
		return sittingTime(level) && !villager.isPassenger() && !villager.isVehicle() && !villager.isSleeping() && resting(villager)
			&& calm(villager);
	}

	/** A seated villager stays seated: the switch on, sitting time, idle or meeting, unafraid, and the seat still there. */
	static boolean seated(ServerLevel level, Villager villager, BlockPos pos) {
		return Furnishings.ENABLED && sittingTime(level) && !villager.isSleeping() && resting(villager) && calm(villager)
			&& level.getBlockState(pos).is(SEATS);
	}

	/** Not working, resting, hiding or panicking: idle or meeting at the bell. */
	private static boolean resting(Villager villager) {
		Activity a = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
		return a == Activity.IDLE || a == Activity.MEET;
	}

	/** No danger: not hurt lately, no hostile near. */
	private static boolean calm(Villager villager) {
		return villager.hurtTime == 0 && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
			&& !villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
	}

	/** The nearest free seat round {@code villager}, or null. */
	@Nullable
	static BlockPos nearestFree(ServerLevel level, Villager villager) {
		BlockPos at = villager.blockPosition();
		Skip skipped = SKIPPED.get(villager);
		if (skipped != null && level.getGameTime() >= skipped.until()) {
			SKIPPED.remove(villager);
			skipped = null;
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-REACH, -2, -REACH), at.offset(REACH, 1, REACH))) {
			if (level.getBlockState(pos).is(SEATS)) {
				double d = pos.distSqr(at);
				if (d < bestDistance && (skipped == null || !skipped.pos().equals(pos)) && free(level, pos, villager)) {
					best = pos.immutable();
					bestDistance = d;
				}
			}
		}
		return best;
	}

	/**
	 * Nobody on the seat at {@code pos} (a villager's seat, a player on the mod's own seat, anything standing in it) and no
	 * other villager walking to it.
	 */
	public static boolean free(ServerLevel level, BlockPos pos, @Nullable Villager asking) {
		for (Map.Entry<Villager, Target> e : TARGETS.entrySet()) {
			if (e.getKey() != asking && e.getKey().isAlive() && e.getValue().pos().equals(pos)) {
				return false;
			}
		}
		return level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> e != asking && e.isAlive()
			&& (e instanceof FurnitureSeat || e.isVehicle() || e instanceof net.minecraft.world.entity.LivingEntity)).isEmpty();
	}

	/**
	 * What {@code villager} is doing about seats, for logs and test messages: where they stand, their activity, the seat they
	 * walk to (and for how long), the seat they leave alone (and till when), their walk target and whether they'd sit now.
	 */
	public static String describe(ServerLevel level, Villager villager) {
		Target target = TARGETS.get(villager);
		Skip skip = SKIPPED.get(villager);
		long now = level.getGameTime();
		return "villager " + villager.getId() + " at " + villager.blockPosition().toShortString() + " ("
			+ villager.getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse("none") + ", wants to sit: "
			+ wantsToSit(level, villager) + ", riding: " + villager.isPassenger() + "), walking to seat "
			+ (target == null ? "none" : target.pos().toShortString() + " for " + (now - target.since()) + " ticks") + ", skipping "
			+ (skip == null ? "none" : skip.pos().toShortString() + " for " + (skip.until() - now) + " more ticks") + ", walk target "
			+ villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString())
				.orElse("none");
	}

	/** Whether {@code box} (a home) holds a seat: a furnished room. */
	public static boolean furnished(ServerLevel level, BoundingBox box) {
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).is(SEATS)) {
				return true;
			}
		}
		return false;
	}

	/** The beauty {@code villager}'s home's furniture is worth to them: {@link #FURNISHED_BEAUTY} if it's furnished. */
	public static int homeBeauty(ServerLevel level, Villager villager) {
		if (!Furnishings.ENABLED) {
			return 0;
		}
		BlockPos bed = io.github.jcondedata.aliveworkplace.hall.VillageNeeds.bed(level, villager);
		if (bed == null) {
			return 0;
		}
		return io.github.jcondedata.aliveworkplace.people.Homes.building(level, bed).map(b -> furnished(level, b.box()) ? FURNISHED_BEAUTY : 0)
			.orElse(0);
	}
}
