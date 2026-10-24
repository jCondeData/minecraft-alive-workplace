package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

/**
 * Retirement and apprentices (ROADMAP 34.20). In the hall's round, an elder with a job retires once someone can take
 * over, at most one villager a village every {@link #EVERY_DAYS} days: their own grown child without a job, else any
 * jobless grown villager whose class the job allows (34.8). With nobody, the elder keeps working and their card on the
 * hall's list says so. Never while they hold something for a player or a build ({@link #holds}).
 *
 * <p>The successor first <b>shadows</b> the elder for {@link #SHADOW_DAYS} days ("learning from Bram" over their head,
 * the {@code retire_shadow} attachment), then takes the workstation and the job one level below the elder's (at least
 * Apprentice), with the job's assignments ({@link #ASSIGNMENTS}). The retired elder ({@code retired}) keeps their trades
 * for players but never takes a job again ({@link #refuses}, and their search for a workstation sees none); by day they
 * mentor a Novice or Apprentice of their old trade, who learns {@link #MENTOR_BONUS 50%} faster ("taught by Bram",
 * {@code taught_by}), or else sit by the hall.
 */
public final class Retirement {
	/** {@code elderRetirement} in the config. Off: nobody starts retiring; those retired stay retired. */
	public static boolean ENABLED = true;
	/** At most one retirement a village in this many days. */
	public static final int EVERY_DAYS = 3;
	/** Days the successor shadows the elder before the hand-over. */
	public static final int SHADOW_DAYS = 2;
	/** How much faster a mentored Novice or Apprentice learns. */
	public static final float MENTOR_BONUS = 0.5f;
	/** How long a mentor's teaching lasts after the hall's round saw them together, in ticks. */
	public static final int TAUGHT_TICKS = 1800;
	private static final int TICK_EVERY = 20;

	/** A retired elder: the day they retired, the trade they left (a profession id) and their level in it. */
	public record Retired(long day, String trade, int level) {
		public static final Codec<Retired> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Retired::day),
			Codec.STRING.optionalFieldOf("trade", "").forGetter(Retired::trade),
			Codec.INT.optionalFieldOf("level", 1).forGetter(Retired::level)
		).apply(i, Retired::new));

		public Retired(long day, String trade) {
			this(day, trade, 1);
		}
	}

	/** A successor learning from {@code elder} since {@code since} (a {@code Chronicle.day}). */
	public record Shadow(UUID elder, String elderName, long since) {
		public static final Codec<Shadow> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("elder").forGetter(Shadow::elder),
			Codec.STRING.optionalFieldOf("elder_name", "").forGetter(Shadow::elderName),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Shadow::since)
		).apply(i, Shadow::new));
	}

	/** A Novice or Apprentice taught by a retired elder until the game time {@code until}. */
	public record Teacher(UUID mentor, String mentorName, long until) {
		public static final Codec<Teacher> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("mentor").forGetter(Teacher::mentor),
			Codec.STRING.optionalFieldOf("mentor_name", "").forGetter(Teacher::mentorName),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(Teacher::until)
		).apply(i, Teacher::new));
	}

	/** The job's assignments, carried from the elder to the successor at the hand-over. */
	public static final List<Attachment<?>> ASSIGNMENTS = List.of(ModAttachments.FARM_FIELD, ModAttachments.TREE_FARM,
		ModAttachments.ORCHARD, ModAttachments.COURIER_ROUTES, ModAttachments.PATROL_ROUTE, ModAttachments.BALL_ORDERS,
		ModAttachments.BUILDER_EMPLOYER);

	// --- State ----------------------------------------------------------------------------------------------------

	public static boolean isRetired(Villager villager) {
		return ModAttachments.RETIRED.has(villager);
	}

	@Nullable
	public static Shadow shadow(Villager villager) {
		return ModAttachments.RETIRE_SHADOW.get(villager);
	}

	/** Whose teaching {@code villager} has right now, or null. */
	@Nullable
	public static Teacher teacher(Villager villager) {
		Teacher t = ModAttachments.TAUGHT_BY.get(villager);
		return t != null && t.until() >= villager.level().getGameTime() ? t : null;
	}

	/** Whether {@code villager} has a job (one that can be handed over). */
	private static boolean hasJob(Villager villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		return p != VillagerProfession.NONE && p != VillagerProfession.NITWIT;
	}

	/**
	 * Why {@code elder} can't retire now because they hold something for a player or a build, or null: a site or a
	 * queue (builder), a quarry (miner), Pokémon in their daycare, fossils being revived, parcels on their round.
	 */
	@Nullable
	public static String holds(ServerLevel level, Villager elder) {
		if (ModAttachments.BUILDER_JOB.has(elder) || !io.github.jcondedata.aliveworkplace.build.Builders.queue(level, elder).isEmpty()) {
			return "site";
		}
		if (ModAttachments.MINER_JOB.has(elder)) {
			return "quarry";
		}
		if (!ModAttachments.DAYCARE.getOrElse(elder, List.of()).isEmpty() || !ModAttachments.DAYCARE_PAIRS.getOrElse(elder, List.of()).isEmpty()) {
			return "daycare";
		}
		if (!ModAttachments.FOSSIL_REVIVALS.getOrElse(elder, List.of()).isEmpty()) {
			return "fossils";
		}
		UUID id = elder.getUUID();
		for (io.github.jcondedata.aliveworkplace.mail.Parcel parcel : io.github.jcondedata.aliveworkplace.mail.PostOffice.get(level.getServer()).parcels()) {
			if (id.equals(parcel.carrier())) {
				return "parcels";
			}
		}
		return null;
	}

	// --- The hall's round -----------------------------------------------------------------------------------------

	/** The hall's round for the village's grown villagers: shadows end in a hand-over, mentors find pupils, one elder may start retiring. */
	public static void round(ServerLevel level, BlockPos hall, List<Villager> grown) {
		long day = Chronicle.day(level);
		List<Villager> all = grown.stream().filter(v -> v.isAlive() && !v.isBaby()).toList();
		boolean busy = false;
		for (Villager successor : all) {
			Shadow shadow = shadow(successor);
			if (shadow == null) {
				continue;
			}
			Villager elder = find(level, all, shadow.elder());
			if (elder == null || !hasJob(elder) || isRetired(elder) || hasJob(successor)) {
				ModAttachments.RETIRE_SHADOW.remove(successor); // the elder is gone, or someone took a job: it's off
				continue;
			}
			busy = true;
			if (day - shadow.since() >= SHADOW_DAYS && holds(level, elder) == null) {
				handOver(level, hall, elder, successor);
			}
		}
		for (Villager elder : all) {
			if (isRetired(elder)) {
				mentor(level, elder, all);
			}
		}
		if (!ENABLED || busy || ClassMigration.quiet(level, hall, day) || all.stream().anyMatch(v -> { // nobody retires while classes settle in (34.22)
			Retired r = ModAttachments.RETIRED.get(v);
			return r != null && day - r.day() < EVERY_DAYS;
		})) {
			return;
		}
		List<Villager> elders = new ArrayList<>(all.stream().filter(v -> hasJob(v) && !isRetired(v) && LifeStages.isElder(v, day)).toList());
		elders.sort(Comparator.comparingLong((Villager v) -> -LifeStages.grownDays(v, day)).thenComparing(Entity::getUUID));
		for (Villager elder : elders) {
			if (holds(level, elder) != null) {
				continue; // never while they hold something for a player or a build
			}
			Villager successor = successor(hall, elder, all, day);
			if (successor == null) {
				ModAttachments.RETIRE_WAITING.set(elder, true);
				continue;
			}
			ModAttachments.RETIRE_WAITING.remove(elder);
			ModAttachments.RETIRE_SHADOW.set(successor, new Shadow(elder.getUUID(), elder.getDisplayName().getString(), day));
			Chronicle.atHall(level, hall, Chronicle.Kind.LIFE, Component.translatable("chronicle.aliveworkplace.retire.learning",
				successor.getDisplayName(), trade(elder.getVillagerData().getProfession()), elder.getDisplayName()));
			return;
		}
	}

	/** Who takes over from {@code elder}: their own grown child without a job, else any jobless grown villager the job's class allows. */
	@Nullable
	public static Villager successor(BlockPos hall, Villager elder, List<Villager> all, long day) {
		VillagerProfession job = elder.getVillagerData().getProfession();
		String name = elder.getDisplayName().getString();
		List<Villager> free = new ArrayList<>(all.stream()
			.filter(v -> v != elder && v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == VillagerProfession.NONE
				&& !isRetired(v) && shadow(v) == null && !LifeStages.isElder(v, day) && ClassJobs.may(hall, v, job))
			.toList());
		free.sort(Comparator.comparing(Entity::getUUID));
		for (Villager v : free) {
			Families.Parents parents = Families.parents(v);
			if (parents != null && childOf(parents, elder, name)) {
				return v;
			}
		}
		return free.isEmpty() ? null : free.get(0);
	}

	/**
	 * Whether {@code parents} name {@code elder} as a parent: by the parent ids when the record has them (34.21), so a
	 * Name Tag can't hide a parent or make a namesake one; by the name only for records older than the ids (B347).
	 */
	private static boolean childOf(Families.Parents parents, Villager elder, String name) {
		if (parents.motherId() != null || parents.fatherId() != null) {
			return elder.getUUID().equals(parents.motherId()) || elder.getUUID().equals(parents.fatherId());
		}
		return parents.mother().getString().equals(name) || parents.father().getString().equals(name);
	}

	/** The hand-over: the workstation, the job one level below the elder's (at least Apprentice) and its assignments. */
	public static void handOver(ServerLevel level, BlockPos hall, Villager elder, Villager successor) {
		VillagerData data = elder.getVillagerData();
		VillagerProfession job = data.getProfession();
		int lvl = Math.max(2, data.getLevel() - 1);
		GlobalPos site = elder.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		MerchantOffers offers = elder.getOffers().copy();
		long days = LifeStages.grownDays(elder, Chronicle.day(level));

		// The elder retires first: the workstation is let go, the trades kept for players.
		ModAttachments.RETIRED.set(elder, new Retired(Chronicle.day(level), BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString(), data.getLevel()));
		ModAttachments.RETIRE_WAITING.remove(elder);
		if (site != null) {
			Stations.releaseOld(level, elder, site);
			elder.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		}
		elder.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		elder.setVillagerData(data.setProfession(VillagerProfession.NONE));
		elder.setOffers(offers);

		// Then the successor takes the workstation and the job.
		ModAttachments.RETIRE_SHADOW.remove(successor);
		boolean placed = false;
		if (site != null && site.dimension() == level.dimension()) {
			var poi = level.getPoiManager().getType(site.pos()).orElse(null);
			placed = poi != null && VillageHalls.assign(level, successor, new VillageHalls.FreeStation(site.pos(), job, poi));
		}
		if (!placed) {
			io.github.jcondedata.aliveworkplace.work.WorkerLimits.order(successor, job);
		}
		successor.setVillagerData(successor.getVillagerData().setProfession(job).setLevel(lvl));
		successor.setVillagerXp(Math.max(1, VillagerData.getMinXpPerLevel(lvl)));
		for (Attachment<?> a : ASSIGNMENTS) {
			move(a, elder, successor);
		}
		Chronicle.atHall(level, hall, Chronicle.Kind.LIFE, Component.translatable("chronicle.aliveworkplace.retire.retired",
			elder.getDisplayName(), trade(job), days));
		Chronicle.atHall(level, hall, Chronicle.Kind.JOINED, Component.translatable("chronicle.aliveworkplace.retire.handover",
			successor.getDisplayName(), trade(job), elder.getDisplayName(), level(lvl)));
	}

	private static <T> void move(Attachment<T> a, Villager from, Villager to) {
		T value = a.get(from);
		if (value != null) {
			a.set(to, value);
			a.remove(from);
		}
	}

	/** A retired elder finds a Novice or Apprentice of their old trade in the village to teach. */
	private static void mentor(ServerLevel level, Villager elder, List<Villager> all) {
		Retired r = ModAttachments.RETIRED.get(elder);
		if (r == null || r.trade().isEmpty()) {
			return;
		}
		Villager pupil = pupil(elder, all);
		if (pupil == null) {
			ModAttachments.MENTORING.remove(elder);
			return;
		}
		ModAttachments.MENTORING.set(elder, pupil.getUUID());
		ModAttachments.TAUGHT_BY.set(pupil, new Teacher(elder.getUUID(), elder.getDisplayName().getString(), level.getGameTime() + TAUGHT_TICKS));
	}

	/** The Novice or Apprentice of {@code elder}'s old trade they teach: the one they taught before, else the nearest. */
	@Nullable
	public static Villager pupil(Villager elder, List<Villager> all) {
		Retired r = ModAttachments.RETIRED.get(elder);
		if (r == null) {
			return null;
		}
		UUID before = ModAttachments.MENTORING.get(elder);
		Villager best = null;
		for (Villager v : all) {
			if (v == elder || isRetired(v) || v.getVillagerData().getLevel() > 2
				|| !BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString().equals(r.trade())) {
				continue;
			}
			if (v.getUUID().equals(before)) {
				return v;
			}
			if (best == null || v.distanceToSqr(elder) < best.distanceToSqr(elder)) {
				best = v;
			}
		}
		return best;
	}

	// --- Learning, jobs, the villager's own tick ------------------------------------------------------------------

	/** Extra xp for {@code xp} earned by a villager a mentor teaches (+{@link #MENTOR_BONUS}); 0 for anyone else. */
	public static int extraXp(Villager villager, int xp, RandomSource random) {
		if (xp <= 0 || villager.getVillagerData().getLevel() > 2 || teacher(villager) == null) {
			return 0;
		}
		float more = xp * MENTOR_BONUS;
		int whole = (int) more;
		return whole + (random.nextFloat() < more - whole ? 1 : 0);
	}

	/** Whether {@code villager} may not take the job in {@code data}: a retired elder never takes a job again. */
	public static boolean refuses(Villager villager, VillagerData data) {
		VillagerProfession p = data.getProfession();
		return isRetired(villager) && p != VillagerProfession.NONE && p != villager.getVillagerData().getProfession();
	}

	/** From the villager's server tick: a successor follows the elder, a mentor stands by their pupil or by the hall. */
	public static void tick(Villager villager) {
		if (villager.tickCount % TICK_EVERY != 0 || !(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
			return;
		}
		Shadow shadow = shadow(villager);
		if (shadow != null) {
			WorkerStatus.set(villager, Component.translatable("status.aliveworkplace.retire.learning", shadow.elderName()).withStyle(ChatFormatting.GOLD),
				-1f, Component.empty());
			if (level.getEntity(shadow.elder()) instanceof Villager elder && !villager.isSleeping() && villager.distanceToSqr(elder) > 9
				&& villager.distanceToSqr(elder) < 48 * 48) {
				Walker.requestWalk(villager, elder.blockPosition(), 0.5f, 2, 0);
			}
			return;
		}
		Teacher teacher = teacher(villager);
		if (teacher != null && !hasStatus(villager, level)) {
			WorkerStatus.set(villager, Component.translatable("status.aliveworkplace.retire.taught", teacher.mentorName()).withStyle(ChatFormatting.GOLD),
				-1f, Component.empty());
		}
		if (isRetired(villager) && !villager.isSleeping() && isDay(level)) {
			UUID pupil = ModAttachments.MENTORING.get(villager);
			Entity by = pupil == null ? null : level.getEntity(pupil);
			BlockPos goal = by != null && by.distanceToSqr(villager) < 64 * 64 ? by.blockPosition()
				: villager.tickCount % (TICK_EVERY * 5) == 0 ? VillageHalls.nearest(level, villager.blockPosition()).orElse(null) : null;
			if (goal != null && villager.blockPosition().distSqr(goal) > 9) {
				Walker.requestWalk(villager, goal, 0.45f, 2, 0);
			}
		}
	}

	private static boolean hasStatus(Villager villager, ServerLevel level) {
		return WorkerStatus.get(villager, level.getGameTime()) != null; // their own work's line first
	}

	private static boolean isDay(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		return time < 12000;
	}

	// --- Words ----------------------------------------------------------------------------------------------------

	private static Component trade(VillagerProfession job) {
		return Stations.name(job);
	}

	private static Component level(int lvl) {
		return Component.translatable("merchant.level." + lvl);
	}

	/** The lines on a villager's card on the hall's list. */
	public static List<Component> hallLines(ServerLevel level, Villager villager) {
		List<Component> lines = new ArrayList<>();
		Retired r = ModAttachments.RETIRED.get(villager);
		if (r != null) {
			ResourceLocation id = ResourceLocation.tryParse(r.trade());
			VillagerProfession p = id == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.get(id);
			lines.add(Component.translatable("screen.aliveworkplace.hall.retired", p == null ? Component.literal(r.trade()) : trade(p)));
		} else if (ENABLED && ModAttachments.RETIRE_WAITING.getOrElse(villager, false) && hasJob(villager)) {
			lines.add(Component.translatable("screen.aliveworkplace.hall.retire_waiting", villager.getDisplayName()));
		}
		Shadow shadow = shadow(villager);
		if (shadow != null) {
			lines.add(Component.translatable("status.aliveworkplace.retire.learning", shadow.elderName()));
		}
		Teacher teacher = teacher(villager);
		if (teacher != null) {
			lines.add(Component.translatable("status.aliveworkplace.retire.taught", teacher.mentorName()));
		}
		return lines;
	}

	@Nullable
	private static Villager find(ServerLevel level, List<Villager> all, UUID id) {
		for (Villager v : all) {
			if (v.getUUID().equals(id)) {
				return v;
			}
		}
		return level.getEntity(id) instanceof Villager v && v.isAlive() ? v : null;
	}

	private Retirement() {
	}
}
