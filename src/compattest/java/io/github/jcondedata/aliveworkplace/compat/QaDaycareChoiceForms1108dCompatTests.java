package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-08, lane d) of B955's fix beyond the Tauros (B854/28.12's hatchling, "in the base form of the mother (or
 * the parent that isn't Ditto)"; wiki villager-jobs.md, "Daycare hatchlings": "A form picked by a choice feature rather
 * than a flag ... is set as that feature, so it stays after a save and load"). Other forms Cobblemon picks with a choice
 * feature: East Sea Shellos from Gastrodon, Flabébé's flowers from Florges (two steps), Sensu Oricorio, Basculin's
 * stripes (and Basculegion, whose pre-evolution is "basculin fish_stripes=white"), each hatched several times and saved
 * and loaded as a party would be. The cases that fail (a West Sea mother, a Baile Oricorio, a female Basculegion, a Blaze
 * Breed Tauros's Cobbreeding egg) are in QaDaycareChoiceForms1108dBugCompatTests.
 */
public class QaDaycareChoiceForms1108dCompatTests implements FabricGameTest {
	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	/** {@code pokemon} saved and loaded again (the keeper's boarders, and a party or PC after a restart). */
	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load: " + pokemon.getSpecies().getName());
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	private static String describe(Pokemon pokemon) {
		return pokemon.getForm().getName() + " " + id(pokemon) + " (aspects " + pokemon.getAspects() + ")";
	}

	private static String features(Pokemon pokemon) {
		StringBuilder out = new StringBuilder();
		for (var feature : pokemon.getFeatures()) {
			out.append(feature.getName()).append('=').append(feature instanceof com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature s ? s.getValue() : "?").append(' ');
		}
		return out.toString().trim();
	}

	/**
	 * Hatches {@code mother} with {@code father} (both as stored) {@code times} times, and asserts every hatchling is a
	 * level-1 {@code species} in the form named {@code form}, before and after it is saved and loaded.
	 */
	private static void expect(GameTestHelper helper, String mother, String father, String species, String form, int times) {
		Pokemon m = stored(helper, make(mother));
		Pokemon f = stored(helper, make(father));
		for (int i = 0; i < times; i++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(f, m, RandomSource.create(100 + i));
			helper.assertTrue(id(baby).equals(species) && baby.getForm().getName().equalsIgnoreCase(form) && baby.getLevel() == 1,
				mother + " x " + father + " hatched a level-" + baby.getLevel() + " " + describe(baby) + " (hatch " + (i + 1) + "); wanted a " + form + " " + species
					+ " (mother " + describe(m) + " features " + features(m) + ")");
			Pokemon loaded = stored(helper, baby);
			helper.assertTrue(id(loaded).equals(species) && loaded.getForm().getName().equalsIgnoreCase(form),
				mother + " x " + father + ": a " + form + " " + species + " hatchling loaded again as " + describe(loaded) + " (hatch " + (i + 1)
					+ "; before saving " + describe(baby) + " features " + features(baby) + ", mother " + describe(m) + ")");
		}
	}

	/** Gastrodon's seas: an East Sea mother hatches East Sea Shellos, every time and after a reload, with a West Sea father or with Ditto. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAnEastSeaGastrodonHatchesEastSeaShellos(GameTestHelper helper) {
		expect(helper, "gastrodon sea=east gender=female level=40", "gastrodon sea=west gender=male level=40", "shellos", "east", 6);
		expect(helper, "shellos sea=east gender=female level=20", "ditto level=40", "shellos", "east", 3);
		helper.succeed();
	}

	/** Florges's flowers, two steps back to Flabébé: a Blue Florges hatches a Blue Flabébé, and so on for each color. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAFlorgesHatchesAFlabebeOfHerFlower(GameTestHelper helper) {
		expect(helper, "florges flower=blue level=40", "ditto level=40", "flabebe", "blue", 2);
		expect(helper, "florges flower=white level=40", "ditto level=40", "flabebe", "white", 2);
		expect(helper, "florges flower=yellow level=40", "ditto level=40", "flabebe", "yellow", 2);
		expect(helper, "floette flower=orange level=30", "ditto level=40", "flabebe", "orange", 2);
		expect(helper, "florges flower=red level=40", "ditto level=40", "flabebe", "normal", 2);
		helper.succeed();
	}

	/** Oricorio's Sensu Style (one of its forms, picked by the dance_style choice): a Sensu mother hatches Sensu Oricorio, every time and after a reload. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaASensuOricorioHatchesSensu(GameTestHelper helper) {
		expect(helper, "oricorio dance_style=sensu gender=female level=30", "ditto level=40", "oricorio", "sensu", 6);
		helper.succeed();
	}

	/** Basculin's stripes: a Blue-Striped mother hatches Blue-Striped, a White-Striped White-Striped, a Red-Striped Red-Striped, after a reload too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBasculinHatchesTheMothersStripes(GameTestHelper helper) {
		expect(helper, "basculin fish_stripes=blue gender=female level=30", "basculin fish_stripes=red gender=male level=30", "basculin", "blue-striped", 3);
		expect(helper, "basculin fish_stripes=white gender=female level=30", "basculin fish_stripes=red gender=male level=30", "basculin", "white-striped", 3);
		expect(helper, "basculin fish_stripes=red gender=female level=30", "basculin fish_stripes=white gender=male level=30", "basculin", "normal", 3);
		helper.succeed();
	}

	/** Basculegion evolves only from a White-Striped Basculin, so a (male) Basculegion with Ditto hatches a White-Striped Basculin. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAMaleBasculegionWithDittoHatchesAWhiteStripedBasculin(GameTestHelper helper) {
		expect(helper, "basculegion gender=male level=40", "ditto level=40", "basculin", "white-striped", 3);
		helper.succeed();
	}
}
