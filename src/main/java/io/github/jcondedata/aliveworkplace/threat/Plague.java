package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import org.jetbrains.annotations.Nullable;

/**
 * The plague (ROADMAP 32.18), {@code data/aliveworkplace/disasters/plague.json} on the disaster engine (32.15). It
 * starts with a first case (one villager falls ill with it, {@link Sickness#PLAGUE}); while it lasts, each hall round,
 * a well villager within the {@code contagion} range of an ill one catches it by the {@code contagion} chance, a quarter
 * less a level of Medicine research. The ill don't get well by themselves, the nurse's remedies don't touch them, and
 * after {@link #BEDRIDDEN_DAYS} ill they keep to their bed. An ill villager a Physician has seen
 * ({@code ModAttachments.PLAGUE_SEEN}) is sent home and passes it on only to those sharing their house (a bed within
 * {@link #HOUSE} blocks of theirs). It ends a day after the last of the ill is cured ({@code ends_when_well}).
 *
 * <p>Besides its chance a day, a coven's hex (32.11) or a traveller from far away at the inn (a villager of another
 * land's dress) can bring it: {@link #carried}.
 */
public final class Plague {
	public static final ResourceLocation ID = AliveWorkplace.id("plague");
	/** After this many days ill, the plague's ill keep to their bed. */
	public static final int BEDRIDDEN_DAYS = 2;
	/** How long a Remedy keeps a villager well. */
	public static final long WELL_FOR = 10 * VillageNeeds.DAY;
	/** Two beds this close are one house. */
	public static final int HOUSE = 6;
	/** The chance a hexed villager or a traveller from far away brings the plague to a village where it could start. */
	public static float CARRIER_CHANCE = 0.1f;

	/** Whether a plague is under way (begun, not only foretold) in the village around {@code pos}. */
	public static boolean underway(ServerLevel level, BlockPos pos) {
		Optional<Disasters.Afflicted> a = Disasters.afflicted(level, pos, Disaster.Effect.Type.CONTAGION);
		return a.isPresent() && is(a.get().disaster());
	}

	/** Whether {@code disaster} is the plague (its first case, spread and Physician are this one's alone). */
	public static boolean is(Disaster disaster) {
		return ID.equals(disaster.id());
	}

	/**
	 * {@code contagion} in a plague, every round: each well grown villager within {@code range} of an ill one catches it
	 * by {@code chance} (once a round, however many ill are near); the ill a Physician has seen reach only their house.
	 * Returns those who caught it.
	 */
	public static List<Villager> spread(ServerLevel level, Disaster.Effect effect, List<Villager> all, RandomSource random) {
		List<Villager> ill = all.stream().filter(Sickness::isIll).toList();
		List<Villager> caught = new ArrayList<>();
		if (ill.isEmpty()) {
			return caught;
		}
		double range = effect.range() * effect.range();
		for (Villager v : all) {
			if (Sickness.isIll(v) || caught.contains(v)) {
				continue;
			}
			boolean near = false;
			for (Villager sick : ill) {
				if (v.distanceToSqr(sick) <= range && (!seen(sick) || sameHouse(level, sick, v))) {
					near = true;
					break;
				}
			}
			if (near && random.nextDouble() < chance(effect, v) && Sickness.catchPlague(level, v)) {
				caught.add(v);
			}
		}
		return caught;
	}

	/** The chance {@code villager} catches it from a round near the ill: the effect's, a quarter less a level of Medicine. */
	public static double chance(Disaster.Effect effect, Villager villager) {
		return effect.factor() * Math.max(0, 1 - Research.level(villager, Research.Topic.MEDICINE) / 4.0);
	}

	/** Whether a Physician has seen {@code villager}'s plague. */
	public static boolean seen(Villager villager) {
		return Boolean.TRUE.equals(ModAttachments.PLAGUE_SEEN.get(villager));
	}

	/** Whether {@code a} and {@code b} share a house: both have a bed, within {@link #HOUSE} blocks of each other. */
	public static boolean sameHouse(ServerLevel level, Villager a, Villager b) {
		BlockPos bedA = VillageNeeds.bed(level, a);
		BlockPos bedB = VillageNeeds.bed(level, b);
		return bedA != null && bedB != null && bedA.closerThan(bedB, HOUSE);
	}

	/** Whether {@code villager} has been ill with the plague long enough to keep to their bed. */
	public static boolean bedridden(ServerLevel level, Villager villager) {
		Long since = ModAttachments.ILL_SINCE.get(villager);
		return since != null && Sickness.isPlagueIll(villager) && level.getGameTime() - since >= BEDRIDDEN_DAYS * VillageNeeds.DAY;
	}

	/** The round for one of the plague's ill: after two days they go to bed and stay in it. */
	public static void toBed(ServerLevel level, Villager villager) {
		if (!bedridden(level, villager) || villager.isSleeping()) {
			return;
		}
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed == null || !(level.getBlockState(bed).getBlock() instanceof BedBlock)) {
			return;
		}
		if (villager.blockPosition().closerThan(bed, 2.5)) {
			villager.startSleeping(bed);
		} else {
			sendHome(level, villager);
		}
	}

	/** Whether a villager in bed with the plague should stay there (Villager.stopSleeping is held back while their bed stands). */
	public static boolean keepsToBed(Villager villager) {
		return villager.level() instanceof ServerLevel level && villager.isAlive() && !villager.isRemoved() && bedridden(level, villager)
			&& villager.getSleepingPos().map(p -> level.getBlockState(p).getBlock() instanceof BedBlock).orElse(false);
	}

	/** Sends {@code villager} walking home to their bed (the Physician's "go home and stay there"). */
	public static void sendHome(ServerLevel level, Villager villager) {
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed != null) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bed, 0.5f, 1));
		}
	}

	/**
	 * The Remedy (32.18) given to {@code villager}: any illness cured, hexes included, and they're kept well for
	 * {@link #WELL_FOR}. Returns whether they were ill.
	 */
	public static boolean cure(ServerLevel level, Villager villager) {
		boolean ill = Sickness.isIll(villager);
		if (ill) {
			Sickness.recover(level, villager);
		}
		if (villager.isSleeping() && level.isDay()) {
			villager.stopSleeping();
		}
		ModAttachments.WELL_UNTIL.set(villager, level.getGameTime() + WELL_FOR);
		level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(), 4, 0.3, 0.2, 0.3, 0);
		level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6f, 1.1f);
		return ill;
	}

	/**
	 * A plague's first case, when it starts ({@code Disasters.start}): {@code carrier} if it brought the plague, else a
	 * grown villager of the village picked by {@code random}. Nothing if someone already has it.
	 */
	@Nullable
	public static Villager firstCase(ServerLevel level, List<Villager> all, RandomSource random) {
		if (all.stream().anyMatch(Sickness::isPlagueIll)) {
			return null;
		}
		List<Villager> open = all.stream().filter(v -> !Sickness.isIll(v)).toList();
		if (open.isEmpty()) {
			return null;
		}
		Villager first = open.get(random.nextInt(open.size()));
		return Sickness.catchPlague(level, first) ? first : null;
	}

	/**
	 * {@code carrier} (a coven's hexed or a traveller from far away) may bring the plague to its village: by
	 * {@link #CARRIER_CHANCE}, where the plague could start today (switched on, its conditions holding, no disaster under
	 * way or just past). The carrier is its first case. Returns whether it started.
	 */
	public static boolean carried(ServerLevel level, Villager carrier, RandomSource random) {
		Optional<Disaster> plague = Disasters.on(ID);
		if (plague.isEmpty() || !Disasters.live() || !Disasters.ROLLING) { // (by chance only: GameTests start it themselves)
			return false;
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, carrier.blockPosition());
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity) || entity.disaster() != null
			|| Chronicle.day(level) - entity.lastDisasterDay() < Disasters.REST) {
			return false;
		}
		VillageHalls.Census census = VillageHalls.census(level, hall.get());
		if (Disasters.chance(level, hall.get(), plague.get(), census) <= 0 || random.nextFloat() >= CARRIER_CHANCE) {
			return false;
		}
		Sickness.recover(level, carrier); // (a hex gives way to the plague it brought)
		Sickness.catchPlague(level, carrier);
		Disasters.start(level, hall.get(), entity, plague.get(), census, random);
		return true;
	}

	/** Whether a traveller at the inn comes from far away: dressed for another land than the innkeeper's. */
	public static boolean fromFarAway(Villager traveller, Villager innkeeper) {
		return traveller.getVillagerData().getType() != innkeeper.getVillagerData().getType();
	}

	/** Who in the village is ill, for the hall's list: the plague's ill first. */
	public static List<Villager> ill(List<Villager> all) {
		List<Villager> ill = new ArrayList<>(all.stream().filter(Sickness::isIll).toList());
		ill.sort((a, b) -> Boolean.compare(Sickness.isPlagueIll(b), Sickness.isPlagueIll(a)));
		return ill;
	}

	private Plague() {
	}
}
