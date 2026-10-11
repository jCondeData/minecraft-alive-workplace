package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The rogue Pokémon of the bounty board (ROADMAP 31.14): a wild Pokémon grown huge and mean. Filled in by
 * {@code compat/cobblemon/CobblemonRogues} when Cobblemon is installed; without it there are no rogues, so this kind of
 * bounty is never posted. A rogue carries the bounty's id, so its defeat in battle or its capture (which the filling
 * watches) pays that bounty through {@code Bounties.rogueBeaten}.
 */
public interface RoguePokemon {
	Extension<RoguePokemon> EXTENSION = new Extension<>("rogue Pokémon");

	/** Whether Cobblemon has the species {@code cobblemon:ursaring} (a species it doesn't have is skipped). */
	boolean has(ResourceLocation species);

	/** The species' name ("Ursaring"). */
	Component speciesName(ResourceLocation species);

	/**
	 * Spawns the rogue at {@code at} with Cobblemon's own spawning: wild, level {@code pokemonLevel} (with Radical Cobblemon
	 * Trainers, {@code near}'s level cap plus 5 instead), half again as big (one of Cobblemon's Alphas instead on 1.8),
	 * named {@code name} and carrying {@code bounty}. Null if it couldn't be made.
	 */
	@Nullable
	Entity spawn(ServerLevel level, BlockPos at, ResourceLocation species, int pokemonLevel, Component name, UUID bounty, @Nullable ServerPlayer near);

	/** The level the rogue {@code entity} is (0 if it's no Pokémon). */
	int level(Entity entity);

	/** Whether there are rogues at all (Cobblemon is installed). */
	static boolean present() {
		return EXTENSION.present();
	}

	static boolean exists(ResourceLocation species) {
		return EXTENSION.call(r -> r.has(species), false);
	}

	/** The species' name, or its id's path with a capital ("Ursaring") without Cobblemon. */
	static Component nameOf(ResourceLocation species) {
		return EXTENSION.call(r -> r.speciesName(species), Component.literal(capital(species.getPath())));
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
	}
}
