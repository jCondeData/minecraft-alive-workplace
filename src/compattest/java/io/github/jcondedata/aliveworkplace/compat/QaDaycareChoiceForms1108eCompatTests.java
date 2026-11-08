package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-e-1108-0413, saves; B955's fix, 28.12): a Daycare hatchling keeps the mother's form when that form is picked
 * by one of Cobblemon's choice features (Shellos/Gastrodon's sea, Squawkabilly's color, Flabébé's flower, Basculin's
 * stripes), in the party right after hatching and after a save and load. From the wiki (villager-jobs
 * "Daycare hatchlings"): the pair hatches the mother's base form in her form, and a form picked by a choice feature
 * survives a save and load. Several hatchlings per case. A mother in the default form of a feature that defaults to
 * random (West Sea, Green, Curly) is B1031, tested in QaDaycareChoiceForms1108eBugCompatTests on tests/daycare-choice-default-1108e.
 */
public class QaDaycareChoiceForms1108eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load");
		return loaded;
	}

	/**
	 * {@code times} hatchlings of {@code mother} with Ditto, each checked right after hatching and after a save and load:
	 * the species is {@code species} and the form's name {@code form}. Fails with every hatchling that wasn't.
	 */
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

	/** An East Sea Gastrodon hatches an East Sea Shellos every time, still East after a save and load (B955's fix). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAnEastGastrodonHatchesAnEastShellosThatStaysEast(GameTestHelper helper) {
		hatchesAs(helper, "gastrodon sea=east", "shellos", "East", 8);
		helper.succeed();
	}

	/** A Blue Squawkabilly hatches a Blue one, still Blue after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABlueSquawkabillyHatchesBlueEveryTime(GameTestHelper helper) {
		hatchesAs(helper, "squawkabilly squawkabilly_color=blue", "squawkabilly", "Blue", 8);
		helper.succeed();
	}

	/** A Blue Flower Florges hatches a Blue Flower Flabébé (two evolutions back), still Blue after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABlueFlorgesHatchesABlueFlabebe(GameTestHelper helper) {
		hatchesAs(helper, "florges flower=blue", "flabebe", "Blue", 4);
		helper.succeed();
	}

	/** A Blue-Striped Basculin hatches a Blue-Striped Basculin, still one after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABlueStripedBasculinHatchesBlueStriped(GameTestHelper helper) {
		hatchesAs(helper, "basculin fish_stripes=blue", "basculin", "Blue-Striped", 4);
		helper.succeed();
	}

	/** Through the player's own path: an East Gastrodon's egg in the party is an East Shellos, still East after a save and load. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaAnEastShellosFromTheDaycareStaysEastInTheParty(GameTestHelper helper) {
		Pokemon gastrodon = stored(helper, make("gastrodon sea=east level=40"));
		Pokemon ditto = stored(helper, make("ditto level=40"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		CobblemonDaycareKeeper.giveEgg(player, ditto, gastrodon, RandomSource.create(7), false);
		Pokemon baby = null;
		for (Pokemon p : party) {
			if (p.getLevel() == 1) {
				baby = p;
			}
		}
		helper.assertTrue(baby != null && baby.getSpecies().getName().equalsIgnoreCase("shellos") && baby.getForm().getName().equalsIgnoreCase("East"),
			"the party's hatchling isn't an East Shellos: " + (baby == null ? null : baby.getSpecies().getName() + " " + baby.getForm().getName()));
		Pokemon loaded = stored(helper, baby);
		helper.assertTrue(loaded.getForm().getName().equalsIgnoreCase("East"),
			"the East Shellos loaded again as " + loaded.getForm().getName() + " (aspects " + loaded.getAspects() + ")");
		helper.succeed();
	}
}
