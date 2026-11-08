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
 * QA lane-c 2026-11-08, B955 (from the bug's text: "any form set by a choice feature rather than a flag"): the Daycare
 * Keeper's hatchling of a mother whose form comes from a choice feature keeps that form after a save and load, for a
 * species beyond Tauros: Basculin's stripes (blue and white; the feature's default is red, the base form). And the
 * default form (a Combat Breed Tauros) stays the default. (Oricorio's dance styles can't be set from properties in the
 * test pack's Cobblemon, so a mother of a chosen style can't be made here.)
 */
public class QaDaycareChoiceForms1108cCompatTests implements FabricGameTest {
	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load");
		return loaded;
	}

	/** A mother made from {@code properties} has the form {@code form}; her hatchling with Ditto does too, saved and loaded twice. */
	private static void keepsForm(GameTestHelper helper, String properties, String form, long seed) {
		Pokemon made = make(properties + " level=40");
		helper.assertTrue(made.getForm().getName().equalsIgnoreCase(form), "setup: '" + properties + "' makes " + made.getForm().getName()
			+ " (aspects " + made.getAspects() + "), not " + form);
		Pokemon mother = stored(helper, made);
		helper.assertTrue(mother.getForm().getName().equalsIgnoreCase(form), "setup: '" + properties + "' isn't " + form + " even saved: "
			+ mother.getForm().getName());
		Pokemon baby = CobblemonDaycareKeeper.hatchling(stored(helper, make("ditto level=40")), mother, RandomSource.create(seed));
		helper.assertTrue(baby.getForm().getName().equalsIgnoreCase(form), "the hatchling of a " + form + " " + mother.getSpecies().getName()
			+ " isn't " + form + ": " + baby.getForm().getName() + " (aspects " + baby.getAspects() + ")");
		Pokemon loaded = stored(helper, baby);
		helper.assertTrue(loaded.getForm().getName().equalsIgnoreCase(form), "a " + form + " " + mother.getSpecies().getName()
			+ " hatchling loaded again as " + loaded.getForm().getName() + " (aspects " + loaded.getAspects() + ", before saving "
			+ baby.getAspects() + ")");
		Pokemon again = stored(helper, loaded);
		helper.assertTrue(again.getForm().getName().equalsIgnoreCase(form), "a " + form + " " + mother.getSpecies().getName()
			+ " hatchling loaded a second time as " + again.getForm().getName());
		helper.assertTrue(baby.getLevel() == 1, "the hatchling is level " + baby.getLevel() + ", not 1");
	}

	/** Blue-Striped Basculin: the stripes' default is red, the base form. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBlueStripedBasculinHatchlingStaysBlueStripedAfterReload(GameTestHelper helper) {
		keepsForm(helper, "basculin fish_stripes=blue", "Blue-Striped", 7);
		keepsForm(helper, "basculin fish_stripes=white", "White-Striped", 8);
		helper.succeed();
	}

	/** A Combat Breed (the feature's default) Paldean Tauros's hatchling is still Combat Breed, saved and loaded. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCombatBreedTaurosHatchlingStaysCombatAfterReload(GameTestHelper helper) {
		Pokemon combat = stored(helper, make("tauros paldean level=40"));
		keepsForm(helper, "tauros paldean bull_breed=combat", combat.getForm().getName(), 9);
		helper.assertFalse(combat.getForm().getName().toLowerCase().contains("blaze") || combat.getForm().getName().toLowerCase().contains("aqua"),
			"setup: a plain Paldean Tauros is " + combat.getForm().getName());
		helper.succeed();
	}
}
