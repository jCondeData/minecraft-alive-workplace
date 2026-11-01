package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-d-1101-1608), 38.5 "the Cup's final: one of them holds its Mega Stone, a Pokémon the theme allows (none when
 * no Mega Evolution fits it, as in the Little Cup)", tested from the spec through the shipped cup themes: the Little
 * Cup's final has no Mega Stone and the same team as an earlier round; the Frost Cup's Mega holder is one of its types;
 * a datapack theme no Pokémon fits gives an empty team at the final, not a crash.
 */
public class QaCupMegaTheme1101dCompatTests implements FabricGameTest {
	private static final int TRAINERS = 6;

	private static UUID trainer(int i) {
		return new UUID(0x11010d00L + i * 7919L, 0x4d3a0000L + i * 104729L);
	}

	private static String show(List<Pokemon> team) {
		return team.stream().map(p -> p.getSpecies().getName() + "/" + p.getLevel() + "@" + BuiltInRegistries.ITEM.getKey(p.heldItem().getItem())).toList().toString();
	}

	private static CupThemes.Theme shipped(GameTestHelper helper, String name) {
		CupThemes.Theme theme = CupThemes.all().stream().filter(t -> t.id().equals(AliveWorkplace.id(name))).findFirst().orElse(null);
		if (theme == null) {
			helper.fail("the shipped " + name + " theme isn't loaded: " + CupThemes.all().stream().map(CupThemes.Theme::id).toList());
		}
		return theme;
	}

	/** The Little Cup (first stages only): no Mega Evolution fits it, so its final is the same team as an earlier round, stoneless. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theLittleCupsFinalHasNoMegaStone(GameTestHelper helper) {
		CupThemes.Theme little = shipped(helper, "little_cup");
		for (int i = 0; i < TRAINERS; i++) {
			int tier = 1 + i % 5;
			List<Pokemon> fin = CobblemonTrainers.team(trainer(i), tier, little, true);
			List<Pokemon> round = CobblemonTrainers.team(trainer(i), tier, little, false);
			helper.assertTrue(fin.stream().noneMatch(CobblemonMegas::holdsItsStone), "a Little Cup finalist holds a Mega Stone: " + show(fin));
			helper.assertTrue(fin.size() == little.bring(), "a Little Cup finalist brings " + fin.size() + ", not " + little.bring() + ": " + show(fin));
			helper.assertTrue(show(fin).equals(show(round)), "the Little Cup final changed the team: " + show(round) + " to " + show(fin));
			helper.assertTrue(fin.stream().allMatch(p -> p.getSpecies().getPreEvolution() == null && !p.getSpecies().getEvolutions().isEmpty()),
				"a Little Cup finalist isn't a first stage: " + show(fin));
		}
		helper.succeed();
	}

	/** The Frost Cup (ice, steel, dragon): the final's one Mega Stone holder is a Pokémon of the theme's types, like the rest of the team. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theFrostCupsMegaIsIceSteelOrDragon(GameTestHelper helper) {
		CupThemes.Theme frost = shipped(helper, "frost_cup");
		Set<String> types = frost.trainerTypes();
		for (int i = 0; i < TRAINERS; i++) {
			int tier = 1 + i % 5;
			List<Pokemon> fin = CobblemonTrainers.team(trainer(i), tier, frost, true);
			helper.assertTrue(fin.size() == frost.bring(), "a Frost Cup finalist brings " + fin.size() + ": " + show(fin));
			helper.assertTrue(fin.stream().filter(CobblemonMegas::holdsItsStone).count() == 1, "a Frost Cup finalist without exactly one Mega Stone: " + show(fin));
			for (Pokemon p : fin) {
				boolean fits = false;
				for (var type : p.getSpecies().getStandardForm().getTypes()) {
					fits |= types.contains(type.getName().toLowerCase(Locale.ROOT));
				}
				helper.assertTrue(fits, p.getSpecies().getName() + " isn't ice, steel or dragon in the Frost Cup final: " + show(fin));
			}
			helper.assertTrue(show(CobblemonTrainers.team(trainer(i), tier, frost, true)).equals(show(fin)), "a Frost Cup finalist's team changed");
		}
		helper.succeed();
	}

	/** A datapack theme with a type no Pokémon has (a typo): the final's team is empty, without an exception, as fighters too. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aThemeNoPokemonFitsGivesAnEmptyFinal(GameTestHelper helper) {
		CupThemes.Theme typo = CupThemes.read(AliveWorkplace.id("qa_typo_cup"), JsonParser.parseString("""
			{"name": "cup.aliveworkplace.grand_cup", "order": 96, "level": 50, "bring": 3, "types": ["frie"], "stage": "any",
			 "dish": "minecraft:bread", "fireworks": ["#FF0000"], "disc": "minecraft:music_disc_cat"}""").getAsJsonObject());
		for (int i = 0; i < TRAINERS; i++) {
			int tier = 1 + i % 5;
			UUID id = trainer(i);
			List<Pokemon> fin;
			try {
				fin = CobblemonTrainers.team(id, tier, typo, true);
			} catch (RuntimeException e) {
				throw new AssertionError("a final under a theme no Pokémon fits threw " + e, e);
			}
			helper.assertTrue(fin.isEmpty(), "a theme no Pokémon fits fielded " + show(fin));
			List<CupBout.Fighter> fighters = CupBattlers.EXTENSION.call(b -> b.team(id, tier, typo, true), List.of());
			helper.assertTrue(fighters.isEmpty(), "a theme no Pokémon fits fielded fighters " + fighters);
		}
		helper.succeed();
	}
}
