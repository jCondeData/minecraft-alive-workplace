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
 * QA (lane D, 2026-11-14), B43 verified from the spec ("the trainer answers a refused choice with Showdown's default
 * choice, at most 3 times per request"): the fix's own test refuses one choice, on turn 1. A battle is long, and the
 * limit is per request, so a trainer refused on every one of five turns in a row (five requests, more refusals than one
 * request's limit) must still see the battle go on to turn 6. Real time is kept as in {@link TrainerRefusedChoiceCompatTests}.
 */
public class QaTrainerRefusedEveryTurn1114dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final long TICK_NANOS = 5_000_000L;
	private static final int REFUSED_TURNS = 5;

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

	/** The trainer's choice is refused on turns 1 to 5, one refusal per request: the battle still reaches turn 6 (or ends). */
	//$ gametest_ticks_batch AREA '12000' '"qa_trainer_refused_every_turn_1114d"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qa_trainer_refused_every_turn_1114d")
	public void aTrainerRefusedOnFiveTurnsInARowStillBattlesOn(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, 6.5)));
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
		int[] refusedOn = {0}; // the last turn whose trainer choice was made one the battle refuses
		int[] refusals = {0};
		long started = System.nanoTime();
		long[] ticks = {0};
		helper.onEachTick(() -> {
			keepRealTime(started, ++ticks[0]);
			if (battle.getEnded() || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
				return;
			}
			int turn = battle.getTurn();
			if (turn > refusedOn[0] && turn <= REFUSED_TURNS) {
				// This turn's trainer choice becomes a switch to the Pokémon already out, which Showdown refuses.
				if (them[0].getResponses().isEmpty() || them[0].getActivePokemon().get(0).getBattlePokemon() == null) {
					return;
				}
				them[0].getResponses().clear();
				them[0].getResponses().add(new SwitchActionResponse(them[0].getActivePokemon().get(0).getBattlePokemon().getUuid()));
				refusedOn[0] = turn;
				refusals[0]++;
			}
			var moves = us.getRequest().getActive().get(0).getMoves();
			var move = moves.stream().filter(m -> m.getId().equals("splash") && m.canBeUsed()).findFirst()
				.or(() -> moves.stream().filter(m -> m.canBeUsed()).findFirst()).orElse(moves.get(0));
			var targets = move.getTargets(us.getActivePokemon().get(0));
			String target = targets == null || targets.isEmpty() ? null : them[0].getActivePokemon().get(0).getPNX();
			us.setActionResponses(List.<ShowdownActionResponse>of(new MoveActionResponse(move.getId(), target, null)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(battle.getTurn() > REFUSED_TURNS || battle.getEnded(), "the battle stands at turn " + battle.getTurn()
				+ " after " + refusals[0] + " refused trainer choices (one per turn): the trainer mustChoose=" + them[0].getMustChoose()
				+ " request=" + (them[0].getRequest() != null) + ", " + ticks[0] + " ticks in " + (System.nanoTime() - started) / 1_000_000L + " ms");
			if (!battle.getEnded()) {
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, false);
			}
		});
	}
}
