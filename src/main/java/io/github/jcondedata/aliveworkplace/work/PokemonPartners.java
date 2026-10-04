package io.github.jcondedata.aliveworkplace.work;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pokémon pastured near a workstation, as the jobs see them: partners that speed up a job, fight beside a guard, get
 * groomed by the rancher or looked after by the chores. Filled in by {@code compat/cobblemon} when Cobblemon is
 * installed; without it there are none.
 */
public interface PokemonPartners {
	Extension<PokemonPartners> EXTENSION = new Extension<>("Pokémon partners");

	/** A Pokémon ready to fight beside a guard: the entity, its level and the type its moves are. */
	record Fighter(LivingEntity entity, int level, String type) {
	}

	/** The names of up to {@code max} pastured Pokémon of {@code types} within {@code radius} of {@code center}, nearest first. */
	List<Component> helpers(ServerLevel level, BlockPos center, int radius, Set<String> types, int max);

	/** Up to {@code max} pastured Pokémon of {@code types} that can fight, nearest first. */
	List<Fighter> fighters(ServerLevel level, BlockPos center, int radius, Set<String> types, int max);

	/** {@code pokemon} shows a move of {@code type} at {@code target} (the damage is dealt by the caller). */
	void useMove(ServerLevel level, LivingEntity pokemon, LivingEntity target, String type);

	/** Every pastured Pokémon within {@code radius} of {@code center}. */
	List<Entity> pastured(ServerLevel level, BlockPos center, int radius);

	/** Whether {@code entity} is a Pokémon matching {@code properties} (Cobblemon's property syntax). */
	boolean matches(Entity entity, String properties);

	/** Whether grooming {@code entity} can still make it friendlier. */
	boolean canBefriend(Entity entity);

	/** Makes {@code entity} {@code amount} friendlier; false if it wasn't a Pokémon that could be. */
	boolean befriend(ServerLevel level, Entity entity, int amount);

	/** Whether {@code entity} is a Pokémon in a pasture. */
	boolean isPastured(Entity entity);

	// Partner shows (ROADMAP 28.3, PartnerShows): a pastured partner seen helping at work.

	/** Whether a pastured Pokémon may do a show now: never in battle, ridden, carrying someone or otherwise busy. */
	default boolean canPerform(Entity entity) {
		return false;
	}

	/** The nearest spot to {@code target} that {@code entity}'s pasture lets it wander to (it is never untethered). */
	default BlockPos reachable(Entity entity, BlockPos target) {
		return entity.blockPosition();
	}

	/** Starts {@code entity} walking to {@code pos}; false if it can't be told to. */
	default boolean walkTo(Entity entity, BlockPos pos, double speed) {
		return false;
	}

	/** Sends {@code entity} back to its pasture. */
	default void goHome(Entity entity) {
	}

	/** Plays one of the Pokémon's animations: {@code physical}, {@code special} or {@code cry}. */
	default void animate(ServerLevel level, Entity entity, String animation) {
	}

	/** Shows one of Cobblemon's effects ({@code cobblemon:impact_water}) at {@code at}; false if there's no such effect. */
	default boolean effect(ServerLevel level, net.minecraft.resources.ResourceLocation id, net.minecraft.world.phys.Vec3 at) {
		return false;
	}
}
