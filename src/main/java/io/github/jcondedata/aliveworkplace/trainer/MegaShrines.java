package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * The Mega Shrine (ROADMAP 38.7): a decoration of Mega Showdown's pedestal on a dais of Mega meteorite bricks, listed at
 * the Blueprint Table only when its blocks exist ({@link #blocksExist}). On a festival day ({@code hall/Festivals}) a
 * village with a finished shrine holds a show there first: a village trainer walks to the pedestal and Mega Evolves their
 * ace, and the villagers off work gather at the shrine to watch; each watcher is {@link #MOOD} happier for as long as a
 * festival's good mood lasts. Behind {@code villageMegas} (38.5's switch, M38's gate) and Mega Showdown; ids only.
 */
public final class MegaShrines {
	/** The blueprint. */
	public static final ResourceLocation SHRINE = AliveWorkplace.id("mega_shrine");
	/** Mega Showdown's pedestal: while it isn't a block, the shrine isn't listed. */
	public static final ResourceLocation PEDESTAL = ResourceLocation.fromNamespaceAndPath("mega_showdown", "pedestal");
	/** The pedestal in the blueprint (tools/blueprints/decor.py, mega_shrine), and where its watchers stand (in front). */
	static final BlockPos PEDESTAL_SPOT = new BlockPos(3, 3, 3);
	static final BlockPos TRAINER_SPOT = new BlockPos(3, 3, 2);
	static final BlockPos CROWD_SPOT = new BlockPos(3, 1, -4);
	/** The show runs from the festival's start for this many ticks (an in-game hour and a half), then the square has them. */
	public static final long SHOW_TICKS = 1500;
	/** A watcher's mood lift. */
	public static final int MOOD = 3;
	/** How near the pedestal the trainer must be to Mega Evolve, and the watchers to see it. */
	static final double TRAINER_REACH = 3.5;
	static final int WATCH_RANGE = 12;
	/** The day each village's show was held (by its hall), so it's held once a festival. */
	private static final Map<GlobalPos, Long> HELD = new ConcurrentHashMap<>();

	/** Whether Mega Showdown's blocks for the shrine exist (the Table lists it only then). */
	public static boolean blocksExist() {
		return BuiltInRegistries.BLOCK.containsKey(PEDESTAL);
	}

	/** Whether the Table lists the shrine: its blocks exist and {@code villageMegas} (with M38's gate) is on. */
	public static boolean listed() {
		return blocksExist() && MegaBracelets.ENABLED;
	}

	/** Whether the shrine's festival show is on: Mega Showdown and {@code villageMegas}. */
	public static boolean live() {
		return MegaBracelets.extraMegas() && blocksExist();
	}

	/** The pedestal of the village round {@code hall}'s finished shrine, the nearest first; empty without one. */
	public static Optional<BlockPos> pedestal(ServerLevel level, BlockPos hall) {
		return finished(level, hall).map(f -> Arenas.world(f.placement(), PEDESTAL_SPOT));
	}

	private static Optional<BuildSiteManager.Finished> finished(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.filter(f -> BlueprintStyles.base(f.structure()).equals(SHRINE))
			.min(Comparator.comparingDouble(f -> f.placement().origin().distSqr(hall)));
	}

	/** Whether the festival round {@code hall} is in its show's hours now (it started less than {@link #SHOW_TICKS} ago). */
	static boolean showHours(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		return time >= Festivals.START && time < Festivals.START + SHOW_TICKS;
	}

	/**
	 * Every festival tick at {@code hall} ({@code Festivals.tick}): in the show's hours with a finished shrine, the crowd
	 * gathers at the shrine instead of the square and the village's trainer goes to the pedestal. Returns whether the
	 * shrine has the crowd now (the square doesn't).
	 */
	public static boolean tick(ServerLevel level, BlockPos hall) {
		if (!live() || !showHours(level)) {
			return false;
		}
		Optional<BuildSiteManager.Finished> shrine = finished(level, hall);
		if (shrine.isEmpty()) {
			return false;
		}
		BlockPos pedestal = Arenas.world(shrine.get().placement(), PEDESTAL_SPOT);
		BlockPos stage = Arenas.world(shrine.get().placement(), TRAINER_SPOT);
		BlockPos crowd = Arenas.world(shrine.get().placement(), CROWD_SPOT);
		Optional<Villager> trainer = trainer(level, hall, pedestal);
		long today = Chronicle.day(level);
		boolean held = Long.valueOf(today).equals(HELD.get(GlobalPos.of(level.dimension(), hall)));
		for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isSleeping())) {
			if (trainer.isPresent() && villager == trainer.get()) {
				continue;
			}
			if (Festivals.gatherAt(level, villager, crowd)) {
				ModAttachments.FESTIVAL_DAY.set(villager, today);
			}
		}
		if (trainer.isEmpty() || held) {
			return true;
		}
		Villager star = trainer.get();
		if (!stage.closerToCenterThan(star.position(), TRAINER_REACH)) {
			star.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stage, 0.6f, 0));
			return true;
		}
		show(level, hall, pedestal, star);
		return true;
	}

	/** The village's trainer for the show: one who can Mega Evolve first, then the nearest to the pedestal. */
	static Optional<Villager> trainer(ServerLevel level, BlockPos hall, BlockPos pedestal) {
		List<Villager> trainers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && Trainers.isTrainer(v));
		return trainers.stream().min(Comparator.<Villager>comparingInt(v -> MegaBracelets.canMegaEvolve(v) ? 0 : 1)
			.thenComparingDouble(v -> v.distanceToSqr(pedestal.getX() + 0.5, pedestal.getY(), pedestal.getZ() + 0.5)));
	}

	/**
	 * The show: {@code trainer} Mega Evolves their ace by the pedestal (the light, the sound, a line to the players near),
	 * and every villager watching within {@link #WATCH_RANGE} is the happier for it. Held once a day per village. Returns
	 * how many watched.
	 */
	public static int show(ServerLevel level, BlockPos hall, BlockPos pedestal, Villager trainer) {
		long today = Chronicle.day(level);
		HELD.put(GlobalPos.of(level.dimension(), hall), today);
		double x = pedestal.getX() + 0.5;
		double y = pedestal.getY() + 1.2;
		double z = pedestal.getZ() + 0.5;
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.6, 1.2, 0.6, 0.08);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 80, 0.8, 1.0, 0.8, 0.4);
		level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
		level.playSound(null, pedestal, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2f, 1.2f);
		level.playSound(null, pedestal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5f, 0.8f);
		int watchers = 0;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(pedestal).inflate(WATCH_RANGE), v -> v.isAlive() && v != trainer)) {
			ModAttachments.MEGA_SHOW_DAY.set(villager, today);
			watchers++;
		}
		Component line = Component.translatable("message.aliveworkplace.mega_shrine.show", trainer.getDisplayName());
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(pedestal) <= 48 * 48)) {
			Chat.chat(player, line);
		}
		return watchers;
	}

	/** {@link #MOOD} while the last show {@code villager} watched is as fresh as a festival's good mood, else 0. */
	public static int mood(ServerLevel level, Villager villager) {
		Long day = ModAttachments.MEGA_SHOW_DAY.get(villager);
		return day != null && Chronicle.day(level) - day <= Festivals.MOOD_DAYS ? MOOD : 0;
	}

	/** Forgets the shows held (server stop). */
	public static void forget() {
		HELD.clear();
	}

	private MegaShrines() {
	}
}
