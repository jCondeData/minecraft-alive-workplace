package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-10, lane d): a second, independent check of B1122 (and B1104), commit 09f9646a4. The spec: "the same
 * trainer fields the same team (form, item, moves) every time, like its species" and the fix's promise that the look is
 * settled for home teams, Cup teams and the Mega Stone holder. Not re-tested here: what QaTrainerTeamsVerify1110d
 * covers, and the known gaps B1140 (Smeargle, Unown) and B1141 (moves below Master, abilities, natures below Expert),
 * so these tests compare Masters (and Masters' Cup teams) and leave the ability out.
 */
public class QaTrainerFormsVerify1110dCompatTests implements FabricGameTest {
	/** The Master from B1122's report, whose team has a Wormadam. */
	private static final UUID WORMADAM_MASTER = UUID.fromString("00000000-1287-488b-0000-054066443ace");

	/** Everything a Master's member shows or fights with that the fix settles: species, form, look, item, moves, nature, IVs, EVs. */
	static String full(Pokemon p) {
		StringBuilder stats = new StringBuilder();
		for (Stats s : List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)) {
			stats.append(' ').append(p.getIvs().get(s)).append('/').append(p.getEvs().get(s));
		}
		return QaTrainerTeamsVerify1110dCompatTests.look(p) + " " + p.getNature().getName() + stats;
	}

	static List<String> fulls(List<Pokemon> team) {
		return team.stream().map(QaTrainerFormsVerify1110dCompatTests::full).toList();
	}

	static UUID id(int n) {
		return new UUID(0x1110DF0L * (n + 3) ^ 0x2B1122L, 0x9E3779B97F4A7C15L * (n + 1));
	}

	/** Builds {@code build} {@code times} times and fails on the first build that differs from the first one. */
	static void sameEveryTime(GameTestHelper helper, String what, int times, Supplier<List<Pokemon>> build) {
		List<String> first = fulls(build.get());
		helper.assertFalse(first.isEmpty(), what + ": an empty team");
		for (int n = 1; n < times; n++) {
			List<String> again = fulls(build.get());
			helper.assertValueEqual(again, first, what + ", build " + (n + 1));
		}
	}

	static CupThemes.Theme theme(String id, String json) {
		return CupThemes.read(AliveWorkplace.id(id), JsonParser.parseString(json).getAsJsonObject());
	}

	/**
	 * B1122's own Master, 25 builds: every member, the Wormadam above all, keeps its cloak, look, item, moves, nature, IVs and
	 * EVs (the old check compared only species, form and item); the cloak is one of Wormadam's three.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWormadamMastersWholeTeamHoldsFor25Builds(GameTestHelper helper) {
		List<Pokemon> team = CobblemonTrainers.team(WORMADAM_MASTER, 5, false);
		Pokemon wormadam = team.stream().filter(p -> p.getSpecies().getName().equalsIgnoreCase("wormadam")).findFirst().orElse(null);
		helper.assertTrue(wormadam != null, "B1122's Master has no Wormadam any more: " + fulls(team));
		String cloak = wormadam.getForm().getName().toLowerCase();
		helper.assertTrue(Set.of("plant", "sandy", "trash").contains(cloak) || cloak.equals("normal"),
			"Wormadam wears no known cloak: " + full(wormadam));
		sameEveryTime(helper, "B1122's Master", 25, () -> CobblemonTrainers.team(WORMADAM_MASTER, 5, false));
		sameEveryTime(helper, "B1122's Master with a Mega Stone", 25, () -> CobblemonTrainers.team(WORMADAM_MASTER, 5, true));
		helper.succeed();
	}

	/**
	 * The Mega Stone holder megaAce brings in is settled too (the fix's CobblemonMegas line): six Masters who can Mega
	 * Evolve field the same whole team, stone holder included, at every one of 15 builds, and exactly one holds its stone.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aMegaMastersStoneHolderLooksTheSameEveryTime(GameTestHelper helper) {
		for (int k = 0; k < 6; k++) {
			UUID trainer = id(k);
			List<Pokemon> team = CobblemonTrainers.team(trainer, 5, true);
			if (CobblemonMegas.ENABLED) {
				helper.assertValueEqual(team.stream().filter(CobblemonMegas::holdsItsStone).count(), 1L,
					"Mega Stone holders in " + fulls(team));
			}
			sameEveryTime(helper, "Mega Master " + trainer, 15, () -> CobblemonTrainers.team(trainer, 5, true));
		}
		helper.succeed();
	}

	/**
	 * Cup teams are settled as home teams are: a Master's team for an open singles Cup, a Bug Cup and a Fire doubles final
	 * (with its Mega Stone holder) is the same at each of 15 builds, for five trainers.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aMastersCupTeamIsTheSameEveryTime(GameTestHelper helper) {
		CupThemes.Theme open = theme("qa1110d_open", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 95, "format": "singles", "level": 30, "bring": 3,
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread", "fireworks": ["#FF0000"],
			 "disc": "minecraft:music_disc_cat"}""");
		CupThemes.Theme bug = theme("qa1110d_bug", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 94, "format": "singles", "level": 50, "bring": 6, "types": ["bug"],
			 "stage": "final", "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread",
			 "fireworks": ["#FF0000"], "disc": "minecraft:music_disc_cat"}""");
		CupThemes.Theme fire = theme("qa1110d_fire", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 93, "format": "doubles", "level": 50, "bring": 2, "types": ["fire"],
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "rules": ["Species Clause"], "dish": "minecraft:bread",
			 "fireworks": ["#FF0000"], "disc": "minecraft:music_disc_cat"}""");
		for (int k = 0; k < 5; k++) {
			UUID trainer = id(20 + k);
			sameEveryTime(helper, "open Cup, " + trainer, 15, () -> CobblemonTrainers.team(trainer, 5, open));
			sameEveryTime(helper, "Bug Cup, " + trainer, 15, () -> CobblemonTrainers.team(trainer, 5, bug));
			sameEveryTime(helper, "Fire Cup final, " + trainer, 15, () -> CobblemonTrainers.team(trainer, 5, fire, true));
		}
		helper.succeed();
	}

	/**
	 * Settling only fixes the look; it may not invent one: over 60 trainers at every rank, each member's gender is one its
	 * species can have (a genderless one stays genderless, a one-gender species keeps that gender), and shinies stay rare
	 * (no more than a few times Cobblemon's shiny rate).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void gendersAndShinesAreOnesTheSpeciesAllows(GameTestHelper helper) {
		int members = 0;
		int shinies = 0;
		int genderless = 0;
		int oneGender = 0;
		for (int k = 0; k < 60; k++) {
			for (int rank = 1; rank <= 5; rank++) {
				for (Pokemon p : CobblemonTrainers.team(id(100 + k), rank, false)) {
					members++;
					Set<Gender> possible = p.getSpecies().getPossibleGenders();
					helper.assertTrue(possible.contains(p.getGender()), p.getSpecies().getName() + " is " + p.getGender()
						+ ", its species can be only " + possible + " (trainer " + id(100 + k) + ", rank " + rank + ")");
					if (possible.size() == 1) {
						if (possible.contains(Gender.GENDERLESS)) {
							genderless++;
						} else {
							oneGender++;
						}
					}
					if (p.getShiny()) {
						shinies++;
					}
				}
			}
		}
		float rate = Cobblemon.INSTANCE.getConfig().getShinyRate();
		int allowed = Math.max(3, (int) Math.ceil(4f * members / Math.max(1f, rate)));
		helper.assertTrue(shinies <= allowed, shinies + " shiny of " + members + " at a shiny rate of 1/" + rate + " (allowed " + allowed + ")");
		helper.assertTrue(genderless > 0 && oneGender > 0, "the sample had " + genderless + " genderless and " + oneGender
			+ " one-gender members of " + members + ": the check proved nothing");
		helper.succeed();
	}

	/**
	 * Different trainers may differ (B1122), and settling the cloak didn't pin Wormadam to one: across the Bug Cup teams
	 * of 300 Masters, every Wormadam keeps its cloak at a second build and the Wormadams wear more than one cloak.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '400'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void differentMastersWormadamsWearDifferentCloaks(GameTestHelper helper) {
		CupThemes.Theme bug = theme("qa1110d_bug_cloaks", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 92, "format": "singles", "level": 50, "bring": 6, "types": ["bug"],
			 "stage": "final", "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread",
			 "fireworks": ["#FF0000"], "disc": "minecraft:music_disc_cat"}""");
		Map<String, Integer> cloaks = new HashMap<>();
		List<String> wormadams = new ArrayList<>();
		for (int k = 0; k < 300; k++) {
			UUID trainer = UUID.nameUUIDFromBytes(("qa1110d-trainer-" + k).getBytes());
			List<Pokemon> first = CobblemonTrainers.team(trainer, 5, bug);
			List<Pokemon> second = CobblemonTrainers.team(trainer, 5, bug);
			for (int i = 0; i < first.size(); i++) {
				Pokemon p = first.get(i);
				if (!p.getSpecies().getName().equalsIgnoreCase("wormadam")) {
					continue;
				}
				String cloak = p.getForm().getName() + new TreeSet<>(p.getAspects());
				helper.assertValueEqual(full(second.get(i)), full(p), "trainer " + trainer + "'s Wormadam at a second build");
				cloaks.merge(cloak, 1, Integer::sum);
				wormadams.add(cloak);
			}
		}
		helper.assertTrue(wormadams.size() >= 8, "only " + wormadams.size() + " Wormadams in 300 Bug Cup teams: the check proved nothing");
		helper.assertTrue(cloaks.size() >= 2, wormadams.size() + " Wormadams, all in one cloak: " + cloaks);
		helper.succeed();
	}
}
