package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.mc.Damage;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Guards fighting beside their Pokémon (with Cobblemon): Fighting and Dragon types kept in a Pasture Block near the
 * Guard Post follow up every hit the guard lands on a monster with a move of their own — from the pasture, at range,
 * with Cobblemon's own attack animation, impact effect and sound. The damage counts as the guard's (monsters turn on the
 * guard, never the Pokémon), and it doesn't wait for the monster's hurt cooldown, so the guard's next hit lands in full.
 */
public final class GuardPartners {
	public static final ResourceKey<DamageType> POKEMON_MOVE = ResourceKey.create(Registries.DAMAGE_TYPE, AliveWorkplace.id("pokemon_move"));
	/** How far a Pokémon's move reaches from where it stands. */
	static final double REACH = 20;
	/** A Pokémon uses a move at most this often. */
	static final int COOLDOWN = 20;
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	private static final Map<Entity, Long> LAST_MOVE = new WeakHashMap<>();

	public static void init() {
		if (!COBBLEMON) {
			return;
		}
		Platform.get().afterDamage((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked && damage > 0 && !source.is(POKEMON_MOVE) && source.getEntity() instanceof Villager guard
				&& Guards.isGuard(guard) && entity.level() instanceof ServerLevel level && Guards.isFoe(entity, guard)) {
				join(level, guard, entity);
			}
		});
	}

	/** Damage of one move from a Pokémon at this level: 2 at level 1, 5 at 50, 8 at 100. */
	public static float damage(int pokemonLevel) {
		return Math.min(8f, 2f + 0.06f * pokemonLevel);
	}

	/** The guard's partners in range follow up on a hit the guard just landed on {@code foe}; returns how many did. */
	static int join(ServerLevel level, Villager guard, LivingEntity foe) {
		BlockPos post = guard.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(p -> p.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
		if (post == null) {
			return 0;
		}
		var holder = Lookup.holder(Lookup.registry(level.registryAccess(), Registries.DAMAGE_TYPE), POKEMON_MOVE).orElse(null);
		if (holder == null) {
			return 0;
		}
		long now = level.getGameTime();
		int joined = 0;
		for (PokemonPartners.Fighter fighter : PokemonPartners.EXTENSION.call(p -> p.fighters(level, post, Partners.RADIUS,
				Partners.types(ModVillagers.GUARD), Partners.MAX), List.<PokemonPartners.Fighter>of())) {
			LivingEntity pokemon = fighter.entity();
			if (!foe.isAlive() || pokemon.distanceToSqr(foe) > REACH * REACH || !pokemon.hasLineOfSight(foe)) {
				continue;
			}
			synchronized (LAST_MOVE) {
				Long last = LAST_MOVE.get(pokemon);
				if (last != null && now - last < COOLDOWN) {
					continue;
				}
				LAST_MOVE.put(pokemon, now);
			}
			trail(level, pokemon, foe, fighter.type());
			PokemonPartners.EXTENSION.run(p -> p.useMove(level, pokemon, foe, fighter.type()));
			Damage.hurt(foe, new DamageSource(holder, pokemon, guard), damage(fighter.level()));
			joined++;
		}
		return joined;
	}

	/** A line of sparks from the Pokémon to the foe, so it's clear where the move came from. */
	private static void trail(ServerLevel level, LivingEntity pokemon, LivingEntity foe, String type) {
		ParticleOptions particle = switch (type) {
			case "dragon" -> ParticleTypes.DRAGON_BREATH;
			case "fighting" -> ParticleTypes.CRIT;
			default -> ParticleTypes.ENCHANTED_HIT;
		};
		Vec3 from = pokemon.position().add(0, pokemon.getBbHeight() * 0.6, 0);
		Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
		int steps = Math.max(4, (int) (from.distanceTo(to) * 2));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
		}
	}

	/** Forgets the move cooldowns (tests). */
	public static void reset() {
		synchronized (LAST_MOVE) {
			LAST_MOVE.clear();
		}
	}

	private GuardPartners() {
	}
}
