package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
	 * Who a villager's parents are: names, their jobs when the child was born, whether the child has grown up, whether
	 * each parent was a schooled Master at the birth (29.7; false in older saves), and the parents' ids (34.21,
	 * {@code mother_id} and {@code father_id}; null in records saved before, which keep the names only).
	 */
	public record Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp,
						  boolean motherSchooledMaster, boolean fatherSchooledMaster, @Nullable UUID motherId, @Nullable UUID fatherId) {
		public static final Codec<Parents> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("mother").forGetter(Parents::mother),
			ComponentSerialization.CODEC.fieldOf("father").forGetter(Parents::father),
			Codec.STRING.optionalFieldOf("mother_job", "").forGetter(Parents::motherJob),
			Codec.STRING.optionalFieldOf("father_job", "").forGetter(Parents::fatherJob),
			Codec.BOOL.optionalFieldOf("grown_up", false).forGetter(Parents::grownUp),
			Codec.BOOL.optionalFieldOf("mother_schooled_master", false).forGetter(Parents::motherSchooledMaster),
			Codec.BOOL.optionalFieldOf("father_schooled_master", false).forGetter(Parents::fatherSchooledMaster),
			UUIDUtil.CODEC.optionalFieldOf("mother_id").forGetter(p -> Optional.ofNullable(p.motherId())),
			UUIDUtil.CODEC.optionalFieldOf("father_id").forGetter(p -> Optional.ofNullable(p.fatherId()))
		).apply(i, (mother, father, motherJob, fatherJob, grownUp, motherMaster, fatherMaster, motherId, fatherId) ->
			new Parents(mother, father, motherJob, fatherJob, grownUp, motherMaster, fatherMaster, motherId.orElse(null), fatherId.orElse(null))));

		/** Parents known by name only (no ids), as every record saved before 34.21. */
		public Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp,
					   boolean motherSchooledMaster, boolean fatherSchooledMaster) {
			this(mother, father, motherJob, fatherJob, grownUp, motherSchooledMaster, fatherSchooledMaster, null, null);
		}

		/** Parents neither of whom is on record as a schooled Master. */
		public Parents(Component mother, Component father, String motherJob, String fatherJob, boolean grownUp) {
			this(mother, father, motherJob, fatherJob, grownUp, false, false);
		}

		Parents grown() {
			return new Parents(mother, father, motherJob, fatherJob, true, motherSchooledMaster, fatherSchooledMaster, motherId, fatherId);
		}
	}

	/** Notes {@code mother} and {@code father} as {@code baby}'s parents, by name and id; the baby takes a parent's family name (34.21). */
	public static void born(Villager baby, Villager mother, Villager father) {
		ModAttachments.PARENTS.set(baby, new Parents(mother.getDisplayName(), father.getDisplayName(), job(mother), job(father), false,
			schooledMaster(mother), schooledMaster(father), mother.getUUID(), father.getUUID()));
		FamilyBook.born(baby, mother, father); // the family name (34.21)
		io.github.jcondedata.aliveworkplace.story.FriendPerks.nameBaby(baby, mother, father); // the first name, after a friend of theirs (31.12)
		if (mother.level() instanceof net.minecraft.server.level.ServerLevel level) {
			io.github.jcondedata.aliveworkplace.hall.VillageMoments.born(level, mother.blockPosition()); // the questbook's "born here" (36.2)
		}
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
	 * a parent's trade if a free workstation in the village offers it (any of its jobs, B397).
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
			LifeStages.setAdultSince(villager, Chronicle.day(level)); // grown up today (34.19)
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
		java.util.Map<net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>, List<VillagerProfession>> jobs = new java.util.HashMap<>();
		for (String job : List.of(parents.motherJob(), parents.fatherJob())) {
			if (job.isEmpty()) {
				continue;
			}
			for (VillageHalls.FreeStation block : free) {
				// Any job the block offers, not only its first (B397: a lectern is a Librarian's first, and a Scholar's too).
				VillagerProfession trade = null;
				for (VillagerProfession p : jobs.computeIfAbsent(block.poi(), poi -> VillageHalls.jobs(block))) {
					if (BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString().equals(job)) {
						trade = p;
						break;
					}
				}
				if (trade == null) {
					continue;
				}
				VillageHalls.FreeStation station = new VillageHalls.FreeStation(block.pos(), trade, block.poi());
				if (ClassJobs.may(hall, villager, trade) // a parent's trade above the child's class waits (34.8)
					&& VillageHalls.assign(level, villager, station)) {
					Component name = Component.translatable("entity.minecraft.villager." + trade.name());
					Chronicle.record(level, hall, Chronicle.Kind.JOINED, Component.translatable("chronicle.aliveworkplace.family_trade",
						villager.getDisplayName(), name));
					return;
				}
			}
		}
	}

	private Families() {
	}
}
