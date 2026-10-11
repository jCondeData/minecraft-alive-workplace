//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.entity.Despawner;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.compat.rct.RctLevelCaps;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.story.RoguePokemon;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// Rogue Pokémon bounties with Cobblemon (ROADMAP 31.14): the rogue is made with Cobblemon's own PokemonProperties,
// wild, at the bounty's level (with Radical Cobblemon Trainers, the nearest player's level cap plus 5), half again as
// big (Pokemon.scaleModifier; on Cobblemon 1.8 one of its Alphas instead), nicknamed "Rogue Ursaring" and carrying the
// bounty's id in its persistent data, so it's known after a save and after its capture. A battle a player wins against
// it, or its capture, pays the bounty (once: Rogues.beaten forgets it).
public final class CobblemonRogues implements RoguePokemon {
	// The bounty's id in the rogue Pokémon's persistent data.
	public static final String KEY = "aliveworkplace_rogue_bounty";
	public static final float SIZE = 1.5f;
	// Wild Pokémon despawn by Cobblemon's own despawner; a rogue stays while its bounty is up (it's put back if lost).
	private static final Despawner<PokemonEntity> NEVER = new Despawner<>() {
		@Override
		public void beginTracking(PokemonEntity entity) {
		}

		@Override
		public boolean shouldDespawn(PokemonEntity entity) {
			return false;
		}
	};
	private static MinecraftServer server;

	// Called once at startup (when Cobblemon is installed): listen for battles won and Pokémon caught.
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
			if (event.getWasWildCapture() || server == null) {
				return; // a capture pays through POKEMON_CAPTURED
			}
			List<UUID> winners = new ArrayList<>();
			for (BattleActor a : event.getWinners()) {
				if (a.getType() == ActorType.PLAYER) {
					a.getPlayerUUIDs().forEach(winners::add);
				}
			}
			List<Pokemon> beaten = new ArrayList<>();
			for (BattleActor a : event.getLosers()) {
				if (a.getType() == ActorType.WILD) {
					for (BattlePokemon p : a.getPokemonList()) {
						beaten.add(p.getOriginalPokemon());
					}
				}
			}
			if (winners.isEmpty() || beaten.isEmpty()) {
				return;
			}
			MinecraftServer s = server;
			s.execute(() -> {
				ServerPlayer player = s.getPlayerList().getPlayer(winners.get(0));
				if (player != null) {
					beaten.forEach(p -> beaten(s, p, player, false));
				}
			});
		});
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
			ServerPlayer player = event.getPlayer();
			beaten(player.getServer(), event.getPokemon(), player, true);
		});
	}

	// The bounty a rogue Pokémon carries, or null.
	public static UUID bountyOf(Pokemon pokemon) {
		String id = Nbt.getString(pokemon.getPersistentData(), KEY);
		try {
			return id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// {@code pokemon} was beaten in battle (or caught) by {@code player}: its bounty, if any, pays them once. A caught
	// rogue is the catcher's to keep, its name with it, and carries no bounty any more.
	public static boolean beaten(MinecraftServer server, Pokemon pokemon, ServerPlayer player, boolean caught) {
		UUID id = bountyOf(pokemon);
		if (id == null || server == null) {
			return false;
		}
		if (caught) {
			pokemon.getPersistentData().remove(KEY);
		}
		return Rogues.beaten(server, id, player, caught);
	}

	@Override
	public boolean has(ResourceLocation species) {
		return PokemonSpecies.getByIdentifier(species) != null;
	}

	@Override
	public Component speciesName(ResourceLocation species) {
		Species s = PokemonSpecies.getByIdentifier(species);
		return s == null ? Component.literal(species.getPath()) : s.getTranslatedName();
	}

	// The level a rogue is spawned at: the bounty's, or with Radical Cobblemon Trainers {@code near}'s cap plus 5.
	public static int level(int level, ServerPlayer near) {
		if (near != null) {
			OptionalInt cap = RctLevelCaps.levelCap(near);
			if (cap.isPresent()) {
				return Math.max(1, Math.min(100, cap.getAsInt() + 5));
			}
		}
		return level;
	}

	@Override
	public Entity spawn(ServerLevel level, BlockPos at, ResourceLocation species, int pokemonLevel, Component name, UUID bounty, ServerPlayer near) {
		if (PokemonSpecies.getByIdentifier(species) == null) {
			return null;
		}
		boolean alpha = PokemonFeatures.ALPHAS.available();
		String props = species + " level=" + level(pokemonLevel, near) + (alpha ? " alpha=true" : "");
		PokemonEntity entity = PokemonProperties.Companion.parse(props, " ", "=").createEntity(level);
		Pokemon pokemon = entity.getPokemon();
		if (!alpha) {
			pokemon.setScaleModifier(SIZE);
		}
		pokemon.setNickname(name.copy());
		pokemon.getPersistentData().putString(KEY, bounty.toString());
		entity.setDespawner(NEVER);
		entity.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		if (!level.addFreshEntity(entity)) {
			AliveWorkplace.LOG.warn("Couldn't add the rogue {} at {}", species, at);
			return null;
		}
		return entity;
	}

	@Override
	public int level(Entity entity) {
		return entity instanceof PokemonEntity e ? e.getPokemon().getLevel() : 0;
	}

	// The rogue's size (1.5, or Cobblemon's own for an Alpha); 0 for anything else.
	public static float size(Entity entity) {
		return entity instanceof PokemonEntity e ? e.getPokemon().getScaleModifier() : 0f;
	}
}
//?}
