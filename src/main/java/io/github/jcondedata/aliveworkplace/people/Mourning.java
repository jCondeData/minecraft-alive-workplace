package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * A death matters (ROADMAP 36.16, owner 2026-10-20). When a grown villager of a village with a hall dies, the players
 * near the village are told who it was, their job and their years ("Mira, the village's Builder, has died aged 31. The
 * village mourns."), and the chronicle's line of the death says the same after its cause. Their family (parents,
 * children, brothers and sisters: {@link Couples#related}) grieve for {@link #GRIEF_DAYS} days, {@link #GRIEF_MOOD} off
 * their mood; their partner mourns as {@link Couples} has them do ({@link Couples#MOURNING_DAYS} days, 15 off). While
 * either lasts they work {@link #PACE} slower ({@code work/Pace}'s {@code mourning}). Then it is over: the hall's round
 * takes the grief off ({@link #endIfOver}).
 *
 * <p>Their grave waits for a <b>funeral</b>: the village's Undertaker ({@code grave/UndertakerWork}) goes to it before
 * anything else, the villagers near come to stand by it, the bell tolls, and after {@link #FUNERAL_TICKS} the chronicle
 * notes it ("Mira's funeral, led by Ulric, the Undertaker; 4 came to say goodbye"). A grave from before 36.16 has none.
 *
 * <p>Years: a villager is {@link #GROWN_UP_AGE} the day they grow up and a year older each village year (four seasons of
 * {@code seasonDays}); with the default 16-day seasons an elder is about 36 and passes at about 43. {@code villageMourning}
 * in the config turns it all off (the partner's mourning stays, as before 36.16).
 */
public final class Mourning {
	public static boolean ENABLED = true;
	/** Days a dead villager's family grieve. */
	public static final int GRIEF_DAYS = 3;
	/** What grief takes off their mood. */
	public static final int GRIEF_MOOD = 10;
	/** The work-time factor of anyone mourning: 10% slower. */
	public static final float PACE = 1.1f;
	/** A villager's age the day they grow up. */
	public static final int GROWN_UP_AGE = 18;
	/** Ticks the Undertaker stands at the grave for a funeral. */
	public static final int FUNERAL_TICKS = 160;
	/** How far away villagers come to a funeral from. */
	public static final int MOURNER_RANGE = 24;
	/** How near the grave a villager counts as having come. */
	static final int ATTENDED = 8;

	/** A death a villager grieves: the day and who it was. */
	public record Grief(long day, Component name) {
		public static final Codec<Grief> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Grief::day),
			ComponentSerialization.CODEC.optionalFieldOf("name", Component.empty()).forGetter(Grief::name)
		).apply(i, Grief::new));
	}

	// --- Years ----------------------------------------------------------------------------------------------------

	/** Days in a village year: four seasons. */
	public static int yearDays() {
		return 4 * Math.max(1, Seasons.DAYS);
	}

	/** {@code villager}'s age in years on {@code day}; -1 when nobody counted their days (an old save without a hall). */
	public static long years(Villager villager, long day) {
		Long since = LifeStages.adultSince(villager);
		return since == null ? -1 : GROWN_UP_AGE + Math.max(0, day - since) / yearDays();
	}

	/** "aged 31", or "at an age nobody had counted". */
	public static Component age(long years) {
		return years < 0 ? Component.translatable("message.aliveworkplace.mourning.age_unknown")
			: Component.translatable("message.aliveworkplace.mourning.age", years);
	}

	/** "Builder", or "villager" for one without a job. */
	public static Component job(Villager villager) {
		VillagerProfession job = villager.getVillagerData().getProfession();
		return job == VillagerProfession.NONE ? Component.translatable("message.aliveworkplace.mourning.no_job")
			: Component.translatable("entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath());
	}

	/** "Mira, the village's Builder, has died aged 31. The village mourns." */
	public static Component told(Villager villager, long day) {
		return villager.hasCustomName()
			? Component.translatable("message.aliveworkplace.mourning.died", villager.getDisplayName(), job(villager), age(years(villager, day)))
			: Component.translatable("message.aliveworkplace.mourning.died_unnamed", job(villager), age(years(villager, day)));
	}

	// --- A death --------------------------------------------------------------------------------------------------

	/** Whether {@code villager}'s death is the village's to mourn: grown, and one of its own (no mercenary, guest or carter). */
	static boolean mourned(Villager villager) {
		return !villager.isBaby() && !Mercenaries.isMercenary(villager) && !io.github.jcondedata.aliveworkplace.inn.Innkeepers.isTraveller(villager)
			&& !io.github.jcondedata.aliveworkplace.hall.CaravanSights.isParty(villager);
	}

	/**
	 * {@code villager} just died of {@code cause}, leaving the grave at {@code grave} (or none): the village is told, the
	 * family grieve, the grave waits for its funeral. Returns the chronicle's line of the death: the cause, then the job
	 * and years (just the cause outside a village with a hall, or with mourning off).
	 */
	public static Component onDeath(ServerLevel level, Villager villager, Component cause, @Nullable BlockPos grave) {
		if (!ENABLED || !mourned(villager)) {
			return cause;
		}
		// B562: the village is its circle and height, not a ball round the hall (a death on its rim up a hill is its own)
		Optional<BlockPos> hall = VillageHalls.containing(level, villager.blockPosition());
		if (hall.isEmpty()) {
			return cause;
		}
		long day = Chronicle.day(level);
		Component told = told(villager, day).copy().withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : level.getPlayers(p -> VillageHalls.contains(hall.get(), p.blockPosition()))) {
			Chat.chat(p, told);
		}
		Component name = villager.getDisplayName().copy();
		for (Villager other : VillageHalls.villagers(level, hall.get(),
			v -> v != villager && Couples.related(villager, v))) {
			ModAttachments.GRIEF.set(other, new Grief(day, name));
			Moods.forget(other);
		}
		if (grave != null && level.getBlockEntity(grave) instanceof GraveBlockEntity g) {
			g.setFuneral(true);
		}
		return Component.translatable("chronicle.aliveworkplace.mourning.died", cause, job(villager), age(years(villager, day)));
	}

	// --- Grief ----------------------------------------------------------------------------------------------------

	/** Whether {@code villager} grieves a death in the family on {@code day}. */
	public static boolean grieving(Villager villager, long day) {
		Grief grief = ModAttachments.GRIEF.get(villager);
		return ENABLED && grief != null && day - grief.day() < GRIEF_DAYS;
	}

	/** Whether {@code villager} mourns anyone today: a partner ({@link Couples#isMourning}) or family. */
	public static boolean mourning(ServerLevel level, Villager villager) {
		return Couples.isMourning(level, villager) || grieving(villager, Chronicle.day(level));
	}

	/** Work-time factor ({@code work/Pace}): {@link #PACE} while mourning, with mourning on. */
	public static float pace(Villager villager) {
		return ENABLED && villager.level() instanceof ServerLevel level && mourning(level, villager) ? PACE : 1f;
	}

	/** Grief that has run its days comes off (the hall's round); mourning is over. */
	public static void endIfOver(ServerLevel level, Villager villager, long day) {
		Grief grief = ModAttachments.GRIEF.get(villager);
		if (grief != null && day - grief.day() >= GRIEF_DAYS) {
			ModAttachments.GRIEF.remove(villager);
			Moods.forget(villager);
		}
	}

	// --- The funeral ----------------------------------------------------------------------------------------------

	/** Whether a funeral waits at {@code pos}. */
	public static boolean funeralDue(ServerLevel level, BlockPos pos) {
		return ENABLED && level.getBlockEntity(pos) instanceof GraveBlockEntity g && g.funeralDue();
	}

	/** The villagers who would come to a funeral at {@code grave}: grown, near, not the Undertaker. */
	static List<Villager> near(ServerLevel level, BlockPos grave, Villager undertaker, int range) {
		return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(grave).inflate(range),
			v -> v != undertaker && v.isAlive() && !v.isBaby() && !v.isSleeping() && mourned(v));
	}

	/** The funeral begins: the villagers near come to stand by the grave, and the bell tolls. */
	public static void gather(ServerLevel level, BlockPos grave, Villager undertaker) {
		for (Villager v : near(level, grave, undertaker, MOURNER_RANGE)) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(grave, 0.5f, 3));
		}
		level.playSound(null, grave, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8f, 0.5f);
	}

	/** One beat of the funeral: candle smoke over the grave, now and then the bell, and the mourners kept by it. */
	public static void beat(ServerLevel level, BlockPos grave, Villager undertaker, int left) {
		if (left % 20 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, grave.getX() + 0.5, grave.getY() + 0.9, grave.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
			level.sendParticles(ParticleTypes.SOUL, grave.getX() + 0.5, grave.getY() + 0.8, grave.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
		}
		if (left % 60 == 0) {
			level.playSound(null, grave, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6f, 0.5f);
			for (Villager v : near(level, grave, undertaker, MOURNER_RANGE)) {
				if (v.distanceToSqr(grave.getCenter()) > ATTENDED * ATTENDED) {
					v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(grave, 0.5f, 3));
				}
			}
		}
	}

	/** The funeral is over: the grave has had its funeral, and the chronicle (and the players near) are told who came. */
	public static void held(ServerLevel level, BlockPos grave, Villager undertaker) {
		if (!(level.getBlockEntity(grave) instanceof GraveBlockEntity g)) {
			return;
		}
		g.setFuneral(false);
		int came = near(level, grave, undertaker, ATTENDED).size();
		Component text = Component.translatable("chronicle.aliveworkplace.funeral", g.name(), undertaker.getDisplayName(), came);
		Chronicle.record(level, grave, Chronicle.Kind.FUNERAL, text);
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(grave.getCenter()) < 48 * 48)) {
			Chat.chat(p, text.copy().withStyle(ChatFormatting.GRAY));
		}
		level.playSound(null, grave, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.6f, 0.6f);
	}

	private Mourning() {
	}
}
