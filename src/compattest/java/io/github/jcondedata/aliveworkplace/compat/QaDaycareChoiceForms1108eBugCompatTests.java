package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-e-1108-0413): failing tests for a bug in the Daycare hatchling (B955's area, 28.12). NOT registered in the
 * compat test mod's fabric.mod.json (main stays green); the builder registers this class with the fix. Bug: a mother in
 * the default form of a choice feature whose default is "random" (a West Sea Gastrodon, a Green Squawkabilly, a Curly
 * Tatsugiri) hatches the right form, but after a save and load the hatchling is a random one (an East Shellos, a
 * Yellow or White Squawkabilly, a Droopy or Stretchy Tatsugiri). The default form has no aspects, so hatchling() sets no
 * feature, Cobblemon rolls the "random" default when the baby is made, and setForm only changes the form until the
 * saved feature picks the rolled one again on load. The passing controls (East, Blue, Flabébé, Basculin) are in
 * QaDaycareChoiceForms1108eCompatTests on main.
 */
public class QaDaycareChoiceForms1108eBugCompatTests implements FabricGameTest {
	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load");
		return loaded;
	}

	/** {@code times} hatchlings of {@code mother} with Ditto: each {@code species} {@code form} right after hatching and after a save and load. */
	private static void hatchesAs(GameTestHelper helper, String mother, String species, String form, int times) {
		Pokemon parent = stored(helper, make(mother + " level=40"));
		Pokemon ditto = stored(helper, make("ditto level=40"));
		List<String> wrong = new ArrayList<>();
		for (int seed = 1; seed <= times; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(ditto, parent, RandomSource.create(seed));
			Pokemon loaded = stored(helper, baby);
			for (Pokemon p : List.of(baby, loaded)) {
				if (!p.getSpecies().getName().equalsIgnoreCase(species) || !p.getForm().getName().equalsIgnoreCase(form)) {
					wrong.add((p == baby ? "hatched" : "after reload") + " #" + seed + ": " + p.getSpecies().getName() + " " + p.getForm().getName()
						+ " " + p.getAspects());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "a " + mother + " (form " + parent.getForm().getName() + ", aspects " + parent.getAspects()
			+ ") with Ditto should hatch " + species + " " + form + " every time; " + wrong.size() + " of " + (2 * times) + " weren't: " + wrong);
	}

	/** A West Sea Gastrodon (the sea feature defaults to random) hatches a West Sea Shellos, still West after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAWestGastrodonHatchesAWestShellosEveryTime(GameTestHelper helper) {
		hatchesAs(helper, "gastrodon sea=west", "shellos", "Normal", 8);
		helper.succeed();
	}

	/** A Green Squawkabilly (its color defaults to random) hatches a Green Squawkabilly, still Green after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAGreenSquawkabillyHatchesGreenEveryTime(GameTestHelper helper) {
		hatchesAs(helper, "squawkabilly squawkabilly_color=green", "squawkabilly", "Normal", 8);
		helper.succeed();
	}

	/** A Curly Tatsugiri (its texture defaults to random) hatches a Curly Tatsugiri, still Curly after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaACurlyTatsugiriHatchesCurlyEveryTime(GameTestHelper helper) {
		hatchesAs(helper, "tatsugiri tatsugiri_texture=curly", "tatsugiri", "Normal", 8);
		helper.succeed();
	}
}
