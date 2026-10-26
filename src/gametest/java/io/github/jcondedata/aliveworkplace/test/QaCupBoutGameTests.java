package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.TypeChart;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane d, 2026-10-26) for ROADMAP 28.18, Cup bouts between villagers, from the spec: type effectiveness from
 * {@code type_chart.json}, the 18 types (the whole chart, every one of its 324 match-ups, against the games' chart since
 * Fairy came in; and two-type Pokémon multiply); "90 seconds at most, then the side with more health left wins", from
 * either side, and a bout where neither side can hurt the other still ends on time with a winner; a Pokémon with no
 * attacking move still fights.
 */
public class QaCupBoutGameTests implements FabricGameTest {
	private static final UUID A = UUID.nameUUIDFromBytes("qa-mira".getBytes());
	private static final UUID B = UUID.nameUUIDFromBytes("qa-dara".getBytes());

	/** The games' chart (attacker to defender, every match-up that isn't 1x). */
	private static final Map<String, Map<String, Double>> CHART = Map.ofEntries(
		Map.entry("normal", Map.of("rock", .5, "ghost", 0.0, "steel", .5)),
		Map.entry("fire", Map.of("fire", .5, "water", .5, "grass", 2.0, "ice", 2.0, "bug", 2.0, "rock", .5, "dragon", .5, "steel", 2.0)),
		Map.entry("water", Map.of("fire", 2.0, "water", .5, "grass", .5, "ground", 2.0, "rock", 2.0, "dragon", .5)),
		Map.entry("electric", Map.of("water", 2.0, "electric", .5, "grass", .5, "ground", 0.0, "flying", 2.0, "dragon", .5)),
		Map.entry("grass", Map.of("fire", .5, "water", 2.0, "grass", .5, "poison", .5, "ground", 2.0, "flying", .5, "bug", .5, "rock", 2.0,
			"dragon", .5, "steel", .5)),
		Map.entry("ice", Map.of("fire", .5, "water", .5, "grass", 2.0, "ice", .5, "ground", 2.0, "flying", 2.0, "dragon", 2.0, "steel", .5)),
		Map.entry("fighting", Map.ofEntries(Map.entry("normal", 2.0), Map.entry("ice", 2.0), Map.entry("poison", .5), Map.entry("flying", .5),
			Map.entry("psychic", .5), Map.entry("bug", .5), Map.entry("rock", 2.0), Map.entry("ghost", 0.0), Map.entry("dark", 2.0),
			Map.entry("steel", 2.0), Map.entry("fairy", .5))),
		Map.entry("poison", Map.of("grass", 2.0, "poison", .5, "ground", .5, "rock", .5, "ghost", .5, "steel", 0.0, "fairy", 2.0)),
		Map.entry("ground", Map.of("fire", 2.0, "electric", 2.0, "grass", .5, "poison", 2.0, "flying", 0.0, "bug", .5, "rock", 2.0, "steel", 2.0)),
		Map.entry("flying", Map.of("electric", .5, "grass", 2.0, "fighting", 2.0, "bug", 2.0, "rock", .5, "steel", .5)),
		Map.entry("psychic", Map.of("fighting", 2.0, "poison", 2.0, "psychic", .5, "dark", 0.0, "steel", .5)),
		Map.entry("bug", Map.of("fire", .5, "grass", 2.0, "fighting", .5, "poison", .5, "flying", .5, "psychic", 2.0, "ghost", .5, "dark", 2.0,
			"steel", .5, "fairy", .5)),
		Map.entry("rock", Map.of("fire", 2.0, "ice", 2.0, "fighting", .5, "ground", .5, "flying", 2.0, "bug", 2.0, "steel", .5)),
		Map.entry("ghost", Map.of("normal", 0.0, "psychic", 2.0, "ghost", 2.0, "dark", .5)),
		Map.entry("dragon", Map.of("dragon", 2.0, "steel", .5, "fairy", 0.0)),
		Map.entry("dark", Map.of("fighting", .5, "psychic", 2.0, "ghost", 2.0, "dark", .5, "fairy", .5)),
		Map.entry("steel", Map.of("fire", .5, "water", .5, "electric", .5, "ice", 2.0, "rock", 2.0, "steel", .5, "fairy", 2.0)),
		Map.entry("fairy", Map.of("fire", .5, "fighting", 2.0, "poison", .5, "dragon", 2.0, "dark", 2.0, "steel", .5)));

	private static CupBout.Fighter fighter(String species, List<String> types, int hp, int attack, int speed, List<CupBout.Move> moves) {
		return new CupBout.Fighter("cobblemon:" + species, "cobblemon.species." + species + ".name", types, 50, hp, attack, 60, attack, 60, speed, moves);
	}

	private static CupBout bout(List<CupBout.Fighter> a, List<CupBout.Fighter> b, long seed) {
		return new CupBout(A, B, a, b, 1, seed, BlockPos.ZERO, BlockPos.ZERO.west(10), BlockPos.ZERO.east(10));
	}

	/** Every one of the 324 match-ups is the games' multiplier, and a two-type Pokémon takes both types' multipliers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyMatchUpOfTheChartIsTheGamesOwn(GameTestHelper helper) {
		helper.assertTrue(TypeChart.TYPES.size() == 18 && TypeChart.loadedTypes().containsAll(TypeChart.TYPES), "the chart has " + TypeChart.loadedTypes());
		StringBuilder wrong = new StringBuilder();
		int checked = 0;
		for (String attack : TypeChart.TYPES) {
			for (String defend : TypeChart.TYPES) {
				double want = CHART.get(attack).getOrDefault(defend, 1.0);
				double got = TypeChart.multiplier(attack, defend);
				checked++;
				if (got != want) {
					wrong.append(attack).append(" on ").append(defend).append(": ").append(got).append(" not ").append(want).append("; ");
				}
			}
		}
		helper.assertTrue(checked == 324 && wrong.isEmpty(), "match-ups off the games' chart: " + wrong);
		helper.assertTrue(TypeChart.multiplier("ground", List.of("fire", "flying")) == 0, "Ground on Fire/Flying (Charizard) isn't 0");
		helper.assertTrue(TypeChart.multiplier("grass", List.of("fire", "flying")) == 0.25, "Grass on Fire/Flying isn't 1/4");
		helper.assertTrue(TypeChart.multiplier("ice", List.of("dragon", "flying")) == 4, "Ice on Dragon/Flying (Dragonite) isn't 4");
		helper.assertTrue(TypeChart.multiplier("FIRE", "Grass") == 2, "the chart minds upper case: Fire on Grass " + TypeChart.multiplier("FIRE", "Grass"));
		helper.succeed();
	}

	/**
	 * At 90 seconds the side with more health left wins, whichever side that is: a Gengar chipping at a sturdy Snorlax
	 * that can't touch it (Normal moves on a Ghost) wins on time from side 0 and from side 1; the bout never runs past
	 * 90 seconds.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onTimeTheSideWithMoreHealthWinsFromEitherSide(GameTestHelper helper) {
		// Normal moves can't touch a Ghost; the Gengar chips with a Poison move (1x on Normal)
		List<CupBout.Fighter> ghost = List.of(fighter("gengar", List.of("ghost", "poison"), 120, 1, 110, List.of(new CupBout.Move("poison_sting", "poison", 15, 0, true))));
		List<CupBout.Fighter> sturdy = List.of(fighter("snorlax", List.of("normal"), 100000, 200, 30,
			List.of(new CupBout.Move("body_slam", "normal", 85, 0, true), new CupBout.Move("hyper_beam", "normal", 150, 0, false))));
		for (long seed = 1; seed <= 4; seed++) {
			CupBout first = bout(ghost, sturdy, seed);
			first.playOut();
			helper.assertTrue(first.over() && first.onTime && first.winner == 0, "seed " + seed + ": the Gengar (side 0) with all its health didn't win on time: winner "
				+ first.winner + ", on time " + first.onTime + ", health " + first.share(0) + " against " + first.share(1));
			helper.assertTrue(first.ticks <= CupBout.MAX_TICKS, "the bout ran " + first.ticks + " ticks, past 90 seconds");
			helper.assertTrue(first.share(1) < 1, "setup: the Gengar never hurt the Snorlax");
			CupBout second = bout(sturdy, ghost, seed);
			second.playOut();
			helper.assertTrue(second.over() && second.onTime && second.winner == 1, "seed " + seed + ": the Gengar (side 1) with all its health didn't win on time: winner "
				+ second.winner + ", health " + second.share(0) + " against " + second.share(1));
		}
		helper.succeed();
	}

	/**
	 * Neither side can hurt the other (a Normal-moved Snorlax and a Ghost-moved Gengar: 0x both ways): the bout still
	 * ends at 90 seconds with a winner; and a Pokémon with no attacking moves at all still fights (Struggle) to a winner.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBoutNobodyCanWinByMovesStillEndsAndAMovelessPokemonFights(GameTestHelper helper) {
		List<CupBout.Fighter> ghost = List.of(fighter("gengar", List.of("ghost"), 120, 80, 110, List.of(new CupBout.Move("shadow_ball", "ghost", 80, 0, false))));
		List<CupBout.Fighter> normal = List.of(fighter("snorlax", List.of("normal"), 200, 80, 30, List.of(new CupBout.Move("body_slam", "normal", 85, 0, true))));
		CupBout stalemate = bout(ghost, normal, 7);
		List<CupBout.Action> log = stalemate.playOut();
		helper.assertTrue(stalemate.over() && stalemate.onTime && (stalemate.winner == 0 || stalemate.winner == 1),
			"a bout neither side can hurt in didn't end on time with a winner: " + stalemate.winner + ", on time " + stalemate.onTime);
		helper.assertTrue(stalemate.ticks == CupBout.MAX_TICKS, "it ended at " + stalemate.ticks + " ticks, not 90 seconds");
		helper.assertTrue(!log.isEmpty() && log.stream().allMatch(a -> a.damage() == 0), "a 0x move did damage: " + log);
		List<CupBout.Fighter> moveless = List.of(fighter("magikarp", List.of("water"), 60, 40, 80, List.of()));
		List<CupBout.Fighter> rattata = List.of(fighter("rattata", List.of("normal"), 60, 40, 70, List.of(new CupBout.Move("tackle", "normal", 40, 0, true))));
		CupBout splash = bout(moveless, rattata, 3);
		List<CupBout.Action> moves = splash.playOut();
		helper.assertTrue(splash.over() && (splash.winner == 0 || splash.winner == 1), "the moveless bout has no winner");
		helper.assertTrue(moves.stream().anyMatch(a -> a.side() == 0 && a.move().equals(CupBout.STRUGGLE) && a.damage() > 0),
			"the Magikarp with no attacking move never struggled: " + moves);
		helper.succeed();
	}
}
