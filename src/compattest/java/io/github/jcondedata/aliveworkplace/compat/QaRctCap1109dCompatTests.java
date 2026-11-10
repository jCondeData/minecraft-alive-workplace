package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.compat.rct.RctLevelCaps;
import io.github.jcondedata.aliveworkplace.trainer.TrainerBattles;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane d, 2026-11-09): the village trainers against Radical Cobblemon Trainers' level cap (ROADMAP 43.1 and the
 * trainer items). RCT isn't installed in the compat suite, so the pieces are tested on their own: each tier's ceiling for
 * a cap ({@link Trainers#capCeiling}), a team brought down to it ({@link CobblemonTrainers#scaleToCap}: same Pokémon,
 * training and all, just younger; lower teams left alone; never below level 1), the screen's rules line for a cap and
 * without one, and that without RCT the screen and the battle field the real team and call no Pokémon over a cap.
 */
public class QaRctCap1109dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final int[] CAPS = {1, 10, 20, 50, 100};

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static List<Integer> levels(List<Pokemon> team) {
		return team.stream().map(Pokemon::getLevel).toList();
	}

	/** For every cap: Novice, Apprentice, Journeyman, Expert, Master each a step higher; a Novice under the cap, a Master over it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ceilingsRiseWithTheTierAroundTheCap(GameTestHelper helper) {
		for (int cap : new int[] {10, 20, 50, 100}) {
			List<Integer> row = new ArrayList<>();
			for (int tier = 1; tier <= 5; tier++) {
				row.add(Trainers.capCeiling(tier, cap));
			}
			for (int i = 1; i < 5; i++) {
				helper.assertTrue(row.get(i) > row.get(i - 1), "cap " + cap + ": tier " + (i + 1) + " isn't above tier " + i + " " + row);
			}
			helper.assertTrue(row.get(0) < cap, "cap " + cap + ": a Novice isn't under the cap " + row);
			helper.assertTrue(row.get(1) < cap, "cap " + cap + ": an Apprentice isn't under the cap " + row);
			helper.assertTrue(row.get(4) > cap, "cap " + cap + ": a Master isn't over the cap " + row);
			helper.assertTrue(row.get(4) - cap <= 10, "cap " + cap + ": a Master goes far over the cap " + row);
		}
		helper.assertValueEqual(List.of(Trainers.capCeiling(1, 50), Trainers.capCeiling(2, 50), Trainers.capCeiling(3, 50),
			Trainers.capCeiling(4, 50), Trainers.capCeiling(5, 50)), List.of(44, 47, 50, 53, 55), "the ceilings for cap 50");
		helper.succeed();
	}

	/** A tier outside 1-5 counts as the nearest one: 0 and below as a Novice, 6 and above as a Master. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ceilingOfATierOutOfRange(GameTestHelper helper) {
		for (int cap : CAPS) {
			int novice = Trainers.capCeiling(1, cap);
			int master = Trainers.capCeiling(5, cap);
			for (int tier : new int[] {0, -1, -50, Integer.MIN_VALUE}) {
				helper.assertValueEqual(Trainers.capCeiling(tier, cap), novice, "cap " + cap + ", tier " + tier + " (as a Novice)");
			}
			for (int tier : new int[] {6, 7, 99, Integer.MAX_VALUE}) {
				helper.assertValueEqual(Trainers.capCeiling(tier, cap), master, "cap " + cap + ", tier " + tier + " (as a Master)");
			}
		}
		helper.succeed();
	}

	/** A tiny cap: no ceiling below level 1 (cap 1 and cap 0, every tier); a Master still goes over a cap of 1. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ceilingNeverBelowLevelOne(GameTestHelper helper) {
		for (int cap : new int[] {0, 1, 2, 5}) {
			for (int tier = 0; tier <= 6; tier++) {
				int ceiling = Trainers.capCeiling(tier, cap);
				helper.assertTrue(ceiling >= 1, "cap " + cap + ", tier " + tier + ": a ceiling of " + ceiling);
			}
		}
		helper.assertValueEqual(Trainers.capCeiling(1, 1), 1, "a Novice against cap 1");
		helper.assertValueEqual(Trainers.capCeiling(1, 0), 1, "a Novice against cap 0");
		helper.assertTrue(Trainers.capCeiling(5, 1) > 1, "a Master doesn't go over a cap of 1: " + Trainers.capCeiling(5, 1));
		helper.succeed();
	}

	/**
	 * A Master against cap 20 (ceiling 25): one at exactly 25 and one under it are kept as they are, one at 26 comes down
	 * to 25 or less; the team keeps its size, its order and its Pokémon (the same objects, not new ones).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void exactlyAtTheCeilingStaysOneAboveComesDown(GameTestHelper helper) {
		int ceiling = Trainers.capCeiling(5, 20);
		Pokemon atCeiling = make("pikachu level=" + ceiling);
		Pokemon above = make("bulbasaur level=" + (ceiling + 1));
		Pokemon under = make("squirtle level=" + (ceiling - 1));
		Pokemon aboveToo = make("charmander level=" + (ceiling + 1));
		List<Pokemon> team = List.of(atCeiling, above, under, aboveToo);
		List<Pokemon> out = CobblemonTrainers.scaleToCap(new ArrayList<>(team), 5, 20);
		helper.assertValueEqual(out.size(), 4, "the team's size");
		for (int i = 0; i < 4; i++) {
			helper.assertTrue(out.get(i) == team.get(i), "slot " + i + " isn't the same Pokémon (order or identity changed)");
		}
		helper.assertValueEqual(atCeiling.getLevel(), ceiling, "a Pokémon exactly at the ceiling");
		helper.assertValueEqual(under.getLevel(), ceiling - 1, "a Pokémon under the ceiling");
		helper.assertTrue(above.getLevel() <= ceiling && above.getLevel() >= 1, "one level above the ceiling became " + above.getLevel());
		helper.assertTrue(aboveToo.getLevel() <= ceiling && aboveToo.getLevel() >= 1, "one level above the ceiling (slot 4) became " + aboveToo.getLevel());
		helper.assertTrue(levels(out).stream().allMatch(l -> l <= ceiling), "over the ceiling after scaling: " + levels(out));
		helper.succeed();
	}

	/**
	 * Same Pokémon, training and all, just younger: a trained level 60 Pikachu brought down to a Novice's ceiling keeps its
	 * species, form, nature, ability, gender, shininess, held item, IVs, EVs, moves and friendship, and is fully healed.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void scaledPokemonKeepTheirTraining(GameTestHelper helper) {
		Pokemon p = make("pikachu level=60 shiny=yes gender=female nature=timid");
		p.swapHeldItem(new ItemStack(Items.GOLDEN_APPLE), false, false);
		List<Stats> stats = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
		for (int i = 0; i < stats.size(); i++) {
			p.getIvs().set(stats.get(i), 31 - i * 3);
			p.getEvs().set(stats.get(i), i * 40);
		}
		p.setFriendship(200, true);
		p.setCurrentHealth(1);
		String species = p.getSpecies().getName();
		String form = p.getForm().getName();
		String nature = p.getNature().getName().toString();
		String ability = p.getAbility().getName();
		var gender = p.getGender();
		List<Integer> ivs = stats.stream().map(s -> p.getIvs().getOrDefault(s)).toList();
		List<Integer> evs = stats.stream().map(s -> p.getEvs().getOrDefault(s)).toList();
		List<String> moves = p.getMoveSet().getMoves().stream().map(m -> m.getName()).toList();
		int friendship = p.getFriendship();
		helper.assertTrue(!moves.isEmpty(), "setup: a Pikachu with no moves");

		int ceiling = Trainers.capCeiling(1, 30);
		List<Pokemon> out = CobblemonTrainers.scaleToCap(new ArrayList<>(List.of(p)), 1, 30);
		helper.assertTrue(out.size() == 1 && out.get(0) == p, "the scaled team isn't the same one Pokémon");
		helper.assertTrue(p.getLevel() <= ceiling && p.getLevel() >= 1, "level " + p.getLevel() + " against a ceiling of " + ceiling);
		helper.assertValueEqual(p.getSpecies().getName(), species, "species");
		helper.assertValueEqual(p.getForm().getName(), form, "form");
		helper.assertValueEqual(p.getNature().getName().toString(), nature, "nature");
		helper.assertValueEqual(p.getAbility().getName(), ability, "ability");
		helper.assertTrue(p.getGender() == gender, "gender changed: " + p.getGender());
		helper.assertTrue(p.getShiny(), "no longer shiny");
		helper.assertTrue(p.heldItem().is(Items.GOLDEN_APPLE), "held item: " + p.heldItem());
		helper.assertValueEqual(stats.stream().map(s -> p.getIvs().getOrDefault(s)).toList(), ivs, "IVs");
		helper.assertValueEqual(stats.stream().map(s -> p.getEvs().getOrDefault(s)).toList(), evs, "EVs");
		helper.assertValueEqual(p.getMoveSet().getMoves().stream().map(m -> m.getName()).toList(), moves, "moves");
		helper.assertValueEqual(p.getFriendship(), friendship, "friendship");
		helper.assertTrue(p.isFullHealth() && !p.isFainted(), "a scaled Pokémon isn't healed: " + p.getCurrentHealth() + "/" + p.getMaxHealth());
		helper.succeed();
	}

	/** A Master's team of level 80-100 against a Novice's ceiling for cap 1 and cap 0: every Pokémon at level 1, none lower, none lost. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTinyCapNeverGoesBelowLevelOne(GameTestHelper helper) {
		UUID id = new UUID(0x5EEDL, 0x1109DL);
		for (int cap : new int[] {1, 0}) {
			List<Pokemon> team = CobblemonTrainers.team(id, 5);
			List<String> species = team.stream().map(p -> p.getSpecies().getName()).toList();
			helper.assertTrue(team.size() >= 2, "setup: a Master's team of " + team.size());
			List<Pokemon> out = CobblemonTrainers.scaleToCap(team, 1, cap);
			helper.assertValueEqual(out.size(), species.size(), "cap " + cap + ": the team's size");
			helper.assertValueEqual(out.stream().map(p -> p.getSpecies().getName()).toList(), species, "cap " + cap + ": the species in order");
			helper.assertValueEqual(levels(out), out.stream().map(p -> 1).toList(), "cap " + cap + ": levels against a ceiling of 1");
			helper.assertTrue(out.stream().allMatch(Pokemon::isFullHealth), "cap " + cap + ": a scaled Pokémon isn't healed");
		}
		helper.succeed();
	}

	/** Lower-level teams are left as they are: a Novice's team against a high cap keeps every level, and a hurt one stays hurt. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTeamUnderTheCeilingIsLeftAlone(GameTestHelper helper) {
		UUID id = new UUID(0x5EEDL, 0x2209DL);
		for (int tier = 1; tier <= 5; tier++) {
			List<Pokemon> team = CobblemonTrainers.team(id, tier);
			List<Integer> before = levels(team);
			team.get(0).setCurrentHealth(1);
			List<Pokemon> out = CobblemonTrainers.scaleToCap(team, tier, 100);
			helper.assertValueEqual(levels(out), before, "tier " + tier + " against cap 100");
			helper.assertValueEqual(out.get(0).getCurrentHealth(), 1, "tier " + tier + ": a Pokémon under the ceiling was healed");
		}
		helper.succeed();
	}

	/**
	 * The same villager's Master and Novice teams against the same cap (30): each at or under its own ceiling (35 and 24),
	 * sizes kept, and the Master's strongest still above the Novice's. The Novice (levels 5-12) isn't touched at all.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void masterAndNoviceAgainstTheSameCap(GameTestHelper helper) {
		UUID id = new UUID(0x5EEDL, 0x3309DL);
		int cap = 30;
		List<Pokemon> master = CobblemonTrainers.team(id, 5);
		List<Pokemon> novice = CobblemonTrainers.team(id, 1);
		int masterSize = master.size();
		List<Integer> noviceBefore = levels(novice);
		helper.assertTrue(master.stream().allMatch(p -> p.getLevel() > Trainers.capCeiling(5, cap)), "setup: a Master's team under 35: " + levels(master));
		List<Pokemon> m = CobblemonTrainers.scaleToCap(master, 5, cap);
		List<Pokemon> n = CobblemonTrainers.scaleToCap(novice, 1, cap);
		helper.assertValueEqual(m.size(), masterSize, "the Master's team size");
		helper.assertValueEqual(levels(n), noviceBefore, "the Novice's team (under its ceiling) changed");
		helper.assertTrue(levels(m).stream().allMatch(l -> l >= 1 && l <= Trainers.capCeiling(5, cap)), "the Master's levels: " + levels(m));
		helper.assertTrue(levels(n).stream().allMatch(l -> l <= Trainers.capCeiling(1, cap)), "the Novice's levels: " + levels(n));
		int masterTop = levels(m).stream().mapToInt(Integer::intValue).max().orElse(0);
		int noviceTop = levels(n).stream().mapToInt(Integer::intValue).max().orElse(0);
		helper.assertTrue(masterTop > noviceTop, "the Master (" + levels(m) + ") isn't tougher than the Novice (" + levels(n) + ")");
		helper.assertValueEqual(masterTop, Trainers.capCeiling(5, cap), "the Master's strongest isn't at its ceiling");
		helper.succeed();
	}

	/**
	 * Without RCT: no cap for the player; a Master's screen shows the team at its real levels (the levels teamOf fields),
	 * none marked as brought down, and a level 100 Pokémon of the player's is Ready, not over a cap.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRctNoCapSides"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRctNoCapSides")
	public void withoutRctAMastersTeamIsShownAsItIs(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, false, "qarctnocap");
		Villager trainer = f.trainer();
		trainer.setVillagerData(trainer.getVillagerData().setLevel(5));
		helper.assertValueEqual(Trainers.tier(trainer), 5, "setup: the trainer's tier");
		QaPokemonScreens1109dCompatTests.party(f.player(), "mewtwo level=100");
		helper.assertTrue(RctLevelCaps.levelCap(f.player()).isEmpty(), "a level cap without RCT");

		List<Integer> fielded = levels(CobblemonTrainers.teamOf(trainer));
		TrainerView.Sides sides = CobblemonTrainers.sides(f.player(), trainer);
		helper.assertTrue(sides.levelCap() <= 0, "a level cap without RCT: " + sides.levelCap());
		helper.assertValueEqual(sides.team().stream().map(TrainerView.TeamSlot::level).toList(), fielded, "the team's levels on the screen");
		helper.assertTrue(sides.team().stream().noneMatch(TrainerView.TeamSlot::scaled), "a slot marked as brought down without a cap");
		helper.assertValueEqual(sides.party().size(), 2, "party lines");
		helper.assertValueEqual(sides.party().get(1).level(), 100, "the Mewtwo's level");
		helper.assertValueEqual(key(sides.party().get(1).state()), "screen.aliveworkplace.trainer.party.ready", "a level 100 Pokémon without a cap");
		helper.assertTrue(sides.party().stream().allMatch(l -> l.color() == TrainerView.GREEN), "a party line not green");
		helper.succeed();
	}

	/** The screen shows the team as challenge() fields it: without RCT, the battle's Pokémon have the levels the screen lists. */
	//$ gametest_ticks_batch AREA '300' '"qaRctScreenMatchesBattle"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaRctScreenMatchesBattle")
	public void theScreenListsTheLevelsTheBattleFields(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, false, "qarctbattle");
		Villager trainer = f.trainer();
		trainer.setVillagerData(trainer.getVillagerData().setLevel(4));
		List<Integer> shown = CobblemonTrainers.sides(f.player(), trainer).team().stream().map(TrainerView.TeamSlot::level).toList();
		Trainers.challenge(f.player(), trainer);
		var battle = BattleRegistry.getBattleByParticipatingPlayer(f.player());
		helper.assertTrue(battle != null, "no battle");
		List<Integer> fielded = null;
		for (BattleActor actor : battle.getActors()) {
			if (!(actor instanceof PlayerBattleActor)) {
				fielded = actor.getPokemonList().stream().map(BattlePokemon::getEffectedPokemon).map(Pokemon::getLevel).toList();
			}
		}
		helper.assertTrue(fielded != null, "no trainer side in the battle");
		helper.assertValueEqual(fielded, shown, "the battle's levels against the screen's");
		QaPokemonScreens1109dCompatTests.finish(f.player(), false);
		helper.succeed();
	}

	/**
	 * The rules line for a level cap (as RCT would give it): "Singles · level cap 35" and "… level cap 1"; for cap 0 or none
	 * the no-cap line. The cap's other lines read in American English too. RCT isn't here, so the cap comes from a stand-in
	 * for the screen's Pokémon half, put back as it was before the test returns.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRctRulesLine"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRctRulesLine")
	public void theRulesLineNamesTheCap(GameTestHelper helper) throws ReflectiveOperationException {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, false, "qarctrules");
		java.lang.reflect.Field field = io.github.jcondedata.aliveworkplace.work.Extension.class.getDeclaredField("filling");
		field.setAccessible(true);
		TrainerBattles real = (TrainerBattles) field.get(TrainerBattles.EXTENSION);
		helper.assertTrue(real != null, "setup: no trainer battles with Cobblemon");
		int[] cap = {0};
		try {
			TrainerBattles.EXTENSION.register("cobblemon", new TrainerBattles() {
				@Override
				public boolean challenge(ServerPlayer player, Villager trainer) {
					return real.challenge(player, trainer);
				}

				@Override
				public Component refusal(ServerPlayer player, Villager trainer) {
					return real.refusal(player, trainer);
				}

				@Override
				public TrainerView.Sides sides(ServerPlayer player, Villager trainer) {
					TrainerView.Sides s = real.sides(player, trainer);
					return new TrainerView.Sides(s.team(), s.party(), cap[0]);
				}
			});
			cap[0] = 35;
			TrainerView v = TrainerScreens.view(f.player(), f.trainer());
			helper.assertValueEqual(key(v.rules()), "screen.aliveworkplace.trainer.rules.cap", "the rules key with cap 35");
			helper.assertValueEqual(v.rules().getString(), "Singles · level cap 35", "the rules line with cap 35");
			cap[0] = 1;
			helper.assertValueEqual(TrainerScreens.view(f.player(), f.trainer()).rules().getString(), "Singles · level cap 1", "the rules line with cap 1");
			cap[0] = 0;
			helper.assertValueEqual(key(TrainerScreens.view(f.player(), f.trainer()).rules()), "screen.aliveworkplace.trainer.rules.no_cap", "the rules with cap 0");
			cap[0] = -1;
			helper.assertValueEqual(TrainerScreens.view(f.player(), f.trainer()).rules().getString(), "Singles · no level cap", "the rules line with no cap");
		} finally {
			TrainerBattles.EXTENSION.register("cobblemon", real);
		}
		helper.assertTrue(field.get(TrainerBattles.EXTENSION) == real, "the screen's Pokémon half wasn't put back");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.party.over_cap", 35).getString(), "Over the cap of 35", "the over-the-cap line");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.team.scaled").getString(), "Brought down to your level cap", "the scaled line");
		for (String line : List.of(Component.translatable("screen.aliveworkplace.trainer.rules.cap", 35).getString(),
				Component.translatable("screen.aliveworkplace.trainer.rules.no_cap").getString(),
				Component.translatable("screen.aliveworkplace.trainer.party.over_cap", 35).getString(),
				Component.translatable("screen.aliveworkplace.trainer.team.scaled").getString())) {
			String lower = line.toLowerCase(java.util.Locale.ROOT);
			for (String british : List.of("levell", "colour", "defence", "honour", "favour", "centre")) {
				helper.assertFalse(lower.contains(british), "British spelling in \"" + line + "\"");
			}
			helper.assertFalse(line.contains("screen.aliveworkplace"), "an untranslated key: " + line);
		}
		helper.succeed();
	}
}
