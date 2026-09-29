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
}
