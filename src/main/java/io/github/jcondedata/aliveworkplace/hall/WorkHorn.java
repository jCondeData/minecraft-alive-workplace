package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The Work Horn (ROADMAP 30.11, docs/design/M30.md): blown in a village it calls a rush, and every grown villager of
 * the village works 50% faster for {@link #RUSH_TICKS} (the {@code work_horn} source of {@code work/Pace}, so the cap
 * holds), with sparks over the rushing workers now and then. Once a village a day: the hall keeps the day it was blown
 * and the time the rush ends. When the rush ends the villagers who answered it are worn out, {@link #WORN_OUT} less
 * happy until the next dawn (attachment {@code worn_out}). Only the hall's owner, their friends and operators can call a
 * rush in an owned village ({@link VillageProtection#mayBuild}); anyone can in a village nobody owns. {@code workHorns}
 * off: the horn sounds and calls no rush.
 */
public final class WorkHorn {
	/** {@code workHorns} in the config. */
	public static boolean ENABLED = true;
	/** How long a rush lasts: 5 minutes (tests shorten it). */
	public static long RUSH_TICKS = 6000;
	/** The work time in a rush: 50% faster. */
	public static final float FACTOR = 1f / 1.5f;
	/** How much less happy the villagers are once the rush is over, until dawn. */
	public static final int WORN_OUT = 10;
	/** How often the rushing workers may give off sparks, and the chance each one does then. */
	static final int SPARK_EVERY = 40;
	private static final float SPARK_CHANCE = 0.3f;

	/** A villager worn out by a rush: from the game time the rush ended until the day time of the next dawn. */
	public record WornOut(long from, long untilDayTime) {
		public static final Codec<WornOut> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("from").forGetter(WornOut::from),
			Codec.LONG.fieldOf("until").forGetter(WornOut::untilDayTime)
		).apply(i, WornOut::new));
	}

	/** What blowing the horn did. */
	public enum Outcome {
		/** A rush was called. */
		RUSH,
		/** No village with a hall here. */
		NO_VILLAGE,
		/** The village already answered the horn today. */
		USED_TODAY,
		/** Someone who isn't the owner or a friend blew it in an owned village. */
		NOT_ALLOWED,
		/** Work Horns are switched off: only the sound. */
		DISABLED
	}

	/** What blowing did, and what to tell the player who blew it (null: nothing). */
	public record Result(Outcome outcome, @Nullable Component message) {
	}

	/**
	 * {@code player} blows the horn at {@code where}: the village round it (its nearest hall) answers with a rush if it
	 * may. The sound is the item's; this is the rest.
	 */
	public static Result blow(ServerLevel level, Player player, BlockPos where) {
		if (!ENABLED) {
			return new Result(Outcome.DISABLED, null);
		}
		BlockPos hallPos = VillageHalls.nearest(level, where).orElse(null);
		if (hallPos == null || !(level.getBlockEntity(hallPos) instanceof VillageHallBlockEntity hall)) {
			return new Result(Outcome.NO_VILLAGE, Component.translatable("message.aliveworkplace.work_horn.no_village").withStyle(ChatFormatting.GRAY));
		}
		Component village = VillageHalls.name(level, hallPos);
		if (!VillageProtection.mayBuild(level, hall, player)) {
			return new Result(Outcome.NOT_ALLOWED, Component.translatable("message.aliveworkplace.work_horn.not_allowed", hall.ownerName(), village)
				.withStyle(ChatFormatting.RED));
		}
		long today = Chronicle.day(level);
		if (hall.hornDay() == today) {
			return new Result(Outcome.USED_TODAY, Component.translatable("message.aliveworkplace.work_horn.used").withStyle(ChatFormatting.YELLOW));
		}
		long end = level.getGameTime() + RUSH_TICKS;
		hall.startRush(today, end);
		// Dawn after the rush ends, on the day clock (day time 0 is sunrise).
		long endDayTime = level.getDayTime() + RUSH_TICKS;
		long dawn = (Math.floorDiv(endDayTime, VillageNeeds.DAY) + 1) * VillageNeeds.DAY;
		int answered = 0;
		for (Villager villager : grown(level, hallPos)) {
			ModAttachments.WORN_OUT.set(villager, new WornOut(end, dawn));
			answered++;
		}
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		return new Result(Outcome.RUSH, Component.translatable("message.aliveworkplace.work_horn.rush", village,
			io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.work_horn.villagers", answered, answered)).withStyle(ChatFormatting.GOLD));
	}

	private static List<Villager> grown(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby());
	}

	/** Whether the village of {@code hall} is rushing now. */
	public static boolean rushing(ServerLevel level, @Nullable VillageHallBlockEntity hall) {
		return ENABLED && hall != null && level.getGameTime() < hall.rushUntil();
	}

	/** The work time a rush makes for {@code villager} (a source of {@code work/Pace}): {@link #FACTOR} while it lasts. */
	public static float pace(Villager villager) {
		if (!ENABLED || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return 1f;
		}
		return rushing(level, CivicEffects.hallOf(villager)) ? FACTOR : 1f;
	}

	/** Whether {@code villager} is worn out by a rush now (after it ended, before dawn). */
	public static boolean wornOut(ServerLevel level, Villager villager) {
		WornOut worn = ModAttachments.WORN_OUT.get(villager);
		if (worn == null) {
			return false;
		}
		if (level.getDayTime() >= worn.untilDayTime()) {
			ModAttachments.WORN_OUT.remove(villager);
			return false;
		}
		return level.getGameTime() >= worn.from();
	}

	/** The hall's tick: now and then, sparks over some of the rushing workers. */
	static void tick(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall) {
		if (!rushing(level, hall) || Math.floorMod(level.getGameTime() + pos.hashCode(), SPARK_EVERY) != 0) {
			return;
		}
		for (Villager villager : grown(level, pos)) {
			if (level.random.nextFloat() < SPARK_CHANCE) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(),
					6, 0.3, 0.2, 0.3, 0.05);
			}
		}
	}

	/** How the horn reads on the Book of Edicts' last row: ready, rushing (minutes left) or used today. */
	public static List<Component> status(ServerLevel level, VillageHallBlockEntity hall) {
		if (!ENABLED) {
			return List.of(VillageHallScreen.line("screen.aliveworkplace.edicts.horn.disabled", ChatFormatting.RED));
		}
		if (rushing(level, hall)) {
			long minutes = Math.max(1, (hall.rushUntil() - level.getGameTime() + 1199) / 1200);
			return List.of(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.edicts.horn.rushing", minutes), ChatFormatting.GOLD),
				VillageHallScreen.line("screen.aliveworkplace.edicts.horn.how", ChatFormatting.DARK_GRAY));
		}
		if (hall.hornDay() == Chronicle.day(level)) {
			return List.of(VillageHallScreen.line("screen.aliveworkplace.edicts.horn.used", ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.edicts.horn.how", ChatFormatting.DARK_GRAY));
		}
		return List.of(VillageHallScreen.line("screen.aliveworkplace.edicts.horn.ready", ChatFormatting.GREEN),
			VillageHallScreen.line("screen.aliveworkplace.edicts.horn.how", ChatFormatting.DARK_GRAY));
	}

	private WorkHorn() {
	}
}
