package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA lane D, 2026-11-09, the Professor's Thesis (31.21) through Cobblemon's own capture event: the wiki and the spec say a
 * catch is judged "where the ball landed" (professors-thesis.md). The other thesis tests hand onCatch a made-up place,
 * so nothing checked that the capture listener reads the ball's place rather than the catcher's. The Fire topic's third
 * catch is a Ground type below y=0; the test area's floor is far below 0, the catcher stands high above it.
 */
public class QaThesisBall1109dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static Pokemon diglett() {
		return PokemonProperties.Companion.parse("diglett level=20", " ", "=").create();
	}

	private static void capture(ServerPlayer player, Pokemon pokemon, double x, double y, double z) {
		EmptyPokeBallEntity ball = new EmptyPokeBallEntity(player.serverLevel());
		ball.setPos(x, y, z);
		CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(pokemon, player, ball));
	}

	/**
	 * A Ground type caught by a ball that landed below y=0 counts for "below y=0" though its catcher stands at y=100; one
	 * caught by a ball that landed at y=100 doesn't, though the catcher stands below 0.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisBall1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisBall1109d")
	public void aCatchCountsWhereTheBallLandedNotWhereTheCatcherStands(GameTestHelper helper) {
		ArcState s = QaThesis1028CompatTests.start(helper, "fire");
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaThesis1028CompatTests.player(helper);
		BlockPos low = helper.absolutePos(new BlockPos(6, 2, 6));
		helper.assertTrue(low.getY() < 0, "setup: the test area's floor is at y=" + low.getY() + ", not below 0");
		player.teleportTo(low.getX() + 0.5, low.getY(), low.getZ() + 0.5);
		capture(player, diglett(), low.getX() + 0.5, 100, low.getZ() + 0.5);
		helper.assertTrue(ProfessorsThesis.catches(s).isEmpty(),
			"a Ground type caught by a ball at y=100 counted for 'below y=0' because its catcher stood at y=" + low.getY());
		player.teleportTo(low.getX() + 0.5, 100, low.getZ() + 0.5);
		capture(player, diglett(), low.getX() + 0.5, low.getY() + 0.2, low.getZ() + 0.5);
		helper.assertTrue(ProfessorsThesis.catches(s).size() == 1,
			"a Ground type caught by a ball that landed at y=" + low.getY() + " didn't count for 'below y=0' (its catcher at y=100)");
		BlockPos where = ProfessorsThesis.catches(s).get(0).pos();
		helper.assertTrue(where.getY() == low.getY() && level.isLoaded(where), "the thesis notes the catch at " + where.toShortString() + ", not where the ball landed");
		helper.succeed();
	}
}
