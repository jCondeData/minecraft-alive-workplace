package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-11-08) for the trainers' "competitive touches" (the Trainer's teams, CobblemonTrainers.train: "better IVs
 * from Journeyman (15+), Expert (25+) and Master (31); from Expert, EVs in their better attacking stat and Speed, a
 * nature to match (Adamant or Modest) and a held item suited to that"). A local compat mutation run of
 * CobblemonTrainers (lane-d-1108-0108, against its 10 compat classes) let the Expert's touches moved to the Master only
 * ({@code t >= 3} to {@code t >= 4}) survive: no test read a team's IVs, EVs, nature or held item. This reads them for
 * twelve trainers at every tier (each team is seeded by the trainer, so the same every run).
 */
public class QaTrainerTraining1108dCompatTests implements FabricGameTest {
	private static final List<Stat> SIX = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);

	private static int of(PokemonStats stats, Stat stat) {
		Integer v = stats.get(stat);
		return v == null ? 0 : v;
	}

	private static int minIv(Pokemon p) {
		return SIX.stream().mapToInt(s -> of(p.getIvs(), s)).min().orElse(0);
	}

	private static int evTotal(Pokemon p) {
		return SIX.stream().mapToInt(s -> of(p.getEvs(), s)).sum();
	}

	private static String show(Pokemon p) {
		return p.getSpecies().getName() + " lv" + p.getLevel() + " ivs " + SIX.stream().map(s -> of(p.getIvs(), s)).toList() + " evs "
			+ SIX.stream().map(s -> of(p.getEvs(), s)).toList() + " " + p.getNature().getName() + " holding " + p.heldItem();
	}

	private static UUID trainer(int i) {
		return UUID.nameUUIDFromBytes(("qa-trainer-training-1108d-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/**
	 * A Novice's and an Apprentice's Pokémon are untrained (no EVs, nothing held); a Journeyman's have every IV 15 or
	 * more and still no EVs or item; an Expert's every IV 25 or more, 252 EVs in Speed and in its better attacking stat
	 * (Attack when its base Attack is at least its base Special Attack, else Special Attack), Adamant or Modest to match,
	 * and a held item; a Master's every IV 31 and the same.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachTierTrainsItsTeamAsTheSpecSays(GameTestHelper helper) {
		for (int i = 0; i < 12; i++) {
			UUID id = trainer(i);
			for (int tier = 1; tier <= 5; tier++) {
				for (Pokemon p : CobblemonTrainers.team(id, tier, false)) {
					String who = "tier " + tier + " trainer " + i + ": " + show(p);
					if (tier <= 3) {
						helper.assertTrue(evTotal(p) == 0, "below Expert a trainer's Pokémon has EVs: " + who);
						helper.assertTrue(p.heldItem().isEmpty(), "below Expert a trainer's Pokémon holds an item: " + who);
					}
					if (tier == 3) {
						helper.assertTrue(minIv(p) >= 15, "a Journeyman's Pokémon has an IV under 15: " + who);
					}
					if (tier >= 4) {
						helper.assertTrue(minIv(p) >= (tier == 4 ? 25 : 31), "an " + (tier == 4 ? "Expert's Pokémon has an IV under 25: " : "Master's Pokémon has an IV under 31: ") + who);
						Map<Stat, Integer> base = p.getForm().getBaseStats();
						boolean physical = base.getOrDefault(Stats.ATTACK, 0) >= base.getOrDefault(Stats.SPECIAL_ATTACK, 0);
						helper.assertTrue(of(p.getEvs(), Stats.SPEED) == 252, "an Expert-or-better's Pokémon hasn't 252 Speed EVs: " + who);
						helper.assertTrue(of(p.getEvs(), physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK) == 252,
							"an Expert-or-better's Pokémon hasn't 252 EVs in its better attacking stat (" + (physical ? "Attack" : "Sp. Atk") + "): " + who);
						helper.assertTrue(p.getNature() == (physical ? Natures.ADAMANT : Natures.MODEST), "an Expert-or-better's Pokémon's nature doesn't match its attacking stat: " + who);
						helper.assertFalse(p.heldItem().isEmpty(), "an Expert-or-better's Pokémon holds nothing: " + who);
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * "Young Pokémon for beginners, fully evolved ones at the top": a Novice's and an Apprentice's are all first stages,
	 * a Journeyman's are each evolved or a species that never evolves (no first stage that still can), and among the
	 * twelve Journeymen's teams there are middle stages (evolved, and still able to evolve); an Expert's and a Master's
	 * are all fully evolved. (Kills the Journeyman's pool {@code !basic || finalForm} turned into {@code &&}, which left
	 * out every middle stage and every species that never evolves.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachTierPicksTheStagesTheSpecSays(GameTestHelper helper) {
		int middle = 0;
		for (int i = 0; i < 12; i++) {
			UUID id = trainer(i);
			for (int tier = 1; tier <= 5; tier++) {
				for (Pokemon p : CobblemonTrainers.team(id, tier, false)) {
					boolean basic = p.getSpecies().getPreEvolution() == null;
					boolean finalForm = p.getSpecies().getEvolutions().isEmpty();
					String who = "tier " + tier + " trainer " + i + ": " + p.getSpecies().getName();
					if (tier <= 2) {
						helper.assertTrue(basic, "a beginner's Pokémon isn't a first stage: " + who);
					} else if (tier == 3) {
						helper.assertTrue(!basic || finalForm, "a Journeyman's Pokémon is a first stage that still evolves: " + who);
						if (!basic && !finalForm) {
							middle++;
						}
					} else {
						helper.assertTrue(finalForm, "an Expert-or-better's Pokémon can still evolve: " + who);
					}
				}
			}
		}
		helper.assertTrue(middle > 0, "none of twelve Journeymen's teams has a middle stage (evolved and still evolving)");
		helper.succeed();
	}

	/**
	 * A Festival Cup team is "trained by the trainer's tier as at home": an Apprentice's Frost Cup team has no EVs, an
	 * Expert's has 252 Speed EVs on every Pokémon. (Kills the Cup team's tier index {@code - 1} turned into {@code + 1},
	 * which trained an Apprentice's Cup team as an Expert's.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCupTeamIsTrainedByTheTrainersTierAsAtHome(GameTestHelper helper) {
		io.github.jcondedata.aliveworkplace.cup.CupThemes.Theme frost = io.github.jcondedata.aliveworkplace.cup.CupThemes.all().stream()
			.filter(t -> t.id().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("frost_cup"))).findFirst().orElse(null);
		helper.assertTrue(frost != null, "the shipped frost_cup theme isn't loaded");
		for (int i = 0; i < 6; i++) {
			List<Pokemon> apprentice = CobblemonTrainers.team(trainer(i), 2, frost);
			List<Pokemon> expert = CobblemonTrainers.team(trainer(i), 4, frost);
			helper.assertFalse(apprentice.isEmpty() || expert.isEmpty(), "setup: an empty Frost Cup team");
			for (Pokemon p : apprentice) {
				helper.assertTrue(evTotal(p) == 0, "an Apprentice's Cup Pokémon has EVs (trained above its tier): " + show(p));
			}
			for (Pokemon p : expert) {
				helper.assertTrue(of(p.getEvs(), Stats.SPEED) == 252, "an Expert's Cup Pokémon isn't trained as at home (no 252 Speed EVs): " + show(p));
			}
		}
		helper.succeed();
	}
}
