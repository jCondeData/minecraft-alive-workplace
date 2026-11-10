package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.trainer;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-10, lane d): an independent check of B1104 and B1122 (commit 09f9646a4). A villager trainer's Cobblemon
 * team must be the same every time it is built: species, gender, shininess, form, held item and moves (the Trainer
 * screen's models and the battle), for every trainer, at every rank, across a save and reload, while different trainers
 * still get different teams.
 *
 * <p>These three fail on the real code (2026-11-10) and are kept out of the suite (not registered in fabric.mod.json)
 * until the builder fixes the mod: random forms other than choice cloaks (Unown's letter, Smeargle's paint), the moves
 * Cobblemon picks below Master (a Journeyman's Wormadam), and the ability, nature and IVs are still rolled afresh on
 * every build. The passing checks are in {@link QaTrainerTeamsVerify1110dCompatTests}.
 */
public class QaTrainerTeamsVerify1110dBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final List<Stat> STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);

	/** What a player sees of a team member on the screen and in battle: species, form, look, held item, moves, level. */
	static String look(Pokemon p) {
		List<String> moves = new ArrayList<>();
		for (Move move : p.getMoveSet().getMoves()) {
			moves.add(move.getName());
		}
		return p.getSpecies().getName() + "/" + p.getForm().getName() + new TreeSet<>(p.getAspects()) + " " + p.getGender()
			+ (p.getShiny() ? " shiny" : "") + " L" + p.getLevel() + " @" + p.heldItem().getItem() + " " + moves;
	}

	/** The rest of what it fights with: ability, nature, IVs and EVs. */
	static String build(Pokemon p) {
		StringBuilder s = new StringBuilder(p.getSpecies().getName() + " " + p.getAbility().getName() + " " + p.getNature().getName() + " iv");
		for (Stat stat : STATS) {
			s.append(' ').append(p.getIvs().getOrDefault(stat));
		}
		s.append(" ev");
		for (Stat stat : STATS) {
			s.append(' ').append(p.getEvs().getOrDefault(stat));
		}
		return s.toString();
	}

	static List<String> looks(List<Pokemon> team) {
		return team.stream().map(QaTrainerTeamsVerify1110dBugCompatTests::look).toList();
	}

	static List<String> builds(List<Pokemon> team) {
		return team.stream().map(QaTrainerTeamsVerify1110dBugCompatTests::build).toList();
	}

	/** Fixed trainers, so every run checks the same teams. */
	static UUID id(int n) {
		return new UUID(0x51A7E5L * (n + 1) ^ 0x1110D000L, 0x7EA3L * (n + 7) ^ 0x8000000000000000L);
	}

	/**
	 * B1104/B1122 on many trainers, not just the two the bug tests pinned: 16 trainers at every rank (and a Master with
	 * its Mega Stone holder), each team built 4 times, look the same each time.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTeams1110dLooks"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTeams1110dLooks")
	public void everyTrainersTeamLooksTheSameAtEveryRank(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int n = 0; n < 16; n++) {
			for (int tier = 1; tier <= 6; tier++) {
				int rank = Math.min(tier, 5);
				boolean mega = tier == 6;
				List<String> first = looks(CobblemonTrainers.team(id(n), rank, mega));
				for (int k = 0; k < 3; k++) {
					List<String> again = looks(CobblemonTrainers.team(id(n), rank, mega));
					if (!again.equals(first)) {
						problems.add("trainer " + n + " rank " + rank + (mega ? "+mega" : "") + ": " + first + " / " + again);
						break;
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " teams changed between builds: " + problems.stream().limit(3).toList());
		helper.succeed();
	}

	/**
	 * B1122 below Master: a Journeyman's Wormadam (a random cloak; its forms learn different level-up moves) has the same
	 * cloak and the same moves every time. A Journeyman isn't given moves by train(), so they come from Cobblemon's own.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaTeams1110dWormadam"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaTeams1110dWormadam")
	public void aJourneymansWormadamKeepsItsCloakAndMoves(GameTestHelper helper) {
		UUID found = null;
		for (long n = 0; n < 4000 && found == null; n++) {
			UUID candidate = new UUID(0x1287488BL, 0x0110D00000000000L + n);
			if (CobblemonTrainers.team(candidate, 3, false).stream().anyMatch(p -> p.getSpecies().getName().equalsIgnoreCase("wormadam"))) {
				found = candidate;
			}
		}
		helper.assertTrue(found != null, "no Journeyman with a Wormadam in 4000 trainers");
		Set<String> seen = new HashSet<>();
		for (int k = 0; k < 15; k++) {
			for (Pokemon p : CobblemonTrainers.team(found, 3, false)) {
				if (p.getSpecies().getName().equalsIgnoreCase("wormadam")) {
					seen.add(look(p));
				}
			}
		}
		helper.assertTrue(seen.size() == 1, "trainer " + found + "'s Wormadam, " + seen.size() + " versions in 15 builds: " + seen);
		helper.succeed();
	}

	/**
	 * "The same team every time" in battle: the ability and nature it fights with, and its IVs and EVs, are the same in
	 * every battle too (12 trainers, every rank, 4 builds each).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTeams1110dBuilds"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTeams1110dBuilds")
	public void everyTrainersTeamFightsWithTheSameAbilitiesAndNatures(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int n = 0; n < 12; n++) {
			for (int rank = 1; rank <= 5; rank++) {
				List<String> first = builds(CobblemonTrainers.team(id(n), rank, false));
				for (int k = 0; k < 3; k++) {
					List<String> again = builds(CobblemonTrainers.team(id(n), rank, false));
					if (!again.equals(first)) {
						problems.add("trainer " + n + " rank " + rank + ": " + first + " / " + again);
						break;
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " of 60 teams fought with another build: " + problems.stream().limit(3).toList());
		helper.succeed();
	}
}
