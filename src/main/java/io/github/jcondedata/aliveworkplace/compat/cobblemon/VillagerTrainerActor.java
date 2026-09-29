package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * A trainer villager's side of a battle. Backed by the villager (Cobblemon's own trainer actor has no body), so the
 * battle sends their Pokémon out beside them the way a player's are, and Mega Showdown can show a Mega Evolution on it.
 * When the battle ends the Pokémon are called back; a trainer's Pokémon found in the world outside a battle (the server
 * stopped mid-battle) is removed as it loads, so it can never be caught.
 */
final class VillagerTrainerActor extends AIBattleActor implements EntityBackedBattleActor<LivingEntity> {
	/** Marks a trainer's Pokémon (in its persistent data). */
	static final String TRAINER_POKEMON = "aliveworkplace_trainer";

	private final Villager villager;
	private final String name;
	private final Vec3 initialPos;

	VillagerTrainerActor(Villager villager, String name, List<BattlePokemon> team, BattleAI ai) {
		super(villager.getUUID(), team, ai);
		this.villager = villager;
		this.name = name;
		this.initialPos = villager.position();
	}

	/** Removes trainers' Pokémon left in the world without a battle (called once at startup). */
	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PokemonEntity pokemon && pokemon.getBattleId() == null
					&& pokemon.getPokemon().getPersistentData().getBoolean(TRAINER_POKEMON)) {
				pokemon.discard();
			}
		});
	}

	@Override
	public LivingEntity getEntity() {
		return villager;
	}

	@Override
	public Vec3 getInitialPos() {
		return initialPos;
	}

	@Override
	public ActorType getType() {
		return ActorType.NPC;
	}

	@Override
	public MutableComponent getName() {
		return Component.literal(name);
	}

	@Override
	public MutableComponent nameOwned(String pokemon) {
		return LocalizationUtilsKt.battleLang("owned_pokemon", getName(), Component.literal(pokemon));
	}

	@Override
	public void sendUpdate(NetworkPacket<?> packet) {
		super.sendUpdate(packet);
		if (packet instanceof BattleEndPacket) {
			for (BattlePokemon pokemon : getPokemonList()) {
				PokemonEntity entity = pokemon.getEntity();
				if (entity != null && entity.isAlive()) {
					entity.recallWithAnimation().whenComplete((done, error) -> {
						if (entity.isAlive()) {
							entity.discard();
						}
					});
				}
			}
		}
	}
}
