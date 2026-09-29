package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pokémon kept in Cobblemon Pasture Blocks, as partners for villagers (see {@code work/Partners}). */
public final class CobblemonPartners {
	/**
	 * Names of the pastured Pokémon within {@code radius} of {@code center} with one of {@code types}
	 * (lower-case type names), nearest first, at most {@code max}.
	 */
	public static List<Component> helpers(ServerLevel level, BlockPos center, int radius, Set<String> types, int max) {
		AABB box = new AABB(center).inflate(radius);
		Vec3 middle = Vec3.atCenterOf(center);
		return level.getEntitiesOfClass(PokemonEntity.class, box, e -> e.isAlive() && e.getTethering() != null && fits(e.getPokemon(), types))
			.stream()
			.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(middle)))
			.limit(max)
			.map(e -> (Component) e.getPokemon().getDisplayName(false))
			.toList();
	}

	/** The pastured Pokémon within {@code radius} of {@code center} with one of {@code types}, nearest first, at most {@code max}. */
	public static List<io.github.jcondedata.aliveworkplace.work.PokemonPartners.Fighter> fighters(ServerLevel level, BlockPos center, int radius, Set<String> types, int max) {
		AABB box = new AABB(center).inflate(radius);
		Vec3 middle = Vec3.atCenterOf(center);
		return level.getEntitiesOfClass(PokemonEntity.class, box, e -> e.isAlive() && e.getTethering() != null && fits(e.getPokemon(), types))
			.stream()
			.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(middle)))
			.limit(max)
			.map(e -> new io.github.jcondedata.aliveworkplace.work.PokemonPartners.Fighter(e, e.getPokemon().getLevel(), firstType(e.getPokemon(), types)))
			.toList();
	}

	/**
	 * Shows a Pokémon using a move on {@code target}: it turns to face it and plays its attack animation, and the
	 * target gets Cobblemon's impact effect and sound for that type (what players see in battles).
	 */
	public static void useMove(ServerLevel level, LivingEntity pokemon, LivingEntity target, String type) {
		double dx = target.getX() - pokemon.getX();
		double dz = target.getZ() - pokemon.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
		pokemon.setYRot(yaw);
		pokemon.setYHeadRot(yaw);
		pokemon.yBodyRot = yaw;
		List<ServerPlayer> near = level.players().stream().filter(p -> p.distanceToSqr(pokemon) < 64 * 64).toList();
		if (!near.isEmpty()) {
			new PlayPosableAnimationPacket(pokemon.getId(), Set.of(type.equals("fighting") ? "physical" : "special"), List.of()).sendToPlayers(near);
			new SpawnSnowstormParticlePacket(ResourceLocation.fromNamespaceAndPath("cobblemon", "impact_" + type),
				target.position().add(0, target.getBbHeight() * 0.5, 0)).sendToPlayers(near);
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "impact." + type))
			.orElse(SoundEvents.PLAYER_ATTACK_STRONG);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.NEUTRAL, 1f, 1f);
	}

	private static String firstType(Pokemon pokemon, Set<String> types) {
		for (ElementalType type : pokemon.getTypes()) {
			String name = type.getName().toLowerCase(java.util.Locale.ROOT);
			if (types.contains(name)) {
				return name;
			}
		}
		return pokemon.getPrimaryType().getName().toLowerCase(java.util.Locale.ROOT);
	}

	/** Every Pokémon in a Pasture Block within {@code radius} of {@code center}. */
	public static List<net.minecraft.world.entity.Entity> pastured(ServerLevel level, BlockPos center, int radius) {
		return List.copyOf(level.getEntitiesOfClass(PokemonEntity.class, new AABB(center).inflate(radius, 6, radius),
			e -> e.isAlive() && e.getTethering() != null));
	}

	private static final java.util.Map<String, com.cobblemon.mod.common.api.pokemon.PokemonProperties> PROPERTIES = new java.util.concurrent.ConcurrentHashMap<>();

	/** Whether {@code entity} is a Pokémon matching Cobblemon properties like {@code "miltank"} or {@code "gogoat gender=female"}. */
	public static boolean matches(net.minecraft.world.entity.Entity entity, String properties) {
		if (!(entity instanceof PokemonEntity pokemon)) {
			return false;
		}
		var parsed = PROPERTIES.computeIfAbsent(properties, p -> com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p, " ", "="));
		return parsed.matches(pokemon.getPokemon());
	}

	/** A Pokémon's friendship (-1 for anything that isn't a Pokémon). */
	public static int friendship(net.minecraft.world.entity.Entity entity) {
		return entity instanceof PokemonEntity pokemon ? pokemon.getPokemon().getFriendship() : -1;
	}

	/** Whether a Pokémon's friendship can still go up. */
	public static boolean canBefriend(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof PokemonEntity pokemon)) {
			return false;
		}
		Pokemon p = pokemon.getPokemon();
		return p.getFriendship() < com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig().getMaxPokemonFriendship();
	}

	/** Raises a Pokémon's friendship by {@code amount} (no higher than the most there is), with hearts; true if it went up. */
	public static boolean befriend(ServerLevel level, net.minecraft.world.entity.Entity entity, int amount) {
		if (!canBefriend(entity)) {
			return false;
		}
		PokemonEntity pokemon = (PokemonEntity) entity;
		int before = pokemon.getPokemon().getFriendship();
		pokemon.getPokemon().incrementFriendship(amount, true);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
			3, 0.3, 0.2, 0.3, 0.0);
		return pokemon.getPokemon().getFriendship() > before;
	}

	/** A Pokémon kept in a Pasture Block. */
	public static boolean isPastured(net.minecraft.world.entity.Entity entity) {
		return entity instanceof PokemonEntity pokemon && pokemon.getTethering() != null;
	}

	static boolean fits(Pokemon pokemon, Set<String> types) {
		for (ElementalType type : pokemon.getTypes()) {
			if (types.contains(type.getName().toLowerCase(java.util.Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private CobblemonPartners() {
	}
}
