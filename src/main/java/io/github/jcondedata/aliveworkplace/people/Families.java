package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * Families: a baby remembers its parents — their names and trades (babies born in a village with a Village Hall, and
 * babies villagers have the vanilla way). The hall's list says whose child someone is. When a child grows up, the
 * chronicle notes it; a grown child without a job takes up a parent's trade when the village has a free workstation for
 * it.
 */
public final class Families {
	/**
	 * Who a villager's parents are: names, their jobs when the child was born, whether the child has grown up, and whether
	 * each parent was a schooled Master at the birth (29.7; false in older saves).
	 */
	public record Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp,
						  boolean motherSchooledMaster, boolean fatherSchooledMaster) {
		public static final Codec<Parents> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("mother").forGetter(Parents::mother),
			ComponentSerialization.CODEC.fieldOf("father").forGetter(Parents::father),
			Codec.STRING.optionalFieldOf("mother_job", "").forGetter(Parents::motherJob),
			Codec.STRING.optionalFieldOf("father_job", "").forGetter(Parents::fatherJob),
			Codec.BOOL.optionalFieldOf("grown_up", false).forGetter(Parents::grownUp),
			Codec.BOOL.optionalFieldOf("mother_schooled_master", false).forGetter(Parents::motherSchooledMaster),
			Codec.BOOL.optionalFieldOf("father_schooled_master", false).forGetter(Parents::fatherSchooledMaster)
		).apply(i, Parents::new));

		/** Parents neither of whom is on record as a schooled Master. */
		public Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp) {
			this(mother, father, motherJob, fatherJob, grownUp, false, false);
		}

		Parents grown() {
			return new Parents(mother, father, motherJob, fatherJob, true, motherSchooledMaster, fatherSchooledMaster);
		}
	}

	/** Notes {@code mother} and {@code father} as {@code baby}'s parents. */
	public static void born(Villager baby, Villager mother, Villager father) {
		ModAttachments.PARENTS.set(baby, new Parents(mother.getDisplayName(), father.getDisplayName(), job(mother), job(father), false,
			schooledMaster(mother), schooledMaster(father)));
	}

	/** A schooled Master: schooled as a child and now at the top level of a trade (29.7's born Legends and gifts). */
	public static boolean schooledMaster(Villager villager) {
		return io.github.jcondedata.aliveworkplace.school.Schools.isSchooled(villager) && !job(villager).isEmpty()
			&& villager.getVillagerData().getLevel() >= net.minecraft.world.entity.npc.VillagerData.MAX_VILLAGER_LEVEL;
	}

	@Nullable
	public static Parents parents(Villager villager) {
		return ModAttachments.PARENTS.get(villager);
	}

	private static String job(Villager villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		return p == VillagerProfession.NONE || p == VillagerProfession.NITWIT ? "" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString();
	}

	/**
	 * The hall's round for one villager: a child just grown up goes in the chronicle; a grown child without a job takes up
	 * a parent's trade if there's a free workstation for it in the village.
	 */
	public static void round(ServerLevel level, BlockPos hall, Villager villager) {
		round(level, hall, villager, level.random);
	}

	/**
	 * The same with the dice of the born rolls given (tests): a child of two schooled Masters may grow up a Legend or
	 * Gifted ({@link io.github.jcondedata.aliveworkplace.legend.BornGifts}).
	 */
	public static void round(ServerLevel level, BlockPos hall, Villager villager, net.minecraft.util.RandomSource random) {
		Parents parents = parents(villager);
		if (parents == null || villager.isBaby()) {
			return;
		}
		if (!parents.grownUp()) {
			ModAttachments.PARENTS.set(villager, parents.grown());
			io.github.jcondedata.aliveworkplace.legend.BornGifts.Outcome born = io.github.jcondedata.aliveworkplace.legend.BornGifts.grownUp(level, hall, villager, parents, random);
			if (born.legend() != null) {
				Chronicle.record(level, hall, Chronicle.Kind.LEGEND, io.github.jcondedata.aliveworkplace.legend.BornGifts.legendLine(villager, parents, born.legend()));
				return; // a Master of the Legend's trade already
			}
			io.github.jcondedata.aliveworkplace.legend.Gifted.Gift gift = io.github.jcondedata.aliveworkplace.legend.Gifted.of(villager);
			Chronicle.record(level, hall, Chronicle.Kind.BIRTH, gift != null
				? Component.translatable("chronicle.aliveworkplace.grown_up_gifted", villager.getDisplayName(), parents.mother(), parents.father(), gift.title())
				: Component.translatable("chronicle.aliveworkplace.grown_up", villager.getDisplayName(), parents.mother(), parents.father()));
		}
		if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return;
		}
		List<VillageHalls.FreeStation> free = VillageHalls.freeStations(level, hall);
		for (String job : List.of(parents.motherJob(), parents.fatherJob())) {
			if (job.isEmpty()) {
				continue;
			}
			for (VillageHalls.FreeStation station : free) {
				if (BuiltInRegistries.VILLAGER_PROFESSION.getKey(station.profession()).toString().equals(job)
					&& ClassJobs.may(hall, villager, station.profession()) // a parent's trade above the child's class waits (34.8)
					&& VillageHalls.assign(level, villager, station)) {
					Component trade = Component.translatable("entity.minecraft.villager." + station.profession().name());
					Chronicle.record(level, hall, Chronicle.Kind.JOINED, Component.translatable("chronicle.aliveworkplace.family_trade",
						villager.getDisplayName(), trade));
					return;
				}
			}
		}
	}

	private Families() {
	}
}
