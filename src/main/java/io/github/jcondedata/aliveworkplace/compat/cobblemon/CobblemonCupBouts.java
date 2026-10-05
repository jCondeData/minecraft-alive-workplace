//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The Pokémon of the Festival Cup's bouts (ROADMAP 28.18): the trainers' themed teams as the bout's fighters, and the
// real Pokémon at the ring. Those are marked in their persistent data, can't be caught (Cobblemon's uncatchable flag),
// hurt or battled, stand still, and are removed as they load unless a bout running now sent them out (so a server that
// stopped mid-bout leaves none behind).
public final class CobblemonCupBouts implements CupBattlers {
	public static final String CUP_POKEMON = "aliveworkplace_cup";
	// The entities the bouts sent out since the server started.
	private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();
	// Called back: discarded when the animation ends, or after this many ticks whatever happens.
	private static final Map<Entity, Long> RECALLING = new ConcurrentHashMap<>();
	private static final int RECALL_TICKS = 40;

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PokemonEntity pokemon && isCupPokemon(pokemon) && !LIVE.contains(pokemon.getUUID())) {
				pokemon.discard();
			}
		});
		CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
			for (BattleActor actor : event.getBattle().getActors()) {
				for (BattlePokemon p : actor.getPokemonList()) {
					if (Nbt.getBoolean(p.getOriginalPokemon().getPersistentData(), CUP_POKEMON)) {
						event.cancel(); // nobody battles a Cup Pokémon
						return;
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RECALLING.isEmpty()) {
				return;
			}
			long now = server.overworld().getGameTime();
			RECALLING.entrySet().removeIf(e -> {
				if (e.getKey().isRemoved()) {
					LIVE.remove(e.getKey().getUUID());
					return true;
				}
				if (now >= e.getValue()) {
					e.getKey().discard();
					LIVE.remove(e.getKey().getUUID());
					return true;
				}
				return false;
			});
		});
	}

	static boolean isCupPokemon(PokemonEntity pokemon) {
		return Nbt.getBoolean(pokemon.getPokemon().getPersistentData(), CUP_POKEMON);
	}

	@Override
	public List<CupBout.Fighter> team(UUID trainer, int tier, CupThemes.Theme theme) {
		List<CupBout.Fighter> out = new ArrayList<>();
		for (Pokemon pokemon : CobblemonTrainers.team(trainer, tier, theme)) {
			out.add(fighter(pokemon));
		}
		return out;
	}

	// A Pokémon as a bout's fighter: its species, name, types, stats at its level and its attacking moves.
	public static CupBout.Fighter fighter(Pokemon pokemon) {
		List<String> types = new ArrayList<>();
		for (ElementalType type : pokemon.getTypes()) {
			types.add(type.getName().toLowerCase(Locale.ROOT));
		}
		List<CupBout.Move> moves = new ArrayList<>();
		for (Move move : pokemon.getMoveSet().getMoves()) {
			if (move.getPower() > 0 && move.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS()) {
				moves.add(new CupBout.Move(move.getName(), move.getTemplate().getElementalType().getName().toLowerCase(Locale.ROOT), (int) move.getPower(),
					move.getAccuracy() <= 0 ? 0 : (int) Math.min(100, move.getAccuracy()), move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL()));
			}
		}
		Component name = pokemon.getSpecies().getTranslatedName();
		String key = name.getContents() instanceof TranslatableContents t ? t.getKey() : name.getString();
		return new CupBout.Fighter(pokemon.getSpecies().getResourceIdentifier().toString(), key, List.copyOf(types), pokemon.getLevel(),
			pokemon.getMaxHealth(), pokemon.getAttack(), pokemon.getDefence(), pokemon.getSpecialAttack(), pokemon.getSpecialDefence(), pokemon.getSpeed(),
			List.copyOf(moves));
	}

	@Override
	public Entity sendOut(ServerLevel level, CupBout.Fighter fighter, Vec3 at, Vec3 facing) {
		ResourceLocation id = ResourceLocation.tryParse(fighter.species());
		Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
		if (species == null) {
			return null;
		}
		Pokemon pokemon = species.create(fighter.level());
		pokemon.getPersistentData().putBoolean(CUP_POKEMON, true);
		UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
		PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
		float yaw = yaw(at, facing);
		entity.moveTo(at.x, at.y, at.z, yaw, 0);
		entity.setYHeadRot(yaw);
		entity.yBodyRot = yaw;
		entity.setNoAi(true);
		entity.setInvulnerable(true);
		entity.setPersistenceRequired();
		LIVE.add(entity.getUUID());
		if (!level.addFreshEntity(entity)) {
			LIVE.remove(entity.getUUID());
			return null;
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		entity.cry();
		return entity;
	}

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180 / Math.PI)) - 90f;
	}

	// As the guards' partners show a move (CobblemonPartners.useMove), with the move's own side: it turns to its target and
	// plays its physical or special animation, and the target gets the impact effect and sound of the move's type.
	@Override
	public void useMove(ServerLevel level, Entity attacker, Entity target, String type, boolean physical) {
		float yaw = yaw(attacker.position(), target.position());
		attacker.setYRot(yaw);
		attacker.setYHeadRot(yaw);
		if (attacker instanceof PokemonEntity p) {
			p.yBodyRot = yaw;
		}
		List<ServerPlayer> near = level.players().stream().filter(p -> p.distanceToSqr(attacker) < 64 * 64).toList();
		if (!near.isEmpty()) {
			new PlayPosableAnimationPacket(attacker.getId(), Set.of(physical ? "physical" : "special"), List.of()).sendToPlayers(near);
			new SpawnSnowstormParticlePacket(ResourceLocation.fromNamespaceAndPath("cobblemon", "impact_" + type),
				target.position().add(0, target.getBbHeight() * 0.5, 0)).sendToPlayers(near);
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "impact." + type))
			.orElse(SoundEvents.PLAYER_ATTACK_STRONG);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.NEUTRAL, 1f, 1f);
	}

	@Override
	public void recall(ServerLevel level, Entity pokemon) {
		if (!(pokemon instanceof PokemonEntity p) || !p.isAlive()) {
			return;
		}
		RECALLING.put(p, level.getGameTime() + RECALL_TICKS);
		p.recallWithAnimation().whenComplete((done, error) -> level.getServer().execute(() -> {
			if (p.isAlive()) {
				p.discard();
			}
		}));
	}

	@Override
	public List<Entity> inBox(ServerLevel level, AABB box) {
		return List.copyOf(level.getEntitiesOfClass(PokemonEntity.class, box, CobblemonCupBouts::isCupPokemon));
	}
}
//?}
