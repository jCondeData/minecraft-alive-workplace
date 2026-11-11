package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
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
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-11, lane d), written from B43's spec: "the scripted battle always finishes", a battle against a Master
 * trainer, which froze for good when the battle refused the trainer's choice once. The fix's test refuses one choice
 * of a Novice trainer on turn 1. These add: a Master (rank 5, the showcase's opponent) whose choice is refused on two
 * turns in a row, so the answer to a refusal must work for every request, not only the first; a battle where the
 * player's Pokémon faint (the showcase's newest stall) running to its end; and the trainer's forced switch picking a
 * fainted Pokémon (the showcase's original error) not stopping the battle. Like the fix's test they keep real time
 * (Cobblemon shows each turn's messages on the wall clock, B1167/B1168).
 */
public class QaTrainerBattle1111dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final long TICK_NANOS = 5_000_000L;

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

	/** A Master's choice refused on two turns in a row (a switch to the Pokémon already out): the battle still goes on. */
	//$ gametest_ticks_batch AREA '8000' '"qa1111d_master_refused"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qa1111d_master_refused")
	public void aMastersChoiceRefusedTwiceInARowDoesNotStopTheBattle(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		trainer.setNoAi(true);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		trainer.setVillagerData(trainer.getVillagerData().setLevel(5));
		helper.assertValueEqual(Trainers.tier(trainer), 5, "the trainer's rank (a Master)");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
		// Shuckle: walls up against a Master's team for many turns, and barely scratches it back.
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("shuckle level=100", " ", "=").create());
		PartnerShowsCompatTests.after(helper, () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(player);
			if (left != null) {
				CobblemonTrainers.finishForTest(left, false);
			}
		});

		helper.assertTrue(Trainers.refusal(player, trainer) == null, "the Master refuses the challenge: " + Trainers.refusal(player, trainer));
		Trainers.challenge(player, trainer);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		BattleActor us = battle.getActor(player);
		BattleActor[] them = new BattleActor[1];
		battle.getActors().forEach(a -> {
			if (a != us) {
				them[0] = a;
			}
		});
		int[] refusedOn = {-1, -1};
		int[] refusals = {0};
		long started = System.nanoTime();
		long[] ticks = {0};
		helper.onEachTick(() -> {
			keepRealTime(started, ++ticks[0]);
			if (battle.getEnded() || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
				return;
			}
			int turn = battle.getTurn();
			if (refusals[0] < 2 && (refusals[0] == 0 || turn > refusedOn[0])) {
				// The trainer has chosen and waits for us: its choice becomes one the battle refuses.
				if (them[0].getResponses().isEmpty() || them[0].getActivePokemon().get(0).getBattlePokemon() == null) {
					return;
				}
				them[0].getResponses().clear();
				them[0].getResponses().add(new SwitchActionResponse(them[0].getActivePokemon().get(0).getBattlePokemon().getUuid()));
				refusedOn[refusals[0]] = turn;
				refusals[0]++;
			}
			if (us.getRequest().getActive() == null || us.getRequest().getActive().isEmpty()) {
				return;
			}
			var moves = us.getRequest().getActive().get(0).getMoves();
			var move = moves.stream().filter(m -> m.canBeUsed()).findFirst().orElse(moves.get(0));
			var targets = move.getTargets(us.getActivePokemon().get(0));
			String target = targets == null || targets.isEmpty() ? null : them[0].getActivePokemon().get(0).getPNX();
			us.setActionResponses(List.<ShowdownActionResponse>of(new MoveActionResponse(move.getId(), target, null)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(refusals[0] == 2, "only " + refusals[0] + " refusal(s) made (turn " + battle.getTurn() + ", ended "
				+ battle.getEnded() + ")");
			helper.assertTrue(refusedOn[1] == refusedOn[0] + 1, "the refusals weren't on two turns in a row: turns " + refusedOn[0]
				+ " and " + refusedOn[1]);
			helper.assertTrue(battle.getTurn() >= refusedOn[1] + 2 || battle.getEnded(), "the battle stands at turn " + battle.getTurn()
				+ " after refusals on turns " + refusedOn[0] + " and " + refusedOn[1] + ": the Master mustChoose=" + them[0].getMustChoose()
				+ " request=" + (them[0].getRequest() != null) + " (" + ticks[0] + " ticks in " + (System.nanoTime() - started) / 1_000_000L
				+ " ms)");
			if (!battle.getEnded()) {
				CobblemonTrainers.finishForTest(battle, false);
			}
		});
	}

	/**
	 * B43's showcase case after the fix: in showcase run 484 (main d8080fc, with the fix) the Master's Focus Blast knocked out
	 * the player's Snorlax on turn 3 and the battle then stood still, with neither side asked anything. So: a player with
	 * two Pokémon a Master knocks out quickly, answering every request as a player would (a move, or the next Pokémon picked
	 * when one faints), must see the battle run to its end (the Master wins). (Answering the forced switch with Showdown's
	 * "default", as the showcase scene does, stood this battle still at turn 1 in two local runs; picking the Pokémon
	 * doesn't.)
	 */
	//$ gametest_ticks_batch AREA '12000' '"qa1111d_master_faint"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qa1111d_master_faint")
	public void aBattleWhereThePlayersPokemonFaintRunsToTheEnd(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		trainer.setNoAi(true);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		trainer.setVillagerData(trainer.getVillagerData().setLevel(5));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("snorlax level=20", " ", "=").create());
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("rattata level=20", " ", "=").create());
		PartnerShowsCompatTests.after(helper, () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(player);
			if (left != null) {
				CobblemonTrainers.finishForTest(left, false);
			}
		});

		helper.assertTrue(Trainers.refusal(player, trainer) == null, "the Master refuses the challenge: " + Trainers.refusal(player, trainer));
		Trainers.challenge(player, trainer);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		BattleActor us = battle.getActor(player);
		BattleActor[] them = new BattleActor[1];
		battle.getActors().forEach(a -> {
			if (a != us) {
				them[0] = a;
			}
		});
		int[] forced = {0};
		long started = System.nanoTime();
		long[] ticks = {0};
		helper.onEachTick(() -> {
			keepRealTime(started, ++ticks[0]);
			if (battle.getEnded() || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
				return;
			}
			var request = us.getRequest();
			if (request.getForceSwitch() != null && request.getForceSwitch().contains(true)) {
				forced[0]++;
				// as a player does on the screen: the next Pokémon that can still fight
				var next = us.getPokemonList().stream().filter(bp -> bp.getHealth() > 0
					&& us.getActivePokemon().stream().noneMatch(a -> a.getBattlePokemon() == bp)).findFirst();
				helper.assertTrue(next.isPresent(), "a forced switch with nobody left to send in");
				us.setActionResponses(List.<ShowdownActionResponse>of(new SwitchActionResponse(next.get().getUuid())));
				return;
			}
			if (request.getActive() == null || request.getActive().isEmpty()) {
				return;
			}
			var moves = request.getActive().get(0).getMoves();
			var move = moves.stream().filter(m -> m.canBeUsed()).findFirst().orElse(moves.get(0));
			var targets = move.getTargets(us.getActivePokemon().get(0));
			String target = targets == null || targets.isEmpty() ? null : them[0].getActivePokemon().get(0).getPNX();
			us.setActionResponses(List.<ShowdownActionResponse>of(new MoveActionResponse(move.getId(), target, null)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(battle.getEnded(), "the battle stands at turn " + battle.getTurn() + " (forced switches " + forced[0]
				+ "): the player request=" + (us.getRequest() != null) + " mustChoose=" + us.getMustChoose() + ", the Master request="
				+ (them[0].getRequest() != null) + " mustChoose=" + them[0].getMustChoose() + " (" + ticks[0] + " ticks in "
				+ (System.nanoTime() - started) / 1_000_000L + " ms)");
			helper.assertTrue(forced[0] >= 1, "the battle ended without the player ever sending in the next Pokémon");
			helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(player) == null, "the player is still held in the battle");
		});
	}

	/**
	 * B43's own case on the trainer's side: the showcase's trainer AI picked a fainted Pokémon to send in ("Can't switch:
	 * You can't switch to a fainted Pokémon") and the battle stood still. A Novice (two Pokémon) against a level 100
	 * Mewtwo; the trainer's first forced switch picks the Pokémon that just fainted. Whoever turns the pick down (Cobblemon
	 * or the battle), the battle must run to its end (the player wins).
	 */
	//$ gametest_ticks_batch AREA '12000' '"qa1111d_forced_refused"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qa1111d_forced_refused")
	public void aTrainersBadForcedSwitchDoesNotStopTheBattle(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		trainer.setNoAi(true);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("mewtwo level=100", " ", "=").create());
		PartnerShowsCompatTests.after(helper, () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(player);
			if (left != null) {
				CobblemonTrainers.finishForTest(left, false);
			}
		});

		helper.assertTrue(Trainers.refusal(player, trainer) == null, "the trainer refuses the challenge: " + Trainers.refusal(player, trainer));
		Trainers.challenge(player, trainer);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		BattleActor us = battle.getActor(player);
		BattleActor[] them = new BattleActor[1];
		battle.getActors().forEach(a -> {
			if (a != us) {
				them[0] = a;
			}
		});
		helper.assertTrue(them[0] instanceof com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor, "the trainer's side isn't an AI: " + them[0]);
		helper.assertTrue(them[0].getPokemonList().size() >= 2, "the Novice has " + them[0].getPokemonList().size() + " Pokémon");
		// The trainer's AI, except that its first forced switch picks the Pokémon that just fainted (a choice the battle refuses).
		int[] badPicks = {0};
		try {
			java.lang.reflect.Field field = com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor.class.getDeclaredField("battleAI");
			field.setAccessible(true);
			com.cobblemon.mod.common.api.battles.model.ai.BattleAI real = (com.cobblemon.mod.common.api.battles.model.ai.BattleAI) field.get(them[0]);
			com.cobblemon.mod.common.api.battles.model.ai.BattleAI picky = (active, b, side, moveset, forceSwitch) -> {
				if (forceSwitch && badPicks[0] == 0) {
					var fainted = them[0].getPokemonList().stream().filter(bp -> bp.getHealth() <= 0).findFirst();
					if (fainted.isPresent()) {
						badPicks[0]++;
						return new SwitchActionResponse(fainted.get().getUuid());
					}
				}
				return real.choose(active, b, side, moveset, forceSwitch);
			};
			field.set(them[0], picky);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: can't replace the trainer's AI", e);
		}
		long started = System.nanoTime();
		long[] ticks = {0};
		helper.onEachTick(() -> {
			keepRealTime(started, ++ticks[0]);
			if (battle.getEnded() || us.getRequest() == null || !us.getMustChoose() || !us.getResponses().isEmpty()) {
				return;
			}
			var request = us.getRequest();
			if (request.getActive() == null || request.getActive().isEmpty()) {
				return;
			}
			var moves = request.getActive().get(0).getMoves();
			var move = moves.stream().filter(m -> m.canBeUsed() && !m.getId().equals("disable") && !m.getId().equals("barrier"))
				.findFirst().orElse(moves.get(0));
			var targets = move.getTargets(us.getActivePokemon().get(0));
			String target = targets == null || targets.isEmpty() ? null : them[0].getActivePokemon().get(0).getPNX();
			us.setActionResponses(List.<ShowdownActionResponse>of(new MoveActionResponse(move.getId(), target, null)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(battle.getEnded(), "the battle stands at turn " + battle.getTurn() + " (bad picks " + badPicks[0]
				+ "): the trainer request=" + (them[0].getRequest() != null) + " mustChoose=" + them[0].getMustChoose() + ", the player request="
				+ (us.getRequest() != null) + " mustChoose=" + us.getMustChoose() + " (" + ticks[0] + " ticks in "
				+ (System.nanoTime() - started) / 1_000_000L + " ms)");
			helper.assertValueEqual(badPicks[0], 1, "the trainer's refused forced switches");
		});
	}
}
