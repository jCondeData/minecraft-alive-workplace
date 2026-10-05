package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * A village with a Village Hall grows: at most once a day, when there's a free bed, enough food in the store and the
 * village is doing well, the two grown villagers nearest the free bed have a baby (the family eats {@link #MEALS} meals
 * from the store for it). Edicts change this with {@code births} effects (Large Families, 30.6: two babies a day, 24 meals
 * needed and 12 eaten). So building houses with beds — and keeping the store full — grows the village, up to
 * {@link #CAP} villagers.
 */
public final class VillageGrowth {
	/** Game time between two births in one village. */
	public static long EVERY = VillageNeeds.DAY;
	/** Meals the store must hold before a baby is on the way. */
	public static int FOOD_NEEDED = 16;
	/** Meals a birth takes from the store. */
	public static int MEALS = 8;
	/** Wellbeing a village needs to grow. */
	public static float WELLBEING_NEEDED = 0.5f;
	/** A village stops growing at this many villagers (0: never grows). */
	public static int CAP = 40;

	/**
	 * Game time between two births in the village of the hall at {@code hall}: {@link #EVERY} over its babies a day (Large
	 * Families: 2; a Cradle one more), halved for the days after a Grand Chef's banquet (29.18).
	 */
	public static long every(ServerLevel level, BlockPos hall) {
		long every = EVERY / Math.max(1, CivicEffects.of(level, hall).birthsPerDay() + Cradles.extraBirths(level, hall));
		return Banquets.growing(level, hall) ? every / 2 : every;
	}

	/** Meals the store of the village round {@code hall} must hold before a baby is on the way (Large Families: 24). */
	public static int foodNeeded(ServerLevel level, BlockPos hall) {
		return CivicEffects.of(level, hall).foodNeeded(FOOD_NEEDED);
	}

	/** Meals a birth takes from the store of the village round {@code hall} (Large Families: 12). */
	public static int familyMeals(ServerLevel level, BlockPos hall) {
		return CivicEffects.of(level, hall).familyMeals(MEALS);
	}

	/** What's keeping the village from growing (NONE: nothing, a baby is on the way). */
	public enum Blocker { NONE, FULL, TOO_FEW, NO_BED, FOOD, WELLBEING, TOO_SOON }

	/** Why the village round {@code hall} can't grow right now, given how it's doing and when the last baby came. */
	public static Blocker blocker(ServerLevel level, BlockPos hall, VillageNeeds.Needs needs, long lastBirth) {
		int cap = VillageRanks.growthCap(VillageRanks.of(level, hall));
		if (cap <= 0 || needs.villagers() >= cap) {
			return Blocker.FULL;
		}
		if (needs.adults() < 2) {
			return Blocker.TOO_FEW;
		}
		if (freeBed(level, hall) == null) {
			return Blocker.NO_BED;
		}
		if (meals(level, VillageNeeds.store(level, hall)) < foodNeeded(level, hall)) {
			return Blocker.FOOD;
		}
		if (needs.wellbeing() < WELLBEING_NEEDED) {
			return Blocker.WELLBEING;
		}
		if (lastBirth != 0 && level.getGameTime() - lastBirth < every(level, hall)) { // 0: never
			return Blocker.TOO_SOON;
		}
		return Blocker.NONE;
	}

	/** A bed nobody has claimed within the village, nearest the hall. */
	@Nullable
	static BlockPos freeBed(ServerLevel level, BlockPos hall) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.HOME), hall, VillageHalls.RADIUS, PoiManager.Occupancy.HAS_SPACE).orElse(null);
	}

	/** The meals in the store. */
	static long meals(ServerLevel level, List<BlockPos> store) {
		long meals = 0;
		for (var e : SupplyContainers.contents(level, store).entrySet()) {
			if (VillageNeeds.isMeal(new net.minecraft.world.item.ItemStack(e.getKey()))) {
				meals += e.getValue();
			}
		}
		return meals;
	}

	/**
	 * A baby for the two grown villagers nearest the free bed, if nothing's in the way; returns it (null if none was born).
	 * The family eats {@link #familyMeals} meals from the store.
	 */
	@Nullable
	public static Villager grow(ServerLevel level, BlockPos hall, VillageNeeds.Needs needs, long lastBirth) {
		Villager blessed = blessedBirth(level, hall, needs);
		if (blessed != null) {
			return blessed;
		}
		if (blocker(level, hall, needs, lastBirth) != Blocker.NONE) {
			return null;
		}
		BlockPos bed = freeBed(level, hall);
		// A married couple first; else the two grown villagers nearest the bed.
		List<Villager> parents = io.github.jcondedata.aliveworkplace.people.Couples.coupleNear(level, hall, bed);
		if (parents.isEmpty()) {
			parents = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && !v.isSleeping()
				&& !io.github.jcondedata.aliveworkplace.guard.Mercenaries.isMercenary(v))
				.stream().sorted(Comparator.comparingDouble(v -> v.distanceToSqr(bed.getCenter()))).limit(2).toList();
		}
		if (parents.size() < 2) {
			return null;
		}
		return born(level, hall, parents);
	}

	/**
	 * A wedding the Seer blessed (29.16): the couple's first baby comes within the blessing's days as soon as a bed is free
	 * and the village isn't full, past the daily wait, the store and the mood (once). Returns it, or null.
	 */
	@Nullable
	static Villager blessedBirth(ServerLevel level, BlockPos hall, @Nullable VillageNeeds.Needs needs) {
		int cap = VillageRanks.growthCap(VillageRanks.of(level, hall));
		if (needs == null || cap <= 0 || needs.villagers() >= cap || freeBed(level, hall) == null) {
			return null;
		}
		List<Villager> parents = io.github.jcondedata.aliveworkplace.legend.Seer.blessedCouple(level, hall);
		if (parents.size() < 2) {
			return null;
		}
		Villager baby = born(level, hall, parents);
		if (baby != null) {
			io.github.jcondedata.aliveworkplace.legend.Seer.babyCame(parents);
		}
		return baby;
	}

	/** {@code parents}' baby is born; the family eats {@link #familyMeals} meals from the store. */
	@Nullable
	private static Villager born(ServerLevel level, BlockPos hall, List<Villager> parents) {
		Villager baby = EntityType.VILLAGER.create(level);
		if (baby == null) {
			return null;
		}
		Villager mother = parents.get(0);
		Villager father = parents.get(1);
		baby.moveTo(mother.getX(), mother.getY(), mother.getZ(), mother.getYRot(), 0f);
		baby.finalizeSpawn(level, level.getCurrentDifficultyAt(mother.blockPosition()), MobSpawnType.BREEDING, null);
		baby.setVillagerData(baby.getVillagerData().setType((level.random.nextBoolean() ? mother : father).getVillagerData().getType()));
		baby.setAge(-24000);
		io.github.jcondedata.aliveworkplace.people.Families.born(baby, mother, father);
		level.addFreshEntityWithPassengers(baby);
		List<BlockPos> store = VillageNeeds.store(level, hall);
		int meals = familyMeals(level, hall);
		for (int i = 0; i < meals; i++) {
			if (SupplyContainers.takeOne(level, store, VillageNeeds::isMeal).isEmpty()) {
				break;
			}
		}
		for (Villager parent : parents) {
			level.sendParticles(ParticleTypes.HEART, parent.getX(), parent.getEyeY() + 0.4, parent.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		}
		level.broadcastEntityEvent(baby, (byte) 12); // the villager's hearts
		level.playSound(null, baby.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1.3f);
		Chronicle.record(level, hall, Chronicle.Kind.BIRTH, Component.translatable("chronicle.aliveworkplace.birth",
			mother.getDisplayName(), father.getDisplayName()), true);
		return baby;
	}

	private VillageGrowth() {
	}
}
