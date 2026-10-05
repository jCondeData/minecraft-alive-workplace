package io.github.jcondedata.aliveworkplace.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The toolbox effects the Legends' research trees need (ROADMAP 29.11), registered with {@link CivicEffects} so edicts,
 * tonics and guilds may use them too. Each is read by the one system it changes, from the effects of the village's
 * edicts and of its research trees together ({@link ResearchTrees#effects}):
 * <ul>
 * <li>{@code wellbeing} ({@code percent}, optional {@code at_least}): {@code VillageNeeds}' wellbeing, points added, and
 * never below the highest {@code at_least};</li>
 * <li>{@code illness} ({@code percent}, {@code days}): {@code people/Sickness}, the daily chance and how many days an
 * illness lasts (added; never under a quarter of a day);</li>
 * <li>{@code raid_chance} ({@code factor}): {@code guard/VillageRaids}' nightly chance, multiplied;</li>
 * <li>{@code xp} ({@code percent}): the XP every villager of the village earns ({@code people/Traits.xp}), added;</li>
 * <li>{@code loot_luck} ({@code luck}): the luck of every loot roll a villager's work makes (sifters, explorers,
 * netherworkers, fishers), added;</li>
 * <li>{@code flag} ({@code name}): a named switch that other code reads with {@link #flag}.</li>
 * </ul>
 * A research topic's effects count once per level it has.
 */
public final class TreeEffects {
	private static final Codec<List<ResourceLocation>> JOBS = ResourceLocation.CODEC.listOf();

	public static final ResourceLocation WELLBEING = AliveWorkplace.id("wellbeing");
	public static final ResourceLocation ILLNESS = AliveWorkplace.id("illness");
	public static final ResourceLocation RAID_CHANCE = AliveWorkplace.id("raid_chance");
	public static final ResourceLocation XP = AliveWorkplace.id("xp");
	public static final ResourceLocation LOOT_LUCK = AliveWorkplace.id("loot_luck");
	public static final ResourceLocation FLAG = AliveWorkplace.id("flag");

	static {
		CivicEffects.register(WELLBEING, RecordCodecBuilder.<Wellbeing>mapCodec(i -> i.group(
			Codec.intRange(-100, 100).optionalFieldOf("percent", 0).forGetter(Wellbeing::percent),
			Codec.intRange(0, 100).optionalFieldOf("at_least").forGetter(Wellbeing::atLeast),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Wellbeing::jobs)
		).apply(i, Wellbeing::new)));
		CivicEffects.register(ILLNESS, RecordCodecBuilder.<Illness>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).optionalFieldOf("percent", 0).forGetter(Illness::percent),
			Codec.intRange(-30, 30).optionalFieldOf("days", 0).forGetter(Illness::days),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Illness::jobs)
		).apply(i, Illness::new)));
		CivicEffects.register(RAID_CHANCE, RecordCodecBuilder.<RaidChance>mapCodec(i -> i.group(
			Codec.floatRange(0f, 100f).fieldOf("factor").forGetter(RaidChance::factor),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(RaidChance::jobs)
		).apply(i, RaidChance::new)));
		CivicEffects.register(XP, RecordCodecBuilder.<Xp>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(Xp::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Xp::jobs)
		).apply(i, Xp::new)));
		CivicEffects.register(LOOT_LUCK, RecordCodecBuilder.<LootLuck>mapCodec(i -> i.group(
			Codec.intRange(-100, 100).fieldOf("luck").forGetter(LootLuck::luck),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(LootLuck::jobs)
		).apply(i, LootLuck::new)));
		CivicEffects.register(FLAG, RecordCodecBuilder.<Flag>mapCodec(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Flag::name),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Flag::jobs)
		).apply(i, Flag::new)));
	}

	/** Makes sure the types are registered (before any tree or edict file is read). */
	public static void init() {
	}

	/** {@code wellbeing}: the village's wellbeing {@code percent} points higher, and never below {@code at_least}. */
	public record Wellbeing(int percent, Optional<Integer> atLeast, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return WELLBEING;
		}
	}

	/** {@code illness}: villagers {@code percent} likelier to fall ill, and ill {@code days} longer. */
	public record Illness(int percent, int days, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return ILLNESS;
		}
	}

	/** {@code raid_chance}: night raids {@code factor} times as likely (0.8: a fifth less likely). */
	public record RaidChance(float factor, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RAID_CHANCE;
		}
	}

	/** {@code xp}: villagers earn {@code percent} more XP. */
	public record Xp(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return XP;
		}
	}

	/** {@code loot_luck}: {@code luck} more luck on every loot roll a villager's work makes. */
	public record LootLuck(int luck, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return LOOT_LUCK;
		}
	}

	/** {@code flag}: the switch {@code name} is on in the village. */
	public record Flag(String name, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return FLAG;
		}
	}

	/** The effect sums in force in {@code hall}'s village: its edicts' and its research trees'. */
	private static List<CivicEffects.Sum> sums(@Nullable VillageHallBlockEntity hall) {
		return hall == null ? List.of() : List.of(CivicEffects.of(hall), ResearchTrees.effects(hall));
	}

	private static List<CivicEffects.Sum> sums(ServerLevel level, BlockPos hall) {
		return sums(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity : null);
	}

	/** The wellbeing points (a share, 0.1 = 10%) the village's {@code wellbeing} effects add. */
	public static float wellbeing(@Nullable VillageHallBlockEntity hall) {
		int percent = 0;
		for (CivicEffects.Sum sum : sums(hall)) {
			for (CivicEffects.Active a : sum.all()) {
				if (a.effect() instanceof Wellbeing w) {
					percent += w.percent();
				}
			}
		}
		return percent / 100f;
	}

	/** The lowest the village's wellbeing may be (a share; 0 without an {@code at_least}). */
	public static float wellbeingFloor(@Nullable VillageHallBlockEntity hall) {
		int floor = 0;
		for (CivicEffects.Sum sum : sums(hall)) {
			for (CivicEffects.Active a : sum.all()) {
				if (a.effect() instanceof Wellbeing w && w.atLeast().isPresent()) {
					floor = Math.max(floor, w.atLeast().get());
				}
			}
		}
		return floor / 100f;
	}

	/** How much likelier (percent, added) {@code villager} is to fall ill. */
	public static int illnessPercent(Villager villager) {
		int percent = 0;
		for (CivicEffects.Sum sum : sums(CivicEffects.hallOf(villager))) {
			for (Illness i : sum.of(Illness.class, villager)) {
				percent += i.percent();
			}
		}
		return percent;
	}

	/** How long {@code villager} stays ill, in ticks: {@code usual} with the {@code illness} days added, a quarter day at least. */
	public static long illnessLasts(Villager villager, long usual) {
		int days = 0;
		for (CivicEffects.Sum sum : sums(CivicEffects.hallOf(villager))) {
			for (Illness i : sum.of(Illness.class, villager)) {
				days += i.days();
			}
		}
		return Math.max(VillageNeeds.DAY / 4, usual + days * VillageNeeds.DAY);
	}

	/** How many times as likely a night raid is on the village round {@code hall} (the {@code raid_chance} factors). */
	public static float raidChance(ServerLevel level, BlockPos hall) {
		float factor = 1f;
		for (CivicEffects.Sum sum : sums(level, hall)) {
			for (CivicEffects.Active a : sum.all()) {
				if (a.effect() instanceof RaidChance r) {
					factor *= r.factor();
				}
			}
		}
		return factor;
	}

	/** How much more XP (percent, added) {@code villager} earns. */
	public static int xpPercent(Villager villager) {
		int percent = 0;
		for (CivicEffects.Sum sum : sums(CivicEffects.hallOf(villager))) {
			for (Xp x : sum.of(Xp.class, villager)) {
				percent += x.percent();
			}
		}
		return percent;
	}

	/** How much more luck {@code villager}'s loot rolls have. */
	public static int lootLuck(Villager villager) {
		int luck = 0;
		for (CivicEffects.Sum sum : sums(CivicEffects.hallOf(villager))) {
			for (LootLuck l : sum.of(LootLuck.class, villager)) {
				luck += l.luck();
			}
		}
		return luck;
	}

	/** Whether the switch {@code name} is on in the village round {@code hall}. */
	public static boolean flag(ServerLevel level, BlockPos hall, String name) {
		return flag(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity : null, name);
	}

	/** Whether the switch {@code name} is on in {@code hall}'s village. */
	public static boolean flag(@Nullable VillageHallBlockEntity hall, String name) {
		for (CivicEffects.Sum sum : sums(hall)) {
			for (CivicEffects.Active a : sum.all()) {
				if (a.effect() instanceof Flag f && f.name().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether the switch {@code name} is on in the village {@code villager} lives in (for them, if it names jobs). */
	public static boolean flag(Villager villager, String name) {
		for (CivicEffects.Sum sum : sums(CivicEffects.hallOf(villager))) {
			for (Flag f : sum.of(Flag.class, villager)) {
				if (f.name().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private TreeEffects() {
	}
}
