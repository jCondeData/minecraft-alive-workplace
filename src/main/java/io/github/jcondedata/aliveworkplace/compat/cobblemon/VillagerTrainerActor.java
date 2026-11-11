//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMadeInvalidChoicePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

// A trainer villager's side of a battle. Backed by the villager (Cobblemon's own trainer actor has no body), so the
// battle sends their Pokémon out beside them the way a player's are, and Mega Showdown can show a Mega Evolution on it.
// When the battle ends the Pokémon are called back; a trainer's Pokémon found in the world outside a battle (the server
// stopped mid-battle) is removed as it loads, so it can never be caught.
final class VillagerTrainerActor extends AIBattleActor implements EntityBackedBattleActor<LivingEntity> {
	// Marks a trainer's Pokémon (in its persistent data).
	static final String TRAINER_POKEMON = "aliveworkplace_trainer";

	private final Villager villager;
	private final String name;
	private final Vec3 initialPos;
	// B43: what the battle last asked this trainer, and how often its answer to that was refused.
	private ShowdownActionRequest asked;
	private int refused;
	// Refusals answered before the trainer gives up on a request (a battle never refused twice in testing).
	static final int MAX_REFUSALS = 3;

	VillagerTrainerActor(Villager villager, String name, List<BattlePokemon> team, BattleAI ai) {
		super(villager.getUUID(), team, ai);
		this.villager = villager;
		this.name = name;
		this.initialPos = villager.position();
	}

	// Removes trainers' Pokémon left in the world without a battle (called once at startup).
	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PokemonEntity pokemon && pokemon.getBattleId() == null
					&& Nbt.getBoolean(pokemon.getPokemon().getPersistentData(), TRAINER_POKEMON)) {
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

	// B43: the battle refused this trainer's choice (the AI once switched to a Pokémon the battle counted as fainted).
	// Cobblemon then waits for the actor to choose again, which a player does on their screen and an AI never did: the
	// battle stood still for good. The trainer answers the same request with Showdown's own "default" choice, its first
	// legal move or switch, which can't be refused.
	private void chooseAgain() {
		if (asked == null || getBattle().getEnded() || refused >= MAX_REFUSALS) {
			return;
		}
		refused++;
		if (getRequest() == null) {
			setRequest(asked); // the battle cleared it when it passed the refused choice on
		}
		List<ShowdownActionResponse> choice = new ArrayList<>();
		int slots = Math.max(asked.getActive() == null ? 0 : asked.getActive().size(), asked.getForceSwitch().size());
		for (int i = 0; i < slots && i < getActivePokemon().size(); i++) {
			choice.add(new DefaultActionResponse());
		}
		getResponses().clear();
		setActionResponses(choice);
	}

	@Override
	public void sendUpdate(NetworkPacket<?> packet) {
		if (packet instanceof BattleMakeChoicePacket && getRequest() != null) {
			asked = getRequest();
			refused = 0;
		}
		super.sendUpdate(packet);
		if (packet instanceof BattleMadeInvalidChoicePacket) {
			chooseAgain();
		}
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
//?}
