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
	/** Who a villager's parents are: names, their jobs when the child was born, and whether the child has grown up. */
	public record Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp) {
		public static final Codec<Parents> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("mother").forGetter(Parents::mother),
			ComponentSerialization.CODEC.fieldOf("father").forGetter(Parents::father),
			Codec.STRING.optionalFieldOf("mother_job", "").forGetter(Parents::motherJob),
			Codec.STRING.optionalFieldOf("father_job", "").forGetter(Parents::fatherJob),
			Codec.BOOL.optionalFieldOf("grown_up", false).forGetter(Parents::grownUp)
		).apply(i, Parents::new));

		Parents grown() {
			return new Parents(mother, father, motherJob, fatherJob, true);
		}
	}

	/** Notes {@code mother} and {@code father} as {@code baby}'s parents. */
	public static void born(Villager baby, Villager mother, Villager father) {
		baby.setAttached(ModAttachments.PARENTS, new Parents(mother.getDisplayName(), father.getDisplayName(), job(mother), job(father), false));
	}

	@Nullable
	public static Parents parents(Villager villager) {
		return villager.getAttached(ModAttachments.PARENTS);
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
		Parents parents = parents(villager);
		if (parents == null || villager.isBaby()) {
			return;
		}
		if (!parents.grownUp()) {
			villager.setAttached(ModAttachments.PARENTS, parents.grown());
			Chronicle.record(level, hall, Chronicle.Kind.BIRTH, Component.translatable("chronicle.aliveworkplace.grown_up", villager.getDisplayName(),
				parents.mother(), parents.father()));
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
				if (BuiltInRegistries.VILLAGER_PROFESSION.getKey(station.profession()).toString().equals(job) && VillageHalls.assign(level, villager, station)) {
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
