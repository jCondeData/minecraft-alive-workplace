package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * B43: a villager trainer's battle goes on when the battle refuses the trainer's choice. In the showcase the trainer's
 * AI switched to a Pokémon the battle counted as fainted ("[Invalid choice] Can't switch: You can't switch to a fainted
 * Pokémon"); Cobblemon then waits for that side to choose again, an AI never did, and the battle stood still for good.
 */
public class TrainerRefusedChoiceCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** The trainer's choice for turn 1 is one the battle refuses (a switch to the Pokémon already out): turn 2 still comes. */
	//$ gametest_ticks_batch AREA '1600' '"trainer_refused_choice"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "trainer_refused_choice")
	public void aRefusedChoiceDoesNotStopTheTrainersBattle(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, 6.5)));
		// Sturdy and harmless: Splash does nothing, so nobody faints while the test watches the turns.
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("magikarp level=100", " ", "=").create());

		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, trainer);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		BattleActor us = battle.getActor(player);
		BattleActor[] them = new BattleActor[1];
		battle.getActors().forEach(a -> {
			if (a != us) {
				them[0] = a;
			}
		});
		boolean[] swapped = {false};
		helper.onEachTick(() -> {
			if (battle.getEnded() || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
				return;
			}
			if (!swapped[0]) {
				// Turn 1: the trainer has chosen and waits for us. Its choice becomes one Showdown refuses.
				if (battle.getTurn() != 1 || them[0].getResponses().isEmpty() || them[0].getActivePokemon().get(0).getBattlePokemon() == null) {
					return;
				}
				them[0].getResponses().clear();
				them[0].getResponses().add(new SwitchActionResponse(them[0].getActivePokemon().get(0).getBattlePokemon().getUuid()));
				swapped[0] = true;
			}
			var move = us.getRequest().getActive().get(0).getMoves().stream().filter(m -> m.getId().equals("splash")).findFirst()
				.orElse(us.getRequest().getActive().get(0).getMoves().get(0));
			us.setActionResponses(List.<ShowdownActionResponse>of(new MoveActionResponse(move.getId(), null, null)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(swapped[0], "turn 1 never asked both sides (turn " + battle.getTurn() + ")");
			helper.assertTrue(battle.getTurn() >= 3 || battle.getEnded(), "the battle stands at turn " + battle.getTurn() + ": the trainer mustChoose="
				+ them[0].getMustChoose() + " request=" + (them[0].getRequest() != null) + " after its choice was refused");
			if (!battle.getEnded()) {
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, false);
			}
		});
	}
}
