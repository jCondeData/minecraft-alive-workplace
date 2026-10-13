package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A stage's dedication (ROADMAP 35.7), once its goods are taken ({@link WonderGoods#dedicate}): the bell rings at the
 * stone and at the village's bell, and for {@link #DURATION} ticks the villagers off work gather round the stone (the
 * festivals' gathering), the village's Bard plays beside it ({@code BardWork} asks {@link #bardSpot}) and its guards
 * stand in a line outside the Wonder's corner ({@link GuardRally#lineUp}), when it has them. Nothing here is saved: a
 * restart in the middle only cuts the gathering short (the goods were already taken and the stage begun).
 */
public final class Dedications {
	/** How long the village stays gathered at the stone (30 seconds). */
	public static final int DURATION = 600;
	/** Most guards in the line. */
	static final int LINE = 8;

	/** A dedication going on: the Wonder, where, its hall, and when it's over. */
	record Ceremony(UUID wonder, ResourceKey<Level> dimension, BlockPos stone, BlockPos hall, int dx, int dz, long until) {
	}

	private static final Map<UUID, Ceremony> ON = new ConcurrentHashMap<>();

	/** {@code site}'s stage was dedicated: the bell rings, and the village gathers at the stone. */
	static void begin(ServerLevel level, WonderSite site) {
		BlockPos stone = site.stone().orElseThrow();
		BlockPos origin = site.placement().map(p -> p.origin()).orElse(stone);
		int dx = Integer.signum(stone.getX() - origin.getX());
		int dz = Integer.signum(stone.getZ() - origin.getZ());
		ON.put(site.id(), new Ceremony(site.id(), level.dimension(), stone.immutable(), site.hall().immutable(), dx == 0 ? -1 : dx, dz == 0 ? -1 : dz,
			level.getGameTime() + DURATION));
		level.playSound(null, stone, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
		// the village's bell (not rung for real: a rung bell sends everyone into hiding)
		level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), site.hall(), VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.ifPresent(bell -> level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, stone.getX() + 0.5, stone.getY() + 1.2, stone.getZ() + 0.5, 12, 0.6, 0.4, 0.6, 0);
		tick(level);
	}

	/** Whether the Wonder {@code wonder}'s dedication is going on. */
	public static boolean isOn(ServerLevel level, UUID wonder) {
		Ceremony c = ON.get(wonder);
		return c != null && c.dimension().equals(level.dimension()) && level.getGameTime() <= c.until();
	}

	/** The Wonder {@code wonder} is gone (cancelled): its dedication ends. */
	static void end(UUID wonder) {
		ON.remove(wonder);
	}

	/** Forgets every dedication (tests). */
	public static void forget() {
		ON.clear();
	}

	/** Where {@code bard} plays while a dedication of their village is on: beside its stone, on the side away from the Wonder. */
	public static Optional<BlockPos> bardSpot(ServerLevel level, Villager bard) {
		for (Ceremony c : ON.values()) {
			if (c.dimension().equals(level.dimension()) && level.getGameTime() <= c.until() && VillageHalls.area(c.hall()).contains(bard.position())) {
				return Optional.of(c.stone().offset(2 * c.dx(), 0, 0));
			}
		}
		return Optional.empty();
	}

	/** Where the {@code i}th guard stands in line: three blocks out from the stone, along its side away from the Wonder. */
	static BlockPos lineSpot(Ceremony c, int i) {
		int along = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2);
		return c.stone().offset(3 * c.dx(), 0, along * c.dz());
	}

	/** Every second ({@link WonderStages}' round): the villagers off work gather at the stone, the guards line up; over ones end. */
	static void tick(ServerLevel level) {
		if (ON.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Ceremony c : List.copyOf(ON.values())) {
			if (!c.dimension().equals(level.dimension())) {
				continue;
			}
			if (now > c.until() || WonderManager.get(level).site(c.wonder()).isEmpty() || !Wonders.ENABLED) {
				ON.remove(c.wonder());
				continue;
			}
			if (!level.isLoaded(c.stone())) {
				continue;
			}
			int guards = 0;
			for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(c.hall()), v -> v.isAlive() && !v.isSleeping() && !v.isBaby())) {
				if (Guards.isGuard(villager)) {
					if (guards < LINE) {
						// renewed every second, so the line breaks up soon after the dedication ends (or the stone is broken)
						GuardRally.lineUp(villager, lineSpot(c, guards++), Math.min(c.until(), now + 2L * WonderStages.INTERVAL));
					}
				} else if (villager.getVillagerData().getProfession() != ModVillagers.BARD) {
					Festivals.gatherAt(level, villager, c.stone());
				}
			}
		}
	}

	/** How many villagers stand within the gathering's reach of the Wonder {@code wonder}'s stone now (tests and the scene). */
	public static int gathered(ServerLevel level, WonderSite site) {
		return site.stone().map(s -> level.getEntitiesOfClass(Villager.class, new AABB(s).inflate(7), Villager::isAlive).size()).orElse(0);
	}

	private Dedications() {
	}
}
