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
 *
 * <p>B1167, B1168: a battle keeps real time, not game time. Cobblemon holds every message of a turn on the clock
 * ({@code PokemonBattle.dispatchWaiting} makes a {@code WaitDispatch}, which compares {@code System.currentTimeMillis}),
 * and the headless test server ticks flat out. The test's 1600 ticks were over in 0.78 s in CI build 42943893035,
 * while the battle, past the refusal and already holding its next request, was still showing the turn's messages. So
 * the test keeps real time too: each of its ticks takes at least {@link #TICK_NANOS}, which makes its timeout at
 * least 30 seconds whatever the server's speed (a battle that goes on reaches turn 3 in a few seconds).
 */
public class TrainerRefusedChoiceCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The least real time one tick of the test takes: 5 ms, so its 6000 ticks are at least 30 seconds. */
	private static final long TICK_NANOS = 5_000_000L;

	/** Waits until {@code ticks} ticks of {@link #TICK_NANOS} have really passed since {@code startedNanos}. */
	private static void keepRealTime(long startedNanos, long ticks) {
		long ahead = startedNanos + ticks * TICK_NANOS - System.nanoTime();
		if (ahead <= 0) {
			return;
		}
		try {
			Thread.sleep(ahead / 1_000_000L, (int) (ahead % 1_000_000L));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The trainer's choice for turn 1 is one the battle refuses (a switch to the Pokémon already out): turn 2 still comes. */
	//$ gametest_ticks_batch AREA '6000' '"trainer_refused_choice"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "trainer_refused_choice")
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
		long started = System.nanoTime();
		long[] ticks = {0};
		helper.onEachTick(() -> {
			keepRealTime(started, ++ticks[0]);
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
				+ them[0].getMustChoose() + " request=" + (them[0].getRequest() != null) + " after its choice was refused (the player mustChoose="
				+ us.getMustChoose() + " request=" + (us.getRequest() != null) + ", " + ticks[0] + " ticks in "
				+ (System.nanoTime() - started) / 1_000_000L + " ms)");
			if (!battle.getEnded()) {
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, false);
			}
		});
	}
}
