package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Guards following a raised {@link RallyBannerItem}: who follows whom (worked out from the players' inventories every
 * {@link #SCAN} ticks), what they fight while they do, and sending them home when the banner comes down.
 */
public final class Escorts {
	static final int SCAN = 20;
	/** A guard further than this from the player they follow catches up at once (as a tame wolf does). */
	public static final double CATCH_UP = 32;
	/** A guard the banner lets go this far from their post is back there at once. */
	public static final double SEND_HOME = 64;
	/** How far round the player guards look for trouble. */
	static final double WATCH = 16;

	/** Guard → the player whose raised banner calls them. */
	private static Map<UUID, UUID> leaders = Map.of();

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % SCAN == 0) {
				scan(server);
			}
		});
	}

	/** Works out again who follows whom; guards no one calls any more go home. */
	public static void scan(MinecraftServer server) {
		Map<UUID, UUID> now = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.is(ModItems.RALLY_BANNER)) {
					RallyBannerItem.Rally rally = RallyBannerItem.rally(stack);
					if (rally.raised()) {
						rally.guards().forEach(guard -> now.putIfAbsent(guard, player.getUUID()));
					}
				}
			}
		}
		Map<UUID, UUID> before = leaders;
		leaders = now;
		for (UUID guard : before.keySet()) {
			if (!now.containsKey(guard)) {
				sendHome(server, guard);
			}
		}
	}

	/** The player {@code guard} is following, if any (online, alive, in the same dimension). */
	public static Optional<ServerPlayer> leader(Villager guard) {
		UUID id = leaders.get(guard.getUUID());
		if (id == null || !(guard.level() instanceof ServerLevel level) || !Guards.isGuard(guard)) {
			return Optional.empty();
		}
		Player player = level.getPlayerByUUID(id);
		return player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() && !serverPlayer.isSpectator()
			? Optional.of(serverPlayer) : Optional.empty();
	}

	public static boolean isEscorting(Villager guard) {
		return leader(guard).isPresent();
	}

	/** Back to their post, if the guard is far from it. */
	static void sendHome(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof Villager guard) {
				Builders.benchPos(guard).ifPresent(post -> {
					if (!post.closerToCenterThan(guard.position(), SEND_HOME)) {
						moveNear(level, guard, post);
					}
				});
				return;
			}
		}
	}

	/** Puts {@code guard} on a free spot near {@code to} (loading the chunk first, so they arrive). */
	static void moveNear(ServerLevel level, Villager guard, BlockPos to) {
		level.getChunk(to);
		BlockPos spot = Walker.standingSpot(level, to, to, 4.0);
		BlockPos at = spot != null ? spot : to.above();
		level.sendParticles(ParticleTypes.CLOUD, guard.getX(), guard.getY() + 0.5, guard.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		guard.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		guard.getNavigation().stop();
		guard.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.sendParticles(ParticleTypes.CLOUD, guard.getX(), guard.getY() + 0.5, guard.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
	}

	/**
	 * What the guard should go for while following {@code player}: whatever just hurt them or they just hit, or anything
	 * near them that's after them — never a player, a villager, a golem, someone's pet or a Pokémon.
	 */
	@Nullable
	public static LivingEntity leaderFoe(ServerPlayer player, Villager guard) {
		LivingEntity attacker = player.getLastHurtByMob();
		if (fair(attacker, player, guard)) {
			return attacker;
		}
		LivingEntity hit = player.getLastHurtMob();
		if (hit != null && player.tickCount - player.getLastHurtMobTimestamp() < 100 && fair(hit, player, guard)) {
			return hit;
		}
		AABB around = player.getBoundingBox().inflate(WATCH, 8, WATCH);
		return guard.level().getEntitiesOfClass(Mob.class, around, m -> m.getTarget() == player && fair(m, player, guard)).stream()
			.min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	static boolean fair(@Nullable LivingEntity entity, ServerPlayer player, Villager guard) {
		return entity != null && entity.isAlive() && entity != guard && entity.level() == guard.level()
			&& !(entity instanceof Player) && !(entity instanceof AbstractVillager) && !(entity instanceof IronGolem)
			&& !(entity instanceof ArmorStand) && !(entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
			&& !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("cobblemon")
			&& entity.distanceToSqr(player) < (WATCH + 8) * (WATCH + 8);
	}

	private Escorts() {
	}
}
