package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Bandit camps: now and then bandits make camp out beyond a village of Village rank or more ({@link #NEAR} to
 * {@link #FAR} blocks from its hall) — tents round a fire, a Bandit Chief and his men. While the camp stands,
 * the village's night raids come from it, bandits (pillagers and vindicators) rather than monsters, twice as often, and
 * the villagers feel less safe. Kill the chief and the camp is broken up: the rest of the band scatters, the chest in
 * the chief's tent is yours, and the chronicle remembers who did it. {@code banditCamps} in the config turns them off.
 *
 * <p>The bandits are a raider culture ({@link #CULTURE}, {@code raider_cultures/bandits.json}, ROADMAP 32.2) and the
 * camp is their lair: {@link Lairs} (32.3) does the work for every culture with a lair, and keeps the camps in the
 * saved data they always had. This class is the bandits' face of it: its methods are what they were, and a
 * {@link Camp} is a bandits' lair.
 */
public final class BanditCamps {
	public static boolean ENABLED = true;
	/** The raider culture whose lair a bandit camp is. */
	public static final ResourceLocation CULTURE = Threats.BANDITS;
	public static final ResourceLocation CAMP = Lairs.BANDIT_CAMP;
	public static final String TAG = Lairs.BANDIT_TAG;
	public static final String CHIEF_TAG = Lairs.BANDIT_CHIEF_TAG;
	public static final int NEAR = Lairs.NEAR;
	public static final int FAR = Lairs.FAR;
	/** Days after a camp is broken up before another comes. */
	public static final int REST_DAYS = Lairs.REST_DAYS;
	/** The chance a day that bandits make camp near a village that could have one. */
	public static final float DAILY_CHANCE = Lairs.DAILY_CHANCE;
	/** How much less safe the villagers feel while a camp stands (share of the safety score kept). */
	public static final float SAFETY = 0.6f;

	/** A camp: where it stands, the village it preys on, its chief, the day it was made. */
	public record Camp(BlockPos pos, BlockPos hall, UUID chief, long day) {
	}

	private static Camp camp(Lairs.Lair lair) {
		return new Camp(lair.pos(), lair.hall(), lair.captain(), lair.day());
	}

	/** The bandit camp preying on the village round {@code hall}, if any. */
	public static Optional<Camp> near(ServerLevel level, BlockPos hall) {
		return Lairs.of(level, hall, CULTURE).map(BanditCamps::camp);
	}

	/** Every bandit camp in the dimension. */
	public static List<Camp> all(ServerLevel level) {
		return Lairs.all(level).stream().filter(lair -> lair.culture().equals(CULTURE)).map(BanditCamps::camp).toList();
	}

	/** The hall's round: a lair whose captain is gone is broken up; with none, raiders (bandits, while {@link #ENABLED}) may come. */
	public static void round(ServerLevel level, BlockPos hall) {
		Lairs.round(level, hall, ENABLED);
	}

	/**
	 * The chance a day that bandits make camp near the village round {@code hall}: {@link #DAILY_CHANCE}, times the
	 * village's {@code bandit_camps} effects (Open Gates, 30.7: twice; reformed by The Watchful Gate: as usual).
	 */
	public static float dailyChance(ServerLevel level, BlockPos hall) {
		return Lairs.dailyChance(level, hall);
	}

	/** Somewhere {@code near} to {@code far} blocks out beyond the village: loaded, dry, fairly flat and open; null if there's nowhere (the Old Sage's hut, 29.14). */
	@Nullable
	public static BlockPos site(ServerLevel level, BlockPos hall, int near, int far, net.minecraft.util.RandomSource random) {
		return Lairs.site(level, hall, near, far, random);
	}

	/** Sets the camp down with its middle at {@code ground} and its band in it; null if the camp's blueprint is missing. */
	@Nullable
	public static Camp found(ServerLevel level, BlockPos hall, BlockPos ground) {
		Lairs.Lair lair = Lairs.found(level, hall, ground, CULTURE);
		return lair == null ? null : camp(lair);
	}

	/** Every death: the chief falling breaks up his camp. */
	public static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		Lairs.onDeath(level, entity, source);
	}

	/** Forgets every camp (tests). */
	public static void forget(ServerLevel level) {
		Lairs.forget(level);
	}

	private BanditCamps() {
	}
}
