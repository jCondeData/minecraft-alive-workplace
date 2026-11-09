package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA 2026-11-09 (lane d), ROADMAP 43.1: a failing test kept out of the suite (not registered) until the mod is fixed.
 *
 * A refused Challenge on a Trainer Leader uses up the player's one challenge of the day. {@code Trainers.challenge} writes
 * the player into {@code LEADER_CHALLENGES} before {@code TrainerBattles.challenge} runs, and that one can still refuse
 * (the Leader already battling someone else, the player already in a battle, or no Pokémon in the party), so no battle
 * happens but the day's challenge is spent. A player who opened the Leader's screen while the Leader was free, and pressed
 * Challenge just after another player started a battle with them, is told "already in a battle" and, once that battle
 * ends, "takes one challenge a day ... Come back tomorrow!" without ever having battled.
 */
public class QaPokemonScreens1109dBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A opens the Leader's screen; B challenges first; A's press is refused (busy); once B is done, A may still challenge today. */
	//$ gametest_ticks_batch AREA '400' '"qa43LeaderBusy"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa43LeaderBusy")
	public void aRefusedLeaderChallengeKeepsTodaysChallenge(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qa43ldrA");
		ServerPlayer b = QaPokemonScreens1109dCompatTests.joiner(helper, f.players(), new ArrayList<>(), "qa43ldrB");
		QaPokemonScreens1109dCompatTests.party(b, "squirtle level=30");
		TrainerMenu menu = QaPokemonScreens1109dCompatTests.openTrainer(helper, f.player(), f.trainer());
		b.moveTo(f.trainer().getX() - 1.5, f.trainer().getY(), f.trainer().getZ());
		Trainers.challenge(b, f.trainer());
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(b), "setup: B's battle with the Leader didn't start");
		QaPokemonScreens1109dCompatTests.press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(f.player()), "A battled a busy Leader");
		QaPokemonScreens1109dCompatTests.finish(b, false);
		TrainerView after = TrainerScreens.view(f.player(), f.trainer());
		helper.assertTrue(after.canChallenge(), "A never battled the Leader but is refused: " + after.refusal().getString());
		helper.assertValueEqual(QaPokemonScreens1109dCompatTests.key(after.today()), "screen.aliveworkplace.trainer.today.none",
			"A's today line after a refused challenge");
		helper.succeed();
	}
}
