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
 * QA (2026-11-03, lane d): failing tests for bugs found in ROADMAP 28.12's hatchling. NOT registered in the compat test
 * mod's fabric.mod.json (main stays green); the builder registers this class with the fix. Bug: an Alolan (any regional)
 * mother hatches the regular form, because her form's pre-evolution resolves to Vulpix's Normal form.
 */
public class QaDaycareBugs1103dCompatTests implements FabricGameTest {
	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a parent didn't load");
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	/** The base form keeps the mother's regional form: a female Alolan Ninetales with a male (Kanto) Ninetales hatches an Alolan Vulpix. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMothersRegionalFormIsKept(GameTestHelper helper) {
		Pokemon mother = stored(helper, make("ninetales alolan gender=female level=40"));
		helper.assertTrue(mother.getForm().getName().equalsIgnoreCase("alola"), "the mother isn't Alolan: " + mother.getForm().getName());
		Pokemon father = stored(helper, make("ninetales gender=male level=40"));
		Pokemon baby = CobblemonDaycareKeeper.hatchling(father, mother, RandomSource.create(2));
		helper.assertTrue(id(baby).equals("vulpix") && baby.getForm().getName().equalsIgnoreCase("alola"),
			"an Alolan mother hatched a " + baby.getForm().getName() + " " + id(baby) + " (mother's aspects " + mother.getAspects()
				+ ", her form's pre-evolution resolves to " + mother.getForm().getPreEvolution().getForm().getName() + ")");
		// And the other way: a Kanto mother, an Alolan father: a Kanto Vulpix
		Pokemon kanto = CobblemonDaycareKeeper.hatchling(make("ninetales gender=female level=40"), make("ninetales alolan gender=male level=40"), RandomSource.create(2));
		helper.assertTrue(id(kanto).equals("vulpix") && !kanto.getForm().getName().equalsIgnoreCase("alola"),
			"a Kanto mother with an Alolan father hatched a " + kanto.getForm().getName() + " " + id(kanto));
		helper.succeed();
	}

}
