package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-11-07) of the Master trainer's battle AI with Mega Showdown (ROADMAP 38.5, CobblemonMegas): "the
 * trainer's battle AI Mega Evolves it the first turn it can", and only for a Pokémon out in the world (Mega Showdown
 * shows the Mega Evolution on its entity and can't do without one). The trainer's own AI ({@code megaEvolving}) is asked
 * for a turn, as Cobblemon asks it, for a Pokémon sent out and for one with no entity. Kills the nightly survivor at
 * CobblemonMegas:141 (the entity check flipped), which no test reached: the existing test calls {@code withMega} only.
 */
public class QaMegaAi1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** What the wrapped AI would do anyway: use Shadow Ball. */
	private static final BattleAI SHADOW_BALL = (active, battle, side, moveset, forceSwitch) -> new MoveActionResponse("shadowball", null, null);

	private static ShowdownActionResponse turn(BattleAI delegate, Pokemon pokemon, boolean canMega) {
		BattlePokemon battlePokemon = new BattlePokemon(pokemon, pokemon, entity -> kotlin.Unit.INSTANCE);
		BattleAI ai = CobblemonMegas.megaEvolving(delegate);
		// Its constructor wants the actor's battle, which only a running battle has; the AI reads the battle Pokémon only.
		ActiveBattlePokemon active = allocate(ActiveBattlePokemon.class);
		active.setBattlePokemon(battlePokemon);
		ShowdownMoveset moveset = new ShowdownMoveset();
		moveset.setCanMegaEvo(canMega);
		return ai.choose(active, null, null, moveset, false);
	}

	private static <T> T allocate(Class<T> c) {
		try {
			java.lang.reflect.Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			Object unsafe = theUnsafe.get(null);
			return c.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, c));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String gimmick(ShowdownActionResponse response) {
		return response instanceof MoveActionResponse move ? move.getGimmickID() : "not a move: " + response;
	}

	/** A Pokémon sent out into the world, as a trainer's is in battle. */
	private static PokemonEntity sentOut(GameTestHelper helper, String properties) {
		Pokemon pokemon = PokemonProperties.Companion.parse(properties, " ", "=").create();
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		PokemonEntity entity = pokemon.sendOut(helper.getLevel(), at, null, e -> {
			e.setNoAi(true);
			return kotlin.Unit.INSTANCE;
		});
		return entity;
	}

	/**
	 * A Gengar holding Gengarite, out in the world, whose moveset says it can Mega Evolve: the AI's Shadow Ball comes
	 * with the Mega Evolution. The same Gengar's turn with the Mega spent (can't any more) is the plain Shadow Ball.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aMastersPokemonOutInTheWorldMegaEvolvesTheFirstTurnItCan(GameTestHelper helper) {
		if (!CobblemonMegas.ENABLED) {
			helper.succeed(); // Mega Showdown isn't in this compat run: the AI is the plain one
			return;
		}
		PokemonEntity gengar = sentOut(helper, "gengar level=60");
		helper.assertTrue(gengar != null && gengar.getPokemon().getEntity() == gengar, "setup: the Gengar wasn't sent out: " + gengar);
		ShowdownActionResponse first = turn(SHADOW_BALL, gengar.getPokemon(), true);
		helper.assertTrue("mega".equals(gimmick(first)) && "shadowball".equals(((MoveActionResponse) first).getMoveName()),
			"a Gengar out in the world that can Mega Evolve: expected Shadow Ball with the Mega Evolution, got " + gimmick(first));
		ShowdownActionResponse later = turn(SHADOW_BALL, gengar.getPokemon(), false);
		helper.assertTrue(gimmick(later) == null, "after its Mega Evolution (can't any more): expected the plain Shadow Ball, got " + gimmick(later));
		gengar.discard();
		helper.succeed();
	}

	/** The same Gengar with no entity (not sent out into the world): no Mega Evolution, even though the moveset allows it. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aPokemonWithNoEntityNeverMegaEvolves(GameTestHelper helper) {
		Pokemon gengar = PokemonProperties.Companion.parse("gengar level=60", " ", "=").create();
		helper.assertTrue(gengar.getEntity() == null, "setup: the Gengar has an entity");
		ShowdownActionResponse response = turn(SHADOW_BALL, gengar, true);
		helper.assertTrue(gimmick(response) == null, "a Gengar with no entity: expected the plain Shadow Ball, got " + gimmick(response));
		helper.succeed();
	}

	/** When the AI switches instead of attacking, the switch is left alone (a switch can't carry a Mega Evolution). */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aSwitchIsLeftAsItIs(GameTestHelper helper) {
		PokemonEntity gengar = sentOut(helper, "gengar level=60");
		helper.assertTrue(gengar != null && gengar.getPokemon().getEntity() == gengar, "setup: the Gengar wasn't sent out: " + gengar);
		UUID next = UUID.randomUUID();
		BattleAI switching = (active, battle, side, moveset, forceSwitch) -> new SwitchActionResponse(next);
		ShowdownActionResponse response = turn(switching, gengar.getPokemon(), true);
		helper.assertTrue(response instanceof SwitchActionResponse s && next.equals(s.getNewPokemonId()),
			"the AI switched, and the switch came back as " + response);
		gengar.discard();
		helper.succeed();
	}
}
